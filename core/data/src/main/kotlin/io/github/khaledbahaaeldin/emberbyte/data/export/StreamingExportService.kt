package io.github.khaledbahaaeldin.emberbyte.data.export

import android.util.JsonWriter
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.CONTRACT_VERSION
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal fun csvField(value: String): String {
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    return value
}

class StreamingExportService(
    private val usage: UsageStore,
    private val plans: PlanStore,
    private val budgets: BudgetStore,
    private val clock: Clock,
) : ExportService {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun exportCsv(range: DateRange, out: OutputStream): Outcome<Unit> = try {
        val writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.write("hourStart,package,network,subscriptionId,rxBytes,txBytes")
        writer.write("\n")
        val packageByUid = usage.appMeta().associate { it.uid to it.packageName }
        usage.forEachHourlyChunk(range.from, range.to) { rows ->
            for (row in rows) {
                val pkg = packageByUid[row.uid] ?: "uid:${row.uid}"
                writer.write(row.hourStart.toString())
                writer.write(",")
                writer.write(csvField(pkg))
                writer.write(",")
                writer.write(row.network.name)
                writer.write(",")
                writer.write(row.subscriptionId.toString())
                writer.write(",")
                writer.write(row.rxBytes.toString())
                writer.write(",")
                writer.write(row.txBytes.toString())
                writer.write("\n")
            }
        }
        writer.flush()
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Export failed"))
    }

    override suspend fun exportJson(out: OutputStream): Outcome<Unit> = try {
        val writer = JsonWriter(BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)))
        writer.setIndent("")
        writer.beginObject()
        writer.name("contractVersion").value(CONTRACT_VERSION.toLong())
        writer.name("exportedAt").value(clock.instant().toString())

        // plans
        writer.name("plans")
        writer.beginArray()
        val planList = plans.observePlans().first()
        for (plan in planList) {
            writer.beginObject()
            writer.name("id").value(plan.id)
            writer.name("name").value(plan.name)
            writer.name("subscriptionId")
            val subId = plan.subscriptionId
            if (subId != null) {
                writer.value(subId.toLong())
            } else {
                writer.nullValue()
            }
            writer.name("capBytes").value(plan.capBytes)

            // cycle
            writer.name("cycle")
            writer.beginObject()
            when (val cycle = plan.cycle) {
                is Cycle.MonthlyOnDay -> {
                    writer.name("type").value("monthlyOnDay")
                    writer.name("dayOfMonth").value(cycle.dayOfMonth.toLong())
                    writer.name("time").value(cycle.time.format(timeFormatter))
                    writer.name("zone").value(cycle.zone.id)
                }
                is Cycle.EveryNDays -> {
                    writer.name("type").value("everyNDays")
                    writer.name("days").value(cycle.days.toLong())
                    writer.name("anchor").value(cycle.anchor.toString())
                    writer.name("time").value(cycle.time.format(timeFormatter))
                    writer.name("zone").value(cycle.zone.id)
                }
            }
            writer.endObject()

            // rollover
            writer.name("rollover")
            writer.beginObject()
            when (val rollover = plan.rollover) {
                Rollover.None -> {
                    writer.name("type").value("none")
                }
                Rollover.Full -> {
                    writer.name("type").value("full")
                }
                is Rollover.Capped -> {
                    writer.name("type").value("capped")
                    writer.name("maxBytes").value(rollover.maxBytes)
                }
            }
            writer.endObject()

            writer.name("archived").value(plan.archived)

            // addOns
            writer.name("addOns")
            writer.beginArray()
            val addOns = plans.observeAddOns(plan.id).first()
            for (addOn in addOns) {
                writer.beginObject()
                writer.name("id").value(addOn.id)
                writer.name("planId").value(addOn.planId)
                writer.name("label").value(addOn.label)
                writer.name("bytes").value(addOn.bytes)
                writer.name("validFrom").value(addOn.validFrom.toString())
                writer.name("validUntil").value(addOn.validUntil.toString())
                writer.endObject()
            }
            writer.endArray()

            // freeRules
            writer.name("freeRules")
            writer.beginArray()
            val freeRules = plans.observeFreeRules(plan.id).first()
            for (rule in freeRules) {
                writer.beginObject()
                writer.name("id").value(rule.id)
                writer.name("planId").value(rule.planId)
                writer.name("label").value(rule.label)
                writer.name("days")
                writer.beginArray()
                for (day in rule.days.sorted()) {
                    writer.value(day.name)
                }
                writer.endArray()
                writer.name("start").value(rule.start.format(timeFormatter))
                writer.name("end").value(rule.end.format(timeFormatter))
                writer.name("package")
                val pkg = rule.packageName
                if (pkg != null) {
                    writer.value(pkg)
                } else {
                    writer.nullValue()
                }
                writer.endObject()
            }
            writer.endArray()

            // alertRules
            writer.name("alertRules")
            writer.beginArray()
            val alertRules = plans.observeAlertRules(plan.id).first()
            for (alert in alertRules) {
                writer.beginObject()
                writer.name("id").value(alert.id)
                writer.name("planId").value(alert.planId)
                writer.name("thresholdPercent").value(alert.thresholdPercent.toLong())
                writer.name("enabled").value(alert.enabled)
                writer.endObject()
            }
            writer.endArray()

            writer.endObject() // plan
        }
        writer.endArray() // plans

        // appBudgets
        writer.name("appBudgets")
        writer.beginArray()
        val budgetList = budgets.budgets()
        for (budget in budgetList) {
            writer.beginObject()
            writer.name("id").value(budget.id)
            writer.name("package").value(budget.packageName)
            writer.name("limitBytes").value(budget.limitBytes)
            writer.name("period").value(budget.period.name)
            writer.name("planId")
            val planId = budget.planId
            if (planId != null) {
                writer.value(planId)
            } else {
                writer.nullValue()
            }
            writer.name("warnAtPercent").value(budget.warnAtPercent.toLong())
            writer.name("enabled").value(budget.enabled)
            writer.endObject()
        }
        writer.endArray()

        // usageHourly
        writer.name("usageHourly")
        writer.beginArray()
        val from = clock.instant().minus(UsageRetention.HOURLY).minus(Duration.ofDays(7))
        val to = clock.instant().plus(Duration.ofHours(2))
        val packageByUid = usage.appMeta().associate { it.uid to it.packageName }
        usage.forEachHourlyChunk(from, to) { rows ->
            for (row in rows) {
                writer.beginObject()
                writer.name("hourStart").value(row.hourStart.toString())
                writer.name("package").value(packageByUid[row.uid] ?: "uid:${row.uid}")
                writer.name("network").value(row.network.name)
                writer.name("subscriptionId").value(row.subscriptionId.toLong())
                writer.name("rxBytes").value(row.rxBytes)
                writer.name("txBytes").value(row.txBytes)
                writer.endObject()
            }
        }
        writer.endArray()

        writer.endObject() // root
        writer.flush()
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Export failed"))
    }
}
