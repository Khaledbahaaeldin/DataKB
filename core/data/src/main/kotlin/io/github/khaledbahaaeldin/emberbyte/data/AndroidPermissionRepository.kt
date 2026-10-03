package io.github.khaledbahaaeldin.emberbyte.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.khaledbahaaeldin.emberbyte.data.android.AppOpsUsageAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AndroidPermissionRepository(private val context: Context) : PermissionRepository {
    private val usageAccess = AppOpsUsageAccess(context)
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() {
        state.value = read()
    }

    private fun read() = PermissionState(
        usageAccess = usageAccess.isGranted(),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED,
        vpnConsentGranted = false, // Live Lens arrives in M5
    )
}
