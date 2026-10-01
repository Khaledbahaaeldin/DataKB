package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorphingNumberTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun exposes_one_node_with_the_plain_language_description() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                MorphingNumber(
                    bytes = 1_240_000_000L,
                    throughputBps = 0L,
                    units = ByteUnits.DECIMAL,
                    contentDescription = "1.24 gigabytes used today",
                    animate = false,
                )
            }
        }
        rule.onNodeWithContentDescription("1.24 gigabytes used today").assertIsDisplayed()
    }
}
