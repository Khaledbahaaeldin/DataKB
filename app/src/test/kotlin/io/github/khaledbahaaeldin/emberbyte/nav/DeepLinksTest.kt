package io.github.khaledbahaaeldin.emberbyte.nav

import android.app.Application
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
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinksTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun links_are_built_from_ids_and_package_names() {
        assertEquals("emberbyte://plans/7", DeepLinks.plan(7))
        assertEquals("emberbyte://apps/com.example.app", DeepLinks.app("com.example.app"))
        assertEquals("plans/0", Routes.planEditor(0))
    }

    @Test fun the_explicit_intent_targets_the_main_activity_and_carries_the_uri() {
        val intent = DeepLinks.intent(context, DeepLinks.plan(3))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("emberbyte://plans/3", intent.data.toString())
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun every_documented_link_resolves_to_the_main_activity_through_the_manifest() {
        val uris = listOf(DeepLinks.HOME, DeepLinks.APPS, DeepLinks.app("com.example"), DeepLinks.PLANS, DeepLinks.plan(3), DeepLinks.LENS, DeepLinks.SETTINGS)
        for (uri in uris) {
            val probe = Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName)
            val resolved = context.packageManager.queryIntentActivities(probe, 0)
            assertTrue("$uri did not resolve", resolved.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }

    @Test fun other_schemes_do_not_resolve() {
        val probe = Intent(Intent.ACTION_VIEW, "https://example.com/plans/1".toUri()).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(probe, 0).none { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test fun nav_controller_handles_deep_link_destinations() {
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
                    Destination.Apps.route,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.APPS }),
                ) { Box {} }
                composable(
                    Routes.PLAN_EDITOR,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
                ) { Box {} }
                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.SETTINGS }),
                ) { Box {} }
            }
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(navController)
            val appsIntent = Intent(Intent.ACTION_VIEW, DeepLinks.APPS.toUri())
            assertTrue(nav.handleDeepLink(appsIntent))
            assertEquals(Destination.Apps.route, nav.currentDestination?.route)

            val planIntent = Intent(Intent.ACTION_VIEW, DeepLinks.plan(42).toUri())
            assertTrue(nav.handleDeepLink(planIntent))
            assertEquals(Routes.PLAN_EDITOR, nav.currentDestination?.route)
            assertEquals(42L, nav.currentBackStackEntry?.arguments?.getLong("planId"))

            val settingsIntent = Intent(Intent.ACTION_VIEW, DeepLinks.SETTINGS.toUri())
            assertTrue(nav.handleDeepLink(settingsIntent))
            assertEquals(Routes.SETTINGS, nav.currentDestination?.route)
        }
    }

    @Test fun on_new_intent_updates_intent_and_notifies_listener() {
        val app = ApplicationProvider.getApplicationContext<Application>() as EmberbyteApplication
        runBlocking {
            app.graph.onboarding.complete()
            app.graph.onboardingCompleted.first { it == true }
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        var delivered: Intent? = null
        val listener = androidx.core.util.Consumer<Intent> { delivered = it }
        activity.addOnNewIntentListener(listener)

        val targetIntent = DeepLinks.intent(activity, DeepLinks.APPS)
        controller.newIntent(targetIntent)

        assertEquals(targetIntent, activity.intent)
        assertEquals(targetIntent, delivered)
    }
}
