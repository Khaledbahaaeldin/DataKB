package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppsContentTest {
    @get:Rule val rule = createComposeRule()

    private val state = AppsUiState(
        apps = listOf(AppRowUi("com.video", "Video", 600_000_000, 400_000_000), AppRowUi("com.chat", "Chat", 100_000_000, 0)),
        totalBytes = 1_100_000_000,
        loaded = true,
    )

    private fun show(
        state: AppsUiState = this.state,
        onEvent: (AppsEvent) -> Unit = {},
        onOpenApp: (String) -> Unit = {},
        onPrompt: (String) -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { AppsContent(state, onEvent, onOpenApp, onPrompt) }
    }

    @Test fun shows_the_rows_and_the_total() {
        show()
        rule.onNodeWithText("Video").assertIsDisplayed()
        rule.onNodeWithText("Chat").assertIsDisplayed()
        rule.onNodeWithText("1.10 GB in total").assertIsDisplayed()
    }

    @Test fun tapping_a_row_opens_that_app() {
        var opened: String? = null
        show(onOpenApp = { opened = it })
        rule.onNodeWithText("Chat").performClick()
        assertEquals("com.chat", opened)
    }

    @Test fun chips_send_range_and_network_events() {
        val events = mutableListOf<AppsEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("7 days").performClick()
        rule.onNodeWithText("Wi-Fi").performClick()
        assertEquals(listOf(AppsEvent.SetRange(AppsRange.LAST_7_DAYS), AppsEvent.SetNetwork(AppsNetwork.WIFI)), events)
    }

    @Test fun typing_in_the_search_field_sends_the_query() {
        val events = mutableListOf<AppsEvent>()
        show(onEvent = { events += it })
        rule.onNode(hasSetTextAction()).performTextInput("vid")
        assertEquals(AppsEvent.SetQuery("vid"), events.last())
    }

    @Test fun missing_usage_access_shows_a_prompt_that_reports_its_action() {
        var picked: String? = null
        show(state.copy(needsUsageAccess = true), onPrompt = { picked = it })
        rule.onNodeWithText("Open settings").performClick()
        assertEquals("usage_access", picked)
    }

    @Test fun an_empty_loaded_list_explains_itself() {
        show(AppsUiState(loaded = true))
        rule.onNodeWithText("No app data yet", substring = true).assertIsDisplayed()
    }

    @Test fun selecting_a_sort_option_sends_the_sort_event() {
        val events = mutableListOf<AppsEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Sort: Most data").performClick()
        rule.onNodeWithText("Least data").performClick()
        assertEquals(listOf(AppsEvent.SetSort(AppSort.BYTES_ASC)), events)
    }
}
