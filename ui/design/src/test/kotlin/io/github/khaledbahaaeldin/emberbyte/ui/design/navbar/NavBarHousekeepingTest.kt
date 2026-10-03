package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w480dp")
class NavBarHousekeepingTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    private class CountingHaptics : HapticFeedback {
        var calls = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { calls++ }
    }

    private fun show(selected: String, haptics: CountingHaptics, onSelect: (String) -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    FloatingPillNavBar(items, selected, onSelect, visible = true)
                }
            }
        }
    }

    @Test fun bar_fills_the_width_it_is_given() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.width(400.dp)) { FloatingPillNavBar(items, "home", {}, visible = true) }
            }
        }
        rule.onNodeWithTag("pill_nav_bar").assertWidthIsEqualTo(400.dp)
    }

    @Test fun tapping_the_selected_item_does_nothing() {
        val haptics = CountingHaptics()
        var picked: String? = null
        show("home", haptics) { picked = it }
        rule.onNodeWithTag("nav_home").performClick()
        assertNull(picked)
        assertEquals(0, haptics.calls)
    }

    @Test fun tapping_another_item_selects_it_and_ticks_once() {
        val haptics = CountingHaptics()
        var picked: String? = null
        show("home", haptics) { picked = it }
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)
        assertEquals(1, haptics.calls)
    }

    @Test fun dragging_within_the_selected_item_does_not_tick_haptics() {
        val haptics = CountingHaptics()
        show("home", haptics) {}
        val home = rule.onNodeWithTag("nav_home").getBoundsInRoot()
        val bar = rule.onNodeWithTag("pill_nav_bar").getBoundsInRoot()
        val density = 1f
        val startX = (home.left - bar.left).value * density + 4f
        val endX = (home.right - bar.left).value * density - 4f
        rule.onNodeWithTag("pill_nav_bar").performTouchInput {
            swipe(start = Offset(startX, centerY), end = Offset(endX, centerY), durationMillis = 300)
        }
        rule.waitForIdle()
        assertEquals(0, haptics.calls)
    }
}
