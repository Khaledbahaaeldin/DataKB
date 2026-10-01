package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarLogicTest {
    @Test fun indexAt_splits_the_bar_into_equal_slots() {
        assertEquals(0, indexAt(x = 10f, width = 400f, count = 4))
        assertEquals(1, indexAt(x = 150f, width = 400f, count = 4))
        assertEquals(2, indexAt(x = 250f, width = 400f, count = 4))
        assertEquals(3, indexAt(x = 399f, width = 400f, count = 4))
    }

    @Test fun indexAt_clamps_outside_the_bar() {
        assertEquals(0, indexAt(x = -50f, width = 400f, count = 4))
        assertEquals(3, indexAt(x = 900f, width = 400f, count = 4))
    }

    @Test fun indexAt_handles_degenerate_input() {
        assertEquals(0, indexAt(x = 10f, width = 0f, count = 4))
        assertEquals(0, indexAt(x = 10f, width = 400f, count = 0))
    }

    @Test fun tracker_starts_visible() =
        assertTrue(ScrollVisibilityTracker(thresholdPx = 40f).visible)

    @Test fun small_downward_scroll_keeps_the_bar_visible() {
        val tracker = ScrollVisibilityTracker(40f)
        assertTrue(tracker.onScroll(downPx = 30f))
    }

    @Test fun accumulated_downward_scroll_past_threshold_hides_the_bar() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(30f)
        assertFalse(tracker.onScroll(20f))
    }

    @Test fun one_big_downward_scroll_hides_the_bar() =
        assertFalse(ScrollVisibilityTracker(40f).onScroll(41f))

    @Test fun any_upward_scroll_shows_the_bar_again() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(100f)
        assertTrue(tracker.onScroll(-1f))
    }

    @Test fun upward_scroll_resets_the_accumulated_distance() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(35f)
        tracker.onScroll(-5f)
        assertTrue(tracker.onScroll(35f))
    }
}
