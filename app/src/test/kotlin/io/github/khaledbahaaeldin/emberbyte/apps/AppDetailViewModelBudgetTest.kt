package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.budget.RoomBudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.spike.RoomSpikeRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailViewModelBudgetTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val date = LocalDate.of(2026, 10, 5)
    private val zone = ZoneOffset.UTC
    private val clock = Clock.fixed(now, zone)
    private val planStore = InMemoryPlanStore()
    private val budgetStore = InMemoryBudgetStore()
    private val spikeStore = InMemorySpikeStore()
    private val usage = InMemoryUsageStore()
    private val plans = RoomPlanRepository(planStore, usage, clock, ticks = flowOf(now))
    private val budgets = RoomBudgetRepository(budgetStore, planStore, usage, clock, ticks = flowOf(now))
    private val spikes = RoomSpikeRepository(spikeStore)
    private val settings = FakeSettingsRepository()
    private val usageRepo = FakeUsageRepository(clock, MutableSharedFlow())
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(pkg: String = "com.video") =
        AppDetailViewModel(
            packageName = pkg,
            usage = usageRepo,
            settings = settings,
            budgets = budgets,
            spikes = spikes,
            plans = plans,
            is24Hour = { true },
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(date),
        )

    private fun TestScope.started(vm: AppDetailViewModel): Job {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return job
    }

    @Test fun test_1_no_budget_starts_with_null_budget_and_null_editor() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)
        assertNull(vm.uiState.value.budget)
        assertNull(vm.uiState.value.editor)
        job.cancel()
    }

    @Test fun test_2_open_budget_editor_opens_defaults_or_prefilled_if_existing() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)

        // No budget -> default editor
        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        advanceUntilIdle()
        val defaultEditor = vm.uiState.value.editor
        assertNotNull(defaultEditor)
        assertEquals("", defaultEditor!!.limitText)
        assertEquals(CapUnit.GB, defaultEditor.unit)
        assertEquals(BudgetPeriod.DAILY, defaultEditor.period)
        assertNull(defaultEditor.planId)
        assertEquals(80, defaultEditor.warnAtPercent)
        assertFalse(defaultEditor.isEdit)

        // Close editor
        vm.onEvent(AppDetailEvent.CloseBudgetEditor)
        advanceUntilIdle()
        assertNull(vm.uiState.value.editor)

        // Create an existing budget in store
        budgets.upsert(AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.DAILY, null, 90, true))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.budget)

        // Open editor -> prefilled with existing budget
        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        advanceUntilIdle()
        val prefilled = vm.uiState.value.editor
        assertNotNull(prefilled)
        assertEquals("750", prefilled!!.limitText)
        assertEquals(CapUnit.MB, prefilled.unit)
        assertEquals(BudgetPeriod.DAILY, prefilled.period)
        assertNull(prefilled.planId)
        assertEquals(90, prefilled.warnAtPercent)
        assertTrue(prefilled.isEdit)

        job.cancel()
    }

    @Test fun test_3_save_budget_creates_the_budget_and_closes_editor() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)

        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        vm.onEvent(AppDetailEvent.SetBudgetLimit("750"))
        vm.onEvent(AppDetailEvent.SetBudgetUnit(CapUnit.MB))
        vm.onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.DAILY))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertNull(vm.uiState.value.editor)
        val status = budgets.observeStatuses().first().firstOrNull { it.budget.packageName == "com.video" }
        assertNotNull(status)
        assertEquals(750_000_000L, status!!.budget.limitBytes)
        assertEquals(BudgetPeriod.DAILY, status.budget.period)
        assertNotNull(vm.uiState.value.budget)

        job.cancel()
    }

    @Test fun test_4_invalid_input_shows_errors_and_writes_nothing() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)

        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.editor!!.errors.containsKey("limitBytes"))
        assertTrue(budgets.observeStatuses().first().isEmpty())

        vm.onEvent(AppDetailEvent.SetBudgetLimit("10"))
        vm.onEvent(AppDetailEvent.SetBudgetWarn(0))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.editor!!.errors.containsKey("warnAtPercent"))
        assertTrue(budgets.observeStatuses().first().isEmpty())

        job.cancel()
    }

    @Test fun test_5_set_budget_period_cycle_lists_plans_and_stores_plan_choice() = runTest(dispatcher) {
        val plan = Plan(1, "Main", 1, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, zone), Rollover.None)
        planStore.upsertPlan(plan)
        advanceUntilIdle()

        val vm = vm()
        val job = started(vm)

        assertEquals(1, vm.uiState.value.planOptions.size)
        assertEquals("Main", vm.uiState.value.planOptions.first().name)

        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        vm.onEvent(AppDetailEvent.SetBudgetLimit("5"))
        vm.onEvent(AppDetailEvent.SetBudgetUnit(CapUnit.GB))
        vm.onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.CYCLE))
        vm.onEvent(AppDetailEvent.SetBudgetPlan(1L))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertNull(vm.uiState.value.editor)
        val status = budgets.observeStatuses().first().first { it.budget.packageName == "com.video" }
        assertEquals(1L, status.budget.planId)
        assertEquals(BudgetPeriod.CYCLE, status.budget.period)
        assertEquals("this cycle (Main)", vm.uiState.value.budget?.periodText)

        // Now test when there are no plans: CYCLE without plan says "this month"
        planStore.setArchived(1L, true)
        budgetStore.deleteBudget(status.budget.id)
        advanceUntilIdle()

        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        vm.onEvent(AppDetailEvent.SetBudgetLimit("5"))
        vm.onEvent(AppDetailEvent.SetBudgetUnit(CapUnit.GB))
        vm.onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.CYCLE))
        vm.onEvent(AppDetailEvent.SetBudgetPlan(null))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertEquals("this month", vm.uiState.value.budget?.periodText)

        job.cancel()
    }

    @Test fun test_6_remove_budget_deletes_it() = runTest(dispatcher) {
        budgets.upsert(AppBudget(1, "com.video", 500_000_000L, BudgetPeriod.DAILY, null, 80, true))
        val vm = vm()
        val job = started(vm)

        assertNotNull(vm.uiState.value.budget)
        vm.onEvent(AppDetailEvent.RemoveBudget)
        advanceUntilIdle()

        assertNull(vm.uiState.value.budget)
        assertTrue(budgets.observeStatuses().first().isEmpty())

        job.cancel()
    }

    @Test fun test_7_budget_status_follows_new_usage() = runTest(dispatcher) {
        usage.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        budgets.upsert(AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.DAILY, null, 80, true))
        val vm = vm()
        val job = started(vm)

        assertEquals("0 B of 750 MB", vm.uiState.value.budget?.usedText)

        // Add a 2-hour window row of usage
        usage.upsertHourly(listOf(HourlyUsage(Instant.parse("2026-10-05T10:00:00Z"), 10001, NetworkKind.MOBILE, 1, 612_000_000L, 0L)))
        advanceUntilIdle()

        assertEquals("612 MB of 750 MB", vm.uiState.value.budget?.usedText)

        job.cancel()
    }

    @Test fun test_8_spikes_list_undismissed_for_this_package_and_dismiss_removes_it() = runTest(dispatcher) {
        val s1 = SpikeEvent(1, "com.video", Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-05T11:00:00Z"), 120, 100_000_000L, 10_000_000L, 10f, false)
        val s2 = SpikeEvent(2, "com.other", Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-05T11:00:00Z"), 120, 100_000_000L, 10_000_000L, 10f, false)
        spikeStore.insertIfAbsent(s1)
        spikeStore.insertIfAbsent(s2)

        val vm = vm("com.video")
        val job = started(vm)

        assertEquals(1, vm.uiState.value.spikes.size)
        assertEquals(1L, vm.uiState.value.spikes.first().id)

        vm.onEvent(AppDetailEvent.DismissSpike(1L))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.spikes.isEmpty())

        job.cancel()
    }

    @Test fun test_9_units_follow_settings() = runTest(dispatcher) {
        usage.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        budgets.upsert(AppBudget(1, "com.video", 1024L * 1024 * 1024, BudgetPeriod.DAILY, null, 80, true))
        val vm = vm()
        val job = started(vm)

        assertEquals(ByteUnits.DECIMAL, vm.uiState.value.units)

        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()

        assertEquals(ByteUnits.BINARY, vm.uiState.value.units)
        assertTrue(vm.uiState.value.budget?.usedText?.contains("GiB") == true)

        job.cancel()
    }

    @Test fun test_10_clearing_errors_on_any_edit() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)

        vm.onEvent(AppDetailEvent.OpenBudgetEditor)
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()

        assertEquals(mapOf("limitBytes" to "Enter a number greater than zero"), vm.uiState.value.editor!!.errors)

        // SetBudgetLimit clears errors
        vm.onEvent(AppDetailEvent.SetBudgetLimit("5"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.isEmpty())

        // Re-trigger error
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.containsKey("limitBytes"))

        // SetBudgetUnit clears errors
        vm.onEvent(AppDetailEvent.SetBudgetUnit(CapUnit.MB))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.isEmpty())

        // Re-trigger error
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.containsKey("limitBytes"))

        // SetBudgetPeriod clears errors
        vm.onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.CYCLE))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.isEmpty())

        // Re-trigger error
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.containsKey("limitBytes"))

        // SetBudgetPlan clears errors
        vm.onEvent(AppDetailEvent.SetBudgetPlan(1L))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.isEmpty())

        // Re-trigger error
        vm.onEvent(AppDetailEvent.SetBudgetLimit("abc"))
        vm.onEvent(AppDetailEvent.SaveBudget)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.containsKey("limitBytes"))

        // SetBudgetWarn clears errors
        vm.onEvent(AppDetailEvent.SetBudgetWarn(50))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editor!!.errors.isEmpty())

        job.cancel()
    }
}
