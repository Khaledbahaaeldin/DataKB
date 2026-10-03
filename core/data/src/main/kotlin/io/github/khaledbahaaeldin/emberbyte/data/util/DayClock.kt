package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the current local date, then again whenever the WALL-CLOCK date changes, and again when the time zone changes, even on the same date.
 * It polls instead of sleeping until midnight because `delay` runs on monotonic time that stops in deep sleep, and because the user can change
 * the clock or the time zone.
 */
class DayClock(private val clock: Clock, private val pollMillis: Long = 30_000L) {
    fun dates(): Flow<LocalDate> = flow {
        var last: Pair<LocalDate, ZoneId>? = null
        while (true) {
            val zone = clock.zone                                  // read once, so the pair is consistent
            val current = clock.instant().atZone(zone).toLocalDate() to zone
            if (current != last) {                                 // the date OR the zone changed
                emit(current.first)
                last = current
            }
            delay(pollMillis)
        }
    }
}
