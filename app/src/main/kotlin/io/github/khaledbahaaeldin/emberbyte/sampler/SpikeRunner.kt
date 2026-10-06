package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits

import kotlinx.coroutines.flow.first

class SpikeRunner(
    private val evaluate: suspend () -> List<SpikeEvent>,
    private val select: suspend (List<SpikeEvent>) -> List<SpikeEvent>,
    private val alertsEnabled: suspend () -> Boolean,
    private val post: (SpikeEvent) -> Unit,
) {
    /** Events are ALWAYS stored; they are only announced when the setting, the app notifications and the `spikes` channel allow it. */
    suspend fun run() {
        val events = evaluate()
        if (events.isEmpty() || !alertsEnabled()) return
        select(events).forEach(post)
    }

    companion object {
        fun create(
            context: Context,
            graph: AppGraph,
            spikeAlertsEnabled: suspend () -> Boolean = { graph.settings.observe().first().spikeAlertsEnabled },
        ): SpikeRunner {
            val app = context.applicationContext
            return SpikeRunner(
                evaluate = { graph.spikeEvaluator.evaluate() },
                select = { events -> graph.spikeEvaluator.selectForNotification(events) },
                alertsEnabled = {
                    spikeAlertsEnabled() &&
                        AlertGate.isEnabled(app, NotificationChannels.SPIKES)
                },
                post = { event ->
                    val pm = app.packageManager
                    val label = try {
                        val appInfo = pm.getApplicationInfo(event.packageName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        event.packageName
                    }
                    val units = (graph.settingsState.value?.unitSystem ?: UnitSystem.DECIMAL).toByteUnits()
                    SpikeNotifier.post(app, event, label, units)
                },
            )
        }
    }
}
