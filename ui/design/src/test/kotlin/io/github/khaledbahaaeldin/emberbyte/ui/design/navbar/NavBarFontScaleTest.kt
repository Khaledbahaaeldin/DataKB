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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavBarFontScaleTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    @Test fun at_200_percent_font_every_item_stays_inside_the_bar() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(360.dp)) { FloatingPillNavBar(items, "home", {}, visible = true) }
                }
            }
        }
        val bar = rule.onNodeWithTag("pill_nav_bar").getBoundsInRoot()
        for (item in items) {
            val bounds = rule.onNodeWithTag("nav_${item.id}").getBoundsInRoot()
            assertTrue("${item.id} right=${bounds.right} bar=${bar.right}", bounds.right <= bar.right)
            assertTrue("${item.id} left=${bounds.left}", bounds.left >= bar.left)
        }
    }
}
