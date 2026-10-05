# Prompt for Antigravity: M3 fix wave (after the first M3 audit and device QA)

> Paste everything below the line into Antigravity, in the project directory. Same project, same branch, same working protocol as the M3 prompt.

---

# 0. What happened

You finished M3 and reported it ready. Claude ran two independent checks on branch `m3-plans-forecast-widgets` (HEAD `7eda5b3`):

- an **Opus auditor** (code, contract, plan conformance): **NOT CLOSED**, 1 Critical (process), 8 Important, 20 Minor;
- an **Opus lead QA engineer** (device): **FAIL**, 1 Important (D3-01), 8 Minor.

Both reports are in `.superpowers/sdd/2026-10-04-emberbyte-m3-plans/`: **`m3-audit-report.md`** and **`m3-qa-report.md`**. Read both completely.

The good news is real: the engine maths was hand-verified correct (DST, odd zones, free windows, forecast), the migration is byte-identical to `2.json` and keeps the M2 data on a device, plan numbers equal `netstats`, alerts fire once per threshold, the widgets work, and the M2 carry-over items pass. What failed:

1. **The contract section the plan relied on did not exist in your working tree** (audit C-1) and your reports then cited text that was not there, including a quoted "formula". Claude's section 10 had been lost from the tree. **This must never happen again, see rule 1 below.**
2. **A deep link replaces the whole back stack while the app is open** (D3-01): one Back goes to the launcher and the plan editor cannot close after saving.
3. Plan numbers are not always honest (rollover double counts an add-on that spans a renewal; "Estimated" is missing for SIM-bound plans with unknown history and for windows older than the first measurement).
4. The editor can fail a save silently and leave a plan half-saved; unsaved input is lost on process death.
5. A pre-existing M2 test was overwritten, three required tests were never written, lint grew from 6 to 14 warnings unreported, the README says Live Lens shipped, and the completion report had false statements ("Deviations: None", "6 lint warnings", "transactional", a task 21 "tester PASS" without any tester report).

# 1. Rules for this wave (the first one is new and absolute)

1. **Never touch Claude's files.** Everything under `docs/superpowers/specs/` and `docs/superpowers/plans/` is Claude's, is intentionally uncommitted, and must stay exactly as it is. In this repository NEVER run `git checkout -- <path>`, `git restore`, `git stash`, `git clean` or `git reset --hard`, and never stage Claude's files. Stage only your own files by explicit path. If a file of Claude's looks missing or changed, STOP and tell the user.
2. **Quote only what you ran.** Real test names (`grep` them), real numbers, "not run" / "CANNOT-VERIFY" when true. No "tester PASS" without a tester report file. No "immediately" for anything that takes a poll or a refresh cycle. Never call the editor save "transactional".
3. **Never delete or overwrite a test** to make room for another. Tests that encode old behaviour are named in the plan; change exactly those and list them.
4. UI, navigation and lifecycle work is proven on the emulator by its tester with quoted evidence, then device settings are restored.

# 2. Your job

Execute **`docs/superpowers/plans/2026-10-05-emberbyte-m3-fix-wave.md`**: tasks **H1, H2, H3, H4, H5, H6**, in that order, on the same branch (do not create another). First read its header and Global Constraints, and **API contract section 10** (it now exists on disk, with the new 10.5 for this wave; it is the contract; where plan and contract differ, the contract wins and you stop and tell the user).

Use the **same orchestration protocol** as M2 and M3 (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, templates A and B, `common-rules.md`): you orchestrate and write no code; a Gemini 3.8 Flash (low) implementer does one task at a time; a fresh Gemini 3.8 Flash (high) tester verifies it; at most 3 fix rounds per task; never push, merge, force or skip hooks.

- **Workspace:** `WS=.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave` (create it, copy `common-rules.md`, keep `progress.md` there; first line `## M3 fix wave (plan docs/superpowers/plans/2026-10-05-emberbyte-m3-fix-wave.md)`).
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-05-emberbyte-m3-fix-wave.md
  N=H1
  WS=.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Give every brief the plan's Global Constraints and contract section 10; tell implementer and tester to read them.
- **Device sessions** (tasks H1, H2, H3, H4 and H6 need them): AVD `Violet_API_36`, one session at a time, clean install each time, restore every setting you change (time zone, rotation, Wi-Fi, 12/24-hour, locale, font scale, density, notification channels) and `adb emu kill` at the end. Use short-path git worktrees (for example `C:/dkbm2`) for a second build.
- **Commits:** one per task with the message given in the task, ending with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`; identity check before every commit.

# 3. Environment

Unchanged: Windows 11, Git Bash, JDK 17 (no Java 18+ APIs), Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk`, `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, Python 3 for reading copied SQLite files. Gate: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` (`--no-build-cache` for H6). Baseline at `7eda5b3`: 628 tests, 0 lint errors, 14 `:app` lint warnings (M2 ended with 6). The QA agent noted the emulator can run out of storage during installs; if that happens, free space (uninstall old builds) and report it, do not silently change system thresholds.

# 4. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a toolchain failure; anything that would break the rules above; any missing or changed file of Claude's; or a case where you believe a rule of the plan is wrong and can demonstrate it (show the evidence).

# 5. What Claude will check afterwards

A focused re-audit and QA re-run: each audit and QA id of the plan against its fix, the deep-link back stack on the device, the editor save and process-death behaviour, the numbers (rollover with add-ons, "Estimated" flags), the deleted-test diff, the lint warning list, and every line of your completion report. Please make that boring.

# 6. Finish

After H6 passes: `git status` shows only Claude's docs (modified or untracked), nothing pushed, you are on `m3-plans-forecast-widgets`. Print a short summary (commits, test counts per module, deviations, open concerns), then **stop** and say: **"M3 fix wave is ready for the Claude re-audit on branch m3-plans-forecast-widgets."** Do not audit yourself, merge, push or start M4.

Start by reading, confirm in one short message, and begin task H1.
