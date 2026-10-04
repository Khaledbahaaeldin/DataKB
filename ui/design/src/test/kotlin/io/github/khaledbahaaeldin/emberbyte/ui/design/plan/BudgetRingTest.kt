package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
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
}
