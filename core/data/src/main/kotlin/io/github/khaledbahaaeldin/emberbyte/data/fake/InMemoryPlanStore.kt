package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryPlanStore : PlanStore {
    private val plans = MutableStateFlow<List<Plan>>(emptyList())
    private val addOns = MutableStateFlow<List<AddOn>>(emptyList())
    private val freeRules = MutableStateFlow<List<FreeRule>>(emptyList())
    private val alertRules = MutableStateFlow<List<AlertRule>>(emptyList())
    private val fired = MutableStateFlow<Map<Pair<Long, Instant>, Int>>(emptyMap())
    private val planIds = AtomicLong(0)
    private val addOnIds = AtomicLong(0)
    private val freeRuleIds = AtomicLong(0)
    private val alertRuleIds = AtomicLong(0)

    private fun <T> MutableStateFlow<List<T>>.put(item: T, id: Long, idOf: (T) -> Long, counter: AtomicLong, withId: (T, Long) -> T): Long {
        val real = if (id == 0L) counter.incrementAndGet() else id
        update { list -> list.filter { idOf(it) != real } + withId(item, real) }
        return real
    }

    override fun observePlans(): Flow<List<Plan>> = plans.map { it.sortedBy(Plan::id) }
    override fun observePlan(id: Long): Flow<Plan?> = plans.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun plan(id: Long): Plan? = plans.value.firstOrNull { it.id == id }
    override suspend fun upsertPlan(plan: Plan): Long = plans.put(plan, plan.id, Plan::id, planIds) { p, id -> p.copy(id = id) }
    override suspend fun setArchived(id: Long, archived: Boolean): Boolean {
        if (plans.value.none { it.id == id }) return false
        plans.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
        return true
    }
    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = addOns.map { list -> list.filter { it.planId == planId }.sortedWith(compareBy({ it.validFrom }, { it.id })) }
    override suspend fun upsertAddOn(addOn: AddOn): Long = addOns.put(addOn, addOn.id, AddOn::id, addOnIds) { a, id -> a.copy(id = id) }
    override suspend fun deleteAddOn(id: Long) = addOns.update { list -> list.filter { it.id != id } }
    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = freeRules.map { list -> list.filter { it.planId == planId }.sortedBy { it.id } }
    override suspend fun upsertFreeRule(rule: FreeRule): Long = freeRules.put(rule, rule.id, FreeRule::id, freeRuleIds) { r, id -> r.copy(id = id) }
    override suspend fun deleteFreeRule(id: Long) = freeRules.update { list -> list.filter { it.id != id } }
    override fun observeAlertRules(planId: Long): Flow<List<AlertRule>> = alertRules.map { list -> list.filter { it.planId == planId }.sortedBy { it.thresholdPercent } }
    override suspend fun alertRules(planId: Long): List<AlertRule> = alertRules.value.filter { it.planId == planId }.sortedBy { it.thresholdPercent }
    override suspend fun upsertAlertRule(rule: AlertRule): Long = alertRules.put(rule, rule.id, AlertRule::id, alertRuleIds) { r, id -> r.copy(id = id) }
    override suspend fun deleteAlertRule(id: Long) = alertRules.update { list -> list.filter { it.id != id } }
    override suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int? = fired.value[planId to windowStart]
    override suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int) = fired.update { it + ((planId to windowStart) to percent) }

    override suspend fun savePlanWithChildren(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>, alertThresholds: Set<Int>): Long = synchronized(this) {
        val id = if (plan.id == 0L) planIds.incrementAndGet() else plan.id
        plans.update { list -> list.filter { it.id != id } + plan.copy(id = id) }

        val savedAddOns = addOns.map { a ->
            val aid = if (a.id == 0L) addOnIds.incrementAndGet() else a.id
            a.copy(id = aid, planId = id)
        }
        this.addOns.update { list ->
            list.filter { it.planId != id } + savedAddOns
        }

        val savedRules = freeRules.map { r ->
            val rid = if (r.id == 0L) freeRuleIds.incrementAndGet() else r.id
            r.copy(id = rid, planId = id)
        }
        this.freeRules.update { list ->
            list.filter { it.planId != id } + savedRules
        }

        val wanted = alertThresholds.toSet()
        this.alertRules.update { list ->
            val otherRules = list.filter { it.planId != id }
            val thisPlanRules = list.filter { it.planId == id }
            val keptAndEnabled = thisPlanRules.filter { it.thresholdPercent in wanted }.map { it.copy(enabled = true) }
            val existingThresholds = keptAndEnabled.map { it.thresholdPercent }.toSet()
            val newRules = (wanted - existingThresholds).map {
                AlertRule(alertRuleIds.incrementAndGet(), id, it, enabled = true)
            }
            otherRules + keptAndEnabled + newRules
        }

        id
    }
}
