package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import io.github.khaledbahaaeldin.emberbyte.engine.usage.dominantSubscriptionId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val CHECKPOINT_CATCHUP_HOURLY = "catchup.hourly"

sealed interface CatchUpResult {
    data class Done(val hoursUpdated: Int) : CatchUpResult
    data object MissingUsageAccess : CatchUpResult
}

private val WINDOW: Duration = Duration.ofSeconds(SeriesBuilder.WINDOW_SECONDS)

/**
 * Pulls per-app usage from the system's network stats, ONE 2-hour window at a time (the platform's bucket size, so nothing is
 * interpolated; the open window is clamped to now), and atomically replaces each window's rows. `hoursUpdated` counts windows.
 * Runs are serialised: the service loop, the worker and a manual refresh can all call [run].
 */
class HourlyCatchUp(
    private val source: NetworkStatsSource,
    private val store: UsageStore,
    private val appInfo: AppInfoSource,
    private val access: UsageAccess,
    private val clock: Clock,
    private val maxBackfill: Duration = Duration.ofDays(7),
) {
    private val lock = Mutex()

    suspend fun run(): CatchUpResult = lock.withLock { runLocked() }

    private suspend fun runLocked(): CatchUpResult {
        if (!access.isGranted()) return CatchUpResult.MissingUsageAccess

        val now = clock.instant()
        val currentWindow = SeriesBuilder.windowStart(now)
        val floor = SeriesBuilder.windowStart(now.minus(maxBackfill))
        val checkpoint = store.loadCheckpoint(CHECKPOINT_CATCHUP_HOURLY)
        val start = checkpoint
            ?.let { SeriesBuilder.windowStart(it.at).minus(WINDOW) } // the previous window may have closed since the last run
            ?.let { if (it < floor) floor else it }
            ?: floor

        val knownUids = store.appMeta().map { it.uid }.toHashSet()
        var window = start
        var updated = 0
        while (window <= currentWindow) {
            val end = window.plus(WINDOW)
            val queryEnd: Instant = if (end < now) end else now
            val minuteRows = store.minuteRows(window, end)
            val rows = ArrayList<HourlyUsage>()
            for (network in NetworkKind.entries) {
                val subscription = if (network == NetworkKind.MOBILE) dominantSubscriptionId(minuteRows) else -1
                for (usage in source.query(network, window, queryEnd)) {
                    if (usage.rxBytes + usage.txBytes > 0L) {
                        rows += HourlyUsage(window, usage.uid, network, subscription, usage.rxBytes, usage.txBytes)
                    }
                }
            }
            store.replaceHourly(window, rows)
            for (uid in rows.map { it.uid }.distinct()) {
                if (knownUids.add(uid)) appInfo.resolve(uid)?.let { store.upsertAppMeta(listOf(it)) }
            }
            updated++
            window = end
        }
        store.saveCheckpoint(CHECKPOINT_CATCHUP_HOURLY, CounterReading(currentWindow, 0L, 0L, "catchup"))
        return CatchUpResult.Done(updated)
    }
}
