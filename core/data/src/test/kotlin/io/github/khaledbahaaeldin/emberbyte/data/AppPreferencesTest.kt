package io.github.khaledbahaaeldin.emberbyte.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun create_wires_working_settings_and_onboarding_repositories() = runBlocking {
        val prefs = AppPreferences.create(app)
        assertNotNull(prefs.settings)
        assertNotNull(prefs.onboarding)
        assertEquals(Settings(), prefs.settings.observe().first())
        assertEquals(false, prefs.onboarding.observeCompleted().first { it != null })
        prefs.onboarding.complete()
        assertEquals(true, prefs.onboarding.observeCompleted().first { it != null })
    }
}
