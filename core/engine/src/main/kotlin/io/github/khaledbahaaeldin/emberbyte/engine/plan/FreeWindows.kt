package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

internal data class Interval(val start: Instant, val end: Instant)

internal object FreeWindows {
    /** Bytes excluded from the plan by [rules] (contract 10.1: all-apps intervals, then app intervals minus the all-apps ones). */
    fun freeBytes(totals: List<UsageSlice>, perApp: List<UsageSlice>, rules: List<FreeRule>, window: CycleWindow, zone: ZoneId): Long {
        if (rules.isEmpty()) return 0L
        val everyone = merge(intervals(rules.filter { it.packageName == null }, window, zone))
        var free = 0L
        for (slice in totals) free += share(slice, window, everyone)
        val byPackage = rules.filter { it.packageName != null }.groupBy { it.packageName!! }
        for ((pkg, pkgRules) in byPackage) {
            val own = subtract(merge(intervals(pkgRules, window, zone)), everyone)
            for (slice in perApp) if (slice.packageName == pkg) free += share(slice, window, own)
        }
        return free
    }

    private fun share(slice: UsageSlice, window: CycleWindow, intervals: List<Interval>): Long {
        val spanStart = maxOf(slice.start, window.start)
        val spanEnd = minOf(slice.end, window.end)
        if (spanEnd <= spanStart || intervals.isEmpty()) return 0L
        val bytes = Slices.clippedBytes(slice, window.start, window.end)
        val spanMillis = Duration.between(spanStart, spanEnd).toMillis()
        var freeMillis = 0L
        for (interval in intervals) freeMillis += Slices.overlapMillis(interval.start, interval.end, spanStart, spanEnd)
        if (freeMillis >= spanMillis) return bytes
        return (bytes.toDouble() * freeMillis / spanMillis).roundToLong()
    }

    private fun intervals(rules: List<FreeRule>, window: CycleWindow, zone: ZoneId): List<Interval> {
        val firstDate = window.start.atZone(zone).toLocalDate().minusDays(1)
        val lastDate = window.end.atZone(zone).toLocalDate()
        val out = ArrayList<Interval>()
        for (rule in rules) {
            if (rule.start == rule.end || rule.days.isEmpty()) continue
            var date = firstDate
            while (!date.isAfter(lastDate)) {
                if (date.dayOfWeek in rule.days) {
                    val start = date.atTime(rule.start).atZone(zone).toInstant()
                    val endDate = if (rule.end > rule.start) date else date.plusDays(1)   // end <= start: crosses midnight
                    out += Interval(start, endDate.atTime(rule.end).atZone(zone).toInstant())
                }
                date = date.plusDays(1)
            }
        }
        return out
    }

    fun merge(list: List<Interval>): List<Interval> {
        if (list.isEmpty()) return list
        val sorted = list.sortedBy { it.start }
        val out = ArrayList<Interval>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (next.start <= current.end) current = Interval(current.start, maxOf(current.end, next.end))
            else { out += current; current = next }
        }
        out += current
        return out
    }

    fun subtract(from: List<Interval>, minus: List<Interval>): List<Interval> {
        var result = from
        for (cut in minus) {
            val next = ArrayList<Interval>()
            for (interval in result) {
                if (cut.end <= interval.start || cut.start >= interval.end) { next += interval; continue }
                if (cut.start > interval.start) next += Interval(interval.start, cut.start)
                if (cut.end < interval.end) next += Interval(cut.end, interval.end)
            }
            result = next
        }
        return result
    }
}
