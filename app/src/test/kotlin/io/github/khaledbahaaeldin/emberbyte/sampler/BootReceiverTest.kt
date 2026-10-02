package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun boot_does_not_start_measuring_before_onboarding_is_complete() = runBlocking {
        val graph = AppGraph(app)
        graph.onboardingCompleted.first { it != null }
        var starts = 0
        assertEquals(false, BootReceiver.startIfOnboarded(app, graph) { starts++; true })
        assertEquals(0, starts)
    }

    @Test fun boot_starts_measuring_after_onboarding() = runBlocking {
        val graph = AppGraph(app)
        graph.onboarding.complete()
        graph.onboardingCompleted.first { it == true }
        var starts = 0
        assertEquals(true, BootReceiver.startIfOnboarded(app, graph) { starts++; true })
        assertEquals(1, starts)
    }

    @Test fun other_broadcasts_are_ignored() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BATTERY_LOW))
        assertNull(shadowOf(app).nextStartedService)
    }
}
