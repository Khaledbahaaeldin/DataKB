# Emberbyte M1 "Foundation" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable Android app with the five-module skeleton, CI, the Emberbyte theme, the floating pill navbar and a Home screen whose big Number reacts to fake live traffic, all on a fake data source.

**Architecture:** Lean multi-module Gradle project (`:app`, `:ui:design`, `:core:engine`, `:core:data`, `:feature:lens`). `:core:engine` holds pure-Kotlin domain types, `:core:data` holds repository interfaces plus in-memory fakes, `:ui:design` holds theme and components with no domain dependency, `:app` maps domain to UI models and hosts navigation. No real sampling, Room or services yet (M2).

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0 (built-in Kotlin), Gradle 9.6.0, JDK 17, Jetpack Compose BOM 2026.09.00, Material 3 1.4.0 (Expressive), Navigation Compose, Robolectric 4.17 for Compose tests on the JVM, Haze 2.0.1 for backdrop blur.

Specs this plan implements: [design](../specs/2026-10-01-datakb-design.md), [frontend](../specs/2026-10-01-datakb-frontend-spec.md), [backend](../specs/2026-10-01-datakb-backend-spec.md), [API contract](../specs/2026-10-01-datakb-api-contract.md). The contract wins on any naming conflict.

## Global Constraints

- minSdk **29**; compileSdk and targetSdk **36** (the SDK installed on this machine; AGP 9.4.0 supports up to 37). Java/Kotlin target **17**.
- Root package and `applicationId`: `io.github.khaledbahaaeldin.emberbyte`. Module packages: `…emberbyte.engine`, `…emberbyte.data`, `…emberbyte.ui.design`, `…emberbyte.lens`.
- Licence **GPL-3.0**; no proprietary dependencies and no Google Play Services (F-Droid compatible). Roboto Flex is OFL and must be credited.
- `:ui:design` has **no dependency** on `:core:*`. `:core:engine` is pure Kotlin/JVM (no Android).
- All motion uses `MaterialTheme.motionScheme` specs, never hand-written durations (the one exception is the 150 ms reduce-motion fade).
- Bytes are always `Long` bytes. Dark theme is the primary design target.
- Reduce motion (system animator scale 0) replaces springs with simple fades/snaps.
- **Commits:** the project `CLAUDE.md` says never commit unless explicitly asked. Run the commit steps below only after the user has approved executing this plan. Before every commit re-check `git config user.name` = `Khaledbahaaeldin` and `git config user.email` = `khaled.bahaaeldin@aiu.edu.eg` (repo-local). End each commit message with the `Co-Authored-By` trailer from your session's attribution instructions. **Never push without the user's confirmation.**
- Windows host: the shell is Git Bash. Use `./gradlew` (the wrapper is created in Task 1).

### Deviations from the specs (made while planning; the specs get updated in the same change)

1. **Colour seed:** the static fallback palette is seeded from ember orange `#E8590C` (generated with `materialyoucolor` TonalSpot), not violet. The name changed after the spec was written.
2. **`UnitSystem` in the UI module:** `:ui:design` cannot see `UnitSystem` (contract §3, `:core:data`). It defines its own `ByteUnits` enum and `:app` maps `UnitSystem → ByteUnits`.
3. **Hilt deferred to M2:** M1 uses a hand-written `AppGraph`. Hilt arrives with the services in M2.
4. **Glass blur uses Haze** (Apache-2.0) because Compose has no stable backdrop-blur API.
5. **`BentoTile` has no `span` parameter in M1:** the Home screen lays out tiles with `Row` and `weight`.
6. **Shape morphing, gap banner, permission prompts, Settings icon** are not in M1 (they need real data or later screens).

## File Structure

```
.gitattributes                        LF line endings, binary markers
.gitignore                            (modify) Android/Gradle ignores
.github/workflows/ci.yml              build, unit tests, lint
settings.gradle.kts, build.gradle.kts, gradle.properties
gradle/libs.versions.toml             all versions in one place
gradle/wrapper/*, gradlew, gradlew.bat
README.md, NOTICE                     overview and credits
licenses/roboto-flex-OFL.txt          font licence

core/engine/                          pure Kotlin
  build.gradle.kts
  src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/
    Outcome.kt  Usage.kt  Plan.kt  Forecast.kt
  src/test/kotlin/.../engine/model/ModelTest.kt

ui/design/                            theme + components
  build.gradle.kts
  src/main/res/font/roboto_flex.ttf
  src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/
    format/Bytes.kt
    theme/EmberbyteColors.kt  theme/EmberbyteTheme.kt
    number/MorphingNumber.kt
    navbar/NavBarLogic.kt  navbar/NavBarVisibility.kt  navbar/FloatingPillNavBar.kt
    model/UiModels.kt
    tiles/BentoTile.kt  tiles/Tiles.kt  tiles/UsageBarRow.kt  tiles/AppRow.kt
  src/test/kotlin/.../ui/design/…      one test file per area

core/data/                            interfaces + fakes
  build.gradle.kts
  src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/
    UsageRepository.kt  PlanRepository.kt  SettingsRepository.kt  PermissionRepository.kt
    fake/FakeTraffic.kt  fake/FakeUsageRepository.kt  fake/FakePlanRepository.kt
    fake/FakeSettingsRepository.kt  fake/FakePermissionRepository.kt
  src/test/kotlin/.../data/fake/*Test.kt

feature/lens/build.gradle.kts         empty module (M5)

app/
  build.gradle.kts, src/main/AndroidManifest.xml, res/values/{strings,themes}.xml
  src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/
    EmberbyteApplication.kt  MainActivity.kt  AppGraph.kt  EmberbyteApp.kt
    nav/Destination.kt
    home/HomeUiState.kt  home/HomeMapping.kt  home/HomeViewModel.kt  home/HomeScreen.kt
    placeholder/PlaceholderScreen.kt
  src/test/kotlin/.../home/HomeMappingTest.kt  HomeViewModelTest.kt
```

---

### Task 1: Gradle skeleton and toolchain gate

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitattributes`, `local.properties` (git-ignored)
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (generated)
- Create: `app/build.gradle.kts`, `ui/design/build.gradle.kts`, `core/engine/build.gradle.kts`, `core/data/build.gradle.kts`, `feature/lens/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/MainActivity.kt`
- Modify: `.gitignore`

**Interfaces:**
- Produces: a Gradle build where `./gradlew assembleDebug` succeeds and every later task adds only source files.

This task is the toolchain gate. If a version or plugin ID is rejected, fix it here before moving on.

- [ ] **Step 1: Bootstrap the Gradle wrapper** (no Gradle is installed on this machine)

```bash
S="$TEMP/gradle-bootstrap"; mkdir -p "$S" && cd "$S"
curl -fsSL -o gradle.zip https://services.gradle.org/distributions/gradle-9.6.0-bin.zip
powershell -NoProfile -Command "Expand-Archive -Force gradle.zip ."
mkdir wrapper-out && cd wrapper-out
"../gradle-9.6.0/bin/gradle.bat" wrapper --gradle-version 9.6.0 --distribution-type bin
cd "/c/Users/Khaled/Documents/Personal Projects/DataKB"
mkdir -p gradle/wrapper
cp "$S/wrapper-out/gradlew" "$S/wrapper-out/gradlew.bat" .
cp "$S/wrapper-out/gradle/wrapper/"* gradle/wrapper/
./gradlew --version
```

Expected: `Gradle 9.6.0` and a JVM line showing 17.

- [ ] **Step 2: Create `local.properties` and `.gitattributes`, update `.gitignore`**

`local.properties`:
```properties
sdk.dir=C\:\\Users\\Khaled\\AppData\\Local\\Android\\Sdk
```

`.gitattributes`:
```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
*.ttf binary
*.png binary
```

Append to `.gitignore`:
```gitignore

# Android / Gradle
.gradle/
.kotlin/
build/
local.properties
*.iml
.idea/
captures/
*.apk
*.aab
```

- [ ] **Step 3: Write the Gradle settings, version catalog and root build**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Emberbyte"

include(":app", ":ui:design", ":core:engine", ":core:data", ":feature:lens")
```

`gradle/libs.versions.toml`:
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

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
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

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
kotlin.code.style=official
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Write the module build files** (final dependency sets; later tasks only add source)

`core/engine/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
}
```

`core/data/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte.data"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:engine"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`feature/lens/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte.lens"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
}
```

`ui/design/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte.ui.design"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    // `api(platform(...))`: a BOM declared as `implementation` does not constrain `api` dependencies.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.khaledbahaaeldin.emberbyte"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.khaledbahaaeldin.emberbyte"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.haze)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 5: Write the minimal app (manifest, resources, activity)**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Emberbyte">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Emberbyte</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Emberbyte" parent="android:Theme.DeviceDefault.DayNight">
        <item name="android:windowActionBar">false</item>
        <item name="android:windowNoTitle">true</item>
    </style>
</resources>
```

`app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/MainActivity.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("Emberbyte") }
    }
}
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug :core:engine:build --stacktrace`
Expected: `BUILD SUCCESSFUL`. If AGP 9 rejects `kotlin { jvmToolchain(17) }`, a property in `gradle.properties`, or a plugin ID, fix it here using the AGP 9 release notes (https://developer.android.com/build/releases/gradle-plugin). Do not continue until this passes.

- [ ] **Step 7: Commit**

```bash
git add .gitattributes .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app core ui feature
git commit -m "build: add five-module Gradle skeleton on AGP 9 and Compose

The toolchain is verified first so every later task only adds source."
```

---

### Task 2: Continuous integration

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: a GitHub Actions workflow that runs `testDebugUnitTest :core:engine:test lintDebug assembleDebug` on every push and PR.

- [ ] **Step 1: Write the workflow**

`.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: Build, test and lint
        run: ./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: "**/build/reports/**"
```

- [ ] **Step 2: Make the wrapper executable for Linux**

```bash
git update-index --chmod=+x gradlew
git ls-files --stage gradlew
```
Expected: mode `100755`.

- [ ] **Step 3: Verify the same command passes locally**

Run: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL` (no tests exist yet). If `lintDebug` reports errors on the skeleton (for example a missing launcher icon warning is fine, errors are not), fix them now.

- [ ] **Step 4: Commit**

```bash
git add .github gradlew
git commit -m "ci: run unit tests, lint and a debug build on every push

Catches regressions before M2 adds services that are hard to test by hand."
```

---

### Task 3: Domain types in `:core:engine`

**Files:**
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/Outcome.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/Usage.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/Plan.kt`
- Create: `core/engine/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/Forecast.kt`
- Test: `core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/ModelTest.kt`

**Interfaces:**
- Produces (package `io.github.khaledbahaaeldin.emberbyte.engine.model`), exactly as in contract §1–2: `CONTRACT_VERSION`, `Outcome`, `EmberbyteError`, `NetworkKind`, `LiveSpeed`, `UsageFilter`, `Granularity`, `DateRange`, `UsagePoint`, `DayUsage`, `AppUsage`, `AppSort`, `GapReason`, `CoverageGap`, `CoverageStatus`, `Plan`, `Cycle`, `Rollover`, `AddOn`, `FreeRule`, `CycleWindow`, `PlanState`, `Confidence`, `Forecast`.
- Types for alerts, budgets, spikes, counters and the calculator objects are added in the milestone that first uses them (M2–M4).

- [ ] **Step 1: Write the failing test**

`core/engine/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/engine/model/ModelTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun usagePoint_total_is_mobile_plus_wifi() {
        val point = UsagePoint(Instant.EPOCH, mobileBytes = 300L, wifiBytes = 200L)
        assertEquals(500L, point.totalBytes)
    }

    @Test
    fun dayUsage_total_is_mobile_plus_wifi() {
        val day = DayUsage(LocalDate.of(2026, 10, 1), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L)
        assertEquals(1_240_000_000L, day.totalBytes)
    }

    @Test
    fun appUsage_total_is_mobile_plus_wifi() {
        val app = AppUsage("com.example", "Example", 10_001, mobileBytes = 5L, wifiBytes = 7L, screenTimeMs = null)
        assertEquals(12L, app.totalBytes)
    }

    @Test
    fun outcome_distinguishes_success_and_failure() {
        val ok: Outcome<Int> = Outcome.Success(1)
        val bad: Outcome<Int> = Outcome.Failure(EmberbyteError.NotFound)
        assertTrue(ok is Outcome.Success)
        assertTrue(bad is Outcome.Failure)
    }

    @Test
    fun contract_version_is_one() {
        assertEquals(1, CONTRACT_VERSION)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:engine:test`
Expected: FAIL (compile error: unresolved reference `UsagePoint`).

- [ ] **Step 3: Write the types**

`Outcome.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.model

const val CONTRACT_VERSION = 1

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: EmberbyteError) : Outcome<Nothing>
}

sealed interface EmberbyteError {
    data object MissingUsageAccess : EmberbyteError
    data object MissingPhoneState : EmberbyteError
    data object NotFound : EmberbyteError
    data class Invalid(val field: String, val reason: String) : EmberbyteError
    data class Storage(val cause: String) : EmberbyteError
    data class Unexpected(val cause: String) : EmberbyteError
}
```

`Usage.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant
import java.time.LocalDate

enum class NetworkKind { MOBILE, WIFI }

/** `network == null` means offline. */
data class LiveSpeed(val rxBps: Long, val txBps: Long, val network: NetworkKind?, val at: Instant)

data class UsageFilter(val network: NetworkKind? = null, val subscriptionId: Int? = null)

enum class Granularity { HOUR, DAY, WEEK, MONTH }

data class DateRange(val from: Instant, val to: Instant)

data class UsagePoint(val start: Instant, val mobileBytes: Long, val wifiBytes: Long) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

data class DayUsage(val date: LocalDate, val mobileBytes: Long, val wifiBytes: Long) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

data class AppUsage(
    val packageName: String,
    val label: String,
    val uid: Int,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val screenTimeMs: Long?,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

enum class AppSort { BYTES_DESC, BYTES_ASC, NAME, SCREEN_TIME_DESC }

enum class GapReason { SERVICE_KILLED, REBOOT, COUNTER_RESET, PERMISSION_MISSING }

data class CoverageGap(val from: Instant, val to: Instant, val reason: GapReason)

data class CoverageStatus(val gaps: List<CoverageGap>, val lastSampleAt: Instant?)
```

`Plan.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Plan(
    val id: Long, // 0 = not yet persisted
    val name: String,
    val subscriptionId: Int?, // null = not bound to a SIM
    val capBytes: Long,
    val cycle: Cycle,
    val rollover: Rollover,
    val archived: Boolean = false,
)

sealed interface Cycle {
    /** Renews on the same day each month; days beyond the month length clamp to the last day. */
    data class MonthlyOnDay(val dayOfMonth: Int, val time: LocalTime, val zone: ZoneId) : Cycle

    /** Renews every N days from an anchor date. */
    data class EveryNDays(val days: Int, val anchor: LocalDate, val time: LocalTime, val zone: ZoneId) : Cycle
}

sealed interface Rollover {
    data object None : Rollover
    data object Full : Rollover
    data class Capped(val maxBytes: Long) : Rollover
}

data class AddOn(
    val id: Long,
    val planId: Long,
    val label: String,
    val bytes: Long,
    val validFrom: Instant,
    val validUntil: Instant,
)

/** Traffic matching a rule is NOT counted against the plan. A window may cross midnight. */
data class FreeRule(
    val id: Long,
    val planId: Long,
    val label: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val packageName: String?, // null = all apps
)

data class CycleWindow(val start: Instant, val end: Instant)

data class PlanState(
    val plan: Plan,
    val window: CycleWindow,
    val effectiveCapBytes: Long,
    val usedBytes: Long,
    val remainingBytes: Long,
    val rolledOverBytes: Long,
    val addOnBytes: Long,
    val freeBytes: Long,
    val daysLeft: Int,
    val fractionUsed: Float,
    val isApproximate: Boolean,
)
```

`Forecast.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant

enum class Confidence { LOW, MEDIUM, HIGH }

/** All three runOut* values are null when the plan is projected NOT to run out this cycle. */
data class Forecast(
    val planId: Long,
    val runOutEarliest: Instant?,
    val runOutExpected: Instant?,
    val runOutLatest: Instant?,
    val projectedCycleEndBytes: Long,
    val confidence: Confidence,
    val historyDays: Int,
)
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :core:engine:test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add core/engine
git commit -m "feat(engine): add domain types from the API contract

Gives every other module one shared vocabulary before any logic exists."
```

---

### Task 4: Byte formatting in `:ui:design`

**Files:**
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/format/Bytes.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/format/BytesTest.kt`

**Interfaces:**
- Produces: `enum class ByteUnits { DECIMAL, BINARY }`, `data class FormattedBytes(val value: String, val unit: String)`, `fun formatBytes(bytes: Long, units: ByteUnits): FormattedBytes`, `fun spokenUnit(unit: String): String`.
- Rules (frontend spec §8): value ≥ 100 shows no decimals, 10–99 one decimal, below 10 two decimals; plain bytes show no decimals; decimal base 1000 (`B KB MB GB TB`), binary base 1024 (`B KiB MiB GiB TiB`); rounding that reaches the base promotes to the next unit.

- [ ] **Step 1: Write the failing test**

`BytesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import org.junit.Assert.assertEquals
import org.junit.Test

class BytesTest {
    private fun d(bytes: Long) = formatBytes(bytes, ByteUnits.DECIMAL)
    private fun b(bytes: Long) = formatBytes(bytes, ByteUnits.BINARY)

    @Test fun zero() = assertEquals(FormattedBytes("0", "B"), d(0))
    @Test fun negative_is_clamped_to_zero() = assertEquals(FormattedBytes("0", "B"), d(-5))
    @Test fun plain_bytes_have_no_decimals() = assertEquals(FormattedBytes("512", "B"), d(512))
    @Test fun below_ten_has_two_decimals() = assertEquals(FormattedBytes("1.50", "KB"), d(1_500))
    @Test fun gigabytes_two_decimals() = assertEquals(FormattedBytes("1.24", "GB"), d(1_240_000_000))
    @Test fun ten_to_ninety_nine_has_one_decimal() = assertEquals(FormattedBytes("12.3", "MB"), d(12_300_000))
    @Test fun hundred_and_up_has_no_decimals() = assertEquals(FormattedBytes("123", "MB"), d(123_456_789))
    @Test fun rounding_to_hundred_drops_the_decimal() = assertEquals(FormattedBytes("100", "MB"), d(99_960_000))
    @Test fun rounding_to_ten_uses_one_decimal() = assertEquals(FormattedBytes("10.0", "MB"), d(9_996_000))
    @Test fun rounding_to_base_promotes_the_unit() = assertEquals(FormattedBytes("1.00", "MB"), d(999_999))
    @Test fun terabytes() = assertEquals(FormattedBytes("2.50", "TB"), d(2_500_000_000_000))
    @Test fun binary_units() = assertEquals(FormattedBytes("1.00", "MiB"), b(1_048_576))
    @Test fun binary_below_base_stays() = assertEquals(FormattedBytes("1000", "B"), b(1000))

    @Test fun spoken_units() {
        assertEquals("bytes", spokenUnit("B"))
        assertEquals("megabytes", spokenUnit("MB"))
        assertEquals("gigabytes", spokenUnit("GB"))
        assertEquals("gibibytes", spokenUnit("GiB"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*BytesTest"`
Expected: FAIL (unresolved reference `formatBytes`).

- [ ] **Step 3: Implement**

`Bytes.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import java.math.BigDecimal
import java.math.RoundingMode

enum class ByteUnits(val base: Long, val labels: List<String>) {
    DECIMAL(1000L, listOf("B", "KB", "MB", "GB", "TB")),
    BINARY(1024L, listOf("B", "KiB", "MiB", "GiB", "TiB")),
}

data class FormattedBytes(val value: String, val unit: String)

fun formatBytes(bytes: Long, units: ByteUnits): FormattedBytes {
    val safe = bytes.coerceAtLeast(0L)
    var scaled = safe.toDouble()
    var index = 0
    while (index < units.labels.lastIndex && scaled >= units.base) {
        scaled /= units.base
        index++
    }
    if (index == 0) return FormattedBytes(safe.toString(), units.labels[0])

    var rounded = roundForDisplay(scaled)
    if (index < units.labels.lastIndex && rounded >= BigDecimal(units.base)) {
        scaled /= units.base
        index++
        rounded = roundForDisplay(scaled)
    }
    return FormattedBytes(rounded.toPlainString(), units.labels[index])
}

private fun decimalsFor(value: Double): Int = when {
    value >= 100 -> 0
    value >= 10 -> 1
    else -> 2
}

private fun roundForDisplay(value: Double): BigDecimal {
    var decimals = decimalsFor(value)
    var rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    // Rounding can cross a threshold (99.96 -> 100.0, 9.996 -> 10.00): re-pick the decimals.
    val recheck = decimalsFor(rounded.toDouble())
    if (recheck < decimals) {
        decimals = recheck
        rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    }
    return rounded
}

fun spokenUnit(unit: String): String = when (unit) {
    "B" -> "bytes"
    "KB" -> "kilobytes"
    "MB" -> "megabytes"
    "GB" -> "gigabytes"
    "TB" -> "terabytes"
    "KiB" -> "kibibytes"
    "MiB" -> "mebibytes"
    "GiB" -> "gibibytes"
    "TiB" -> "tebibytes"
    else -> unit
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*BytesTest"`
Expected: PASS (14 tests). If `rounding_to_base_promotes_the_unit` fails, the promotion check is wrong: 999.999 KB rounds to `1000` at zero decimals and must become `1.00 MB`.

- [ ] **Step 5: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add byte formatting with decimal and binary units

One function owns every size shown in the app, so rounding stays consistent."
```

---

### Task 5: Theme (colours, motion, reduce-motion)

**Files:**
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteColors.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteTheme.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/theme/EmberbyteThemeTest.kt`

**Interfaces:**
- Produces: `@Composable fun EmberbyteTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, amoledBlack: Boolean = false, content: @Composable () -> Unit)`, `val LocalReduceMotion: ProvidableCompositionLocal<Boolean>`, `internal fun resolveColorScheme(...)`, `internal fun isReduceMotion(animatorDurationScale: Float): Boolean`, `internal val EmberLightColors`, `internal val EmberDarkColors`.

- [ ] **Step 1: Write the failing test**

`EmberbyteThemeTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmberbyteThemeTest {
    private val dynLight = EmberLightColors.copy(primary = Color.Red)
    private val dynDark = EmberDarkColors.copy(primary = Color.Blue)

    private fun resolve(dark: Boolean, dynamic: Boolean, amoled: Boolean, sdk: Int) =
        resolveColorScheme(dark, dynamic, amoled, sdk, { dynLight }, { dynDark })

    @Test fun static_light_below_android_12() =
        assertEquals(EmberLightColors, resolve(dark = false, dynamic = true, amoled = false, sdk = 30))

    @Test fun static_dark_below_android_12() =
        assertEquals(EmberDarkColors, resolve(dark = true, dynamic = true, amoled = false, sdk = 30))

    @Test fun dynamic_light_on_android_12() =
        assertEquals(dynLight, resolve(dark = false, dynamic = true, amoled = false, sdk = 31))

    @Test fun dynamic_dark_on_android_12() =
        assertEquals(dynDark, resolve(dark = true, dynamic = true, amoled = false, sdk = 34))

    @Test fun dynamic_disabled_uses_static_palette() =
        assertEquals(EmberDarkColors, resolve(dark = true, dynamic = false, amoled = false, sdk = 34))

    @Test fun amoled_makes_dark_surfaces_black() {
        val scheme = resolve(dark = true, dynamic = false, amoled = true, sdk = 34)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    @Test fun amoled_is_ignored_in_light_theme() =
        assertEquals(EmberLightColors, resolve(dark = false, dynamic = false, amoled = true, sdk = 34))

    @Test fun reduce_motion_only_when_scale_is_zero() {
        assertTrue(isReduceMotion(0f))
        assertFalse(isReduceMotion(1f))
        assertFalse(isReduceMotion(0.5f))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*EmberbyteThemeTest"`
Expected: FAIL (unresolved reference `EmberLightColors`).

- [ ] **Step 3: Write the palettes** (generated from seed `#E8590C`, TonalSpot; do not hand-edit)

`EmberbyteColors.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val EmberLightColors = lightColorScheme(
    primary = Color(0xFF8B5037),
    onPrimary = Color(0xFFFFF7F5),
    primaryContainer = Color(0xFFFFB193),
    onPrimaryContainer = Color(0xFF63301A),
    inversePrimary = Color(0xFFFFB193),
    secondary = Color(0xFF77584C),
    onSecondary = Color(0xFFFFF7F5),
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF694B3F),
    tertiary = Color(0xFF725C25),
    onTertiary = Color(0xFFFFF8F0),
    tertiaryContainer = Color(0xFFFBDC98),
    onTertiaryContainer = Color(0xFF624D17),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF3E2F2A),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF3E2F2A),
    surfaceVariant = Color(0xFFF6DDD5),
    onSurfaceVariant = Color(0xFF6D5B55),
    inverseSurface = Color(0xFF130D0A),
    inverseOnSurface = Color(0xFFA79A96),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    outline = Color(0xFF8A7770),
    outlineVariant = Color(0xFFC3ADA6),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFEDD5CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFEEAE3),
    surfaceContainerHigh = Color(0xFFF9E4DC),
    surfaceContainerHighest = Color(0xFFF6DDD5),
)

internal val EmberDarkColors = darkColorScheme(
    primary = Color(0xFFF8B89F),
    onPrimary = Color(0xFF603523),
    primaryContainer = Color(0xFF754633),
    onPrimaryContainer = Color(0xFFFFDCCF),
    inversePrimary = Color(0xFF84533F),
    secondary = Color(0xFFE7BEAF),
    onSecondary = Color(0xFF553A2F),
    secondaryContainer = Color(0xFF50352A),
    onSecondaryContainer = Color(0xFFDFB7A8),
    tertiary = Color(0xFFFFECC6),
    onTertiary = Color(0xFF6B561F),
    tertiaryContainer = Color(0xFFFBDC98),
    onTertiaryContainer = Color(0xFF624D17),
    background = Color(0xFF130D0A),
    onBackground = Color(0xFFF9E0D8),
    surface = Color(0xFF130D0A),
    onSurface = Color(0xFFF9E0D8),
    surfaceVariant = Color(0xFF30231E),
    onSurfaceVariant = Color(0xFFBCA79F),
    inverseSurface = Color(0xFFFFF8F6),
    inverseOnSurface = Color(0xFF5D534F),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF84716B),
    outlineVariant = Color(0xFF54443E),
    surfaceBright = Color(0xFF372924),
    surfaceDim = Color(0xFF130D0A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1A110E),
    surfaceContainer = Color(0xFF211713),
    surfaceContainerHigh = Color(0xFF281D19),
    surfaceContainerHighest = Color(0xFF30231E),
)
```

- [ ] **Step 4: Write the theme**

`EmberbyteTheme.kt`:
```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val DYNAMIC_COLOR_MIN_SDK = 31

/** True when the user turned system animations off. Springs become snaps and fades. */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

internal fun isReduceMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

internal fun resolveColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    amoledBlack: Boolean,
    sdkInt: Int,
    dynamicLight: () -> ColorScheme,
    dynamicDark: () -> ColorScheme,
): ColorScheme {
    val base = when {
        dynamicColor && sdkInt >= DYNAMIC_COLOR_MIN_SDK -> if (darkTheme) dynamicDark() else dynamicLight()
        darkTheme -> EmberDarkColors
        else -> EmberLightColors
    }
    return if (darkTheme && amoledBlack) {
        base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    } else {
        base
    }
}

@Composable
fun EmberbyteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = resolveColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        amoledBlack = amoledBlack,
        sdkInt = Build.VERSION.SDK_INT,
        dynamicLight = { dynamicLightColorScheme(context) },
        dynamicDark = { dynamicDarkColorScheme(context) },
    )
    val reduceMotion = remember(context) {
        isReduceMotion(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*EmberbyteThemeTest"`
Expected: PASS (8 tests). If `MaterialExpressiveTheme` or `MotionScheme.expressive()` is not found, check the Material 3 1.4.0 API (https://developer.android.com/jetpack/androidx/releases/compose-material3) and use the equivalent; keep the `@file:OptIn` only if the compiler still requires it.

- [ ] **Step 6: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add Emberbyte theme with dynamic colour and reduce-motion

The ember palette is the fallback below Android 12, so the app has an
identity even without Material You."
```

---

### Task 6: MorphingNumber

**Files:**
- Create: `ui/design/src/main/res/font/roboto_flex.ttf` (downloaded)
- Create: `licenses/roboto-flex-OFL.txt` (downloaded)
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/number/MorphingNumber.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/number/NumberAxesTest.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/number/MorphingNumberTest.kt`

**Interfaces:**
- Consumes: `formatBytes`, `ByteUnits` (Task 4), `EmberbyteTheme`, `LocalReduceMotion` (Task 5).
- Produces: `@Composable fun MorphingNumber(bytes: Long, throughputBps: Long, units: ByteUnits, contentDescription: String, modifier: Modifier = Modifier, animate: Boolean = true)`, plus internal `throughputIntensity(bps: Long): Float`, `axesFor(intensity: Float): NumberAxes`, `data class NumberAxes(val weight: Float, val width: Float)`.
- Behaviour (frontend spec §4.1): throughput is mapped on a log scale between 10 KB/s (rest) and 20 MB/s (max) to `t ∈ [0,1]`; `wght = 520 + 380·t`, `wdth = 100 + 25·t`; text size never changes; digits roll with the default spatial spring.

- [ ] **Step 1: Download Roboto Flex (OFL) and its licence**

```bash
mkdir -p ui/design/src/main/res/font licenses
curl -fsSL -o ui/design/src/main/res/font/roboto_flex.ttf \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/robotoflex/RobotoFlex%5BGRAD%2CXOPQ%2CXTRA%2CYOPQ%2CYTAS%2CYTDE%2CYTFI%2CYTLC%2CYTUC%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
curl -fsSL -o licenses/roboto-flex-OFL.txt https://raw.githubusercontent.com/google/fonts/main/ofl/robotoflex/OFL.txt
ls -l ui/design/src/main/res/font/roboto_flex.ttf licenses/roboto-flex-OFL.txt
```
Expected: the font is about 1,787,292 bytes and the licence about 4,488 bytes.

- [ ] **Step 2: Write the failing axis test**

`NumberAxesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberAxesTest {
    @Test fun zero_throughput_is_at_rest() {
        assertEquals(0f, throughputIntensity(0L), 0f)
        val axes = axesFor(throughputIntensity(0L))
        assertEquals(520f, axes.weight, 0.001f)
        assertEquals(100f, axes.width, 0.001f)
    }

    @Test fun rest_threshold_is_still_at_rest() =
        assertEquals(0f, throughputIntensity(10_000L), 0f)

    @Test fun max_throughput_is_full_intensity() {
        val axes = axesFor(throughputIntensity(20_000_000L))
        assertEquals(900f, axes.weight, 0.001f)
        assertEquals(125f, axes.width, 0.001f)
    }

    @Test fun above_max_is_clamped() =
        assertEquals(1f, throughputIntensity(500_000_000L), 0f)

    @Test fun geometric_midpoint_is_half_intensity() =
        assertEquals(0.5f, throughputIntensity(447_214L), 0.01f)

    @Test fun intensity_increases_with_throughput() {
        val values = listOf(50_000L, 200_000L, 1_000_000L, 5_000_000L).map(::throughputIntensity)
        assertTrue(values.zipWithNext().all { (a, b) -> a < b })
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*NumberAxesTest"`
Expected: FAIL (unresolved reference `throughputIntensity`).

- [ ] **Step 4: Implement MorphingNumber**

`MorphingNumber.kt`:
```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.khaledbahaaeldin.emberbyte.ui.design.R
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion
import kotlin.math.ln
import kotlin.math.roundToInt

internal const val REST_BPS = 10_000L
internal const val MAX_BPS = 20_000_000L
private const val WEIGHT_REST = 520f
private const val WEIGHT_MAX = 900f
private const val WIDTH_REST = 100f
private const val WIDTH_MAX = 125f
private const val AXIS_STEPS = 32

internal data class NumberAxes(val weight: Float, val width: Float)

/** 0 at or below 10 KB/s, 1 at or above 20 MB/s, log-scaled in between. */
internal fun throughputIntensity(bps: Long): Float {
    if (bps <= REST_BPS) return 0f
    val t = (ln(bps.toDouble()) - ln(REST_BPS.toDouble())) /
        (ln(MAX_BPS.toDouble()) - ln(REST_BPS.toDouble()))
    return t.coerceIn(0.0, 1.0).toFloat()
}

internal fun axesFor(intensity: Float): NumberAxes = NumberAxes(
    weight = WEIGHT_REST + (WEIGHT_MAX - WEIGHT_REST) * intensity,
    width = WIDTH_REST + (WIDTH_MAX - WIDTH_REST) * intensity,
)

private fun robotoFlexFamily(axes: NumberAxes): FontFamily {
    val weight = axes.weight.roundToInt()
    return FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(axes.width),
            ),
        ),
    )
}

@Composable
fun MorphingNumber(
    bytes: Long,
    throughputBps: Long,
    units: ByteUnits,
    contentDescription: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val live = animate && !LocalReduceMotion.current
    val formatted = remember(bytes, units) { formatBytes(bytes, units) }
    val motion = MaterialTheme.motionScheme

    val intensity by animateFloatAsState(
        targetValue = if (live) throughputIntensity(throughputBps) else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "numberIntensity",
    )
    val step = (intensity * AXIS_STEPS).roundToInt()
    val family = remember(step) { robotoFlexFamily(axesFor(step / AXIS_STEPS.toFloat())) }
    val rollSpatial = motion.defaultSpatialSpec<IntOffset>()
    val rollEffects = motion.defaultEffectsSpec<Float>()

    Column(modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }) {
        Row {
            val text = formatted.value
            text.forEachIndexed { index, char ->
                // Keyed from the right so existing digits keep their identity when the length changes.
                key(text.length - index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            if (live) {
                                (slideInVertically(rollSpatial) { it } + fadeIn(rollEffects)) togetherWith
                                    (slideOutVertically(rollSpatial) { -it } + fadeOut(rollEffects))
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "digit",
                    ) { digit ->
                        Text(
                            text = digit.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = family,
                                fontSize = 72.sp,
                                lineHeight = 72.sp,
                                letterSpacing = (-2).sp,
                            ),
                        )
                    }
                }
            }
        }
        Text(
            text = formatted.unit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
```

- [ ] **Step 5: Run the axis test to verify it passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*NumberAxesTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Write the Compose semantics test and run it**

`MorphingNumberTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorphingNumberTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun exposes_one_node_with_the_plain_language_description() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                MorphingNumber(
                    bytes = 1_240_000_000L,
                    throughputBps = 0L,
                    units = ByteUnits.DECIMAL,
                    contentDescription = "1.24 gigabytes used today",
                    animate = false,
                )
            }
        }
        rule.onNodeWithContentDescription("1.24 gigabytes used today").assertIsDisplayed()
    }
}
```

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*MorphingNumberTest"`
Expected: PASS. If Robolectric cannot load the variable font resource, add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` to the class (import `org.robolectric.annotation.GraphicsMode`) and re-run.

- [ ] **Step 7: Commit**

```bash
git add ui/design licenses
git commit -m "feat(ui): add MorphingNumber driven by live throughput

The hero figure is the product, so its weight and width carry the traffic
signal without ever changing its size."
```

---

### Task 7: Floating pill navbar

**Files:**
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarLogic.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarVisibility.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/FloatingPillNavBar.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/NavBarLogicTest.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/FloatingPillNavBarTest.kt`

**Interfaces:**
- Consumes: `EmberbyteTheme`, `LocalReduceMotion` (Task 5).
- Produces: `data class NavBarItem(val id: String, val label: String, val icon: ImageVector, val accent: NavAccent)`, `enum class NavAccent { Primary, Secondary, Tertiary, Error }`, `@Composable fun FloatingPillNavBar(items: List<NavBarItem>, selectedId: String, onSelect: (String) -> Unit, visible: Boolean, modifier: Modifier = Modifier, hapticsEnabled: Boolean = true)`, `@Stable class NavBarVisibility { val visible: Boolean; val connection: NestedScrollConnection }`, `@Composable fun rememberNavBarVisibility(threshold: Dp = 40.dp): NavBarVisibility`, internal `indexAt(x, width, count)` and `ScrollVisibilityTracker`.
- Behaviour (frontend spec §4.2): 64 dp floating pill, selected item expands to icon + label in its accent container colour, drag to preview and snap on release with a haptic tick, hides after 40 dp of downward scroll and shows on any upward scroll, reduce-motion = no morph or slide.
- Design credit: Floating-Navbar-M3-Flutter (MIT). The implementation here is original.

- [ ] **Step 1: Write the failing logic test**

`NavBarLogicTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarLogicTest {
    @Test fun indexAt_splits_the_bar_into_equal_slots() {
        assertEquals(0, indexAt(x = 10f, width = 400f, count = 4))
        assertEquals(1, indexAt(x = 150f, width = 400f, count = 4))
        assertEquals(2, indexAt(x = 250f, width = 400f, count = 4))
        assertEquals(3, indexAt(x = 399f, width = 400f, count = 4))
    }

    @Test fun indexAt_clamps_outside_the_bar() {
        assertEquals(0, indexAt(x = -50f, width = 400f, count = 4))
        assertEquals(3, indexAt(x = 900f, width = 400f, count = 4))
    }

    @Test fun indexAt_handles_degenerate_input() {
        assertEquals(0, indexAt(x = 10f, width = 0f, count = 4))
        assertEquals(0, indexAt(x = 10f, width = 400f, count = 0))
    }

    @Test fun tracker_starts_visible() =
        assertTrue(ScrollVisibilityTracker(thresholdPx = 40f).visible)

    @Test fun small_downward_scroll_keeps_the_bar_visible() {
        val tracker = ScrollVisibilityTracker(40f)
        assertTrue(tracker.onScroll(downPx = 30f))
    }

    @Test fun accumulated_downward_scroll_past_threshold_hides_the_bar() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(30f)
        assertFalse(tracker.onScroll(20f))
    }

    @Test fun one_big_downward_scroll_hides_the_bar() =
        assertFalse(ScrollVisibilityTracker(40f).onScroll(41f))

    @Test fun any_upward_scroll_shows_the_bar_again() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(100f)
        assertTrue(tracker.onScroll(-1f))
    }

    @Test fun upward_scroll_resets_the_accumulated_distance() {
        val tracker = ScrollVisibilityTracker(40f)
        tracker.onScroll(35f)
        tracker.onScroll(-5f)
        assertTrue(tracker.onScroll(35f))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*NavBarLogicTest"`
Expected: FAIL (unresolved reference `indexAt`).

- [ ] **Step 3: Implement the logic and the visibility holder**

`NavBarLogic.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

/** Index of the equal-width slot containing [x]; clamped to the valid range. */
internal fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 0 || width <= 0f) return 0
    return ((x / width) * count).toInt().coerceIn(0, count - 1)
}

/**
 * Hides after [thresholdPx] of accumulated downward scroll; any upward scroll shows again.
 * `downPx` is positive when the user scrolls the content down (finger moves up).
 */
internal class ScrollVisibilityTracker(private val thresholdPx: Float) {
    var visible: Boolean = true
        private set
    private var accumulatedDown = 0f

    fun onScroll(downPx: Float): Boolean {
        when {
            downPx < 0f -> {
                accumulatedDown = 0f
                visible = true
            }
            downPx > 0f -> {
                accumulatedDown += downPx
                if (accumulatedDown > thresholdPx) visible = false
            }
        }
        return visible
    }
}
```

`NavBarVisibility.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class NavBarVisibility internal constructor(thresholdPx: Float) {
    private val tracker = ScrollVisibilityTracker(thresholdPx)

    var visible: Boolean by mutableStateOf(true)
        private set

    /** Attach with `Modifier.nestedScroll(connection)` on an ancestor of the scrolling content. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            visible = tracker.onScroll(downPx = -available.y)
            return Offset.Zero
        }
    }
}

@Composable
fun rememberNavBarVisibility(threshold: Dp = 40.dp): NavBarVisibility {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { NavBarVisibility(thresholdPx) }
}
```

- [ ] **Step 4: Run to verify the logic passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*NavBarLogicTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Write the failing composable test**

`FloatingPillNavBarTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class FloatingPillNavBarTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavBarItem("home", "Home", Icons.Rounded.Home, NavAccent.Primary),
        NavBarItem("apps", "Apps", Icons.Rounded.Apps, NavAccent.Tertiary),
        NavBarItem("plans", "Plans", Icons.Rounded.AccountBalanceWallet, NavAccent.Secondary),
        NavBarItem("lens", "Lens", Icons.Rounded.Visibility, NavAccent.Error),
    )

    private fun show(selectedId: String, visible: Boolean = true, onSelect: (String) -> Unit = {}) {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                FloatingPillNavBar(items, selectedId, onSelect, visible)
            }
        }
    }

    @Test fun selected_item_shows_its_label_and_others_do_not() {
        show("home")
        rule.onNodeWithTag("pill_nav_bar").assertIsDisplayed()
        rule.onNodeWithTag("nav_home").assertIsSelected()
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText("Apps").assertDoesNotExist()
    }

    @Test fun tapping_an_item_reports_its_id() {
        var picked: String? = null
        show("home") { picked = it }
        rule.onNodeWithTag("nav_apps").performClick()
        assertEquals("apps", picked)
    }

    @Test fun hidden_bar_is_not_composed() {
        show("home", visible = false)
        rule.onNodeWithTag("pill_nav_bar").assertDoesNotExist()
    }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*FloatingPillNavBarTest"`
Expected: FAIL (unresolved reference `NavBarItem`).

- [ ] **Step 7: Implement the composable**

`FloatingPillNavBar.kt`:
```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion

enum class NavAccent { Primary, Secondary, Tertiary, Error }

data class NavBarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: NavAccent,
)

@Composable
fun FloatingPillNavBar(
    items: List<NavBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val shownIndex = previewIndex ?: selectedIndex

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) {
            fadeIn(tween(150))
        } else {
            slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { it } + fadeIn(motion.defaultEffectsSpec())
        },
        exit = if (reduceMotion) {
            fadeOut(tween(150))
        } else {
            slideOutVertically(motion.defaultSpatialSpec<IntOffset>()) { it } + fadeOut(motion.defaultEffectsSpec())
        },
    ) {
        Surface(
            modifier = Modifier
                .testTag("pill_nav_bar")
                .semantics { contentDescription = "Navigation" }
                .height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(items, selectedIndex, hapticsEnabled) {
                        val width = { size.width.toFloat() }
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> previewIndex = indexAt(offset.x, width(), items.size) },
                            onDragEnd = {
                                previewIndex?.let { if (it != selectedIndex) onSelect(items[it].id) }
                                previewIndex = null
                            },
                            onDragCancel = { previewIndex = null },
                            onHorizontalDrag = { change, _ ->
                                val index = indexAt(change.position.x, width(), items.size)
                                if (index != previewIndex) {
                                    previewIndex = index
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavItem(
                        item = item,
                        selected = index == shownIndex,
                        onClick = { onSelect(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(item: NavBarItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = item.accent.colors(scheme)
    val reduceMotion = LocalReduceMotion.current
    val motion = MaterialTheme.motionScheme

    val background by animateColorAsState(
        targetValue = if (selected) container else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else motion.fastEffectsSpec(),
        label = "navItemBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) content else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else motion.fastEffectsSpec(),
        label = "navItemForeground",
    )

    Row(
        modifier = Modifier
            .testTag("nav_${item.id}")
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .animateContentSize(if (reduceMotion) snap<IntSize>() else motion.fastSpatialSpec<IntSize>())
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = item.icon, contentDescription = null, tint = foreground)
        if (selected) {
            Text(
                text = item.label,
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

private fun NavAccent.colors(scheme: ColorScheme): Pair<Color, Color> = when (this) {
    NavAccent.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
    NavAccent.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
    NavAccent.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    NavAccent.Error -> scheme.errorContainer to scheme.onErrorContainer
}
```

- [ ] **Step 8: Run to verify everything passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*navbar*"`
Expected: PASS (12 tests). If `HapticFeedbackType.SegmentTick` is missing in this Compose version, use `HapticFeedbackType.TextHandleMove`.

- [ ] **Step 9: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add floating pill navbar with drag-to-snap and hide on scroll

Recreated in Compose from the Floating-Navbar-M3-Flutter design (MIT) so
navigation feels as expressive as the rest of the app."
```

---

### Task 8: Glass backdrop blur for the navbar

**Files:**
- Modify: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/FloatingPillNavBar.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/navbar/GlassSource.kt`

**Interfaces:**
- Consumes: Haze 2.0.1 (`libs.haze`, `libs.haze.blur`, already declared in Task 1).
- Produces: `FloatingPillNavBar(…, glass: GlassSource? = null)` (new last parameter), `@Composable fun rememberGlassSource(): GlassSource`, `fun Modifier.glassSource(source: GlassSource): Modifier`. With `glass == null` the bar renders exactly as in Task 7 (tint-only fallback).

This task is isolated on purpose: the navbar already works without it. Haze 2.0 changed its API, so confirm names against the README before coding: https://github.com/chrisbanes/haze (the README documents `rememberHazeState()`, `Modifier.hazeSource(state)` and `Modifier.hazeBlur(input = HazeInput.Sources(state), style = HazeBlurStyle { blurRadius(…) })`).

- [ ] **Step 1: Wrap Haze behind a tiny seam** so the rest of the code never imports Haze

`GlassSource.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Marks content that the navbar should blur behind itself. Wraps Haze so callers never import it. */
@Stable
class GlassSource internal constructor(internal val state: HazeState)

@Composable
fun rememberGlassSource(): GlassSource {
    val state = rememberHazeState()
    return androidx.compose.runtime.remember(state) { GlassSource(state) }
}

fun Modifier.glassSource(source: GlassSource): Modifier = hazeSource(source.state)
```
Imports of `HazeState`, `hazeSource` and `rememberHazeState` come from the base `haze` artifact; if the compiler cannot resolve one, look it up in the Haze README or the library's sources and fix the import only.

- [ ] **Step 2: Add the blur to the bar**

In `FloatingPillNavBar.kt`: add the parameter `glass: GlassSource? = null` after `hapticsEnabled`, and apply the blur to the `Surface` modifier chain before `.height(64.dp)`:

```kotlin
// imports
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.shape.CircleShape

// inside Surface(modifier = ...)
modifier = Modifier
    .testTag("pill_nav_bar")
    .semantics { contentDescription = "Navigation" }
    .then(
        if (glass != null) {
            Modifier
                .clip(CircleShape)
                .hazeBlur(
                    input = HazeInput.Sources(glass.state),
                    style = HazeBlurStyle { blurRadius(24.dp) },
                )
        } else {
            Modifier
        },
    )
    .height(64.dp),
```
Keep the translucent `color` (alpha 0.92) as the tint: it is also the fallback on Android 10–11, where real blur is unavailable.

- [ ] **Step 3: Build and re-run the navbar tests**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*navbar*" :ui:design:compileDebugKotlin`
Expected: PASS and `BUILD SUCCESSFUL` (no `glass` is passed in the tests, so behaviour is unchanged). If the Haze imports or `hazeBlur` signature do not compile, adjust them to the pinned version's README and keep the seam; if it cannot be made to work in reasonable time, leave `glass` unused (the bar stays tint-only), record this in the commit message and in `README.md` as a known gap.

- [ ] **Step 4: Commit**

```bash
git add ui/design
git commit -m "feat(ui): blur the content behind the navbar with Haze

Compose has no stable backdrop-blur API; the seam keeps Haze out of callers
and the tint-only bar remains the fallback."
```

---

### Task 9: Bento tiles and supporting components

**Files:**
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/model/UiModels.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/BentoTile.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/Tiles.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/UsageBarRow.kt`
- Create: `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/AppRow.kt`
- Test: `ui/design/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/TilesTest.kt`

**Interfaces:**
- Consumes: `formatBytes`, `ByteUnits` (Task 4), `EmberbyteTheme` (Task 5).
- Produces (package `…ui.design.model`): `enum class NetworkKindUi { Mobile, Wifi }`, `data class ForecastUi(val headline: String, val detail: String, val confidence: String)`, `data class AppRowUi(val packageName: String, val label: String, val mobileBytes: Long, val wifiBytes: Long) { val totalBytes }`, `data class BarUi(val label: String, val bytes: Long, val description: String)`.
- Produces (package `…ui.design.tiles`): `enum class TileContainer { Default, Primary, Tertiary }`, `BentoTile(title, modifier, container, onClick, content)`, `SpeedTile(rxBps, txBps, network, units, modifier)`, `ForecastTile(model, modifier)`, `UsageBarRow(points, selectedIndex, onSelect, modifier)`, `AppRow(model, units, onClick, modifier)`, `EstimatedBadge(modifier)`.

- [ ] **Step 1: Write the UI models**

`UiModels.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.model

enum class NetworkKindUi { Mobile, Wifi }

data class ForecastUi(val headline: String, val detail: String, val confidence: String)

data class AppRowUi(
    val packageName: String,
    val label: String,
    val mobileBytes: Long,
    val wifiBytes: Long,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

/** [description] is the spoken form, e.g. "Wednesday, 2.10 gigabytes". */
data class BarUi(val label: String, val bytes: Long, val description: String)
```

- [ ] **Step 2: Write the failing test**

`TilesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TilesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun speedTile_shows_rate_and_network() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SpeedTile(rxBps = 4_200_000L, txBps = 350_000L, network = NetworkKindUi.Wifi, units = ByteUnits.DECIMAL)
            }
        }
        rule.onNodeWithText("4.20 MB/s", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Wi-Fi", substring = true).assertIsDisplayed()
    }

    @Test fun speedTile_says_no_connection_when_offline() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                SpeedTile(rxBps = 0L, txBps = 0L, network = null, units = ByteUnits.DECIMAL)
            }
        }
        rule.onNodeWithText("No connection").assertIsDisplayed()
        rule.onNodeWithText("Offline", substring = true).assertIsDisplayed()
    }

    @Test fun forecastTile_shows_headline_and_detail() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                ForecastTile(ForecastUi(headline = "Thu", detail = "runs out (±1 day)", confidence = "Medium confidence"))
            }
        }
        rule.onNodeWithText("Thu").assertIsDisplayed()
        rule.onNodeWithText("runs out (±1 day)").assertIsDisplayed()
        rule.onNodeWithText("Medium confidence").assertIsDisplayed()
    }

    @Test fun usageBarRow_reports_the_tapped_bar() {
        var picked = -1
        val bars = listOf(
            BarUi("Mon", 1_000_000_000L, "Monday, 1.00 gigabytes"),
            BarUi("Tue", 2_000_000_000L, "Tuesday, 2.00 gigabytes"),
        )
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                UsageBarRow(bars, selectedIndex = null, onSelect = { picked = it })
            }
        }
        rule.onNodeWithText("Tue").performClick()
        assertEquals(1, picked)
    }

    @Test fun appRow_shows_name_and_total() {
        rule.setContent {
            EmberbyteTheme(darkTheme = true, dynamicColor = false) {
                AppRow(AppRowUi("com.yt", "YouTube", 610_000_000L, 0L), ByteUnits.DECIMAL, onClick = {})
            }
        }
        rule.onNodeWithText("YouTube").assertIsDisplayed()
        rule.onNodeWithText("610 MB").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*TilesTest"`
Expected: FAIL (unresolved reference `SpeedTile`).

- [ ] **Step 4: Implement the tiles**

`BentoTile.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TileContainer { Default, Primary, Tertiary }

@Composable
fun BentoTile(
    title: String,
    modifier: Modifier = Modifier,
    container: TileContainer = TileContainer.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground) = when (container) {
        TileContainer.Default -> scheme.surfaceContainerHigh to scheme.onSurface
        TileContainer.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        TileContainer.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = foreground.copy(alpha = 0.72f))
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = background,
            contentColor = foreground,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = background,
            contentColor = foreground,
            content = body,
        )
    }
}
```

`Tiles.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi

@Composable
fun SpeedTile(
    rxBps: Long,
    txBps: Long,
    network: NetworkKindUi?,
    units: ByteUnits,
    modifier: Modifier = Modifier,
) {
    val networkLabel = when (network) {
        NetworkKindUi.Wifi -> "Wi-Fi"
        NetworkKindUi.Mobile -> "Mobile"
        null -> "Offline"
    }
    val down = formatBytes(rxBps, units)
    val up = formatBytes(txBps, units)
    BentoTile(title = "Live · $networkLabel", modifier = modifier.fillMaxHeight()) {
        if (network == null) {
            Text("No connection", style = MaterialTheme.typography.headlineSmall)
        } else {
            Text("↓ ${down.value} ${down.unit}/s", style = MaterialTheme.typography.headlineSmall)
            Text(
                "↑ ${up.value} ${up.unit}/s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ForecastTile(model: ForecastUi, modifier: Modifier = Modifier) {
    BentoTile(title = "Forecast", modifier = modifier.fillMaxHeight(), container = TileContainer.Tertiary) {
        Text(model.headline, style = MaterialTheme.typography.headlineLarge)
        Text(model.detail, style = MaterialTheme.typography.bodyMedium)
        Text(
            model.confidence,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun EstimatedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            "Estimated",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
```

`UsageBarRow.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi

@Composable
fun UsageBarRow(
    points: List<BarUi>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = (points.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEachIndexed { index, bar ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics {
                        contentDescription = bar.description
                        selected = isSelected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + 56 * (bar.bytes.toFloat() / max)).dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

`AppRow.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi

@Composable
fun AppRow(
    model: AppRowUi,
    units: ByteUnits,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = formatBytes(model.totalBytes, units)
    val mobileShare = if (model.totalBytes > 0) model.mobileBytes.toFloat() / model.totalBytes else 0f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                model.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(model.label, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
                if (mobileShare > 0f) {
                    Box(Modifier.weight(mobileShare).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
                if (mobileShare < 1f) {
                    Box(Modifier.weight(1f - mobileShare).height(4.dp).background(MaterialTheme.colorScheme.tertiary))
                }
            }
        }
        Text("${total.value} ${total.unit}", style = MaterialTheme.typography.labelLarge)
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :ui:design:testDebugUnitTest --tests "*TilesTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add ui/design
git commit -m "feat(ui): add Bento tiles, usage bar row and app row

The Home screen is built from these pieces, and they stay free of any
domain types."
```

---

### Task 10: Repository interfaces and fakes in `:core:data`

**Files:**
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/UsageRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/PlanRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/SettingsRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/PermissionRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeTraffic.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeUsageRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakePlanRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeSettingsRepository.kt`
- Create: `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakePermissionRepository.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeTrafficTest.kt`
- Test: `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/fake/FakeRepositoriesTest.kt`

**Interfaces:**
- Consumes: all types from Task 3.
- Produces (package `…emberbyte.data`), exactly as contract §3: `UsageRepository`, `PlanRepository`, `UnitSystem`, `Settings`, `SettingsRepository`, `PermissionState`, `PermissionRepository`.
- Produces (package `…data.fake`): `object FakeTraffic { fun rxBpsAt(elapsedMs: Long): Long; fun txBpsAt(elapsedMs: Long): Long }`, `class FakeUsageRepository(clock: Clock = Clock.systemUTC(), tick: Flow<Unit> = oneSecondTicker())`, `class FakePlanRepository(clock: Clock = Clock.systemUTC())`, `class FakeSettingsRepository`, `class FakePermissionRepository`.
- Alert, budget, spike, export and backup interfaces from the contract are declared in the milestone that implements them.

- [ ] **Step 1: Write the interfaces** (contract §3, verbatim signatures)

`UsageRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeLiveSpeed(): Flow<LiveSpeed>
    fun observeToday(filter: UsageFilter = UsageFilter()): Flow<DayUsage>
    fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter = UsageFilter()): Flow<List<UsagePoint>>
    fun observeApps(range: DateRange, filter: UsageFilter = UsageFilter(), sort: AppSort = AppSort.BYTES_DESC): Flow<List<AppUsage>>
    fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>>
    fun observeCoverage(): Flow<CoverageStatus>
    suspend fun refreshNow(): Outcome<Unit>
}
```

`PlanRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observePlans(includeArchived: Boolean = false): Flow<List<Plan>>
    fun observePlanState(planId: Long): Flow<PlanState?>
    fun observeActivePlanStates(): Flow<List<PlanState>>
    fun observeForecast(planId: Long): Flow<Forecast?>
    fun observeAddOns(planId: Long): Flow<List<AddOn>>
    fun observeFreeRules(planId: Long): Flow<List<FreeRule>>
    suspend fun upsertPlan(plan: Plan): Outcome<Long>
    suspend fun archivePlan(planId: Long): Outcome<Unit>
    suspend fun upsertAddOn(addOn: AddOn): Outcome<Long>
    suspend fun deleteAddOn(id: Long): Outcome<Unit>
    suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long>
    suspend fun deleteFreeRule(id: Long): Outcome<Unit>

    /** Forecast with extra daily usage added from [from] onward. Not persisted. */
    suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast>
}
```

`SettingsRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.flow.Flow

enum class UnitSystem { DECIMAL, BINARY }

data class Settings(
    val liveNotificationEnabled: Boolean = true,
    val notificationShowsSpeed: Boolean = true,
    val spikeAlertsEnabled: Boolean = true,
    val amoledBlack: Boolean = false,
    val useDynamicColor: Boolean = true,
    val unitSystem: UnitSystem = UnitSystem.DECIMAL,
    val lensHistoryHours: Int = 24,
    val hapticsEnabled: Boolean = true,
)

interface SettingsRepository {
    fun observe(): Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings): Outcome<Unit>
}
```

`PermissionRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data

import kotlinx.coroutines.flow.Flow

data class PermissionState(
    val usageAccess: Boolean,
    val notifications: Boolean,
    val phoneState: Boolean,
    val vpnConsentGranted: Boolean,
)

interface PermissionRepository {
    fun observe(): Flow<PermissionState>
    suspend fun recheck()
}
```

- [ ] **Step 2: Write the failing traffic test**

`FakeTrafficTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTrafficTest {
    @Test fun is_deterministic() =
        assertEquals(FakeTraffic.rxBpsAt(12_345L), FakeTraffic.rxBpsAt(12_345L))

    @Test fun stays_within_plausible_bounds() {
        for (ms in 0L..200_000L step 1_000L) {
            val rx = FakeTraffic.rxBpsAt(ms)
            assertTrue("rx=$rx at $ms", rx in 0L..9_500_000L)
        }
    }

    @Test fun burst_windows_are_much_faster_than_quiet_ones() =
        assertTrue(FakeTraffic.rxBpsAt(25_000L) > FakeTraffic.rxBpsAt(5_000L) * 2)

    @Test fun upload_is_a_fraction_of_download() {
        val ms = 25_000L
        assertEquals(FakeTraffic.rxBpsAt(ms) / 12, FakeTraffic.txBpsAt(ms))
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*FakeTrafficTest"`
Expected: FAIL (unresolved reference `FakeTraffic`).

- [ ] **Step 4: Implement FakeTraffic**

`FakeTraffic.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import kotlin.math.sin

/** Deterministic pretend traffic: a gentle baseline plus a burst in every third 20-second block. */
object FakeTraffic {
    fun rxBpsAt(elapsedMs: Long): Long {
        val t = elapsedMs / 1000.0
        val base = 600_000.0 + 400_000.0 * sin(t / 5.0)
        val burst = if ((t.toLong() / 20) % 3 == 1L) 8_000_000.0 * (0.5 + 0.5 * sin(t)) else 0.0
        return (base + burst).toLong().coerceAtLeast(0L)
    }

    fun txBpsAt(elapsedMs: Long): Long = rxBpsAt(elapsedMs) / 12
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*FakeTrafficTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Write the failing repository test**

`FakeRepositoriesTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

    @Test fun today_totals_1_24_gigabytes() = runTest {
        val today = FakeUsageRepository(clock).observeToday().first()
        assertEquals(1_240_000_000L, today.totalBytes)
    }

    @Test fun series_has_seven_daily_points() = runTest {
        val range = io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange(clock.instant(), clock.instant())
        val series = FakeUsageRepository(clock)
            .observeSeries(range, io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity.DAY).first()
        assertEquals(7, series.size)
    }

    @Test fun apps_are_sorted_largest_first() = runTest {
        val range = io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange(clock.instant(), clock.instant())
        val apps = FakeUsageRepository(clock).observeApps(range).first()
        assertTrue(apps.zipWithNext().all { (a, b) -> a.totalBytes >= b.totalBytes })
        assertEquals("YouTube", apps.first().label)
    }

    @Test fun live_speed_emits_once_per_tick() = runTest(UnconfinedTestDispatcher()) {
        val tick = MutableSharedFlow<Unit>()
        val repo = FakeUsageRepository(clock, tick)
        val seen = mutableListOf<Long>()
        val job = launch { repo.observeLiveSpeed().collect { seen += it.rxBps } }
        tick.emit(Unit)
        tick.emit(Unit)
        job.cancel()
        assertEquals(2, seen.size)
    }

    @Test fun live_speed_is_on_mobile_with_a_known_network() = runTest(UnconfinedTestDispatcher()) {
        val tick = MutableSharedFlow<Unit>()
        val repo = FakeUsageRepository(clock, tick)
        var network: NetworkKind? = null
        val job = launch { repo.observeLiveSpeed().collect { network = it.network } }
        tick.emit(Unit)
        job.cancel()
        assertEquals(NetworkKind.MOBILE, network)
    }

    @Test fun seeded_plan_has_3_8_gigabytes_left() = runTest {
        val state = FakePlanRepository(clock).observeActivePlanStates().first().single()
        assertEquals(3_800_000_000L, state.remainingBytes)
        assertEquals(10_000_000_000L, state.effectiveCapBytes)
    }

    @Test fun forecast_exists_for_the_seeded_plan() = runTest {
        val repo = FakePlanRepository(clock)
        val planId = repo.observePlans().first().single().id
        assertNotNull(repo.observeForecast(planId).first())
    }

    @Test fun unknown_plan_has_no_state_or_forecast() = runTest {
        val repo = FakePlanRepository(clock)
        assertNull(repo.observePlanState(999L).first())
        assertNull(repo.observeForecast(999L).first())
    }

    @Test fun upserting_a_new_plan_assigns_an_id() = runTest {
        val repo = FakePlanRepository(clock)
        val result = repo.upsertPlan(
            Plan(0, "Travel", null, 5_000_000_000L, Cycle.EveryNDays(30, java.time.LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None),
        )
        assertTrue(result is Outcome.Success)
        assertEquals(2, repo.observePlans().first().size)
    }

    @Test fun archiving_hides_a_plan_from_the_active_list() = runTest {
        val repo = FakePlanRepository(clock)
        val id = repo.observePlans().first().single().id
        repo.archivePlan(id)
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertEquals(1, repo.observePlans(includeArchived = true).first().size)
    }

    @Test fun settings_update_is_observed() = runTest {
        val repo = FakeSettingsRepository()
        repo.update { it.copy(unitSystem = UnitSystem.BINARY, amoledBlack = true) }
        val settings = repo.observe().first()
        assertEquals(UnitSystem.BINARY, settings.unitSystem)
        assertTrue(settings.amoledBlack)
    }

    @Test fun permissions_are_all_granted_in_the_fake() = runTest {
        val state = FakePermissionRepository().observe().first()
        assertTrue(state.usageAccess && state.notifications && state.phoneState)
    }
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*FakeRepositoriesTest"`
Expected: FAIL (unresolved reference `FakeUsageRepository`).

- [ ] **Step 8: Implement the fakes**

`FakeUsageRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun oneSecondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

/** Canned, deterministic usage for M1: 1.24 GB today and a pretend week. */
class FakeUsageRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val tick: Flow<Unit> = oneSecondTicker(),
) : UsageRepository {

    override fun observeLiveSpeed(): Flow<LiveSpeed> {
        val startMs = clock.millis()
        return tick.map {
            val elapsed = clock.millis() - startMs
            LiveSpeed(
                rxBps = FakeTraffic.rxBpsAt(elapsed),
                txBps = FakeTraffic.txBpsAt(elapsed),
                network = NetworkKind.MOBILE,
                at = clock.instant(),
            )
        }
    }

    override fun observeToday(filter: UsageFilter): Flow<DayUsage> =
        flowOf(DayUsage(LocalDate.now(clock), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L))

    override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> =
        flowOf(weekSeries())

    override fun observeApps(range: DateRange, filter: UsageFilter, sort: AppSort): Flow<List<AppUsage>> =
        flowOf(apps.sortedByDescending { it.totalBytes })

    override fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>> =
        flowOf(weekSeries())

    override fun observeCoverage(): Flow<CoverageStatus> =
        flowOf(CoverageStatus(gaps = emptyList(), lastSampleAt = clock.instant()))

    override suspend fun refreshNow(): Outcome<Unit> = Outcome.Success(Unit)

    private fun weekSeries(): List<UsagePoint> {
        val dayBytes = listOf(0.9e9, 1.4e9, 0.7e9, 2.1e9, 1.1e9, 1.6e9, 1.24e9)
        val today = LocalDate.now(clock)
        return dayBytes.mapIndexed { index, total ->
            val day = today.minusDays((dayBytes.size - 1 - index).toLong())
            UsagePoint(
                start = day.atStartOfDay(clock.zone).toInstant(),
                mobileBytes = (total * 0.75).toLong(),
                wifiBytes = (total * 0.25).toLong(),
            )
        }
    }

    private val apps = listOf(
        AppUsage("com.google.android.youtube", "YouTube", 10_101, 610_000_000L, 0L, null),
        AppUsage("com.android.chrome", "Chrome", 10_102, 212_000_000L, 0L, null),
        AppUsage("com.google.android.apps.maps", "Maps", 10_103, 96_000_000L, 0L, null),
        AppUsage("com.spotify.music", "Spotify", 10_104, 71_000_000L, 0L, null),
        AppUsage("org.telegram.messenger", "Telegram", 10_105, 33_000_000L, 0L, null),
    )
}
```
`FakePlanRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** One seeded plan ("Main SIM", 10 GB, 3.8 GB left) with canned state and forecast. */
class FakePlanRepository(private val clock: Clock = Clock.systemUTC()) : PlanRepository {

    private val plans = MutableStateFlow(
        listOf(
            Plan(
                id = 1L,
                name = "Main SIM",
                subscriptionId = null,
                capBytes = 10_000_000_000L,
                cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, clock.zone),
                rollover = Rollover.None,
            ),
        ),
    )
    private val addOns = MutableStateFlow<List<AddOn>>(emptyList())
    private val freeRules = MutableStateFlow<List<FreeRule>>(emptyList())
    private var nextId = 2L

    override fun observePlans(includeArchived: Boolean): Flow<List<Plan>> =
        plans.map { list -> if (includeArchived) list else list.filter { !it.archived } }

    override fun observePlanState(planId: Long): Flow<PlanState?> =
        plans.map { list -> list.firstOrNull { it.id == planId }?.let(::stateFor) }

    override fun observeActivePlanStates(): Flow<List<PlanState>> =
        plans.map { list -> list.filter { !it.archived }.map(::stateFor) }

    override fun observeForecast(planId: Long): Flow<Forecast?> =
        plans.map { list -> if (list.any { it.id == planId }) forecastFor(planId) else null }

    override fun observeAddOns(planId: Long): Flow<List<AddOn>> =
        addOns.map { list -> list.filter { it.planId == planId } }

    override fun observeFreeRules(planId: Long): Flow<List<FreeRule>> =
        freeRules.map { list -> list.filter { it.planId == planId } }

    override suspend fun upsertPlan(plan: Plan): Outcome<Long> {
        val id = if (plan.id == 0L) nextId++ else plan.id
        plans.update { list -> list.filter { it.id != id } + plan.copy(id = id) }
        return Outcome.Success(id)
    }

    override suspend fun archivePlan(planId: Long): Outcome<Unit> {
        if (plans.value.none { it.id == planId }) return Outcome.Failure(EmberbyteError.NotFound)
        plans.update { list -> list.map { if (it.id == planId) it.copy(archived = true) else it } }
        return Outcome.Success(Unit)
    }

    override suspend fun upsertAddOn(addOn: AddOn): Outcome<Long> {
        val id = if (addOn.id == 0L) nextId++ else addOn.id
        addOns.update { list -> list.filter { it.id != id } + addOn.copy(id = id) }
        return Outcome.Success(id)
    }

    override suspend fun deleteAddOn(id: Long): Outcome<Unit> {
        addOns.update { list -> list.filter { it.id != id } }
        return Outcome.Success(Unit)
    }

    override suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long> {
        val id = if (rule.id == 0L) nextId++ else rule.id
        freeRules.update { list -> list.filter { it.id != id } + rule.copy(id = id) }
        return Outcome.Success(id)
    }

    override suspend fun deleteFreeRule(id: Long): Outcome<Unit> {
        freeRules.update { list -> list.filter { it.id != id } }
        return Outcome.Success(Unit)
    }

    override suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast> =
        if (plans.value.any { it.id == planId }) Outcome.Success(forecastFor(planId))
        else Outcome.Failure(EmberbyteError.NotFound)

    private fun stateFor(plan: Plan): PlanState {
        val now = clock.instant()
        val used = 6_200_000_000L
        return PlanState(
            plan = plan,
            window = CycleWindow(now.minus(21, ChronoUnit.DAYS), now.plus(9, ChronoUnit.DAYS)),
            effectiveCapBytes = plan.capBytes,
            usedBytes = used,
            remainingBytes = plan.capBytes - used,
            rolledOverBytes = 0L,
            addOnBytes = 0L,
            freeBytes = 0L,
            daysLeft = 9,
            fractionUsed = used.toFloat() / plan.capBytes,
            isApproximate = false,
        )
    }

    private fun forecastFor(planId: Long): Forecast {
        val now = clock.instant()
        return Forecast(
            planId = planId,
            runOutEarliest = now.plus(2, ChronoUnit.DAYS),
            runOutExpected = now.plus(3, ChronoUnit.DAYS),
            runOutLatest = now.plus(4, ChronoUnit.DAYS),
            projectedCycleEndBytes = 13_100_000_000L,
            confidence = Confidence.MEDIUM,
            historyDays = 14,
        )
    }
}
```

`FakeSettingsRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Settings> = state

    override suspend fun update(transform: (Settings) -> Settings): Outcome<Unit> {
        state.update(transform)
        return Outcome.Success(Unit)
    }
}
```

`FakePermissionRepository.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePermissionRepository : PermissionRepository {
    private val state = MutableStateFlow(
        PermissionState(usageAccess = true, notifications = true, phoneState = true, vpnConsentGranted = false),
    )

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() = Unit
}
```

- [ ] **Step 9: Run to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS (16 tests). If `live_speed_emits_once_per_tick` hangs, the test dispatcher is not unconfined: confirm `runTest(UnconfinedTestDispatcher())` is used as written.

- [ ] **Step 10: Commit**

```bash
git add core/data
git commit -m "feat(data): add repository interfaces and in-memory fakes

Lets the UI be built and tested end to end before any real sampling exists."
```

---

### Task 11: Home screen, ViewModel and app shell

**Files:**
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeUiState.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeMapping.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeViewModel.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeScreen.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/Destination.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/placeholder/PlaceholderScreen.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/AppGraph.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/EmberbyteApplication.kt`
- Create: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/EmberbyteApp.kt`
- Modify: `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:name=".EmberbyteApplication"`)
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeMappingTest.kt`
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 3–10.
- Produces: `data class HomeUiState`, `sealed interface HomeEvent`, `internal fun buildHomeUiState(...)`, `internal fun forecastToUi(...)`, `class HomeViewModel`, `class AppGraph`, `enum class Destination`.

- [ ] **Step 1: Write the UI state**

`HomeUiState.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi

data class HomeUiState(
    val heroLabel: String = "Today",
    val heroBytes: Long = 0L,
    val heroDescription: String = "",
    val isToday: Boolean = true,
    val subtitle: String? = null,
    val isEstimated: Boolean = false,
    val throughputBps: Long = 0L,
    val liveRxBps: Long = 0L,
    val liveTxBps: Long = 0L,
    val network: NetworkKindUi? = null,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val forecast: ForecastUi? = null,
    val topApps: List<AppRowUi> = emptyList(),
    val week: List<BarUi> = emptyList(),
    val selectedDay: Int? = null,
    val units: ByteUnits = ByteUnits.DECIMAL,
)

sealed interface HomeEvent {
    /** Tapping the selected bar again returns to "Today". */
    data class SelectDay(val index: Int) : HomeEvent
}
```

- [ ] **Step 2: Write the failing mapping test**

`HomeMappingTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z") // a Monday
    private val zone = ZoneOffset.UTC
    private val locale = Locale.ENGLISH

    private fun forecast(earliest: Long?, expected: Long?, latest: Long?, confidence: Confidence = Confidence.MEDIUM) =
        Forecast(
            planId = 1,
            runOutEarliest = earliest?.let { now.plusSeconds(it * 86_400) },
            runOutExpected = expected?.let { now.plusSeconds(it * 86_400) },
            runOutLatest = latest?.let { now.plusSeconds(it * 86_400) },
            projectedCycleEndBytes = 0,
            confidence = confidence,
            historyDays = 14,
        )

    private val plan = Plan(1, "Main SIM", null, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, zone), Rollover.None)
    private val planState = PlanState(
        plan = plan,
        window = CycleWindow(now, now),
        effectiveCapBytes = 10_000_000_000L,
        usedBytes = 6_200_000_000L,
        remainingBytes = 3_800_000_000L,
        rolledOverBytes = 0,
        addOnBytes = 0,
        freeBytes = 0,
        daysLeft = 9,
        fractionUsed = 0.62f,
        isApproximate = false,
    )
    private val today = DayUsage(LocalDate.of(2026, 10, 5), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L)
    private val week = (0..6).map { i ->
        UsagePoint(
            start = now.minusSeconds((6 - i) * 86_400L),
            mobileBytes = (i + 1) * 100_000_000L,
            wifiBytes = 0L,
        )
    }

    private fun build(
        live: LiveSpeed? = null,
        plan: PlanState? = planState,
        forecast: Forecast? = forecast(2, 3, 4),
        apps: List<AppUsage> = emptyList(),
        selectedDay: Int? = null,
        approximate: Boolean = false,
    ) = buildHomeUiState(
        today = today,
        live = live,
        planState = plan?.copy(isApproximate = approximate),
        forecast = forecast,
        apps = apps,
        week = week,
        units = UnitSystem.DECIMAL,
        selectedDay = selectedDay,
        now = now,
        zone = zone,
        locale = locale,
    )

    // ---- forecastToUi ----

    @Test fun forecast_with_a_range_shows_weekday_and_plus_minus_days() {
        val ui = forecastToUi(forecast(2, 3, 4), now, zone, locale)
        assertEquals(ForecastUi("Thu", "runs out (±1 day)", "Medium confidence"), ui)
    }

    @Test fun forecast_uses_the_wider_side_of_the_range() {
        val ui = forecastToUi(forecast(1, 3, 6), now, zone, locale)
        assertEquals("runs out (±3 days)", ui.detail)
    }

    @Test fun forecast_without_a_range_has_no_plus_minus() {
        val ui = forecastToUi(forecast(3, 3, 3), now, zone, locale)
        assertEquals("runs out", ui.detail)
    }

    @Test fun forecast_that_never_runs_out_says_so() {
        val ui = forecastToUi(forecast(null, null, null, Confidence.HIGH), now, zone, locale)
        assertEquals(ForecastUi("Safe", "Won't run out this cycle", "High confidence"), ui)
    }

    @Test fun missing_forecast_is_null() = assertNull(forecastToUi(null, now, zone, locale))

    // ---- buildHomeUiState ----

    @Test fun hero_defaults_to_today() {
        val ui = build()
        assertTrue(ui.isToday)
        assertEquals("Today", ui.heroLabel)
        assertEquals(1_240_000_000L, ui.heroBytes)
    }

    @Test fun hero_description_is_plain_language_with_remaining_data() {
        assertEquals("1.24 gigabytes used today, 3.80 gigabytes left", build().heroDescription)
    }

    @Test fun subtitle_combines_remaining_data_and_forecast() {
        assertEquals("3.80 GB left · runs out Thu (±1 day)", build().subtitle)
    }

    @Test fun subtitle_for_a_safe_plan() {
        val ui = build(forecast = forecast(null, null, null))
        assertEquals("3.80 GB left · won't run out this cycle", ui.subtitle)
    }

    @Test fun no_plan_means_no_subtitle_and_no_remaining_in_the_description() {
        val ui = build(plan = null, forecast = null)
        assertNull(ui.subtitle)
        assertEquals("1.24 gigabytes used today", ui.heroDescription)
    }

    @Test fun approximate_plan_is_flagged() {
        assertTrue(build(approximate = true).isEstimated)
        assertFalse(build(approximate = false).isEstimated)
    }

    @Test fun live_speed_fills_the_speed_fields() {
        val ui = build(live = LiveSpeed(4_200_000L, 350_000L, NetworkKind.WIFI, now))
        assertEquals(4_200_000L, ui.liveRxBps)
        assertEquals(350_000L, ui.liveTxBps)
        assertEquals(4_550_000L, ui.throughputBps)
        assertEquals(NetworkKindUi.Wifi, ui.network)
    }

    @Test fun no_live_speed_means_offline_and_no_throughput() {
        val ui = build(live = null)
        assertEquals(0L, ui.throughputBps)
        assertNull(ui.network)
    }

    // The fixture week runs Tue 2026-09-29 .. Mon 2026-10-05, so index 2 is Thursday 2026-10-01.
    @Test fun week_has_seven_bars_labelled_by_weekday() {
        val ui = build()
        assertEquals(7, ui.week.size)
        assertEquals("Tue", ui.week.first().label)
        assertEquals("Mon", ui.week.last().label)
        assertEquals("Tuesday, 100 megabytes", ui.week.first().description)
    }

    @Test fun selecting_a_day_switches_the_hero_to_that_day() {
        val ui = build(selectedDay = 2)
        assertFalse(ui.isToday)
        assertEquals(300_000_000L, ui.heroBytes)
        assertEquals("Thursday", ui.heroLabel)
        assertEquals(2, ui.selectedDay)
        assertEquals("300 megabytes used on Thursday", ui.heroDescription)
        assertNull(ui.subtitle)
    }

    @Test fun out_of_range_selection_falls_back_to_today() {
        val ui = build(selectedDay = 99)
        assertTrue(ui.isToday)
        assertNull(ui.selectedDay)
    }

    @Test fun top_apps_are_the_three_largest() {
        val apps = listOf(
            AppUsage("a", "A", 1, 10, 0, null),
            AppUsage("b", "B", 2, 400, 0, null),
            AppUsage("c", "C", 3, 300, 0, null),
            AppUsage("d", "D", 4, 200, 0, null),
        )
        assertEquals(listOf("B", "C", "D"), build(apps = apps).topApps.map { it.label })
    }

    @Test fun binary_units_are_mapped() {
        val ui = buildHomeUiState(today, null, null, null, emptyList(), week, UnitSystem.BINARY, null, now, zone, locale)
        assertEquals(ByteUnits.BINARY, ui.units)
    }
}
```
- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeMappingTest"`
Expected: FAIL (unresolved reference `buildHomeUiState`).

- [ ] **Step 4: Implement the mapping**

`HomeMapping.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.spokenUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal fun UnitSystem.toByteUnits(): ByteUnits = when (this) {
    UnitSystem.DECIMAL -> ByteUnits.DECIMAL
    UnitSystem.BINARY -> ByteUnits.BINARY
}

private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
    Confidence.LOW -> "Low confidence"
    Confidence.MEDIUM -> "Medium confidence"
    Confidence.HIGH -> "High confidence"
}

private fun daysBetween(a: Instant, b: Instant): Long = abs(a.epochSecond - b.epochSecond) / 86_400

private fun Forecast.neverRunsOut(): Boolean =
    runOutEarliest == null && runOutExpected == null && runOutLatest == null

@Suppress("UNUSED_PARAMETER")
internal fun forecastToUi(forecast: Forecast?, now: Instant, zone: ZoneId, locale: Locale): ForecastUi? {
    if (forecast == null) return null
    if (forecast.neverRunsOut()) {
        return ForecastUi("Safe", "Won't run out this cycle", confidenceLabel(forecast.confidence))
    }
    val expected = forecast.runOutExpected
    val at = expected ?: forecast.runOutEarliest ?: forecast.runOutLatest!!
    val weekday = at.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val spread = max(
        forecast.runOutEarliest?.let { daysBetween(at, it) } ?: 0L,
        forecast.runOutLatest?.let { daysBetween(at, it) } ?: 0L,
    )
    val detail = when (spread) {
        0L -> "runs out"
        1L -> "runs out (±1 day)"
        else -> "runs out (±$spread days)"
    }
    return ForecastUi(weekday, detail, confidenceLabel(forecast.confidence))
}

private fun spoken(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${spokenUnit(f.unit)}"
}

private fun plain(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${f.unit}"
}

internal fun buildHomeUiState(
    today: DayUsage,
    live: LiveSpeed?,
    planState: PlanState?,
    forecast: Forecast?,
    apps: List<AppUsage>,
    week: List<UsagePoint>,
    units: UnitSystem,
    selectedDay: Int?,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): HomeUiState {
    val byteUnits = units.toByteUnits()
    val validSelection = selectedDay?.takeIf { it in week.indices }

    val bars = week.map { point ->
        val day = point.start.atZone(zone).dayOfWeek
        BarUi(
            label = day.getDisplayName(TextStyle.SHORT, locale),
            bytes = point.totalBytes,
            description = "${day.getDisplayName(TextStyle.FULL, locale)}, ${spoken(point.totalBytes, byteUnits)}",
        )
    }

    val forecastUi = forecastToUi(forecast, now, zone, locale)
    val isToday = validSelection == null

    val heroBytes: Long
    val heroLabel: String
    val description: String
    val subtitle: String?
    if (validSelection == null) {
        heroBytes = today.totalBytes
        heroLabel = "Today"
        description = buildString {
            append("${spoken(heroBytes, byteUnits)} used today")
            if (planState != null) append(", ${spoken(planState.remainingBytes, byteUnits)} left")
        }
        subtitle = planState?.let {
            val left = "${plain(it.remainingBytes, byteUnits)} left"
            when {
                forecastUi == null -> left
                forecast?.neverRunsOut() == true -> "$left · won't run out this cycle"
                // "runs out (±1 day)" + "Thu" -> "runs out Thu (±1 day)"
                else -> "$left · ${forecastUi.detail.replace("runs out", "runs out ${forecastUi.headline}")}"
            }
        }
    } else {
        val point = week[validSelection]
        heroBytes = point.totalBytes
        heroLabel = point.start.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        description = "${spoken(heroBytes, byteUnits)} used on $heroLabel"
        subtitle = null
    }

    return HomeUiState(
        heroLabel = heroLabel,
        heroBytes = heroBytes,
        heroDescription = description,
        isToday = isToday,
        subtitle = subtitle,
        isEstimated = planState?.isApproximate ?: false,
        throughputBps = (live?.rxBps ?: 0L) + (live?.txBps ?: 0L),
        liveRxBps = live?.rxBps ?: 0L,
        liveTxBps = live?.txBps ?: 0L,
        network = when (live?.network) {
            NetworkKind.MOBILE -> NetworkKindUi.Mobile
            NetworkKind.WIFI -> NetworkKindUi.Wifi
            null -> null
        },
        mobileBytes = today.mobileBytes,
        wifiBytes = today.wifiBytes,
        forecast = forecastUi,
        topApps = apps.sortedByDescending { it.totalBytes }.take(3)
            .map { AppRowUi(it.packageName, it.label, it.mobileBytes, it.wifiBytes) },
        week = bars,
        selectedDay = validSelection,
        units = byteUnits,
    )
}
```
- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeMappingTest"`
Expected: PASS (18 tests).

- [ ] **Step 6: Write the failing ViewModel test**

`HomeViewModelTest.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        tick: MutableSharedFlow<Unit> = MutableSharedFlow(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = HomeViewModel(FakeUsageRepository(clock, tick), FakePlanRepository(clock), settings, clock, Locale.ENGLISH)

    @Test fun emits_today_total_plan_and_forecast() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1_240_000_000L, state.heroBytes)
        assertEquals("3.80 GB left · runs out Thu (±1 day)", state.subtitle)
        assertEquals(7, state.week.size)
        assertEquals(3, state.topApps.size)
        job.cancel()
    }

    @Test fun live_speed_updates_throughput() = runTest(dispatcher) {
        val tick = MutableSharedFlow<Unit>()
        val vm = viewModel(tick)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.throughputBps)
        tick.emit(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.throughputBps > 0L)
        job.cancel()
    }

    @Test fun selecting_a_day_switches_the_hero_and_selecting_it_again_returns_to_today() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onEvent(HomeEvent.SelectDay(1))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isToday)
        assertEquals(1, vm.uiState.value.selectedDay)

        vm.onEvent(HomeEvent.SelectDay(1))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isToday)
        job.cancel()
    }

    @Test fun unit_setting_is_applied() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()
        assertEquals(io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits.BINARY, vm.uiState.value.units)
        job.cancel()
    }
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest"`
Expected: FAIL (unresolved reference `HomeViewModel`).

- [ ] **Step 8: Implement the ViewModel**

`HomeViewModel.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    usage: UsageRepository,
    plans: PlanRepository,
    settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val selectedDay = MutableStateFlow<Int?>(null)

    private val live: Flow<LiveSpeed?> = usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }

    private val planAndForecast: Flow<Pair<PlanState?, Forecast?>> =
        plans.observeActivePlanStates().flatMapLatest { states ->
            val state = states.firstOrNull()
            if (state == null) flowOf<Pair<PlanState?, Forecast?>>(null to null)
            else plans.observeForecast(state.plan.id).map { state to it }
        }

    // The ranges are fixed when the ViewModel is created; M2 recomputes them when the date changes.
    private val data: Flow<HomeData> = run {
        val zone = clock.zone
        val todayDate = LocalDate.now(clock)
        val todayRange = DateRange(todayDate.atStartOfDay(zone).toInstant(), clock.instant())
        val weekRange = DateRange(todayDate.minusDays(6).atStartOfDay(zone).toInstant(), clock.instant())
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

    val uiState: StateFlow<HomeUiState> = combine(data, settings.observe(), selectedDay) { d, s, selected ->
        buildHomeUiState(
            today = d.today,
            live = d.live,
            planState = d.planState,
            forecast = d.forecast,
            apps = d.apps,
            week = d.week,
            units = s.unitSystem,
            selectedDay = selected,
            now = clock.instant(),
            zone = clock.zone,
            locale = locale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectDay -> selectedDay.update { current -> if (current == event.index) null else event.index }
        }
    }
}
```

- [ ] **Step 9: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (22 tests). If `emits_today_total_plan_and_forecast` fails on the weekday (`Thu`), confirm the test's `Clock` is the fixed Monday 2026-10-05 and the fake forecast expects `now + 3 days`.

- [ ] **Step 10: Write the navigation, placeholders and graph**

`nav/Destination.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.NavAccent
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.NavBarItem

enum class Destination(val id: String, val route: String, private val label: String, private val accent: NavAccent) {
    Home("home", "home", "Home", NavAccent.Primary),
    Apps("apps", "apps", "Apps", NavAccent.Tertiary),
    Plans("plans", "plans", "Plans", NavAccent.Secondary),
    Lens("lens", "lens", "Lens", NavAccent.Error),
    ;

    fun toNavBarItem(): NavBarItem = NavBarItem(
        id = id,
        label = label,
        accent = accent,
        icon = when (this) {
            Home -> Icons.Rounded.Home
            Apps -> Icons.Rounded.Apps
            Plans -> Icons.Rounded.AccountBalanceWallet
            Lens -> Icons.Rounded.Visibility
        },
    )

    companion object {
        fun fromRoute(route: String?): Destination = entries.firstOrNull { it.route == route } ?: Home
        fun fromId(id: String): Destination = entries.first { it.id == id }
    }
}
```

`placeholder/PlaceholderScreen.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

`AppGraph.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.home.HomeViewModel

/** Hand-written dependency graph. Hilt replaces this in M2 when real services need injection. */
class AppGraph {
    val usage: UsageRepository = FakeUsageRepository()
    val plans: PlanRepository = FakePlanRepository()
    val settings: SettingsRepository = FakeSettingsRepository()
    val permissions: PermissionRepository = FakePermissionRepository()

    fun homeViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(usage, plans, settings) }
    }
}
```

`EmberbyteApplication.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.app.Application

class EmberbyteApplication : Application() {
    val graph: AppGraph by lazy { AppGraph() }
}
```

- [ ] **Step 11: Write the Home screen**

`home/HomeScreen.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.number.MorphingNumber
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.AppRow
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.EstimatedBadge
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.ForecastTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.SpeedTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow

@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun HomeContent(state: HomeUiState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                state.heroLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        item {
            BentoTile(title = "Top apps today", modifier = Modifier.fillMaxWidth()) {
                state.topApps.forEach { app -> AppRow(app, state.units, onClick = {}) }
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

- [ ] **Step 12: Write the app shell and activity**

`EmberbyteApp.kt`:
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.khaledbahaaeldin.emberbyte.home.HomeScreen
import io.github.khaledbahaaeldin.emberbyte.nav.Destination
import io.github.khaledbahaaeldin.emberbyte.placeholder.PlaceholderScreen
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.FloatingPillNavBar
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.glassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberGlassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberNavBarVisibility

@Composable
fun EmberbyteApp(graph: AppGraph, hapticsEnabled: Boolean = true) {
    val navController = rememberNavController()
    val visibility = rememberNavBarVisibility()
    val glass = rememberGlassSource()
    val backStack by navController.currentBackStackEntryAsState()
    val selected = Destination.fromRoute(backStack?.destination?.route)
    val items = remember { Destination.entries.map { it.toNavBarItem() } }

    Box(Modifier.fillMaxSize().nestedScroll(visibility.connection)) {
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.fillMaxSize().glassSource(glass),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(viewModel(factory = graph.homeViewModelFactory()))
            }
            composable(Destination.Apps.route) {
                PlaceholderScreen("Apps", "Per-app usage arrives in the next milestone.")
            }
            composable(Destination.Plans.route) {
                PlaceholderScreen("Plans", "The flexible plan editor arrives in milestone 3.")
            }
            composable(Destination.Lens.route) {
                PlaceholderScreen("Lens", "Live Lens is opt-in and arrives in milestone 5.")
            }
        }
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
```
If Task 8 left the navbar tint-only (the `glass` parameter does not exist), delete the two glass lines (`.glassSource(glass)` and `glass = glass,`) and the `rememberGlassSource` import.

`MainActivity.kt` (replace):
```kotlin
package io.github.khaledbahaaeldin.emberbyte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.EmberbyteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as EmberbyteApplication).graph
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
}
```

Manifest: add `android:name=".EmberbyteApplication"` as the first attribute of `<application>`.

- [ ] **Step 13: Build and run all tests**

Run: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL`. Fix any lint errors (not warnings) that appear.

- [ ] **Step 14: Commit**

```bash
git add app
git commit -m "feat(app): add Home screen, ViewModel and navigation shell

Wires the fake data, the morphing Number, Bento tiles and the pill navbar
into one runnable screen so M1 can be judged on a device."
```

---

### Task 12: README and attribution

**Files:**
- Create: `README.md`
- Create: `NOTICE`

- [ ] **Step 1: Write the README**

`README.md`:
```markdown
# Emberbyte

An open-source Android data monitor with a Material 3 Expressive interface: daily and real-time usage,
flexible data plans, a forecast of when your cap runs out, and an opt-in live per-app view.

> Status: milestone M1 (foundation). The app runs on fake data. Real measurement arrives in M2.
> "DataKB" is the project codename; the repository keeps that name.

## Privacy

Everything stays on your device. There are no accounts, no analytics and no cloud. The optional
**Live Lens** feature (milestone 5) uses a local-only VPN to show which app is using data right now; it is
off unless you turn it on, and nothing it sees leaves the phone.

## Build

Requirements: JDK 17 and the Android SDK (platform 36, build tools 36.0.0).

    ./gradlew assembleDebug
    ./gradlew testDebugUnitTest :core:engine:test

Create `local.properties` with `sdk.dir=<path to your Android SDK>` (it is git-ignored).

## Documentation

Design and specs live in `docs/superpowers/specs/`; implementation plans in `docs/superpowers/plans/`.

## Licence

GPL-3.0. See `LICENSE`. Third-party credits are in `NOTICE`.
```

- [ ] **Step 2: Write NOTICE**

`NOTICE`:
```text
Emberbyte
Copyright (C) 2026 Khaled Bahaaeldin
Licensed under the GNU General Public License v3.0 (see LICENSE).

Third-party material
--------------------

Floating pill navigation bar (design reference)
  Floating-Navbar-M3-Flutter by Damantha126 (MIT License)
  https://github.com/Damantha126/Floating-Navbar-M3-Flutter
  The Compose implementation in this project is original; the visual design and behaviour are inspired
  by the reference above.

Roboto Flex (variable font)
  Copyright 2017 The Roboto Flex Project Authors
  SIL Open Font License 1.1 (see licenses/roboto-flex-OFL.txt)
  https://github.com/googlefonts/roboto-flex

Haze (backdrop blur)
  Chris Banes (Apache License 2.0)
  https://github.com/chrisbanes/haze

AndroidX, Jetpack Compose, Material 3 and Kotlin libraries
  Copyright The Android Open Source Project / JetBrains (Apache License 2.0)
```
If the copyright holder line is wrong for you, change it before committing.

- [ ] **Step 3: Commit**

```bash
git add README.md NOTICE
git commit -m "docs: add README and third-party notices

The navbar design reference and the OFL font require visible credit."
```

---

### Task 13: Final verification on a device

**Files:** none (verification only; screenshots go to the scratchpad, not the repo).

- [ ] **Step 1: Clean build and the full check**

Run: `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL`, all tests green. Record the test counts per module in your report.

- [ ] **Step 2: Start the emulator** (the machine has the AVD `Violet_API_36`)

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/emulator/emulator.exe" -avd Violet_API_36 -no-snapshot-save &
"$SDK/platform-tools/adb.exe" wait-for-device
"$SDK/platform-tools/adb.exe" shell getprop sys.boot_completed
```
Expected: `1` once booted (re-run until it is).

- [ ] **Step 3: Install and launch**

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
"$SDK/platform-tools/adb.exe" shell am start -n io.github.khaledbahaaeldin.emberbyte/.MainActivity
"$SDK/platform-tools/adb.exe" exec-out screencap -p > "$TEMP/emberbyte-home.png"
```
Expected: the app launches; read the screenshot to confirm it is not blank.

- [ ] **Step 4: Manual checklist** (report each item as pass/fail, with the screenshot)

  1. Home shows "Today", the large number `1.24` with `GB` beneath, and the subtitle `3.80 GB left · runs out … (±1 day)`.
  2. Every few seconds the number visibly gets bolder and wider (fake bursts), then relaxes. Its size never changes.
  3. Speed tile shows `↓ … MB/s`, Forecast tile shows a weekday, the network and top-apps tiles are present.
  4. Tapping a week bar switches the hero label to that weekday and the number to that day; tapping it again returns to "Today".
  5. The pill navbar shows the Home pill expanded with its label. Tapping Apps morphs the pill; Apps, Plans and Lens show their placeholder text.
  6. Dragging along the bar previews other tabs and snaps on release.
  7. Scrolling Home down hides the bar; scrolling up brings it back.
  8. Light and dark system themes both look correct (toggle with `adb shell cmd uimode night yes` / `no`).
  9. With system animations off (`adb shell settings put global animator_duration_scale 0`), the number does not react and the bar does not slide; restore with `… 1`.
  10. On this API 36 emulator the dynamic (wallpaper) colours are used; the ember palette appears when `Settings.useDynamicColor` is false (verify by temporarily setting the default to false, then revert).

- [ ] **Step 5: Report and ask before pushing**

Summarise results (tests, build, checklist). **Do not push.** Ask the user to confirm, then:

```bash
git status -sb
git push
gh run list --limit 1
```
and watch the CI run (`gh run watch`) to confirm it passes on GitHub.
