package io.github.khaledbahaaeldin.emberbyte.data.budget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.db.AppBudgetEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomBudgetStoreTest : BudgetStoreContractTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun db() = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
    override fun createStore(): BudgetStore = RoomBudgetStore(db())

    @Test fun an_unreadable_enum_is_skipped_instead_of_throwing() = runBlocking {
        val db = db()
        val store = RoomBudgetStore(db)
        store.upsertBudget(AppBudget(0, "valid", 100, BudgetPeriod.DAILY, null, 80, true))
        db.insightDao().putBudget(AppBudgetEntity(packageName = "invalid", limitBytes = 100, period = "UNKNOWN_PERIOD", planId = null, warnAtPercent = 80, enabled = true))
        val list = store.observeBudgets().first()
        assertEquals(1, list.size)
        assertEquals("valid", list.first().packageName)
    }
}
