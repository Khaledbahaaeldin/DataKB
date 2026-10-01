package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeCounterSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SamplerEngineTest {
    private val t0 = Instant.parse("2026-10-07T10:00:00Z")
    private val minute10 = t0
    private val minute11 = t0.plusSeconds(60)
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun snap(sec: Long, mobRx: Long = 0, mobTx: Long = 0, totRx: Long = mobRx, totTx: Long = mobTx, boot: String = "b1") =
        CounterSnapshot(t0.plusSeconds(sec), mobRx, mobTx, totRx, totTx, boot)

    private fun engine(
        script: List<CounterSnapshot>,
        store: InMemoryUsageStore = InMemoryUsageStore(),
        kind: NetworkKind? = NetworkKind.MOBILE,
        sub: Int = 7,
    ) = SamplerEngine(FakeCounterSource(script), FakeNetworkKindSource(kind), FakeSubscriptionSource(sub), store)

    private suspend fun InMemoryUsageStore.rows() = minuteRows(open.first, open.second)

    @Test fun first_tick_reports_the_instantaneous_speed() = runTest {
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000)))
        e.start(); e.tick()
        val live = e.liveSpeed.replayCache.single()
        assertEquals(1000L, live.rxBps)
        assertEquals(0L, live.txBps)
    }

    @Test fun later_ticks_are_smoothed_with_an_ema_of_half() = runTest {
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000), snap(2, mobRx = 4000)))
        e.start(); e.tick(); e.tick()
        assertEquals(2000L, e.liveSpeed.replayCache.last().rxBps) // 0.5 * 3000 + 0.5 * 1000
    }

    @Test fun wifi_is_total_minus_mobile_and_rows_carry_their_subscription() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 200, totRx = 1000)), store)
        e.start(); e.tick(); e.stop()
        val rows = store.rows()
        val mobile = rows.single { it.network == NetworkKind.MOBILE }
        val wifi = rows.single { it.network == NetworkKind.WIFI }
        assertEquals(200L, mobile.rxBytes); assertEquals(7, mobile.subscriptionId)
        assertEquals(800L, wifi.rxBytes); assertEquals(-1, wifi.subscriptionId)
    }

    @Test fun a_closed_minute_is_flushed_when_the_minute_changes_and_the_rest_on_stop() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(30, mobRx = 100), snap(59, mobRx = 200), snap(61, mobRx = 300)), store)
        e.start(); e.tick(); e.tick(); e.tick()
        assertEquals(200L, store.minuteRows(minute10, minute11).single { it.network == NetworkKind.MOBILE }.rxBytes)
        assertTrue(store.minuteRows(minute11, minute11.plusSeconds(60)).isEmpty())
        e.stop()
        assertEquals(100L, store.minuteRows(minute11, minute11.plusSeconds(60)).single { it.network == NetworkKind.MOBILE }.rxBytes)
    }

    @Test fun both_networks_get_a_row_every_minute_even_with_no_traffic() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1)), store)
        e.start(); e.tick(); e.stop()
        val rows = store.rows()
        assertEquals(setOf(NetworkKind.MOBILE, NetworkKind.WIFI), rows.map { it.network }.toSet())
        assertTrue(rows.all { it.totalBytes == 0L })
    }

    @Test fun the_live_speed_carries_the_current_network_kind() = runTest {
        val wifi = engine(listOf(snap(0), snap(1, totRx = 10)), kind = NetworkKind.WIFI)
        wifi.start(); wifi.tick()
        assertEquals(NetworkKind.WIFI, wifi.liveSpeed.replayCache.last().network)
        val offline = engine(listOf(snap(0), snap(1)), kind = null)
        offline.start(); offline.tick()
        assertNull(offline.liveSpeed.replayCache.last().network)
    }

    @Test fun a_tick_with_no_elapsed_time_is_skipped() = runTest {
        val e = engine(listOf(snap(0), snap(0, mobRx = 500)))
        e.start(); e.tick()
        assertTrue(e.liveSpeed.replayCache.isEmpty())
    }

    @Test fun restart_after_a_long_pause_records_a_service_killed_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b1"))
        engine(listOf(snap(0)), store).start()
        val gap = store.gaps(open.first, open.second).single()
        assertEquals(GapReason.SERVICE_KILLED, gap.reason)
        assertEquals(t0.minusSeconds(300), gap.from); assertEquals(t0, gap.to)
    }

    @Test fun restart_with_a_different_boot_id_records_a_reboot_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b0"))
        engine(listOf(snap(0)), store).start()
        assertEquals(GapReason.REBOOT, store.gaps(open.first, open.second).single().reason)
    }

    @Test fun a_quick_restart_records_no_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(60), 0, 0, "b1"))
        engine(listOf(snap(0)), store).start()
        assertTrue(store.gaps(open.first, open.second).isEmpty())
    }

    @Test fun bytes_used_while_the_service_was_down_are_not_added_to_minute_totals() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b1"))
        val e = engine(listOf(snap(0, mobRx = 5000), snap(1, mobRx = 5100)), store)
        e.start(); e.tick(); e.stop()
        assertEquals(100L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes)
    }

    @Test fun a_counter_decrease_records_a_reset_gap_and_counts_the_new_value() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000), snap(2, mobRx = 50)), store)
        e.start(); e.tick(); e.tick(); e.stop()
        assertEquals(1050L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes)
        val gap = store.gaps(open.first, open.second).single()
        assertEquals(GapReason.COUNTER_RESET, gap.reason)
        assertEquals(t0.plusSeconds(1), gap.from); assertEquals(t0.plusSeconds(2), gap.to)
    }

    @Test fun stop_saves_both_checkpoints() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 10, totRx = 30)), store)
        e.start(); e.tick(); e.stop()
        assertEquals(30L, store.loadCheckpoint("sampler.total")?.rxBytes)
        assertEquals(10L, store.loadCheckpoint("sampler.mobile")?.rxBytes)
        assertNotNull(store.loadCheckpoint("sampler.total")?.bootId)
    }

    @Test fun run_ticks_every_second_and_flushes_when_cancelled() = runTest {
        val store = InMemoryUsageStore()
        val script = (0L..10L).map { snap(it, mobRx = it * 10) }
        val e = SamplerEngine(FakeCounterSource(script), FakeNetworkKindSource(), FakeSubscriptionSource(7), store, tickMillis = 1_000)
        val job = launch { e.run() }
        advanceTimeBy(3_500); runCurrent()
        job.cancelAndJoin()
        assertEquals(40L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes) // 4 ticks x 10
        assertNotNull(store.loadCheckpoint("sampler.total"))
    }

    @Test fun wifi_delta_is_clamped_to_zero_when_total_is_less_than_mobile() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 500, totRx = 200)), store)
        e.start(); e.tick(); e.stop()
        val wifi = store.rows().single { it.network == NetworkKind.WIFI }
        assertEquals(0L, wifi.rxBytes)
        assertEquals(0L, wifi.txBytes)
    }

    @Test(expected = IllegalStateException::class)
    fun calling_tick_before_start_throws() = runTest {
        val e = engine(listOf(snap(0), snap(1)))
        e.tick()
    }

    @Test fun a_tick_with_negative_elapsed_time_is_skipped() = runTest {
        val e = engine(listOf(snap(5), snap(2, mobRx = 500)))
        e.start(); e.tick()
        assertTrue(e.liveSpeed.replayCache.isEmpty())
    }
}

