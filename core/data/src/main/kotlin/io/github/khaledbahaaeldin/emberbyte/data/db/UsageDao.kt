package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {
    // ---- total_minute ----
    @Query("SELECT * FROM total_minute WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart, network, subscriptionId")
    abstract suspend fun minuteRows(from: Long, to: Long): List<TotalMinuteEntity>

    @Query("SELECT * FROM total_minute WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart, network, subscriptionId")
    abstract fun observeMinuteRows(from: Long, to: Long): Flow<List<TotalMinuteEntity>>

    @Query("SELECT * FROM total_minute WHERE minuteStart = :minuteStart AND network = :network AND subscriptionId = :subscriptionId")
    abstract suspend fun getMinute(minuteStart: Long, network: Int, subscriptionId: Int): TotalMinuteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putMinute(entity: TotalMinuteEntity)

    @Transaction
    open suspend fun addMinute(entity: TotalMinuteEntity) {
        val existing = getMinute(entity.minuteStart, entity.network, entity.subscriptionId)
        putMinute(
            if (existing == null) entity
            else existing.copy(rxBytes = existing.rxBytes + entity.rxBytes, txBytes = existing.txBytes + entity.txBytes),
        )
    }

    @Query("SELECT MAX(minuteStart) FROM total_minute")
    abstract fun observeLastSample(): Flow<Long?>

    @Query("DELETE FROM total_minute WHERE minuteStart < :before")
    abstract suspend fun pruneMinutes(before: Long)

    // ---- usage_hourly ----
    @Query("SELECT * FROM usage_hourly WHERE hourStart >= :from AND hourStart < :to ORDER BY hourStart, uid")
    abstract suspend fun hourlyRows(from: Long, to: Long): List<UsageHourlyEntity>

    @Query("SELECT * FROM usage_hourly WHERE hourStart >= :from AND hourStart < :to ORDER BY hourStart, uid")
    abstract fun observeHourlyRows(from: Long, to: Long): Flow<List<UsageHourlyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHourly(rows: List<UsageHourlyEntity>)

    @Query("DELETE FROM usage_hourly WHERE hourStart < :before")
    abstract suspend fun pruneHourly(before: Long)

    // ---- app_meta ----
    @Query("SELECT * FROM app_meta")
    abstract suspend fun appMeta(): List<AppMetaEntity>

    @Query("SELECT * FROM app_meta")
    abstract fun observeAppMeta(): Flow<List<AppMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAppMeta(rows: List<AppMetaEntity>)

    // ---- coverage_gap ----
    @Insert
    abstract suspend fun insertGap(entity: CoverageGapEntity)

    @Query("SELECT * FROM coverage_gap WHERE toAt >= :from AND fromAt <= :to ORDER BY fromAt")
    abstract suspend fun gaps(from: Long, to: Long): List<CoverageGapEntity>

    @Query("SELECT * FROM coverage_gap WHERE toAt >= :from AND fromAt <= :to ORDER BY fromAt")
    abstract fun observeGaps(from: Long, to: Long): Flow<List<CoverageGapEntity>>

    @Query("DELETE FROM coverage_gap WHERE toAt < :before")
    abstract suspend fun pruneGaps(before: Long)

    // ---- counter_checkpoint ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCheckpoint(entity: CounterCheckpointEntity)

    @Query("SELECT * FROM counter_checkpoint WHERE source = :source")
    abstract suspend fun checkpoint(source: String): CounterCheckpointEntity?
}
