package io.github.khaledbahaaeldin.emberbyte.data.backup

import io.github.khaledbahaaeldin.emberbyte.data.export.StreamingExportService
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.CONTRACT_VERSION
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupParserTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun round_trip_parse_of_json_from_streaming_export_service() = runBlocking {
        val usageStore = InMemoryUsageStore()
        val planStore = InMemoryPlanStore()
        val budgetStore = InMemoryBudgetStore()
        val exportService = StreamingExportService(usageStore, planStore, budgetStore, clock)

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
        planStore.savePlanWithChildren(plan, listOf(addOn), listOf(freeRule), setOf(80, 100))

        val budget = AppBudget(
            id = 1,
            packageName = "com.video",
            limitBytes = 1_000_000_000L,
            period = BudgetPeriod.DAILY,
            planId = null,
            warnAtPercent = 80,
            enabled = true,
        )
        budgetStore.upsertBudget(budget)

        usageStore.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        val row1 = HourlyUsage(Instant.parse("2026-10-15T08:00:00Z"), 10001, NetworkKind.MOBILE, 3, 100L, 20L)
        val row2 = HourlyUsage(Instant.parse("2026-10-15T10:00:00Z"), 10001, NetworkKind.WIFI, -1, 50L, 10L)
        val row3 = HourlyUsage(Instant.parse("2026-10-15T12:00:00Z"), 77, NetworkKind.MOBILE, 3, 200L, 30L)
        usageStore.upsertHourly(listOf(row1, row2, row3))

        val out = ByteArrayOutputStream()
        exportService.exportJson(out)

        val parsed = BackupParser.parse(ByteArrayInputStream(out.toByteArray()))

        assertEquals(CONTRACT_VERSION, parsed.contractVersion)
        assertEquals(1, parsed.plans.size)
        val expectedPlan = RestoredPlan(
            plan = plan,
            addOns = listOf(addOn),
            freeRules = listOf(freeRule),
            alertRules = planStore.observeAlertRules(plan.id).first(),
        )
        assertEquals(expectedPlan, parsed.plans.first())

        assertEquals(1, parsed.budgets.size)
        assertEquals(budget, parsed.budgets.first())

        val expectedRows = listOf(
            ParsedUsageRow(Instant.parse("2026-10-15T08:00:00Z"), "com.video", NetworkKind.MOBILE, 3, 100L, 20L),
            ParsedUsageRow(Instant.parse("2026-10-15T10:00:00Z"), "com.video", NetworkKind.WIFI, -1, 50L, 10L),
            ParsedUsageRow(Instant.parse("2026-10-15T12:00:00Z"), "uid:77", NetworkKind.MOBILE, 3, 200L, 30L),
        )
        assertEquals(expectedRows, parsed.usage.toList())
    }

    @Test
    fun a_document_without_archived_parses_with_archived_false() {
        val json = """
        {
          "contractVersion": 1,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [
            {
              "id": 1,
              "name": "Default Plan",
              "subscriptionId": null,
              "capBytes": 5000000000,
              "cycle": { "type": "monthlyOnDay", "dayOfMonth": 1, "time": "00:00", "zone": "UTC" },
              "rollover": { "type": "none" },
              "addOns": [],
              "freeRules": [],
              "alertRules": []
            }
          ],
          "appBudgets": [],
          "usageHourly": []
        }
        """.trimIndent()
        val parsed = BackupParser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, parsed.plans.size)
        assertFalse(parsed.plans.first().plan.archived)
    }

    @Test
    fun contract_version_is_returned() {
        val json = """
        {
          "contractVersion": 2,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [],
          "appBudgets": [],
          "usageHourly": []
        }
        """.trimIndent()
        val parsed = BackupParser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, parsed.contractVersion)
    }

    @Test
    fun missing_contract_version_throws_backup_format_exception() {
        val json = """
        {
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [],
          "appBudgets": [],
          "usageHourly": []
        }
        """.trimIndent()
        val e = assertThrows(BackupFormatException::class.java) {
            BackupParser.parse(ByteArrayInputStream(json.toByteArray()))
        }
        assertEquals("file", e.field)
    }

    @Test
    fun truncated_json_or_type_mismatch_throws_backup_format_exception() {
        val truncated = "{\"contractVersion\": 1, \"plans\": ["
        val e1 = assertThrows(BackupFormatException::class.java) {
            BackupParser.parse(ByteArrayInputStream(truncated.toByteArray()))
        }
        assertEquals("file", e1.field)

        val typeMismatch = "{\"contractVersion\": \"one\"}"
        val e2 = assertThrows(BackupFormatException::class.java) {
            BackupParser.parse(ByteArrayInputStream(typeMismatch.toByteArray()))
        }
        assertEquals("file", e2.field)
    }

    @Test
    fun unknown_extra_keys_are_ignored() {
        val json = """
        {
          "contractVersion": 1,
          "exportedAt": "2026-10-15T12:00:00Z",
          "futureField": { "foo": "bar" },
          "plans": [
            {
              "id": 1,
              "name": "Plan",
              "subscriptionId": null,
              "capBytes": 5000000000,
              "cycle": { "type": "monthlyOnDay", "dayOfMonth": 1, "time": "00:00", "zone": "UTC" },
              "rollover": { "type": "none" },
              "archived": false,
              "extraPlanKey": 123,
              "addOns": [],
              "freeRules": [],
              "alertRules": []
            }
          ],
          "appBudgets": [
            {
              "id": 1,
              "package": "com.test",
              "limitBytes": 1000,
              "period": "DAILY",
              "planId": null,
              "warnAtPercent": 80,
              "enabled": true,
              "extraBudgetKey": "hello"
            }
          ],
          "usageHourly": [
            {
              "hourStart": "2026-10-15T10:00:00Z",
              "package": "com.test",
              "network": "MOBILE",
              "subscriptionId": -1,
              "rxBytes": 10,
              "txBytes": 20,
              "extraUsageKey": true
            }
          ]
        }
        """.trimIndent()
        val parsed = BackupParser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, parsed.contractVersion)
        assertEquals(1, parsed.plans.size)
        assertEquals(1, parsed.budgets.size)
        val rows = parsed.usage.toList()
        assertEquals(1, rows.size)
        assertEquals("com.test", rows.first().packageName)
    }

    @Test
    fun usage_rows_are_produced_lazily() {
        val json = """
        {
          "contractVersion": 1,
          "exportedAt": "2026-10-15T12:00:00Z",
          "plans": [],
          "appBudgets": [],
          "usageHourly": [
            { "hourStart": "invalid-instant", "package": "com.test" }
          ]
        }
        """.trimIndent()

        // Parsing alone does not throw even though usage data is damaged
        val parsed = BackupParser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, parsed.contractVersion)

        // Evaluating the lazy sequence throws BackupFormatException with field "file"
        val e = assertThrows(BackupFormatException::class.java) {
            parsed.usage.toList()
        }
        assertEquals("file", e.field)
    }
}
