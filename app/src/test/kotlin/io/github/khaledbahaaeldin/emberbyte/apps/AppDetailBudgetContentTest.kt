package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDetailBudgetContentTest {
    @get:Rule val rule = createComposeRule()

    private val baseState = AppDetailUiState(
        packageName = "com.video",
        label = "Video",
        totalBytes = 1_000_000L,
        mobileBytes = 600_000L,
        wifiBytes = 400_000L,
        units = ByteUnits.DECIMAL,
        loaded = true,
    )

    private fun show(
        state: AppDetailUiState = baseState,
        onEvent: (AppDetailEvent) -> Unit = {},
        onBack: () -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            AppDetailContent(state = state, onEvent = onEvent, onBack = onBack)
        }
    }

    @Test fun without_a_budget_shows_data_budget_tile_and_set_a_budget_button() {
        val events = mutableListOf<AppDetailEvent>()
        show(state = baseState.copy(budget = null), onEvent = { events += it })

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Data budget"))
        rule.onNodeWithText("Data budget").assertIsDisplayed()
        rule.onNodeWithText("Set a budget").assertIsDisplayed().performClick()
        assertEquals(listOf(AppDetailEvent.OpenBudgetEditor), events)
    }

    @Test fun with_a_budget_shows_ring_used_text_period_text_and_edit_remove_buttons() {
        val events = mutableListOf<AppDetailEvent>()
        val budget = BudgetUi(
            id = 1L,
            fraction = 0.816f,
            ringState = BudgetStateUi.Warning,
            percentText = "81%",
            usedText = "612 MB of 750 MB",
            periodText = "today",
            enabled = true,
        )
        show(state = baseState.copy(budget = budget), onEvent = { events += it })

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Edit"))
        rule.onNodeWithText("Data budget").assertIsDisplayed()
        rule.onNodeWithText("612 MB of 750 MB").assertIsDisplayed()
        rule.onNodeWithText("today").assertIsDisplayed()
        rule.onNodeWithText("81%").assertIsDisplayed()

        rule.onNodeWithText("Edit").assertIsDisplayed().performClick()
        rule.onNodeWithText("Remove").assertIsDisplayed().performClick()
        assertEquals(listOf(AppDetailEvent.OpenBudgetEditor, AppDetailEvent.RemoveBudget), events)
    }

    @Test fun editor_dialog_shows_fields_chips_buttons_and_error_texts() {
        val events = mutableListOf<AppDetailEvent>()
        val editor = BudgetEditorUi(
            limitText = "abc",
            unit = CapUnit.GB,
            period = BudgetPeriod.CYCLE,
            planId = null,
            warnAtPercent = 80,
            errors = mapOf("limitBytes" to "Enter a number greater than zero", "planId" to "That plan no longer exists"),
            saving = false,
            isEdit = false,
        )
        show(
            state = baseState.copy(
                editor = editor,
                planOptions = listOf(PlanOptionUi(1L, "Main")),
            ),
            onEvent = { events += it },
        )

        rule.onNodeWithText("Limit").assertIsDisplayed()
        rule.onNodeWithText("Enter a number greater than zero").assertIsDisplayed()
        rule.onNodeWithText("That plan no longer exists").performScrollTo().assertIsDisplayed()

        rule.onNodeWithText("MB").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("GB").assertIsDisplayed()
        rule.onNodeWithText("Daily").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Plan cycle").assertIsDisplayed()
        rule.onNodeWithText("Calendar month").assertIsDisplayed()
        rule.onNodeWithText("Main").performScrollTo().assertIsDisplayed().performClick()

        rule.onNodeWithText("50%").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("80%").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("90%").performScrollTo().assertIsDisplayed().performClick()

        rule.onNodeWithText("Save").assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()

        assertTrue(events.contains(AppDetailEvent.SetBudgetUnit(CapUnit.MB)))
        assertTrue(events.contains(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.DAILY)))
        assertTrue(events.contains(AppDetailEvent.SetBudgetPlan(1L)))
        assertTrue(events.contains(AppDetailEvent.SetBudgetWarn(90)))
        assertTrue(events.contains(AppDetailEvent.SaveBudget))
        assertTrue(events.contains(AppDetailEvent.CloseBudgetEditor))
    }

    @Test fun spike_section_is_absent_when_empty() {
        show(state = baseState.copy(spikes = emptyList()))
        rule.onNodeWithText("Spikes").assertDoesNotExist()
    }

    @Test fun spike_section_lists_rows_with_dismiss_button() {
        val events = mutableListOf<AppDetailEvent>()
        val spikes = listOf(
            SpikeRowUi(10L, "Mon 10:00–12:00", "1.24 GB", "8.3x its usual"),
        )

        show(state = baseState.copy(spikes = spikes), onEvent = { events += it })
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Spikes"))
        rule.onNodeWithText("Spikes").assertIsDisplayed()
        rule.onNodeWithText("Mon 10:00–12:00").assertIsDisplayed()
        rule.onNodeWithText("1.24 GB", substring = true).assertIsDisplayed()
        rule.onNodeWithText("8.3x its usual", substring = true).assertIsDisplayed()

        rule.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf(AppDetailEvent.DismissSpike(10L)), events)
    }
}
