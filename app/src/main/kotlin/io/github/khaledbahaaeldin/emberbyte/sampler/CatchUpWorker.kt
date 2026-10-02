package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Pulls per-app hourly usage even when the sampler service is not running, and tries to restart the service. */
class CatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as EmberbyteApplication).graph
        graph.catchUp.run()
        graph.prune()
        if (graph.onboarding.observeCompleted().first { it != null } == true) {
            ServiceStarter.start(applicationContext)
        }
        return Result.success()
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
