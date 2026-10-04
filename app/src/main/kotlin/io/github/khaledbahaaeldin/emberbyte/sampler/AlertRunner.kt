package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits

class AlertRunner(
    private val enabled: () -> Boolean,
    private val evaluate: suspend () -> List<AlertNotice>,
    private val post: (AlertNotice) -> Unit,
) {
    /** Skips when notifications are off, so a threshold is never marked as fired without being seen (contract 10.3). */
    suspend fun run() {
        if (!enabled()) return
        evaluate().forEach(post)
    }

    companion object {
        fun create(context: Context, graph: AppGraph): AlertRunner {
            val app = context.applicationContext
            return AlertRunner(
                enabled = { NotificationManagerCompat.from(app).areNotificationsEnabled() },
                evaluate = { graph.alertEvaluator.evaluate() },
                post = { notice ->
                    val units = (graph.settingsState.value?.unitSystem ?: UnitSystem.DECIMAL).toByteUnits()
                    AlertNotifier.post(app, notice, units)
                },
            )
        }
    }
}
