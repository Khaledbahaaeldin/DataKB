# Prompt for Antigravity: M4 fix wave

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the M4 prompt.

---

# 0. What happened

You finished M4. Claude's Opus auditor and device QA checked branch `m4-insight` (HEAD `58618f3`):

- **Audit: NOT CLOSED** (no Critical, 3 Important, 13 Minor). Report: `.superpowers/sdd/2026-10-07-emberbyte-m4-insight/m4-audit-report.md`.
- **Device QA: PASS** with 8 Minor defects. Report: `m4-qa-report.md` in the same folder (screenshots in `qa-shots/`).

Read both completely. The substance is good: the contract maths, the `3.json` migration, the atomic plan save, the one-transaction restore and its rollback test, the backup byte format (a Python script decrypted a device-made backup), budgets equal the Apps "Mobile" figure on the device, the real M3 to M4 upgrade keeps every row, spikes, what-if, deep links, 911 tests, 0 lint errors. What must be fixed:

1. **I-1:** backup creation and restore run on the main thread (Argon2id, gzip, parsing): the UI froze for about 2 s on the emulator (`Skipped 120 frames, Davey 2079 ms`) and will ANR on slower phones.
2. **I-2:** a hostile backup header within the old memory bound (256 MiB) crashes the app with an uncaught `OutOfMemoryError`. Claude changed contract 11.2: the accepted memory cost is at most **65536 KiB**, an `OutOfMemoryError` becomes an invalid-file result, and the work runs off the main thread.
3. **I-3:** the completion report again had false statements (wrong Kotlin/Gradle versions, wrong class names, wrong lint locations, "0 production warnings", wrong schema path, "zero deviations" although the upgrade proof used a hand-built database with no row counts, wrong dialog buttons, an unsupported claim about the Python script, budget numbers that do not add up, and more).
4. Smaller correctness items (spike float rounding at exactly 5x, the 3-day history rule, restore strictness, a stale exceeded-notification, and so on) and the QA's 8 Minor UX defects.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; use `git switch` for branches only. Never touch anything under `docs/superpowers/` (Claude's files, committed or not). Stage only your files by explicit path. **Copy this rule block verbatim at the top of EVERY implementer and tester brief**, and after every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`; if anything there changed, or `grep -c "## 11. Milestone 4" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.
2. A task whose diff has no production code and no test is NOT done. Quote only what you ran; real test names; "not run"/"CANNOT-VERIFY" when true; no "tester PASS" without a tester report file; list EVERY deviation and EVERY pre-existing test you changed; no "transactional" for anything that is not one database transaction.
3. Never delete or overwrite a test; never `@Ignore`; no new dependency; JDK 17.
4. UI work is proven on the emulator by its tester with quoted evidence; open and describe every screenshot you cite; restore every device setting.
5. Use the implementer model the plan names (Gemini 3.8 Flash, low); do not substitute a smaller one.

# 2. Your job

Execute **`docs/superpowers/plans/2026-10-08-emberbyte-m4-fix-wave.md`**: tasks **L1, L2, L3, L4**, in order, on branch `m4-insight` (`git switch m4-insight`; do not create another). Read the plan's header and **contract section 11.2** first (the contract wins; if it contradicts the plan, stop and tell the user).

Same orchestration protocol as before (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, templates A and B, `common-rules.md`): you orchestrate and write no code; a Gemini 3.8 Flash (low) implementer then a fresh Gemini 3.8 Flash (high) tester per task; at most 3 fix rounds; never push or merge.

- **Workspace:** `WS=.superpowers/sdd/2026-10-08-emberbyte-m4-fix-wave` (create it, copy `common-rules.md`, ledger `progress.md`, first line `## M4 fix wave (plan docs/superpowers/plans/2026-10-08-emberbyte-m4-fix-wave.md)`).
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-08-emberbyte-m4-fix-wave.md
  N=L1
  WS=.superpowers/sdd/2026-10-08-emberbyte-m4-fix-wave
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
- **Commits:** one per task with the message in the task; identity check first.
- **Device sessions** (AVD `Violet_API_36`, one at a time, clean installs, short-path worktrees, restore every setting incl. system language, `adb emu kill` at the end): tasks L1, L2, L3, L4. If the emulator is sluggish at start, reboot it once and say so. If setting the language with `setprop` is refused, use the Settings app.

# 3. Environment

Unchanged: Windows 11, Git Bash, JDK 17, `adb` under `$LOCALAPPDATA/Android/Sdk/platform-tools`, Python 3 (the QA installed `argon2-cffi` and `cryptography` with pip for the recovery example). Baseline at `58618f3`: 911 tests, 0 lint errors, 8 `:app` lint warnings. A stray Python HTTP server from the QA run may still be running on the host: if port conflicts appear, stop it.

# 4. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a toolchain failure; any missing or changed file of Claude's; anything that would break the rules above.

# 5. Finish

After L4: nothing modified except git-ignored files, nothing pushed, you are on `m4-insight`. Print a short summary (commits, test counts per module, lint warnings, deviations), then **stop** and say: **"M4 fix wave is ready for the Claude re-audit on branch m4-insight."** Do not merge, push or start M5.

Start by reading, confirm in one short message, and begin task L1.
