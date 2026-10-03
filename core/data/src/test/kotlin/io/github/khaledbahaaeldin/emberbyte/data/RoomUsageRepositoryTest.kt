package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.sampler.CatchUpResult
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.data.util.MutableZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomUsageRepositoryTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val live = LiveSpeed(10, 5, NetworkKind.WIFI, now)
    private var catchUp: CatchUpResult = CatchUpResult.Done(1)

    private fun repo(store: InMemoryUsageStore) =
        RoomUsageRepository(store, flowOf(live), DayClock(clock), clock) { catchUp }

    private fun minute(at: String, network: NetworkKind, rx: Long) = MinuteTotal(Instant.parse(at), network, -1, rx, 0)
    private fun hourly(at: String, uid: Int, network: NetworkKind, rx: Long) = HourlyUsage(Instant.parse(at), uid, network, -1, rx, 0)

    @Test fun live_speed_is_passed_through() = runBlocking {
        assertEquals(live, repo(InMemoryUsageStore()).observeLiveSpeed().first())
    }

    @Test fun today_sums_only_todays_rows_split_by_network() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100))
        store.addMinute(minute("2026-10-07T10:01:00Z", NetworkKind.WIFI, 40))
        store.addMinute(minute("2026-10-06T10:00:00Z", NetworkKind.MOBILE, 999_999))
        val today = repo(store).observeToday().first()
        assertEquals(LocalDate.of(2026, 10, 7), today.date)
        assertEquals(100L, today.mobileBytes)
        assertEquals(40L, today.wifiBytes)
    }

    @Test fun today_uses_the_larger_of_minute_and_hourly_data() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100))
        store.upsertHourly(listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300)))
        assertEquals(300L, repo(store).observeToday().first().mobileBytes)
    }

    @Test fun today_respects_the_network_filter() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100))
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.WIFI, 40))
        val today = repo(store).observeToday(UsageFilter(network = NetworkKind.WIFI)).first()
        assertEquals(0L, today.mobileBytes)
        assertEquals(40L, today.wifiBytes)
    }

    @Test fun series_has_one_point_per_day_in_the_range() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-05T10:00:00Z", NetworkKind.MOBILE, 10))
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 30))
        val range = DateRange(Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        val series = repo(store).observeSeries(range, Granularity.DAY).first()
        assertEquals(listOf(10L, 0L, 30L), series.map { it.mobileBytes })
    }

    @Test fun apps_are_aggregated_sorted_and_labelled() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.chat", "Chat")))
        store.upsertHourly(
            listOf(
                hourly("2026-10-07T09:00:00Z", 10001, NetworkKind.MOBILE, 500),
                hourly("2026-10-07T09:00:00Z", 10002, NetworkKind.WIFI, 900),
                hourly("2026-10-06T09:00:00Z", 10001, NetworkKind.MOBILE, 7_000), // before the range
            ),
        )
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), now)
        val apps = repo(store).observeApps(range, UsageFilter(), AppSort.BYTES_DESC).first()
        assertEquals(listOf("Chat", "Video"), apps.map { it.label })
        val mobileOnly = repo(store).observeApps(range, UsageFilter(network = NetworkKind.MOBILE)).first()
        assertEquals(listOf("Video"), mobileOnly.map { it.label })
    }

    @Test fun app_series_contains_only_that_packages_uids() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.chat", "Chat")))
        store.upsertHourly(
            listOf(
                hourly("2026-10-07T09:00:00Z", 10001, NetworkKind.MOBILE, 500),
                hourly("2026-10-07T09:00:00Z", 10002, NetworkKind.MOBILE, 9_000),
            ),
        )
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        val series = repo(store).observeAppSeries("com.video", range, Granularity.DAY).first()
        assertEquals(500L, series.single().mobileBytes)
    }

    @Test fun coverage_reports_gaps_and_the_last_sample() = runBlocking {
        val store = InMemoryUsageStore()
        store.insertGap(CoverageGap(now.minusSeconds(3600), now.minusSeconds(1800), GapReason.SERVICE_KILLED))
        store.addMinute(minute("2026-10-07T11:59:00Z", NetworkKind.WIFI, 1))
        val coverage = repo(store).observeCoverage().first()
        assertEquals(1, coverage.gaps.size)
        assertEquals(Instant.parse("2026-10-07T11:59:00Z"), coverage.lastSampleAt)
    }

    @Test fun refresh_maps_the_catch_up_result_to_an_outcome() = runBlocking {
        val repo = repo(InMemoryUsageStore())
        catchUp = CatchUpResult.Done(3)
        assertTrue(repo.refreshNow() is Outcome.Success)
        catchUp = CatchUpResult.MissingUsageAccess
        assertEquals(Outcome.Failure(EmberbyteError.MissingUsageAccess), repo.refreshNow())
    }

    @Test fun today_is_exactly_the_matching_bucket_of_the_series_in_a_half_hour_zone() = runBlocking {
        val kolkata = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), java.time.ZoneId.of("Asia/Kolkata"))
        val store = InMemoryUsageStore()
        store.upsertHourly(listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.WIFI, 1_000), hourly("2026-10-07T08:00:00Z", 10001, NetworkKind.WIFI, 400)))
        val repo = RoomUsageRepository(store, flowOf(live), DayClock(kolkata), kolkata) { catchUp }
        val today = repo.observeToday().first()
        val range = DateRange(Instant.parse("2026-10-06T18:30:00Z"), Instant.parse("2026-10-07T18:30:00Z"))
        val bar = repo.observeSeries(range, Granularity.DAY).first().single()
        assertEquals(bar.totalBytes, today.totalBytes)
    }

    @Test fun apps_use_the_same_start_instant_rule() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        store.upsertHourly(listOf(hourly("2026-10-06T23:00:00Z", 10001, NetworkKind.MOBILE, 700), hourly("2026-10-07T01:00:00Z", 10001, NetworkKind.MOBILE, 300)))
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        assertEquals(300L, repo(store).observeApps(range).first().single().totalBytes)
    }

    @Test fun today_follows_a_time_zone_change_on_the_same_date() = runTest {
        val cairo = java.time.ZoneId.of("Africa/Cairo")
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), cairo) // Oct 2 in both Cairo (03:30) and London (01:30)
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(Instant.parse("2026-10-01T22:10:00Z"), NetworkKind.MOBILE, -1, 5_000_000, 0)) // Cairo: today (01:10 Oct 2); London: yesterday
        store.addMinute(MinuteTotal(Instant.parse("2026-10-02T00:10:00Z"), NetworkKind.MOBILE, -1, 7_000_000, 0)) // today in both
        val repo = RoomUsageRepository(store, flowOf(live), DayClock(clock, pollMillis = 30_000), clock) { catchUp }
        val seen = mutableListOf<Long>()
        val job = launch { repo.observeToday().collect { seen += it.totalBytes } }
        runCurrent()
        assertEquals(12_000_000L, seen.last())                              // Cairo day
        clock.zoneId = java.time.ZoneId.of("Europe/London")
        advanceTimeBy(31_000); runCurrent()
        assertEquals(7_000_000L, seen.last())                               // London day: exactly the London day, not a slice of yesterday
        clock.zoneId = cairo
        advanceTimeBy(31_000); runCurrent()
        assertEquals(12_000_000L, seen.last())
        job.cancel()
    }

    @Test fun the_week_strip_never_grows_beyond_its_range_after_a_zone_change() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), java.time.ZoneId.of("Africa/Cairo"))
        val repo = RoomUsageRepository(InMemoryUsageStore(), flowOf(live), DayClock(clock), clock) { catchUp }
        clock.zoneId = java.time.ZoneId.of("Europe/London")
        val from = Instant.parse("2026-09-25T23:00:00Z")                   // 7 London days
        val bars = repo.observeSeries(DateRange(from, from.plusSeconds(7 * 86_400L)), Granularity.DAY).first()
        assertEquals(7, bars.size)
    }
}
