package io.github.khaledbahaaeldin.emberbyte.engine.budget

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId

object BudgetCalculator {
    fun state(budget: AppBudget, usedBytes: Long): BudgetState {
        if (budget.limitBytes <= 0L) return BudgetState.OK
        if (usedBytes > budget.limitBytes) return BudgetState.EXCEEDED
        val used100 = BigInteger.valueOf(usedBytes).multiply(BigInteger.valueOf(100L))
        val limitWarn = BigInteger.valueOf(budget.limitBytes).multiply(BigInteger.valueOf(budget.warnAtPercent.toLong()))
        return if (used100 >= limitWarn) BudgetState.WARNING else BudgetState.OK
    }

    fun period(budget: AppBudget, now: Instant, zone: ZoneId, planWindow: CycleWindow?): CycleWindow {
        val today = now.atZone(zone).toLocalDate()
        return when (budget.period) {
            BudgetPeriod.DAILY -> CycleWindow(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
            BudgetPeriod.CYCLE -> planWindow ?: today.withDayOfMonth(1).let {
                CycleWindow(it.atStartOfDay(zone).toInstant(), it.plusMonths(1).atStartOfDay(zone).toInstant())
            }
        }
    }
}
