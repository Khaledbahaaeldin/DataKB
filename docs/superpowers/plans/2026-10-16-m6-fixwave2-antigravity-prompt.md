# Prompt for Antigravity: M6 fix wave 2

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the earlier M6 prompts. Nothing is released or pushed.

---

# 0. What happened

You finished fix wave 1 (F1 to F5, HEAD `e09c0f1`). Claude re-audited it (Opus) and re-checked it on the device (QA). Read both reports completely first: `.superpowers/sdd/2026-10-15-emberbyte-m6-fix-wave/m6-reaudit-report.md` (findings R-1 to R-20, the status table of the first audit's findings 1 to 22) and `m6-qa2-report.md` (D8 repro, N1 to N5).

- **Good and must not regress:** drag with reduced motion, the full-screen touch-target walker, the production focus return, chart summary nodes, the selected-chip contrast in the app (5.2 to 12.4:1), the release documents (NOTICE, 11 permissions, F-Droid `Repo`), two byte-identical release builds, the release smoke path and the M4 to M6 upgrade on the emulator.
- **Critical (R-1):** `setRequestPromotedOngoing` exists only since API **36.1**, but the code calls it when `SDK_INT >= 36`. On an Android 16 (36.0) phone this throws `NoSuchMethodError`, nothing catches it, and the START_STICKY sampler crash-loops. Contract section 12.1 was **corrected by Claude** (it had the wrong guard). Read it.
- **Still broken:** D8 (the keyboard hides Create/Cancel in the backup dialog at font scale 2.0; your own screenshots showed it; the test passes even with the fix reverted); one live-update test is flaky (4 of 10 runs); the ring track change made the coloured arc only 1.50:1 against the track; fixes no test protects; and **the records again contain false statements** (a helper cited as a test, D8 "fixed", six undisclosed deleted tests, "Tests removed: 0", "Deviations: None", 20 more).
- **Not in this wave:** the user's visual polish and the store screenshots. Do not touch them.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; `git switch` for branches only. Never touch `docs/superpowers/` (Claude's files; the plan `2026-10-16-emberbyte-m6-fix-wave-2.md` and the corrected contract are read-only for you). **Copy this rule block verbatim at the top of EVERY implementer and tester brief.** After every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`; stop if a tracked file changed or `grep -c "## 12. Milestone 6" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
2. **Briefs are never edited.** Produce them ONLY with the `awk` command below and `diff` them against the plan.
3. **Truth rules (new, because two reports were wrong):** statements about the device are backed by an `adb exec-out screencap` you opened and described, never by `uiautomator` node bounds; counts such as "tests removed", "added", "deviations" come from `tools/test-diff.py` or `git diff --stat` output pasted into the report; before finishing a report run `python tools/check-cited-tests.py` on it and paste the output; every test cited exists with `@Test`; no PASS line that is not computed. Say "CANNOT-VERIFY" when true.
4. **Every new or changed test is shown failing on the unfixed code** (temporary revert or a `git archive` copy; stash forbidden) and quoted. A test that fails 1 time in 10 is a failing test: run each touched test 10 times in isolation and the `:app` and `:ui:design` suites 3 times in a row; paste the loops. Never delete or rename a test without listing it and the reason; prefer restoring it.
5. No new dependency, JDK 17, Room schema stays at 3, no `INTERNET`, no `VpnService`, never push, merge or tag. Nothing under `.superpowers` is ever added to git. Commit bodies explain WHY (at least two lines) and end with the Gemini co-author trailer.
6. Device work only on `Violet_API_36` with `adb -s emulator-5554`; a physical phone is attached and must never be touched. **Start exactly ONE emulator** (the user's RAM is limited and duplicate instances were already killed once), one Gradle build at a time, clean install, restore every setting, `adb emu kill` at the end.
7. Implementer Gemini 3.8 Flash (low), tester a fresh Gemini 3.8 Flash (high).

# 2. Your job

You are on branch `m6-polish` (local, not pushed; do not switch). Record `git rev-parse HEAD` as START. Execute **`docs/superpowers/plans/2026-10-16-emberbyte-m6-fix-wave-2.md`**: tasks **G1, G2, G3, G4, G5**, in order. Same orchestration as before (protocol and templates in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, `common-rules.md`; at most 3 fix rounds per task).

- **Workspace:** `WS=.superpowers/sdd/2026-10-16-emberbyte-m6-fix-wave-2` (git-ignored). Ledger `$WS/progress.md`, first line `## M6 fix wave 2 (plan docs/superpowers/plans/2026-10-16-emberbyte-m6-fix-wave-2.md), START=<sha>`.
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-16-emberbyte-m6-fix-wave-2.md
  N=G1
  WS=.superpowers/sdd/2026-10-16-emberbyte-m6-fix-wave-2
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Each brief also gets the plan's header paragraphs ("Why", "Contract change", "Scope", "Rules"), contract section 12 and the finding numbers it names.
- One commit per task with the message in the task. Device sessions: G1 (notification dump), G2, G3 (screenshots), G5.

# 3. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; lint that cannot express the 36.1 guard (G1 step 3: show the lint message); a dialog fix that needs a library change; a fix that would change visual design beyond what the task says; anything breaking the rules above.

# 4. Finish

After G5: tree clean except git-ignored files, nothing pushed, no tag, branch `m6-polish`. Print a short summary (commits, per-module test counts, mutants table, deleted/restored tests from `tools/test-diff.py`, deviations, CANNOT-VERIFY) and say exactly: **"The M6 fix wave 2 is done. Waiting for Claude to re-audit on branch m6-polish."** Do not audit yourself, merge, push or tag.

Start by reading, confirm in one short message, and begin task G1.
