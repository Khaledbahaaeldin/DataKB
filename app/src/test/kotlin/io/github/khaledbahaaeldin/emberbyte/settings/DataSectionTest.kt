package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        uiState: DataUiState = DataUiState(),
        onExportCsv: (ExportRange) -> Unit = {},
        onExportJson: () -> Unit = {},
        onCreateBackup: (CharArray) -> Unit = {},
        onRestoreChosen: () -> Unit = {},
        onRestoreBackup: (CharArray) -> Unit = {},
        onClearMessage: () -> Unit = {},
        restoreFileChosen: Boolean = false,
        onDismissRestorePassphrase: () -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            DataSection(
                uiState = uiState,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                onCreateBackup = onCreateBackup,
                onRestoreChosen = onRestoreChosen,
                onRestoreBackup = onRestoreBackup,
                onClearMessage = onClearMessage,
                restoreFileChosen = restoreFileChosen,
                onDismissRestorePassphrase = onDismissRestorePassphrase,
            )
        }
    }

    @Test
    fun section_shows_all_four_action_rows() {
        show()
        rule.onNodeWithText("Export usage (CSV)").assertIsDisplayed()
        rule.onNodeWithText("Export everything (JSON)").assertIsDisplayed()
        rule.onNodeWithText("Create backup").assertIsDisplayed()
        rule.onNodeWithText("Restore backup").assertIsDisplayed()
    }

    @Test
    fun export_usage_csv_opens_range_dialog_and_choosing_range_invokes_callback() {
        var selectedRange: ExportRange? = null
        show(onExportCsv = { selectedRange = it })

        rule.onNodeWithText("Export usage (CSV)").performClick()
        rule.onNodeWithText("Last 7 days").assertIsDisplayed()
        rule.onNodeWithText("Last 30 days").assertIsDisplayed()
        rule.onNodeWithText("All").assertIsDisplayed()

        rule.onNodeWithText("Last 7 days").performClick()
        assertEquals(ExportRange.LAST_7_DAYS, selectedRange)
    }

    @Test
    fun export_usage_csv_can_select_last_30_days_or_all() {
        var selectedRange: ExportRange? = null
        show(onExportCsv = { selectedRange = it })

        rule.onNodeWithText("Export usage (CSV)").performClick()
        rule.onNodeWithText("Last 30 days").performClick()
        assertEquals(ExportRange.LAST_30_DAYS, selectedRange)

        rule.onNodeWithText("Export usage (CSV)").performClick()
        rule.onNodeWithText("All").performClick()
        assertEquals(ExportRange.ALL, selectedRange)
    }

    @Test
    fun export_everything_json_calls_callback() {
        var called = false
        show(onExportJson = { called = true })

        rule.onNodeWithText("Export everything (JSON)").performClick()
        assertTrue(called)
    }

    @Test
    fun create_backup_opens_dialog_with_two_password_fields_and_validation() {
        var createdPassphrase: CharArray? = null
        show(onCreateBackup = { createdPassphrase = it })

        rule.onNodeWithText("Create backup").performClick()

        // Two password fields with SemanticsProperties.Password
        rule.onNodeWithText("Passphrase").assertIsDisplayed()
        rule.onNodeWithText("Repeat passphrase").assertIsDisplayed()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertCountEquals(2)

        // Create button is disabled initially
        val createButton = rule.onNodeWithText("Create")
        createButton.assertIsNotEnabled()

        // Type short passphrase
        rule.onNodeWithText("Passphrase").performTextInput("12345")
        rule.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
        createButton.assertIsNotEnabled()

        // Type 8 characters in first field and mismatched repeat
        rule.onNodeWithText("Passphrase").performTextInput("678") // now 8 characters: 12345678
        rule.onNodeWithText("Repeat passphrase").performTextInput("abcdefgh")
        rule.onNodeWithText("The passphrases do not match").assertIsDisplayed()
        createButton.assertIsNotEnabled()

        // Clear repeat and enter matching passphrase
        rule.onNodeWithText("Repeat passphrase").performTextClearance()
        rule.onNodeWithText("Repeat passphrase").performTextInput("12345678")
        createButton.assertIsEnabled()

        createButton.performClick()
        assertArrayEquals("12345678".toCharArray(), createdPassphrase)
    }

    @Test
    fun restore_backup_shows_warning_dialog_then_continue_calls_onRestoreChosen() {
        var restoreChosen = false
        show(onRestoreChosen = { restoreChosen = true })

        rule.onNodeWithText("Restore backup").performClick()

        rule.onNodeWithText("Restore replaces your plans, budgets and history on this device. Continue?")
            .assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Continue").assertIsDisplayed()

        rule.onNodeWithText("Continue").performClick()
        assertTrue(restoreChosen)
    }

    @Test
    fun passphrase_prompt_for_chosen_file_has_one_field_and_restore_button() {
        var restoredPassphrase: CharArray? = null
        show(
            restoreFileChosen = true,
            onRestoreBackup = { restoredPassphrase = it },
        )

        rule.onNodeWithText("Passphrase").assertIsDisplayed()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertCountEquals(1)

        rule.onNodeWithText("Passphrase").performTextInput("mybackupkey")
        rule.onNodeWithText("Restore").performClick()

        assertArrayEquals("mybackupkey".toCharArray(), restoredPassphrase)
    }

    @Test
    fun busy_state_disables_all_four_rows_and_shows_progress_indicator() {
        show(uiState = DataUiState(busy = true))

        rule.onNodeWithText("Export usage (CSV)").assertIsNotEnabled()
        rule.onNodeWithText("Export everything (JSON)").assertIsNotEnabled()
        rule.onNodeWithText("Create backup").assertIsNotEnabled()
        rule.onNodeWithText("Restore backup").assertIsNotEnabled()

        rule.onNodeWithContentDescription("Working").assertIsDisplayed()
    }

    @Test
    fun message_is_shown_under_rows_with_dismiss_button() {
        var dismissed = false
        show(
            uiState = DataUiState(message = "Usage exported", isError = false),
            onClearMessage = { dismissed = true },
        )

        rule.onNodeWithText("Usage exported").assertIsDisplayed()
        rule.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }
}
