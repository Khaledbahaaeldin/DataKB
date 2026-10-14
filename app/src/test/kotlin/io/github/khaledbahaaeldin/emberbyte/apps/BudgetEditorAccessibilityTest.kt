package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BudgetEditorAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun budget_editor_limit_field_has_label_and_error_linked_via_semantics() {
        val editor = BudgetEditorUi(
            limitText = "0",
            unit = CapUnit.GB,
            period = BudgetPeriod.DAILY,
            planId = null,
            warnAtPercent = 80,
            errors = mapOf("limitBytes" to "Enter a number greater than zero"),
            saving = false,
            isEdit = false,
        )

        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                BudgetEditorDialog(
                    editor = editor,
                    planOptions = emptyList(),
                    onEvent = {},
                )
            }
        }

        // Limit text field should have Error semantics
        val limitField = rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)),
        )
        limitField.assertIsDisplayed()
        val errorValue = limitField.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error)
        assertEquals("Enter a number greater than zero", errorValue)

        // Error message text should be displayed
        val errorTextNode = rule.onNodeWithText("Enter a number greater than zero")
        errorTextNode.assertIsDisplayed()
    }
}
