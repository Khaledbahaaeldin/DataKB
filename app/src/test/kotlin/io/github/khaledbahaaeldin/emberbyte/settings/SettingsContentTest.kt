package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        settings: Settings = Settings(),
        onUpdate: ((Settings) -> Settings) -> Unit = {},
        onBack: () -> Unit = {},
        onOpenBatterySettings: () -> Unit = {},
    ) =
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SettingsContent(
                    settings = settings,
                    onUpdate = onUpdate,
                    onBack = onBack,
                    versionName = "0.2.0",
                    onOpenSource = {},
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }
        }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test fun toggling_the_live_notification_updates_only_that_field() {
        var transform: ((Settings) -> Settings)? = null
        show(onUpdate = { transform = it })
        rule.onNodeWithText("Live notification").performClick()
        val result = transform!!(Settings())
        assertFalse(result.liveNotificationEnabled)
        assertEquals(Settings().copy(liveNotificationEnabled = false), result)
    }

    @Test fun amoled_and_dynamic_colour_toggles() {
        val seen = mutableListOf<(Settings) -> Settings>()
        show(onUpdate = { seen += it })
        scrollTo("AMOLED black")
        rule.onNodeWithText("AMOLED black").performClick()
        rule.onNodeWithText("Dynamic colour").performClick()
        assertTrue(seen[0](Settings()).amoledBlack)
        assertFalse(seen[1](Settings()).useDynamicColor)
    }

    @Test fun unit_chips_switch_the_unit_system() {
        var transform: ((Settings) -> Settings)? = null
        show(onUpdate = { transform = it })
        scrollTo("Binary (GiB)")
        rule.onNodeWithText("Binary (GiB)").performClick()
        assertEquals(UnitSystem.BINARY, transform!!(Settings()).unitSystem)
    }

    @Test fun the_version_is_shown_and_back_works() {
        var back = false
        show(onBack = { back = true })
        rule.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
        scrollTo("Emberbyte 0.2.0")
        rule.onNodeWithText("Emberbyte 0.2.0").assertIsDisplayed()
    }

    @Test fun scrolling_to_and_clicking_battery_settings_calls_callback() {
        var opened = false
        show(onOpenBatterySettings = { opened = true })
        scrollTo("Battery settings")
        rule.onNodeWithText("Battery settings").performClick()
        assertTrue(opened)
    }
}
