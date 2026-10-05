package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.data.db.AppBudgetEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.BudgetStateEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun AppBudget.toEntity() = AppBudgetEntity(
    id = id,
    packageName = packageName,
    limitBytes = limitBytes,
    period = period.name,
    planId = planId,
    warnAtPercent = warnAtPercent,
    enabled = enabled,
)

private fun AppBudgetEntity.toDomainOrNull(): AppBudget? = runCatching {
    AppBudget(
        id = id,
        packageName = packageName,
        limitBytes = limitBytes,
        period = BudgetPeriod.valueOf(period),
        planId = planId,
        warnAtPercent = warnAtPercent,
        enabled = enabled,
    )
}.getOrNull()

class RoomBudgetStore(db: EmberbyteDatabase) : BudgetStore {
    private val dao = db.insightDao()

    override fun observeBudgets(): Flow<List<AppBudget>> =
        dao.observeBudgets().map { list -> list.mapNotNull { it.toDomainOrNull() } }

    override suspend fun budgets(): List<AppBudget> =
        dao.budgets().mapNotNull { it.toDomainOrNull() }

    override suspend fun upsertBudget(budget: AppBudget): Long =
        dao.putBudget(budget.toEntity())

    override suspend fun deleteBudget(id: Long) =
        dao.deleteBudget(id)

    override suspend fun lastState(budgetId: Long, periodStart: Instant): BudgetState? =
        dao.lastState(budgetId, periodStart.toEpochMilli())?.let { str ->
            runCatching { BudgetState.valueOf(str) }.getOrNull()
        }

    override suspend fun setState(budgetId: Long, periodStart: Instant, state: BudgetState) =
        dao.putState(BudgetStateEntity(budgetId, periodStart.toEpochMilli(), state.name))
}
