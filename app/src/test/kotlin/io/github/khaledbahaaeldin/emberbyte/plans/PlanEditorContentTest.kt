package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import java.time.LocalTime
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
        rule.onNodeWithText("Renews at", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertTrue(events.none { it is PlanEditorEvent.SetRenewalTime })
    }

    @Test fun every_error_key_is_shown_as_text_somewhere_on_screen() {
        val formWithAllSections = PlanEditorForm(
            name = "Main",
            capText = "10",
            cycleKind = CycleKind.Monthly,
            dayOfMonth = 1,
            rolloverKind = RolloverKind.Capped,
            rolloverCapText = "5",
            addOns = listOf(AddOnDraft(label = "Boost", amountText = "2", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 15))),
            freeRules = listOf(FreeRuleDraft(label = "Night", days = setOf(DayOfWeek.MONDAY), start = LocalTime.of(0, 0), end = LocalTime.of(6, 0))),
        )
        val keys = listOf(
            "freeRule.0.days",
            "freeRule.0.time",
            "addOn.0.validUntil",
            "addOn.0.bytes",
            "addOn.0.label",
            "freeRule.0.label",
            "name",
            "capBytes",
            "cycle.dayOfMonth",
            "rollover.maxBytes",
            "form",
        )
        show(state.copy(form = formWithAllSections, errors = keys.associateWith { "Problem $it" }))
        for (key in keys) {
            rule.onNodeWithText("Problem $key").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun cycle_days_error_is_shown_under_every_n_days() {
        show(state.copy(
            form = state.form.copy(cycleKind = CycleKind.EveryNDays, everyDays = 10),
            errors = mapOf("cycle.days" to "Problem cycle.days"),
        ))
        rule.onNodeWithText("Problem cycle.days").performScrollTo().assertIsDisplayed()
    }

    @Test fun unknown_error_key_is_shown_above_save() {
        show(state.copy(errors = mapOf("unknown.key" to "Problem unknown.key")))
        rule.onNodeWithText("Problem unknown.key").performScrollTo().assertIsDisplayed()
    }

    @Test fun time_field_formats_time_following_device() {
        show(state.copy(form = state.form.copy(renewalTime = LocalTime.of(14, 30))))
        rule.onNodeWithText("Renews at", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun date_field_formats_date_following_device() {
        val date = LocalDate.of(2026, 10, 15)
        show(state.copy(
            form = state.form.copy(
                cycleKind = CycleKind.EveryNDays,
                anchor = date,
            ),
        ))
        val expected = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault()).format(date)
        rule.onNodeWithText("Starts on: $expected").performScrollTo().assertIsDisplayed()
    }

    @Test fun free_window_day_chips_in_320dp_container_wrap_and_keep_min_width() {
        val draft = FreeRuleDraft(label = "Night", days = DayOfWeek.entries.toSet())
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(320.dp)) {
                    PlanEditorContent(
                        state = state.copy(form = state.form.copy(freeRules = listOf(draft))),
                        onEvent = {},
                        onBack = {},
                        onPrompt = {},
                    )
                }
            }
        }
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        for (day in dayLabels) {
            val node = rule.onNodeWithText(day).performScrollTo()
            node.assertIsDisplayed()
            val width = node.fetchSemanticsNode().size.width
            val widthDp = with(rule.density) { width.toDp() }
            assertTrue("Expected $day chip width ($widthDp) >= 48dp", widthDp >= 48.dp)
        }
    }

    @Test fun threshold_chips_at_font_scale_2_do_not_wrap() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    PlanEditorContent(state = state, onEvent = {}, onBack = {}, onPrompt = {})
                }
            }
        }
        val chip50 = rule.onNodeWithText("50%").performScrollTo().fetchSemanticsNode()
        val chip100 = rule.onNodeWithText("100%").performScrollTo().fetchSemanticsNode()
        assertTrue(
            "100% chip height (${chip100.size.height}) must be <= 50% chip height (${chip50.size.height})",
            chip100.size.height <= chip50.size.height,
        )
    }

    @Test fun for_existing_plan_what_if_section_shows_fields_chips_and_sentences() {
        val whatIf = WhatIfUi(
            amountText = "500",
            unit = CapUnit.MB,
            currentText = "runs out Mon (±1 day)",
            whatIfText = "runs out Sun (±1 day)",
            error = null,
        )
        show(state.copy(isNew = false, planId = 1, whatIf = whatIf))
        rule.onNodeWithText("What if I use more?").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Extra per day").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Now: runs out Mon (±1 day)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("With extra: runs out Sun (±1 day)").performScrollTo().assertIsDisplayed()
    }

    @Test fun what_if_section_is_absent_for_new_plan() {
        show(state.copy(isNew = true, planId = 0, whatIf = null))
        rule.onNodeWithText("What if I use more?").assertDoesNotExist()
        rule.onNodeWithText("Extra per day").assertDoesNotExist()
        rule.onNodeWithText("Now:", substring = true).assertDoesNotExist()
        rule.onNodeWithText("With extra:", substring = true).assertDoesNotExist()
    }

    @Test fun what_if_error_text_is_shown_under_field_when_error_is_present() {
        val whatIf = WhatIfUi(
            amountText = "abc",
            unit = CapUnit.MB,
            currentText = "runs out Mon (±1 day)",
            whatIfText = null,
            error = "Enter a number",
        )
        show(state.copy(isNew = false, planId = 1, whatIf = whatIf))
        rule.onNodeWithText("Enter a number").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("With extra:", substring = true).assertDoesNotExist()
    }

    @Test fun when_not_dirty_back_leaves_immediately() {
        var backCount = 0
        show(state.copy(isDirty = false), onBack = { backCount++ })
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCount)
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test fun when_dirty_back_arrow_shows_discard_dialog_keep_editing_dismisses() {
        var backCount = 0
        show(state.copy(isDirty = true), onBack = { backCount++ })
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, backCount)

        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, backCount)
    }

    @Test fun when_dirty_discard_dialog_confirm_calls_on_back() {
        var backCount = 0
        show(state.copy(isDirty = true), onBack = { backCount++ })
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()

        rule.onNodeWithText("Discard").performClick()
        assertEquals(1, backCount)
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test fun when_dirty_system_back_triggers_discard_dialog() {
        var backCount = 0
        show(state.copy(isDirty = true), onBack = { backCount++ })
        (rule as? androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>)?.activity?.onBackPressedDispatcher?.onBackPressed()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, backCount)

        rule.onNodeWithText("Discard").performClick()
        assertEquals(1, backCount)
    }

    @Test fun when_not_dirty_system_back_leaves_immediately() {
        var backCount = 0
        show(state.copy(isDirty = false), onBack = { backCount++ })
        (rule as? androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>)?.activity?.onBackPressedDispatcher?.onBackPressed()
        assertEquals(1, backCount)
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
