package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class PlanStoreContractTest {
    abstract fun createStore(): PlanStore

    private fun draft(name: String = "Main", sub: Int? = 3) =
        Plan(0L, name, sub, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.of(0, 0), ZoneOffset.UTC), Rollover.Capped(2_000_000_000L))

    @Test fun a_new_plan_gets_an_id_and_reads_back_unchanged() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        assertTrue(id > 0)
        assertEquals(draft().copy(id = id), store.plan(id))
        assertEquals(listOf(draft().copy(id = id)), store.observePlans().first())
        assertEquals(draft().copy(id = id), store.observePlan(id).first())
    }

    @Test fun upserting_an_existing_id_replaces_it() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        store.upsertPlan(draft(name = "Renamed", sub = null).copy(id = id))
        assertEquals(1, store.observePlans().first().size)
        assertEquals("Renamed", store.plan(id)!!.name)
        assertNull(store.plan(id)!!.subscriptionId)
    }

    @Test fun archiving_flips_the_flag_and_reports_a_missing_plan() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        assertTrue(store.setArchived(id, true))
        assertTrue(store.plan(id)!!.archived)
        assertTrue(store.setArchived(id, false))
        assertFalse(store.plan(id)!!.archived)
        assertFalse(store.setArchived(999L, true))
    }

    @Test fun a_missing_plan_observes_as_null() = runBlocking {
        assertNull(createStore().observePlan(42L).first())
        assertNull(createStore().plan(42L))
    }

    @Test fun add_ons_are_stored_per_plan_and_deletable() = runBlocking {
        val store = createStore()
        val a = store.upsertPlan(draft("A"))
        val b = store.upsertPlan(draft("B"))
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val until = Instant.parse("2026-10-20T00:00:00Z")
        val id = store.upsertAddOn(AddOn(0, a, "Boost", 2_000_000_000L, from, until))
        store.upsertAddOn(AddOn(0, b, "Other", 1L, from, until))
        assertEquals(listOf(AddOn(id, a, "Boost", 2_000_000_000L, from, until)), store.observeAddOns(a).first())
        store.deleteAddOn(id)
        assertTrue(store.observeAddOns(a).first().isEmpty())
        assertEquals(1, store.observeAddOns(b).first().size)
    }

    @Test fun free_rules_keep_their_days_times_and_package() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        val rule = FreeRule(0, plan, "Night", setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "com.video")
        val id = store.upsertFreeRule(rule)
        assertEquals(listOf(rule.copy(id = id)), store.observeFreeRules(plan).first())
        store.upsertFreeRule(rule.copy(id = id, packageName = null, days = setOf(DayOfWeek.MONDAY)))
        val back = store.observeFreeRules(plan).first().single()
        assertEquals(setOf(DayOfWeek.MONDAY), back.days)
        assertNull(back.packageName)
        store.deleteFreeRule(id)
        assertTrue(store.observeFreeRules(plan).first().isEmpty())
    }

    @Test fun alert_rules_are_ordered_by_threshold_and_deletable() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        store.upsertAlertRule(AlertRule(0, plan, 90, true))
        val fifty = store.upsertAlertRule(AlertRule(0, plan, 50, false))
        assertEquals(listOf(50, 90), store.alertRules(plan).map { it.thresholdPercent })
        assertEquals(listOf(50, 90), store.observeAlertRules(plan).first().map { it.thresholdPercent })
        store.deleteAlertRule(fifty)
        assertEquals(listOf(90), store.alertRules(plan).map { it.thresholdPercent })
    }

    @Test fun the_fired_percent_is_remembered_per_plan_and_window() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        val w1 = Instant.parse("2026-10-01T00:00:00Z")
        val w2 = Instant.parse("2026-11-01T00:00:00Z")
        assertNull(store.lastFiredPercent(plan, w1))
        store.setFiredPercent(plan, w1, 80)
        store.setFiredPercent(plan, w1, 90)
        assertEquals(90, store.lastFiredPercent(plan, w1))
        assertNull(store.lastFiredPercent(plan, w2))
        assertNull(store.lastFiredPercent(plan + 1, w1))
    }

    private fun addOn(id: Long = 0L, label: String = "Boost", planId: Long = 0L) =
        AddOn(id, planId, label, 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z"))

    private fun rule(id: Long = 0L, label: String = "Night", planId: Long = 0L) =
        FreeRule(id, planId, label, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "com.video")

    @Test fun saving_a_new_plan_with_children_creates_everything() = runBlocking {
        val s = createStore()
        val id = s.savePlanWithChildren(draft(), listOf(addOn(0)), listOf(rule(0)), setOf(80, 100))
        assertEquals(1, s.observeAddOns(id).first().size)
        assertEquals(1, s.observeFreeRules(id).first().size)
        assertEquals(listOf(80, 100), s.alertRules(id).map { it.thresholdPercent })
        assertTrue(s.alertRules(id).all { it.enabled })
        assertEquals(id, s.observeAddOns(id).first().single().planId)
    }

    @Test fun saving_again_keeps_given_ids_deletes_the_rest_and_syncs_thresholds() = runBlocking {
        val s = createStore()
        val id = s.savePlanWithChildren(draft(), listOf(addOn(0, "A"), addOn(0, "B")), listOf(rule(0)), setOf(50, 80))
        val a = s.observeAddOns(id).first().first { it.label == "A" }
        s.savePlanWithChildren(draft().copy(id = id, name = "Renamed"), listOf(a.copy(label = "A2")), emptyList(), setOf(80, 90))
        assertEquals("Renamed", s.plan(id)!!.name)
        assertEquals(listOf(a.copy(label = "A2")), s.observeAddOns(id).first()) // same id, B gone
        assertTrue(s.observeFreeRules(id).first().isEmpty())
        assertEquals(listOf(80, 90), s.alertRules(id).map { it.thresholdPercent })
    }

    @Test fun a_disabled_rule_with_a_wanted_threshold_is_enabled_again() = runBlocking {
        val s = createStore()
        val id = s.savePlanWithChildren(draft(), emptyList(), emptyList(), emptySet())
        val ruleId = s.upsertAlertRule(AlertRule(0, id, 80, enabled = false))
        assertEquals(listOf(false), s.alertRules(id).map { it.enabled })
        s.savePlanWithChildren(draft().copy(id = id), emptyList(), emptyList(), setOf(80))
        val rules = s.alertRules(id)
        assertEquals(1, rules.size)
        assertEquals(ruleId, rules.single().id)
        assertTrue(rules.single().enabled)
    }

    @Test fun another_plans_children_are_untouched() = runBlocking {
        val s = createStore()
        val p1 = s.savePlanWithChildren(draft("P1"), listOf(addOn(0, "P1-Addon")), listOf(rule(0, "P1-Rule")), setOf(80))
        val p2 = s.savePlanWithChildren(draft("P2"), listOf(addOn(0, "P2-Addon")), listOf(rule(0, "P2-Rule")), setOf(90))
        s.savePlanWithChildren(draft("P1-Updated").copy(id = p1), emptyList(), emptyList(), emptySet())
        assertTrue(s.observeAddOns(p1).first().isEmpty())
        assertTrue(s.observeFreeRules(p1).first().isEmpty())
        assertTrue(s.alertRules(p1).isEmpty())
        assertEquals(1, s.observeAddOns(p2).first().size)
        assertEquals(1, s.observeFreeRules(p2).first().size)
        assertEquals(listOf(90), s.alertRules(p2).map { it.thresholdPercent })
    }
}
