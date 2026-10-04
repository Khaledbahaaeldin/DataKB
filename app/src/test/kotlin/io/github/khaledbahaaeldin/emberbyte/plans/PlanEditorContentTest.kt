package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanEditorContentTest {
    @get:Rule val rule = createComposeRule()

    private val state = PlanEditorUiState(
        loading = false,
        sims = listOf(SimOptionUi(null, "Not tied to a SIM"), SimOptionUi(5, "SIM 1 · Alpha")),
        form = PlanEditorForm(name = "Main", capText = "10"),
    )

    private fun show(
        state: PlanEditorUiState = this.state,
        onEvent: (PlanEditorEvent) -> Unit = {},
        onBack: () -> Unit = {},
        onPrompt: (String) -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            PlanEditorContent(state = state, onEvent = onEvent, onBack = onBack, onPrompt = onPrompt)
        }
    }

    @Test fun the_title_depends_on_new_or_existing() {
        show(state.copy(isNew = true))
        rule.onNodeWithText("New plan").assertIsDisplayed()
    }

    @Test fun an_existing_plan_says_edit_and_offers_archive() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(isNew = false, planId = 3), onEvent = { events += it })
        rule.onNodeWithText("Edit plan").assertIsDisplayed()
        rule.onNodeWithText("Archive plan").performScrollTo().performClick()
        assertEquals(listOf<PlanEditorEvent>(PlanEditorEvent.Archive), events)
    }

    @Test fun a_new_plan_has_no_archive_button() {
        show(state.copy(isNew = true))
        rule.onNodeWithText("Archive plan").assertDoesNotExist()
    }

    @Test fun typing_the_name_and_the_cap_sends_events() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Plan name").performTextInput("x")
        rule.onNodeWithText("Cap").performTextInput("5")
        assertTrue(events.any { it is PlanEditorEvent.SetName })
        assertTrue(events.any { it is PlanEditorEvent.SetCap })
    }

    @Test fun unit_chips_send_the_unit() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onAllNodesWithText("MB")[0].performScrollTo().performClick()
        assertEquals(PlanEditorEvent.SetCapUnit(CapUnit.MB), events.last())
    }

    @Test fun errors_are_shown_under_their_fields() {
        show(state.copy(errors = mapOf("name" to "Enter a name of up to 40 characters", "capBytes" to "Enter a number greater than zero")))
        rule.onNodeWithText("Enter a name of up to 40 characters").assertIsDisplayed()
        rule.onNodeWithText("Enter a number greater than zero").assertIsDisplayed()
    }

    @Test fun the_sim_choices_are_listed_and_selectable() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Not tied to a SIM").assertIsDisplayed()
        rule.onNodeWithText("SIM 1 · Alpha").performClick()
        assertEquals(PlanEditorEvent.SetSim(5), events.last())
    }

    @Test fun without_the_phone_permission_a_prompt_is_shown() {
        var picked: String? = null
        show(state.copy(needsPhonePermission = true), onPrompt = { picked = it })
        rule.onNodeWithText("Allow phone access").assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals("phone_state", picked)
    }

    @Test fun the_cycle_fields_follow_the_cycle_kind() {
        show(state.copy(form = state.form.copy(cycleKind = CycleKind.EveryNDays)))
        rule.onNodeWithText("Number of days").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Day of month").assertDoesNotExist()
    }

    @Test fun monthly_shows_the_day_of_month() {
        show(state)
        rule.onNodeWithText("Day of month").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Number of days").assertDoesNotExist()
    }

    @Test fun the_rollover_cap_field_only_exists_for_capped() {
        show(state.copy(form = state.form.copy(rolloverKind = RolloverKind.Capped)))
        rule.onNodeWithText("Maximum rollover").performScrollTo().assertIsDisplayed()
    }

    @Test fun no_rollover_cap_field_for_none() {
        show(state)
        rule.onNodeWithText("Maximum rollover").assertDoesNotExist()
    }

    @Test fun add_ons_can_be_added_and_removed() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(form = state.form.copy(addOns = listOf(AddOnDraft(label = "Boost", amountText = "2", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))))), onEvent = { events += it })
        rule.onNodeWithText("Add add-on pack").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Remove add-on pack").performScrollTo().performClick()
        assertEquals(listOf(PlanEditorEvent.AddAddOn, PlanEditorEvent.RemoveAddOn(0)), events)
    }

    @Test fun free_windows_toggle_days_and_can_be_removed() {
        val events = mutableListOf<PlanEditorEvent>()
        val draft = FreeRuleDraft(label = "Night", days = setOf(DayOfWeek.MONDAY))
        show(state.copy(form = state.form.copy(freeRules = listOf(draft))), onEvent = { events += it })
        rule.onNodeWithText("Tue").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.UpdateFreeRule(0, draft.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))), events.last())
        rule.onNodeWithContentDescription("Remove free window").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.RemoveFreeRule(0), events.last())
    }

    @Test fun the_alert_chips_toggle_thresholds() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("50%").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.ToggleThreshold(50), events.last())
    }

    @Test fun save_sends_save() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf<PlanEditorEvent>(PlanEditorEvent.Save), events)
    }

    @Test fun save_is_disabled_while_saving() {
        show(state.copy(saving = true))
        rule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test fun the_preview_card_is_shown_when_there_is_one() {
        val card = PlanCardUi(1, "Main", null, 0.1f, BudgetStateUi.Ok, "10%", "1.00 GB of 10.0 GB", "9.00 GB left", "16 days left", null, false, emptyList(), false)
        show(state.copy(preview = card))
        rule.onNodeWithText("Preview").assertIsDisplayed()
        rule.onNodeWithText("1.00 GB of 10.0 GB").assertIsDisplayed()
    }

    @Test fun a_missing_plan_shows_message() {
        show(state.copy(notFound = true))
        rule.onNodeWithText("This plan no longer exists").assertIsDisplayed()
    }

    @Test fun the_loading_state() {
        show(state.copy(loading = true))
        rule.onNodeWithText("Plan name").assertDoesNotExist()
    }

    @Test fun the_time_field_opens_a_dialog_that_can_be_cancelled() {
        val events = mutableListOf<PlanEditorEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Renews at 00:00").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertTrue(events.none { it is PlanEditorEvent.SetRenewalTime })
    }
}
