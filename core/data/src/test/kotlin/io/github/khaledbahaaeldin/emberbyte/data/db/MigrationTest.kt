package io.github.khaledbahaaeldin.emberbyte.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * The version-1 schema. COPY the `createSql` strings of the five tables from
     * core/data/schemas/.../1.json (replace `${TABLE_NAME}` by the table name) and the `identityHash` of 1.json.
     */
    private val v1Statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `total_minute` (`minuteStart` INTEGER NOT NULL, `network` INTEGER NOT NULL, `subscriptionId` INTEGER NOT NULL, `rxBytes` INTEGER NOT NULL, `txBytes` INTEGER NOT NULL, PRIMARY KEY(`minuteStart`, `network`, `subscriptionId`))",
        "CREATE TABLE IF NOT EXISTS `usage_hourly` (`hourStart` INTEGER NOT NULL, `uid` INTEGER NOT NULL, `network` INTEGER NOT NULL, `subscriptionId` INTEGER NOT NULL, `rxBytes` INTEGER NOT NULL, `txBytes` INTEGER NOT NULL, PRIMARY KEY(`hourStart`, `uid`, `network`, `subscriptionId`))",
        "CREATE TABLE IF NOT EXISTS `app_meta` (`uid` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
        "CREATE TABLE IF NOT EXISTS `coverage_gap` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAt` INTEGER NOT NULL, `toAt` INTEGER NOT NULL, `reason` TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `counter_checkpoint` (`source` TEXT NOT NULL, `at` INTEGER NOT NULL, `rxBytes` INTEGER NOT NULL, `txBytes` INTEGER NOT NULL, `bootId` TEXT NOT NULL, PRIMARY KEY(`source`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7c308347c508fc357ffa36c5ecfc51a3')",
    )

    private fun createVersion1(name: String) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) { v1Statements.forEach(db::execSQL) }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO total_minute (minuteStart, network, subscriptionId, rxBytes, txBytes) VALUES (1000, 0, 3, 11, 22)")
        }
        helper.close()
    }

    @Test fun migrating_from_1_to_2_keeps_the_usage_rows_and_creates_the_plan_tables(): Unit = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        createVersion1(name)
        val room = Room.databaseBuilder(context, EmberbyteDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        assertEquals(1, room.usageDao().minuteRows(0L, Long.MAX_VALUE).size)
        val id = room.planDao().putPlan(PlanEntity(name = "Main", subscriptionId = null, capBytes = 1, cycleJson = "{}", rolloverJson = "{}", archived = false))
        assertEquals(1L, id)
        room.planDao().putFired(AlertFiredEntity(planId = id, windowStart = 5L, percent = 80))
        assertEquals(80, room.planDao().firedPercent(id, 5L))
        room.close()
        context.deleteDatabase(name)
    }

    @Test fun a_fresh_install_creates_version_2_directly() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
        val id = room.planDao().putPlan(PlanEntity(name = "Main", subscriptionId = 3, capBytes = 5, cycleJson = "{}", rolloverJson = "{}", archived = false))
        val back = room.planDao().plan(id)!!
        assertEquals(3, back.subscriptionId)
        assertEquals(5L, back.capBytes)
        room.close()
    }
}
