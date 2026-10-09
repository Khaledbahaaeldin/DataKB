package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlanRepositoryTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planStore = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private val repo = RoomPlanRepository(planStore, usage, clock, ticks = flowOf(now))

    private fun draft(name: String = "Main", sub: Int? = null, cap: Long = 10_000_000_000L, rollover: Rollover = Rollover.None) =
        Plan(0L, name, sub, cap, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), rollover)

    private suspend fun mobile(at: String, bytes: Long, sub: Int = 1) =
        usage.addMinute(MinuteTotal(Instant.parse(at), NetworkKind.MOBILE, sub, bytes, 0))

    private suspend fun save(plan: Plan = draft()): Long = (repo.upsertPlan(plan) as Outcome.Success).value

    @Test fun a_saved_plan_is_observable_with_its_state() = runBlocking {
        val id = save()
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        val state = repo.observePlanState(id).first()!!
        assertEquals(1_000_000_000L, state.usedBytes)
        assertEquals(9_000_000_000L, state.remainingBytes)
        assertEquals(listOf("Main"), repo.observePlans().first().map { it.name })
    }

    @Test fun the_state_follows_new_usage() = runTest {
        val id = save()
        val seen = mutableListOf<Long>()
        val job = launch { repo.observePlanState(id).collect { seen += it!!.usedBytes } }
        runCurrent()
        mobile("2026-10-10T10:00:00Z", 2_000_000_000L)
        runCurrent()
        job.cancel()
        assertEquals(listOf(0L, 2_000_000_000L), seen.distinct())
    }

    @Test fun a_missing_plan_observes_as_null_state_and_forecast() = runBlocking {
        assertNull(repo.observePlanState(99L).first())
        assertNull(repo.observeForecast(99L).first())
    }

    @Test fun invalid_plans_are_rejected_and_nothing_is_saved() = runBlocking {
        val bad = repo.upsertPlan(draft(name = " ")) as Outcome.Failure
        assertEquals("name", (bad.error as EmberbyteError.Invalid).field)
        assertEquals("capBytes", ((repo.upsertPlan(draft(cap = 0)) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())
    }

    @Test fun the_name_is_trimmed() = runBlocking {
        val id = save(draft(name = "  Work  "))
        assertEquals("Work", repo.observePlans().first().single { it.id == id }.name)
    }

    @Test fun editing_a_missing_plan_and_adding_children_to_it_is_not_found() = runBlocking {
        assertEquals(EmberbyteError.NotFound, (repo.upsertPlan(draft().copy(id = 99)) as Outcome.Failure).error)
        val from = Instant.parse("2026-10-05T00:00:00Z")
        assertEquals(EmberbyteError.NotFound, (repo.upsertAddOn(AddOn(0, 99, "Boost", 1, from, from.plusSeconds(60))) as Outcome.Failure).error)
        val rule = FreeRule(0, 99, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        assertEquals(EmberbyteError.NotFound, (repo.upsertFreeRule(rule) as Outcome.Failure).error)
    }

    @Test fun invalid_children_are_rejected_with_the_field_name() = runBlocking {
        val id = save()
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val badAddOn = repo.upsertAddOn(AddOn(0, id, "Boost", 0, from, from.plusSeconds(60))) as Outcome.Failure
        assertEquals("bytes", (badAddOn.error as EmberbyteError.Invalid).field)
        val badRule = repo.upsertFreeRule(FreeRule(0, id, "Night", emptySet(), LocalTime.of(0, 0), LocalTime.of(6, 0), null)) as Outcome.Failure
        assertEquals("days", (badRule.error as EmberbyteError.Invalid).field)
    }

    @Test fun add_ons_and_free_rules_change_the_state() = runBlocking {
        val id = save()
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        repo.upsertAddOn(AddOn(0, id, "Boost", 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")))
        assertEquals(12_000_000_000L, repo.observePlanState(id).first()!!.effectiveCapBytes)
        repo.upsertFreeRule(FreeRule(0, id, "Mornings", DayOfWeek.entries.toSet(), LocalTime.of(10, 0), LocalTime.of(12, 0), null))
        assertEquals(0L, repo.observePlanState(id).first()!!.usedBytes)
        assertEquals(1_000_000_000L, repo.observePlanState(id).first()!!.freeBytes)
    }

    @Test fun archived_plans_leave_the_active_list() = runBlocking {
        val id = save()
        assertEquals(1, repo.observeActivePlanStates().first().size)
        assertTrue(repo.archivePlan(id) is Outcome.Success)
        assertTrue(repo.observePlans(includeArchived = false).first().isEmpty())
        assertEquals(1, repo.observePlans(includeArchived = true).first().size)
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertEquals(EmberbyteError.NotFound, (repo.archivePlan(99) as Outcome.Failure).error)
    }

    @Test fun rollover_uses_the_previous_cycle() = runBlocking {
        val id = save(draft(rollover = Rollover.Full))
        mobile("2026-09-10T10:00:00Z", 6_000_000_000L)
        assertEquals(4_000_000_000L, repo.observePlanState(id).first()!!.rolledOverBytes)
    }

    private suspend fun fillHistory() {
        // 28 days of 1 GB at 10:00 UTC, 17 Sep .. 14 Oct
        var day = Instant.parse("2026-09-17T10:00:00Z")
        repeat(28) { mobile(day.toString(), 1_000_000_000L); day = day.plusSeconds(86_400) }
    }

    @Test fun a_forecast_with_enough_history_says_the_plan_lasts() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 100_000_000_000L))
        val f = repo.observeForecast(id).first()!!
        assertNull(f.runOutExpected)
        assertEquals(Confidence.HIGH, f.confidence)
        assertEquals(28, f.historyDays)
    }

    @Test fun a_forecast_names_the_run_out_day() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 20_000_000_000L))       // 14 GB used in October, 6 GB left at 1 GB/day
        val f = repo.observeForecast(id).first()!!
        assertEquals("2026-10-22", f.runOutExpected!!.toString().substring(0, 10))
        val expected = Instant.parse("2026-10-22T00:00:00Z")
        val diffSeconds = Math.abs(f.runOutExpected!!.epochSecond - expected.epochSecond)
        assertTrue("runOutExpected was ${f.runOutExpected}, expected within 1s of $expected", diffSeconds <= 1)
    }

    @Test fun two_days_of_history_keep_the_default_curve() = runBlocking {
        mobile("2026-10-13T10:00:00Z", 1_000_000_000L)
        mobile("2026-10-14T10:00:00Z", 1_000_000_000L)
        val id = save(draft(cap = 5_000_000_000L))
        val f = repo.observeForecast(id).first()!!
        assertNotNull(f.runOutExpected)
        assertEquals("2026-10-18", f.runOutExpected!!.toString().substring(0, 10))
    }

    @Test fun minute_rows_of_another_subscription_do_not_shape_the_personal_curve() = runBlocking {
        for (i in 8..14) {
            mobile("2026-10-${i.toString().padStart(2, '0')}T20:00:00Z", 1_000_000_000L, sub = 1)
            mobile("2026-10-${i.toString().padStart(2, '0')}T10:00:00Z", 1_000_000_000L, sub = 2)
        }
        val id = save(draft(sub = 2, cap = 7_200_000_000L))
        val f = repo.observeForecast(id).first()!!
        assertNotNull(f.runOutExpected)
        assertEquals("2026-10-16", f.runOutExpected!!.toString().substring(0, 10))
    }

    @Test fun preview_state_evaluates_an_unsaved_draft_and_rejects_an_invalid_one() = runBlocking {
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        val ok = repo.previewState(draft(), emptyList(), emptyList()) as Outcome.Success
        assertEquals(1_000_000_000L, ok.value.state.usedBytes)
        assertNotNull(ok.value.forecast)
        val bad = repo.previewState(draft(cap = 0), emptyList(), emptyList()) as Outcome.Failure
        assertEquals("capBytes", (bad.error as EmberbyteError.Invalid).field)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())     // nothing was persisted
    }

    @Test fun what_if_extra_usage_brings_the_run_out_forward_and_a_missing_plan_is_not_found() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 100_000_000_000L))
        assertNull(repo.observeForecast(id).first()!!.runOutExpected)
        val extra = (repo.whatIf(id, 20_000_000_000L, now) as Outcome.Success).value
        assertNotNull(extra.runOutExpected)
        assertEquals(EmberbyteError.NotFound, (repo.whatIf(99L, 1L, now) as Outcome.Failure).error)
    }

    @Test fun validate_draft_returns_all_errors_and_never_persists_anything() = runBlocking {
        val goodPlan = draft()
        val goodAddOn = AddOn(0, 0, "Boost", 1_000_000_000L, now, now.plusSeconds(86400))
        val goodRule = FreeRule(0, 0, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        assertTrue(repo.validateDraft(goodPlan, listOf(goodAddOn), listOf(goodRule)).isEmpty())

        val badPlan = draft(name = " ")
        val badAddOn = AddOn(0, 0, "Boost", 0L, now, now.plusSeconds(86400))
        val badRule = FreeRule(0, 0, "Night", emptySet(), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        val errors = repo.validateDraft(badPlan, listOf(badAddOn), listOf(badRule))
        assertEquals(setOf("name", "addOn.0.bytes", "freeRule.0.days"), errors.keys)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())
    }

    @Test fun validate_draft_returns_all_plan_level_errors_at_once() {
        val badPlan = draft(
            name = " ",
            cap = 0L,
        ).copy(cycle = Cycle.MonthlyOnDay(0, LocalTime.MIDNIGHT, ZoneOffset.UTC))
        val errors = repo.validateDraft(badPlan, emptyList(), emptyList())
        assertEquals(setOf("name", "capBytes", "cycle.dayOfMonth"), errors.keys)
    }

    @Test fun saving_an_invalid_draft_writes_nothing() = runBlocking {
        val badPlan = draft(name = " ")
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val badAddOn = AddOn(0, 0, "Boost", 0L, from, from.plusSeconds(60))
        val res = repo.savePlan(badPlan, listOf(badAddOn), emptyList(), setOf(80))
        assertTrue(res is Outcome.Failure)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())
    }

    @Test fun saving_a_valid_draft_returns_the_id_and_the_state_reflects_the_children() = runBlocking {
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val until = Instant.parse("2026-10-25T00:00:00Z")
        val addOn = AddOn(0, 0, "Boost", 2_000_000_000L, from, until)
        val rule = FreeRule(0, 0, "AllWeek", DayOfWeek.entries.toSet(), LocalTime.MIN, LocalTime.MAX, null)
        val outcome = repo.savePlan(draft(cap = 10_000_000_000L), listOf(addOn), listOf(rule), setOf(80, 100))
        assertTrue(outcome is Outcome.Success)
        val id = (outcome as Outcome.Success).value
        assertTrue(id > 0)

        val state = repo.observePlanState(id).first()!!
        assertEquals(2_000_000_000L, state.addOnBytes)
        assertEquals(12_000_000_000L, state.effectiveCapBytes)
    }

    @Test fun saving_for_a_missing_plan_id_is_not_found() = runBlocking {
        val outcome = repo.savePlan(draft().copy(id = 999L), emptyList(), emptyList(), setOf(80))
        assertEquals(Outcome.Failure(EmberbyteError.NotFound), outcome)
    }

    @Test fun thresholds_outside_1_to_100_are_invalid() = runBlocking {
        val res0 = repo.savePlan(draft(), emptyList(), emptyList(), setOf(0))
        assertTrue(res0 is Outcome.Failure)
        assertEquals("thresholdPercent", ((res0 as Outcome.Failure).error as EmberbyteError.Invalid).field)

        val res101 = repo.savePlan(draft(), emptyList(), emptyList(), setOf(101))
        assertTrue(res101 is Outcome.Failure)
        assertEquals("thresholdPercent", ((res101 as Outcome.Failure).error as EmberbyteError.Invalid).field)
    }
}
