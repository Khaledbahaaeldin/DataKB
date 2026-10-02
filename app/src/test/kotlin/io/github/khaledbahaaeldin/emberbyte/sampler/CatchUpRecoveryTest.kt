package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchUpRecoveryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    private fun graph(onboarded: Boolean): AppGraph {
        val graph = AppGraph(app)
        runBlocking {
            if (onboarded) graph.onboarding.complete()
            graph.onboardingCompleted.first { it == onboarded }
        }
        return graph
    }

    @Test fun nothing_happens_before_onboarding_is_complete() = runBlocking {
        var starts = 0
        CatchUpRecovery.afterCatchUp(app, graph(false), serviceRunning = { false }, tryStart = { starts++; false })
        assertEquals(0, starts)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun a_running_service_needs_no_recovery() = runBlocking {
        var starts = 0
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { true }, tryStart = { starts++; true })
        assertEquals(0, starts)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun a_refused_start_posts_the_paused_notification() = runBlocking {
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { false }, tryStart = { false })
        assertNotNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun an_accepted_start_posts_nothing() = runBlocking {
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { false }, tryStart = { true })
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun cancel_removes_the_paused_notification() {
        StatusNotifications.showPaused(app)
        assertNotNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
        StatusNotifications.cancelPaused(app)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }
}
