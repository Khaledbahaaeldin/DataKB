package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Production [PlanRepository] for M2: there are no plans yet (they arrive in M3). */
class NoPlanRepository : PlanRepository {
    private fun unavailable() = Outcome.Failure(EmberbyteError.Unexpected("Data plans arrive in a later update"))

    override fun observePlans(includeArchived: Boolean): Flow<List<Plan>> = flowOf(emptyList())
    override fun observePlanState(planId: Long): Flow<PlanState?> = flowOf(null)
    override fun observeActivePlanStates(): Flow<List<PlanState>> = flowOf(emptyList())
    override fun observeForecast(planId: Long): Flow<Forecast?> = flowOf(null)
    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = flowOf(emptyList())
    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = flowOf(emptyList())
    override suspend fun upsertPlan(plan: Plan): Outcome<Long> = unavailable()
    override suspend fun archivePlan(planId: Long): Outcome<Unit> = unavailable()
    override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> = unavailable()
    override suspend fun deleteAddOn(id: Long): Outcome<Unit> = unavailable()
    override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> = unavailable()
    override suspend fun deleteFreeRule(id: Long): Outcome<Unit> = unavailable()
    override suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast> = unavailable()
}
