package io.github.khaledbahaaeldin.emberbyte.nav

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApp
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule val rule = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    @Test fun tab_routes_map_to_their_destination() {
        assertEquals(Destination.Home, Destination.fromRoute("home"))
        assertEquals(Destination.Apps, Destination.fromRoute("apps"))
        assertEquals(Destination.Plans, Destination.fromRoute("plans"))
        assertEquals(Destination.Lens, Destination.fromRoute("lens"))
    }

    @Test fun the_app_detail_route_keeps_the_apps_tab_selected() {
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.APP_DETAIL))
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.appDetail("com.example.app")))
    }

    @Test fun other_routes_and_null_fall_back_to_home() {
        assertEquals(Destination.Home, Destination.fromRoute(Routes.HISTORY))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.SETTINGS))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.ONBOARDING))
        assertEquals(Destination.Home, Destination.fromRoute(null))
    }

    @Test fun a_tab_name_inside_another_word_does_not_match() =
        assertEquals(Destination.Home, Destination.fromRoute("appsettings"))

    @Test fun package_names_are_encoded_into_the_route() {
        assertEquals("apps/com.example.app", Routes.appDetail("com.example.app"))
        assertEquals("apps/uid%3A10123", Routes.appDetail("uid:10123"))
    }

    @Test fun lens_tab_shows_placeholder_text_that_it_arrives_in_a_later_update() {
        val graph = (app as EmberbyteApplication).graph
        runBlocking {
            graph.onboarding.complete()
            graph.onboardingCompleted.first { it == true }
        }
        rule.setContent {
            val settings by graph.settingsState.collectAsState()
            val current = settings
            if (current != null) {
                EmberbyteTheme(darkTheme = true, dynamicColor = current.useDynamicColor, amoledBlack = current.amoledBlack) {
                    EmberbyteApp(graph, hapticsEnabled = current.hapticsEnabled)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_lens").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nav_lens").performClick()
        rule.onNodeWithText("Live Lens is opt-in and arrives in a later update.").assertIsDisplayed()
    }
}
