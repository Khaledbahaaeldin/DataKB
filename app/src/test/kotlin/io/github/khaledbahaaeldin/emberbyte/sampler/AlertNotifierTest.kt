package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertNotifierTest {
    @Test fun post_delivers_notification_on_alerts_channel_with_deep_link() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val notice = AlertNotice(
            planId = 7,
            planName = "Main",
            thresholdPercent = 80,
            usedBytes = 8_100_000_000L,
            effectiveCapBytes = 10_000_000_000L,
            remainingBytes = 1_900_000_000L,
            runOutExpected = null,
        )

        AlertNotifier.post(app, notice, ByteUnits.DECIMAL)

        val manager = app.getSystemService(NotificationManager::class.java)
        val shadowManager = shadowOf(manager)
        val notification = shadowManager.getNotification(AlertNotifier.TAG, 7)
        assertNotNull(notification)
        assertEquals("Main: 80% of your data used", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(NotificationChannels.ALERTS, notification.channelId)
        assertEquals("emberbyte://plans/7", shadowOf(notification.contentIntent).savedIntent.data.toString())
    }
}
