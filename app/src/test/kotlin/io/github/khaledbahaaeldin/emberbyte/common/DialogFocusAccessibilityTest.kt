package io.github.khaledbahaaeldin.emberbyte.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.khaledbahaaeldin.emberbyte.plans.PlanEditorContent
import io.github.khaledbahaaeldin.emberbyte.plans.PlanEditorForm
import io.github.khaledbahaaeldin.emberbyte.plans.PlanEditorUiState
import io.github.khaledbahaaeldin.emberbyte.settings.CreateBackupPassphraseDialog
import io.github.khaledbahaaeldin.emberbyte.settings.RestoreWarningDialog
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogFocusAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun settings_create_backup_dialog_moves_focus_in_and_returns_focus_to_trigger_on_dismiss() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                io.github.khaledbahaaeldin.emberbyte.settings.DataSection(
                    uiState = io.github.khaledbahaaeldin.emberbyte.settings.DataUiState(),
                    onExportCsv = {},
                    onExportJson = {},
                    onCreateBackup = {},
                    onRestoreChosen = {},
                    onRestoreBackup = {},
                    onClearMessage = {},
                )
            }
        }

        rule.onNodeWithText("Create backup").performClick()
        rule.onNodeWithText("Passphrase").assertIsFocused()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Create backup").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun settings_restore_backup_dialog_moves_focus_in_and_returns_focus_to_trigger_on_dismiss() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                io.github.khaledbahaaeldin.emberbyte.settings.SettingsContent(
                    settings = io.github.khaledbahaaeldin.emberbyte.data.Settings(),
                    onUpdate = {},
                    onBack = {},
                    versionName = "1.0.0",
                    onOpenSource = {},
                    onOpenBatterySettings = {},
                    dataUiState = io.github.khaledbahaaeldin.emberbyte.settings.DataUiState(),
                )
            }
        }

        rule.onNodeWithText("Restore backup").performClick()

        rule.onNodeWithText("Continue").assertIsFocused()

        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithText("Restore backup").assertIsFocused()
    }

    @Test
    fun discard_dialog_moves_focus_in_on_open_and_returns_focus_on_keep_editing() {
        val dirtyState = PlanEditorUiState(
            isNew = false,
            form = PlanEditorForm(name = "Edited name"),
            isDirty = true,
        )

        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PlanEditorContent(
                    state = dirtyState,
                    onEvent = {},
                    onBack = {},
                    onPrompt = {},
                )
            }
        }

        // Click Back button with dirty form triggers discard dialog
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()

        // Focus inside discard dialog
        rule.onNodeWithText("Discard").assertIsFocused()

        // Click "Keep editing"
        rule.onNodeWithText("Keep editing").performClick()
        rule.waitForIdle()

        // Focus returns to the Back button
        rule.onNodeWithContentDescription("Back").assertIsFocused()
    }
}
