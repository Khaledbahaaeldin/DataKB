package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CsvRangeDialog(
    onDismiss: () -> Unit,
    onSelectRange: (ExportRange) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RangeOptionRow("Last 7 days") { onSelectRange(ExportRange.LAST_7_DAYS) }
                RangeOptionRow("Last 30 days") { onSelectRange(ExportRange.LAST_30_DAYS) }
                RangeOptionRow("All") { onSelectRange(ExportRange.ALL) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RangeOptionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

@Composable
fun CreateBackupPassphraseDialog(
    onDismiss: () -> Unit,
    onCreate: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeatPassphrase by remember { mutableStateOf("") }

    val tooShort = passphrase.isNotEmpty() && passphrase.length < 8
    val mismatch = repeatPassphrase.isNotEmpty() && passphrase != repeatPassphrase
    val canCreate = passphrase.length >= 8 && passphrase == repeatPassphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tooShort) {
                    Text(
                        text = "Use at least 8 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = repeatPassphrase,
                    onValueChange = { repeatPassphrase = it },
                    label = { Text("Repeat passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mismatch) {
                    Text(
                        text = "The passphrases do not match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chars = passphrase.toCharArray()
                    onCreate(chars)
                },
                enabled = canCreate,
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun RestoreWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Text("Restore replaces your plans, budgets and history on this device. Continue?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun RestorePassphraseDialog(
    onDismiss: () -> Unit,
    onRestore: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chars = passphrase.toCharArray()
                    onRestore(chars)
                },
                enabled = passphrase.isNotEmpty(),
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
