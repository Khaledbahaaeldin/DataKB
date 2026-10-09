package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
        onEvent: (HomeEvent) -> Unit = {},
        onSettings: () -> Unit = {},
        onHistory: () -> Unit = {},
        onPrompt: (String) -> Unit = {},
        onOpenApp: (String) -> Unit = {},
        onSetUpPlan: () -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            HomeContent(
                state = state,
                onEvent = onEvent,
                onOpenSettings = onSettings,
                onOpenHistory = onHistory,
                onPromptAction = onPrompt,
                onOpenApp = onOpenApp,
                onSetUpPlan = onSetUpPlan,
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

    @Test fun rtl_layout_direction_isolates_home_subtitle_with_force_ltr() {
        val subtitle1 = "3.80 GB left · won't run out this cycle"
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    HomeContent(
                        state = HomeUiState(subtitle = subtitle1),
                        onEvent = {},
                        onOpenSettings = {},
                        onOpenHistory = {},
                        onPromptAction = {},
                        onOpenApp = {},
                        onSetUpPlan = {},
                    )
                }
            }
        }
        val node1 = rule.onNodeWithText(subtitle1)
        node1.assertIsDisplayed()
        assertEquals(LayoutDirection.Ltr, node1.fetchSemanticsNode().layoutInfo.layoutDirection)
    }

    @Test fun rtl_layout_direction_isolates_no_data_left_with_force_ltr() {
        val subtitle2 = "No data left"
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    HomeContent(
                        state = HomeUiState(subtitle = subtitle2),
                        onEvent = {},
                        onOpenSettings = {},
                        onOpenHistory = {},
                        onPromptAction = {},
                        onOpenApp = {},
                        onSetUpPlan = {},
                    )
                }
            }
        }
        val node2 = rule.onNodeWithText(subtitle2)
        node2.assertIsDisplayed()
        assertEquals(LayoutDirection.Ltr, node2.fetchSemanticsNode().layoutInfo.layoutDirection)
    }

    @Test fun spike_tile_shows_title_and_detail_and_tapping_it_calls_on_open_app() {
        var opened: String? = null
        show(
            HomeUiState(
                spike = SpikeTileUi(
                    id = 42L,
                    packageName = "com.video",
                    title = "Video used a lot of data",
                    detail = "1.24 GB in two hours, 8.3x its usual",
                ),
            ),
            onOpenApp = { opened = it },
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Spike detected"))
        rule.onNodeWithText("Spike detected").assertIsDisplayed()
        rule.onNodeWithText("Video used a lot of data").assertIsDisplayed()
        rule.onNodeWithText("1.24 GB in two hours, 8.3x its usual").assertIsDisplayed()
        rule.onNodeWithText("Spike detected").performClick()
        assertEquals("com.video", opened)
    }

    @Test fun spike_tile_dismiss_button_sends_dismiss_event() {
        var dismissedEvent: HomeEvent? = null
        show(
            HomeUiState(
                spike = SpikeTileUi(
                    id = 42L,
                    packageName = "com.video",
                    title = "Video used a lot of data",
                    detail = "1.24 GB in two hours, 8.3x its usual",
                ),
            ),
            onEvent = { dismissedEvent = it },
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dismiss"))
        rule.onNodeWithText("Dismiss").performClick()
        assertEquals(HomeEvent.DismissSpike(42L), dismissedEvent)
    }

    @Test fun without_a_spike_the_spike_detected_tile_is_not_shown() {
        show(HomeUiState(spike = null))
        rule.onNodeWithText("Spike detected").assertDoesNotExist()
    }
}
