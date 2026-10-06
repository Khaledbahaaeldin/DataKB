package io.github.khaledbahaaeldin.emberbyte.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import java.time.LocalDate

private const val SOURCE_URL = "https://github.com/Khaledbahaaeldin/DataKB"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    dataViewModel: DataViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val dataUiState by dataViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val pendingState = rememberSaveable(saver = SettingsPendingState.Saver) { SettingsPendingState() }

    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { dataViewModel.exportCsv(pendingState.pendingRange, it) }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(dataViewModel::exportJson)
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingState.pendingCreatePassphrase
        if (uri != null && pass != null) {
            dataViewModel.createBackup(uri, pass)
        } else {
            pass?.fill('\u0000')
        }
        pendingState.pendingCreatePassphrase = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingState.pendingRestoreUri = uri
    }

    SettingsContent(
        settings = settings,
        onUpdate = viewModel::update,
        onBack = onBack,
        versionName = versionName,
        onOpenSource = { runCatching { uriHandler.openUri(SOURCE_URL) } },
        onOpenBatterySettings = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        dataUiState = dataUiState,
        onExportCsv = { range ->
            pendingState.pendingRange = range
            val date = LocalDate.now(dataViewModel.clock)
            createCsv.launch("emberbyte-usage-$date.csv")
        },
        onExportJson = {
            val date = LocalDate.now(dataViewModel.clock)
            createJson.launch("emberbyte-export-$date.json")
        },
        onCreateBackup = { passphrase ->
            pendingState.pendingCreatePassphrase = passphrase.copyOf()
            val date = LocalDate.now(dataViewModel.clock)
            createBackup.launch("emberbyte-backup-$date.emberbyte")
        },
        onRestoreChosen = {
            openBackup.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onRestoreBackup = { passphrase ->
            val uri = pendingState.pendingRestoreUri
            if (uri != null) {
                dataViewModel.restoreBackup(uri, passphrase)
                pendingState.pendingRestoreUri = null
            }
        },
        onClearDataMessage = dataViewModel::clearMessage,
        restoreFileChosen = pendingState.pendingRestoreUri != null,
        onDismissRestorePassphrase = {
            pendingState.pendingRestoreUri = null
        },
        modifier = modifier,
    )
}

class SettingsPendingState(
    initialRange: ExportRange = ExportRange.LAST_7_DAYS,
    initialRestoreUri: Uri? = null,
) {
    var pendingRange: ExportRange by mutableStateOf(initialRange)
    var pendingRestoreUri: Uri? by mutableStateOf(initialRestoreUri)
    var pendingCreatePassphrase: CharArray? by mutableStateOf(null)

    companion object {
        val Saver: Saver<SettingsPendingState, Map<String, Any?>> = Saver(
            save = { state ->
                mapOf(
                    "range" to state.pendingRange.name,
                    "restoreUri" to state.pendingRestoreUri?.toString(),
                )
            },
            restore = { map ->
                val rangeName = map["range"] as? String
                val range = rangeName?.let { runCatching { ExportRange.valueOf(it) }.getOrNull() } ?: ExportRange.LAST_7_DAYS
                val uriStr = map["restoreUri"] as? String
                val uri = uriStr?.let { Uri.parse(it) }
                SettingsPendingState(range, uri)
            },
        )
    }
}

@Composable
internal fun SettingsContent(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    onOpenSource: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
    dataUiState: DataUiState = DataUiState(),
    onExportCsv: (ExportRange) -> Unit = {},
    onExportJson: () -> Unit = {},
    onCreateBackup: (CharArray) -> Unit = {},
    onRestoreChosen: () -> Unit = {},
    onRestoreBackup: (CharArray) -> Unit = {},
    onClearDataMessage: () -> Unit = {},
    restoreFileChosen: Boolean = false,
    onDismissRestorePassphrase: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ScreenHeader("Settings", onBack) }
        item { SectionTitle("Notifications") }
        item {
            SwitchRow("Live notification", "Today's usage in the notification shade", settings.liveNotificationEnabled) {
                onUpdate { s -> s.copy(liveNotificationEnabled = it) }
            }
        }
        item {
            SwitchRow("Show speed", "Add your current speed to the notification", settings.notificationShowsSpeed,
                enabled = settings.liveNotificationEnabled) {
                onUpdate { s -> s.copy(notificationShowsSpeed = it) }
            }
        }
        item {
            SwitchRow(
                "Spike alerts",
                "Tell me when an app suddenly uses a lot of mobile data",
                settings.spikeAlertsEnabled,
            ) {
                onUpdate { s -> s.copy(spikeAlertsEnabled = it) }
            }
        }
        item { SectionTitle("Background") }
        item {
            Text(
                "Some phones stop background apps. Allow Emberbyte in the battery settings so it can keep measuring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TextButton(onClick = onOpenBatterySettings) { Text("Battery settings") } }
        item { SectionTitle("Appearance") }
        item {
            SwitchRow("Dynamic colour", "Use your wallpaper colours (Android 12 and newer)", settings.useDynamicColor) {
                onUpdate { s -> s.copy(useDynamicColor = it) }
            }
        }
        item {
            SwitchRow("AMOLED black", "Pure black backgrounds in dark theme", settings.amoledBlack) {
                onUpdate { s -> s.copy(amoledBlack = it) }
            }
        }
        item {
            SwitchRow("Haptics", "A light tick when the navigation pill snaps", settings.hapticsEnabled) {
                onUpdate { s -> s.copy(hapticsEnabled = it) }
            }
        }
        item { SectionTitle("Units") }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.unitSystem == UnitSystem.DECIMAL,
                    onClick = { onUpdate { s -> s.copy(unitSystem = UnitSystem.DECIMAL) } },
                    label = { Text("Decimal (GB)") },
                )
                FilterChip(
                    selected = settings.unitSystem == UnitSystem.BINARY,
                    onClick = { onUpdate { s -> s.copy(unitSystem = UnitSystem.BINARY) } },
                    label = { Text("Binary (GiB)") },
                )
            }
        }
        item { SectionTitle("Data") }
        item {
            DataSection(
                uiState = dataUiState,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                onCreateBackup = onCreateBackup,
                onRestoreChosen = onRestoreChosen,
                onRestoreBackup = onRestoreBackup,
                onClearMessage = onClearDataMessage,
                restoreFileChosen = restoreFileChosen,
                onDismissRestorePassphrase = onDismissRestorePassphrase,
            )
        }
        item { SectionTitle("About") }
        item { Text("Emberbyte $versionName", style = MaterialTheme.typography.bodyLarge) }
        item {
            Text(
                "Open source under the GPL-3.0 or later. Your data never leaves this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TextButton(onClick = onOpenSource) { Text("Source code") } }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
