package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
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

    @Test fun boot_completed_starts_the_sampler_service() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(SamplerService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test fun package_replaced_restarts_the_service() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(SamplerService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test fun other_broadcasts_are_ignored() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BATTERY_LOW))
        assertNull(shadowOf(app).nextStartedService)
    }
}
