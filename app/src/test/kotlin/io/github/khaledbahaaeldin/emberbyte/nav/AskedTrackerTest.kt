package io.github.khaledbahaaeldin.emberbyte.nav

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AskedTrackerTest {

    @Test
    fun asked_is_false_initially_and_true_after_mark_result() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_permissions", Context.MODE_PRIVATE)
        val tracker = AskedTracker(prefs)

        assertFalse(tracker.asked(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(tracker.asked(Manifest.permission.READ_PHONE_STATE))

        tracker.markResult(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(tracker.asked(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(tracker.asked(Manifest.permission.READ_PHONE_STATE))

        tracker.markResult(Manifest.permission.READ_PHONE_STATE)
        assertTrue(tracker.asked(Manifest.permission.READ_PHONE_STATE))
    }

    @Test
    fun unknown_permission_is_safely_ignored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_permissions_unknown", Context.MODE_PRIVATE)
        val tracker = AskedTracker(prefs)

        assertFalse(tracker.asked("android.permission.UNKNOWN"))
        tracker.markResult("android.permission.UNKNOWN")
        assertFalse(tracker.asked("android.permission.UNKNOWN"))
    }
}
