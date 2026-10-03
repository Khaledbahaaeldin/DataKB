package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: OnboardingUiState, usage: () -> Unit = {}, notifications: () -> Unit = {}, finish: () -> Unit = {}) =
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) { OnboardingContent(state, usage, notifications, finish) }
        }

    @Test fun explains_that_everything_stays_on_the_device() {
        show(OnboardingUiState())
        rule.onNodeWithText("Nothing ever leaves your phone", substring = true).assertIsDisplayed()
    }

    @Test fun both_step_buttons_report_their_actions() {
        var usage = 0; var notifications = 0
        show(OnboardingUiState(), usage = { usage++ }, notifications = { notifications++ })
        rule.onNodeWithText("Open settings").performClick()
        rule.onNodeWithText("Allow notifications").performClick()
        assertEquals(1, usage); assertEquals(1, notifications)
    }

    @Test fun granted_steps_show_allowed_instead_of_a_button() {
        show(OnboardingUiState(usageAccess = true, notifications = true))
        rule.onNodeWithText("Usage access allowed").assertIsDisplayed()
        rule.onNodeWithText("Notifications allowed").assertIsDisplayed()
    }

    @Test fun continue_and_skip_both_finish() {
        var finished = 0
        show(OnboardingUiState(), finish = { finished++ })
        rule.onNodeWithText("Skip for now").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        assertEquals(2, finished)
    }
}
