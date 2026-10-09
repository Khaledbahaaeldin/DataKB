package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import kotlinx.coroutines.flow.Flow

interface SpikeRepository {
    fun observeSpikes(includeDismissed: Boolean = false): Flow<List<SpikeEvent>>
    suspend fun dismiss(id: Long): Outcome<Unit>
}
