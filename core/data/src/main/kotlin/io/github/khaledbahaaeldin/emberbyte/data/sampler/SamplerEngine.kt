package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReconciler
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

const val NO_SUBSCRIPTION = -1
const val CHECKPOINT_SAMPLER_MOBILE = "sampler.mobile"
const val CHECKPOINT_SAMPLER_TOTAL = "sampler.total"
private const val GAP_THRESHOLD_MS = 90_000L
private const val EMA_ALPHA = 0.5

private data class MinuteKey(val minuteStart: Instant, val network: NetworkKind, val subscriptionId: Int)
private class Pending(var rx: Long = 0L, var tx: Long = 0L)

/**
 * Samples the system counters: smoothed live speed once per tick, and per-minute totals written to [store].
 * Not thread-safe: drive it from a single coroutine ([run]).
 */
class SamplerEngine(
    private val counters: CounterSource,
    private val network: NetworkKindSource,
    private val subscription: SubscriptionSource,
    private val store: UsageStore,
    private val tickMillis: Long = 1_000L,
) {
    private val _liveSpeed = MutableSharedFlow<LiveSpeed>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val liveSpeed: SharedFlow<LiveSpeed> = _liveSpeed.asSharedFlow()

    private var previousMobile: CounterReading? = null
    private var previousTotal: CounterReading? = null
    private var emaRx = 0.0
    private var emaTx = 0.0
    private var hasEma = false
    private var started = false
    private val runLock = Mutex()
    private val pending = LinkedHashMap<MinuteKey, Pending>()

    suspend fun start() {
        val snapshot = counters.read()
        val checkpoint = store.loadCheckpoint(CHECKPOINT_SAMPLER_TOTAL)
        if (checkpoint != null && Duration.between(checkpoint.at, snapshot.at).toMillis() > GAP_THRESHOLD_MS) {
            val reason = if (checkpoint.bootId != snapshot.bootId) GapReason.REBOOT else GapReason.SERVICE_KILLED
            store.insertGap(CoverageGap(checkpoint.at, snapshot.at, reason))
        }
        previousMobile = CounterReading(snapshot.at, snapshot.mobileRxBytes, snapshot.mobileTxBytes, snapshot.bootId)
        previousTotal = CounterReading(snapshot.at, snapshot.totalRxBytes, snapshot.totalTxBytes, snapshot.bootId)
        hasEma = false
        started = true
    }

    suspend fun tick() {
        check(started) { "call start() before tick()" }
        val snapshot = counters.read()
        val mobileNow = CounterReading(snapshot.at, snapshot.mobileRxBytes, snapshot.mobileTxBytes, snapshot.bootId)
        val totalNow = CounterReading(snapshot.at, snapshot.totalRxBytes, snapshot.totalTxBytes, snapshot.bootId)
        val before = previousTotal!!
        val elapsedMs = Duration.between(before.at, snapshot.at).toMillis()
        val mobileRaw = CounterReconciler.delta(previousMobile, mobileNow)
        val totalRaw = CounterReconciler.delta(before, totalNow)
        previousMobile = mobileNow
        previousTotal = totalNow
        if (elapsedMs <= 0L) return

        if (elapsedMs > GAP_THRESHOLD_MS) {
            // The device slept or the process was frozen: these bytes belong to hours the catch-up fills from system data.
            store.insertGap(CoverageGap(before.at, snapshot.at, GapReason.DEVICE_ASLEEP))
            hasEma = false
            return
        }
        if (mobileRaw.wasReset || totalRaw.wasReset) {
            store.insertGap(CoverageGap(before.at, snapshot.at, GapReason.COUNTER_RESET))
        }

        val kind = network.current.value
        // The all-interface total includes mobile, so mobile can never exceed it (a returning mobile counter must not create usage).
        val mobileRx = minOf(mobileRaw.rxBytes, totalRaw.rxBytes)
        val mobileTx = minOf(mobileRaw.txBytes, totalRaw.txBytes)
        // Wi-Fi is only derived while Wi-Fi is the default network; offline noise, tethering and cellular-side VPN bytes are not Wi-Fi.
        val wifiRx = if (kind == NetworkKind.WIFI) (totalRaw.rxBytes - mobileRx).coerceAtLeast(0L) else 0L
        val wifiTx = if (kind == NetworkKind.WIFI) (totalRaw.txBytes - mobileTx).coerceAtLeast(0L) else 0L

        val seconds = elapsedMs / 1000.0
        val instantRx = (mobileRx + wifiRx) / seconds
        val instantTx = (mobileTx + wifiTx) / seconds
        if (!hasEma) {
            emaRx = instantRx; emaTx = instantTx; hasEma = true
        } else {
            emaRx = EMA_ALPHA * instantRx + (1 - EMA_ALPHA) * emaRx
            emaTx = EMA_ALPHA * instantTx + (1 - EMA_ALPHA) * emaTx
        }
        _liveSpeed.tryEmit(LiveSpeed(emaRx.roundToLong(), emaTx.roundToLong(), kind, snapshot.at))

        val minute = snapshot.at.truncatedTo(ChronoUnit.MINUTES)
        add(MinuteKey(minute, NetworkKind.MOBILE, subscription.defaultDataSubscriptionId()), mobileRx, mobileTx)
        add(MinuteKey(minute, NetworkKind.WIFI, NO_SUBSCRIPTION), wifiRx, wifiTx)
        flush(olderThan = minute)
    }

    suspend fun stop() {
        if (!started) return
        flush(olderThan = null)
        previousMobile?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_MOBILE, it) }
        previousTotal?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_TOTAL, it) }
        started = false
    }

    /** Runs until cancelled; always flushes and saves checkpoints on the way out. Runs never overlap (they are serialised). */
    suspend fun run() = runLock.withLock {
        start()
        try {
            while (true) {
                tick()
                delay(tickMillis)
            }
        } finally {
            withContext(NonCancellable) { stop() }
        }
    }

    private fun add(key: MinuteKey, rx: Long, tx: Long) {
        val entry = pending.getOrPut(key) { Pending() }
        entry.rx += rx
        entry.tx += tx
    }

    /** Writes pending minutes older than [olderThan] (all of them when null) and refreshes the checkpoints. */
    private suspend fun flush(olderThan: Instant?) {
        val closed = pending.keys.filter { olderThan == null || it.minuteStart < olderThan }
        if (closed.isEmpty()) return
        for (key in closed) {
            val entry = pending.getValue(key)
            store.addMinute(MinuteTotal(key.minuteStart, key.network, key.subscriptionId, entry.rx, entry.tx))
            pending.remove(key)                                    // only after the write succeeded
        }
        previousMobile?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_MOBILE, it) }
        previousTotal?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_TOTAL, it) }
    }
}
