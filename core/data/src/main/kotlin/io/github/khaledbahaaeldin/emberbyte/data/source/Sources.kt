package io.github.khaledbahaaeldin.emberbyte.data.source

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class CounterSnapshot(
    val at: Instant,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val bootId: String,
)

/** Cumulative system traffic counters (mobile and all interfaces). Wi-Fi = total - mobile. */
interface CounterSource { fun read(): CounterSnapshot }

/** Kind of the current default network; null = offline or something that is neither Wi-Fi nor cellular. */
interface NetworkKindSource { val current: StateFlow<NetworkKind?> }

interface SubscriptionSource {
    /** Default data subscription id, or -1 when unknown. */
    fun defaultDataSubscriptionId(): Int
}

data class UidUsage(val uid: Int, val rxBytes: Long, val txBytes: Long)

interface NetworkStatsSource {
    /** Bytes per UID for [network] in [from, to). Empty when Usage Access is missing. */
    suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage>
}

interface AppInfoSource { fun resolve(uid: Int): AppMeta? }

interface UsageAccess { fun isGranted(): Boolean }
