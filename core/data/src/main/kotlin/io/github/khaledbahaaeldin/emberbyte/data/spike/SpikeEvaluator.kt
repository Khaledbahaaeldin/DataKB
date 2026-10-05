package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.plan.UsageSlice
import io.github.khaledbahaaeldin.emberbyte.engine.spike.SpikeDetector
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpikeEvaluator(
    private val usage: UsageStore,
    private val store: SpikeStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val window = Duration.ofSeconds(SeriesBuilder.WINDOW_SECONDS)

    suspend fun evaluate(): List<SpikeEvent> = mutex.withLock {
        val now = clock.instant()
        val current = SeriesBuilder.windowStart(now)
        val windows = listOf(current, current.minus(window), current.minus(window.multipliedBy(2)))
        val historyStart = windows.last().minus(Duration.ofDays(HISTORY_DAYS)).minus(window)
        val allRows = usage.hourlyRows(historyStart, current.plus(window))
        val packageByUid = usage.appMeta().associate { it.uid to it.packageName }
        val firstSeen = HashMap<String, Instant>()
        val mobileBytes = HashMap<Pair<String, Instant>, Long>()
        for (row in allRows) {
            val pkg = packageByUid[row.uid] ?: continue
            firstSeen.merge(pkg, row.hourStart) { a, b -> minOf(a, b) }
            if (row.network == NetworkKind.MOBILE) mobileBytes.merge(pkg to row.hourStart, row.totalBytes) { a, b -> a + b }
        }
        val stored = ArrayList<SpikeEvent>()
        for (start in windows) {
            val candidates = mobileBytes.filterKeys { it.second == start }.map { (key, bytes) -> key.first to bytes }
            val eligible = candidates.filter { (pkg, _) -> firstSeen.getValue(pkg) <= start.minus(Duration.ofDays(MIN_HISTORY_DAYS)) }
            val slices = eligible.map { (pkg, bytes) -> UsageSlice(start, start.plus(window), pkg, bytes) }
            val baselines = eligible.associate { (pkg, _) -> pkg to median(pkg, start, mobileBytes) }
            for (event in SpikeDetector.detect(slices, baselines, now)) {
                if (store.insertIfAbsent(event)) stored += event
            }
        }
        stored
    }

    private fun median(pkg: String, start: Instant, bytes: Map<Pair<String, Instant>, Long>): Long {
        val values = LongArray(HISTORY_DAYS.toInt() * 12) { i -> bytes[pkg to start.minus(window.multipliedBy(i + 1L))] ?: 0L }
        values.sort()
        return (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    }

    suspend fun selectForNotification(events: List<SpikeEvent>): List<SpikeEvent> = mutex.withLock {
        val now = clock.instant()
        val chosen = ArrayList<SpikeEvent>()
        val handled = HashSet<String>()
        for (event in events) {
            if (!handled.add(event.packageName)) continue
            val last = store.lastNoticeAt(event.packageName)
            if (last != null && Duration.between(last, now) < Duration.ofHours(1)) continue
            store.markNotified(event.packageName, now)
            chosen += event
        }
        chosen
    }

    private companion object {
        const val HISTORY_DAYS = 14L
        const val MIN_HISTORY_DAYS = 3L
    }
}
