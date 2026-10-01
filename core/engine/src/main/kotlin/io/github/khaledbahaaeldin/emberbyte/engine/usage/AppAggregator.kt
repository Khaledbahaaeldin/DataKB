package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter

private class Totals(var mobile: Long = 0L, var wifi: Long = 0L)

object AppAggregator {
    fun aggregate(
        hourlyRows: List<HourlyUsage>,
        meta: Map<Int, AppMeta>,
        filter: UsageFilter = UsageFilter(),
        sort: AppSort = AppSort.BYTES_DESC,
    ): List<AppUsage> {
        val byUid = LinkedHashMap<Int, Totals>()
        for (row in hourlyRows) {
            if (filter.network != null && row.network != filter.network) continue
            if (filter.subscriptionId != null && row.subscriptionId != filter.subscriptionId) continue
            val totals = byUid.getOrPut(row.uid) { Totals() }
            if (row.network == NetworkKind.MOBILE) totals.mobile += row.totalBytes else totals.wifi += row.totalBytes
        }
        val apps = byUid.entries
            .filter { it.value.mobile + it.value.wifi > 0L }
            .map { (uid, totals) ->
                val info = meta[uid]
                AppUsage(
                    packageName = info?.packageName ?: "uid:$uid",
                    label = info?.label ?: "UID $uid",
                    uid = uid,
                    mobileBytes = totals.mobile,
                    wifiBytes = totals.wifi,
                    screenTimeMs = null,
                )
            }
        return when (sort) {
            AppSort.BYTES_DESC, AppSort.SCREEN_TIME_DESC -> apps.sortedByDescending { it.totalBytes }
            AppSort.BYTES_ASC -> apps.sortedBy { it.totalBytes }
            AppSort.NAME -> apps.sortedBy { it.label.lowercase() }
        }
    }
}
