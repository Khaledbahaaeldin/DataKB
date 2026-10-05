package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BudgetNotice(val budget: AppBudget, val label: String, val usedBytes: Long, val state: BudgetState)

class BudgetEvaluator(
    private val statuses: suspend () -> List<BudgetPeriodStatus>,
    private val store: BudgetStore,
) {
    private val mutex = Mutex()

    suspend fun evaluate(): List<BudgetNotice> = mutex.withLock {
        val notices = ArrayList<BudgetNotice>()
        for (entry in statuses()) {
            val budget = entry.status.budget
            if (!budget.enabled) continue
            val last = store.lastState(budget.id, entry.periodStart) ?: BudgetState.OK
            val state = entry.status.state
            if (state.ordinal > last.ordinal) notices += BudgetNotice(budget, entry.label, entry.status.usedBytes, state)
            if (state != last) store.setState(budget.id, entry.periodStart, state)
        }
        notices
    }
}
