package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

object DataSettingsIntent {
    fun forPackage(context: Context, packageName: String): Intent {
        val target = Intent(
            Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
            "package:$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return if (target.resolveActivity(context.packageManager) != null) {
            target
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
