package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationChannelsTest {
    @Test fun ensure_creates_the_low_importance_live_channel_and_is_idempotent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        NotificationChannels.ensure(app)
        NotificationChannels.ensure(app)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationChannels.LIVE)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test fun ensure_creates_the_status_channel_with_low_importance() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        NotificationChannels.ensure(app)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationChannels.STATUS)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }
}
