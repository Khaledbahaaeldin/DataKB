package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.data.BudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomBudgetRepositoryTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val budgetStore = InMemoryBudgetStore()
    private val planStore = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private val repo = RoomBudgetRepository(budgetStore, planStore, usage, clock, ticks = flowOf(now))

    private fun budget(
        id: Long = 0L,
        pkg: String = "com.video",
        limit: Long = 1_000_000_000L,
        period: BudgetPeriod = BudgetPeriod.DAILY,
        planId: Long? = null,
        warn: Int = 80,
        enabled: Boolean = true,
    ) = AppBudget(id, pkg, limit, period, planId, warn, enabled)

    private suspend fun window(
        startIso: String,
        uid: Int,
        bytes: Long,
        network: NetworkKind = NetworkKind.MOBILE,
        sub: Int = 1,
    ) {
        usage.upsertHourly(listOf(HourlyUsage(Instant.parse(startIso), uid, network, sub, bytes, 0L)))
    }

    @Before
    fun setUp() = runBlocking {
        usage.upsertAppMeta(
            listOf(
                AppMeta(10001, "com.video", "Video"),
                AppMeta(10002, "com.video", "Video"),
                AppMeta(10003, "com.chat", "Chat"),
            ),
        )
    }

    @Test
    fun a_daily_budget_counts_mobile_bytes_of_all_uids_of_the_app_by_window_start() = runBlocking {
        budgetStore.upsertBudget(budget())
        window("2026-10-14T22:00:00Z", 10001, 100_000_000L) // yesterday's window
        window("2026-10-15T10:00:00Z", 10001, 300_000_000L)
        window("2026-10-15T10:00:00Z", 10002, 200_000_000L)
        window("2026-10-15T10:00:00Z", 10001, 900_000_000L, network = NetworkKind.WIFI)
        window("2026-10-15T10:00:00Z", 10003, 400_000_000L) // chat

        val s = repo.observeStatuses().first().single()
        assertEquals(500_000_000L, s.usedBytes)
    }

    @Test
    fun the_state_follows_the_calculator() = runBlocking {
        budgetStore.upsertBudget(budget(limit = 600_000_000L, warn = 80))
        window("2026-10-15T10:00:00Z", 10001, 400_000_000L) // 400/600 = 66% < 80% -> OK
        assertEquals(BudgetState.OK, repo.observeStatuses().first().single().state)

        window("2026-10-15T10:00:00Z", 10001, 500_000_000L) // 500/600 = 83% >= 80% -> WARNING
        assertEquals(BudgetState.WARNING, repo.observeStatuses().first().single().state)

        window("2026-10-15T10:00:00Z", 10001, 600_000_001L) // > 600 MB -> EXCEEDED
        assertEquals(BudgetState.EXCEEDED, repo.observeStatuses().first().single().state)
    }

    @Test
    fun a_cycle_budget_uses_the_plan_window_and_its_subscription() = runBlocking {
        val plan = Plan(
            id = 1L,
            name = "Main",
            subscriptionId = 2,
            capBytes = 10_000_000_000L,
            cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC),
            rollover = Rollover.None,
        )
        planStore.upsertPlan(plan)
        budgetStore.upsertBudget(budget(period = BudgetPeriod.CYCLE, planId = 1L))

        window("2026-10-03T10:00:00Z", 10001, 400_000_000L, sub = 2)
        window("2026-10-03T10:00:00Z", 10001, 700_000_000L, sub = 1)
        window("2026-10-03T10:00:00Z", 10001, 100_000_000L, sub = -1)
        window("2026-09-20T10:00:00Z", 10001, 900_000_000L, sub = 2) // before window

        val status = repo.observeStatuses().first().single()
        assertEquals(400_000_000L, status.usedBytes)
    }

    @Test
    fun a_cycle_budget_without_a_plan_uses_the_calendar_month() = runBlocking {
        budgetStore.upsertBudget(budget(period = BudgetPeriod.CYCLE, planId = null))
        window("2026-10-02T10:00:00Z", 10001, 200_000_000L)
        window("2026-09-30T10:00:00Z", 10001, 300_000_000L) // previous month

        val status = repo.observeStatuses().first().single()
        assertEquals(200_000_000L, status.usedBytes)
    }

    @Test
    fun a_cycle_budget_whose_plan_was_removed_falls_back_to_the_calendar_month() = runBlocking {
        budgetStore.upsertBudget(budget(period = BudgetPeriod.CYCLE, planId = 99L)) // missing plan
        window("2026-10-02T10:00:00Z", 10001, 200_000_000L)
        window("2026-09-30T10:00:00Z", 10001, 300_000_000L)

        val status = repo.observeStatuses().first().single()
        assertEquals(200_000_000L, status.usedBytes)
    }

    @Test
    fun the_period_statuses_carry_the_period_start_and_the_app_label() = runBlocking {
        budgetStore.upsertBudget(budget())
        val p = repo.currentPeriodStatuses().single()
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), p.periodStart)
        assertEquals("Video", p.label)
    }

    @Test
    fun an_unknown_app_label_falls_back_to_the_package_name() = runBlocking {
        budgetStore.upsertBudget(budget(pkg = "com.unknown"))
        val p = repo.currentPeriodStatuses().single()
        assertEquals("com.unknown", p.label)
    }

    @Test
    fun validation_names_the_field() = runBlocking {
        suspend fun field(b: AppBudget) = ((repo.upsert(b) as Outcome.Failure).error as EmberbyteError.Invalid).field
        assertEquals("packageName", field(budget(pkg = " ")))
        assertEquals("limitBytes", field(budget(limit = 0)))
        assertEquals("warnAtPercent", field(budget(warn = 0)))
        assertEquals("warnAtPercent", field(budget(warn = 100)))
        assertTrue(repo.upsert(budget(warn = 99)) is Outcome.Success<*>)
    }

    @Test
    fun one_budget_per_app_but_editing_it_is_fine() = runBlocking {
        val id = (repo.upsert(budget()) as Outcome.Success<Long>).value
        assertEquals("packageName", ((repo.upsert(budget()) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertTrue(repo.upsert(budget().copy(id = id, limitBytes = 1)) is Outcome.Success<*>)
    }

    @Test
    fun a_daily_budget_stores_no_plan_and_a_cycle_budget_for_a_missing_plan_is_not_found() = runBlocking {
        val id = (repo.upsert(budget(period = BudgetPeriod.DAILY, planId = 123L)) as Outcome.Success<Long>).value
        val stored = budgetStore.budgets().single { it.id == id }
        assertNull(stored.planId)

        val notFound = repo.upsert(budget(pkg = "com.other", period = BudgetPeriod.CYCLE, planId = 999L))
        assertEquals(Outcome.Failure(EmberbyteError.NotFound), notFound)
    }

    @Test
    fun delete_removes_the_budget_and_a_missing_id_is_still_success() = runBlocking {
        val id = (repo.upsert(budget()) as Outcome.Success<Long>).value
        val delSuccess = repo.delete(id)
        assertTrue(delSuccess is Outcome.Success<*>)
        assertTrue(budgetStore.budgets().isEmpty())

        val missingSuccess = repo.delete(999L)
        assertTrue(missingSuccess is Outcome.Success<*>)
    }
}
