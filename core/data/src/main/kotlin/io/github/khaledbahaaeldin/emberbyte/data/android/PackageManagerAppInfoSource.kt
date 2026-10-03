package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.content.Context
import android.content.pm.PackageManager
import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta

internal fun specialUidMeta(uid: Int): AppMeta? = when (uid) {
    NetworkStats.Bucket.UID_REMOVED -> AppMeta(uid, "uid:removed", "Removed apps")
    NetworkStats.Bucket.UID_TETHERING -> AppMeta(uid, "uid:tethering", "Hotspot & tethering")
    0 -> AppMeta(uid, "uid:root", "Root")
    1000 -> AppMeta(uid, "android", "Android system")
    else -> null
}

class PackageManagerAppInfoSource(context: Context) : AppInfoSource {
    private val packageManager = context.packageManager

    override fun resolve(uid: Int): AppMeta? {
        specialUidMeta(uid)?.let { return it }
        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return null
        return try {
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            AppMeta(uid, packageName, label)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
