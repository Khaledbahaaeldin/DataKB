package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind

/** Subscription id with the most mobile bytes among [minuteRows] (rows of one hour); -1 when there is none. */
fun dominantSubscriptionId(minuteRows: List<MinuteTotal>): Int {
    val bytesBySubscription = HashMap<Int, Long>()
    for (row in minuteRows) {
        if (row.network != NetworkKind.MOBILE) continue
        bytesBySubscription.merge(row.subscriptionId, row.totalBytes) { a, b -> a + b }
    }
    return bytesBySubscription.maxByOrNull { it.value }?.key ?: -1
}
