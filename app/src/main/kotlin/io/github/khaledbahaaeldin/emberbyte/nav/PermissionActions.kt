package io.github.khaledbahaaeldin.emberbyte.nav

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.launch

/** What the permission buttons do. The permission state is re-read in `MainActivity.onResume`. */
class PermissionActions(
    val openUsageAccess: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestPhoneState: () -> Unit = {},
) {
    fun onPrompt(id: String) {
        when (id) {
            "usage_access" -> openUsageAccess()
            "notifications" -> requestNotifications()
            "phone_state" -> requestPhoneState()
        }
    }
}

@Composable
fun rememberPermissionActions(graph: AppGraph): PermissionActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findComponentActivity() }
    val prefs = remember(context) { context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { graph.permissions.recheck() }
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { graph.permissions.recheck() }
    }

    return remember(context, launcher, phoneLauncher, activity) {
        PermissionActions(
            openUsageAccess = { context.startActivityOrIgnore(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)) },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) {
                    val asked = prefs.getBoolean("notif_asked", false)
                    val showRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                    when (PermissionRequestPolicy.decide(asked, showRationale)) {
                        PermissionDecision.Launch -> {
                            prefs.edit().putBoolean("notif_asked", true).apply()
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        PermissionDecision.OpenSettings -> {
                            context.startActivityOrIgnore(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }
                } else {
                    context.startActivityOrIgnore(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
            requestPhoneState = {
                val asked = prefs.getBoolean("phone_asked", false)
                val showRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_PHONE_STATE)
                when (PermissionRequestPolicy.decide(asked, showRationale)) {
                    PermissionDecision.Launch -> {
                        prefs.edit().putBoolean("phone_asked", true).apply()
                        phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                    PermissionDecision.OpenSettings -> {
                        context.startActivityOrIgnore(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }
            },
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun Context.startActivityOrIgnore(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // The device has no such settings screen.
    }
}
