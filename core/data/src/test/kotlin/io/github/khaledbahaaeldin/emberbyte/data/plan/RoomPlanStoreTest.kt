package io.github.khaledbahaaeldin.emberbyte.data.plan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.PlanEntity
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomPlanStoreTest : PlanStoreContractTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun db() = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
    override fun createStore(): PlanStore = RoomPlanStore(db())

    @Test fun a_plan_row_with_unreadable_json_is_skipped_instead_of_breaking_the_flow() = runBlocking {
        val db = db()
        val store = RoomPlanStore(db)
        store.upsertPlan(Plan(0, "Good", null, 1, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None))
        db.planDao().putPlan(PlanEntity(name = "Broken", subscriptionId = null, capBytes = 1, cycleJson = "not json", rolloverJson = "{}", archived = false))
        assertEquals(listOf("Good"), store.observePlans().first().map { it.name })
    }

    @Test fun a_failure_inside_the_transaction_rolls_everything_back() = runBlocking {
        val db = db()
        val store = RoomPlanStore(db)
        store.beforeCommit = { throw IllegalStateException("boom") }
        val addOn = AddOn(0, 0, "Boost", 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z"))
        val rule = FreeRule(0, 0, "Night", setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), null)
        val draft = Plan(0, "Draft", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)

        val result = runCatching {
            store.savePlanWithChildren(draft, listOf(addOn), listOf(rule), setOf(80))
        }
        assertTrue(result.isFailure)
        assertTrue(store.observePlans().first().isEmpty())
        assertTrue(db.planDao().alertRules(1).isEmpty())

        // Now test rollback of an update
        store.beforeCommit = null
        val id = store.savePlanWithChildren(draft, listOf(addOn), listOf(rule), setOf(80))
        val existingAddOn = store.observeAddOns(id).first().single()
        val existingRule = store.observeFreeRules(id).first().single()

        store.beforeCommit = { throw IllegalStateException("boom on update") }
        val updateResult = runCatching {
            store.savePlanWithChildren(draft.copy(id = id, name = "Changed"), emptyList(), emptyList(), setOf(50))
        }
        assertTrue(updateResult.isFailure)
        assertEquals("Draft", store.plan(id)!!.name)
        assertEquals(listOf(existingAddOn), store.observeAddOns(id).first())
        assertEquals(listOf(existingRule), store.observeFreeRules(id).first())
        assertEquals(listOf(80), store.alertRules(id).map { it.thresholdPercent })
    }
}
