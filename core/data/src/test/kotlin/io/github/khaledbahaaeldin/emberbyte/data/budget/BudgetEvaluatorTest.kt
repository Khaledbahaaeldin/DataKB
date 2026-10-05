package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetStatus
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEvaluatorTest {
    private val store = InMemoryBudgetStore()
    private val periodStart = Instant.parse("2026-10-15T00:00:00Z")

    private fun budget(
        id: Long = 1L,
        pkg: String = "com.video",
        limit: Long = 1_000L,
        period: BudgetPeriod = BudgetPeriod.DAILY,
        planId: Long? = null,
        warn: Int = 80,
        enabled: Boolean = true,
    ) = AppBudget(id, pkg, limit, period, planId, warn, enabled)

    private fun periodStatus(
        b: AppBudget,
        usedBytes: Long,
        state: BudgetState,
        start: Instant = periodStart,
        label: String = "YouTube",
    ) = BudgetPeriodStatus(BudgetStatus(b, usedBytes, state), start, label)

    @Test
    fun ok_to_warning_posts_once() = runBlocking {
        val b = budget()
        var currentStatuses = listOf(periodStatus(b, 850L, BudgetState.WARNING))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val first = evaluator.evaluate()
        assertEquals(1, first.size)
        assertEquals(BudgetState.WARNING, first.single().state)
        assertEquals(850L, first.single().usedBytes)
        assertEquals("YouTube", first.single().label)
        assertEquals(b, first.single().budget)

        val second = evaluator.evaluate()
        assertTrue(second.isEmpty())
    }

    @Test
    fun warning_to_exceeded_posts_once_more() = runBlocking {
        val b = budget()
        var currentStatuses = listOf(periodStatus(b, 850L, BudgetState.WARNING))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val first = evaluator.evaluate()
        assertEquals(1, first.size)
        assertEquals(BudgetState.WARNING, first.single().state)

        currentStatuses = listOf(periodStatus(b, 1200L, BudgetState.EXCEEDED))
        val second = evaluator.evaluate()
        assertEquals(1, second.size)
        assertEquals(BudgetState.EXCEEDED, second.single().state)
        assertEquals(1200L, second.single().usedBytes)

        val third = evaluator.evaluate()
        assertTrue(third.isEmpty())
    }

    @Test
    fun ok_straight_to_exceeded_posts_exceeded_once() = runBlocking {
        val b = budget()
        val currentStatuses = listOf(periodStatus(b, 1200L, BudgetState.EXCEEDED))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val notices = evaluator.evaluate()
        assertEquals(1, notices.size)
        assertEquals(BudgetState.EXCEEDED, notices.single().state)
        assertEquals(1200L, notices.single().usedBytes)

        assertTrue(evaluator.evaluate().isEmpty())
    }

    @Test
    fun a_better_state_is_stored_silently_and_a_new_worsening_notifies_again() = runBlocking {
        val b = budget()
        var currentStatuses = listOf(periodStatus(b, 1200L, BudgetState.EXCEEDED))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val first = evaluator.evaluate()
        assertEquals(1, first.size)
        assertEquals(BudgetState.EXCEEDED, first.single().state)

        // Limit raised or usage dropped -> back to WARNING
        val raised = b.copy(limitBytes = 2000L)
        currentStatuses = listOf(periodStatus(raised, 1200L, BudgetState.WARNING))
        val silent = evaluator.evaluate()
        assertTrue(silent.isEmpty())
        assertEquals(BudgetState.WARNING, store.lastState(b.id, periodStart))

        // Exceeded again -> notifies again
        currentStatuses = listOf(periodStatus(raised, 2500L, BudgetState.EXCEEDED))
        val reExceeded = evaluator.evaluate()
        assertEquals(1, reExceeded.size)
        assertEquals(BudgetState.EXCEEDED, reExceeded.single().state)
    }

    @Test
    fun a_new_period_notifies_again() = runBlocking {
        val b = budget()
        val nextDay = Instant.parse("2026-10-16T00:00:00Z")
        var currentStatuses = listOf(periodStatus(b, 850L, BudgetState.WARNING, start = periodStart))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val first = evaluator.evaluate()
        assertEquals(1, first.size)
        assertEquals(BudgetState.WARNING, first.single().state)
        assertTrue(evaluator.evaluate().isEmpty())

        currentStatuses = listOf(periodStatus(b, 850L, BudgetState.WARNING, start = nextDay))
        val nextPeriod = evaluator.evaluate()
        assertEquals(1, nextPeriod.size)
        assertEquals(BudgetState.WARNING, nextPeriod.single().state)
    }

    @Test
    fun disabled_budgets_and_ok_statuses_stay_quiet() = runBlocking {
        val disabled = budget(id = 1L, enabled = false)
        val okBudget = budget(id = 2L, pkg = "com.chat")
        val currentStatuses = listOf(
            periodStatus(disabled, 1200L, BudgetState.EXCEEDED),
            periodStatus(okBudget, 200L, BudgetState.OK, label = "Chat"),
        )
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        assertTrue(evaluator.evaluate().isEmpty())
    }

    @Test
    fun parallel_evaluations_post_a_transition_once() = runBlocking(Dispatchers.Default) {
        val b = budget()
        val currentStatuses = listOf(periodStatus(b, 850L, BudgetState.WARNING))
        val evaluator = BudgetEvaluator({ currentStatuses }, store)

        val allNotices = coroutineScope {
            awaitAll(
                async { evaluator.evaluate() },
                async { evaluator.evaluate() },
                async { evaluator.evaluate() },
            ).flatten()
        }
        assertEquals(1, allNotices.size)
        assertEquals(BudgetState.WARNING, allNotices.single().state)
    }
}
