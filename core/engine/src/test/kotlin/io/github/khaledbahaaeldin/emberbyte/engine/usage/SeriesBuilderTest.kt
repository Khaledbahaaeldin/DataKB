package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBuilderTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun i(s: String) = Instant.parse(s)
    private fun minute(at: String, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(i(at), network, sub, rx, tx)
    private fun hourly(at: String, uid: Int, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        HourlyUsage(i(at), uid, network, sub, rx, tx)

    // ---- bucket maths ----
    @Test fun bucketStart_for_every_granularity_in_utc() {
        val at = i("2026-10-07T13:45:10Z") // a Wednesday
        assertEquals(i("2026-10-07T13:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.HOUR, utc))
        assertEquals(i("2026-10-07T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.DAY, utc))
        assertEquals(i("2026-10-05T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.WEEK, utc))
        assertEquals(i("2026-10-01T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.MONTH, utc))
    }

    @Test fun nextBucketStart_for_every_granularity() {
        assertEquals(i("2026-10-07T14:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-07T13:00:00Z"), Granularity.HOUR, utc))
        assertEquals(i("2026-10-08T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-07T00:00:00Z"), Granularity.DAY, utc))
        assertEquals(i("2026-10-12T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-05T00:00:00Z"), Granularity.WEEK, utc))
        assertEquals(i("2026-11-01T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-01T00:00:00Z"), Granularity.MONTH, utc))
    }

    @Test fun day_buckets_follow_the_time_zone() {
        val cairo = ZoneId.of("Africa/Cairo") // UTC+3 on 2026-10-07
        val start = SeriesBuilder.bucketStart(i("2026-10-07T22:30:00Z"), Granularity.DAY, cairo) // 01:30 local on the 8th
        assertEquals(i("2026-10-07T21:00:00Z"), start)
    }

    // ---- build ----
    @Test fun buckets_are_zero_filled_across_the_range() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-05T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, emptyList(), emptyList(),
        )
        assertEquals(listOf(i("2026-10-05T00:00:00Z"), i("2026-10-06T00:00:00Z"), i("2026-10-07T00:00:00Z")), points.map { it.start })
        assertTrue(points.all { it.totalBytes == 0L })
    }

    @Test fun minute_rows_sum_into_their_day_split_by_network() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-06T10:00:00Z", NetworkKind.MOBILE, 100, 20),
                minute("2026-10-06T10:01:00Z", NetworkKind.MOBILE, 30),
                minute("2026-10-06T10:01:00Z", NetworkKind.WIFI, 500),
                minute("2026-10-07T23:59:00Z", NetworkKind.WIFI, 7),
            ),
            hourlyRows = emptyList(),
        )
        assertEquals(150L, points[0].mobileBytes)
        assertEquals(500L, points[0].wifiBytes)
        assertEquals(0L, points[1].mobileBytes)
        assertEquals(7L, points[1].wifiBytes)
    }

    @Test fun hourly_wins_when_it_is_larger_than_the_minute_sum() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100)),
            hourlyRows = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 200, 100)),
        )
        assertEquals(300L, points.single().mobileBytes)
    }

    @Test fun minute_sum_wins_when_it_is_larger_than_the_hourly_sum() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 900)),
            hourlyRows = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 200)),
        )
        assertEquals(900L, points.single().mobileBytes)
    }

    @Test fun hourly_rows_of_several_uids_are_summed_before_comparing() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = emptyList(),
            hourlyRows = listOf(
                hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.WIFI, 200),
                hourly("2026-10-07T10:00:00Z", 10002, NetworkKind.WIFI, 50, 25),
            ),
        )
        assertEquals(275L, points.single().wifiBytes)
    }

    @Test fun network_filter_keeps_only_that_network() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100),
                minute("2026-10-07T10:00:00Z", NetworkKind.WIFI, 400),
            ),
            hourlyRows = emptyList(),
            filter = UsageFilter(network = NetworkKind.WIFI),
        )
        assertEquals(0L, points.single().mobileBytes)
        assertEquals(400L, points.single().wifiBytes)
    }

    @Test fun subscription_filter_keeps_only_that_subscription() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100, sub = 1),
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 900, sub = 2),
            ),
            hourlyRows = emptyList(),
            filter = UsageFilter(subscriptionId = 2),
        )
        assertEquals(900L, points.single().mobileBytes)
    }

    @Test fun rows_at_or_after_the_end_of_the_range_are_ignored() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc,
            minuteRows = listOf(
                minute("2026-10-07T11:59:00Z", NetworkKind.MOBILE, 10),
                minute("2026-10-07T12:00:00Z", NetworkKind.MOBILE, 999),
            ),
            hourlyRows = emptyList(),
        )
        assertEquals(12, points.size)
        assertEquals(10L, points.last().mobileBytes)
    }

    @Test fun an_empty_range_gives_no_points() {
        val t = i("2026-10-07T00:00:00Z")
        assertTrue(SeriesBuilder.build(DateRange(t, t), Granularity.DAY, utc, emptyList(), emptyList()).isEmpty())
    }

    @Test fun an_hour_straddling_the_first_bucket_start_is_counted_in_the_first_bucket() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30: local 7 Oct starts at 2026-10-06T18:30Z
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata,
            minuteRows = emptyList(),
            hourlyRows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.MOBILE, 123)),
        )
        assertEquals(123L, points.single().mobileBytes)
    }

    @Test fun week_buckets_start_on_monday() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-01T00:00:00Z"), i("2026-10-14T00:00:00Z")), Granularity.WEEK, utc,
            minuteRows = listOf(minute("2026-10-06T10:00:00Z", NetworkKind.WIFI, 5)),
            hourlyRows = emptyList(),
        )
        assertEquals(listOf(i("2026-09-28T00:00:00Z"), i("2026-10-05T00:00:00Z"), i("2026-10-12T00:00:00Z")), points.map { it.start })
        assertEquals(5L, points[1].wifiBytes)
    }
}
