package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class TileContrastTest {
    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = fg.compositeOver(bg).luminance() + 0.05
        val l2 = bg.luminance() + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    @Test
    fun tile_title_alpha_constant() {
        assertTrue(TILE_TITLE_ALPHA >= 0.87f)
    }
}
