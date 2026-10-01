package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observePlans(includeArchived: Boolean = false): Flow<List<Plan>>
    fun observePlanState(planId: Long): Flow<PlanState?>
    fun observeActivePlanStates(): Flow<List<PlanState>>
    fun observeForecast(planId: Long): Flow<Forecast?>
    fun observeAddOns(planId: Long): Flow<List<AddOn>>
    fun observeFreeRules(planId: Long): Flow<List<FreeRule>>
    suspend fun upsertPlan(plan: Plan): Outcome<Long>
    suspend fun archivePlan(planId: Long): Outcome<Unit>
    suspend fun upsertAddOn(addOn: AddOn): Outcome<Long>
    suspend fun deleteAddOn(id: Long): Outcome<Unit>
    suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long>
    suspend fun deleteFreeRule(id: Long): Outcome<Unit>

    /** Forecast with extra daily usage added from [from] onward. Not persisted. */
    suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast>
}
