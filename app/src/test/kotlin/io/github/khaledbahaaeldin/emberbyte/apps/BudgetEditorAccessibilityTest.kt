package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density

import io.github.khaledbahaaeldin.emberbyte.common.injectImeInsets
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
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

    @Test
    fun budget_dialog_at_font_scale_2_with_ime_insets_keeps_save_and_cancel_reachable() {
        var saved = false
        var dismissed = false
        val editor = BudgetEditorUi(
            limitText = "500",
            unit = CapUnit.MB,
            period = BudgetPeriod.DAILY,
            planId = null,
            warnAtPercent = 80,
            saving = false,
            isEdit = false,
        )

        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.625f, fontScale = 2f),
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    BudgetEditorDialog(
                        editor = editor,
                        planOptions = emptyList(),
                        onEvent = { event ->
                            when (event) {
                                is AppDetailEvent.SaveBudget -> saved = true
                                is AppDetailEvent.CloseBudgetEditor -> dismissed = true
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }

        val rootHeight = rule.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }
        val imeHeightPx = (rootHeight * 0.40f).toInt()
        val imeTopPx = rootHeight - imeHeightPx


        injectImeInsets(imeHeightPx)
        rule.waitForIdle()

        // Cancel button is reachable and above IME
        val cancelButton = rule.onNodeWithText("Cancel")
        cancelButton.performScrollTo()
        val cancelBounds = cancelButton.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Cancel button bottom (${cancelBounds.bottom}) must be <= IME top ($imeTopPx)",
            cancelBounds.bottom <= imeTopPx,
        )
        cancelButton.performClick()
        assertTrue("Clicking Cancel must fire CloseBudgetEditor", dismissed)

        // Save button is reachable and above IME
        val saveButton = rule.onNodeWithText("Save")
        saveButton.performScrollTo()
        val saveBounds = saveButton.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Save button bottom (${saveBounds.bottom}) must be <= IME top ($imeTopPx)",
            saveBounds.bottom <= imeTopPx,
        )
        saveButton.performClick()
        assertTrue("Clicking Save must fire SaveBudget when limit is valid", saved)
    }
}

