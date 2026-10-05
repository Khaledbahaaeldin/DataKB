package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class BudgetStoreContractTest {
    abstract fun createStore(): BudgetStore
    private fun draft(pkg: String = "com.video") = AppBudget(0, pkg, 750_000_000L, BudgetPeriod.CYCLE, 4L, 90, true)

    @Test fun a_new_budget_gets_an_id_and_reads_back_unchanged() = runBlocking {
        val s = createStore(); val id = s.upsertBudget(draft())
        assertTrue(id > 0)
        assertEquals(listOf(draft().copy(id = id)), s.budgets())
        assertEquals(listOf(draft().copy(id = id)), s.observeBudgets().first())
    }
    @Test fun upserting_an_existing_id_replaces_it_and_keeps_a_null_plan() = runBlocking {
        val s = createStore(); val id = s.upsertBudget(draft())
        s.upsertBudget(draft().copy(id = id, planId = null, period = BudgetPeriod.DAILY, enabled = false))
        val back = s.budgets().single()
        assertEquals(BudgetPeriod.DAILY, back.period); assertNull(back.planId); assertFalse(back.enabled)
    }
    @Test fun deleting_a_budget_removes_its_remembered_states() = runBlocking {
        val s = createStore(); val id = s.upsertBudget(draft()); val period = Instant.parse("2026-10-01T00:00:00Z")
        s.setState(id, period, BudgetState.WARNING)
        s.deleteBudget(id)
        assertTrue(s.budgets().isEmpty()); assertNull(s.lastState(id, period))
    }
    @Test fun states_are_remembered_per_budget_and_period() = runBlocking {
        val s = createStore(); val id = s.upsertBudget(draft())
        val p1 = Instant.parse("2026-10-01T00:00:00Z"); val p2 = Instant.parse("2026-11-01T00:00:00Z")
        assertNull(s.lastState(id, p1))
        s.setState(id, p1, BudgetState.WARNING); s.setState(id, p1, BudgetState.EXCEEDED)
        assertEquals(BudgetState.EXCEEDED, s.lastState(id, p1)); assertNull(s.lastState(id, p2))
    }
}
