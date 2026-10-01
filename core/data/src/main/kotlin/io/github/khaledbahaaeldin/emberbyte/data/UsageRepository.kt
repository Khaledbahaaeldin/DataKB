package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeLiveSpeed(): Flow<LiveSpeed>
    fun observeToday(filter: UsageFilter = UsageFilter()): Flow<DayUsage>
    fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter = UsageFilter()): Flow<List<UsagePoint>>
    fun observeApps(range: DateRange, filter: UsageFilter = UsageFilter(), sort: AppSort = AppSort.BYTES_DESC): Flow<List<AppUsage>>
    fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>>
    fun observeCoverage(): Flow<CoverageStatus>
    suspend fun refreshNow(): Outcome<Unit>
}
