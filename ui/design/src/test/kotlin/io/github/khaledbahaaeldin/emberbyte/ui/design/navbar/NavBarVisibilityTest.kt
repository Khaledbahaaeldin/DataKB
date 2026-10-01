package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarVisibilityTest {
    @Test fun connection_hides_after_threshold_shows_on_upward_scroll_and_never_consumes() {
        val nav = NavBarVisibility(thresholdPx = 40f)
        assertTrue(nav.visible)
        val first = nav.connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, first)
        assertFalse(nav.visible)
        nav.connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        assertFalse(nav.visible)
        val up = nav.connection.onPreScroll(Offset(0f, 10f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, up)
        assertTrue(nav.visible)
    }
}
