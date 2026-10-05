package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.db.*
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import androidx.room.withTransaction

private fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
private fun daysOf(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
private fun Plan.toEntity() = PlanEntity(id, name, subscriptionId, capBytes, PlanJson.cycleToJson(cycle), PlanJson.rolloverToJson(rollover), archived)
private fun PlanEntity.toDomainOrNull(): Plan? = runCatching {
    Plan(id, name, subscriptionId, capBytes, PlanJson.cycleFromJson(cycleJson), PlanJson.rolloverFromJson(rolloverJson), archived)
}.getOrNull()
private fun AddOn.toEntity() = AddOnEntity(id, planId, label, bytes, validFrom.toEpochMilli(), validUntil.toEpochMilli())
private fun AddOnEntity.toDomain() = AddOn(id, planId, label, bytes, Instant.ofEpochMilli(validFrom), Instant.ofEpochMilli(validUntil))
private fun FreeRule.toEntity() = FreeRuleEntity(id, planId, label, maskOf(days), start.toSecondOfDay() / 60, end.toSecondOfDay() / 60, packageName)
private fun FreeRuleEntity.toDomain() = FreeRule(id, planId, label, daysOf(daysMask), LocalTime.ofSecondOfDay(startMinute * 60L), LocalTime.ofSecondOfDay(endMinute * 60L), packageName)
private fun AlertRule.toEntity() = AlertRuleEntity(id, planId, thresholdPercent, enabled)
private fun AlertRuleEntity.toDomain() = AlertRule(id, planId, thresholdPercent, enabled)

class RoomPlanStore(private val db: EmberbyteDatabase) : PlanStore {
    private val dao = db.planDao()

    override fun observePlans(): Flow<List<Plan>> = dao.observePlans().map { list -> list.mapNotNull { it.toDomainOrNull() } }
    override fun observePlan(id: Long): Flow<Plan?> = dao.observePlan(id).map { it?.toDomainOrNull() }
    override suspend fun plan(id: Long): Plan? = dao.plan(id)?.toDomainOrNull()
    override suspend fun upsertPlan(plan: Plan): Long = dao.putPlan(plan.toEntity())
    override suspend fun setArchived(id: Long, archived: Boolean): Boolean = dao.setArchived(id, archived) > 0
    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = dao.observeAddOns(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun upsertAddOn(addOn: AddOn): Long = dao.putAddOn(addOn.toEntity())
    override suspend fun deleteAddOn(id: Long) = dao.deleteAddOn(id)
    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = dao.observeFreeRules(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun upsertFreeRule(rule: FreeRule): Long = dao.putFreeRule(rule.toEntity())
    override suspend fun deleteFreeRule(id: Long) = dao.deleteFreeRule(id)
    override fun observeAlertRules(planId: Long): Flow<List<AlertRule>> = dao.observeAlertRules(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun alertRules(planId: Long): List<AlertRule> = dao.alertRules(planId).map { it.toDomain() }
    override suspend fun upsertAlertRule(rule: AlertRule): Long = dao.putAlertRule(rule.toEntity())
    override suspend fun deleteAlertRule(id: Long) = dao.deleteAlertRule(id)
    override suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int? = dao.firedPercent(planId, windowStart.toEpochMilli())
    override suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int) =
        dao.putFired(AlertFiredEntity(planId, windowStart.toEpochMilli(), percent))

    internal var beforeCommit: (suspend () -> Unit)? = null

    override suspend fun savePlanWithChildren(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>, alertThresholds: Set<Int>): Long =
        db.withTransaction {
            val id = dao.putPlan(plan.toEntity())
            dao.deleteAddOnsExcept(id, addOns.map { it.id }.filter { it != 0L })
            addOns.forEach { dao.putAddOn(it.copy(planId = id).toEntity()) }
            dao.deleteFreeRulesExcept(id, freeRules.map { it.id }.filter { it != 0L })
            freeRules.forEach { dao.putFreeRule(it.copy(planId = id).toEntity()) }
            val wanted = alertThresholds.toList()
            dao.deleteAlertRulesExcept(id, wanted)
            if (wanted.isNotEmpty()) dao.enableAlertRules(id, wanted)
            val existing = dao.thresholds(id).toSet()
            wanted.filter { it !in existing }.forEach { dao.putAlertRule(AlertRuleEntity(planId = id, thresholdPercent = it, enabled = true)) }
            beforeCommit?.invoke()
            id
        }
}
