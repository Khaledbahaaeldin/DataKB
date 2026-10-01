@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeContractBehaviourTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @Test fun upserting_an_existing_plan_keeps_its_id_and_replaces_it() = runTest {
        val repo = FakePlanRepository(clock)
        val seeded = repo.observePlans().first().single()
        val result = repo.upsertPlan(seeded.copy(name = "Renamed", capBytes = 20_000_000_000L))
        assertEquals(Outcome.Success(seeded.id), result)
        val plans = repo.observePlans().first()
        assertEquals(1, plans.size)
        assertEquals("Renamed", plans.single().name)
        assertEquals(20_000_000_000L, repo.observePlanState(seeded.id).first()!!.effectiveCapBytes)
    }

    @Test fun add_on_and_free_rule_round_trip() = runTest {
        val repo = FakePlanRepository(clock)
        val addOn = AddOn(0, 1L, "Booster", 1_000_000_000L, clock.instant(), clock.instant().plusSeconds(86_400))
        val rule = FreeRule(0, 1L, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(1, 0), LocalTime.of(6, 0), null)
        val addOnId = (repo.upsertAddOn(addOn) as Outcome.Success).value
        val ruleId = (repo.upsertFreeRule(rule) as Outcome.Success).value
        assertTrue(addOnId != 0L && ruleId != 0L)
        assertEquals(listOf(addOnId), repo.observeAddOns(1L).first().map { it.id })
        assertEquals(listOf(ruleId), repo.observeFreeRules(1L).first().map { it.id })
        assertTrue(repo.observeAddOns(2L).first().isEmpty())

        assertEquals(Outcome.Success(Unit), repo.deleteAddOn(addOnId))
        assertEquals(Outcome.Success(Unit), repo.deleteFreeRule(ruleId))
        assertTrue(repo.observeAddOns(1L).first().isEmpty())
        assertTrue(repo.observeFreeRules(1L).first().isEmpty())
    }

    @Test fun missing_plan_yields_not_found_for_archive_and_what_if() = runTest {
        val repo = FakePlanRepository(clock)
        assertEquals(Outcome.Failure(EmberbyteError.NotFound), repo.archivePlan(999L))
        assertEquals(Outcome.Failure(EmberbyteError.NotFound), repo.whatIf(999L, 1L, clock.instant()))
        assertTrue(repo.whatIf(1L, 1L, clock.instant()) is Outcome.Success)
    }

    @Test fun seeded_forecast_is_two_three_four_days_out_with_medium_confidence() = runTest {
        val f = FakePlanRepository(clock).observeForecast(1L).first()!!
        assertEquals(clock.instant().plusSeconds(2 * 86_400L), f.runOutEarliest)
        assertEquals(clock.instant().plusSeconds(3 * 86_400L), f.runOutExpected)
        assertEquals(clock.instant().plusSeconds(4 * 86_400L), f.runOutLatest)
    }

    @Test fun live_speed_timestamp_and_elapsed_come_from_the_clock_not_wall_time() = runTest(UnconfinedTestDispatcher()) {
        val mutable = MutableClock(Instant.parse("2026-10-05T12:00:00Z"))
        val tick = MutableSharedFlow<Unit>()
        val repo = FakeUsageRepository(mutable, tick)
        val seen = mutableListOf<io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed>()
        val job = launch { repo.observeLiveSpeed().collect { seen += it } }
        tick.emit(Unit)
        mutable.now = mutable.now.plusSeconds(25)
        tick.emit(Unit)
        job.cancel()
        assertEquals(FakeTraffic.rxBpsAt(0L), seen[0].rxBps)
        assertEquals(FakeTraffic.rxBpsAt(25_000L), seen[1].rxBps)
        assertEquals(FakeTraffic.txBpsAt(25_000L), seen[1].txBps)
        assertEquals(mutable.now, seen[1].at)
    }

    @Test fun today_is_split_940_mobile_300_wifi_and_series_ends_today() = runTest {
        val repo = FakeUsageRepository(clock)
        val today = repo.observeToday().first()
        assertEquals(940_000_000L, today.mobileBytes)
        assertEquals(300_000_000L, today.wifiBytes)
        val range = io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange(clock.instant(), clock.instant())
        val series = repo.observeSeries(range, io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity.DAY).first()
        assertEquals(today.date.atStartOfDay(ZoneOffset.UTC).toInstant(), series.last().start)
        assertEquals(today.totalBytes, series.last().totalBytes)
    }
}
