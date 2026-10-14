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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun passphrase_dialog_moves_focus_in_on_open_and_returns_focus_on_dismiss() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                var openDialog by remember { mutableStateOf(false) }
                val triggerRequester = remember { FocusRequester() }

                Box {
                    Button(
                        onClick = { openDialog = true },
                        modifier = Modifier.focusRequester(triggerRequester),
                    ) {
                        Text("Open Passphrase Dialog")
                    }

                    if (openDialog) {
                        CreateBackupPassphraseDialog(
                            onDismiss = {
                                openDialog = false
                                triggerRequester.requestFocus()
                            },
                            onCreate = { openDialog = false },
                        )
                    }
                }
            }
        }

        // Open dialog
        rule.onNodeWithText("Open Passphrase Dialog").performClick()
        rule.waitForIdle()

        // Passphrase text field must be focused inside dialog
        rule.onNodeWithText("Passphrase").assertIsFocused()

        // Dismiss dialog
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        // Focus must return to triggering button
        rule.onNodeWithText("Open Passphrase Dialog").assertIsFocused()
    }

    @Test
    fun restore_dialog_moves_focus_in_on_open_and_returns_focus_on_dismiss() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                var openDialog by remember { mutableStateOf(false) }
                val triggerRequester = remember { FocusRequester() }

                Box {
                    Button(
                        onClick = { openDialog = true },
                        modifier = Modifier.focusRequester(triggerRequester),
                    ) {
                        Text("Open Restore Dialog")
                    }

                    if (openDialog) {
                        RestoreWarningDialog(
                            onDismiss = {
                                openDialog = false
                                triggerRequester.requestFocus()
                            },
                            onConfirm = { openDialog = false },
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Open Restore Dialog").performClick()
        rule.waitForIdle()

        // Inside dialog, Continue action must be focused
        rule.onNodeWithText("Continue").assertIsFocused()

        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Open Restore Dialog").assertIsFocused()
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
        rule.onNodeWithText("Back", substring = true).performClick()
        rule.waitForIdle()

        // Focus inside discard dialog
        rule.onNodeWithText("Discard").assertIsFocused()

        // Click "Keep editing"
        rule.onNodeWithText("Keep editing").performClick()
        rule.waitForIdle()

        // Focus returns to the Back button
        rule.onNodeWithText("Back", substring = true).assertIsFocused()
    }
}
