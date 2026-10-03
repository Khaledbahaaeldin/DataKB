package io.github.khaledbahaaeldin.emberbyte.history

import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 7) // a Wednesday

    @Test fun day_range_is_the_last_fourteen_days() {
        val range = rangeForHistory(Granularity.DAY, today, zone)
        assertEquals(Instant.parse("2026-09-24T00:00:00Z"), range.from)
        assertEquals(Instant.parse("2026-10-08T00:00:00Z"), range.to)
    }

    @Test fun week_range_is_twelve_weeks_from_a_monday() =
        assertEquals(Instant.parse("2026-07-20T00:00:00Z"), rangeForHistory(Granularity.WEEK, today, zone).from)

    @Test fun month_range_is_twelve_months_from_the_first() =
        assertEquals(Instant.parse("2025-11-01T00:00:00Z"), rangeForHistory(Granularity.MONTH, today, zone).from)

    private fun point(iso: String, mobile: Long, wifi: Long = 0) = UsagePoint(Instant.parse(iso), mobile, wifi)

    @Test fun bar_labels_and_descriptions_follow_the_granularity() {
        val day = historyBars(listOf(point("2026-10-05T00:00:00Z", 100_000_000)), Granularity.DAY, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("5", day.label)
        assertEquals("Monday, 5 October, 100 megabytes", day.description)
        val week = historyBars(listOf(point("2026-10-05T00:00:00Z", 1)), Granularity.WEEK, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("5 Oct", week.label)
        assertEquals("Week of 5 October, 1 bytes", week.description)
        val month = historyBars(listOf(point("2026-10-01T00:00:00Z", 2_000_000_000)), Granularity.MONTH, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("Oct", month.label)
        assertEquals("October 2026, 2.00 gigabytes", month.description)
    }

    @Test fun total_and_average_cover_all_buckets_including_empty_ones() {
        val points = listOf(point("2026-10-05T00:00:00Z", 300), point("2026-10-06T00:00:00Z", 0, 100), point("2026-10-07T00:00:00Z", 0))
        val state = buildHistoryUiState(Granularity.DAY, points, ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals(400L, state.totalBytes)
        assertEquals(133L, state.averageBytes)
        assertEquals(3, state.bars.size)
    }

    @Test fun no_points_means_zero_totals() {
        val state = buildHistoryUiState(Granularity.DAY, emptyList(), ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals(0L, state.totalBytes); assertEquals(0L, state.averageBytes)
    }

    @Test fun the_view_model_switches_granularity() = runTest(dispatcher) {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val vm = HistoryViewModel(FakeUsageRepository(clock, MutableSharedFlow()), FakeSettingsRepository(), FakePermissionRepository(), clock, Locale.ENGLISH, flowOf(today))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(Granularity.DAY, vm.uiState.value.granularity)
        vm.onEvent(HistoryEvent.SetGranularity(Granularity.MONTH))
        advanceUntilIdle()
        assertEquals(Granularity.MONTH, vm.uiState.value.granularity)
        job.cancel()
    }

    @Test fun history_ui_state_needs_usage_access_follows_permission() = runTest(dispatcher) {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val perms = FakePermissionRepository(PermissionState(usageAccess = false, notifications = true, phoneState = true, vpnConsentGranted = false))
        val vm = HistoryViewModel(FakeUsageRepository(clock, MutableSharedFlow()), FakeSettingsRepository(), perms, clock, Locale.ENGLISH, flowOf(today))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.needsUsageAccess)
        perms.set(PermissionState(usageAccess = true, notifications = true, phoneState = true, vpnConsentGranted = false))
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.needsUsageAccess)
        job.cancel()
    }
}
