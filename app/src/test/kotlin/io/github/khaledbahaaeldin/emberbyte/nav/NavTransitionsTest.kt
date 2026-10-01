@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavTransitionsTest {
    private val motion = MotionScheme.expressive()

    @Test fun reduce_motion_switches_tabs_without_any_transition() {
        assertEquals(EnterTransition.None, tabEnterTransition(motion, reduceMotion = true))
        assertEquals(ExitTransition.None, tabExitTransition(motion, reduceMotion = true))
    }

    @Test fun normal_motion_cross_fades_with_the_motion_scheme() {
        assertNotEquals(EnterTransition.None, tabEnterTransition(motion, reduceMotion = false))
        assertNotEquals(ExitTransition.None, tabExitTransition(motion, reduceMotion = false))
    }
}
