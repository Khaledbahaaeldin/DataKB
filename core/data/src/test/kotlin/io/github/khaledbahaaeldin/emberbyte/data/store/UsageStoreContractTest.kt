package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class UsageStoreContractTest {
    abstract fun createStore(): UsageStore

    private val t = Instant.parse("2026-10-07T10:00:00Z")
    private val wide = t.minus(Duration.ofDays(2)) to t.plus(Duration.ofDays(2))
    private fun m(offsetMin: Long, network: NetworkKind = NetworkKind.MOBILE, rx: Long = 1, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(t.plusSeconds(offsetMin * 60), network, sub, rx, tx)
    private fun h(offsetHours: Long, uid: Int, rx: Long, network: NetworkKind = NetworkKind.MOBILE, sub: Int = -1) =
        HourlyUsage(t.plusSeconds(offsetHours * 3600), uid, network, sub, rx, 0)

    @Test fun addMinute_adds_to_an_existing_row_with_the_same_key() = runBlocking {
        val store = createStore()
        store.addMinute(m(0, rx = 10, tx = 1))
        store.addMinute(m(0, rx = 5, tx = 2))
        val rows = store.minuteRows(wide.first, wide.second)
        assertEquals(listOf(m(0, rx = 15, tx = 3)), rows)
    }

    @Test fun rows_with_a_different_network_or_subscription_are_separate() = runBlocking {
        val store = createStore()
        store.addMinute(m(0, NetworkKind.MOBILE, sub = 1))
        store.addMinute(m(0, NetworkKind.MOBILE, sub = 2))
        store.addMinute(m(0, NetworkKind.WIFI))
        assertEquals(3, store.minuteRows(wide.first, wide.second).size)
    }

    @Test fun minuteRows_is_half_open_and_ordered() = runBlocking {
        val store = createStore()
        store.addMinute(m(2)); store.addMinute(m(0)); store.addMinute(m(1))
        val rows = store.minuteRows(t, t.plusSeconds(120))
        assertEquals(listOf(t, t.plusSeconds(60)), rows.map { it.minuteStart })
    }

    @Test fun upsertHourly_replaces_the_same_key_and_keeps_others() = runBlocking {
        val store = createStore()
        store.upsertHourly(listOf(h(0, 1, 100), h(0, 2, 200)))
        store.upsertHourly(listOf(h(0, 1, 111)))
        val rows = store.hourlyRows(wide.first, wide.second).sortedBy { it.uid }
        assertEquals(listOf(h(0, 1, 111), h(0, 2, 200)), rows)
    }

    @Test fun appMeta_is_replaced_by_uid() = runBlocking {
        val store = createStore()
        store.upsertAppMeta(listOf(AppMeta(1, "a", "A"), AppMeta(2, "b", "B")))
        store.upsertAppMeta(listOf(AppMeta(1, "a", "A2")))
        assertEquals(listOf(AppMeta(1, "a", "A2"), AppMeta(2, "b", "B")), store.appMeta().sortedBy { it.uid })
    }

    @Test fun gaps_returns_the_ones_overlapping_the_window() = runBlocking {
        val store = createStore()
        store.insertGap(CoverageGap(t, t.plusSeconds(600), GapReason.SERVICE_KILLED))
        store.insertGap(CoverageGap(t.minus(Duration.ofDays(30)), t.minus(Duration.ofDays(29)), GapReason.REBOOT))
        val found = store.gaps(t.plusSeconds(300), t.plusSeconds(900))
        assertEquals(listOf(GapReason.SERVICE_KILLED), found.map { it.reason })
    }

    @Test fun checkpoints_round_trip_and_overwrite() = runBlocking {
        val store = createStore()
        assertNull(store.loadCheckpoint("sampler.total"))
        store.saveCheckpoint("sampler.total", CounterReading(t, 10, 20, "b1"))
        store.saveCheckpoint("sampler.total", CounterReading(t.plusSeconds(5), 11, 21, "b2"))
        assertEquals(CounterReading(t.plusSeconds(5), 11, 21, "b2"), store.loadCheckpoint("sampler.total"))
        assertNull(store.loadCheckpoint("sampler.mobile"))
    }

    @Test fun observeMinuteRows_emits_again_after_a_write() = runBlocking {
        val store = createStore()
        withTimeout(10_000) {
            val seen = mutableListOf<List<MinuteTotal>>()
            val job = launch { store.observeMinuteRows(wide.first, wide.second).toList(seen) }
            while (seen.isEmpty()) delay(10)
            store.addMinute(m(0, rx = 42))
            while (seen.last().isEmpty()) delay(10)
            job.cancel()
            assertEquals(42L, seen.last().single().rxBytes)
        }
    }

    @Test fun observeLastSample_tracks_the_newest_minute() = runBlocking {
        val store = createStore()
        withTimeout(10_000) {
            val seen = mutableListOf<Instant?>()
            val job = launch { store.observeLastSample().toList(seen) }
            while (seen.isEmpty()) delay(10)
            assertNull(seen.last())
            store.addMinute(m(5))
            while (seen.last() == null) delay(10)
            store.addMinute(m(9))
            while (seen.last() != m(9).minuteStart) delay(10)
            job.cancel()
        }
    }

    @Test fun prune_deletes_only_rows_older_than_the_retention() = runBlocking {
        val store = createStore()
        val now = t
        val oldMinute = MinuteTotal(now.minus(UsageRetention.MINUTES).minusSeconds(60), NetworkKind.WIFI, -1, 1, 0)
        val keptMinute = MinuteTotal(now.minus(UsageRetention.MINUTES).plusSeconds(60), NetworkKind.WIFI, -1, 1, 0)
        store.addMinute(oldMinute); store.addMinute(keptMinute)
        val oldHour = HourlyUsage(now.minus(UsageRetention.HOURLY).minusSeconds(3600), 1, NetworkKind.WIFI, -1, 1, 0)
        val keptHour = HourlyUsage(now.minus(UsageRetention.HOURLY).plusSeconds(3600), 1, NetworkKind.WIFI, -1, 1, 0)
        store.upsertHourly(listOf(oldHour, keptHour))
        store.insertGap(CoverageGap(now.minus(UsageRetention.GAPS).minusSeconds(100), now.minus(UsageRetention.GAPS).minusSeconds(50), GapReason.REBOOT))
        store.insertGap(CoverageGap(now.minusSeconds(100), now.minusSeconds(50), GapReason.REBOOT))

        store.prune(now)

        val everything = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")
        assertEquals(listOf(keptMinute), store.minuteRows(everything.first, everything.second))
        assertEquals(listOf(keptHour), store.hourlyRows(everything.first, everything.second))
        assertEquals(1, store.gaps(everything.first, everything.second).size)
    }
}
