package io.github.khaledbahaaeldin.emberbyte.data.backup

import androidx.room.withTransaction
import io.github.khaledbahaaeldin.emberbyte.data.budget.toEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.AppMetaEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.UsageHourlyEntity
import io.github.khaledbahaaeldin.emberbyte.data.plan.toEntity
import io.github.khaledbahaaeldin.emberbyte.data.store.toEntity
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage

class RoomRestoreStore(private val db: EmberbyteDatabase) : RestoreStore {
    private val planDao = db.planDao()
    private val insightDao = db.insightDao()
    private val usageDao = db.usageDao()

    override suspend fun replaceAll(
        plans: List<RestoredPlan>,
        budgets: List<AppBudget>,
        usage: Sequence<HourlyUsage>,
        meta: () -> List<AppMeta>,
    ) {
        db.withTransaction {
            planDao.deleteAllFired()
            planDao.deleteAllAlertRules()
            planDao.deleteAllFreeRules()
            planDao.deleteAllAddOns()
            planDao.deleteAllPlans()
            insightDao.deleteAllSpikes()
            insightDao.deleteAllNotices()
            insightDao.deleteAllBudgetStates()
            insightDao.deleteAllBudgets()
            usageDao.deleteAllHourly()
            for (p in plans) {
                planDao.putPlan(p.plan.toEntity())
                p.addOns.forEach { planDao.putAddOn(it.toEntity()) }
                p.freeRules.forEach { planDao.putFreeRule(it.toEntity()) }
                p.alertRules.forEach { planDao.putAlertRule(it.copy(id = 0).toEntity()) }
            }
            budgets.forEach { insightDao.putBudget(it.toEntity()) }
            val batch = ArrayList<UsageHourlyEntity>(1_000)
            for (row in usage) {
                batch += row.toEntity()
                if (batch.size == 1_000) {
                    usageDao.upsertHourly(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                usageDao.upsertHourly(batch.toList())
            }
            usageDao.upsertAppMeta(meta().map { AppMetaEntity(it.uid, it.packageName, it.label, System.currentTimeMillis()) })
        }
    }
}
