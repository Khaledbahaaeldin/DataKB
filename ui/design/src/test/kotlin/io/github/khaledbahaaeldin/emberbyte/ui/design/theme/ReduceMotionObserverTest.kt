package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReduceMotionObserverTest {
    @get:Rule val rule = createComposeRule()

    private fun setScale(context: Context, scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    @Test fun reflects_the_system_setting_and_updates_live() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setScale(context, 1f)
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Text(if (LocalReduceMotion.current) "reduced" else "full", Modifier.testTag("probe"))
            }
        }
        rule.onNodeWithTag("probe").assertTextEquals("full")

        setScale(context, 0f)
        rule.waitForIdle()
        rule.onNodeWithTag("probe").assertTextEquals("reduced")

        setScale(context, 1f)
        rule.waitForIdle()
        rule.onNodeWithTag("probe").assertTextEquals("full")
    }

    @Test fun starts_with_reduced_motion_when_setting_is_already_zero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setScale(context, 0f)
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Text(if (LocalReduceMotion.current) "reduced" else "full", Modifier.testTag("probe"))
            }
        }
        rule.onNodeWithTag("probe").assertTextEquals("reduced")
    }

    @Test fun unregisters_content_observer_when_disposed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        var showTheme by androidx.compose.runtime.mutableStateOf(true)

        rule.setContent {
            if (showTheme) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    Text("probe", Modifier.testTag("probe"))
                }
            }
        }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(1, org.robolectric.Shadows.shadowOf(context.contentResolver).getContentObservers(uri).size)

        showTheme = false
        rule.waitForIdle()
        org.junit.Assert.assertTrue(org.robolectric.Shadows.shadowOf(context.contentResolver).getContentObservers(uri).isEmpty())
    }
}
