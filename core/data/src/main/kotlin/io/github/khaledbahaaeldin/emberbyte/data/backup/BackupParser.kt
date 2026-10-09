package io.github.khaledbahaaeldin.emberbyte.data.backup

import android.util.JsonReader
import android.util.JsonToken
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanJson
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BackupParser {
    fun parse(input: InputStream): ParsedBackup {
        try {
            val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
            reader.beginObject()
            var contractVersion: Int? = null
            val plans = mutableListOf<RestoredPlan>()
            val budgets = mutableListOf<AppBudget>()
            var hasUsage = false

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "contractVersion" -> contractVersion = reader.nextInt()
                    "plans" -> readPlans(reader, plans)
                    "appBudgets" -> readBudgets(reader, budgets)
                    "usageHourly" -> {
                        hasUsage = true
                        break
                    }
                    else -> reader.skipValue()
                }
            }

            if (contractVersion == null) {
                throw BackupFormatException("file", "Missing contractVersion")
            }

            val usageSequence = if (hasUsage) {
                sequence {
                    try {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            yield(readUsageRow(reader))
                        }
                        reader.endArray()
                        while (reader.hasNext()) {
                            reader.skipValue()
                        }
                        reader.endObject()
                    } catch (e: BackupFormatException) {
                        throw e
                    } catch (e: Exception) {
                        when (e) {
                            is IOException, is IllegalStateException, is IllegalArgumentException,
                            is DateTimeException, is JSONException, is NumberFormatException ->
                                throw BackupFormatException("file", "The backup is damaged")
                            else -> throw e
                        }
                    } finally {
                        runCatching { reader.close() }
                    }
                }
            } else {
                while (reader.hasNext()) {
                    reader.skipValue()
                }
                reader.endObject()
                reader.close()
                emptySequence()
            }

            return ParsedBackup(contractVersion, plans, budgets, usageSequence)
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalStateException, is IllegalArgumentException,
                is DateTimeException, is JSONException, is NumberFormatException ->
                    throw BackupFormatException("file", "The backup is damaged")
                else -> throw e
            }
        }
    }

    private fun readPlans(reader: JsonReader, plans: MutableList<RestoredPlan>) {
        reader.beginArray()
        while (reader.hasNext()) {
            val obj = readJsonObject(reader)
            plans += parsePlan(obj)
        }
        reader.endArray()
    }

    private fun parsePlan(obj: JSONObject): RestoredPlan {
        val id = obj.getLong("id")
        val name = obj.getString("name")
        val subId = if (obj.isNull("subscriptionId")) null else obj.getInt("subscriptionId")
        val capBytes = obj.getLong("capBytes")
        val cycle = PlanJson.cycleFromJson(obj.getJSONObject("cycle").toString())
        val rollover = PlanJson.rolloverFromJson(obj.getJSONObject("rollover").toString())
        val archived = if (obj.has("archived")) obj.getBoolean("archived") else false
        val plan = Plan(id, name, subId, capBytes, cycle, rollover, archived)

        val addOns = mutableListOf<AddOn>()
        val addOnsArray = obj.optJSONArray("addOns")
        if (addOnsArray != null) {
            for (i in 0 until addOnsArray.length()) {
                val a = addOnsArray.getJSONObject(i)
                addOns += AddOn(
                    id = a.getLong("id"),
                    planId = a.getLong("planId"),
                    label = a.getString("label"),
                    bytes = a.getLong("bytes"),
                    validFrom = Instant.parse(a.getString("validFrom")),
                    validUntil = Instant.parse(a.getString("validUntil")),
                )
            }
        }

        val freeRules = mutableListOf<FreeRule>()
        val freeRulesArray = obj.optJSONArray("freeRules")
        if (freeRulesArray != null) {
            for (i in 0 until freeRulesArray.length()) {
                val f = freeRulesArray.getJSONObject(i)
                val daysArray = f.getJSONArray("days")
                val days = (0 until daysArray.length()).map { DayOfWeek.valueOf(daysArray.getString(it)) }.toSet()
                val pkg = if (f.isNull("package")) null else f.getString("package")
                freeRules += FreeRule(
                    id = f.getLong("id"),
                    planId = f.getLong("planId"),
                    label = f.getString("label"),
                    days = days,
                    start = LocalTime.parse(f.getString("start")),
                    end = LocalTime.parse(f.getString("end")),
                    packageName = pkg,
                )
            }
        }

        val alertRules = mutableListOf<AlertRule>()
        val alertRulesArray = obj.optJSONArray("alertRules")
        if (alertRulesArray != null) {
            for (i in 0 until alertRulesArray.length()) {
                val ar = alertRulesArray.getJSONObject(i)
                alertRules += AlertRule(
                    id = ar.getLong("id"),
                    planId = ar.getLong("planId"),
                    thresholdPercent = ar.getInt("thresholdPercent"),
                    enabled = ar.getBoolean("enabled"),
                )
            }
        }

        return RestoredPlan(plan, addOns, freeRules, alertRules)
    }

    private fun readBudgets(reader: JsonReader, budgets: MutableList<AppBudget>) {
        reader.beginArray()
        while (reader.hasNext()) {
            val obj = readJsonObject(reader)
            budgets += parseBudget(obj)
        }
        reader.endArray()
    }

    private fun parseBudget(obj: JSONObject): AppBudget {
        val id = obj.getLong("id")
        val packageName = obj.getString("package")
        val limitBytes = obj.getLong("limitBytes")
        val period = BudgetPeriod.valueOf(obj.getString("period"))
        val planId = if (obj.isNull("planId")) null else obj.getLong("planId")
        val warnAtPercent = obj.getInt("warnAtPercent")
        val enabled = obj.getBoolean("enabled")
        return AppBudget(id, packageName, limitBytes, period, planId, warnAtPercent, enabled)
    }

    private fun readUsageRow(reader: JsonReader): ParsedUsageRow {
        reader.beginObject()
        var hourStart: Instant? = null
        var packageName: String? = null
        var network: NetworkKind? = null
        var subscriptionId: Int? = null
        var rxBytes: Long? = null
        var txBytes: Long? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "hourStart" -> hourStart = Instant.parse(reader.nextString())
                "package" -> packageName = reader.nextString()
                "network" -> network = NetworkKind.valueOf(reader.nextString())
                "subscriptionId" -> subscriptionId = reader.nextInt()
                "rxBytes" -> rxBytes = reader.nextLong()
                "txBytes" -> txBytes = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (hourStart == null || packageName == null || network == null ||
            subscriptionId == null || rxBytes == null || txBytes == null
        ) {
            throw BackupFormatException("file", "The backup is damaged")
        }

        return ParsedUsageRow(hourStart, packageName, network, subscriptionId, rxBytes, txBytes)
    }

    private fun readJsonObject(reader: JsonReader): JSONObject {
        val obj = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            obj.put(name, readJsonValue(reader))
        }
        reader.endObject()
        return obj
    }

    private fun readJsonArray(reader: JsonReader): JSONArray {
        val arr = JSONArray()
        reader.beginArray()
        while (reader.hasNext()) {
            arr.put(readJsonValue(reader))
        }
        reader.endArray()
        return arr
    }

    private fun readJsonValue(reader: JsonReader): Any {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readJsonObject(reader)
            JsonToken.BEGIN_ARRAY -> readJsonArray(reader)
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> {
                val s = reader.nextString()
                if (s.contains('.') || s.contains('e') || s.contains('E')) {
                    s.toDouble()
                } else {
                    s.toLong()
                }
            }
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            else -> throw BackupFormatException("file", "The backup is damaged")
        }
    }
}
