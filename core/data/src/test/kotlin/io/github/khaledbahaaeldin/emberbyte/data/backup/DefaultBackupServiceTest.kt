package io.github.khaledbahaaeldin.emberbyte.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.budget.RoomBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.db.AlertFiredEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.BudgetStateEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.SpikeEventEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.SpikeNoticeEntity
import io.github.khaledbahaaeldin.emberbyte.data.export.StreamingExportService
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.store.RoomUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultBackupServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val fast = BackupCodec.Params(memoryKiB = 64, iterations = 1, parallelism = 1)
    private lateinit var context: Context
    private lateinit var dbA: EmberbyteDatabase
    private lateinit var dbB: EmberbyteDatabase
    private lateinit var resolver: FakePackageResolver

    private class FakePackageResolver(var packages: Map<String, AppMeta> = emptyMap()) : PackageResolver {
        override fun resolve(packageName: String): AppMeta? = packages[packageName]
    }

    private fun service(db: EmberbyteDatabase, resolver: PackageResolver = this.resolver): DefaultBackupService {
        val planStore = RoomPlanStore(db)
        val budgetStore = RoomBudgetStore(db)
        val usageStore = RoomUsageStore(db)
        val exportService = StreamingExportService(usageStore, planStore, budgetStore, clock)
        val restoreStore = RoomRestoreStore(db)
        return DefaultBackupService(exportService, restoreStore, resolver, fast)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbA = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
        resolver = FakePackageResolver(mapOf("com.video" to AppMeta(20001, "com.video", "Video")))
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    @Test
    fun a_backup_restores_plans_budgets_and_history_on_another_device() = runBlocking {
        val planStoreA = RoomPlanStore(dbA)
        val budgetStoreA = RoomBudgetStore(dbA)
        val usageStoreA = RoomUsageStore(dbA)

        val plan = Plan(
            id = 1,
            name = "Red Unlimited",
            subscriptionId = 3,
            capBytes = 10_000_000_000L,
            cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneId.of("Africa/Cairo")),
            rollover = Rollover.Capped(2_000_000_000L),
            archived = false,
        )
        val addOn = AddOn(
            id = 10,
            planId = 1,
            label = "Extra 5GB",
            bytes = 5_000_000_000L,
            validFrom = Instant.parse("2026-10-01T00:00:00Z"),
            validUntil = Instant.parse("2026-10-31T00:00:00Z"),
        )
        val freeRule = FreeRule(
            id = 20,
            planId = 1,
            label = "Social Weekends",
            days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(23, 59),
            packageName = "com.video",
        )
        planStoreA.savePlanWithChildren(plan, listOf(addOn), listOf(freeRule), setOf(80))

        val budget = AppBudget(
            id = 1,
            packageName = "com.video",
            limitBytes = 1_000_000_000L,
            period = BudgetPeriod.DAILY,
            planId = null,
            warnAtPercent = 80,
            enabled = true,
        )
        budgetStoreA.upsertBudget(budget)

        usageStoreA.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        val row1 = HourlyUsage(Instant.parse("2026-10-15T08:00:00Z"), 10001, NetworkKind.MOBILE, 3, 100L, 20L)
        val row2 = HourlyUsage(Instant.parse("2026-10-15T10:00:00Z"), 10001, NetworkKind.WIFI, -1, 50L, 10L)
        val row3 = HourlyUsage(Instant.parse("2026-10-15T12:00:00Z"), 77, NetworkKind.MOBILE, 3, 200L, 30L)
        usageStoreA.upsertHourly(listOf(row1, row2, row3))

        val file = ByteArrayOutputStream()
        assertTrue(service(dbA).createBackup(file, "correct horse".toCharArray()) is Outcome.Success)

        val summary = (service(dbB).restoreBackup(
            ByteArrayInputStream(file.toByteArray()),
            "correct horse".toCharArray(),
        ) as Outcome.Success<RestoreSummary>).value

        assertEquals(RestoreSummary(plans = 1, budgets = 1, usageRows = 3, skippedUsageRows = 0), summary)

        val planStoreB = RoomPlanStore(dbB)
        val plansB = planStoreB.observePlans().first()
        assertEquals(1, plansB.size)
        assertEquals(plan, plansB.first())
        assertEquals(listOf(addOn), planStoreB.observeAddOns(1).first())
        assertEquals(listOf(freeRule), planStoreB.observeFreeRules(1).first())
        val alertRulesB = planStoreB.observeAlertRules(1).first()
        assertEquals(1, alertRulesB.size)
        assertEquals(80, alertRulesB.first().thresholdPercent)

        val budgetStoreB = RoomBudgetStore(dbB)
        val budgetsB = budgetStoreB.budgets()
        assertEquals(1, budgetsB.size)
        assertEquals(budget, budgetsB.first())

        val usageStoreB = RoomUsageStore(dbB)
        val rowsB = usageStoreB.hourlyRows(Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"))
        assertEquals(3, rowsB.size)
        val videoRows = rowsB.filter { it.uid == 20001 }
        assertEquals(2, videoRows.size)
        val uid77Rows = rowsB.filter { it.uid == 77 }
        assertEquals(1, uid77Rows.size)

        val metaB = usageStoreB.appMeta()
        assertEquals(1, metaB.size)
        assertEquals(20001, metaB.first().uid)
        assertEquals("com.video", metaB.first().packageName)
    }

    @Test
    fun rows_of_packages_not_installed_here_are_skipped_and_counted() = runBlocking {
        val usageStoreA = RoomUsageStore(dbA)
        usageStoreA.upsertAppMeta(
            listOf(
                AppMeta(10001, "com.video", "Video"),
                AppMeta(10002, "com.gone", "Gone App"),
            ),
        )
        usageStoreA.upsertHourly(
            listOf(
                HourlyUsage(Instant.parse("2026-10-15T08:00:00Z"), 10001, NetworkKind.MOBILE, -1, 100L, 20L),
                HourlyUsage(Instant.parse("2026-10-15T10:00:00Z"), 10002, NetworkKind.MOBILE, -1, 50L, 10L),
            ),
        )

        val file = ByteArrayOutputStream()
        assertTrue(service(dbA).createBackup(file, "correct horse".toCharArray()) is Outcome.Success<*>)

        // Resolver on B only resolves com.video, com.gone returns null
        val summary = (service(dbB).restoreBackup(
            ByteArrayInputStream(file.toByteArray()),
            "correct horse".toCharArray(),
        ) as Outcome.Success<RestoreSummary>).value

        assertEquals(1, summary.usageRows)
        assertEquals(1, summary.skippedUsageRows)

        val usageStoreB = RoomUsageStore(dbB)
        val rowsB = usageStoreB.hourlyRows(Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"))
        assertEquals(1, rowsB.size)
        assertEquals(20001, rowsB.first().uid)
    }

    @Test
    fun restore_replaces_what_was_there() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val budgetStoreB = RoomBudgetStore(dbB)
        val usageStoreB = RoomUsageStore(dbB)

        // Seed old state into B
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))
        budgetStoreB.upsertBudget(AppBudget(50, "com.old", 500, BudgetPeriod.DAILY, null, 80, true))
        dbB.insightDao().insertSpike(
            SpikeEventEntity(
                packageName = "com.old",
                windowStart = 1000L,
                detectedAt = 1000L,
                windowMinutes = 120,
                bytes = 1000L,
                baselineBytes = 100L,
                multiple = 10.0,
                dismissed = false,
            ),
        )
        dbB.planDao().putFired(AlertFiredEntity(planId = 99, windowStart = 1000L, percent = 80))
        dbB.insightDao().putState(BudgetStateEntity(budgetId = 50, periodStart = 1000L, state = "WARNING"))
        dbB.insightDao().putNotice(SpikeNoticeEntity(packageName = "com.old", notifiedAt = 1000L))
        usageStoreB.upsertHourly(listOf(HourlyUsage(Instant.parse("2026-10-10T10:00:00Z"), 999, NetworkKind.MOBILE, -1, 100, 100)))

        // A has new data
        val newPlan = Plan(1, "New", null, 2_000_000_000L, Cycle.MonthlyOnDay(5, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        RoomPlanStore(dbA).savePlanWithChildren(newPlan, emptyList(), emptyList(), setOf(80))

        val file = ByteArrayOutputStream()
        assertTrue(service(dbA).createBackup(file, "correct horse".toCharArray()) is Outcome.Success<*>)

        // Restore into B
        val result = service(dbB).restoreBackup(ByteArrayInputStream(file.toByteArray()), "correct horse".toCharArray())
        assertTrue(result is Outcome.Success<*>)

        assertEquals(listOf(newPlan), planStoreB.observePlans().first())
        assertTrue(budgetStoreB.budgets().isEmpty())
        assertTrue(dbB.insightDao().observeAllSpikes().first().isEmpty())
        assertNull(dbB.planDao().firedPercent(99, 1000L))
        assertNull(dbB.insightDao().lastState(50, 1000L))
        assertNull(dbB.insightDao().noticeAt("com.old"))
        assertTrue(usageStoreB.hourlyRows(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")).isEmpty())
    }

    @Test
    fun a_wrong_passphrase_changes_nothing() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))

        val newPlan = Plan(1, "New", null, 2_000_000_000L, Cycle.MonthlyOnDay(5, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        RoomPlanStore(dbA).savePlanWithChildren(newPlan, emptyList(), emptyList(), setOf(80))

        val file = ByteArrayOutputStream()
        service(dbA).createBackup(file, "correct horse".toCharArray())

        val result = service(dbB).restoreBackup(ByteArrayInputStream(file.toByteArray()), "wrong passphrase".toCharArray())
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("passphrase", error.field)

        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    @Test
    fun a_failure_in_the_middle_rolls_everything_back() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))

        val badJson = """
        {
          "contractVersion": 1,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [
            {
              "id": 1,
              "name": "New Plan",
              "subscriptionId": null,
              "capBytes": 5000000000,
              "cycle": { "type": "monthlyOnDay", "dayOfMonth": 1, "time": "00:00", "zone": "UTC" },
              "rollover": { "type": "none" },
              "archived": false,
              "addOns": [],
              "freeRules": [],
              "alertRules": []
            }
          ],
          "appBudgets": [],
          "usageHourly": [
            { "hourStart": "2026-10-15T10:00:00Z", "package": "com.video", "network": "MOBILE", "subscriptionId": 1, "rxBytes": 100, "txBytes": 200 },
            { "hourStart": "broken-instant"
        """.trimIndent()

        val sealed = BackupCodec.seal(gzip(badJson.toByteArray()), "correct horse".toCharArray(), fast)
        val result = service(dbB).restoreBackup(ByteArrayInputStream(sealed), "correct horse".toCharArray())

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)

        // B is unchanged: oldPlan remains, New Plan was rolled back
        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    @Test
    fun a_newer_contract_version_is_refused() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))

        val futureJson = """
        {
          "contractVersion": 2,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [],
          "appBudgets": [],
          "usageHourly": []
        }
        """.trimIndent()

        val sealed = BackupCodec.seal(gzip(futureJson.toByteArray()), "correct horse".toCharArray(), fast)
        val result = service(dbB).restoreBackup(ByteArrayInputStream(sealed), "correct horse".toCharArray())

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)
        assertTrue(error.reason.contains("newer version"))
        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    @Test
    fun an_invalid_plan_in_the_file_is_refused_before_anything_is_written() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))

        val invalidPlanJson = """
        {
          "contractVersion": 1,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [
            {
              "id": 1,
              "name": "Zero Cap Plan",
              "subscriptionId": null,
              "capBytes": 0,
              "cycle": { "type": "monthlyOnDay", "dayOfMonth": 1, "time": "00:00", "zone": "UTC" },
              "rollover": { "type": "none" },
              "archived": false,
              "addOns": [],
              "freeRules": [],
              "alertRules": []
            }
          ],
          "appBudgets": [],
          "usageHourly": []
        }
        """.trimIndent()

        val sealed = BackupCodec.seal(gzip(invalidPlanJson.toByteArray()), "correct horse".toCharArray(), fast)
        val result = service(dbB).restoreBackup(ByteArrayInputStream(sealed), "correct horse".toCharArray())

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)
        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    @Test
    fun a_short_passphrase_cannot_create_a_backup() = runBlocking {
        val result = service(dbA).createBackup(ByteArrayOutputStream(), "short".toCharArray())
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("passphrase", error.field)
    }

    @Test
    fun the_passphrase_arrays_are_zeroed_after_use() = runBlocking {
        val passCreateSuccess = "correct horse".toCharArray()
        service(dbA).createBackup(ByteArrayOutputStream(), passCreateSuccess)
        assertTrue(passCreateSuccess.all { it == '\u0000' })

        val passCreateFailure = "short".toCharArray()
        service(dbA).createBackup(ByteArrayOutputStream(), passCreateFailure)
        assertTrue(passCreateFailure.all { it == '\u0000' })

        val file = ByteArrayOutputStream()
        service(dbA).createBackup(file, "another horse".toCharArray())

        val passRestoreSuccess = "another horse".toCharArray()
        service(dbB).restoreBackup(ByteArrayInputStream(file.toByteArray()), passRestoreSuccess)
        assertTrue(passRestoreSuccess.all { it == '\u0000' })

        val passRestoreWrong = "wrong password".toCharArray()
        service(dbB).restoreBackup(ByteArrayInputStream(file.toByteArray()), passRestoreWrong)
        assertTrue(passRestoreWrong.all { it == '\u0000' })

        val passRestoreCorrupt = "correct horse".toCharArray()
        service(dbB).restoreBackup(ByteArrayInputStream(byteArrayOf(1, 2, 3)), passRestoreCorrupt)
        assertTrue(passRestoreCorrupt.all { it == '\u0000' })
    }

    @Test
    fun garbage_input_is_a_file_problem() = runBlocking {
        val result = service(dbB).restoreBackup(
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
            "correct horse".toCharArray(),
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)
    }

    @Test
    fun the_restored_history_is_visible_through_the_usage_flows() = runBlocking {
        val usageStoreA = RoomUsageStore(dbA)
        usageStoreA.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        val row = HourlyUsage(Instant.parse("2026-10-15T08:00:00Z"), 10001, NetworkKind.MOBILE, 3, 100L, 20L)
        usageStoreA.upsertHourly(listOf(row))

        val file = ByteArrayOutputStream()
        service(dbA).createBackup(file, "correct horse".toCharArray())

        val usageStoreB = RoomUsageStore(dbB)
        assertTrue(service(dbB).restoreBackup(ByteArrayInputStream(file.toByteArray()), "correct horse".toCharArray()) is Outcome.Success<*>)

        val flowRows = usageStoreB.observeHourlyRows(Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z")).first()
        assertEquals(1, flowRows.size)
        assertEquals(20001, flowRows.first().uid)
        assertEquals(100L, flowRows.first().rxBytes)
    }

    @Test
    fun restoring_a_large_backup_with_many_usage_rows_succeeds_without_closing_stream_prematurely() = runBlocking {
        val usageStoreA = RoomUsageStore(dbA)
        usageStoreA.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        val rowCount = 250
        val baseInstant = clock.instant().minusSeconds(3600L)
        val rows = (1..rowCount).map { i ->
            HourlyUsage(
                baseInstant.minusSeconds(i * 3600L),
                10001,
                NetworkKind.MOBILE,
                3,
                100L * i,
                20L * i,
            )
        }
        usageStoreA.upsertHourly(rows)

        val file = ByteArrayOutputStream()
        val createResult = service(dbA).createBackup(file, "correct horse".toCharArray())
        assertTrue(createResult is Outcome.Success)

        val backupBytes = file.toByteArray()
        val usageStoreB = RoomUsageStore(dbB)
        val restoreResult = service(dbB).restoreBackup(ByteArrayInputStream(backupBytes), "correct horse".toCharArray())
        assertTrue("Restore should succeed for large backup, but got: $restoreResult", restoreResult is Outcome.Success)

        val summary = (restoreResult as Outcome.Success).value
        assertEquals(RestoreSummary(plans = 0, budgets = 0, usageRows = rowCount, skippedUsageRows = 0), summary)

        val rowsB = usageStoreB.hourlyRows(baseInstant.minusSeconds((rowCount + 1) * 3600L), clock.instant().plusSeconds(3600L))
        assertEquals(rowCount, rowsB.size)
    }

    @Test
    fun restore_of_a_hostile_header_returns_invalid_file_and_changes_nothing() = runBlocking {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))

        val sealed = BackupCodec.seal(byteArrayOf(1), "correct horse".toCharArray(), fast)
        ByteBuffer.wrap(sealed, 8 + 16, 12).putInt(262_144).putInt(1).putInt(1)

        val result = service(dbB).restoreBackup(ByteArrayInputStream(sealed), "correct horse".toCharArray())
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)

        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    private fun sealJson(json: String): ByteArray {
        val gzipped = gzip(json.toByteArray(Charsets.UTF_8))
        return BackupCodec.seal(gzipped, "correct horse".toCharArray(), fast)
    }

    private suspend fun setupDbBWithOldPlan(): Plan {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = Plan(99, "Old", null, 1_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, false)
        planStoreB.savePlanWithChildren(oldPlan, emptyList(), emptyList(), setOf(50))
        return oldPlan
    }

    private suspend fun assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealed: ByteArray) {
        val planStoreB = RoomPlanStore(dbB)
        val oldPlan = setupDbBWithOldPlan()
        val result = service(dbB).restoreBackup(ByteArrayInputStream(sealed), "correct horse".toCharArray())
        assertTrue("Expected failure but got $result", result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as EmberbyteError.Invalid
        assertEquals("file", error.field)
        assertEquals(listOf(oldPlan), planStoreB.observePlans().first())
    }

    @Test
    fun restore_refuses_budget_limit_above_1e15() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[],"appBudgets":[{"id":1,"package":"com.video","limitBytes":1000000000000001,"period":"DAILY","planId":null,"warnAtPercent":80,"enabled":true}],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_budget_limit_non_positive() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[],"appBudgets":[{"id":1,"package":"com.video","limitBytes":0,"period":"DAILY","planId":null,"warnAtPercent":80,"enabled":true}],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_budget_warn_percent_outside_1_to_99() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[],"appBudgets":[{"id":1,"package":"com.video","limitBytes":1000,"period":"DAILY","planId":null,"warnAtPercent":100,"enabled":true}],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_duplicate_budget_packages() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[],"appBudgets":[{"id":1,"package":"com.video","limitBytes":1000,"period":"DAILY","planId":null,"warnAtPercent":80,"enabled":true},{"id":2,"package":"com.video","limitBytes":2000,"period":"DAILY","planId":null,"warnAtPercent":80,"enabled":true}],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_cycle_budget_with_missing_plan_id() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[],"appBudgets":[{"id":1,"package":"com.video","limitBytes":1000,"period":"CYCLE","planId":42,"warnAtPercent":80,"enabled":true}],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_addon_with_different_plan_id() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[{"id":1,"name":"Main","subscriptionId":null,"capBytes":1000,"cycle":{"type":"monthlyOnDay","dayOfMonth":1,"time":"00:00","zone":"UTC"},"rollover":{"type":"none"},"archived":false,"addOns":[{"id":1,"planId":2,"label":"Boost","bytes":100,"validFrom":"2026-10-01T00:00:00Z","validUntil":"2026-10-20T00:00:00Z"}],"freeRules":[],"alertRules":[]}],"appBudgets":[],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_free_rule_with_different_plan_id() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[{"id":1,"name":"Main","subscriptionId":null,"capBytes":1000,"cycle":{"type":"monthlyOnDay","dayOfMonth":1,"time":"00:00","zone":"UTC"},"rollover":{"type":"none"},"archived":false,"addOns":[],"freeRules":[{"id":1,"planId":2,"label":"Free","days":["MONDAY"],"start":"00:00","end":"06:00","package":null}],"alertRules":[]}],"appBudgets":[],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_alert_threshold_outside_1_to_100() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[{"id":1,"name":"Main","subscriptionId":null,"capBytes":1000,"cycle":{"type":"monthlyOnDay","dayOfMonth":1,"time":"00:00","zone":"UTC"},"rollover":{"type":"none"},"archived":false,"addOns":[],"freeRules":[],"alertRules":[{"id":1,"planId":1,"thresholdPercent":101,"enabled":true}]}],"appBudgets":[],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }

    @Test
    fun restore_refuses_duplicate_plan_ids() = runBlocking {
        val json = """{"contractVersion":1,"exportedAt":"2026-10-15T12:00:00Z","plans":[{"id":1,"name":"Main","subscriptionId":null,"capBytes":1000,"cycle":{"type":"monthlyOnDay","dayOfMonth":1,"time":"00:00","zone":"UTC"},"rollover":{"type":"none"},"archived":false,"addOns":[],"freeRules":[],"alertRules":[]},{"id":1,"name":"Second","subscriptionId":null,"capBytes":1000,"cycle":{"type":"monthlyOnDay","dayOfMonth":1,"time":"00:00","zone":"UTC"},"rollover":{"type":"none"},"archived":false,"addOns":[],"freeRules":[],"alertRules":[]}],"appBudgets":[],"usageHourly":[]}"""
        assertRestoreRefusedAsInvalidFileAndDbBUnchanged(sealJson(json))
    }
}

