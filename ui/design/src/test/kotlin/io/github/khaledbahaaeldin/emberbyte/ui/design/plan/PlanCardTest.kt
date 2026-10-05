package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
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
class PlanCardTest {
    @get:Rule val rule = createComposeRule()
    private val model = PlanCardUi(
        id = 3, name = "Main SIM", simLabel = "SIM 1 · Alpha", fraction = 0.62f, ringState = BudgetStateUi.Ok,
        percentText = "62%", usedText = "6.20 GB of 10.0 GB", remainingText = "3.80 GB left", daysLeftText = "9 days left",
        forecastText = "runs out Thu (±1 day)", isEstimated = false, chips = listOf("Boost +2.00 GB"), archived = false,
    )
    private fun show(m: PlanCardUi = model, onClick: () -> Unit = {}) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { PlanCard(m, onClick) }
    }

    @Test fun shows_the_numbers_the_sim_and_the_chips() {
        show()
        listOf("Main SIM", "SIM 1 · Alpha", "6.20 GB of 10.0 GB", "3.80 GB left", "9 days left", "runs out Thu (±1 day)", "Boost +2.00 GB")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun the_estimated_badge_only_appears_for_estimated_plans() {
        show(model.copy(isEstimated = true))
        rule.onNodeWithText("Estimated").assertIsDisplayed()
    }

    @Test fun an_exact_plan_has_no_badge() {
        show()
        rule.onNodeWithText("Estimated").assertDoesNotExist()
    }

    @Test fun missing_optional_parts_are_simply_left_out() {
        show(model.copy(simLabel = null, forecastText = null, chips = emptyList()))
        rule.onNodeWithText("Main SIM").assertIsDisplayed()
    }

    @Test fun tapping_the_card_reports_a_click_and_the_card_has_one_spoken_summary() {
        var clicked = 0
        show(onClick = { clicked++ })
        rule.onNodeWithText("Main SIM").performClick()
        assertEquals(1, clicked)
        rule.onNodeWithContentDescription("Main SIM, 62 percent used, 3.80 GB left, 9 days left").assertExists()
    }

    @Test fun an_archived_plan_is_labelled_archived() {
        show(model.copy(archived = true))
        rule.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test fun large_font_scale_keeps_percent_single_line_and_days_left_within_two_lines() {
        val testModel = model.copy(
            percentText = "100%",
            daysLeftText = "28 days left",
        )
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    PlanCard(testModel, onClick = {})
                }
            }
        }
        listOf("Main SIM", "SIM 1 · Alpha", "6.20 GB of 10.0 GB", "3.80 GB left", "28 days left", "runs out Thu (±1 day)", "Boost +2.00 GB")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }

        val percentNode = rule.onNodeWithText("100%", useUnmergedTree = true).assertIsDisplayed()
        val percentHeightDp = with(rule.density) { percentNode.fetchSemanticsNode().size.height.toDp() }
        assertTrue("Percent height ($percentHeightDp) must be <= 64.dp", percentHeightDp <= 64.dp)

        val daysLeftNode = rule.onNodeWithText("28 days left", useUnmergedTree = true).assertIsDisplayed()
        val daysHeightDp = with(rule.density) { daysLeftNode.fetchSemanticsNode().size.height.toDp() }
        assertTrue("Days left height ($daysHeightDp) must be <= 96.dp", daysHeightDp <= 96.dp)
    }

    @Test fun rtl_layout_direction_keeps_number_nodes_unbroken() {
        rule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    PlanCard(model, onClick = {})
                }
            }
        }
        rule.onNodeWithText("3.80 GB left").assertIsDisplayed()
        rule.onNodeWithText("9 days left").assertIsDisplayed()
    }
}
