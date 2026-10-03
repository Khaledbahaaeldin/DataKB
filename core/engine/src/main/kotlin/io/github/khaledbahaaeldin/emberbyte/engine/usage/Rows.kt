package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant

data class MinuteTotal(
    val minuteStart: Instant,
    val network: NetworkKind,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class HourlyUsage(
    val hourStart: Instant,
    val uid: Int,
    val network: NetworkKind,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class AppMeta(val uid: Int, val packageName: String, val label: String)
