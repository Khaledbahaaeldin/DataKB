# Task A1 Report: Deterministic Alert Lock Test & MainActivity onNewIntent Link Tests

## Summary
- **Status:** DONE
- **Commit:** `7946cb4` `test: make the alert lock test deterministic and cover the new-intent fix on the real activity`
- **Author:** `Omar Amin <omarsadiq2012@gmail.com>`
- **Files Modified / Created:**
  - `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/alert/AlertEvaluatorTest.kt`
  - `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/MainActivityNewIntentTest.kt` (new)
  - `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/ColdStartLinkTest.kt`

---

## Step 1: AlertEvaluator Concurrency Lock Test Determinism

### Implementation
In `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/alert/AlertEvaluatorTest.kt`:
Updated `concurrent_evaluate_calls_return_exactly_one_notice` to use `CompletableDeferred` gating:
1. `first` begins evaluation and enters `interceptingStore.lastFiredPercent(...)`, increments `concurrent`, signals `firstStarted.complete(Unit)`, and suspends on `gate.await()`.
2. Test awaits `firstStarted.await()`.
3. Test starts `second` with `secondStarted` `CompletableDeferred` to ensure the second coroutine has actively launched and invoked `e.evaluate()`.
4. Test runs bounded polling loop with `withTimeout(2_000)` checking every 5ms whether `maxConcurrent.get() > 1` (detecting the bug immediately if mutex is missing) until a 200ms settling window passes.
5. In a `try / finally { gate.complete(Unit) }` block, asserts `maxConcurrent == 1`, releases gate, awaits both results, and verifies `maxConcurrent == 1` and exactly one notice of threshold 50.

### Proof of Failure without Mutex
`mutex.withLock` was temporarily removed from `AlertEvaluator.evaluate()` in `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/alert/AlertEvaluator.kt`.

#### 5/5 Alone:
Command: `.\gradlew :core:data:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest.concurrent_evaluate_calls_return_exactly_one_notice --rerun-tasks`
- **Run 1:** FAILED (54s)
  ```
  AlertEvaluatorTest > concurrent_evaluate_calls_return_exactly_one_notice FAILED
      java.lang.AssertionError at Assert.java:89
  1 test completed, 1 failed
  TEST-*.xml: java.lang.AssertionError: expected:<1> but was:<2> at AlertEvaluatorTest.kt:134
  ```
- **Run 2:** FAILED (53s) - `expected:<1> but was:<2> at AlertEvaluatorTest.kt:134`
- **Run 3:** FAILED (52s) - `expected:<1> but was:<2> at AlertEvaluatorTest.kt:134`
- **Run 4:** FAILED (52s) - `expected:<1> but was:<2> at AlertEvaluatorTest.kt:134`
- **Run 5:** FAILED (52s) - `expected:<1> but was:<2> at AlertEvaluatorTest.kt:134`
Result: **5/5 runs failed at `maxConcurrent` assertion (`expected:<1> but was:<2>`)**.

#### 3/3 Full `:core:data:testDebugUnitTest`:
Command: `.\gradlew :core:data:testDebugUnitTest --rerun-tasks`
- **Run 1:** FAILED (2m 44s)
  ```
  AlertEvaluatorTest > concurrent_evaluate_calls_return_exactly_one_notice FAILED
      java.lang.AssertionError at Assert.java:89
  324 tests completed, 1 failed
  TEST-*.xml: java.lang.AssertionError: expected:<1> but was:<2> at AlertEvaluatorTest.kt:134
  ```
- **Run 2:** FAILED (1m 02s) - `324 tests completed, 1 failed` at `maxConcurrent`
- **Run 3:** FAILED (1m 04s) - `324 tests completed, 1 failed` at `maxConcurrent`
Result: **3/3 full test runs failed at `maxConcurrent` assertion (`expected:<1> but was:<2>`)**.

#### 5/5 Green with Mutex Restored:
Mutex was restored in `AlertEvaluator.kt`.
Command: `.\gradlew :core:data:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest.concurrent_evaluate_calls_return_exactly_one_notice --rerun-tasks`
- **Run 1:** BUILD SUCCESSFUL in 52s
- **Run 2:** BUILD SUCCESSFUL in 54s
- **Run 3:** BUILD SUCCESSFUL in 53s
- **Run 4:** BUILD SUCCESSFUL in 49s
- **Run 5:** BUILD SUCCESSFUL in 53s
Result: **5/5 runs green**.

---

## Step 2: MainActivity onNewIntent Link Handling Test

### Implementation
Added `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/MainActivityNewIntentTest.kt` with Robolectric (`sdk = [35]`):
1. `onNewIntent_drops_deep_link_when_onboarding_not_completed`:
   - Builds activity with `Robolectric.buildActivity(MainActivity::class.java).create().start().resume()`.
   - Dispatches `newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings")))`.
   - Asserts `activity.intent.data == null`.
2. `onNewIntent_keeps_deep_link_when_onboarding_completed`:
   - Runs `app.graph.onboarding.complete()` and waits for `onboardingCompleted` flow to emit `true`.
   - Builds activity with `Robolectric.buildActivity(MainActivity::class.java).create().start().resume()`.
   - Dispatches `newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings")))`.
   - Asserts `activity.intent.data == Uri.parse("emberbyte://settings")`.

### Proof of Failure when Reverting `setIntent` Fix
Temporarily reverted `setIntent(launchIntentFor(intent, completed))` in `MainActivity.kt:49` to `setIntent(intent)`.
Command: `.\gradlew :app:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.nav.MainActivityNewIntentTest --rerun-tasks`
Output:
```
MainActivityNewIntentTest > onNewIntent_drops_deep_link_when_onboarding_not_completed FAILED
    java.lang.AssertionError at MainActivityNewIntentTest.kt:35
2 tests completed, 1 failed
TEST-*.xml: java.lang.AssertionError: expected null, but was:<emberbyte://settings>
```
The onboarding not-completed test failed as expected.
Restored `setIntent(launchIntentFor(intent, completed))` in `MainActivity.kt`.
Re-ran `MainActivityNewIntentTest`:
```
BUILD SUCCESSFUL in 1m 11s (2 tests completed, 0 failed)
```

### Vacuous Assertion Fix in ColdStartLinkTest
In `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/ColdStartLinkTest.kt`:
Replaced the vacuous assertion in `launch_intent_carrying_settings_when_onboarding_not_completed_is_dropped_and_finishing_onboarding_is_on_home` by making the test feed the sanitized intent through the navigation route path:
```kotlin
// Feeding sanitized launch intent does not navigate to Settings because data was stripped
assertFalse(nav.handleDeepLink(sanitizedIntent))
assertEquals(Routes.ONBOARDING, nav.currentDestination?.route)
```
Ran `.\gradlew :app:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.nav.ColdStartLinkTest` -> BUILD SUCCESSFUL in 24s.

---

## Step 3: Gate Execution
Command:
```
.\gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
```
Output:
```
BUILD SUCCESSFUL in 1m 34s
218 actionable tasks: 57 executed, 1 from cache, 160 up-to-date
```
- All unit tests across all modules passed.
- `:core:engine:test` passed.
- `lintDebug` across all modules passed with 0 errors.
- `assembleDebug` passed.

---

## Step 4: Git Commit
- `user.name`: `Omar Amin`
- `user.email`: `omarsadiq2012@gmail.com`
- Commit: `7946cb4d1a885113ce8649c2e920e76f0dc25e77`
- Subject: `test: make the alert lock test deterministic and cover the new-intent fix on the real activity`
