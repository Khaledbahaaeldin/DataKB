package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A test clock that follows the scheduler's virtual time and whose zone can be changed while a test runs. */
@OptIn(ExperimentalCoroutinesApi::class)
class MutableZoneClock(
    private val scheduler: TestCoroutineScheduler,
    private val base: Instant,
    var zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableZoneClock(scheduler, base, zone)
    override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
}
