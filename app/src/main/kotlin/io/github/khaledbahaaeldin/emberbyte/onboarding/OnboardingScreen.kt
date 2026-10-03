package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.nav.PermissionActions
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    actions: PermissionActions,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(state, actions.openUsageAccess, actions.requestNotifications, onFinish, modifier)
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onOpenUsageAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Emberbyte", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Emberbyte measures your mobile and Wi-Fi data on this device. Nothing ever leaves your phone: no account, no cloud.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepTile(
            title = "Usage access",
            body = "Lets Emberbyte show which apps use your data. You can skip it and still see your totals.",
            granted = state.usageAccess,
            grantedText = "Usage access allowed",
            actionLabel = "Open settings",
            onAction = onOpenUsageAccess,
        )
        StepTile(
            title = "Notifications",
            body = "Shows today's usage and your current speed in the notification shade.",
            granted = state.notifications,
            grantedText = "Notifications allowed",
            actionLabel = "Allow notifications",
            onAction = onRequestNotifications,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFinish) { Text("Skip for now") }
            Button(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) { Text("Continue") }
        }
    }
}

@Composable
private fun StepTile(
    title: String,
    body: String,
    granted: Boolean,
    grantedText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    BentoTile(title = title, modifier = Modifier.fillMaxWidth()) {
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        if (granted) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(grantedText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}
