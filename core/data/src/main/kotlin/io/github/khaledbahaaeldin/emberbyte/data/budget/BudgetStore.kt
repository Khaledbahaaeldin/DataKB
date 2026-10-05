package io.github.khaledbahaaeldin.emberbyte.data.budget

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabases
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface BudgetStore {
    fun observeBudgets(): Flow<List<AppBudget>>
    suspend fun budgets(): List<AppBudget>
    suspend fun upsertBudget(budget: AppBudget): Long
    suspend fun deleteBudget(id: Long)
    suspend fun lastState(budgetId: Long, periodStart: Instant): BudgetState?
    suspend fun setState(budgetId: Long, periodStart: Instant, state: BudgetState)
}

object BudgetStores {
    fun create(context: Context): BudgetStore = RoomBudgetStore(EmberbyteDatabases.get(context))
}
