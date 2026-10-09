package io.github.khaledbahaaeldin.emberbyte.data.android

import android.content.Context
import android.content.pm.PackageManager
import io.github.khaledbahaaeldin.emberbyte.data.backup.PackageResolver
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta

class PackageManagerPackageResolver(private val context: Context) : PackageResolver {
    override fun resolve(packageName: String): AppMeta? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        AppMeta(info.uid, packageName, info.loadLabel(pm).toString())
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
