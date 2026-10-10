# Task A1 Brief: Two tests that really can fail (= task N1 of M4 fix wave 3)

### Source: Task N1 of `docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md`

**Files (read first):**
- `core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/alert/AlertEvaluatorTest.kt`
- `core/data/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/data/alert/AlertEvaluator.kt`
- `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/MainActivity.kt`
- `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/ColdStartLinkTest.kt`
- New: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/nav/MainActivityNewIntentTest.kt`

**Requirements:**

- [ ] **Step 1: The lock test of `AlertEvaluator` must be deterministic.**
  The final check removed the `Mutex` in a scratch copy: `concurrent_evaluate_calls_return_exactly_one_notice` still PASSED in 2 of 3 full `:core:data` runs (it failed 5/5 alone, only at the notice count, never at the `maxConcurrent` assertions), while `BudgetEvaluatorTest.parallel_evaluations_post_a_transition_once` failed 8/8. Make the Alert test as strict as the Budget test: gate the dependency the first call waits on with a `CompletableDeferred` that the test completes only AFTER the second call has started, then wait (bounded, `withTimeout(2_000)` polling every 5 ms, never a fixed sleep) until either the second call has entered the dependency (the bug) or a short settling window has passed, and assert the number of concurrent entries stayed at 1 (`maxConcurrent == 1`) and exactly one notice was produced. Prove it: remove the `Mutex` locally (do not commit that), run the test 5 times alone and `./gradlew :core:data:testDebugUnitTest` 3 times; it must fail every time, and the failure must be at the `maxConcurrent` assertion; put the `Mutex` back and run it 5 times green. Quote the commands and results in the report.

- [ ] **Step 2: Test the line that fixes the onboarding link.**
  `MainActivity.onNewIntent` calls `setIntent(launchIntentFor(...))`; reverting it to `setIntent(intent)` leaves every current test green. Add `MainActivityNewIntentTest` (Robolectric, `Robolectric.buildActivity(MainActivity::class.java)` with `.create().start().resume()`, the application's graph with onboarding NOT completed; use whatever test hook `ColdStartLinkTest`/`AppGraphTest` already use to build or fake the graph; do not add test-only code to production beyond an `internal` seam if one is truly needed, and say so): call `controller.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings")))` and assert `activity.intent.data == null`. A second test with onboarding COMPLETED: the same call leaves `activity.intent.data == Uri.parse("emberbyte://settings")`. Prove it fails: temporarily revert the `setIntent(launchIntentFor(...))` line to `setIntent(intent)` (do not commit), the first test fails, restore, green. Quote it. Also replace the vacuous "lands on Home" assertion in `ColdStartLinkTest` (it navigates to Home by hand) by removing it or by making the test feed the intent through the same code path, and say which.

- [ ] **Step 3: Run the gate** — green; `:app` lint warnings unchanged in code/config terms.

- [ ] **Step 4: Commit** `test: make the alert lock test deterministic and cover the new-intent fix on the real activity`.

### Global Constraints & Rules:
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- Stage only files you changed by explicit path.
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com`.
- Commit message ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
