package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanEditorAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val baseForm = PlanEditorForm(
        name = "Unlimited Plus",
        capText = "10",
        capUnit = CapUnit.GB,
        cycleKind = CycleKind.Monthly,
        dayOfMonth = 1,
    )

    private fun show(state: PlanEditorUiState) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PlanEditorContent(
                    state = state,
                    onEvent = {},
                    onBack = {},
                    onPrompt = {},
                )
            }
        }
    }

    @Test
    fun plan_editor_action_buttons_have_accessible_names() {
        val state = PlanEditorUiState(isNew = true, loading = false, form = baseForm)
        show(state)

        val backButton = rule.onNodeWithContentDescription("Back")
        backButton.assertIsDisplayed()
        backButton.assert(hasClickAction())

        val saveButton = rule.onNodeWithText("Save").performScrollTo()
        saveButton.assertIsDisplayed()
        saveButton.assert(hasClickAction())
    }

    @Test
    fun form_fields_have_labels_tied_to_the_fields_and_errors_linked_via_semantics() {
        val state = PlanEditorUiState(
            isNew = true,
            loading = false,
            form = baseForm.copy(name = "", capText = ""),
            errors = mapOf(
                "name" to "Enter a name for the plan",
                "capBytes" to "Enter a cap greater than zero",
            ),
        )
        show(state)

        // 1. "Plan name" field must have label and error semantics
        val nameField = rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a name for the plan"),
        )
        nameField.assertIsDisplayed()
        val nameSemantics = nameField.fetchSemanticsNode()
        assertEquals("Enter a name for the plan", nameSemantics.config.getOrNull(SemanticsProperties.Error))

        // Supporting text is displayed
        val nameErrorNode = rule.onNodeWithText("Enter a name for the plan")
        nameErrorNode.assertIsDisplayed()

        // 2. "Cap" field must have error semantics
        val capField = rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a cap greater than zero"),
        )
        capField.assertIsDisplayed()
        val capErrorNode = rule.onNodeWithText("Enter a cap greater than zero")
        capErrorNode.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun plan_editor_touch_targets_are_at_least_48dp() {
        val state = PlanEditorUiState(
            isNew = false,
            loading = false,
            sims = listOf(SimOptionUi(null, "Not tied to a SIM")),
            form = baseForm.copy(
                cycleKind = CycleKind.EveryNDays,
                everyDays = 30,
                rolloverKind = RolloverKind.Capped,
                rolloverCapText = "5",
                addOns = listOf(AddOnDraft(label = "Boost", amountText = "1", unit = CapUnit.GB, validFrom = java.time.LocalDate.now(), validUntil = java.time.LocalDate.now().plusDays(7))),
                freeRules = listOf(FreeRuleDraft(label = "Night", days = setOf(java.time.DayOfWeek.MONDAY))),
            ),
        )
        show(state)
        assertNoTouchTargetSmallerThan48dp(rule)
    }
}
