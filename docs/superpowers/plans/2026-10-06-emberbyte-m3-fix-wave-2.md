# Emberbyte M3 Fix Wave 2 (the last one before M3 closes)

> **For the executing agents (Antigravity):** follow `docs/superpowers/plans/2026-10-06-m3-fixwave2-antigravity-prompt.md`
> (implementer and a fresh tester per task, at most 3 fix rounds). Tasks J1 and J2, in order.

**Why:** the re-audit (`.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave/m3-reaudit-report.md`) found NO Critical or Important code defect;
it is blocked only by false statements in the completion report (I-R1). The device QA re-run
(`m3-qa-rerun-report.md`) passed navigation, plan numbers, editor safety, alerts and regression; it failed on five Minor items. This wave
fixes those, trims two lint warnings, and rewrites the report truthfully. Read both reports first.

**Rules (unchanged and absolute):** never run `git checkout`, `git restore`, `git stash`, `git clean`, `git reset` or `git add .` / `-A`;
never touch `docs/superpowers/` (Claude's files; the contract and the plans are committed in `313b309` and later); stage by explicit path;
never delete or overwrite a test; quote only what you ran; no new dependency; JDK 17; never push or merge. Branch
`m3-plans-forecast-widgets`, identity `Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`, commits end with
`Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Gate: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
(`--no-build-cache` for J2). Baseline at `5e66f0e`: 672 tests (`:app` 267, `:core:data` 196, `:core:engine` 97, `:ui:design` 112),
0 lint errors, 11 `:app` lint warnings.

---

### Task J1: The five Minor QA items and two lint warnings

**Files (read each before editing):** `app/.../plans/PlanEditorScreen.kt` (threshold chips), `app/.../home/HomeScreen.kt` and `HomeMapping.kt` (subtitle, hero description), `app/.../plans/{PlanEditorViewModel,PlanEditorMapping,PlansViewModel}.kt`, `core/data/.../plan/{PlanValidation,RoomPlanRepository}.kt`, `app/build.gradle.kts`; tests in `PlanEditorContentTest`, `HomeContentTest`/`HomeMappingM3Test`, `PlanEditorViewModelTest`, `PlanValidationTest`, `PlansViewModelTest`.

- [ ] **Step 1: Write the failing tests** (add them; change no existing test):
  * **QA4-01 (threshold chips at font scale 2.0).** `PlanEditorContentTest`: under `CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f))` the node `"100%"` has height at most as tall as the node `"50%"` (one line each, same height).
  * **QA4-02 (Arabic Home subtitle).** `HomeContentTest`: with `LocalLayoutDirection provides LayoutDirection.Rtl` and a subtitle `"3.80 GB left · won't run out this cycle"` the subtitle is rendered as ONE text node whose text equals the subtitle exactly (the number stays beside "GB left"); add the same for `"No data left"`. The test must FAIL before the fix (it fails when the subtitle is not isolated with `ForceLtr`; if it passes on the old code, say so and instead assert the presence of the `ForceLtr` isolation through the subtitle node's text being unchanged, and rely on the device check).
  * **QA2-02 (spoken text).** `HomeMappingM3Test`: for `remainingBytes = 0` the hero description is `"<used> used today, no data left"`; for 3.8 GB it still ends `", 3.80 gigabytes left"`.
  * **QA3-01 (all errors at once).** `PlanValidationTest`: a new `PlanValidation.planAll(plan): Map<String, String>` returns EVERY plan-level problem (blank name, cap 0, day of month 0, `rollover.maxBytes` 0, negative subscription) and `plan(plan)` keeps returning the first one. `PlanEditorViewModelTest`: ONE `Save` with a blank name, cap text `"abc"` and day of month `0` produces `errors.keys == {name, capBytes, cycle.dayOfMonth}`; nothing is saved. `RoomPlanRepositoryTest`: `validateDraft` of a draft with blank name AND cap 0 AND day 0 returns three keys.
  * **QA2-01 (default data SIM label).** `PlansViewModelTest`: a plan bound to subscription 5 when no SIM is listed (no phone permission) but `defaultDataSubscriptionId() == 5` gets `simLabel == "Default data SIM"`; a bound plan whose SIM is neither listed nor the default keeps `"SIM (not found)"`.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
  * Threshold chips: a `FlowRow` with `softWrap = false`, `maxLines = 1` on the labels, like the day chips (H4).
  * Home subtitle: wrap the subtitle `Text` in `ForceLtr { }` (the module already has it); `heroDescription`: when `remainingBytes == 0` append `", no data left"` instead of `", 0 bytes left"` (reuse the `remainingText` idea, spoken form).
  * `PlanValidation.planAll`: collect all problems with the existing field names; `RoomPlanRepository.validateDraft` uses it. `PlanEditorViewModel.save()`: when `toDomain` returns parse `Errors`, ALSO build a lenient draft (use `1L` for an unreadable cap, `Rollover.None` for an unreadable rollover cap, and skip unparsable add-ons' bytes by using `1L`) and merge `plans.validateDraft(...)` into the error map (parse errors win for the same key), so one Save shows all problems; still write nothing.
  * `PlansViewModel.simLabel`: after the installed-SIM lookup, `subscriptionId == sims.defaultDataSubscriptionId()` gives `"Default data SIM"`, else `"SIM (not found)"`.
  * Lint: remove the DEFAULT value `SavedStateHandle()` from the production constructor of `PlanEditorViewModel` (tests pass `SavedStateHandle()` explicitly; this clears the two `VisibleForTests` warnings). The `navigation-testing` test dependency: if no test imports `androidx.navigation.testing`, remove it from `app/build.gradle.kts`; if a test does (the deep-link tests use `TestNavHostController`), keep it and record it as a deviation (it is a test-only dependency, the M3 plan's own test suggestion needed it).
- [ ] **Step 4: Run to verify they pass** — gate green; `:app` lint warnings at most 9 (11 minus the two `VisibleForTests`). **Tester (emulator, mandatory):** (a) `settings put system font_scale 2.0`: the plan editor's `100%` chip is on one line (compare the node heights in `uiautomator dump`); restore the scale; (b) set the device language to Arabic (`ar-EG`; restore it after): the Home subtitle shows the number beside its words (quote the node text order); (c) in the editor leave the name empty, type `abc` as the cap and `0` as the day of month and press Save once: three errors are visible at once and no plan is created; (d) a plan bound to the default data SIM, with phone access NOT granted (`adb shell pm revoke $PKG android.permission.READ_PHONE_STATE`), shows `Default data SIM` on its card; (e) an over-cap plan: Home's content description for the figure ends with `no data left` (`uiautomator dump` content-desc).
- [ ] **Step 5: Commit** `fix: show all editor errors at once, isolate right-to-left text and keep the alert chips on one line`.

---

### Task J2: Truthful completion report and the final proof

No production changes; a defect found here goes back to J1 for a fix round.

- [ ] **Step 1: Full gate, no cache** (`./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace`): record per-module test counts (baseline 672), the `:app` lint warning list with ids and locations (baseline 11), the Kotlin warning count with the REAL breakdown (the re-audit counted: 24 deprecated `createComposeRule` in tests (9 in `:app`, 15 in `:ui:design`), 2 at `ForecastEngineTest.kt:80`, 1 opt-in at `SchedulerClock.kt:17`, 3 production deprecations in `:core:data`; recount and correct it if your numbers differ).
- [ ] **Step 2: Rewrite `.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave/m3-fixwave-completion-report.md`** (keep the old text in an appendix titled `Superseded draft` and put a new, correct report first). It must fix every finding I-R1 of the re-audit (read §7 of `m3-reaudit-report.md`):
  * real test names only: `grep -rn "fun " app/src/test/.../AskedTrackerTest.kt` and `.../PlanEditorFormJsonTest.kt` (the H3 report already has the right ones);
  * "Deviations": list ALL of them, including the new test dependency `navigation-testing` (and its fate in J1), the `plans/abc` assertion that was dropped from a test and why, the H1 fix round (`60c9274`), the `SamplerEngineTest` fixture change (150 s to 300 s), and the credit for the contract section (it is Claude's commit `313b309`, not yours);
  * the COMPLETE list of pre-existing tests that were changed (use `git diff 7eda5b3 -U0 -- '*Test.kt' | grep -E '^[-+].*fun '` and account for every line) and the real number of tests added to existing files (the re-audit counted 29, not 21);
  * the correct cause of the two `VisibleForTests` warnings (the production default `SavedStateHandle()`, removed in J1) and the justification of the others;
  * the alert notification id is the plan id (tag `plan-alert`), not `1`;
  * emulator evidence: where the earlier evidence was thin (the upgrade had no row counts; the force-stop check did not show that no second notification was posted), either redo it now and quote it (`dumpsys notification --noredact | grep -c plan-alert` before and after `force-stop`; Python `sqlite3` row counts of `usage_hourly` before and after the upgrade) or write `not shown` / `CANNOT-VERIFY`;
  * deferred items, exactly: the Minor items of the re-audit (navigation edge cases: single-top replaces an open editor for another plan, a tab link pushes a duplicate tab, a link during onboarding lands on top of it; weak tests: RTL, time format, mutex, History caveat UI; "Estimated" flicker until the first backfill; a partial save plus process death can create a duplicate plan), M-4, M-9, M-10, M-16, M-17, the true database transaction, the README hour-curve limitation (RESTORE that README sentence in this task: "The forecast uses a built-in hour-of-day curve, not yours").
- [ ] **Step 3: Emulator proof** (clean installs; restore every setting; `adb emu kill`): the J1 tester checks (a) to (e); one more pass of navigation (`emberbyte://plans/1` then Back lands on the previous screen), a 40-tap navigation stress with 0 bounces, `logcat -d | grep -E "AndroidRuntime|FATAL"` empty. Quote the evidence.
- [ ] **Step 4: Commit** the README change only (`docs: restore the hour-curve limitation in the README`). Then `git status` shows nothing but git-ignored files, nothing was pushed. Say: **"M3 fix wave 2 is ready for the Claude final check on branch m3-plans-forecast-widgets."**
