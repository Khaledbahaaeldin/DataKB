package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightTextTest {
    private val daily = AppBudget(1, "com.video", 750_000_000L, BudgetPeriod.DAILY, null, 80, true)

    @Test
    fun a_warning_names_the_app_the_amounts_and_the_period() {
        val t = budgetText(BudgetNotice(daily, "Video", 612_000_000L, BudgetState.WARNING), ByteUnits.DECIMAL)
        assertEquals("Video: nearly at its data budget", t.title)
        assertEquals("612 MB of 750 MB used today", t.text)
    }

    @Test
    fun an_exceeded_cycle_budget_says_this_cycle() {
        val t = budgetText(BudgetNotice(daily.copy(period = BudgetPeriod.CYCLE), "Video", 800_000_000L, BudgetState.EXCEEDED), ByteUnits.DECIMAL)
        assertEquals("Video: data budget exceeded", t.title)
        assertEquals("800 MB of 750 MB used this cycle", t.text)
    }

    @Test
    fun a_spike_text_shows_the_size_and_the_multiple_with_one_decimal() {
        val e = SpikeEvent(1, "com.video", Instant.EPOCH, Instant.EPOCH, 120, 1_240_000_000L, 150_000_000L, 8.2667f, false)
        val t = spikeText(e, "Video", ByteUnits.DECIMAL)
        assertEquals("Video used a lot of data", t.title)
        assertEquals("1.24 GB in two hours, 8.3x its usual", t.text)
    }
}
