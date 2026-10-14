package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import android.content.Context
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
class LicensesScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun settings_about_section_has_open_source_licences_row_that_navigates() {
        var openedLicenses = false
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SettingsContent(
                    settings = io.github.khaledbahaaeldin.emberbyte.data.Settings(),
                    onUpdate = {},
                    onBack = {},
                    versionName = "0.1.0",
                    onOpenSource = {},
                    onOpenBatterySettings = {},
                    onOpenLicenses = { openedLicenses = true },
                )
            }
        }

        rule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Open-source licences"))
        val row = rule.onNodeWithText("Open-source licences")
        row.assertIsDisplayed()
        row.performClick()
        assertTrue("Clicking Open-source licences must invoke onOpenLicenses", openedLicenses)
    }

    @Test
    fun licenses_screen_displays_back_button_and_licence_texts() {
        var backed = false
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                LicensesScreen(
                    onBack = { backed = true },
                    noticeContent = "Emberbyte\nBouncy Castle (Argon2id)\nMIT licence",
                    robotoFlexContent = "Roboto Flex OFL 1.1 licence text",
                )
            }
        }

        rule.onNodeWithText("Open-source licences").assertIsDisplayed()
        rule.onNodeWithText("Emberbyte\nBouncy Castle (Argon2id)\nMIT licence").assertIsDisplayed()
        rule.onNodeWithText("Roboto Flex OFL 1.1 licence text").assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        assertTrue("Back button in LicensesScreen must invoke onBack", backed)
    }
}
