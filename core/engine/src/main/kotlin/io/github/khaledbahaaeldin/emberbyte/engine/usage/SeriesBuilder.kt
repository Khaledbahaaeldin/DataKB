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

private data class HourKey(val hour: Instant, val network: NetworkKind)

object SeriesBuilder {

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

    fun build(
        range: DateRange,
        granularity: Granularity,
        zone: ZoneId,
        minuteRows: List<MinuteTotal>,
        hourlyRows: List<HourlyUsage>,
        filter: UsageFilter = UsageFilter(),
    ): List<UsagePoint> {
        val first = bucketStart(range.from, granularity, zone)
        val starts = ArrayList<Instant>()
        var cursor = first
        while (cursor < range.to) {
            starts += cursor
            cursor = nextBucketStart(cursor, granularity, zone)
        }
        if (starts.isEmpty()) return emptyList()

        val minuteByKey = HashMap<HourKey, Long>()
        for (row in minuteRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            val key = HourKey(row.minuteStart.truncatedTo(ChronoUnit.HOURS), row.network)
            minuteByKey.merge(key, row.totalBytes) { a, b -> a + b }
        }
        val hourlyByKey = HashMap<HourKey, Long>()
        for (row in hourlyRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            hourlyByKey.merge(HourKey(row.hourStart, row.network), row.totalBytes) { a, b -> a + b }
        }

        val mobile = LongArray(starts.size)
        val wifi = LongArray(starts.size)
        for (key in minuteByKey.keys + hourlyByKey.keys) {
            // Include hours that overlap the first bucket; exclude hours that start at or after the range end.
            if (key.hour.plus(1, ChronoUnit.HOURS) <= first || key.hour >= range.to) continue
            val value = maxOf(minuteByKey[key] ?: 0L, hourlyByKey[key] ?: 0L)
            val index = indexFor(starts, key.hour)
            if (key.network == NetworkKind.MOBILE) mobile[index] += value else wifi[index] += value
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
