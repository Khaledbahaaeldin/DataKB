package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GapBannerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun gapBanner_renders_message_in_one_node_matching_model_text_under_rtl() {
        val message = "Not measured 15:41–15:47 because the app was stopped"
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    GapBanner(GapUi(message))
                }
            }
        }
        rule.onNodeWithText(message).assertIsDisplayed()
    }
}
