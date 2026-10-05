package io.github.khaledbahaaeldin.emberbyte.data.alert

import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.engine.plan.percentUsed
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AlertNotice(
    val planId: Long, val planName: String, val thresholdPercent: Int, val usedBytes: Long,
    val effectiveCapBytes: Long, val remainingBytes: Long, val runOutExpected: Instant?,
)

class AlertEvaluator(private val plans: PlanRepository, private val store: PlanStore) {
    private val mutex = Mutex()

    /** Contract 10.2: one notice per plan at most (the highest crossed, not yet fired threshold of the current window). */
    suspend fun evaluate(): List<AlertNotice> = mutex.withLock {
        val notices = ArrayList<AlertNotice>()
        for (state in plans.observeActivePlanStates().first()) {
            val cap = state.effectiveCapBytes
            if (cap <= 0L) continue
            val percent = state.percentUsed()
            val last = store.lastFiredPercent(state.plan.id, state.window.start) ?: 0
            val top = store.alertRules(state.plan.id)
                .filter { it.enabled && it.thresholdPercent <= percent && it.thresholdPercent > last }
                .maxOfOrNull { it.thresholdPercent } ?: continue
            val forecast = plans.observeForecast(state.plan.id).first()
            notices += AlertNotice(state.plan.id, state.plan.name, top, state.usedBytes, cap, state.remainingBytes, forecast?.runOutExpected)
            store.setFiredPercent(state.plan.id, state.window.start, top)
        }
        notices
    }
}
