# Emberbyte M3 "Plans" Implementation Plan

> **For the executing agents (Antigravity):** follow the protocol in `docs/superpowers/plans/2026-10-04-m3-antigravity-prompt.md`
> (an implementer and a fresh tester per task, a ledger, at most 3 fix rounds per task). Steps use checkbox (`- [ ]`) syntax.
> Do the tasks in order, 1 to 21.

**Goal:** Flexible data plans with per-SIM attribution, a run-out forecast with a confidence range, threshold alerts and home-screen
widgets, plus the cheap carry-over minors of M2. After M3 the Plans tab is real, Home shows remaining data and the forecast, and the
notification, widgets and alerts all read the same plan state.

**Architecture:** Pure maths in `:core:engine` (`CycleCalculator`, `PlanCalculator`, `ForecastEngine`), persistence in `:core:data`
(Room database version 2, `PlanStore`, `RoomPlanRepository`, `AlertEvaluator`), screens and widgets in `:app`, components in
`:ui:design`. Everything is derived from the same usage rows as Home (`SeriesBuilder`), so no number can disagree with another surface.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0, Compose BOM 2026.09.00 with Material 3 `1.5.0-alpha29`, Room 2.8.5 (KSP 2.3.12), DataStore,
WorkManager 2.12.0, Robolectric 4.17, plus ONE new dependency family: Jetpack Glance (`androidx.glance:glance-appwidget` and
`androidx.glance:glance-material3`, task 18).

**Read first:** `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` **section 10** (everything in this plan implements it; where this
plan and the contract differ, the contract wins and you stop and report), then backend spec sections 4 to 6 and 9, and frontend spec
sections 6.3 and 7. Section 9.4 of the contract still defines the usage semantics; do not change them.

## Global Constraints

- minSdk 29, compileSdk 37, targetSdk 36, Java/Kotlin 17. No version bumps. The only new dependency is Glance (task 18); no kotlinx.serialization, no Hilt, no new test libraries.
- Module rules: `:core:engine` is pure Kotlin (no Android, no `org.json`); `:ui:design` has no `:core:*` dependency and takes primitives and its own UI models; Room and DataStore types never leave `:core:data`; no `Fake*` class in production wiring (`AppGraph`).
- Package root `io.github.khaledbahaaeldin.emberbyte`; in paths below `$PKG` means `io/github/khaledbahaaeldin/emberbyte`.
- All new Kotlin is test-first. Tests that exist today and encode the OLD behaviour are named in the task that changes them; change exactly those and say so in your report. Never weaken, skip or `@Ignore` any other test.
- All motion uses `MaterialTheme.motionScheme`; all user-visible strings are plain English literals in code like the existing screens (no new string-resource migration), except notification and channel texts which go to `strings.xml`.
- Plan maths uses `java.time` with the zone stored in the plan's `Cycle`; never `ZoneId.systemDefault()` inside plan code.
- Identity check before every commit (`git config user.name` = `Khaledbahaaeldin`, `user.email` = `khaled.bahaaeldin@aiu.edu.eg`, repo-local only); commit messages explain WHY and end with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Work on branch `m3-plans-forecast-widgets` (already created from `main`, which includes M2). Never push, merge, force-push or skip hooks.
- Gate after every task: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`; the last task uses `--no-build-cache`. Lint must stay at 0 errors; the warning count must not grow beyond the 6 `:app` warnings of M2 (record any new one and fix it or say why not).
- Every task that changes the UI must also be proven on the emulator by its tester (see the protocol): clean install, quote evidence, restore device settings.

## File structure (what is created or changed, by responsibility)

| Area | Files |
|---|---|
| `:core:engine` | `engine/model/Plan.kt` (+`AlertRule`), `engine/model/Forecast.kt` (+`PlanPreview`), NEW `engine/plan/{CycleCalculator,PlanCalculator,Slices,FreeWindows}.kt`, NEW `engine/forecast/ForecastEngine.kt` |
| `:core:data` | `db/{Entities,EmberbyteDatabase,Migrations}.kt` + NEW `db/{PlanDao,EmberbyteDatabases}.kt`; NEW `plan/{PlanJson,PlanStore,RoomPlanStore,PlanValidation,PlanStateAssembler,RoomPlanRepository}.kt`; NEW `fake/{InMemoryPlanStore,FakeSimInfoSource}.kt`; NEW `alert/{AlertRepository,RoomAlertRepository,AlertEvaluator}.kt`; NEW `android/AndroidSimInfoSource.kt`; `source/Sources.kt` (+`SimInfoSource`); `PlanRepository.kt` (+`previewState`); DELETE `NoPlanRepository.kt`; `fake/FakePlanRepository.kt`; `sampler/HourlyCatchUp.kt` (28-day backfill); schema `2.json` |
| `:ui:design` | NEW `plan/{BudgetRing,PlanCard}.kt`, `model/UiModels.kt` (+`BudgetStateUi`, `PlanCardUi`) |
| `:app` | NEW `plans/*` (list + editor), `widget/*`, `sampler/{AlertRunner,AlertNotifier}.kt`, `nav/*` (routes, deep links), changes in `AppGraph`, `EmberbyteApp`, `home/*`, `onboarding/*`, `sampler/*`, manifest |

---

### Task 1: Engine models and `CycleCalculator`

**Files:**
- Modify: `core/engine/src/main/kotlin/$PKG/engine/model/Plan.kt` (add `AlertRule`)
- Modify: `core/engine/src/main/kotlin/$PKG/engine/model/Forecast.kt` (add `PlanPreview`)
- Create: `core/engine/src/main/kotlin/$PKG/engine/plan/CycleCalculator.kt`
- Test: `core/engine/src/test/kotlin/$PKG/engine/plan/CycleCalculatorTest.kt`

**Interfaces:**
- Produces: `AlertRule(id, planId, thresholdPercent, enabled)`, `PlanPreview(state: PlanState, forecast: Forecast)`, `CycleCalculator.windowAt(cycle, at): CycleWindow`, `CycleCalculator.previousWindow(cycle, window): CycleWindow` (contract 10.1).

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleCalculatorTest {
    private fun i(text: String) = Instant.parse(text)
    private fun w(start: String, end: String) = CycleWindow(i(start), i(end))
    private val utc = ZoneOffset.UTC

    @Test fun monthly_window_contains_the_instant() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-09-12T00:00:00Z", "2026-10-12T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-04T10:00:00Z")))
    }

    @Test fun the_renewal_instant_itself_starts_the_new_window() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-10-12T00:00:00Z", "2026-11-12T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T00:00:00Z")))
    }

    @Test fun the_time_of_day_of_the_renewal_is_respected() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), utc)
        assertEquals(w("2026-09-12T14:30:00Z", "2026-10-12T14:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T14:29:00Z")))
        assertEquals(w("2026-10-12T14:30:00Z", "2026-11-12T14:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T14:30:00Z")))
    }

    @Test fun day_31_clamps_to_the_last_day_of_short_months() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-01-31T00:00:00Z", "2026-02-28T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-02-15T00:00:00Z")))
        assertEquals(w("2026-02-28T00:00:00Z", "2026-03-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-03-01T00:00:00Z")))
        assertEquals(w("2026-04-30T00:00:00Z", "2026-05-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-05-02T00:00:00Z")))
    }

    @Test fun a_leap_february_has_a_29th() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2028-02-29T00:00:00Z", "2028-03-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2028-03-05T00:00:00Z")))
    }

    @Test fun a_half_hour_zone_shifts_the_window_in_utc() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, kolkata)
        assertEquals(w("2026-09-30T18:30:00Z", "2026-10-31T18:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-09-30T18:30:00Z")))
        assertEquals(w("2026-08-31T18:30:00Z", "2026-09-30T18:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-09-30T18:29:59Z")))
    }

    @Test fun a_dst_change_inside_the_window_is_followed() {
        val berlin = ZoneId.of("Europe/Berlin")      // +02:00 until 2026-10-25, +01:00 afterwards
        val cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, berlin)
        assertEquals(w("2026-09-30T22:00:00Z", "2026-10-31T23:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-15T00:00:00Z")))
    }

    @Test fun every_n_days_steps_from_the_anchor() {
        val cycle = Cycle.EveryNDays(30, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-01-31T00:00:00Z", "2026-03-02T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-31T00:00:00Z")))
        assertEquals(w("2026-01-01T00:00:00Z", "2026-01-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-30T23:59:00Z")))
    }

    @Test fun every_n_days_works_before_the_anchor() {
        val cycle = Cycle.EveryNDays(7, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, utc)
        assertEquals(w("2025-12-25T00:00:00Z", "2026-01-01T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2025-12-30T12:00:00Z")))
    }

    @Test fun every_n_days_respects_a_renewal_time_that_has_not_come_yet() {
        val cycle = Cycle.EveryNDays(7, LocalDate.of(2026, 1, 1), LocalTime.of(6, 0), utc)
        assertEquals(w("2026-01-01T06:00:00Z", "2026-01-08T06:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-08T05:00:00Z")))
        assertEquals(w("2026-01-08T06:00:00Z", "2026-01-15T06:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-08T06:00:00Z")))
    }

    @Test fun the_previous_window_ends_where_the_window_starts() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        val window = CycleCalculator.windowAt(cycle, i("2026-10-04T10:00:00Z"))
        assertEquals(w("2026-08-12T00:00:00Z", "2026-09-12T00:00:00Z"), CycleCalculator.previousWindow(cycle, window))
    }

    @Test fun the_previous_window_of_a_clamped_month_is_correct() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        val window = w("2026-03-31T00:00:00Z", "2026-04-30T00:00:00Z")
        assertEquals(w("2026-02-28T00:00:00Z", "2026-03-31T00:00:00Z"), CycleCalculator.previousWindow(cycle, window))
    }

    @Test fun windows_tile_the_time_line_without_gaps() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.of(3, 15), ZoneId.of("Africa/Cairo"))
        var window = CycleCalculator.windowAt(cycle, i("2026-01-05T00:00:00Z"))
        repeat(24) {
            val next = CycleCalculator.windowAt(cycle, window.end)
            assertEquals(window.end, next.start)
            assertEquals(window, CycleCalculator.previousWindow(cycle, next))
            window = next
        }
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test --tests "*CycleCalculatorTest"` → FAIL (`CycleCalculator` does not exist).

- [ ] **Step 3: Implement.**

In `Plan.kt` append:
```kotlin
data class AlertRule(val id: Long, val planId: Long, val thresholdPercent: Int, val enabled: Boolean)
```
In `Forecast.kt` append:
```kotlin
/** State and forecast of an unsaved plan draft (the plan editor's live preview). */
data class PlanPreview(val state: PlanState, val forecast: Forecast)
```
Create `CycleCalculator.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object CycleCalculator {
    /** The window `[start, end)` that contains [at]. */
    fun windowAt(cycle: Cycle, at: Instant): CycleWindow = when (cycle) {
        is Cycle.MonthlyOnDay -> monthly(cycle, at)
        is Cycle.EveryNDays -> everyNDays(cycle, at)
    }

    fun previousWindow(cycle: Cycle, window: CycleWindow): CycleWindow = windowAt(cycle, window.start.minusMillis(1))

    private fun renewal(cycle: Cycle.MonthlyOnDay, month: YearMonth): Instant =
        month.atDay(minOf(cycle.dayOfMonth, month.lengthOfMonth())).atTime(cycle.time).atZone(cycle.zone).toInstant()

    private fun monthly(cycle: Cycle.MonthlyOnDay, at: Instant): CycleWindow {
        val month = YearMonth.from(at.atZone(cycle.zone))
        val thisRenewal = renewal(cycle, month)
        return if (at >= thisRenewal) {
            CycleWindow(thisRenewal, renewal(cycle, month.plusMonths(1)))
        } else {
            CycleWindow(renewal(cycle, month.minusMonths(1)), thisRenewal)
        }
    }

    private fun everyNDays(cycle: Cycle.EveryNDays, at: Instant): CycleWindow {
        fun start(step: Long): Instant =
            cycle.anchor.plusDays(step * cycle.days).atTime(cycle.time).atZone(cycle.zone).toInstant()
        val localDate = at.atZone(cycle.zone).toLocalDate()
        var step = Math.floorDiv(ChronoUnit.DAYS.between(cycle.anchor, localDate), cycle.days.toLong())
        if (start(step) > at) step -= 1   // right local date, but that day's renewal time has not come yet
        return CycleWindow(start(step), start(step + 1))
    }
}
```

- [ ] **Step 4: Run to verify they pass** — `./gradlew :core:engine:test` → all pass (the existing engine tests too).

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): compute plan cycle windows with month-end clamping and zones

Plans renew on a day of the month or every N days; every later number (used, remaining, forecast) hangs on knowing exactly which window an instant is in."
```

---

### Task 2: `PlanCalculator` (used, remaining, rollover, add-ons, free windows)

**Files:**
- Create: `core/engine/src/main/kotlin/$PKG/engine/plan/{PlanCalculator,Slices,FreeWindows}.kt`
- Test: `core/engine/src/test/kotlin/$PKG/engine/plan/PlanCalculatorTest.kt`

**Interfaces:**
- Consumes: `CycleCalculator` (task 1), `Plan`, `Cycle`, `Rollover`, `AddOn`, `FreeRule`, `PlanState`, `CycleWindow`.
- Produces: `UsageSlice(start, end, packageName, bytes)` and `PlanCalculator.state(plan, at, usage, addOns, freeRules, previousCycleUnusedBytes, isApproximate): PlanState` exactly as contract 10.1.

- [ ] **Step 1: Write the failing tests** (`PlanCalculatorTest.kt`). `2026-10-01` is a Thursday, so `2026-10-09` is a Friday.

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCalculatorTest {
    private val utc = ZoneOffset.UTC
    private val at = Instant.parse("2026-10-15T12:00:00Z")        // window 2026-10-01 .. 2026-11-01 (UTC)
    private val allDays = DayOfWeek.entries.toSet()

    private fun plan(cap: Long = 10_000_000_000L, rollover: Rollover = Rollover.None, zone: ZoneId = utc) =
        Plan(1L, "Main", null, cap, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, zone), rollover)

    private fun slice(start: String, hours: Long, bytes: Long, pkg: String? = null): UsageSlice {
        val s = Instant.parse(start)
        return UsageSlice(s, s.plus(hours, ChronoUnit.HOURS), pkg, bytes)
    }

    private fun rule(id: Long, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime, pkg: String? = null) =
        FreeRule(id, 1L, "rule$id", days, start, end, pkg)

    private fun state(
        plan: Plan = plan(),
        usage: List<UsageSlice> = emptyList(),
        addOns: List<AddOn> = emptyList(),
        rules: List<FreeRule> = emptyList(),
        previousUnused: Long = 0L,
        approximate: Boolean = false,
        now: Instant = at,
    ): PlanState = PlanCalculator.state(plan, now, usage, addOns, rules, previousUnused, approximate)

    @Test fun usage_inside_the_window_is_counted_and_the_rest_remains() {
        val s = state(usage = listOf(slice("2026-10-10T10:00:00Z", 1, 3_000_000_000L)))
        assertEquals(3_000_000_000L, s.usedBytes)
        assertEquals(7_000_000_000L, s.remainingBytes)
        assertEquals(10_000_000_000L, s.effectiveCapBytes)
        assertEquals(0.3f, s.fractionUsed, 1e-6f)
        assertEquals(17, s.daysLeft)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), s.window.start)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), s.window.end)
    }

    @Test fun a_slice_that_straddles_the_window_start_counts_only_its_overlap() {
        val s = state(usage = listOf(slice("2026-09-30T23:00:00Z", 2, 1000)))
        assertEquals(500L, s.usedBytes)
    }

    @Test fun slices_outside_the_window_are_ignored() {
        val s = state(usage = listOf(slice("2026-09-30T20:00:00Z", 1, 1000), slice("2026-11-01T00:00:00Z", 1, 1000)))
        assertEquals(0L, s.usedBytes)
    }

    @Test fun usage_above_the_cap_gives_zero_remaining_and_a_fraction_above_one() {
        val s = state(plan = plan(cap = 1_000_000_000L), usage = listOf(slice("2026-10-10T10:00:00Z", 1, 1_500_000_000L)))
        assertEquals(0L, s.remainingBytes)
        assertEquals(1.5f, s.fractionUsed, 1e-6f)
    }

    @Test fun add_ons_overlapping_the_window_raise_the_cap_and_others_are_ignored() {
        val inside = AddOn(1, 1, "Boost", 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z"))
        val before = AddOn(2, 1, "Old", 1_000_000_000L, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"))
        val s = state(addOns = listOf(inside, before))
        assertEquals(2_000_000_000L, s.addOnBytes)
        assertEquals(12_000_000_000L, s.effectiveCapBytes)
    }

    @Test fun rollover_none_full_and_capped() {
        assertEquals(0L, state(plan = plan(rollover = Rollover.None), previousUnused = 4_000_000_000L).rolledOverBytes)
        val full = state(plan = plan(rollover = Rollover.Full), previousUnused = 4_000_000_000L)
        assertEquals(4_000_000_000L, full.rolledOverBytes)
        assertEquals(14_000_000_000L, full.effectiveCapBytes)
        assertEquals(1_500_000_000L, state(plan = plan(rollover = Rollover.Capped(1_500_000_000L)), previousUnused = 4_000_000_000L).rolledOverBytes)
        assertEquals(4_000_000_000L, state(plan = plan(rollover = Rollover.Capped(9_000_000_000L)), previousUnused = 4_000_000_000L).rolledOverBytes)
    }

    @Test fun an_all_apps_free_rule_excludes_its_traffic() {
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        val s = state(
            usage = listOf(slice("2026-10-10T02:00:00Z", 1, 1_000_000_000L), slice("2026-10-10T07:00:00Z", 1, 500)),
            rules = listOf(night),
        )
        assertEquals(1_000_000_000L, s.freeBytes)
        assertEquals(500L, s.usedBytes)
    }

    @Test fun a_slice_half_inside_a_free_window_is_split_by_time() {
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        val s = state(usage = listOf(slice("2026-10-10T05:30:00Z", 1, 1000)), rules = listOf(night))
        assertEquals(500L, s.freeBytes)
        assertEquals(500L, s.usedBytes)
    }

    @Test fun a_free_window_crossing_midnight_belongs_to_the_day_it_starts() {
        val fridayNight = rule(1, setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0))
        val s = state(
            usage = listOf(
                slice("2026-10-09T23:00:00Z", 1, 100),   // Friday 23:00: free
                slice("2026-10-10T01:00:00Z", 1, 200),   // Saturday 01:00: free (the Friday window)
                slice("2026-10-10T23:00:00Z", 1, 400),   // Saturday 23:00: not free
                slice("2026-10-11T01:00:00Z", 1, 800),   // Sunday 01:00: not free
            ),
            rules = listOf(fridayNight),
        )
        assertEquals(300L, s.freeBytes)
        assertEquals(1200L, s.usedBytes)
    }

    @Test fun an_end_of_midnight_is_valid() {
        val evening = rule(1, allDays, LocalTime.of(22, 0), LocalTime.MIDNIGHT)
        val s = state(usage = listOf(slice("2026-10-10T23:00:00Z", 1, 700)), rules = listOf(evening))
        assertEquals(700L, s.freeBytes)
    }

    @Test fun a_rule_with_equal_start_and_end_is_empty() {
        val none = rule(1, allDays, LocalTime.of(8, 0), LocalTime.of(8, 0))
        val s = state(usage = listOf(slice("2026-10-10T08:00:00Z", 1, 1000)), rules = listOf(none))
        assertEquals(0L, s.freeBytes)
    }

    @Test fun an_app_scoped_free_rule_only_excludes_that_app() {
        val video = rule(1, allDays, LocalTime.of(21, 0), LocalTime.of(23, 0), pkg = "com.video")
        val s = state(
            usage = listOf(
                slice("2026-10-10T21:00:00Z", 1, 1000),
                slice("2026-10-10T21:00:00Z", 1, 700, "com.video"),
                slice("2026-10-10T21:00:00Z", 1, 300, "com.other"),
            ),
            rules = listOf(video),
        )
        assertEquals(700L, s.freeBytes)
        assertEquals(300L, s.usedBytes)
    }

    @Test fun overlapping_all_apps_and_app_rules_are_not_double_counted() {
        val everyone = rule(1, allDays, LocalTime.of(21, 30), LocalTime.of(22, 0))
        val video = rule(2, allDays, LocalTime.of(21, 0), LocalTime.of(23, 0), pkg = "com.video")
        val s = state(
            usage = listOf(slice("2026-10-10T21:00:00Z", 1, 1000), slice("2026-10-10T21:00:00Z", 1, 700, "com.video")),
            rules = listOf(everyone, video),
        )
        assertEquals(850L, s.freeBytes)       // 500 by the all-apps rule + 350 by the app rule outside it
        assertEquals(150L, s.usedBytes)
    }

    @Test fun per_app_slices_are_never_added_to_the_total() {
        val s = state(usage = listOf(slice("2026-10-10T21:00:00Z", 1, 1000), slice("2026-10-10T21:00:00Z", 1, 700, "com.video")))
        assertEquals(1000L, s.usedBytes)
    }

    @Test fun a_plan_in_another_zone_evaluates_free_windows_on_its_own_clock() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        // 2026-10-10T19:00Z..20:00Z is 2026-10-11 00:30..01:30 in Kolkata: inside the free night
        val s = state(plan = plan(zone = kolkata), usage = listOf(slice("2026-10-10T19:00:00Z", 1, 1000)), rules = listOf(night))
        assertEquals(1000L, s.freeBytes)
        assertEquals(0L, s.usedBytes)
    }

    @Test fun days_left_rounds_up_and_never_goes_negative() {
        assertEquals(1, state(now = Instant.parse("2026-10-31T00:00:00Z")).daysLeft)
        assertEquals(2, state(now = Instant.parse("2026-10-30T23:59:59Z")).daysLeft)
        assertEquals(1, state(now = Instant.parse("2026-10-31T23:59:59Z")).daysLeft)
    }

    @Test fun the_approximate_flag_is_passed_through() {
        assertTrue(state(approximate = true).isApproximate)
        assertFalse(state(approximate = false).isApproximate)
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test --tests "*PlanCalculatorTest"` → FAIL.

- [ ] **Step 3: Implement.**

`Slices.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/** packageName == null: total mobile traffic; otherwise a per-app breakdown used only for package-scoped free rules (contract 10.1). */
data class UsageSlice(val start: Instant, val end: Instant, val packageName: String?, val bytes: Long)

internal object Slices {
    fun overlapMillis(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Long {
        val start = maxOf(aStart, bStart)
        val end = minOf(aEnd, bEnd)
        return if (end > start) Duration.between(start, end).toMillis() else 0L
    }

    /** Bytes of [slice] inside `[from, to)`, clipped proportionally to the time overlap. */
    fun clippedBytes(slice: UsageSlice, from: Instant, to: Instant): Long {
        val length = Duration.between(slice.start, slice.end).toMillis()
        if (length <= 0L || slice.bytes <= 0L) return 0L
        val overlap = overlapMillis(slice.start, slice.end, from, to)
        if (overlap <= 0L) return 0L
        if (overlap >= length) return slice.bytes
        return (slice.bytes.toDouble() * overlap / length).roundToLong()
    }
}
```
`FreeWindows.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

internal data class Interval(val start: Instant, val end: Instant)

internal object FreeWindows {
    /** Bytes excluded from the plan by [rules] (contract 10.1: all-apps intervals, then app intervals minus the all-apps ones). */
    fun freeBytes(totals: List<UsageSlice>, perApp: List<UsageSlice>, rules: List<FreeRule>, window: CycleWindow, zone: ZoneId): Long {
        if (rules.isEmpty()) return 0L
        val everyone = merge(intervals(rules.filter { it.packageName == null }, window, zone))
        var free = 0L
        for (slice in totals) free += share(slice, window, everyone)
        val byPackage = rules.filter { it.packageName != null }.groupBy { it.packageName!! }
        for ((pkg, pkgRules) in byPackage) {
            val own = subtract(merge(intervals(pkgRules, window, zone)), everyone)
            for (slice in perApp) if (slice.packageName == pkg) free += share(slice, window, own)
        }
        return free
    }

    private fun share(slice: UsageSlice, window: CycleWindow, intervals: List<Interval>): Long {
        val spanStart = maxOf(slice.start, window.start)
        val spanEnd = minOf(slice.end, window.end)
        if (spanEnd <= spanStart || intervals.isEmpty()) return 0L
        val bytes = Slices.clippedBytes(slice, window.start, window.end)
        val spanMillis = Duration.between(spanStart, spanEnd).toMillis()
        var freeMillis = 0L
        for (interval in intervals) freeMillis += Slices.overlapMillis(interval.start, interval.end, spanStart, spanEnd)
        if (freeMillis >= spanMillis) return bytes
        return (bytes.toDouble() * freeMillis / spanMillis).roundToLong()
    }

    private fun intervals(rules: List<FreeRule>, window: CycleWindow, zone: ZoneId): List<Interval> {
        val firstDate = window.start.atZone(zone).toLocalDate().minusDays(1)
        val lastDate = window.end.atZone(zone).toLocalDate()
        val out = ArrayList<Interval>()
        for (rule in rules) {
            if (rule.start == rule.end || rule.days.isEmpty()) continue
            var date = firstDate
            while (!date.isAfter(lastDate)) {
                if (date.dayOfWeek in rule.days) {
                    val start = date.atTime(rule.start).atZone(zone).toInstant()
                    val endDate = if (rule.end > rule.start) date else date.plusDays(1)   // end <= start: crosses midnight
                    out += Interval(start, endDate.atTime(rule.end).atZone(zone).toInstant())
                }
                date = date.plusDays(1)
            }
        }
        return out
    }

    fun merge(list: List<Interval>): List<Interval> {
        if (list.isEmpty()) return list
        val sorted = list.sortedBy { it.start }
        val out = ArrayList<Interval>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (next.start <= current.end) current = Interval(current.start, maxOf(current.end, next.end))
            else { out += current; current = next }
        }
        out += current
        return out
    }

    fun subtract(from: List<Interval>, minus: List<Interval>): List<Interval> {
        var result = from
        for (cut in minus) {
            val next = ArrayList<Interval>()
            for (interval in result) {
                if (cut.end <= interval.start || cut.start >= interval.end) { next += interval; continue }
                if (cut.start > interval.start) next += Interval(interval.start, cut.start)
                if (cut.end < interval.end) next += Interval(cut.end, interval.end)
            }
            result = next
        }
        return result
    }
}
```
`PlanCalculator.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object PlanCalculator {
    fun state(
        plan: Plan,
        at: Instant,
        usage: List<UsageSlice>,
        addOns: List<AddOn>,
        freeRules: List<FreeRule>,
        previousCycleUnusedBytes: Long,
        isApproximate: Boolean,
    ): PlanState {
        val window = CycleCalculator.windowAt(plan.cycle, at)
        val totals = usage.filter { it.packageName == null }
        val perApp = usage.filter { it.packageName != null }
        val totalBytes = totals.sumOf { Slices.clippedBytes(it, window.start, window.end) }
        val freeBytes = FreeWindows.freeBytes(totals, perApp, freeRules, window, zoneOf(plan.cycle))
        val used = maxOf(0L, totalBytes - freeBytes)
        val rolledOver = when (val rollover = plan.rollover) {
            Rollover.None -> 0L
            Rollover.Full -> previousCycleUnusedBytes
            is Rollover.Capped -> minOf(previousCycleUnusedBytes, rollover.maxBytes)
        }.coerceAtLeast(0L)
        val addOnBytes = addOns.filter { it.validFrom < window.end && it.validUntil > window.start }.sumOf { it.bytes }
        val effective = plan.capBytes + rolledOver + addOnBytes
        val secondsLeft = Duration.between(at, window.end).seconds
        return PlanState(
            plan = plan,
            window = window,
            effectiveCapBytes = effective,
            usedBytes = used,
            remainingBytes = maxOf(0L, effective - used),
            rolledOverBytes = rolledOver,
            addOnBytes = addOnBytes,
            freeBytes = freeBytes,
            daysLeft = if (secondsLeft <= 0L) 0 else ((secondsLeft + 86_399L) / 86_400L).toInt(),   // Math.ceilDiv needs Java 18
            fractionUsed = if (effective > 0L) used.toFloat() / effective else 0f,
            isApproximate = isApproximate,
        )
    }

    fun zoneOf(cycle: Cycle): ZoneId = when (cycle) {
        is Cycle.MonthlyOnDay -> cycle.zone
        is Cycle.EveryNDays -> cycle.zone
    }
}
```
Because `UsageSlice` is now defined in `Slices.kt`, do NOT define it twice.

- [ ] **Step 4: Run to verify they pass** — `./gradlew :core:engine:test` → all pass.

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): compute plan state with rollover, add-on packs and free windows

Free windows can cross midnight and be scoped to one app; excluding the overlap of the two kinds only once keeps the used figure honest."
```

---

### Task 3: `ForecastEngine`

**Files:**
- Create: `core/engine/src/main/kotlin/$PKG/engine/forecast/ForecastEngine.kt`
- Test: `core/engine/src/test/kotlin/$PKG/engine/forecast/ForecastEngineTest.kt`

**Interfaces:**
- Consumes: `PlanState`, `Plan`, `Cycle`, `CycleWindow`, `DayUsage`, `Forecast`, `Confidence`.
- Produces: `ForecastEngine.forecast(state, dailyHistory, now, zone, extraBytesPerDay = 0L, extraFrom = now, hourCurve = DEFAULT_HOUR_CURVE): Forecast` and `ForecastEngine.DEFAULT_HOUR_CURVE` (contract 10.1).

- [ ] **Step 1: Write the failing tests.** `2026-10-10` is a Saturday. The tests build a `PlanState` by hand.

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {
    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-10T00:00:00Z")
    private val uniform = DoubleArray(24) { 1.0 / 24 }

    private fun stateOf(remaining: Long, used: Long = 3_000_000_000L, windowEnd: String = "2026-11-01T00:00:00Z") = PlanState(
        plan = Plan(7L, "Main", null, used + remaining, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, utc), Rollover.None),
        window = CycleWindow(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse(windowEnd)),
        effectiveCapBytes = used + remaining,
        usedBytes = used,
        remainingBytes = remaining,
        rolledOverBytes = 0, addOnBytes = 0, freeBytes = 0, daysLeft = 22, fractionUsed = 0.3f, isApproximate = false,
    )

    /** [days] consecutive days ending the day before 2026-10-10, each [bytes], or [pattern] repeated. */
    private fun history(days: Int, bytes: Long = 1_000_000_000L, pattern: List<Long>? = null): List<DayUsage> {
        val end = LocalDate.of(2026, 10, 9)
        return (0 until days).map { index ->
            val date = end.minusDays((days - 1 - index).toLong())
            DayUsage(date, pattern?.get(index % pattern.size) ?: bytes, 0L)
        }
    }

    @Test fun a_steady_history_gives_one_precise_run_out_instant() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), now, utc, hourCurve = uniform)
        val expected = Instant.parse("2026-10-15T00:00:00Z")
        assertEquals(expected, f.runOutExpected)
        assertEquals(expected, f.runOutEarliest)
        assertEquals(expected, f.runOutLatest)
        assertEquals(Confidence.HIGH, f.confidence)
        assertEquals(28, f.historyDays)
        assertEquals(7L, f.planId)
    }

    @Test fun a_plan_that_lasts_gives_three_nulls_and_the_projected_total() {
        val f = ForecastEngine.forecast(stateOf(100_000_000_000L), history(28), now, utc, hourCurve = uniform)
        assertNull(f.runOutEarliest); assertNull(f.runOutExpected); assertNull(f.runOutLatest)
        assertEquals(3_000_000_000L + 22_000_000_000L, f.projectedCycleEndBytes)   // 22 days left from 10 Oct to 1 Nov
    }

    @Test fun the_last_day_is_cut_at_the_end_of_the_window() {
        val f = ForecastEngine.forecast(stateOf(100_000_000_000L, windowEnd = "2026-10-12T12:00:00Z"), history(28), now, utc, hourCurve = uniform)
        assertEquals(3_000_000_000L + 2_500_000_000L, f.projectedCycleEndBytes)
    }

    @Test fun usage_already_exhausted_means_now() {
        val f = ForecastEngine.forecast(stateOf(0L), history(28), now, utc, hourCurve = uniform)
        assertEquals(now, f.runOutEarliest); assertEquals(now, f.runOutExpected); assertEquals(now, f.runOutLatest)
    }

    @Test fun only_the_rest_of_today_counts_according_to_the_hour_curve() {
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), noon, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), f.runOutExpected)   // 0.5 + 4 days + 0.5 day
    }

    @Test fun a_variable_history_widens_the_range() {
        val f = ForecastEngine.forecast(stateOf(15_000_000_000L), history(28, pattern = listOf(0L, 4_000_000_000L)), now, utc, hourCurve = uniform)
        assertNotNull(f.runOutEarliest); assertNotNull(f.runOutExpected)
        assertTrue(f.runOutEarliest!! < f.runOutExpected!!)
        assertTrue(f.runOutLatest == null || f.runOutLatest!! > f.runOutExpected!!)
        assertEquals(Confidence.MEDIUM, f.confidence)      // 28 days but a coefficient of variation of about 1
    }

    @Test fun a_weekday_without_samples_borrows_the_overall_mean() {
        // two days of history on two different weekdays, every other weekday has no sample at all
        val sparse = listOf(DayUsage(LocalDate.of(2026, 10, 8), 1_000_000_000L, 0), DayUsage(LocalDate.of(2026, 10, 9), 1_000_000_000L, 0))
        val f = ForecastEngine.forecast(stateOf(3_000_000_000L), sparse, now, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-13T00:00:00Z"), f.runOutExpected)
        assertEquals(Confidence.LOW, f.confidence)
    }

    @Test fun no_history_at_all_never_runs_out_and_is_low_confidence() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), emptyList(), now, utc, hourCurve = uniform)
        assertNull(f.runOutExpected)
        assertEquals(Confidence.LOW, f.confidence)
        assertEquals(0, f.historyDays)
        assertEquals(3_000_000_000L, f.projectedCycleEndBytes)
    }

    @Test fun confidence_depends_on_the_number_of_days() {
        fun conf(days: Int) = ForecastEngine.forecast(stateOf(5_000_000_000L), history(days), now, utc).confidence
        assertEquals(Confidence.LOW, conf(6))
        assertEquals(Confidence.MEDIUM, conf(7))
        assertEquals(Confidence.MEDIUM, conf(20))
        assertEquals(Confidence.HIGH, conf(21))
    }

    @Test fun today_and_the_future_in_the_history_are_ignored_and_only_28_days_are_used() {
        val noise = history(28) + DayUsage(LocalDate.of(2026, 10, 10), 99_000_000_000L, 0) + DayUsage(LocalDate.of(2026, 10, 11), 99_000_000_000L, 0)
        val old = (1..5).map { DayUsage(LocalDate.of(2026, 10, 9).minusDays(28L + it), 90_000_000_000L, 0) }
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), old + noise, now, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), f.runOutExpected)
        assertEquals(28, f.historyDays)
    }

    @Test fun what_if_extra_daily_usage_brings_the_run_out_forward() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), now, utc, extraBytesPerDay = 1_000_000_000L, extraFrom = now, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-12T12:00:00Z"), f.runOutExpected)
    }

    @Test fun extra_usage_starts_on_the_chosen_day() {
        val f = ForecastEngine.forecast(
            stateOf(5_000_000_000L), history(28), now, utc,
            extraBytesPerDay = 1_000_000_000L, extraFrom = Instant.parse("2026-10-12T00:00:00Z"), hourCurve = uniform,
        )
        // 10 Oct 1, 11 Oct 1, then 2 per day from 12 Oct: after 12 Oct 4, 13 Oct reaches 5 after half of that day
        assertEquals(Instant.parse("2026-10-13T12:00:00Z"), f.runOutExpected)
    }

    @Test fun the_default_hour_curve_is_a_proper_distribution() {
        assertEquals(24, ForecastEngine.DEFAULT_HOUR_CURVE.size)
        assertEquals(1.0, ForecastEngine.DEFAULT_HOUR_CURVE.sum(), 1e-9)
    }
}
```
Check the last scenario by hand before trusting it: daily 1 GB; from 12 Oct extra +1 GB. Cumulative: 10 Oct 1, 11 Oct 2, 12 Oct 4 (2 GB that day), 13 Oct segment starts with 4 and has 2 GB: reaches 5 after half → `2026-10-13T12:00Z`. ✓.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test --tests "*ForecastEngineTest"` → FAIL.

- [ ] **Step 3: Implement** `ForecastEngine.kt`:

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

object ForecastEngine {
    /** Share of a day's mobile usage per LOCAL hour (a typical smartphone day; normalised before use). */
    val DEFAULT_HOUR_CURVE: DoubleArray = doubleArrayOf(
        0.010, 0.006, 0.004, 0.003, 0.003, 0.004, 0.010, 0.030, 0.055, 0.060, 0.060, 0.060,
        0.065, 0.060, 0.055, 0.055, 0.060, 0.065, 0.070, 0.075, 0.075, 0.065, 0.035, 0.015,
    )

    private const val MAX_HISTORY_DAYS = 28
    private const val FULL_WEEKDAY_SAMPLES = 3

    private data class Stat(val mean: Double, val std: Double)
    private class Projection(val runOut: Instant?, val bytes: Double)

    fun forecast(
        state: PlanState,
        dailyHistory: List<DayUsage>,
        now: Instant,
        zone: ZoneId,
        extraBytesPerDay: Long = 0L,
        extraFrom: Instant = now,
        hourCurve: DoubleArray = DEFAULT_HOUR_CURVE,
    ): Forecast {
        require(hourCurve.size == 24 && hourCurve.sum() > 0.0) { "hourCurve needs 24 positive weights" }
        val today = now.atZone(zone).toLocalDate()
        val history = dailyHistory.filter { it.date < today }.sortedBy { it.date }.takeLast(MAX_HISTORY_DAYS)
        val overall = stat(history.map { it.mobileBytes.toDouble() })
        val profile = DayOfWeek.entries.associateWith { weekday ->
            blended(history.filter { it.date.dayOfWeek == weekday }.map { it.mobileBytes.toDouble() }, overall)
        }
        val cv = if (overall.mean > 0.0) overall.std / overall.mean else 0.0
        val confidence = when {
            history.size < 7 -> Confidence.LOW
            history.size >= 21 && cv < 0.6 -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }

        fun run(k: Double) = project(state, profile, now, zone, k, extraBytesPerDay, extraFrom, hourCurve)
        val high = run(+1.0)
        val mid = run(0.0)
        val low = run(-1.0)
        val exhausted = state.remainingBytes <= 0L
        return Forecast(
            planId = state.plan.id,
            runOutEarliest = if (exhausted) now else high.runOut,
            runOutExpected = if (exhausted) now else mid.runOut,
            runOutLatest = if (exhausted) now else low.runOut,
            projectedCycleEndBytes = state.usedBytes + mid.bytes.roundToLong(),
            confidence = confidence,
            historyDays = history.size,
        )
    }

    private fun project(
        state: PlanState, profile: Map<DayOfWeek, Stat>, now: Instant, zone: ZoneId, k: Double,
        extraBytesPerDay: Long, extraFrom: Instant, curve: DoubleArray,
    ): Projection {
        val end = state.window.end
        val remaining = state.remainingBytes.toDouble()
        val extraDate = extraFrom.atZone(zone).toLocalDate()
        var cumulative = 0.0
        var runOut: Instant? = null
        var date = now.atZone(zone).toLocalDate()
        var segmentStart = now
        var first = true
        while (segmentStart < end) {
            val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
            val segmentEnd = minOf(nextDayStart, end)
            val stat = profile.getValue(date.dayOfWeek)
            var dayBytes = max(0.0, stat.mean + k * stat.std)
            if (extraBytesPerDay > 0L && !date.isBefore(extraDate)) dayBytes += extraBytesPerDay
            val ahead = if (first) 1.0 - cumulativeShare(curve, now.atZone(zone).toLocalTime()) else 1.0
            val inWindow = millis(segmentStart, segmentEnd).toDouble() / millis(segmentStart, nextDayStart)
            val segmentBytes = dayBytes * ahead * inWindow
            if (runOut == null && segmentBytes > 0.0 && cumulative + segmentBytes >= remaining) {
                val fraction = (remaining - cumulative) / segmentBytes
                runOut = segmentStart.plusMillis((millis(segmentStart, segmentEnd) * fraction).roundToLong())
            }
            cumulative += segmentBytes
            segmentStart = segmentEnd
            date = date.plusDays(1)
            first = false
        }
        return Projection(runOut, cumulative)
    }

    private fun millis(a: Instant, b: Instant): Long = Duration.between(a, b).toMillis()

    private fun cumulativeShare(curve: DoubleArray, time: LocalTime): Double {
        val total = curve.sum()
        var done = 0.0
        for (hour in 0 until time.hour) done += curve[hour]
        done += curve[time.hour] * ((time.minute + time.second / 60.0) / 60.0)
        return done / total
    }

    private fun stat(values: List<Double>): Stat {
        if (values.isEmpty()) return Stat(0.0, 0.0)
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return Stat(mean, sqrt(variance))
    }

    /** A weekday with fewer than 3 samples is pulled toward the overall figure (no samples: the overall figure). */
    private fun blended(values: List<Double>, overall: Stat): Stat {
        if (values.size >= FULL_WEEKDAY_SAMPLES) return stat(values)
        val own = stat(values)
        val weight = values.size / FULL_WEEKDAY_SAMPLES.toDouble()
        return Stat(weight * own.mean + (1 - weight) * overall.mean, weight * own.std + (1 - weight) * overall.std)
    }
}
```
(The default curve above sums to exactly 1.000; `cumulativeShare` normalises anyway.)

- [ ] **Step 4: Run to verify they pass** — `./gradlew :core:engine:test` and the full gate → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): forecast the run-out as a range from a weekday profile

A single falsely precise date would mislead; earliest, expected and latest come from mean +1 sigma, mean and mean -1 sigma of the user's own weekdays."
```


### Task 4: Room database version 2, one shared database instance, `Migration(1, 2)`

**Files:**
- Modify: `core/data/src/main/kotlin/$PKG/data/db/Entities.kt`, `db/EmberbyteDatabase.kt`, `store/UsageStore.kt` (`UsageStores.create`)
- Create: `core/data/src/main/kotlin/$PKG/data/db/{PlanDao,Migrations,EmberbyteDatabases}.kt`
- Create (generated, commit it): `core/data/schemas/io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase/2.json`
- Test: `core/data/src/test/kotlin/$PKG/data/db/MigrationTest.kt`

**Interfaces:**
- Produces: entities `PlanEntity`, `AddOnEntity`, `FreeRuleEntity`, `AlertRuleEntity`, `AlertFiredEntity`; `PlanDao`; `MIGRATION_1_2`; `EmberbyteDatabases.get(context): EmberbyteDatabase` (process-wide singleton used by BOTH `UsageStores` and `PlanStores`, because two Room instances on one file are not allowed).

- [ ] **Step 1: Write the failing test** `MigrationTest.kt`. It builds a real version-1 file by hand (the statements are copied from the committed `1.json`), then opens it with Room version 2 and the migration, so Room itself verifies that the migration produced exactly the expected schema.

```kotlin
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
        // "CREATE TABLE IF NOT EXISTS `total_minute` (...)", ... for total_minute, usage_hourly, app_meta, coverage_gap, counter_checkpoint
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        // "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '<identityHash of 1.json>')",
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

    @Test fun migrating_from_1_to_2_keeps_the_usage_rows_and_creates_the_plan_tables() = runBlocking {
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
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*MigrationTest"` → FAIL (`PlanEntity` does not exist).

- [ ] **Step 3: Implement.**

Append to `Entities.kt`:
```kotlin
@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subscriptionId: Int?,
    val capBytes: Long,
    val cycleJson: String,
    val rolloverJson: String,
    val archived: Boolean,
)

@Entity(tableName = "add_on", indices = [Index("planId")])
data class AddOnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val bytes: Long,
    val validFrom: Long,
    val validUntil: Long,
)

@Entity(tableName = "free_rule", indices = [Index("planId")])
data class FreeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val daysMask: Int,       // bit 0 = Monday ... bit 6 = Sunday
    val startMinute: Int,    // minutes after midnight
    val endMinute: Int,
    val packageName: String?,
)

@Entity(tableName = "alert_rule", indices = [Index("planId")])
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val thresholdPercent: Int,
    val enabled: Boolean,
)

@Entity(tableName = "alert_fired", primaryKeys = ["planId", "windowStart"])
data class AlertFiredEntity(val planId: Long, val windowStart: Long, val percent: Int)
```
(import `androidx.room.Index`.)

`PlanDao.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plan ORDER BY id") abstract fun observePlans(): Flow<List<PlanEntity>>
    @Query("SELECT * FROM plan WHERE id = :id") abstract fun observePlan(id: Long): Flow<PlanEntity?>
    @Query("SELECT * FROM plan WHERE id = :id") abstract suspend fun plan(id: Long): PlanEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putPlan(entity: PlanEntity): Long
    @Query("UPDATE plan SET archived = :archived WHERE id = :id") abstract suspend fun setArchived(id: Long, archived: Boolean): Int

    @Query("SELECT * FROM add_on WHERE planId = :planId ORDER BY validFrom, id") abstract fun observeAddOns(planId: Long): Flow<List<AddOnEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putAddOn(entity: AddOnEntity): Long
    @Query("DELETE FROM add_on WHERE id = :id") abstract suspend fun deleteAddOn(id: Long)

    @Query("SELECT * FROM free_rule WHERE planId = :planId ORDER BY id") abstract fun observeFreeRules(planId: Long): Flow<List<FreeRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putFreeRule(entity: FreeRuleEntity): Long
    @Query("DELETE FROM free_rule WHERE id = :id") abstract suspend fun deleteFreeRule(id: Long)

    @Query("SELECT * FROM alert_rule WHERE planId = :planId ORDER BY thresholdPercent") abstract fun observeAlertRules(planId: Long): Flow<List<AlertRuleEntity>>
    @Query("SELECT * FROM alert_rule WHERE planId = :planId ORDER BY thresholdPercent") abstract suspend fun alertRules(planId: Long): List<AlertRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putAlertRule(entity: AlertRuleEntity): Long
    @Query("DELETE FROM alert_rule WHERE id = :id") abstract suspend fun deleteAlertRule(id: Long)

    @Query("SELECT percent FROM alert_fired WHERE planId = :planId AND windowStart = :windowStart") abstract suspend fun firedPercent(planId: Long, windowStart: Long): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putFired(entity: AlertFiredEntity)
}
```
`EmberbyteDatabase.kt`: add the five entities to `entities = [...]`, set `version = 2`, add `abstract fun planDao(): PlanDao`.

Build once (`./gradlew :core:data:kspDebugKotlin`) so Room writes `schemas/.../2.json`; commit it. Then write `Migrations.kt` by copying the `createSql` of the five NEW tables (and the `CREATE INDEX` statements of their `indices`) from `2.json`, replacing `${TABLE_NAME}`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Paste, in this order, from schemas/.../2.json (createSql with the table name substituted):
        // plan, add_on (+ its index on planId), free_rule (+ index), alert_rule (+ index), alert_fired
        db.execSQL("CREATE TABLE IF NOT EXISTS `plan` (...)")
        // ... one db.execSQL(...) per statement
    }
}
```
(The statements must be exactly Room's, otherwise Room refuses to open the database; the test below proves it.) Also finish the `v1Statements` list in the test with the 1.json statements.

`EmberbyteDatabases.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import android.content.Context
import androidx.room.Room

/** The one Room instance of the process (two instances on one file are not allowed). */
object EmberbyteDatabases {
    @Volatile private var instance: EmberbyteDatabase? = null

    fun get(context: Context): EmberbyteDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, EmberbyteDatabase::class.java, "emberbyte.db")
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { instance = it }
    }
}
```
In `UsageStore.kt` change `UsageStores.create` to `RoomUsageStore(EmberbyteDatabases.get(context))` and drop the unused `Room` import.

- [ ] **Step 4: Run to verify it passes** — the two new tests pass, `git diff --stat core/data/schemas` shows a NEW `2.json` and an UNCHANGED `1.json`, and the full gate is green.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): add the plan, add-on, free-rule and alert tables as database version 2

One shared Room instance serves usage and plans, and a tested migration keeps every existing usage row."
```

---

### Task 5: `PlanJson` and the `PlanStore` (in-memory and Room)

**Files:**
- Create: `core/data/src/main/kotlin/$PKG/data/plan/{PlanJson,PlanStore,RoomPlanStore}.kt`
- Create: `core/data/src/main/kotlin/$PKG/data/fake/InMemoryPlanStore.kt`
- Test: `core/data/src/test/kotlin/$PKG/data/plan/{PlanJsonTest,PlanStoreContractTest,InMemoryPlanStoreTest,RoomPlanStoreTest}.kt`

**Interfaces:**
- Consumes: entities and `PlanDao` (task 4); `Plan`, `Cycle`, `Rollover`, `AddOn`, `FreeRule`, `AlertRule`.
- Produces: `interface PlanStore` and `PlanStores.create(context)` exactly as contract 10.2; `internal object PlanJson` with `cycleToJson/cycleFromJson/rolloverToJson/rolloverFromJson`.

- [ ] **Step 1: Write the failing tests.**

`PlanJsonTest.kt` (needs `org.json`, so it runs under Robolectric):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanJsonTest {
    private val cairo = ZoneId.of("Africa/Cairo")

    @Test fun every_cycle_survives_a_round_trip() {
        val monthly = Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), cairo)
        val every = Cycle.EveryNDays(30, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneId.of("Asia/Kolkata"))
        assertEquals(monthly, PlanJson.cycleFromJson(PlanJson.cycleToJson(monthly)))
        assertEquals(every, PlanJson.cycleFromJson(PlanJson.cycleToJson(every)))
    }

    @Test fun every_rollover_survives_a_round_trip() {
        listOf(Rollover.None, Rollover.Full, Rollover.Capped(2_000_000_000L)).forEach {
            assertEquals(it, PlanJson.rolloverFromJson(PlanJson.rolloverToJson(it)))
        }
    }

    @Test fun the_json_uses_the_shapes_of_contract_section_7() {
        val monthly = JSONObject(PlanJson.cycleToJson(Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, cairo)))
        assertEquals("monthlyOnDay", monthly.getString("type"))
        assertEquals(12, monthly.getInt("dayOfMonth"))
        assertEquals("00:00", monthly.getString("time"))
        assertEquals("Africa/Cairo", monthly.getString("zone"))
        val capped = JSONObject(PlanJson.rolloverToJson(Rollover.Capped(2_000_000_000L)))
        assertEquals("capped", capped.getString("type"))
        assertEquals(2_000_000_000L, capped.getLong("maxBytes"))
        assertEquals("none", JSONObject(PlanJson.rolloverToJson(Rollover.None)).getString("type"))
    }

    @Test(expected = IllegalArgumentException::class) fun an_unknown_cycle_type_is_rejected() {
        PlanJson.cycleFromJson("""{"type":"weekly","time":"00:00","zone":"UTC"}""")
    }
}
```

`PlanStoreContractTest.kt` (abstract; the two subclasses below run it against both stores):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class PlanStoreContractTest {
    abstract fun createStore(): PlanStore

    private fun draft(name: String = "Main", sub: Int? = 3) =
        Plan(0L, name, sub, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.of(0, 0), ZoneOffset.UTC), Rollover.Capped(2_000_000_000L))

    @Test fun a_new_plan_gets_an_id_and_reads_back_unchanged() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        assertTrue(id > 0)
        assertEquals(draft().copy(id = id), store.plan(id))
        assertEquals(listOf(draft().copy(id = id)), store.observePlans().first())
        assertEquals(draft().copy(id = id), store.observePlan(id).first())
    }

    @Test fun upserting_an_existing_id_replaces_it() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        store.upsertPlan(draft(name = "Renamed", sub = null).copy(id = id))
        assertEquals(1, store.observePlans().first().size)
        assertEquals("Renamed", store.plan(id)!!.name)
        assertNull(store.plan(id)!!.subscriptionId)
    }

    @Test fun archiving_flips_the_flag_and_reports_a_missing_plan() = runBlocking {
        val store = createStore()
        val id = store.upsertPlan(draft())
        assertTrue(store.setArchived(id, true))
        assertTrue(store.plan(id)!!.archived)
        assertTrue(store.setArchived(id, false))
        assertFalse(store.plan(id)!!.archived)
        assertFalse(store.setArchived(999L, true))
    }

    @Test fun a_missing_plan_observes_as_null() = runBlocking {
        assertNull(createStore().observePlan(42L).first())
        assertNull(createStore().plan(42L))
    }

    @Test fun add_ons_are_stored_per_plan_and_deletable() = runBlocking {
        val store = createStore()
        val a = store.upsertPlan(draft("A"))
        val b = store.upsertPlan(draft("B"))
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val until = Instant.parse("2026-10-20T00:00:00Z")
        val id = store.upsertAddOn(AddOn(0, a, "Boost", 2_000_000_000L, from, until))
        store.upsertAddOn(AddOn(0, b, "Other", 1L, from, until))
        assertEquals(listOf(AddOn(id, a, "Boost", 2_000_000_000L, from, until)), store.observeAddOns(a).first())
        store.deleteAddOn(id)
        assertTrue(store.observeAddOns(a).first().isEmpty())
        assertEquals(1, store.observeAddOns(b).first().size)
    }

    @Test fun free_rules_keep_their_days_times_and_package() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        val rule = FreeRule(0, plan, "Night", setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "com.video")
        val id = store.upsertFreeRule(rule)
        assertEquals(listOf(rule.copy(id = id)), store.observeFreeRules(plan).first())
        store.upsertFreeRule(rule.copy(id = id, packageName = null, days = setOf(DayOfWeek.MONDAY)))
        val back = store.observeFreeRules(plan).first().single()
        assertEquals(setOf(DayOfWeek.MONDAY), back.days)
        assertNull(back.packageName)
        store.deleteFreeRule(id)
        assertTrue(store.observeFreeRules(plan).first().isEmpty())
    }

    @Test fun alert_rules_are_ordered_by_threshold_and_deletable() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        store.upsertAlertRule(AlertRule(0, plan, 90, true))
        val fifty = store.upsertAlertRule(AlertRule(0, plan, 50, false))
        assertEquals(listOf(50, 90), store.alertRules(plan).map { it.thresholdPercent })
        assertEquals(listOf(50, 90), store.observeAlertRules(plan).first().map { it.thresholdPercent })
        store.deleteAlertRule(fifty)
        assertEquals(listOf(90), store.alertRules(plan).map { it.thresholdPercent })
    }

    @Test fun the_fired_percent_is_remembered_per_plan_and_window() = runBlocking {
        val store = createStore()
        val plan = store.upsertPlan(draft())
        val w1 = Instant.parse("2026-10-01T00:00:00Z")
        val w2 = Instant.parse("2026-11-01T00:00:00Z")
        assertNull(store.lastFiredPercent(plan, w1))
        store.setFiredPercent(plan, w1, 80)
        store.setFiredPercent(plan, w1, 90)
        assertEquals(90, store.lastFiredPercent(plan, w1))
        assertNull(store.lastFiredPercent(plan, w2))
        assertNull(store.lastFiredPercent(plan + 1, w1))
    }
}
```
`InMemoryPlanStoreTest.kt`: `class InMemoryPlanStoreTest : PlanStoreContractTest() { override fun createStore(): PlanStore = InMemoryPlanStore() }`.
`RoomPlanStoreTest.kt`:
```kotlin
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
```
(imports: `Room`, `ApplicationProvider`, `EmberbyteDatabase`, `PlanEntity`, `runBlocking`, `first`, `Plan`, `Cycle`, `Rollover`, `LocalTime`, `ZoneOffset`, `assertEquals`, `Test`, `Context`.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*PlanJsonTest" --tests "*PlanStore*"` → FAIL.

- [ ] **Step 3: Implement.**

`PlanStore.kt`: the interface of contract 10.2 verbatim, plus
```kotlin
object PlanStores {
    /** Room-backed store on the shared database. Room types never leave this module. */
    fun create(context: Context): PlanStore = RoomPlanStore(EmberbyteDatabases.get(context))
}
```
`PlanJson.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** JSON shapes of contract section 7; also what the M4 export will write. Throws on anything unreadable. */
internal object PlanJson {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun cycleToJson(cycle: Cycle): String {
        val o = JSONObject()
        when (cycle) {
            is Cycle.MonthlyOnDay -> o.put("type", "monthlyOnDay").put("dayOfMonth", cycle.dayOfMonth)
                .put("time", cycle.time.format(TIME)).put("zone", cycle.zone.id)
            is Cycle.EveryNDays -> o.put("type", "everyNDays").put("days", cycle.days).put("anchor", cycle.anchor.toString())
                .put("time", cycle.time.format(TIME)).put("zone", cycle.zone.id)
        }
        return o.toString()
    }

    fun cycleFromJson(json: String): Cycle {
        val o = JSONObject(json)
        val time = LocalTime.parse(o.getString("time"))
        val zone = ZoneId.of(o.getString("zone"))
        return when (val type = o.getString("type")) {
            "monthlyOnDay" -> Cycle.MonthlyOnDay(o.getInt("dayOfMonth"), time, zone)
            "everyNDays" -> Cycle.EveryNDays(o.getInt("days"), LocalDate.parse(o.getString("anchor")), time, zone)
            else -> throw IllegalArgumentException("Unknown cycle type $type")
        }
    }

    fun rolloverToJson(rollover: Rollover): String = when (rollover) {
        Rollover.None -> JSONObject().put("type", "none")
        Rollover.Full -> JSONObject().put("type", "full")
        is Rollover.Capped -> JSONObject().put("type", "capped").put("maxBytes", rollover.maxBytes)
    }.toString()

    fun rolloverFromJson(json: String): Rollover {
        val o = JSONObject(json)
        return when (val type = o.getString("type")) {
            "none" -> Rollover.None
            "full" -> Rollover.Full
            "capped" -> Rollover.Capped(o.getLong("maxBytes"))
            else -> throw IllegalArgumentException("Unknown rollover type $type")
        }
    }
}
```
`RoomPlanStore.kt` (mapping + DAO calls; every `observe*` that maps rows to domain skips unreadable plan rows):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.db.*
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
private fun daysOf(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
private fun Plan.toEntity() = PlanEntity(id, name, subscriptionId, capBytes, PlanJson.cycleToJson(cycle), PlanJson.rolloverToJson(rollover), archived)
private fun PlanEntity.toDomainOrNull(): Plan? = runCatching {
    Plan(id, name, subscriptionId, capBytes, PlanJson.cycleFromJson(cycleJson), PlanJson.rolloverFromJson(rolloverJson), archived)
}.getOrNull()
private fun AddOn.toEntity() = AddOnEntity(id, planId, label, bytes, validFrom.toEpochMilli(), validUntil.toEpochMilli())
private fun AddOnEntity.toDomain() = AddOn(id, planId, label, bytes, Instant.ofEpochMilli(validFrom), Instant.ofEpochMilli(validUntil))
private fun FreeRule.toEntity() = FreeRuleEntity(id, planId, label, maskOf(days), start.toSecondOfDay() / 60, end.toSecondOfDay() / 60, packageName)
private fun FreeRuleEntity.toDomain() = FreeRule(id, planId, label, daysOf(daysMask), LocalTime.ofSecondOfDay(startMinute * 60L), LocalTime.ofSecondOfDay(endMinute * 60L), packageName)
private fun AlertRule.toEntity() = AlertRuleEntity(id, planId, thresholdPercent, enabled)
private fun AlertRuleEntity.toDomain() = AlertRule(id, planId, thresholdPercent, enabled)

class RoomPlanStore(db: EmberbyteDatabase) : PlanStore {
    private val dao = db.planDao()

    override fun observePlans(): Flow<List<Plan>> = dao.observePlans().map { list -> list.mapNotNull { it.toDomainOrNull() } }
    override fun observePlan(id: Long): Flow<Plan?> = dao.observePlan(id).map { it?.toDomainOrNull() }
    override suspend fun plan(id: Long): Plan? = dao.plan(id)?.toDomainOrNull()
    override suspend fun upsertPlan(plan: Plan): Long = dao.putPlan(plan.toEntity())
    override suspend fun setArchived(id: Long, archived: Boolean): Boolean = dao.setArchived(id, archived) > 0
    override fun observeAddOns(planId: Long) = dao.observeAddOns(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun upsertAddOn(addOn: AddOn): Long = dao.putAddOn(addOn.toEntity())
    override suspend fun deleteAddOn(id: Long) = dao.deleteAddOn(id)
    override fun observeFreeRules(planId: Long) = dao.observeFreeRules(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun upsertFreeRule(rule: FreeRule): Long = dao.putFreeRule(rule.toEntity())
    override suspend fun deleteFreeRule(id: Long) = dao.deleteFreeRule(id)
    override fun observeAlertRules(planId: Long) = dao.observeAlertRules(planId).map { list -> list.map { it.toDomain() } }
    override suspend fun alertRules(planId: Long) = dao.alertRules(planId).map { it.toDomain() }
    override suspend fun upsertAlertRule(rule: AlertRule): Long = dao.putAlertRule(rule.toEntity())
    override suspend fun deleteAlertRule(id: Long) = dao.deleteAlertRule(id)
    override suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int? = dao.firedPercent(planId, windowStart.toEpochMilli())
    override suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int) =
        dao.putFired(AlertFiredEntity(planId, windowStart.toEpochMilli(), percent))
}
```
`InMemoryPlanStore.kt` (package `…data.fake`, same semantics, one counter per table, `observePlans` sorted by id):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryPlanStore : PlanStore {
    private val plans = MutableStateFlow<List<Plan>>(emptyList())
    private val addOns = MutableStateFlow<List<AddOn>>(emptyList())
    private val freeRules = MutableStateFlow<List<FreeRule>>(emptyList())
    private val alertRules = MutableStateFlow<List<AlertRule>>(emptyList())
    private val fired = MutableStateFlow<Map<Pair<Long, Instant>, Int>>(emptyMap())
    private val planIds = AtomicLong(0)
    private val addOnIds = AtomicLong(0)
    private val freeRuleIds = AtomicLong(0)
    private val alertRuleIds = AtomicLong(0)

    private fun <T> MutableStateFlow<List<T>>.put(item: T, id: Long, idOf: (T) -> Long, counter: AtomicLong, withId: (T, Long) -> T): Long {
        val real = if (id == 0L) counter.incrementAndGet() else id
        update { list -> list.filter { idOf(it) != real } + withId(item, real) }
        return real
    }

    override fun observePlans(): Flow<List<Plan>> = plans.map { it.sortedBy(Plan::id) }
    override fun observePlan(id: Long): Flow<Plan?> = plans.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun plan(id: Long): Plan? = plans.value.firstOrNull { it.id == id }
    override suspend fun upsertPlan(plan: Plan): Long = plans.put(plan, plan.id, Plan::id, planIds) { p, id -> p.copy(id = id) }
    override suspend fun setArchived(id: Long, archived: Boolean): Boolean {
        if (plans.value.none { it.id == id }) return false
        plans.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
        return true
    }
    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = addOns.map { list -> list.filter { it.planId == planId }.sortedWith(compareBy({ it.validFrom }, { it.id })) }
    override suspend fun upsertAddOn(addOn: AddOn): Long = addOns.put(addOn, addOn.id, AddOn::id, addOnIds) { a, id -> a.copy(id = id) }
    override suspend fun deleteAddOn(id: Long) = addOns.update { list -> list.filter { it.id != id } }
    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = freeRules.map { list -> list.filter { it.planId == planId }.sortedBy { it.id } }
    override suspend fun upsertFreeRule(rule: FreeRule): Long = freeRules.put(rule, rule.id, FreeRule::id, freeRuleIds) { r, id -> r.copy(id = id) }
    override suspend fun deleteFreeRule(id: Long) = freeRules.update { list -> list.filter { it.id != id } }
    override fun observeAlertRules(planId: Long): Flow<List<AlertRule>> = alertRules.map { list -> list.filter { it.planId == planId }.sortedBy { it.thresholdPercent } }
    override suspend fun alertRules(planId: Long): List<AlertRule> = alertRules.value.filter { it.planId == planId }.sortedBy { it.thresholdPercent }
    override suspend fun upsertAlertRule(rule: AlertRule): Long = alertRules.put(rule, rule.id, AlertRule::id, alertRuleIds) { r, id -> r.copy(id = id) }
    override suspend fun deleteAlertRule(id: Long) = alertRules.update { list -> list.filter { it.id != id } }
    override suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int? = fired.value[planId to windowStart]
    override suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int) = fired.update { it + ((planId to windowStart) to percent) }
}
```

- [ ] **Step 4: Run to verify they pass** — new tests green, full gate green.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): persist plans, add-ons, free windows and alert rules behind a PlanStore

An in-memory twin runs the same contract tests as the Room store, so repository and view-model tests stay fast and honest."
```

---

### Task 6: Plan validation and the pure state assembler

**Files:**
- Create: `core/data/src/main/kotlin/$PKG/data/plan/{PlanValidation,PlanStateAssembler}.kt`
- Test: `core/data/src/test/kotlin/$PKG/data/plan/{PlanValidationTest,PlanStateAssemblerTest}.kt`

**Interfaces:**
- Consumes: `CycleCalculator`, `PlanCalculator`, `UsageSlice` (tasks 1-2); `SeriesBuilder`, `MinuteTotal`, `HourlyUsage`, `AppMeta`, `CoverageGap`.
- Produces: `internal object PlanValidation { fun plan(p: Plan): EmberbyteError.Invalid?; fun addOn(a: AddOn): EmberbyteError.Invalid?; fun freeRule(r: FreeRule): EmberbyteError.Invalid? }`; `internal data class PlanRows(minutes, hourly, meta, gaps)`; `internal object PlanStateAssembler { fun rowsFrom(plan, now): Instant; fun state(plan, addOns, rules, now, rows): PlanState; fun history(plan, now, rows): List<DayUsage> }`.

- [ ] **Step 1: Write the failing tests.**

`PlanValidationTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanValidationTest {
    private val ok = Plan(0, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
    private fun field(p: Plan) = PlanValidation.plan(p)?.field

    @Test fun a_good_plan_passes() = assertNull(PlanValidation.plan(ok))

    @Test fun each_bad_field_is_named() {
        assertEquals("name", field(ok.copy(name = "  ")))
        assertEquals("name", field(ok.copy(name = "x".repeat(41))))
        assertEquals("capBytes", field(ok.copy(capBytes = 0)))
        assertEquals("capBytes", field(ok.copy(capBytes = 2_000_000_000_000_000L)))
        assertEquals("cycle.dayOfMonth", field(ok.copy(cycle = Cycle.MonthlyOnDay(0, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.dayOfMonth", field(ok.copy(cycle = Cycle.MonthlyOnDay(32, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.days", field(ok.copy(cycle = Cycle.EveryNDays(0, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("cycle.days", field(ok.copy(cycle = Cycle.EveryNDays(367, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertEquals("rollover.maxBytes", field(ok.copy(rollover = Rollover.Capped(0))))
        assertEquals("subscriptionId", field(ok.copy(subscriptionId = -5)))
    }

    @Test fun boundaries_are_accepted() {
        assertNull(PlanValidation.plan(ok.copy(name = "x".repeat(40), capBytes = 1)))
        assertNull(PlanValidation.plan(ok.copy(cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, ZoneOffset.UTC))))
        assertNull(PlanValidation.plan(ok.copy(cycle = Cycle.EveryNDays(366, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC))))
    }

    @Test fun add_ons_and_free_rules_are_checked() {
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val addOn = AddOn(0, 1, "Boost", 1, from, from.plusSeconds(60))
        assertNull(PlanValidation.addOn(addOn))
        assertEquals("bytes", PlanValidation.addOn(addOn.copy(bytes = 0))?.field)
        assertEquals("validUntil", PlanValidation.addOn(addOn.copy(validUntil = from))?.field)
        assertEquals("label", PlanValidation.addOn(addOn.copy(label = " "))?.field)
        val rule = FreeRule(0, 1, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        assertNull(PlanValidation.freeRule(rule))
        assertEquals("days", PlanValidation.freeRule(rule.copy(days = emptySet()))?.field)
        assertEquals("time", PlanValidation.freeRule(rule.copy(end = LocalTime.of(0, 0)))?.field)
        assertEquals("label", PlanValidation.freeRule(rule.copy(label = ""))?.field)
    }
}
```
`PlanStateAssemblerTest.kt` (`now = 2026-10-15T12:00Z`, plan cap 10 GB, monthly day 1 UTC):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanStateAssemblerTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private fun plan(sub: Int? = null, rollover: Rollover = Rollover.None) =
        Plan(1, "Main", sub, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), rollover)
    private fun minute(at: String, bytes: Long, sub: Int = 1, network: NetworkKind = NetworkKind.MOBILE) =
        MinuteTotal(Instant.parse(at), network, sub, bytes, 0)
    private fun rows(minutes: List<MinuteTotal> = emptyList(), hourly: List<HourlyUsage> = emptyList(), meta: List<AppMeta> = emptyList(), gaps: List<CoverageGap> = emptyList()) =
        PlanRows(minutes, hourly, meta, gaps)
    private fun state(p: Plan = plan(), rows: PlanRows, rules: List<FreeRule> = emptyList()) =
        PlanStateAssembler.state(p, emptyList(), rules, now, rows)

    @Test fun mobile_minutes_in_the_window_are_the_used_bytes() {
        val s = state(rows = rows(listOf(minute("2026-10-10T10:00:00Z", 1_000_000_000L), minute("2026-10-12T10:00:00Z", 500_000_000L))))
        assertEquals(1_500_000_000L, s.usedBytes)
    }

    @Test fun wifi_and_traffic_before_the_window_do_not_count() {
        val s = state(rows = rows(listOf(
            minute("2026-10-10T10:00:00Z", 9_000_000_000L, network = NetworkKind.WIFI),
            minute("2026-09-20T10:00:00Z", 9_000_000_000L),
        )))
        assertEquals(0L, s.usedBytes)
    }

    @Test fun a_bound_plan_counts_only_its_sim_and_is_marked_approximate_when_another_sim_was_used() {
        val r = rows(listOf(minute("2026-10-10T10:00:00Z", 1_000_000_000L, sub = 1), minute("2026-10-11T10:00:00Z", 400_000_000L, sub = 2)))
        val bound = state(plan(sub = 2), r)
        assertEquals(400_000_000L, bound.usedBytes)
        assertTrue(bound.isApproximate)
        val unbound = state(plan(sub = null), r)
        assertEquals(1_400_000_000L, unbound.usedBytes)
        assertFalse(unbound.isApproximate)
    }

    @Test fun a_bound_plan_whose_only_traffic_is_its_own_sim_is_exact() {
        val s = state(plan(sub = 2), rows(listOf(minute("2026-10-11T10:00:00Z", 400_000_000L, sub = 2))))
        assertFalse(s.isApproximate)
    }

    @Test fun kill_reboot_and_permission_gaps_inside_the_window_make_it_approximate() {
        fun gap(reason: GapReason, from: String = "2026-10-10T10:00:00Z", to: String = "2026-10-10T11:00:00Z") =
            CoverageGap(Instant.parse(from), Instant.parse(to), reason)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.SERVICE_KILLED)))).isApproximate)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.REBOOT)))).isApproximate)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.PERMISSION_MISSING)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.DEVICE_ASLEEP)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.COUNTER_RESET)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.SERVICE_KILLED, "2026-09-10T10:00:00Z", "2026-09-10T11:00:00Z")))).isApproximate)
    }

    @Test fun full_rollover_carries_the_unused_part_of_the_previous_cycle() {
        val r = rows(listOf(minute("2026-09-10T10:00:00Z", 6_000_000_000L)))
        val s = state(plan(rollover = Rollover.Full), r)
        assertEquals(4_000_000_000L, s.rolledOverBytes)
        assertEquals(14_000_000_000L, s.effectiveCapBytes)
        assertEquals(1_000_000_000L, state(plan(rollover = Rollover.Capped(1_000_000_000L)), r).rolledOverBytes)
    }

    @Test fun nothing_rolls_over_when_the_previous_cycle_was_overspent() {
        val r = rows(listOf(minute("2026-09-10T10:00:00Z", 12_000_000_000L)))
        assertEquals(0L, state(plan(rollover = Rollover.Full), r).rolledOverBytes)
    }

    @Test fun an_app_scoped_free_rule_uses_the_two_hour_window_rows_of_that_app() {
        val video = FreeRule(1, 1, "Video", DayOfWeek.entries.toSet(), LocalTime.of(21, 0), LocalTime.of(23, 0), "com.video")
        val hourly = listOf(
            HourlyUsage(Instant.parse("2026-10-10T20:00:00Z"), 10001, NetworkKind.MOBILE, 1, 700_000_000L, 0),
            HourlyUsage(Instant.parse("2026-10-10T20:00:00Z"), 10002, NetworkKind.MOBILE, 1, 300_000_000L, 0),
        )
        val meta = listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.other", "Other"))
        val s = state(rows = rows(hourly = hourly, meta = meta), rules = listOf(video))
        // the app's window row [20:00, 22:00) overlaps the rule [21:00, 23:00) by half: 350 MB are free
        assertEquals(350_000_000L, s.freeBytes)
        assertEquals(1_000_000_000L - 350_000_000L, s.usedBytes)
    }

    @Test fun the_history_has_one_mobile_entry_per_day_without_leading_empty_days() {
        val r = rows(listOf(minute("2026-10-05T10:00:00Z", 1_000_000_000L), minute("2026-10-07T10:00:00Z", 2_000_000_000L)))
        val h = PlanStateAssembler.history(plan(), now, r)
        assertEquals(LocalDate.of(2026, 10, 5), h.first().date)
        assertEquals(LocalDate.of(2026, 10, 15), h.last().date)           // today is included; the forecast engine ignores it
        assertEquals(11, h.size)
        assertEquals(listOf(1_000_000_000L, 0L, 2_000_000_000L), h.take(3).map { it.mobileBytes })
    }

    @Test fun no_traffic_gives_an_empty_history() {
        assertTrue(PlanStateAssembler.history(plan(), now, rows()).isEmpty())
    }

    @Test fun rows_are_requested_from_before_the_history_and_the_previous_window() {
        val from = PlanStateAssembler.rowsFrom(plan(), now)
        assertTrue(from <= Instant.parse("2026-09-17T00:00:00Z").minusSeconds(7200))   // 28 days before 15 Oct
        assertTrue(from <= Instant.parse("2026-09-01T00:00:00Z").minusSeconds(7200))   // start of the previous window
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*PlanValidationTest" --tests "*PlanStateAssemblerTest"` → FAIL.

- [ ] **Step 3: Implement.**

`PlanValidation.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError.Invalid
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover

/** Contract 10.2 validation: the first problem found, naming the field. null = valid. */
internal object PlanValidation {
    private const val MAX_CAP = 1_000_000_000_000_000L

    fun plan(plan: Plan): Invalid? {
        if (plan.name.isBlank() || plan.name.trim().length > 40) return Invalid("name", "Enter a name of up to 40 characters")
        if (plan.capBytes !in 1L..MAX_CAP) return Invalid("capBytes", "Enter a cap greater than zero")
        when (val cycle = plan.cycle) {
            is Cycle.MonthlyOnDay -> if (cycle.dayOfMonth !in 1..31) return Invalid("cycle.dayOfMonth", "Choose a day from 1 to 31")
            is Cycle.EveryNDays -> if (cycle.days !in 1..366) return Invalid("cycle.days", "Choose from 1 to 366 days")
        }
        val rollover = plan.rollover
        if (rollover is Rollover.Capped && rollover.maxBytes <= 0L) return Invalid("rollover.maxBytes", "Enter an amount greater than zero")
        val subscription = plan.subscriptionId
        if (subscription != null && subscription < 0) return Invalid("subscriptionId", "Choose a SIM from the list")
        return null
    }

    fun addOn(addOn: AddOn): Invalid? = when {
        addOn.label.isBlank() -> Invalid("label", "Enter a label")
        addOn.bytes <= 0L -> Invalid("bytes", "Enter an amount greater than zero")
        addOn.validUntil <= addOn.validFrom -> Invalid("validUntil", "Must be after the start")
        else -> null
    }

    fun freeRule(rule: FreeRule): Invalid? = when {
        rule.label.isBlank() -> Invalid("label", "Enter a label")
        rule.days.isEmpty() -> Invalid("days", "Choose at least one day")
        rule.start == rule.end -> Invalid("time", "Start and end must differ")
        else -> null
    }
}
```
`PlanStateAssembler.kt` — implement exactly the logic described in contract 10.2 (this is the whole file):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.plan.CycleCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.PlanCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.UsageSlice
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Instant
import java.time.ZoneId

internal data class PlanRows(
    val minutes: List<MinuteTotal>,
    val hourly: List<HourlyUsage>,
    val meta: List<AppMeta>,
    val gaps: List<CoverageGap>,
)

internal object PlanStateAssembler {
    const val HISTORY_DAYS = 28L
    private val APPROXIMATE_GAPS = setOf(GapReason.SERVICE_KILLED, GapReason.REBOOT, GapReason.PERMISSION_MISSING)

    private fun zoneOf(plan: Plan): ZoneId = PlanCalculator.zoneOf(plan.cycle)

    /** Earliest instant whose rows are needed: the forecast history or the previous window, whichever is older, plus one platform window. */
    fun rowsFrom(plan: Plan, now: Instant): Instant {
        val zone = zoneOf(plan)
        val historyStart = now.atZone(zone).toLocalDate().minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val previous = CycleCalculator.previousWindow(plan.cycle, CycleCalculator.windowAt(plan.cycle, now))
        return minOf(historyStart, previous.start).minusSeconds(SeriesBuilder.WINDOW_SECONDS)
    }

    fun state(plan: Plan, addOns: List<AddOn>, rules: List<FreeRule>, now: Instant, rows: PlanRows): PlanState {
        val window = CycleCalculator.windowAt(plan.cycle, now)
        val slices = slices(plan, rules, window, rows)
        val previousUnused = if (plan.rollover == Rollover.None) 0L else {
            val previous = CycleCalculator.previousWindow(plan.cycle, window)
            // No chained rollover: the previous cycle is evaluated as if it had none (contract 10.1).
            PlanCalculator.state(
                plan.copy(rollover = Rollover.None), previous.end.minusMillis(1),
                slices(plan, rules, previous, rows), addOns, rules, 0L, false,
            ).remainingBytes
        }
        return PlanCalculator.state(plan, now, slices, addOns, rules, previousUnused, approximate(plan, window, now, rows))
    }

    fun history(plan: Plan, now: Instant, rows: PlanRows): List<DayUsage> {
        val zone = zoneOf(plan)
        val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        val points = SeriesBuilder.build(
            DateRange(from, to), Granularity.DAY, zone, rows.minutes, rows.hourly,
            UsageFilter(NetworkKind.MOBILE, plan.subscriptionId),
        )
        return points.map { DayUsage(it.start.atZone(zone).toLocalDate(), it.mobileBytes, 0L) }.dropWhile { it.mobileBytes == 0L }
    }

    private fun slices(plan: Plan, rules: List<FreeRule>, window: CycleWindow, rows: PlanRows): List<UsageSlice> {
        val filter = UsageFilter(NetworkKind.MOBILE, plan.subscriptionId)
        val hours = SeriesBuilder.build(DateRange(window.start, window.end), Granularity.HOUR, zoneOf(plan), rows.minutes, rows.hourly, filter)
        val total = hours.filter { it.mobileBytes > 0L }.map { UsageSlice(it.start, it.start.plusSeconds(3_600), null, it.mobileBytes) }
        if (rules.none { it.packageName != null }) return total
        // Per-app slices are the 2-hour window rows (the platform's resolution); only package-scoped free rules use them.
        val packageByUid = rows.meta.associate { it.uid to it.packageName }
        val perApp = rows.hourly
            .filter { it.network == NetworkKind.MOBILE && (plan.subscriptionId == null || it.subscriptionId == plan.subscriptionId) }
            .groupBy { it.hourStart to packageByUid[it.uid] }
            .mapNotNull { (key, list) ->
                val pkg = key.second ?: return@mapNotNull null
                UsageSlice(key.first, key.first.plusSeconds(SeriesBuilder.WINDOW_SECONDS), pkg, list.sumOf { it.totalBytes })
            }
            .filter { it.bytes > 0L }
        return total + perApp
    }

    private fun approximate(plan: Plan, window: CycleWindow, now: Instant, rows: PlanRows): Boolean {
        val to = minOf(now, window.end)
        val bound = plan.subscriptionId
        val otherSim = bound != null && rows.minutes.any {
            it.network == NetworkKind.MOBILE && it.totalBytes > 0L && it.minuteStart >= window.start && it.minuteStart < to && it.subscriptionId != bound
        }
        val gap = rows.gaps.any { it.reason in APPROXIMATE_GAPS && it.to > window.start && it.from < to }
        return otherSim || gap
    }
}
```

- [ ] **Step 4: Run to verify they pass** — new tests green, full gate green.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): validate plans and assemble plan state from the same rows Home uses

Reusing SeriesBuilder means the plan's used bytes can never disagree with Home or the notification."
```

---

### Task 7: `RoomPlanRepository`, `previewState`, wiring, 28-day backfill

**Files:**
- Create: `core/data/src/main/kotlin/$PKG/data/plan/RoomPlanRepository.kt`
- Modify: `core/data/src/main/kotlin/$PKG/data/PlanRepository.kt` (add `previewState`), `data/fake/FakePlanRepository.kt` (implement it), `data/sampler/HourlyCatchUp.kt` (`maxBackfill` default `Duration.ofDays(28)`)
- Delete: `core/data/src/main/kotlin/$PKG/data/NoPlanRepository.kt` and `core/data/src/test/kotlin/$PKG/data/NoPlanRepositoryTest.kt`
- Modify: `app/src/main/kotlin/$PKG/AppGraph.kt` (use the real repository), `app/src/main/kotlin/$PKG/sampler/SamplerService.kt` (log the catch-up duration)
- Test: `core/data/src/test/kotlin/$PKG/data/plan/RoomPlanRepositoryTest.kt`; adjust `HourlyCatchUpTest` only where it relies on the old 7-day default (name each such test in your report)

**Interfaces:**
- Consumes: `PlanStore`, `InMemoryPlanStore`, `UsageStore`, `PlanValidation`, `PlanStateAssembler`, `PlanRows`, `CycleCalculator`, `ForecastEngine`.
- Produces: `class RoomPlanRepository(plans: PlanStore, usage: UsageStore, clock: Clock, ticks: Flow<Instant> = minuteTicks(clock)) : PlanRepository`; `fun minuteTicks(clock: Clock): Flow<Instant>`; `PlanRepository.previewState(plan, addOns, freeRules): Outcome<PlanPreview>`.

- [ ] **Step 1: Write the failing tests** `RoomPlanRepositoryTest.kt` (JVM only: both stores are in-memory; `2026-10-15T12:00Z` fixed):

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlanRepositoryTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planStore = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private val repo = RoomPlanRepository(planStore, usage, clock, ticks = flowOf(now))

    private fun draft(name: String = "Main", sub: Int? = null, cap: Long = 10_000_000_000L, rollover: Rollover = Rollover.None) =
        Plan(0L, name, sub, cap, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), rollover)

    private suspend fun mobile(at: String, bytes: Long, sub: Int = 1) =
        usage.addMinute(MinuteTotal(Instant.parse(at), NetworkKind.MOBILE, sub, bytes, 0))

    private suspend fun save(plan: Plan = draft()): Long = (repo.upsertPlan(plan) as Outcome.Success).value

    @Test fun a_saved_plan_is_observable_with_its_state() = runBlocking {
        val id = save()
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        val state = repo.observePlanState(id).first()!!
        assertEquals(1_000_000_000L, state.usedBytes)
        assertEquals(9_000_000_000L, state.remainingBytes)
        assertEquals(listOf("Main"), repo.observePlans().first().map { it.name })
    }

    @Test fun the_state_follows_new_usage() = runTest {
        val id = save()
        val seen = mutableListOf<Long>()
        val job = launch { repo.observePlanState(id).collect { seen += it!!.usedBytes } }
        runCurrent()
        mobile("2026-10-10T10:00:00Z", 2_000_000_000L)
        runCurrent()
        job.cancel()
        assertEquals(listOf(0L, 2_000_000_000L), seen.distinct())
    }

    @Test fun a_missing_plan_observes_as_null_state_and_forecast() = runBlocking {
        assertNull(repo.observePlanState(99L).first())
        assertNull(repo.observeForecast(99L).first())
    }

    @Test fun invalid_plans_are_rejected_and_nothing_is_saved() = runBlocking {
        val bad = repo.upsertPlan(draft(name = " ")) as Outcome.Failure
        assertEquals("name", (bad.error as EmberbyteError.Invalid).field)
        assertEquals("capBytes", ((repo.upsertPlan(draft(cap = 0)) as Outcome.Failure).error as EmberbyteError.Invalid).field)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())
    }

    @Test fun the_name_is_trimmed() = runBlocking {
        val id = save(draft(name = "  Work  "))
        assertEquals("Work", repo.observePlans().first().single { it.id == id }.name)
    }

    @Test fun editing_a_missing_plan_and_adding_children_to_it_is_not_found() = runBlocking {
        assertEquals(EmberbyteError.NotFound, (repo.upsertPlan(draft().copy(id = 99)) as Outcome.Failure).error)
        val from = Instant.parse("2026-10-05T00:00:00Z")
        assertEquals(EmberbyteError.NotFound, (repo.upsertAddOn(AddOn(0, 99, "Boost", 1, from, from.plusSeconds(60))) as Outcome.Failure).error)
        val rule = FreeRule(0, 99, "Night", setOf(DayOfWeek.MONDAY), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        assertEquals(EmberbyteError.NotFound, (repo.upsertFreeRule(rule) as Outcome.Failure).error)
    }

    @Test fun invalid_children_are_rejected_with_the_field_name() = runBlocking {
        val id = save()
        val from = Instant.parse("2026-10-05T00:00:00Z")
        val badAddOn = repo.upsertAddOn(AddOn(0, id, "Boost", 0, from, from.plusSeconds(60))) as Outcome.Failure
        assertEquals("bytes", (badAddOn.error as EmberbyteError.Invalid).field)
        val badRule = repo.upsertFreeRule(FreeRule(0, id, "Night", emptySet(), LocalTime.of(0, 0), LocalTime.of(6, 0), null)) as Outcome.Failure
        assertEquals("days", (badRule.error as EmberbyteError.Invalid).field)
    }

    @Test fun add_ons_and_free_rules_change_the_state() = runBlocking {
        val id = save()
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        repo.upsertAddOn(AddOn(0, id, "Boost", 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")))
        assertEquals(12_000_000_000L, repo.observePlanState(id).first()!!.effectiveCapBytes)
        repo.upsertFreeRule(FreeRule(0, id, "Mornings", DayOfWeek.entries.toSet(), LocalTime.of(10, 0), LocalTime.of(12, 0), null))
        assertEquals(0L, repo.observePlanState(id).first()!!.usedBytes)
        assertEquals(1_000_000_000L, repo.observePlanState(id).first()!!.freeBytes)
    }

    @Test fun archived_plans_leave_the_active_list() = runBlocking {
        val id = save()
        assertEquals(1, repo.observeActivePlanStates().first().size)
        assertTrue(repo.archivePlan(id) is Outcome.Success)
        assertTrue(repo.observePlans(includeArchived = false).first().isEmpty())
        assertEquals(1, repo.observePlans(includeArchived = true).first().size)
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertEquals(EmberbyteError.NotFound, (repo.archivePlan(99) as Outcome.Failure).error)
    }

    @Test fun rollover_uses_the_previous_cycle() = runBlocking {
        val id = save(draft(rollover = Rollover.Full))
        mobile("2026-09-10T10:00:00Z", 6_000_000_000L)
        assertEquals(4_000_000_000L, repo.observePlanState(id).first()!!.rolledOverBytes)
    }

    private suspend fun fillHistory() {
        // 28 days of 1 GB at 10:00 UTC, 17 Sep .. 14 Oct
        var day = Instant.parse("2026-09-17T10:00:00Z")
        repeat(28) { mobile(day.toString(), 1_000_000_000L); day = day.plusSeconds(86_400) }
    }

    @Test fun a_forecast_with_enough_history_says_the_plan_lasts() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 100_000_000_000L))
        val f = repo.observeForecast(id).first()!!
        assertNull(f.runOutExpected)
        assertEquals(Confidence.HIGH, f.confidence)
        assertEquals(28, f.historyDays)
    }

    @Test fun a_forecast_names_the_run_out_day() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 20_000_000_000L))       // 14 GB used in October, 6 GB left at 1 GB/day
        val f = repo.observeForecast(id).first()!!
        assertEquals("2026-10-21", f.runOutExpected!!.toString().substring(0, 10))
    }

    @Test fun preview_state_evaluates_an_unsaved_draft_and_rejects_an_invalid_one() = runBlocking {
        mobile("2026-10-10T10:00:00Z", 1_000_000_000L)
        val ok = repo.previewState(draft(), emptyList(), emptyList()) as Outcome.Success
        assertEquals(1_000_000_000L, ok.value.state.usedBytes)
        assertNotNull(ok.value.forecast)
        val bad = repo.previewState(draft(cap = 0), emptyList(), emptyList()) as Outcome.Failure
        assertEquals("capBytes", (bad.error as EmberbyteError.Invalid).field)
        assertTrue(repo.observePlans(includeArchived = true).first().isEmpty())     // nothing was persisted
    }

    @Test fun what_if_extra_usage_brings_the_run_out_forward_and_a_missing_plan_is_not_found() = runBlocking {
        fillHistory()
        val id = save(draft(cap = 100_000_000_000L))
        assertNull(repo.observeForecast(id).first()!!.runOutExpected)
        val extra = (repo.whatIf(id, 20_000_000_000L, now) as Outcome.Success).value
        assertNotNull(extra.runOutExpected)
        assertEquals(EmberbyteError.NotFound, (repo.whatIf(99L, 1L, now) as Outcome.Failure).error)
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*RoomPlanRepositoryTest"` → FAIL.

- [ ] **Step 3: Implement.**

`PlanRepository.kt`: add `suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview>` (import `PlanPreview`) with the KDoc "state and forecast of an unsaved draft, not persisted".

`FakePlanRepository.kt`: add
```kotlin
    override suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview> =
        Outcome.Success(PlanPreview(stateFor(plan), forecastFor(plan.id)))
```
Delete `NoPlanRepository.kt` and `NoPlanRepositoryTest.kt`.

`RoomPlanRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.forecast.ForecastEngine
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanPreview
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.plan.CycleCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.PlanCalculator
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Emits the current instant now and then every minute (plan windows roll over at the renewal instant). */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(60_000L)
    }
}

/** The real [PlanRepository]: plans live in the [PlanStore], every number is derived from the [UsageStore] rows (contract 10.2). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlanRepository(
    private val plans: PlanStore,
    private val usage: UsageStore,
    private val clock: Clock,
    private val ticks: Flow<Instant> = minuteTicks(clock),
) : PlanRepository {

    private data class Inputs(val plan: Plan?, val addOns: List<AddOn>, val rules: List<FreeRule>)
    private data class Snapshot(val state: PlanState, val history: List<DayUsage>, val now: Instant)

    private fun rows(plan: Plan, now: Instant): Flow<PlanRows> {
        val window = CycleCalculator.windowAt(plan.cycle, now)
        val from = PlanStateAssembler.rowsFrom(plan, now)
        return combine(
            usage.observeMinuteRows(from, window.end),
            usage.observeHourlyRows(from, window.end),
            usage.observeAppMeta(),
            usage.observeGaps(window.start, window.end),
        ) { minutes, hourly, meta, gaps -> PlanRows(minutes, hourly, meta, gaps) }
    }

    private fun snapshots(planId: Long): Flow<Snapshot?> =
        combine(plans.observePlan(planId), plans.observeAddOns(planId), plans.observeFreeRules(planId)) { p, a, r -> Inputs(p, a, r) }
            .flatMapLatest { (plan, addOns, rules) ->
                if (plan == null) flowOf(null)
                else ticks.flatMapLatest { now ->
                    rows(plan, now).map { rows ->
                        Snapshot(PlanStateAssembler.state(plan, addOns, rules, now, rows), PlanStateAssembler.history(plan, now, rows), now)
                    }
                }
            }

    private fun forecastOf(snapshot: Snapshot, extra: Long = 0L, extraFrom: Instant = snapshot.now): Forecast =
        ForecastEngine.forecast(
            snapshot.state, snapshot.history, snapshot.now, PlanCalculator.zoneOf(snapshot.state.plan.cycle),
            extraBytesPerDay = extra, extraFrom = extraFrom,
        )

    override fun observePlans(includeArchived: Boolean): Flow<List<Plan>> =
        plans.observePlans().map { list -> if (includeArchived) list else list.filter { !it.archived } }

    override fun observePlanState(planId: Long): Flow<PlanState?> = snapshots(planId).map { it?.state }

    override fun observeActivePlanStates(): Flow<List<PlanState>> = observePlans(false).flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { observePlanState(it.id) }) { states -> states.filterNotNull() }
    }

    override fun observeForecast(planId: Long): Flow<Forecast?> = snapshots(planId).map { it?.let(::forecastOf) }

    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = plans.observeAddOns(planId)

    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = plans.observeFreeRules(planId)

    private suspend fun <T> guarded(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error"))
    }

    override suspend fun upsertPlan(plan: Plan): Outcome<Long> {
        PlanValidation.plan(plan)?.let { return Outcome.Failure(it) }
        if (plan.id != 0L && plans.plan(plan.id) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertPlan(plan.copy(name = plan.name.trim())) }
    }

    override suspend fun archivePlan(planId: Long): Outcome<Unit> =
        guarded { plans.setArchived(planId, true) }.let { outcome ->
            when (outcome) {
                is Outcome.Failure -> outcome
                is Outcome.Success -> if (outcome.value) Outcome.Success(Unit) else Outcome.Failure(EmberbyteError.NotFound)
            }
        }

    override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> {
        PlanValidation.addOn(addOn)?.let { return Outcome.Failure(it) }
        if (plans.plan(addOn.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertAddOn(addOn.copy(label = addOn.label.trim())) }
    }

    override suspend fun deleteAddOn(id: Long): Outcome<Unit> = guarded { plans.deleteAddOn(id) }

    override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> {
        PlanValidation.freeRule(rule)?.let { return Outcome.Failure(it) }
        if (plans.plan(rule.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { plans.upsertFreeRule(rule.copy(label = rule.label.trim())) }
    }

    override suspend fun deleteFreeRule(id: Long): Outcome<Unit> = guarded { plans.deleteFreeRule(id) }

    override suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast> {
        if (plans.plan(planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        return guarded { forecastOf(snapshots(planId).first() ?: error("plan vanished"), extraBytesPerDay, from) }
    }

    override suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview> {
        PlanValidation.plan(plan)?.let { return Outcome.Failure(it) }
        return guarded {
            val now = clock.instant()
            val rows = rows(plan, now).first()
            val snapshot = Snapshot(PlanStateAssembler.state(plan, addOns, freeRules, now, rows), PlanStateAssembler.history(plan, now, rows), now)
            PlanPreview(snapshot.state, forecastOf(snapshot))
        }
    }
}
```
`HourlyCatchUp.kt`: change the default to `Duration.ofDays(28)`; keep the KDoc honest ("the platform keeps a few weeks; older windows simply return nothing"). `SamplerService.supervise`: wrap the catch-up call with timing and `Log.i(TAG, "catch-up finished in ${ms} ms")` (use `System.nanoTime()`; the line is what the final proof measures on the first run).

`AppGraph.kt`: replace `val plans: PlanRepository = NoPlanRepository()` with
```kotlin
    private val planStore = PlanStores.create(appContext)
    val plans: PlanRepository = RoomPlanRepository(planStore, store, clock)
```
(`store` is the `UsageStore` already created above it; keep the declaration order so `store` is initialised first; import `PlanStores`, `RoomPlanRepository`, remove the `NoPlanRepository` import.) Also expose `val planStoreForAlerts get() = planStore` ONLY if task 9 needs it (it does: keep `planStore` `internal`, not private, and use `internal val`).

- [ ] **Step 4: Run to verify they pass** — new tests green; `HourlyCatchUpTest` and `AppGraphTest` green (adjust only the expectation tied to the old default, named in your report); full gate green.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "feat(data): serve real plans, state, forecast and previews from stored usage

The plan numbers come from the same rows as Home; a 28-day backfill gives a first forecast without waiting a month."
```

---

### Task 8: SIM list, `READ_PHONE_STATE` and the permission action

**Files:**
- Modify: `core/data/src/main/kotlin/$PKG/data/source/Sources.kt` (add `SimInfoSource`, `SimInfo`), `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/$PKG/nav/PermissionActions.kt`, `app/src/main/kotlin/$PKG/AppGraph.kt`
- Create: `core/data/src/main/kotlin/$PKG/data/android/AndroidSimInfoSource.kt`, `core/data/src/main/kotlin/$PKG/data/fake/FakeSimInfoSource.kt`
- Test: `core/data/src/test/kotlin/$PKG/data/android/AndroidSimInfoSourceTest.kt`, update any test that constructs `PermissionActions`

**Interfaces:**
- Produces: `interface SimInfoSource { fun activeSims(): List<SimInfo>; fun defaultDataSubscriptionId(): Int }`, `data class SimInfo(subscriptionId, slotIndex, displayName)`, `AndroidSimInfoSource(context)`, `FakeSimInfoSource(sims, defaultId)`, `PermissionActions.requestPhoneState`, prompt id `"phone_state"`, `AppGraph.simInfo`.

- [ ] **Step 1: Write the failing test** `AndroidSimInfoSourceTest.kt` (Robolectric, `@Config(sdk = [35])`):
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSimInfoSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = AndroidSimInfoSource(context)

    @Test fun without_the_phone_permission_no_sims_are_listed() {
        assertTrue(source.activeSims().isEmpty())
    }

    @Test fun the_default_data_subscription_needs_no_permission_and_is_minus_one_when_unknown() {
        assertTrue(source.defaultDataSubscriptionId() >= -1)
    }

    @Test fun with_the_permission_the_sims_are_listed_in_slot_order() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val shadow = shadowOf(context.getSystemService(SubscriptionManager::class.java))
        shadow.setActiveSubscriptionInfoList(
            listOf(
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(5).setSimSlotIndex(1).setCarrierName("Beta").buildSubscriptionInfo(),
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(3).setSimSlotIndex(0).setCarrierName("Alpha").buildSubscriptionInfo(),
            ),
        )
        assertEquals(listOf(3, 5), source.activeSims().map { it.subscriptionId })
    }
}
```
(imports: `android.Manifest`, `android.app.Application`, `android.content.Context`, `android.telephony.SubscriptionManager`, `androidx.test.core.app.ApplicationProvider`, `org.robolectric.Shadows.shadowOf`, `org.robolectric.shadows.ShadowSubscriptionManager`, the JUnit/Robolectric runner imports.) If building `SubscriptionInfo` through the shadow is not possible in Robolectric 4.17, keep the first two tests and replace the third by a test of an `internal fun mapSims(...)` that you extract; say so in your report.

- [ ] **Step 2: Run to verify it fails** → FAIL (`AndroidSimInfoSource` does not exist).

- [ ] **Step 3: Implement.**

`Sources.kt` append:
```kotlin
data class SimInfo(val subscriptionId: Int, val slotIndex: Int, val displayName: String)

interface SimInfoSource {
    /** Active SIMs; empty without READ_PHONE_STATE. */
    fun activeSims(): List<SimInfo>
    /** Default data subscription id, -1 when unknown. Needs no permission. */
    fun defaultDataSubscriptionId(): Int
}
```
`AndroidSimInfoSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource

class AndroidSimInfoSource(private val context: Context) : SimInfoSource {
    override fun activeSims(): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
                .map { info ->
                    SimInfo(info.subscriptionId, info.simSlotIndex, info.displayName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" })
                }
                .sortedBy { it.slotIndex }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    override fun defaultDataSubscriptionId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId().let { if (it < 0) -1 else it }
    } catch (_: RuntimeException) {
        -1
    }
}
```
`FakeSimInfoSource.kt` (package `…data.fake`): `class FakeSimInfoSource(var sims: List<SimInfo> = emptyList(), var defaultId: Int = -1) : SimInfoSource { override fun activeSims() = sims; override fun defaultDataSubscriptionId() = defaultId }`.

Manifest: add `<uses-permission android:name="android.permission.READ_PHONE_STATE" />` (optional, requested only from the plan editor).

`PermissionActions.kt`: add a constructor parameter `val requestPhoneState: () -> Unit`, add `"phone_state" -> requestPhoneState()` to `onPrompt`, and in `rememberPermissionActions` add a second `rememberLauncherForActivityResult(RequestPermission())` for `Manifest.permission.READ_PHONE_STATE` with its own `askedPhone` flag; when already asked, open `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the package (same pattern as notifications). Update every test that constructs `PermissionActions(...)`.
`AppGraph.kt`: `val simInfo: SimInfoSource = AndroidSimInfoSource(appContext)`.

- [ ] **Step 4: Run to verify it passes** — full gate green.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "feat(data): list the device's SIMs when the user allows phone state

The plan editor can then bind a plan to a SIM; the default data SIM stays available without any permission."
```

---

### Task 9: `AlertRepository` and `AlertEvaluator`

**Files:**
- Create: `core/data/src/main/kotlin/$PKG/data/alert/{AlertRepository,RoomAlertRepository,AlertEvaluator}.kt`
- Modify: `app/src/main/kotlin/$PKG/AppGraph.kt` (add `alerts`, `alertEvaluator`)
- Test: `core/data/src/test/kotlin/$PKG/data/alert/{RoomAlertRepositoryTest,AlertEvaluatorTest}.kt`

**Interfaces:**
- Consumes: `PlanStore`, `RoomPlanRepository`, `AlertRule`.
- Produces: `interface AlertRepository` (contract section 3), `RoomAlertRepository(store: PlanStore)`, `AlertNotice`, `AlertEvaluator(plans: PlanRepository, store: PlanStore)`.

- [ ] **Step 1: Write the failing tests.**

`RoomAlertRepositoryTest.kt`:
```kotlin
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
```
`AlertEvaluatorTest.kt` (a 10 GB plan, rules 50/80/100, usage added as minute rows):
```kotlin
class AlertEvaluatorTest {
    private val october = Instant.parse("2026-10-15T12:00:00Z")
    private val store = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private fun repo(now: Instant) = RoomPlanRepository(store, usage, Clock.fixed(now, ZoneOffset.UTC), ticks = flowOf(now))
    private fun evaluator(now: Instant = october) = AlertEvaluator(repo(now), store)
    private suspend fun plan(rules: List<Int> = listOf(50, 80, 100), enabled: Boolean = true): Long {
        val id = store.upsertPlan(Plan(0, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None))
        rules.forEach { store.upsertAlertRule(AlertRule(0, id, it, enabled)) }
        return id
    }
    private var minuteCounter = 0L
    private suspend fun use(bytes: Long, day: String = "2026-10-10") =
        usage.addMinute(MinuteTotal(Instant.parse("${day}T10:00:00Z").plusSeconds(60 * minuteCounter++), NetworkKind.MOBILE, 1, bytes, 0))

    @Test fun crossing_a_threshold_fires_once() = runBlocking {
        plan(); use(5_200_000_000L)
        val first = evaluator().evaluate()
        assertEquals(listOf(50), first.map { it.thresholdPercent })
        assertEquals("Main", first.single().planName)
        assertTrue(evaluator().evaluate().isEmpty())
    }
    @Test fun each_later_threshold_fires_when_crossed() = runBlocking {
        plan(); use(5_200_000_000L); evaluator().evaluate()
        use(2_900_000_000L)                                      // 8.1 GB
        assertEquals(listOf(80), evaluator().evaluate().map { it.thresholdPercent })
        use(2_400_000_000L)                                      // 10.5 GB
        val last = evaluator().evaluate().single()
        assertEquals(100, last.thresholdPercent)
        assertEquals(0L, last.remainingBytes)
    }
    @Test fun a_jump_over_several_thresholds_fires_only_the_highest() = runBlocking {
        plan(); use(9_500_000_000L)
        assertEquals(listOf(80), evaluator().evaluate().map { it.thresholdPercent })
        assertTrue(evaluator().evaluate().isEmpty())             // 50 never fires afterwards
    }
    @Test fun the_percentage_is_rounded_down() = runBlocking {
        plan(); use(7_999_000_000L)                              // 79.99 %
        assertEquals(listOf(50), evaluator().evaluate().map { it.thresholdPercent })
    }
    @Test fun disabled_rules_and_plans_without_rules_stay_quiet() = runBlocking {
        plan(enabled = false); plan(rules = emptyList()); use(9_900_000_000L)
        assertTrue(evaluator().evaluate().isEmpty())
    }
    @Test fun a_new_cycle_fires_again() = runBlocking {
        plan(); use(5_200_000_000L); evaluator().evaluate()
        use(5_200_000_000L, day = "2026-11-10")
        val november = Instant.parse("2026-11-15T12:00:00Z")
        assertEquals(listOf(50), evaluator(november).evaluate().map { it.thresholdPercent })
    }
    @Test fun the_notice_carries_the_forecast_and_the_numbers() = runBlocking {
        val id = plan(); use(5_200_000_000L)
        val n = evaluator().evaluate().single()
        assertEquals(5_200_000_000L, n.usedBytes)
        assertEquals(10_000_000_000L, n.effectiveCapBytes)
        assertEquals(4_800_000_000L, n.remainingBytes)
        assertEquals(repo(october).observeForecast(id).first()!!.runOutExpected, n.runOutExpected)   // the same forecast the Plans screen shows
    }
}
```
(imports as in the earlier tests: `Clock`, `Instant`, `LocalTime`, `ZoneOffset`, `flowOf`, `runBlocking`, `NetworkKind`, `MinuteTotal`, `Plan`, `Cycle`, `Rollover`, `AlertRule`, `InMemoryPlanStore`, `InMemoryUsageStore`, `RoomPlanRepository`, JUnit asserts.)

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement.**
```kotlin
// AlertRepository.kt
package io.github.khaledbahaaeldin.emberbyte.data.alert
interface AlertRepository {
    fun observeRules(planId: Long): Flow<List<AlertRule>>
    suspend fun upsertRule(rule: AlertRule): Outcome<Long>
    suspend fun deleteRule(id: Long): Outcome<Unit>
}

// RoomAlertRepository.kt
class RoomAlertRepository(private val store: PlanStore) : AlertRepository {
    override fun observeRules(planId: Long): Flow<List<AlertRule>> = store.observeAlertRules(planId)

    override suspend fun upsertRule(rule: AlertRule): Outcome<Long> {
        if (rule.thresholdPercent !in 1..100) return Outcome.Failure(EmberbyteError.Invalid("thresholdPercent", "Choose 1 to 100"))
        if (store.plan(rule.planId) == null) return Outcome.Failure(EmberbyteError.NotFound)
        if (store.alertRules(rule.planId).any { it.id != rule.id && it.thresholdPercent == rule.thresholdPercent }) {
            return Outcome.Failure(EmberbyteError.Invalid("threshold", "Already set"))
        }
        return try { Outcome.Success(store.upsertAlertRule(rule)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error")) }
    }

    override suspend fun deleteRule(id: Long): Outcome<Unit> = try { store.deleteAlertRule(id); Outcome.Success(Unit) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Outcome.Failure(EmberbyteError.Storage(e.message ?: "Storage error")) }
}

// AlertEvaluator.kt
data class AlertNotice(
    val planId: Long, val planName: String, val thresholdPercent: Int, val usedBytes: Long,
    val effectiveCapBytes: Long, val remainingBytes: Long, val runOutExpected: Instant?,
)

class AlertEvaluator(private val plans: PlanRepository, private val store: PlanStore) {
    /** Contract 10.2: one notice per plan at most (the highest crossed, not yet fired threshold of the current window). */
    suspend fun evaluate(): List<AlertNotice> {
        val notices = ArrayList<AlertNotice>()
        for (state in plans.observeActivePlanStates().first()) {
            val cap = state.effectiveCapBytes
            if (cap <= 0L) continue
            val percent = (state.usedBytes * 100 / cap).coerceAtMost(1_000L).toInt()
            val last = store.lastFiredPercent(state.plan.id, state.window.start) ?: 0
            val top = store.alertRules(state.plan.id)
                .filter { it.enabled && it.thresholdPercent <= percent && it.thresholdPercent > last }
                .maxOfOrNull { it.thresholdPercent } ?: continue
            val forecast = plans.observeForecast(state.plan.id).first()
            notices += AlertNotice(state.plan.id, state.plan.name, top, state.usedBytes, cap, state.remainingBytes, forecast?.runOutExpected)
            store.setFiredPercent(state.plan.id, state.window.start, top)
        }
        return notices
    }
}
```
`AppGraph.kt`: `val alerts: AlertRepository = RoomAlertRepository(planStore)` and `val alertEvaluator = AlertEvaluator(plans, planStore)`.

- [ ] **Step 4: Run to verify they pass** — full gate green.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "feat(data): evaluate plan alerts once per threshold per cycle

Remembering the highest fired threshold per plan window means raising a cap or restarting the app can never repeat a notification."
```


### Task 10: Deep links (`emberbyte://…`)

**Files:**
- Create: `app/src/main/kotlin/$PKG/nav/DeepLinks.kt`
- Modify: `app/src/main/kotlin/$PKG/nav/Routes.kt`, `app/src/main/kotlin/$PKG/EmberbyteApp.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/$PKG/sampler/LiveNotification.kt` (tap opens `emberbyte://home`)
- Test: `app/src/test/kotlin/$PKG/nav/DeepLinksTest.kt`

**Interfaces:**
- Produces: `object DeepLinks { const val SCHEME; const val HOME, APPS, PLANS, LENS, SETTINGS; fun app(packageName): String; fun plan(planId: Long): String; fun intent(context, uri): Intent }`, `object DeepLinkPatterns` (the `{placeholder}` forms used by navigation), `Routes.PLAN_EDITOR = "plans/{planId}"`, `Routes.planEditor(planId: Long): String`. Notifications and widgets use ONLY these (contract section 6).

- [ ] **Step 1: Write the failing tests** `DeepLinksTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinksTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun links_are_built_from_ids_and_package_names() {
        assertEquals("emberbyte://plans/7", DeepLinks.plan(7))
        assertEquals("emberbyte://apps/com.example.app", DeepLinks.app("com.example.app"))
        assertEquals("plans/0", Routes.planEditor(0))
    }

    @Test fun the_explicit_intent_targets_the_main_activity_and_carries_the_uri() {
        val intent = DeepLinks.intent(context, DeepLinks.plan(3))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("emberbyte://plans/3", intent.data.toString())
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun every_documented_link_resolves_to_the_main_activity_through_the_manifest() {
        val uris = listOf(DeepLinks.HOME, DeepLinks.APPS, DeepLinks.app("com.example"), DeepLinks.PLANS, DeepLinks.plan(3), DeepLinks.LENS, DeepLinks.SETTINGS)
        for (uri in uris) {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(context.packageName)
            val resolved = context.packageManager.queryIntentActivities(probe, 0)
            assertTrue("$uri did not resolve", resolved.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }

    @Test fun other_schemes_do_not_resolve() {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/plans/1")).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(probe, 0).none { it.activityInfo.name == MainActivity::class.java.name })
    }
}
```
(imports: `android.app.Application`, `android.content.Intent`, `android.net.Uri`, `androidx.test.core.app.ApplicationProvider`, `MainActivity`, `Routes`, `DeepLinks`, JUnit, Robolectric.)

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*DeepLinksTest"` → FAIL.

- [ ] **Step 3: Implement.**

`DeepLinks.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.khaledbahaaeldin.emberbyte.MainActivity

object DeepLinks {
    const val SCHEME = "emberbyte"
    const val HOME = "emberbyte://home"
    const val APPS = "emberbyte://apps"
    const val PLANS = "emberbyte://plans"
    const val LENS = "emberbyte://lens"
    const val SETTINGS = "emberbyte://settings"

    fun app(packageName: String): String = "emberbyte://apps/" + Uri.encode(packageName)
    fun plan(planId: Long): String = "emberbyte://plans/$planId"

    /** The intent notifications and widgets fire: explicit component, so nothing else can intercept it. */
    fun intent(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The `{placeholder}` forms that `navDeepLink` needs. */
object DeepLinkPatterns {
    const val HOME = DeepLinks.HOME
    const val APPS = DeepLinks.APPS
    const val APP_DETAIL = "emberbyte://apps/{packageName}"
    const val PLANS = DeepLinks.PLANS
    const val PLAN_EDITOR = "emberbyte://plans/{planId}"
    const val LENS = DeepLinks.LENS
    const val SETTINGS = DeepLinks.SETTINGS
}
```
`Routes.kt` add: `const val PLAN_EDITOR = "plans/{planId}"` and `fun planEditor(planId: Long): String = "plans/$planId"`.

`AndroidManifest.xml`: inside `MainActivity` add a second `<intent-filter>`:
```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="emberbyte" android:host="home" />
                <data android:scheme="emberbyte" android:host="apps" />
                <data android:scheme="emberbyte" android:host="plans" />
                <data android:scheme="emberbyte" android:host="lens" />
                <data android:scheme="emberbyte" android:host="settings" />
            </intent-filter>
```
`EmberbyteApp.kt`: add `deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.X })` (import `androidx.navigation.navDeepLink`) to the `composable(...)` of Home, Apps, `Routes.APP_DETAIL` (`APP_DETAIL`), Plans, Lens and `Routes.SETTINGS`; and add, next to the Plans destination, a TEMPORARY editor destination (task 16 replaces it):
```kotlin
            composable(
                Routes.PLAN_EDITOR,
                arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
            ) { PlaceholderScreen("Plan", "The plan editor arrives in task 16 of milestone 3.") }
```
`LiveNotification.kt`: the content intent becomes `PendingIntent.getActivity(context, 0, DeepLinks.intent(context, DeepLinks.HOME), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)` (the existing `LiveNotificationTest` must still pass; add one assertion that the notification's content intent data is `emberbyte://home` using `shadowOf(notification.contentIntent).savedIntent`).

- [ ] **Step 4: Run to verify it passes** — new tests green, `NavigationTest` / `NavigationStabilityTest` still green, full gate green. **Tester (emulator):** `adb shell am start -a android.intent.action.VIEW -d "emberbyte://plans/0" io.github.khaledbahaaeldin.emberbyte` opens the app on the (temporary) editor placeholder with the Plans tab highlighted; `-d "emberbyte://apps"` and `-d "emberbyte://settings"` open those screens; with the app already open the link switches screen instead of opening a second instance (`adb shell dumpsys activity activities | grep -c "MainActivity"` stays 1 task).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): open any screen from an emberbyte:// link

Notifications and widgets can only reach the app through these documented links, so nothing else is exposed."
```

---

### Task 11: Alert delivery (`alerts` channel, `AlertNotifier`, `AlertRunner`)

**Files:**
- Create: `app/src/main/kotlin/$PKG/sampler/{AlertText,AlertNotifier,AlertRunner}.kt`
- Modify: `app/src/main/kotlin/$PKG/sampler/{NotificationChannels,SamplerService,CatchUpWorker}.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/$PKG/sampler/{AlertTextTest,AlertNotifierTest,AlertRunnerTest}.kt`, add one test to `NotificationChannelsTest.kt` (existing tests unchanged)

**Interfaces:**
- Consumes: `AlertNotice`, `AlertEvaluator` (task 9), `DeepLinks` (task 10), `NotificationChannels`, `plainBytes` (internal in `:app`), `ByteUnits`.
- Produces: `NotificationChannels.ALERTS = "alerts"`; `data class AlertText(val title: String, val text: String)`; `fun alertText(notice: AlertNotice, units: ByteUnits, zone: ZoneId, locale: Locale): AlertText`; `object AlertNotifier { const val TAG = "plan-alert"; fun post(context, notice, units) }`; `class AlertRunner(enabled: () -> Boolean, evaluate: suspend () -> List<AlertNotice>, post: (AlertNotice) -> Unit)` with `suspend fun run()` and `companion fun create(context, graph): AlertRunner`.

- [ ] **Step 1: Write the failing tests.**

`AlertTextTest.kt` (`Locale.ENGLISH`, `ZoneOffset.UTC`, `ByteUnits.DECIMAL`):
```kotlin
class AlertTextTest {
    private fun notice(threshold: Int, used: Long, cap: Long = 10_000_000_000L, remaining: Long = maxOf(0, cap - used), runOut: Instant? = null) =
        AlertNotice(7, "Main", threshold, used, cap, remaining, runOut)
    private fun text(n: AlertNotice) = alertText(n, ByteUnits.DECIMAL, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun a_threshold_notice_names_the_plan_and_the_percentage() {
        val t = text(notice(80, 8_100_000_000L))
        assertEquals("Main: 80% of your data used", t.title)
        assertEquals("8.10 GB of 10.0 GB used · 1.90 GB left", t.text)
    }

    @Test fun the_run_out_day_is_added_when_a_forecast_exists() {
        // 2026-10-15 is a Thursday
        val t = text(notice(80, 8_100_000_000L, runOut = Instant.parse("2026-10-15T08:00:00Z")))
        assertEquals("8.10 GB of 10.0 GB used · 1.90 GB left · runs out Thu", t.text)
    }

    @Test fun reaching_the_cap_says_the_data_is_used_up() {
        val t = text(notice(100, 10_500_000_000L, runOut = Instant.parse("2026-10-15T08:00:00Z")))
        assertEquals("Main: all your data is used", t.title)
        assertEquals("10.5 GB of 10.0 GB used", t.text)
    }
}
```
`AlertNotifierTest.kt` (Robolectric): after `AlertNotifier.post(app, notice, ByteUnits.DECIMAL)`, `shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(AlertNotifier.TAG, 7)` is non-null, `extras` title equals `"Main: 80% of your data used"`, the channel id is `NotificationChannels.ALERTS`, and `shadowOf(notification.contentIntent).savedIntent.data.toString() == "emberbyte://plans/7"`.
`AlertRunnerTest.kt` (JVM):
```kotlin
class AlertRunnerTest {
    private val notice = AlertNotice(1, "Main", 80, 8, 10, 2, null)

    @Test fun disabled_notifications_do_not_consume_alerts() = runBlocking {
        var evaluated = 0
        AlertRunner(enabled = { false }, evaluate = { evaluated++; listOf(notice) }, post = { }).run()
        assertEquals(0, evaluated)
    }

    @Test fun every_notice_is_posted_once() = runBlocking {
        val posted = mutableListOf<AlertNotice>()
        AlertRunner(enabled = { true }, evaluate = { listOf(notice, notice.copy(planId = 2)) }, post = { posted += it }).run()
        assertEquals(listOf(1L, 2L), posted.map { it.planId })
    }
}
```
`NotificationChannelsTest` new test: `ensure_creates_the_alerts_channel_with_default_importance` (`IMPORTANCE_DEFAULT`).

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement.**

`strings.xml`: add `<string name="channel_alerts_name">Plan alerts</string>` and `<string name="channel_alerts_description">Tells you when you have used a set share of a data plan</string>`.
`NotificationChannels.kt`: add `const val ALERTS = "alerts"`, create it (`IMPORTANCE_DEFAULT`, name/description from the strings) in `ensure`, and update the KDoc ("`live`, `status` and `alerts` exist; `lens` and `spikes` come with their features").
`AlertText.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class AlertText(val title: String, val text: String)

fun alertText(notice: AlertNotice, units: ByteUnits, zone: ZoneId, locale: Locale): AlertText {
    val used = plainBytes(notice.usedBytes, units)
    val cap = plainBytes(notice.effectiveCapBytes, units)
    if (notice.thresholdPercent >= 100) {
        return AlertText("${notice.planName}: all your data is used", "$used of $cap used")
    }
    val parts = mutableListOf("$used of $cap used", "${plainBytes(notice.remainingBytes, units)} left")
    notice.runOutExpected?.let { parts += "runs out ${it.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}" }
    return AlertText("${notice.planName}: ${notice.thresholdPercent}% of your data used", parts.joinToString(" · "))
}
```
`AlertNotifier.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

object AlertNotifier {
    const val TAG = "plan-alert"

    fun post(context: Context, notice: AlertNotice, units: ByteUnits) {
        NotificationChannels.ensure(context)
        val text = alertText(notice, units, ZoneId.systemDefault(), Locale.getDefault())
        val open = PendingIntent.getActivity(
            context, notice.planId.toInt() + 100, DeepLinks.intent(context, DeepLinks.plan(notice.planId)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TAG, notice.planId.toInt(), notification)
    }
}
```
(`planId.toInt()` is fine: ids are small autoincrement values.) `AlertRunner.kt`:
```kotlin
class AlertRunner(
    private val enabled: () -> Boolean,
    private val evaluate: suspend () -> List<AlertNotice>,
    private val post: (AlertNotice) -> Unit,
) {
    /** Skips when notifications are off, so a threshold is never marked as fired without being seen (contract 10.3). */
    suspend fun run() {
        if (!enabled()) return
        evaluate().forEach(post)
    }

    companion object {
        fun create(context: Context, graph: AppGraph): AlertRunner {
            val app = context.applicationContext
            return AlertRunner(
                enabled = { NotificationManagerCompat.from(app).areNotificationsEnabled() },
                evaluate = { graph.alertEvaluator.evaluate() },
                post = { notice ->
                    val units = (graph.settingsState.value?.unitSystem ?: UnitSystem.DECIMAL).toByteUnits()
                    AlertNotifier.post(app, notice, units)
                },
            )
        }
    }
}
```
(`toByteUnits` is the `internal` helper in `home/HomeMapping.kt`.) `SamplerService.supervise`: add `launch { retrying("alerts") { val runner = AlertRunner.create(this@SamplerService, graph); while (true) { runner.run(); delay(60_000L) } } }`. `CatchUpWorker.doWork`: after `graph.catchUp.run()` and `graph.prune()` add `AlertRunner.create(applicationContext, graph).run()` (wrapped in `runCatching` so an alert failure never fails the catch-up).

- [ ] **Step 4: Run to verify they pass** — full gate green. **Tester (emulator):** create a plan with a 20 MB cap and alert thresholds 50 and 100 through the temporary Debug route is NOT possible yet, so seed it instead: this task's device proof is deferred to task 21 item 5 (alerts end to end). Say so in the report; the tester only verifies that the `alerts` channel appears in `adb shell dumpsys notification | grep -A3 "alerts"` after the app has run once.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): post one plan alert per crossed threshold from the service and the worker

The runner skips while notifications are off, so no threshold is spent on a notification nobody could see."
```

---

### Task 12: `BudgetRing` and `PlanCard` components

**Files:**
- Modify: `ui/design/src/main/kotlin/$PKG/ui/design/model/UiModels.kt`
- Create: `ui/design/src/main/kotlin/$PKG/ui/design/plan/{BudgetRing,PlanCard}.kt`
- Test: `ui/design/src/test/kotlin/$PKG/ui/design/plan/{BudgetRingTest,PlanCardTest}.kt`

**Interfaces:**
- Produces (all primitives, no `:core:*`):
```kotlin
enum class BudgetStateUi { Ok, Warning, Exceeded }
enum class ForecastKind { RunsOut, Safe, Out, ProbablySafe }                      // added to ForecastUi with a default
data class ForecastUi(val headline: String, val detail: String, val confidence: String, val kind: ForecastKind = ForecastKind.RunsOut)
data class PlanChipUi(val id: Long, val name: String, val selected: Boolean)
data class PlanCardUi(
    val id: Long, val name: String, val simLabel: String?, val fraction: Float, val ringState: BudgetStateUi,
    val percentText: String, val usedText: String, val remainingText: String, val daysLeftText: String,
    val forecastText: String?, val isEstimated: Boolean, val chips: List<String>, val archived: Boolean,
)
@Composable fun BudgetRing(fraction: Float, state: BudgetStateUi, modifier: Modifier = Modifier, size: Dp = 72.dp)
@Composable fun PlanCard(model: PlanCardUi, onClick: () -> Unit, modifier: Modifier = Modifier)
```

- [ ] **Step 1: Write the failing tests.**

`BudgetRingTest.kt` (Robolectric Compose, dark static theme like the other component tests; use `onNodeWithContentDescription`):
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BudgetRingTest {
    @get:Rule val rule = createComposeRule()
    private fun show(fraction: Float, state: BudgetStateUi) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { BudgetRing(fraction, state) }
    }

    @Test fun an_ok_ring_describes_the_percentage() {
        show(0.62f, BudgetStateUi.Ok)
        rule.onNodeWithContentDescription("62 percent of the plan used").assertIsDisplayed()
        rule.onNodeWithText("62%").assertIsDisplayed()
    }

    @Test fun a_warning_ring_adds_words_not_just_colour() {
        show(0.85f, BudgetStateUi.Warning)
        rule.onNodeWithContentDescription("85 percent of the plan used, nearly used up").assertIsDisplayed()
    }

    @Test fun an_exceeded_ring_may_show_more_than_100_percent() {
        show(1.12f, BudgetStateUi.Exceeded)
        rule.onNodeWithContentDescription("112 percent of the plan used, over the cap").assertIsDisplayed()
        rule.onNodeWithText("112%").assertIsDisplayed()
    }

    @Test fun a_negative_or_nan_fraction_is_drawn_as_zero() {
        show(Float.NaN, BudgetStateUi.Ok)
        rule.onNodeWithText("0%").assertIsDisplayed()
    }
}
```
`PlanCardTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanCardTest {
    @get:Rule val rule = createComposeRule()
    private val model = PlanCardUi(
        id = 3, name = "Main SIM", simLabel = "SIM 1 · Alpha", fraction = 0.62f, ringState = BudgetStateUi.Ok,
        percentText = "62%", usedText = "6.20 GB of 10.0 GB", remainingText = "3.80 GB left", daysLeftText = "9 days left",
        forecastText = "runs out Thu (±1 day)", isEstimated = false, chips = listOf("Boost +2.00 GB"), archived = false,
    )
    private fun show(m: PlanCardUi = model, onClick: () -> Unit = {}) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { PlanCard(m, onClick) }
    }

    @Test fun shows_the_numbers_the_sim_and_the_chips() {
        show()
        listOf("Main SIM", "SIM 1 · Alpha", "6.20 GB of 10.0 GB", "3.80 GB left", "9 days left", "runs out Thu (±1 day)", "Boost +2.00 GB")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun the_estimated_badge_only_appears_for_estimated_plans() {
        show(model.copy(isEstimated = true))
        rule.onNodeWithText("Estimated").assertIsDisplayed()
    }

    @Test fun an_exact_plan_has_no_badge() {
        show()
        rule.onNodeWithText("Estimated").assertDoesNotExist()
    }

    @Test fun missing_optional_parts_are_simply_left_out() {
        show(model.copy(simLabel = null, forecastText = null, chips = emptyList()))
        rule.onNodeWithText("Main SIM").assertIsDisplayed()
    }

    @Test fun tapping_the_card_reports_a_click_and_the_card_has_one_spoken_summary() {
        var clicked = 0
        show(onClick = { clicked++ })
        rule.onNodeWithText("Main SIM").performClick()
        assertEquals(1, clicked)
        rule.onNodeWithContentDescription("Main SIM, 62 percent used, 3.80 GB left, 9 days left").assertExists()
    }

    @Test fun an_archived_plan_is_labelled_archived() {
        show(model.copy(archived = true))
        rule.onNodeWithText("Archived").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :ui:design:testDebugUnitTest --tests "*BudgetRingTest" --tests "*PlanCardTest"` → FAIL.

- [ ] **Step 3: Implement.**

`UiModels.kt`: replace the `ForecastUi` line with the version in **Interfaces** (the new `kind` has a default, so existing call sites compile; existing tests that compare a `ForecastUi` for the "never runs out" case must pass `kind = ForecastKind.Safe` once task 13 sets it; task 13 names them) and append `BudgetStateUi`, `ForecastKind`, `PlanChipUi`, `PlanCardUi`.

`BudgetRing.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetRing(fraction: Float, state: BudgetStateUi, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val safe = if (fraction.isNaN() || fraction < 0f) 0f else fraction
    val percent = (safe * 100f).toInt()
    val reduceMotion = LocalReduceMotion.current
    val sweep by animateFloatAsState(
        targetValue = minOf(safe, 1f),
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ring",
    )
    val scheme = MaterialTheme.colorScheme
    val color = when (state) {
        BudgetStateUi.Ok -> scheme.primary
        BudgetStateUi.Warning -> scheme.tertiary
        BudgetStateUi.Exceeded -> scheme.error
    }
    val track = scheme.surfaceContainerHighest
    val description = buildString {
        append("$percent percent of the plan used")
        when (state) {
            BudgetStateUi.Warning -> append(", nearly used up")
            BudgetStateUi.Exceeded -> append(", over the cap")
            BudgetStateUi.Ok -> Unit
        }
    }
    Box(
        modifier.size(size).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = this.size.minDimension * 0.12f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
        }
        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
        // A state is never colour only: warning and exceeded also show an icon in the corner.
        if (state != BudgetStateUi.Ok) {
            Icon(
                imageVector = if (state == BudgetStateUi.Exceeded) Icons.Rounded.Error else Icons.Rounded.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.TopEnd).size(size / 4),
            )
        }
    }
}
```
(Imports: `androidx.compose.animation.core.animateFloatAsState`/`snap`, `androidx.compose.foundation.Canvas`, layout and graphics classes, `androidx.compose.material.icons.rounded.Error/Warning`, `LocalReduceMotion` from the theme package. The inner `Icon` has no description because the ring's merged description already says it in words.)

`PlanCard.kt`:
```kotlin
@Composable
fun PlanCard(model: PlanCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = "${model.name}, ${model.percentText.removeSuffix("%")} percent used, ${model.remainingText}, ${model.daysLeftText}"
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(model.fraction, model.ringState)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium)
                model.simLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ForceLtr { Text(model.usedText, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.remainingText, style = MaterialTheme.typography.bodyMedium)
                    Text("·", style = MaterialTheme.typography.bodyMedium)
                    Text(model.daysLeftText, style = MaterialTheme.typography.bodyMedium)
                }
                model.forecastText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
                if (model.isEstimated) EstimatedBadge()
                if (model.archived) Text("Archived", style = MaterialTheme.typography.labelMedium)
                if (model.chips.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.chips.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }
        }
    }
}
```
(`FlowRow` needs `@OptIn(ExperimentalLayoutApi::class)`; `EstimatedBadge` and `ForceLtr` already exist in this module. The remaining data and the days left are two separate `Text` nodes so the tests can find each exactly.)

- [ ] **Step 4: Run to verify they pass** — new tests green, full gate green. **Tester (emulator):** add a temporary `@Preview`-free check is not possible; the real device proof of these components is task 14.

- [ ] **Step 5: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add the cap ring and plan card components

The ring says the same thing in words and an icon as it does in colour, and shows more than 100% when a plan is overspent."
```

---

### Task 13: Forecast sentences and Home integration (plan chips, set-up tile)

**Files:**
- Create: `app/src/main/kotlin/$PKG/common/ForecastText.kt`
- Modify: `app/src/main/kotlin/$PKG/home/{HomeMapping,HomeUiState,HomeViewModel,HomeScreen}.kt`, `app/src/main/kotlin/$PKG/EmberbyteApp.kt`, `app/src/main/kotlin/$PKG/AppGraph.kt` (Home factory unchanged unless the constructor changes)
- Test: new `app/src/test/kotlin/$PKG/home/HomeMappingM3Test.kt`, `HomeViewModelM3Test.kt`; modify the existing tests named below

**Interfaces:**
- Consumes: `ForecastKind`, `PlanChipUi` (task 12), `forecastToUi`, `PlanRepository`.
- Produces: `internal fun forecastSentence(ui: ForecastUi): String`; `HomeUiState.planChips: List<PlanChipUi>` (only filled when there are two or more plans); `HomeEvent.SelectPlan(planId: Long)`; `HomeContent`/`HomeScreen` get a REQUIRED `onSetUpPlan: () -> Unit` placed before `modifier`.

- [ ] **Step 1: Write the failing tests.**

`HomeMappingM3Test.kt` (use `Locale.ENGLISH`, `ZoneOffset.UTC`, `now = 2026-10-10T12:00Z`; `2026-10-12` is a Monday):
```kotlin
class HomeMappingM3Test {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun forecast(early: String? = null, exp: String? = null, late: String? = null) =
        Forecast(1, early?.let(Instant::parse), exp?.let(Instant::parse), late?.let(Instant::parse), 0, Confidence.MEDIUM, 14)
    private fun ui(f: Forecast) = forecastToUi(f, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun a_plan_that_lasts_is_safe() {
        val u = ui(forecast())
        assertEquals(ForecastKind.Safe, u.kind)
        assertEquals("Won't run out this cycle", forecastSentence(u))
    }

    @Test fun a_plan_that_is_already_used_up_says_so() {
        val u = ui(forecast("2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z"))
        assertEquals(ForecastKind.Out, u.kind)
        assertEquals("Out", u.headline)
        assertEquals("Data used up", forecastSentence(u))
    }

    @Test fun an_expected_miss_with_an_early_scenario_is_probably_safe() {
        val u = ui(forecast(early = "2026-10-12T08:00:00Z"))
        assertEquals(ForecastKind.ProbablySafe, u.kind)
        assertEquals("Probably safe", u.headline)
        assertEquals("Could run out as early as Mon", forecastSentence(u))
    }

    @Test fun a_normal_run_out_reads_like_before() {
        val u = ui(forecast("2026-10-11T12:00:00Z", "2026-10-12T12:00:00Z", "2026-10-13T12:00:00Z"))
        assertEquals(ForecastKind.RunsOut, u.kind)
        assertEquals("runs out Mon (±1 day)", forecastSentence(u))
    }

    @Test fun the_home_subtitle_uses_the_same_sentences() {
        // Safe: "3.80 GB left · won't run out this cycle" (unchanged text); Out: "0 B left · data used up"
        // build with buildHomeUiState(...) exactly like HomeMappingTest does and assert both strings.
    }
}
```
(Complete the last test: copy the `buildHomeUiState` call from `HomeMappingTest`, once with the Safe forecast and `remainingBytes = 3_800_000_000L`, once with the Out forecast and `remainingBytes = 0L`, and assert `subtitle == "3.80 GB left · won't run out this cycle"` and `"0 B left · data used up"`.)

`HomeViewModelM3Test.kt`: using the same set-up as `HomeViewModelBehaviourTest` (read it first and copy its dispatcher and `dates = flowOf(date)` pattern), with a `FakePlanRepository` plus one more plan added through `upsertPlan`: assert (a) with one plan `uiState.planChips` is empty; (b) with two plans it has two chips, the first selected; (c) after `onEvent(HomeEvent.SelectPlan(2L))` the second chip is selected and the first is not; (d) selecting an unknown id falls back to the first plan; (e) with no plan `hasPlan` is false and `planChips` empty.

Existing tests to adjust (and only these): every assertion in `HomeMappingTest`, `HomeMappingEdgeTest` and `HomeMappingM2Test` that compares a whole `ForecastUi` for the never-runs-out case must now expect `kind = ForecastKind.Safe`; `HomeContentTest` constructs `HomeContent(...)` and must pass `onSetUpPlan = {}`. List each changed test in your report.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests "*HomeMappingM3Test" --tests "*HomeViewModelM3Test"` → FAIL.

- [ ] **Step 3: Implement.**

`common/ForecastText.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastKind
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi

/** One sentence for a forecast, used by Home, the plan card and the widgets. */
internal fun forecastSentence(ui: ForecastUi): String = when (ui.kind) {
    ForecastKind.Safe, ForecastKind.Out, ForecastKind.ProbablySafe -> ui.detail
    ForecastKind.RunsOut -> ui.detail.replace("runs out", "runs out ${ui.headline}")
}
```
`HomeMapping.kt` `forecastToUi(forecast, now, zone, locale)` (non-null overload), replace the beginning with:
```kotlin
    if (forecast.neverRunsOut()) {
        return ForecastUi("Safe", "Won't run out this cycle", confidenceLabel(forecast.confidence), ForecastKind.Safe)
    }
    val expected = forecast.runOutExpected
    if (expected != null && expected <= now) {
        return ForecastUi("Out", "Data used up", confidenceLabel(forecast.confidence), ForecastKind.Out)
    }
    if (expected == null && forecast.runOutEarliest != null) {
        val early = forecast.runOutEarliest!!
        val day = if (Duration.between(now, early) > Duration.ofDays(6)) {
            DateTimeFormatter.ofPattern("EEE d MMM", locale).format(early.atZone(zone))
        } else {
            early.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
        return ForecastUi("Probably safe", "Could run out as early as $day", confidenceLabel(forecast.confidence), ForecastKind.ProbablySafe)
    }
```
and keep the rest (the `RunsOut` path, `kind` default). In `buildHomeUiState` replace the subtitle `when` by:
```kotlin
        subtitle = planState?.let {
            val left = "${plainBytes(it.remainingBytes, byteUnits)} left"
            if (forecastUi == null) left
            else "$left · ${forecastSentence(forecastUi).replaceFirstChar { c -> c.lowercase() }}"
        }
```
(the existing expected strings stay the same: "won't run out this cycle", "runs out Thu (±1 day)".) Add the parameter `planChips: List<PlanChipUi> = emptyList()` to `buildHomeUiState` and set `planChips = if (planChips.size >= 2) planChips else emptyList()` in the result; add `val planChips: List<PlanChipUi> = emptyList()` to `HomeUiState`; add `data class SelectPlan(val planId: Long) : HomeEvent` to `HomeEvent`.

`HomeViewModel.kt`: add `private val selectedPlanId = MutableStateFlow<Long?>(null)`; replace `planAndForecast` by
```kotlin
    private data class PlanBundle(val state: PlanState?, val forecast: Forecast?, val chips: List<PlanChipUi>)

    private val planBundle: Flow<PlanBundle> =
        combine(plans.observeActivePlanStates(), selectedPlanId) { states, selected ->
            val chosen = states.firstOrNull { it.plan.id == selected } ?: states.firstOrNull()
            chosen to states.map { PlanChipUi(it.plan.id, it.plan.name, it.plan.id == chosen?.plan?.id) }
        }.flatMapLatest { (chosen, chips) ->
            if (chosen == null) flowOf(PlanBundle(null, null, chips))
            else plans.observeForecast(chosen.plan.id).map { PlanBundle(chosen, it, chips) }
        }
```
adapt `HomeData` (`planState`, `forecast`, new `planChips`) and the `combine` that builds it, pass `planChips = b.data.planChips` into `buildHomeUiState`, and handle `HomeEvent.SelectPlan -> selectedPlanId.value = event.planId`.

`HomeScreen.kt`: add `onSetUpPlan: () -> Unit` (required, before `modifier`) to `HomeScreen` and `HomeContent`; render, when `state.planChips.size >= 2`, a horizontally scrollable `Row` of `FilterChip`s (selected state, label = plan name, `onClick = { onEvent(HomeEvent.SelectPlan(chip.id)) }`) above the tiles; replace the "Data plan" tile (when `!state.hasPlan`) by
```kotlin
BentoTile(title = "Data plan", modifier = Modifier.fillMaxWidth(), onClick = onSetUpPlan) {
    Text("Set up your data plan to see what is left and when it runs out.", style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onSetUpPlan) { Text("Set up your data plan") }
}
```
`EmberbyteApp.kt`: pass `onSetUpPlan = { navController.navigate(Routes.planEditor(0)) }` to `HomeScreen`.

- [ ] **Step 4: Run to verify they pass** — full gate green. **Tester (emulator):** on a clean install with no plan Home shows the "Set up your data plan" tile and tapping it opens the (still placeholder) editor route; no crash; the navigation stress test of M2 (10 taps per tab) still shows 0 bounces.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): let Home pick a plan and say honestly when data is used up

One sentence function feeds Home, the plan card and the widgets, so the forecast can never read differently on two surfaces."
```

---

### Task 14: Plans screen (list of plan cards)

**Files:**
- Create: `app/src/main/kotlin/$PKG/plans/{PlansUiState,PlansMapping,PlansViewModel,PlansScreen}.kt`
- Modify: `app/src/main/kotlin/$PKG/AppGraph.kt` (`plansViewModelFactory`), `app/src/main/kotlin/$PKG/EmberbyteApp.kt` (replace the Plans placeholder)
- Test: `app/src/test/kotlin/$PKG/plans/{PlansMappingTest,PlansViewModelTest,PlansContentTest}.kt`

**Interfaces:**
- Consumes: `PlanCardUi`, `BudgetStateUi`, `PlanCard` (task 12), `forecastToUi`, `forecastSentence` (task 13), `PlanRepository`, `SimInfoSource` (task 8), `plainBytes`.
- Produces:
```kotlin
data class PlansUiState(val cards: List<PlanCardUi> = emptyList(), val archivedCards: List<PlanCardUi> = emptyList(),
                        val showArchived: Boolean = false, val loaded: Boolean = false)
sealed interface PlansEvent { data object ToggleArchived : PlansEvent }
internal fun planCardUi(state: PlanState, forecast: Forecast?, addOns: List<AddOn>, simLabel: String?,
                        units: ByteUnits, now: Instant, zone: ZoneId, locale: Locale): PlanCardUi
class PlansViewModel(plans: PlanRepository, settings: SettingsRepository, sims: SimInfoSource,
                     clock: Clock = SystemZoneClock(), locale: Locale = Locale.getDefault()) : ViewModel()
@Composable fun PlansScreen(viewModel: PlansViewModel, onOpenPlan: (Long) -> Unit, onNewPlan: () -> Unit, modifier: Modifier = Modifier)
@Composable internal fun PlansContent(state: PlansUiState, onEvent: (PlansEvent) -> Unit, onOpenPlan: (Long) -> Unit, onNewPlan: () -> Unit, modifier: Modifier = Modifier)
```

- [ ] **Step 1: Write the failing tests.**

`PlansMappingTest.kt` (fixed `now = 2026-10-10T12:00Z`, UTC, `Locale.ENGLISH`, DECIMAL; build a `PlanState` by hand: cap 10 GB, used 6.2 GB, remaining 3.8 GB, `daysLeft = 9`):
```kotlin
class PlansMappingTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val plan = Plan(3, "Main SIM", 5, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
    private fun state(used: Long, cap: Long = 10_000_000_000L, days: Int = 9, approx: Boolean = false) = PlanState(
        plan, CycleWindow(now.minusSeconds(86_400 * 9), now.plusSeconds(86_400 * 9)), cap, used, maxOf(0, cap - used), 0, 0, 0, days,
        used.toFloat() / cap, approx,
    )
    private fun card(s: PlanState, f: Forecast? = null, addOns: List<AddOn> = emptyList(), sim: String? = "SIM 1 · Alpha") =
        planCardUi(s, f, addOns, sim, ByteUnits.DECIMAL, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun the_texts_follow_the_state() {
        val c = card(state(6_200_000_000L))
        assertEquals("Main SIM", c.name)
        assertEquals("62%", c.percentText)
        assertEquals("6.20 GB of 10.0 GB", c.usedText)
        assertEquals("3.80 GB left", c.remainingText)
        assertEquals("9 days left", c.daysLeftText)
        assertEquals(0.62f, c.fraction, 1e-6f)
        assertEquals(BudgetStateUi.Ok, c.ringState)
        assertEquals("SIM 1 · Alpha", c.simLabel)
    }

    @Test fun the_ring_state_warns_from_80_percent_and_is_exceeded_at_100() {
        assertEquals(BudgetStateUi.Ok, card(state(7_900_000_000L)).ringState)
        assertEquals(BudgetStateUi.Warning, card(state(8_000_000_000L)).ringState)
        assertEquals(BudgetStateUi.Warning, card(state(9_900_000_000L)).ringState)
        assertEquals(BudgetStateUi.Exceeded, card(state(10_000_000_000L)).ringState)
        assertEquals("112%", card(state(11_200_000_000L)).percentText)
    }

    @Test fun nothing_left_and_day_wording() {
        assertEquals("No data left", card(state(10_500_000_000L)).remainingText)
        assertEquals("1 day left", card(state(1, days = 1)).daysLeftText)
        assertEquals("Renews today", card(state(1, days = 0)).daysLeftText)
    }

    @Test fun the_forecast_becomes_one_sentence_and_may_be_absent() {
        val f = Forecast(3, now.plusSeconds(86_400), now.plusSeconds(2 * 86_400), now.plusSeconds(3 * 86_400), 0, Confidence.MEDIUM, 14)
        assertEquals("runs out Mon (±1 day)", card(state(6_200_000_000L), f).forecastText)     // 12 Oct 2026 is a Monday
        assertNull(card(state(6_200_000_000L), null).forecastText)
    }

    @Test fun add_ons_become_chips_and_estimates_are_flagged() {
        val boost = AddOn(1, 3, "Boost", 2_000_000_000L, now, now.plusSeconds(86_400))
        val c = card(state(1, approx = true), addOns = listOf(boost))
        assertEquals(listOf("Boost +2.00 GB"), c.chips)
        assertTrue(c.isEstimated)
        assertFalse(c.archived)
    }
}
```
`PlansViewModelTest.kt`: use `RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(now))` and a `FakeSettingsRepository`; cases: (a) before any plan `loaded` becomes true with empty lists; (b) after `upsertPlan` the card list has the plan with `usedText` derived from a seeded minute row; (c) an archived plan moves to `archivedCards` and is hidden until `ToggleArchived`; (d) `simLabel` is `"SIM 1 · Alpha"` when `FakeSimInfoSource` lists subscription 5 in slot 0 and `null` for an unbound plan; for a bound plan whose SIM is not listed it is `"SIM (not found)"`; (e) units from settings: with `UnitSystem.BINARY` the cap text uses GiB. Copy the dispatcher set-up from `HomeViewModelBehaviourTest`.
`PlansContentTest.kt` (Robolectric Compose): cards render; tapping a card calls `onOpenPlan(id)`; "New plan" calls `onNewPlan`; with `showArchived = false` and `archivedCards` non-empty a "Show archived (1)" button exists and sends `PlansEvent.ToggleArchived`; with no cards and `loaded = true` the empty-state text `"No plans yet"` and a "Set up your first plan" button (calls `onNewPlan`) show; with `loaded = false` nothing flashes the empty state.

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement.**

`PlansMapping.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.plans

internal fun planCardUi(
    state: PlanState, forecast: Forecast?, addOns: List<AddOn>, simLabel: String?,
    units: ByteUnits, now: Instant, zone: ZoneId, locale: Locale,
): PlanCardUi {
    val percent = (state.fractionUsed * 100f).toInt().coerceAtLeast(0)
    return PlanCardUi(
        id = state.plan.id,
        name = state.plan.name,
        simLabel = simLabel,
        fraction = state.fractionUsed,
        ringState = when {
            state.fractionUsed >= 1f -> BudgetStateUi.Exceeded
            state.fractionUsed >= 0.8f -> BudgetStateUi.Warning
            else -> BudgetStateUi.Ok
        },
        percentText = "$percent%",
        usedText = "${plainBytes(state.usedBytes, units)} of ${plainBytes(state.effectiveCapBytes, units)}",
        remainingText = if (state.remainingBytes == 0L) "No data left" else "${plainBytes(state.remainingBytes, units)} left",
        daysLeftText = when (state.daysLeft) { 0 -> "Renews today"; 1 -> "1 day left"; else -> "${state.daysLeft} days left" },
        forecastText = forecast?.let { forecastSentence(forecastToUi(it, now, zone, locale)) },
        isEstimated = state.isApproximate,
        chips = addOns.filter { it.validFrom < state.window.end && it.validUntil > state.window.start }
            .map { "${it.label} +${plainBytes(it.bytes, units)}" },
        archived = state.plan.archived,
    )
}
```
(`forecastToUi` is `internal` in `home/HomeMapping.kt`; import it.)

`PlansViewModel.kt`:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModel(
    private val plans: PlanRepository,
    settings: SettingsRepository,
    private val sims: SimInfoSource,
    private val clock: Clock = SystemZoneClock(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    private data class Entry(val plan: Plan, val state: PlanState?, val forecast: Forecast?, val addOns: List<AddOn>)

    private val entries: Flow<List<Entry>> = plans.observePlans(includeArchived = true).flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { plan ->
            combine(plans.observePlanState(plan.id), plans.observeForecast(plan.id), plans.observeAddOns(plan.id)) { s, f, a -> Entry(plan, s, f, a) }
        }) { it.toList() }
    }

    val uiState: StateFlow<PlansUiState> = combine(entries, settings.observe(), showArchived) { list, s, archivedShown ->
        val units = s.unitSystem.toByteUnits()
        val installed = sims.activeSims()
        val now = clock.instant()
        val cards = list.mapNotNull { e ->
            val state = e.state ?: return@mapNotNull null
            planCardUi(state, e.forecast, e.addOns, simLabel(e.plan.subscriptionId, installed), units, now, clock.zone, locale)
        }
        PlansUiState(cards.filter { !it.archived }, cards.filter { it.archived }, archivedShown, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun onEvent(event: PlansEvent) { when (event) { PlansEvent.ToggleArchived -> showArchived.update { !it } } }

    private fun simLabel(subscriptionId: Int?, installed: List<SimInfo>): String? = when {
        subscriptionId == null -> null
        else -> installed.firstOrNull { it.subscriptionId == subscriptionId }
            ?.let { "SIM ${it.slotIndex + 1} · ${it.displayName}" } ?: "SIM (not found)"
    }
}
```
`PlansScreen.kt`: `PlansScreen` collects `uiState` with `collectAsStateWithLifecycle` and calls `PlansContent`. `PlansContent`: `LazyColumn` with `contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp)` (room for the floating navbar, like the other screens), first item a header row (`Text("Plans", headlineMedium)` + `FilledTonalButton(onClick = onNewPlan) { Text("New plan") }`), then, when `loaded && cards.isEmpty() && archivedCards.isEmpty()`, a `BentoTile("No plans yet")` with text "Add your cap and renewal day to see what is left and when it runs out." and `Button("Set up your first plan")`; otherwise `items(state.cards, key = { it.id })` → `PlanCard(card, { onOpenPlan(card.id) })`; then when `archivedCards.isNotEmpty()` a `TextButton` ("Show archived (n)" / "Hide archived") that sends `ToggleArchived` and, when `showArchived`, the archived cards.
`AppGraph.kt`: `fun plansViewModelFactory() = viewModelFactory { initializer { PlansViewModel(plans, settings, simInfo, clock) } }` (the graph's `clock` is a constructor parameter; make it a `private val clock`). `EmberbyteApp.kt`: replace the Plans placeholder with
```kotlin
            composable(Destination.Plans.route, deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLANS })) {
                PlansScreen(
                    viewModel(factory = graph.plansViewModelFactory()),
                    onOpenPlan = { navController.navigate(Routes.planEditor(it)) },
                    onNewPlan = { navController.navigate(Routes.planEditor(0)) },
                )
            }
```
(keep the deep link added in task 10).

- [ ] **Step 4: Run to verify they pass** — new tests green, `NavigationTest` and `NavigationStabilityTest` still green, full gate green. **Tester (emulator, needed because this is UI and navigation):** clean install; the Plans tab shows "No plans yet"; 10 taps on each tab produce 0 bounces; the empty-state button opens the editor placeholder; dark theme and AMOLED render the tile text legibly; font scale 2.0 shows one readable column.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): show the user's plans as cards with ring, remaining data and forecast

Archived plans stay out of the way behind a toggle, and an estimate is always labelled."
```


### Task 15: Plan editor state (`PlanEditorViewModel`, form model, parsing, save)

**Files:**
- Create: `app/src/main/kotlin/$PKG/plans/{PlanEditorForm,PlanEditorMapping,PlanEditorViewModel}.kt`
- Modify: `app/src/main/kotlin/$PKG/AppGraph.kt` (`planEditorViewModelFactory(planId)`)
- Test: `app/src/test/kotlin/$PKG/plans/{PlanEditorMappingTest,PlanEditorViewModelTest}.kt`

**Interfaces:**
- Consumes: `PlanRepository` (incl. `previewState`), `AlertRepository`, `SettingsRepository`, `PermissionRepository`, `SimInfoSource`, `planCardUi` (task 14), `PlanCardUi`.
- Produces (all in package `…plans`):
```kotlin
enum class CapUnit(val label: String, val power: Int) { MB("MB", 2), GB("GB", 3), TB("TB", 4) }
enum class CycleKind { Monthly, EveryNDays }
enum class RolloverKind { None, Full, Capped }
data class AddOnDraft(val id: Long = 0, val label: String = "", val amountText: String = "", val unit: CapUnit = CapUnit.GB,
                      val validFrom: LocalDate, val validUntil: LocalDate)
data class FreeRuleDraft(val id: Long = 0, val label: String = "", val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
                         val start: LocalTime = LocalTime.MIDNIGHT, val end: LocalTime = LocalTime.of(6, 0), val packageName: String? = null)
data class PlanEditorForm(
    val name: String = "", val subscriptionId: Int? = null,
    val capText: String = "", val capUnit: CapUnit = CapUnit.GB,
    val cycleKind: CycleKind = CycleKind.Monthly, val dayOfMonth: Int = 1, val everyDays: Int = 30, val anchor: LocalDate = LocalDate.of(2026, 1, 1),
    val renewalTime: LocalTime = LocalTime.MIDNIGHT,
    val rolloverKind: RolloverKind = RolloverKind.None, val rolloverCapText: String = "", val rolloverCapUnit: CapUnit = CapUnit.GB,
    val addOns: List<AddOnDraft> = emptyList(), val freeRules: List<FreeRuleDraft> = emptyList(),
    val alertThresholds: Set<Int> = setOf(80, 100),
)
data class SimOptionUi(val subscriptionId: Int?, val label: String)
data class PlanEditorUiState(
    val form: PlanEditorForm = PlanEditorForm(), val planId: Long = 0, val isNew: Boolean = true,
    val loading: Boolean = true, val notFound: Boolean = false,
    val sims: List<SimOptionUi> = emptyList(), val needsPhonePermission: Boolean = false,
    val errors: Map<String, String> = emptyMap(), val preview: PlanCardUi? = null,
    val saving: Boolean = false, val saved: Boolean = false, val unitSystem: UnitSystem = UnitSystem.DECIMAL,
)
sealed interface PlanEditorEvent {
    data class SetName(val value: String) : PlanEditorEvent
    data class SetSim(val subscriptionId: Int?) : PlanEditorEvent
    data class SetCap(val text: String) : PlanEditorEvent
    data class SetCapUnit(val unit: CapUnit) : PlanEditorEvent
    data class SetCycleKind(val kind: CycleKind) : PlanEditorEvent
    data class SetDayOfMonth(val day: Int) : PlanEditorEvent
    data class SetEveryDays(val days: Int) : PlanEditorEvent
    data class SetAnchor(val date: LocalDate) : PlanEditorEvent
    data class SetRenewalTime(val time: LocalTime) : PlanEditorEvent
    data class SetRolloverKind(val kind: RolloverKind) : PlanEditorEvent
    data class SetRolloverCap(val text: String) : PlanEditorEvent
    data class SetRolloverUnit(val unit: CapUnit) : PlanEditorEvent
    data object AddAddOn : PlanEditorEvent
    data class UpdateAddOn(val index: Int, val draft: AddOnDraft) : PlanEditorEvent
    data class RemoveAddOn(val index: Int) : PlanEditorEvent
    data object AddFreeRule : PlanEditorEvent
    data class UpdateFreeRule(val index: Int, val draft: FreeRuleDraft) : PlanEditorEvent
    data class RemoveFreeRule(val index: Int) : PlanEditorEvent
    data class ToggleThreshold(val percent: Int) : PlanEditorEvent
    data object Save : PlanEditorEvent
    data object Archive : PlanEditorEvent
}
internal fun bytesOf(text: String, unit: CapUnit, system: UnitSystem): Long?
internal fun textAndUnitOf(bytes: Long, system: UnitSystem): Pair<String, CapUnit> {
    val base = if (system == UnitSystem.BINARY) 1024L else 1000L
    fun scale(unit: CapUnit): Long = BigDecimal.valueOf(base).pow(unit.power).toLong()
    // The largest unit in which the value is at least 1 and exact to three decimals; otherwise MB with up to 8 decimals,
    // which reads back to the same number of bytes (a saved cap must never drift when it is loaded and saved again).
    val unit = CapUnit.entries.sortedByDescending { it.power }.firstOrNull { bytes >= scale(it) && (bytes * 1000) % scale(it) == 0L }
    if (unit != null) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(scale(unit)), 3, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString() to unit
    }
    return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(scale(CapUnit.MB)), 8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() to CapUnit.MB
}
```
Error keys of `errors` are the contract field names (`name`, `capBytes`, `cycle.dayOfMonth`, `cycle.days`, `rollover.maxBytes`, `subscriptionId`) and, for list items, `addOn.<index>.<field>` / `freeRule.<index>.<field>` (`<field>` from the contract: `label`, `bytes`, `validUntil`, `days`, `time`).

- [ ] **Step 1: Write the failing tests.**

`PlanEditorMappingTest.kt` (pure):
```kotlin
class PlanEditorMappingTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val goodForm = PlanEditorForm(name = "Main", capText = "10", capUnit = CapUnit.GB)

    @Test fun text_converts_to_bytes_in_the_chosen_unit_system() {
        assertEquals(10_000_000_000L, bytesOf("10", CapUnit.GB, UnitSystem.DECIMAL))
        assertEquals(10L * 1024 * 1024 * 1024, bytesOf("10", CapUnit.GB, UnitSystem.BINARY))
        assertEquals(1_500_000_000L, bytesOf("1,5", CapUnit.GB, UnitSystem.DECIMAL))      // decimal comma
        assertEquals(500_000_000L, bytesOf("500", CapUnit.MB, UnitSystem.DECIMAL))
        assertEquals(2_000_000_000_000L, bytesOf(" 2 ", CapUnit.TB, UnitSystem.DECIMAL))
    }

    @Test fun unreadable_or_non_positive_text_is_null() {
        listOf("", "abc", "0", "-3", "1e", "99999999999999999999").forEach {
            assertNull(it, bytesOf(it, CapUnit.GB, UnitSystem.DECIMAL))
        }
    }

    @Test fun bytes_go_back_to_the_largest_unit_that_shows_them_exactly() {
        assertEquals("10" to CapUnit.GB, textAndUnitOf(10_000_000_000L, UnitSystem.DECIMAL))
        assertEquals("1.5" to CapUnit.GB, textAndUnitOf(1_500_000_000L, UnitSystem.DECIMAL))
        assertEquals("500" to CapUnit.MB, textAndUnitOf(500_000_000L, UnitSystem.DECIMAL))
        assertEquals("2" to CapUnit.TB, textAndUnitOf(2_000_000_000_000L, UnitSystem.DECIMAL))
        assertEquals("10" to CapUnit.GB, textAndUnitOf(10L * 1024 * 1024 * 1024, UnitSystem.BINARY))
        assertEquals("0.5" to CapUnit.MB, textAndUnitOf(500_000L, UnitSystem.DECIMAL))
    }

    @Test fun loading_a_cap_and_saving_it_again_never_changes_it() {
        listOf(1_234_567_890L, 10_000_000_000L, 999_999_999_999L, 7L, 5_368_709_120L).forEach { bytes ->
            UnitSystem.entries.forEach { system ->
                val (text, unit) = textAndUnitOf(bytes, system)
                assertEquals("$bytes in $system", bytes, bytesOf(text, unit, system))
            }
        }
    }

    @Test fun a_good_form_becomes_a_plan_with_the_plans_zone() {
        val ok = goodForm.copy(
            subscriptionId = 5, dayOfMonth = 12, renewalTime = LocalTime.of(14, 30),
            rolloverKind = RolloverKind.Capped, rolloverCapText = "2", rolloverCapUnit = CapUnit.GB,
        ).toDomain(planId = 9, zone = zone, system = UnitSystem.DECIMAL, archived = false) as FormResult.Ok
        assertEquals(
            Plan(9, "Main", 5, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), zone), Rollover.Capped(2_000_000_000L)),
            ok.plan,
        )
    }

    @Test fun every_n_days_uses_the_anchor() {
        val ok = goodForm.copy(cycleKind = CycleKind.EveryNDays, everyDays = 28, anchor = LocalDate.of(2026, 9, 1))
            .toDomain(0, zone, UnitSystem.DECIMAL, false) as FormResult.Ok
        assertEquals(Cycle.EveryNDays(28, LocalDate.of(2026, 9, 1), LocalTime.MIDNIGHT, zone), ok.plan.cycle)
    }

    @Test fun unreadable_numbers_are_reported_under_the_contract_field_names() {
        val errors = goodForm.copy(
            capText = "abc", rolloverKind = RolloverKind.Capped, rolloverCapText = "",
            addOns = listOf(AddOnDraft(label = "Boost", amountText = "x", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))),
        ).toDomain(0, zone, UnitSystem.DECIMAL, false) as FormResult.Errors
        assertEquals(setOf("capBytes", "rollover.maxBytes", "addOn.0.bytes"), errors.errors.keys)
    }

    @Test fun add_ons_and_free_windows_are_converted_with_the_plan_id_and_whole_days() {
        val ok = goodForm.copy(
            addOns = listOf(AddOnDraft(id = 4, label = "Boost", amountText = "2", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))),
            freeRules = listOf(FreeRuleDraft(id = 6, label = "Night", days = setOf(DayOfWeek.FRIDAY), start = LocalTime.of(22, 0), end = LocalTime.of(6, 0), packageName = "com.video")),
        ).toDomain(planId = 9, zone = zone, system = UnitSystem.DECIMAL, archived = false) as FormResult.Ok
        val addOn = ok.addOns.single()
        assertEquals(AddOn(4, 9, "Boost", 2_000_000_000L,
            LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant(), LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()), addOn)   // valid until the END of the last day
        assertEquals(FreeRule(6, 9, "Night", setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), "com.video"), ok.freeRules.single())
    }

    @Test fun a_blank_package_name_means_all_apps() {
        val ok = goodForm.copy(freeRules = listOf(FreeRuleDraft(label = "Night", packageName = "  ")))
            .toDomain(1, zone, UnitSystem.DECIMAL, false) as FormResult.Ok
        assertNull(ok.freeRules.single().packageName)
    }
}
```
`PlanEditorViewModelTest.kt` (integration with the real repositories over in-memory stores; copy the dispatcher set-up of `HomeViewModelBehaviourTest`; `previewDebounceMillis = 0`; `now = 2026-10-15T12:00Z`, UTC):
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditorViewModelTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planStore = InMemoryPlanStore()
    private val usage = InMemoryUsageStore()
    private val plans = RoomPlanRepository(planStore, usage, clock, ticks = flowOf(now))
    private val alerts = RoomAlertRepository(planStore)
    private val settings = FakeSettingsRepository()
    private val permissions = FakePermissionRepository()
    private val sims = FakeSimInfoSource(listOf(SimInfo(5, 0, "Alpha"), SimInfo(8, 1, "Beta")), defaultId = 5)
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(planId: Long = 0L) =
        PlanEditorViewModel(planId, plans, alerts, settings, permissions, sims, clock, Locale.ENGLISH, previewDebounceMillis = 0L)

    private fun TestScope.started(vm: PlanEditorViewModel): Job { val job = launch { vm.uiState.collect {} }; advanceUntilIdle(); return job }
    private fun PlanEditorViewModel.fill(name: String = "Main", cap: String = "10") {
        onEvent(PlanEditorEvent.SetName(name)); onEvent(PlanEditorEvent.SetCap(cap))
    }

    @Test fun a_new_editor_starts_empty_with_default_alerts() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        val s = vm.uiState.value
        assertTrue(s.isNew); assertFalse(s.loading); assertFalse(s.notFound)
        assertEquals("", s.form.name)
        assertEquals(setOf(80, 100), s.form.alertThresholds)
        assertEquals(LocalDate.of(2026, 10, 15), s.form.anchor)           // the anchor defaults to today in the device zone
        job.cancel()
    }

    @Test fun saving_an_invalid_form_shows_errors_and_saves_nothing() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.SetCap("abc")); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(setOf("capBytes"), vm.uiState.value.errors.keys)
        vm.onEvent(PlanEditorEvent.SetCap("10")); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()   // the name is still blank: the repository says so
        assertEquals("name", vm.uiState.value.errors.keys.single())
        assertTrue(plans.observePlans(true).first().isEmpty())
        assertFalse(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun editing_a_field_clears_the_errors() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.isNotEmpty())
        vm.onEvent(PlanEditorEvent.SetName("Main")); advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.isEmpty())
        job.cancel()
    }

    @Test fun a_valid_form_creates_the_plan_with_alert_rules_and_marks_saved() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.SetSim(5)); vm.onEvent(PlanEditorEvent.SetDayOfMonth(12))
        vm.onEvent(PlanEditorEvent.ToggleThreshold(50)); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        val plan = plans.observePlans().first().single()
        assertEquals("Main", plan.name); assertEquals(5, plan.subscriptionId); assertEquals(10_000_000_000L, plan.capBytes)
        assertEquals(Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneOffset.UTC), plan.cycle)
        assertEquals(listOf(50, 80, 100), alerts.observeRules(plan.id).first().map { it.thresholdPercent })
        assertTrue(vm.uiState.value.saved)
        job.cancel()
    }

    @Test fun binary_units_are_honoured() = runTest(dispatcher) {
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        val vm = vm(); val job = started(vm)
        vm.fill(cap = "10"); vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(10L * 1024 * 1024 * 1024, plans.observePlans().first().single().capBytes)
        job.cancel()
    }

    @Test fun a_repository_error_is_mapped_to_its_field() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.SetCycleKind(CycleKind.EveryNDays)); vm.onEvent(PlanEditorEvent.SetEveryDays(0))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(setOf("cycle.days"), vm.uiState.value.errors.keys)
        job.cancel()
    }

    @Test fun an_existing_plan_loads_into_the_form_and_saves_changes() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Old", null, 5_000_000_000L, Cycle.MonthlyOnDay(3, LocalTime.of(8, 0), ZoneOffset.UTC), Rollover.Full)) as Outcome.Success).value
        alerts.upsertRule(AlertRule(0, id, 90, true))
        val vm = vm(id); val job = started(vm)
        val s = vm.uiState.value
        assertFalse(s.isNew); assertEquals("Old", s.form.name); assertEquals("5", s.form.capText); assertEquals(CapUnit.GB, s.form.capUnit)
        assertEquals(3, s.form.dayOfMonth); assertEquals(LocalTime.of(8, 0), s.form.renewalTime); assertEquals(RolloverKind.Full, s.form.rolloverKind)
        assertEquals(setOf(90), s.form.alertThresholds)
        vm.onEvent(PlanEditorEvent.SetName("New")); vm.onEvent(PlanEditorEvent.ToggleThreshold(90)); vm.onEvent(PlanEditorEvent.ToggleThreshold(50))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals("New", plans.observePlans().first().single().name)
        assertEquals(listOf(50), alerts.observeRules(id).first().map { it.thresholdPercent })       // 90 removed, 50 added
        job.cancel()
    }

    @Test fun a_missing_plan_id_is_reported() = runTest(dispatcher) {
        val vm = vm(99L); val job = started(vm)
        assertTrue(vm.uiState.value.notFound)
        job.cancel()
    }

    @Test fun add_ons_and_free_windows_are_saved_updated_and_deleted() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.AddAddOn); vm.onEvent(PlanEditorEvent.AddFreeRule)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = "Boost", amountText = "2")))
        vm.onEvent(PlanEditorEvent.UpdateFreeRule(0, vm.uiState.value.form.freeRules[0].copy(label = "Night")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        val id = plans.observePlans().first().single().id
        assertEquals(listOf("Boost"), plans.observeAddOns(id).first().map { it.label })
        assertEquals(listOf("Night"), plans.observeFreeRules(id).first().map { it.label })
        job.cancel()

        val again = vm(id); val job2 = started(again)
        again.onEvent(PlanEditorEvent.RemoveAddOn(0)); again.onEvent(PlanEditorEvent.RemoveFreeRule(0))
        again.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertTrue(plans.observeAddOns(id).first().isEmpty()); assertTrue(plans.observeFreeRules(id).first().isEmpty())
        job2.cancel()
    }

    @Test fun a_bad_add_on_is_reported_under_its_index_and_the_plan_is_kept() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.fill(); vm.onEvent(PlanEditorEvent.AddAddOn)
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = " ", amountText = "2")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals("addOn.0.label", vm.uiState.value.errors.keys.single())
        assertEquals(1, plans.observePlans().first().size)            // the plan itself was saved; a retry updates it instead of duplicating
        vm.onEvent(PlanEditorEvent.UpdateAddOn(0, vm.uiState.value.form.addOns[0].copy(label = "Boost")))
        vm.onEvent(PlanEditorEvent.Save); advanceUntilIdle()
        assertEquals(1, plans.observePlans().first().size)
        assertEquals(1, plans.observeAddOns(plans.observePlans().first().single().id).first().size)
        job.cancel()
    }

    @Test fun a_preview_appears_for_a_valid_form_and_not_for_an_invalid_one() = runTest(dispatcher) {
        usage.addMinute(MinuteTotal(Instant.parse("2026-10-10T10:00:00Z"), NetworkKind.MOBILE, 1, 1_000_000_000L, 0))
        val vm = vm(); val job = started(vm)
        assertNull(vm.uiState.value.preview)
        vm.fill(); advanceUntilIdle()
        assertEquals("1.00 GB of 10.0 GB", vm.uiState.value.preview!!.usedText)
        vm.onEvent(PlanEditorEvent.SetCap("abc")); advanceUntilIdle()
        assertNull(vm.uiState.value.preview)
        job.cancel()
    }

    @Test fun the_sim_options_list_the_choices_and_follow_the_permission() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        assertEquals(listOf(null, 5, 8), vm.uiState.value.sims.map { it.subscriptionId })
        assertEquals("Not tied to a SIM", vm.uiState.value.sims.first().label)
        assertEquals("SIM 1 · Alpha", vm.uiState.value.sims[1].label)
        assertFalse(vm.uiState.value.needsPhonePermission)
        sims.sims = emptyList()
        permissions.set(PermissionState(usageAccess = true, notifications = true, phoneState = false, vpnConsentGranted = false)); advanceUntilIdle()
        assertTrue(vm.uiState.value.needsPhonePermission)
        assertEquals(listOf(null, 5), vm.uiState.value.sims.map { it.subscriptionId })     // "Not tied" and the default data SIM, which needs no permission
        assertEquals("Default data SIM", vm.uiState.value.sims[1].label)
        job.cancel()
    }

    @Test fun archiving_an_existing_plan_marks_saved() = runTest(dispatcher) {
        val id = (plans.upsertPlan(Plan(0, "Old", null, 5_000_000_000L, Cycle.MonthlyOnDay(3, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)) as Outcome.Success).value
        val vm = vm(id); val job = started(vm)
        vm.onEvent(PlanEditorEvent.Archive); advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertTrue(plans.observePlans(false).first().isEmpty())
        job.cancel()
    }

    @Test fun thresholds_toggle_on_and_off() = runTest(dispatcher) {
        val vm = vm(); val job = started(vm)
        vm.onEvent(PlanEditorEvent.ToggleThreshold(80)); assertEquals(setOf(100), vm.uiState.value.form.alertThresholds)
        vm.onEvent(PlanEditorEvent.ToggleThreshold(80)); assertEquals(setOf(80, 100), vm.uiState.value.form.alertThresholds)
        job.cancel()
    }
}
```
(Add the imports. `FakeSimInfoSource.sims` is a `var`, defined in task 8.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests "*PlanEditor*"` → FAIL.

- [ ] **Step 3: Implement.**

`PlanEditorMapping.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.plans

internal fun bytesOf(text: String, unit: CapUnit, system: UnitSystem): Long? {
    val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    val base = if (system == UnitSystem.BINARY) 1024L else 1000L
    val bytes = value.multiply(BigDecimal.valueOf(base).pow(unit.power)).setScale(0, RoundingMode.HALF_UP)
    return if (bytes > BigDecimal.valueOf(Long.MAX_VALUE)) null else bytes.toLong()
}

internal fun textAndUnitOf(bytes: Long, system: UnitSystem): Pair<String, CapUnit> {
    val base = if (system == UnitSystem.BINARY) 1024L else 1000L
    fun scale(unit: CapUnit) = BigDecimal.valueOf(base).pow(unit.power)
    val exact = CapUnit.entries.sortedByDescending { it.power }.firstOrNull { bytes % scale(it).toLong() == 0L }
    val unit = exact ?: CapUnit.entries.sortedByDescending { it.power }.firstOrNull { bytes >= scale(it).toLong() } ?: CapUnit.MB
    val value = BigDecimal.valueOf(bytes).divide(scale(unit), 3, RoundingMode.HALF_UP).stripTrailingZeros()
    return value.toPlainString() to unit
}
```
(`stripTrailingZeros` on 10 gives `1E+1`; `toPlainString()` returns "10" ✓. Bytes of 0 or below 1 MB: `exact` for MB when bytes % 10^6 == 0; else falls through to `CapUnit.MB` with decimals.)

`toDomain`:
```kotlin
internal sealed interface FormResult {
    data class Ok(val plan: Plan, val addOns: List<AddOn>, val freeRules: List<FreeRule>) : FormResult
    data class Errors(val errors: Map<String, String>) : FormResult
}

internal fun PlanEditorForm.toDomain(planId: Long, zone: ZoneId, system: UnitSystem, archived: Boolean): FormResult {
    val errors = LinkedHashMap<String, String>()
    val cap = bytesOf(capText, capUnit, system)
    if (cap == null) errors["capBytes"] = "Enter a number greater than zero"
    val rollover: Rollover = when (rolloverKind) {
        RolloverKind.None -> Rollover.None
        RolloverKind.Full -> Rollover.Full
        RolloverKind.Capped -> bytesOf(rolloverCapText, rolloverCapUnit, system)?.let { Rollover.Capped(it) }
            ?: run { errors["rollover.maxBytes"] = "Enter a number greater than zero"; Rollover.None }
    }
    val addOnBytes = addOns.mapIndexed { index, draft ->
        bytesOf(draft.amountText, draft.unit, system).also { if (it == null) errors["addOn.$index.bytes"] = "Enter a number greater than zero" }
    }
    if (errors.isNotEmpty()) return FormResult.Errors(errors)
    val cycle = when (cycleKind) {
        CycleKind.Monthly -> Cycle.MonthlyOnDay(dayOfMonth, renewalTime, zone)
        CycleKind.EveryNDays -> Cycle.EveryNDays(everyDays, anchor, renewalTime, zone)
    }
    val plan = Plan(planId, name, subscriptionId, cap!!, cycle, rollover, archived)
    val addOnDomain = addOns.mapIndexed { index, d ->
        AddOn(d.id, planId, d.label, addOnBytes[index]!!, d.validFrom.atStartOfDay(zone).toInstant(), d.validUntil.plusDays(1).atStartOfDay(zone).toInstant())
    }
    val ruleDomain = freeRules.map { FreeRule(it.id, planId, it.label, it.days, it.start, it.end, it.packageName?.trim()?.ifEmpty { null }) }
    return FormResult.Ok(plan, addOnDomain, ruleDomain)
}
```
`PlanEditorViewModel.kt`: implement exactly the behaviour the tests specify. Skeleton to follow:
```kotlin
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PlanEditorViewModel(
    private val planId: Long, private val plans: PlanRepository, private val alerts: AlertRepository,
    private val settings: SettingsRepository, private val permissions: PermissionRepository, private val sims: SimInfoSource,
    private val clock: Clock = SystemZoneClock(), private val locale: Locale = Locale.getDefault(),
    private val previewDebounceMillis: Long = 300L,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanEditorUiState(planId = planId, isNew = planId == 0L, form = PlanEditorForm(anchor = LocalDate.now(clock))))
    val uiState: StateFlow<PlanEditorUiState> = _state.asStateFlow()

    private var zone: ZoneId = clock.zone
    private var loadedAddOnIds = emptySet<Long>()
    private var loadedFreeRuleIds = emptySet<Long>()
    private var loadedAlertRules = emptyList<AlertRule>()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { permissions.observe().collect { refreshSims(it.phoneState) } }
        viewModelScope.launch {
            _state.map { it.form to it.unitSystem }.distinctUntilChanged().debounce(previewDebounceMillis)
                .collectLatest { (form, system) -> _state.update { it.copy(preview = preview(form, system)) } }
        }
    }
    ...
}
```
Requirements for the parts the skeleton elides:
- `load()`: read `settings.observe().first().unitSystem` into the state; for `planId == 0L` finish with `loading = false`; otherwise find the plan in `plans.observePlans(true).first()`, set `notFound = true` when absent, else fill the form: name, subscription, `textAndUnitOf(capBytes, system)`, cycle fields (`zone` of the plan's cycle becomes `this.zone`), rollover, add-on drafts (`validFrom` = local date of `validFrom` in the zone, `validUntil` = local date of `validUntil.minusMillis(1)`), free rule drafts, alert thresholds from `alerts.observeRules(planId).first()`; remember `loadedAddOnIds`, `loadedFreeRuleIds`, `loadedAlertRules`.
- `refreshSims(phoneState)`: options = `[SimOptionUi(null, "Not tied to a SIM")]` + (if the default data id `sims.defaultDataSubscriptionId() >= 0` and it is not among `sims.activeSims()`: `SimOptionUi(defaultId, "Default data SIM")`) + one `SimOptionUi(id, "SIM ${slot + 1} · ${name}")` per active SIM (sorted by slot); `needsPhonePermission = !phoneState && sims.activeSims().isEmpty()`.
- `preview(form, system)`: `form.toDomain(planId, zone, system, archived = false)`; on `Ok` call `plans.previewState(plan, addOns, freeRules)`; on `Success` return `planCardUi(state, forecast, addOns, simLabel = null, units, clock.instant(), zone, locale)`; anything else returns `null`. A brand-new empty form returns `null` (the cap text is empty).
- `onEvent`: every edit event updates the form with `copy` and clears `errors` (`Save` and `Archive` do not); `AddAddOn` appends `AddOnDraft(validFrom = today, validUntil = today.plusDays(30))` where `today = LocalDate.now(clock)`; `AddFreeRule` appends `FreeRuleDraft()`; `ToggleThreshold(p)` adds or removes `p`. `Save` and `Archive` run in `viewModelScope.launch`.
- `save()` and `archive()` always use the plan id held in the STATE (`_state.value.planId`), never the constructor argument: after the first successful `upsertPlan` the state holds the new id, so a retry updates that plan instead of creating a second one. `save()`: ignore when `saving`; convert with `toDomain`; `Errors` -> set `errors` and stop; set `saving = true`; `plans.upsertPlan(plan)` (`Failure(Invalid(f, r))` -> `errors = mapOf(f to r)`, `saving = false`, stop; other failures -> `errors = mapOf("form" to "Could not save. Try again.")`); on success remember the id (`planId` of the state becomes the new id, `isNew = false`, so a retry updates it); then children, in this order, stopping at the first failure and reporting `addOn.<index>.<field>` / `freeRule.<index>.<field>`: delete add-ons whose id was loaded but is no longer in the form; `upsertAddOn` each draft with the plan id and store the returned id back into the draft; the same for free rules; thresholds: delete loaded rules whose threshold is no longer wanted, `upsertRule(AlertRule(existingId or 0, id, threshold, true))` for each wanted threshold; finally update the `loaded*` remembered sets and set `saved = true`.
- `archive()`: only for an existing plan: `plans.archivePlan(planId)`; success -> `saved = true`; failure -> `errors = mapOf("form" to "Could not archive. Try again.")`.
`AppGraph.kt`: `fun planEditorViewModelFactory(planId: Long) = viewModelFactory { initializer { PlanEditorViewModel(planId, plans, alerts, settings, permissions, simInfo, clock) } }`.

- [ ] **Step 4: Run to verify they pass** — new tests green, full gate green.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): drive the plan editor from a tested form state

Parsing and unit handling live in pure functions, and a failed child save keeps the plan so a retry never duplicates it."
```

---

### Task 16: Plan editor screen, routing and the onboarding step

**Files:**
- Create: `app/src/main/kotlin/$PKG/plans/{PlanEditorScreen,EditorFields}.kt`
- Modify: `app/src/main/kotlin/$PKG/EmberbyteApp.kt` (editor route), `app/src/main/kotlin/$PKG/onboarding/{OnboardingViewModel,OnboardingScreen}.kt`, `app/src/main/kotlin/$PKG/AppGraph.kt` (onboarding factory gets `plans`)
- Test: `app/src/test/kotlin/$PKG/plans/PlanEditorContentTest.kt`; modify `onboarding/{OnboardingContentTest,OnboardingViewModelTest}.kt` where they construct the changed types (name each in your report)

**Interfaces:**
- Consumes: everything from task 15, `PlanCard`, `PermissionPrompt`, `ScreenHeader`, `DeepLinkPatterns`.
- Produces: `@Composable fun PlanEditorScreen(viewModel: PlanEditorViewModel, onBack: () -> Unit, onPrompt: (String) -> Unit, modifier: Modifier = Modifier)` (navigates back when `saved`), `@Composable internal fun PlanEditorContent(state: PlanEditorUiState, onEvent: (PlanEditorEvent) -> Unit, onBack: () -> Unit, onPrompt: (String) -> Unit, modifier: Modifier = Modifier)`, `DateField(label, date, onChange)` and `TimeField(label, time, onChange)`; `OnboardingUiState(usageAccess, notifications, hasPlan)`; `OnboardingContent(state, onOpenUsageAccess, onRequestNotifications, onSetUpPlan, onFinish, modifier)`; `OnboardingViewModel(onboarding, permissions, plans)`.

- [ ] **Step 1: Write the failing tests.** `PlanEditorContentTest.kt` (Robolectric Compose, dark static theme, `@Config(sdk = [35])`; default `state = PlanEditorUiState(loading = false, sims = listOf(SimOptionUi(null, "Not tied to a SIM"), SimOptionUi(5, "SIM 1 · Alpha")), form = PlanEditorForm(name = "Main", capText = "10"))`; a `show(state, onEvent, onPrompt)` helper like `AppsContentTest`):

```kotlin
    @Test fun the_title_depends_on_new_or_existing() {
        show(state.copy(isNew = true)); rule.onNodeWithText("New plan").assertIsDisplayed()
    }
    @Test fun an_existing_plan_says_edit_and_offers_archive() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(isNew = false, planId = 3), onEvent = { events += it })
        rule.onNodeWithText("Edit plan").assertIsDisplayed()
        rule.onNodeWithText("Archive plan").performScrollTo().performClick()
        assertEquals(listOf<PlanEditorEvent>(PlanEditorEvent.Archive), events)
    }
    @Test fun a_new_plan_has_no_archive_button() { show(state.copy(isNew = true)); rule.onNodeWithText("Archive plan").assertDoesNotExist() }
    @Test fun typing_the_name_and_the_cap_sends_events() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onNodeWithText("Plan name").performTextInput("x")
        rule.onNodeWithText("Cap").performTextInput("5")
        assertTrue(events.any { it is PlanEditorEvent.SetName }); assertTrue(events.any { it is PlanEditorEvent.SetCap })
    }
    @Test fun unit_chips_send_the_unit() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onAllNodesWithText("MB")[0].performScrollTo().performClick()
        assertEquals(PlanEditorEvent.SetCapUnit(CapUnit.MB), events.last())
    }
    @Test fun errors_are_shown_under_their_fields() {
        show(state.copy(errors = mapOf("name" to "Enter a name of up to 40 characters", "capBytes" to "Enter a number greater than zero")))
        rule.onNodeWithText("Enter a name of up to 40 characters").assertIsDisplayed()
        rule.onNodeWithText("Enter a number greater than zero").assertIsDisplayed()
    }
    @Test fun the_sim_choices_are_listed_and_selectable() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onNodeWithText("Not tied to a SIM").assertIsDisplayed()
        rule.onNodeWithText("SIM 1 · Alpha").performClick()
        assertEquals(PlanEditorEvent.SetSim(5), events.last())
    }
    @Test fun without_the_phone_permission_a_prompt_is_shown() {
        var picked: String? = null; show(state.copy(needsPhonePermission = true), onPrompt = { picked = it })
        rule.onNodeWithText("Allow phone access").assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals("phone_state", picked)
    }
    @Test fun the_cycle_fields_follow_the_cycle_kind() {
        show(state.copy(form = state.form.copy(cycleKind = CycleKind.EveryNDays)))
        rule.onNodeWithText("Number of days").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Day of month").assertDoesNotExist()
    }
    @Test fun monthly_shows_the_day_of_month() {
        show(state); rule.onNodeWithText("Day of month").performScrollTo().assertIsDisplayed(); rule.onNodeWithText("Number of days").assertDoesNotExist()
    }
    @Test fun the_rollover_cap_field_only_exists_for_capped() {
        show(state.copy(form = state.form.copy(rolloverKind = RolloverKind.Capped)))
        rule.onNodeWithText("Maximum rollover").performScrollTo().assertIsDisplayed()
    }
    @Test fun no_rollover_cap_field_for_none() { show(state); rule.onNodeWithText("Maximum rollover").assertDoesNotExist() }
    @Test fun add_ons_can_be_added_and_removed() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(form = state.form.copy(addOns = listOf(AddOnDraft(label = "Boost", amountText = "2", validFrom = LocalDate.of(2026, 10, 1), validUntil = LocalDate.of(2026, 10, 31))))), onEvent = { events += it })
        rule.onNodeWithText("Add add-on pack").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Remove add-on pack").performScrollTo().performClick()
        assertEquals(listOf(PlanEditorEvent.AddAddOn, PlanEditorEvent.RemoveAddOn(0)), events)
    }
    @Test fun free_windows_toggle_days_and_can_be_removed() {
        val events = mutableListOf<PlanEditorEvent>()
        val draft = FreeRuleDraft(label = "Night", days = setOf(DayOfWeek.MONDAY))
        show(state.copy(form = state.form.copy(freeRules = listOf(draft))), onEvent = { events += it })
        rule.onNodeWithText("Tue").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.UpdateFreeRule(0, draft.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))), events.last())
        rule.onNodeWithContentDescription("Remove free window").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.RemoveFreeRule(0), events.last())
    }
    @Test fun the_alert_chips_toggle_thresholds() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onNodeWithText("50%").performScrollTo().performClick()
        assertEquals(PlanEditorEvent.ToggleThreshold(50), events.last())
    }
    @Test fun save_sends_save_and_is_disabled_while_saving() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf<PlanEditorEvent>(PlanEditorEvent.Save), events)
        show(state.copy(saving = true)); rule.onNodeWithText("Save").assertIsNotEnabled()
    }
    @Test fun the_preview_card_is_shown_when_there_is_one() {
        val card = PlanCardUi(1, "Main", null, 0.1f, BudgetStateUi.Ok, "10%", "1.00 GB of 10.0 GB", "9.00 GB left", "16 days left", null, false, emptyList(), false)
        show(state.copy(preview = card)); rule.onNodeWithText("Preview").assertIsDisplayed(); rule.onNodeWithText("1.00 GB of 10.0 GB").assertIsDisplayed()
    }
    @Test fun a_missing_plan_and_the_loading_state() {
        show(state.copy(notFound = true)); rule.onNodeWithText("This plan no longer exists").assertIsDisplayed()
        show(state.copy(loading = true)); rule.onNodeWithText("Plan name").assertDoesNotExist()
    }
    @Test fun the_time_field_opens_a_dialog_that_can_be_cancelled() {
        val events = mutableListOf<PlanEditorEvent>(); show(onEvent = { events += it })
        rule.onNodeWithText("Renews at 00:00").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertTrue(events.none { it is PlanEditorEvent.SetRenewalTime })
    }
```
Two `show(...)` calls in one test are not allowed by the Compose rule; split those into separate tests where shown above (`save_is_disabled_while_saving`, `a_missing_plan...`, `the_loading_state`). Onboarding tests: in `OnboardingContentTest` add `the_plan_step_offers_to_set_up_a_plan` (the text "Your data plan" shows; the button "Set up a plan" calls `onSetUpPlan`), `a_plan_that_exists_shows_plan_added` (`hasPlan = true` shows "Plan added" and no button); in `OnboardingViewModelTest` add `has_plan_follows_the_plan_repository` (`FakePlanRepository` has one seeded plan so `hasPlan` is true; an empty repository gives false, build it with `RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock)`).

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement.**

`EditorFields.kt`: `DateField` is an `OutlinedButton` showing `"$label: ${date}"` that opens a Material 3 `DatePickerDialog` (`rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())`; confirm converts `state.selectedDateMillis` with `Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()`); `TimeField(label, time, onChange)` shows `"$label $HH:mm"` (format `"%02d:%02d"`; the test above expects exactly `Renews at 00:00`, so the call site passes label `"Renews at"`), opens an `AlertDialog` with `TimePicker(rememberTimePickerState(time.hour, time.minute, is24Hour = DateFormat.is24HourFormat(context)))` plus "OK" and "Cancel" buttons. Each dialog keeps its open flag in `rememberSaveable`.
`PlanEditorScreen.kt`: `PlanEditorScreen` collects `uiState`, runs `LaunchedEffect(state.saved) { if (state.saved) onBack() }` and calls `PlanEditorContent`. `PlanEditorContent` is a plain `Column` with `Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 112.dp)` and `Arrangement.spacedBy(12.dp)` (NOT a `LazyColumn`: every section must exist in the semantics tree so the tests can reach it with `performScrollTo`) with: `ScreenHeader(if (isNew) "New plan" else "Edit plan", onBack)`; `loading` shows `Text("Loading plan")` only; `notFound` shows `Text("This plan no longer exists")` and a back button; otherwise, in this order: a "Preview" label + `PlanCard(preview, onClick = {})` when `preview != null`; an `OutlinedTextField` labelled "Plan name" (`isError`/`supportingText` from `errors["name"]`); the SIM section (title "SIM", a `PermissionPrompt(PermissionPromptUi("phone_state", "Allow phone access", "Lets Emberbyte list your SIMs so a plan can follow one SIM. You can also use the default data SIM below.", "Allow"), onAction = { onPrompt("phone_state") })` when `needsPhonePermission`, then one selectable `Row` (a `RadioButton` + `Text(label)`, whole row `selectable(role = Role.RadioButton)`) per `sims` entry, selected when `form.subscriptionId == option.subscriptionId`); the cap field ("Cap", decimal keyboard, `errors["capBytes"]`) with `MB`/`GB`/`TB` `FilterChip`s; the cycle section (`SingleChoiceSegmentedButtonRow` with "Monthly" and "Every N days"; for monthly an `OutlinedTextField` "Day of month" (number keyboard, `errors["cycle.dayOfMonth"]`), for every-N-days a "Number of days" field (`errors["cycle.days"]`) and `DateField("Starts on", anchor, ...)`; both show `TimeField("Renews at", renewalTime, ...)`); the rollover section ("None"/"Full"/"Capped" `FilterChip`s and, for capped, an `OutlinedTextField` "Maximum rollover" with unit chips and `errors["rollover.maxBytes"]`); "Add-on packs" (per draft: label field, "Amount" field with unit chips, `DateField("Valid from")`, `DateField("Valid until")`, `IconButton` with content description "Remove add-on pack", errors `addOn.<i>.<field>`; then `OutlinedButton("Add add-on pack")`); "Free windows" (explanatory line "Traffic in a free window does not count toward the plan."; per draft: label field, seven day `FilterChip`s labelled `Mon`..`Sun` that send `UpdateFreeRule(index, draft.copy(days = toggled))`, two `TimeField`s ("From", "To"), an optional "App package" field, `IconButton` "Remove free window", errors `freeRule.<i>.<field>`; then `OutlinedButton("Add free window")`); "Alert me at" with `FilterChip`s `50%`, `80%`, `90%`, `100%`; `errors["form"]` as an error line; `Button("Save")` (`enabled = !saving`) and, when `!isNew`, `TextButton("Archive plan")`. Every text change sends the matching event; the chips' `selected` come from the form.
`EmberbyteApp.kt`: replace the temporary editor destination (task 10) with
```kotlin
            composable(
                Routes.PLAN_EDITOR,
                arguments = listOf(navArgument("planId") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = DeepLinkPatterns.PLAN_EDITOR }),
            ) { entry ->
                val planId = entry.arguments?.getLong("planId") ?: 0L
                val viewModel: PlanEditorViewModel =
                    viewModel(key = "plan-$planId", factory = graph.planEditorViewModelFactory(planId))
                PlanEditorScreen(viewModel, onBack = { navController.popBackStack() }, onPrompt = actions::onPrompt)
            }
```
Onboarding: `OnboardingUiState(val usageAccess: Boolean = false, val notifications: Boolean = false, val hasPlan: Boolean = false)`; `OnboardingViewModel(onboarding, permissions, plans: PlanRepository)` builds the state with `combine(permissions.observe(), plans.observePlans()) { p, list -> OnboardingUiState(p.usageAccess, p.notifications, list.isNotEmpty()) }`; `OnboardingContent` gets the required `onSetUpPlan: () -> Unit` (before `onFinish`) and a third `StepTile(title = "Your data plan", body = "Optional. Add your cap and renewal day to see what is left and when it runs out.", granted = state.hasPlan, grantedText = "Plan added", actionLabel = "Set up a plan", onAction = onSetUpPlan)`; `OnboardingScreen` passes `onSetUpPlan` through (new required parameter before `onFinish`); `EmberbyteApp` passes `onSetUpPlan = { navController.navigate(Routes.planEditor(0)) }`; `AppGraph.onboardingViewModelFactory` passes `plans`.

- [ ] **Step 4: Run to verify they pass** — new tests green, full gate green. **Tester (emulator, mandatory):** (1) from Home tap "Set up your data plan", fill name "Main", cap 5 GB, save: the Plans tab shows the card with ring and "5.00 GB"-style numbers, Home shows "left" in the subtitle; (2) edit the plan, change the cap, add an add-on pack "Boost 1 GB" and a free window, save, and see the cap and chips change; (3) leave the cap empty and press Save: the error shows under the field and nothing is saved; (4) fold the editor under font scale 2.0: every field readable, Save reachable by scrolling; (5) `adb shell am start -a android.intent.action.VIEW -d "emberbyte://plans/<id>"` opens the editor of that plan; (6) a clean install in onboarding shows the third step and returns there after saving a plan; (7) 10 taps per tab still give 0 bounces. Evidence: screenshots in the tester's `qa-shots/` folder and quoted `uiautomator dump` texts.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): add the plan editor screen and the onboarding plan step

One scrolling form with a live preview; every error sits under the field the contract names."
```

---

### Task 17: Remaining data in the live notification

**Files:**
- Modify: `app/src/main/kotlin/$PKG/sampler/{LiveNotification,SamplerService}.kt`
- Test: add tests to `app/src/test/kotlin/$PKG/sampler/LiveNotificationTest.kt` (existing tests unchanged)

**Interfaces:**
- Produces: `liveNotificationText(todayBytes, rxBps, units, showSpeed, offline = false, remainingBytes: Long? = null)`; the text line becomes `"<remaining> left · ↓ <speed>"` (frontend spec section 7).

- [ ] **Step 1: Write the failing tests** (add to `LiveNotificationTest`):
```kotlin
    @Test fun remaining_data_comes_first_and_the_speed_follows() {
        val t = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true, remainingBytes = 3_800_000_000L)
        assertEquals("1.24 GB today", t.title)
        assertEquals("3.80 GB left · ↓ 4.20 MB/s", t.text)
    }
    @Test fun remaining_data_alone_when_the_speed_is_hidden() {
        val t = liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left", t.text)
    }
    @Test fun offline_keeps_the_remaining_data() {
        val t = liveNotificationText(0, 0, ByteUnits.DECIMAL, showSpeed = true, offline = true, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left · No connection", t.text)
    }
    @Test fun no_plan_keeps_the_old_text() {
        val t = liveNotificationText(1_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("↓ 4.20 MB/s", t.text)
        assertNull(liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false).text)
    }
    @Test fun nothing_left_says_so() {
        val t = liveNotificationText(1, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 0L)
        assertEquals("No data left", t.text)
    }
```
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement** in `LiveNotification.kt`:
```kotlin
fun liveNotificationText(
    todayBytes: Long, rxBps: Long, units: ByteUnits, showSpeed: Boolean, offline: Boolean = false, remainingBytes: Long? = null,
): LiveNotificationText {
    val today = formatBytes(todayBytes, units)
    val parts = buildList {
        if (remainingBytes != null) {
            val left = formatBytes(remainingBytes, units)
            add(if (remainingBytes <= 0L) "No data left" else "${left.value} ${left.unit} left")
        }
        if (showSpeed) {
            val speed = formatBytes(rxBps, units)
            add(if (offline) "No connection" else "↓ ${speed.value} ${speed.unit}/s")
        }
    }
    return LiveNotificationText(title = "${today.value} ${today.unit} today", text = parts.joinToString(" · ").ifEmpty { null })
}
```
`SamplerService.updateNotification`: add a fourth flow `val remaining = graph.plans.observeActivePlanStates().map { it.firstOrNull()?.remainingBytes }` and `combine(speeds, graph.usage.observeToday(), graph.settings.observe(), remaining) { speed, today, settings, left -> ... remainingBytes = left ... }`.
- [ ] **Step 4: Run to verify they pass** — full gate green. **Tester (emulator):** with a plan saved (task 16) `adb shell dumpsys notification --noredact | grep -A6 "io.github.khaledbahaaeldin.emberbyte"` shows the live notification text `"… GB left · ↓ …"`; with no plan the old text.
- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): show the remaining data in the live notification

The notification now answers the question people ask most: how much is left."
```


### Task 18: Home-screen widgets (`HeroWidget`, `BentoWidget`)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/$PKG/AppGraph.kt` (make `clock` a public `val`), `app/src/main/kotlin/$PKG/sampler/{SamplerService,CatchUpWorker}.kt`
- Create: `app/src/main/res/xml/{hero_widget_info,bento_widget_info}.xml`, `app/src/main/kotlin/$PKG/widget/{WidgetData,WidgetModels,HeroWidget,BentoWidget,WidgetUpdater}.kt`
- Test: `app/src/test/kotlin/$PKG/widget/{WidgetModelsTest,WidgetDataTest,WidgetManifestTest}.kt`

**Interfaces:**
- Consumes: `UsageRepository`, `PlanRepository`, `SettingsRepository`, `forecastToUi`, `forecastSentence`, `plainBytes`, `DeepLinks`.
- Produces:
```kotlin
data class WidgetSnapshot(val today: DayUsage, val plan: PlanState?, val forecast: Forecast?, val speed: LiveSpeed?,
                          val topApps: List<AppUsage>, val units: UnitSystem, val now: Instant, val zone: ZoneId, val locale: Locale)
object WidgetData { suspend fun load(usage: UsageRepository, plans: PlanRepository, settings: SettingsRepository, clock: Clock,
                                     locale: Locale = Locale.getDefault(), speedTimeoutMillis: Long = 500L): WidgetSnapshot }
data class HeroWidgetModel(val number: String, val unit: String, val caption: String)
data class BentoWidgetModel(val hero: HeroWidgetModel, val forecast: String, val speed: String, val topApps: List<String>)
internal fun heroWidgetModel(s: WidgetSnapshot): HeroWidgetModel
internal fun bentoWidgetModel(s: WidgetSnapshot): BentoWidgetModel
class HeroWidget : GlanceAppWidget(); class HeroWidgetReceiver : GlanceAppWidgetReceiver()
class BentoWidget : GlanceAppWidget(); class BentoWidgetReceiver : GlanceAppWidgetReceiver()
object WidgetUpdater { suspend fun updateAll(context: Context) }
```

- [ ] **Step 1: Write the failing tests.**

`WidgetModelsTest.kt` (build a `WidgetSnapshot` helper with `now = 2026-10-10T12:00Z` (a Saturday), `ZoneOffset.UTC`, `Locale.ENGLISH`, `UnitSystem.DECIMAL`; `today = DayUsage(date, 1_240_000_000L, 0)`; plan state with `remainingBytes = 3_800_000_000L`):
```kotlin
class WidgetModelsTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun planState(remaining: Long = 3_800_000_000L) = PlanState(
        Plan(1, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None),
        CycleWindow(now.minusSeconds(86_400), now.plusSeconds(86_400)), 10_000_000_000L, 10_000_000_000L - remaining, remaining, 0, 0, 0, 9, 0.62f, false,
    )
    private fun snapshot(plan: PlanState? = planState(), forecast: Forecast? = null, speed: LiveSpeed? = null, apps: List<AppUsage> = emptyList()) =
        WidgetSnapshot(DayUsage(LocalDate.of(2026, 10, 10), 1_240_000_000L, 0), plan, forecast, speed, apps, UnitSystem.DECIMAL, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun hero_shows_today_and_the_remaining_data() {
        assertEquals(HeroWidgetModel("1.24", "GB", "3.80 GB left"), heroWidgetModel(snapshot()))
    }
    @Test fun hero_without_a_plan_asks_for_one() {
        assertEquals("Set up a plan", heroWidgetModel(snapshot(plan = null)).caption)
    }
    @Test fun hero_uses_binary_units_when_asked() {
        val m = heroWidgetModel(snapshot().copy(units = UnitSystem.BINARY))
        assertEquals("GiB", m.unit)
    }
    @Test fun bento_forecast_uses_the_shared_sentence() {
        val f = Forecast(1, now.plusSeconds(86_400), now.plusSeconds(2 * 86_400), now.plusSeconds(3 * 86_400), 0, Confidence.MEDIUM, 14)
        assertEquals("runs out Mon (±1 day)", bentoWidgetModel(snapshot(forecast = f)).forecast)      // 12 Oct 2026 is a Monday
        assertEquals("Won't run out this cycle", bentoWidgetModel(snapshot(forecast = Forecast(1, null, null, null, 0, Confidence.HIGH, 28))).forecast)
    }
    @Test fun bento_forecast_without_a_plan_or_forecast_has_a_plain_fallback() {
        assertEquals("No plan yet", bentoWidgetModel(snapshot(plan = null)).forecast)
        assertEquals("Not enough history yet", bentoWidgetModel(snapshot(forecast = null)).forecast)
    }
    @Test fun bento_speed_says_offline_or_unknown_or_the_rate() {
        assertEquals("—", bentoWidgetModel(snapshot(speed = null)).speed)
        assertEquals("Offline", bentoWidgetModel(snapshot(speed = LiveSpeed(0, 0, null, now))).speed)
        assertEquals("↓ 4.20 MB/s", bentoWidgetModel(snapshot(speed = LiveSpeed(4_200_000, 100, NetworkKind.WIFI, now))).speed)
    }
    @Test fun bento_lists_the_top_apps_with_their_totals() {
        val apps = listOf(AppUsage("com.video", "Video", 10001, 600_000_000L, 10_000_000L, null), AppUsage("com.chat", "Chat", 10002, 50_000_000L, 0, null))
        assertEquals(listOf("Video · 610 MB", "Chat · 50.0 MB"), bentoWidgetModel(snapshot(apps = apps)).topApps)
    }
}
```
`WidgetDataTest.kt` (JVM; use `RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(now))`, a `FakeSettingsRepository`, and a small private `UsageRepository` fake you write in the test (`observeToday` returns `flowOf(DayUsage(...))`, `observeApps` returns a canned list, `observeLiveSpeed` returns `emptyFlow()` for the timeout case and `flowOf(LiveSpeed(...))` for the other; every other method returns an empty flow or `Outcome.Success(Unit)`)): (a) with no plan `plan` and `forecast` are null; (b) with a saved plan both are filled; (c) when the live-speed flow never emits, `load(..., speedTimeoutMillis = 50)` returns `speed == null` instead of hanging (use `runBlocking`); (d) the top apps are at most two and sorted by total descending; (e) `units` follows the settings.
`WidgetManifestTest.kt` (Robolectric): `queryBroadcastReceivers(Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName), 0)` contains `HeroWidgetReceiver` and `BentoWidgetReceiver`, both `exported`.

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement.**

Dependencies: in `libs.versions.toml` add `glance = "1.1.1"` under `[versions]` and
```toml
androidx-glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
```
In `app/build.gradle.kts`: `implementation(libs.androidx.glance.appwidget)` and `implementation(libs.androidx.glance.material3)`. `1.1.1` is only a starting point: if it does not resolve or does not compile against compileSdk 37 and the Compose BOM, use the newest STABLE `androidx.glance` release on Google Maven and record the version and the reason in your report. No other new dependency.

`WidgetData.kt`:
```kotlin
object WidgetData {
    suspend fun load(
        usage: UsageRepository, plans: PlanRepository, settings: SettingsRepository, clock: Clock,
        locale: Locale = Locale.getDefault(), speedTimeoutMillis: Long = 500L,
    ): WidgetSnapshot {
        val zone = clock.zone
        val today = usage.observeToday().first()
        val plan = plans.observeActivePlanStates().first().firstOrNull()
        val forecast = plan?.let { plans.observeForecast(it.plan.id).first() }
        val speed = withTimeoutOrNull(speedTimeoutMillis) { usage.observeLiveSpeed().first() }
        val start = today.date.atStartOfDay(zone).toInstant()
        val end = today.date.plusDays(1).atStartOfDay(zone).toInstant()
        val apps = usage.observeApps(DateRange(start, end)).first().sortedByDescending { it.totalBytes }.take(2)
        return WidgetSnapshot(today, plan, forecast, speed, apps, settings.observe().first().unitSystem, clock.instant(), zone, locale)
    }
}
```
`WidgetModels.kt`:
```kotlin
internal fun heroWidgetModel(s: WidgetSnapshot): HeroWidgetModel {
    val units = s.units.toByteUnits()
    val today = formatBytes(s.today.totalBytes, units)
    val caption = s.plan?.let { "${plainBytes(it.remainingBytes, units)} left" } ?: "Set up a plan"
    return HeroWidgetModel(today.value, today.unit, caption)
}

internal fun bentoWidgetModel(s: WidgetSnapshot): BentoWidgetModel {
    val units = s.units.toByteUnits()
    val forecast = when {
        s.plan == null -> "No plan yet"
        s.forecast == null -> "Not enough history yet"
        else -> forecastSentence(forecastToUi(s.forecast, s.now, s.zone, s.locale))
    }
    val speed = when {
        s.speed == null -> "—"
        s.speed.network == null -> "Offline"
        else -> "↓ ${plainBytes(s.speed.rxBps, units)}/s"
    }
    return BentoWidgetModel(heroWidgetModel(s), forecast, speed, s.topApps.map { "${it.label} · ${plainBytes(it.totalBytes, units)}" })
}
```
(`s.speed` is a `val` of a data class in the same module, so the smart casts compile; if the compiler objects, copy it into a local first.)

`HeroWidget.kt` / `BentoWidget.kt` (Glance; keep the composables thin, all text comes from the models):
```kotlin
class HeroWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as EmberbyteApplication).graph
        val model = heroWidgetModel(WidgetData.load(graph.usage, graph.plans, graph.settings, graph.clock))
        val open = actionStartActivity(DeepLinks.intent(context, DeepLinks.HOME))
        provideContent { GlanceTheme { HeroContent(model, open) } }
    }
}

class HeroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeroWidget()
}

@Composable
private fun HeroContent(model: HeroWidgetModel, open: Action) {
    val wide = LocalSize.current.width >= 250.dp
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Today", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(model.number, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (wide) 44.sp else 36.sp, fontWeight = FontWeight.Bold))
            Text(" ${model.unit}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
        }
        Text(model.caption, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp))
    }
}
```
`BentoWidget` is the same pattern with `SizeMode.Responsive(setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)))` and `BentoContent(model, open)`: a column with the hero block (number, unit, caption), a row with `Text(model.forecast)` and `Text(model.speed)`, and, when `LocalSize.current.height >= 200.dp`, one `Text` per `model.topApps` entry. `BentoWidgetReceiver` mirrors `HeroWidgetReceiver`.
`WidgetUpdater.kt`:
```kotlin
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        HeroWidget().updateAll(context)
        BentoWidget().updateAll(context)
    }
}
```
Resources: `strings.xml` add `widget_hero_label` ("Emberbyte: today"), `widget_hero_description` ("Today's data usage and what is left"), `widget_bento_label` ("Emberbyte: overview"), `widget_bento_description` ("Today, forecast, speed and top apps"). `res/xml/hero_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:description="@string/widget_hero_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:widgetCategory="home_screen" />
```
and `bento_widget_info.xml` the same with `minWidth="250dp"`, `minHeight="110dp"`, `targetCellWidth="4"`, `targetCellHeight="2"`, the bento description. Manifest, inside `<application>`:
```xml
        <receiver android:name=".widget.HeroWidgetReceiver" android:exported="true" android:label="@string/widget_hero_label">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/hero_widget_info" />
        </receiver>
        <receiver android:name=".widget.BentoWidgetReceiver" android:exported="true" android:label="@string/widget_bento_label">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/bento_widget_info" />
        </receiver>
```
`AppGraph.kt`: change `private val clock: Clock` to `val clock: Clock`. Refresh cadence (contract 10.3): in `SamplerService.supervise`'s catch-up loop call `runCatching { WidgetUpdater.updateAll(this@SamplerService) }` after `graph.prune()` (every 5 minutes); in `CatchUpWorker.doWork` the same after the alert run (every 15 minutes without the service). No other trigger.

- [ ] **Step 4: Run to verify they pass** — new tests green, `lintDebug` has 0 errors (add `tools:ignore` only with a written reason), full gate green. **Tester (emulator):** (1) `adb shell dumpsys appwidget | grep -i emberbyte` lists both providers; (2) try to add each widget from the launcher's widget picker (long-press home screen, Widgets, Emberbyte) by driving the UI with `adb shell input` and `uiautomator dump`; if the picker cannot be driven reliably say `CANNOT-VERIFY adding a widget` and rely on (1) plus the unit tests; (3) if a widget is placed: its text equals Home's today number and, with a plan, the remaining data; tapping it opens Home; after saving a plan the widget updates within 5 minutes (service running); the APK size and cold start are not visibly worse. Quote evidence.

- [ ] **Step 5: Commit**

```bash
git add gradle app
git commit -m "feat(app): add the hero and bento home-screen widgets

Both read the same repositories as the app and refresh with the catch-up cycle, so killing the app never blanks them."
```

---

### Task 19: M2 carry-over A (permission denial, 12/24-hour, History caveat, tile contrast, cold-start frame)

**Files (read each before editing):**
- `app/src/main/kotlin/$PKG/nav/PermissionActions.kt`, new `nav/PermissionRequestPolicy.kt`
- `app/src/main/kotlin/$PKG/home/HomeMapping.kt` (`latestGap`), `HomeViewModel.kt`, `AppGraph.kt`
- `app/src/main/kotlin/$PKG/history/History.kt`
- `ui/design/src/main/kotlin/$PKG/ui/design/tiles/BentoTile.kt`
- `app/src/main/kotlin/$PKG/MainActivity.kt`, `EmberbyteApp.kt`, new `placeholder/StartupPlaceholder.kt`
- Tests: new `nav/PermissionRequestPolicyTest.kt`, `placeholder/StartupPlaceholderTest.kt`, `ui/design/.../tiles/TileContrastTest.kt`; additions to `HomeMappingM2Test`/`HomeMappingM3Test` and `HistoryTest`

**What each item fixes (audit and QA ids refer to `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/m2-qa-report.md` and `…/2026-10-02-emberbyte-m2-fix-wave/m2-qa-rerun-report.md`; read the entry first):**
- **D-08.** After a permanent denial the "Allow" button does nothing the first time. New rule, pure and tested:
```kotlin
internal enum class PermissionDecision { Launch, OpenSettings }
internal object PermissionRequestPolicy {
    /** First request: ask. Later: ask again only while Android still shows the rationale, otherwise the system refuses silently, so go to settings. */
    fun decide(asked: Boolean, shouldShowRationale: Boolean): PermissionDecision =
        if (!asked || shouldShowRationale) PermissionDecision.Launch else PermissionDecision.OpenSettings
}
```
 `PermissionActions` uses it for notifications (API 33+) and phone state: `asked` is stored per permission in `SharedPreferences("permission_prefs")` and set when the launcher returns; `shouldShowRationale` comes from `ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)` (find the activity by unwrapping `LocalContext` through `ContextWrapper`). Below API 33 notifications always open the app notification settings.
- **D-11.** Times in the gap banner follow the device's 12/24-hour setting: `latestGap(coverage, now, zone, is24Hour: Boolean = true, locale: Locale = Locale.getDefault())` formats with `DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)`; `HomeViewModel` gets `is24Hour: () -> Boolean = { true }` and passes it through `buildHomeUiState`; `AppGraph.homeViewModelFactory` passes `{ DateFormat.is24HourFormat(appContext) }`.
- **D-14.** History: the "Without usage access only the last 7 days are available; older bars stay empty." caveat is shown ONLY when usage access is missing, and the "average per day" divides by the number of buckets that have data (label it "Average per active day"), not by all buckets.
- **Q2-04.** `BentoTile` titles (`foreground.copy(alpha = 0.72f)`) are 4.05:1 on the tonal card in dark theme. Extract `internal const val TILE_TITLE_ALPHA = 0.87f` and `internal fun tileColors(container: TileContainer, scheme: ColorScheme): Pair<Color, Color>` (background, foreground) from `BentoTile`, use both there, and prove the contrast.
- **D-22.** Cold start shows a plain frame for 1 to 2 s. New `StartupPlaceholder()` (a static skeleton shaped like Home: one 140 dp rounded block, a row of two 96 dp blocks, one 120 dp block, all `surfaceContainerHigh`, no animation) with semantics `contentDescription = "Loading Emberbyte"`; `MainActivity` shows it while the settings are still `null`, and `EmberbyteApp` shows it while the onboarding flag is still `null`.
- **O-2 (from the device QA re-run).** "Right after onboarding Home still showed the 'Show live usage / Allow' prompt although notifications were already granted." Reproduce it first on the emulator (clean install, grant notifications during onboarding, finish, look at Home). If it reproduces, find the cause and apply the smallest fix (candidate: call `graph.permissions.recheck()` when the Home destination resumes) with a test; if it does not reproduce in 3 attempts, say so with the evidence and change nothing.

- [ ] **Step 1: Write the failing tests.**
`PermissionRequestPolicyTest`: the four combinations: `(false,false)` Launch, `(false,true)` Launch, `(true,true)` Launch, `(true,false)` OpenSettings.
`HomeMappingM2Test` (or `HomeMappingM3Test`) additions for `latestGap`: with `Locale.US`, `UTC`, a gap from `2026-10-10T15:41:00Z` to `2026-10-10T15:47:00Z` ended 10 minutes before `now`: `is24Hour = true` gives `"Not measured 15:41–15:47 because the app was stopped"` and `is24Hour = false` gives `"Not measured 3:41 PM–3:47 PM because the app was stopped"`.
`TileContrastTest` (Robolectric Compose, capture the scheme inside `setContent { EmberbyteTheme(darkTheme = dark, dynamicColor = false) { scheme = MaterialTheme.colorScheme } }`; one test per theme because the rule allows one `setContent`):
```kotlin
private fun contrast(fg: Color, bg: Color): Double {
    val l1 = fg.compositeOver(bg).luminance() + 0.05
    val l2 = bg.luminance() + 0.05
    return maxOf(l1, l2) / minOf(l1, l2)
}
// for every TileContainer: val (bg, fg) = tileColors(container, scheme); assertTrue("$container", contrast(fg.copy(alpha = TILE_TITLE_ALPHA), bg) >= 4.5)
```
If 0.87 does not reach 4.5 for some container in some theme, raise `TILE_TITLE_ALPHA` to the smallest value that does (try 0.92, then 1.0) and say which.
`StartupPlaceholderTest`: the node with content description "Loading Emberbyte" exists.
`HistoryTest`: read the existing tests first. Add: `the_caveat_is_hidden_when_usage_access_is_granted`, `the_caveat_is_shown_without_usage_access`, `the_average_counts_only_days_with_data` (2 days with data in a 14-day range: the average is the sum divided by 2). Existing `HistoryTest` expectations that encode "caveat always shown" or "average over all buckets" change; list each in your report.

- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement** as described in the list above.
- [ ] **Step 4: Run to verify they pass** — full gate green. **Tester (emulator):** (D-08) deny notifications twice during onboarding, tap "Allow notifications": the system settings screen opens at once; (D-11) set `adb shell settings put system time_12_24 12`, create a gap (stop and restart the service for more than 5 minutes) and read the Home banner in 12-hour form, then restore the setting; (D-14) with usage access granted History shows no caveat and the average is not tiny; (Q2-04) pixel-measure the three tile labels in dark theme (≥ 4.5:1); (D-22) `adb shell am start -W` then `screencap` at about 300 ms: the placeholder blocks are visible, not a blank frame; (O-2) as above.
- [ ] **Step 5: Commit**

```bash
git add app ui
git commit -m "fix: ask for permissions again only when Android will show the dialog, follow the 12/24-hour setting, tighten tile contrast

Carry-over minors of M2: no silent no-op button, honest History caveat and average, readable tile labels, no blank frame at start."
```

---

### Task 20: M2 carry-over B (content width, touch targets, release manifest, gap rows)

**Files (read each before editing):**
- `ui/design/src/main/kotlin/$PKG/ui/design/tiles/UsageBarRow.kt`, new `ui/design/.../layout/ContentWidth.kt`
- `app/src/main/kotlin/$PKG/EmberbyteApp.kt` (apply the width limit to the NavHost and the navbar)
- `core/data/src/main/kotlin/$PKG/data/RoomUsageRepository.kt` (`observeCoverage`), `core/data/.../sampler/SamplerEngine.kt` (restart gap)
- Tests: new `ui/design/.../layout/ContentWidthTest.kt`; additions to `ui/design/.../tiles/TilesTest.kt` (or a new `UsageBarRowTest`), `core/data/.../RoomUsageRepositoryTest.kt`, `core/data/.../sampler/SamplerEngineTest.kt`

**What each item fixes:**
- **D-16.** Landscape and tablets ignore the 600 dp maximum content width of frontend spec section 3. New `fun Modifier.contentWidth(): Modifier = this.widthIn(max = 600.dp)` in `:ui:design`; `EmberbyteApp` wraps the `NavHost` in `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter)` and gives the `NavHost` `Modifier.contentWidth().fillMaxHeight().glassSource(glass)`; the `FloatingPillNavBar` gets `.contentWidth()` before its horizontal padding (it stays bottom-centred).
- **D-17.** Bars are narrower than 48 dp. `UsageBarRow` bars get `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` for their clickable area (the drawn bar can stay narrower inside it); when the row has more bars than fit, the row scrolls horizontally (`horizontalScroll`) instead of shrinking the bars.
- **D-20.** `ui-test-manifest` puts an exported `androidx.activity.ComponentActivity` into the DEBUG APK. It is already a `debugImplementation`; only VERIFY: `./gradlew :app:processReleaseManifest` and `grep -c "androidx.activity.ComponentActivity" app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` must print `0`. Record the command output; change code only if it prints more than 0.
- **N-5.** Gap rows pile up and `observeCoverage()` loads 395 days of gaps on every insert. `RoomUsageRepository.observeCoverage` now loads only the last 31 days (the Home banner needs 24 hours; plan approximation reads the store directly and is unaffected): `val from = clock.instant().minus(Duration.ofDays(31))`.
- **Q2-02.** Two restarts within the first minute record overlapping `SERVICE_KILLED` gaps (`04:44:00-04:51:07` and `04:44:00-04:51:30`). Read `SamplerEngine.start()`: the second restart reads the OLD checkpoint (it is only saved at flush), so it records the whole span again. Fix: save the checkpoint right after recording the restart gap in `start()`, so a second restart's gap begins where the first one ended.

- [ ] **Step 1: Write the failing tests.**
`ContentWidthTest` (Robolectric Compose): a `Box(Modifier.size(1200.dp, 400.dp))` containing `Box(Modifier.contentWidth().fillMaxHeight().testTag("content"))`: `onNodeWithTag("content").assertWidthIsEqualTo(600.dp)`; and with a `Box(Modifier.size(360.dp, 400.dp))` the node is `360.dp` wide.
`UsageBarRow` test: 14 `BarUi` bars in a 360 dp wide container: every bar node (find them through their content descriptions) satisfies `assertWidthIsAtLeast(48.dp)` and `assertHeightIsAtLeast(48.dp)`, and the row can scroll (the last bar exists in the tree and `performScrollTo()` works); 7 bars still fit without scrolling.
`RoomUsageRepositoryTest` additions: a `SERVICE_KILLED` gap 40 days before `now` is NOT in `observeCoverage().first().gaps`, one 10 days before is.
`SamplerEngineTest` addition: find the existing test that asserts a `SERVICE_KILLED` gap on restart and copy its set-up; start the engine twice without a flush in between (the second start 20 s after the first, both more than 90 s after the stored checkpoint): the two gap rows do not overlap (`second.from >= first.to`).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement** as described.
- [ ] **Step 4: Run to verify they pass** — full gate green; the D-20 command output is quoted in the report. **Tester (emulator):** (D-16) `adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1` (landscape): Home content is at most 600 dp wide and centred, the navbar is centred, and nothing is cut off; restore `user_rotation 0` and `accelerometer_rotation 1`; (D-17) Home week bars and History day bars measured with `uiautomator dump` bounds are at least 48 dp (use `adb shell wm density` to convert); (Q2-02) kill the service twice within a minute (`adb shell am force-stop` then reopen, twice) and read `coverage_gap` from a copy of the database (`adb exec-out run-as io.github.khaledbahaaeldin.emberbyte cat databases/emberbyte.db > local.db`, read it with Python's `sqlite3`): no overlapping `SERVICE_KILLED` rows.
- [ ] **Step 5: Commit**

```bash
git add app ui core
git commit -m "fix: keep content within 600 dp, give bars 48 dp targets, stop duplicate restart gaps

Carry-over minors of M2, each with a test."
```

---

### Task 21: Final proof, docs and an honest completion report

Do not change production code in this task; report defects with evidence (a defect found here goes back to the owning task, with a fix round, before this task finishes).

**Files:**
- Modify: `README.md`
- Create: `docs/manual-checks/dual-sim-m3.md`
- Create (git-ignored, not committed): `.superpowers/sdd/2026-10-04-emberbyte-m3-plans/m3-completion-report.md`

- [ ] **Step 1: Full gate with no cache.** `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace` → `BUILD SUCCESSFUL`. Record the test count per module exactly as printed, the lint error and warning counts per module with each warning's id and location, and the Kotlin compiler warning count (production and test, as printed). Confirm `git diff --stat main..HEAD -- core/data/schemas` shows ONLY a new `2.json` and no change to `1.json`.

- [ ] **Step 2: README.** Update the feature list (plans with per-SIM binding, add-on packs, free windows, rollover, run-out forecast with a range, threshold alerts, widgets) and add a "Known limitations" section with exactly these true statements, nothing invented: new traffic can take up to about 5 minutes to appear in Today (O-1); "Measurement paused" only appears after the system or a vendor kills the service, and force-stopping Emberbyte stops measuring until it is opened again (N-10); the forecast ignores free-window traffic in its history and uses a built-in hour-of-day curve; per-SIM history is estimated when more than one SIM was used (backend spec section 5); a VPN running over Wi-Fi is counted twice.

- [ ] **Step 3: Manual dual-SIM checklist.** Write `docs/manual-checks/dual-sim-m3.md` for the project owner (the emulator has one SIM): numbered steps to run on a real dual-SIM phone: install, allow phone access from the plan editor, create one plan per SIM, switch the default data SIM in system settings, use data on each, and check which plan grows, that a plan showing both SIMs in its window is labelled "Estimated", that SIM names and slots in the editor list match the system settings, and that denying phone access still offers "Default data SIM". Each step has the expected result.

- [ ] **Step 4: Emulator proof** (AVD `Violet_API_36`; `PKG=io.github.khaledbahaaeldin.emberbyte`; `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`; use short-path worktrees such as `C:/dkbm2` because Git Bash cannot delete long paths; grant usage access with `adb shell appops set $PKG GET_USAGE_STATS allow` and notifications with `adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS`). Each item is PASS, FAIL or CANNOT-VERIFY with quoted evidence (command output, `uiautomator` text, a screenshot path):
  1. **Upgrade from M2 keeps the data.** Build the M2 APK from commit `ae9bf29` in a worktree, install it, finish onboarding, let it measure for at least 10 minutes with some traffic, note Home's Today. `adb install -r` the M3 APK, open it: no crash (`logcat -d | grep -E "AndroidRuntime|FATAL"` empty), Today unchanged within normal growth, the Plans tab opens. Copy the database (`adb exec-out run-as $PKG cat databases/emberbyte.db > local.db`) and with Python `sqlite3` print `PRAGMA user_version` (must be `2`) and the row counts of `total_minute`, `usage_hourly` and the five new tables.
  2. **Plans end to end.** Create a plan (name, 5 GB cap, day of month 12, SIM "Not tied"), see the card, Home subtitle and the notification text with "left"; edit it (add-on pack, free window, rollover Capped, thresholds); archive it and see it leave Home; the editor validation errors for an empty cap and for day 0 (type 0 in "Day of month").
  3. **Numbers match the system.** `adb shell svc wifi disable` so traffic uses mobile data, fetch about 20 MB, compare the plan's used bytes with `adb shell dumpsys netstats` mobile bytes of the same period: within 5 %; re-enable Wi-Fi at the end.
  4. **Alerts.** Create a plan with a 30 MB cap and thresholds 50 and 100; generate mobile traffic in two steps (about 16 MB, then about 16 MB more). Exactly one notification per threshold: `adb shell dumpsys notification --noredact | grep -c "plan-alert"` is 1 after the first step and 2 after the second (two different ids/texts), a third check after `adb shell am force-stop $PKG` and reopen shows no repeat, and tapping the notification opens that plan's editor.
  5. **Widgets.** As in task 18 (providers registered; place and read them if the launcher allows it; say CANNOT-VERIFY otherwise).
  6. **Deep links.** `emberbyte://home`, `apps`, `plans`, `plans/<id>`, `settings`, `lens` open the right screens, also with the app already open.
  7. **Zone independence.** Note a plan's "days left"; `adb shell cmd alarm set-timezone Asia/Kolkata`, wait 60 s: the plan's window and days left are unchanged (a plan keeps the zone it was created in) while Home's Today still follows the device zone as in M2; restore the original zone.
  8. **First catch-up with the 28-day backfill.** After a clean install with usage access granted, the log line `catch-up finished in N ms` (task 7) shows N below 60 000; if not, report it as a defect against task 7 (candidate fix: 14 days).
  9. **Regression sweep.** 10 taps on each tab with 0 bounces; dark theme and AMOLED (`(0,0,0)` pixel); Arabic digits on the plan card (`adb shell setprop persist.sys.locale ar-EG` or the system language setting, then restore); font scale 2.0 on Plans and the editor; the M2 carry-over items of tasks 19 and 20 as their tasks describe; `logcat -d | grep -E "AndroidRuntime|FATAL"` is empty at the end.
  10. **Cannot verify here:** dual SIM, Android 16 live updates and any OEM battery behaviour: write exactly that, with the pointer to `docs/manual-checks/dual-sim-m3.md`.
  Restore every device setting you changed (time zone, rotation, Wi-Fi, 12/24-hour, locale, font scale, density) and stop the emulator (`adb emu kill`).

- [ ] **Step 5: Completion report.** Write `.superpowers/sdd/2026-10-04-emberbyte-m3-plans/m3-completion-report.md` containing: the task table (id, commit range, what changed, tester verdict, and "self-verified, no separate tester report" wherever that is the truth); the exact gate numbers of Step 1; every deviation from this plan with the reason; the version of Glance actually used; the Step 4 results; the deferred items, listing exactly: audit minors M-1, M-4, M-7 to M-9, M-11, M-13 to M-21, QA D-10, N-6, Q2-05, F-6, F-7, F-8, O-1 (documented), and VPN-over-Wi-Fi (M5); and a short "Corrections" section for anything an earlier task report got wrong. Rules for this report: quote only things you ran; use real test names (`grep` them); say "not run" when something was not run; do not describe a state as "immediately" when it takes a poll or a refresh cycle. The two completion reports of M2 contained false statements that the audit found; this one will be checked line by line.

- [ ] **Step 6: Commit the tracked changes and stop.**

```bash
git add README.md docs/manual-checks
git commit -m "docs: describe plans and widgets, list known limits, add the dual-SIM manual check

The limitations section only states what is true today."
```
Then `git status` shows only Claude-owned docs untracked or modified, nothing was pushed, you are on `m3-plans-forecast-widgets`. Print a short summary (commits, test counts, deviations, open concerns) and say: **"M3 is ready for the Claude audit on branch m3-plans-forecast-widgets."** Do not merge, push or start M4.

---

## Self-review (done by the plan's author)

**Spec coverage.** Design M3 = plan editor (tasks 15-16), dual-SIM handling (4-8, 15-16, manual check in 21), alerts (9, 11), forecast v1 (3, 7, 13), widgets (18). Backend section 4 (cycles, plan state, rollover, free windows) = tasks 1-2; section 5 (dual SIM, honesty flag) = 6, 8; section 6 (forecast) = 3; section 9 (notification, widgets) = 17, 18; section 11 (invalid cycle, READ_PHONE_STATE missing) = 6-8. Frontend 6.3 (Plans cards, editor sections, live preview) = 12, 14-16; 6.6 (onboarding step 3) = 16; section 7 (notification text with remaining data, widgets) = 17-18; section 8 (forecast wording) = 13. Contract 10 is implemented one-to-one; contract section 6 deep links = 10.
**Not in this plan on purpose:** budgets, spikes, what-if UI, export and backup (M4); Live Lens (M5); the personal hour-of-day curve (M4).
**Consistency of names** (checked): `previewState`, `PlanPreview`, `planCardUi`, `forecastSentence`, `ForecastKind`, `PlanChipUi`, `AlertNotice`, `AlertEvaluator`, `AlertRunner`, `SimInfoSource`, `WidgetSnapshot`, `DeepLinks`, `Routes.planEditor` are defined in the task that first needs them and used with the same signatures later.
**Known risks the executing agents must watch:** the Room migration SQL must be copied from the generated `2.json`; Glance and Material 3 alpha APIs may need small signature adjustments; `FlowRow`/`SegmentedButton` need their opt-ins; every deviation goes in the report.
