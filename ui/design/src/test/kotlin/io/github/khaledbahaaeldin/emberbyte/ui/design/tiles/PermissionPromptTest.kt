package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionPromptTest {
    @get:Rule val rule = createComposeRule()

    @Test fun prompt_shows_its_text_and_reports_the_action() {
        var clicks = 0
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PermissionPrompt(
                    PermissionPromptUi("usage_access", "Allow usage access", "See which apps use your data.", "Open settings"),
                    onAction = { clicks++ },
                )
            }
        }
        rule.onNodeWithText("Allow usage access").assertIsDisplayed()
        rule.onNodeWithText("See which apps use your data.").assertIsDisplayed()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(1, clicks)
    }

    @Test fun gap_banner_shows_its_message() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                GapBanner(GapUi("Not measured 02:10–03:40 because the app was stopped"))
            }
        }
        rule.onNodeWithText("Not measured 02:10–03:40 because the app was stopped").assertIsDisplayed()
    }
}
