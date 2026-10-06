package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore
import io.github.khaledbahaaeldin.emberbyte.data.spike.RoomSpikeRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelM4Test {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val spikeStore = InMemorySpikeStore()
    private val spikes = RoomSpikeRepository(spikeStore)
    private val labels = mapOf("com.video" to "Video App", "com.music" to "Music App")
    private val labelOf: (String) -> String = { labels[it] ?: it }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        usage = FakeUsageRepository(clock, MutableSharedFlow()),
        plans = FakePlanRepository(clock),
        settings = FakeSettingsRepository(),
        permissions = FakePermissionRepository(),
        spikes = spikes,
        labelOf = labelOf,
        clock = clock,
        locale = Locale.ENGLISH,
        dates = flowOf(LocalDate.of(2026, 10, 5)),
    )

    @Test
    fun with_no_spikes_spike_tile_is_null() = runTest(dispatcher) {
        val vm = createViewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(vm.uiState.value.spike)
        job.cancel()
    }

    @Test
    fun with_two_undismissed_spikes_the_newest_is_shown_and_label_comes_from_label_of() = runTest(dispatcher) {
        val older = SpikeEvent(
            id = 1L,
            packageName = "com.music",
            windowStart = Instant.parse("2026-10-05T08:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T10:00:00Z"),
            windowMinutes = 120,
            bytes = 500_000_000L,
            baselineBytes = 50_000_000L,
            multiple = 10f,
            dismissed = false,
        )
        val newer = SpikeEvent(
            id = 2L,
            packageName = "com.video",
            windowStart = Instant.parse("2026-10-05T10:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T12:00:00Z"),
            windowMinutes = 120,
            bytes = 1_240_000_000L,
            baselineBytes = 150_000_000L,
            multiple = 8.3f,
            dismissed = false,
        )
        spikeStore.insertIfAbsent(older)
        spikeStore.insertIfAbsent(newer)

        val vm = createViewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val spike = vm.uiState.value.spike
        assertNotNull(spike)
        assertEquals(2L, spike?.id)
        assertEquals("com.video", spike?.packageName)
        assertEquals("Video App used a lot of data", spike?.title)
        job.cancel()
    }

    @Test
    fun dismiss_spike_removes_it_and_the_older_one_takes_its_place() = runTest(dispatcher) {
        val older = SpikeEvent(
            id = 1L,
            packageName = "com.music",
            windowStart = Instant.parse("2026-10-05T08:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T10:00:00Z"),
            windowMinutes = 120,
            bytes = 500_000_000L,
            baselineBytes = 50_000_000L,
            multiple = 10f,
            dismissed = false,
        )
        val newer = SpikeEvent(
            id = 2L,
            packageName = "com.video",
            windowStart = Instant.parse("2026-10-05T10:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T12:00:00Z"),
            windowMinutes = 120,
            bytes = 1_240_000_000L,
            baselineBytes = 150_000_000L,
            multiple = 8.3f,
            dismissed = false,
        )
        spikeStore.insertIfAbsent(older)
        spikeStore.insertIfAbsent(newer)

        val vm = createViewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.spike?.id)

        vm.onEvent(HomeEvent.DismissSpike(2L))
        advanceUntilIdle()

        val currentSpike = vm.uiState.value.spike
        assertNotNull(currentSpike)
        assertEquals(1L, currentSpike?.id)
        assertEquals("com.music", currentSpike?.packageName)
        assertEquals("Music App used a lot of data", currentSpike?.title)

        vm.onEvent(HomeEvent.DismissSpike(1L))
        advanceUntilIdle()

        assertNull(vm.uiState.value.spike)
        job.cancel()
    }

    @Test
    fun a_dismissed_spike_is_never_shown() = runTest(dispatcher) {
        val dismissedSpike = SpikeEvent(
            id = 10L,
            packageName = "com.video",
            windowStart = Instant.parse("2026-10-05T10:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T12:00:00Z"),
            windowMinutes = 120,
            bytes = 1_240_000_000L,
            baselineBytes = 150_000_000L,
            multiple = 8.3f,
            dismissed = true,
        )
        spikeStore.insertIfAbsent(dismissedSpike)

        val vm = createViewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(vm.uiState.value.spike)
        job.cancel()
    }
}
