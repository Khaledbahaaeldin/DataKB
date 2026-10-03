package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Pulls per-app hourly usage even when the sampler service is not running, and tries to restart the service. */
class CatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as EmberbyteApplication).graph
        graph.catchUp.run()
        graph.prune()
        CatchUpRecovery.afterCatchUp(applicationContext, graph)
        return Result.success()
    }
}

internal object CatchUpRecovery {
    /**
     * If measuring should be running but is not: try to restart it; when Android refuses (background start on Android 12+), tell the
     * user with a "Measurement paused" notification instead of failing silently.
     */
    suspend fun afterCatchUp(
        context: Context,
        graph: AppGraph,
        serviceRunning: () -> Boolean = { SamplerService.running.get() },
        tryStart: (Context) -> Boolean = ServiceStarter::start,
    ) {
        if (graph.onboardingCompleted.first { it != null } != true) return
        if (serviceRunning()) return
        if (!tryStart(context)) StatusNotifications.showPaused(context)
    }
}

object CatchUpScheduler {
    private const val NAME = "catchup"

    fun schedule(context: Context) {
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val request = PeriodicWorkRequestBuilder<CatchUpWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
