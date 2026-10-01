package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Emits the current local date immediately, then again shortly after every local midnight. */
class DayClock(private val clock: Clock) {
    fun dates(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant().atZone(clock.zone)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone).toInstant()
            val wait = Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(1_000L)
            delay(wait + 500L)
        }
    }.distinctUntilChanged()
}
