# Emberbyte M2 "Real Usage" Implementation Plan

> **For the executing agents (Antigravity):** follow the orchestration protocol in
> `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` (one implementer + one tester per task, a ledger,
> no pushing). Steps use checkbox (`- [ ]`) syntax. Do the tasks in order; each ends in a green build and a commit.

**Goal:** Replace the M1 fake data with real measurement: a foreground sampler (live speed + minute totals), hourly
per-app history from `NetworkStatsManager`, Room storage, a live notification, and the screens that show it
(Home with real data, Apps, App detail, History, Settings, Onboarding).

**Architecture:** Pure-Kotlin maths in `:core:engine` (counter reconciliation, series building, aggregation). A Room
`UsageStore` plus thin Android "source" wrappers in `:core:data`, a coroutine `SamplerEngine` and `HourlyCatchUp`
driven by a foreground `SamplerService` and a WorkManager worker in `:app`. Real repositories replace the fakes in
the hand-written `AppGraph`; the fakes stay for tests. No DI framework.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0, Gradle 9.6.0, JDK 17, Room 2.8.5 + KSP 2.3.12 (verified together on this
exact toolchain), WorkManager 2.12.0, DataStore 1.2.1, Compose BOM 2026.09.00 with Material 3 1.5.0-alpha29,
Robolectric 4.17.

Specs (read first, the API contract wins on any naming conflict): `docs/superpowers/specs/2026-10-01-datakb-api-contract.md`
(**section 9 is the M2 contract**), `...-backend-spec.md`, `...-frontend-spec.md`, `...-design.md`.
M1 record: `docs/superpowers/plans/2026-10-01-emberbyte-m1-foundation.md`.

## Global Constraints

- minSdk **29**, compileSdk **37**, targetSdk **36**, Java/Kotlin target **17**. Do not change AGP/Kotlin/Gradle/Compose versions.
- Root package and applicationId `io.github.khaledbahaaeldin.emberbyte`; sub-packages `.engine`, `.data`, `.ui.design`, `.lens`.
- `:ui:design` must NOT depend on `:core:*`. `:core:engine` is pure Kotlin/JVM (no Android classes).
- Room types never appear in a public signature that `:app` touches: `:app` uses `UsageStores.create(context)`.
- Local-first: no network permission, no analytics, no accounts. Licence GPL-3.0-or-later; no proprietary libraries (dependencies must be GPL-compatible, for example Apache-2.0 or MIT).
- Bytes are always `Long` bytes. Times are `Instant`/epoch milliseconds. Network stored as `0 = MOBILE`, `1 = WIFI`;
  subscription id `-1` means Wi-Fi/unknown.
- All animation goes through `MaterialTheme.motionScheme`. The system "remove animations" setting disables motion.
- Every behaviour is test-first (write the test, run it and watch it fail, implement, watch it pass).
- Code in this plan is meant to be used verbatim. If it does not compile because of tool/version drift, make the
  smallest fix, and record exactly what changed and why in your task report. Never weaken a test to make it pass.
- Git: before EVERY commit check `git config user.name` = `Khaledbahaaeldin` and `git config user.email` =
  `khaled.bahaaeldin@aiu.edu.eg` (repo-local config only). Commit messages explain WHY and end with the
  `Co-Authored-By` trailer from the prompt file. **Never push, never force, never `--no-verify`.**
- Windows host, Git Bash shell, `./gradlew`. Gradle needs generous timeouts (first run of a task can take minutes).

### Verified facts (do not re-research)

- Room 2.8.5 + KSP 2.3.12 + AGP 9.4.0 + Kotlin 2.4.20 compile and pass a Robolectric in-memory-database test with
  exactly the Gradle configuration in Task 1. `kapt` does not work here; always use `ksp(...)`.
- `QUERY_ALL_PACKAGES` is required to resolve app labels for any UID (package visibility, API 30+).
- `NetworkStatsManager.querySummary(...)` returns interpolated per-UID summaries for the whole queried interval, so
  history is queried **one hour at a time**. Device history is limited, so back-fill is capped at 7 days.
- `SubscriptionManager.getDefaultDataSubscriptionId()` is static and needs no permission.
- A foreground service of type `specialUse` needs the permission `FOREGROUND_SERVICE_SPECIAL_USE`, a `<property>`
  subtype in the manifest, and `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` in `startForeground` on API 34+.

### Deviations from the earlier specs (recorded by the planner; specs are already updated)

1. **No Hilt.** The hand-written `AppGraph` stays (KSP+Hilt on AGP 9 is risk for little gain).
2. **Plans are not in M2.** Production uses `NoPlanRepository`; Home shows a "Data plan" tile saying plans are coming.
   Plan chips for several plans are M3.
3. **Not in M2:** widgets, budgets, spike alerts, export/backup, Live Lens, per-app screen time, the "Cycle" range chip,
   the Android 16 progress-style notification, plan editor, `READ_PHONE_STATE` flows, AGP/Gradle version bumps.
4. Deferred navbar polish: N-5 (density-safe drag test) and N-6 (preview semantics) move to M3.

## File Structure (new or changed in M2)

```
.github/workflows/ci.yml                               (modify: permissions)
gradle/libs.versions.toml, build.gradle.kts            (modify)
core/engine/src/main/kotlin/.../engine/counter/Counter.kt                 NEW  CounterReading/Delta/Reconciler
core/engine/src/main/kotlin/.../engine/usage/Rows.kt                      NEW  MinuteTotal, HourlyUsage, AppMeta
core/engine/src/main/kotlin/.../engine/usage/SeriesBuilder.kt             NEW
core/engine/src/main/kotlin/.../engine/usage/AppAggregator.kt             NEW
core/engine/src/main/kotlin/.../engine/usage/Attribution.kt               NEW  dominantSubscriptionId
core/engine/src/main/kotlin/.../engine/usage/HourlyReconciler.kt          NEW
core/data/build.gradle.kts                                                (modify: Room/KSP/DataStore)
core/data/schemas/.../1.json                                              NEW  (generated, COMMIT it)
core/data/src/main/kotlin/.../data/db/*.kt                                NEW  entities, DAO, database
core/data/src/main/kotlin/.../data/store/*.kt                             NEW  UsageStore, RoomUsageStore, UsageStores, mappers
core/data/src/main/kotlin/.../data/source/Sources.kt                      NEW  interfaces
core/data/src/main/kotlin/.../data/android/*.kt                           NEW  Android implementations of sources
core/data/src/main/kotlin/.../data/sampler/SamplerEngine.kt, HourlyCatchUp.kt   NEW
core/data/src/main/kotlin/.../data/util/DayClock.kt                       NEW
core/data/src/main/kotlin/.../data/RoomUsageRepository.kt                 NEW
core/data/src/main/kotlin/.../data/DataStore*.kt, AndroidPermissionRepository.kt, NoPlanRepository.kt, OnboardingRepository.kt   NEW
core/data/src/main/kotlin/.../data/fake/InMemoryUsageStore.kt + fake sources  NEW
ui/design/.../model/UiModels.kt (modify), tiles/PermissionPrompt.kt (NEW), theme/EmberbyteTheme.kt (modify)
app/src/main/kotlin/.../sampler/*.kt  NEW  SamplerService, BootReceiver, CatchUpWorker, notification pieces
app/src/main/kotlin/.../{apps,history,settings,onboarding}/*.kt           NEW  screens + view models
app/src/main/kotlin/.../AppGraph.kt, EmberbyteApp.kt, MainActivity.kt, home/*  (modify)
app/src/main/AndroidManifest.xml, res/xml/data_extraction_rules.xml, res/drawable/*, res/mipmap-anydpi/*
```

---

### Task 1: Dependencies and CI hardening

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `core/data/build.gradle.kts`, `app/build.gradle.kts`, `.github/workflows/ci.yml`

**Interfaces:**
- Produces: version-catalog aliases `libs.androidx.room.runtime`, `libs.androidx.room.compiler`, `libs.androidx.room.testing`, `libs.androidx.work.runtime.ktx`, `libs.androidx.datastore.preferences`, `libs.androidx.test.core`, plugins `libs.plugins.ksp`, `libs.plugins.androidx.room`. `:core:data` becomes a Room+KSP module; `:app` loses its direct Haze dependency (audit M-2).

- [ ] **Step 1: Replace `gradle/libs.versions.toml` with exactly this**

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
composeBom = "2026.09.00"
coroutines = "1.11.0"
activityCompose = "1.13.0"
navigationCompose = "2.10.2"
lifecycle = "2.11.0"
coreKtx = "1.19.1"
robolectric = "4.17"
junit = "4.13.2"
haze = "2.0.1"
ksp = "2.3.12"
room = "2.8.5"
work = "2.12.0"
datastore = "1.2.1"
androidxTest = "1.7.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version = "1.5.0-alpha29" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
haze = { module = "dev.chrisbanes.haze:haze", version.ref = "haze" }
haze-blur = { module = "dev.chrisbanes.haze:haze-blur", version.ref = "haze" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Root `build.gradle.kts` — replace with**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}
```

- [ ] **Step 3: `core/data/build.gradle.kts` — replace with**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

room { schemaDirectory("$projectDir/schemas") }

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte.data"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:engine"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4: `app/build.gradle.kts` — replace the `dependencies { ... }` block with**

```kotlin
dependencies {
    implementation(project(":ui:design"))
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
    implementation(project(":feature:lens"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```
(The direct `implementation(libs.haze)` is gone on purpose: Haze stays inside `:ui:design`.)

- [ ] **Step 5: `.github/workflows/ci.yml` — add a top-level permissions block**

Insert these two lines directly after the `on:` block (before `concurrency:`), keep everything else unchanged:
```yaml
permissions:
  contents: read
```

- [ ] **Step 6: Verify the whole build still works**

Run: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL`, all existing tests (141) green. If `:app` fails because it used Haze directly, grep `dev.chrisbanes` in `app/src`; it must be empty (the app only uses `GlassSource` from `:ui:design`).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts core/data/build.gradle.kts app/build.gradle.kts .github/workflows/ci.yml
git commit -m "build: add Room, KSP, DataStore and WorkManager; harden CI permissions

Room+KSP was proven on this exact toolchain first. Haze stays inside the design
module and CI gets least-privilege permissions (audit M-2, M-23)."
```

---

### Task 2: Navbar housekeeping (audit M-3, M-4/N-1, N-2, selected-tap)

**Files:**
- Modify: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/FloatingPillNavBar.kt`
- Modify: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarLogic.kt`
- Modify: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarLogicTest.kt`
- Create: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarHousekeepingTest.kt`

**Interfaces:** public API of `FloatingPillNavBar` is unchanged. Behaviour changes: the bar fills the width it is given (M-3); the haptic tick fires only when the selection really changes (M-4/N-1); tapping the already-selected item does nothing; the unused `indexAt` is deleted (N-2).

- [ ] **Step 1: Write the failing tests**

`NavBarHousekeepingTest.kt`:
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavBarHousekeepingTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    private class CountingHaptics : HapticFeedback {
        var calls = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { calls++ }
    }

    private fun show(selected: String, haptics: CountingHaptics, onSelect: (String) -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                    FloatingPillNavBar(items, selected, onSelect, visible = true)
                }
            }
        }
    }

    @Test fun bar_fills_the_width_it_is_given() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.width(400.dp)) { FloatingPillNavBar(items, "home", {}, visible = true) }
            }
        }
        rule.onNodeWithTag("pill_nav_bar").assertWidthIsEqualTo(400.dp)
    }

    @Test fun tapping_the_selected_item_does_nothing() {
        val haptics = CountingHaptics()
        var picked: String? = null
        show("home", haptics) { picked = it }
        rule.onNodeWithTag("nav_home").performClick()
        assertNull(picked)
        assertEquals(0, haptics.calls)
    }

    @Test fun tapping_another_item_selects_it_and_ticks_once() {
        val haptics = CountingHaptics()
        var picked: String? = null
        show("home", haptics) { picked = it }
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)
        assertEquals(1, haptics.calls)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*NavBarHousekeepingTest"`
Expected: FAIL (`bar_fills_the_width…` width mismatch; `tapping_the_selected_item…` onSelect was called; haptics 0 for the tap).

- [ ] **Step 3: Edit `FloatingPillNavBar.kt`**

(a) Add the import `import androidx.compose.foundation.layout.fillMaxWidth` (next to the other `foundation.layout` imports).

(b) In `PillSurface`, in the `Surface(modifier = Modifier ... )` chain, replace the last line `.height(64.dp),` with:
```kotlin
            .fillMaxWidth()
            .height(64.dp),
```

(c) In `PillSurface`, inside `fun resolve(x: Float)`, replace this block
```kotlin
                                dragDx = dragResistance((x - center).coerceIn(-half, half))
                                if (index != previewIndex) {
                                    previewIndex = index
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
```
with
```kotlin
                                dragDx = dragResistance((x - center).coerceIn(-half, half))
                                val previous = previewIndex ?: selectedIndex
                                previewIndex = index
                                if (index != previous && hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
```

(d) In `PillSurface`, in the `NavItem(...)` call, replace `onClick = { onSelect(item.id) },` with:
```kotlin
                    onClick = {
                        if (item.id != selectedId) {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(item.id)
                        }
                    },
```

- [ ] **Step 4: Delete `indexAt` (N-2)**

In `NavBarLogic.kt` delete the whole function `internal fun indexAt(x: Float, width: Float, count: Int): Int { ... }` together with its KDoc comment. In `NavBarLogicTest.kt` delete the three test functions `indexAt_splits_the_bar_into_equal_slots`, `indexAt_clamps_outside_the_bar`, `indexAt_handles_degenerate_input`. Check that nothing else references `indexAt` (`grep -rn "indexAt(" ui/ app/` must only show `itemIndexAt`).

- [ ] **Step 5: Run the module tests**

Run: `./gradlew :ui:design:testDebugUnitTest`
Expected: PASS (the three new tests pass; the earlier navbar tests, including `tapping_an_item_reports_its_id`, still pass).

- [ ] **Step 6: Commit**

```bash
git add ui/design
git commit -m "fix(ui): navbar fills its width, ticks only on real selection changes, ignores selected taps

Closes audit M-3, M-4/N-1, N-2 and the selected-tap note; removes dead code."
```

---

### Task 3: Live reduce-motion observer (audit M-18)

**Files:**
- Modify: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteTheme.kt`
- Create: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/ReduceMotionObserverTest.kt`

**Interfaces:** `EmberbyteTheme(...)` signature unchanged. New internal `@Composable fun rememberReduceMotion(): Boolean` replaces the one-shot `remember(context)` read; it updates when the user toggles "remove animations" while the app is open.

- [ ] **Step 1: Write the failing test**

`ReduceMotionObserverTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReduceMotionObserverTest {
    @get:Rule val rule = createComposeRule()

    private fun setScale(context: Context, scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    @Test fun reflects_the_system_setting_and_updates_live() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setScale(context, 1f)
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                Text(if (LocalReduceMotion.current) "reduced" else "full", Modifier.testTag("probe"))
            }
        }
        rule.onNodeWithTag("probe").assertTextEquals("full")

        setScale(context, 0f)
        rule.waitForIdle()
        rule.onNodeWithTag("probe").assertTextEquals("reduced")

        setScale(context, 1f)
        rule.waitForIdle()
        rule.onNodeWithTag("probe").assertTextEquals("full")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*ReduceMotionObserverTest"`
Expected: FAIL (stays `full` after the setting changes).

- [ ] **Step 3: Implement**

In `EmberbyteTheme.kt`: remove the block
```kotlin
    val reduceMotion = remember(context) {
        isReduceMotion(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
```
and replace it with `val reduceMotion = rememberReduceMotion()`. Add this code to the same file (new imports as needed:
`android.content.Context`, `android.database.ContentObserver`, `android.os.Handler`, `android.os.Looper`,
`androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`,
`androidx.compose.runtime.setValue`; `remember` is already imported):
```kotlin
internal fun readReduceMotion(context: Context): Boolean = isReduceMotion(
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
)

/** True while the system animator scale is 0; updates live when the user changes the setting. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readReduceMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        reduce = readReduceMotion(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}
```
Keep the `@SuppressLint("NewApi")` and everything else in the file as it is.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :ui:design:testDebugUnitTest`
Expected: PASS (whole module). If Robolectric does not deliver the observer callback in the test, first try
`org.robolectric.Shadows.shadowOf(context.contentResolver).notifyChange(uri, null)` instead of
`contentResolver.notifyChange`; if it still cannot be delivered, keep the first assertion and the production code,
mark the live part of the test `@Ignore("Robolectric does not deliver Settings.Global observer callbacks")`, and say so
in your report (the tester will verify it on the emulator).

- [ ] **Step 5: Commit**

```bash
git add ui/design
git commit -m "fix(ui): follow the system 'remove animations' setting live

It was read once at start-up, so toggling it while the app was open had no effect (audit M-18)."
```

---

### Task 4: Counter reconciliation (`:core:engine`)

**Files:**
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/counter/Counter.kt`
- Test: `core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/counter/CounterReconcilerTest.kt`

**Interfaces:**
- Produces (package `io.github.khaledbahaaeldin.emberbyte.engine.counter`): `CounterReading`, `CounterDelta`, `CounterReconciler.delta(previous, current)` exactly as in contract section 9.1.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.counter

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterReconcilerTest {
    private val t0 = Instant.parse("2026-10-07T10:00:00Z")
    private fun reading(sec: Long, rx: Long, tx: Long, boot: String = "b1") =
        CounterReading(t0.plusSeconds(sec), rx, tx, boot)

    @Test fun no_previous_reading_means_zero_delta_and_no_reset() =
        assertEquals(CounterDelta(0, 0, false), CounterReconciler.delta(null, reading(0, 500, 100)))

    @Test fun normal_growth_is_the_difference() =
        assertEquals(CounterDelta(300, 50, false), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1300, 250)))

    @Test fun identical_readings_give_zero() =
        assertEquals(CounterDelta(0, 0, false), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1000, 200)))

    @Test fun a_decreasing_rx_counter_is_a_reset_and_the_delta_is_the_new_value() =
        assertEquals(CounterDelta(40, 260, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 40, 260)))

    @Test fun a_decreasing_tx_counter_alone_is_also_a_reset() =
        assertEquals(CounterDelta(1500, 10, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1500, 10)))

    @Test fun a_different_boot_id_is_a_reset_even_if_counters_grew() =
        assertEquals(CounterDelta(5000, 900, true), CounterReconciler.delta(reading(0, 100, 50, "b1"), reading(1, 5000, 900, "b2")))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:engine:test --tests "*CounterReconcilerTest"`
Expected: FAIL (unresolved reference `CounterReading`).

- [ ] **Step 3: Implement** (`Counter.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.counter

import java.time.Instant

data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)

data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)

object CounterReconciler {
    /**
     * Turns cumulative counter readings into a non-negative delta.
     * previous == null -> zero delta, not a reset. A different bootId, or EITHER counter decreasing, is a reset:
     * the delta is the current reading (both directions) and wasReset is true.
     */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta {
        if (previous == null) return CounterDelta(0L, 0L, wasReset = false)
        val reset = previous.bootId != current.bootId ||
            current.rxBytes < previous.rxBytes ||
            current.txBytes < previous.txBytes
        return if (reset) {
            CounterDelta(current.rxBytes.coerceAtLeast(0L), current.txBytes.coerceAtLeast(0L), wasReset = true)
        } else {
            CounterDelta(current.rxBytes - previous.rxBytes, current.txBytes - previous.txBytes, wasReset = false)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:engine:test` → PASS (all engine tests).

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): reconcile cumulative counters into safe deltas

Counter resets and reboots must never produce negative or inflated usage."
```

---

### Task 5: Usage rows and the series builder (`:core:engine`)

**Files:**
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/Rows.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/SeriesBuilder.kt`
- Test: `core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/SeriesBuilderTest.kt`

**Interfaces:**
- Consumes: `NetworkKind`, `Granularity`, `DateRange`, `UsageFilter`, `UsagePoint` from `engine.model` (M1).
- Produces: `MinuteTotal`, `HourlyUsage`, `AppMeta` and `SeriesBuilder` as in contract 9.1.
- Rule (contract 9.4): per (hour, network) the value is `max(minute sum, hourly sum)`; hours overlapping the range start are included; buckets are zero-filled; HOUR buckets are epoch-hour aligned, DAY/WEEK(Monday)/MONTH buckets use the zone.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBuilderTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun i(s: String) = Instant.parse(s)
    private fun minute(at: String, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(i(at), network, sub, rx, tx)
    private fun hourly(at: String, uid: Int, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        HourlyUsage(i(at), uid, network, sub, rx, tx)

    // ---- bucket maths ----
    @Test fun bucketStart_for_every_granularity_in_utc() {
        val at = i("2026-10-07T13:45:10Z") // a Wednesday
        assertEquals(i("2026-10-07T13:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.HOUR, utc))
        assertEquals(i("2026-10-07T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.DAY, utc))
        assertEquals(i("2026-10-05T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.WEEK, utc))
        assertEquals(i("2026-10-01T00:00:00Z"), SeriesBuilder.bucketStart(at, Granularity.MONTH, utc))
    }

    @Test fun nextBucketStart_for_every_granularity() {
        assertEquals(i("2026-10-07T14:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-07T13:00:00Z"), Granularity.HOUR, utc))
        assertEquals(i("2026-10-08T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-07T00:00:00Z"), Granularity.DAY, utc))
        assertEquals(i("2026-10-12T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-05T00:00:00Z"), Granularity.WEEK, utc))
        assertEquals(i("2026-11-01T00:00:00Z"), SeriesBuilder.nextBucketStart(i("2026-10-01T00:00:00Z"), Granularity.MONTH, utc))
    }

    @Test fun day_buckets_follow_the_time_zone() {
        val cairo = ZoneId.of("Africa/Cairo") // UTC+3 on 2026-10-07
        val start = SeriesBuilder.bucketStart(i("2026-10-07T22:30:00Z"), Granularity.DAY, cairo) // 01:30 local on the 8th
        assertEquals(i("2026-10-07T21:00:00Z"), start)
    }

    // ---- build ----
    @Test fun buckets_are_zero_filled_across_the_range() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-05T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc, emptyList(), emptyList(),
        )
        assertEquals(listOf(i("2026-10-05T00:00:00Z"), i("2026-10-06T00:00:00Z"), i("2026-10-07T00:00:00Z")), points.map { it.start })
        assertTrue(points.all { it.totalBytes == 0L })
    }

    @Test fun minute_rows_sum_into_their_day_split_by_network() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-06T10:00:00Z", NetworkKind.MOBILE, 100, 20),
                minute("2026-10-06T10:01:00Z", NetworkKind.MOBILE, 30),
                minute("2026-10-06T10:01:00Z", NetworkKind.WIFI, 500),
                minute("2026-10-07T23:59:00Z", NetworkKind.WIFI, 7),
            ),
            hourlyRows = emptyList(),
        )
        assertEquals(150L, points[0].mobileBytes)
        assertEquals(500L, points[0].wifiBytes)
        assertEquals(0L, points[1].mobileBytes)
        assertEquals(7L, points[1].wifiBytes)
    }

    @Test fun hourly_wins_when_it_is_larger_than_the_minute_sum() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100)),
            hourlyRows = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 200, 100)),
        )
        assertEquals(300L, points.single().mobileBytes)
    }

    @Test fun minute_sum_wins_when_it_is_larger_than_the_hourly_sum() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 900)),
            hourlyRows = listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 200)),
        )
        assertEquals(900L, points.single().mobileBytes)
    }

    @Test fun hourly_rows_of_several_uids_are_summed_before_comparing() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = emptyList(),
            hourlyRows = listOf(
                hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.WIFI, 200),
                hourly("2026-10-07T10:00:00Z", 10002, NetworkKind.WIFI, 50, 25),
            ),
        )
        assertEquals(275L, points.single().wifiBytes)
    }

    @Test fun network_filter_keeps_only_that_network() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100),
                minute("2026-10-07T10:00:00Z", NetworkKind.WIFI, 400),
            ),
            hourlyRows = emptyList(),
            filter = UsageFilter(network = NetworkKind.WIFI),
        )
        assertEquals(0L, points.single().mobileBytes)
        assertEquals(400L, points.single().wifiBytes)
    }

    @Test fun subscription_filter_keeps_only_that_subscription() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-08T00:00:00Z")), Granularity.DAY, utc,
            minuteRows = listOf(
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100, sub = 1),
                minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 900, sub = 2),
            ),
            hourlyRows = emptyList(),
            filter = UsageFilter(subscriptionId = 2),
        )
        assertEquals(900L, points.single().mobileBytes)
    }

    @Test fun rows_at_or_after_the_end_of_the_range_are_ignored() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-07T00:00:00Z"), i("2026-10-07T12:00:00Z")), Granularity.HOUR, utc,
            minuteRows = listOf(
                minute("2026-10-07T11:59:00Z", NetworkKind.MOBILE, 10),
                minute("2026-10-07T12:00:00Z", NetworkKind.MOBILE, 999),
            ),
            hourlyRows = emptyList(),
        )
        assertEquals(12, points.size)
        assertEquals(10L, points.last().mobileBytes)
    }

    @Test fun an_empty_range_gives_no_points() {
        val t = i("2026-10-07T00:00:00Z")
        assertTrue(SeriesBuilder.build(DateRange(t, t), Granularity.DAY, utc, emptyList(), emptyList()).isEmpty())
    }

    @Test fun an_hour_straddling_the_first_bucket_start_is_counted_in_the_first_bucket() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+5:30: local 7 Oct starts at 2026-10-06T18:30Z
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-06T18:30:00Z"), i("2026-10-07T18:30:00Z")), Granularity.DAY, kolkata,
            minuteRows = emptyList(),
            hourlyRows = listOf(hourly("2026-10-06T18:00:00Z", 10001, NetworkKind.MOBILE, 123)),
        )
        assertEquals(123L, points.single().mobileBytes)
    }

    @Test fun week_buckets_start_on_monday() {
        val points = SeriesBuilder.build(
            DateRange(i("2026-10-01T00:00:00Z"), i("2026-10-14T00:00:00Z")), Granularity.WEEK, utc,
            minuteRows = listOf(minute("2026-10-06T10:00:00Z", NetworkKind.WIFI, 5)),
            hourlyRows = emptyList(),
        )
        assertEquals(listOf(i("2026-09-28T00:00:00Z"), i("2026-10-05T00:00:00Z"), i("2026-10-12T00:00:00Z")), points.map { it.start })
        assertEquals(5L, points[1].wifiBytes)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:engine:test --tests "*SeriesBuilderTest"` → FAIL (unresolved `SeriesBuilder`).

- [ ] **Step 3: Implement**

`Rows.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant

data class MinuteTotal(
    val minuteStart: Instant,
    val network: NetworkKind,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class HourlyUsage(
    val hourStart: Instant,
    val uid: Int,
    val network: NetworkKind,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class AppMeta(val uid: Int, val packageName: String, val label: String)
```

`SeriesBuilder.kt`:
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

private data class HourKey(val hour: Instant, val network: NetworkKind)

object SeriesBuilder {

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

    fun build(
        range: DateRange,
        granularity: Granularity,
        zone: ZoneId,
        minuteRows: List<MinuteTotal>,
        hourlyRows: List<HourlyUsage>,
        filter: UsageFilter = UsageFilter(),
    ): List<UsagePoint> {
        val first = bucketStart(range.from, granularity, zone)
        val starts = ArrayList<Instant>()
        var cursor = first
        while (cursor < range.to) {
            starts += cursor
            cursor = nextBucketStart(cursor, granularity, zone)
        }
        if (starts.isEmpty()) return emptyList()

        val minuteByKey = HashMap<HourKey, Long>()
        for (row in minuteRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            val key = HourKey(row.minuteStart.truncatedTo(ChronoUnit.HOURS), row.network)
            minuteByKey.merge(key, row.totalBytes) { a, b -> a + b }
        }
        val hourlyByKey = HashMap<HourKey, Long>()
        for (row in hourlyRows) {
            if (!matches(row.network, row.subscriptionId, filter)) continue
            hourlyByKey.merge(HourKey(row.hourStart, row.network), row.totalBytes) { a, b -> a + b }
        }

        val mobile = LongArray(starts.size)
        val wifi = LongArray(starts.size)
        for (key in minuteByKey.keys + hourlyByKey.keys) {
            // Include hours that overlap the first bucket; exclude hours that start at or after the range end.
            if (key.hour.plus(1, ChronoUnit.HOURS) <= first || key.hour >= range.to) continue
            val value = maxOf(minuteByKey[key] ?: 0L, hourlyByKey[key] ?: 0L)
            val index = indexFor(starts, key.hour)
            if (key.network == NetworkKind.MOBILE) mobile[index] += value else wifi[index] += value
        }
        return starts.indices.map { UsagePoint(starts[it], mobile[it], wifi[it]) }
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

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:engine:test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): build zero-filled usage series from minute and hourly rows

Sampler minutes and system hourly history are merged per hour so totals work
with or without Usage Access."
```

---

### Task 6: App aggregation, SIM attribution and hourly reconciliation (`:core:engine`)

**Files:**
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/AppAggregator.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/Attribution.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/HourlyReconciler.kt`
- Test: `core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/usage/UsageAggregationTest.kt`

**Interfaces:** `AppAggregator.aggregate`, `dominantSubscriptionId`, `HourlyReconciler.scale` exactly as in contract 9.1.
Fallbacks: an app with no `AppMeta` gets `packageName = "uid:<uid>"` and `label = "UID <uid>"`; `screenTimeMs` is always `null` in M2; `SCREEN_TIME_DESC` therefore sorts like `BYTES_DESC`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregationTest {
    private val h = Instant.parse("2026-10-07T10:00:00Z")
    private fun hourly(uid: Int, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1, hour: Instant = h) =
        HourlyUsage(hour, uid, network, sub, rx, tx)
    private fun minute(offsetMin: Long, network: NetworkKind, rx: Long, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(h.plusSeconds(offsetMin * 60), network, sub, rx, tx)

    private val meta = mapOf(
        10001 to AppMeta(10001, "com.video", "Video"),
        10002 to AppMeta(10002, "com.browser", "browser"),
    )

    // ---- AppAggregator ----
    @Test fun groups_per_uid_and_splits_mobile_and_wifi() {
        val apps = AppAggregator.aggregate(
            listOf(
                hourly(10001, NetworkKind.MOBILE, 100, 50),
                hourly(10001, NetworkKind.WIFI, 400),
                hourly(10001, NetworkKind.MOBILE, 10, hour = h.plusSeconds(3600)),
            ),
            meta,
        )
        val app = apps.single()
        assertEquals("com.video", app.packageName)
        assertEquals("Video", app.label)
        assertEquals(160L, app.mobileBytes)
        assertEquals(400L, app.wifiBytes)
        assertNull(app.screenTimeMs)
    }

    @Test fun unknown_uid_gets_a_fallback_identity() {
        val app = AppAggregator.aggregate(listOf(hourly(10999, NetworkKind.WIFI, 5)), emptyMap()).single()
        assertEquals("uid:10999", app.packageName)
        assertEquals("UID 10999", app.label)
    }

    @Test fun apps_with_no_traffic_are_dropped() =
        assertTrue(AppAggregator.aggregate(listOf(hourly(10001, NetworkKind.MOBILE, 0, 0)), meta).isEmpty())

    @Test fun network_filter_drops_the_other_network_and_apps_that_only_used_it() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 100), hourly(10002, NetworkKind.WIFI, 900))
        val apps = AppAggregator.aggregate(rows, meta, UsageFilter(network = NetworkKind.MOBILE))
        assertEquals(listOf("com.video"), apps.map { it.packageName })
        assertEquals(0L, apps.single().wifiBytes)
    }

    @Test fun sorts_by_bytes_name_and_screen_time_fallback() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 100), hourly(10002, NetworkKind.MOBILE, 900))
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.BYTES_DESC).map { it.packageName })
        assertEquals(listOf("com.video", "com.browser"), AppAggregator.aggregate(rows, meta, sort = AppSort.BYTES_ASC).map { it.packageName })
        // names compare case-insensitively: "browser" < "Video"
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.NAME).map { it.packageName })
        assertEquals(listOf("com.browser", "com.video"), AppAggregator.aggregate(rows, meta, sort = AppSort.SCREEN_TIME_DESC).map { it.packageName })
    }

    // ---- dominantSubscriptionId ----
    @Test fun dominant_subscription_is_the_one_with_most_mobile_bytes() {
        val rows = listOf(
            minute(0, NetworkKind.MOBILE, 100, sub = 1),
            minute(1, NetworkKind.MOBILE, 50, sub = 2),
            minute(2, NetworkKind.MOBILE, 80, sub = 2),
            minute(3, NetworkKind.WIFI, 99_999, sub = -1),
        )
        assertEquals(2, dominantSubscriptionId(rows))
    }

    @Test fun dominant_subscription_is_minus_one_without_mobile_rows() {
        assertEquals(-1, dominantSubscriptionId(emptyList()))
        assertEquals(-1, dominantSubscriptionId(listOf(minute(0, NetworkKind.WIFI, 10))))
    }

    // ---- HourlyReconciler ----
    private fun fullHour(mobilePerMinute: Long, wifiPerMinute: Long = 0) = (0L until 60L).flatMap { m ->
        listOf(minute(m, NetworkKind.MOBILE, mobilePerMinute), minute(m, NetworkKind.WIFI, wifiPerMinute))
    }

    @Test fun incomplete_minute_coverage_leaves_rows_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 20_000))
        val minutes = (0L until 54L).map { minute(it, NetworkKind.MOBILE, 100) } // 54 < 55 covered minutes
        assertEquals(rows, HourlyReconciler.scale(h, rows, minutes))
    }

    @Test fun rows_within_tolerance_are_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 6_100)) // trusted 6000 (60 x 100): +1.7%
        assertEquals(rows, HourlyReconciler.scale(h, rows, fullHour(100)))
    }

    @Test fun rows_far_from_the_trusted_total_are_scaled_proportionally() {
        val rows = listOf(
            hourly(10001, NetworkKind.MOBILE, 9_000, 3_000), // 12_000
            hourly(10002, NetworkKind.MOBILE, 6_000, 0),     // 6_000  -> actual 18_000, trusted 6_000
        )
        val scaled = HourlyReconciler.scale(h, rows, fullHour(100))
        assertEquals(6_000L, scaled.sumOf { it.totalBytes })
        assertEquals(4_000L, scaled.first { it.uid == 10001 }.totalBytes)
        assertEquals(2_000L, scaled.first { it.uid == 10002 }.totalBytes)
    }

    @Test fun networks_are_scaled_independently() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 12_000), hourly(10001, NetworkKind.WIFI, 3_000))
        val scaled = HourlyReconciler.scale(h, rows, fullHour(mobilePerMinute = 100, wifiPerMinute = 50))
        assertEquals(6_000L, scaled.first { it.network == NetworkKind.MOBILE }.totalBytes)
        assertEquals(3_000L, scaled.first { it.network == NetworkKind.WIFI }.totalBytes) // 3000 == trusted 3000
    }

    @Test fun a_zero_trusted_total_leaves_rows_untouched() {
        val rows = listOf(hourly(10001, NetworkKind.MOBILE, 5_000))
        assertEquals(rows, HourlyReconciler.scale(h, rows, fullHour(mobilePerMinute = 0)))
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:engine:test --tests "*UsageAggregationTest"` → FAIL.

- [ ] **Step 3: Implement**

`AppAggregator.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter

private class Totals(var mobile: Long = 0L, var wifi: Long = 0L)

object AppAggregator {
    fun aggregate(
        hourlyRows: List<HourlyUsage>,
        meta: Map<Int, AppMeta>,
        filter: UsageFilter = UsageFilter(),
        sort: AppSort = AppSort.BYTES_DESC,
    ): List<AppUsage> {
        val byUid = LinkedHashMap<Int, Totals>()
        for (row in hourlyRows) {
            if (filter.network != null && row.network != filter.network) continue
            if (filter.subscriptionId != null && row.subscriptionId != filter.subscriptionId) continue
            val totals = byUid.getOrPut(row.uid) { Totals() }
            if (row.network == NetworkKind.MOBILE) totals.mobile += row.totalBytes else totals.wifi += row.totalBytes
        }
        val apps = byUid.entries
            .filter { it.value.mobile + it.value.wifi > 0L }
            .map { (uid, totals) ->
                val info = meta[uid]
                AppUsage(
                    packageName = info?.packageName ?: "uid:$uid",
                    label = info?.label ?: "UID $uid",
                    uid = uid,
                    mobileBytes = totals.mobile,
                    wifiBytes = totals.wifi,
                    screenTimeMs = null,
                )
            }
        return when (sort) {
            AppSort.BYTES_DESC, AppSort.SCREEN_TIME_DESC -> apps.sortedByDescending { it.totalBytes }
            AppSort.BYTES_ASC -> apps.sortedBy { it.totalBytes }
            AppSort.NAME -> apps.sortedBy { it.label.lowercase() }
        }
    }
}
```

`Attribution.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind

/** Subscription id with the most mobile bytes among [minuteRows] (rows of one hour); -1 when there is none. */
fun dominantSubscriptionId(minuteRows: List<MinuteTotal>): Int {
    val bytesBySubscription = HashMap<Int, Long>()
    for (row in minuteRows) {
        if (row.network != NetworkKind.MOBILE) continue
        bytesBySubscription.merge(row.subscriptionId, row.totalBytes) { a, b -> a + b }
    }
    return bytesBySubscription.maxByOrNull { it.value }?.key ?: -1
}
```

`HourlyReconciler.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.usage

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

object HourlyReconciler {
    const val TOLERANCE = 0.05
    const val FULL_COVERAGE_MINUTES = 55

    /**
     * [hourlyRows] are the per-app rows of ONE hour (any networks). When the sampler covered at least
     * [FULL_COVERAGE_MINUTES] distinct minutes of that hour, each network's rows are scaled so they add up to the
     * sampler's total whenever the two differ by more than [TOLERANCE].
     */
    fun scale(hourStart: Instant, hourlyRows: List<HourlyUsage>, minuteRows: List<MinuteTotal>): List<HourlyUsage> {
        val inHour = minuteRows.filter { it.minuteStart.truncatedTo(ChronoUnit.HOURS) == hourStart }
        val coveredMinutes = inHour.map { it.minuteStart }.distinct().size
        if (coveredMinutes < FULL_COVERAGE_MINUTES) return hourlyRows
        return hourlyRows.groupBy { it.network }.flatMap { (network, rows) ->
            val trusted = inHour.filter { it.network == network }.sumOf { it.totalBytes }
            val actual = rows.sumOf { it.totalBytes }
            if (actual <= 0L || trusted <= 0L) {
                rows
            } else {
                val difference = abs(actual - trusted).toDouble() / trusted
                if (difference <= TOLERANCE) {
                    rows
                } else {
                    val factor = trusted.toDouble() / actual
                    rows.map {
                        it.copy(
                            rxBytes = (it.rxBytes * factor).roundToLong(),
                            txBytes = (it.txBytes * factor).roundToLong(),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:engine:test` → PASS. Rounding note: in `rows_far_from_the_trusted_total_are_scaled_proportionally` the expected values are exact (factor 1/3 of 12 000 and 6 000); if rounding makes the sum differ by 1, keep the implementation and change only the `sumOf` assertion to `assertEquals(6_000L, scaled.sumOf { it.totalBytes }, )` with the exact numbers shown (they are exact for these inputs).

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): aggregate per-app usage, attribute SIMs and reconcile hourly data

Per-app history from the system can disagree with the sampler; scaling only fully
covered hours keeps app totals honest without punishing partial coverage."
```

---

### Task 7: Room database and `UsageStore` (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/db/Entities.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/db/UsageDao.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/db/EmberbyteDatabase.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/store/UsageStore.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/store/RoomUsageStore.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/InMemoryUsageStore.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/store/UsageStoreContractTest.kt` (abstract), `RoomUsageStoreTest.kt`, `InMemoryUsageStoreTest.kt`
- Generated and COMMITTED: `core/data/schemas/io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase/1.json`

**Interfaces:**
- Consumes: engine types from Tasks 3-6 and `CoverageGap`/`GapReason` (M1).
- Produces: `UsageStore`, `UsageRetention`, `RoomUsageStore`, `UsageStores.create(context)`, `InMemoryUsageStore` exactly as contract 9.2 and the tables of contract section 5 (`total_minute`, `usage_hourly`, `app_meta`, `coverage_gap`, `counter_checkpoint`). The other tables of section 5 arrive with their milestones (they need a `Migration(1, 2)` then).

- [ ] **Step 1: Write the shared contract test** (one test class run against BOTH implementations)

`UsageStoreContractTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class UsageStoreContractTest {
    abstract fun createStore(): UsageStore

    private val t = Instant.parse("2026-10-07T10:00:00Z")
    private val wide = t.minus(Duration.ofDays(2)) to t.plus(Duration.ofDays(2))
    private fun m(offsetMin: Long, network: NetworkKind = NetworkKind.MOBILE, rx: Long = 1, tx: Long = 0, sub: Int = -1) =
        MinuteTotal(t.plusSeconds(offsetMin * 60), network, sub, rx, tx)
    private fun h(offsetHours: Long, uid: Int, rx: Long, network: NetworkKind = NetworkKind.MOBILE, sub: Int = -1) =
        HourlyUsage(t.plusSeconds(offsetHours * 3600), uid, network, sub, rx, 0)

    @Test fun addMinute_adds_to_an_existing_row_with_the_same_key() = runBlocking {
        val store = createStore()
        store.addMinute(m(0, rx = 10, tx = 1))
        store.addMinute(m(0, rx = 5, tx = 2))
        val rows = store.minuteRows(wide.first, wide.second)
        assertEquals(listOf(m(0, rx = 15, tx = 3)), rows)
    }

    @Test fun rows_with_a_different_network_or_subscription_are_separate() = runBlocking {
        val store = createStore()
        store.addMinute(m(0, NetworkKind.MOBILE, sub = 1))
        store.addMinute(m(0, NetworkKind.MOBILE, sub = 2))
        store.addMinute(m(0, NetworkKind.WIFI))
        assertEquals(3, store.minuteRows(wide.first, wide.second).size)
    }

    @Test fun minuteRows_is_half_open_and_ordered() = runBlocking {
        val store = createStore()
        store.addMinute(m(2)); store.addMinute(m(0)); store.addMinute(m(1))
        val rows = store.minuteRows(t, t.plusSeconds(120))
        assertEquals(listOf(t, t.plusSeconds(60)), rows.map { it.minuteStart })
    }

    @Test fun upsertHourly_replaces_the_same_key_and_keeps_others() = runBlocking {
        val store = createStore()
        store.upsertHourly(listOf(h(0, 1, 100), h(0, 2, 200)))
        store.upsertHourly(listOf(h(0, 1, 111)))
        val rows = store.hourlyRows(wide.first, wide.second).sortedBy { it.uid }
        assertEquals(listOf(h(0, 1, 111), h(0, 2, 200)), rows)
    }

    @Test fun appMeta_is_replaced_by_uid() = runBlocking {
        val store = createStore()
        store.upsertAppMeta(listOf(AppMeta(1, "a", "A"), AppMeta(2, "b", "B")))
        store.upsertAppMeta(listOf(AppMeta(1, "a", "A2")))
        assertEquals(listOf(AppMeta(1, "a", "A2"), AppMeta(2, "b", "B")), store.appMeta().sortedBy { it.uid })
    }

    @Test fun gaps_returns_the_ones_overlapping_the_window() = runBlocking {
        val store = createStore()
        store.insertGap(CoverageGap(t, t.plusSeconds(600), GapReason.SERVICE_KILLED))
        store.insertGap(CoverageGap(t.minus(Duration.ofDays(30)), t.minus(Duration.ofDays(29)), GapReason.REBOOT))
        val found = store.gaps(t.plusSeconds(300), t.plusSeconds(900))
        assertEquals(listOf(GapReason.SERVICE_KILLED), found.map { it.reason })
    }

    @Test fun checkpoints_round_trip_and_overwrite() = runBlocking {
        val store = createStore()
        assertNull(store.loadCheckpoint("sampler.total"))
        store.saveCheckpoint("sampler.total", CounterReading(t, 10, 20, "b1"))
        store.saveCheckpoint("sampler.total", CounterReading(t.plusSeconds(5), 11, 21, "b2"))
        assertEquals(CounterReading(t.plusSeconds(5), 11, 21, "b2"), store.loadCheckpoint("sampler.total"))
        assertNull(store.loadCheckpoint("sampler.mobile"))
    }

    @Test fun observeMinuteRows_emits_again_after_a_write() = runBlocking {
        val store = createStore()
        withTimeout(10_000) {
            val seen = mutableListOf<List<MinuteTotal>>()
            val job = launch { store.observeMinuteRows(wide.first, wide.second).toList(seen) }
            while (seen.isEmpty()) delay(10)
            store.addMinute(m(0, rx = 42))
            while (seen.last().isEmpty()) delay(10)
            job.cancel()
            assertEquals(42L, seen.last().single().rxBytes)
        }
    }

    @Test fun observeLastSample_tracks_the_newest_minute() = runBlocking {
        val store = createStore()
        withTimeout(10_000) {
            val seen = mutableListOf<Instant?>()
            val job = launch { store.observeLastSample().toList(seen) }
            while (seen.isEmpty()) delay(10)
            assertNull(seen.last())
            store.addMinute(m(5))
            while (seen.last() == null) delay(10)
            store.addMinute(m(9))
            while (seen.last() != m(9).minuteStart) delay(10)
            job.cancel()
        }
    }

    @Test fun prune_deletes_only_rows_older_than_the_retention() = runBlocking {
        val store = createStore()
        val now = t
        val oldMinute = MinuteTotal(now.minus(UsageRetention.MINUTES).minusSeconds(60), NetworkKind.WIFI, -1, 1, 0)
        val keptMinute = MinuteTotal(now.minus(UsageRetention.MINUTES).plusSeconds(60), NetworkKind.WIFI, -1, 1, 0)
        store.addMinute(oldMinute); store.addMinute(keptMinute)
        val oldHour = HourlyUsage(now.minus(UsageRetention.HOURLY).minusSeconds(3600), 1, NetworkKind.WIFI, -1, 1, 0)
        val keptHour = HourlyUsage(now.minus(UsageRetention.HOURLY).plusSeconds(3600), 1, NetworkKind.WIFI, -1, 1, 0)
        store.upsertHourly(listOf(oldHour, keptHour))
        store.insertGap(CoverageGap(now.minus(UsageRetention.GAPS).minusSeconds(100), now.minus(UsageRetention.GAPS).minusSeconds(50), GapReason.REBOOT))
        store.insertGap(CoverageGap(now.minusSeconds(100), now.minusSeconds(50), GapReason.REBOOT))

        store.prune(now)

        val everything = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")
        assertEquals(listOf(keptMinute), store.minuteRows(everything.first, everything.second))
        assertEquals(listOf(keptHour), store.hourlyRows(everything.first, everything.second))
        assertEquals(1, store.gaps(everything.first, everything.second).size)
    }
}
```

`InMemoryUsageStoreTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore

class InMemoryUsageStoreTest : UsageStoreContractTest() {
    override fun createStore(): UsageStore = InMemoryUsageStore()
}
```

`RoomUsageStoreTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.store

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomUsageStoreTest : UsageStoreContractTest() {
    override fun createStore(): UsageStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EmberbyteDatabase::class.java).allowMainThreadQueries().build()
        return RoomUsageStore(db)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*UsageStore*"` → FAIL (unresolved `UsageStore` etc.).

- [ ] **Step 3: Implement the interface and retention**

`store/UsageStore.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.store

import android.content.Context
import androidx.room.Room
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface UsageStore {
    /** ADDS rx/tx to an existing row with the same key (atomic); inserts otherwise. */
    suspend fun addMinute(row: MinuteTotal)
    /** Replaces rows with the same (hour, uid, network, subscription) key. */
    suspend fun upsertHourly(rows: List<HourlyUsage>)
    suspend fun upsertAppMeta(meta: List<AppMeta>)
    suspend fun insertGap(gap: CoverageGap)
    suspend fun saveCheckpoint(source: String, reading: CounterReading)
    suspend fun loadCheckpoint(source: String): CounterReading?
    /** `from` inclusive, `to` exclusive, ordered by time. */
    suspend fun minuteRows(from: Instant, to: Instant): List<MinuteTotal>
    suspend fun hourlyRows(from: Instant, to: Instant): List<HourlyUsage>
    suspend fun appMeta(): List<AppMeta>
    /** Gaps overlapping [from, to]. */
    suspend fun gaps(from: Instant, to: Instant): List<CoverageGap>
    fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>>
    fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>>
    fun observeAppMeta(): Flow<List<AppMeta>>
    fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>>
    /** Newest `total_minute.minuteStart`, or null when there is none. */
    fun observeLastSample(): Flow<Instant?>
    /** Deletes rows strictly older than the retention of contract section 5. */
    suspend fun prune(now: Instant)
}

object UsageRetention {
    val MINUTES: Duration = Duration.ofDays(7)
    val HOURLY: Duration = Duration.ofDays(395) // about 13 months
    val GAPS: Duration = Duration.ofDays(395)
}

/** Open end used when a query only has a lower bound: the year 2100. */
val OPEN_END: Instant = Instant.parse("2100-01-01T00:00:00Z")

object UsageStores {
    /** Room-backed store in `emberbyte.db`. Room types never leave this module. */
    fun create(context: Context): UsageStore = RoomUsageStore(
        Room.databaseBuilder(context.applicationContext, EmberbyteDatabase::class.java, "emberbyte.db").build(),
    )
}
```

- [ ] **Step 4: Implement the Room layer**

`db/Entities.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "total_minute", primaryKeys = ["minuteStart", "network", "subscriptionId"])
data class TotalMinuteEntity(
    val minuteStart: Long,
    val network: Int,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "usage_hourly", primaryKeys = ["hourStart", "uid", "network", "subscriptionId"])
data class UsageHourlyEntity(
    val hourStart: Long,
    val uid: Int,
    val network: Int,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val uid: Int,
    val packageName: String,
    val label: String,
    val updatedAt: Long,
)

@Entity(tableName = "coverage_gap")
data class CoverageGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAt: Long,
    val toAt: Long,
    val reason: String,
)

@Entity(tableName = "counter_checkpoint")
data class CounterCheckpointEntity(
    @PrimaryKey val source: String,
    val at: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val bootId: String,
)
```

`db/UsageDao.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {
    // ---- total_minute ----
    @Query("SELECT * FROM total_minute WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart, network, subscriptionId")
    abstract suspend fun minuteRows(from: Long, to: Long): List<TotalMinuteEntity>

    @Query("SELECT * FROM total_minute WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart, network, subscriptionId")
    abstract fun observeMinuteRows(from: Long, to: Long): Flow<List<TotalMinuteEntity>>

    @Query("SELECT * FROM total_minute WHERE minuteStart = :minuteStart AND network = :network AND subscriptionId = :subscriptionId")
    abstract suspend fun getMinute(minuteStart: Long, network: Int, subscriptionId: Int): TotalMinuteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putMinute(entity: TotalMinuteEntity)

    @Transaction
    open suspend fun addMinute(entity: TotalMinuteEntity) {
        val existing = getMinute(entity.minuteStart, entity.network, entity.subscriptionId)
        putMinute(
            if (existing == null) entity
            else existing.copy(rxBytes = existing.rxBytes + entity.rxBytes, txBytes = existing.txBytes + entity.txBytes),
        )
    }

    @Query("SELECT MAX(minuteStart) FROM total_minute")
    abstract fun observeLastSample(): Flow<Long?>

    @Query("DELETE FROM total_minute WHERE minuteStart < :before")
    abstract suspend fun pruneMinutes(before: Long)

    // ---- usage_hourly ----
    @Query("SELECT * FROM usage_hourly WHERE hourStart >= :from AND hourStart < :to ORDER BY hourStart, uid")
    abstract suspend fun hourlyRows(from: Long, to: Long): List<UsageHourlyEntity>

    @Query("SELECT * FROM usage_hourly WHERE hourStart >= :from AND hourStart < :to ORDER BY hourStart, uid")
    abstract fun observeHourlyRows(from: Long, to: Long): Flow<List<UsageHourlyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHourly(rows: List<UsageHourlyEntity>)

    @Query("DELETE FROM usage_hourly WHERE hourStart < :before")
    abstract suspend fun pruneHourly(before: Long)

    // ---- app_meta ----
    @Query("SELECT * FROM app_meta")
    abstract suspend fun appMeta(): List<AppMetaEntity>

    @Query("SELECT * FROM app_meta")
    abstract fun observeAppMeta(): Flow<List<AppMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAppMeta(rows: List<AppMetaEntity>)

    // ---- coverage_gap ----
    @Insert
    abstract suspend fun insertGap(entity: CoverageGapEntity)

    @Query("SELECT * FROM coverage_gap WHERE toAt >= :from AND fromAt <= :to ORDER BY fromAt")
    abstract suspend fun gaps(from: Long, to: Long): List<CoverageGapEntity>

    @Query("SELECT * FROM coverage_gap WHERE toAt >= :from AND fromAt <= :to ORDER BY fromAt")
    abstract fun observeGaps(from: Long, to: Long): Flow<List<CoverageGapEntity>>

    @Query("DELETE FROM coverage_gap WHERE toAt < :before")
    abstract suspend fun pruneGaps(before: Long)

    // ---- counter_checkpoint ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCheckpoint(entity: CounterCheckpointEntity)

    @Query("SELECT * FROM counter_checkpoint WHERE source = :source")
    abstract suspend fun checkpoint(source: String): CounterCheckpointEntity?
}
```

`db/EmberbyteDatabase.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TotalMinuteEntity::class,
        UsageHourlyEntity::class,
        AppMetaEntity::class,
        CoverageGapEntity::class,
        CounterCheckpointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EmberbyteDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
```

`store/RoomUsageStore.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.data.db.AppMetaEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.CounterCheckpointEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.CoverageGapEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase
import io.github.khaledbahaaeldin.emberbyte.data.db.TotalMinuteEntity
import io.github.khaledbahaaeldin.emberbyte.data.db.UsageHourlyEntity
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun NetworkKind.code(): Int = if (this == NetworkKind.MOBILE) 0 else 1
private fun networkOf(code: Int): NetworkKind = if (code == 0) NetworkKind.MOBILE else NetworkKind.WIFI
private fun Instant.ms(): Long = toEpochMilli()
private fun at(ms: Long): Instant = Instant.ofEpochMilli(ms)

private fun MinuteTotal.toEntity() = TotalMinuteEntity(minuteStart.ms(), network.code(), subscriptionId, rxBytes, txBytes)
private fun TotalMinuteEntity.toDomain() = MinuteTotal(at(minuteStart), networkOf(network), subscriptionId, rxBytes, txBytes)
private fun HourlyUsage.toEntity() = UsageHourlyEntity(hourStart.ms(), uid, network.code(), subscriptionId, rxBytes, txBytes)
private fun UsageHourlyEntity.toDomain() = HourlyUsage(at(hourStart), uid, networkOf(network), subscriptionId, rxBytes, txBytes)
private fun AppMetaEntity.toDomain() = AppMeta(uid, packageName, label)
private fun CoverageGapEntity.toDomain() = CoverageGap(at(fromAt), at(toAt), GapReason.valueOf(reason))

class RoomUsageStore(db: EmberbyteDatabase) : UsageStore {
    private val dao = db.usageDao()

    override suspend fun addMinute(row: MinuteTotal) = dao.addMinute(row.toEntity())

    override suspend fun upsertHourly(rows: List<HourlyUsage>) = dao.upsertHourly(rows.map { it.toEntity() })

    override suspend fun upsertAppMeta(meta: List<AppMeta>) =
        dao.upsertAppMeta(meta.map { AppMetaEntity(it.uid, it.packageName, it.label, System.currentTimeMillis()) })

    override suspend fun insertGap(gap: CoverageGap) =
        dao.insertGap(CoverageGapEntity(fromAt = gap.from.ms(), toAt = gap.to.ms(), reason = gap.reason.name))

    override suspend fun saveCheckpoint(source: String, reading: CounterReading) =
        dao.putCheckpoint(CounterCheckpointEntity(source, reading.at.ms(), reading.rxBytes, reading.txBytes, reading.bootId))

    override suspend fun loadCheckpoint(source: String): CounterReading? =
        dao.checkpoint(source)?.let { CounterReading(at(it.at), it.rxBytes, it.txBytes, it.bootId) }

    override suspend fun minuteRows(from: Instant, to: Instant) = dao.minuteRows(from.ms(), to.ms()).map { it.toDomain() }

    override suspend fun hourlyRows(from: Instant, to: Instant) = dao.hourlyRows(from.ms(), to.ms()).map { it.toDomain() }

    override suspend fun appMeta() = dao.appMeta().map { it.toDomain() }

    override suspend fun gaps(from: Instant, to: Instant) = dao.gaps(from.ms(), to.ms()).map { it.toDomain() }

    override fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>> =
        dao.observeMinuteRows(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>> =
        dao.observeHourlyRows(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeAppMeta(): Flow<List<AppMeta>> = dao.observeAppMeta().map { list -> list.map { it.toDomain() } }

    override fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>> =
        dao.observeGaps(from.ms(), to.ms()).map { list -> list.map { it.toDomain() } }

    override fun observeLastSample(): Flow<Instant?> = dao.observeLastSample().map { it?.let(::at) }

    override suspend fun prune(now: Instant) {
        dao.pruneMinutes(now.minus(UsageRetention.MINUTES).ms())
        dao.pruneHourly(now.minus(UsageRetention.HOURLY).ms())
        dao.pruneGaps(now.minus(UsageRetention.GAPS).ms())
    }
}
```

`fake/InMemoryUsageStore.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private data class MinuteKey(val minuteStart: Instant, val network: NetworkKind, val subscriptionId: Int)
private data class HourKey(val hourStart: Instant, val uid: Int, val network: NetworkKind, val subscriptionId: Int)

/** Pure in-memory `UsageStore` with the same semantics as the Room one; used by tests and previews. */
class InMemoryUsageStore : UsageStore {
    private val minutes = MutableStateFlow<Map<MinuteKey, MinuteTotal>>(emptyMap())
    private val hours = MutableStateFlow<Map<HourKey, HourlyUsage>>(emptyMap())
    private val meta = MutableStateFlow<Map<Int, AppMeta>>(emptyMap())
    private val gapList = MutableStateFlow<List<CoverageGap>>(emptyList())
    private val checkpoints = MutableStateFlow<Map<String, CounterReading>>(emptyMap())

    override suspend fun addMinute(row: MinuteTotal) = minutes.update { map ->
        val key = MinuteKey(row.minuteStart, row.network, row.subscriptionId)
        val existing = map[key]
        map + (key to if (existing == null) row else existing.copy(
            rxBytes = existing.rxBytes + row.rxBytes,
            txBytes = existing.txBytes + row.txBytes,
        ))
    }

    override suspend fun upsertHourly(rows: List<HourlyUsage>) = hours.update { map ->
        map + rows.associateBy { HourKey(it.hourStart, it.uid, it.network, it.subscriptionId) }
    }

    override suspend fun upsertAppMeta(meta: List<AppMeta>) = this.meta.update { map -> map + meta.associateBy { it.uid } }

    override suspend fun insertGap(gap: CoverageGap) = gapList.update { it + gap }

    override suspend fun saveCheckpoint(source: String, reading: CounterReading) =
        checkpoints.update { it + (source to reading) }

    override suspend fun loadCheckpoint(source: String): CounterReading? = checkpoints.value[source]

    private fun minuteList(from: Instant, to: Instant) = minutes.value.values
        .filter { it.minuteStart >= from && it.minuteStart < to }
        .sortedWith(compareBy({ it.minuteStart }, { it.network }, { it.subscriptionId }))

    private fun hourList(map: Map<HourKey, HourlyUsage>, from: Instant, to: Instant) = map.values
        .filter { it.hourStart >= from && it.hourStart < to }
        .sortedWith(compareBy({ it.hourStart }, { it.uid }))

    private fun gapsIn(list: List<CoverageGap>, from: Instant, to: Instant) =
        list.filter { it.to >= from && it.from <= to }.sortedBy { it.from }

    override suspend fun minuteRows(from: Instant, to: Instant) = minuteList(from, to)

    override suspend fun hourlyRows(from: Instant, to: Instant) = hourList(hours.value, from, to)

    override suspend fun appMeta(): List<AppMeta> = meta.value.values.toList()

    override suspend fun gaps(from: Instant, to: Instant) = gapsIn(gapList.value, from, to)

    override fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>> =
        minutes.map { map -> map.values.filter { it.minuteStart >= from && it.minuteStart < to }
            .sortedWith(compareBy({ it.minuteStart }, { it.network }, { it.subscriptionId })) }.distinctUntilChanged()

    override fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>> =
        hours.map { hourList(it, from, to) }.distinctUntilChanged()

    override fun observeAppMeta(): Flow<List<AppMeta>> = meta.map { it.values.toList() }.distinctUntilChanged()

    override fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>> =
        gapList.map { gapsIn(it, from, to) }.distinctUntilChanged()

    override fun observeLastSample(): Flow<Instant?> =
        minutes.map { map -> map.values.maxOfOrNull { it.minuteStart } }.distinctUntilChanged()

    override suspend fun prune(now: Instant) {
        val minuteCut = now.minus(UsageRetention.MINUTES)
        val hourCut = now.minus(UsageRetention.HOURLY)
        val gapCut = now.minus(UsageRetention.GAPS)
        minutes.update { map -> map.filterValues { it.minuteStart >= minuteCut } }
        hours.update { map -> map.filterValues { it.hourStart >= hourCut } }
        gapList.update { list -> list.filter { it.to >= gapCut } }
    }
}
```

- [ ] **Step 5: Run to verify both implementations pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*UsageStore*"`
Expected: PASS for `InMemoryUsageStoreTest` and `RoomUsageStoreTest` (10 tests each). Then confirm the schema file exists: `ls core/data/schemas/` must contain `io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase/1.json`. Then run the whole gate `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** (include the generated schema)

```bash
git add core/data
git commit -m "feat(data): store minute totals, per-app hours, gaps and checkpoints in Room

One contract test runs against Room and the in-memory twin so the fake cannot drift.
The exported schema is committed so migrations can be tested from M3."
```

---

### Task 8: Data sources — interfaces, Android implementations, fakes (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/source/Sources.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/android/{TrafficStatsCounterSource,ConnectivityNetworkKindSource,TelephonySubscriptionSource,NetworkStatsManagerSource,PackageManagerAppInfoSource,AppOpsUsageAccess}.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/{FakeCounterSource,FakeNetworkKindSource,FakeSubscriptionSource,FakeNetworkStatsSource,FakeAppInfoSource,FakeUsageAccess}.kt` (one file `FakeSources.kt` containing all six is fine)
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/android/AndroidSourcesTest.kt`, `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeSourcesTest.kt`

**Interfaces:** contract 9.2 `…data.source`. The Android implementations are deliberately thin; their real behaviour is verified on the emulator in Task 21. They must not contain logic worth unit-testing except `specialUidMeta`.

- [ ] **Step 1: Write the failing tests**

`AndroidSourcesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSourcesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun special_uids_have_friendly_names() {
        assertEquals(AppMeta(NetworkStats.Bucket.UID_REMOVED, "uid:removed", "Removed apps"), specialUidMeta(NetworkStats.Bucket.UID_REMOVED))
        assertEquals(AppMeta(NetworkStats.Bucket.UID_TETHERING, "uid:tethering", "Hotspot & tethering"), specialUidMeta(NetworkStats.Bucket.UID_TETHERING))
        assertEquals(AppMeta(1000, "android", "Android system"), specialUidMeta(1000))
        assertNull(specialUidMeta(10_123))
    }

    @Test fun network_kind_source_constructs_and_reports_a_known_value() {
        // Robolectric's default connectivity may or may not expose capabilities; either is fine, a crash is not.
        val value = ConnectivityNetworkKindSource(context).current.value
        assertTrue(value == null || value == NetworkKind.WIFI)
    }

    @Test fun subscription_source_never_throws_and_returns_minus_one_or_an_id() {
        assertTrue(TelephonySubscriptionSource().defaultDataSubscriptionId() >= -1)
    }
}
```

`FakeSourcesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeSourcesTest {
    private val t = Instant.parse("2026-10-07T10:00:00Z")
    private fun snap(sec: Long) = CounterSnapshot(t.plusSeconds(sec), sec, 0, sec, 0, "b1")

    @Test fun counter_source_replays_the_script_then_repeats_the_last_snapshot() {
        val source = FakeCounterSource(listOf(snap(0), snap(1)))
        assertEquals(snap(0), source.read())
        assertEquals(snap(1), source.read())
        assertEquals(snap(1), source.read())
    }

    @Test fun network_stats_source_returns_the_scripted_rows_and_records_queries() = runBlocking {
        val rows = listOf(UidUsage(10001, 5, 6))
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to t) to rows))
        assertEquals(rows, source.query(NetworkKind.MOBILE, t, t.plusSeconds(3600)))
        assertEquals(emptyList<UidUsage>(), source.query(NetworkKind.WIFI, t, t.plusSeconds(3600)))
        assertEquals(2, source.queries.size)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*AndroidSourcesTest" --tests "*FakeSourcesTest"` → FAIL (unresolved references).

- [ ] **Step 3: Write the interfaces** (`source/Sources.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.source

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class CounterSnapshot(
    val at: Instant,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val bootId: String,
)

/** Cumulative system traffic counters (mobile and all interfaces). Wi-Fi = total - mobile. */
interface CounterSource { fun read(): CounterSnapshot }

/** Kind of the current default network; null = offline or something that is neither Wi-Fi nor cellular. */
interface NetworkKindSource { val current: StateFlow<NetworkKind?> }

interface SubscriptionSource {
    /** Default data subscription id, or -1 when unknown. */
    fun defaultDataSubscriptionId(): Int
}

data class UidUsage(val uid: Int, val rxBytes: Long, val txBytes: Long)

interface NetworkStatsSource {
    /** Bytes per UID for [network] in [from, to). Empty when Usage Access is missing. */
    suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage>
}

interface AppInfoSource { fun resolve(uid: Int): AppMeta? }

interface UsageAccess { fun isGranted(): Boolean }
```

- [ ] **Step 4: Write the Android implementations** (one file each, package `io.github.khaledbahaaeldin.emberbyte.data.android`)

`TrafficStatsCounterSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.content.Context
import android.net.TrafficStats
import android.provider.Settings
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import java.time.Clock

class TrafficStatsCounterSource(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : CounterSource {
    override fun read(): CounterSnapshot {
        val boots = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        return CounterSnapshot(
            at = clock.instant(),
            mobileRxBytes = TrafficStats.getMobileRxBytes().orZero(),
            mobileTxBytes = TrafficStats.getMobileTxBytes().orZero(),
            totalRxBytes = TrafficStats.getTotalRxBytes().orZero(),
            totalTxBytes = TrafficStats.getTotalTxBytes().orZero(),
            bootId = boots.toString(),
        )
    }

    /** `TrafficStats.UNSUPPORTED` is -1. */
    private fun Long.orZero(): Long = if (this < 0L) 0L else this
}
```

`ConnectivityNetworkKindSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectivityNetworkKindSource(context: Context) : NetworkKindSource {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(kindOf(manager.getNetworkCapabilities(manager.activeNetwork)))
    override val current: StateFlow<NetworkKind?> = state

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state.value = kindOf(capabilities)
            }

            override fun onLost(network: Network) {
                state.value = null
            }
        })
    }

    private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind? = when {
        capabilities == null -> null
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
        else -> null
    }
}
```

`TelephonySubscriptionSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.telephony.SubscriptionManager
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource

class TelephonySubscriptionSource : SubscriptionSource {
    override fun defaultDataSubscriptionId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId().let { if (it < 0) -1 else it }
    } catch (_: RuntimeException) {
        -1
    }
}
```

`NetworkStatsManagerSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkStatsManagerSource(context: Context) : NetworkStatsSource {
    private val manager = context.getSystemService(NetworkStatsManager::class.java)

    override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> =
        withContext(Dispatchers.IO) {
            val type = if (network == NetworkKind.MOBILE) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
            val totals = HashMap<Int, LongArray>()
            try {
                // subscriberId = null: on Android 10+ an unprivileged app queries all subscriptions together.
                val stats = manager.querySummary(type, null, from.toEpochMilli(), to.toEpochMilli())
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val pair = totals.getOrPut(bucket.uid) { LongArray(2) }
                        pair[0] += bucket.rxBytes
                        pair[1] += bucket.txBytes
                    }
                } finally {
                    stats.close()
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }
            totals.map { (uid, pair) -> UidUsage(uid, pair[0], pair[1]) }
        }
}
```

`PackageManagerAppInfoSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.content.Context
import android.content.pm.PackageManager
import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta

internal fun specialUidMeta(uid: Int): AppMeta? = when (uid) {
    NetworkStats.Bucket.UID_REMOVED -> AppMeta(uid, "uid:removed", "Removed apps")
    NetworkStats.Bucket.UID_TETHERING -> AppMeta(uid, "uid:tethering", "Hotspot & tethering")
    0 -> AppMeta(uid, "uid:root", "Root")
    1000 -> AppMeta(uid, "android", "Android system")
    else -> null
}

class PackageManagerAppInfoSource(context: Context) : AppInfoSource {
    private val packageManager = context.packageManager

    override fun resolve(uid: Int): AppMeta? {
        specialUidMeta(uid)?.let { return it }
        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return null
        return try {
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            AppMeta(uid, packageName, label)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
```

`AppOpsUsageAccess.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess

class AppOpsUsageAccess(private val context: Context) : UsageAccess {
    override fun isGranted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
```

- [ ] **Step 5: Write the fakes** (`data/fake/FakeSources.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Replays [script] one snapshot per `read()`, then keeps returning the last one. */
class FakeCounterSource(private val script: List<CounterSnapshot>) : CounterSource {
    private var index = 0
    override fun read(): CounterSnapshot = script[minOf(index++, script.lastIndex)]
}

class FakeNetworkKindSource(initial: NetworkKind? = NetworkKind.MOBILE) : NetworkKindSource {
    private val state = MutableStateFlow(initial)
    override val current: StateFlow<NetworkKind?> = state
    fun set(kind: NetworkKind?) { state.value = kind }
}

class FakeSubscriptionSource(var id: Int = -1) : SubscriptionSource {
    override fun defaultDataSubscriptionId(): Int = id
}

/** Rows are looked up by (network, hour start == `from`). Every call is recorded in [queries]. */
class FakeNetworkStatsSource(
    private val data: Map<Pair<NetworkKind, Instant>, List<UidUsage>> = emptyMap(),
) : NetworkStatsSource {
    val queries = mutableListOf<Triple<NetworkKind, Instant, Instant>>()
    override suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> {
        queries += Triple(network, from, to)
        return data[network to from] ?: emptyList()
    }
}

class FakeAppInfoSource(private val apps: Map<Int, AppMeta> = emptyMap()) : AppInfoSource {
    val resolved = mutableListOf<Int>()
    override fun resolve(uid: Int): AppMeta? {
        resolved += uid
        return apps[uid]
    }
}

class FakeUsageAccess(var granted: Boolean = true) : UsageAccess {
    override fun isGranted(): Boolean = granted
}
```

- [ ] **Step 6: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest` → PASS. If Robolectric cannot construct `ConnectivityNetworkKindSource` (for example `registerDefaultNetworkCallback` unsupported), keep the production code and replace only that one test with one that asserts the class compiles via a `@Ignore("verified on device")` and say so in your report.

- [ ] **Step 7: Commit**

```bash
git add core/data
git commit -m "feat(data): wrap system counters, connectivity, SIM, usage stats and app info as sources

Thin interfaces keep the sampler and catch-up logic testable on the JVM with fakes."
```

---

### Task 9: `SamplerEngine` (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/sampler/SamplerEngine.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/sampler/SamplerEngineTest.kt`

**Interfaces:**
- Consumes: sources and fakes (Task 8), `UsageStore`/`InMemoryUsageStore` (Task 7), `CounterReconciler` (Task 4).
- Produces: `SamplerEngine(counters, network, subscription, store, tickMillis = 1_000L)` with `liveSpeed: SharedFlow<LiveSpeed>`, `start()`, `tick()`, `stop()`, `run()` (contract 9.2).
- Behaviour: see contract 9.4. `start()` reads one snapshot as the baseline and records a gap if the stored checkpoint `sampler.total` is more than 90 000 ms old (`REBOOT` if the boot id differs, else `SERVICE_KILLED`); the downtime delta is discarded. `tick()` computes mobile and total deltas, derives Wi-Fi = total - mobile (clamped >= 0), records `COUNTER_RESET` gaps, emits an EMA-smoothed (alpha 0.5) `LiveSpeed`, and accumulates bytes per (minute, network, subscription) for BOTH networks (zero rows included). Closed minutes are flushed when the minute changes; `stop()` flushes everything and saves the `sampler.mobile` / `sampler.total` checkpoints. An elapsed time of 0 ms or less skips the tick.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeCounterSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SamplerEngineTest {
    private val t0 = Instant.parse("2026-10-07T10:00:00Z")
    private val minute10 = t0
    private val minute11 = t0.plusSeconds(60)
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun snap(sec: Long, mobRx: Long = 0, mobTx: Long = 0, totRx: Long = mobRx, totTx: Long = mobTx, boot: String = "b1") =
        CounterSnapshot(t0.plusSeconds(sec), mobRx, mobTx, totRx, totTx, boot)

    private fun engine(
        script: List<CounterSnapshot>,
        store: InMemoryUsageStore = InMemoryUsageStore(),
        kind: NetworkKind? = NetworkKind.MOBILE,
        sub: Int = 7,
    ) = SamplerEngine(FakeCounterSource(script), FakeNetworkKindSource(kind), FakeSubscriptionSource(sub), store)

    private suspend fun InMemoryUsageStore.rows() = minuteRows(open.first, open.second)

    @Test fun first_tick_reports_the_instantaneous_speed() = runTest {
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000)))
        e.start(); e.tick()
        val live = e.liveSpeed.replayCache.single()
        assertEquals(1000L, live.rxBps)
        assertEquals(0L, live.txBps)
    }

    @Test fun later_ticks_are_smoothed_with_an_ema_of_half() = runTest {
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000), snap(2, mobRx = 4000)))
        e.start(); e.tick(); e.tick()
        assertEquals(2000L, e.liveSpeed.replayCache.last().rxBps) // 0.5 * 3000 + 0.5 * 1000
    }

    @Test fun wifi_is_total_minus_mobile_and_rows_carry_their_subscription() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 200, totRx = 1000)), store)
        e.start(); e.tick(); e.stop()
        val rows = store.rows()
        val mobile = rows.single { it.network == NetworkKind.MOBILE }
        val wifi = rows.single { it.network == NetworkKind.WIFI }
        assertEquals(200L, mobile.rxBytes); assertEquals(7, mobile.subscriptionId)
        assertEquals(800L, wifi.rxBytes); assertEquals(-1, wifi.subscriptionId)
    }

    @Test fun a_closed_minute_is_flushed_when_the_minute_changes_and_the_rest_on_stop() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(30, mobRx = 100), snap(59, mobRx = 200), snap(61, mobRx = 300)), store)
        e.start(); e.tick(); e.tick(); e.tick()
        assertEquals(200L, store.minuteRows(minute10, minute11).single { it.network == NetworkKind.MOBILE }.rxBytes)
        assertTrue(store.minuteRows(minute11, minute11.plusSeconds(60)).isEmpty())
        e.stop()
        assertEquals(100L, store.minuteRows(minute11, minute11.plusSeconds(60)).single { it.network == NetworkKind.MOBILE }.rxBytes)
    }

    @Test fun both_networks_get_a_row_every_minute_even_with_no_traffic() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1)), store)
        e.start(); e.tick(); e.stop()
        val rows = store.rows()
        assertEquals(setOf(NetworkKind.MOBILE, NetworkKind.WIFI), rows.map { it.network }.toSet())
        assertTrue(rows.all { it.totalBytes == 0L })
    }

    @Test fun the_live_speed_carries_the_current_network_kind() = runTest {
        val wifi = engine(listOf(snap(0), snap(1, totRx = 10)), kind = NetworkKind.WIFI)
        wifi.start(); wifi.tick()
        assertEquals(NetworkKind.WIFI, wifi.liveSpeed.replayCache.last().network)
        val offline = engine(listOf(snap(0), snap(1)), kind = null)
        offline.start(); offline.tick()
        assertNull(offline.liveSpeed.replayCache.last().network)
    }

    @Test fun a_tick_with_no_elapsed_time_is_skipped() = runTest {
        val e = engine(listOf(snap(0), snap(0, mobRx = 500)))
        e.start(); e.tick()
        assertTrue(e.liveSpeed.replayCache.isEmpty())
    }

    @Test fun restart_after_a_long_pause_records_a_service_killed_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b1"))
        engine(listOf(snap(0)), store).start()
        val gap = store.gaps(open.first, open.second).single()
        assertEquals(GapReason.SERVICE_KILLED, gap.reason)
        assertEquals(t0.minusSeconds(300), gap.from); assertEquals(t0, gap.to)
    }

    @Test fun restart_with_a_different_boot_id_records_a_reboot_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b0"))
        engine(listOf(snap(0)), store).start()
        assertEquals(GapReason.REBOOT, store.gaps(open.first, open.second).single().reason)
    }

    @Test fun a_quick_restart_records_no_gap() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(60), 0, 0, "b1"))
        engine(listOf(snap(0)), store).start()
        assertTrue(store.gaps(open.first, open.second).isEmpty())
    }

    @Test fun bytes_used_while_the_service_was_down_are_not_added_to_minute_totals() = runTest {
        val store = InMemoryUsageStore()
        store.saveCheckpoint("sampler.total", CounterReading(t0.minusSeconds(300), 0, 0, "b1"))
        val e = engine(listOf(snap(0, mobRx = 5000), snap(1, mobRx = 5100)), store)
        e.start(); e.tick(); e.stop()
        assertEquals(100L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes)
    }

    @Test fun a_counter_decrease_records_a_reset_gap_and_counts_the_new_value() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 1000), snap(2, mobRx = 50)), store)
        e.start(); e.tick(); e.tick(); e.stop()
        assertEquals(1050L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes)
        val gap = store.gaps(open.first, open.second).single()
        assertEquals(GapReason.COUNTER_RESET, gap.reason)
        assertEquals(t0.plusSeconds(1), gap.from); assertEquals(t0.plusSeconds(2), gap.to)
    }

    @Test fun stop_saves_both_checkpoints() = runTest {
        val store = InMemoryUsageStore()
        val e = engine(listOf(snap(0), snap(1, mobRx = 10, totRx = 30)), store)
        e.start(); e.tick(); e.stop()
        assertEquals(30L, store.loadCheckpoint("sampler.total")?.rxBytes)
        assertEquals(10L, store.loadCheckpoint("sampler.mobile")?.rxBytes)
        assertNotNull(store.loadCheckpoint("sampler.total")?.bootId)
    }

    @Test fun run_ticks_every_second_and_flushes_when_cancelled() = runTest {
        val store = InMemoryUsageStore()
        val script = (0L..10L).map { snap(it, mobRx = it * 10) }
        val e = SamplerEngine(FakeCounterSource(script), FakeNetworkKindSource(), FakeSubscriptionSource(7), store, tickMillis = 1_000)
        val job = launch { e.run() }
        advanceTimeBy(3_500); runCurrent()
        job.cancelAndJoin()
        assertEquals(40L, store.rows().single { it.network == NetworkKind.MOBILE }.rxBytes) // 4 ticks x 10
        assertNotNull(store.loadCheckpoint("sampler.total"))
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*SamplerEngineTest"` → FAIL (unresolved `SamplerEngine`).

- [ ] **Step 3: Implement** (`sampler/SamplerEngine.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReconciler
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

const val NO_SUBSCRIPTION = -1
const val CHECKPOINT_SAMPLER_MOBILE = "sampler.mobile"
const val CHECKPOINT_SAMPLER_TOTAL = "sampler.total"
private const val GAP_THRESHOLD_MS = 90_000L
private const val EMA_ALPHA = 0.5

private data class MinuteKey(val minuteStart: Instant, val network: NetworkKind, val subscriptionId: Int)
private class Pending(var rx: Long = 0L, var tx: Long = 0L)

/**
 * Samples the system counters: smoothed live speed once per tick, and per-minute totals written to [store].
 * Not thread-safe: drive it from a single coroutine ([run]).
 */
class SamplerEngine(
    private val counters: CounterSource,
    private val network: NetworkKindSource,
    private val subscription: SubscriptionSource,
    private val store: UsageStore,
    private val tickMillis: Long = 1_000L,
) {
    private val _liveSpeed = MutableSharedFlow<LiveSpeed>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val liveSpeed: SharedFlow<LiveSpeed> = _liveSpeed.asSharedFlow()

    private var previousMobile: CounterReading? = null
    private var previousTotal: CounterReading? = null
    private var emaRx = 0.0
    private var emaTx = 0.0
    private var hasEma = false
    private var started = false
    private val pending = LinkedHashMap<MinuteKey, Pending>()

    suspend fun start() {
        val snapshot = counters.read()
        val checkpoint = store.loadCheckpoint(CHECKPOINT_SAMPLER_TOTAL)
        if (checkpoint != null && Duration.between(checkpoint.at, snapshot.at).toMillis() > GAP_THRESHOLD_MS) {
            val reason = if (checkpoint.bootId != snapshot.bootId) GapReason.REBOOT else GapReason.SERVICE_KILLED
            store.insertGap(CoverageGap(checkpoint.at, snapshot.at, reason))
        }
        previousMobile = CounterReading(snapshot.at, snapshot.mobileRxBytes, snapshot.mobileTxBytes, snapshot.bootId)
        previousTotal = CounterReading(snapshot.at, snapshot.totalRxBytes, snapshot.totalTxBytes, snapshot.bootId)
        hasEma = false
        started = true
    }

    suspend fun tick() {
        check(started) { "call start() before tick()" }
        val snapshot = counters.read()
        val mobileNow = CounterReading(snapshot.at, snapshot.mobileRxBytes, snapshot.mobileTxBytes, snapshot.bootId)
        val totalNow = CounterReading(snapshot.at, snapshot.totalRxBytes, snapshot.totalTxBytes, snapshot.bootId)
        val before = previousTotal!!
        val mobileDelta = CounterReconciler.delta(previousMobile, mobileNow)
        val totalDelta = CounterReconciler.delta(before, totalNow)
        val elapsedMs = Duration.between(before.at, snapshot.at).toMillis()
        previousMobile = mobileNow
        previousTotal = totalNow
        if (elapsedMs <= 0L) return

        if (mobileDelta.wasReset || totalDelta.wasReset) {
            store.insertGap(CoverageGap(before.at, snapshot.at, GapReason.COUNTER_RESET))
        }
        val wifiRx = (totalDelta.rxBytes - mobileDelta.rxBytes).coerceAtLeast(0L)
        val wifiTx = (totalDelta.txBytes - mobileDelta.txBytes).coerceAtLeast(0L)

        val seconds = elapsedMs / 1000.0
        val instantRx = (mobileDelta.rxBytes + wifiRx) / seconds
        val instantTx = (mobileDelta.txBytes + wifiTx) / seconds
        if (!hasEma) {
            emaRx = instantRx; emaTx = instantTx; hasEma = true
        } else {
            emaRx = EMA_ALPHA * instantRx + (1 - EMA_ALPHA) * emaRx
            emaTx = EMA_ALPHA * instantTx + (1 - EMA_ALPHA) * emaTx
        }
        _liveSpeed.tryEmit(LiveSpeed(emaRx.roundToLong(), emaTx.roundToLong(), network.current.value, snapshot.at))

        val minute = snapshot.at.truncatedTo(ChronoUnit.MINUTES)
        add(MinuteKey(minute, NetworkKind.MOBILE, subscription.defaultDataSubscriptionId()), mobileDelta.rxBytes, mobileDelta.txBytes)
        add(MinuteKey(minute, NetworkKind.WIFI, NO_SUBSCRIPTION), wifiRx, wifiTx)
        flush(olderThan = minute)
    }

    suspend fun stop() {
        if (!started) return
        flush(olderThan = null)
        previousMobile?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_MOBILE, it) }
        previousTotal?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_TOTAL, it) }
        started = false
    }

    /** Runs until cancelled; always flushes and saves checkpoints on the way out. */
    suspend fun run() {
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

    private fun add(key: MinuteKey, rx: Long, tx: Long) {
        val entry = pending.getOrPut(key) { Pending() }
        entry.rx += rx
        entry.tx += tx
    }

    /** Writes pending minutes older than [olderThan] (all of them when null) and refreshes the checkpoints. */
    private suspend fun flush(olderThan: Instant?) {
        val closed = pending.keys.filter { olderThan == null || it.minuteStart < olderThan }
        if (closed.isEmpty()) return
        for (key in closed) {
            val entry = pending.remove(key)!!
            store.addMinute(MinuteTotal(key.minuteStart, key.network, key.subscriptionId, entry.rx, entry.tx))
        }
        previousMobile?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_MOBILE, it) }
        previousTotal?.let { store.saveCheckpoint(CHECKPOINT_SAMPLER_TOTAL, it) }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest --tests "*SamplerEngineTest"` → PASS (14 tests); then `./gradlew :core:data:testDebugUnitTest` → PASS. If `run_ticks_every_second…` reports 30 or 50 instead of 40, the off-by-one is in how many ticks fit before 3 500 ms: `start()` reads script[0]; ticks at 0, 1000, 2000, 3000 ms read script[1..4]; do not change the test, fix the engine so the first tick runs immediately after `start()` and then waits `tickMillis`.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): sample counters into live speed and per-minute totals

Gaps, resets and restarts are recorded instead of inventing data, so the numbers stay honest."
```

---

### Task 10: `HourlyCatchUp` (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/sampler/HourlyCatchUp.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/sampler/HourlyCatchUpTest.kt`

**Interfaces:**
- Produces: `sealed interface CatchUpResult { Done(hoursUpdated), MissingUsageAccess }` and `HourlyCatchUp(source, store, appInfo, access, clock, maxBackfill = 7 days).run()` (contract 9.2).
- Algorithm: if no Usage Access -> `MissingUsageAccess`. Otherwise iterate hours from `max(checkpoint "catchup.hourly" hour minus 2 h, currentHour minus maxBackfill)` to the current hour inclusive. Per hour and network query the source (`to` clamped to now), keep UIDs with bytes > 0, tag mobile rows with `dominantSubscriptionId(minute rows of that hour)` (Wi-Fi: -1), scale completed hours with `HourlyReconciler`, `upsertHourly`, resolve app labels for UIDs not yet in `app_meta`, and finally save the checkpoint `catchup.hourly` with `at = currentHour`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeNetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.source.UidUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyCatchUpTest {
    private val now = Instant.parse("2026-10-07T10:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val hour10 = Instant.parse("2026-10-07T10:00:00Z")
    private val hour09 = Instant.parse("2026-10-07T09:00:00Z")
    private val open = Instant.parse("2000-01-01T00:00:00Z") to Instant.parse("2100-01-01T00:00:00Z")

    private fun catchUp(
        source: FakeNetworkStatsSource = FakeNetworkStatsSource(),
        store: InMemoryUsageStore = InMemoryUsageStore(),
        info: FakeAppInfoSource = FakeAppInfoSource(),
        access: FakeUsageAccess = FakeUsageAccess(true),
    ) = HourlyCatchUp(source, store, info, access, clock)

    @Test fun without_usage_access_nothing_is_queried() = runBlocking {
        val source = FakeNetworkStatsSource()
        val result = catchUp(source, access = FakeUsageAccess(false)).run()
        assertEquals(CatchUpResult.MissingUsageAccess, result)
        assertTrue(source.queries.isEmpty())
    }

    @Test fun the_first_run_backfills_seven_days_for_both_networks() = runBlocking {
        val source = FakeNetworkStatsSource()
        val result = catchUp(source).run()
        assertEquals(CatchUpResult.Done(7 * 24 + 1), result)
        assertEquals((7 * 24 + 1) * 2, source.queries.size)
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), source.queries.minOf { it.second })
    }

    @Test fun rows_are_stored_per_uid_and_network_and_mobile_rows_get_the_dominant_subscription() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(MinuteTotal(hour10.plusSeconds(300), NetworkKind.MOBILE, 3, 10, 0))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to hour10) to listOf(UidUsage(10001, 1000, 500)),
                (NetworkKind.WIFI to hour10) to listOf(UidUsage(10001, 2000, 0), UidUsage(10002, 300, 0)),
            ),
        )
        catchUp(source, store).run()
        val rows = store.hourlyRows(hour10, hour10.plusSeconds(3600)).sortedWith(compareBy({ it.uid }, { it.network }))
        assertEquals(
            listOf(
                HourlyUsage(hour10, 10001, NetworkKind.MOBILE, 3, 1000, 500),
                HourlyUsage(hour10, 10001, NetworkKind.WIFI, -1, 2000, 0),
                HourlyUsage(hour10, 10002, NetworkKind.WIFI, -1, 300, 0),
            ),
            rows,
        )
    }

    @Test fun the_current_hour_is_queried_only_up_to_now() = runBlocking {
        val source = FakeNetworkStatsSource()
        catchUp(source).run()
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, hour10, now)))
        assertTrue(source.queries.contains(Triple(NetworkKind.MOBILE, hour09, hour10)))
    }

    @Test fun a_fully_sampled_past_hour_is_scaled_to_the_samplers_total() = runBlocking {
        val store = InMemoryUsageStore()
        repeat(60) { store.addMinute(MinuteTotal(hour09.plusSeconds(it * 60L), NetworkKind.MOBILE, -1, 100, 0)) }
        val source = FakeNetworkStatsSource(mapOf((NetworkKind.MOBILE to hour09) to listOf(UidUsage(10001, 12_000, 0))))
        catchUp(source, store).run()
        val row = store.hourlyRows(hour09, hour10).single()
        assertEquals(6_000L, row.totalBytes)
    }

    @Test fun a_second_run_only_revisits_the_last_hours() = runBlocking {
        val source = FakeNetworkStatsSource()
        val job = catchUp(source, InMemoryUsageStore())
        job.run()
        source.queries.clear()
        job.run()
        assertEquals(Instant.parse("2026-10-07T08:00:00Z"), source.queries.minOf { it.second })
        assertEquals(6, source.queries.size) // hours 08, 09, 10 for two networks
    }

    @Test fun app_labels_are_resolved_once_per_new_uid_and_zero_byte_rows_are_skipped() = runBlocking {
        val store = InMemoryUsageStore()
        val info = FakeAppInfoSource(mapOf(10001 to AppMeta(10001, "com.video", "Video")))
        val source = FakeNetworkStatsSource(
            mapOf(
                (NetworkKind.MOBILE to hour10) to listOf(UidUsage(10001, 10, 0), UidUsage(10003, 0, 0)),
                (NetworkKind.WIFI to hour10) to listOf(UidUsage(10001, 20, 0)),
            ),
        )
        val job = catchUp(source, store, info)
        job.run()
        job.run()
        assertEquals(listOf(AppMeta(10001, "com.video", "Video")), store.appMeta())
        assertEquals(1, info.resolved.count { it == 10001 })
        assertTrue(store.hourlyRows(open.first, open.second).none { it.uid == 10003 })
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*HourlyCatchUpTest"` → FAIL.

- [ ] **Step 3: Implement** (`sampler/HourlyCatchUp.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.sampler

import io.github.khaledbahaaeldin.emberbyte.data.source.AppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkStatsSource
import io.github.khaledbahaaeldin.emberbyte.data.source.UsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyReconciler
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.dominantSubscriptionId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

const val CHECKPOINT_CATCHUP_HOURLY = "catchup.hourly"

sealed interface CatchUpResult {
    data class Done(val hoursUpdated: Int) : CatchUpResult
    data object MissingUsageAccess : CatchUpResult
}

/** Pulls per-app hourly usage from the system's network stats into the store. */
class HourlyCatchUp(
    private val source: NetworkStatsSource,
    private val store: UsageStore,
    private val appInfo: AppInfoSource,
    private val access: UsageAccess,
    private val clock: Clock,
    private val maxBackfill: Duration = Duration.ofDays(7),
) {
    suspend fun run(): CatchUpResult {
        if (!access.isGranted()) return CatchUpResult.MissingUsageAccess

        val now = clock.instant()
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val floor = currentHour.minus(maxBackfill)
        val checkpoint = store.loadCheckpoint(CHECKPOINT_CATCHUP_HOURLY)
        val start = checkpoint
            ?.let { it.at.truncatedTo(ChronoUnit.HOURS).minus(2, ChronoUnit.HOURS) }
            ?.let { if (it < floor) floor else it }
            ?: floor

        val knownUids = store.appMeta().map { it.uid }.toHashSet()
        var hour = start
        var updated = 0
        while (hour <= currentHour) {
            val end = hour.plus(1, ChronoUnit.HOURS)
            val minuteRows = store.minuteRows(hour, end)
            val rows = ArrayList<HourlyUsage>()
            for (network in NetworkKind.entries) {
                val subscription = if (network == NetworkKind.MOBILE) dominantSubscriptionId(minuteRows) else -1
                val queryEnd = if (end < now) end else now
                for (usage in source.query(network, hour, queryEnd)) {
                    if (usage.rxBytes + usage.txBytes > 0L) {
                        rows += HourlyUsage(hour, usage.uid, network, subscription, usage.rxBytes, usage.txBytes)
                    }
                }
            }
            val finalRows = if (end <= now) HourlyReconciler.scale(hour, rows, minuteRows) else rows
            if (finalRows.isNotEmpty()) store.upsertHourly(finalRows)
            for (uid in finalRows.map { it.uid }.distinct()) {
                if (knownUids.add(uid)) appInfo.resolve(uid)?.let { store.upsertAppMeta(listOf(it)) }
            }
            updated++
            hour = end
        }
        store.saveCheckpoint(CHECKPOINT_CATCHUP_HOURLY, CounterReading(currentHour, 0L, 0L, "catchup"))
        return CatchUpResult.Done(updated)
    }
}
```
Note on the label test: `knownUids` is seeded from the store, so on the second run `10001` is already known and `resolve` is not called again; `10003` has no bytes so it never reaches `resolve`.

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): back-fill hourly per-app usage from the system network stats

Queried hour by hour because the system returns interpolated summaries per interval."
```

---

### Task 11: `DayClock` and `RoomUsageRepository` (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/DayClock.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/RoomUsageRepository.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/DayClockTest.kt`, `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/RoomUsageRepositoryTest.kt`, `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/util/SchedulerClock.kt` (test helper)

**Interfaces:**
- Produces: `DayClock(clock).dates(): Flow<LocalDate>` (emits the current date, then again after every local midnight) and `RoomUsageRepository(store, liveSpeed, dayClock, clock, refresh)` implementing `UsageRepository` (contract section 3 + 9.3). It uses `SeriesBuilder`/`AppAggregator`; it never throws; `refreshNow()` maps `MissingUsageAccess` to `Outcome.Failure(EmberbyteError.MissingUsageAccess)`.

- [ ] **Step 1: Write the test helper and failing tests**

`util/SchedulerClock.kt` (in `src/test`):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A clock that moves with the coroutine test scheduler's virtual time. */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val base: Instant,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = SchedulerClock(scheduler, base, zone)
    override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
}
```

`util/DayClockTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DayClockTest {
    @Test fun emits_today_then_the_next_day_after_midnight() = runTest {
        val clock = SchedulerClock(testScheduler, Instant.parse("2026-10-07T23:59:00Z"))
        val dates = DayClock(clock).dates().take(3).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9)), dates)
    }

    @Test fun the_first_emission_is_immediate() = runTest {
        val clock = SchedulerClock(testScheduler, Instant.parse("2026-10-07T12:00:00Z"))
        val first = DayClock(clock).dates().take(1).toList().single()
        assertEquals(LocalDate.of(2026, 10, 7), first)
        assertEquals(0L, testScheduler.currentTime)
    }
}
```

`RoomUsageRepositoryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.sampler.CatchUpResult
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomUsageRepositoryTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val live = LiveSpeed(10, 5, NetworkKind.WIFI, now)
    private var catchUp: CatchUpResult = CatchUpResult.Done(1)

    private fun repo(store: InMemoryUsageStore) =
        RoomUsageRepository(store, flowOf(live), DayClock(clock), clock) { catchUp }

    private fun minute(at: String, network: NetworkKind, rx: Long) = MinuteTotal(Instant.parse(at), network, -1, rx, 0)
    private fun hourly(at: String, uid: Int, network: NetworkKind, rx: Long) = HourlyUsage(Instant.parse(at), uid, network, -1, rx, 0)

    @Test fun live_speed_is_passed_through() = runBlocking {
        assertEquals(live, repo(InMemoryUsageStore()).observeLiveSpeed().first())
    }

    @Test fun today_sums_only_todays_rows_split_by_network() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100))
        store.addMinute(minute("2026-10-07T10:01:00Z", NetworkKind.WIFI, 40))
        store.addMinute(minute("2026-10-06T10:00:00Z", NetworkKind.MOBILE, 999_999))
        val today = repo(store).observeToday().first()
        assertEquals(LocalDate.of(2026, 10, 7), today.date)
        assertEquals(100L, today.mobileBytes)
        assertEquals(40L, today.wifiBytes)
    }

    @Test fun today_uses_the_larger_of_minute_and_hourly_data() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:05:00Z", NetworkKind.MOBILE, 100))
        store.upsertHourly(listOf(hourly("2026-10-07T10:00:00Z", 10001, NetworkKind.MOBILE, 300)))
        assertEquals(300L, repo(store).observeToday().first().mobileBytes)
    }

    @Test fun today_respects_the_network_filter() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 100))
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.WIFI, 40))
        val today = repo(store).observeToday(UsageFilter(network = NetworkKind.WIFI)).first()
        assertEquals(0L, today.mobileBytes)
        assertEquals(40L, today.wifiBytes)
    }

    @Test fun series_has_one_point_per_day_in_the_range() = runBlocking {
        val store = InMemoryUsageStore()
        store.addMinute(minute("2026-10-05T10:00:00Z", NetworkKind.MOBILE, 10))
        store.addMinute(minute("2026-10-07T10:00:00Z", NetworkKind.MOBILE, 30))
        val range = DateRange(Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        val series = repo(store).observeSeries(range, Granularity.DAY).first()
        assertEquals(listOf(10L, 0L, 30L), series.map { it.mobileBytes })
    }

    @Test fun apps_are_aggregated_sorted_and_labelled() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.chat", "Chat")))
        store.upsertHourly(
            listOf(
                hourly("2026-10-07T09:00:00Z", 10001, NetworkKind.MOBILE, 500),
                hourly("2026-10-07T09:00:00Z", 10002, NetworkKind.WIFI, 900),
                hourly("2026-10-06T09:00:00Z", 10001, NetworkKind.MOBILE, 7_000), // before the range
            ),
        )
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), now)
        val apps = repo(store).observeApps(range, UsageFilter(), AppSort.BYTES_DESC).first()
        assertEquals(listOf("Chat", "Video"), apps.map { it.label })
        val mobileOnly = repo(store).observeApps(range, UsageFilter(network = NetworkKind.MOBILE)).first()
        assertEquals(listOf("Video"), mobileOnly.map { it.label })
    }

    @Test fun app_series_contains_only_that_packages_uids() = runBlocking {
        val store = InMemoryUsageStore()
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.chat", "Chat")))
        store.upsertHourly(
            listOf(
                hourly("2026-10-07T09:00:00Z", 10001, NetworkKind.MOBILE, 500),
                hourly("2026-10-07T09:00:00Z", 10002, NetworkKind.MOBILE, 9_000),
            ),
        )
        val range = DateRange(Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"))
        val series = repo(store).observeAppSeries("com.video", range, Granularity.DAY).first()
        assertEquals(500L, series.single().mobileBytes)
    }

    @Test fun coverage_reports_gaps_and_the_last_sample() = runBlocking {
        val store = InMemoryUsageStore()
        store.insertGap(CoverageGap(now.minusSeconds(3600), now.minusSeconds(1800), GapReason.SERVICE_KILLED))
        store.addMinute(minute("2026-10-07T11:59:00Z", NetworkKind.WIFI, 1))
        val coverage = repo(store).observeCoverage().first()
        assertEquals(1, coverage.gaps.size)
        assertEquals(Instant.parse("2026-10-07T11:59:00Z"), coverage.lastSampleAt)
    }

    @Test fun refresh_maps_the_catch_up_result_to_an_outcome() = runBlocking {
        val repo = repo(InMemoryUsageStore())
        catchUp = CatchUpResult.Done(3)
        assertTrue(repo.refreshNow() is Outcome.Success)
        catchUp = CatchUpResult.MissingUsageAccess
        assertEquals(Outcome.Failure(EmberbyteError.MissingUsageAccess), repo.refreshNow())
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*DayClockTest" --tests "*RoomUsageRepositoryTest"` → FAIL.

- [ ] **Step 3: Implement**

`util/DayClock.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Emits the current local date immediately, then again shortly after every local midnight. */
class DayClock(private val clock: Clock) {
    fun dates(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant().atZone(clock.zone)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone).toInstant()
            val wait = Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(1_000L)
            delay(wait + 500L)
        }
    }.distinctUntilChanged()
}
```

`RoomUsageRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.data.sampler.CatchUpResult
import io.github.khaledbahaaeldin.emberbyte.data.store.OPEN_END
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppAggregator
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** The real [UsageRepository]: everything is derived from the [UsageStore] with the pure engine functions. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomUsageRepository(
    private val store: UsageStore,
    private val liveSpeed: Flow<LiveSpeed>,
    private val dayClock: DayClock,
    private val clock: Clock,
    private val refresh: suspend () -> CatchUpResult,
) : UsageRepository {
    private val zone: ZoneId get() = clock.zone

    override fun observeLiveSpeed(): Flow<LiveSpeed> = liveSpeed

    override fun observeToday(filter: UsageFilter): Flow<DayUsage> = dayClock.dates().flatMapLatest { date ->
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        val padded = from.minus(1, ChronoUnit.HOURS) // an hour that straddles local midnight
        combine(store.observeMinuteRows(padded, to), store.observeHourlyRows(padded, to)) { minutes, hours ->
            val point = SeriesBuilder.build(DateRange(from, to), Granularity.DAY, zone, minutes, hours, filter).firstOrNull()
            DayUsage(date, point?.mobileBytes ?: 0L, point?.wifiBytes ?: 0L)
        }
    }

    override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> {
        val padded = SeriesBuilder.bucketStart(range.from, granularity, zone).minus(1, ChronoUnit.HOURS)
        return combine(store.observeMinuteRows(padded, range.to), store.observeHourlyRows(padded, range.to)) { minutes, hours ->
            SeriesBuilder.build(range, granularity, zone, minutes, hours, filter)
        }
    }

    override fun observeApps(range: DateRange, filter: UsageFilter, sort: AppSort): Flow<List<AppUsage>> =
        combine(
            store.observeHourlyRows(range.from.truncatedTo(ChronoUnit.HOURS), range.to),
            store.observeAppMeta(),
        ) { rows, meta -> AppAggregator.aggregate(rows, meta.associateBy { it.uid }, filter, sort) }

    override fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>> {
        val padded = SeriesBuilder.bucketStart(range.from, granularity, zone).minus(1, ChronoUnit.HOURS)
        return combine(store.observeHourlyRows(padded, range.to), store.observeAppMeta()) { rows, meta ->
            val uids = meta.filter { it.packageName == packageName }.map { it.uid }.toSet()
            SeriesBuilder.build(range, granularity, zone, emptyList(), rows.filter { it.uid in uids })
        }
    }

    override fun observeCoverage(): Flow<CoverageStatus> {
        val from = clock.instant().minus(UsageRetention.GAPS)
        return combine(store.observeGaps(from, OPEN_END), store.observeLastSample()) { gaps, last ->
            CoverageStatus(gaps, last)
        }
    }

    override suspend fun refreshNow(): Outcome<Unit> = when (refresh()) {
        is CatchUpResult.Done -> Outcome.Success(Unit)
        CatchUpResult.MissingUsageAccess -> Outcome.Failure(EmberbyteError.MissingUsageAccess)
    }
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): derive usage, series, apps and coverage from the store

The real UsageRepository is pure composition of store flows and engine maths, and follows the date across midnight."
```

---

### Task 12: Settings, onboarding, permissions and the no-plan repository (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/{AppPreferences,DataStoreSettingsRepository,OnboardingRepository,AndroidPermissionRepository,NoPlanRepository}.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/{DataStoreRepositoriesTest,AndroidPermissionRepositoryTest,NoPlanRepositoryTest}.kt`

**Interfaces:**
- Produces: `DataStoreSettingsRepository(store: DataStore<Preferences>)`, `interface OnboardingRepository { observeCompleted(): Flow<Boolean?>; complete() }` + `DataStoreOnboardingRepository(store)`, `class AppPreferences(val settings: SettingsRepository, val onboarding: OnboardingRepository) { companion object { fun create(context: Context): AppPreferences } }` (so `:app` never sees DataStore types), `AndroidPermissionRepository(context)`, `NoPlanRepository()` (contract 9.3).
- Settings keys (DataStore): `live_notification_enabled`, `notification_shows_speed`, `spike_alerts_enabled`, `amoled_black`, `use_dynamic_color`, `unit_system` (enum name), `lens_history_hours`, `haptics_enabled`; onboarding key `onboarding_completed`.

- [ ] **Step 1: Write the failing tests**

`DataStoreRepositoriesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreRepositoriesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") }

    @Test fun an_empty_store_gives_the_default_settings() = runTest {
        assertEquals(Settings(), DataStoreSettingsRepository(store()).observe().first())
    }

    @Test fun every_setting_survives_a_round_trip() = runTest {
        val repo = DataStoreSettingsRepository(store())
        val changed = Settings(
            liveNotificationEnabled = false, notificationShowsSpeed = false, spikeAlertsEnabled = false,
            amoledBlack = true, useDynamicColor = false, unitSystem = UnitSystem.BINARY,
            lensHistoryHours = 6, hapticsEnabled = false,
        )
        repo.update { changed }
        assertEquals(changed, repo.observe().first())
    }

    @Test fun update_receives_the_current_settings() = runTest {
        val repo = DataStoreSettingsRepository(store())
        repo.update { it.copy(amoledBlack = true) }
        repo.update { it.copy(hapticsEnabled = false) }
        val settings = repo.observe().first()
        assertTrue(settings.amoledBlack); assertEquals(false, settings.hapticsEnabled)
    }

    @Test fun onboarding_starts_unknown_then_false_then_true_after_complete() = runTest {
        val repo = DataStoreOnboardingRepository(store())
        assertEquals(listOf<Boolean?>(null, false), repo.observeCompleted().take(2).toList())
        repo.complete()
        assertEquals(true, repo.observeCompleted().first { it != null })
    }

    @Test fun an_unknown_unit_value_falls_back_to_decimal() = runTest {
        val dataStore = store()
        val repo = DataStoreSettingsRepository(dataStore)
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().also { it[SettingsKeys.UNIT_SYSTEM] = "NOT_A_UNIT" }
        }
        assertEquals(UnitSystem.DECIMAL, repo.observe().first().unitSystem)
    }
}
```
(`SettingsKeys` is `internal` in the main source set and visible to this test.)

`AndroidPermissionRepositoryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPermissionRepositoryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun phone_state_follows_the_granted_permission_after_recheck() = runBlocking {
        val repo = AndroidPermissionRepository(app)
        assertFalse(repo.observe().first().phoneState)
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        repo.recheck()
        assertTrue(repo.observe().first().phoneState)
    }

    @Test fun vpn_consent_is_always_false_until_lens_exists() = runBlocking {
        assertEquals(false, AndroidPermissionRepository(app).observe().first().vpnConsentGranted)
    }
}
```

`NoPlanRepositoryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoPlanRepositoryTest {
    private val repo = NoPlanRepository()

    @Test fun all_observations_are_empty() = runBlocking {
        assertTrue(repo.observePlans().first().isEmpty())
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertNull(repo.observePlanState(1).first())
        assertNull(repo.observeForecast(1).first())
        assertTrue(repo.observeAddOns(1).first().isEmpty())
        assertTrue(repo.observeFreeRules(1).first().isEmpty())
    }

    @Test fun mutations_fail_with_an_outcome_instead_of_throwing() = runBlocking {
        val plan = Plan(0, "x", null, 1, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        assertTrue(repo.upsertPlan(plan) is Outcome.Failure)
        assertTrue(repo.archivePlan(1) is Outcome.Failure)
        assertTrue(repo.whatIf(1, 1, Instant.EPOCH) is Outcome.Failure)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest --tests "*DataStoreRepositoriesTest" --tests "*AndroidPermissionRepositoryTest" --tests "*NoPlanRepositoryTest"` → FAIL.

- [ ] **Step 3: Implement**

`DataStoreSettingsRepository.kt` (also holds the keys and the onboarding repository):
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal object SettingsKeys {
    val LIVE_NOTIFICATION = booleanPreferencesKey("live_notification_enabled")
    val NOTIFICATION_SHOWS_SPEED = booleanPreferencesKey("notification_shows_speed")
    val SPIKE_ALERTS = booleanPreferencesKey("spike_alerts_enabled")
    val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val UNIT_SYSTEM = stringPreferencesKey("unit_system")
    val LENS_HISTORY_HOURS = intPreferencesKey("lens_history_hours")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        liveNotificationEnabled = this[SettingsKeys.LIVE_NOTIFICATION] ?: d.liveNotificationEnabled,
        notificationShowsSpeed = this[SettingsKeys.NOTIFICATION_SHOWS_SPEED] ?: d.notificationShowsSpeed,
        spikeAlertsEnabled = this[SettingsKeys.SPIKE_ALERTS] ?: d.spikeAlertsEnabled,
        amoledBlack = this[SettingsKeys.AMOLED_BLACK] ?: d.amoledBlack,
        useDynamicColor = this[SettingsKeys.USE_DYNAMIC_COLOR] ?: d.useDynamicColor,
        unitSystem = this[SettingsKeys.UNIT_SYSTEM]
            ?.let { name -> runCatching { UnitSystem.valueOf(name) }.getOrNull() } ?: d.unitSystem,
        lensHistoryHours = this[SettingsKeys.LENS_HISTORY_HOURS] ?: d.lensHistoryHours,
        hapticsEnabled = this[SettingsKeys.HAPTICS] ?: d.hapticsEnabled,
    )
}

internal fun MutablePreferences.write(settings: Settings) {
    this[SettingsKeys.LIVE_NOTIFICATION] = settings.liveNotificationEnabled
    this[SettingsKeys.NOTIFICATION_SHOWS_SPEED] = settings.notificationShowsSpeed
    this[SettingsKeys.SPIKE_ALERTS] = settings.spikeAlertsEnabled
    this[SettingsKeys.AMOLED_BLACK] = settings.amoledBlack
    this[SettingsKeys.USE_DYNAMIC_COLOR] = settings.useDynamicColor
    this[SettingsKeys.UNIT_SYSTEM] = settings.unitSystem.name
    this[SettingsKeys.LENS_HISTORY_HOURS] = settings.lensHistoryHours
    this[SettingsKeys.HAPTICS] = settings.hapticsEnabled
}

private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override fun observe(): Flow<Settings> = store.data.safe().map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings): Outcome<Unit> = try {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        Outcome.Success(Unit)
    } catch (error: IOException) {
        Outcome.Failure(EmberbyteError.Storage(error.message ?: "I/O error"))
    }
}

interface OnboardingRepository {
    /** `null` while the stored value is still loading. */
    fun observeCompleted(): Flow<Boolean?>
    suspend fun complete()
}

class DataStoreOnboardingRepository(private val store: DataStore<Preferences>) : OnboardingRepository {
    override fun observeCompleted(): Flow<Boolean?> = store.data.safe()
        .map<Preferences, Boolean?> { it[SettingsKeys.ONBOARDING_COMPLETED] ?: false }
        .onStart { emit(null) }

    override suspend fun complete() {
        store.edit { it[SettingsKeys.ONBOARDING_COMPLETED] = true }
    }
}
```

`AppPreferences.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile

/** Settings + onboarding backed by ONE DataStore file; keeps DataStore types out of `:app`. */
class AppPreferences(val settings: SettingsRepository, val onboarding: OnboardingRepository) {
    companion object {
        fun create(context: Context): AppPreferences {
            val store = PreferenceDataStoreFactory.create(
                produceFile = { context.applicationContext.preferencesDataStoreFile("emberbyte_prefs") },
            )
            return AppPreferences(DataStoreSettingsRepository(store), DataStoreOnboardingRepository(store))
        }
    }
}
```

`AndroidPermissionRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.khaledbahaaeldin.emberbyte.data.android.AppOpsUsageAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AndroidPermissionRepository(private val context: Context) : PermissionRepository {
    private val usageAccess = AppOpsUsageAccess(context)
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() {
        state.value = read()
    }

    private fun read() = PermissionState(
        usageAccess = usageAccess.isGranted(),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED,
        vpnConsentGranted = false, // Live Lens arrives in M5
    )
}
```

`NoPlanRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Production [PlanRepository] for M2: there are no plans yet (they arrive in M3). */
class NoPlanRepository : PlanRepository {
    private fun unavailable() = Outcome.Failure(EmberbyteError.Unexpected("Data plans arrive in a later update"))

    override fun observePlans(includeArchived: Boolean): Flow<List<Plan>> = flowOf(emptyList())
    override fun observePlanState(planId: Long): Flow<PlanState?> = flowOf(null)
    override fun observeActivePlanStates(): Flow<List<PlanState>> = flowOf(emptyList())
    override fun observeForecast(planId: Long): Flow<Forecast?> = flowOf(null)
    override fun observeAddOns(planId: Long): Flow<List<AddOn>> = flowOf(emptyList())
    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> = flowOf(emptyList())
    override suspend fun upsertPlan(plan: Plan): Outcome<Long> = unavailable()
    override suspend fun archivePlan(planId: Long): Outcome<Unit> = unavailable()
    override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> = unavailable()
    override suspend fun deleteAddOn(id: Long): Outcome<Unit> = unavailable()
    override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> = unavailable()
    override suspend fun deleteFreeRule(id: Long): Outcome<Unit> = unavailable()
    override suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast> = unavailable()
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest` → PASS. Then run the full gate `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): persist settings and onboarding in DataStore, add permission and no-plan repositories

Production now has real implementations for every repository except plans, which arrive in M3."
```

---

### Task 13: Wire the real data layer into `AppGraph`

**Files:**
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt` (replace)
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/EmberbyteApplication.kt` (replace)
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraphTest.kt`

**Interfaces:**
- Produces: `AppGraph(context, clock = Clock.systemDefaultZone())` exposing `settings: SettingsRepository`, `onboarding: OnboardingRepository`, `permissions: PermissionRepository`, `plans: PlanRepository`, `usage: UsageRepository`, `sampler: SamplerEngine`, `catchUp: HourlyCatchUp`, `suspend fun prune()`, `fun homeViewModelFactory()`. Later tasks add one factory function each. `EmberbyteApplication.graph` stays a `lazy` property.
- Production no longer references any `Fake*` class.

- [ ] **Step 1: Write the failing smoke test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppGraphTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun the_graph_builds_and_serves_real_empty_data() = runBlocking {
        val graph = AppGraph(app)
        assertEquals(Settings(), graph.settings.observe().first())
        assertTrue(graph.plans.observePlans().first().isEmpty())
        assertEquals(0L, graph.usage.observeToday().first().totalBytes)
        assertEquals(false, graph.onboarding.observeCompleted().first { it != null })
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*AppGraphTest"` → FAIL (the current `AppGraph()` has no `Context` parameter and no `onboarding`).

- [ ] **Step 3: Replace `AppGraph.kt`**

```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.khaledbahaaeldin.emberbyte.data.AndroidPermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.AppPreferences
import io.github.khaledbahaaeldin.emberbyte.data.NoPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.RoomUsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.android.AppOpsUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.android.ConnectivityNetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.android.NetworkStatsManagerSource
import io.github.khaledbahaaeldin.emberbyte.data.android.PackageManagerAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.android.TelephonySubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.android.TrafficStatsCounterSource
import io.github.khaledbahaaeldin.emberbyte.data.sampler.HourlyCatchUp
import io.github.khaledbahaaeldin.emberbyte.data.sampler.SamplerEngine
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStores
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.home.HomeViewModel
import java.time.Clock

/**
 * Hand-written dependency graph (no DI framework). One instance per process, owned by [EmberbyteApplication].
 * Production wiring only: the `Fake*` classes in `:core:data` are for tests and previews.
 */
class AppGraph(context: Context, private val clock: Clock = Clock.systemDefaultZone()) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences.create(appContext)
    private val store: UsageStore = UsageStores.create(appContext)

    val settings: SettingsRepository = preferences.settings
    val onboarding: OnboardingRepository = preferences.onboarding
    val permissions: PermissionRepository = AndroidPermissionRepository(appContext)
    val plans: PlanRepository = NoPlanRepository()

    val sampler = SamplerEngine(
        counters = TrafficStatsCounterSource(appContext, clock),
        network = ConnectivityNetworkKindSource(appContext),
        subscription = TelephonySubscriptionSource(),
        store = store,
    )
    val catchUp = HourlyCatchUp(
        source = NetworkStatsManagerSource(appContext),
        store = store,
        appInfo = PackageManagerAppInfoSource(appContext),
        access = AppOpsUsageAccess(appContext),
        clock = clock,
    )
    val usage: UsageRepository = RoomUsageRepository(store, sampler.liveSpeed, DayClock(clock), clock) { catchUp.run() }

    /** Deletes rows older than the retention windows (cheap; called from the service loop). */
    suspend fun prune() = store.prune(clock.instant())

    fun homeViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(usage, plans, settings) }
    }
}
```
(At this point `HomeViewModel` still has its M1 constructor; Task 16 changes it together with this factory.)

- [ ] **Step 4: Replace `EmberbyteApplication.kt`**

```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.app.Application

class EmberbyteApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
```

- [ ] **Step 5: Run to verify it passes** — `./gradlew :app:testDebugUnitTest --tests "*AppGraphTest"` → PASS; then `./gradlew assembleDebug` → BUILD SUCCESSFUL. If the Robolectric test cannot build the graph because a framework class is missing from Robolectric (for example `registerDefaultNetworkCallback`), keep the production code and make only the failing collaborator injectable with a no-op default in the test by constructing the graph pieces individually; record what you did.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): wire Room, DataStore and the sampler into the dependency graph

Production now runs on real repositories; plans stay empty until M3."
```

---

### Task 14: Foreground sampler service, live notification, boot receiver and catch-up worker

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/sampler/{NotificationChannels,LiveNotification,SamplerService,ServiceStarter,BootReceiver,CatchUpWorker}.kt`
- Create: `app/src/main/res/drawable/ic_stat_ember.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `app/src/main/AndroidManifest.xml` (replace), `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/{EmberbyteApplication,MainActivity}.kt`
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/sampler/{LiveNotificationTest,NotificationChannelsTest,BootReceiverTest}.kt`

**Interfaces:**
- Consumes: `AppGraph` (Task 13), `toByteUnits()` from `home/HomeMapping.kt`.
- Produces: `NotificationChannels.ensure(context)` (channel id `live`, `IMPORTANCE_LOW`), `LiveNotificationText(title, text?)` + `liveNotificationText(todayBytes, rxBps, units, showSpeed)`, `LiveNotificationBuilder(context).build(text)`, `SamplerService` (foreground, type `specialUse`), `ServiceStarter.start(context)` (never throws), `BootReceiver`, `CatchUpWorker`, `CatchUpScheduler.schedule(context)`.
- Foreground service rules (verified fact): permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, a `<property>` subtype, and `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` passed to `startForeground` on API 34+.

- [ ] **Step 1: Write the failing tests**

`LiveNotificationTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveNotificationTest {
    @Test fun text_shows_today_and_speed() {
        val text = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("1.24 GB today", text.title)
        assertEquals("↓ 4.20 MB/s", text.text)
    }

    @Test fun text_without_speed_has_no_body() {
        val text = liveNotificationText(512L, 9_999L, ByteUnits.DECIMAL, showSpeed = false)
        assertEquals("512 B today", text.title)
        assertNull(text.text)
    }

    @Test fun zero_usage_reads_naturally() {
        val text = liveNotificationText(0L, 0L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("0 B today", text.title)
        assertEquals("↓ 0 B/s", text.text)
    }

    @Test fun binary_units_are_respected() {
        assertEquals("1.00 MiB today", liveNotificationText(1_048_576L, 0L, ByteUnits.BINARY, showSpeed = false).title)
    }

    @Test fun the_built_notification_is_ongoing_on_the_live_channel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val notification = LiveNotificationBuilder(app).build(LiveNotificationText("1.24 GB today", "↓ 4.20 MB/s"))
        assertEquals(NotificationChannels.LIVE, notification.channelId)
        assertEquals("1.24 GB today", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("↓ 4.20 MB/s", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
```

`NotificationChannelsTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationChannelsTest {
    @Test fun ensure_creates_the_low_importance_live_channel_and_is_idempotent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        NotificationChannels.ensure(app)
        NotificationChannels.ensure(app)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationChannels.LIVE)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }
}
```

`BootReceiverTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun boot_completed_starts_the_sampler_service() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(SamplerService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test fun package_replaced_restarts_the_service() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(SamplerService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test fun other_broadcasts_are_ignored() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BATTERY_LOW))
        assertNull(shadowOf(app).nextStartedService)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*LiveNotificationTest" --tests "*NotificationChannelsTest" --tests "*BootReceiverTest"` → FAIL.

- [ ] **Step 3: Resources**

Append to `app/src/main/res/values/strings.xml` (inside `<resources>`):
```xml
    <string name="channel_live_name">Live usage</string>
    <string name="channel_live_description">Today\'s data usage and your current speed</string>
    <string name="notification_measuring">Measuring data usage</string>
    <string name="fgs_subtype">Continuously reads local network traffic counters to show data usage and live speed</string>
```

`app/src/main/res/drawable/ic_stat_ember.xml` (24 dp white flame, used as the status-bar icon):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2 C12,2 6,8.5 6,13.5 A6,6 0 0 0 18,13.5 C18,8.5 12,2 12,2 Z" />
</vector>
```

`app/src/main/res/xml/data_extraction_rules.xml` (nothing leaves the device, which matches the local-first rule):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 4: Replace `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Per-app history (the user grants it in Settings > Usage access). -->
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <!-- Needed to show app names for any UID (package visibility on Android 11+). -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".EmberbyteApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Emberbyte">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".sampler.SamplerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="@string/fgs_subtype" />
        </service>

        <receiver
            android:name=".sampler.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```
(The launcher icon is replaced with the real adaptive icon in Task 20.)

- [ ] **Step 5: Implement the sampler package** (package `io.github.khaledbahaaeldin.emberbyte.sampler`)

`NotificationChannels.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.R

object NotificationChannels {
    const val LIVE = "live"

    /** Creates the channels (idempotent). Only `live` exists in M2; `alerts`, `lens`, `spikes` come with their features. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            LIVE,
            context.getString(R.string.channel_live_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_live_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
```

`LiveNotification.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes

data class LiveNotificationText(val title: String, val text: String?)

fun liveNotificationText(todayBytes: Long, rxBps: Long, units: ByteUnits, showSpeed: Boolean): LiveNotificationText {
    val today = formatBytes(todayBytes, units)
    val speed = formatBytes(rxBps, units)
    return LiveNotificationText(
        title = "${today.value} ${today.unit} today",
        text = if (showSpeed) "↓ ${speed.value} ${speed.unit}/s" else null,
    )
}

class LiveNotificationBuilder(private val context: Context) {
    fun build(text: LiveNotificationText): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }
}
```

`ServiceStarter.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceStarter {
    private const val TAG = "ServiceStarter"

    /** Starts the sampler service. Never throws: Android may refuse a foreground start from the background. */
    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, SamplerService::class.java))
        } catch (error: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Foreground start not allowed right now", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "Foreground start refused", error)
        }
    }
}
```

`BootReceiver.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ServiceStarter.start(context)
        }
    }
}
```

`CatchUpWorker.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import java.util.concurrent.TimeUnit

/** Pulls per-app hourly usage even when the sampler service is not running, and tries to restart the service. */
class CatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as EmberbyteApplication).graph
        graph.catchUp.run()
        graph.prune()
        ServiceStarter.start(applicationContext)
        return Result.success()
    }
}

object CatchUpScheduler {
    private const val NAME = "catchup"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CatchUpWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
```

`SamplerService.kt`:
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
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import kotlinx.coroutines.CancellationException
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

/** Foreground service (type specialUse) that keeps the sampler, the catch-up loop and the live notification running. */
class SamplerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as EmberbyteApplication).graph
        val builder = LiveNotificationBuilder(this)
        val initial = builder.build(liveNotificationText(0L, 0L, ByteUnits.DECIMAL, showSpeed = false))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }
        if (job == null) job = scope.launch { supervise(graph, builder) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun supervise(graph: AppGraph, builder: LiveNotificationBuilder) = supervisorScope {
        launch { guarded("sampler") { graph.sampler.run() } }
        launch {
            guarded("catch-up") {
                while (true) {
                    graph.catchUp.run()
                    graph.prune()
                    delay(CATCH_UP_INTERVAL_MS)
                }
            }
        }
        launch { guarded("notification") { updateNotification(graph, builder) } }
    }

    private suspend fun guarded(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "$name failed", error)
        }
    }

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
                )
            } else {
                // A foreground service must show a notification; keep it minimal when the user turned this off.
                LiveNotificationText(getString(R.string.notification_measuring), null)
            }
        }.distinctUntilChanged().conflate().collect { text ->
            manager.notify(NOTIFICATION_ID, builder.build(text))
            delay(1_000L) // at most one update per second
        }
    }
}
```

- [ ] **Step 6: Hook the lifecycle**

`EmberbyteApplication.kt` — replace with:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import io.github.khaledbahaaeldin.emberbyte.sampler.CatchUpScheduler
import io.github.khaledbahaaeldin.emberbyte.sampler.NotificationChannels

class EmberbyteApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        CatchUpScheduler.schedule(this)
    }
}
```

`MainActivity.kt` — replace with:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
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
            val settings by graph.settings.observe().collectAsStateWithLifecycle(initialValue = Settings())
            EmberbyteTheme(
                dynamicColor = settings.useDynamicColor,
                amoledBlack = settings.amoledBlack,
            ) {
                EmberbyteApp(graph, hapticsEnabled = settings.hapticsEnabled)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Start measuring only after the first-run flow is finished.
        lifecycleScope.launch {
            if (graph.onboarding.observeCompleted().first { it != null } == true) ServiceStarter.start(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { graph.permissions.recheck() }
    }
}
```

- [ ] **Step 7: Run to verify it passes** — `./gradlew :app:testDebugUnitTest` → PASS (the new tests plus the existing ones). Then `./gradlew lintDebug assembleDebug` → BUILD SUCCESSFUL; fix lint ERRORS only (for example a missing `tools:targetApi`), and record any warnings you could not fix. A real-device check of the service happens in Task 21.

- [ ] **Step 8: Commit**

```bash
git add app
git commit -m "feat(app): run the sampler as a foreground service with a live notification

Includes the boot receiver, a 15-minute catch-up worker and backup rules that keep all data on the device."
```

---

### Task 15: Permission prompt and gap banner components (`:ui:design`)

**Files:**
- Modify: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/model/UiModels.kt` (append)
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/PermissionPrompt.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/PermissionPromptTest.kt`

**Interfaces:**
- Produces: `PermissionPromptUi(id, title, body, actionLabel)`, `GapUi(message)`, `@Composable PermissionPrompt(model, onAction, modifier)`, `@Composable GapBanner(model, modifier)` (frontend spec §4 component list).

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionPromptTest {
    @get:Rule val rule = createComposeRule()

    @Test fun prompt_shows_its_text_and_reports_the_action() {
        var clicks = 0
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                PermissionPrompt(
                    PermissionPromptUi("usage_access", "Allow usage access", "See which apps use your data.", "Open settings"),
                    onAction = { clicks++ },
                )
            }
        }
        rule.onNodeWithText("Allow usage access").assertIsDisplayed()
        rule.onNodeWithText("See which apps use your data.").assertIsDisplayed()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(1, clicks)
    }

    @Test fun gap_banner_shows_its_message() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                GapBanner(GapUi("Not measured 02:10–03:40 because the app was stopped"))
            }
        }
        rule.onNodeWithText("Not measured 02:10–03:40 because the app was stopped").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :ui:design:testDebugUnitTest --tests "*PermissionPromptTest"` → FAIL.

- [ ] **Step 3: Implement**

Append to `UiModels.kt`:
```kotlin

data class PermissionPromptUi(val id: String, val title: String, val body: String, val actionLabel: String)

data class GapUi(val message: String)
```

`tiles/PermissionPrompt.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi

@Composable
fun PermissionPrompt(model: PermissionPromptUi, onAction: () -> Unit, modifier: Modifier = Modifier) {
    BentoTile(title = model.title, modifier = modifier.fillMaxWidth(), container = TileContainer.Tertiary) {
        Text(model.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(model.actionLabel)
        }
    }
}

@Composable
fun GapBanner(model: GapUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Text(model.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — `./gradlew :ui:design:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add permission prompt and measurement-gap banner

Missing permissions and gaps must be visible, never silent."
```

---

### Task 16: Home on real data (prompts, gap banner, no-plan tile, settings/history entry points, date-driven ranges)

**Files:**
- Modify: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakePermissionRepository.kt` (replace)
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/common/BarMapping.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/{HomeUiState,HomeMapping,HomeViewModel,HomeScreen}.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/{AppGraph,EmberbyteApp}.kt`
- Modify (tests): `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/home/{HomeViewModelTest,HomeViewModelBehaviourTest}.kt`
- Create (tests): `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/home/{HomeMappingM2Test,HomeContentTest}.kt`

**Interfaces:**
- Consumes: Tasks 13-15, `PermissionState`, `CoverageStatus`.
- Produces: `HomeUiState` gains `prompts: List<PermissionPromptUi>`, `gap: GapUi?`, `hasPlan: Boolean`. `buildHomeUiState(...)` gains two trailing parameters with defaults (`coverage: CoverageStatus = CoverageStatus(emptyList(), null)`, `permissions: PermissionState? = null`) so the existing mapping tests stay valid. `HomeViewModel(usage, plans, settings, permissions, clock = system, locale = default, dates = DayClock(clock).dates())`. `HomeScreen(viewModel, onOpenSettings, onOpenHistory, onPromptAction)`. `FakePermissionRepository(initial)` gets a `set(state)` function. New shared helpers in `common/BarMapping.kt`: `spokenBytes`, `plainBytes`, `weekdayBars`.

- [ ] **Step 1: Make the permission fake settable** (`FakePermissionRepository.kt`, replace)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePermissionRepository(
    initial: PermissionState = PermissionState(usageAccess = true, notifications = true, phoneState = true, vpnConsentGranted = false),
) : PermissionRepository {
    private val state = MutableStateFlow(initial)

    fun set(value: PermissionState) {
        state.value = value
    }

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() = Unit
}
```

- [ ] **Step 2: Write the new failing tests**

`HomeMappingM2Test.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingM2Test {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private fun gap(fromMin: Long, toMin: Long, reason: GapReason) =
        CoverageGap(now.minusSeconds(fromMin * 60), now.minusSeconds(toMin * 60), reason)

    private fun build(coverage: CoverageStatus = CoverageStatus(emptyList(), null), permissions: PermissionState? = null) =
        buildHomeUiState(
            today = DayUsage(LocalDate.of(2026, 10, 7), 0, 0), live = null, planState = null, forecast = null,
            apps = emptyList(), week = emptyList(), units = UnitSystem.DECIMAL, selectedDay = null,
            now = now, zone = zone, locale = Locale.ENGLISH, coverage = coverage, permissions = permissions,
        )

    @Test fun no_permission_info_means_no_prompts() = assertTrue(build().prompts.isEmpty())

    @Test fun all_permissions_granted_means_no_prompts() =
        assertTrue(build(permissions = PermissionState(true, true, true, false)).prompts.isEmpty())

    @Test fun missing_usage_access_prompts_to_open_settings() {
        val prompts = build(permissions = PermissionState(false, true, true, false)).prompts
        assertEquals(listOf("usage_access"), prompts.map { it.id })
        assertEquals("Open settings", prompts.single().actionLabel)
    }

    @Test fun missing_notifications_prompts_to_allow() {
        val prompts = build(permissions = PermissionState(true, false, true, false)).prompts
        assertEquals(listOf("notifications"), prompts.map { it.id })
        assertEquals("Allow", prompts.single().actionLabel)
    }

    @Test fun both_missing_gives_usage_access_first() {
        val prompts = build(permissions = PermissionState(false, false, true, false)).prompts
        assertEquals(listOf("usage_access", "notifications"), prompts.map { it.id })
    }

    @Test fun no_gaps_means_no_banner() = assertNull(build().gap)

    @Test fun a_recent_long_gap_is_described_with_clock_times_and_a_reason() {
        val ui = build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.SERVICE_KILLED)), null))
        assertEquals("Not measured 09:50–11:40 because the app was stopped", ui.gap?.message)
    }

    @Test fun reasons_have_plain_wording() {
        assertEquals("Not measured 09:50–11:40 because the device restarted", build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.REBOOT)), null)).gap?.message)
        assertEquals("Not measured 09:50–11:40 because usage access was off", build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.PERMISSION_MISSING)), null)).gap?.message)
    }

    @Test fun very_short_gaps_are_not_worth_a_banner() =
        assertNull(build(coverage = CoverageStatus(listOf(gap(10, 8, GapReason.COUNTER_RESET)), null)).gap)

    @Test fun gaps_older_than_a_day_are_ignored() =
        assertNull(build(coverage = CoverageStatus(listOf(gap(60 * 30, 60 * 29, GapReason.SERVICE_KILLED)), null)).gap)

    @Test fun the_newest_gap_wins() {
        val ui = build(coverage = CoverageStatus(listOf(gap(600, 500, GapReason.REBOOT), gap(130, 20, GapReason.SERVICE_KILLED)), null))
        assertEquals("Not measured 09:50–11:40 because the app was stopped", ui.gap?.message)
    }

    @Test fun hasPlan_follows_the_plan_state() {
        assertFalse(build().hasPlan)
    }
}
```

`HomeContentTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        state: HomeUiState,
        onSettings: () -> Unit = {},
        onHistory: () -> Unit = {},
        onPrompt: (String) -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) {
            HomeContent(state, onEvent = {}, onOpenSettings = onSettings, onOpenHistory = onHistory, onPromptAction = onPrompt)
        }
    }

    @Test fun settings_icon_opens_settings() {
        var opened = false
        show(HomeUiState(), onSettings = { opened = true })
        rule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(true, opened)
    }

    @Test fun prompts_and_gap_banner_are_shown_and_report_their_action() {
        var picked: String? = null
        show(
            HomeUiState(
                prompts = listOf(PermissionPromptUi("usage_access", "Allow usage access", "Body", "Open settings")),
                gap = GapUi("Not measured 02:10–03:40 because the app was stopped"),
            ),
            onPrompt = { picked = it },
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Open settings"))
        rule.onNodeWithText("Not measured 02:10–03:40 because the app was stopped").assertExists()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals("usage_access", picked)
    }

    @Test fun without_a_plan_the_data_plan_tile_explains_whats_coming() {
        show(HomeUiState(hasPlan = false))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Data plan"))
        rule.onNodeWithText("Data plan").assertIsDisplayed()
    }

    @Test fun the_history_tile_opens_history() {
        var opened = false
        show(HomeUiState(), onHistory = { opened = true })
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("History"))
        rule.onNodeWithText("History").performClick()
        assertEquals(true, opened)
    }
}
```

Update the two existing ViewModel test files: wherever they build the view model, use
```kotlin
HomeViewModel(
    usage = FakeUsageRepository(clock, tick), plans = FakePlanRepository(clock), settings = settings,
    permissions = FakePermissionRepository(), clock = clock, locale = Locale.ENGLISH,
    dates = flowOf(LocalDate.of(2026, 10, 5)),
)
```
(adding the imports `FakePermissionRepository`, `kotlinx.coroutines.flow.flowOf`, `java.time.LocalDate`). `dates` MUST be injected: the default `DayClock` loops forever and would hang `advanceUntilIdle()`. Add one new test to `HomeViewModelTest`:
```kotlin
@Test fun missing_permissions_become_prompts() = runTest(dispatcher) {
    val permissions = FakePermissionRepository(PermissionState(usageAccess = false, notifications = true, phoneState = true, vpnConsentGranted = false))
    val vm = HomeViewModel(FakeUsageRepository(clock, MutableSharedFlow()), FakePlanRepository(clock), FakeSettingsRepository(), permissions, clock, Locale.ENGLISH, flowOf(LocalDate.of(2026, 10, 5)))
    val job = launch { vm.uiState.collect {} }
    advanceUntilIdle()
    assertEquals(listOf("usage_access"), vm.uiState.value.prompts.map { it.id })
    permissions.set(PermissionState(true, true, true, false))
    advanceUntilIdle()
    assertTrue(vm.uiState.value.prompts.isEmpty())
    job.cancel()
}
```

- [ ] **Step 3: Run to verify it fails** — `./gradlew :app:testDebugUnitTest` → FAIL (compilation: unknown parameters and fields).

- [ ] **Step 4: Shared mapping helpers** — create `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/common/BarMapping.kt`

```kotlin
package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.spokenUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** "1.24 gigabytes" - for screen readers. */
internal fun spokenBytes(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${spokenUnit(f.unit)}"
}

/** "1.24 GB". */
internal fun plainBytes(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${f.unit}"
}

/** One bar per point, labelled with the short weekday and described "Tuesday, 100 megabytes". */
internal fun weekdayBars(points: List<UsagePoint>, units: ByteUnits, zone: ZoneId, locale: Locale): List<BarUi> =
    points.map { point ->
        val day = point.start.atZone(zone).dayOfWeek
        BarUi(
            label = day.getDisplayName(TextStyle.SHORT, locale),
            bytes = point.totalBytes,
            description = "${day.getDisplayName(TextStyle.FULL, locale)}, ${spokenBytes(point.totalBytes, units)}",
        )
    }
```

- [ ] **Step 5: Update `HomeUiState.kt`** — add three fields (keep the existing ones) and imports

```kotlin
    val prompts: List<PermissionPromptUi> = emptyList(),
    val gap: GapUi? = null,
    val hasPlan: Boolean = false,
```
placed after `selectedDay`, before `units` (imports: `ui.design.model.GapUi`, `ui.design.model.PermissionPromptUi`).

- [ ] **Step 6: Update `HomeMapping.kt`**

(a) Delete the two private helpers `spoken` and `plain`; import `io.github.khaledbahaaeldin.emberbyte.common.{plainBytes, spokenBytes, weekdayBars}` and replace every call `spoken(` with `spokenBytes(` and `plain(` with `plainBytes(`.
(b) Replace the whole `val bars = week.map { ... }` block by `val bars = weekdayBars(week, byteUnits, zone, locale)`. Remove imports that become unused (`BarUi`, `TextStyle` if no longer used, `spokenUnit`, `formatBytes` if unused).
(c) Add the two parameters at the END of `buildHomeUiState`'s parameter list:
```kotlin
    coverage: CoverageStatus = CoverageStatus(emptyList(), null),
    permissions: PermissionState? = null,
```
and, in the returned `HomeUiState(...)`, add:
```kotlin
        prompts = permissionPrompts(permissions),
        gap = latestGap(coverage, now, zone),
        hasPlan = planState != null,
```
(d) Add these functions to the file (imports: `data.PermissionState`, `engine.model.CoverageGap`, `engine.model.CoverageStatus`, `engine.model.GapReason`, `ui.design.model.GapUi`, `ui.design.model.PermissionPromptUi`, `java.time.Duration`, `java.time.format.DateTimeFormatter`):
```kotlin
internal fun permissionPrompts(permissions: PermissionState?): List<PermissionPromptUi> {
    if (permissions == null) return emptyList()
    return buildList {
        if (!permissions.usageAccess) {
            add(
                PermissionPromptUi(
                    id = "usage_access",
                    title = "Allow usage access",
                    body = "See which apps use your data. Until you allow it, Emberbyte shows totals only.",
                    actionLabel = "Open settings",
                ),
            )
        }
        if (!permissions.notifications) {
            add(
                PermissionPromptUi(
                    id = "notifications",
                    title = "Show live usage",
                    body = "Get today's usage and your current speed in the notification shade.",
                    actionLabel = "Allow",
                ),
            )
        }
    }
}

private val GAP_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MIN_GAP_FOR_BANNER: Duration = Duration.ofMinutes(5)
private val GAP_BANNER_WINDOW: Duration = Duration.ofHours(24)

private fun gapReasonText(reason: GapReason): String = when (reason) {
    GapReason.SERVICE_KILLED -> "the app was stopped"
    GapReason.REBOOT -> "the device restarted"
    GapReason.COUNTER_RESET -> "the counters reset"
    GapReason.PERMISSION_MISSING -> "usage access was off"
}

/** The newest gap of at least 5 minutes that ended within the last 24 hours, as a banner message. */
internal fun latestGap(coverage: CoverageStatus, now: Instant, zone: ZoneId): GapUi? {
    val gap: CoverageGap = coverage.gaps
        .filter { Duration.between(it.from, it.to) >= MIN_GAP_FOR_BANNER && it.to >= now.minus(GAP_BANNER_WINDOW) }
        .maxByOrNull { it.to } ?: return null
    val from = GAP_CLOCK.format(gap.from.atZone(zone))
    val to = GAP_CLOCK.format(gap.to.atZone(zone))
    return GapUi("Not measured $from–$to because ${gapReasonText(gap.reason)}")
}
```

- [ ] **Step 7: Replace `HomeViewModel.kt`**

```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private data class HomeData(
    val today: DayUsage,
    val live: LiveSpeed?,
    val planState: PlanState?,
    val forecast: Forecast?,
    val apps: List<AppUsage>,
    val week: List<UsagePoint>,
)

private data class HomeBundle(val data: HomeData, val coverage: CoverageStatus, val permissions: PermissionState)

/**
 * @param dates emits the current local date and again after every midnight; the ranges below follow it.
 *   Tests MUST pass a finite flow (for example `flowOf(date)`): the default never completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    usage: UsageRepository,
    plans: PlanRepository,
    settings: SettingsRepository,
    permissions: PermissionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {

    private val selectedDay = MutableStateFlow<Int?>(null)

    private val live: Flow<LiveSpeed?> = usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }

    private val planAndForecast: Flow<Pair<PlanState?, Forecast?>> =
        plans.observeActivePlanStates().flatMapLatest { states ->
            val state = states.firstOrNull()
            if (state == null) flowOf<Pair<PlanState?, Forecast?>>(null to null)
            else plans.observeForecast(state.plan.id).map { state to it }
        }

    private val data: Flow<HomeData> = dates.flatMapLatest { todayDate ->
        val zone = clock.zone
        val endOfDay = todayDate.plusDays(1).atStartOfDay(zone).toInstant()
        val todayRange = DateRange(todayDate.atStartOfDay(zone).toInstant(), endOfDay)
        val weekRange = DateRange(todayDate.minusDays(6).atStartOfDay(zone).toInstant(), endOfDay)
        combine(
            usage.observeToday(),
            live,
            planAndForecast,
            usage.observeApps(todayRange),
            usage.observeSeries(weekRange, Granularity.DAY),
        ) { today, liveSpeed, pf, apps, week ->
            HomeData(today, liveSpeed, pf.first, pf.second, apps, week)
        }
    }

    private val bundle: Flow<HomeBundle> =
        combine(data, usage.observeCoverage(), permissions.observe()) { d, coverage, perms -> HomeBundle(d, coverage, perms) }

    val uiState: StateFlow<HomeUiState> = combine(bundle, settings.observe(), selectedDay) { b, s, selected ->
        buildHomeUiState(
            today = b.data.today,
            live = b.data.live,
            planState = b.data.planState,
            forecast = b.data.forecast,
            apps = b.data.apps,
            week = b.data.week,
            units = s.unitSystem,
            selectedDay = selected,
            now = clock.instant(),
            zone = clock.zone,
            locale = locale,
            coverage = b.coverage,
            permissions = b.permissions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectDay -> selectedDay.update { current -> if (current == event.index) null else event.index }
        }
    }
}
```

- [ ] **Step 8: Update `HomeScreen.kt`** — replace the first function and `HomeContent` as follows (keep the file's other imports; add `androidx.compose.foundation.lazy.items`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.rounded.Settings`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.ui.Alignment`, `io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.GapBanner`, `io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.PermissionPrompt`)

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onPromptAction = onPromptAction,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.heroLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
            MorphingNumber(
                bytes = state.heroBytes,
                throughputBps = state.throughputBps,
                units = state.units,
                contentDescription = state.heroDescription,
                animate = state.isToday,
            )
            state.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.isEstimated) EstimatedBadge(Modifier.padding(top = 8.dp))
        }
        state.gap?.let { gap -> item { GapBanner(gap) } }
        items(state.prompts, key = { it.id }) { prompt ->
            PermissionPrompt(prompt, onAction = { onPromptAction(prompt.id) })
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedTile(
                    rxBps = state.liveRxBps,
                    txBps = state.liveTxBps,
                    network = state.network,
                    units = state.units,
                    modifier = Modifier.weight(1f),
                )
                state.forecast?.let { ForecastTile(it, Modifier.weight(1f)) }
            }
        }
        item {
            val mobile = formatBytes(state.mobileBytes, state.units)
            val wifi = formatBytes(state.wifiBytes, state.units)
            BentoTile(
                title = "Today by network",
                modifier = Modifier.fillMaxWidth(),
                container = TileContainer.Primary,
            ) {
                Text(
                    "Mobile ${mobile.value} ${mobile.unit}  ·  Wi-Fi ${wifi.value} ${wifi.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (!state.hasPlan) {
            item {
                BentoTile(title = "Data plan", modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Plans, caps and run-out forecasts arrive in an upcoming update. Until then Emberbyte tracks your usage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            BentoTile(title = "Top apps today", modifier = Modifier.fillMaxWidth()) {
                if (state.topApps.isEmpty()) {
                    Text(
                        "No per-app data yet. It appears within about 15 minutes once usage access is allowed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.topApps.forEach { app -> AppRow(app, state.units, onClick = {}) }
                }
            }
        }
        item {
            BentoTile(title = "History", modifier = Modifier.fillMaxWidth(), onClick = onOpenHistory) {
                Text("Daily, weekly and monthly usage", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            UsageBarRow(
                points = state.week,
                selectedIndex = state.selectedDay,
                onSelect = { onEvent(HomeEvent.SelectDay(it)) },
            )
        }
    }
}
```

- [ ] **Step 9: Update `AppGraph` and `EmberbyteApp` so the app compiles**

In `AppGraph.kt` change the factory to `initializer { HomeViewModel(usage, plans, settings, permissions) }`.
In `EmberbyteApp.kt` change the Home destination to
```kotlin
            composable(Destination.Home.route) {
                HomeScreen(
                    viewModel(factory = graph.homeViewModelFactory()),
                    onOpenSettings = {},
                    onOpenHistory = {},
                    onPromptAction = {},
                )
            }
```
(the three callbacks are wired to real navigation and permission actions in Task 19).

- [ ] **Step 10: Run to verify everything passes** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug` → BUILD SUCCESSFUL. The earlier Home mapping tests (18) must still pass unchanged because of the parameter defaults. If `hasPlan_follows_the_plan_state` needs a `PlanState`, keep the test as written (it only asserts the no-plan case); the plan case is already covered by the existing mapping tests.

- [ ] **Step 11: Commit**

```bash
git add core app
git commit -m "feat(home): show real data with permission prompts, gap banner and a no-plan tile

Ranges now follow the date across midnight, and missing permissions or measurement gaps are visible on Home."
```

---

### Task 17: Apps screen

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/apps/{AppsViewModel,AppsScreen}.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt` (add a factory)
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/apps/{AppsMappingTest,AppsViewModelTest,AppsContentTest}.kt`

**Interfaces:**
- Consumes: `UsageRepository.observeApps`, `SettingsRepository`, `PermissionRepository`, `toByteUnits()`, `plainBytes()` (Task 16), `AppRow`, `PermissionPrompt`.
- Produces: `AppsRange`, `AppsNetwork`, `AppsUiState`, `AppsEvent`, `rangeFor`, `filterFor`, `buildAppsUiState`, `AppsViewModel(usage, settings, permissions, clock = system, dates = DayClock(clock).dates())`, `AppsScreen(viewModel, onOpenApp, onPromptAction)`, `AppsContent(state, onEvent, onOpenApp, onPromptAction)`, and `AppGraph.appsViewModelFactory()`.
- Behaviour (frontend spec 6.2, minus the "Cycle" chip which needs plans): range chips Today / 7 days / This month; network chips All / Mobile / Wi-Fi; sort menu (Most data, Least data, Name); search by name or package; total; empty and locked states; tapping a row opens the app detail.

- [ ] **Step 1: Write the failing tests**

`AppsMappingTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsMappingTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun today_covers_exactly_the_local_day() {
        val range = rangeFor(AppsRange.TODAY, today, zone)
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), range.from)
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), range.to)
    }

    @Test fun last_seven_days_includes_today_and_the_six_before() =
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), rangeFor(AppsRange.LAST_7_DAYS, today, zone).from)

    @Test fun this_month_starts_on_the_first() =
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), rangeFor(AppsRange.THIS_MONTH, today, zone).from)

    @Test fun network_chips_map_to_usage_filters() {
        assertEquals(UsageFilter(), filterFor(AppsNetwork.ALL))
        assertEquals(UsageFilter(network = NetworkKind.MOBILE), filterFor(AppsNetwork.MOBILE))
        assertEquals(UsageFilter(network = NetworkKind.WIFI), filterFor(AppsNetwork.WIFI))
    }

    private val apps = listOf(
        AppUsage("com.video", "Video", 1, 600, 400, null),
        AppUsage("com.chat", "Chat", 2, 100, 0, null),
    )

    private fun build(query: String = "", needsAccess: Boolean = false) =
        buildAppsUiState(apps, AppsRange.TODAY, AppsNetwork.ALL, AppSort.BYTES_DESC, query, ByteUnits.DECIMAL, needsAccess)

    @Test fun rows_total_and_loaded_flag() {
        val state = build()
        assertEquals(listOf("Video", "Chat"), state.apps.map { it.label })
        assertEquals(1_100L, state.totalBytes)
        assertTrue(state.loaded)
    }

    @Test fun search_matches_label_or_package_ignoring_case_and_updates_the_total() {
        assertEquals(listOf("Chat"), build("CHA").apps.map { it.label })
        assertEquals(listOf("Video"), build("com.vid").apps.map { it.label })
        assertEquals(100L, build("chat").totalBytes)
        assertTrue(build("nothing like this").apps.isEmpty())
    }

    @Test fun blank_query_shows_everything() = assertEquals(2, build("   ").apps.size)

    @Test fun the_usage_access_flag_is_passed_through() {
        assertTrue(build(needsAccess = true).needsUsageAccess)
        assertFalse(build(needsAccess = false).needsUsageAccess)
    }
}
```

`AppsViewModelTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(permissions: FakePermissionRepository = FakePermissionRepository()) = AppsViewModel(
        usage = FakeUsageRepository(clock, MutableSharedFlow()),
        settings = FakeSettingsRepository(),
        permissions = permissions,
        clock = clock,
        dates = flowOf(LocalDate.of(2026, 10, 5)),
    )

    @Test fun lists_the_apps_with_their_total() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(5, state.apps.size)
        assertEquals("YouTube", state.apps.first().label)
        assertEquals(1_022_000_000L, state.totalBytes)
        job.cancel()
    }

    @Test fun events_change_the_selection_and_the_search() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEvent(AppsEvent.SetRange(AppsRange.LAST_7_DAYS))
        vm.onEvent(AppsEvent.SetNetwork(AppsNetwork.WIFI))
        vm.onEvent(AppsEvent.SetSort(AppSort.NAME))
        vm.onEvent(AppsEvent.SetQuery("chr"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(AppsRange.LAST_7_DAYS, state.range)
        assertEquals(AppsNetwork.WIFI, state.network)
        assertEquals(AppSort.NAME, state.sort)
        assertEquals("chr", state.query)
        assertEquals(listOf("Chrome"), state.apps.map { it.label })
        job.cancel()
    }

    @Test fun missing_usage_access_is_reported() = runTest(dispatcher) {
        val permissions = FakePermissionRepository(PermissionState(false, true, true, false))
        val vm = viewModel(permissions)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsUsageAccess)
        permissions.set(PermissionState(true, true, true, false))
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.needsUsageAccess)
        job.cancel()
    }
}
```

`AppsContentTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNode
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppsContentTest {
    @get:Rule val rule = createComposeRule()

    private val state = AppsUiState(
        apps = listOf(AppRowUi("com.video", "Video", 600_000_000, 400_000_000), AppRowUi("com.chat", "Chat", 100_000_000, 0)),
        totalBytes = 1_100_000_000,
        loaded = true,
    )

    private fun show(
        state: AppsUiState = this.state,
        onEvent: (AppsEvent) -> Unit = {},
        onOpenApp: (String) -> Unit = {},
        onPrompt: (String) -> Unit = {},
    ) = rule.setContent {
        EmberbyteTheme(darkTheme = true, dynamicColor = false) { AppsContent(state, onEvent, onOpenApp, onPrompt) }
    }

    @Test fun shows_the_rows_and_the_total() {
        show()
        rule.onNodeWithText("Video").assertIsDisplayed()
        rule.onNodeWithText("Chat").assertIsDisplayed()
        rule.onNodeWithText("1.10 GB in total").assertIsDisplayed()
    }

    @Test fun tapping_a_row_opens_that_app() {
        var opened: String? = null
        show(onOpenApp = { opened = it })
        rule.onNodeWithText("Chat").performClick()
        assertEquals("com.chat", opened)
    }

    @Test fun chips_send_range_and_network_events() {
        val events = mutableListOf<AppsEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("7 days").performClick()
        rule.onNodeWithText("Wi-Fi").performClick()
        assertEquals(listOf(AppsEvent.SetRange(AppsRange.LAST_7_DAYS), AppsEvent.SetNetwork(AppsNetwork.WIFI)), events)
    }

    @Test fun typing_in_the_search_field_sends_the_query() {
        val events = mutableListOf<AppsEvent>()
        show(onEvent = { events += it })
        rule.onNode(hasSetTextAction()).performTextInput("vid")
        assertEquals(AppsEvent.SetQuery("vid"), events.last())
    }

    @Test fun missing_usage_access_shows_a_prompt_that_reports_its_action() {
        var picked: String? = null
        show(state.copy(needsUsageAccess = true), onPrompt = { picked = it })
        rule.onNodeWithText("Open settings").performClick()
        assertEquals("usage_access", picked)
    }

    @Test fun an_empty_loaded_list_explains_itself() {
        show(AppsUiState(loaded = true))
        rule.onNodeWithText("No app data yet", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*Apps*"` → FAIL.

- [ ] **Step 3: Implement the view model** (`apps/AppsViewModel.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class AppsRange { TODAY, LAST_7_DAYS, THIS_MONTH }

enum class AppsNetwork { ALL, MOBILE, WIFI }

data class AppsUiState(
    val range: AppsRange = AppsRange.TODAY,
    val network: AppsNetwork = AppsNetwork.ALL,
    val sort: AppSort = AppSort.BYTES_DESC,
    val query: String = "",
    val apps: List<AppRowUi> = emptyList(),
    val totalBytes: Long = 0L,
    val units: ByteUnits = ByteUnits.DECIMAL,
    val needsUsageAccess: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface AppsEvent {
    data class SetRange(val range: AppsRange) : AppsEvent
    data class SetNetwork(val network: AppsNetwork) : AppsEvent
    data class SetSort(val sort: AppSort) : AppsEvent
    data class SetQuery(val query: String) : AppsEvent
}

/** The range ends at the end of [today] so rows written later in the day are included. */
internal fun rangeFor(range: AppsRange, today: LocalDate, zone: ZoneId): DateRange {
    val end = today.plusDays(1).atStartOfDay(zone).toInstant()
    val start = when (range) {
        AppsRange.TODAY -> today
        AppsRange.LAST_7_DAYS -> today.minusDays(6)
        AppsRange.THIS_MONTH -> today.withDayOfMonth(1)
    }.atStartOfDay(zone).toInstant()
    return DateRange(start, end)
}

internal fun filterFor(network: AppsNetwork): UsageFilter = UsageFilter(
    network = when (network) {
        AppsNetwork.ALL -> null
        AppsNetwork.MOBILE -> NetworkKind.MOBILE
        AppsNetwork.WIFI -> NetworkKind.WIFI
    },
)

internal fun buildAppsUiState(
    apps: List<AppUsage>,
    range: AppsRange,
    network: AppsNetwork,
    sort: AppSort,
    query: String,
    units: ByteUnits,
    needsUsageAccess: Boolean,
): AppsUiState {
    val text = query.trim()
    val shown = if (text.isEmpty()) {
        apps
    } else {
        apps.filter { it.label.contains(text, ignoreCase = true) || it.packageName.contains(text, ignoreCase = true) }
    }
    return AppsUiState(
        range = range,
        network = network,
        sort = sort,
        query = query,
        apps = shown.map { AppRowUi(it.packageName, it.label, it.mobileBytes, it.wifiBytes) },
        totalBytes = shown.sumOf { it.totalBytes },
        units = units,
        needsUsageAccess = needsUsageAccess,
        loaded = true,
    )
}

private data class Selection(
    val range: AppsRange = AppsRange.TODAY,
    val network: AppsNetwork = AppsNetwork.ALL,
    val sort: AppSort = AppSort.BYTES_DESC,
)

/** @param dates tests MUST pass a finite flow (for example `flowOf(date)`). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModel(
    usage: UsageRepository,
    settings: SettingsRepository,
    permissions: PermissionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {
    private val selection = MutableStateFlow(Selection())
    private val query = MutableStateFlow("")

    private val apps: Flow<List<AppUsage>> = combine(dates, selection) { date, sel -> date to sel }
        .flatMapLatest { (date, sel) ->
            usage.observeApps(rangeFor(sel.range, date, clock.zone), filterFor(sel.network), sel.sort)
        }

    val uiState: StateFlow<AppsUiState> =
        combine(apps, selection, query, settings.observe(), permissions.observe()) { list, sel, text, s, perm ->
            buildAppsUiState(list, sel.range, sel.network, sel.sort, text, s.unitSystem.toByteUnits(), !perm.usageAccess)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun onEvent(event: AppsEvent) {
        when (event) {
            is AppsEvent.SetRange -> selection.update { it.copy(range = event.range) }
            is AppsEvent.SetNetwork -> selection.update { it.copy(network = event.network) }
            is AppsEvent.SetSort -> selection.update { it.copy(sort = event.sort) }
            is AppsEvent.SetQuery -> query.value = event.query
        }
    }
}
```

- [ ] **Step 4: Implement the screen** (`apps/AppsScreen.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.AppRow
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.PermissionPrompt

private val USAGE_ACCESS_PROMPT = PermissionPromptUi(
    id = "usage_access",
    title = "Allow usage access",
    body = "Per-app numbers need usage access. Totals keep working without it.",
    actionLabel = "Open settings",
)

@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    onOpenApp: (String) -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppsContent(state, viewModel::onEvent, onOpenApp, onPromptAction, modifier)
}

@Composable
internal fun AppsContent(
    state: AppsUiState,
    onEvent: (AppsEvent) -> Unit,
    onOpenApp: (String) -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Apps", style = MaterialTheme.typography.headlineMedium) }
        item {
            ChipRow {
                ChoiceChip("Today", state.range == AppsRange.TODAY) { onEvent(AppsEvent.SetRange(AppsRange.TODAY)) }
                ChoiceChip("7 days", state.range == AppsRange.LAST_7_DAYS) { onEvent(AppsEvent.SetRange(AppsRange.LAST_7_DAYS)) }
                ChoiceChip("This month", state.range == AppsRange.THIS_MONTH) { onEvent(AppsEvent.SetRange(AppsRange.THIS_MONTH)) }
            }
        }
        item {
            ChipRow {
                ChoiceChip("All", state.network == AppsNetwork.ALL) { onEvent(AppsEvent.SetNetwork(AppsNetwork.ALL)) }
                ChoiceChip("Mobile", state.network == AppsNetwork.MOBILE) { onEvent(AppsEvent.SetNetwork(AppsNetwork.MOBILE)) }
                ChoiceChip("Wi-Fi", state.network == AppsNetwork.WIFI) { onEvent(AppsEvent.SetNetwork(AppsNetwork.WIFI)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(AppsEvent.SetQuery(it)) },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SortMenu(state.sort) { onEvent(AppsEvent.SetSort(it)) }
            }
        }
        if (state.needsUsageAccess) {
            item { PermissionPrompt(USAGE_ACCESS_PROMPT, onAction = { onPromptAction(USAGE_ACCESS_PROMPT.id) }) }
        }
        item {
            Text(
                "${plainBytes(state.totalBytes, state.units)} in total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.loaded && state.apps.isEmpty()) {
            item {
                Text(
                    "No app data yet. Per-app numbers appear within about 15 minutes once usage access is allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.apps, key = { it.packageName }) { app ->
                AppRow(app, state.units, onClick = { onOpenApp(app.packageName) })
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SortMenu(sort: AppSort, onSort: (AppSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Sort: ${sortLabel(sort)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(AppSort.BYTES_DESC, AppSort.BYTES_ASC, AppSort.NAME).forEach { option ->
                DropdownMenuItem(text = { Text(sortLabel(option)) }, onClick = { onSort(option); open = false })
            }
        }
    }
}

private fun sortLabel(sort: AppSort): String = when (sort) {
    AppSort.BYTES_DESC, AppSort.SCREEN_TIME_DESC -> "Most data"
    AppSort.BYTES_ASC -> "Least data"
    AppSort.NAME -> "Name"
}
```

- [ ] **Step 5: Add the factory** to `AppGraph.kt` (and its import `io.github.khaledbahaaeldin.emberbyte.apps.AppsViewModel`):
```kotlin
    fun appsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppsViewModel(usage, settings, permissions) }
    }
```

- [ ] **Step 6: Run to verify it passes** — `./gradlew :app:testDebugUnitTest` → PASS. If the text `"1.10 GB in total"` mismatches only in formatting, fix the code (the contract formatting rules win), not the test.

- [ ] **Step 7: Commit**

```bash
git add app
git commit -m "feat(apps): list per-app usage with range, network, sort and search

The list is a thin view over the aggregated hourly rows; empty and permission states say why it is empty."
```

---

### Task 18: App detail and History screens

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/common/ScreenHeader.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/apps/AppDetail.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/history/History.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt` (two factories)
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/apps/AppDetailTest.kt`, `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/history/HistoryTest.kt`

**Interfaces:**
- Produces: `AppDetailUiState`, `buildAppDetailUiState`, `AppDetailViewModel(packageName, usage, settings, clock, locale, dates)`, `AppDetailScreen(viewModel, onBack)`; `HistoryUiState`, `HistoryEvent.SetGranularity`, `rangeForHistory`, `historyBars`, `buildHistoryUiState`, `HistoryViewModel(usage, settings, clock, locale, dates)`, `HistoryScreen(viewModel, onBack)`; `ScreenHeader(title, onBack)`; `AppGraph.appDetailViewModelFactory(packageName)` and `AppGraph.historyViewModelFactory()`.
- Behaviour: App detail = label, 7-day total, mobile/Wi-Fi split, 7 daily bars, and an "App settings" shortcut (hidden for synthetic packages `uid:*`). History = Day (last 14 days), Week (last 12 weeks, Monday start), Month (last 12 months) bars with total and average. Retention caveat: without Usage Access only the last 7 days exist, so older bars are zero - say so in the screen.

- [ ] **Step 1: Write the failing tests**

`AppDetailTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneOffset.UTC
    private val start = Instant.parse("2026-09-29T00:00:00Z") // a Tuesday

    private fun point(day: Long, mobile: Long, wifi: Long = 0) = UsagePoint(start.plusSeconds(day * 86_400), mobile, wifi)

    @Test fun known_app_uses_its_label_and_totals() {
        val apps = listOf(AppUsage("com.video", "Video", 1, 600, 400, null), AppUsage("com.chat", "Chat", 2, 5, 5, null))
        val state = buildAppDetailUiState("com.video", apps, listOf(point(0, 100_000_000)), ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals("Video", state.label)
        assertEquals(1_000L, state.totalBytes)
        assertEquals(600L, state.mobileBytes)
        assertEquals(400L, state.wifiBytes)
        assertTrue(state.loaded)
        assertEquals("Tue", state.bars.single().label)
        assertEquals("Tuesday, 100 megabytes", state.bars.single().description)
    }

    @Test fun an_app_without_usage_in_range_falls_back_to_its_package_name_and_the_series_totals() {
        val state = buildAppDetailUiState(
            "com.quiet", emptyList(), listOf(point(0, 10, 5), point(1, 20, 0)), ByteUnits.DECIMAL, zone, Locale.ENGLISH,
        )
        assertEquals("com.quiet", state.label)
        assertEquals(35L, state.totalBytes)
        assertEquals(30L, state.mobileBytes)
        assertEquals(5L, state.wifiBytes)
        assertEquals(2, state.bars.size)
    }

    @Test fun the_view_model_combines_apps_and_series() = runTest(dispatcher) {
        val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), zone)
        val vm = AppDetailViewModel(
            "com.android.chrome", FakeUsageRepository(clock, MutableSharedFlow()), FakeSettingsRepository(),
            clock, Locale.ENGLISH, flowOf(LocalDate.of(2026, 10, 5)),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Chrome", vm.uiState.value.label)
        assertEquals(212_000_000L, vm.uiState.value.totalBytes)
        assertEquals(7, vm.uiState.value.bars.size)
        job.cancel()
    }
}
```

`HistoryTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.history

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 7) // a Wednesday

    @Test fun day_range_is_the_last_fourteen_days() {
        val range = rangeForHistory(Granularity.DAY, today, zone)
        assertEquals(Instant.parse("2026-09-24T00:00:00Z"), range.from)
        assertEquals(Instant.parse("2026-10-08T00:00:00Z"), range.to)
    }

    @Test fun week_range_is_twelve_weeks_from_a_monday() =
        assertEquals(Instant.parse("2026-07-20T00:00:00Z"), rangeForHistory(Granularity.WEEK, today, zone).from)

    @Test fun month_range_is_twelve_months_from_the_first() =
        assertEquals(Instant.parse("2025-11-01T00:00:00Z"), rangeForHistory(Granularity.MONTH, today, zone).from)

    private fun point(iso: String, mobile: Long, wifi: Long = 0) = UsagePoint(Instant.parse(iso), mobile, wifi)

    @Test fun bar_labels_and_descriptions_follow_the_granularity() {
        val day = historyBars(listOf(point("2026-10-05T00:00:00Z", 100_000_000)), Granularity.DAY, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("5", day.label)
        assertEquals("Monday, 5 October, 100 megabytes", day.description)
        val week = historyBars(listOf(point("2026-10-05T00:00:00Z", 1)), Granularity.WEEK, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("5 Oct", week.label)
        assertEquals("Week of 5 October, 1 bytes", week.description)
        val month = historyBars(listOf(point("2026-10-01T00:00:00Z", 2_000_000_000)), Granularity.MONTH, ByteUnits.DECIMAL, zone, Locale.ENGLISH).single()
        assertEquals("Oct", month.label)
        assertEquals("October 2026, 2.00 gigabytes", month.description)
    }

    @Test fun total_and_average_cover_all_buckets_including_empty_ones() {
        val points = listOf(point("2026-10-05T00:00:00Z", 300), point("2026-10-06T00:00:00Z", 0, 100), point("2026-10-07T00:00:00Z", 0))
        val state = buildHistoryUiState(Granularity.DAY, points, ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals(400L, state.totalBytes)
        assertEquals(133L, state.averageBytes)
        assertEquals(3, state.bars.size)
    }

    @Test fun no_points_means_zero_totals() {
        val state = buildHistoryUiState(Granularity.DAY, emptyList(), ByteUnits.DECIMAL, zone, Locale.ENGLISH)
        assertEquals(0L, state.totalBytes); assertEquals(0L, state.averageBytes)
    }

    @Test fun the_view_model_switches_granularity() = runTest(dispatcher) {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), zone)
        val vm = HistoryViewModel(FakeUsageRepository(clock, MutableSharedFlow()), FakeSettingsRepository(), clock, Locale.ENGLISH, flowOf(today))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(Granularity.DAY, vm.uiState.value.granularity)
        vm.onEvent(HistoryEvent.SetGranularity(Granularity.MONTH))
        advanceUntilIdle()
        assertEquals(Granularity.MONTH, vm.uiState.value.granularity)
        job.cancel()
    }
}
```
(`FakeUsageRepository.observeSeries` returns a canned 7-point week regardless of range, which is enough for these tests.)

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*AppDetailTest" --tests "*HistoryTest"` → FAIL.

- [ ] **Step 3: Implement the shared header** (`common/ScreenHeader.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
```

- [ ] **Step 4: Implement the app detail** (`apps/AppDetail.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.common.weekdayBars
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.number.MorphingNumber
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class AppDetailUiState(
    val packageName: String,
    val label: String = "",
    val totalBytes: Long = 0L,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val bars: List<BarUi> = emptyList(),
    val units: ByteUnits = ByteUnits.DECIMAL,
    val loaded: Boolean = false,
)

internal fun buildAppDetailUiState(
    packageName: String,
    apps: List<AppUsage>,
    series: List<UsagePoint>,
    units: ByteUnits,
    zone: ZoneId,
    locale: Locale,
): AppDetailUiState {
    val app = apps.firstOrNull { it.packageName == packageName }
    return AppDetailUiState(
        packageName = packageName,
        label = app?.label ?: packageName,
        totalBytes = app?.totalBytes ?: series.sumOf { it.totalBytes },
        mobileBytes = app?.mobileBytes ?: series.sumOf { it.mobileBytes },
        wifiBytes = app?.wifiBytes ?: series.sumOf { it.wifiBytes },
        bars = weekdayBars(series, units, zone, locale),
        units = units,
        loaded = true,
    )
}

/** @param dates tests MUST pass a finite flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailViewModel(
    private val packageName: String,
    usage: UsageRepository,
    settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {
    val uiState: StateFlow<AppDetailUiState> = dates.flatMapLatest { date ->
        val zone = clock.zone
        val range = DateRange(
            date.minusDays(6).atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        combine(
            usage.observeApps(range),
            usage.observeAppSeries(packageName, range, Granularity.DAY),
            settings.observe(),
        ) { apps, series, s -> buildAppDetailUiState(packageName, apps, series, s.unitSystem.toByteUnits(), zone, locale) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState(packageName))
}

@Composable
fun AppDetailScreen(viewModel: AppDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(state.label.ifEmpty { state.packageName }, onBack) }
        item {
            Text(
                "Last 7 days",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MorphingNumber(
                bytes = state.totalBytes,
                throughputBps = 0L,
                units = state.units,
                contentDescription = "${spokenBytes(state.totalBytes, state.units)} used in the last 7 days",
                animate = false,
            )
        }
        item {
            val mobile = formatBytes(state.mobileBytes, state.units)
            val wifi = formatBytes(state.wifiBytes, state.units)
            BentoTile(title = "By network", modifier = Modifier.fillMaxWidth(), container = TileContainer.Primary) {
                Text(
                    "Mobile ${mobile.value} ${mobile.unit}  ·  Wi-Fi ${wifi.value} ${wifi.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { UsageBarRow(points = state.bars, selectedIndex = null, onSelect = {}) }
        if (!state.packageName.startsWith("uid:")) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = { openAppSettings(context, state.packageName) }) { Text("App settings") }
                }
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings screen to open; nothing to do.
    }
}
```

- [ ] **Step 5: Implement History** (`history/History.kt`)

```kotlin
package io.github.khaledbahaaeldin.emberbyte.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val granularity: Granularity = Granularity.DAY,
    val bars: List<BarUi> = emptyList(),
    val totalBytes: Long = 0L,
    val averageBytes: Long = 0L,
    val units: ByteUnits = ByteUnits.DECIMAL,
    val loaded: Boolean = false,
)

sealed interface HistoryEvent {
    data class SetGranularity(val granularity: Granularity) : HistoryEvent
}

/** Day: last 14 days. Week: last 12 weeks (Monday start). Month: last 12 months. Ends at the end of [today]. */
internal fun rangeForHistory(granularity: Granularity, today: LocalDate, zone: ZoneId): DateRange {
    val end = today.plusDays(1).atStartOfDay(zone).toInstant()
    val start = when (granularity) {
        Granularity.DAY, Granularity.HOUR -> today.minusDays(13)
        Granularity.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
        Granularity.MONTH -> today.withDayOfMonth(1).minusMonths(11)
    }.atStartOfDay(zone).toInstant()
    return DateRange(start, end)
}

internal fun historyBars(
    points: List<UsagePoint>,
    granularity: Granularity,
    units: ByteUnits,
    zone: ZoneId,
    locale: Locale,
): List<BarUi> = points.map { point ->
    val date = point.start.atZone(zone).toLocalDate()
    val month = date.month.getDisplayName(TextStyle.FULL, locale)
    val spoken = spokenBytes(point.totalBytes, units)
    val (label, description) = when (granularity) {
        Granularity.DAY, Granularity.HOUR ->
            date.dayOfMonth.toString() to
                "${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${date.dayOfMonth} $month, $spoken"
        Granularity.WEEK ->
            DateTimeFormatter.ofPattern("d MMM", locale).format(date) to "Week of ${date.dayOfMonth} $month, $spoken"
        Granularity.MONTH ->
            date.month.getDisplayName(TextStyle.SHORT, locale) to "$month ${date.year}, $spoken"
    }
    BarUi(label, point.totalBytes, description)
}

internal fun buildHistoryUiState(
    granularity: Granularity,
    points: List<UsagePoint>,
    units: ByteUnits,
    zone: ZoneId,
    locale: Locale,
): HistoryUiState {
    val total = points.sumOf { it.totalBytes }
    return HistoryUiState(
        granularity = granularity,
        bars = historyBars(points, granularity, units, zone, locale),
        totalBytes = total,
        averageBytes = if (points.isEmpty()) 0L else total / points.size,
        units = units,
        loaded = true,
    )
}

/** @param dates tests MUST pass a finite flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    usage: UsageRepository,
    settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {
    private val granularity = MutableStateFlow(Granularity.DAY)

    val uiState: StateFlow<HistoryUiState> = combine(dates, granularity) { date, g -> date to g }
        .flatMapLatest { (date, g) ->
            combine(usage.observeSeries(rangeForHistory(g, date, clock.zone), g), settings.observe()) { points, s ->
                buildHistoryUiState(g, points, s.unitSystem.toByteUnits(), clock.zone, locale)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.SetGranularity -> granularity.value = event.granularity
        }
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = when (state.granularity) {
        Granularity.WEEK -> "week"
        Granularity.MONTH -> "month"
        else -> "day"
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("History", onBack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.granularity == Granularity.DAY,
                    onClick = { viewModel.onEvent(HistoryEvent.SetGranularity(Granularity.DAY)) },
                    label = { Text("Day") },
                )
                FilterChip(
                    selected = state.granularity == Granularity.WEEK,
                    onClick = { viewModel.onEvent(HistoryEvent.SetGranularity(Granularity.WEEK)) },
                    label = { Text("Week") },
                )
                FilterChip(
                    selected = state.granularity == Granularity.MONTH,
                    onClick = { viewModel.onEvent(HistoryEvent.SetGranularity(Granularity.MONTH)) },
                    label = { Text("Month") },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoTile(title = "Total", modifier = Modifier.weight(1f), container = TileContainer.Primary) {
                    Text(plainBytes(state.totalBytes, state.units), style = MaterialTheme.typography.headlineSmall)
                }
                BentoTile(title = "Average per $unit", modifier = Modifier.weight(1f)) {
                    Text(plainBytes(state.averageBytes, state.units), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item { UsageBarRow(points = state.bars, selectedIndex = null, onSelect = {}) }
        item {
            Text(
                "Without usage access only the last 7 days are available; older bars stay empty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 6: Add the factories** to `AppGraph.kt` (imports: `apps.AppDetailViewModel`, `history.HistoryViewModel`):
```kotlin
    fun appDetailViewModelFactory(packageName: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppDetailViewModel(packageName, usage, settings) }
    }

    fun historyViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HistoryViewModel(usage, settings) }
    }
```

- [ ] **Step 7: Run to verify it passes** — `./gradlew :app:testDebugUnitTest` → PASS. Note: the week test string uses "1 bytes" because the real `spokenUnit("B")` is "bytes"; keep it, it documents the current wording.

- [ ] **Step 8: Commit**

```bash
git add app
git commit -m "feat(app): add per-app detail and a day/week/month history screen

Both reuse the shared bar mapping so Home, detail and history describe usage the same way for screen readers."
```

---

### Task 19: Settings, onboarding and navigation wiring

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/settings/{SettingsScreen,SettingsViewModel}.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/onboarding/{OnboardingScreen,OnboardingViewModel}.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/{Routes,PermissionActions}.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/Destination.kt` (prefix match), `EmberbyteApp.kt` (replace), `AppGraph.kt` (two factories)
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/{settings/SettingsContentTest,onboarding/OnboardingContentTest,nav/NavigationTest}.kt`

**Interfaces:**
- Produces: `SettingsViewModel(settings)`, `SettingsContent(settings, onUpdate, onBack, versionName, onOpenSource)`, `SettingsScreen(viewModel, onBack, versionName)`; `OnboardingViewModel(onboarding, permissions)`, `OnboardingUiState(usageAccess, notifications)`, `OnboardingContent(state, onOpenUsageAccess, onRequestNotifications, onFinish)`, `OnboardingScreen(viewModel, actions, onFinish)`; `Routes` (`ONBOARDING`, `HISTORY`, `SETTINGS`, `APP_DETAIL`, `appDetail(pkg)`); `PermissionActions` + `rememberPermissionActions(graph)`; `Destination.fromRoute` now maps `apps/{packageName}` to Apps and the other non-tab routes to Home; `AppGraph.settingsViewModelFactory()`, `onboardingViewModelFactory()`.
- Behaviour: first launch shows onboarding (usage access, notifications, both skippable); finishing marks it complete, starts the sampler service and goes Home. Settings (frontend spec 6.5, M2 subset): live notification, show speed, dynamic colour, AMOLED black, haptics, units (Decimal/Binary), about. The navbar is hidden during onboarding.

- [ ] **Step 1: Write the failing tests**

`NavigationTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @Test fun tab_routes_map_to_their_destination() {
        assertEquals(Destination.Home, Destination.fromRoute("home"))
        assertEquals(Destination.Apps, Destination.fromRoute("apps"))
        assertEquals(Destination.Plans, Destination.fromRoute("plans"))
        assertEquals(Destination.Lens, Destination.fromRoute("lens"))
    }

    @Test fun the_app_detail_route_keeps_the_apps_tab_selected() {
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.APP_DETAIL))
        assertEquals(Destination.Apps, Destination.fromRoute(Routes.appDetail("com.example.app")))
    }

    @Test fun other_routes_and_null_fall_back_to_home() {
        assertEquals(Destination.Home, Destination.fromRoute(Routes.HISTORY))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.SETTINGS))
        assertEquals(Destination.Home, Destination.fromRoute(Routes.ONBOARDING))
        assertEquals(Destination.Home, Destination.fromRoute(null))
    }

    @Test fun a_tab_name_inside_another_word_does_not_match() =
        assertEquals(Destination.Home, Destination.fromRoute("appsettings"))

    @Test fun package_names_are_encoded_into_the_route() {
        assertEquals("apps/com.example.app", Routes.appDetail("com.example.app"))
        assertEquals("apps/uid%3A10123", Routes.appDetail("uid:10123"))
    }
}
```
`SettingsContentTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(settings: Settings = Settings(), onUpdate: ((Settings) -> Settings) -> Unit = {}, onBack: () -> Unit = {}) =
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SettingsContent(settings, onUpdate, onBack, versionName = "0.2.0", onOpenSource = {})
            }
        }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test fun toggling_the_live_notification_updates_only_that_field() {
        var transform: ((Settings) -> Settings)? = null
        show(onUpdate = { transform = it })
        rule.onNodeWithText("Live notification").performClick()
        val result = transform!!(Settings())
        assertFalse(result.liveNotificationEnabled)
        assertEquals(Settings().copy(liveNotificationEnabled = false), result)
    }

    @Test fun amoled_and_dynamic_colour_toggles() {
        val seen = mutableListOf<(Settings) -> Settings>()
        show(onUpdate = { seen += it })
        scrollTo("AMOLED black")
        rule.onNodeWithText("AMOLED black").performClick()
        rule.onNodeWithText("Dynamic colour").performClick()
        assertTrue(seen[0](Settings()).amoledBlack)
        assertFalse(seen[1](Settings()).useDynamicColor)
    }

    @Test fun unit_chips_switch_the_unit_system() {
        var transform: ((Settings) -> Settings)? = null
        show(onUpdate = { transform = it })
        scrollTo("Binary (GiB)")
        rule.onNodeWithText("Binary (GiB)").performClick()
        assertEquals(UnitSystem.BINARY, transform!!(Settings()).unitSystem)
    }

    @Test fun the_version_is_shown_and_back_works() {
        var back = false
        show(onBack = { back = true })
        rule.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
        scrollTo("Emberbyte 0.2.0")
        rule.onNodeWithText("Emberbyte 0.2.0").assertIsDisplayed()
    }
}
```

`OnboardingContentTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingContentTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: OnboardingUiState, usage: () -> Unit = {}, notifications: () -> Unit = {}, finish: () -> Unit = {}) =
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) { OnboardingContent(state, usage, notifications, finish) }
        }

    @Test fun explains_that_everything_stays_on_the_device() {
        show(OnboardingUiState())
        rule.onNodeWithText("Nothing ever leaves your phone", substring = true).assertIsDisplayed()
    }

    @Test fun both_step_buttons_report_their_actions() {
        var usage = 0; var notifications = 0
        show(OnboardingUiState(), usage = { usage++ }, notifications = { notifications++ })
        rule.onNodeWithText("Open settings").performClick()
        rule.onNodeWithText("Allow notifications").performClick()
        assertEquals(1, usage); assertEquals(1, notifications)
    }

    @Test fun granted_steps_show_allowed_instead_of_a_button() {
        show(OnboardingUiState(usageAccess = true, notifications = true))
        rule.onNodeWithText("Usage access allowed").assertIsDisplayed()
        rule.onNodeWithText("Notifications allowed").assertIsDisplayed()
    }

    @Test fun continue_and_skip_both_finish() {
        var finished = 0
        show(OnboardingUiState(), finish = { finished++ })
        rule.onNodeWithText("Skip for now").performClick()
        rule.onNodeWithText("Continue").performClick()
        assertEquals(2, finished)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "*NavigationTest" --tests "*SettingsContentTest" --tests "*OnboardingContentTest"` → FAIL.

- [ ] **Step 3: Routes, destination matching and permission actions**

`nav/Routes.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.nav

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val APP_DETAIL = "apps/{packageName}"
    fun appDetail(packageName: String): String = "apps/" + Uri.encode(packageName)
}
```

In `nav/Destination.kt` replace `fromRoute` with:
```kotlin
        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { route == it.route || route?.startsWith(it.route + "/") == true } ?: Home
```

`nav/PermissionActions.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.nav

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import kotlinx.coroutines.launch

/** What the permission buttons do. The permission state is re-read in `MainActivity.onResume`. */
class PermissionActions(
    val openUsageAccess: () -> Unit,
    val requestNotifications: () -> Unit,
) {
    fun onPrompt(id: String) {
        when (id) {
            "usage_access" -> openUsageAccess()
            "notifications" -> requestNotifications()
        }
    }
}

@Composable
fun rememberPermissionActions(graph: AppGraph): PermissionActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { graph.permissions.recheck() }
    }
    return remember(context, launcher) {
        PermissionActions(
            openUsageAccess = { context.startActivityOrIgnore(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)) },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !asked) {
                    asked = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Already asked (the system will not show the dialog again) or below Android 13: open the settings.
                    context.startActivityOrIgnore(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )
    }
}

private fun Context.startActivityOrIgnore(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // The device has no such settings screen.
    }
}
```

- [ ] **Step 4: Settings** — `settings/SettingsViewModel.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<Settings> =
        settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }
}
```

`settings/SettingsScreen.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem

private const val SOURCE_URL = "https://github.com/Khaledbahaaeldin/DataKB"

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsContent(
        settings = settings,
        onUpdate = viewModel::update,
        onBack = onBack,
        versionName = versionName,
        onOpenSource = { uriHandler.openUri(SOURCE_URL) },
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ScreenHeader("Settings", onBack) }
        item { SectionTitle("Notifications") }
        item {
            SwitchRow("Live notification", "Today's usage in the notification shade", settings.liveNotificationEnabled) {
                onUpdate { s -> s.copy(liveNotificationEnabled = it) }
            }
        }
        item {
            SwitchRow("Show speed", "Add your current speed to the notification", settings.notificationShowsSpeed,
                enabled = settings.liveNotificationEnabled) {
                onUpdate { s -> s.copy(notificationShowsSpeed = it) }
            }
        }
        item { SectionTitle("Appearance") }
        item {
            SwitchRow("Dynamic colour", "Use your wallpaper colours (Android 12 and newer)", settings.useDynamicColor) {
                onUpdate { s -> s.copy(useDynamicColor = it) }
            }
        }
        item {
            SwitchRow("AMOLED black", "Pure black backgrounds in dark theme", settings.amoledBlack) {
                onUpdate { s -> s.copy(amoledBlack = it) }
            }
        }
        item {
            SwitchRow("Haptics", "A light tick when the navigation pill snaps", settings.hapticsEnabled) {
                onUpdate { s -> s.copy(hapticsEnabled = it) }
            }
        }
        item { SectionTitle("Units") }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.unitSystem == UnitSystem.DECIMAL,
                    onClick = { onUpdate { s -> s.copy(unitSystem = UnitSystem.DECIMAL) } },
                    label = { Text("Decimal (GB)") },
                )
                FilterChip(
                    selected = settings.unitSystem == UnitSystem.BINARY,
                    onClick = { onUpdate { s -> s.copy(unitSystem = UnitSystem.BINARY) } },
                    label = { Text("Binary (GiB)") },
                )
            }
        }
        item { SectionTitle("About") }
        item { Text("Emberbyte $versionName", style = MaterialTheme.typography.bodyLarge) }
        item {
            Text(
                "Open source under the GPL-3.0 or later. Your data never leaves this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TextButton(onClick = onOpenSource) { Text("Source code") } }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
```
(add `import androidx.compose.runtime.remember` to `SettingsScreen.kt`).

- [ ] **Step 5: Onboarding** — `onboarding/OnboardingViewModel.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(val usageAccess: Boolean = false, val notifications: Boolean = false)

class OnboardingViewModel(
    private val onboarding: OnboardingRepository,
    permissions: PermissionRepository,
) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> = permissions.observe()
        .map { OnboardingUiState(it.usageAccess, it.notifications) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    /** Marks the first-run flow as done, then calls [onDone] (on the main thread). */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            onboarding.complete()
            onDone()
        }
    }
}
```

`onboarding/OnboardingScreen.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.nav.PermissionActions
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    actions: PermissionActions,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(state, actions.openUsageAccess, actions.requestNotifications, onFinish, modifier)
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onOpenUsageAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Emberbyte", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Emberbyte measures your mobile and Wi-Fi data on this device. Nothing ever leaves your phone: no account, no cloud.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepTile(
            title = "Usage access",
            body = "Lets Emberbyte show which apps use your data. You can skip it and still see your totals.",
            granted = state.usageAccess,
            grantedText = "Usage access allowed",
            actionLabel = "Open settings",
            onAction = onOpenUsageAccess,
        )
        StepTile(
            title = "Notifications",
            body = "Shows today's usage and your current speed in the notification shade.",
            granted = state.notifications,
            grantedText = "Notifications allowed",
            actionLabel = "Allow notifications",
            onAction = onRequestNotifications,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFinish) { Text("Skip for now") }
            Button(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) { Text("Continue") }
        }
    }
}

@Composable
private fun StepTile(
    title: String,
    body: String,
    granted: Boolean,
    grantedText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    BentoTile(title = title, modifier = Modifier.fillMaxWidth()) {
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        if (granted) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(grantedText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}
```

- [ ] **Step 6: Factories** — add to `AppGraph.kt` (imports `onboarding.OnboardingViewModel`, `settings.SettingsViewModel`):
```kotlin
    fun settingsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settings) }
    }

    fun onboardingViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { OnboardingViewModel(onboarding, permissions) }
    }
```

- [ ] **Step 7: Replace `EmberbyteApp.kt`**

```kotlin
package io.github.khaledbahaaeldin.emberbyte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.khaledbahaaeldin.emberbyte.apps.AppDetailScreen
import io.github.khaledbahaaeldin.emberbyte.apps.AppDetailViewModel
import io.github.khaledbahaaeldin.emberbyte.apps.AppsScreen
import io.github.khaledbahaaeldin.emberbyte.apps.AppsViewModel
import io.github.khaledbahaaeldin.emberbyte.history.HistoryScreen
import io.github.khaledbahaaeldin.emberbyte.history.HistoryViewModel
import io.github.khaledbahaaeldin.emberbyte.home.HomeScreen
import io.github.khaledbahaaeldin.emberbyte.nav.Destination
import io.github.khaledbahaaeldin.emberbyte.nav.Routes
import io.github.khaledbahaaeldin.emberbyte.nav.rememberPermissionActions
import io.github.khaledbahaaeldin.emberbyte.nav.tabEnterTransition
import io.github.khaledbahaaeldin.emberbyte.nav.tabExitTransition
import io.github.khaledbahaaeldin.emberbyte.onboarding.OnboardingScreen
import io.github.khaledbahaaeldin.emberbyte.onboarding.OnboardingViewModel
import io.github.khaledbahaaeldin.emberbyte.placeholder.PlaceholderScreen
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
import io.github.khaledbahaaeldin.emberbyte.settings.SettingsScreen
import io.github.khaledbahaaeldin.emberbyte.settings.SettingsViewModel
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.FloatingPillNavBar
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.glassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberGlassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberNavBarVisibility
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion

@Composable
fun EmberbyteApp(graph: AppGraph, hapticsEnabled: Boolean = true) {
    val completed by graph.onboarding.observeCompleted().collectAsStateWithLifecycle(initialValue = null)
    val value = completed
    if (value == null) {
        // The stored value is still loading: draw only the background so the right start screen is chosen once.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        AppScaffold(graph, startOnboarding = !value, hapticsEnabled = hapticsEnabled)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppScaffold(graph: AppGraph, startOnboarding: Boolean, hapticsEnabled: Boolean) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val visibility = rememberNavBarVisibility()
    val glass = rememberGlassSource()
    val actions = rememberPermissionActions(graph)
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val selected = Destination.fromRoute(route)
    val items = remember { Destination.entries.map { it.toNavBarItem() } }
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current

    Box(Modifier.fillMaxSize().nestedScroll(visibility.connection)) {
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Routes.ONBOARDING else Destination.Home.route,
            modifier = Modifier.fillMaxSize().glassSource(glass),
            enterTransition = { tabEnterTransition(motion, reduceMotion) },
            exitTransition = { tabExitTransition(motion, reduceMotion) },
            popEnterTransition = { tabEnterTransition(motion, reduceMotion) },
            popExitTransition = { tabExitTransition(motion, reduceMotion) },
        ) {
            composable(Routes.ONBOARDING) {
                val viewModel: OnboardingViewModel = viewModel(factory = graph.onboardingViewModelFactory())
                OnboardingScreen(viewModel, actions) {
                    viewModel.finish {
                        ServiceStarter.start(context)
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    viewModel(factory = graph.homeViewModelFactory()),
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onPromptAction = actions::onPrompt,
                )
            }
            composable(Destination.Apps.route) {
                val viewModel: AppsViewModel = viewModel(factory = graph.appsViewModelFactory())
                AppsScreen(
                    viewModel,
                    onOpenApp = { packageName -> navController.navigate(Routes.appDetail(packageName)) },
                    onPromptAction = actions::onPrompt,
                )
            }
            composable(
                Routes.APP_DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                val viewModel: AppDetailViewModel =
                    viewModel(key = "app-$packageName", factory = graph.appDetailViewModelFactory(packageName))
                AppDetailScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Destination.Plans.route) {
                PlaceholderScreen("Plans", "The flexible plan editor arrives in milestone 3.")
            }
            composable(Destination.Lens.route) {
                PlaceholderScreen("Lens", "Live Lens is opt-in and arrives in milestone 5.")
            }
            composable(Routes.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(factory = graph.historyViewModelFactory())
                HistoryScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = graph.settingsViewModelFactory())
                SettingsScreen(viewModel, onBack = { navController.popBackStack() })
            }
        }
        if (route != Routes.ONBOARDING) {
            FloatingPillNavBar(
                items = items,
                selectedId = selected.id,
                onSelect = { id ->
                    navController.navigate(Destination.fromId(id).route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                visible = visibility.visible,
                hapticsEnabled = hapticsEnabled,
                glass = glass,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}
```

- [ ] **Step 8: Run to verify it passes** — `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug` → BUILD SUCCESSFUL. Known pitfalls: (a) after onboarding completes the `completed` flow flips to `true` while `AppScaffold` is alive - that is fine because `NavHost` ignores later changes to `startDestination`; (b) if the settings test reports two nodes with the text "Live notification" (title and a content description), make the assertion use `onAllNodesWithText(...)[0]`.

- [ ] **Step 9: Commit**

```bash
git add app
git commit -m "feat(app): add settings, first-run onboarding and the full navigation graph

Onboarding is skippable, finishing it starts measuring, and every Home entry point now leads somewhere real."
```

---

### Task 20: Launcher icon and README

**Files:**
- Create: `app/src/main/res/values/colors.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml` (icon attributes), `README.md`

**Interfaces:** none (assets and docs). Closes audit M-24 (icon).

- [ ] **Step 1: Create the resources**

`values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#130D0A</color>
</resources>
```

`drawable/ic_launcher_foreground.xml` (an ember flame inside the 66 dp adaptive-icon safe zone):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFF7A33"
        android:pathData="M54,22 C54,22 34,44 34,62 A20,20 0 0 0 74,62 C74,44 54,22 54,22 Z" />
    <path
        android:fillColor="#FFFFD8B8"
        android:pathData="M54,50 C54,50 45,60 45,68 A9,9 0 0 0 63,68 C63,60 54,50 54,50 Z" />
</vector>
```

`mipmap-anydpi/ic_launcher.xml` and an identical `ic_launcher_round.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

In `AndroidManifest.xml` replace `android:icon="@android:drawable/sym_def_app_icon"` with
```xml
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
```

- [ ] **Step 2: Update `README.md`**: change the status line to "milestone M2 (real usage): measures mobile and Wi-Fi data, per-app history and a live notification; data plans, widgets and Live Lens are not in yet"; in **Privacy** add one sentence that the app requests `QUERY_ALL_PACKAGES` only to show app names and `PACKAGE_USAGE_STATS` only for per-app history, both optional for totals; keep everything else.

- [ ] **Step 3: Verify** — `./gradlew lintDebug assembleDebug` → BUILD SUCCESSFUL, no lint ERRORS (an "icon not monochrome-safe" warning is acceptable). Then `grep -n "sym_def_app_icon" -r app/src` must print nothing.

- [ ] **Step 4: Commit**

```bash
git add app README.md
git commit -m "feat(app): add the ember launcher icon and update the README for M2

Closes the default-icon polish item from the M1 audit."
```

---

### Task 21: Final verification (tester-led; also the M2 hand-off checklist)

**Files:** none (verification only). Screenshots go to `.superpowers/sdd/<this plan>/shots/` (git-ignored).

This task proves M2 on a real emulator with real traffic. Do not change production code here; report defects with evidence instead.

- [ ] **Step 1: Full gate on a clean build**

Run: `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL`. Record the test count per module (`:core:engine`, `:core:data`, `:ui:design`, `:app`) and the lint error/warning counts. Confirm `git status` is clean and `core/data/schemas/.../1.json` is tracked.

- [ ] **Step 2: Emulator setup** — start the AVD `Violet_API_36` (`$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Violet_API_36 -no-snapshot-save`, in the background; poll `adb shell getprop sys.boot_completed` until `1`). `adb` is `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. Use `PKG=io.github.khaledbahaaeldin.emberbyte`.

- [ ] **Step 3: Checklist** — report each item PASS / FAIL / CANNOT-VERIFY with evidence (command output, a UI dump, or a screenshot you actually looked at):

  1. **Fresh install shows onboarding.** `adb uninstall $PKG`, install the debug APK, launch. The screen says "Welcome to Emberbyte", the navbar is NOT shown, and `adb shell dumpsys activity services $PKG` shows no running `SamplerService`.
  2. **Finish onboarding.** Grant usage access with `adb shell appops set $PKG GET_USAGE_STATS allow` and notifications with `adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS`, return to the app (the buttons switch to "allowed"), tap **Continue**. Home appears with the navbar.
  3. **Foreground service type.** `adb shell dumpsys activity services $PKG` shows `SamplerService` with `isForeground=true` and `foregroundServiceType=0x40000000` (specialUse). The notification exists: `adb shell dumpsys notification --noredact | grep -A6 $PKG` shows the title "... today" on channel `live`. (This closes the "foreground-service type on API 34+" spike for the emulator.)
  4. **Real traffic is measured.** Generate traffic: try `adb shell "toybox wget -q -O /dev/null http://speedtest.tele2.net/10MB.zip"`; if that fails, open the URL in the emulator browser with `adb shell am start -a android.intent.action.VIEW -d http://speedtest.tele2.net/10MB.zip`. While it runs, the Home number grows, the live speed tile shows a non-zero rate, and the number's weight/width react. Afterwards pull the database (`adb exec-out run-as $PKG cat databases/emberbyte.db > emberbyte.db`, and the `-wal` and `-shm` files the same way) and read it with Python's `sqlite3`: `SELECT minuteStart, network, subscriptionId, rxBytes, txBytes FROM total_minute ORDER BY minuteStart DESC LIMIT 10` has rows for BOTH networks, with non-zero bytes for the active one.
  5. **Per-app history.** Force the catch-up loop by restarting the service (`adb shell am force-stop $PKG`, relaunch the app) or by waiting at most 5 minutes. Then the **Apps** tab lists apps with bytes and real labels, `usage_hourly` and `app_meta` have rows, and tapping an app opens its detail with 7 bars.
  6. **Totals vs apps.** On Home, "Today" is at least as large as the sum of the "Top apps today" rows (within 5%, or larger because of un-attributed UIDs such as the system).
  7. **Missing usage access degrades gracefully.** `adb shell appops set $PKG GET_USAGE_STATS deny`, bring the app to the foreground: Home and Apps show the "Allow usage access" prompt, totals still work. Restore with `allow`.
  8. **Gap banner.** `adb shell am force-stop $PKG`, wait 6 minutes, relaunch: Home shows "Not measured HH:mm–HH:mm because the app was stopped" (the banner needs a gap of at least 5 minutes); `coverage_gap` has a `SERVICE_KILLED` row.
  9. **Boot restart.** `adb reboot`, wait for boot WITHOUT opening the app: `dumpsys activity services $PKG` shows the sampler running again (boot receiver), and after the app is opened the next gap row is `REBOOT`.
  10. **Settings.** The gear icon on Home opens Settings. Turning "Live notification" off changes the notification to "Measuring data usage"; "Binary (GiB)" changes the units on Home; "AMOLED black" makes the dark background pure black; all choices survive `force-stop` and relaunch.
  11. **History.** The History tile opens the screen; Day/Week/Month chips change the bars; total and average are shown.
  12. **Navigation.** The navbar highlights Apps while an app detail is open; back arrows and the system back gesture return correctly; the bar is hidden only during onboarding.
  13. **Light and dark, reduce motion.** Light/dark both render correctly (`adb shell cmd uimode night yes|no`); with `adb shell settings put global animator_duration_scale 0` the navbar stays visible and the number stops reacting, and toggling it back to `1` while the app is open restores motion without a restart. Restore device settings afterwards.
  14. **Logcat is clean.** `adb logcat -d | grep -E "AndroidRuntime|FATAL|emberbyte"` shows no crash, ANR or repeated exception from the app.
  15. **Retention.** `adb shell run-as $PKG ls databases` shows `emberbyte.db`; no other app data is written outside the app's private storage.

- [ ] **Step 4: Hand-off list for the user** (write it into your report; these cannot be checked on an emulator):
  - Dual-SIM attribution: on a real dual-SIM phone, switch the default data SIM and confirm `total_minute.subscriptionId` follows it (the "per-SIM" spike from the backend spec, section 13).
  - Battery/OEM killers: leave the phone idle for a day and check that the service survives or that gaps are shown honestly.
  - Real-network speed notification smoothness and the glass blur strength (tuning item N-3).

- [ ] **Step 5: Cleanup and report** — restore every device setting you changed, stop the emulator (`adb emu kill`), confirm `git status` is clean, and write the full report (verdict, per-item results with evidence, defects as Critical / Important / Minor with a concrete fix) to the path you were given. Do not push.

