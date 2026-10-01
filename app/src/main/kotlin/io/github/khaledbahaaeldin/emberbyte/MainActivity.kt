package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as EmberbyteApplication).graph
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
}
