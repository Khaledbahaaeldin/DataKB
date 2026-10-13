package io.github.khaledbahaaeldin.emberbyte.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun history_screen_opens_scrolled_to_the_newest_period() {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val baseDate = LocalDate.of(2026, 9, 24)
        val points = (0 until 14).map { dayOffset ->
            val d = baseDate.plusDays(dayOffset.toLong())
            val instant = d.atStartOfDay(zone).toInstant()
            UsagePoint(instant, 100_000_000L, 0L)
        }
        val usage = object : io.github.khaledbahaaeldin.emberbyte.data.UsageRepository {
            override fun observeLiveSpeed(): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeToday(filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage> =
                kotlinx.coroutines.flow.flowOf(io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage(today, 0L, 0L))
            override fun observeSeries(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
            ) = kotlinx.coroutines.flow.flowOf(points)
            override fun observeApps(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
                sort: io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort,
            ): kotlinx.coroutines.flow.Flow<List<io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage>> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeAppSeries(
                packageName: String,
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
            ): kotlinx.coroutines.flow.Flow<List<UsagePoint>> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeCoverage(): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus> = kotlinx.coroutines.flow.emptyFlow()
            override suspend fun refreshNow(): io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome<Unit> = io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome.Success(Unit)
        }

        val vm = HistoryViewModel(
            usage = usage,
            settings = FakeSettingsRepository(),
            permissions = FakePermissionRepository(PermissionState(usageAccess = true, notifications = true, phoneState = true)),
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(today),
        )

        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                HistoryScreen(viewModel = vm, onBack = {})
            }
        }
        rule.waitForIdle()

        // 7 Oct is the newest (today's) bar, 24 Sep is the oldest bar
        rule.onNodeWithContentDescription("Wednesday, 7 October, 100 megabytes").assertIsDisplayed()
        rule.onNodeWithContentDescription("Thursday, 24 September, 100 megabytes").assertIsNotDisplayed()
    }

    @Test
    fun switching_granularity_scrolls_to_the_newest_period_again() {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val usage = object : io.github.khaledbahaaeldin.emberbyte.data.UsageRepository {
            override fun observeLiveSpeed(): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeToday(filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage> =
                kotlinx.coroutines.flow.flowOf(io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage(today, 0L, 0L))
            override fun observeSeries(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
            ): kotlinx.coroutines.flow.Flow<List<UsagePoint>> {
                return if (granularity == Granularity.DAY) {
                    val baseDate = LocalDate.of(2026, 9, 24)
                    val pts = (0 until 14).map { dayOffset ->
                        val d = baseDate.plusDays(dayOffset.toLong())
                        UsagePoint(d.atStartOfDay(zone).toInstant(), 100_000_000L, 0L)
                    }
                    kotlinx.coroutines.flow.flowOf(pts)
                } else {
                    // Week granularity: 12 weeks
                    val baseDate = LocalDate.of(2026, 7, 20)
                    val pts = (0 until 12).map { weekOffset ->
                        val d = baseDate.plusWeeks(weekOffset.toLong())
                        UsagePoint(d.atStartOfDay(zone).toInstant(), 200_000_000L, 0L)
                    }
                    kotlinx.coroutines.flow.flowOf(pts)
                }
            }
            override fun observeApps(
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                filter: io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter,
                sort: io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort,
            ): kotlinx.coroutines.flow.Flow<List<io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage>> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeAppSeries(
                packageName: String,
                range: io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange,
                granularity: Granularity,
            ): kotlinx.coroutines.flow.Flow<List<UsagePoint>> = kotlinx.coroutines.flow.emptyFlow()
            override fun observeCoverage(): kotlinx.coroutines.flow.Flow<io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus> = kotlinx.coroutines.flow.emptyFlow()
            override suspend fun refreshNow(): io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome<Unit> = io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome.Success(Unit)
        }

        val vm = HistoryViewModel(
            usage = usage,
            settings = FakeSettingsRepository(),
            permissions = FakePermissionRepository(PermissionState(usageAccess = true, notifications = true, phoneState = true)),
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(today),
        )

        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                HistoryScreen(viewModel = vm, onBack = {})
            }
        }
        rule.waitForIdle()

        // Switch to Week
        rule.onNodeWithText("Week").performClick()
        rule.waitForIdle()

        // Week of 5 Oct is the newest week, Week of 20 Jul is the oldest week
        rule.onNodeWithContentDescription("Week of 5 October, 200 megabytes").assertIsDisplayed()
        rule.onNodeWithContentDescription("Week of 20 July, 200 megabytes").assertIsNotDisplayed()
    }

}
