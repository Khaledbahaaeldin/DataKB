package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeSourcesTest {
    private val t = Instant.parse("2026-10-07T10:00:00Z")
    private fun snap(sec: Long) = CounterSnapshot(t.plusSeconds(sec), sec, 0, sec, 0, "b1")

    @Test fun counter_source_replays_the_script_then_repeats_the_last_snapshot() {
        val source = FakeCounterSource(listOf(snap(0), snap(1)))
        assertEquals(snap(0), source.read())
        assertEquals(snap(1), source.read())
        assertEquals(snap(1), source.read())
    }

    @Test fun network_stats_source_returns_the_scripted_rows_and_records_queries() = runBlocking {
        val rows = listOf(UidUsage(10001, 5, 6))
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to t) to rows))
        assertEquals(rows, source.query(NetworkKind.MOBILE, t, t.plusSeconds(3600)))
        assertEquals(emptyList<UidUsage>(), source.query(NetworkKind.WIFI, t, t.plusSeconds(3600)))
        assertEquals(2, source.queries.size)
    }

    @Test fun network_kind_source_updates_current_value() {
        val source = FakeNetworkKindSource(NetworkKind.MOBILE)
        assertEquals(NetworkKind.MOBILE, source.current.value)
        source.set(NetworkKind.WIFI)
        assertEquals(NetworkKind.WIFI, source.current.value)
        source.set(null)
        org.junit.Assert.assertNull(source.current.value)
    }

    @Test fun subscription_source_returns_configured_id() {
        val source = FakeSubscriptionSource(42)
        assertEquals(42, source.defaultDataSubscriptionId())
        source.id = -1
        assertEquals(-1, source.defaultDataSubscriptionId())
    }

    @Test fun app_info_source_resolves_and_records_queries() {
        val meta = io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta(1001, "com.example.app", "Example")
        val source = FakeAppInfoSource(mapOf(1001 to meta))
        assertEquals(meta, source.resolve(1001))
        org.junit.Assert.assertNull(source.resolve(9999))
        assertEquals(listOf(1001, 9999), source.resolved)
    }

    @Test fun usage_access_reflects_granted_state() {
        val access = FakeUsageAccess(true)
        org.junit.Assert.assertTrue(access.isGranted())
        access.granted = false
        org.junit.Assert.assertFalse(access.isGranted())
    }
}
