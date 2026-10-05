package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.spike.SpikeStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemorySpikeStore : SpikeStore {
    private val spikes = MutableStateFlow<List<SpikeEvent>>(emptyList())
    private val notices = MutableStateFlow<Map<String, Instant>>(emptyMap())
    private val spikeIds = AtomicLong(0)

    override fun observeSpikes(includeDismissed: Boolean): Flow<List<SpikeEvent>> =
        spikes.map { list ->
            list.filter { includeDismissed || !it.dismissed }
                .sortedWith(compareByDescending<SpikeEvent> { it.detectedAt }.thenByDescending { it.id })
        }

    override suspend fun insertIfAbsent(event: SpikeEvent): Boolean {
        var inserted = false
        spikes.update { list ->
            if (list.any { it.packageName == event.packageName && it.windowStart == event.windowStart }) {
                list
            } else {
                inserted = true
                val id = if (event.id == 0L) spikeIds.incrementAndGet() else event.id
                list + event.copy(id = id)
            }
        }
        return inserted
    }

    override suspend fun dismiss(id: Long): Boolean {
        var modified = false
        spikes.update { list ->
            if (list.any { it.id == id }) {
                modified = true
                list.map { if (it.id == id) it.copy(dismissed = true) else it }
            } else {
                list
            }
        }
        return modified
    }

    override suspend fun lastNoticeAt(packageName: String): Instant? =
        notices.value[packageName]

    override suspend fun markNotified(packageName: String, at: Instant) {
        notices.update { it + (packageName to at) }
    }
}
