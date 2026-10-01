package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi

@Composable
fun AppRow(
    model: AppRowUi,
    units: ByteUnits,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = formatBytes(model.totalBytes, units)
    val mobileShare = if (model.totalBytes > 0) model.mobileBytes.toFloat() / model.totalBytes else 0f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                model.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(model.label, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
                if (mobileShare > 0f) {
                    Box(Modifier.weight(mobileShare).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
                if (mobileShare < 1f) {
                    Box(Modifier.weight(1f - mobileShare).height(4.dp).background(MaterialTheme.colorScheme.tertiary))
                }
            }
        }
        Text("${total.value} ${total.unit}", style = MaterialTheme.typography.labelLarge)
    }
}
