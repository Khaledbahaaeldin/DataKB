package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkStatsManagerSource(context: Context) : NetworkStatsSource {
    private val manager = context.getSystemService(NetworkStatsManager::class.java)

    override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> =
        withContext(Dispatchers.IO) {
            val type = if (network == NetworkKind.MOBILE) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
            val totals = HashMap<Int, LongArray>()
            try {
                // subscriberId = null: on Android 10+ an unprivileged app queries all subscriptions together.
                val stats = manager?.querySummary(type, null, from.toEpochMilli(), to.toEpochMilli())
                    ?: return@withContext emptyList()
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val pair = totals.getOrPut(bucket.uid) { LongArray(2) }
                        pair[0] += bucket.rxBytes
                        pair[1] += bucket.txBytes
                    }
                } finally {
                    stats.close()
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }
            totals.map { (uid, pair) -> UidUsage(uid, pair[0], pair[1]) }
        }
}
