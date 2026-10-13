package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageBarRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fourteen_bars_scrolls_and_meets_touch_targets() {
        val bars = (1..14).map { BarUi(bytes = 100L * it, label = "$it", description = "Bar $it") }
        rule.setContent {
            Box(Modifier.size(360.dp, 200.dp)) {
                UsageBarRow(points = bars, selectedIndex = 0, onSelect = {})
            }
        }
        for (i in 1..14) {
            val node = rule.onNodeWithContentDescription("Bar $i")
            node.assertWidthIsAtLeast(48.dp)
            node.assertHeightIsAtLeast(48.dp)
            if (i == 14) {
                node.performScrollTo()
            }
        }
    }

    @Test
    fun seven_bars_in_379dp_container_is_scrollable_but_starts_at_first_bar_unclipped() {
        val bars = (1..7).map { BarUi(bytes = 100L * it, label = "$it", description = "FitBar $it") }
        rule.setContent {
            Box(Modifier.size(379.dp, 200.dp)) {
                UsageBarRow(points = bars, selectedIndex = 0, onSelect = {}, openAtEnd = false)
            }
        }
        rule.waitForIdle()
        // First bar is displayed
        val firstBar = rule.onNodeWithContentDescription("FitBar 1")
        firstBar.assertIsDisplayed()
        firstBar.assertWidthIsAtLeast(48.dp)
        firstBar.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun bar_labels_render_in_one_node_matching_text_under_rtl() {
        val bars = listOf(
            BarUi(bytes = 100L, label = "Mon", description = "Monday"),
            BarUi(bytes = 200L, label = "7 days", description = "7 days"),
        )
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(360.dp, 200.dp)) {
                    UsageBarRow(points = bars, selectedIndex = null, onSelect = {})
                }
            }
        }
        rule.onNodeWithText("Mon").assertIsDisplayed()
        rule.onNodeWithText("7 days").assertIsDisplayed()
    }

    @Test
    fun row_with_many_bars_opens_scrolled_to_end() {
        val bars = (1..60).map { BarUi(bytes = 100L * it, label = "$it", description = "Bar $it") }
        rule.setContent {
            Box(Modifier.size(360.dp, 200.dp)) {
                UsageBarRow(points = bars, selectedIndex = null, onSelect = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Bar 60").assertIsDisplayed()
        rule.onNodeWithContentDescription("Bar 1").assertIsNotDisplayed()
    }

    @Test
    fun switching_key_or_granularity_rescrolls_to_end() {
        val barsDay = (1..60).map { BarUi(bytes = 100L * it, label = "D$it", description = "Day $it") }
        val barsWeek = (1..20).map { BarUi(bytes = 100L * it, label = "W$it", description = "Week $it") }
        var currentBars by androidx.compose.runtime.mutableStateOf(barsDay)
        var scrollKey by androidx.compose.runtime.mutableStateOf("DAY")

        rule.setContent {
            Box(Modifier.size(360.dp, 200.dp)) {
                UsageBarRow(points = currentBars, selectedIndex = null, onSelect = {}, scrollKey = scrollKey)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Day 60").assertIsDisplayed()
        rule.onNodeWithContentDescription("Day 1").assertIsNotDisplayed()

        currentBars = barsWeek
        scrollKey = "WEEK"
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Week 20").assertIsDisplayed()
        rule.onNodeWithContentDescription("Week 1").assertIsNotDisplayed()
    }

    @Test
    fun selecting_a_bar_does_not_jump_scroll_position() {
        val bars = (1..60).map { BarUi(bytes = 100L * it, label = "$it", description = "Bar $it") }
        var selected by androidx.compose.runtime.mutableStateOf<Int?>(null)

        rule.setContent {
            Box(Modifier.size(360.dp, 200.dp)) {
                UsageBarRow(points = bars, selectedIndex = selected, onSelect = { selected = it })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Bar 60").assertIsDisplayed()
        rule.onNodeWithContentDescription("Bar 1").assertIsNotDisplayed()

        // Select the last bar
        selected = 59
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Bar 60").assertIsDisplayed()
        rule.onNodeWithContentDescription("Bar 1").assertIsNotDisplayed()
    }
}

