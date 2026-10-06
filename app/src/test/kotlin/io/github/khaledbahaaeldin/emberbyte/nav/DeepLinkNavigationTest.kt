package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    @Test
    fun with_editor_of_plan_1_on_top_link_for_plan_2_pushes_second_editor() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable(
                    Routes.PLAN_EDITOR,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1".toUri())))
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/2".toUri())))
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(2L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            assertTrue(nav.popBackStack())
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
        }
    }

    @Test
    fun with_editor_of_plan_1_on_top_link_for_plan_1_does_not_push_second_copy() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable(
                    Routes.PLAN_EDITOR,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1".toUri())))
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1".toUri())))
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun on_plans_tab_apps_link_switches_tab_like_navigation_bar_and_plans_link_does_not_duplicate() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = Destination.Home.route) {
                composable(Destination.Home.route, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME })) { Box {} }
                composable(Destination.Apps.route, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.APPS })) { Box {} }
                composable(Destination.Plans.route, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLANS })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            nav.navigate(Destination.Plans.route) {
                popUpTo(Destination.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            assertEquals(Destination.Plans.route, nav.currentDestination?.route)

            // emberbyte://plans while already on Plans changes nothing
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans".toUri())))
            assertEquals(Destination.Plans.route, nav.currentDestination?.route)

            // emberbyte://apps switches to Apps tab
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://apps".toUri())))
            assertEquals(Destination.Apps.route, nav.currentDestination?.route)

            // Back from Apps goes to Home, not to Plans
            assertTrue(nav.popBackStack())
            assertEquals(Destination.Home.route, nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun settings_link_twice_leaves_single_settings_entry() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable(Routes.SETTINGS, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS })) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())))
            assertEquals(Routes.SETTINGS, nav.currentDestination?.route)

            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())))
            assertEquals(Routes.SETTINGS, nav.currentDestination?.route)

            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun app_detail_links_stack_different_apps_but_not_same_app_twice() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable(
                    Routes.APP_DETAIL,
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.APP_DETAIL }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://apps/com.a".toUri())))
            assertEquals(Routes.APP_DETAIL, nav.currentDestination?.route)
            assertEquals("com.a", nav.currentBackStackEntry?.arguments?.getString("packageName"))

            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://apps/com.b".toUri())))
            assertEquals(Routes.APP_DETAIL, nav.currentDestination?.route)
            assertEquals("com.b", nav.currentBackStackEntry?.arguments?.getString("packageName"))

            assertTrue(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://apps/com.b".toUri())))
            assertEquals(Routes.APP_DETAIL, nav.currentDestination?.route)
            assertEquals("com.b", nav.currentBackStackEntry?.arguments?.getString("packageName"))

            assertTrue(nav.popBackStack())
            assertEquals(Routes.APP_DETAIL, nav.currentDestination?.route)
            assertEquals("com.a", nav.currentBackStackEntry?.arguments?.getString("packageName"))

            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun when_not_allowed_links_return_false_and_stack_is_unchanged() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
                composable(Routes.ONBOARDING) { Box {} }
                composable(Destination.Home.route, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME })) { Box {} }
                composable(Routes.SETTINGS, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS })) { Box {} }
                composable(
                    Routes.PLAN_EDITOR,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
                ) { Box {} }
                composable(
                    Routes.APP_DETAIL,
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.APP_DETAIL }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            val disallowed = { false }

            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://home".toUri()), allowed = disallowed))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri()), allowed = disallowed))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1".toUri()), allowed = disallowed))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://apps/com.example".toUri()), allowed = disallowed))

            assertEquals(Routes.ONBOARDING, nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun malformed_and_out_of_bounds_links_return_false_without_crash() {
        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box {} }
                composable(
                    Routes.PLAN_EDITOR,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/abc".toUri())))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/-1".toUri())))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://plans/1/2".toUri())))
            assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, "emberbyte://".toUri())))
            assertEquals("home", nav.currentDestination?.route)
        }
    }
}
