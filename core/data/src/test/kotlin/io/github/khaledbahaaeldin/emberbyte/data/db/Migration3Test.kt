package io.github.khaledbahaaeldin.emberbyte.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration3Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createVersion2(name: String) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) { SchemaFile.statements(2).forEach(db::execSQL) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO plan (name, subscriptionId, capBytes, cycleJson, rolloverJson, archived) VALUES ('Main', NULL, 5, '{}', '{}', 0)")
            db.execSQL("INSERT INTO total_minute (minuteStart, network, subscriptionId, rxBytes, txBytes) VALUES (1000, 0, 3, 11, 22)")
        }
        helper.close()
    }

    @Test fun migrating_from_2_to_3_keeps_plans_and_usage_and_creates_the_insight_tables(): Unit = runBlocking {
        val name = "migration3-test.db"
        context.deleteDatabase(name)
        createVersion2(name)
        val room = Room.databaseBuilder(context, EmberbyteDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        assertNotNull(room.planDao().plan(1))
        assertEquals(1, room.usageDao().minuteRows(0L, Long.MAX_VALUE).size)
        val id = room.insightDao().putBudget(AppBudgetEntity(packageName = "com.video", limitBytes = 5, period = "DAILY", planId = null, warnAtPercent = 80, enabled = true))
        assertEquals(1L, id)
        assertEquals(1L, room.insightDao().insertSpike(SpikeEventEntity(packageName = "a", windowStart = 1, detectedAt = 2, windowMinutes = 120, bytes = 3, baselineBytes = 4, multiple = 5.0, dismissed = false)))
        assertEquals(-1L, room.insightDao().insertSpike(SpikeEventEntity(packageName = "a", windowStart = 1, detectedAt = 9, windowMinutes = 120, bytes = 9, baselineBytes = 9, multiple = 9.0, dismissed = false)))   // the unique (package, window) index
        room.close()
        context.deleteDatabase(name)
    }
}
