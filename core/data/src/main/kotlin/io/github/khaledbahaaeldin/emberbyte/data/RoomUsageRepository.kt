package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.data.sampler.CatchUpResult
import io.github.khaledbahaaeldin.emberbyte.data.store.OPEN_END
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppAggregator
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The real [UsageRepository]: everything is derived from the [UsageStore] with the pure engine functions. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomUsageRepository(
    private val store: UsageStore,
    private val liveSpeed: Flow<LiveSpeed>,
    private val dayClock: DayClock,
    private val clock: Clock,
    private val refresh: suspend () -> CatchUpResult,
) : UsageRepository {
    private val zone: ZoneId get() = clock.zone

    override fun observeLiveSpeed(): Flow<LiveSpeed> = liveSpeed

    override fun observeToday(filter: UsageFilter): Flow<DayUsage> = dayClock.dates().flatMapLatest { date ->
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        observeSeries(DateRange(from, to), Granularity.DAY, filter).map { points ->
            val point = points.firstOrNull()
            DayUsage(date, point?.mobileBytes ?: 0L, point?.wifiBytes ?: 0L)
        }
    }

    override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> {
        val padded = SeriesBuilder.bucketStart(range.from, granularity, zone).minusSeconds(SeriesBuilder.WINDOW_SECONDS)
        return combine(store.observeMinuteRows(padded, range.to), store.observeHourlyRows(padded, range.to)) { minutes, hours ->
            SeriesBuilder.build(range, granularity, zone, minutes, hours, filter)
        }
    }

    override fun observeApps(range: DateRange, filter: UsageFilter, sort: AppSort): Flow<List<AppUsage>> =
        combine(
            store.observeHourlyRows(range.from, range.to),
            store.observeAppMeta(),
        ) { rows, meta -> AppAggregator.aggregate(rows, meta.associateBy { it.uid }, filter, sort) }

    override fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>> {
        val padded = SeriesBuilder.bucketStart(range.from, granularity, zone).minusSeconds(SeriesBuilder.WINDOW_SECONDS)
        return combine(store.observeHourlyRows(padded, range.to), store.observeAppMeta()) { rows, meta ->
            val uids = meta.filter { it.packageName == packageName }.map { it.uid }.toSet()
            SeriesBuilder.build(range, granularity, zone, emptyList(), rows.filter { it.uid in uids })
        }
    }

    override fun observeCoverage(): Flow<CoverageStatus> {
        val from = clock.instant().minus(UsageRetention.GAPS)
        return combine(store.observeGaps(from, OPEN_END), store.observeLastSample()) { gaps, last ->
            CoverageStatus(gaps, last)
        }
    }

    override suspend fun refreshNow(): Outcome<Unit> = when (refresh()) {
        is CatchUpResult.Done -> Outcome.Success(Unit)
        CatchUpResult.MissingUsageAccess -> Outcome.Failure(EmberbyteError.MissingUsageAccess)
    }
}
