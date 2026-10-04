package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlansMappingTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val plan = Plan(3, "Main SIM", 5, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
    private fun state(used: Long, cap: Long = 10_000_000_000L, days: Int = 9, approx: Boolean = false) = PlanState(
        plan, CycleWindow(now.minusSeconds(86_400 * 9), now.plusSeconds(86_400 * 9)), cap, used, maxOf(0, cap - used), 0, 0, 0, days,
        used.toFloat() / cap, approx,
    )
    private fun card(s: PlanState, f: Forecast? = null, addOns: List<AddOn> = emptyList(), sim: String? = "SIM 1 · Alpha") =
        planCardUi(s, f, addOns, sim, ByteUnits.DECIMAL, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun the_texts_follow_the_state() {
        val c = card(state(6_200_000_000L))
        assertEquals("Main SIM", c.name)
        assertEquals("62%", c.percentText)
        assertEquals("6.20 GB of 10.0 GB", c.usedText)
        assertEquals("3.80 GB left", c.remainingText)
        assertEquals("9 days left", c.daysLeftText)
        assertEquals(0.62f, c.fraction, 1e-6f)
        assertEquals(BudgetStateUi.Ok, c.ringState)
        assertEquals("SIM 1 · Alpha", c.simLabel)
    }

    @Test fun the_ring_state_warns_from_80_percent_and_is_exceeded_at_100() {
        assertEquals(BudgetStateUi.Ok, card(state(7_900_000_000L)).ringState)
        assertEquals(BudgetStateUi.Warning, card(state(8_000_000_000L)).ringState)
        assertEquals(BudgetStateUi.Warning, card(state(9_900_000_000L)).ringState)
        assertEquals(BudgetStateUi.Exceeded, card(state(10_000_000_000L)).ringState)
        assertEquals("112%", card(state(11_200_000_000L)).percentText)
    }

    @Test fun nothing_left_and_day_wording() {
        assertEquals("No data left", card(state(10_500_000_000L)).remainingText)
        assertEquals("1 day left", card(state(1, days = 1)).daysLeftText)
        assertEquals("Renews today", card(state(1, days = 0)).daysLeftText)
    }

    @Test fun the_forecast_becomes_one_sentence_and_may_be_absent() {
        val f = Forecast(3, now.plusSeconds(86_400), now.plusSeconds(2 * 86_400), now.plusSeconds(3 * 86_400), 0, Confidence.MEDIUM, 14)
        assertEquals("runs out Mon (±1 day)", card(state(6_200_000_000L), f).forecastText)     // 12 Oct 2026 is a Monday
        assertNull(card(state(6_200_000_000L), null).forecastText)
    }

    @Test fun add_ons_become_chips_and_estimates_are_flagged() {
        val boost = AddOn(1, 3, "Boost", 2_000_000_000L, now, now.plusSeconds(86_400))
        val c = card(state(1, approx = true), addOns = listOf(boost))
        assertEquals(listOf("Boost +2.00 GB"), c.chips)
        assertTrue(c.isEstimated)
        assertFalse(c.archived)
    }
}
