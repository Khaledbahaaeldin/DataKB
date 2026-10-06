package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.lifecycle.SavedStateHandle
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.alert.RoomAlertRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
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

    private fun vm(planId: Long = 0L, handle: SavedStateHandle = SavedStateHandle(), planRepo: PlanRepository = plans) =
        PlanEditorViewModel(planId, planRepo, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L, savedStateHandle = handle)

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

    @Test fun saving_stores_the_plan_its_children_and_thresholds_in_one_call() = runTest(dispatcher) {
        val recording = RecordingPlanRepository(plans)
        val vm = vm(planRepo = recording); val job = started(vm)
        vm.fill()
        vm.onEvent(PlanEditorEvent.AddAddOn)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = "Boost", amountText = "2")))
        vm.onEvent(PlanEditorEvent.AddFreeRule)
        vm.onEvent(PlanEditorEvent.UpdateFreeRule(0, vm.uiState.value.form.freeRules[0].copy(label = "Night")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()

        assertEquals(1, recording.savePlanCalls)
        assertEquals(0, recording.upsertPlanCalls)
        assertEquals(0, recording.upsertAddOnCalls)
        assertEquals(0, recording.upsertFreeRuleCalls)
        assertTrue(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun a_storage_failure_shows_a_form_error_and_does_not_mark_saved() = runTest(dispatcher) {
        val recording = RecordingPlanRepository(plans).apply {
            savePlanFailure = EmberbyteError.Storage("disk error")
            failCount = 1
        }
        val vm = vm(planRepo = recording); val job = started(vm)
        vm.fill()
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()

        assertEquals(setOf("form"), vm.uiState.value.errors.keys)
        assertFalse(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun a_second_save_after_a_failure_does_not_create_a_second_plan() = runTest(dispatcher) {
        val recording = RecordingPlanRepository(plans).apply {
            savePlanFailure = EmberbyteError.Storage("disk error")
            failCount = 1
        }
        val vm = vm(planRepo = recording); val job = started(vm)
        vm.fill()
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertFalse(vm.uiState.value.saved)

        // Second save should succeed and not create a second plan
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertEquals(1, plans.observePlans(true).first().size)
        job.cancel()
    }

    private suspend fun mobile(at: String, bytes: Long, sub: Int = 1) =
        usage.addMinute(MinuteTotal(Instant.parse(at), NetworkKind.MOBILE, sub, bytes, 0))

    private suspend fun fillHistory() {
        var day = Instant.parse("2026-09-17T10:00:00Z")
        repeat(28) { mobile(day.toString(), 1_000_000_000L); day = day.plusSeconds(86_400) }
    }

    private suspend fun savePlan(cap: Long = 20_000_000_000L): Long =
        (plans.upsertPlan(Plan(0, "Main", 1, cap, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)) as Outcome.Success).value

    @Test fun what_if_is_null_for_a_new_plan() = runTest(dispatcher) {
        val vm = vm(0L)
        val job = started(vm)
        assertNull(vm.uiState.value.whatIf)
        job.cancel()
    }

    @Test fun what_if_is_populated_for_an_existing_plan() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val expectedForecast = plans.observeForecast(id).first()!!
        val expectedRemaining = plans.observePlanState(id).first()!!.remainingBytes
        val expectedSentence = whatIfSentence(expectedForecast, expectedRemaining, now, ZoneOffset.UTC, Locale.ENGLISH)

        val vm = vm(id)
        val job = started(vm)
        val whatIf = vm.uiState.value.whatIf
        assertNotNull(whatIf)
        assertEquals(expectedSentence, whatIf!!.currentText)
        assertEquals("", whatIf.amountText)
        assertEquals(CapUnit.MB, whatIf.unit)
        assertNull(whatIf.whatIfText)
        assertNull(whatIf.error)
        job.cancel()
    }

    @Test fun set_what_if_amount_computes_what_if_sentence_and_earlier_run_out() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val currentForecast = plans.observeForecast(id).first()!!
        val expectedRemaining = plans.observePlanState(id).first()!!.remainingBytes
        val whatIfOutcome = plans.whatIf(id, 500_000_000L, now) as Outcome.Success
        val expectedSentence = whatIfSentence(whatIfOutcome.value, expectedRemaining, now, ZoneOffset.UTC, Locale.ENGLISH)

        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("500"))
        advanceUntilIdle()

        val whatIf = vm.uiState.value.whatIf!!
        assertEquals("500", whatIf.amountText)
        assertEquals(expectedSentence, whatIf.whatIfText)
        assertNull(whatIf.error)
        assertTrue(whatIfOutcome.value.runOutExpected!! <= currentForecast.runOutExpected!!)
        job.cancel()
    }

    @Test fun clearing_what_if_amount_clears_what_if_text_without_error() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("500"))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.whatIf!!.whatIfText)

        vm.onEvent(PlanEditorEvent.SetWhatIfAmount(""))
        advanceUntilIdle()
        val whatIf = vm.uiState.value.whatIf!!
        assertEquals("", whatIf.amountText)
        assertNull(whatIf.whatIfText)
        assertNull(whatIf.error)
        job.cancel()
    }

    @Test fun invalid_what_if_amount_sets_error_and_clears_what_if_text() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("abc"))
        advanceUntilIdle()

        val whatIf = vm.uiState.value.whatIf!!
        assertEquals("abc", whatIf.amountText)
        assertEquals("Enter a number", whatIf.error)
        assertNull(whatIf.whatIfText)
        job.cancel()
    }

    @Test fun what_if_unit_change_recomputes_with_new_unit() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val expectedRemaining = plans.observePlanState(id).first()!!.remainingBytes
        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("1"))
        advanceUntilIdle()
        val mbForecast = (plans.whatIf(id, 1_000_000L, now) as Outcome.Success).value
        assertEquals(whatIfSentence(mbForecast, expectedRemaining, now, ZoneOffset.UTC, Locale.ENGLISH), vm.uiState.value.whatIf!!.whatIfText)

        vm.onEvent(PlanEditorEvent.SetWhatIfUnit(CapUnit.GB))
        advanceUntilIdle()
        val gbForecast = (plans.whatIf(id, 1_000_000_000L, now) as Outcome.Success).value
        assertEquals(whatIfSentence(gbForecast, expectedRemaining, now, ZoneOffset.UTC, Locale.ENGLISH), vm.uiState.value.whatIf!!.whatIfText)
        job.cancel()
    }

    @Test fun what_if_honours_binary_unit_system() = runTest(dispatcher) {
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        fillHistory()
        val id = savePlan()
        val expectedRemaining = plans.observePlanState(id).first()!!.remainingBytes
        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("1"))
        vm.onEvent(PlanEditorEvent.SetWhatIfUnit(CapUnit.GB))
        advanceUntilIdle()

        val binaryGb = 1024L * 1024 * 1024
        val forecast = (plans.whatIf(id, binaryGb, now) as Outcome.Success).value
        assertEquals(whatIfSentence(forecast, expectedRemaining, now, ZoneOffset.UTC, Locale.ENGLISH), vm.uiState.value.whatIf!!.whatIfText)
        job.cancel()
    }

    @Test fun what_if_computation_is_debounced() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val vm = PlanEditorViewModel(
            id, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH,
            previewDebounceMillis = 300L, savedStateHandle = SavedStateHandle(),
        )
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("500"))
        testScheduler.advanceTimeBy(100L)
        assertNull(vm.uiState.value.whatIf!!.whatIfText)

        testScheduler.advanceTimeBy(250L)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.whatIf!!.whatIfText)
        job.cancel()
    }

    @Test fun what_if_never_writes_to_plan_store() = runTest(dispatcher) {
        fillHistory()
        val id = savePlan()
        val beforePlans = plans.observePlans(true).first()
        val vm = vm(id)
        val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetWhatIfAmount("500"))
        vm.onEvent(PlanEditorEvent.SetWhatIfUnit(CapUnit.GB))
        advanceUntilIdle()

        val afterPlans = plans.observePlans(true).first()
        assertEquals(beforePlans, afterPlans)
        job.cancel()
    }

    @Test fun isDirty_is_false_right_after_loading_and_true_after_any_edit_false_again_after_successful_save() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Existing", null, 5_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.Full)) as Outcome.Success).value
        val vm = vm(id)
        val job = started(vm)
        assertFalse(vm.uiState.value.isDirty)

        vm.onEvent(PlanEditorEvent.SetName("Edited Name"))
        assertTrue(vm.uiState.value.isDirty)

        vm.onEvent(PlanEditorEvent.Save)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertFalse(vm.uiState.value.isDirty)
        job.cancel()
    }

    @Test fun restored_saved_state_form_counts_as_dirty() = runTest(dispatcher) {
        val handle = SavedStateHandle().apply {
            set("form", PlanEditorFormJson.encode(PlanEditorForm(name = "Restored Draft", capText = "20")))
        }
        val vm = vm(0L, handle = handle)
        val job = started(vm)
        assertTrue(vm.uiState.value.isDirty)
        job.cancel()
    }

    private class RecordingPlanRepository(private val delegate: PlanRepository) : PlanRepository by delegate {
        var savePlanCalls = 0
        var upsertPlanCalls = 0
        var upsertAddOnCalls = 0
        var upsertFreeRuleCalls = 0
        var savePlanFailure: EmberbyteError? = null
        var failCount = 0

        override suspend fun savePlan(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>, alertThresholds: Set<Int>): Outcome<Long> {
            savePlanCalls++
            if (failCount > 0) {
                failCount--
                return Outcome.Failure(savePlanFailure ?: EmberbyteError.Storage("error"))
            }
            return delegate.savePlan(plan, addOns, freeRules, alertThresholds)
        }

        override suspend fun upsertPlan(plan: Plan): Outcome<Long> {
            upsertPlanCalls++
            return delegate.upsertPlan(plan)
        }

        override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> {
            upsertAddOnCalls++
            return delegate.upsertAddOn(addOn)
        }

        override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> {
            upsertFreeRuleCalls++
            return delegate.upsertFreeRule(rule)
        }
    }
}
