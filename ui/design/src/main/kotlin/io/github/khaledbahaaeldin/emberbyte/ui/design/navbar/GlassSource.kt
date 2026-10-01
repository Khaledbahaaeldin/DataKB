package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Marks content that the navbar should blur behind itself. Wraps Haze so callers never import it. */
@Stable
class GlassSource internal constructor(internal val state: HazeState)

@Composable
fun rememberGlassSource(): GlassSource {
    val state = rememberHazeState()
    return remember(state) { GlassSource(state) }
}

fun Modifier.glassSource(source: GlassSource): Modifier = hazeSource(source.state)
