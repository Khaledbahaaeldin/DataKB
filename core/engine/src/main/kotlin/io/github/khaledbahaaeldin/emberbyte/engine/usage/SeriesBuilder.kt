package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private data class NetworkHour(val hour: Instant, val network: NetworkKind)

object SeriesBuilder {
    /** The platform keeps per-UID history in buckets of 2 hours aligned to even UTC hours. */
    const val WINDOW_SECONDS = 7_200L

    fun windowStart(at: Instant): Instant =
        Instant.ofEpochSecond(Math.floorDiv(at.epochSecond, WINDOW_SECONDS) * WINDOW_SECONDS)

    fun bucketStart(at: Instant, granularity: Granularity, zone: ZoneId): Instant = when (granularity) {
        Granularity.HOUR -> at.truncatedTo(ChronoUnit.HOURS)
        Granularity.DAY -> at.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        Granularity.WEEK -> at.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
        Granularity.MONTH -> at.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
    }

    fun nextBucketStart(start: Instant, granularity: Granularity, zone: ZoneId): Instant = when (granularity) {
        Granularity.HOUR -> start.plus(1, ChronoUnit.HOURS)
        Granularity.DAY -> start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        Granularity.WEEK -> start.atZone(zone).toLocalDate().plusWeeks(1).atStartOfDay(zone).toInstant()
        Granularity.MONTH -> start.atZone(zone).toLocalDate().plusMonths(1).atStartOfDay(zone).toInstant()
    }

    /**
     * Zero-filled buckets covering [range]. The two sources are merged per (2-hour window, network), never per hour:
     * total = max(sampler minute sum, system window sum); the surplus over the minute sum is booked on the window's first hour.
     * An hour contributes to the bucket that contains its START instant; hours that start before the first bucket or at/after
     * range.to are ignored, so Home, Apps and History all use the same rule.
     */
    fun build(
        range: DateRange,
        granularity: Granularity,
        zone: ZoneId,
        minuteRows: List<MinuteTotal>,
        hourlyRows: List<HourlyUsage>,
        filter: UsageFilter = UsageFilter(),
    ): List<UsagePoint> {
        if (range.from >= range.to) return emptyList()
        val first = bucketStart(range.from, granularity, zone)
        val starts = ArrayList<Instant>()
        var cursor = first
        while (cursor < range.to) {
            starts += cursor
            cursor = nextBucketStart(cursor, granularity, zone)
        }
        if (starts.isEmpty()) return emptyList()

        val minuteByHour = HashMap<NetworkHour, Long>()
        for (row in minuteRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            minuteByHour.merge(NetworkHour(row.minuteStart.truncatedTo(ChronoUnit.HOURS), row.network), row.totalBytes) { a, b -> a + b }
        }
        val systemByWindow = HashMap<NetworkHour, Long>()
        for (row in hourlyRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            systemByWindow.merge(NetworkHour(windowStart(row.hourStart), row.network), row.totalBytes) { a, b -> a + b }
        }

        val windows = HashSet<NetworkHour>()
        minuteByHour.keys.mapTo(windows) { NetworkHour(windowStart(it.hour), it.network) }
        windows.addAll(systemByWindow.keys)

        val mobile = LongArray(starts.size)
        val wifi = LongArray(starts.size)
        for (window in windows) {
            val hours = listOf(window.hour, window.hour.plus(1, ChronoUnit.HOURS))
            val minuteValues = hours.map { minuteByHour[NetworkHour(it, window.network)] ?: 0L }
            val minuteSum = minuteValues.sum()
            val total = maxOf(minuteSum, systemByWindow[window] ?: 0L)
            // The surplus (system bytes the sampler did not see) is booked on the window's FIRST hour: the same window-start rule the
            // per-app rows use, and never on an hour that has not started yet (that made "today" start high and then fall).
            val values = longArrayOf(minuteValues[0] + (total - minuteSum), minuteValues[1])
            hours.forEachIndexed { i, hour ->
                val value = values[i]
                if (value > 0L && hour >= first && hour < range.to) {
                    val index = indexFor(starts, hour)
                    if (window.network == NetworkKind.MOBILE) mobile[index] += value else wifi[index] += value
                }
            }
        }
        return starts.indices.map { UsagePoint(starts[it], mobile[it], wifi[it]) }
    }

    private fun matches(network: NetworkKind, subscriptionId: Int, filter: UsageFilter): Boolean =
        (filter.network == null || filter.network == network) &&
            (filter.subscriptionId == null || filter.subscriptionId == subscriptionId)

    /** Index of the last bucket whose start is <= [hour] (0 when the hour starts before the first bucket). */
    private fun indexFor(starts: List<Instant>, hour: Instant): Int {
        var low = 0
        var high = starts.lastIndex
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= hour) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
