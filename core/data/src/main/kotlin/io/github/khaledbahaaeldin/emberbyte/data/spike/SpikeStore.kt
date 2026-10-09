package io.github.khaledbahaaeldin.emberbyte.data.spike

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabases
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface SpikeStore {
    fun observeSpikes(includeDismissed: Boolean = false): Flow<List<SpikeEvent>>
    suspend fun insertIfAbsent(event: SpikeEvent): Boolean
    suspend fun dismiss(id: Long): Boolean
    suspend fun lastNoticeAt(packageName: String): Instant?
    suspend fun markNotified(packageName: String, at: Instant)
}

object SpikeStores {
    fun create(context: Context): SpikeStore = RoomSpikeStore(EmberbyteDatabases.get(context))
}
