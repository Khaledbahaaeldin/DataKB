package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeStatuses(): Flow<List<BudgetStatus>>
    suspend fun upsert(budget: AppBudget): Outcome<Long>
    suspend fun delete(id: Long): Outcome<Unit>
}
