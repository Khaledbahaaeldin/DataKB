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
import io.github.khaledbahaaeldin.emberbyte.launchIntentFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.khaledbahaaeldin.emberbyte.handleNewIntent
import org.junit.Assert.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ColdStartLinkTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun launchIntentFor_keeps_intent_when_onboarding_completed() {
        val intent = Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())
        val result = launchIntentFor(intent, onboardingCompleted = true)
        assertEquals("emberbyte://settings".toUri(), result.data)
    }

    @Test
    fun launchIntentFor_drops_data_when_onboarding_not_completed() {
        val intent = Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())
        val result = launchIntentFor(intent, onboardingCompleted = false)
        assertNull(result.data)
    }

    @Test
    fun launch_intent_carrying_settings_when_onboarding_not_completed_is_dropped_and_finishing_onboarding_is_on_home() {
        val launchIntent = Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())
        val sanitizedIntent = launchIntentFor(launchIntent, onboardingCompleted = false)
        assertNull(sanitizedIntent.data)

        var navController: NavHostController? = null
        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
                composable(Routes.ONBOARDING) { Box {} }
                composable(
                    Destination.Home.route,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME }),
                ) { Box {} }
                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            // Onboarding destination is active
            assertEquals(Routes.ONBOARDING, nav.currentDestination?.route)

            // When onboarding finishes, navigate to Home
            nav.navigate(Destination.Home.route) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }

            // Current destination is Home, NOT Settings
            assertEquals(Destination.Home.route, nav.currentDestination?.route)
            assertFalse(nav.popBackStack())
        }
    }

    @Test
    fun onNewIntent_link_during_onboarding_drops_data_and_finishing_onboarding_lands_on_home() {
        val composeActivity = ComponentActivity()
        composeActivity.intent = Intent()

        var startOnboarding by mutableStateOf(true)
        var navController: NavHostController? = null

        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) Routes.ONBOARDING else Destination.Home.route,
            ) {
                composable(Routes.ONBOARDING) { Box {} }
                composable(
                    Destination.Home.route,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME }),
                ) { Box {} }
                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertEquals(Routes.ONBOARDING, nav.currentDestination?.route)

            val intent = Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())
            handleNewIntent(
                activity = composeActivity,
                navController = nav,
                onboardingCompleted = false,
                intent = intent,
            )
        }

        composeRule.runOnUiThread {
            val nav = checkNotNull(navController)
            startOnboarding = false
            nav.navigate(Destination.Home.route) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertEquals(Destination.Home.route, nav.currentDestination?.route)
            assertNull(composeActivity.intent?.data)
        }
    }

    @Test
    fun control_without_link_finishing_onboarding_lands_on_home() {
        val composeActivity = ComponentActivity()
        composeActivity.intent = Intent()

        var startOnboarding by mutableStateOf(true)
        var navController: NavHostController? = null

        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) Routes.ONBOARDING else Destination.Home.route,
            ) {
                composable(Routes.ONBOARDING) { Box {} }
                composable(
                    Destination.Home.route,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME }),
                ) { Box {} }
                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS }),
                ) { Box {} }
            }
        }

        composeRule.runOnUiThread {
            val nav = checkNotNull(navController)
            startOnboarding = false
            nav.navigate(Destination.Home.route) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertEquals(Destination.Home.route, nav.currentDestination?.route)
            assertNull(composeActivity.intent?.data)
        }
    }

    @Test
    fun handleNewIntent_when_onboarding_completed_opens_settings_with_home_on_back_stack() {
        val composeActivity = ComponentActivity()
        composeActivity.intent = Intent()

        var navController: NavHostController? = null

        composeRule.setContent {
            val nav = rememberNavController()
            navController = nav
            NavHost(
                navController = nav,
                startDestination = Destination.Home.route,
            ) {
                composable(
                    Destination.Home.route,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.HOME }),
                ) { Box {} }
                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertEquals(Destination.Home.route, nav.currentDestination?.route)

            val intent = Intent(Intent.ACTION_VIEW, "emberbyte://settings".toUri())
            handleNewIntent(
                activity = composeActivity,
                navController = nav,
                onboardingCompleted = true,
                intent = intent,
            )
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            assertEquals(Routes.SETTINGS, nav.currentDestination?.route)
            assertTrue(nav.popBackStack())
            assertEquals(Destination.Home.route, nav.currentDestination?.route)
        }
    }
}

