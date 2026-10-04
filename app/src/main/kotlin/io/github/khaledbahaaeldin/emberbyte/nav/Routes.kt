package io.github.khaledbahaaeldin.emberbyte.nav

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val APP_DETAIL = "apps/{packageName}"
    const val PLAN_EDITOR = "plans/{planId}"
    fun appDetail(packageName: String): String = "apps/" + Uri.encode(packageName)
    fun planEditor(planId: Long): String = "plans/$planId"
}
