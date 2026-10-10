package io.github.khaledbahaaeldin.emberbyte.nav

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityNewIntentTest {

    @Test
    fun onNewIntent_drops_deep_link_when_onboarding_not_completed() {
        val app = ApplicationProvider.getApplicationContext<Application>() as EmberbyteApplication
        runBlocking {
            app.graph.onboardingCompleted.first { it != null }
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings"))
        controller.newIntent(intent)

        assertNull(activity.intent.data)
    }

    @Test
    fun onNewIntent_keeps_deep_link_when_onboarding_completed() {
        val app = ApplicationProvider.getApplicationContext<Application>() as EmberbyteApplication
        runBlocking {
            app.graph.onboarding.complete()
            app.graph.onboardingCompleted.first { it == true }
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings"))
        controller.newIntent(intent)

        assertEquals(Uri.parse("emberbyte://settings"), activity.intent.data)
    }
}
