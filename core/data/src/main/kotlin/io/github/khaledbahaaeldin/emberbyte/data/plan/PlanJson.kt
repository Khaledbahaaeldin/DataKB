package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** JSON shapes of contract section 7; also what the M4 export will write. Throws on anything unreadable. */
internal object PlanJson {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun cycleToJson(cycle: Cycle): String {
        val o = JSONObject()
        when (cycle) {
            is Cycle.MonthlyOnDay -> o.put("type", "monthlyOnDay").put("dayOfMonth", cycle.dayOfMonth)
                .put("time", cycle.time.format(TIME)).put("zone", cycle.zone.id)
            is Cycle.EveryNDays -> o.put("type", "everyNDays").put("days", cycle.days).put("anchor", cycle.anchor.toString())
                .put("time", cycle.time.format(TIME)).put("zone", cycle.zone.id)
        }
        return o.toString()
    }

    fun cycleFromJson(json: String): Cycle {
        val o = JSONObject(json)
        val time = LocalTime.parse(o.getString("time"))
        val zone = ZoneId.of(o.getString("zone"))
        return when (val type = o.getString("type")) {
            "monthlyOnDay" -> Cycle.MonthlyOnDay(o.getInt("dayOfMonth"), time, zone)
            "everyNDays" -> Cycle.EveryNDays(o.getInt("days"), LocalDate.parse(o.getString("anchor")), time, zone)
            else -> throw IllegalArgumentException("Unknown cycle type $type")
        }
    }

    fun rolloverToJson(rollover: Rollover): String = when (rollover) {
        Rollover.None -> JSONObject().put("type", "none")
        Rollover.Full -> JSONObject().put("type", "full")
        is Rollover.Capped -> JSONObject().put("type", "capped").put("maxBytes", rollover.maxBytes)
    }.toString()

    fun rolloverFromJson(json: String): Rollover {
        val o = JSONObject(json)
        return when (val type = o.getString("type")) {
            "none" -> Rollover.None
            "full" -> Rollover.Full
            "capped" -> Rollover.Capped(o.getLong("maxBytes"))
            else -> throw IllegalArgumentException("Unknown rollover type $type")
        }
    }
}
