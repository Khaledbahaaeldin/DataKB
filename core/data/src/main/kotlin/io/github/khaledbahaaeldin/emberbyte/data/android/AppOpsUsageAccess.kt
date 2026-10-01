package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess

class AppOpsUsageAccess(private val context: Context) : UsageAccess {
    override fun isGranted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
