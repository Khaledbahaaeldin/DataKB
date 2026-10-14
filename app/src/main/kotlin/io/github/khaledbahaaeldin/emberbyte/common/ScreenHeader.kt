package io.github.khaledbahaaeldin.emberbyte.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
internal fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backModifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                    contentDescription = "Back"
                    text = AnnotatedString("Back")
                }
                .then(backModifier),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
