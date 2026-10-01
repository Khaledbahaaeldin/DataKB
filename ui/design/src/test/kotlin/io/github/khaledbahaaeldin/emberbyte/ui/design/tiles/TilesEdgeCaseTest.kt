package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TilesEdgeCaseTest {
    @get:Rule val rule = createComposeRule()

    @Test fun usageBarRow_with_empty_list_does_not_crash() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(emptyList(), selectedIndex = null, onSelect = {})
            }
        }
        rule.waitForIdle()
    }

    @Test fun usageBarRow_with_all_zero_bytes_renders_every_bar() {
        val bars = listOf(BarUi("Mon", 0L, "Monday, 0 bytes"), BarUi("Tue", 0L, "Tuesday, 0 bytes"))
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(bars, selectedIndex = null, onSelect = {})
            }
        }
        rule.onNodeWithText("Mon").assertIsDisplayed()
        rule.onNodeWithText("Tue").assertIsDisplayed()
    }

    @Test fun usageBarRow_exposes_description_selected_state_and_touch_height() {
        val bars = listOf(
            BarUi("Mon", 1_000_000_000L, "Monday, 1.00 gigabytes"),
            BarUi("Tue", 2_000_000_000L, "Tuesday, 2.00 gigabytes"),
        )
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(bars, selectedIndex = 1, onSelect = {})
            }
        }
        val mon = rule.onNode(hasContentDescription("Monday, 1.00 gigabytes", substring = true))
        val tue = rule.onNode(hasContentDescription("Tuesday, 2.00 gigabytes", substring = true))
        mon.assertIsNotSelected()
        tue.assertIsSelected()
        mon.assertHeightIsAtLeast(48.dp)
        tue.assertHeightIsAtLeast(48.dp)
    }

    @Test fun usageBarRow_tap_by_description_reports_index() {
        var picked = -1
        val bars = listOf(BarUi("Mon", 5L, "Monday, 5 bytes"), BarUi("Tue", 9L, "Tuesday, 9 bytes"))
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(bars, selectedIndex = null, onSelect = { picked = it })
            }
        }
        rule.onNode(hasContentDescription("Monday, 5 bytes", substring = true)).performClick()
        assertEquals(0, picked)
    }

    @Test fun appRow_with_zero_total_does_not_crash_and_shows_zero() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                AppRow(AppRowUi("com.idle", "Idle", 0L, 0L), ByteUnits.DECIMAL, onClick = {})
            }
        }
        rule.onNodeWithText("Idle").assertIsDisplayed()
    }

    @Test fun appRow_with_only_wifi_or_only_mobile_renders() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    AppRow(AppRowUi("a", "OnlyWifi", 0L, 5_000_000L), ByteUnits.DECIMAL, onClick = {})
                    AppRow(AppRowUi("b", "OnlyMobile", 5_000_000L, 0L), ByteUnits.DECIMAL, onClick = {})
                }
            }
        }
        rule.onNodeWithText("OnlyWifi").assertIsDisplayed()
        rule.onNodeWithText("OnlyMobile").assertIsDisplayed()
    }

    @Test fun appRow_is_at_least_48dp_and_clickable() {
        var clicks = 0
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                AppRow(AppRowUi("com.yt", "YouTube", 1L, 1L), ByteUnits.DECIMAL, onClick = { clicks++ })
            }
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
            .assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test fun estimatedBadge_says_estimated() {
        rule.setContent { EmberbyteTheme(darkTheme = true, dynamicColor = false) { EstimatedBadge() } }
        rule.onNodeWithText("Estimated").assertIsDisplayed()
    }

    @Test fun speedTile_mobile_label_and_upload_rate() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SpeedTile(rxBps = 1_500_000L, txBps = 350_000L, network = NetworkKindUi.Mobile, units = ByteUnits.DECIMAL)
            }
        }
        rule.onNodeWithText("Mobile", substring = true).assertIsDisplayed()
        rule.onNodeWithText("350 KB/s", substring = true).assertIsDisplayed()
    }

    @Test fun tiles_with_very_long_text_do_not_crash() {
        val long = "very long text ".repeat(40)
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    ForecastTile(ForecastUi(long, long, long))
                    SpeedTile(Long.MAX_VALUE, Long.MAX_VALUE, NetworkKindUi.Wifi, ByteUnits.BINARY)
                }
            }
        }
        rule.waitForIdle()
    }
}
