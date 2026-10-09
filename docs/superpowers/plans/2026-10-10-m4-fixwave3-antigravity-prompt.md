# Prompt for Antigravity: M4 fix wave 3 (tests, one small UX fix, honest ledger)

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as before.

---

# 0. Where we are

Claude's final check of your fix wave 2 found **no defect in the production code** of `8b11f92` (onboarding link dropped on `onNewIntent`, passphrase kept across rotation and zeroed, exact budget arithmetic, out-of-memory mapping, double-tap guard; 965 tests, 0 lint errors) and a fresh device QA **passed every case** (rotation during Create backup now writes a valid 8,176-byte backup that restores; the onboarding recipe lands on Home; budgets equal Apps > Mobile). M4 is held only by:

1. **Two test gaps.** (a) `AlertEvaluatorTest.concurrent_evaluate_calls_return_exactly_one_notice` still passed in 2 of 3 full `:core:data` runs with the `Mutex` removed (the Budget twin failed 8/8): your report's "fails 5/5" was true only when the test ran alone. (b) The one line that fixes the onboarding link, `setIntent(launchIntentFor(...))` in `MainActivity.onNewIntent`, has no test: reverting it leaves all 30 related tests green, and the NavHost assertion of the new test is vacuous (it navigates to Home by hand).
2. **A small UX bug** the QA found: after restoring about 2 months of history, History opens scrolled to the OLDEST period (today's bar needs a swipe).
3. **Report wording** in your M1 and M2 reports of wave 2 ("baseline 9" was 8, Kotlin warnings 29+2+1 not 31+1, missing deviations, incomplete changed-tests list, thin device evidence for M2).

Reports: `.superpowers/sdd/2026-10-09-emberbyte-m4-fix-wave-2/m4-final-check-report.md` and `m4-final-qa-report.md`. Read both.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; use `git switch` for branches only. Never touch anything under `docs/superpowers/`. Stage only your files by explicit path. **Copy this rule block verbatim at the top of EVERY implementer and tester brief**; after every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`, and stop if anything there changed or `grep -c "## 11. Milestone 4" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
2. A task whose diff has no production code and no test is NOT done. Quote only what you ran, with the commands (the Mutex proof must show the commands and the failing assertion lines); real test names; "not run"/"CANNOT-VERIFY" when true; no "tester PASS" without a tester report file (N3 is "self-verified, no separate tester report"); list every deviation and every changed pre-existing test.
3. Never delete or overwrite a test; no `@Ignore`; no new dependency; JDK 17.
4. UI work is proven on the emulator with quoted evidence; open and describe every screenshot; restore every device setting.
5. Implementer: Gemini 3.8 Flash (low); tester: a fresh Gemini 3.8 Flash (high); do not substitute a smaller model.

# 2. Your job

Execute **`docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md`**: tasks **N1, N2, N3**, in order, on branch `m4-insight` (`git switch m4-insight`). Orchestration as before (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, templates A and B, `common-rules.md`; at most 3 fix rounds; never push or merge). `WS=.superpowers/sdd/2026-10-10-emberbyte-m4-fix-wave-3` (create it, copy `common-rules.md`, ledger `progress.md`). Briefs:
```bash
PLAN=docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md
N=N1
WS=.superpowers/sdd/2026-10-10-emberbyte-m4-fix-wave-3
awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
```
One commit for N1 and one for N2 with the messages in the tasks; N3 commits nothing. Device sessions (AVD `Violet_API_36`, `-s emulator-5554` always, clean installs, restore every setting, `adb emu kill`): N2 and N3. A physical phone may be attached: never touch it.

# 3. Finish

After N3: nothing tracked is modified, nothing pushed, you are on `m4-insight`. Print a short summary (commits, test counts per module, lint warnings, deviations), then **stop** and say: **"M4 fix wave 3 is ready for the Claude final check on branch m4-insight."** Do not merge, push or start M5.

Start by reading, confirm in one short message, and begin task N1.
