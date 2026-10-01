package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class GlassNavBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bar_with_a_real_glass_source_is_displayed_and_tappable() {
        val items = listOf(
            NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
            NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        )
        var picked: String? = null
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                val source = rememberGlassSource()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().glassSource(source).background(Color.Magenta))
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        FloatingPillNavBar(items, "home", { picked = it }, visible = true, glass = source)
                    }
                }
            }
        }
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)
    }
}
