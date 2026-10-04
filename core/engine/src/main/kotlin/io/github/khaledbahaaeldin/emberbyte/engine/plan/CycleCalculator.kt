package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object CycleCalculator {
    /** The window `[start, end)` that contains [at]. */
    fun windowAt(cycle: Cycle, at: Instant): CycleWindow = when (cycle) {
        is Cycle.MonthlyOnDay -> monthly(cycle, at)
        is Cycle.EveryNDays -> everyNDays(cycle, at)
    }

    fun previousWindow(cycle: Cycle, window: CycleWindow): CycleWindow = windowAt(cycle, window.start.minusMillis(1))

    private fun renewal(cycle: Cycle.MonthlyOnDay, month: YearMonth): Instant =
        month.atDay(minOf(cycle.dayOfMonth, month.lengthOfMonth())).atTime(cycle.time).atZone(cycle.zone).toInstant()

    private fun monthly(cycle: Cycle.MonthlyOnDay, at: Instant): CycleWindow {
        val month = YearMonth.from(at.atZone(cycle.zone))
        val thisRenewal = renewal(cycle, month)
        return if (at >= thisRenewal) {
            CycleWindow(thisRenewal, renewal(cycle, month.plusMonths(1)))
        } else {
            CycleWindow(renewal(cycle, month.minusMonths(1)), thisRenewal)
        }
    }

    private fun everyNDays(cycle: Cycle.EveryNDays, at: Instant): CycleWindow {
        fun start(step: Long): Instant =
            cycle.anchor.plusDays(step * cycle.days).atTime(cycle.time).atZone(cycle.zone).toInstant()
        val localDate = at.atZone(cycle.zone).toLocalDate()
        var step = Math.floorDiv(ChronoUnit.DAYS.between(cycle.anchor, localDate), cycle.days.toLong())
        if (start(step) > at) step -= 1   // right local date, but that day's renewal time has not come yet
        return CycleWindow(start(step), start(step + 1))
    }
}
