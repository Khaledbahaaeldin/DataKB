package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanValidationTest {
    private val ok = Plan(0, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
    private fun field(p: Plan) = PlanValidation.plan(p)?.field

    @Test fun a_good_plan_passes() = assertNull(PlanValidation.plan(ok))

    @Test fun each_bad_field_is_named() {
        assertEquals("name", field(ok.copy(name = "  ")))
        assertEquals("name", field(ok.copy(name = "x".repeat(41))))
        assertEquals("capBytes", field(ok.copy(capBytes = 0)))
        assertEquals("capBytes", field(ok.copy(capBytes = 2_000_000_000_000_000L)))
        assertEquals("cycle.dayOfMonth", field(ok.copy(cycle = Cycle.MonthlyOnDay(0, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.dayOfMonth", field(ok.copy(cycle = Cycle.MonthlyOnDay(32, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.days", field(ok.copy(cycle = Cycle.EveryNDays(0, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.days", field(ok.copy(cycle = Cycle.EveryNDays(367, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("rollover.maxBytes", field(ok.copy(rollover = Rollover.Capped(0))))
        assertEquals("subscriptionId", field(ok.copy(subscriptionId = -5)))
    }

    @Test fun boundaries_are_accepted() {
        assertNull(PlanValidation.plan(ok.copy(name = "x".repeat(40), capBytes = 1)))
        assertNull(PlanValidation.plan(ok.copy(cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertNull(PlanValidation.plan(ok.copy(cycle = Cycle.EveryNDays(366, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
    }

    @Test fun add_ons_and_free_rules_are_checked() {
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val addOn = AddOn(0, 1, "Boost", 1, from, from.plusSeconds(60))
        assertNull(PlanValidation.addOn(addOn))
        assertEquals("bytes", PlanValidation.addOn(addOn.copy(bytes = 0))?.field)
        assertEquals("validUntil", PlanValidation.addOn(addOn.copy(validUntil = from))?.field)
        assertEquals("label", PlanValidation.addOn(addOn.copy(label = " "))?.field)
        val rule = FreeRule(0, 1, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        assertNull(PlanValidation.freeRule(rule))
        assertEquals("days", PlanValidation.freeRule(rule.copy(days = emptySet()))?.field)
        assertEquals("time", PlanValidation.freeRule(rule.copy(end = LocalTime.of(0, 0)))?.field)
        assertEquals("label", PlanValidation.freeRule(rule.copy(label = ""))?.field)
    }
}
