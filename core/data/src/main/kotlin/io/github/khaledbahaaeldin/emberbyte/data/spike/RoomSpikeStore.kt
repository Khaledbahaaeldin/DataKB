package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.SpikeEventEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.SpikeNoticeEntity
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun SpikeEvent.toEntity() = SpikeEventEntity(
    id = id,
    packageName = packageName,
    windowStart = windowStart.toEpochMilli(),
    detectedAt = detectedAt.toEpochMilli(),
    windowMinutes = windowMinutes,
    bytes = bytes,
    baselineBytes = baselineBytes,
    multiple = multiple.toDouble(),
    dismissed = dismissed,
)

private fun SpikeEventEntity.toDomain() = SpikeEvent(
    id = id,
    packageName = packageName,
    windowStart = Instant.ofEpochMilli(windowStart),
    detectedAt = Instant.ofEpochMilli(detectedAt),
    windowMinutes = windowMinutes,
    bytes = bytes,
    baselineBytes = baselineBytes,
    multiple = multiple.toFloat(),
    dismissed = dismissed,
)

class RoomSpikeStore(db: EmberbyteDatabase) : SpikeStore {
    private val dao = db.insightDao()

    override fun observeSpikes(includeDismissed: Boolean): Flow<List<SpikeEvent>> {
        val flow = if (includeDismissed) dao.observeAllSpikes() else dao.observeOpenSpikes()
        return flow.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertIfAbsent(event: SpikeEvent): Boolean {
        val rowId = dao.insertSpike(event.toEntity())
        return rowId != -1L
    }

    override suspend fun dismiss(id: Long): Boolean =
        dao.dismissSpike(id) > 0

    override suspend fun lastNoticeAt(packageName: String): Instant? =
        dao.noticeAt(packageName)?.let { Instant.ofEpochMilli(it) }

    override suspend fun markNotified(packageName: String, at: Instant) =
        dao.putNotice(SpikeNoticeEntity(packageName, at.toEpochMilli()))
}
