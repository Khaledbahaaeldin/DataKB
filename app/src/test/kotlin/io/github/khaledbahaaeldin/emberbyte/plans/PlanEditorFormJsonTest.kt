package io.github.khaledbahaaeldin.emberbyte.plans

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanEditorFormJsonTest {

    @Test
    fun roundtrip_preserves_every_field_of_plan_editor_form() {
        val form = PlanEditorForm(
            name = "Test Plan",
            subscriptionId = 42,
            capText = "15.5",
            capUnit = CapUnit.TB,
            cycleKind = CycleKind.EveryNDays,
            dayOfMonth = 15,
            everyDays = 28,
            anchor = LocalDate.of(2026, 6, 1),
            renewalTime = LocalTime.of(14, 30),
            rolloverKind = RolloverKind.Capped,
            rolloverCapText = "5.0",
            rolloverCapUnit = CapUnit.MB,
            addOns = listOf(
                AddOnDraft(
                    id = 101L,
                    label = "Extra 2GB",
                    amountText = "2",
                    unit = CapUnit.GB,
                    validFrom = LocalDate.of(2026, 10, 1),
                    validUntil = LocalDate.of(2026, 10, 15),
                ),
            ),
            freeRules = listOf(
                FreeRuleDraft(
                    id = 202L,
                    label = "Night Free",
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    start = LocalTime.of(1, 0),
                    end = LocalTime.of(5, 0),
                    packageName = "com.example.free",
                ),
                FreeRuleDraft(
                    id = 203L,
                    label = "All Apps Free",
                    days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    start = LocalTime.MIDNIGHT,
                    end = LocalTime.of(8, 0),
                    packageName = null,
                ),
            ),
            alertThresholds = setOf(50, 80, 95),
        )

        val encoded = PlanEditorFormJson.encode(form)
        val decoded = PlanEditorFormJson.decode(encoded)
        assertEquals(form, decoded)
    }

    @Test
    fun decode_handles_null_subscription_and_package_name() {
        val form = PlanEditorForm(
            name = "Default SIM",
            subscriptionId = null,
            freeRules = listOf(
                FreeRuleDraft(
                    id = 1L,
                    label = "Free Window",
                    packageName = null,
                ),
            ),
        )
        val encoded = PlanEditorFormJson.encode(form)
        val decoded = PlanEditorFormJson.decode(encoded)
        assertEquals(form, decoded)
    }

    @Test
    fun decode_of_garbage_returns_null() {
        assertNull(PlanEditorFormJson.decode(""))
        assertNull(PlanEditorFormJson.decode("not a json"))
        assertNull(PlanEditorFormJson.decode("{corrupted: true}"))
        assertNull(PlanEditorFormJson.decode("[]"))
    }
}
