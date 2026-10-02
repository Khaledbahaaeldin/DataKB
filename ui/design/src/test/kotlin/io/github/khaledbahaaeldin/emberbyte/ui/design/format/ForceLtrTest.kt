package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForceLtrTest {
    @get:Rule val rule = createComposeRule()

    @Test fun numbers_stay_left_to_right_inside_a_right_to_left_screen() {
        var outer: LayoutDirection? = null
        var inner: LayoutDirection? = null
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                outer = LocalLayoutDirection.current
                ForceLtr { inner = LocalLayoutDirection.current }
            }
        }
        assertEquals(LayoutDirection.Rtl, outer)
        assertEquals(LayoutDirection.Ltr, inner)
    }
}
