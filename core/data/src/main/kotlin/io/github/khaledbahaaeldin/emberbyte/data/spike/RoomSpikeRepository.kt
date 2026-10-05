package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.SpikeRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import kotlinx.coroutines.flow.Flow

class RoomSpikeRepository(private val store: SpikeStore) : SpikeRepository {
    override fun observeSpikes(includeDismissed: Boolean): Flow<List<SpikeEvent>> =
        store.observeSpikes(includeDismissed)

    override suspend fun dismiss(id: Long): Outcome<Unit> =
        if (store.dismiss(id)) Outcome.Success(Unit) else Outcome.Failure(EmberbyteError.NotFound)
}
