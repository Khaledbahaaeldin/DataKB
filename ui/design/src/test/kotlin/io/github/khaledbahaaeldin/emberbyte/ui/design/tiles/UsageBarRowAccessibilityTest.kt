package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageBarRowAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val sampleBars = listOf(
        BarUi(label = "Mon", bytes = 100_000_000L, description = "Monday, 100 megabytes"),
        BarUi(label = "Tue", bytes = 200_000_000L, description = "Tuesday, 200 megabytes"),
        BarUi(label = "Wed", bytes = 2_100_000_000L, description = "Wednesday, 2.10 gigabytes"),
        BarUi(label = "Thu", bytes = 300_000_000L, description = "Thursday, 300 megabytes"),
        BarUi(label = "Fri", bytes = 400_000_000L, description = "Friday, 400 megabytes"),
        BarUi(label = "Sat", bytes = 500_000_000L, description = "Saturday, 500 megabytes"),
        BarUi(label = "Sun", bytes = 600_000_000L, description = "Sunday, 600 megabytes"),
    )

    @Test
    fun bar_chart_exposes_summary_node_and_one_node_per_bar() {
        rule.setContent {
            Box(Modifier.size(411.dp, 200.dp)) {
                UsageBarRow(points = sampleBars, selectedIndex = null, onSelect = {})
            }
        }

        // Must expose ONE summary node containing highest day and its usage
        val summaryNode = rule.onNodeWithContentDescription(
            "Daily usage, last 7 days, highest Wednesday, 2.10 gigabytes",
        )
        summaryNode.assertExists()

        // Each bar must be reachable in order with its individual description
        sampleBars.forEach { bar ->
            val node = rule.onNodeWithContentDescription(bar.description)
            node.assertExists()
            node.assert(hasClickAction())
        }
    }

    @Test
    fun accessibility_click_on_bar_selects_it_like_a_tap() {
        var selected: Int? = null
        rule.setContent {
            Box(Modifier.size(411.dp, 200.dp)) {
                UsageBarRow(
                    points = sampleBars,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }

        // Accessibility click on Wednesday (index 2)
        val wednesdayNode = rule.onNodeWithContentDescription("Wednesday, 2.10 gigabytes")
        wednesdayNode.performClick()
        assertEquals(2, selected)
    }

    @Test
    fun clipped_leftmost_bar_d8_first_bar_inside_viewport_on_379dp() {
        rule.setContent {
            Box(Modifier.size(379.dp, 200.dp)) {
                UsageBarRow(
                    points = sampleBars,
                    selectedIndex = 0,
                    onSelect = {},
                    openAtEnd = false,
                )
            }
        }
        rule.waitForIdle()
        val firstBar379 = rule.onNodeWithContentDescription("Monday, 100 megabytes")
        firstBar379.assertIsDisplayed()
        assertTrue(
            "Expected first bar left edge >= 0 on 379dp, but was ${firstBar379.getBoundsInRoot().left}",
            firstBar379.getBoundsInRoot().left >= 0.dp,
        )
    }

    @Test
    fun clipped_leftmost_bar_d8_first_bar_inside_viewport_on_411dp() {
        rule.setContent {
            Box(Modifier.size(411.dp, 200.dp)) {
                UsageBarRow(
                    points = sampleBars,
                    selectedIndex = 0,
                    onSelect = {},
                    openAtEnd = false,
                )
            }
        }
        rule.waitForIdle()
        val firstBar411 = rule.onNodeWithContentDescription("Monday, 100 megabytes")
        firstBar411.assertIsDisplayed()
        assertTrue(
            "Expected first bar left edge >= 0 on 411dp, but was ${firstBar411.getBoundsInRoot().left}",
            firstBar411.getBoundsInRoot().left >= 0.dp,
        )
    }
}
