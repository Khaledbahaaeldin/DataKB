package io.github.khaledbahaaeldin.emberbyte.data.export

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanJson
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamingExportServiceTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var usageStore: InMemoryUsageStore
    private lateinit var planStore: InMemoryPlanStore
    private lateinit var budgetStore: InMemoryBudgetStore
    private lateinit var service: StreamingExportService

    @Before
    fun setUp() {
        usageStore = InMemoryUsageStore()
        planStore = InMemoryPlanStore()
        budgetStore = InMemoryBudgetStore()
        service = StreamingExportService(usageStore, planStore, budgetStore, clock)
        runBlocking {
            usageStore.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        }
    }

    private fun hourly(startIso: String, uid: Int, network: NetworkKind, sub: Int, rx: Long, tx: Long) =
        HourlyUsage(Instant.parse(startIso), uid, network, sub, rx, tx)

    @Test
    fun the_csv_has_the_documented_header_and_one_line_per_row_in_range() = runBlocking {
        // rows: 2026-10-14T10:00Z uid 10001 MOBILE sub 3 rx 100 tx 20; 2026-10-14T12:00Z uid 10001 WIFI sub -1 rx 5 tx 6; 2026-10-01T00:00Z (out of range)
        usageStore.upsertHourly(
            listOf(
                hourly("2026-10-14T10:00:00Z", 10001, NetworkKind.MOBILE, 3, 100L, 20L),
                hourly("2026-10-14T12:00:00Z", 10001, NetworkKind.WIFI, -1, 5L, 6L),
                hourly("2026-10-01T00:00:00Z", 10001, NetworkKind.MOBILE, 3, 50L, 10L),
            ),
        )
        val out = ByteArrayOutputStream()
        assertTrue(service.exportCsv(DateRange(Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z")), out) is Outcome.Success<*>)
        assertEquals(
            listOf(
                "hourStart,package,network,subscriptionId,rxBytes,txBytes",
                "2026-10-14T10:00:00Z,com.video,MOBILE,3,100,20",
                "2026-10-14T12:00:00Z,com.video,WIFI,-1,5,6",
            ),
            out.toString("UTF-8").lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun the_range_includes_its_start_and_excludes_its_end() = runBlocking {
        // rows exactly at from and exactly at to: only the first is exported
        val from = Instant.parse("2026-10-14T00:00:00Z")
        val to = Instant.parse("2026-10-15T00:00:00Z")
        usageStore.upsertHourly(
            listOf(
                hourly("2026-10-14T00:00:00Z", 10001, NetworkKind.MOBILE, 3, 100L, 20L),
                hourly("2026-10-15T00:00:00Z", 10001, NetworkKind.MOBILE, 3, 200L, 30L),
            ),
        )
        val out = ByteArrayOutputStream()
        assertTrue(service.exportCsv(DateRange(from, to), out) is Outcome.Success<*>)
        assertEquals(
            listOf(
                "hourStart,package,network,subscriptionId,rxBytes,txBytes",
                "2026-10-14T00:00:00Z,com.video,MOBILE,3,100,20",
            ),
            out.toString("UTF-8").lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun an_unknown_uid_is_written_as_uid_colon_number() = runBlocking {
        // uid 55555 without app_meta -> package column "uid:55555"
        usageStore.upsertHourly(
            listOf(
                hourly("2026-10-14T10:00:00Z", 55555, NetworkKind.MOBILE, 3, 100L, 20L),
            ),
        )
        val out = ByteArrayOutputStream()
        assertTrue(service.exportCsv(DateRange(Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z")), out) is Outcome.Success<*>)
        assertEquals(
            listOf(
                "hourStart,package,network,subscriptionId,rxBytes,txBytes",
                "2026-10-14T10:00:00Z,uid:55555,MOBILE,3,100,20",
            ),
            out.toString("UTF-8").lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun an_empty_database_exports_only_the_header() = runBlocking {
        val out = ByteArrayOutputStream()
        assertTrue(service.exportCsv(DateRange(Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z")), out) is Outcome.Success<*>)
        assertEquals(
            listOf("hourStart,package,network,subscriptionId,rxBytes,txBytes"),
            out.toString("UTF-8").lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun the_json_follows_the_document_of_section_7() = runBlocking {
        // seed: plan "Main" (sub 3, cap 10 GB, MonthlyOnDay(12, 00:00, Africa/Cairo), Capped(2 GB), archived false) with one add-on, one free rule
        // (FRIDAY+SATURDAY, 22:00-06:00, package com.video), alert rules 80 enabled and 100 disabled; one budget; two usage rows
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneId.of("Africa/Cairo"))
        val plan = Plan(
            id = 1L,
            name = "Main",
            subscriptionId = 3,
            capBytes = 10_000_000_000L,
            cycle = cycle,
            rollover = Rollover.Capped(2_000_000_000L),
            archived = false,
        )
        planStore.upsertPlan(plan)
        planStore.upsertAddOn(
            AddOn(
                id = 1L,
                planId = 1L,
                label = "Boost",
                bytes = 1_000_000_000L,
                validFrom = Instant.parse("2026-10-05T00:00:00Z"),
                validUntil = Instant.parse("2026-10-20T00:00:00Z"),
            ),
        )
        planStore.upsertFreeRule(
            FreeRule(
                id = 1L,
                planId = 1L,
                label = "Free Nights",
                days = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                start = LocalTime.of(22, 0),
                end = LocalTime.of(6, 0),
                packageName = "com.video",
            ),
        )
        planStore.upsertAlertRule(AlertRule(1L, 1L, 80, true))
        planStore.upsertAlertRule(AlertRule(2L, 1L, 100, false))

        budgetStore.upsertBudget(
            AppBudget(
                id = 1L,
                packageName = "com.video",
                limitBytes = 500_000_000L,
                period = BudgetPeriod.DAILY,
                planId = null,
                warnAtPercent = 80,
                enabled = true,
            ),
        )

        usageStore.upsertHourly(
            listOf(
                hourly("2026-10-14T10:00:00Z", 10001, NetworkKind.MOBILE, 3, 100L, 20L),
                hourly("2026-10-14T12:00:00Z", 10001, NetworkKind.WIFI, -1, 5L, 6L),
            ),
        )

        val out = ByteArrayOutputStream()
        assertTrue(service.exportJson(out) is Outcome.Success<*>)

        val root = JSONObject(String(out.toByteArray(), Charsets.UTF_8))
        assertEquals(1, root.getInt("contractVersion"))
        assertEquals("2026-10-15T12:00:00Z", root.getString("exportedAt"))
        val planJson = root.getJSONArray("plans").getJSONObject(0)
        assertEquals("Main", planJson.getString("name"))
        assertEquals(3, planJson.getInt("subscriptionId"))
        assertEquals(10_000_000_000L, planJson.getLong("capBytes"))
        assertEquals(JSONObject(PlanJson.cycleToJson(cycle)).toString(), planJson.getJSONObject("cycle").toString())
        assertEquals("capped", planJson.getJSONObject("rollover").getString("type"))
        assertFalse(planJson.getBoolean("archived"))
        val addOn = planJson.getJSONArray("addOns").getJSONObject(0)
        assertEquals("Boost", addOn.getString("label"))
        assertEquals("2026-10-05T00:00:00Z", addOn.getString("validFrom"))
        val rule = planJson.getJSONArray("freeRules").getJSONObject(0)
        assertEquals(setOf("FRIDAY", "SATURDAY"), (0 until rule.getJSONArray("days").length()).map { rule.getJSONArray("days").getString(it) }.toSet())
        assertEquals("22:00", rule.getString("start"))
        assertEquals("06:00", rule.getString("end"))
        assertEquals("com.video", rule.getString("package"))
        assertEquals(listOf(80, 100), (0 until 2).map { planJson.getJSONArray("alertRules").getJSONObject(it).getInt("thresholdPercent") })
        assertFalse(planJson.getJSONArray("alertRules").getJSONObject(1).getBoolean("enabled"))
        val budget = root.getJSONArray("appBudgets").getJSONObject(0)
        assertEquals("com.video", budget.getString("package"))
        assertEquals("DAILY", budget.getString("period"))
        val row = root.getJSONArray("usageHourly").getJSONObject(0)
        assertEquals("2026-10-14T10:00:00Z", row.getString("hourStart"))
        assertEquals("com.video", row.getString("package"))
        assertEquals("MOBILE", row.getString("network"))
        assertEquals(3, row.getInt("subscriptionId"))
        assertEquals(100L, row.getLong("rxBytes"))
    }

    @Test
    fun a_plan_without_a_sim_or_package_writes_json_null() = runBlocking {
        // subscriptionId and the free rule package are JSONObject.NULL
        val cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneId.of("UTC"))
        val plan = Plan(1L, "Unbound", null, 1_000_000L, cycle, Rollover.None, false)
        planStore.upsertPlan(plan)
        planStore.upsertFreeRule(
            FreeRule(
                1L, 1L, "All Apps Rule", setOf(DayOfWeek.SUNDAY),
                LocalTime.of(1, 0), LocalTime.of(2, 0), null,
            ),
        )
        val out = ByteArrayOutputStream()
        assertTrue(service.exportJson(out) is Outcome.Success<*>)
        val root = JSONObject(String(out.toByteArray(), Charsets.UTF_8))
        val planJson = root.getJSONArray("plans").getJSONObject(0)
        assertTrue(planJson.isNull("subscriptionId"))
        assertEquals(JSONObject.NULL, planJson.get("subscriptionId"))
        val rule = planJson.getJSONArray("freeRules").getJSONObject(0)
        assertTrue(rule.isNull("package"))
        assertEquals(JSONObject.NULL, rule.get("package"))
    }

    @Test
    fun the_json_keys_are_in_the_order_the_parser_needs() = runBlocking {
        val out = ByteArrayOutputStream()
        assertTrue(service.exportJson(out) is Outcome.Success<*>)
        val text = out.toString("UTF-8")
        assertTrue(text.indexOf("\"plans\"") < text.indexOf("\"appBudgets\""))
        assertTrue(text.indexOf("\"appBudgets\"") < text.indexOf("\"usageHourly\""))
    }

    @Test
    fun usage_is_read_in_chunks_of_at_most_a_week() = runBlocking {
        val seen = mutableListOf<Duration>()
        val realStore = InMemoryUsageStore()
        val spy = object : UsageStore by realStore {
            override suspend fun hourlyRows(from: Instant, to: Instant) =
                realStore.hourlyRows(from, to).also { seen += Duration.between(from, to) }
        }
        val spiedService = StreamingExportService(spy, planStore, budgetStore, clock)
        // 60 days of rows, one per day; export JSON and CSV over the whole period
        val rows = (0 until 60).map { i ->
            hourly(now.minus(Duration.ofDays(i.toLong())).toString(), 10001, NetworkKind.MOBILE, 3, 10L, 10L)
        }
        realStore.upsertHourly(rows)
        realStore.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))

        // Test CSV
        val outCsv = ByteArrayOutputStream()
        val range = DateRange(now.minus(Duration.ofDays(60)), now.plus(Duration.ofHours(1)))
        assertTrue(spiedService.exportCsv(range, outCsv) is Outcome.Success<*>)
        val countRowsInCsv = outCsv.toString("UTF-8").lines().filter { it.isNotEmpty() }.size - 1
        assertEquals(60, countRowsInCsv)
        assertTrue(seen.all { it <= Duration.ofDays(7) })

        // Test JSON
        seen.clear()
        val outJson = ByteArrayOutputStream()
        assertTrue(spiedService.exportJson(outJson) is Outcome.Success<*>)
        val root = JSONObject(String(outJson.toByteArray(), Charsets.UTF_8))
        val countRowsInJson = root.getJSONArray("usageHourly").length()
        assertEquals(60, countRowsInJson)
        assertTrue(seen.all { it <= Duration.ofDays(7) })
    }

    @Test
    fun a_failing_output_stream_gives_a_storage_failure_not_an_exception() = runBlocking {
        val broken = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("disk full")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("disk full")
            }
        }
        val jsonResult = service.exportJson(broken)
        assertTrue(jsonResult is Outcome.Failure)
        assertTrue((jsonResult as Outcome.Failure).error is EmberbyteError.Storage)

        val csvResult = service.exportCsv(DateRange(now.minus(Duration.ofDays(1)), now), broken)
        assertTrue(csvResult is Outcome.Failure)
        assertTrue((csvResult as Outcome.Failure).error is EmberbyteError.Storage)
    }

    @Test
    fun csv_fields_with_special_characters_are_escaped() = runBlocking {
        usageStore.upsertAppMeta(listOf(AppMeta(999, "com.foo,bar\"baz\nqux", "Odd")))
        usageStore.upsertHourly(listOf(hourly("2026-10-14T10:00:00Z", 999, NetworkKind.MOBILE, 1, 10, 20)))
        val out = ByteArrayOutputStream()
        assertTrue(service.exportCsv(DateRange(Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z")), out) is Outcome.Success<*>)
        val text = out.toString("UTF-8")
        assertTrue(text.contains("\"com.foo,bar\"\"baz\nqux\""))
    }
}
