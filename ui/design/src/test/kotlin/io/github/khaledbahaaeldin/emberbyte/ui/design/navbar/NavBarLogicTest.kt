package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarLogicTest {
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

    // ---- itemIndexAt: real, unequal item bounds ----

    // A wide selected item (0..120) followed by three 52 px items separated by 4 px gaps.
    private val starts = listOf(0f, 124f, 180f, 236f)
    private val ends = listOf(120f, 176f, 232f, 288f)

    @Test fun itemIndexAt_stays_on_the_wide_item_over_its_whole_width() {
        assertEquals(0, itemIndexAt(5f, starts, ends))
        assertEquals(0, itemIndexAt(119f, starts, ends))
    }

    @Test fun itemIndexAt_picks_the_item_containing_the_finger() {
        assertEquals(1, itemIndexAt(150f, starts, ends))
        assertEquals(2, itemIndexAt(200f, starts, ends))
        assertEquals(3, itemIndexAt(287f, starts, ends))
    }

    @Test fun itemIndexAt_resolves_gaps_and_overshoot_to_the_nearest_edge() {
        assertEquals(0, itemIndexAt(121f, starts, ends))
        assertEquals(1, itemIndexAt(123f, starts, ends))
        assertEquals(0, itemIndexAt(-40f, starts, ends))
        assertEquals(3, itemIndexAt(900f, starts, ends))
    }

    @Test fun itemIndexAt_without_bounds_is_zero() =
        assertEquals(0, itemIndexAt(10f, emptyList(), emptyList()))

    @Test fun dragResistance_follows_the_finger_with_damping() {
        assertEquals(0f, dragResistance(0f), 0.001f)
        assertEquals(10f, dragResistance(20f), 0.001f)
        assertEquals(-10f, dragResistance(-20f), 0.001f)
    }

    @Test fun glass_tint_is_translucent_only_when_blur_is_live() {
        assertEquals(0.6f, glassTintAlpha(hasGlass = true, sdkInt = 31), 0.001f)
        assertEquals(0.6f, glassTintAlpha(hasGlass = true, sdkInt = 36), 0.001f)
        assertEquals(0.92f, glassTintAlpha(hasGlass = true, sdkInt = 30), 0.001f)
        assertEquals(0.92f, glassTintAlpha(hasGlass = false, sdkInt = 34), 0.001f)
    }
}
