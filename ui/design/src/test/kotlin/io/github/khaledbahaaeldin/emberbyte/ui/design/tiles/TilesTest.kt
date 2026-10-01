package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class TilesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun speedTile_shows_rate_and_network() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SpeedTile(rxBps = 4_200_000L, txBps = 350_000L, network = NetworkKindUi.Wifi, units = ByteUnits.DECIMAL)
            }
        }
        rule.onNodeWithText("4.20 MB/s", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Wi-Fi", substring = true).assertIsDisplayed()
    }

    @Test fun speedTile_says_no_connection_when_offline() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SpeedTile(rxBps = 0L, txBps = 0L, network = null, units = ByteUnits.DECIMAL)
            }
        }
        rule.onNodeWithText("No connection").assertIsDisplayed()
        rule.onNodeWithText("Offline", substring = true).assertIsDisplayed()
    }

    @Test fun forecastTile_shows_headline_and_detail() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                ForecastTile(ForecastUi(headline = "Thu", detail = "runs out (±1 day)", confidence = "Medium confidence"))
            }
        }
        rule.onNodeWithText("Thu").assertIsDisplayed()
        rule.onNodeWithText("runs out (±1 day)").assertIsDisplayed()
        rule.onNodeWithText("Medium confidence").assertIsDisplayed()
    }

    @Test fun usageBarRow_reports_the_tapped_bar() {
        var picked = -1
        val bars = listOf(
            BarUi("Mon", 1_000_000_000L, "Monday, 1.00 gigabytes"),
            BarUi("Tue", 2_000_000_000L, "Tuesday, 2.00 gigabytes"),
        )
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(bars, selectedIndex = null, onSelect = { picked = it })
            }
        }
        rule.onNodeWithText("Tue").performClick()
        assertEquals(1, picked)
    }

    @Test fun appRow_shows_name_and_total() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                AppRow(AppRowUi("com.yt", "YouTube", 610_000_000L, 0L), ByteUnits.DECIMAL, onClick = {})
            }
        }
        rule.onNodeWithText("YouTube").assertIsDisplayed()
        rule.onNodeWithText("610 MB").assertIsDisplayed()
    }
}
