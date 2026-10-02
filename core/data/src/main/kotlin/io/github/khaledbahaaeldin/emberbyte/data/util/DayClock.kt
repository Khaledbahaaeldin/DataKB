package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Emits the current local date, then again whenever the WALL-CLOCK date changes. It polls instead of sleeping until midnight because
 * `delay` runs on monotonic time that stops in deep sleep, and because the user can change the clock or the time zone.
 */
class DayClock(private val clock: Clock, private val pollMillis: Long = 30_000L) {
    fun dates(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(pollMillis)
        }
    }.distinctUntilChanged()
}
