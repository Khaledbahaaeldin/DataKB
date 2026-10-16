package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BudgetRingTest {
    @get:Rule val rule = createComposeRule()
    private fun show(fraction: Float, state: BudgetStateUi) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { BudgetRing(fraction, state) }
    }

    @Test fun an_ok_ring_describes_the_percentage() {
        show(0.62f, BudgetStateUi.Ok)
        rule.onNodeWithContentDescription("62 percent of the plan used").assertIsDisplayed()
        rule.onNodeWithText("62%").assertIsDisplayed()
    }

    @Test fun a_warning_ring_adds_words_not_just_colour() {
        show(0.85f, BudgetStateUi.Warning)
        rule.onNodeWithContentDescription("85 percent of the plan used, nearly used up").assertIsDisplayed()
    }

    @Test fun an_exceeded_ring_may_show_more_than_100_percent() {
        show(1.12f, BudgetStateUi.Exceeded)
        rule.onNodeWithContentDescription("112 percent of the plan used, over the cap").assertIsDisplayed()
        rule.onNodeWithText("112%").assertIsDisplayed()
    }

    @Test fun a_negative_or_nan_fraction_is_drawn_as_zero() {
        show(Float.NaN, BudgetStateUi.Ok)
        rule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test fun custom_percent_text_overrides_float_derived_text_and_description() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                BudgetRing(0.49f, BudgetStateUi.Ok, percentText = "49%")
            }
        }
        rule.onNodeWithContentDescription("49 percent of the plan used").assertIsDisplayed()
        rule.onNodeWithText("49%").assertIsDisplayed()
    }

    @Test
    fun large_font_scale_scales_down_label_to_fit_inside_ring_bounds() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    BudgetRing(1.37f, BudgetStateUi.Exceeded, percentText = "137%")
                }
            }
        }
        val node = rule.onNodeWithText("137%", useUnmergedTree = true).assertIsDisplayed()
        val widthDp = with(rule.density) { node.fetchSemanticsNode().size.width.toDp() }
        // Inner chord width is ~40dp; without font scaling, width would be > 40dp
        assertTrue("Label width ($widthDp) must be <= 40.dp", widthDp <= 40.dp)
    }
}


