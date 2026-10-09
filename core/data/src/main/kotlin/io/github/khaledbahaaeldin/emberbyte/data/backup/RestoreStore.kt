package io.github.khaledbahaaeldin.emberbyte.data.backup

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage

interface RestoreStore {
    suspend fun replaceAll(
        plans: List<RestoredPlan>,
        budgets: List<AppBudget>,
        usage: Sequence<HourlyUsage>,
        meta: () -> List<AppMeta>,
    )
}
