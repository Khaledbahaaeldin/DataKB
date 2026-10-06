package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController

internal sealed interface DeepLinkTarget {
    data class Tab(val destination: Destination) : DeepLinkTarget
    data object Settings : DeepLinkTarget
    data class AppDetail(val packageName: String) : DeepLinkTarget
    data class PlanEditor(val planId: Long) : DeepLinkTarget

    companion object {
        fun parse(uri: Uri): DeepLinkTarget? {
            if (!uri.scheme.equals(DeepLinks.SCHEME, ignoreCase = true)) return null
            val host = uri.host?.takeIf { it.isNotEmpty() }?.lowercase() ?: return null
            val segments = uri.pathSegments
            return when (host) {
                "home" -> if (segments.isEmpty()) Tab(Destination.Home) else null
                "apps" -> when (segments.size) {
                    0 -> Tab(Destination.Apps)
                    1 -> segments[0].takeIf { it.isNotEmpty() }?.let { AppDetail(it) }
                    else -> null
                }
                "plans" -> when (segments.size) {
                    0 -> Tab(Destination.Plans)
                    1 -> {
                        val id = segments[0].toLongOrNull()
                        if (id != null && id >= 0L) PlanEditor(id) else null
                    }
                    else -> null
                }
                "lens" -> if (segments.isEmpty()) Tab(Destination.Lens) else null
                "settings" -> if (segments.isEmpty()) Settings else null
                else -> null
            }
        }
    }
}

/** Opens an emberbyte:// link ON TOP of the current back stack (the user can go Back). Returns false when the link is not ours or has no destination. */
internal fun NavController.openDeepLink(intent: Intent, allowed: () -> Boolean = { true }): Boolean {
    val uri = intent.data ?: return false
    if (!allowed()) return false
    val target = DeepLinkTarget.parse(uri) ?: return false
    val current = currentBackStackEntry
    return try {
        when (target) {
            is DeepLinkTarget.Tab -> navigate(target.destination.route) {
                popUpTo(Destination.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            DeepLinkTarget.Settings -> if (current?.destination?.route != Routes.SETTINGS) navigate(uri)
            is DeepLinkTarget.AppDetail ->
                if (!(current?.destination?.route == Routes.APP_DETAIL && current.arguments?.getString("packageName") == target.packageName)) navigate(uri)
            is DeepLinkTarget.PlanEditor ->
                if (!(current?.destination?.route == Routes.PLAN_EDITOR && current.arguments?.getLong("planId") == target.planId)) navigate(uri)
        }
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
