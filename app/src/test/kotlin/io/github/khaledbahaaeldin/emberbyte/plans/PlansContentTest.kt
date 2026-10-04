package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlansContentTest {
    @get:Rule val rule = createComposeRule()

    private fun sampleCard(id: Long = 1L, name: String = "Main SIM", archived: Boolean = false) = PlanCardUi(
        id = id,
        name = name,
        simLabel = "SIM 1 · Alpha",
        fraction = 0.5f,
        ringState = BudgetStateUi.Ok,
        percentText = "50%",
        usedText = "5.00 GB of 10.0 GB",
        remainingText = "5.00 GB left",
        daysLeftText = "15 days left",
        forecastText = null,
        isEstimated = false,
        chips = emptyList(),
        archived = archived,
    )

    private fun show(
        state: PlansUiState,
        onEvent: (PlansEvent) -> Unit = {},
        onOpenPlan: (Long) -> Unit = {},
        onNewPlan: () -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            PlansContent(
                state = state,
                onEvent = onEvent,
                onOpenPlan = onOpenPlan,
                onNewPlan = onNewPlan,
            )
        }
    }

    @Test fun cards_render_and_tapping_calls_on_open_plan() {
        var openedId: Long? = null
        val card = sampleCard(id = 42L, name = "Primary SIM")
        show(
            state = PlansUiState(cards = listOf(card), loaded = true),
            onOpenPlan = { openedId = it },
        )

        rule.onNodeWithText("Primary SIM").assertIsDisplayed()
        rule.onNodeWithText("Primary SIM").performClick()
        assertEquals(42L, openedId)
    }

    @Test fun new_plan_button_calls_on_new_plan() {
        var newPlanCalled = false
        show(
            state = PlansUiState(loaded = true),
            onNewPlan = { newPlanCalled = true },
        )

        rule.onNodeWithText("New plan").assertIsDisplayed()
        rule.onNodeWithText("New plan").performClick()
        assertEquals(true, newPlanCalled)
    }

    @Test fun archived_toggle_button_exists_and_sends_toggle_event() {
        var eventReceived: PlansEvent? = null
        val archived = sampleCard(id = 99L, name = "Old SIM", archived = true)
        show(
            state = PlansUiState(
                cards = emptyList(),
                archivedCards = listOf(archived),
                showArchived = false,
                loaded = true,
            ),
            onEvent = { eventReceived = it },
        )

        rule.onNodeWithText("Show archived (1)").assertIsDisplayed()
        rule.onNodeWithText("Show archived (1)").performClick()
        assertEquals(PlansEvent.ToggleArchived, eventReceived)
    }

    @Test fun empty_state_shows_when_no_cards_and_loaded_true() {
        var newPlanCalled = false
        show(
            state = PlansUiState(cards = emptyList(), archivedCards = emptyList(), loaded = true),
            onNewPlan = { newPlanCalled = true },
        )

        rule.onNodeWithText("No plans yet").assertIsDisplayed()
        rule.onNodeWithText("Add your cap and renewal day to see what is left and when it runs out.").assertIsDisplayed()
        rule.onNodeWithText("Set up your first plan").assertIsDisplayed()
        rule.onNodeWithText("Set up your first plan").performClick()
        assertEquals(true, newPlanCalled)
    }

    @Test fun empty_state_does_not_show_when_loaded_false() {
        show(
            state = PlansUiState(cards = emptyList(), archivedCards = emptyList(), loaded = false),
        )

        rule.onNodeWithText("No plans yet").assertDoesNotExist()
        rule.onNodeWithText("Set up your first plan").assertDoesNotExist()
    }

    @Test fun archived_cards_render_when_show_archived_true() {
        val archived = sampleCard(id = 99L, name = "Archived Plan", archived = true)
        show(
            state = PlansUiState(
                cards = emptyList(),
                archivedCards = listOf(archived),
                showArchived = true,
                loaded = true,
            ),
        )

        rule.onNodeWithText("Hide archived").assertIsDisplayed()
        rule.onNodeWithText("Archived Plan").assertIsDisplayed()
    }
}
