package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        val graph = (app as EmberbyteApplication).graph
        CoroutineScope(Dispatchers.Default).launch {
            try {
                startIfOnboarded(app, graph)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Measuring starts at boot only for people who finished the first-run flow. */
        internal suspend fun startIfOnboarded(
            context: Context,
            graph: AppGraph,
            start: (Context) -> Boolean = ServiceStarter::start,
        ): Boolean {
            if (graph.onboardingCompleted.first { it != null } != true) return false
            return start(context)
        }
    }
}
