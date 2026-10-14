package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppsRtlTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun apps_range_chips_are_ordered_from_right_to_left_in_rtl() {
        val state = AppsUiState(loaded = true)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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

        val todayBounds = rule.onNodeWithText("Today").getBoundsInRoot()
        val sevenDaysBounds = rule.onNodeWithText("7 days").getBoundsInRoot()
        val monthBounds = rule.onNodeWithText("This month").getBoundsInRoot()

        // In RTL layout direction, the first item "Today" should be on the right (higher X),
        // followed by "7 days" to its left, and "This month" furthest to the left.
        assertTrue(
            "In RTL, Today (${todayBounds.left}) must be to the right of 7 days (${sevenDaysBounds.left})",
            todayBounds.left > sevenDaysBounds.left,
        )
        assertTrue(
            "In RTL, 7 days (${sevenDaysBounds.left}) must be to the right of This month (${monthBounds.left})",
            sevenDaysBounds.left > monthBounds.left,
        )
    }
}
