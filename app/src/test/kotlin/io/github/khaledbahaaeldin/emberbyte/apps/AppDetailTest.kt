package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.data.budget.RoomBudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.spike.RoomSpikeRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneOffset.UTC
    private val start = Instant.parse("2026-09-29T00:00:00Z") // a Tuesday

    private fun point(day: Long, mobile: Long, wifi: Long = 0) = UsagePoint(start.plusSeconds(day * 86_400), mobile, wifi)

    @Test fun known_app_uses_its_label_and_totals() {
        val apps = listOf(AppUsage("com.video", "Video", 1, 600, 400, null), AppUsage("com.chat", "Chat", 2, 5, 5, null))
        val state = buildAppDetailUiState("com.video", apps, listOf(point(0, 100_000_000)), ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals("Video", state.label)
        assertEquals(1_000L, state.totalBytes)
        assertEquals(600L, state.mobileBytes)
        assertEquals(400L, state.wifiBytes)
        assertTrue(state.loaded)
        assertEquals("Tue", state.bars.single().label)
        assertEquals("Tuesday, 100 megabytes", state.bars.single().description)
    }

    @Test fun an_app_without_usage_in_range_falls_back_to_its_package_name_and_the_series_totals() {
        val state = buildAppDetailUiState(
            "com.quiet", emptyList(), listOf(point(0, 10, 5), point(1, 20, 0)), ByteUnits.DECIMAL, zone, Locale.ENGLISH,
        )
        assertEquals("com.quiet", state.label)
        assertEquals(35L, state.totalBytes)
        assertEquals(30L, state.mobileBytes)
        assertEquals(5L, state.wifiBytes)
        assertEquals(2, state.bars.size)
    }

    @Test fun the_view_model_combines_apps_and_series() = runTest(dispatcher) {
        val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), zone)
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val budgetStore = InMemoryBudgetStore()
        val spikeStore = InMemorySpikeStore()
        val vm = AppDetailViewModel(
            packageName = "com.android.chrome",
            usage = FakeUsageRepository(clock, MutableSharedFlow()),
            settings = FakeSettingsRepository(),
            budgets = RoomBudgetRepository(budgetStore, planStore, usageStore, clock, ticks = flowOf(clock.instant())),
            spikes = RoomSpikeRepository(spikeStore),
            plans = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(clock.instant())),
            is24Hour = { true },
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(LocalDate.of(2026, 10, 5)),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Chrome", vm.uiState.value.label)
        assertEquals(212_000_000L, vm.uiState.value.totalBytes)
        assertEquals(7, vm.uiState.value.bars.size)
        job.cancel()
    }
}
