package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
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
class PlansAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val samplePlanCard = PlanCardUi(
        id = 1L,
        name = "Unlimited Plus",
        simLabel = "SIM 1",
        fraction = 0.5f,
        ringState = BudgetStateUi.Ok,
        percentText = "50%",
        usedText = "5.0 GB of 10.0 GB",
        remainingText = "5.0 GB left",
        daysLeftText = "15 days left",
        forecastText = null,
        isEstimated = false,
        chips = emptyList(),
        archived = false,
    )

    private fun show(state: PlansUiState) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PlansContent(
                    state = state,
                    onEvent = {},
                    onOpenPlan = {},
                    onNewPlan = {},
                )
            }
        }
    }

    @Test
    fun plans_screen_touch_targets_are_at_least_48dp_with_plans() {
        show(
            PlansUiState(
                loaded = true,
                cards = listOf(samplePlanCard),
                archivedCards = listOf(samplePlanCard.copy(id = 2L, archived = true)),
                showArchived = true,
            ),
        )
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun plans_screen_touch_targets_are_at_least_48dp_empty_state() {
        show(
            PlansUiState(
                loaded = true,
                cards = emptyList(),
                archivedCards = emptyList(),
            ),
        )
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun plans_action_buttons_have_non_empty_accessible_names() {
        show(
            PlansUiState(
                loaded = true,
                cards = listOf(samplePlanCard),
                archivedCards = listOf(samplePlanCard.copy(id = 2L, archived = true)),
            ),
        )

        val newPlanNode = rule.onNodeWithText("New plan")
        newPlanNode.assertIsDisplayed()
        newPlanNode.assert(hasClickAction())

        val archivedNode = rule.onNodeWithText("Show archived (1)")
        archivedNode.assertIsDisplayed()
        archivedNode.assert(hasClickAction())
    }
}
