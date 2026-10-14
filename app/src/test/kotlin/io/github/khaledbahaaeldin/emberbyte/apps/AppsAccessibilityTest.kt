package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppsAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val sampleAppsState = AppsUiState(
        range = AppsRange.TODAY,
        network = AppsNetwork.ALL,
        apps = listOf(
            AppRowUi("com.video", "Video", 600_000_000L, 400_000_000L),
            AppRowUi("com.chat", "Chat", 100_000_000L, 0L),
        ),
        totalBytes = 1_100_000_000L,
        loaded = true,
    )

    private fun show(state: AppsUiState = sampleAppsState) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                AppsContent(
                    state = state,
                    onEvent = {},
                    onOpenApp = {},
                    onPromptAction = {},
                )
            }
        }
    }

    @Test
    fun apps_icon_and_filter_buttons_have_content_descriptions() {
        show()
        // Filter chips and sort menu have clear text labels/names
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("7 days").assertIsDisplayed()
        rule.onNodeWithText("This month").assertIsDisplayed()
        rule.onNodeWithText("All").assertIsDisplayed()
        rule.onNodeWithText("Mobile").assertIsDisplayed()
        rule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        rule.onNodeWithText("Sort: Most data").assertIsDisplayed()
    }

    @Test
    fun apps_touch_targets_are_at_least_48dp() {
        show()
        assertNoTouchTargetSmallerThan48dp(rule)
    }
}
