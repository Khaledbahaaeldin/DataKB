package io.github.khaledbahaaeldin.emberbyte.engine.budget

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCalculatorTest {
    private fun budget(limit: Long = 1000, warn: Int = 80, period: BudgetPeriod = BudgetPeriod.DAILY) =
        AppBudget(1, "com.video", limit, period, null, warn, true)

    @Test fun below_the_warning_percentage_is_ok() {
        assertEquals(BudgetState.OK, BudgetCalculator.state(budget(), 0))
        assertEquals(BudgetState.OK, BudgetCalculator.state(budget(), 799))
    }
    @Test fun from_the_warning_percentage_up_to_and_including_the_limit_is_a_warning() {
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(budget(), 800))
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(budget(), 1000))
    }
    @Test fun above_the_limit_is_exceeded() {
        assertEquals(BudgetState.EXCEEDED, BudgetCalculator.state(budget(), 1001))
    }
    @Test fun the_warning_percentage_is_used_as_given() {
        assertEquals(BudgetState.OK, BudgetCalculator.state(budget(warn = 50), 499))
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(budget(warn = 50), 500))
    }
    @Test fun large_values_do_not_overflow() {
        val b = budget(limit = 1_000_000_000_000_000L, warn = 99)
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(b, 990_000_000_000_000L))
        assertEquals(BudgetState.OK, BudgetCalculator.state(b, 989_999_999_999_999L))
    }
    @Test fun max_long_limit_does_not_overflow() {
        val b = budget(limit = Long.MAX_VALUE, warn = 80)
        assertEquals(BudgetState.OK, BudgetCalculator.state(b, 1000L))
        assertEquals(BudgetState.OK, BudgetCalculator.state(b, 0L))
    }
    @Test fun max_long_limit_exact_comparisons_with_large_used() {
        val b = budget(limit = Long.MAX_VALUE, warn = 80)
        assertEquals(BudgetState.OK, BudgetCalculator.state(b, 2_000_000_000_000_000L))
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(b, (Long.MAX_VALUE / 10) * 9))
        assertEquals(BudgetState.WARNING, BudgetCalculator.state(b, Long.MAX_VALUE))
    }
    @Test fun a_zero_limit_never_warns() = assertEquals(BudgetState.OK, BudgetCalculator.state(budget(limit = 0), 5))

    @Test fun a_daily_period_is_the_local_day() {
        val cairo = ZoneId.of("Africa/Cairo")                 // +03:00 on 2026-10-05/06
        val w = BudgetCalculator.period(budget(), Instant.parse("2026-10-05T22:30:00Z"), cairo, null)   // 01:30 on 6 Oct in Cairo
        assertEquals(Instant.parse("2026-10-05T21:00:00Z"), w.start)
        assertEquals(Instant.parse("2026-10-06T21:00:00Z"), w.end)
    }
    @Test fun a_cycle_period_is_the_plan_window_when_there_is_one() {
        val plan = CycleWindow(Instant.parse("2026-09-12T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z"))
        assertEquals(plan, BudgetCalculator.period(budget(period = BudgetPeriod.CYCLE), Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC, plan))
    }
    @Test fun a_cycle_period_without_a_plan_is_the_calendar_month() {
        val w = BudgetCalculator.period(budget(period = BudgetPeriod.CYCLE), Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC, null)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), w.start)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), w.end)
    }
}
