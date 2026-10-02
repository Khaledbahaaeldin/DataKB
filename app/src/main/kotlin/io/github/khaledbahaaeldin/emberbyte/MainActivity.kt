package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
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
            val settings by graph.settings.observe().collectAsStateWithLifecycle(initialValue = Settings())
            EmberbyteTheme(
                dynamicColor = settings.useDynamicColor,
                amoledBlack = settings.amoledBlack,
            ) {
                EmberbyteApp(graph, hapticsEnabled = settings.hapticsEnabled)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Start measuring only after the first-run flow is finished.
        lifecycleScope.launch {
            if (graph.onboarding.observeCompleted().first { it != null } == true) ServiceStarter.start(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { graph.permissions.recheck() }
    }
}
