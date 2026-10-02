package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemZoneClockTest {
    private val original = TimeZone.getDefault()

    @After fun restore() = TimeZone.setDefault(original)

    @Test fun it_follows_the_default_time_zone_as_it_changes() {
        val clock = SystemZoneClock()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        assertEquals(ZoneId.of("Asia/Kolkata"), clock.zone)
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"))
        assertEquals(ZoneId.of("Africa/Cairo"), clock.zone)
    }
}
