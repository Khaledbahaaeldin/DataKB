package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
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
class HomeContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        state: HomeUiState,
        onSettings: () -> Unit = {},
        onHistory: () -> Unit = {},
        onPrompt: (String) -> Unit = {},
        onOpenApp: (String) -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            HomeContent(
                state = state,
                onEvent = {},
                onOpenSettings = onSettings,
                onOpenHistory = onHistory,
                onPromptAction = onPrompt,
                onOpenApp = onOpenApp,
            )
        }
    }

    @Test fun settings_icon_opens_settings() {
        var opened = false
        show(HomeUiState(), onSettings = { opened = true })
        rule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(true, opened)
    }

    @Test fun prompts_and_gap_banner_are_shown_and_report_their_action() {
        var picked: String? = null
        show(
            HomeUiState(
                prompts = listOf(PermissionPromptUi("usage_access", "Allow usage access", "Body", "Open settings")),
                gap = GapUi("Not measured 02:10–03:40 because the app was stopped"),
            ),
            onPrompt = { picked = it },
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Open settings"))
        rule.onNodeWithText("Not measured 02:10–03:40 because the app was stopped").assertExists()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals("usage_access", picked)
    }

    @Test fun without_a_plan_the_data_plan_tile_explains_whats_coming() {
        show(HomeUiState(hasPlan = false))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Data plan"))
        rule.onNodeWithText("Data plan").assertIsDisplayed()
    }

    @Test fun with_a_plan_the_data_plan_tile_is_hidden() {
        show(HomeUiState(hasPlan = true))
        rule.onNodeWithText("Data plan").assertDoesNotExist()
    }

    @Test fun the_history_tile_opens_history() {
        var opened = false
        show(HomeUiState(), onHistory = { opened = true })
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("History"))
        rule.onNodeWithText("History").performClick()
        assertEquals(true, opened)
    }

    @Test fun tapping_a_top_app_opens_it() {
        var opened: String? = null
        show(
            HomeUiState(topApps = listOf(AppRowUi("com.video", "Video", 1, 1))),
            onOpenApp = { opened = it },
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Video"))
        rule.onNodeWithText("Video").performClick()
        assertEquals("com.video", opened)
    }

    @Test fun locked_top_apps_explain_why() {
        show(HomeUiState(topAppsLocked = true))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Allow usage access to see which apps use your data."))
        rule.onNodeWithText("Allow usage access to see which apps use your data.").assertIsDisplayed()
    }
}
