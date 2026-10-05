package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertGateTest {
    @Test fun alert_gate_is_enabled_by_default_after_ensure() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        NotificationChannels.ensure(app)
        assertTrue(AlertGate.isEnabled(app))
    }

    @Test fun alert_gate_is_disabled_when_channel_importance_is_none() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NotificationChannels.ALERTS, "Plan alerts", NotificationManager.IMPORTANCE_NONE)
        )
        NotificationChannels.ensure(app)
        assertFalse(AlertGate.isEnabled(app))
    }

    @Test fun alert_gate_is_disabled_when_app_notifications_are_disabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        NotificationChannels.ensure(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(AlertGate.isEnabled(app))
    }

    @Test fun alert_gate_is_disabled_when_specified_channel_importance_is_none() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NotificationChannels.SPIKES, "Spike alerts", NotificationManager.IMPORTANCE_NONE)
        )
        NotificationChannels.ensure(app)
        assertFalse(AlertGate.isEnabled(app, NotificationChannels.SPIKES))
    }
}

