package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

object ForecastEngine {
    /** Share of a day's mobile usage per LOCAL hour (a typical smartphone day; normalised before use). */
    val DEFAULT_HOUR_CURVE: DoubleArray = doubleArrayOf(
        0.010, 0.006, 0.004, 0.003, 0.003, 0.004, 0.010, 0.030, 0.055, 0.060, 0.060, 0.060,
        0.065, 0.060, 0.055, 0.055, 0.060, 0.065, 0.070, 0.075, 0.075, 0.065, 0.035, 0.015,
    )

    private const val MAX_HISTORY_DAYS = 28
    private const val FULL_WEEKDAY_SAMPLES = 3

    private data class Stat(val mean: Double, val std: Double)
    private class Projection(val runOut: Instant?, val bytes: Double)

    fun forecast(
        state: PlanState,
        dailyHistory: List<DayUsage>,
        now: Instant,
        zone: ZoneId,
        extraBytesPerDay: Long = 0L,
        extraFrom: Instant = now,
        hourCurve: DoubleArray = DEFAULT_HOUR_CURVE,
    ): Forecast {
        require(hourCurve.size == 24 && hourCurve.sum() > 0.0) { "hourCurve needs 24 positive weights" }
        val today = now.atZone(zone).toLocalDate()
        val history = dailyHistory.filter { it.date < today }.sortedBy { it.date }.takeLast(MAX_HISTORY_DAYS)
        val overall = stat(history.map { it.mobileBytes.toDouble() })
        val profile = DayOfWeek.entries.associateWith { weekday ->
            blended(history.filter { it.date.dayOfWeek == weekday }.map { it.mobileBytes.toDouble() }, overall)
        }
        val cv = if (overall.mean > 0.0) overall.std / overall.mean else 0.0
        val confidence = when {
            history.size < 7 -> Confidence.LOW
            history.size >= 21 && cv < 0.6 -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }

        fun run(k: Double) = project(state, profile, now, zone, k, extraBytesPerDay, extraFrom, hourCurve)
        val high = run(+1.0)
        val mid = run(0.0)
        val low = run(-1.0)
        val exhausted = state.remainingBytes <= 0L
        return Forecast(
            planId = state.plan.id,
            runOutEarliest = if (exhausted) now else high.runOut,
            runOutExpected = if (exhausted) now else mid.runOut,
            runOutLatest = if (exhausted) now else low.runOut,
            projectedCycleEndBytes = state.usedBytes + mid.bytes.roundToLong(),
            confidence = confidence,
            historyDays = history.size,
        )
    }

    private fun project(
        state: PlanState, profile: Map<DayOfWeek, Stat>, now: Instant, zone: ZoneId, k: Double,
        extraBytesPerDay: Long, extraFrom: Instant, curve: DoubleArray,
    ): Projection {
        val end = state.window.end
        val remaining = state.remainingBytes.toDouble()
        val extraDate = extraFrom.atZone(zone).toLocalDate()
        var cumulative = 0.0
        var runOut: Instant? = null
        var date = now.atZone(zone).toLocalDate()
        var segmentStart = now
        var first = true
        while (segmentStart < end) {
            val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
            val segmentEnd = minOf(nextDayStart, end)
            val stat = profile.getValue(date.dayOfWeek)
            val nowTime = now.atZone(zone).toLocalTime()
            val extraTime = extraFrom.atZone(zone).toLocalTime()
            val ahead = if (first) 1.0 - cumulativeShare(curve, nowTime) else 1.0
            val extraShare = when {
                date.isBefore(extraDate) -> 0.0
                date.isAfter(extraDate) -> ahead // a later day: the whole day (on today's segment: what is still ahead)
                else -> 1.0 - cumulativeShare(curve, if (first && nowTime.isAfter(extraTime)) nowTime else extraTime)
            }
            val baseBytes = max(0.0, stat.mean + k * stat.std) * ahead
            val extraBytes = if (extraBytesPerDay > 0L) extraBytesPerDay * extraShare else 0.0
            val inWindow = millis(segmentStart, segmentEnd).toDouble() / millis(segmentStart, nextDayStart)
            val segmentBytes = (baseBytes + extraBytes) * inWindow
            if (runOut == null && segmentBytes > 0.0 && cumulative + segmentBytes >= remaining) {
                val fraction = (remaining - cumulative) / segmentBytes
                runOut = segmentStart.plusMillis((millis(segmentStart, segmentEnd) * fraction).roundToLong())
            }
            cumulative += segmentBytes
            segmentStart = segmentEnd
            date = date.plusDays(1)
            first = false
        }
        return Projection(runOut, cumulative)
    }

    private fun millis(a: Instant, b: Instant): Long = Duration.between(a, b).toMillis()

    private fun cumulativeShare(curve: DoubleArray, time: LocalTime): Double {
        val total = curve.sum()
        var done = 0.0
        for (hour in 0 until time.hour) done += curve[hour]
        done += curve[time.hour] * ((time.minute + time.second / 60.0) / 60.0)
        return done / total
    }

    private fun stat(values: List<Double>): Stat {
        if (values.isEmpty()) return Stat(0.0, 0.0)
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return Stat(mean, sqrt(variance))
    }

    /** A weekday with fewer than 3 samples is pulled toward the overall figure (no samples: the overall figure). */
    private fun blended(values: List<Double>, overall: Stat): Stat {
        if (values.size >= FULL_WEEKDAY_SAMPLES) return stat(values)
        val own = stat(values)
        val weight = values.size / FULL_WEEKDAY_SAMPLES.toDouble()
        return Stat(weight * own.mean + (1 - weight) * overall.mean, weight * own.std + (1 - weight) * overall.std)
    }
}
