package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.navOptions

/** Opens an emberbyte:// link ON TOP of the current back stack (the user can go Back). Returns false when the link is not ours or has no destination. */
internal fun NavController.openDeepLink(intent: Intent): Boolean {
    val uri = intent.data ?: return false
    if (uri.scheme != DeepLinks.SCHEME) return false
    return try {
        navigate(uri, navOptions { launchSingleTop = true })
        true
    } catch (_: IllegalArgumentException) {
        false // no destination matches (unknown host, planId that is not a number)
    }
}
