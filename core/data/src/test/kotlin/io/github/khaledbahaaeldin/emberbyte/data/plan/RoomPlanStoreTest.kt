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
}
