package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetEvaluation
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits

class BudgetRunner(
    private val enabled: () -> Boolean,
    private val evaluate: suspend () -> BudgetEvaluation,
    private val post: (BudgetNotice) -> Unit,
    private val cancel: (Long) -> Unit = {},
) {
    suspend fun run() {
        if (!enabled()) return
        val eval = evaluate()
        eval.notices.forEach(post)
        eval.cleared.forEach(cancel)
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
                cancel = { id ->
                    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.cancel(BudgetNotifier.TAG, id.toInt())
                },
            )
        }
    }
}
