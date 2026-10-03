package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyCatchUpTest {
    private val now = Instant.parse("2026-10-07T10:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val window10 = Instant.parse("2026-10-07T10:00:00Z") // window [10:00, 12:00)
    private val window08 = Instant.parse("2026-10-07T08:00:00Z")
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun catchUp(
        source: NetworkStatsSource = FakeNetworkStatsSource(),
        store: InMemoryUsageStore = InMemoryUsageStore(),
        info: FakeAppInfoSource = FakeAppInfoSource(),
        access: FakeUsageAccess = FakeUsageAccess(true),
    ) = HourlyCatchUp(source, store, info, access, clock)

    @Test fun without_usage_access_nothing_is_queried() = runBlocking {
        val source = FakeNetworkStatsSource()
        assertEquals(CatchUpResult.MissingUsageAccess, catchUp(source, access = FakeUsageAccess(false)).run())
        assertTrue(source.queries.isEmpty())
    }

    @Test fun the_first_run_backfills_seven_days_one_two_hour_window_at_a_time() = runBlocking {
        val source = FakeNetworkStatsSource()
        assertEquals(CatchUpResult.Done(7 * 12 + 1), catchUp(source).run())
        assertEquals((7 * 12 + 1) * 2, source.queries.size)
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), source.queries.minOf { it.second })
        // every closed window is queried over its full two hours
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, window08, window10)))
    }

    @Test fun the_open_window_is_queried_up_to_now_only() = runBlocking {
        val source = FakeNetworkStatsSource()
        catchUp(source).run()
        assertTrue(source.queries.contains(Triple(NetworkKind.WIFI, window10, now)))
    }

    @Test fun rows_are_stored_per_uid_and_network_keyed_by_the_window_start_with_the_dominant_subscription() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(window10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 1000, 500)),
                (NetworkKind.WIFI to window10) to listOf(UidUsage(10001, 2000, 0), UidUsage(10002, 300, 0)),
            ),
        )
        catchUp(source, store).run()
        val rows = store.hourlyRows(window10, window10.plusSeconds(1)).sortedWith(compareBy({ it.uid }, { it.network }))
        assertEquals(
            listOf(
                HourlyUsage(window10, 10001, NetworkKind.MOBILE, 3, 1000, 500),
                HourlyUsage(window10, 10001, NetworkKind.WIFI, -1, 2000, 0),
                HourlyUsage(window10, 10002, NetworkKind.WIFI, -1, 300, 0),
            ),
            rows,
        )
    }

    @Test fun a_later_run_replaces_the_window_so_a_changed_subscription_leaves_no_duplicate() = runBlocking {
        val store = InMemoryUsageStore()
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 1000, 0))))
        val job = catchUp(source, store)
        job.run()                                                    // no minute rows yet: subscription -1
        store.addMinute(MinuteTotal(window10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        job.run()                                                    // now the dominant subscription is 3
        val mobile = store.hourlyRows(window10, window10.plusSeconds(1)).filter { it.network == NetworkKind.MOBILE }
        assertEquals(1, mobile.size)
        assertEquals(3, mobile.single().subscriptionId)
    }

    @Test fun a_uid_that_disappears_from_the_system_data_disappears_from_the_window() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertHourly(listOf(HourlyUsage(window10, 10009, NetworkKind.WIFI, -1, 5, 5)))
        catchUp(FakeNetworkStatsSource(), store).run()
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10009 })
    }

    @Test fun a_second_run_only_revisits_the_previous_and_the_current_window() = runBlocking {
        val source = FakeNetworkStatsSource()
        val job = catchUp(source, InMemoryUsageStore())
        job.run()
        source.queries.clear()
        job.run()
        assertEquals(Instant.parse("2026-10-07T08:00:00Z"), source.queries.minOf { it.second })
        assertEquals(4, source.queries.size) // windows 08:00 and 10:00 for two networks
    }

    @Test fun app_labels_are_resolved_once_per_new_uid_and_zero_byte_rows_are_skipped() = runBlocking {
        val store = InMemoryUsageStore()
        val info = FakeAppInfoSource(mapOf(10001 to AppMeta(10001, "com.video", "Video")))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 10, 0), UidUsage(10003, 0, 0)),
                (NetworkKind.WIFI to window10) to listOf(UidUsage(10001, 20, 0)),
            ),
        )
        val job = catchUp(source, store, info)
        job.run(); job.run()
        assertEquals(listOf(AppMeta(10001, "com.video", "Video")), store.appMeta())
        assertEquals(1, info.resolved.count { it == 10001 })
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10003 })
    }

    @Test fun concurrent_runs_are_serialised() = runBlocking {
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val slow = object : NetworkStatsSource {
            override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> {
                val now = running.incrementAndGet()
                maxSeen.updateAndGet { maxOf(it, now) }
                delay(1)
                running.decrementAndGet()
                return emptyList()
            }
        }
        val job = catchUp(slow)
        listOf(async { job.run() }, async { job.run() }, async { job.run() }).awaitAll()
        assertEquals(1, maxSeen.get())
    }
}
