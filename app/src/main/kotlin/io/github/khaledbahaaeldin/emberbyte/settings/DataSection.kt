package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DataSection(
    uiState: DataUiState,
    onExportCsv: (ExportRange) -> Unit,
    onExportJson: () -> Unit,
    onCreateBackup: (CharArray) -> Unit,
    onRestoreChosen: () -> Unit,
    onRestoreBackup: (CharArray) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
    restoreFileChosen: Boolean = false,
    onDismissRestorePassphrase: () -> Unit = {},
) {
    var showRangeDialog by remember { mutableStateOf(false) }
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var showRestoreWarningDialog by remember { mutableStateOf(false) }
    val createBackupRequester = remember { FocusRequester() }
    val restoreBackupRequester = remember { FocusRequester() }

    if (showRangeDialog) {
        CsvRangeDialog(
            onDismiss = { showRangeDialog = false },
            onSelectRange = { range ->
                showRangeDialog = false
                onExportCsv(range)
            },
        )
    }

    if (showCreateBackupDialog) {
        CreateBackupPassphraseDialog(
            onDismiss = {
                showCreateBackupDialog = false
                createBackupRequester.requestFocus()
            },
            onCreate = { passphrase ->
                showCreateBackupDialog = false
                onCreateBackup(passphrase)
                createBackupRequester.requestFocus()
            },
        )
    }

    if (showRestoreWarningDialog) {
        RestoreWarningDialog(
            onDismiss = {
                showRestoreWarningDialog = false
                restoreBackupRequester.requestFocus()
            },
            onConfirm = {
                showRestoreWarningDialog = false
                onRestoreChosen()
            },
        )
    }

    if (restoreFileChosen) {
        RestorePassphraseDialog(
            onDismiss = {
                onDismissRestorePassphrase()
                restoreBackupRequester.requestFocus()
            },
            onRestore = { passphrase ->
                onRestoreBackup(passphrase)
                restoreBackupRequester.requestFocus()
            },
        )
    }

    val rowsEnabled = !uiState.busy

    Column(modifier = modifier.fillMaxWidth()) {
        ActionRow(
            title = "Export usage (CSV)",
            subtitle = "Detailed hourly usage for a spreadsheet",
            enabled = rowsEnabled,
            onClick = { showRangeDialog = true },
        )
        ActionRow(
            title = "Export everything (JSON)",
            subtitle = "Plans, budgets and usage as standard JSON",
            enabled = rowsEnabled,
            onClick = onExportJson,
        )
        ActionRow(
            title = "Create backup",
            subtitle = "Save an encrypted copy of your data",
            enabled = rowsEnabled,
            onClick = { showCreateBackupDialog = true },
            modifier = Modifier.focusRequester(createBackupRequester),
        )
        ActionRow(
            title = "Restore backup",
            subtitle = "Replace data from an encrypted backup",
            enabled = rowsEnabled,
            onClick = { showRestoreWarningDialog = true },
            modifier = Modifier.focusRequester(restoreBackupRequester),
        )

        if (uiState.busy) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Working" },
                )
            }
        }

        if (uiState.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                TextButton(
                    onClick = onClearMessage,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
    }
}
