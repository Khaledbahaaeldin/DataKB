package io.github.khaledbahaaeldin.emberbyte.data.plan

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabases
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface PlanStore {
    fun observePlans(): Flow<List<Plan>>
    fun observePlan(id: Long): Flow<Plan?>
    suspend fun plan(id: Long): Plan?
    suspend fun upsertPlan(plan: Plan): Long
    suspend fun setArchived(id: Long, archived: Boolean): Boolean
    fun observeAddOns(planId: Long): Flow<List<AddOn>>
    suspend fun upsertAddOn(addOn: AddOn): Long
    suspend fun deleteAddOn(id: Long)
    fun observeFreeRules(planId: Long): Flow<List<FreeRule>>
    suspend fun upsertFreeRule(rule: FreeRule): Long
    suspend fun deleteFreeRule(id: Long)
    fun observeAlertRules(planId: Long): Flow<List<AlertRule>>
    suspend fun alertRules(planId: Long): List<AlertRule>
    suspend fun upsertAlertRule(rule: AlertRule): Long
    suspend fun deleteAlertRule(id: Long)
    suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int?
    suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int)
}

object PlanStores {
    /** Room-backed store on the shared database. Room types never leave this module. */
    fun create(context: Context): PlanStore = RoomPlanStore(EmberbyteDatabases.get(context))
}
