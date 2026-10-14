package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberLightColors
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
        val lightContrast = contrast(EmberLightColors.onTertiaryContainer, EmberLightColors.tertiaryContainer)
        val darkContrast = contrast(EmberDarkColors.onTertiaryContainer, EmberDarkColors.tertiaryContainer)
        assertTrue("Light contrast ($lightContrast) >= 4.5", lightContrast >= 4.5)
        assertTrue("Dark contrast ($darkContrast) >= 4.5", darkContrast >= 4.5)

        // The old colour (primary on tertiaryContainer) failed contrast in dark theme (< 4.5:1)
        val oldDarkContrast = contrast(EmberDarkColors.primary, EmberDarkColors.tertiaryContainer)
        assertTrue("Old dark primary contrast ($oldDarkContrast) was < 4.5", oldDarkContrast < 4.5)
    }

    private val amoledColors: ColorScheme = EmberDarkColors.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
    )

    private fun assertTextContrast(name: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast ($ratio) must be >= $minRatio", ratio >= minRatio)
    }

    @Test
    fun static_light_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val s = EmberLightColors
        assertTextContrast("Light onPrimary / primary", s.onPrimary, s.primary)
        assertTextContrast("Light onPrimaryContainer / primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertTextContrast("Light onSecondary / secondary", s.onSecondary, s.secondary)
        assertTextContrast("Light onSecondaryContainer / secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertTextContrast("Light onTertiary / tertiary", s.onTertiary, s.tertiary)
        assertTextContrast("Light onTertiaryContainer / tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertTextContrast("Light onBackground / background", s.onBackground, s.background)
        assertTextContrast("Light onSurface / surface", s.onSurface, s.surface)
        assertTextContrast("Light onSurfaceVariant / surface", s.onSurfaceVariant, s.surface)
        assertTextContrast("Light onSurface / surfaceContainer", s.onSurface, s.surfaceContainer)
        assertTextContrast("Light onSurfaceVariant / surfaceContainer", s.onSurfaceVariant, s.surfaceContainer)
        assertTextContrast("Light onSurface / surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh)
        assertTextContrast("Light onSurfaceVariant / surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertTextContrast("Light onSurface / surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest)
        assertTextContrast("Light onSurfaceVariant / surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest)
        assertTextContrast("Light onError / error", s.onError, s.error)
        assertTextContrast("Light onErrorContainer / errorContainer", s.onErrorContainer, s.errorContainer)
        assertTextContrast("Light primary / surface", s.primary, s.surface)
        assertTextContrast("Light error / surface (exceeded state)", s.error, s.surface)
        assertTextContrast("Light outline / surface (graphic/boundary)", s.outline, s.surface, minRatio = 3.0)
    }

    @Test
    fun static_dark_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val s = EmberDarkColors
        assertTextContrast("Dark onPrimary / primary", s.onPrimary, s.primary)
        assertTextContrast("Dark onPrimaryContainer / primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertTextContrast("Dark onSecondary / secondary", s.onSecondary, s.secondary)
        assertTextContrast("Dark onSecondaryContainer / secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertTextContrast("Dark onTertiary / tertiary", s.onTertiary, s.tertiary)
        assertTextContrast("Dark onTertiaryContainer / tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertTextContrast("Dark onBackground / background", s.onBackground, s.background)
        assertTextContrast("Dark onSurface / surface", s.onSurface, s.surface)
        assertTextContrast("Dark onSurfaceVariant / surface", s.onSurfaceVariant, s.surface)
        assertTextContrast("Dark onSurface / surfaceContainer", s.onSurface, s.surfaceContainer)
        assertTextContrast("Dark onSurfaceVariant / surfaceContainer", s.onSurfaceVariant, s.surfaceContainer)
        assertTextContrast("Dark onSurface / surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh)
        assertTextContrast("Dark onSurfaceVariant / surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertTextContrast("Dark onSurface / surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest)
        assertTextContrast("Dark onSurfaceVariant / surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest)
        assertTextContrast("Dark onError / error", s.onError, s.error)
        assertTextContrast("Dark onErrorContainer / errorContainer", s.onErrorContainer, s.errorContainer)
        assertTextContrast("Dark primary / surface", s.primary, s.surface)
        assertTextContrast("Dark error / surface (exceeded state)", s.error, s.surface)
        assertTextContrast("Dark outline / surface (graphic/boundary)", s.outline, s.surface, minRatio = 3.0)
    }

    @Test
    fun amoled_black_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val s = amoledColors
        assertTextContrast("AMOLED onBackground / background", s.onBackground, s.background)
        assertTextContrast("AMOLED onSurface / surface", s.onSurface, s.surface)
        assertTextContrast("AMOLED onSurfaceVariant / surface", s.onSurfaceVariant, s.surface)
        assertTextContrast("AMOLED onSurface / surfaceContainer", s.onSurface, s.surfaceContainer)
        assertTextContrast("AMOLED onSurfaceVariant / surfaceContainer", s.onSurfaceVariant, s.surfaceContainer)
        assertTextContrast("AMOLED onSurface / surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh)
        assertTextContrast("AMOLED onSurfaceVariant / surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertTextContrast("AMOLED primary / surface", s.primary, s.surface)
        assertTextContrast("AMOLED error / surface (exceeded state)", s.error, s.surface)
        assertTextContrast("AMOLED outline / surface (graphic/boundary)", s.outline, s.surface, minRatio = 3.0)
    }
}
