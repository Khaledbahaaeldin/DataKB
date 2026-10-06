package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
    fun seven_bars_fit_without_scrolling() {
        val bars = (1..7).map { BarUi(bytes = 100L * it, label = "$it", description = "FitBar $it") }
        rule.setContent {
            Box(Modifier.size(360.dp, 200.dp)) {
                UsageBarRow(points = bars, selectedIndex = 0, onSelect = {})
            }
        }
        for (i in 1..7) {
            val node = rule.onNodeWithContentDescription("FitBar $i")
            node.assertWidthIsAtLeast(48.dp)
            node.assertHeightIsAtLeast(48.dp)
        }
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
}
