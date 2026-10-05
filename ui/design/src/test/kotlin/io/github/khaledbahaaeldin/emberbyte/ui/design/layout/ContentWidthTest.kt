package io.github.khaledbahaaeldin.emberbyte.ui.design.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentWidthTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w1200dp-h400dp")
    fun max_width_constrained_to_600_dp() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.contentWidth().fillMaxHeight().testTag("content"))
            }
        }
        rule.onNodeWithTag("content").assertWidthIsEqualTo(600.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h400dp")
    fun smaller_width_takes_available_width() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.contentWidth().fillMaxHeight().testTag("content"))
            }
        }
        rule.onNodeWithTag("content").assertWidthIsEqualTo(360.dp)
    }
}
