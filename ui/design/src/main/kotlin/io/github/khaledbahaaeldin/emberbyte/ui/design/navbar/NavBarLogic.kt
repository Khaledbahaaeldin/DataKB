package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar


/**
 * Index of the item whose measured bounds contain [x]; in gaps or beyond the ends, the item with the nearest edge.
 * Items have unequal widths (the selected one shows its label), so equal slots would drift.
 */
internal fun itemIndexAt(x: Float, starts: List<Float>, ends: List<Float>): Int {
    var best = 0
    var bestDistance = Float.MAX_VALUE
    for (i in starts.indices) {
        val distance = when {
            x < starts[i] -> starts[i] - x
            x > ends[i] -> x - ends[i]
            else -> 0f
        }
        if (distance < bestDistance) {
            best = i
            bestDistance = distance
        }
    }
    return best
}

/** Pixels the previewed pill trails behind the finger: it follows with resistance, never 1:1. */
internal fun dragResistance(deltaPx: Float): Float = deltaPx * 0.5f

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
