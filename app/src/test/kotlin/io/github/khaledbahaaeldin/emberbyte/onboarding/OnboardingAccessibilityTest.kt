package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.ui.test.performScrollTo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: OnboardingUiState = OnboardingUiState()) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                OnboardingContent(
                    state = state,
                    onOpenUsageAccess = {},
                    onRequestNotifications = {},
                    onSetUpPlan = {},
                    onFinish = {},
                )
            }
        }
    }

    @Test
    fun onboarding_touch_targets_are_at_least_48dp() {
        show(OnboardingUiState(usageAccess = false, notifications = false, hasPlan = false))
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun onboarding_action_buttons_have_non_empty_accessible_names() {
        show(OnboardingUiState(usageAccess = false, notifications = false, hasPlan = false))

        val openSettings = rule.onNodeWithText("Open settings")
        openSettings.assertIsDisplayed()
        openSettings.assert(hasClickAction())

        val allowNotifications = rule.onNodeWithText("Allow notifications")
        allowNotifications.assertIsDisplayed()
        allowNotifications.assert(hasClickAction())

        val setUpPlan = rule.onNodeWithText("Set up a plan").performScrollTo()
        setUpPlan.assertIsDisplayed()
        setUpPlan.assert(hasClickAction())

        val skip = rule.onNodeWithText("Skip for now").performScrollTo()
        skip.assertIsDisplayed()
        skip.assert(hasClickAction())

        val continueBtn = rule.onNodeWithText("Continue").performScrollTo()
        continueBtn.assertIsDisplayed()
        continueBtn.assert(hasClickAction())
    }
}
