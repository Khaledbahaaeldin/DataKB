package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetEvaluation
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetRunnerTest {
    private val budget = AppBudget(1, "com.video", 1000L, BudgetPeriod.DAILY, null, 80, true)
    private val notice = BudgetNotice(budget, "Video", 850L, BudgetState.WARNING)

    @Test
    fun disabled_notifications_do_not_evaluate_budgets() = runBlocking {
        var evaluated = 0
        BudgetRunner(enabled = { false }, evaluate = { evaluated++; BudgetEvaluation(listOf(notice), emptyList()) }, post = { }).run()
        assertEquals(0, evaluated)
    }

    @Test
    fun enabled_notifications_post_every_notice() = runBlocking {
        val posted = mutableListOf<BudgetNotice>()
        BudgetRunner(
            enabled = { true },
            evaluate = { BudgetEvaluation(listOf(notice, notice.copy(usedBytes = 1000L)), emptyList()) },
            post = { posted += it },
        ).run()
        assertEquals(2, posted.size)
    }

    @Test
    fun cleared_budgets_have_their_notifications_cancelled() = runBlocking {
        val cancelled = mutableListOf<Long>()
        BudgetRunner(
            enabled = { true },
            evaluate = { BudgetEvaluation(emptyList(), listOf(1L, 42L)) },
            post = {},
            cancel = { cancelled += it },
        ).run()
        assertEquals(listOf(1L, 42L), cancelled)
    }
}
