package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoPlanRepositoryTest {
    private val repo = NoPlanRepository()

    @Test fun all_observations_are_empty() = runBlocking {
        assertTrue(repo.observePlans().first().isEmpty())
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertNull(repo.observePlanState(1).first())
        assertNull(repo.observeForecast(1).first())
        assertTrue(repo.observeAddOns(1).first().isEmpty())
        assertTrue(repo.observeFreeRules(1).first().isEmpty())
    }

    @Test fun mutations_fail_with_an_outcome_instead_of_throwing() = runBlocking {
        val plan = Plan(0, "x", null, 1, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        assertTrue(repo.upsertPlan(plan) is Outcome.Failure)
        assertTrue(repo.archivePlan(1) is Outcome.Failure)
        assertTrue(repo.whatIf(1, 1, Instant.EPOCH) is Outcome.Failure)
        val addOn = AddOn(1, 1, "test", 100, Instant.EPOCH, Instant.EPOCH)
        assertTrue(repo.upsertAddOn(addOn) is Outcome.Failure)
        assertTrue(repo.deleteAddOn(1) is Outcome.Failure)
        val rule = FreeRule(1, 1, "free", emptySet(), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, null)
        assertTrue(repo.upsertFreeRule(rule) is Outcome.Failure)
        assertTrue(repo.deleteFreeRule(1) is Outcome.Failure)
    }
}
