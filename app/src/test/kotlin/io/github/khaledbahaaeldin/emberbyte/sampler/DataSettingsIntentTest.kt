package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataSettingsIntentTest {
    @Test
    fun fallback_to_app_details_when_no_data_restriction_handler() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = DataSettingsIntent.forPackage(context, "com.video")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.parse("package:com.video"), intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    @Suppress("DEPRECATION")
    fun returns_ignore_background_data_restrictions_when_handler_available() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val restrictionIntent = Intent(
            Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
            Uri.parse("package:com.video"),
        )
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "DataSettingsActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.settings"
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(restrictionIntent, resolveInfo)

        val intent = DataSettingsIntent.forPackage(context, "com.video")
        assertEquals(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, intent.action)
        assertEquals(Uri.parse("package:com.video"), intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
