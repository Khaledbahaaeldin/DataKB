package io.github.khaledbahaaeldin.emberbyte.engine.model

enum class BudgetPeriod { DAILY, CYCLE }

data class AppBudget(
    val id: Long,
    val packageName: String,
    val limitBytes: Long,
    val period: BudgetPeriod,
    val planId: Long?,
    val warnAtPercent: Int = 80,
    val enabled: Boolean = true,
)

enum class BudgetState { OK, WARNING, EXCEEDED }

data class BudgetStatus(val budget: AppBudget, val usedBytes: Long, val state: BudgetState)
