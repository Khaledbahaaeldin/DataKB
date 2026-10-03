# Emberbyte M2 Fix Wave 2 Implementation Plan (the last one before M2 closes)

> **For the executing agents (Antigravity):** follow the protocol in `docs/superpowers/plans/2026-10-03-m2-fixwave2-antigravity-prompt.md`
> (same implementer + fresh tester loop as before). Steps use checkbox (`- [ ]`) syntax. Do the tasks in order: G1, G2, G3, G4.

**Goal:** Close the two Important defects that the re-audit found in the first fix wave, plus a few cheap Minors, so that M2 passes.

**Why:** The Opus re-audit (`.superpowers/sdd/2026-10-02-emberbyte-m2-fix-wave/m2-reaudit-report.md`) and the device QA re-run
(`m2-qa-rerun-report.md`) confirmed that the first fix wave fixed both Criticals on the device (0 navigation bounces in 76 taps; Home matches the
system's own totals) and 26 of 30 findings. Two Important defects remain, both in the date logic:

- **N-1 (audit):** `SeriesBuilder` shared a window's surplus between its two hours by "how little the sampler covered each". An hour that has not
  started yet has coverage 0, so it got the LARGEST share. In every time zone whose offset is odd or has a half hour (Cairo in summer, CET, Kolkata ...)
  a day boundary splits a 2-hour window, so "today" started with yesterday's bytes and then FELL (39.8 MB at 00:01, 0 MB at 01:05). Home also showed less
  than Apps for the same day.
- **N-2 (audit) = Q2-01 (QA):** `DayClock` re-emitted only when the DATE changed. After a time-zone change on the same date (for example Cairo to London),
  the ranges stayed in the old zone while the totals were bucketed in the new one, so Home and the notification showed a stale slice and froze; the week
  strip grew to 8 bars. The first fix wave's device check only moved the zone eastward, which happens to land on the right day.

Read the corrected contract wording: `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` section 9.1 (`SeriesBuilder` KDoc: the surplus goes on the
window's FIRST hour) and section 9.2 (`DayClock` also re-emits after a zone change). Specs were updated by Claude; do not edit them.

## Global Constraints (unchanged)

- minSdk 29, compileSdk 37, targetSdk 36, Java/Kotlin 17; no version changes, no new libraries; module rules as before (`:ui:design` has no `:core:*`,
  `:core:engine` pure Kotlin, no Room/DataStore types in `:app`, no Fakes in production code).
- Test-first. **The tests named in each task encoded the OLD rule; change exactly those and say so.** Never weaken, skip or `@Ignore` any other test.
- Identity check before every commit (`Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`), WHY-style messages, `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
  Branch `m2-real-usage`. Never push, merge, force or skip hooks.
- Gate after every task: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`; the last task uses `--no-build-cache`.
- Room schema must stay unchanged (`git diff --stat core/data/schemas` empty).

---

### Task G1: Book a window's surplus on its first hour (audit N-1)

**Files:**
- Modify: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/SeriesBuilder.kt`
- Modify (tests): `core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/SeriesBuilderTest.kt`

**Interfaces:** `SeriesBuilder.build` keeps its signature. New rule (contract 9.1): per (2-hour window, network) `total = max(minute sum, system window sum)`; the surplus
`total - minuteSum` is booked on the window's **first** hour, never on the second. The sampler-coverage bookkeeping is no longer needed and is removed.
This is also the rule the per-app rows already use (a window row belongs to the range that contains its window start), so Home >= Apps at a day boundary.

- [ ] **Step 1: Update and add the tests FIRST** (`SeriesBuilderTest.kt`).

  Change exactly these existing tests (they encoded the equal / coverage-weighted sharing):
```kotlin
    @Test fun an_hour_belongs_to_the_bucket_that_contains_its_start_instant() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30: local 7 Oct starts at 2026-10-06T18:30Z
        // The whole window 18:00Z-20:00Z is booked on its first hour 18:00Z, which started BEFORE local midnight: it belongs to yesterday.
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata,
            minuteRows = emptyList(),
            hourlyRows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.MOBILE, 1_000)),
        )
        assertEquals(0L, points.single().mobileBytes)
    }

    @Test fun a_window_only_the_system_knows_is_booked_on_its_first_hour() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T10:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(300L, 0L), points.map { it.mobileBytes })
    }

    @Test fun hours_that_start_before_the_range_are_ignored_even_when_their_window_overlaps_it() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T11:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(0L), points.map { it.mobileBytes })   // the first hour (10:00Z) carries all 300 and is outside the range
    }
```
  and rename `the_surplus_goes_to_the_hour_the_sampler_covered_less` to `the_surplus_is_booked_on_the_first_hour` (its expected values `[400, 600]` stay the same).
  Keep every other test unchanged. Then add:
```kotlin
    private val cairo: ZoneId = ZoneId.of("Africa/Cairo") // UTC+3 on 2026-10-01/02: local midnight is 21:00Z, in the MIDDLE of the window 20:00Z-22:00Z

    private fun cairoDay(day: String, minutes: List<MinuteTotal>, system: List<HourlyUsage>): Long {
        val from = i("${day}T21:00:00Z").minusSeconds(86_400)                      // local midnight of `day`
        val range = DateRange(from, from.plusSeconds(86_400))
        return SeriesBuilder.build(range, Granularity.DAY, cairo, minutes, system).single().mobileBytes
    }

    @Test fun today_never_decreases_across_a_day_boundary_that_splits_a_window() {
        // 60 MB arrived while the sampler slept, 20:00Z-20:30Z (23:00-23:30 Cairo, the evening of Oct 1). The system window row says 60 MB.
        val system = listOf(hourly("2026-10-01T20:00:00Z", 10001, NetworkKind.MOBILE, 60_000_000))
        val zeros = (30 until 60).map { minute("2026-10-01T20:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 0) } +
            (0 until 60).map { minute("2026-10-01T21:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 0) }
        // "today" is Oct 2 (Cairo). Sample it at several moments by adding more zero rows: it must stay 0 and never go negative or fall.
        var previous = -1L
        for (upTo in listOf(0, 1, 30, 59, 60)) {
            val rows = zeros.filter { it.minuteStart <= i("2026-10-01T21:00:00Z").plusSeconds(upTo * 60L) }
            val today = cairoDay("2026-10-02", rows, system)
            assertTrue("today=$today previous=$previous", today >= previous)
            previous = today
        }
        assertEquals(0L, previous)
    }

    @Test fun yesterdays_evening_burst_stays_in_yesterday_and_matches_the_apps_rule() {
        val system = listOf(hourly("2026-10-01T20:00:00Z", 10001, NetworkKind.MOBILE, 60_000_000))
        assertEquals(60_000_000L, cairoDay("2026-10-01", emptyList(), system))   // Home "yesterday" == the per-app window row
        assertEquals(0L, cairoDay("2026-10-02", emptyList(), system))
    }

    @Test fun traffic_after_midnight_inside_the_split_window_moves_from_yesterdays_surplus_to_today() {
        val system = listOf(hourly("2026-10-01T20:00:00Z", 10001, NetworkKind.MOBILE, 60_000_000))
        val after = listOf(minute("2026-10-01T21:10:00Z", NetworkKind.MOBILE, 10_000_000))   // 00:10 Cairo, Oct 2
        assertEquals(10_000_000L, cairoDay("2026-10-02", after, system))      // today gets exactly what was measured in its hour
        assertEquals(50_000_000L, cairoDay("2026-10-01", after, system))      // yesterday keeps the rest; the sum is still 60 MB
    }

    @Test fun a_half_hour_zone_boundary_behaves_the_same_way() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val system = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.WIFI, 1_000))   // first hour 18:00Z = 23:30 IST, yesterday
        val today = SeriesBuilder.build(DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata, emptyList(), system).single()
        assertEquals(0L, today.wifiBytes)
    }
```
  (`assertTrue` is already imported in this test file; add the import if it is not.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test --tests "*SeriesBuilderTest"` → FAIL (the old sharing rule).

- [ ] **Step 3: Implement.** In `SeriesBuilder.kt`:
  1. delete the whole `coveredMinutes` block (the `HashMap<Instant, HashSet<Instant>>` and its loop) and the private function `surplusShares`;
  2. remove the import `kotlin.math.roundToLong`;
  3. replace the body of `for (window in windows) { ... }` with
```kotlin
        for (window in windows) {
            val hours = listOf(window.hour, window.hour.plus(1, ChronoUnit.HOURS))
            val minuteValues = hours.map { minuteByHour[NetworkHour(it, window.network)] ?: 0L }
            val minuteSum = minuteValues.sum()
            val total = maxOf(minuteSum, systemByWindow[window] ?: 0L)
            // The surplus (system bytes the sampler did not see) is booked on the window's FIRST hour: the same window-start rule the
            // per-app rows use, and never on an hour that has not started yet (that made "today" start high and then fall).
            val values = longArrayOf(minuteValues[0] + (total - minuteSum), minuteValues[1])
            hours.forEachIndexed { i, hour ->
                val value = values[i]
                if (value > 0L && hour >= first && hour < range.to) {
                    val index = indexFor(starts, hour)
                    if (window.network == NetworkKind.MOBILE) mobile[index] += value else wifi[index] += value
                }
            }
        }
```
  4. update the KDoc of `build` to say "the surplus over the minute sum is booked on the window's first hour".

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL (the `:core:data` repository tests that sum windows must still pass; if one encoded the equal sharing, change only that expectation and say so).

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "fix(engine): book a window's surplus on its first hour so today never falls

Giving surplus to the less-covered hour sent bytes to an hour that had not started, so in odd and half-hour zones today began with yesterday's bytes and then decreased."
```

---

### Task G2: Zone-correct days (audit N-2, QA Q2-01)

**Files:**
- Modify: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/DayClock.kt`, `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/RoomUsageRepository.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeViewModel.kt`, `history/History.kt`
- Create (test helper): `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/MutableZoneClock.kt`
- Modify (tests): `core/data/src/test/.../util/DayClockTest.kt`, `core/data/src/test/.../RoomUsageRepositoryTest.kt`

**Interfaces:** `DayClock.dates()` additionally re-emits the same date after the clock's time zone changed (contract 9.2). `RoomUsageRepository` captures the zone ONCE per call/emission and uses that one zone for the range, the padding and `SeriesBuilder.build`; `observeToday` selects the bucket whose `start == from`.

- [ ] **Step 1: Write the failing tests.**

`MutableZoneClock.kt` (in `src/test`):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A test clock that follows the scheduler's virtual time and whose zone can be changed while a test runs. */
class MutableZoneClock(
    private val scheduler: TestCoroutineScheduler,
    private val base: Instant,
    var zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableZoneClock(scheduler, base, zone)
    override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
}
```
`DayClockTest.kt` — add:
```kotlin
    @Test fun the_same_date_is_emitted_again_after_a_time_zone_change() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), java.time.ZoneId.of("Africa/Cairo")) // Oct 2, 03:30
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(clock, pollMillis = 30_000).dates().toList(seen) }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 2)), seen)
        clock.zoneId = java.time.ZoneId.of("Europe/London")                 // Oct 2, 01:30: the SAME date in a new zone
        advanceTimeBy(31_000); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2)), seen)
        job.cancel()
    }

    @Test fun nothing_is_emitted_again_while_neither_date_nor_zone_changes() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T12:00:00Z"), java.time.ZoneOffset.UTC)
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(clock, pollMillis = 30_000).dates().toList(seen) }
        runCurrent(); advanceTimeBy(5 * 60_000); runCurrent()
        assertEquals(1, seen.size)
        job.cancel()
    }
```
`RoomUsageRepositoryTest.kt` — add (keep the others):
```kotlin
    @Test fun today_follows_a_time_zone_change_on_the_same_date() = runTest {
        val cairo = java.time.ZoneId.of("Africa/Cairo")
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), cairo) // Oct 2 in both Cairo (03:30) and London (01:30)
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(Instant.parse("2026-10-01T22:10:00Z"), NetworkKind.MOBILE, -1, 5_000_000, 0)) // Cairo: today (01:10 Oct 2); London: yesterday
        store.addMinute(MinuteTotal(Instant.parse("2026-10-02T00:10:00Z"), NetworkKind.MOBILE, -1, 7_000_000, 0)) // today in both
        val repo = RoomUsageRepository(store, flowOf(live), DayClock(clock, pollMillis = 30_000), clock) { catchUp }
        val seen = mutableListOf<Long>()
        val job = launch { repo.observeToday().collect { seen += it.totalBytes } }
        runCurrent()
        assertEquals(12_000_000L, seen.last())                              // Cairo day
        clock.zoneId = java.time.ZoneId.of("Europe/London")
        advanceTimeBy(31_000); runCurrent()
        assertEquals(7_000_000L, seen.last())                               // London day: exactly the London day, not a slice of yesterday
        clock.zoneId = cairo
        advanceTimeBy(31_000); runCurrent()
        assertEquals(12_000_000L, seen.last())
        job.cancel()
    }

    @Test fun the_week_strip_never_grows_beyond_its_range_after_a_zone_change() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), java.time.ZoneId.of("Africa/Cairo"))
        val repo = RoomUsageRepository(InMemoryUsageStore(), flowOf(live), DayClock(clock), clock) { catchUp }
        clock.zoneId = java.time.ZoneId.of("Europe/London")
        val from = Instant.parse("2026-09-25T23:00:00Z")                   // 7 London days
        val bars = repo.observeSeries(DateRange(from, from.plusSeconds(7 * 86_400L)), Granularity.DAY).first()
        assertEquals(7, bars.size)
    }
```
(imports: `kotlinx.coroutines.test.runTest`, `runCurrent`, `advanceTimeBy`, `kotlinx.coroutines.launch`, `MutableZoneClock`, `MinuteTotal`; the file already has `flowOf`, `first`.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*DayClockTest" --tests "*RoomUsageRepositoryTest"` → FAIL.

- [ ] **Step 3: Implement.**

`DayClock.kt` (replace the `dates()` function):
```kotlin
    fun dates(): Flow<LocalDate> = flow {
        var last: Pair<LocalDate, ZoneId>? = null
        while (true) {
            val zone = clock.zone                                  // read once, so the pair is consistent
            val current = clock.instant().atZone(zone).toLocalDate() to zone
            if (current != last) {                                 // the date OR the zone changed
                emit(current.first)
                last = current
            }
            delay(pollMillis)
        }
    }
```
(imports `java.time.ZoneId`; remove `distinctUntilChanged` and `LocalDate.now(clock)`; update the KDoc: "...and again when the time zone changes, even on the same date".)

`RoomUsageRepository.kt`: add a private function and rewrite the three series-based members to capture the zone once:
```kotlin
    private fun series(range: DateRange, granularity: Granularity, filter: UsageFilter, zone: ZoneId): Flow<List<UsagePoint>> {
        val padded = SeriesBuilder.bucketStart(range.from, granularity, zone).minusSeconds(SeriesBuilder.WINDOW_SECONDS)
        return combine(store.observeMinuteRows(padded, range.to), store.observeHourlyRows(padded, range.to)) { minutes, hours ->
            SeriesBuilder.build(range, granularity, zone, minutes, hours, filter)
        }
    }

    override fun observeToday(filter: UsageFilter): Flow<DayUsage> = dayClock.dates().flatMapLatest { date ->
        val zone = this.zone                                       // one zone for the whole emission
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        series(DateRange(from, to), Granularity.DAY, filter, zone).map { points ->
            val point = points.firstOrNull { it.start == from }
            DayUsage(date, point?.mobileBytes ?: 0L, point?.wifiBytes ?: 0L)
        }
    }

    override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> =
        series(range, granularity, filter, zone)                   // captured when the flow is created
```
and in `observeAppSeries` capture `val zone = this.zone` at the top and use it in both the padding and the `combine` body.

`HomeViewModel.kt`: add `val zone: ZoneId` to the private `HomeData` class; inside `dates.flatMapLatest { ... }` the existing `val zone = clock.zone` is put into `HomeData(..., zone = zone)`; and `buildHomeUiState(... zone = b.data.zone ...)` uses it instead of `clock.zone` (the weekday labels now use the zone the data was computed in).
`history/History.kt`: inside `flatMapLatest { (date, g) -> ... }` capture `val zone = clock.zone` once and use it for `rangeForHistory(g, date, zone)` AND `buildHistoryUiState(g, points, units, zone, locale)`.
(`AppDetailViewModel` already captures its zone once; `AppsViewModel` uses `clock.zone` only for the range, inside `flatMapLatest`: leave it.)

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "fix(data): recompute the day when the time zone changes, not only the date

The day clock ignored zone changes on the same date, so ranges stayed in the old zone while totals were bucketed in the new one and Home froze on a slice of yesterday."
```

---

### Task G3: Small cleanups from the re-audit (N-3, N-4, N-7, Q2-03)

**Files:** `app/.../history/History.kt`, `app/.../home/HomeScreen.kt` (+ `EmberbyteApp.kt` call site), `core/data/.../sampler/SamplerEngine.kt`, `app/.../apps/AppsScreen.kt`; tests `core/data/.../sampler/SamplerEngineTest.kt`, `app/.../apps/AppsContentTest.kt`, the existing Home tests.

- [ ] **Step 1: Write the failing tests.**
  * `SamplerEngineTest.kt`: a write that throws must not lose the minute.
```kotlin
    @Test fun a_failed_write_keeps_the_minute_so_the_next_flush_retries_it() = runTest {
        val real = InMemoryUsageStore()
        var failOnce = true
        val flaky = object : UsageStore by real {
            override suspend fun addMinute(row: MinuteTotal) {
                if (failOnce) { failOnce = false; error("disk full") }
                real.addMinute(row)
            }
        }
        val e = SamplerEngine(
            FakeCounterSource(listOf(snap(0), snap(30, mobRx = 100), snap(61, mobRx = 300))),
            FakeNetworkKindSource(NetworkKind.MOBILE), FakeSubscriptionSource(7), flaky,
        )
        e.start(); e.tick()
        runCatching { e.tick() }                                      // the minute rolls over: the flush throws once
        e.stop()                                                      // the pending minutes are written now
        assertEquals(300L, real.rows().filter { it.network == NetworkKind.MOBILE }.sumOf { it.rxBytes })
    }
```
    (imports: `io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore`, `io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal`.)
  * `AppsContentTest.kt`: `fun a_search_without_matches_says_so()`: `AppsUiState(query = "zzz", apps = emptyList(), loaded = true)` shows the text `No apps match "zzz".` (use straight quotes).
  * `HomeContentTest` / `HistoryTest`: adapt the constructors to the new signatures (below) without changing assertions.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*SamplerEngineTest" :app:testDebugUnitTest --tests "*AppsContentTest"` → FAIL.

- [ ] **Step 3: Implement.**
  * N-7, `SamplerEngine.flush`: write first, remove afterwards:
```kotlin
        for (key in closed) {
            val entry = pending.getValue(key)
            store.addMinute(MinuteTotal(key.minuteStart, key.network, key.subscriptionId, entry.rx, entry.tx))
            pending.remove(key)                                    // only after the write succeeded
        }
```
  * N-3: in `HistoryViewModel` remove the default value of the `permissions` parameter (`permissions: PermissionRepository,`) and the import of `FakePermissionRepository` from production code; `AppGraph` already passes the real one.
  * N-4: in `HomeScreen` and `HomeContent` make `onOpenApp: (String) -> Unit` a REQUIRED parameter placed before `modifier` (no `= {}` default) and move `modifier: Modifier = Modifier` to be the last parameter; update the call in `EmberbyteApp.kt` and the tests. The two `ModifierParameter` lint warnings must disappear (`./gradlew :app:lintDebug` shows 6 warnings or fewer).
  * Q2-03: in `AppsContent`, the empty-state branch becomes
```kotlin
        if (state.loaded && state.apps.isEmpty()) {
            item {
                Text(
                    if (state.query.isNotBlank()) "No apps match \"${state.query.trim()}\"."
                    else "No app data yet. Per-app numbers appear within about 5 minutes once usage access is allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
```
    (keep the `else` branch with the list as it is; the existing empty-state test must still find "No app data yet" when the query is blank).

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "fix: keep a minute whose write failed, drop dead defaults, say when a search finds nothing

Small re-audit findings: no Fake in production defaults, a required app-open callback, and honest empty-state wording."
```

---

### Task G4: Final proof and an honest completion report

Do not change production code here; report defects with evidence.

- [ ] **Step 1: Full gate, no cache.** `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace` → BUILD SUCCESSFUL. Record the per-module test counts and the lint error/warning counts exactly as printed. Confirm `git diff --stat 6a5a8f5..HEAD -- core/data/schemas` is empty.

- [ ] **Step 2: Emulator proof** (AVD `Violet_API_36`, `PKG=io.github.khaledbahaaeldin.emberbyte`, start from `adb shell pm clear $PKG` or a clean install, grant usage access with `adb shell appops set $PKG GET_USAGE_STATS allow` and notifications with `adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS` once the onboarding screen has been shown). PASS / FAIL / CANNOT-VERIFY with quoted evidence for each:
  1. **Westward and eastward zone changes on the SAME date (N-2).** Note the Home "Today", the History Day bar for today and the week-strip bar count. `adb shell cmd alarm set-timezone Europe/London` (a westward move from Cairo), wait 60 s, reopen the app: Home "Today" == the History Day bar for today == the week-strip bar for today; the strip has exactly 7 bars; the live notification title shows the same total; traffic generated afterwards makes all of them grow. Repeat with `America/Los_Angeles`, `Asia/Kolkata`, `Pacific/Kiritimati`, and back to the original zone. Record the original zone first and restore it at the end.
  2. **Monotone today in an odd-offset zone (N-1).** In `Africa/Cairo` (UTC+3 while the device zone is Cairo; if the device is in another odd-offset zone use that one) read Home "Today" every 2 minutes for at least 20 minutes while the device is idle after a transfer: the readings never decrease; Home "Today" >= the sum of the Top apps rows. If the run crosses local midnight say so; if it does not, say that the boundary itself was covered by unit tests only.
  3. **Known-transfer accuracy** (repeat of the earlier check, 5 % tolerance against `dumpsys netstats --uid`) and **no phantom data** after `airplane-mode enable/disable` and `svc data/wifi disable/enable`.
  4. **Quick regression sweep:** navigation stress (10 taps on each tab, 0 bounces), dark theme text and AMOLED pixel `(0,0,0)`, Arabic digits, 200 % font + density 560 navbar, notification text after relaunch, Settings persistence, `logcat -d | grep -E "AndroidRuntime|FATAL"` clean.
  Restore every device setting, stop the emulator (`adb emu kill`).

- [ ] **Step 3: Truthful completion report.** Rewrite `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/m2-completion-report.md`. Read section 5 of `.superpowers/sdd/2026-10-02-emberbyte-m2-fix-wave/m2-reaudit-report.md` ("Completion report: false or unsupported statements": 13 items such as wrong finding ids, test names that do not exist, a navbar font cap described as 1.1x when the code uses 1.15) and correct EVERY item. The report must contain: both fix waves with commit ranges and tester verdicts, per-module test counts and lint counts exactly as measured in Step 1, every deviation from the plans with the reason, the Step 2 results, deferred minors, and a short "Corrections" section. Do not claim anything you did not run, and use real test names.

- [ ] **Step 4: Stop.** `git status` shows only the Claude-owned docs, you are on `m2-real-usage`, nothing was pushed. Report "M2 fix wave 2 is ready for the Claude final check on branch m2-real-usage".
