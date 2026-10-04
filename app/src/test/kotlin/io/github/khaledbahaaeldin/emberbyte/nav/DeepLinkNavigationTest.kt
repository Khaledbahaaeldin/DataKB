package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinkNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun a_deep_link_is_pushed_onto_the_current_stack() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("plans/{planId}", deepLinks = listOf(navDeepLink { uriPattern = "emberbyte://plans/{planId}" })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            val handled = nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1".toUri()))
            assertTrue(handled)
            assertEquals("plans/{planId}", nav.currentDestination?.route)
            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
        }
    }

    @Test
    fun a_link_to_the_current_destination_does_not_stack_a_copy() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home", deepLinks = listOf(navDeepLink { uriPattern = "emberbyte://home" })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://home".toUri()))
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun settings_opened_from_a_link_closes_back_to_the_previous_screen() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("settings", deepLinks = listOf(navDeepLink { uriPattern = "emberbyte://settings" })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            val handled = nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri()))
            assertTrue(handled)
            assertEquals("settings", nav.currentDestination?.route)
            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
        }
    }

    @Test
    fun a_malformed_or_unknown_link_is_ignored_without_a_crash() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("plans/{planId}", deepLinks = listOf(navDeepLink { uriPattern = "emberbyte://plans/{planId}" })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://nowhere".toUri())))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "https://example.com/plans/1".toUri())))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW)))
            assertEquals("home", nav.currentDestination?.route)
        }
    }
}
