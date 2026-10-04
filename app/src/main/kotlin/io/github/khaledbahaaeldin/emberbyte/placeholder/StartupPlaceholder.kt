package io.github.khaledbahaaeldin.emberbyte.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun StartupPlaceholder() {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = "Loading Emberbyte" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // One 140 dp rounded block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(color)
        )
        // Row of two 96 dp blocks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color)
            )
        }
        // One 120 dp block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(color)
        )
    }
}
