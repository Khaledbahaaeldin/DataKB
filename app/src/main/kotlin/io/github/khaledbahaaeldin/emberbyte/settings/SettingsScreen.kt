package io.github.khaledbahaaeldin.emberbyte.settings

import android.content.Intent
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
import androidx.compose.runtime.remember
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

private const val SOURCE_URL = "https://github.com/Khaledbahaaeldin/DataKB"

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
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
        modifier = modifier,
    )
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
