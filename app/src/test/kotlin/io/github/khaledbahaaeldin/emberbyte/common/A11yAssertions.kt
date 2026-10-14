package io.github.khaledbahaaeldin.emberbyte.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

fun assertNoTouchTargetSmallerThan48dp(rule: ComposeContentTestRule) {
    val density = rule.density
    val visitedNodeIds = mutableSetOf<Int>()
    val tooSmallNodes = mutableListOf<String>()

    fun collectNodes() {
        val clickableNodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        for (node in clickableNodes) {
            if (visitedNodeIds.add(node.id)) {
                val widthDp = with(density) { node.size.width.toDp() }
                val heightDp = with(density) { node.size.height.toDp() }
                if (widthDp < 47.9.dp || heightDp < 47.9.dp) {
                    val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                        ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                        ?: "node#${node.id}"
                    tooSmallNodes.add("$desc (size: ${widthDp} x ${heightDp})")
                }
            }
        }
    }

    collectNodes()
    val allowList = emptyList<String>()
    val violations = tooSmallNodes.filterNot { it in allowList }
    assertTrue(
        "Clickable nodes under 48x48 dp (allow-list must stay empty): $violations",
        violations.isEmpty(),
    )
}
