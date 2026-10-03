package io.github.khaledbahaaeldin.emberbyte.nav

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.launch

/** What the permission buttons do. The permission state is re-read in `MainActivity.onResume`. */
class PermissionActions(
    val openUsageAccess: () -> Unit,
    val requestNotifications: () -> Unit,
) {
    fun onPrompt(id: String) {
        when (id) {
            "usage_access" -> openUsageAccess()
            "notifications" -> requestNotifications()
        }
    }
}

@Composable
fun rememberPermissionActions(graph: AppGraph): PermissionActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { graph.permissions.recheck() }
    }
    return remember(context, launcher) {
        PermissionActions(
            openUsageAccess = { context.startActivityOrIgnore(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)) },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !asked) {
                    asked = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Already asked (the system will not show the dialog again) or below Android 13: open the settings.
                    context.startActivityOrIgnore(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )
    }
}

private fun Context.startActivityOrIgnore(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // The device has no such settings screen.
    }
}
