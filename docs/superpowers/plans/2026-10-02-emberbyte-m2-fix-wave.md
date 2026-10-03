# Emberbyte M2 Fix Wave Implementation Plan

> **For the executing agents (Antigravity):** follow the orchestration protocol in
> `docs/superpowers/plans/2026-10-02-m2-fixwave-antigravity-prompt.md` (it reuses the protocol of the first M2 prompt:
> one implementer + one fresh tester per task, a ledger, no pushing). Steps use checkbox (`- [ ]`) syntax. Do the tasks in order.

**Goal:** Make milestone M2 pass its audit. The first M2 audit (Opus auditor) and the independent device QA (Opus lead QA engineer) both
returned **FAIL**: the data path double counts and can invent gigabytes, and the app resets to Home on a real device. This wave fixes
every Critical and Important finding of both reports and a few cheap Minors.

**Architecture:** No new modules. Three corrections of substance: (1) sampler and catch-up data are merged **per 2-hour window**, never
per hour, and the catch-up queries whole windows and replaces them atomically; (2) the sampler can no longer attribute vanished-and-returned
mobile counters, sleep gaps or non-Wi-Fi bytes to usage; (3) navigation state is held in hot `StateFlow`s so the UI is never torn down.

**Tech Stack:** unchanged (Kotlin 2.4.20, AGP 9.4.0, Room 2.8.5 + KSP, DataStore, WorkManager, Compose with Material 3 1.5.0-alpha29).

Read first: `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` (**section 9 was rewritten for this wave**; 9.1 counter rules,
`SeriesBuilder`, the removal of `HourlyReconciler`; 9.2 `replaceHourly`, `DayClock`, `SystemZoneClock`; 9.4 the corrected semantics;
section 2 `GapReason.DEVICE_ASLEEP`; section 5 window rows; section 6 worker/receiver wording). The two reports that this wave answers are
in `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/`: `m2-audit-report.md` (IDs C-1, C-2, I-1..I-7, M-1..M-21) and `m2-qa-report.md`
(IDs D-01..D-22). Their findings are the acceptance criteria; the table below says where each one is fixed.

## Finding map

| Finding | Where fixed |
|---|---|
| QA D-01 (navigation resets to Home), D-03 (dark text near-black), D-04 (AMOLED does nothing), audit M-3 | Task F1 |
| Audit C-2 (phantom GB after mobile counter returns), I-2 (late tick lumped), I-3 + QA D-13 (Wi-Fi attribution), M-12 (overlapping runs) | Task F2 |
| Audit C-1 + QA D-02 (double counting, total goes down), QA D-09 + audit M-2 (surfaces disagree), I-1 (duplicate rows), M-10 (concurrent catch-up) | Task F3 |
| Audit I-4 + QA D-12 (day roll-over, time/zone change) | Task F4 |
| Audit I-5 (loops die), I-6 (no recovery), I-7 (boot before onboarding), QA D-05 (stale "0 B today") | Task F5 |
| QA D-06 (RTL digits), D-07 (200% font navbar), D-15 + audit M-5 (dead row click), D-18, D-19, D-21, D-14, audit M-6 | Task F6 |
| Everything else (final proof, truthful completion report) | Task F7 |
| Deferred to M3 (do NOT do now): audit M-1, M-4, M-7..M-9, M-11, M-13..M-21 (except M-6, M-10, M-12), QA D-08, D-10, D-11, D-16, D-17, D-20, D-22 | - |

## Global Constraints (unchanged from the M2 plan unless stated)

- minSdk 29, compileSdk 37, targetSdk 36, Java/Kotlin 17. Do not change AGP, Kotlin, Gradle, Compose or Material versions. No new libraries
  (exception: Task F5 may add `androidx.work:work-testing` ONLY if you cannot test the worker otherwise; record it).
- `:ui:design` must not depend on `:core:*`; `:core:engine` is pure Kotlin; no Room/DataStore types in `:app`; no network permission; no Fakes in production wiring.
- All animation through `MaterialTheme.motionScheme`; system "remove animations" disables motion.
- Test-first for every behaviour change. **When the contract changed a rule, the test that encoded the old rule must change with it**: the plan
  names every such test; change only those, say so in the report, and do not weaken anything else. Never skip or ignore a test to get green.
- Code in this plan is meant to be used verbatim. If it does not compile because of tool/version drift, make the smallest fix and record it.
- Git: identity check before EVERY commit (`Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`, repo-local config), WHY-style messages with the
  `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>` trailer. Work on branch `m2-real-usage` (continue; do not create a new branch).
  **Never push, force, merge, or use `--no-verify`.**
- After every task the full gate must pass: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`. Use
  `--no-build-cache` once at the very end (a cached run can hide failures).
- Dev databases created by the first M2 build contain rows with the old per-hour meaning. The app is unreleased: do NOT write a migration;
  tell testers to run `adb shell pm clear io.github.khaledbahaaeldin.emberbyte` before device checks.

### Verified platform facts (do not re-research)

- Android stores per-UID history in **2-hour buckets aligned to even UTC hours** (`bucketDuration=7200` was observed on the test device).
  `querySummary` over a shorter span **interpolates** a bucket proportionally; over an **open** bucket it can return the whole bucket-so-far
  for every overlapping query. A query that covers the whole 2-hour window (clamped to now) returns the bucket once, without interpolation.
- `TrafficStats.getMobileRx/TxBytes()` counts only interfaces that currently exist: when mobile data disconnects the value DROPS (often to 0)
  and it returns to its since-boot value when it reconnects. `getTotalRx/TxBytes()` is monotonic within a boot and includes mobile, tethering and VPN tunnels.
- `delay()` runs on monotonic time that stops in deep sleep; a tick can arrive minutes or hours late.
- A background worker may not start a foreground service on Android 12+ (`ForegroundServiceStartNotAllowedException`).
- `collectAsStateWithLifecycle` restarts its collection (and resets to the initial value) when it is handed a NEW Flow instance on recomposition
  or when the lifecycle restarts; `DataStoreOnboardingRepository.observeCompleted()` emits `null` first on every new collection.

---

### Task F1: Navigation stability and the root surface (QA D-01, D-03, D-04; audit M-3)

**Files:**
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt`, `MainActivity.kt`, `EmberbyteApp.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteSurface.kt`
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/NavigationStabilityTest.kt`, `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteSurfaceTest.kt`

**Interfaces:** `AppGraph.onboardingCompleted: StateFlow<Boolean?>` and `AppGraph.settingsState: StateFlow<Settings?>` (hot caches; `null` only until the first read, never again); `@Composable fun EmberbyteSurface(modifier, content)` in `:ui:design` (fills the screen with `colorScheme.background` and sets the content colour to `onBackground`).

Root cause (confirmed by QA on the emulator): `EmberbyteApp` and `MainActivity` call `repo.observe…()` **inside composition**, which creates a new cold Flow on every recomposition; `collectAsStateWithLifecycle` then resets to `null` / defaults, `AppScaffold` leaves composition, and `rememberNavController()` plus all saveable state are thrown away, so the app snaps back to Home. The screens also draw no background and no content colour, which causes the near-black text in dark theme and the dead AMOLED switch.

- [ ] **Step 1: Write the failing tests**

`ui/design/.../theme/EmberbyteSurfaceTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmberbyteSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun content_gets_the_on_background_colour_in_dark_theme() {
        var content = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                expected = MaterialTheme.colorScheme.onBackground
                EmberbyteSurface { content = LocalContentColor.current }
            }
        }
        assertEquals(expected, content)
    }

    @Test fun amoled_black_reaches_the_surface_colour() {
        var background = Color.Unspecified
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false, amoledBlack = true) {
                EmberbyteSurface { background = MaterialTheme.colorScheme.background }
            }
        }
        assertEquals(Color.Black, background)
    }
}
```

`app/.../NavigationStabilityTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationStabilityTest {
    @get:Rule val rule = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun the_onboarding_flag_never_returns_to_null_once_loaded() = runBlocking {
        val graph = AppGraph(app)
        val loaded = graph.onboardingCompleted.first { it != null }
        repeat(5) { assertEquals(loaded, graph.onboardingCompleted.value) }
        // a brand new collector gets the cached value immediately, not null
        assertEquals(loaded, graph.onboardingCompleted.first())
    }

    @Test fun the_settings_state_never_returns_to_null_once_loaded() = runBlocking {
        val graph = AppGraph(app)
        val loaded = graph.settingsState.first { it != null }
        assertNotNull(loaded)
        graph.settings.update { it.copy(amoledBlack = true) }
        assertEquals(true, graph.settingsState.first { it?.amoledBlack == true }?.amoledBlack)
        assertNotNull(graph.settingsState.first())
    }

    @Test fun a_settings_change_does_not_throw_the_navigation_state_away() {
        val graph = AppGraph(app)
        runBlocking {
            graph.onboarding.complete()
            graph.onboardingCompleted.first { it == true }
        }
        rule.setContent {
            val settings by graph.settingsState.collectAsState()
            val current = settings
            if (current != null) {
                EmberbyteTheme(darkTheme = true, dynamicColor = current.useDynamicColor, amoledBlack = current.amoledBlack) {
                    EmberbyteApp(graph, hapticsEnabled = current.hapticsEnabled)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_apps").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nav_apps").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { graph.settings.update { it.copy(useDynamicColor = false) } }
        rule.waitForIdle()
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }
}
```
(If the third test cannot be made to run under Robolectric after a real attempt - for example because of the Haze blur or navigation animations - keep
the first two tests, delete only the third, and say so in the report: the device check in Task F7 then covers it. Do not weaken the first two.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :ui:design:testDebugUnitTest --tests "*EmberbyteSurfaceTest" :app:testDebugUnitTest --tests "*NavigationStabilityTest"` → FAIL (unresolved `EmberbyteSurface`, `onboardingCompleted`, `settingsState`).

- [ ] **Step 3: `EmberbyteSurface`** (`ui/design/.../theme/EmberbyteSurface.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The root of every screen: paints the theme background (so "AMOLED black" is really #000000) and sets the default content colour
 * (so `Text` and `Icon` without an explicit colour are readable in dark theme).
 */
@Composable
fun EmberbyteSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}
```

- [ ] **Step 4: Hot caches in `AppGraph.kt`.** Add the imports `io.github.khaledbahaaeldin.emberbyte.data.Settings`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.stateIn`, and add these members right after `val plans = ...`:
```kotlin
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Hot cache of the onboarding flag. `null` only until the first read; collecting it never restarts the underlying flow. */
    val onboardingCompleted: StateFlow<Boolean?> =
        onboarding.observeCompleted().stateIn(appScope, SharingStarted.Eagerly, null)

    /** Hot cache of the settings. `null` only until the first read; collecting it never restarts the underlying flow. */
    val settingsState: StateFlow<Settings?> =
        settings.observe().map<Settings, Settings?> { it }.stateIn(appScope, SharingStarted.Eagerly, null)
```

- [ ] **Step 5: Replace `MainActivity.kt`**

```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteSurface
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = (application as EmberbyteApplication).graph
        setContent {
            val settings by graph.settingsState.collectAsStateWithLifecycle()
            val current = settings
            EmberbyteTheme(
                dynamicColor = current?.useDynamicColor ?: true,
                amoledBlack = current?.amoledBlack ?: false,
            ) {
                EmberbyteSurface {
                    // While the stored settings load, only the background is drawn; the app is never torn down afterwards.
                    if (current != null) EmberbyteApp(graph, hapticsEnabled = current.hapticsEnabled)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Start measuring only after the first-run flow is finished.
        lifecycleScope.launch {
            if (graph.onboardingCompleted.first { it != null } == true) ServiceStarter.start(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { graph.permissions.recheck() }
    }
}
```

- [ ] **Step 6: Edit `EmberbyteApp.kt`.**
  (a) Replace the whole `EmberbyteApp` function with:
```kotlin
@Composable
fun EmberbyteApp(graph: AppGraph, hapticsEnabled: Boolean = true) {
    val completed by graph.onboardingCompleted.collectAsStateWithLifecycle()
    val value = completed
    // `null` only before the first read. The scaffold is created ONCE with the right start destination and then stays.
    if (value != null) AppScaffold(graph, startOnboarding = !value, hapticsEnabled = hapticsEnabled)
}
```
  (b) In `AppScaffold`, in the navbar's `onSelect`, replace `popUpTo(navController.graph.findStartDestination().id) { saveState = true }` with `popUpTo(Destination.Home.route) { saveState = true }` (after onboarding the start destination is no longer in the back stack, M-3).
  (c) Remove now-unused imports (`background`, `Box` only if unused; keep `Box` - it is still used in `AppScaffold`; remove `findStartDestination` if unused).

- [ ] **Step 7: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add ui app
git commit -m "fix(app): keep navigation alive across recomposition; paint the root background

Collecting a fresh cold flow in composition reset the onboarding flag to null and rebuilt the NavHost at Home. Hot StateFlows plus a root surface fix navigation, dark-theme text colour and AMOLED black."
```

---

### Task F2: Counter rules and the sampler (audit C-2, I-2, I-3, M-12; QA D-13)

**Files:**
- Modify: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/counter/Counter.kt`, `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/Usage.kt` (add `DEVICE_ASLEEP`), `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/sampler/SamplerEngine.kt` (replace)
- Modify (tests): `core/engine/src/test/.../counter/CounterReconcilerTest.kt`, `core/data/src/test/.../sampler/SamplerEngineTest.kt`

**Interfaces:** contract 9.1 (`CounterReconciler`), section 2 (`GapReason.DEVICE_ASLEEP`), 9.4 (attribution, late ticks). `SamplerEngine`'s constructor and public members are unchanged; `run()` is now serialised by a mutex.

New rules (read contract 9.4 first):
1. `CounterReconciler.delta`: `previous == null` → `(0, 0, false)`; **different `bootId`** → `(current.rx, current.tx, true)`; **same boot but either counter decreased** → `(0, 0, true)` (an interface vanished; re-baseline, never invent usage); otherwise the difference.
2. In `tick()`: `mobile = min(mobileDelta, totalDelta)` per direction (the total includes mobile). `wifi = max(0, totalDelta - mobile)` **only when the default network kind is `WIFI`**, otherwise `0`.
3. In `tick()`: if more than 90 000 ms passed since the previous tick, do not add anything to minute rows; record a `DEVICE_ASLEEP` gap (previous tick → now), reset the EMA, and return (the baseline is already updated).
4. `run()` runs under a `Mutex`, so an old run's `finally { stop() }` can never overlap a new run's `start()`/`tick()`.

- [ ] **Step 1: Update and add the tests FIRST.**

`CounterReconcilerTest.kt` — these are the tests that encoded the OLD rule; change exactly these two and add the third:
```kotlin
    @Test fun a_decreasing_rx_counter_in_the_same_boot_rebaselines_without_inventing_usage() =
        assertEquals(CounterDelta(0, 0, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 40, 260)))

    @Test fun a_decreasing_tx_counter_alone_also_rebaselines() =
        assertEquals(CounterDelta(0, 0, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1500, 10)))

    @Test fun an_increase_after_a_decrease_is_an_ordinary_difference_clamping_is_the_samplers_job() {
        val afterDrop = reading(1, 0, 0)
        assertEquals(CounterDelta(2_000_000_000, 0, false), CounterReconciler.delta(afterDrop, reading(2, 2_000_000_000, 0)))
    }
```
(the "different boot id" test stays: delta = current reading, reset = true).

`SamplerEngineTest.kt` — change exactly these existing tests, then add the new ones:
  * `wifi_is_total_minus_mobile_and_rows_carry_their_subscription`: build the engine with `kind = NetworkKind.WIFI` (Wi-Fi is now only derived while Wi-Fi is the default network). Expected values unchanged (mobile 200, Wi-Fi 800).
  * `a_counter_decrease_records_a_reset_gap_and_counts_the_new_value`: rename to `a_counter_decrease_records_a_reset_gap_and_counts_nothing`; the expected MOBILE total is now **1000** (the 50 after the decrease is not counted); the gap assertions stay.
  * New tests (use the existing `snap`, `engine`, `rows()` helpers):
```kotlin
    @Test fun a_mobile_counter_that_vanishes_and_returns_does_not_create_usage() = runTest {
        val store = InMemoryUsageStore()
        val two = 2_000_000_000L
        val e = engine(listOf(snap(0, mobRx = two, totRx = two), snap(1, mobRx = 0, totRx = two), snap(2, mobRx = two, totRx = two + 10_000)), store, kind = NetworkKind.MOBILE)
        e.start(); e.tick(); e.tick(); e.stop()
        val mobile = store.rows().filter { it.network == NetworkKind.MOBILE }.sumOf { it.rxBytes }
        assertTrue("mobile was $mobile", mobile <= 10_000L)
        assertEquals(0L, store.rows().filter { it.network == NetworkKind.WIFI }.sumOf { it.totalBytes })
    }

    @Test fun wifi_is_not_booked_while_the_default_network_is_cellular_or_offline() = runTest {
        for (kind in listOf(NetworkKind.MOBILE, null)) {
            val store = InMemoryUsageStore()
            val e = engine(listOf(snap(0), snap(1, totRx = 500)), store, kind = kind)
            e.start(); e.tick(); e.stop()
            assertEquals("kind=$kind", 0L, store.rows().filter { it.network == NetworkKind.WIFI }.sumOf { it.totalBytes })
        }
    }

    @Test fun a_tick_that_arrives_very_late_is_not_lumped_into_one_minute() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(600, mobRx = 50_000_000), snap(601, mobRx = 50_000_100)), store)
        e.start(); e.tick()
        assertEquals(0L, store.rows().sumOf { it.totalBytes })
        val gap = store.gaps(open.first, open.second).single()
        assertEquals(GapReason.DEVICE_ASLEEP, gap.reason)
        assertEquals(t0, gap.from); assertEquals(t0.plusSeconds(600), gap.to)
        e.tick(); e.stop()
        assertEquals(100L, store.rows().filter { it.network == NetworkKind.MOBILE }.sumOf { it.rxBytes })
    }

    @Test fun overlapping_runs_are_serialised_instead_of_failing() = runTest {
        val script = (0L..40L).map { snap(it, mobRx = it * 10) }
        val e = SamplerEngine(FakeCounterSource(script), FakeNetworkKindSource(), FakeSubscriptionSource(7), InMemoryUsageStore(), tickMillis = 1_000)
        val first = launch { e.run() }
        advanceTimeBy(1_500); runCurrent()
        val second = launch { e.run() }          // must wait for the first run to finish
        advanceTimeBy(1_500); runCurrent()
        first.cancelAndJoin()
        advanceTimeBy(2_500); runCurrent()
        assertTrue(second.isActive)               // it took over without an IllegalStateException
        second.cancelAndJoin()
    }
```
  (add the missing imports: `kotlinx.coroutines.test.advanceTimeBy`, `runCurrent`, `kotlinx.coroutines.cancelAndJoin`, `kotlinx.coroutines.isActive` as needed.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test :core:data:testDebugUnitTest --tests "*SamplerEngineTest"` → FAIL.

- [ ] **Step 3: Implement.**

`engine/model/Usage.kt`: change the enum to
```kotlin
enum class GapReason { SERVICE_KILLED, REBOOT, COUNTER_RESET, PERMISSION_MISSING, DEVICE_ASLEEP }
```

`engine/counter/Counter.kt`: replace the `CounterReconciler` object with
```kotlin
object CounterReconciler {
    /**
     * previous == null -> zero delta, not a reset.
     * A different bootId means the counters restarted from zero: the delta is the current reading (wasReset = true).
     * In the SAME boot a decreasing counter means an interface vanished (for example mobile data went away): the delta is 0
     * (wasReset = true) so nothing is invented; when the interface returns, the caller clamps the jump (see SamplerEngine).
     */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta {
        if (previous == null) return CounterDelta(0L, 0L, wasReset = false)
        if (previous.bootId != current.bootId) {
            return CounterDelta(current.rxBytes.coerceAtLeast(0L), current.txBytes.coerceAtLeast(0L), wasReset = true)
        }
        if (current.rxBytes < previous.rxBytes || current.txBytes < previous.txBytes) {
            return CounterDelta(0L, 0L, wasReset = true)
        }
        return CounterDelta(current.rxBytes - previous.rxBytes, current.txBytes - previous.txBytes, wasReset = false)
    }
}
```

`data/sampler/SamplerEngine.kt`: in the existing file (1) add imports `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`; (2) add the member `private val runLock = Mutex()`; (3) replace `run()` and `tick()` with:
```kotlin
    /** Runs until cancelled; always flushes and saves checkpoints on the way out. Runs never overlap (they are serialised). */
    suspend fun run() = runLock.withLock {
        start()
        try {
            while (true) {
                tick()
                delay(tickMillis)
            }
        } finally {
            withContext(NonCancellable) { stop() }
        }
    }

    suspend fun tick() {
        check(started) { "call start() before tick()" }
        val snapshot = counters.read()
        val mobileNow = CounterReading(snapshot.at, snapshot.mobileRxBytes, snapshot.mobileTxBytes, snapshot.bootId)
        val totalNow = CounterReading(snapshot.at, snapshot.totalRxBytes, snapshot.totalTxBytes, snapshot.bootId)
        val before = previousTotal!!
        val elapsedMs = Duration.between(before.at, snapshot.at).toMillis()
        val mobileRaw = CounterReconciler.delta(previousMobile, mobileNow)
        val totalRaw = CounterReconciler.delta(before, totalNow)
        previousMobile = mobileNow
        previousTotal = totalNow
        if (elapsedMs <= 0L) return

        if (elapsedMs > GAP_THRESHOLD_MS) {
            // The device slept or the process was frozen: these bytes belong to hours the catch-up fills from system data.
            store.insertGap(CoverageGap(before.at, snapshot.at, GapReason.DEVICE_ASLEEP))
            hasEma = false
            return
        }
        if (mobileRaw.wasReset || totalRaw.wasReset) {
            store.insertGap(CoverageGap(before.at, snapshot.at, GapReason.COUNTER_RESET))
        }

        val kind = network.current.value
        // The all-interface total includes mobile, so mobile can never exceed it (a returning mobile counter must not create usage).
        val mobileRx = minOf(mobileRaw.rxBytes, totalRaw.rxBytes)
        val mobileTx = minOf(mobileRaw.txBytes, totalRaw.txBytes)
        // Wi-Fi is only derived while Wi-Fi is the default network; offline noise, tethering and cellular-side VPN bytes are not Wi-Fi.
        val wifiRx = if (kind == NetworkKind.WIFI) (totalRaw.rxBytes - mobileRx).coerceAtLeast(0L) else 0L
        val wifiTx = if (kind == NetworkKind.WIFI) (totalRaw.txBytes - mobileTx).coerceAtLeast(0L) else 0L

        val seconds = elapsedMs / 1000.0
        val instantRx = (mobileRx + wifiRx) / seconds
        val instantTx = (mobileTx + wifiTx) / seconds
        if (!hasEma) {
            emaRx = instantRx; emaTx = instantTx; hasEma = true
        } else {
            emaRx = EMA_ALPHA * instantRx + (1 - EMA_ALPHA) * emaRx
            emaTx = EMA_ALPHA * instantTx + (1 - EMA_ALPHA) * emaTx
        }
        _liveSpeed.tryEmit(LiveSpeed(emaRx.roundToLong(), emaTx.roundToLong(), kind, snapshot.at))

        val minute = snapshot.at.truncatedTo(ChronoUnit.MINUTES)
        add(MinuteKey(minute, NetworkKind.MOBILE, subscription.defaultDataSubscriptionId()), mobileRx, mobileTx)
        add(MinuteKey(minute, NetworkKind.WIFI, NO_SUBSCRIPTION), wifiRx, wifiTx)
        flush(olderThan = minute)
    }
```
(keep `start()`, `stop()`, `add()`, `flush()` exactly as they are).

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug` → BUILD SUCCESSFUL. Room stores `GapReason` by name, so no schema change.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "fix(data): never invent usage from returning counters, sleep gaps or non-Wi-Fi bytes

A mobile counter that vanished and came back added the whole since-boot count to one minute, a late tick lumped hours into one minute, and offline/tethering bytes were booked as Wi-Fi."
```

---

### Task F3: Window-based totals, atomic window replacement, one rule for every screen (audit C-1, I-1, M-2, M-10; QA D-02, D-09)

**Files:**
- Modify: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/SeriesBuilder.kt` (replace)
- Delete: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/HourlyReconciler.kt` and every test that exercises it (the `HourlyReconciler` tests inside `UsageAggregationTest.kt`)
- Modify: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/store/{UsageStore,RoomUsageStore}.kt`, `data/db/UsageDao.kt`, `data/fake/InMemoryUsageStore.kt`, `data/sampler/HourlyCatchUp.kt` (replace), `data/RoomUsageRepository.kt`
- Modify (tests): `engine/.../usage/SeriesBuilderTest.kt`, `data/.../store/UsageStoreContractTest.kt`, `data/.../sampler/HourlyCatchUpTest.kt` (replace), `data/.../RoomUsageRepositoryTest.kt`

**Interfaces:** contract 9.1 (`SeriesBuilder.WINDOW_SECONDS`, `windowStart`, `build`), 9.2 (`UsageStore.replaceHourly`), 9.4. `HourlyReconciler` ceases to exist.

Why (confirmed on a real device by the QA engineer): the platform keeps history in 2-hour buckets and interpolates them, so comparing "minute sum" with "hourly sum" per HOUR counted the same bytes twice (Home showed +12 to +21 % and could go DOWN). The fix compares sources per 2-hour window and queries whole windows.

- [ ] **Step 1: Update and add the tests FIRST.**

(a) Delete the `HourlyReconciler` tests (everything below the `// ---- HourlyReconciler` comment in `UsageAggregationTest.kt`, including its helper `fullHour`). Nothing else in that file changes.

(b) `SeriesBuilderTest.kt`: **change exactly one existing test**, `an_hour_straddling_the_first_bucket_start_is_counted_in_the_first_bucket`: delete it and add the two tests below that replace it. Add the others. Keep every other existing test as it is (they must still pass).
```kotlin
    @Test fun an_hour_belongs_to_the_bucket_that_contains_its_start_instant() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30: local 7 Oct starts at 2026-10-06T18:30Z
        // window 18:00Z-20:00Z holds hours 18:00Z (yesterday locally) and 19:00Z (today); nothing else is known, so it is shared equally
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata,
            minuteRows = emptyList(),
            hourlyRows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.MOBILE, 1_000)),
        )
        assertEquals(500L, points.single().mobileBytes)
    }

    @Test fun the_same_instant_rule_makes_today_and_the_week_bar_agree() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val rows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.WIFI, 1_000), hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.WIFI, 400))
        val today = SeriesBuilder.build(DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata, emptyList(), rows).single()
        val week = SeriesBuilder.build(DateRange(i("2026-10-01T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, kolkata, emptyList(), rows)
        assertEquals(today.totalBytes, week.single { it.start == today.start }.totalBytes)
    }

    @Test fun a_burst_in_the_first_half_of_a_window_is_not_counted_twice() {
        // 100 MB used between 10:00 and 10:30. The sampler wrote hour 10 = 100 and hour 11 = 0 (zero rows exist);
        // the system window row says 100 (all of it). The day must show 100, not 150.
        val minutes = (0 until 60).map { minute("2026-10-07T11:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 0) } +
            minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100_000_000)
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 100_000_000))
        val day = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single()
        assertEquals(100_000_000L, day.mobileBytes)
    }

    @Test fun a_window_the_sampler_only_saw_the_end_of_is_not_counted_twice() {
        // The device test case: the sampler started at 13:44 inside the system window 12:00-14:00 (hour 12 uncovered, hour 13 covered 16 minutes).
        val minutes = (44 until 60).map { minute("2026-10-07T13:${"%02d".format(it)}:00Z", NetworkKind.WIFI, 2_278_750) } // 36.46 MB
        val system = listOf(hourly("2026-10-07T12:00:00Z", 10001, NetworkKind.WIFI, 37_890_000))
        val day = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single()
        assertEquals(37_890_000L, day.wifiBytes)
    }

    @Test fun a_window_only_the_system_knows_is_shared_equally_between_its_two_hours() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T10:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(150L, 150L), points.map { it.mobileBytes })
    }

    @Test fun the_surplus_goes_to_the_hour_the_sampler_covered_less() {
        val hour11 = (0 until 60).map { minute("2026-10-07T11:${"%02d".format(it)}:00Z", NetworkKind.MOBILE, 10) } // 600, fully covered
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 1_000))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T10:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, hour11, system)
        assertEquals(listOf(400L, 600L), points.map { it.mobileBytes })
    }

    @Test fun hours_that_start_before_the_range_are_ignored_even_when_their_window_overlaps_it() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300))
        val points = SeriesBuilder.build(DateRange(i("2026-10-07T11:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc, emptyList(), system)
        assertEquals(listOf(150L), points.map { it.mobileBytes })
    }

    @Test fun the_total_never_decreases_while_the_sampler_catches_up_with_the_system() {
        val system = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 500))
        var previous = 0L
        for (sampled in listOf(0L, 100L, 300L, 500L, 700L)) {
            val minutes = if (sampled == 0L) emptyList() else listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, sampled))
            val total = SeriesBuilder.build(DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, minutes, system).single().mobileBytes
            assertTrue("$total < $previous", total >= previous)
            previous = total
        }
    }

    @Test fun a_range_that_ends_before_it_starts_has_no_buckets() {
        val from = i("2026-10-07T12:00:00Z")
        assertTrue(SeriesBuilder.build(DateRange(from, from.minusSeconds(60)), Granularity.DAY, utc, emptyList(), emptyList()).isEmpty())
    }
```

(c) `UsageStoreContractTest.kt` add:
```kotlin
    @Test fun replaceHourly_removes_every_row_of_that_window_and_keeps_the_others() = runBlocking {
        val store = createStore()
        store.upsertHourly(listOf(h(0, 1, 100, sub = -1), h(0, 2, 200, sub = 3), h(2, 1, 999)))
        store.replaceHourly(t.plusSeconds(0), listOf(h(0, 1, 111, sub = 3)))
        val rows = store.hourlyRows(wide.first, wide.second).sortedWith(compareBy({ it.hourStart }, { it.uid }))
        assertEquals(listOf(h(0, 1, 111, sub = 3), h(2, 1, 999)), rows)
    }

    @Test fun replaceHourly_with_no_rows_empties_that_window() = runBlocking {
        val store = createStore()
        store.upsertHourly(listOf(h(0, 1, 100)))
        store.replaceHourly(t, emptyList())
        assertTrue(store.hourlyRows(wide.first, wide.second).isEmpty())
    }
```

(d) `HourlyCatchUpTest.kt` — REPLACE the whole file with:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyCatchUpTest {
    private val now = Instant.parse("2026-10-07T10:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val window10 = Instant.parse("2026-10-07T10:00:00Z") // window [10:00, 12:00)
    private val window08 = Instant.parse("2026-10-07T08:00:00Z")
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun catchUp(
        source: NetworkStatsSource = FakeNetworkStatsSource(),
        store: InMemoryUsageStore = InMemoryUsageStore(),
        info: FakeAppInfoSource = FakeAppInfoSource(),
        access: FakeUsageAccess = FakeUsageAccess(true),
    ) = HourlyCatchUp(source, store, info, access, clock)

    @Test fun without_usage_access_nothing_is_queried() = runBlocking {
        val source = FakeNetworkStatsSource()
        assertEquals(CatchUpResult.MissingUsageAccess, catchUp(source, access = FakeUsageAccess(false)).run())
        assertTrue(source.queries.isEmpty())
    }

    @Test fun the_first_run_backfills_seven_days_one_two_hour_window_at_a_time() = runBlocking {
        val source = FakeNetworkStatsSource()
        assertEquals(CatchUpResult.Done(7 * 12 + 1), catchUp(source).run())
        assertEquals((7 * 12 + 1) * 2, source.queries.size)
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), source.queries.minOf { it.second })
        // every closed window is queried over its full two hours
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, window08, window10)))
    }

    @Test fun the_open_window_is_queried_up_to_now_only() = runBlocking {
        val source = FakeNetworkStatsSource()
        catchUp(source).run()
        assertTrue(source.queries.contains(Triple(NetworkKind.WIFI, window10, now)))
    }

    @Test fun rows_are_stored_per_uid_and_network_keyed_by_the_window_start_with_the_dominant_subscription() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(window10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 1000, 500)),
                (NetworkKind.WIFI to window10) to listOf(UidUsage(10001, 2000, 0), UidUsage(10002, 300, 0)),
            ),
        )
        catchUp(source, store).run()
        val rows = store.hourlyRows(window10, window10.plusSeconds(1)).sortedWith(compareBy({ it.uid }, { it.network }))
        assertEquals(
            listOf(
                HourlyUsage(window10, 10001, NetworkKind.MOBILE, 3, 1000, 500),
                HourlyUsage(window10, 10001, NetworkKind.WIFI, -1, 2000, 0),
                HourlyUsage(window10, 10002, NetworkKind.WIFI, -1, 300, 0),
            ),
            rows,
        )
    }

    @Test fun a_later_run_replaces_the_window_so_a_changed_subscription_leaves_no_duplicate() = runBlocking {
        val store = InMemoryUsageStore()
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 1000, 0))))
        val job = catchUp(source, store)
        job.run()                                                    // no minute rows yet: subscription -1
        store.addMinute(MinuteTotal(window10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        job.run()                                                    // now the dominant subscription is 3
        val mobile = store.hourlyRows(window10, window10.plusSeconds(1)).filter { it.network == NetworkKind.MOBILE }
        assertEquals(1, mobile.size)
        assertEquals(3, mobile.single().subscriptionId)
    }

    @Test fun a_uid_that_disappears_from_the_system_data_disappears_from_the_window() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertHourly(listOf(HourlyUsage(window10, 10009, NetworkKind.WIFI, -1, 5, 5)))
        catchUp(FakeNetworkStatsSource(), store).run()
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10009 })
    }

    @Test fun a_second_run_only_revisits_the_previous_and_the_current_window() = runBlocking {
        val source = FakeNetworkStatsSource()
        val job = catchUp(source, InMemoryUsageStore())
        job.run()
        source.queries.clear()
        job.run()
        assertEquals(Instant.parse("2026-10-07T08:00:00Z"), source.queries.minOf { it.second })
        assertEquals(4, source.queries.size) // windows 08:00 and 10:00 for two networks
    }

    @Test fun app_labels_are_resolved_once_per_new_uid_and_zero_byte_rows_are_skipped() = runBlocking {
        val store = InMemoryUsageStore()
        val info = FakeAppInfoSource(mapOf(10001 to AppMeta(10001, "com.video", "Video")))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to window10) to listOf(UidUsage(10001, 10, 0), UidUsage(10003, 0, 0)),
                (NetworkKind.WIFI to window10) to listOf(UidUsage(10001, 20, 0)),
            ),
        )
        val job = catchUp(source, store, info)
        job.run(); job.run()
        assertEquals(listOf(AppMeta(10001, "com.video", "Video")), store.appMeta())
        assertEquals(1, info.resolved.count { it == 10001 })
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10003 })
    }

    @Test fun concurrent_runs_are_serialised() = runBlocking {
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val slow = object : NetworkStatsSource {
            override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> {
                val now = running.incrementAndGet()
                maxSeen.updateAndGet { maxOf(it, now) }
                delay(1)
                running.decrementAndGet()
                return emptyList()
            }
        }
        val job = catchUp(slow)
        listOf(async { job.run() }, async { job.run() }, async { job.run() }).awaitAll()
        assertEquals(1, maxSeen.get())
    }
}
```

(e) `RoomUsageRepositoryTest.kt` — add (keep the existing tests; they must still pass):
```kotlin
    @Test fun today_is_exactly_the_matching_bucket_of_the_series_in_a_half_hour_zone() = runBlocking {
        val kolkata = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), java.time.ZoneId.of("Asia/Kolkata"))
        val store = InMemoryUsageStore()
        store.upsertHourly(listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.WIFI, 1_000), hourly("2026-10-07T08:00:00Z", 10001, NetworkKind.WIFI, 400)))
        val repo = RoomUsageRepository(store, flowOf(live), DayClock(kolkata), kolkata) { catchUp }
        val today = repo.observeToday().first()
        val range = DateRange(Instant.parse("2026-10-06T18:30:00Z"), Instant.parse("2026-10-07T18:30:00Z"))
        val bar = repo.observeSeries(range, Granularity.DAY).first().single()
        assertEquals(bar.totalBytes, today.totalBytes)
    }

    @Test fun apps_use_the_same_start_instant_rule() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        store.upsertHourly(listOf(hourly("2026-10-06T23:00:00Z", 10001, NetworkKind.MOBILE, 700), hourly("2026-10-07T01:00:00Z", 10001, NetworkKind.MOBILE, 300)))
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        assertEquals(300L, repo(store).observeApps(range).first().single().totalBytes)
    }
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:engine:test :core:data:testDebugUnitTest` → FAIL (compilation: `replaceHourly`, `windowStart`; behaviour of `SeriesBuilder`).

- [ ] **Step 3: Implement `SeriesBuilder`** (`engine/usage/SeriesBuilder.kt`, replace the whole file)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

private data class NetworkHour(val hour: Instant, val network: NetworkKind)

object SeriesBuilder {
    /** The platform keeps per-UID history in buckets of 2 hours aligned to even UTC hours. */
    const val WINDOW_SECONDS = 7_200L

    fun windowStart(at: Instant): Instant =
        Instant.ofEpochSecond(Math.floorDiv(at.epochSecond, WINDOW_SECONDS) * WINDOW_SECONDS)

    fun bucketStart(at: Instant, granularity: Granularity, zone: ZoneId): Instant = when (granularity) {
        Granularity.HOUR -> at.truncatedTo(ChronoUnit.HOURS)
        Granularity.DAY -> at.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        Granularity.WEEK -> at.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
        Granularity.MONTH -> at.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
    }

    fun nextBucketStart(start: Instant, granularity: Granularity, zone: ZoneId): Instant = when (granularity) {
        Granularity.HOUR -> start.plus(1, ChronoUnit.HOURS)
        Granularity.DAY -> start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        Granularity.WEEK -> start.atZone(zone).toLocalDate().plusWeeks(1).atStartOfDay(zone).toInstant()
        Granularity.MONTH -> start.atZone(zone).toLocalDate().plusMonths(1).atStartOfDay(zone).toInstant()
    }

    /**
     * Zero-filled buckets covering [range]. The two sources are merged per (2-hour window, network), never per hour:
     * total = max(sampler minute sum, system window sum). The surplus over the minute sum is shared between the window's two hours in
     * proportion to how little the sampler covered each hour (equally when both are fully covered). An hour contributes to the bucket that
     * contains its START instant; hours that start before the first bucket or at/after range.to are ignored, so Home, Apps and History
     * all use the same rule.
     */
    fun build(
        range: DateRange,
        granularity: Granularity,
        zone: ZoneId,
        minuteRows: List<MinuteTotal>,
        hourlyRows: List<HourlyUsage>,
        filter: UsageFilter = UsageFilter(),
    ): List<UsagePoint> {
        if (range.from >= range.to) return emptyList()
        val first = bucketStart(range.from, granularity, zone)
        val starts = ArrayList<Instant>()
        var cursor = first
        while (cursor < range.to) {
            starts += cursor
            cursor = nextBucketStart(cursor, granularity, zone)
        }
        if (starts.isEmpty()) return emptyList()

        // How much of each hour the sampler saw, whatever the network or filter: distinct minutes that have a row.
        val coveredMinutes = HashMap<Instant, HashSet<Instant>>()
        for (row in minuteRows) {
            coveredMinutes.getOrPut(row.minuteStart.truncatedTo(ChronoUnit.HOURS)) { HashSet() }.add(row.minuteStart)
        }

        val minuteByHour = HashMap<NetworkHour, Long>()
        for (row in minuteRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            minuteByHour.merge(NetworkHour(row.minuteStart.truncatedTo(ChronoUnit.HOURS), row.network), row.totalBytes) { a, b -> a + b }
        }
        val systemByWindow = HashMap<NetworkHour, Long>()
        for (row in hourlyRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            systemByWindow.merge(NetworkHour(windowStart(row.hourStart), row.network), row.totalBytes) { a, b -> a + b }
        }

        val windows = HashSet<NetworkHour>()
        minuteByHour.keys.mapTo(windows) { NetworkHour(windowStart(it.hour), it.network) }
        windows.addAll(systemByWindow.keys)

        val mobile = LongArray(starts.size)
        val wifi = LongArray(starts.size)
        for (window in windows) {
            val hours = listOf(window.hour, window.hour.plus(1, ChronoUnit.HOURS))
            val minuteValues = hours.map { minuteByHour[NetworkHour(it, window.network)] ?: 0L }
            val minuteSum = minuteValues.sum()
            val total = maxOf(minuteSum, systemByWindow[window] ?: 0L)
            val shares = surplusShares(total - minuteSum, hours.map { coveredMinutes[it]?.size ?: 0 })
            hours.forEachIndexed { i, hour ->
                val value = minuteValues[i] + shares[i]
                if (value > 0L && hour >= first && hour < range.to) {
                    val index = indexFor(starts, hour)
                    if (window.network == NetworkKind.MOBILE) mobile[index] += value else wifi[index] += value
                }
            }
        }
        return starts.indices.map { UsagePoint(starts[it], mobile[it], wifi[it]) }
    }

    /** Splits [surplus] over two hours in proportion to how little the sampler covered each; equally when both are fully covered. */
    private fun surplusShares(surplus: Long, covered: List<Int>): LongArray {
        if (surplus <= 0L) return longArrayOf(0L, 0L)
        val w0 = 1.0 - (covered[0] / 60.0).coerceIn(0.0, 1.0)
        val w1 = 1.0 - (covered[1] / 60.0).coerceIn(0.0, 1.0)
        val sum = w0 + w1
        val firstShare = if (sum <= 0.0) surplus / 2 else (surplus * (w0 / sum)).roundToLong()
        return longArrayOf(firstShare, surplus - firstShare)
    }

    private fun matches(network: NetworkKind, subscriptionId: Int, filter: UsageFilter): Boolean =
        (filter.network == null || filter.network == network) &&
            (filter.subscriptionId == null || filter.subscriptionId == subscriptionId)

    /** Index of the last bucket whose start is <= [hour] (0 when the hour starts before the first bucket). */
    private fun indexFor(starts: List<Instant>, hour: Instant): Int {
        var low = 0
        var high = starts.lastIndex
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= hour) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
```
Then delete `HourlyReconciler.kt`. `dominantSubscriptionId` and `AppAggregator` stay as they are.

- [ ] **Step 4: Atomic window replacement.**
  * `UsageStore.kt`: add to the interface, right after `upsertHourly`:
```kotlin
    /** Atomically deletes ALL rows whose hourStart == [hourStart] (any uid/network/subscription), then inserts [rows]. */
    suspend fun replaceHourly(hourStart: Instant, rows: List<HourlyUsage>)
```
  * `UsageDao.kt`: add
```kotlin
    @Query("DELETE FROM usage_hourly WHERE hourStart = :hourStart")
    abstract suspend fun deleteHour(hourStart: Long)

    @Transaction
    open suspend fun replaceHourly(hourStart: Long, rows: List<UsageHourlyEntity>) {
        deleteHour(hourStart)
        if (rows.isNotEmpty()) upsertHourly(rows)
    }
```
  * `RoomUsageStore.kt`: add
```kotlin
    override suspend fun replaceHourly(hourStart: Instant, rows: List<HourlyUsage>) =
        dao.replaceHourly(hourStart.ms(), rows.map { it.toEntity() })
```
  * `InMemoryUsageStore.kt`: add
```kotlin
    override suspend fun replaceHourly(hourStart: Instant, rows: List<HourlyUsage>) = hours.update { map ->
        map.filterKeys { it.hourStart != hourStart } + rows.associateBy { HourKey(it.hourStart, it.uid, it.network, it.subscriptionId) }
    }
```
  The Room schema does not change (no new table, no new column); `1.json` must stay byte-identical.

- [ ] **Step 5: `HourlyCatchUp`** (`data/sampler/HourlyCatchUp.kt`, replace the whole file)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import io.github.khaledbahaaeldin.emberbyte.engine.usage.dominantSubscriptionId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val CHECKPOINT_CATCHUP_HOURLY = "catchup.hourly"

sealed interface CatchUpResult {
    data class Done(val hoursUpdated: Int) : CatchUpResult
    data object MissingUsageAccess : CatchUpResult
}

private val WINDOW: Duration = Duration.ofSeconds(SeriesBuilder.WINDOW_SECONDS)

/**
 * Pulls per-app usage from the system's network stats, ONE 2-hour window at a time (the platform's bucket size, so nothing is
 * interpolated; the open window is clamped to now), and atomically replaces each window's rows. `hoursUpdated` counts windows.
 * Runs are serialised: the service loop, the worker and a manual refresh can all call [run].
 */
class HourlyCatchUp(
    private val source: NetworkStatsSource,
    private val store: UsageStore,
    private val appInfo: AppInfoSource,
    private val access: UsageAccess,
    private val clock: Clock,
    private val maxBackfill: Duration = Duration.ofDays(7),
) {
    private val lock = Mutex()

    suspend fun run(): CatchUpResult = lock.withLock { runLocked() }

    private suspend fun runLocked(): CatchUpResult {
        if (!access.isGranted()) return CatchUpResult.MissingUsageAccess

        val now = clock.instant()
        val currentWindow = SeriesBuilder.windowStart(now)
        val floor = SeriesBuilder.windowStart(now.minus(maxBackfill))
        val checkpoint = store.loadCheckpoint(CHECKPOINT_CATCHUP_HOURLY)
        val start = checkpoint
            ?.let { SeriesBuilder.windowStart(it.at).minus(WINDOW) } // the previous window may have closed since the last run
            ?.let { if (it < floor) floor else it }
            ?: floor

        val knownUids = store.appMeta().map { it.uid }.toHashSet()
        var window = start
        var updated = 0
        while (window <= currentWindow) {
            val end = window.plus(WINDOW)
            val queryEnd: Instant = if (end < now) end else now
            val minuteRows = store.minuteRows(window, end)
            val rows = ArrayList<HourlyUsage>()
            for (network in NetworkKind.entries) {
                val subscription = if (network == NetworkKind.MOBILE) dominantSubscriptionId(minuteRows) else -1
                for (usage in source.query(network, window, queryEnd)) {
                    if (usage.rxBytes + usage.txBytes > 0L) {
                        rows += HourlyUsage(window, usage.uid, network, subscription, usage.rxBytes, usage.txBytes)
                    }
                }
            }
            store.replaceHourly(window, rows)
            for (uid in rows.map { it.uid }.distinct()) {
                if (knownUids.add(uid)) appInfo.resolve(uid)?.let { store.upsertAppMeta(listOf(it)) }
            }
            updated++
            window = end
        }
        store.saveCheckpoint(CHECKPOINT_CATCHUP_HOURLY, CounterReading(currentWindow, 0L, 0L, "catchup"))
        return CatchUpResult.Done(updated)
    }
}
```

- [ ] **Step 6: `RoomUsageRepository`.** Make every surface use the one rule:
  * `observeToday`: replace the body of the `flatMapLatest` with
```kotlin
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        observeSeries(DateRange(from, to), Granularity.DAY, filter).map { points ->
            val point = points.firstOrNull()
            DayUsage(date, point?.mobileBytes ?: 0L, point?.wifiBytes ?: 0L)
        }
```
    (add `import kotlinx.coroutines.flow.map`).
  * `observeSeries` and `observeAppSeries`: replace the padding `.minus(1, ChronoUnit.HOURS)` by `.minusSeconds(SeriesBuilder.WINDOW_SECONDS)` (a window that starts before the first bucket can still contain an hour inside it).
  * `observeApps`: replace `store.observeHourlyRows(range.from.truncatedTo(ChronoUnit.HOURS), range.to)` by `store.observeHourlyRows(range.from, range.to)` (a window row belongs to the range that contains its start instant).
  * Remove imports that became unused.

- [ ] **Step 7: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL. Check `git diff --stat core/data/schemas` prints nothing (the exported schema is unchanged).

- [ ] **Step 8: Commit**

```bash
git add core
git commit -m "fix(data): merge sampler and system data per 2-hour window and replace windows atomically

Per-hour max() double counted because the platform interpolates 2-hour buckets (Home was 12-21% too high and could go down). Windows are queried whole, replaced atomically, serialised, and every screen uses the same start-instant rule."
```

---

### Task F4: Day roll-over, clock and time-zone changes (audit I-4; QA D-12)

**Files:**
- Modify: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/DayClock.kt` (replace)
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/SystemZoneClock.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt` (default clock), the four view models `home/HomeViewModel.kt`, `apps/AppsViewModel.kt`, `apps/AppDetail.kt`, `history/History.kt` (default clock)
- Modify (tests): `core/data/src/test/.../util/DayClockTest.kt`; create `core/data/src/test/.../util/SystemZoneClockTest.kt`

**Interfaces:** contract 9.2: `DayClock(clock, pollMillis = 30_000L)` emits the current date and again whenever the **wall-clock** date changes (polled, so it survives deep sleep and clock/zone changes); `SystemZoneClock` re-reads `ZoneId.systemDefault()` on every call.

- [ ] **Step 1: Write the failing tests.**

`DayClockTest.kt` — keep the existing two tests; add:
```kotlin
    @Test fun a_forward_clock_jump_is_noticed_within_one_poll() = runTest {
        var offsetSeconds = 0L
        val base = SchedulerClock(testScheduler, Instant.parse("2026-10-07T12:00:00Z"))
        val jumping = object : java.time.Clock() {
            override fun getZone() = base.zone
            override fun withZone(zone: java.time.ZoneId) = base.withZone(zone)
            override fun instant(): Instant = base.instant().plusSeconds(offsetSeconds)
        }
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(jumping, pollMillis = 30_000).dates().toList(seen) }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 7)), seen)
        offsetSeconds = 86_400L                      // somebody set the clock one day ahead (or the phone slept through midnight)
        advanceTimeBy(31_000); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 8), seen.last())
        job.cancel()
    }
```
(imports: `kotlinx.coroutines.launch`, `kotlinx.coroutines.test.advanceTimeBy`, `runCurrent`, `kotlinx.coroutines.flow.toList` - `toList(seen)` form.)

`SystemZoneClockTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemZoneClockTest {
    private val original = TimeZone.getDefault()

    @After fun restore() = TimeZone.setDefault(original)

    @Test fun it_follows_the_default_time_zone_as_it_changes() {
        val clock = SystemZoneClock()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        assertEquals(ZoneId.of("Asia/Kolkata"), clock.zone)
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"))
        assertEquals(ZoneId.of("Africa/Cairo"), clock.zone)
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:data:testDebugUnitTest --tests "*DayClock*" --tests "*SystemZoneClock*"` → FAIL.

- [ ] **Step 3: Implement.**

`DayClock.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Emits the current local date, then again whenever the WALL-CLOCK date changes. It polls instead of sleeping until midnight because
 * `delay` runs on monotonic time that stops in deep sleep, and because the user can change the clock or the time zone.
 */
class DayClock(private val clock: Clock, private val pollMillis: Long = 30_000L) {
    fun dates(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(pollMillis)
        }
    }.distinctUntilChanged()
}
```
`SystemZoneClock.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The system clock whose zone is re-read on every call, so a time-zone change is followed without restarting anything. */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
```
Defaults: in `AppGraph` change the constructor default `clock: Clock = Clock.systemDefaultZone()` to `Clock = SystemZoneClock()`; in `HomeViewModel`, `AppsViewModel`, `AppDetailViewModel`, `HistoryViewModel` change the default of their `clock` parameter the same way (import `io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock`). Tests that pass an explicit clock are unaffected.

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "fix(data): follow the wall-clock date and the system time zone

The midnight timer slept on monotonic time and the clock captured the zone once, so 'today' stuck on yesterday after sleep or a clock/zone change."
```

---

### Task F5: Service resilience, recovery and boot gating (audit I-5, I-6, I-7; QA D-05, D-13 notification part)

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/sampler/{Retry,StatusNotifications}.kt`
- Modify: `sampler/{SamplerService,ServiceStarter,BootReceiver,CatchUpWorker,NotificationChannels,LiveNotification}.kt`, `settings/SettingsScreen.kt`, `res/values/strings.xml`, `AppGraph.kt` (nothing to add if F1 is done)
- Modify (tests): `sampler/{LiveNotificationTest,NotificationChannelsTest,BootReceiverTest}.kt`, `settings/SettingsContentTest.kt`; create `sampler/{RetryTest,CatchUpRecoveryTest}.kt`

**Interfaces:** `retryWithBackoff(...)`; `ServiceStarter.start(context): Boolean` (false when Android refused the start); `StatusNotifications.showPaused/cancelPaused`; `SamplerService.running: AtomicBoolean` (process-wide flag); `BootReceiver.startIfOnboarded(...)`; `CatchUpRecovery.afterCatchUp(...)`; `liveNotificationText(..., offline: Boolean = false)`; notification channel `status` (contract section 6); `SettingsContent(..., onOpenBatterySettings)`.

Behaviour: each service loop is retried with capped exponential backoff, and `onStartCommand` relaunches the work whenever the job is not active (I-5); a start command while the job runs re-posts the LAST notification text, not "0 B today" (D-05, and the initial text is "Measuring data usage"); offline shows "No connection" (D-13); `BootReceiver` starts the service only after onboarding is complete (I-7); the periodic worker, which may not start a foreground service on Android 12+, posts a "Measurement paused - tap to resume" notification when the service is not running and the start was refused (I-6); Settings gets a "Background" row that opens the battery-optimisation settings.

- [ ] **Step 1: Write the failing tests.**

`sampler/RetryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {
    @Test fun a_failing_block_is_retried_with_doubling_backoff_until_it_succeeds() = runTest {
        var calls = 0
        val errors = mutableListOf<Throwable>()
        val job = launch {
            retryWithBackoff(initialMillis = 1_000, maxMillis = 60_000, onError = { errors += it }) {
                calls++
                if (calls < 3) error("boom $calls")
            }
        }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(1_999); runCurrent()
        assertEquals(2, calls)               // the second wait is 2 s, not 1 s
        advanceTimeBy(1); runCurrent()
        assertEquals(3, calls)
        assertTrue(job.isCompleted)
        assertEquals(2, errors.size)
    }

    @Test fun the_backoff_is_capped() = runTest {
        var calls = 0
        val job = launch { retryWithBackoff(initialMillis = 1_000, maxMillis = 4_000, onError = {}) { calls++; error("always") } }
        runCurrent()
        advanceTimeBy(1_000 + 2_000 + 4_000 + 4_000); runCurrent()
        assertEquals(5, calls)               // waits: 1 s, 2 s, 4 s, 4 s (capped)
        job.cancel()
    }

    @Test fun cancellation_is_never_swallowed() = runTest {
        var calls = 0
        val job = launch { retryWithBackoff(1_000, 4_000, onError = {}) { calls++; throw CancellationException("stop") } }
        runCurrent()
        assertEquals(1, calls)
        assertTrue(job.isCancelled)
        assertFalse(job.isActive)
    }
}
```

`sampler/CatchUpRecoveryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchUpRecoveryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    private fun graph(onboarded: Boolean): AppGraph {
        val graph = AppGraph(app)
        runBlocking {
            if (onboarded) graph.onboarding.complete()
            graph.onboardingCompleted.first { it == onboarded }
        }
        return graph
    }

    @Test fun nothing_happens_before_onboarding_is_complete() = runBlocking {
        var starts = 0
        CatchUpRecovery.afterCatchUp(app, graph(false), serviceRunning = { false }, tryStart = { starts++; false })
        assertEquals(0, starts)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun a_running_service_needs_no_recovery() = runBlocking {
        var starts = 0
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { true }, tryStart = { starts++; true })
        assertEquals(0, starts)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun a_refused_start_posts_the_paused_notification() = runBlocking {
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { false }, tryStart = { false })
        assertNotNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun an_accepted_start_posts_nothing() = runBlocking {
        CatchUpRecovery.afterCatchUp(app, graph(true), serviceRunning = { false }, tryStart = { true })
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }

    @Test fun cancel_removes_the_paused_notification() {
        StatusNotifications.showPaused(app)
        assertNotNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
        StatusNotifications.cancelPaused(app)
        assertNull(shadowOf(manager).getNotification(StatusNotifications.PAUSED_ID))
    }
}
```

`BootReceiverTest.kt` — these tests encoded the old "always start" rule; REPLACE the first two tests with the gating tests below and keep the third:
```kotlin
    @Test fun boot_does_not_start_measuring_before_onboarding_is_complete() = runBlocking {
        val graph = AppGraph(app)
        graph.onboardingCompleted.first { it != null }
        var starts = 0
        assertEquals(false, BootReceiver.startIfOnboarded(app, graph) { starts++; true })
        assertEquals(0, starts)
    }

    @Test fun boot_starts_measuring_after_onboarding() = runBlocking {
        val graph = AppGraph(app)
        graph.onboarding.complete()
        graph.onboardingCompleted.first { it == true }
        var starts = 0
        assertEquals(true, BootReceiver.startIfOnboarded(app, graph) { starts++; true })
        assertEquals(1, starts)
    }
```
(add the imports `io.github.khaledbahaaeldin.emberbyte.AppGraph`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.runBlocking`; `other_broadcasts_are_ignored` stays).

`LiveNotificationTest.kt` add:
```kotlin
    @Test fun offline_says_no_connection_instead_of_a_stale_speed() {
        val text = liveNotificationText(1_000_000L, 195L, ByteUnits.DECIMAL, showSpeed = true, offline = true)
        assertEquals("1.00 MB today", text.title)
        assertEquals("No connection", text.text)
    }
```
`NotificationChannelsTest.kt` add: after `ensure`, the `status` channel exists with `IMPORTANCE_LOW`.
`SettingsContentTest.kt`: pass the new parameter `onOpenBatterySettings = {}` in `show(...)`, and add a test that scrolling to "Battery settings" and clicking it calls the callback.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest` → FAIL (compilation).

- [ ] **Step 3: Implement.**

`sampler/Retry.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [block]; when it throws (other than cancellation) it reports the error, waits and runs it again, doubling the wait up to
 * [maxMillis]. Returns when [block] returns normally. Cancellation always propagates.
 */
suspend fun retryWithBackoff(
    initialMillis: Long,
    maxMillis: Long,
    onError: (Throwable) -> Unit,
    block: suspend () -> Unit,
) {
    var wait = initialMillis
    while (true) {
        try {
            block()
            return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error)
            delay(wait)
            wait = minOf(wait * 2, maxMillis)
        }
    }
}
```

`res/values/strings.xml` (append inside `<resources>`):
```xml
    <string name="channel_status_name">Status</string>
    <string name="channel_status_description">Tells you when measuring has paused</string>
    <string name="notification_paused_title">Measurement paused</string>
    <string name="notification_paused_text">Tap to resume measuring your data usage.</string>
```

`NotificationChannels.kt`: add `const val STATUS = "status"` and, inside `ensure`, create a second channel:
```kotlin
        manager.createNotificationChannel(
            NotificationChannel(STATUS, context.getString(R.string.channel_status_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_status_description)
                setShowBadge(false)
            },
        )
```

`sampler/StatusNotifications.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import io.github.khaledbahaaeldin.emberbyte.R

object StatusNotifications {
    const val PAUSED_ID = 1002

    /** "Measurement paused - tap to resume". Opening the app starts the service from the foreground, which Android allows. */
    fun showPaused(context: Context) {
        NotificationChannels.ensure(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(context.getString(R.string.notification_paused_title))
            .setContentText(context.getString(R.string.notification_paused_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(PAUSED_ID, notification)
    }

    fun cancelPaused(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(PAUSED_ID)
    }
}
```

`ServiceStarter.kt` (replace):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceStarter {
    private const val TAG = "ServiceStarter"

    /** Starts the sampler service. Returns false when Android refused (a foreground start from the background); never throws. */
    fun start(context: Context): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, SamplerService::class.java))
        true
    } catch (error: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException
        Log.w(TAG, "Foreground start not allowed right now", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "Foreground start refused", error)
        false
    }
}
```
(Update any caller that ignored the old `Unit` return: the call sites compile unchanged.)

`BootReceiver.kt` (replace):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        val graph = (app as EmberbyteApplication).graph
        CoroutineScope(Dispatchers.Default).launch {
            try {
                startIfOnboarded(app, graph)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Measuring starts at boot only for people who finished the first-run flow. */
        internal suspend fun startIfOnboarded(
            context: Context,
            graph: AppGraph,
            start: (Context) -> Boolean = ServiceStarter::start,
        ): Boolean {
            if (graph.onboardingCompleted.first { it != null } != true) return false
            return start(context)
        }
    }
}
```

`CatchUpWorker.kt`: replace the body of `doWork()` and add the recovery object (keep `CatchUpScheduler` as it is):
```kotlin
    override suspend fun doWork(): Result {
        val graph = (applicationContext as EmberbyteApplication).graph
        graph.catchUp.run()
        graph.prune()
        CatchUpRecovery.afterCatchUp(applicationContext, graph)
        return Result.success()
    }
```
```kotlin
internal object CatchUpRecovery {
    /**
     * If measuring should be running but is not: try to restart it; when Android refuses (background start on Android 12+), tell the
     * user with a "Measurement paused" notification instead of failing silently.
     */
    suspend fun afterCatchUp(
        context: Context,
        graph: AppGraph,
        serviceRunning: () -> Boolean = { SamplerService.running.get() },
        tryStart: (Context) -> Boolean = ServiceStarter::start,
    ) {
        if (graph.onboardingCompleted.first { it != null } != true) return
        if (serviceRunning()) return
        if (!tryStart(context)) StatusNotifications.showPaused(context)
    }
}
```
(imports: `io.github.khaledbahaaeldin.emberbyte.AppGraph`; the `observeCompleted()` import can go.)

`LiveNotification.kt`: change the function to
```kotlin
fun liveNotificationText(todayBytes: Long, rxBps: Long, units: ByteUnits, showSpeed: Boolean, offline: Boolean = false): LiveNotificationText {
    val today = formatBytes(todayBytes, units)
    val speed = formatBytes(rxBps, units)
    return LiveNotificationText(
        title = "${today.value} ${today.unit} today",
        text = when {
            !showSpeed -> null
            offline -> "No connection"
            else -> "↓ ${speed.value} ${speed.unit}/s"
        },
    )
}
```

`SamplerService.kt` (replace the whole file):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val TAG = "SamplerService"
private const val NOTIFICATION_ID = 1001
private const val CATCH_UP_INTERVAL_MS = 5 * 60 * 1000L
private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 60_000L

/** Foreground service (type specialUse) that keeps the sampler, the catch-up loop and the live notification running. */
class SamplerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var lastText: LiveNotificationText? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        running.set(true)
        StatusNotifications.cancelPaused(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as EmberbyteApplication).graph
        val builder = LiveNotificationBuilder(this)
        // Re-post the last known text, never a zero placeholder, when Android calls us again while the work is already running.
        val text = lastText ?: LiveNotificationText(getString(R.string.notification_measuring), null)
        val notification = builder.build(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (job?.isActive != true) job = scope.launch { supervise(graph, builder) }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun supervise(graph: AppGraph, builder: LiveNotificationBuilder) = supervisorScope {
        launch { retrying("sampler") { graph.sampler.run() } }
        launch {
            retrying("catch-up") {
                while (true) {
                    graph.catchUp.run()
                    graph.prune()
                    delay(CATCH_UP_INTERVAL_MS)
                }
            }
        }
        launch { retrying("notification") { updateNotification(graph, builder) } }
    }

    private suspend fun retrying(name: String, block: suspend () -> Unit) =
        retryWithBackoff(
            initialMillis = INITIAL_BACKOFF_MS,
            maxMillis = MAX_BACKOFF_MS,
            onError = { Log.e(TAG, "$name failed; retrying", it) },
            block = block,
        )

    private suspend fun updateNotification(graph: AppGraph, builder: LiveNotificationBuilder) {
        val manager = getSystemService(NotificationManager::class.java)
        val speeds = graph.usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }
        combine(speeds, graph.usage.observeToday(), graph.settings.observe()) { speed, today, settings ->
            if (settings.liveNotificationEnabled) {
                liveNotificationText(
                    todayBytes = today.totalBytes,
                    rxBps = speed?.rxBps ?: 0L,
                    units = settings.unitSystem.toByteUnits(),
                    showSpeed = settings.notificationShowsSpeed,
                    offline = speed != null && speed.network == null,
                )
            } else {
                // A foreground service must show a notification; keep it minimal when the user turned this off.
                LiveNotificationText(getString(R.string.notification_measuring), null)
            }
        }.distinctUntilChanged().conflate().collect { text ->
            lastText = text
            manager.notify(NOTIFICATION_ID, builder.build(text))
            delay(1_000L) // at most one update per second
        }
    }

    companion object {
        /** True while a [SamplerService] instance exists in this process. */
        val running = AtomicBoolean(false)
    }
}
```

`settings/SettingsScreen.kt`: add the parameter `onOpenBatterySettings: () -> Unit` to `SettingsContent` (before `modifier`), and after the "Show speed" item add:
```kotlin
        item { SectionTitle("Background") }
        item {
            Text(
                "Some phones stop background apps. Allow Emberbyte in the battery settings so it can keep measuring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TextButton(onClick = onOpenBatterySettings) { Text("Battery settings") } }
```
In `SettingsScreen` pass
```kotlin
        onOpenBatterySettings = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
```
and make the existing `onOpenSource = { uriHandler.openUri(SOURCE_URL) }` safe: `onOpenSource = { runCatching { uriHandler.openUri(SOURCE_URL) } }` (audit M-6: no browser installed must not crash). Add `import android.content.Intent`.

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "fix(app): keep measuring alive, say so when it pauses, and never start before consent

Loops now retry with backoff and are relaunched on the next start command; the worker posts 'Measurement paused' when Android refuses a background start; the boot receiver waits for onboarding; offline and restarts no longer show stale notification text."
```

---

### Task F6: UI defects (QA D-06, D-07, D-14, D-15, D-18, D-19, D-21; audit M-5)

**Files:**
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/format/ForceLtr.kt`
- Modify: `ui/design/.../number/MorphingNumber.kt`, `ui/design/.../navbar/FloatingPillNavBar.kt`, `ui/design/.../tiles/{AppRow,Tiles}.kt`, `app/.../home/{HomeUiState,HomeMapping,HomeScreen}.kt`, `app/.../history/History.kt`, `app/.../apps/{AppDetail,AppsScreen}.kt`, `app/.../EmberbyteApp.kt`, `app/.../AppGraph.kt`
- Tests: new `ui/design/.../format/ForceLtrTest.kt`, `ui/design/.../navbar/NavBarFontScaleTest.kt`, additions to `MorphingNumberTest`, `TilesTest`, `app/.../home/HomeContentTest.kt`, `HomeMappingM2Test.kt`, `app/.../history/HistoryTest.kt`

**Interfaces:** `@Composable fun ForceLtr(content)`; `HomeUiState.topAppsLocked: Boolean`; `HomeScreen/HomeContent(..., onOpenApp: (String) -> Unit)`; `HistoryViewModel(usage, settings, permissions, clock, locale, dates)` and `HistoryUiState.needsUsageAccess`; `AppRow` exposes a spoken mobile/Wi-Fi split as its state description.

- [ ] **Step 1: Write the failing tests.**

`ForceLtrTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForceLtrTest {
    @get:Rule val rule = createComposeRule()

    @Test fun numbers_stay_left_to_right_inside_a_right_to_left_screen() {
        var outer: LayoutDirection? = null
        var inner: LayoutDirection? = null
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                outer = LocalLayoutDirection.current
                ForceLtr { inner = LocalLayoutDirection.current }
            }
        }
        assertEquals(LayoutDirection.Rtl, outer)
        assertEquals(LayoutDirection.Ltr, inner)
    }
}
```

`NavBarFontScaleTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavBarFontScaleTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    @Test fun at_200_percent_font_every_item_stays_inside_the_bar() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(360.dp)) { FloatingPillNavBar(items, "home", {}, visible = true) }
                }
            }
        }
        val bar = rule.onNodeWithTag("pill_nav_bar").getBoundsInRoot()
        for (item in items) {
            val bounds = rule.onNodeWithTag("nav_${item.id}").getBoundsInRoot()
            assertTrue("${item.id} right=${bounds.right} bar=${bar.right}", bounds.right <= bar.right)
            assertTrue("${item.id} left=${bounds.left}", bounds.left >= bar.left)
        }
    }
}
```

Additions:
  * `MorphingNumberTest`: `fun digits_are_not_separate_semantics_nodes()`: render `MorphingNumber(213_000_000L, 0L, ByteUnits.DECIMAL, "213 megabytes used today", animate = false)`; assert `rule.onNodeWithContentDescription("213 megabytes used today").assertIsDisplayed()` and `rule.onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)` (the digits must not leak as separate nodes).
  * `TilesTest` (or `TilesEdgeCaseTest`): `fun an_app_row_speaks_its_mobile_and_wifi_split()`: render `AppRow(AppRowUi("com.video", "Video", 600_000_000L, 400_000_000L), ByteUnits.DECIMAL, onClick = {})` and assert the node `hasText("Video")` has `SemanticsProperties.StateDescription` equal to `"Mobile 600 megabytes, Wi-Fi 400 megabytes"` (use `SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "...")`).
  * `HomeMappingM2Test`: `topAppsLocked` is true exactly when `permissions.usageAccess == false`, false when granted, false when permissions are unknown (null).
  * `HomeContentTest`: update `show(...)` to pass `onOpenApp`; add `fun tapping_a_top_app_opens_it()`: state with `topApps = listOf(AppRowUi("com.video","Video",1,1))`, scroll to "Video", click, assert the callback received `"com.video"`; and `fun locked_top_apps_explain_why()`: `HomeUiState(topAppsLocked = true)` shows "Allow usage access to see which apps use your data.".
  * `HistoryTest`: the view-model construction becomes `HistoryViewModel(usage, settings, FakePermissionRepository(), clock, Locale.ENGLISH, flowOf(today))`; add a mapping assertion that `HistoryUiState.needsUsageAccess` follows `PermissionState.usageAccess` (grant/revoke with `FakePermissionRepository.set`).

- [ ] **Step 2: Run to verify they fail** — `./gradlew :ui:design:testDebugUnitTest :app:testDebugUnitTest` → FAIL.

- [ ] **Step 3: Implement.**

`ui/design/.../format/ForceLtr.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Numbers, units and rates must read the same in every language: lay their content out left-to-right even in an RTL screen (QA D-06). */
@Composable
fun ForceLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
```
Apply `ForceLtr { ... }` around: the whole `Column` of `MorphingNumber`; both `Text` lines in `SpeedTile`; the total `Text` in `AppRow`; the "Today by network" text in `HomeContent`; the "By network" text in `AppDetailScreen`; the Total/Average values in `HistoryScreen`; the "... in total" line in `AppsContent`.

`MorphingNumber.kt` (D-18): replace
`Column(modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription })`
with
`Column(modifier.clearAndSetSemantics { this.contentDescription = contentDescription })` (import `androidx.compose.ui.semantics.clearAndSetSemantics`; drop the unused `semantics` import).

`FloatingPillNavBar.kt` (D-07): add `const val`/constant and wrap the content:
```kotlin
private const val MAX_NAV_FONT_SCALE = 1.15f
```
In `FloatingPillNavBar`, after `val motion = ...`:
```kotlin
    val density = LocalDensity.current
    val cappedDensity = remember(density) { Density(density.density, minOf(density.fontScale, MAX_NAV_FONT_SCALE)) }
```
and wrap everything that follows (the `val content = ...` lambda and the `if (reduceMotion) ... else ...` block) in `CompositionLocalProvider(LocalDensity provides cappedDensity) { ... }` (imports: `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.unit.Density`). In `NavItem`'s label `Text` add `overflow = TextOverflow.Ellipsis` (import `androidx.compose.ui.text.style.TextOverflow`).

`AppRow.kt` (D-19): add to the row's modifier chain, after `.clickable(...)`: 
```kotlin
            .semantics {
                stateDescription = "Mobile ${spoken(model.mobileBytes, units)}, Wi-Fi ${spoken(model.wifiBytes, units)}"
            }
```
with a private helper in the same file `private fun spoken(bytes: Long, units: ByteUnits): String { val f = formatBytes(bytes, units); return "${f.value} ${spokenUnit(f.unit)}" }` (imports `androidx.compose.ui.semantics.semantics`, `stateDescription`, `spokenUnit`).

Home (D-15, D-21, audit M-5):
  * `HomeUiState`: add `val topAppsLocked: Boolean = false`.
  * `buildHomeUiState`: add `topAppsLocked = permissions?.usageAccess == false` to the returned state.
  * `HomeScreen` and `HomeContent`: add the parameter `onOpenApp: (String) -> Unit` (before `modifier`); the top-apps tile becomes
```kotlin
            BentoTile(title = "Top apps today", modifier = Modifier.fillMaxWidth()) {
                when {
                    state.topAppsLocked -> Text("Allow usage access to see which apps use your data.", style = MaterialTheme.typography.bodyMedium)
                    state.topApps.isEmpty() -> Text("No per-app data yet. It appears within about 5 minutes once usage access is allowed.", style = MaterialTheme.typography.bodyMedium)
                    else -> state.topApps.forEach { app -> AppRow(app, state.units, onClick = { onOpenApp(app.packageName) }) }
                }
            }
```
  * `EmberbyteApp.kt`: pass `onOpenApp = { navController.navigate(Routes.appDetail(it)) }` to `HomeScreen`.

History (D-14): `HistoryViewModel` takes `permissions: PermissionRepository` as its third parameter and combines `permissions.observe()` into the state; `HistoryUiState` gains `val needsUsageAccess: Boolean = false`; the caveat `Text` in `HistoryScreen` is shown only when `state.needsUsageAccess`; `AppGraph.historyViewModelFactory()` passes `permissions`.

- [ ] **Step 4: Run to verify they pass** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add ui app
git commit -m "fix(ui): keep numbers left-to-right, cap navbar font scale, wire dead rows and describe row splits

RTL reversed the hero digits, 200% font pushed the Lens tab off the bar, Home app rows did nothing, digits were separate accessibility nodes and the mobile/Wi-Fi split was colour only."
```

---

### Task F7: Final proof and a truthful completion report

**Files:** none in the repo except what the testers commit as tests. Reports go to `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/`.

Do not change production code in this task; report defects with evidence instead.

- [ ] **Step 1: Full gate with no build cache.** `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace` → BUILD SUCCESSFUL. Record the test count per module and the lint error/warning counts exactly as printed (the first completion report claimed "0 warnings" while there were 6; report the real numbers).

- [ ] **Step 2: Emulator proof.** AVD `Violet_API_36` (`$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Violet_API_36 -no-snapshot-save`, in the background; wait for `sys.boot_completed`). `adb` is `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. `PKG=io.github.khaledbahaaeldin.emberbyte`. Start from a clean state: `adb uninstall $PKG`, install the fresh debug APK, then grant usage access with `adb shell appops set $PKG GET_USAGE_STATS allow` and notifications with `adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS` after the onboarding screen is shown. For each item report PASS / FAIL / CANNOT-VERIFY with evidence (command output, a UI dump or a screenshot you looked at):

  1. **Navigation never resets (D-01).** After onboarding, tap each of the four tabs 10 times in random order, returning to Home between them: every tap lands on the chosen tab and STAYS there for at least 5 seconds (0 bounces in 40 taps). Open Apps > a row > App detail; press HOME and reopen the app: the detail is still shown. Open Settings, toggle "Dynamic colour": still on Settings, and Back returns to Home. Rotate the device on Apps: still on Apps.
  2. **Dark theme text (D-03) and AMOLED (D-04).** `adb shell cmd uimode night yes`: titles, back arrows, the Home gear and the Settings switch labels are light on dark (sample a screenshot pixel or compute contrast > 4.5:1). Turn "AMOLED black" on: a screenshot pixel of the background is exactly `(0, 0, 0)` on Home and Settings.
  3. **Data correctness (D-02/C-1).** `pm clear`, finish onboarding, wait for the first catch-up. Download a known amount of traffic (`adb shell "toybox wget -q -O /dev/null http://speedtest.tele2.net/10MB.zip"` three times, or the browser) and record the system truth with `adb shell dumpsys netstats --uid` (sum of all UIDs for the current day, per network) before and after. Home "Today" minus the value before must equal the system delta within **5 %**, and Home "Today" must be **>= the sum of the Top apps rows**. Take a Home reading every 2 minutes for 20 minutes while the device is idle after the transfer, crossing at least one even-UTC-hour boundary: the readings **never decrease**. Compare Home, the Apps total, the History "Day" bar for today and the notification text: they agree (allow the documented day-boundary window difference only).
  4. **Phantom data (C-2).** Note the Home mobile number, then `adb shell cmd connectivity airplane-mode enable`, wait 30 s, `... disable`, wait 60 s: the mobile number grows by no more than the real traffic of that minute (never by hundreds of MB). Repeat with `adb shell svc data disable` / `enable` and with `svc wifi disable` / `enable`. Read the newest `total_minute` rows from the database (pull `emberbyte.db`, `-wal`, `-shm` with `adb exec-out run-as $PKG cat databases/<file>` and read them with Python `sqlite3`): no minute row contains an absurd value.
  5. **Late ticks (I-2).** Put the device to sleep for 10 minutes (`adb shell input keyevent KEYCODE_SLEEP`, wait, `KEYCODE_WAKEUP`): `coverage_gap` has a `DEVICE_ASLEEP` row (or none if the CPU never slept), no minute row holds the whole interval, and the Home banner does NOT mention it.
  6. **Duplicate rows (I-1).** Query `usage_hourly`: for every (`hourStart`, `uid`, `network`) there is exactly ONE row, and every `hourStart` is a multiple of 7200 seconds. Restart the service twice within one window: still one row per key.
  7. **Time zone and clock (D-09, D-12).** `adb shell cmd alarm set-timezone Asia/Kolkata` (and back): Home "Today" equals the History "Day" bar for today and the week-bar value for today; `Pacific/Kiritimati` too. Set the clock one day forward and back (`adb shell date` may not work on a locked-down image; if so say CANNOT-VERIFY): Home, the notification and History roll to the right day within one minute.
  8. **Service resilience.** `adb shell am force-stop $PKG` then open the app: the sampler runs again; `dumpsys activity services $PKG` shows `isForeground=true` and type `0x40000000`. Open the app while it is running and read `dumpsys notification --noredact`: the live notification title shows today's real total, never `0 B today`. Airplane mode: the notification text says "No connection". Reboot without opening the app: the service is running after boot ONLY if onboarding had been completed; on a fresh install that has not finished onboarding, a reboot does not start it.
  9. **Paused notification (I-6).** With onboarding completed, `adb shell am force-stop $PKG`, then trigger the periodic worker (`adb shell cmd jobscheduler run -f $PKG 1` or wait up to 15 minutes): if Android refuses the foreground start, a notification "Measurement paused" appears and tapping it opens the app and restarts measuring (CANNOT-VERIFY is acceptable if the emulator allows the start; say which).
  10. **RTL and font scale (D-06, D-07).** `adb shell cmd locale set-app-locales $PKG --locales ar`: the hero shows the same digits as in English (for example 213 MB reads 213, not 312). `adb shell settings put system font_scale 2.0` and `adb shell wm density 560`: all four navbar items are visible and inside the pill. Restore font scale 1.0 and `adb shell wm density reset`.
  11. **Smaller fixes.** Home top-app rows open the app detail; with usage access revoked the Top apps tile shows the locked text; History shows the "only the last 7 days" caveat ONLY when usage access is missing; Settings has the "Background" row that opens the system battery-optimisation list; "Source code" does not crash when no browser exists (CANNOT-VERIFY is acceptable).
  12. **Regression quick pass.** Onboarding, Settings persistence across `force-stop`, units switch, the gap banner after a 6-minute stop, light/dark, reduce-motion live toggle (`adb shell settings put global animator_duration_scale 0` while the app is open, then back to 1), `logcat -d | grep -E "AndroidRuntime|FATAL"` clean.

  Restore every device setting you change, stop the emulator (`adb emu kill`) and confirm `git status` shows only the Claude-owned docs.

- [ ] **Step 3: Truthful completion report.** Overwrite `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/m2-completion-report.md` with a new report that contains: the fix-wave tasks with commit ranges and tester verdicts; per-module test counts and lint counts exactly as measured in Step 1; every deviation from this plan with the reason; the Step 2 results; the deferred minors; and a short **"Corrections to the previous report"** section listing what the first report got wrong (there is no `DominantSubscription` class, the Apps chips are Today / 7 days / This month, the reduce-motion function is `rememberReduceMotion`, lint had 6 warnings, the test counts per module were 87 and 113, WorkManager arrived in Task 14). Do not claim anything you did not run.

- [ ] **Step 4: Stop.** Make sure `git status` shows nothing but the Claude-owned docs, you are on `m2-real-usage`, and nothing was pushed. Report "M2 fix wave is ready for the Claude re-audit on branch m2-real-usage".

