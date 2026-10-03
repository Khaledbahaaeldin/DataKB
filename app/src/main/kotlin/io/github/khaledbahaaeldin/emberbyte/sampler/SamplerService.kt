package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val TAG = "SamplerService"
private const val NOTIFICATION_ID = 1001
private const val CATCH_UP_INTERVAL_MS = 5 * 60 * 1000L
private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 60_000L

/** Foreground service (type specialUse) that keeps the sampler, the catch-up loop and the live notification running. */
class SamplerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var lastText: LiveNotificationText? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        running.set(true)
        StatusNotifications.cancelPaused(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as EmberbyteApplication).graph
        val builder = LiveNotificationBuilder(this)
        // Re-post the last known text, never a zero placeholder, when Android calls us again while the work is already running.
        val text = lastText ?: LiveNotificationText(getString(R.string.notification_measuring), null)
        val notification = builder.build(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (job?.isActive != true) job = scope.launch { supervise(graph, builder) }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun supervise(graph: AppGraph, builder: LiveNotificationBuilder) = supervisorScope {
        launch { retrying("sampler") { graph.sampler.run() } }
        launch {
            retrying("catch-up") {
                while (true) {
                    graph.catchUp.run()
                    graph.prune()
                    delay(CATCH_UP_INTERVAL_MS)
                }
            }
        }
        launch { retrying("notification") { updateNotification(graph, builder) } }
    }

    private suspend fun retrying(name: String, block: suspend () -> Unit) =
        retryWithBackoff(
            initialMillis = INITIAL_BACKOFF_MS,
            maxMillis = MAX_BACKOFF_MS,
            onError = { Log.e(TAG, "$name failed; retrying", it) },
            block = block,
        )

    private suspend fun updateNotification(graph: AppGraph, builder: LiveNotificationBuilder) {
        val manager = getSystemService(NotificationManager::class.java)
        val speeds = graph.usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }
        combine(speeds, graph.usage.observeToday(), graph.settings.observe()) { speed, today, settings ->
            if (settings.liveNotificationEnabled) {
                liveNotificationText(
                    todayBytes = today.totalBytes,
                    rxBps = speed?.rxBps ?: 0L,
                    units = settings.unitSystem.toByteUnits(),
                    showSpeed = settings.notificationShowsSpeed,
                    offline = speed != null && speed.network == null,
                )
            } else {
                // A foreground service must show a notification; keep it minimal when the user turned this off.
                LiveNotificationText(getString(R.string.notification_measuring), null)
            }
        }.distinctUntilChanged().conflate().collect { text ->
            lastText = text
            manager.notify(NOTIFICATION_ID, builder.build(text))
            delay(1_000L) // at most one update per second
        }
    }

    companion object {
        /** True while a [SamplerService] instance exists in this process. */
        val running = AtomicBoolean(false)
    }
}
