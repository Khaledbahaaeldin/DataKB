package io.github.khaledbahaaeldin.emberbyte.data.store

import android.content.Context
import androidx.room.Room
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface UsageStore {
    /** ADDS rx/tx to an existing row with the same key (atomic); inserts otherwise. */
    suspend fun addMinute(row: MinuteTotal)
    /** Replaces rows with the same (hour, uid, network, subscription) key. */
    suspend fun upsertHourly(rows: List<HourlyUsage>)
    suspend fun upsertAppMeta(meta: List<AppMeta>)
    suspend fun insertGap(gap: CoverageGap)
    suspend fun saveCheckpoint(source: String, reading: CounterReading)
    suspend fun loadCheckpoint(source: String): CounterReading?
    /** `from` inclusive, `to` exclusive, ordered by time. */
    suspend fun minuteRows(from: Instant, to: Instant): List<MinuteTotal>
    suspend fun hourlyRows(from: Instant, to: Instant): List<HourlyUsage>
    suspend fun appMeta(): List<AppMeta>
    /** Gaps overlapping [from, to]. */
    suspend fun gaps(from: Instant, to: Instant): List<CoverageGap>
    fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>>
    fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>>
    fun observeAppMeta(): Flow<List<AppMeta>>
    fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>>
    /** Newest `total_minute.minuteStart`, or null when there is none. */
    fun observeLastSample(): Flow<Instant?>
    /** Deletes rows strictly older than the retention of contract section 5. */
    suspend fun prune(now: Instant)
}

object UsageRetention {
    val MINUTES: Duration = Duration.ofDays(7)
    val HOURLY: Duration = Duration.ofDays(395) // about 13 months
    val GAPS: Duration = Duration.ofDays(395)
}

/** Open end used when a query only has a lower bound: the year 2100. */
val OPEN_END: Instant = Instant.parse("2100-01-01T00:00:00Z")

object UsageStores {
    /** Room-backed store in `emberbyte.db`. Room types never leave this module. */
    fun create(context: Context): UsageStore = RoomUsageStore(
        Room.databaseBuilder(context.applicationContext, EmberbyteDatabase::class.java, "emberbyte.db").build(),
    )
}
