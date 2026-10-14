package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberLightColors
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.resolveColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TileContrastTest {
    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = fg.compositeOver(bg).luminance() + 0.05
        val l2 = bg.luminance() + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast ($ratio) must be >= $minRatio", ratio >= minRatio)
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

    @Test
    fun old_progress_ring_track_at_0_percent_failed_contrast_at_1_05() {
        val lightTrack = EmberLightColors.surfaceContainerHighest
        val lightCard = EmberLightColors.surfaceContainerHigh
        val ratio = contrast(lightTrack, lightCard)
        assertTrue("Old track against card ($ratio) was < 3.0", ratio < 3.0)
    }

    @Test
    fun old_selected_chip_container_failed_contrast_at_1_48() {
        val darkSelected = EmberDarkColors.secondaryContainer
        val darkUnselected = EmberDarkColors.surfaceContainer
        val ratio = contrast(darkSelected, darkUnselected)
        assertTrue("Old selected chip against unselected ($ratio) was < 3.0", ratio < 3.0)
    }

    private fun assertSchemeMeetsWcag(schemeName: String, s: ColorScheme) {
        // Core text
        assertContrast("$schemeName onPrimary / primary", s.onPrimary, s.primary)
        assertContrast("$schemeName onPrimaryContainer / primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertContrast("$schemeName onSecondary / secondary", s.onSecondary, s.secondary)
        assertContrast("$schemeName onSecondaryContainer / secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertContrast("$schemeName onTertiary / tertiary", s.onTertiary, s.tertiary)
        assertContrast("$schemeName onTertiaryContainer / tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertContrast("$schemeName onBackground / background", s.onBackground, s.background)
        assertContrast("$schemeName onSurface / surface", s.onSurface, s.surface)
        assertContrast("$schemeName onSurfaceVariant / surface", s.onSurfaceVariant, s.surface)
        assertContrast("$schemeName onSurface / surfaceContainer", s.onSurface, s.surfaceContainer)
        assertContrast("$schemeName onSurfaceVariant / surfaceContainer", s.onSurfaceVariant, s.surfaceContainer)
        assertContrast("$schemeName onSurface / surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh)
        assertContrast("$schemeName onSurfaceVariant / surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertContrast("$schemeName onSurface / surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest)
        assertContrast("$schemeName onSurfaceVariant / surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest)
        assertContrast("$schemeName onError / error", s.onError, s.error)
        assertContrast("$schemeName onErrorContainer / errorContainer", s.onErrorContainer, s.errorContainer)

        // Tertiary text on surfaceContainerHigh (PlanCard forecast line)
        assertContrast("$schemeName tertiary / surfaceContainerHigh (PlanCard forecast)", s.tertiary, s.surfaceContainerHigh, minRatio = 4.5)

        // Ring arcs on card (primary/tertiary/error)
        assertContrast("$schemeName primary / surfaceContainerHigh (ring arc)", s.primary, s.surfaceContainerHigh, minRatio = 3.0)
        assertContrast("$schemeName tertiary / surfaceContainerHigh (ring arc warning)", s.tertiary, s.surfaceContainerHigh, minRatio = 3.0)
        assertContrast("$schemeName error / surfaceContainerHigh (ring arc error)", s.error, s.surfaceContainerHigh, minRatio = 3.0)

        // Ring track at 0% (outline on card >= 3:1)
        assertContrast("$schemeName outline / surfaceContainerHigh (ring track 0%)", s.outline, s.surfaceContainerHigh, minRatio = 3.0)

        // Selected chip container (primary) vs unselected container (surfaceContainer / surface >= 3:1)
        assertContrast("$schemeName primary / surfaceContainer (selected chip container)", s.primary, s.surfaceContainer, minRatio = 3.0)
        assertContrast("$schemeName primary / surface (selected chip container)", s.primary, s.surface, minRatio = 3.0)

        // Indicators & graphics on surface
        assertContrast("$schemeName primary / surface", s.primary, s.surface, minRatio = 3.0)
        assertContrast("$schemeName error / surface (exceeded state)", s.error, s.surface, minRatio = 3.0)
        assertContrast("$schemeName outline / surface (boundary/graphic)", s.outline, s.surface, minRatio = 3.0)
    }

    @Test
    fun static_light_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        assertSchemeMeetsWcag("Static Light", EmberLightColors)
    }

    @Test
    fun static_dark_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        assertSchemeMeetsWcag("Static Dark", EmberDarkColors)
    }

    @Test
    fun amoled_black_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val amoled = resolveColorScheme(
            darkTheme = true,
            dynamicColor = false,
            amoledBlack = true,
            sdkInt = 34,
            dynamicLight = { EmberLightColors },
            dynamicDark = { EmberDarkColors },
        )
        assertSchemeMeetsWcag("AMOLED Black", amoled)
    }

    @Test
    fun dynamic_light_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dynamicLight = resolveColorScheme(
            darkTheme = false,
            dynamicColor = true,
            amoledBlack = false,
            sdkInt = 34,
            dynamicLight = { dynamicLightColorScheme(context) },
            dynamicDark = { dynamicDarkColorScheme(context) },
        )
        assertSchemeMeetsWcag("Dynamic Light", dynamicLight)
    }

    @Test
    fun dynamic_dark_palette_contrast_meets_wcag_aa_for_all_text_and_indicators() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dynamicDark = resolveColorScheme(
            darkTheme = true,
            dynamicColor = true,
            amoledBlack = false,
            sdkInt = 34,
            dynamicLight = { dynamicLightColorScheme(context) },
            dynamicDark = { dynamicDarkColorScheme(context) },
        )
        assertSchemeMeetsWcag("Dynamic Dark", dynamicDark)
    }
}
