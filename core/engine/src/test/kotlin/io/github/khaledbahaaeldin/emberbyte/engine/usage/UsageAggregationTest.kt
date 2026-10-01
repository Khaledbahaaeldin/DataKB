package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregationTest {
    private val h = Instant.parse("2026-10-07T10:00:00Z")
    private fun hourly(uid: Int, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1, hour: Instant = h) =
        HourlyUsage(hour, uid, network, sub, rx, tx)
    private fun minute(offsetMin: Long, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(h.plusSeconds(offsetMin * 60), network, sub, rx, tx)

    private val meta = mapOf(
        10001 to AppMeta(10001, "com.video", "Video"),
        10002 to AppMeta(10002, "com.browser", "browser"),
    )

    // ---- AppAggregator ----
    @Test fun groups_per_uid_and_splits_mobile_and_wifi() {
        val apps = AppAggregator.aggregate(
            listOf(
                hourly(10001, NetworkKind.MOBILE, 100, 50),
                hourly(10001, NetworkKind.WIFI, 400),
                hourly(10001, NetworkKind.MOBILE, 10, hour = h.plusSeconds(3600)),
            ),
            meta,
        )
        val app = apps.single()
        assertEquals("com.video", app.packageName)
        assertEquals("Video", app.label)
        assertEquals(160L, app.mobileBytes)
        assertEquals(400L, app.wifiBytes)
        assertNull(app.screenTimeMs)
    }

    @Test fun unknown_uid_gets_a_fallback_identity() {
        val app = AppAggregator.aggregate(listOf(hourly(10999, NetworkKind.WIFI, 5)), emptyMap()).single()
        assertEquals("uid:10999", app.packageName)
        assertEquals("UID 10999", app.label)
    }

    @Test fun apps_with_no_traffic_are_dropped() =
        assertTrue(AppAggregator.aggregate(listOf(hourly(10001, NetworkKind.MOBILE, 0, 0)), meta).isEmpty())

    @Test fun network_filter_drops_the_other_network_and_apps_that_only_used_it() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 100), hourly(10002, NetworkKind.WIFI, 900))
        val apps = AppAggregator.aggregate(rows, meta, UsageFilter(network = NetworkKind.MOBILE))
        assertEquals(listOf("com.video"), apps.map { it.packageName })
        assertEquals(0L, apps.single().wifiBytes)
    }

    @Test fun sorts_by_bytes_name_and_screen_time_fallback() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 100), hourly(10002, NetworkKind.MOBILE, 900))
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.BYTES_DESC).map { it.packageName })
        assertEquals(listOf("com.video", "com.browser"), AppAggregator.aggregate(rows, meta, sort = AppSort.BYTES_ASC).map { it.packageName })
        // names compare case-insensitively: "browser" < "Video"
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.NAME).map { it.packageName })
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.SCREEN_TIME_DESC).map { it.packageName })
    }

    // ---- dominantSubscriptionId ----
    @Test fun dominant_subscription_is_the_one_with_most_mobile_bytes() {
        val rows = listOf(
            minute(0, NetworkKind.MOBILE, 100, sub = 1),
            minute(1, NetworkKind.MOBILE, 50, sub = 2),
            minute(2, NetworkKind.MOBILE, 80, sub = 2),
            minute(3, NetworkKind.WIFI, 99_999, sub = -1),
        )
        assertEquals(2, dominantSubscriptionId(rows))
    }

    @Test fun dominant_subscription_is_minus_one_without_mobile_rows() {
        assertEquals(-1, dominantSubscriptionId(emptyList()))
        assertEquals(-1, dominantSubscriptionId(listOf(minute(0, NetworkKind.WIFI, 10))))
    }

    // ---- HourlyReconciler ----
    private fun fullHour(mobilePerMinute: Long, wifiPerMinute: Long = 0) = (0L until 60L).flatMap { m ->
        listOf(minute(m, NetworkKind.MOBILE, mobilePerMinute), minute(m, NetworkKind.WIFI, wifiPerMinute))
    }

    @Test fun incomplete_minute_coverage_leaves_rows_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 20_000))
        val minutes = (0L until 54L).map { minute(it, NetworkKind.MOBILE, 100) } // 54 < 55 covered minutes
        assertEquals(rows, HourlyReconciler.scale(h, rows, minutes))
    }

    @Test fun rows_within_tolerance_are_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 6_100)) // trusted 6000 (60 x 100): +1.7%
        assertEquals(rows, HourlyReconciler.scale(h, rows, fullHour(100)))
    }

    @Test fun rows_far_from_the_trusted_total_are_scaled_proportionally() {
        val rows = listOf(
            hourly(10001, NetworkKind.MOBILE, 9_000, 3_000), // 12_000
            hourly(10002, NetworkKind.MOBILE, 6_000, 0),     // 6_000  -> actual 18_000, trusted 6_000
        )
        val scaled = HourlyReconciler.scale(h, rows, fullHour(100))
        assertEquals(6_000L, scaled.sumOf { it.totalBytes })
        assertEquals(4_000L, scaled.first { it.uid == 10001 }.totalBytes)
        assertEquals(2_000L, scaled.first { it.uid == 10002 }.totalBytes)
    }

    @Test fun networks_are_scaled_independently() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 12_000), hourly(10001, NetworkKind.WIFI, 3_000))
        val scaled = HourlyReconciler.scale(h, rows, fullHour(mobilePerMinute = 100, wifiPerMinute = 50))
        assertEquals(6_000L, scaled.first { it.network == NetworkKind.MOBILE }.totalBytes)
        assertEquals(3_000L, scaled.first { it.network == NetworkKind.WIFI }.totalBytes) // 3000 == trusted 3000
    }

    @Test fun a_zero_trusted_total_leaves_rows_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 5_000))
        assertEquals(rows, HourlyReconciler.scale(h, rows, fullHour(mobilePerMinute = 0)))
    }
}
