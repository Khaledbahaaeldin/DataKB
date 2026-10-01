package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

/** Index of the equal-width slot containing [x]; clamped to the valid range. */
internal fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 0 || width <= 0f) return 0
    return ((x / width) * count).toInt().coerceIn(0, count - 1)
}

/**
 * Hides after [thresholdPx] of accumulated downward scroll; any upward scroll shows again.
 * `downPx` is positive when the user scrolls the content down (finger moves up).
 */
internal class ScrollVisibilityTracker(private val thresholdPx: Float) {
    var visible: Boolean = true
        private set
    private var accumulatedDown = 0f

    fun onScroll(downPx: Float): Boolean {
        when {
            downPx < 0f -> {
                accumulatedDown = 0f
                visible = true
            }
            downPx > 0f -> {
                accumulatedDown += downPx
                if (accumulatedDown > thresholdPx) visible = false
            }
        }
        return visible
    }
}
