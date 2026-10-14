package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanCardAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val baseModel = PlanCardUi(
        id = 1L,
        name = "Unlimited Plus",
        simLabel = "SIM 1",
        fraction = 0.63f,
        ringState = BudgetStateUi.Ok,
        percentText = "63%",
        usedText = "6.30 GB of 10.0 GB",
        remainingText = "3.70 GB left",
        daysLeftText = "7 days left",
        forecastText = null,
        isEstimated = false,
        chips = emptyList(),
        archived = false,
    )

    private fun show(model: PlanCardUi) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PlanCard(model = model, onClick = {})
            }
        }
    }

    @Test
    fun plan_card_exposes_text_value_with_percent_used_and_days_left() {
        show(baseModel)
        rule.onNodeWithContentDescription("Unlimited Plus, 63 percent used, 3.70 GB left, 7 days left").assertIsDisplayed()
    }

    @Test
    fun over_cap_state_does_not_rely_on_color_only_and_shows_text_or_described_icon() {
        val overCapModel = baseModel.copy(
            fraction = 1.15f,
            ringState = BudgetStateUi.Exceeded,
            percentText = "115%",
            remainingText = "0 GB left",
        )
        show(overCapModel)

        // Card spoken description must convey over-cap state
        val cardNode = rule.onNodeWithContentDescription("Unlimited Plus, 115 percent used, 0 GB left, 7 days left, over the cap")
        cardNode.assertIsDisplayed()

        // Over-cap icon must have an accessible description ("Over the cap")
        rule.onNodeWithContentDescription("Over the cap", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun estimated_state_does_not_rely_on_color_only_and_shows_text_and_described_state() {
        val estimatedModel = baseModel.copy(isEstimated = true)
        show(estimatedModel)

        // Must display visible text "Estimated"
        rule.onNodeWithText("Estimated", substring = true).assertIsDisplayed()

        // Card spoken description must announce estimated state
        rule.onNodeWithContentDescription("estimated", substring = true).assertIsDisplayed()
    }
}
