package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteSurface
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
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
                    // While the stored settings load, only the background is drawn; the app is never torn down afterwards.
                    if (current != null) EmberbyteApp(graph, hapticsEnabled = current.hapticsEnabled)
                }
            }
        }
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
