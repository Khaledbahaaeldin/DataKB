package io.github.khaledbahaaeldin.emberbyte.nav

import android.Manifest
import android.content.SharedPreferences

class AskedTracker(private val prefs: SharedPreferences) {
    fun markResult(permission: String) {
        val key = keyFor(permission) ?: return
        prefs.edit().putBoolean(key, true).apply()
    }

    fun asked(permission: String): Boolean {
        val key = keyFor(permission) ?: return false
        return prefs.getBoolean(key, false)
    }

    companion object {
        fun keyFor(permission: String): String? = when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "notif_asked"
            Manifest.permission.READ_PHONE_STATE -> "phone_asked"
            else -> null
        }
    }
}
