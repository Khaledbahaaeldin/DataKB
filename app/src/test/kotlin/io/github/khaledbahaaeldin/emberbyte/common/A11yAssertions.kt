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

fun injectImeInsets(imeHeightPx: Int) {
    val insets = androidx.core.view.WindowInsetsCompat.Builder()
        .setInsets(
            androidx.core.view.WindowInsetsCompat.Type.ime(),
            androidx.core.graphics.Insets.of(0, 0, 0, imeHeightPx),
        )
        .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true)
        .build()

    fun updateHolderForView(view: android.view.View) {
        try {
            val holderClass = Class.forName("androidx.compose.foundation.layout.WindowInsetsHolder")
            val companion = holderClass.getField("Companion").get(null)
            val holder = companion.javaClass.getMethod("getOrCreateFor", android.view.View::class.java).invoke(companion, view)
            holderClass.getMethod("update", androidx.core.view.WindowInsetsCompat::class.java, Int::class.javaPrimitiveType)
                .invoke(holder, insets, 0)
        } catch (_: Throwable) {}
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view, insets)
    }

    val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    if (dialog != null) {
        dialog.window?.decorView?.let { decorView ->
            updateHolderForView(decorView)
            if (decorView is android.view.ViewGroup) {
                fun traverse(vg: android.view.ViewGroup) {
                    for (i in 0 until vg.childCount) {
                        val child = vg.getChildAt(i)
                        updateHolderForView(child)
                        if (child is android.view.ViewGroup) traverse(child)
                    }
                }
                traverse(decorView)
            }
        }
    }
}

