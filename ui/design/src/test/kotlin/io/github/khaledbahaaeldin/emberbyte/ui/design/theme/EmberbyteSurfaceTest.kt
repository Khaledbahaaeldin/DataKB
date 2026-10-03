package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmberbyteSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun content_gets_the_on_background_colour_in_dark_theme() {
        var content = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                expected = MaterialTheme.colorScheme.onBackground
                EmberbyteSurface { content = LocalContentColor.current }
            }
        }
        assertEquals(expected, content)
    }

    @Test fun amoled_black_reaches_the_surface_colour() {
        var background = Color.Unspecified
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false, amoledBlack = true) {
                EmberbyteSurface { background = MaterialTheme.colorScheme.background }
            }
        }
        assertEquals(Color.Black, background)
    }
}
