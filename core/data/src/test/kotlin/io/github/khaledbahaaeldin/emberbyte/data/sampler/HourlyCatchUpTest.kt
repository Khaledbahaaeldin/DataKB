package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyCatchUpTest {
    private val now = Instant.parse("2026-10-07T10:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val hour10 = Instant.parse("2026-10-07T10:00:00Z")
    private val hour09 = Instant.parse("2026-10-07T09:00:00Z")
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun catchUp(
        source: FakeNetworkStatsSource = FakeNetworkStatsSource(),
        store: InMemoryUsageStore = InMemoryUsageStore(),
        info: FakeAppInfoSource = FakeAppInfoSource(),
        access: FakeUsageAccess = FakeUsageAccess(true),
    ) = HourlyCatchUp(source, store, info, access, clock)

    @Test fun without_usage_access_nothing_is_queried() = runBlocking {
        val source = FakeNetworkStatsSource()
        val result = catchUp(source, access = FakeUsageAccess(false)).run()
        assertEquals(CatchUpResult.MissingUsageAccess, result)
        assertTrue(source.queries.isEmpty())
    }

    @Test fun the_first_run_backfills_seven_days_for_both_networks() = runBlocking {
        val source = FakeNetworkStatsSource()
        val result = catchUp(source).run()
        assertEquals(CatchUpResult.Done(7 * 24 + 1), result)
        assertEquals((7 * 24 + 1) * 2, source.queries.size)
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), source.queries.minOf { it.second })
    }

    @Test fun rows_are_stored_per_uid_and_network_and_mobile_rows_get_the_dominant_subscription() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(hour10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to hour10) to listOf(UidUsage(10001, 1000, 500)),
                (NetworkKind.WIFI to hour10) to listOf(UidUsage(10001, 2000, 0), UidUsage(10002, 300, 0)),
            ),
        )
        catchUp(source, store).run()
        val rows = store.hourlyRows(hour10, hour10.plusSeconds(3600)).sortedWith(compareBy({ it.uid }, { it.network }))
        assertEquals(
            listOf(
                HourlyUsage(hour10, 10001, NetworkKind.MOBILE, 3, 1000, 500),
                HourlyUsage(hour10, 10001, NetworkKind.WIFI, -1, 2000, 0),
                HourlyUsage(hour10, 10002, NetworkKind.WIFI, -1, 300, 0),
            ),
            rows,
        )
    }

    @Test fun the_current_hour_is_queried_only_up_to_now() = runBlocking {
        val source = FakeNetworkStatsSource()
        catchUp(source).run()
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, hour10, now)))
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, hour09, hour10)))
    }

    @Test fun a_fully_sampled_past_hour_is_scaled_to_the_samplers_total() = runBlocking {
        val store = InMemoryUsageStore()
        repeat(60) { store.addMinute(MinuteTotal(hour09.plusSeconds(it * 60L), NetworkKind.MOBILE, -1, 100, 0)) }
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to hour09) to listOf(UidUsage(10001, 12_000, 0))))
        catchUp(source, store).run()
        val row = store.hourlyRows(hour09, hour10).single()
        assertEquals(6_000L, row.totalBytes)
    }

    @Test fun a_second_run_only_revisits_the_last_hours() = runBlocking {
        val source = FakeNetworkStatsSource()
        val job = catchUp(source, InMemoryUsageStore())
        job.run()
        source.queries.clear()
        job.run()
        assertEquals(Instant.parse("2026-10-07T08:00:00Z"), source.queries.minOf { it.second })
        assertEquals(6, source.queries.size) // hours 08, 09, 10 for two networks
    }

    @Test fun app_labels_are_resolved_once_per_new_uid_and_zero_byte_rows_are_skipped() = runBlocking {
        val store = InMemoryUsageStore()
        val info = FakeAppInfoSource(mapOf(10001 to AppMeta(10001, "com.video", "Video")))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to hour10) to listOf(UidUsage(10001, 10, 0), UidUsage(10003, 0, 0)),
                (NetworkKind.WIFI to hour10) to listOf(UidUsage(10001, 20, 0)),
            ),
        )
        val job = catchUp(source, store, info)
        job.run()
        job.run()
        assertEquals(listOf(AppMeta(10001, "com.video", "Video")), store.appMeta())
        assertEquals(1, info.resolved.count { it == 10001 })
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10003 })
    }
}
