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

    @Test
    fun dismiss_button_on_tertiary_container_meets_contrast() {
        val lightContrast = contrast(io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberLightColors.onTertiaryContainer, io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberLightColors.tertiaryContainer)
        val darkContrast = contrast(io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors.onTertiaryContainer, io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors.tertiaryContainer)
        assertTrue("Light contrast ($lightContrast) >= 4.5", lightContrast >= 4.5)
        assertTrue("Dark contrast ($darkContrast) >= 4.5", darkContrast >= 4.5)

        // The old colour (primary on tertiaryContainer) failed contrast in dark theme (< 4.5:1)
        val oldDarkContrast = contrast(io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors.primary, io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors.tertiaryContainer)
        assertTrue("Old dark primary contrast ($oldDarkContrast) was < 4.5", oldDarkContrast < 4.5)
    }
}
