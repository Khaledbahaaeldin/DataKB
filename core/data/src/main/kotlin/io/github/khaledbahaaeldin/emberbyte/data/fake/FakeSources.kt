package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Replays [script] one snapshot per `read()`, then keeps returning the last one. */
class FakeCounterSource(private val script: List<CounterSnapshot>) : CounterSource {
    private var index = 0
    override fun read(): CounterSnapshot = script[minOf(index++, script.lastIndex)]
}

class FakeNetworkKindSource(initial: NetworkKind? = NetworkKind.MOBILE) : NetworkKindSource {
    private val state = MutableStateFlow(initial)
    override val current: StateFlow<NetworkKind?> = state
    fun set(kind: NetworkKind?) { state.value = kind }
}

class FakeSubscriptionSource(var id: Int = -1) : SubscriptionSource {
    override fun defaultDataSubscriptionId(): Int = id
}

/** Rows are looked up by (network, hour start == `from`). Every call is recorded in [queries]. */
class FakeNetworkStatsSource(
    private val data: Map<Pair<NetworkKind, Instant>, List<UidUsage>> = emptyMap(),
) : NetworkStatsSource {
    val queries = mutableListOf<Triple<NetworkKind, Instant, Instant>>()
    override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> {
        queries += Triple(network, from, to)
        return data[network to from] ?: emptyList()
    }
}

class FakeAppInfoSource(private val apps: Map<Int, AppMeta> = emptyMap()) : AppInfoSource {
    val resolved = mutableListOf<Int>()
    override fun resolve(uid: Int): AppMeta? {
        resolved += uid
        return apps[uid]
    }
}

class FakeUsageAccess(var granted: Boolean = true) : UsageAccess {
    override fun isGranted(): Boolean = granted
}
