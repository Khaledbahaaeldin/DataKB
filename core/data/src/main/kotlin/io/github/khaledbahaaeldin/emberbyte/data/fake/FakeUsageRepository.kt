package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun oneSecondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

/** Canned, deterministic usage for M1: 1.24 GB today and a pretend week. */
class FakeUsageRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val tick: Flow<Unit> = oneSecondTicker(),
) : UsageRepository {

    override fun observeLiveSpeed(): Flow<LiveSpeed> {
        val startMs = clock.millis()
        return tick.map {
            val elapsed = clock.millis() - startMs
            LiveSpeed(
                rxBps = FakeTraffic.rxBpsAt(elapsed),
                txBps = FakeTraffic.txBpsAt(elapsed),
                network = NetworkKind.MOBILE,
                at = clock.instant(),
            )
        }
    }

    override fun observeToday(filter: UsageFilter): Flow<DayUsage> =
        flowOf(DayUsage(LocalDate.now(clock), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L))

    override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> =
        flowOf(weekSeries())

    override fun observeApps(range: DateRange, filter: UsageFilter, sort: AppSort): Flow<List<AppUsage>> =
        flowOf(apps.sortedByDescending { it.totalBytes })

    override fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>> =
        flowOf(weekSeries())

    override fun observeCoverage(): Flow<CoverageStatus> =
        flowOf(CoverageStatus(gaps = emptyList(), lastSampleAt = clock.instant()))

    override suspend fun refreshNow(): Outcome<Unit> = Outcome.Success(Unit)

    private fun weekSeries(): List<UsagePoint> {
        val dayBytes = listOf(0.9e9, 1.4e9, 0.7e9, 2.1e9, 1.1e9, 1.6e9, 1.24e9)
        val today = LocalDate.now(clock)
        return dayBytes.mapIndexed { index, total ->
            val day = today.minusDays((dayBytes.size - 1 - index).toLong())
            UsagePoint(
                start = day.atStartOfDay(clock.zone).toInstant(),
                mobileBytes = (total * 0.75).toLong(),
                wifiBytes = (total * 0.25).toLong(),
            )
        }
    }

    private val apps = listOf(
        AppUsage("com.google.android.youtube", "YouTube", 10_101, 610_000_000L, 0L, null),
        AppUsage("com.android.chrome", "Chrome", 10_102, 212_000_000L, 0L, null),
        AppUsage("com.google.android.apps.maps", "Maps", 10_103, 96_000_000L, 0L, null),
        AppUsage("com.spotify.music", "Spotify", 10_104, 71_000_000L, 0L, null),
        AppUsage("org.telegram.messenger", "Telegram", 10_105, 33_000_000L, 0L, null),
    )
}
