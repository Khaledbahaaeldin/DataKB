package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class NavBarVisibility internal constructor(thresholdPx: Float) {
    private val tracker = ScrollVisibilityTracker(thresholdPx)

    var visible: Boolean by mutableStateOf(true)
        private set

    /** Attach with `Modifier.nestedScroll(connection)` on an ancestor of the scrolling content. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            visible = tracker.onScroll(downPx = -available.y)
            return Offset.Zero
        }
    }
}

@Composable
fun rememberNavBarVisibility(threshold: Dp = 40.dp): NavBarVisibility {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { NavBarVisibility(thresholdPx) }
}
