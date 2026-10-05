package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits

class BudgetRunner(
    private val enabled: () -> Boolean,
    private val evaluate: suspend () -> List<BudgetNotice>,
    private val post: (BudgetNotice) -> Unit,
) {
    suspend fun run() {
        if (!enabled()) return
        evaluate().forEach(post)
    }

    companion object {
        fun create(context: Context, graph: AppGraph): BudgetRunner {
            val app = context.applicationContext
            return BudgetRunner(
                enabled = { AlertGate.isEnabled(app) },
                evaluate = { graph.budgetEvaluator.evaluate() },
                post = { n ->
                    val units = (graph.settingsState.value?.unitSystem ?: UnitSystem.DECIMAL).toByteUnits()
                    BudgetNotifier.post(app, n, units)
                },
            )
        }
    }
}
