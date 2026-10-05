package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.data.BudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.minuteTicks
import io.github.khaledbahaaeldin.emberbyte.data.store.OPEN_END
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.budget.BudgetCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.plan.CycleCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class BudgetPeriodStatus(val status: BudgetStatus, val periodStart: Instant, val label: String)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomBudgetRepository(
    private val budgets: BudgetStore,
    private val plans: PlanStore,
    private val usage: UsageStore,
    private val clock: Clock,
    private val ticks: Flow<Instant> = minuteTicks(clock),
) : BudgetRepository {
    private data class Period(val window: CycleWindow, val subscriptionId: Int?)

    private fun periodFor(budget: AppBudget, planList: List<Plan>, now: Instant): Period {
        val plan = if (budget.period == BudgetPeriod.CYCLE) planList.firstOrNull { it.id == budget.planId } else null
        val planWindow = plan?.let { CycleCalculator.windowAt(it.cycle, now) }
        return Period(BudgetCalculator.period(budget, now, clock.zone, planWindow), plan?.subscriptionId)
    }

    private fun uidsOf(packageName: String, meta: List<AppMeta>): Set<Int> {
        val named = meta.filter { it.packageName == packageName }.map { it.uid }.toSet()
        return if (named.isNotEmpty() || !packageName.startsWith("uid:")) named else setOfNotNull(packageName.removePrefix("uid:").toIntOrNull())
    }

    private fun snapshot(now: Instant): Flow<List<BudgetPeriodStatus>> =
        combine(budgets.observeBudgets(), plans.observePlans(), usage.observeAppMeta()) { b, p, m -> Triple(b, p, m) }
            .flatMapLatest { (list, planList, meta) ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val periods = list.map { it to periodFor(it, planList, now) }
                    val from = periods.minOf { it.second.window.start }.minusSeconds(SeriesBuilder.WINDOW_SECONDS)
                    usage.observeHourlyRows(from, OPEN_END).map { rows ->
                        periods.map { (budget, period) ->
                            val uids = uidsOf(budget.packageName, meta)
                            val used = rows.filter {
                                it.uid in uids && it.network == NetworkKind.MOBILE &&
                                    it.hourStart >= period.window.start && it.hourStart < period.window.end &&
                                    (period.subscriptionId == null || it.subscriptionId == period.subscriptionId)
                            }.sumOf { it.totalBytes }
                            BudgetPeriodStatus(
                                BudgetStatus(budget, used, BudgetCalculator.state(budget, used)),
                                period.window.start,
                                meta.firstOrNull { it.packageName == budget.packageName }?.label ?: budget.packageName,
                            )
                        }
                    }
                }
            }

    override fun observeStatuses(): Flow<List<BudgetStatus>> =
        ticks.flatMapLatest { snapshot(it) }.map { list -> list.map { it.status } }

    suspend fun currentPeriodStatuses(): List<BudgetPeriodStatus> = snapshot(clock.instant()).first()

    private suspend fun <T> guarded(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error"))
    }

    override suspend fun upsert(budget: AppBudget): Outcome<Long> {
        val trimmedPackage = budget.packageName.trim()
        if (trimmedPackage.isEmpty()) {
            return Outcome.Failure(EmberbyteError.Invalid("packageName", "Package name cannot be blank"))
        }
        if (budget.limitBytes !in 1L..1_000_000_000_000_000L) {
            return Outcome.Failure(EmberbyteError.Invalid("limitBytes", "Limit must be greater than zero"))
        }
        if (budget.warnAtPercent !in 1..99) {
            return Outcome.Failure(EmberbyteError.Invalid("warnAtPercent", "Warning percentage must be between 1 and 99"))
        }
        val existing = try {
            budgets.budgets()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error"))
        }
        if (existing.any { it.packageName == trimmedPackage && it.id != budget.id }) {
            return Outcome.Failure(EmberbyteError.Invalid("packageName", "Already set"))
        }
        val targetPlanId = when (budget.period) {
            BudgetPeriod.DAILY -> null
            BudgetPeriod.CYCLE -> budget.planId
        }
        if (budget.period == BudgetPeriod.CYCLE && targetPlanId != null) {
            val plan = plans.plan(targetPlanId)
            if (plan == null) {
                return Outcome.Failure(EmberbyteError.NotFound)
            }
        }
        return guarded {
            budgets.upsertBudget(
                budget.copy(
                    packageName = trimmedPackage,
                    planId = targetPlanId,
                ),
            )
        }
    }

    override suspend fun delete(id: Long): Outcome<Unit> = guarded {
        budgets.deleteBudget(id)
    }
}
