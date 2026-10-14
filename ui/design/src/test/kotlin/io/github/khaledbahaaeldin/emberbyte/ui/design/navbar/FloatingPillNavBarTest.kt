package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        NavBarItem("extra", "Extra", Icons.Rounded.Visibility, NavAccent.Error),
    )

    private fun show(
        selectedId: String,
        visible: Boolean = true,
        reduceMotion: Boolean = false,
        onSelect: (String) -> Unit = {},
    ) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    FloatingPillNavBar(items, selectedId, onSelect, visible)
                }
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

    @Test fun touching_an_item_reports_its_id() {
        var picked: String? = null
        show("home") { picked = it }
        rule.onNodeWithTag("nav_apps").performTouchInput { click() }
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

    // ---- accessibility: labels and tablist (I-2) ----

    @Test fun every_tab_is_a_labelled_tab_with_its_selected_state() {
        show("home")
        items.forEach { item ->
            val selected = item.id == "home"
            // Selected tabs are named by their visible label; unselected ones by a content description.
            val node = if (selected) rule.onNodeWithText(item.label) else rule.onNodeWithContentDescription(item.label)
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (selected) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    @Test fun the_bar_is_a_tablist_container_with_a_description() {
        show("home")
        rule.onNodeWithTag("pill_nav_bar")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertContentDescriptionEquals("Navigation")
    }

    // ---- drag-to-snap (I-3) ----

    @Test fun dragging_across_the_bar_selects_the_item_under_the_finger_on_release() {
        var picked: String? = null
        show("home") { picked = it }
        rule.onNodeWithTag("pill_nav_bar").performTouchInput {
            swipe(start = Offset(30f, centerY), end = Offset(width - 12f, centerY), durationMillis = 300)
        }
        rule.waitForIdle()
        assertEquals("extra", picked)
    }

    @Test fun dragging_within_the_wide_selected_item_does_not_switch() {
        var picked: String? = null
        show("home") { picked = it }
        val home = rule.onNodeWithTag("nav_home").getBoundsInRoot()
        val bar = rule.onNodeWithTag("pill_nav_bar").getBoundsInRoot()
        val density = 1f // Robolectric default: 1 dp == 1 px
        val startX = (home.left - bar.left).value * density + 4f
        val endX = (home.right - bar.left).value * density - 4f
        rule.onNodeWithTag("pill_nav_bar").performTouchInput {
            swipe(start = Offset(startX, centerY), end = Offset(endX, centerY), durationMillis = 300)
        }
        rule.waitForIdle()
        assertNull(picked)
    }

    // ---- reduce motion (I-1) ----

    @Test fun reduce_motion_keeps_the_bar_visible_even_when_asked_to_hide() {
        show("home", visible = false, reduceMotion = true)
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
    }

    @Test fun reduce_motion_disables_the_drag_but_taps_still_select() {
        var picked: String? = null
        show("home", reduceMotion = true) { picked = it }
        rule.onNodeWithTag("pill_nav_bar").performTouchInput {
            swipe(start = Offset(30f, centerY), end = Offset(width - 12f, centerY), durationMillis = 300)
        }
        rule.waitForIdle()
        assertNull(picked)
        rule.onNodeWithTag("nav_plans").performClick()
        assertEquals("plans", picked)
    }

    // ---- 3-item navbar accessibility & drag (M6 Task P2) ----

    private val threeItems = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
    )

    private fun showThree(
        initialSelectedId: String = "home",
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        onSelect: (String) -> Unit = {},
    ) {
        rule.setContent {
            var currentSelected by remember { mutableStateOf(initialSelectedId) }
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    FloatingPillNavBar(
                        items = threeItems,
                        selectedId = currentSelected,
                        onSelect = {
                            currentSelected = it
                            onSelect(it)
                        },
                        visible = true,
                    )
                }
            }
        }
    }

    @Test fun three_items_are_role_tab_with_selected_state_and_name_and_touch_target_at_least_48dp() {
        showThree("home")
        threeItems.forEach { item ->
            val selected = item.id == "home"
            val tabNode = rule.onNodeWithTag("nav_${item.id}")
            tabNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (selected) tabNode.assertIsSelected() else tabNode.assertIsNotSelected()

            val semantics = tabNode.fetchSemanticsNode()
            val hasName = semantics.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true ||
                semantics.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true
            assertTrue("Tab ${item.id} must expose an accessible name on its semantics node", hasName)

            val widthDp = with(rule.density) { semantics.size.width.toDp() }
            val heightDp = with(rule.density) { semantics.size.height.toDp() }
            assertTrue("Touch target width ($widthDp) must be >= 48.dp", widthDp >= 47.9.dp)
            assertTrue("Touch target height ($heightDp) must be >= 48.dp", heightDp >= 47.9.dp)
        }
    }

    @Test fun three_items_selection_by_click_for_each_index() {
        var picked: String? = null
        showThree("home") { picked = it }
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)

        rule.onNodeWithTag("nav_plans").performClick()
        assertEquals("plans", picked)

        rule.onNodeWithTag("nav_home").performClick()
        assertEquals("home", picked)
    }

    @Test fun three_items_drag_snap_lands_on_the_right_item_for_each_index() {
        var picked: String? = null
        showThree("home") { picked = it }
        val bar = rule.onNodeWithTag("pill_nav_bar")

        // Drag to apps (center)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.15f, centerY), end = Offset(width * 0.50f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("apps", picked)

        // Drag to plans (right)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.15f, centerY), end = Offset(width * 0.85f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("plans", picked)

        // Drag to home (left)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.85f, centerY), end = Offset(width * 0.15f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("home", picked)
    }

    @Test fun three_items_rtl_drag_snap_lands_on_the_right_item_for_each_index() {
        var picked: String? = null
        showThree("home", layoutDirection = LayoutDirection.Rtl) { picked = it }
        val bar = rule.onNodeWithTag("pill_nav_bar")

        // In RTL, index 0 (home) is at the right, index 1 (apps) in center, index 2 (plans) at the left.
        // Drag from home (right) to plans (left)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.85f, centerY), end = Offset(width * 0.15f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("plans", picked)

        // Drag from plans (left) to apps (center)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.15f, centerY), end = Offset(width * 0.50f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("apps", picked)

        // Drag from apps (center) to home (right)
        bar.performTouchInput {
            swipe(start = Offset(width * 0.50f, centerY), end = Offset(width * 0.85f, centerY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals("home", picked)
    }
}
