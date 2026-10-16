package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun show(settings: Settings = Settings(), dataUiState: DataUiState = DataUiState()) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SettingsContent(
                    settings = settings,
                    onUpdate = {},
                    onBack = {},
                    versionName = "1.0.0",
                    onOpenSource = {},
                    onOpenBatterySettings = {},
                    dataUiState = dataUiState,
                )
            }
        }
    }

    @Test
    fun switches_in_settings_are_merged_with_their_labels_into_one_clickable_node_with_role_switch() {
        show(Settings(liveNotificationEnabled = true, useDynamicColor = false))

        // "Live notification" should be a single toggleable node merging the text and the switch
        val liveNode = rule.onNodeWithText("Live notification")
        liveNode.assertIsDisplayed()
        liveNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        liveNode.assertIsOn()

        // In the unmerged tree, there should not be a duplicate switch node without the label
        val unmergedLiveNodes = rule.onAllNodesWithText("Live notification", useUnmergedTree = true)
        unmergedLiveNodes.assertCountEquals(1)

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dynamic colour"))
        val dynamicColorNode = rule.onNodeWithText("Dynamic colour")
        dynamicColorNode.assertIsDisplayed()
        dynamicColorNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        dynamicColorNode.assertIsOff()
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun settings_touch_targets_are_at_least_48dp() {
        show()
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun data_dialogs_touch_targets_are_at_least_48dp() {
        // Passphrase dialog
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                CreateBackupPassphraseDialog(onDismiss = {}, onCreate = {})
            }
        }
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun restore_dialogs_touch_targets_are_at_least_48dp() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                RestoreWarningDialog(onDismiss = {}, onConfirm = {})
            }
        }
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun csv_range_dialog_touch_targets_are_at_least_48dp() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                CsvRangeDialog(onDismiss = {}, onSelectRange = {})
            }
        }
        assertNoTouchTargetSmallerThan48dp(rule)
    }

    @Test
    fun create_backup_dialog_at_font_scale_2_keeps_create_and_cancel_reachable_and_clickable() {
        var created = false
        var dismissed = false
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.625f, fontScale = 2f),
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    CreateBackupPassphraseDialog(
                        onDismiss = { dismissed = true },
                        onCreate = { created = true },
                    )
                }
            }
        }

        val rootHeight = rule.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }
        val imeHeightPx = (rootHeight * 0.40f).toInt()
        val imeTopPx = rootHeight - imeHeightPx

        io.github.khaledbahaaeldin.emberbyte.common.injectImeInsets(imeHeightPx)
        rule.waitForIdle()

        // Cancel button is displayed and clickable above IME
        val cancelButton = rule.onNodeWithText("Cancel")
        cancelButton.performScrollTo().assertIsDisplayed()
        val cancelBounds = cancelButton.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Cancel button bottom (${cancelBounds.bottom}) must be <= IME top ($imeTopPx)",
            cancelBounds.bottom <= imeTopPx,
        )
        cancelButton.performClick()
        assertTrue(dismissed)

        // Create button is displayed above IME
        val createButton = rule.onNodeWithText("Create")
        createButton.performScrollTo().assertIsDisplayed()
        val createBounds = createButton.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Create button bottom (${createBounds.bottom}) must be <= IME top ($imeTopPx)",
            createBounds.bottom <= imeTopPx,
        )
    }
}


