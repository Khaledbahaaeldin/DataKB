package io.github.khaledbahaaeldin.emberbyte

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.khaledbahaaeldin.emberbyte.placeholder.StartupPlaceholder
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteSurface
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import androidx.navigation.NavController
import io.github.khaledbahaaeldin.emberbyte.nav.Routes
import io.github.khaledbahaaeldin.emberbyte.nav.openDeepLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = (application as EmberbyteApplication).graph
        setContent {
            val settings by graph.settingsState.collectAsStateWithLifecycle()
            val current = settings
            EmberbyteTheme(
                dynamicColor = current?.useDynamicColor ?: true,
                amoledBlack = current?.amoledBlack ?: false,
            ) {
                EmberbyteSurface {
                    if (current != null) {
                        EmberbyteApp(graph, hapticsEnabled = current.hapticsEnabled)
                    } else {
                        StartupPlaceholder()
                    }
                }
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val completed = if (::graph.isInitialized) graph.onboardingCompleted.value == true else false
        setIntent(launchIntentFor(intent, completed))
    }

    override fun onStart() {
        super.onStart()
        // Start measuring only after the first-run flow is finished.
        lifecycleScope.launch {
            if (graph.onboardingCompleted.first { it != null } == true) ServiceStarter.start(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { graph.permissions.recheck() }
    }
}

internal fun launchIntentFor(intent: Intent, onboardingCompleted: Boolean): Intent =
    if (onboardingCompleted) intent else Intent(intent).setData(null)

internal fun handleNewIntent(
    activity: ComponentActivity,
    navController: NavController,
    onboardingCompleted: Boolean,
    intent: Intent,
) {
    if (!onboardingCompleted) {
        activity.intent = launchIntentFor(intent, false)
        return
    }
    activity.intent = intent
    navController.openDeepLink(
        intent = intent,
        allowed = { navController.currentDestination?.route != Routes.ONBOARDING },
    )
}

