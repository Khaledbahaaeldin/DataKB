package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastKind
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanChipUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val sampleHomeState = HomeUiState(
        heroLabel = "Today",
        heroBytes = 1_240_000_000L,
        heroDescription = "1.24 gigabytes used today, 3.80 gigabytes left",
        subtitle = "3.80 GB left · runs out Thu (±1 day)",
        throughputBps = 4_200_000L,
        liveRxBps = 3_800_000L,
        liveTxBps = 400_000L,
        network = NetworkKindUi.Wifi,
        mobileBytes = 940_000_000L,
        wifiBytes = 300_000_000L,
        forecast = ForecastUi("Thu", "runs out (±1 day)", "Medium confidence", ForecastKind.RunsOut),
        topApps = listOf(
            AppRowUi("com.video", "Video", 600_000_000L, 400_000_000L),
            AppRowUi("com.chat", "Chat", 100_000_000L, 0L),
        ),
        week = listOf(
            BarUi(label = "Mon", bytes = 100_000_000L, description = "Monday, 100 megabytes"),
            BarUi(label = "Tue", bytes = 200_000_000L, description = "Tuesday, 200 megabytes"),
            BarUi(label = "Wed", bytes = 2_100_000_000L, description = "Wednesday, 2.10 gigabytes"),
            BarUi(label = "Thu", bytes = 300_000_000L, description = "Thursday, 300 megabytes"),
            BarUi(label = "Fri", bytes = 400_000_000L, description = "Friday, 400 megabytes"),
            BarUi(label = "Sat", bytes = 500_000_000L, description = "Saturday, 500 megabytes"),
            BarUi(label = "Sun", bytes = 600_000_000L, description = "Sunday, 600 megabytes"),
        ),
        hasPlan = true,
        units = ByteUnits.DECIMAL,
        planChips = listOf(
            PlanChipUi(1L, "SIM 1", true),
            PlanChipUi(2L, "SIM 2", false),
        ),
        spike = SpikeTileUi(1L, "com.video", "Spike detected", "Video used 1.2 GB in 2h"),
    )

    private fun show(state: HomeUiState = sampleHomeState) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                HomeContent(
                    state = state,
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

    @Test
    fun home_number_exposes_one_node_with_used_and_remaining_spelled_out_units() {
        show()
        // Exactly ONE node with the full spelled-out description exists
        rule.onAllNodesWithContentDescription("1.24 gigabytes used today, 3.80 gigabytes left")
            .assertCountEquals(1)

        // Ensure child unit/digit nodes do not leak separate semantics nodes in unmerged tree
        rule.onAllNodesWithText("GB", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun home_icon_buttons_have_content_descriptions() {
        show()
        // Settings icon button
        val settingsNode = rule.onNodeWithContentDescription("Settings")
        settingsNode.assertIsDisplayed()
        settingsNode.assert(hasClickAction())
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun home_chart_exposes_summary_node_with_highest_day() {
        show()
        val summaryNode = rule.onNodeWithContentDescription(
            "Daily usage, last 7 days, highest Wednesday, 2.10 gigabytes",
        )
        summaryNode.assertExists()
        val summaryBounds = summaryNode.getBoundsInRoot()
        val width = summaryBounds.right - summaryBounds.left
        val height = summaryBounds.bottom - summaryBounds.top
        assertTrue("Summary node width must be >= 48dp but was $width", width >= 48.dp)
        assertTrue("Summary node height must be >= 48dp but was $height", height >= 48.dp)
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun home_touch_targets_are_at_least_48dp() {
        show()
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun at_font_scale_2_home_tiles_reflow_to_one_column_and_no_number_is_clipped() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 2f),
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.size(360.dp, 2000.dp)) {
                        HomeContent(
                            state = sampleHomeState,
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
        }

        rule.waitForIdle()

        // Tiles must reflow to ONE column: ForecastTile should be stacked vertically below SpeedTile
        val speedTile = rule.onNodeWithText("Live · Wi-Fi")
        val forecastTile = rule.onNodeWithText("Forecast")

        speedTile.assertIsDisplayed()
        forecastTile.assertIsDisplayed()

        val speedBounds = speedTile.getBoundsInRoot()
        val forecastBounds = forecastTile.getBoundsInRoot()

        assertTrue(
            "Expected Forecast tile to be stacked below Speed tile in 1 column, but forecast top (${forecastBounds.top}) is not >= speed top (${speedBounds.top})",
            forecastBounds.top > speedBounds.top,
        )

        // The speed number text node is fully displayed and fits inside parent
        val downText = rule.onNodeWithText("↓ 3.80 MB/s", substring = true)
        downText.assertIsDisplayed()
        val downBounds = downText.getBoundsInRoot()
        assertTrue("Speed number right edge exceeds 360dp parent", downBounds.right <= 360.dp)
    }
}
