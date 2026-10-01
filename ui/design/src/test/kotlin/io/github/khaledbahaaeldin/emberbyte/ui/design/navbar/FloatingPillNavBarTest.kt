package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingPillNavBarTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    private fun show(selectedId: String, visible: Boolean = true, onSelect: (String) -> Unit = {}) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                FloatingPillNavBar(items, selectedId, onSelect, visible)
            }
        }
    }

    @Test fun selected_item_shows_its_label_and_others_do_not() {
        show("home")
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
        rule.onNodeWithTag("nav_home").assertIsSelected()
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText("Apps").assertDoesNotExist()
    }

    @Test fun tapping_an_item_reports_its_id() {
        var picked: String? = null
        show("home") { picked = it }
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)
    }

    @Test fun hidden_bar_is_not_composed() {
        show("home", visible = false)
        rule.onNodeWithTag("pill_nav_bar").assertDoesNotExist()
    }

    @Test fun toggling_visible_removes_and_restores_the_bar() {
        var visible by mutableStateOf(true)
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                FloatingPillNavBar(items, "home", {}, visible)
            }
        }
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
        visible = false
        rule.waitForIdle()
        rule.onNodeWithTag("pill_nav_bar").assertDoesNotExist()
        visible = true
        rule.waitForIdle()
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
    }
}
