package io.github.khaledbahaaeldin.emberbyte.history

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.common.assertNoTouchTargetSmallerThan48dp
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 7)

    private fun createViewModel(): HistoryViewModel {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val baseDate = LocalDate.of(2026, 9, 24)
        val points = (0 until 14).map { dayOffset ->
            val d = baseDate.plusDays(dayOffset.toLong())
            val instant = d.atStartOfDay(zone).toInstant()
            val bytes = if (dayOffset == 13) 2_100_000_000L else 100_000_000L
            UsagePoint(instant, bytes, 0L)
        }
        val usage = object : io.github.khaledbahaaeldin.emberbyte.data.UsageRepository {
            override fun observeLiveSpeed() = kotlinx.coroutines.flow.emptyFlow<io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed>()
            override fun observeToday(filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter) =
                flowOf(io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage(today, 0L, 0L))
            override fun observeSeries(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
            ) = flowOf(points)
            override fun observeApps(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
                sort: io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort,
            ) = kotlinx.coroutines.flow.emptyFlow<List<io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage>>()
            override fun observeAppSeries(
                packageName: String,
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
            ) = kotlinx.coroutines.flow.emptyFlow<List<UsagePoint>>()
            override fun observeCoverage() = kotlinx.coroutines.flow.emptyFlow<io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus>()
            override suspend fun refreshNow() = io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome.Success(Unit)
        }

        return HistoryViewModel(
            usage = usage,
            settings = FakeSettingsRepository(),
            permissions = FakePermissionRepository(PermissionState(usageAccess = true, notifications = true, phoneState = true)),
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(today),
        )
    }

    private fun show(vm: HistoryViewModel = createViewModel()) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                HistoryScreen(viewModel = vm, onBack = {})
            }
        }
    }

    @Test
    fun history_chart_has_summary_node_and_accessibility_click_selects_bar() {
        val vm = createViewModel()
        show(vm)
        rule.waitForIdle()

        // 1. Chart summary node exists
        rule.onNodeWithContentDescription(
            "Daily usage, last 14 days, highest Wednesday, 7 October, 2.10 gigabytes",
        ).assertExists()

        // 2. Individual bar can be clicked via accessibility click, selecting it
        val barNode = rule.onNodeWithContentDescription("Wednesday, 7 October, 2.10 gigabytes")
        barNode.assert(hasClickAction())
        barNode.performClick()
        rule.waitForIdle()

        // Bar should be selected in state
        assertEquals(13, vm.uiState.value.selectedIndex)
    }

    @Test
    fun selected_bar_persists_when_series_bytes_update_and_resets_on_granularity_change() {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val baseDate = LocalDate.of(2026, 9, 24)
        val initialPoints = (0 until 14).map { dayOffset ->
            val d = baseDate.plusDays(dayOffset.toLong())
            val instant = d.atStartOfDay(zone).toInstant()
            val bytes = if (dayOffset == 13) 2_100_000_000L else 100_000_000L
            UsagePoint(instant, bytes, 0L)
        }
        val pointsFlow = kotlinx.coroutines.flow.MutableStateFlow(initialPoints)
        val usage = object : io.github.khaledbahaaeldin.emberbyte.data.UsageRepository {
            override fun observeLiveSpeed() = kotlinx.coroutines.flow.emptyFlow<io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed>()
            override fun observeToday(filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter) =
                flowOf(io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage(today, 0L, 0L))
            override fun observeSeries(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
            ) = pointsFlow
            override fun observeApps(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
                sort: io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort,
            ) = kotlinx.coroutines.flow.emptyFlow<List<io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage>>()
            override fun observeAppSeries(
                packageName: String,
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
            ) = kotlinx.coroutines.flow.emptyFlow<List<UsagePoint>>()
            override fun observeCoverage() = kotlinx.coroutines.flow.emptyFlow<io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus>()
            override suspend fun refreshNow() = io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome.Success(Unit)
        }

        val vm = HistoryViewModel(
            usage = usage,
            settings = FakeSettingsRepository(),
            permissions = FakePermissionRepository(PermissionState(usageAccess = true, notifications = true, phoneState = true)),
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(today),
        )

        show(vm)
        rule.waitForIdle()

        // 1. Select the last bar
        val barNode = rule.onNodeWithContentDescription("Wednesday, 7 October, 2.10 gigabytes")
        barNode.performClick()
        rule.waitForIdle()
        assertEquals(13, vm.uiState.value.selectedIndex)

        // 2. Points update (bytes increase on the last bar, simulating sampler update)
        val updatedPoints = initialPoints.mapIndexed { idx, pt ->
            if (idx == 13) pt.copy(mobileBytes = pt.mobileBytes + 50_000_000L) else pt
        }
        pointsFlow.value = updatedPoints
        rule.waitForIdle()

        // Selection should NOT be wiped out by live points update
        assertEquals(13, vm.uiState.value.selectedIndex)

        // 3. User switches granularity to Week -> selection resets to null
        rule.onNodeWithText("Week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertNull(vm.uiState.value.selectedIndex)
    }

    @Test
    fun history_buttons_and_filter_chips_have_accessible_names() {
        show()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithText("Day").assertIsDisplayed()
        rule.onNodeWithText("Week").assertIsDisplayed()
        rule.onNodeWithText("Month").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun history_touch_targets_are_at_least_48dp() {
        show()
        assertNoTouchTargetSmallerThan48dp(rule)
    }
}
