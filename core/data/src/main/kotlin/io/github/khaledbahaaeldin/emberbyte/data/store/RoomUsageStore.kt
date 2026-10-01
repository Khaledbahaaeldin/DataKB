package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.data.db.AppMetaEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.CounterCheckpointEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.CoverageGapEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.TotalMinuteEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.UsageHourlyEntity
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun NetworkKind.code(): Int = if (this == NetworkKind.MOBILE) 0 else 1
private fun networkOf(code: Int): NetworkKind = if (code == 0) NetworkKind.MOBILE else NetworkKind.WIFI
private fun Instant.ms(): Long = toEpochMilli()
private fun at(ms: Long): Instant = Instant.ofEpochMilli(ms)

private fun MinuteTotal.toEntity() = TotalMinuteEntity(minuteStart.ms(), network.code(), subscriptionId, rxBytes, txBytes)
private fun TotalMinuteEntity.toDomain() = MinuteTotal(at(minuteStart), networkOf(network), subscriptionId, rxBytes, txBytes)
private fun HourlyUsage.toEntity() = UsageHourlyEntity(hourStart.ms(), uid, network.code(), subscriptionId, rxBytes, txBytes)
private fun UsageHourlyEntity.toDomain() = HourlyUsage(at(hourStart), uid, networkOf(network), subscriptionId, rxBytes, txBytes)
private fun AppMetaEntity.toDomain() = AppMeta(uid, packageName, label)
private fun CoverageGapEntity.toDomain() = CoverageGap(at(fromAt), at(toAt), GapReason.valueOf(reason))

class RoomUsageStore(db: EmberbyteDatabase) : UsageStore {
    private val dao = db.usageDao()

    override suspend fun addMinute(row: MinuteTotal) = dao.addMinute(row.toEntity())

    override suspend fun upsertHourly(rows: List<HourlyUsage>) = dao.upsertHourly(rows.map { it.toEntity() })

    override suspend fun upsertAppMeta(meta: List<AppMeta>) =
        dao.upsertAppMeta(meta.map { AppMetaEntity(it.uid, it.packageName, it.label, System.currentTimeMillis()) })

    override suspend fun insertGap(gap: CoverageGap) =
        dao.insertGap(CoverageGapEntity(fromAt = gap.from.ms(), toAt = gap.to.ms(), reason = gap.reason.name))

    override suspend fun saveCheckpoint(source: String, reading: CounterReading) =
        dao.putCheckpoint(CounterCheckpointEntity(source, reading.at.ms(), reading.rxBytes, reading.txBytes, reading.bootId))

    override suspend fun loadCheckpoint(source: String): CounterReading? =
        dao.checkpoint(source)?.let { CounterReading(at(it.at), it.rxBytes, it.txBytes, it.bootId) }

    override suspend fun minuteRows(from: Instant, to: Instant) = dao.minuteRows(from.ms(), to.ms()).map { it.toDomain() }

    override suspend fun hourlyRows(from: Instant, to: Instant) = dao.hourlyRows(from.ms(), to.ms()).map { it.toDomain() }

    override suspend fun appMeta() = dao.appMeta().map { it.toDomain() }

    override suspend fun gaps(from: Instant, to: Instant) = dao.gaps(from.ms(), to.ms()).map { it.toDomain() }

    override fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>> =
        dao.observeMinuteRows(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>> =
        dao.observeHourlyRows(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeAppMeta(): Flow<List<AppMeta>> = dao.observeAppMeta().map { list -> list.map { it.toDomain() } }

    override fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>> =
        dao.observeGaps(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeLastSample(): Flow<Instant?> = dao.observeLastSample().map { it?.let(::at) }

    override suspend fun prune(now: Instant) {
        dao.pruneMinutes(now.minus(UsageRetention.MINUTES).ms())
        dao.pruneHourly(now.minus(UsageRetention.HOURLY).ms())
        dao.pruneGaps(now.minus(UsageRetention.GAPS).ms())
    }
}
