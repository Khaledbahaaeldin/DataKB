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

    // Robolectric measures text with a stub font (a "137%" label is 5 px wide), so a width assertion can never fail.
    // The cap is therefore asserted on what it controls: the effective size (sp x fontScale) the label is drawn at.
    private fun labelSizes(fontScale: Float, text: String): Pair<Float, Float> {
        var baseSp = 0f
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    baseSp = androidx.compose.material3.MaterialTheme.typography.labelLarge.fontSize.value
                    BudgetRing(1.37f, BudgetStateUi.Exceeded, percentText = text)
                }
            }
        }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val node = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val drawnSp = results.first().layoutInput.style.fontSize.value
        return (drawnSp * fontScale) to (baseSp * fontScale)
    }

    @Test
    fun at_font_scale_2_a_long_label_is_drawn_no_larger_than_1_3_times_the_base_size() {
        val (drawn, uncapped) = labelSizes(fontScale = 2f, text = "137%")
        // Uncapped it would be base x 2.0; the ring caps it at base x 1.3 so it stays inside the stroke.
        assertTrue("drawn $drawn must be <= 1.3 x base (${uncapped / 2f * 1.3f + 0.01f})", drawn <= uncapped / 2f * 1.3f + 0.01f)
        assertTrue("drawn $drawn must be smaller than the uncapped $uncapped", drawn < uncapped - 1f)
    }

    @Test
    fun at_font_scale_1_the_label_keeps_its_normal_size() {
        val (drawn, uncapped) = labelSizes(fontScale = 1f, text = "137%")
        assertTrue("drawn $drawn must equal the base size $uncapped", kotlin.math.abs(drawn - uncapped) < 0.01f)
    }
}
