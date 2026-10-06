package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDetailBudgetTest {
    @Test fun a_daily_budget_maps_to_ring_and_texts() {
        val ui = budgetUi(BudgetStatus(AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.DAILY, null, 80, true), 612_000_000L, BudgetState.WARNING), planName = null, units = ByteUnits.DECIMAL)
        assertEquals("81%", ui.percentText)
        assertEquals(BudgetStateUi.Warning, ui.ringState)
        assertEquals("612 MB of 750 MB", ui.usedText)
        assertEquals("today", ui.periodText)
        assertEquals(0.816f, ui.fraction, 0.001f)
    }

    @Test fun an_exceeded_cycle_budget_with_a_plan_names_it_and_over_100_percent_shows_more() {
        // 900 of 750 MB: "120%", Exceeded, periodText "this cycle (Main)"
        val status = BudgetStatus(AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.CYCLE, 10L, 80, true), 900_000_000L, BudgetState.EXCEEDED)
        val ui = budgetUi(status, planName = "Main", units = ByteUnits.DECIMAL)
        assertEquals("120%", ui.percentText)
        assertEquals(BudgetStateUi.Exceeded, ui.ringState)
        assertEquals("900 MB of 750 MB", ui.usedText)
        assertEquals("this cycle (Main)", ui.periodText)
        assertEquals(1.2f, ui.fraction, 0.001f)
    }

    @Test fun a_cycle_budget_without_a_plan_says_this_month() {
        // periodText "this month"
        val status = BudgetStatus(AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.CYCLE, null, 80, true), 200_000_000L, BudgetState.OK)
        val ui = budgetUi(status, planName = null, units = ByteUnits.DECIMAL)
        assertEquals("this month", ui.periodText)
    }

    @Test fun a_spike_row_shows_the_window_the_size_and_the_multiple() {
        val e = SpikeEvent(5, "com.video", Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-05T11:05:00Z"), 120, 1_240_000_000L, 150_000_000L, 8.2667f, false)
        val row = spikeRowUi(e, ByteUnits.DECIMAL, ZoneOffset.UTC, Locale.ENGLISH, is24Hour = true)
        assertEquals("Mon 10:00–12:00", row.whenText)
        assertEquals("1.24 GB", row.bytesText)
        assertEquals("8.3x its usual", row.multipleText)
        assertEquals("Mon 10:00 AM–12:00 PM", spikeRowUi(e, ByteUnits.DECIMAL, ZoneOffset.UTC, Locale.US, is24Hour = false).whenText) // 5 Oct 2026 is a Monday
    }
}
