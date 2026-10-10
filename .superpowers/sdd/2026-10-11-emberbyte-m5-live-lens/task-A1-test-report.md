# Task A1 Test Report: Deterministic Alert Lock Test & MainActivity onNewIntent Link Tests

## Verdict
**PASS**

- Critical Issues: 0
- Important Issues: 0
- Minor Issues: 0

---

## Spec Findings

Commit range verified: `287f9c9..HEAD` on branch `m5-live-lens` (commit `7946cb4d1a885113ce8649c2e920e76f0dc25e77`).
Author: `Omar Amin <omarsadiq2012@gmail.com>`.
Commit message trailer: `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.

All requirements from `task-A1-brief.md` were checked and verified:
1. **Step 1: The lock test of `AlertEvaluator` is deterministic**:
   - Gated dependency using `CompletableDeferred` (`firstStarted`, `gate`, `secondStarted`).
   - Bounded wait with `withTimeout(2_000)` polling every 5ms over a 200ms settle window checking `maxConcurrent.get() > 1`.
   - Asserts `maxConcurrent == 1` and notice count == 1 with threshold 50.
   - Tested under mutation (removing `mutex.withLock` from `AlertEvaluator.kt`): failed deterministically at `assertEquals(1, maxConcurrent.get())` (`expected:<1> but was:<2>`).
2. **Step 2: MainActivity onNewIntent coverage**:
   - Added `MainActivityNewIntentTest` using Robolectric (`sdk = [35]`).
   - Tests onboarding not completed -> `activity.intent.data == null`.
   - Tests onboarding completed -> `activity.intent.data == Uri.parse("emberbyte://settings")`.
   - Tested under mutation (reverting `setIntent(launchIntentFor(...))` to `setIntent(intent)` in `MainActivity.kt`): `onNewIntent_drops_deep_link_when_onboarding_not_completed` failed (`expected null, but was:<emberbyte://settings>`).
   - Replaced vacuous assertion in `ColdStartLinkTest` by passing `sanitizedIntent` through `nav.handleDeepLink(sanitizedIntent)` and asserting false and destination remains `ONBOARDING`.
3. **Step 3: Gate execution**:
   - Full gate `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` passed cleanly.
4. **Step 4: Commit**:
   - Properly authored and formatted commit `7946cb4`.

---

## Run Results (with Quoted Output)

### 1. AlertEvaluatorTest Alone
Command:
```bash
.\gradlew :core:data:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest
```
Output:
```
> Task :core:data:testDebugUnitTest

BUILD SUCCESSFUL in 12s
34 actionable tasks: 1 executed, 33 up-to-date
```

### 2. Mutation Test on AlertEvaluator (Removing `mutex.withLock`)
Command:
```bash
.\gradlew :core:data:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest.concurrent_evaluate_calls_return_exactly_one_notice --rerun-tasks
```
Output:
```
> Task :core:data:testDebugUnitTest FAILED

AlertEvaluatorTest > concurrent_evaluate_calls_return_exactly_one_notice FAILED
    java.lang.AssertionError at Assert.java:89

1 test completed, 1 failed

FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':core:data:testDebugUnitTest'.
> There were failing tests. See the report at: file:///C:/Fast%20Project/DataKB/core/data/build/reports/tests/testDebugUnitTest/index.html
BUILD FAILED in 47s
```
Failure details from `TEST-io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest.xml`:
```xml
<failure message="java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt;" type="java.lang.AssertionError">
java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
	at org.junit.Assert.assertEquals(Assert.java:146)
	at io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluatorTest$concurrent_evaluate_calls_return_exactly_one_notice$1.invokeSuspend(AlertEvaluatorTest.kt:134)
```

### 3. MainActivityNewIntentTest Alone
Command:
```bash
.\gradlew :app:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.nav.MainActivityNewIntentTest --rerun-tasks
```
Output:
```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 2s
78 actionable tasks: 78 executed
```

### 4. Mutation Test on MainActivity (Reverting `setIntent` fix to `setIntent(intent)`)
Command:
```bash
.\gradlew :app:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.nav.MainActivityNewIntentTest --rerun-tasks
```
Output:
```
> Task :app:testDebugUnitTest

MainActivityNewIntentTest > onNewIntent_drops_deep_link_when_onboarding_not_completed FAILED
    java.lang.AssertionError at MainActivityNewIntentTest.kt:35

2 tests completed, 1 failed

> Task :app:testDebugUnitTest FAILED

BUILD FAILED in 1m 4s
```

### 5. ColdStartLinkTest Alone
Command:
```bash
.\gradlew :app:testDebugUnitTest --tests io.github.khaledbahaaeldin.emberbyte.nav.ColdStartLinkTest
```
Output:
```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 19s
78 actionable tasks: 1 executed, 77 up-to-date
```

### 6. Full Gate Execution
Command:
```bash
.\gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
```
Output:
```
> Task :app:lintDebug
> Task :ui:design:lintDebug
> Task :feature:lens:lintDebug
> Task :core:data:lintDebug
> Task :app:assembleDebug UP-TO-DATE

BUILD SUCCESSFUL in 24s
218 actionable tasks: 5 executed, 1 from cache, 212 up-to-date
```

---

## Test-Quality Findings
- **Real Behavior Tested**:
  - `AlertEvaluatorTest`: Directly intercepts `PlanStore.lastFiredPercent` and asserts concurrency bounds before and after gating. Detects missing locks immediately.
  - `MainActivityNewIntentTest`: Uses Robolectric on the actual `MainActivity` and exercises `onNewIntent` across both onboarding states. Verifies intent data sanitation on incomplete onboarding and preservation on completed onboarding.
  - `ColdStartLinkTest`: Feeds sanitized intent directly into `NavController.handleDeepLink` and asserts non-navigation.
- **Determinism**:
  - `AlertEvaluatorTest` does not use arbitrary sleeps to race threads; it coordinates synchronization via `CompletableDeferred` and checks a bounded settling window.
- **Invariants**:
  - No Java 18+ APIs used.
  - No changes under `docs/superpowers/`.
  - Git working tree is clean.

---

## Issues
- **Critical**: None
- **Important**: None
- **Minor**: None
