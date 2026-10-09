package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryBudgetStore : BudgetStore {
    private val budgets = MutableStateFlow<List<AppBudget>>(emptyList())
    private val states = MutableStateFlow<Map<Pair<Long, Instant>, BudgetState>>(emptyMap())
    private val budgetIds = AtomicLong(0)

    override fun observeBudgets(): Flow<List<AppBudget>> = budgets.map { it.sortedBy(AppBudget::id) }

    override suspend fun budgets(): List<AppBudget> = budgets.value.sortedBy(AppBudget::id)

    override suspend fun upsertBudget(budget: AppBudget): Long {
        val id = if (budget.id == 0L) budgetIds.incrementAndGet() else budget.id
        budgets.update { list -> list.filter { it.id != id } + budget.copy(id = id) }
        return id
    }

    override suspend fun deleteBudget(id: Long) {
        budgets.update { list -> list.filter { it.id != id } }
        states.update { map -> map.filterKeys { it.first != id } }
    }

    override suspend fun lastState(budgetId: Long, periodStart: Instant): BudgetState? =
        states.value[budgetId to periodStart]

    override suspend fun setState(budgetId: Long, periodStart: Instant, state: BudgetState) {
        states.update { it + ((budgetId to periodStart) to state) }
    }
}
