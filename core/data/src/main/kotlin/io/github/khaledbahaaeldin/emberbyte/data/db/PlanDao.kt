package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plan ORDER BY id") abstract fun observePlans(): Flow<List<PlanEntity>>
    @Query("SELECT * FROM plan WHERE id = :id") abstract fun observePlan(id: Long): Flow<PlanEntity?>
    @Query("SELECT * FROM plan WHERE id = :id") abstract suspend fun plan(id: Long): PlanEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putPlan(entity: PlanEntity): Long
    @Query("UPDATE plan SET archived = :archived WHERE id = :id") abstract suspend fun setArchived(id: Long, archived: Boolean): Int

    @Query("SELECT * FROM add_on WHERE planId = :planId ORDER BY validFrom, id") abstract fun observeAddOns(planId: Long): Flow<List<AddOnEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putAddOn(entity: AddOnEntity): Long
    @Query("DELETE FROM add_on WHERE id = :id") abstract suspend fun deleteAddOn(id: Long)

    @Query("SELECT * FROM free_rule WHERE planId = :planId ORDER BY id") abstract fun observeFreeRules(planId: Long): Flow<List<FreeRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putFreeRule(entity: FreeRuleEntity): Long
    @Query("DELETE FROM free_rule WHERE id = :id") abstract suspend fun deleteFreeRule(id: Long)

    @Query("SELECT * FROM alert_rule WHERE planId = :planId ORDER BY thresholdPercent") abstract fun observeAlertRules(planId: Long): Flow<List<AlertRuleEntity>>
    @Query("SELECT * FROM alert_rule WHERE planId = :planId ORDER BY thresholdPercent") abstract suspend fun alertRules(planId: Long): List<AlertRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putAlertRule(entity: AlertRuleEntity): Long
    @Query("DELETE FROM alert_rule WHERE id = :id") abstract suspend fun deleteAlertRule(id: Long)

    @Query("SELECT percent FROM alert_fired WHERE planId = :planId AND windowStart = :windowStart") abstract suspend fun firedPercent(planId: Long, windowStart: Long): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putFired(entity: AlertFiredEntity)

    @Query("DELETE FROM add_on WHERE planId = :planId AND id NOT IN (:keep)") abstract suspend fun deleteAddOnsExcept(planId: Long, keep: List<Long>)
    @Query("DELETE FROM free_rule WHERE planId = :planId AND id NOT IN (:keep)") abstract suspend fun deleteFreeRulesExcept(planId: Long, keep: List<Long>)
    @Query("DELETE FROM alert_rule WHERE planId = :planId AND thresholdPercent NOT IN (:keep)") abstract suspend fun deleteAlertRulesExcept(planId: Long, keep: List<Int>)
    @Query("UPDATE alert_rule SET enabled = 1 WHERE planId = :planId AND thresholdPercent IN (:wanted)") abstract suspend fun enableAlertRules(planId: Long, wanted: List<Int>)
    @Query("SELECT thresholdPercent FROM alert_rule WHERE planId = :planId") abstract suspend fun thresholds(planId: Long): List<Int>
}
