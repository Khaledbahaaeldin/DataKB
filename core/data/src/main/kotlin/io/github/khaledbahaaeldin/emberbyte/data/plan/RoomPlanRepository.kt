package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.forecast.ForecastEngine
import io.github.khaledbahaaeldin.emberbyte.engine.forecast.HourCurves
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanPreview
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.plan.CycleCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.PlanCalculator
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Emits the current instant now and then every minute (plan windows roll over at the renewal instant). */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(60_000L)
    }
}

/** The real [PlanRepository]: plans live in the [PlanStore], every number is derived from the [UsageStore] rows (contract 10.2). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlanRepository(
    private val plans: PlanStore,
    private val usage: UsageStore,
    private val clock: Clock,
    private val ticks: Flow<Instant> = minuteTicks(clock),
) : PlanRepository {

    private data class Inputs(val plan: Plan?, val addOns: List<AddOn>, val rules: List<FreeRule>)
    private data class Snapshot(val state: PlanState, val history: List<DayUsage>, val now: Instant, val hourCurve: DoubleArray)

    private fun rows(plan: Plan, now: Instant): Flow<PlanRows> {
        val window = CycleCalculator.windowAt(plan.cycle, now)
        val from = PlanStateAssembler.rowsFrom(plan, now)
        return combine(
            usage.observeMinuteRows(from, window.end),
            usage.observeHourlyRows(from, window.end),
            usage.observeAppMeta(),
            usage.observeGaps(window.start, window.end),
        ) { minutes, hourly, meta, gaps -> PlanRows(minutes, hourly, meta, gaps) }
    }

    private fun snapshots(planId: Long): Flow<Snapshot?> =
        combine(plans.observePlan(planId), plans.observeAddOns(planId), plans.observeFreeRules(planId)) { p, a, r -> Inputs(p, a, r) }
            .flatMapLatest { (plan, addOns, rules) ->
                if (plan == null) flowOf(null)
                else ticks.flatMapLatest { now ->
                    val zone = PlanCalculator.zoneOf(plan.cycle)
                    rows(plan, now).map { rows ->
                        Snapshot(
                            PlanStateAssembler.state(plan, addOns, rules, now, rows),
                            PlanStateAssembler.history(plan, rules, now, rows),
                            now,
                            HourCurves.personal(rows.minutes, now, zone),
                        )
                    }
                }
            }

    private fun forecastOf(snapshot: Snapshot, extra: Long = 0L, extraFrom: Instant = snapshot.now): Forecast =
        ForecastEngine.forecast(
            snapshot.state, snapshot.history, snapshot.now, PlanCalculator.zoneOf(snapshot.state.plan.cycle),
            extraBytesPerDay = extra, extraFrom = extraFrom,
            hourCurve = snapshot.hourCurve,
        )

    override fun observePlans(includeArchived: Boolean): Flow<List<Plan>> =
        plans.observePlans().map { list -> if (includeArchived) list else list.filter { !it.archived } }

    override fun observePlanState(planId: Long): Flow<PlanState?> = snapshots(planId).map { it?.state }

    override fun observeActivePlanStates(): Flow<List<PlanState>> = observePlans(false).flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { observePlanState(it.id) }) { states -> states.filterNotNull() }
    }

    override fun observeForecast(planId: Long): Flow<Forecast?> = snapshots(planId).map { it?.let(::forecastOf) }

    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = plans.observeAddOns(planId)

    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = plans.observeFreeRules(planId)

    private suspend fun <T> guarded(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error"))
    }

    override suspend fun upsertPlan(plan: Plan): Outcome<Long> {
        PlanValidation.plan(plan)?.let { return Outcome.Failure(it) }
        if (plan.id != 0L && plans.plan(plan.id) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertPlan(plan.copy(name = plan.name.trim())) }
    }

    override suspend fun archivePlan(planId: Long): Outcome<Unit> =
        guarded { plans.setArchived(planId, true) }.let { outcome ->
            when (outcome) {
                is Outcome.Failure -> outcome
                is Outcome.Success -> if (outcome.value) Outcome.Success(Unit) else Outcome.Failure(EmberbyteError.NotFound)
            }
        }

    override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> {
        PlanValidation.addOn(addOn)?.let { return Outcome.Failure(it) }
        if (plans.plan(addOn.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertAddOn(addOn.copy(label = addOn.label.trim())) }
    }

    override suspend fun deleteAddOn(id: Long): Outcome<Unit> = guarded { plans.deleteAddOn(id) }

    override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> {
        PlanValidation.freeRule(rule)?.let { return Outcome.Failure(it) }
        if (plans.plan(rule.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertFreeRule(rule.copy(label = rule.label.trim())) }
    }

    override suspend fun deleteFreeRule(id: Long): Outcome<Unit> = guarded { plans.deleteFreeRule(id) }

    override suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast> {
        if (plans.plan(planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { forecastOf(snapshots(planId).first() ?: error("plan vanished"), extraBytesPerDay, from) }
    }

    override suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview> {
        PlanValidation.plan(plan)?.let { return Outcome.Failure(it) }
        return guarded {
            val now = clock.instant()
            val rows = rows(plan, now).first()
            val zone = PlanCalculator.zoneOf(plan.cycle)
            val snapshot = Snapshot(
                PlanStateAssembler.state(plan, addOns, freeRules, now, rows),
                PlanStateAssembler.history(plan, freeRules, now, rows),
                now,
                HourCurves.personal(rows.minutes, now, zone),
            )
            PlanPreview(snapshot.state, forecastOf(snapshot))
        }
    }

    override fun validateDraft(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        errors.putAll(PlanValidation.planAll(plan))
        addOns.forEachIndexed { i, a ->
            PlanValidation.addOn(a)?.let { errors["addOn.$i.${it.field}"] = it.reason }
        }
        freeRules.forEachIndexed { i, r ->
            PlanValidation.freeRule(r)?.let { errors["freeRule.$i.${it.field}"] = it.reason }
        }
        return errors
    }
}
