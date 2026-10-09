package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BudgetNotice(
    val budget: AppBudget,
    val label: String,
    val usedBytes: Long,
    val state: BudgetState,
    val periodLabel: String = "today",
)

data class BudgetEvaluation(val notices: List<BudgetNotice>, val cleared: List<Long>)

class BudgetEvaluator(
    private val statuses: suspend () -> List<BudgetPeriodStatus>,
    private val store: BudgetStore,
) {
    private val mutex = Mutex()

    suspend fun evaluate(): BudgetEvaluation = mutex.withLock {
        val notices = ArrayList<BudgetNotice>()
        val cleared = ArrayList<Long>()
        for (entry in statuses()) {
            val budget = entry.status.budget
            if (!budget.enabled) continue
            val last = store.lastState(budget.id, entry.periodStart) ?: BudgetState.OK
            val state = entry.status.state
            if (state.ordinal > last.ordinal) notices += BudgetNotice(budget, entry.label, entry.status.usedBytes, state, entry.periodLabel)
            if (state.ordinal < last.ordinal) cleared += budget.id
            if (state != last) store.setState(budget.id, entry.periodStart, state)
        }
        BudgetEvaluation(notices, cleared)
    }
}
