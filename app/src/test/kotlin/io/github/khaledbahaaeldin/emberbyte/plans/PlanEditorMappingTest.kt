package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanEditorMappingTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val goodForm = PlanEditorForm(name = "Main", capText = "10", capUnit = CapUnit.GB)

    @Test fun text_converts_to_bytes_in_the_chosen_unit_system() {
        assertEquals(10_000_000_000L, bytesOf("10", CapUnit.GB, UnitSystem.DECIMAL))
        assertEquals(10L * 1024 * 1024 * 1024, bytesOf("10", CapUnit.GB, UnitSystem.BINARY))
        assertEquals(1_500_000_000L, bytesOf("1,5", CapUnit.GB, UnitSystem.DECIMAL))      // decimal comma
        assertEquals(500_000_000L, bytesOf("500", CapUnit.MB, UnitSystem.DECIMAL))
        assertEquals(2_000_000_000_000L, bytesOf(" 2 ", CapUnit.TB, UnitSystem.DECIMAL))
    }

    @Test fun unreadable_or_non_positive_text_is_null() {
        listOf("", "abc", "0", "-3", "1e", "99999999999999999999").forEach {
            assertNull(it, bytesOf(it, CapUnit.GB, UnitSystem.DECIMAL))
        }
    }

    @Test fun bytes_go_back_to_the_largest_unit_that_shows_them_exactly() {
        assertEquals("10" to CapUnit.GB, textAndUnitOf(10_000_000_000L, UnitSystem.DECIMAL))
        assertEquals("1.5" to CapUnit.GB, textAndUnitOf(1_500_000_000L, UnitSystem.DECIMAL))
        assertEquals("500" to CapUnit.MB, textAndUnitOf(500_000_000L, UnitSystem.DECIMAL))
        assertEquals("2" to CapUnit.TB, textAndUnitOf(2_000_000_000_000L, UnitSystem.DECIMAL))
        assertEquals("10" to CapUnit.GB, textAndUnitOf(10L * 1024 * 1024 * 1024, UnitSystem.BINARY))
        assertEquals("0.5" to CapUnit.MB, textAndUnitOf(500_000L, UnitSystem.DECIMAL))
    }

    @Test fun loading_a_cap_and_saving_it_again_never_changes_it() {
        listOf(1_234_567_890L, 10_000_000_000L, 999_999_999_999L, 7L, 5_368_709_120L).forEach { bytes ->
            UnitSystem.entries.forEach { system ->
                val (text, unit) = textAndUnitOf(bytes, system)
                assertEquals("$bytes in $system", bytes, bytesOf(text, unit, system))
            }
        }
    }

    @Test fun a_good_form_becomes_a_plan_with_the_plans_zone() {
        val ok = goodForm.copy(
            subscriptionId = 5, dayOfMonth = 12, renewalTime = LocalTime.of(14, 30),
            rolloverKind = RolloverKind.Capped, rolloverCapText = "2", rolloverCapUnit = CapUnit.GB,
        ).toDomain(planId = 9, zone = zone, system = UnitSystem.DECIMAL, archived = false) as FormResult.Ok
        assertEquals(
            Plan(9, "Main", 5, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), zone), Rollover.Capped(2_000_000_000L)),
            ok.plan,
        )
    }

    @Test fun every_n_days_uses_the_anchor() {
        val ok = goodForm.copy(cycleKind = CycleKind.EveryNDays, everyDays = 28, anchor = LocalDate.of(2026, 9, 1))
            .toDomain(0, zone, UnitSystem.DECIMAL, false) as FormResult.Ok
        assertEquals(Cycle.EveryNDays(28, LocalDate.of(2026, 9, 1), LocalTime.MIDNIGHT, zone), ok.plan.cycle)
    }

    @Test fun unreadable_numbers_are_reported_under_the_contract_field_names() {
        val errors = goodForm.copy(
            capText = "abc", rolloverKind = RolloverKind.Capped, rolloverCapText = "",
            addOns = listOf(AddOnDraft(label = "Boost", amountText = "x", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))),
        ).toDomain(0, zone, UnitSystem.DECIMAL, false) as FormResult.Errors
        assertEquals(setOf("capBytes", "rollover.maxBytes", "addOn.0.bytes"), errors.errors.keys)
    }

    @Test fun add_ons_and_free_windows_are_converted_with_the_plan_id_and_whole_days() {
        val ok = goodForm.copy(
            addOns = listOf(AddOnDraft(id = 4, label = "Boost", amountText = "2", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))),
            freeRules = listOf(FreeRuleDraft(id = 6, label = "Night", days = setOf(DayOfWeek.FRIDAY), start = LocalTime.of(22, 0), end = LocalTime.of(6, 0), packageName = "com.video")),
        ).toDomain(planId = 9, zone = zone, system = UnitSystem.DECIMAL, archived = false) as FormResult.Ok
        val addOn = ok.addOns.single()
        assertEquals(AddOn(4, 9, "Boost", 2_000_000_000L,
            LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant(), LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()), addOn)   // valid until the END of the last day
        assertEquals(FreeRule(6, 9, "Night", setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "com.video"), ok.freeRules.single())
    }

    @Test fun a_blank_package_name_means_all_apps() {
        val ok = goodForm.copy(freeRules = listOf(FreeRuleDraft(label = "Night", packageName = "  ")))
            .toDomain(1, zone, UnitSystem.DECIMAL, false) as FormResult.Ok
        assertNull(ok.freeRules.single().packageName)
    }
}
