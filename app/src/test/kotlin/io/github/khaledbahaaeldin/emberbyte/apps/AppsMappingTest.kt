package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsMappingTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun today_covers_exactly_the_local_day() {
        val range = rangeFor(AppsRange.TODAY, today, zone)
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), range.from)
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), range.to)
    }

    @Test fun last_seven_days_includes_today_and_the_six_before() =
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), rangeFor(AppsRange.LAST_7_DAYS, today, zone).from)

    @Test fun this_month_starts_on_the_first() =
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), rangeFor(AppsRange.THIS_MONTH, today, zone).from)

    @Test fun network_chips_map_to_usage_filters() {
        assertEquals(UsageFilter(), filterFor(AppsNetwork.ALL))
        assertEquals(UsageFilter(network = NetworkKind.MOBILE), filterFor(AppsNetwork.MOBILE))
        assertEquals(UsageFilter(network = NetworkKind.WIFI), filterFor(AppsNetwork.WIFI))
    }

    private val apps = listOf(
        AppUsage("com.video", "Video", 1, 600, 400, null),
        AppUsage("com.chat", "Chat", 2, 100, 0, null),
    )

    private fun build(query: String = "", needsAccess: Boolean = false) =
        buildAppsUiState(apps, AppsRange.TODAY, AppsNetwork.ALL, AppSort.BYTES_DESC, query, ByteUnits.DECIMAL, needsAccess)

    @Test fun rows_total_and_loaded_flag() {
        val state = build()
        assertEquals(listOf("Video", "Chat"), state.apps.map { it.label })
        assertEquals(1_100L, state.totalBytes)
        assertTrue(state.loaded)
    }

    @Test fun search_matches_label_or_package_ignoring_case_and_updates_the_total() {
        assertEquals(listOf("Chat"), build("CHA").apps.map { it.label })
        assertEquals(listOf("Video"), build("com.vid").apps.map { it.label })
        assertEquals(100L, build("chat").totalBytes)
        assertTrue(build("nothing like this").apps.isEmpty())
    }

    @Test fun blank_query_shows_everything() = assertEquals(2, build("   ").apps.size)

    @Test fun the_usage_access_flag_is_passed_through() {
        assertTrue(build(needsAccess = true).needsUsageAccess)
        assertFalse(build(needsAccess = false).needsUsageAccess)
    }
}
