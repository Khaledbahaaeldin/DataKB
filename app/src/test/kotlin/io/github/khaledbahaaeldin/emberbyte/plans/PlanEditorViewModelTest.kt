package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.lifecycle.SavedStateHandle
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.alert.RoomAlertRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
class PlanEditorViewModelTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planStore = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private val plans = RoomPlanRepository(planStore, usage, clock, ticks = flowOf(now))
    private val alerts = RoomAlertRepository(planStore)
    private val settings = FakeSettingsRepository()
    private val permissions = FakePermissionRepository()
    private val sims = FakeSimInfoSource(listOf(SimInfo(5, 0, "Alpha"), SimInfo(8, 1, "Beta")), defaultId = 5)
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(planId: Long = 0L, handle: SavedStateHandle = SavedStateHandle()) =
        PlanEditorViewModel(planId, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L, savedStateHandle = handle)

    private fun TestScope.started(vm: PlanEditorViewModel): Job { val job = launch { vm.uiState.collect {} }; advanceUntilIdle(); return job }
    private fun PlanEditorViewModel.fill(name: String = "Main", cap: String = "10") {
        onEvent(PlanEditorEvent.SetName(name)); onEvent(PlanEditorEvent.SetCap(cap))
    }

    @Test fun a_new_editor_starts_empty_with_default_alerts() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        val s = vm.uiState.value
        assertTrue(s.isNew); assertFalse(s.loading); assertFalse(s.notFound)
        assertEquals("", s.form.name)
        assertEquals(setOf(80, 100), s.form.alertThresholds)
        assertEquals(LocalDate.of(2026, 10, 15), s.form.anchor)           // the anchor defaults to today in the device zone
        job.cancel()
    }

    @Test fun saving_an_invalid_form_shows_errors_and_saves_nothing() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetCap("abc")); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(setOf("name", "capBytes"), vm.uiState.value.errors.keys)
        vm.onEvent(PlanEditorEvent.SetCap("10")); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()   // the name is still blank: the repository says so
        assertEquals("name", vm.uiState.value.errors.keys.single())
        assertTrue(plans.observePlans(true).first().isEmpty())
        assertFalse(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun one_save_with_multiple_errors_shows_all_errors_at_once_and_saves_nothing() = runTest(dispatcher) {
        val vm = vm()
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetName(""))
        vm.onEvent(PlanEditorEvent.SetCap("abc"))
        vm.onEvent(PlanEditorEvent.SetDayOfMonth(0))
        vm.onEvent(PlanEditorEvent.Save)
        advanceUntilIdle()

        assertEquals(setOf("name", "capBytes", "cycle.dayOfMonth"), vm.uiState.value.errors.keys)
        assertTrue(plans.observePlans(true).first().isEmpty())
        assertFalse(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun editing_a_field_clears_the_errors() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.isNotEmpty())
        vm.onEvent(PlanEditorEvent.SetName("Main")); advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.isEmpty())
        job.cancel()
    }

    @Test fun a_valid_form_creates_the_plan_with_alert_rules_and_marks_saved() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.SetSim(5)); vm.onEvent(PlanEditorEvent.SetDayOfMonth(12))
        vm.onEvent(PlanEditorEvent.ToggleThreshold(50)); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        val plan = plans.observePlans().first().single()
        assertEquals("Main", plan.name); assertEquals(5, plan.subscriptionId); assertEquals(10_000_000_000L, plan.capBytes)
        assertEquals(Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneOffset.UTC), plan.cycle)
        assertEquals(listOf(50, 80, 100), alerts.observeRules(plan.id).first().map { it.thresholdPercent })
        assertTrue(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun binary_units_are_honoured() = runTest(dispatcher) {
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        val vm = vm(); val job = started(vm)
        vm.fill(cap = "10"); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(10L * 1024 * 1024 * 1024, plans.observePlans().first().single().capBytes)
        job.cancel()
    }

    @Test fun a_repository_error_is_mapped_to_its_field() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.SetCycleKind(CycleKind.EveryNDays)); vm.onEvent(PlanEditorEvent.SetEveryDays(0))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(setOf("cycle.days"), vm.uiState.value.errors.keys)
        job.cancel()
    }

    @Test fun an_existing_plan_loads_into_the_form_and_saves_changes() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Old", null, 5_000_000_000L, Cycle.MonthlyOnDay(3, LocalTime.of(8, 0), ZoneOffset.UTC), Rollover.Full)) as Outcome.Success).value
        alerts.upsertRule(AlertRule(0, id, 90, true))
        val vm = vm(id); val job = started(vm)
        val s = vm.uiState.value
        assertFalse(s.isNew); assertEquals("Old", s.form.name); assertEquals("5", s.form.capText); assertEquals(CapUnit.GB, s.form.capUnit)
        assertEquals(3, s.form.dayOfMonth); assertEquals(LocalTime.of(8, 0), s.form.renewalTime); assertEquals(RolloverKind.Full, s.form.rolloverKind)
        assertEquals(setOf(90), s.form.alertThresholds)
        vm.onEvent(PlanEditorEvent.SetName("New")); vm.onEvent(PlanEditorEvent.ToggleThreshold(90)); vm.onEvent(PlanEditorEvent.ToggleThreshold(50))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals("New", plans.observePlans().first().single().name)
        assertEquals(listOf(50), alerts.observeRules(id).first().map { it.thresholdPercent })       // 90 removed, 50 added
        job.cancel()
    }

    @Test fun a_missing_plan_id_is_reported() = runTest(dispatcher) {
        val vm = vm(99L); val job = started(vm)
        assertTrue(vm.uiState.value.notFound)
        job.cancel()
    }

    @Test fun add_ons_and_free_windows_are_saved_updated_and_deleted() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.AddAddOn); vm.onEvent(PlanEditorEvent.AddFreeRule)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = "Boost", amountText = "2")))
        vm.onEvent(PlanEditorEvent.UpdateFreeRule(0, vm.uiState.value.form.freeRules[0].copy(label = "Night")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        val id = plans.observePlans().first().single().id
        assertEquals(listOf("Boost"), plans.observeAddOns(id).first().map { it.label })
        assertEquals(listOf("Night"), plans.observeFreeRules(id).first().map { it.label })
        job.cancel()

        val again = vm(id); val job2 = started(again)
        again.onEvent(PlanEditorEvent.RemoveAddOn(0)); again.onEvent(PlanEditorEvent.RemoveFreeRule(0))
        again.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(plans.observeAddOns(id).first().isEmpty()); assertTrue(plans.observeFreeRules(id).first().isEmpty())
        job2.cancel()
    }

    @Test fun a_bad_add_on_is_reported_under_its_index_and_the_plan_is_kept() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.AddAddOn)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = " ", amountText = "2")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals("addOn.0.label", vm.uiState.value.errors.keys.single())
        assertEquals(0, plans.observePlans().first().size)            // draft has an invalid child: nothing was saved
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = "Boost")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(1, plans.observePlans().first().size)
        assertEquals(1, plans.observeAddOns(plans.observePlans().first().single().id).first().size)
        job.cancel()
    }

    @Test fun nothing_is_written_when_a_child_is_invalid() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill() // valid name "Main", cap "10"
        vm.onEvent(PlanEditorEvent.AddFreeRule)
        vm.onEvent(PlanEditorEvent.UpdateFreeRule(0, vm.uiState.value.form.freeRules[0].copy(label = "Night", days = emptySet())))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(plans.observePlans(true).first().isEmpty())
        assertEquals(setOf("freeRule.0.days"), vm.uiState.value.errors.keys)
        // A second save does not duplicate or save
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(plans.observePlans(true).first().isEmpty())
        job.cancel()
    }

    @Test fun all_keys_at_once_are_reported() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetName("")) // blank name
        vm.onEvent(PlanEditorEvent.SetCap("10")) // valid cap
        vm.onEvent(PlanEditorEvent.AddAddOn)
        // add-on validUntil before validFrom
        val today = LocalDate.now(clock)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(
            label = "Boost",
            amountText = "2",
            validFrom = today,
            validUntil = today.minusDays(5),
        )))
        vm.onEvent(PlanEditorEvent.AddFreeRule)
        // free window start == end
        vm.onEvent(PlanEditorEvent.UpdateFreeRule(0, vm.uiState.value.form.freeRules[0].copy(
            label = "Free",
            start = LocalTime.of(1, 0),
            end = LocalTime.of(1, 0),
        )))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(setOf("name", "addOn.0.validUntil", "freeRule.0.time"), vm.uiState.value.errors.keys)
        job.cancel()
    }

    @Test fun editing_an_existing_plan_with_invalid_addon_leaves_stored_plan_unchanged() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Existing", null, 5_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.Full)) as Outcome.Success).value
        val vm = vm(id); val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetRolloverKind(RolloverKind.None)) // try to change rollover
        vm.onEvent(PlanEditorEvent.AddAddOn)
        val today = LocalDate.now(clock)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(
            label = "Boost",
            amountText = "2",
            validFrom = today,
            validUntil = today.minusDays(5), // invalid
        )))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        val stored = plans.observePlans(true).first().single { it.id == id }
        assertEquals(Rollover.Full, stored.rollover) // remains unchanged
        job.cancel()
    }

    @Test fun restoring_unsaved_input_across_saved_state_handle_instances() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm1 = PlanEditorViewModel(0L, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L, savedStateHandle = handle)
        val job1 = started(vm1)
        vm1.onEvent(PlanEditorEvent.SetName("Draft"))
        vm1.onEvent(PlanEditorEvent.SetCap("7"))
        vm1.onEvent(PlanEditorEvent.AddAddOn)
        vm1.onEvent(PlanEditorEvent.UpdateAddOn(0, vm1.uiState.value.form.addOns[0].copy(label = "Pack", amountText = "3")))
        job1.cancel()

        // Build a SECOND view model with the SAME handle
        val vm2 = PlanEditorViewModel(0L, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L, savedStateHandle = handle)
        val job2 = started(vm2)
        assertEquals("Draft", vm2.uiState.value.form.name)
        assertEquals("7", vm2.uiState.value.form.capText)
        assertEquals(1, vm2.uiState.value.form.addOns.size)
        assertEquals("Pack", vm2.uiState.value.form.addOns[0].label)

        // Save successfully clears the stored form
        vm2.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(vm2.uiState.value.saved)
        job2.cancel()

        // A third view model with the same handle starts fresh
        val vm3 = PlanEditorViewModel(0L, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L, savedStateHandle = handle)
        val job3 = started(vm3)
        assertEquals("", vm3.uiState.value.form.name)
        assertEquals("", vm3.uiState.value.form.capText)
        assertTrue(vm3.uiState.value.form.addOns.isEmpty())
        job3.cancel()
    }

    @Test fun a_preview_appears_for_a_valid_form_and_not_for_an_invalid_one() = runTest(dispatcher) {
        usage.addMinute(MinuteTotal(Instant.parse("2026-10-10T10:00:00Z"), NetworkKind.MOBILE, 1, 1_000_000_000L, 0))
        val vm = vm(); val job = started(vm)
        assertNull(vm.uiState.value.preview)
        vm.fill(); advanceUntilIdle()
        assertEquals("1.00 GB of 10.0 GB", vm.uiState.value.preview!!.usedText)
        vm.onEvent(PlanEditorEvent.SetCap("abc")); advanceUntilIdle()
        assertNull(vm.uiState.value.preview)
        job.cancel()
    }

    @Test fun the_sim_options_list_the_choices_and_follow_the_permission() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        assertEquals(listOf(null, 5, 8), vm.uiState.value.sims.map { it.subscriptionId })
        assertEquals("Not tied to a SIM", vm.uiState.value.sims.first().label)
        assertEquals("SIM 1 · Alpha", vm.uiState.value.sims[1].label)
        assertFalse(vm.uiState.value.needsPhonePermission)
        sims.sims = emptyList()
        permissions.set(PermissionState(usageAccess = true, notifications = true, phoneState = false, vpnConsentGranted = false)); advanceUntilIdle()
        assertTrue(vm.uiState.value.needsPhonePermission)
        assertEquals(listOf(null, 5), vm.uiState.value.sims.map { it.subscriptionId })     // "Not tied" and the default data SIM, which needs no permission
        assertEquals("Default data SIM", vm.uiState.value.sims[1].label)
        job.cancel()
    }

    @Test fun archiving_an_existing_plan_marks_saved() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Old", null, 5_000_000_000L, Cycle.MonthlyOnDay(3, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)) as Outcome.Success).value
        val vm = vm(id); val job = started(vm)
        vm.onEvent(PlanEditorEvent.Archive); advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertTrue(plans.observePlans(false).first().isEmpty())
        job.cancel()
    }

    @Test fun thresholds_toggle_on_and_off() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.ToggleThreshold(80)); assertEquals(setOf(100), vm.uiState.value.form.alertThresholds)
        vm.onEvent(PlanEditorEvent.ToggleThreshold(80)); assertEquals(setOf(80, 100), vm.uiState.value.form.alertThresholds)
        job.cancel()
    }
}
