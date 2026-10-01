package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmberbyteThemeTest {
    private val dynLight = EmberLightColors.copy(primary = Color.Red)
    private val dynDark = EmberDarkColors.copy(primary = Color.Blue)

    private fun resolve(dark: Boolean, dynamic: Boolean, amoled: Boolean, sdk: Int) =
        resolveColorScheme(dark, dynamic, amoled, sdk, { dynLight }, { dynDark })

    @Test fun static_light_below_android_12() =
        assertEquals(EmberLightColors, resolve(dark = false, dynamic = true, amoled = false, sdk = 30))

    @Test fun static_dark_below_android_12() =
        assertEquals(EmberDarkColors, resolve(dark = true, dynamic = true, amoled = false, sdk = 30))

    @Test fun dynamic_light_on_android_12() =
        assertEquals(dynLight, resolve(dark = false, dynamic = true, amoled = false, sdk = 31))

    @Test fun dynamic_dark_on_android_12() =
        assertEquals(dynDark, resolve(dark = true, dynamic = true, amoled = false, sdk = 34))

    @Test fun dynamic_disabled_uses_static_palette() =
        assertEquals(EmberDarkColors, resolve(dark = true, dynamic = false, amoled = false, sdk = 34))

    @Test fun amoled_makes_dark_surfaces_black() {
        val scheme = resolve(dark = true, dynamic = false, amoled = true, sdk = 34)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    @Test fun amoled_is_ignored_in_light_theme() =
        assertEquals(EmberLightColors, resolve(dark = false, dynamic = false, amoled = true, sdk = 34))

    @Test fun reduce_motion_only_when_scale_is_zero() {
        assertTrue(isReduceMotion(0f))
        assertFalse(isReduceMotion(1f))
        assertFalse(isReduceMotion(0.5f))
    }

    @Test fun amoled_over_dynamic_dark_blackens_three_surfaces_and_keeps_the_rest() {
        val lifted = dynDark.copy(
            background = Color.Gray,
            surface = Color.Gray,
            surfaceContainerLowest = Color.Gray,
            surfaceContainer = Color.DarkGray,
        )
        val scheme = resolveColorScheme(true, true, true, 34, { dynLight }, { lifted })
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.surfaceContainerLowest)
        assertEquals(Color.Blue, scheme.primary)
        assertEquals(Color.DarkGray, scheme.surfaceContainer)
    }
}
