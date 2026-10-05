package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveNotificationTest {
    @Test fun text_shows_today_and_speed() {
        val text = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("1.24 GB today", text.title)
        assertEquals("↓ 4.20 MB/s", text.text)
    }

    @Test fun text_without_speed_has_no_body() {
        val text = liveNotificationText(512L, 9_999L, ByteUnits.DECIMAL, showSpeed = false)
        assertEquals("512 B today", text.title)
        assertNull(text.text)
    }

    @Test fun zero_usage_reads_naturally() {
        val text = liveNotificationText(0L, 0L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("0 B today", text.title)
        assertEquals("↓ 0 B/s", text.text)
    }

    @Test fun binary_units_are_respected() {
        assertEquals("1.00 MiB today", liveNotificationText(1_048_576L, 0L, ByteUnits.BINARY, showSpeed = false).title)
    }

    @Test fun offline_says_no_connection_instead_of_a_stale_speed() {
        val text = liveNotificationText(1_000_000L, 195L, ByteUnits.DECIMAL, showSpeed = true, offline = true)
        assertEquals("1.00 MB today", text.title)
        assertEquals("No connection", text.text)
    }

    @Test fun the_built_notification_is_ongoing_on_the_live_channel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val notification = LiveNotificationBuilder(app).build(LiveNotificationText("1.24 GB today", "↓ 4.20 MB/s"))
        assertEquals(NotificationChannels.LIVE, notification.channelId)
        assertEquals("1.24 GB today", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("↓ 4.20 MB/s", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("emberbyte://home", shadowOf(notification.contentIntent).savedIntent.data.toString())
    }

    @Test fun remaining_data_comes_first_and_the_speed_follows() {
        val t = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true, remainingBytes = 3_800_000_000L)
        assertEquals("1.24 GB today", t.title)
        assertEquals("3.80 GB left · ↓ 4.20 MB/s", t.text)
    }
    @Test fun remaining_data_alone_when_the_speed_is_hidden() {
        val t = liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left", t.text)
    }
    @Test fun offline_keeps_the_remaining_data() {
        val t = liveNotificationText(0, 0, ByteUnits.DECIMAL, showSpeed = true, offline = true, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left · No connection", t.text)
    }
    @Test fun no_plan_keeps_the_old_text() {
        val t = liveNotificationText(1_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("↓ 4.20 MB/s", t.text)
        assertNull(liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false).text)
    }
    @Test fun nothing_left_says_so() {
        val t = liveNotificationText(1, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 0L)
        assertEquals("No data left", t.text)
    }
}
