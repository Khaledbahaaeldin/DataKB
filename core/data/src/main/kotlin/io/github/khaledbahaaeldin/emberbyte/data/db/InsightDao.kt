package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InsightDao {
    @Query("SELECT * FROM app_budget ORDER BY id")
    abstract fun observeBudgets(): Flow<List<AppBudgetEntity>>

    @Query("SELECT * FROM app_budget ORDER BY id")
    abstract suspend fun budgets(): List<AppBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putBudget(entity: AppBudgetEntity): Long

    @Query("DELETE FROM app_budget WHERE id = :id")
    abstract suspend fun deleteBudgetRow(id: Long)

    @Query("DELETE FROM budget_state WHERE budgetId = :id")
    abstract suspend fun deleteBudgetStates(id: Long)

    @Transaction
    open suspend fun deleteBudget(id: Long) {
        deleteBudgetRow(id)
        deleteBudgetStates(id)
    }

    @Query("SELECT state FROM budget_state WHERE budgetId = :budgetId AND periodStart = :periodStart")
    abstract suspend fun lastState(budgetId: Long, periodStart: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putState(entity: BudgetStateEntity)

    @Query("SELECT * FROM spike_event WHERE dismissed = 0 ORDER BY detectedAt DESC, id DESC")
    abstract fun observeOpenSpikes(): Flow<List<SpikeEventEntity>>

    @Query("SELECT * FROM spike_event ORDER BY detectedAt DESC, id DESC")
    abstract fun observeAllSpikes(): Flow<List<SpikeEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSpike(entity: SpikeEventEntity): Long

    @Query("UPDATE spike_event SET dismissed = 1 WHERE id = :id")
    abstract suspend fun dismissSpike(id: Long): Int

    @Query("SELECT notifiedAt FROM spike_notice WHERE packageName = :packageName")
    abstract suspend fun noticeAt(packageName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putNotice(entity: SpikeNoticeEntity)

    @Query("DELETE FROM app_budget") abstract suspend fun deleteAllBudgets()
    @Query("DELETE FROM budget_state") abstract suspend fun deleteAllBudgetStates()
    @Query("DELETE FROM spike_event") abstract suspend fun deleteAllSpikes()
    @Query("DELETE FROM spike_notice") abstract suspend fun deleteAllNotices()
}
