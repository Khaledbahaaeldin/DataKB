package io.github.khaledbahaaeldin.emberbyte.data.alert

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAlertRepositoryTest {
    private val store = InMemoryPlanStore()
    private val repo = RoomAlertRepository(store)
    private suspend fun plan() = store.upsertPlan(Plan(0, "Main", null, 1, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None))

    @Test fun a_rule_is_stored_and_observed_in_threshold_order() = runBlocking {
        val p = plan()
        repo.upsertRule(AlertRule(0, p, 90, true)); repo.upsertRule(AlertRule(0, p, 50, true))
        assertEquals(listOf(50, 90), repo.observeRules(p).first().map { it.thresholdPercent })
    }
    @Test fun thresholds_outside_1_to_100_are_invalid() = runBlocking {
        val p = plan()
        assertEquals("thresholdPercent", ((repo.upsertRule(AlertRule(0, p, 0, true)) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertEquals("thresholdPercent", ((repo.upsertRule(AlertRule(0, p, 101, true)) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertTrue(repo.upsertRule(AlertRule(0, p, 100, true)) is Outcome.Success)
    }
    @Test fun a_duplicate_threshold_on_one_plan_is_rejected_but_editing_the_same_rule_is_fine() = runBlocking {
        val p = plan()
        val id = (repo.upsertRule(AlertRule(0, p, 80, true)) as Outcome.Success).value
        assertEquals("threshold", ((repo.upsertRule(AlertRule(0, p, 80, true)) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertTrue(repo.upsertRule(AlertRule(id, p, 80, false)) is Outcome.Success)
    }
    @Test fun a_rule_for_a_missing_plan_is_not_found_and_delete_removes() = runBlocking {
        assertEquals(EmberbyteError.NotFound, (repo.upsertRule(AlertRule(0, 99, 80, true)) as Outcome.Failure).error)
        val p = plan()
        val id = (repo.upsertRule(AlertRule(0, p, 80, true)) as Outcome.Success).value
        assertTrue(repo.deleteRule(id) is Outcome.Success)
        assertTrue(repo.observeRules(p).first().isEmpty())
    }
}
