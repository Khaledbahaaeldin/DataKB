package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class NotificationSentencesTest {

    @Test
    fun live_notification_text_has_no_lone_symbols_in_accessibility_description_and_spells_out_units() {
        val notification = liveNotificationText(
            todayBytes = 1_250_000_000L,
            rxBps = 3_800_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            offline = false,
            remainingBytes = 4_200_000_000L,
        )

        // Visible text preserves concise display
        assertTrue(notification.text?.contains("↓") == true)

        // Accessibility description must exist and read as full sentences
        val desc = notification.contentDescription
        assertFalse("Accessibility description must not contain raw '↓' arrow", desc.contains("↓"))
        assertFalse("Accessibility description must not contain middle dot '·'", desc.contains("·"))

        assertTrue("Must include spoken today bytes with units", desc.contains("gigabytes") || desc.contains("gigabyte"))
        assertTrue("Must describe download speed with spelled out words", desc.contains("download") || desc.contains("per second"))
        assertTrue("Must end with sentence punctuation", desc.endsWith("."))
    }

    @Test
    fun alert_text_has_no_lone_symbols_and_numbers_carry_unit_words_in_accessibility_description() {
        val notice = AlertNotice(
            planId = 1L,
            planName = "Red Bull Mobile",
            thresholdPercent = 80,
            usedBytes = 8_000_000_000L,
            effectiveCapBytes = 10_000_000_000L,
            remainingBytes = 2_000_000_000L,
            runOutExpected = Instant.parse("2026-10-18T12:00:00Z"),
        )
        val alert = alertText(notice, ByteUnits.DECIMAL, ZoneId.of("UTC"), Locale.ENGLISH)

        val desc = alert.contentDescription
        assertFalse("Must not contain middle dot '·'", desc.contains("·"))
        assertTrue("Numbers must carry spelled-out unit words", desc.contains("gigabytes"))
        assertTrue("Must be a full sentence with period", desc.endsWith("."))
    }

    @Test
    fun budget_text_has_no_lone_symbols_and_numbers_carry_unit_words_in_accessibility_description() {
        val notice = BudgetNotice(
            budget = AppBudget(id = 1L, packageName = "com.video", limitBytes = 1_000_000_000L, period = BudgetPeriod.DAILY, planId = null, warnAtPercent = 80, enabled = true),
            label = "YouTube",
            usedBytes = 900_000_000L,
            state = BudgetState.WARNING,
            periodLabel = "today",
        )
        val budget = budgetText(notice, ByteUnits.DECIMAL)

        val desc = budget.contentDescription
        assertFalse("Must not contain lone symbols", desc.contains("·"))
        assertTrue("Numbers must carry unit words", desc.contains("megabytes") || desc.contains("gigabytes"))
        assertTrue("Must end with period", desc.endsWith("."))
    }

    @Test
    fun spike_text_has_no_lone_symbols_and_numbers_carry_unit_words_in_accessibility_description() {
        val event = SpikeEvent(
            id = 1L,
            packageName = "com.video",
            windowStart = Instant.now(),
            detectedAt = Instant.now(),
            windowMinutes = 120,
            bytes = 500_000_000L,
            baselineBytes = 100_000_000L,
            multiple = 3.5f,
            dismissed = false,
        )
        val spike = spikeText(event, "YouTube", ByteUnits.DECIMAL)

        val desc = spike.contentDescription
        assertFalse("Must not contain lone symbols", desc.contains("·"))
        assertTrue("Numbers must carry unit words", desc.contains("megabytes"))
        assertTrue("Must spell out 'times' instead of raw 'x'", desc.contains("times") || !desc.contains("x its usual"))
        assertTrue("Must end with period", desc.endsWith("."))
    }
}
