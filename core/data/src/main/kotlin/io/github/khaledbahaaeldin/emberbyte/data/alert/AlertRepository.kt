package io.github.khaledbahaaeldin.emberbyte.data.alert

import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeRules(planId: Long): Flow<List<AlertRule>>
    suspend fun upsertRule(rule: AlertRule): Outcome<Long>
    suspend fun deleteRule(id: Long): Outcome<Unit>
}
