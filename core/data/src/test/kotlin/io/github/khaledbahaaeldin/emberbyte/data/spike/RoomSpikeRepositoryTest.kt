package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.SpikeRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSpikeRepositoryTest {
    private val store = InMemorySpikeStore()
    private val repo: SpikeRepository = RoomSpikeRepository(store)

    private fun event(
        id: Long = 0L,
        pkg: String = "com.video",
        windowStart: Instant = Instant.parse("2026-10-15T10:00:00Z"),
        detectedAt: Instant = Instant.parse("2026-10-15T11:00:00Z"),
        windowMinutes: Int = 120,
        bytes: Long = 200_000_000L,
        baselineBytes: Long = 20_000_000L,
        multiple: Float = 10f,
        dismissed: Boolean = false,
    ) = SpikeEvent(id, pkg, windowStart, detectedAt, windowMinutes, bytes, baselineBytes, multiple, dismissed)

    @Test
    fun observeSpikes_returns_spikes_from_store() = runBlocking {
        store.insertIfAbsent(event(pkg = "com.video"))
        val spikes = repo.observeSpikes().first()
        assertEquals(1, spikes.size)
        assertEquals("com.video", spikes.single().packageName)
    }

    @Test
    fun dismiss_on_existing_id_succeeds() = runBlocking {
        store.insertIfAbsent(event(pkg = "com.video"))
        val existingId = store.observeSpikes(includeDismissed = true).first().single().id
        assertTrue(existingId > 0L)

        val result = repo.dismiss(existingId)
        assertEquals(Outcome.Success(Unit), result)
        assertTrue(repo.observeSpikes(includeDismissed = false).first().isEmpty())
        assertTrue(repo.observeSpikes(includeDismissed = true).first().single().dismissed)
    }

    @Test
    fun dismiss_on_missing_id_returns_not_found() = runBlocking {
        val result = repo.dismiss(999L)
        assertEquals(Outcome.Failure(EmberbyteError.NotFound), result)
    }
}
