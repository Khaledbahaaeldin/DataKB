# Emberbyte M4 Fix Wave 3 (tests, one small UX fix, honest ledger)

> **For the executing agents (Antigravity):** follow `docs/superpowers/plans/2026-10-10-m4-fixwave3-antigravity-prompt.md`. Tasks N1, N2, N3, in order. Small wave.

**Why:** the final check (`.superpowers/sdd/2026-10-09-emberbyte-m4-fix-wave-2/m4-final-check-report.md`, Opus) found NO production-code defect in `8b11f92`, and the device QA
(`m4-final-qa-report.md`) passed every case. M4 is held only by two TEST gaps and report wording. Read both reports.

**Rules (absolute, copy verbatim at the top of every implementer and tester brief):** never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset`
or `git add .` / `-A`; `git switch` for branches only; never touch `docs/superpowers/`; stage by explicit path; after every subagent run `git status --short` and
`git diff --stat HEAD -- docs/superpowers` and stop if it changed or `grep -c "## 11. Milestone 4" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
A task with no production code and no test in its diff is not done. Quote only what you ran; real test names; "not run"/"CANNOT-VERIFY" when true; no "tester PASS"
without a tester report file; list every deviation and every changed pre-existing test. Never delete or overwrite a test; no `@Ignore`; no new dependency; JDK 17;
never push or merge. Branch `m4-insight`; identity `Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`; commits explain WHY and end with
`Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Gate: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
(`--no-build-cache` for N3). Baseline at `8b11f92`: 965 tests (`:app` 400, `:core:data` 324, `:core:engine` 125, `:ui:design` 116), 0 lint errors; `:app` lint warnings
are 8 code/config warnings plus up to 3 lint "newer version available" notices on build files that depend on the day (state the exact list you see, with ids and locations,
and which are version notices; add no code warning).

---

### Task N1: Two tests that really can fail

**Files (read first):** `core/data/src/test/kotlin/$PKG/data/alert/AlertEvaluatorTest.kt`, `core/data/src/main/kotlin/$PKG/data/alert/AlertEvaluator.kt`, `app/src/main/kotlin/$PKG/MainActivity.kt`, `app/src/test/kotlin/$PKG/nav/ColdStartLinkTest.kt` (+ a new `MainActivityNewIntentTest.kt`).

- [ ] **Step 1: The lock test of `AlertEvaluator` must be deterministic.** The final check removed the `Mutex` in a scratch copy: `concurrent_evaluate_calls_return_exactly_one_notice` still PASSED in 2 of 3 full `:core:data` runs (it failed 5/5 alone, only at the notice count, never at the `maxConcurrent` assertions), while `BudgetEvaluatorTest.parallel_evaluations_post_a_transition_once` failed 8/8. Make the Alert test as strict as the Budget test: gate the dependency the first call waits on with a `CompletableDeferred` that the test completes only AFTER the second call has started, then wait (bounded, `withTimeout(2_000)` polling every 5 ms, never a fixed sleep) until either the second call has entered the dependency (the bug) or a short settling window has passed, and assert the number of concurrent entries stayed at 1 (`maxConcurrent == 1`) and exactly one notice was produced. Prove it: remove the `Mutex` locally (do not commit that), run the test 5 times alone and `./gradlew :core:data:testDebugUnitTest` 3 times; it must fail every time, and the failure must be at the `maxConcurrent` assertion; put the `Mutex` back and run it 5 times green. Quote the commands and results in the report.
- [ ] **Step 2: Test the line that fixes the onboarding link.** `MainActivity.onNewIntent` calls `setIntent(launchIntentFor(...))`; reverting it to `setIntent(intent)` leaves every current test green. Add `MainActivityNewIntentTest` (Robolectric, `Robolectric.buildActivity(MainActivity::class.java)` with `.create().start().resume()`, the application's graph with onboarding NOT completed; use whatever test hook `ColdStartLinkTest`/`AppGraphTest` already use to build or fake the graph; do not add test-only code to production beyond an `internal` seam if one is truly needed, and say so): call `controller.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://settings")))` and assert `activity.intent.data == null`. A second test with onboarding COMPLETED: the same call leaves `activity.intent.data == Uri.parse("emberbyte://settings")`. Prove it fails: temporarily revert the `setIntent(launchIntentFor(...))` line to `setIntent(intent)` (do not commit), the first test fails, restore, green. Quote it. Also replace the vacuous "lands on Home" assertion in `ColdStartLinkTest` (it navigates to Home by hand) by removing it or by making the test feed the intent through the same code path, and say which.
- [ ] **Step 3: Run the gate** — green; `:app` lint warnings unchanged in code/config terms.
- [ ] **Step 4: Commit** `test: make the alert lock test deterministic and cover the new-intent fix on the real activity`.

---

### Task N2: History opens on the newest period (QA observation, Minor UX)

**Files (read first):** `app/src/main/kotlin/$PKG/history/History.kt` (the Day/Week/Month chart and its scroll state), `ui/design/.../tiles/UsageBarRow.kt`; tests `app/src/test/.../history/HistoryTest.kt` (ADD).

The QA restored about 2 months of history and found that History opens scrolled to the OLDEST period (the Day chart showed 26 Sep to 2 Oct, the Week chart started 20 Jul), so today's bar needs a swipe. With under a week of data nobody saw it.

- [ ] **Step 1: Write the failing tests.** A bar row with MORE bars than fit (for example 60 day bars at 360 dp, 48 dp minimum width) opens scrolled to the END: after composition the last bar is displayed without scrolling (`onNodeWithContentDescription(<last bar>).assertIsDisplayed()`) and the first is not; switching Day/Week/Month scrolls to the end again; selecting a bar does not jump the scroll position; a row with few bars is unchanged. If `UsageBarRow` is shared with Home (7 bars), Home's behaviour must not change (test).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** In `UsageBarRow` (or the History screen if the row has no scroll state API) use `rememberScrollState()` and `LaunchedEffect(points.size, key) { scrollState.scrollTo(scrollState.maxValue) }` only for scrolling rows; keep `horizontalScroll` as is; the key includes the granularity so a switch rescrolls but a selection does not.
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator):** restore a backup (or seed `usage_hourly` for 60 days through the DB-copy technique of earlier QA) and open History: the Day and Week charts show the newest bars first; swipe back works; switching granularity keeps the newest visible; Home's week row is unchanged. Open and describe the screenshots.
- [ ] **Step 5: Commit** `fix(history): open the charts on the newest period`.

---

### Task N3: Final proof and an honest ledger

No production changes; a defect found goes back to its task.

- [ ] **Step 1: Full gate, no cache.** `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace`: per-module counts as printed (baseline 965), the `:app` lint warning list with ids and locations (mark which are online version notices), Kotlin warnings with the REAL breakdown (the final check counted 35 = 3 production + 29 `createComposeRule` + 2 `!!` + 1 opt-in; recount), schema diff `main..HEAD` (only `3.json`), `git diff --stat main..HEAD -- docs/superpowers` (nothing), `git log --oneline main..HEAD | wc -l`.
- [ ] **Step 2: Device proof** by a fresh tester on a clean install: the N2 History check, plus the two earlier repros (onboarding link `am start ... emberbyte://settings` during onboarding then Continue: Home; rotation during Create backup: non-empty backup that restores), a 40-tap navigation stress with the tap coordinates read from the current screen, Home/Apps/History agreement (read all THREE and quote them), `logcat -d | grep -E "AndroidRuntime|FATAL"` with the process of each FATAL line named. Open and describe every screenshot you cite. Restore every device setting; `adb emu kill`.
- [ ] **Step 3: Ledger and reports.** Write `task-N1-report.md`, `task-N1-test-report.md`, `task-N2-report.md`, `task-N2-test-report.md`, `task-N3-report.md` (N3 is self-verified: write exactly "self-verified, no separate tester report"). Correct the wording errors the final check found in the M1 and M2 reports of wave 2 by appending a `## Correction (final check)` section to each (do not rewrite them): "baseline" was 8 code/config warnings (not 9) plus version notices; the Kotlin warning breakdown is 29 + 2 + 1 (not 31 + 1); the M1 report had no deviations section and did not say that the label test already passed on `0ac4ca1`; the M2 list of changed pre-existing tests omitted the two lock tests and the precondition added to `DeepLinksTest`; the M2 device evidence had no method for the 40 taps, did not check History, repeated the M1 tester's text and had no screenshots.
- [ ] **Step 4: Stop.** `git status` shows nothing modified except git-ignored files, nothing pushed. Say: **"M4 fix wave 3 is ready for the Claude final check on branch m4-insight."**
