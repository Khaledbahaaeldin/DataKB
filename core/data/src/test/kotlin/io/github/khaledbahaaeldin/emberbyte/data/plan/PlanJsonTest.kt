package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanJsonTest {
    private val cairo = ZoneId.of("Africa/Cairo")

    @Test fun every_cycle_survives_a_round_trip() {
        val monthly = Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), cairo)
        val every = Cycle.EveryNDays(30, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneId.of("Asia/Kolkata"))
        assertEquals(monthly, PlanJson.cycleFromJson(PlanJson.cycleToJson(monthly)))
        assertEquals(every, PlanJson.cycleFromJson(PlanJson.cycleToJson(every)))
    }

    @Test fun every_rollover_survives_a_round_trip() {
        listOf(Rollover.None, Rollover.Full, Rollover.Capped(2_000_000_000L)).forEach {
            assertEquals(it, PlanJson.rolloverFromJson(PlanJson.rolloverToJson(it)))
        }
    }

    @Test fun the_json_uses_the_shapes_of_contract_section_7() {
        val monthly = JSONObject(PlanJson.cycleToJson(Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, cairo)))
        assertEquals("monthlyOnDay", monthly.getString("type"))
        assertEquals(12, monthly.getInt("dayOfMonth"))
        assertEquals("00:00", monthly.getString("time"))
        assertEquals("Africa/Cairo", monthly.getString("zone"))
        val capped = JSONObject(PlanJson.rolloverToJson(Rollover.Capped(2_000_000_000L)))
        assertEquals("capped", capped.getString("type"))
        assertEquals(2_000_000_000L, capped.getLong("maxBytes"))
        assertEquals("none", JSONObject(PlanJson.rolloverToJson(Rollover.None)).getString("type"))
    }

    @Test(expected = IllegalArgumentException::class) fun an_unknown_cycle_type_is_rejected() {
        PlanJson.cycleFromJson("""{"type":"weekly","time":"00:00","zone":"UTC"}""")
    }
}
