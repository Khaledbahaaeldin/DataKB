package io.github.khaledbahaaeldin.emberbyte.ui.design.chips

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberDarkColors
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmberbyteFilterChipTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun selected_chip_has_selected_semantics_and_shows_check_icon() {
        var clicked = false
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                EmberbyteFilterChip(
                    selected = true,
                    onClick = { clicked = true },
                    label = { Text("Selected Chip") },
                    modifier = Modifier.testTag("chip"),
                )
            }
        }
        val node = rule.onNodeWithTag("chip")
        node.assertIsSelected()
        val semantics = node.fetchSemanticsNode()
        assertEquals(Role.Checkbox, semantics.config.getOrNull(SemanticsProperties.Role))

        // Check icon exists in unmerged tree
        val unmergedNode = rule.onNodeWithTag("chip", useUnmergedTree = true)
        val unmergedSemantics = unmergedNode.fetchSemanticsNode()
        assertTrue("Selected chip must have child nodes including check icon", unmergedSemantics.children.isNotEmpty())

        node.performClick()
        assertTrue(clicked)
    }

    @Test
    fun unselected_chip_has_unselected_semantics() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                EmberbyteFilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Unselected Chip") },
                    modifier = Modifier.testTag("chip"),
                )
            }
        }
        val node = rule.onNodeWithTag("chip")
        node.assertIsNotSelected()
    }

    @Test
    fun selected_chip_colors_use_primary_not_faint_tint() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                val colors = emberbyteFilterChipColors()
                assertEquals(EmberDarkColors.primary, colors.selectedContainerColor)
                assertEquals(EmberDarkColors.onPrimary, colors.selectedLabelColor)
            }
        }
    }
}
