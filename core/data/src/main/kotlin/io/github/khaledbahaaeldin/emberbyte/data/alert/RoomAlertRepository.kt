package io.github.khaledbahaaeldin.emberbyte.data.alert

import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class RoomAlertRepository(private val store: PlanStore) : AlertRepository {
    override fun observeRules(planId: Long): Flow<List<AlertRule>> = store.observeAlertRules(planId)

    override suspend fun upsertRule(rule: AlertRule): Outcome<Long> {
        if (rule.thresholdPercent !in 1..100) return Outcome.Failure(EmberbyteError.Invalid("thresholdPercent", "Choose 1 to 100"))
        if (store.plan(rule.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        if (store.alertRules(rule.planId).any { it.id != rule.id && it.thresholdPercent == rule.thresholdPercent }) {
            return Outcome.Failure(EmberbyteError.Invalid("threshold", "Already set"))
        }
        return try { Outcome.Success(store.upsertAlertRule(rule)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error")) }
    }

    override suspend fun deleteRule(id: Long): Outcome<Unit> = try { store.deleteAlertRule(id); Outcome.Success(Unit) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error")) }
}
