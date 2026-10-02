package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationStabilityTest {
    @get:Rule val rule = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun the_onboarding_flag_never_returns_to_null_once_loaded() = runBlocking {
        val graph = (app as EmberbyteApplication).graph
        val loaded = graph.onboardingCompleted.first { it != null }
        repeat(5) { assertEquals(loaded, graph.onboardingCompleted.value) }
        // a brand new collector gets the cached value immediately, not null
        assertEquals(loaded, graph.onboardingCompleted.first())
    }

    @Test fun the_settings_state_never_returns_to_null_once_loaded() = runBlocking {
        val graph = (app as EmberbyteApplication).graph
        val loaded = graph.settingsState.first { it != null }
        assertNotNull(loaded)
        graph.settings.update { it.copy(amoledBlack = true) }
        assertEquals(true, graph.settingsState.first { it?.amoledBlack == true }?.amoledBlack)
        assertNotNull(graph.settingsState.first())
    }

    @Test fun a_settings_change_does_not_throw_the_navigation_state_away() {
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
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_apps").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nav_apps").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { graph.settings.update { it.copy(useDynamicColor = false) } }
        rule.waitForIdle()
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }
}
