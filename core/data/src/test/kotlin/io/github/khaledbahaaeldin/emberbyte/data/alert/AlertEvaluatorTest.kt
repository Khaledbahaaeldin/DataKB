package io.github.khaledbahaaeldin.emberbyte.data.alert

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private val october = Instant.parse("2026-10-15T12:00:00Z")
    private val store = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private fun repo(now: Instant) = RoomPlanRepository(store, usage, Clock.fixed(now, ZoneOffset.UTC), ticks = flowOf(now))
    private fun evaluator(now: Instant = october) = AlertEvaluator(repo(now), store)
    private suspend fun plan(rules: List<Int> = listOf(50, 80, 100), enabled: Boolean = true): Long {
        val id = store.upsertPlan(Plan(0, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None))
        rules.forEach { store.upsertAlertRule(AlertRule(0, id, it, enabled)) }
        return id
    }
    private var minuteCounter = 0L
    private suspend fun use(bytes: Long, day: String = "2026-10-10") =
        usage.addMinute(MinuteTotal(Instant.parse("${day}T10:00:00Z").plusSeconds(60 * minuteCounter++), NetworkKind.MOBILE, 1, bytes, 0))

    @Test fun crossing_a_threshold_fires_once() = runBlocking {
        plan(); use(5_200_000_000L)
        val first = evaluator().evaluate()
        assertEquals(listOf(50), first.map { it.thresholdPercent })
        assertEquals("Main", first.single().planName)
        assertTrue(evaluator().evaluate().isEmpty())
    }
    @Test fun each_later_threshold_fires_when_crossed() = runBlocking {
        plan(); use(5_200_000_000L); evaluator().evaluate()
        use(2_900_000_000L)                                      // 8.1 GB
        assertEquals(listOf(80), evaluator().evaluate().map { it.thresholdPercent })
        use(2_400_000_000L)                                      // 10.5 GB
        val last = evaluator().evaluate().single()
        assertEquals(100, last.thresholdPercent)
        assertEquals(0L, last.remainingBytes)
    }
    @Test fun a_jump_over_several_thresholds_fires_only_the_highest() = runBlocking {
        plan(); use(9_500_000_000L)
        assertEquals(listOf(80), evaluator().evaluate().map { it.thresholdPercent })
        assertTrue(evaluator().evaluate().isEmpty())             // 50 never fires afterwards
    }
    @Test fun the_percentage_is_rounded_down() = runBlocking {
        plan(); use(7_999_000_000L)                              // 79.99 %
        assertEquals(listOf(50), evaluator().evaluate().map { it.thresholdPercent })
    }
    @Test fun disabled_rules_and_plans_without_rules_stay_quiet() = runBlocking {
        plan(enabled = false); plan(rules = emptyList()); use(9_900_000_000L)
        assertTrue(evaluator().evaluate().isEmpty())
    }
    @Test fun a_new_cycle_fires_again() = runBlocking {
        plan(); use(5_200_000_000L); evaluator().evaluate()
        use(5_200_000_000L, day = "2026-11-10")
        val november = Instant.parse("2026-11-15T12:00:00Z")
        assertEquals(listOf(50), evaluator(november).evaluate().map { it.thresholdPercent })
    }
    @Test fun the_notice_carries_the_forecast_and_the_numbers() = runBlocking {
        val id = plan(); use(5_200_000_000L)
        val n = evaluator().evaluate().single()
        assertEquals(5_200_000_000L, n.usedBytes)
        assertEquals(10_000_000_000L, n.effectiveCapBytes)
        assertEquals(4_800_000_000L, n.remainingBytes)
        assertEquals(repo(october).observeForecast(id).first()!!.runOutExpected, n.runOutExpected)   // the same forecast the Plans screen shows
    }
}
