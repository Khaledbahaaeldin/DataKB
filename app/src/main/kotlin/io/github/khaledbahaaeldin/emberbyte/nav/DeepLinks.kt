package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import io.github.khaledbahaaeldin.emberbyte.MainActivity

object DeepLinks {
    const val SCHEME = "emberbyte"
    const val HOME = "emberbyte://home"
    const val APPS = "emberbyte://apps"
    const val PLANS = "emberbyte://plans"
    const val LENS = "emberbyte://lens"
    const val SETTINGS = "emberbyte://settings"

    fun app(packageName: String): String = "emberbyte://apps/" + Uri.encode(packageName)
    fun plan(planId: Long): String = "emberbyte://plans/$planId"

    /** The intent notifications and widgets fire: explicit component, so nothing else can intercept it. */
    fun intent(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The `{placeholder}` forms that `navDeepLink` needs. */
object DeepLinkPatterns {
    const val HOME = DeepLinks.HOME
    const val APPS = DeepLinks.APPS
    const val APP_DETAIL = "emberbyte://apps/{packageName}"
    const val PLANS = DeepLinks.PLANS
    const val PLAN_EDITOR = "emberbyte://plans/{planId}"
    const val LENS = DeepLinks.LENS
    const val SETTINGS = DeepLinks.SETTINGS
}
