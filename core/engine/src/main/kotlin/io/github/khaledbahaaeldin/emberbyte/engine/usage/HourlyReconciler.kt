package io.github.khaledbahaaeldin.emberbyte.engine.usage

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

object HourlyReconciler {
    const val TOLERANCE = 0.05
    const val FULL_COVERAGE_MINUTES = 55

    /**
     * [hourlyRows] are the per-app rows of ONE hour (any networks). When the sampler covered at least
     * [FULL_COVERAGE_MINUTES] distinct minutes of that hour, each network's rows are scaled so they add up to the
     * sampler's total whenever the two differ by more than [TOLERANCE].
     */
    fun scale(hourStart: Instant, hourlyRows: List<HourlyUsage>, minuteRows: List<MinuteTotal>): List<HourlyUsage> {
        val inHour = minuteRows.filter { it.minuteStart.truncatedTo(ChronoUnit.HOURS) == hourStart }
        val coveredMinutes = inHour.map { it.minuteStart }.distinct().size
        if (coveredMinutes < FULL_COVERAGE_MINUTES) return hourlyRows
        return hourlyRows.groupBy { it.network }.flatMap { (network, rows) ->
            val trusted = inHour.filter { it.network == network }.sumOf { it.totalBytes }
            val actual = rows.sumOf { it.totalBytes }
            if (actual <= 0L || trusted <= 0L) {
                rows
            } else {
                val difference = abs(actual - trusted).toDouble() / trusted
                if (difference <= TOLERANCE) {
                    rows
                } else {
                    val factor = trusted.toDouble() / actual
                    rows.map {
                        it.copy(
                            rxBytes = (it.rxBytes * factor).roundToLong(),
                            txBytes = (it.txBytes * factor).roundToLong(),
                        )
                    }
                }
            }
        }
    }
}
