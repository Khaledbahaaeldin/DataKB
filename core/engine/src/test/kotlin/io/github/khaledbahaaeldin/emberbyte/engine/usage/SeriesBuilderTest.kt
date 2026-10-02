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

    @Test fun an_hour_belongs_to_the_bucket_that_contains_its_start_instant() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30: local 7 Oct starts at 2026-10-06T18:30Z
        // window 18:00Z-20:00Z holds hours 18:00Z (yesterday locally) and 19:00Z (today); nothing else is known, so it is shared equally
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata,
            minuteRows = emptyList(),
            hourlyRows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.MOBILE, 1_000)),
        )
        assertEquals(500L, points.single().mobileBytes)
    }

    @Test fun the_same_instant_rule_makes_today_and_the_week_bar_agree() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val rows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.WIFI, 1_000), hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.WIFI, 400))
        val today = SeriesBuilder.build(DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata, emptyList(), rows).single()
        val week = SeriesBuilder.build(DateRange(i("2026-10-01T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, kolkata, emptyList(), rows)
        assertEquals(today.totalBytes, week.single { it.start == today.start }.totalBytes)
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

    @Test fun month_buckets_aggregate_across_multiple_days() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-09-01T00:00:00Z"), i("2026-11-01T00:00:00Z")), Granularity.MONTH, utc,
            minuteRows = listOf(
                minute("2026-09-15T12:00:00Z", NetworkKind.MOBILE, 100),
                minute("2026-10-10T08:00:00Z", NetworkKind.MOBILE, 200),
            ),
            hourlyRows = listOf(
                hourly("2026-10-10T08:00:00Z", 10001, NetworkKind.MOBILE, 350),
            ),
        )
        assertEquals(listOf(i("2026-09-01T00:00:00Z"), i("2026-10-01T00:00:00Z")), points.map { it.start })
        assertEquals(100L, points[0].mobileBytes)
        assertEquals(350L, points[1].mobileBytes)
    }

    @Test fun combined_filter_keeps_only_matching_network_and_subscription() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100, sub = 1),
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 200, sub = 2),
                minute("2026-10-07T10:00:00Z", NetworkKind.WIFI, 300, sub = 1),
            ),
            hourlyRows = emptyList(),
            filter = UsageFilter(network = NetworkKind.MOBILE, subscriptionId = 1),
        )
        assertEquals(100L, points.single().mobileBytes)
        assertEquals(0L, points.single().wifiBytes)
    }

    @Test fun a_burst_in_the_first_half_of_a_window_is_not_counted_twice() {
        // 100 MB used between 10:00 and 10:30. The sampler wrote hour 10 = 100 and hour 11 = 0 (zero rows exist);
        // the system window row says 100 (all of it). The day must show 100, not 150.
        val minutes = (0 until 60).map { minute("2026-10-07T11:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 0) } +
            minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100_000_000)
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 100_000_000))
        val day = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single()
        assertEquals(100_000_000L, day.mobileBytes)
    }

    @Test fun a_window_the_sampler_only_saw_the_end_of_is_not_counted_twice() {
        // The device test case: the sampler started at 13:44 inside the system window 12:00-14:00 (hour 12 uncovered, hour 13 covered 16 minutes).
        val minutes = (44 until 60).map { minute("2026-10-07T13:${"%02d".format(it)}:00Z", NetworkKind.WIFI, 2_278_750) } // 36.46 MB
        val system = listOf(hourly("2026-10-07T12:00:00Z", 10001, NetworkKind.WIFI, 37_890_000))
        val day = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single()
        assertEquals(37_890_000L, day.wifiBytes)
    }

    @Test fun a_window_only_the_system_knows_is_shared_equally_between_its_two_hours() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T10:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(150L, 150L), points.map { it.mobileBytes })
    }

    @Test fun the_surplus_goes_to_the_hour_the_sampler_covered_less() {
        val hour11 = (0 until 60).map { minute("2026-10-07T11:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 10) } // 600, fully covered
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 1_000))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T10:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, hour11, system)
        assertEquals(listOf(400L, 600L), points.map { it.mobileBytes })
    }

    @Test fun hours_that_start_before_the_range_are_ignored_even_when_their_window_overlaps_it() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T11:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(150L), points.map { it.mobileBytes })
    }

    @Test fun the_total_never_decreases_while_the_sampler_catches_up_with_the_system() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 500))
        var previous = 0L
        for (sampled in listOf(0L, 100L, 300L, 500L, 700L)) {
            val minutes = if (sampled == 0L) emptyList() else listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, sampled))
            val total = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single().mobileBytes
            assertTrue("$total < $previous", total >= previous)
            previous = total
        }
    }

    @Test fun a_range_that_ends_before_it_starts_has_no_buckets() {
        val from = i("2026-10-07T12:00:00Z")
        assertTrue(SeriesBuilder.build(DateRange(from, from.minusSeconds(60)), Granularity.DAY, utc, emptyList(), emptyList()).isEmpty())
    }
}
