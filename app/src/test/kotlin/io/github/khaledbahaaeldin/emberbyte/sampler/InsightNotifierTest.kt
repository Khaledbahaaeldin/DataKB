package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InsightNotifierTest {
    @Test
    fun budget_notifier_posts_on_alerts_channel_with_correct_tag_id_deeplink_and_action() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val budget = AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.DAILY, null, 80, true)
        val notice = BudgetNotice(budget, "Video", 612_000_000L, BudgetState.WARNING)

        BudgetNotifier.post(app, notice, ByteUnits.DECIMAL)

        val manager = app.getSystemService(NotificationManager::class.java)
        val shadowManager = shadowOf(manager)
        val expectedId = budget.id.toInt()
        val notification = shadowManager.getNotification(BudgetNotifier.TAG, expectedId)
        assertNotNull(notification)
        assertEquals(NotificationChannels.ALERTS, notification.channelId)
        assertEquals("Video: nearly at its data budget", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("612 MB of 750 MB used today", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("emberbyte://apps/com.video", shadowOf(notification.contentIntent).savedIntent.data.toString())
        assertEquals(1, notification.actions?.size ?: 0)
        assertEquals("Data settings", notification.actions[0].title.toString())
    }

    @Test
    fun spike_notifier_posts_on_spikes_channel_with_correct_tag_id_deeplink_and_action() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val event = SpikeEvent(
            id = 1,
            packageName = "com.video",
            windowStart = Instant.EPOCH,
            detectedAt = Instant.EPOCH,
            windowMinutes = 120,
            bytes = 1_240_000_000L,
            baselineBytes = 150_000_000L,
            multiple = 8.2667f,
            dismissed = false,
        )

        SpikeNotifier.post(app, event, "Video", ByteUnits.DECIMAL)

        val manager = app.getSystemService(NotificationManager::class.java)
        val shadowManager = shadowOf(manager)
        val expectedId = "com.video".hashCode() and Int.MAX_VALUE
        val notification = shadowManager.getNotification(SpikeNotifier.TAG, expectedId)
        assertNotNull(notification)
        assertEquals(NotificationChannels.SPIKES, notification.channelId)
        assertEquals("Video used a lot of data", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("1.24 GB in two hours, 8.3x its usual", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("emberbyte://apps/com.video", shadowOf(notification.contentIntent).savedIntent.data.toString())
        assertEquals(1, notification.actions?.size ?: 0)
        assertEquals("Data settings", notification.actions[0].title.toString())
    }
}
