package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyReconciler
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.dominantSubscriptionId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

const val CHECKPOINT_CATCHUP_HOURLY = "catchup.hourly"

sealed interface CatchUpResult {
    data class Done(val hoursUpdated: Int) : CatchUpResult
    data object MissingUsageAccess : CatchUpResult
}

/** Pulls per-app hourly usage from the system's network stats into the store. */
class HourlyCatchUp(
    private val source: NetworkStatsSource,
    private val store: UsageStore,
    private val appInfo: AppInfoSource,
    private val access: UsageAccess,
    private val clock: Clock,
    private val maxBackfill: Duration = Duration.ofDays(7),
) {
    suspend fun run(): CatchUpResult {
        if (!access.isGranted()) return CatchUpResult.MissingUsageAccess

        val now = clock.instant()
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val floor = currentHour.minus(maxBackfill)
        val checkpoint = store.loadCheckpoint(CHECKPOINT_CATCHUP_HOURLY)
        val start = checkpoint
            ?.let { it.at.truncatedTo(ChronoUnit.HOURS).minus(2, ChronoUnit.HOURS) }
            ?.let { if (it < floor) floor else it }
            ?: floor

        val knownUids = store.appMeta().map { it.uid }.toHashSet()
        var hour = start
        var updated = 0
        while (hour <= currentHour) {
            val end = hour.plus(1, ChronoUnit.HOURS)
            val minuteRows = store.minuteRows(hour, end)
            val rows = ArrayList<HourlyUsage>()
            for (network in NetworkKind.entries) {
                val subscription = if (network == NetworkKind.MOBILE) dominantSubscriptionId(minuteRows) else -1
                val queryEnd = if (end < now) end else now
                for (usage in source.query(network, hour, queryEnd)) {
                    if (usage.rxBytes + usage.txBytes > 0L) {
                        rows += HourlyUsage(hour, usage.uid, network, subscription, usage.rxBytes, usage.txBytes)
                    }
                }
            }
            val finalRows = if (end <= now) HourlyReconciler.scale(hour, rows, minuteRows) else rows
            if (finalRows.isNotEmpty()) store.upsertHourly(finalRows)
            for (uid in finalRows.map { it.uid }.distinct()) {
                if (knownUids.add(uid)) appInfo.resolve(uid)?.let { store.upsertAppMeta(listOf(it)) }
            }
            updated++
            hour = end
        }
        store.saveCheckpoint(CHECKPOINT_CATCHUP_HOURLY, CounterReading(currentHour, 0L, 0L, "catchup"))
        return CatchUpResult.Done(updated)
    }
}
