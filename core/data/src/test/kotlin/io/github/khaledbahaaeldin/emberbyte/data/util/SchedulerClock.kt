package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A clock that moves with the coroutine test scheduler's virtual time. */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val base: Instant,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = SchedulerClock(scheduler, base, zone)
    override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
}
