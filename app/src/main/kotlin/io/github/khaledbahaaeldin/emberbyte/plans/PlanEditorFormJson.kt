package io.github.khaledbahaaeldin.emberbyte.plans

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

internal object PlanEditorFormJson {
    fun encode(form: PlanEditorForm): String {
        val root = JSONObject()
        root.put("name", form.name)
        if (form.subscriptionId != null) {
            root.put("subscriptionId", form.subscriptionId)
        } else {
            root.put("subscriptionId", JSONObject.NULL)
        }
        root.put("capText", form.capText)
        root.put("capUnit", form.capUnit.name)
        root.put("cycleKind", form.cycleKind.name)
        root.put("dayOfMonth", form.dayOfMonth)
        root.put("everyDays", form.everyDays)
        root.put("anchor", form.anchor.toString())
        root.put("renewalTime", form.renewalTime.toString())
        root.put("rolloverKind", form.rolloverKind.name)
        root.put("rolloverCapText", form.rolloverCapText)
        root.put("rolloverCapUnit", form.rolloverCapUnit.name)

        val addOnsArray = JSONArray()
        for (a in form.addOns) {
            val aObj = JSONObject()
            aObj.put("id", a.id)
            aObj.put("label", a.label)
            aObj.put("amountText", a.amountText)
            aObj.put("unit", a.unit.name)
            aObj.put("validFrom", a.validFrom.toString())
            aObj.put("validUntil", a.validUntil.toString())
            addOnsArray.put(aObj)
        }
        root.put("addOns", addOnsArray)

        val freeRulesArray = JSONArray()
        for (r in form.freeRules) {
            val rObj = JSONObject()
            rObj.put("id", r.id)
            rObj.put("label", r.label)
            val daysArray = JSONArray()
            for (d in r.days) {
                daysArray.put(d.name)
            }
            rObj.put("days", daysArray)
            rObj.put("start", r.start.toString())
            rObj.put("end", r.end.toString())
            if (r.packageName != null) {
                rObj.put("packageName", r.packageName)
            } else {
                rObj.put("packageName", JSONObject.NULL)
            }
            freeRulesArray.put(rObj)
        }
        root.put("freeRules", freeRulesArray)

        val thresholdsArray = JSONArray()
        for (t in form.alertThresholds) {
            thresholdsArray.put(t)
        }
        root.put("alertThresholds", thresholdsArray)

        return root.toString()
    }

    fun decode(json: String): PlanEditorForm? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            val name = root.optString("name", "")
            val subscriptionId = if (root.isNull("subscriptionId")) null else root.getInt("subscriptionId")
            val capText = root.optString("capText", "")
            val capUnit = CapUnit.valueOf(root.getString("capUnit"))
            val cycleKind = CycleKind.valueOf(root.getString("cycleKind"))
            val dayOfMonth = root.getInt("dayOfMonth")
            val everyDays = root.getInt("everyDays")
            val anchor = LocalDate.parse(root.getString("anchor"))
            val renewalTime = LocalTime.parse(root.getString("renewalTime"))
            val rolloverKind = RolloverKind.valueOf(root.getString("rolloverKind"))
            val rolloverCapText = root.optString("rolloverCapText", "")
            val rolloverCapUnit = CapUnit.valueOf(root.getString("rolloverCapUnit"))

            val addOns = mutableListOf<AddOnDraft>()
            val addOnsArray = root.getJSONArray("addOns")
            for (i in 0 until addOnsArray.length()) {
                val aObj = addOnsArray.getJSONObject(i)
                addOns.add(
                    AddOnDraft(
                        id = aObj.getLong("id"),
                        label = aObj.optString("label", ""),
                        amountText = aObj.optString("amountText", ""),
                        unit = CapUnit.valueOf(aObj.getString("unit")),
                        validFrom = LocalDate.parse(aObj.getString("validFrom")),
                        validUntil = LocalDate.parse(aObj.getString("validUntil")),
                    ),
                )
            }

            val freeRules = mutableListOf<FreeRuleDraft>()
            val freeRulesArray = root.getJSONArray("freeRules")
            for (i in 0 until freeRulesArray.length()) {
                val rObj = freeRulesArray.getJSONObject(i)
                val daysArray = rObj.getJSONArray("days")
                val days = mutableSetOf<DayOfWeek>()
                for (j in 0 until daysArray.length()) {
                    days.add(DayOfWeek.valueOf(daysArray.getString(j)))
                }
                val pkg = if (rObj.isNull("packageName")) null else rObj.getString("packageName")
                freeRules.add(
                    FreeRuleDraft(
                        id = rObj.getLong("id"),
                        label = rObj.optString("label", ""),
                        days = days,
                        start = LocalTime.parse(rObj.getString("start")),
                        end = LocalTime.parse(rObj.getString("end")),
                        packageName = pkg,
                    ),
                )
            }

            val alertThresholds = mutableSetOf<Int>()
            val thresholdsArray = root.getJSONArray("alertThresholds")
            for (i in 0 until thresholdsArray.length()) {
                alertThresholds.add(thresholdsArray.getInt(i))
            }

            PlanEditorForm(
                name = name,
                subscriptionId = subscriptionId,
                capText = capText,
                capUnit = capUnit,
                cycleKind = cycleKind,
                dayOfMonth = dayOfMonth,
                everyDays = everyDays,
                anchor = anchor,
                renewalTime = renewalTime,
                rolloverKind = rolloverKind,
                rolloverCapText = rolloverCapText,
                rolloverCapUnit = rolloverCapUnit,
                addOns = addOns,
                freeRules = freeRules,
                alertThresholds = alertThresholds,
            )
        } catch (_: Exception) {
            null
        }
    }
}
