# Prompt for Antigravity: M2 fix wave 2 (the last wave before M2 closes)

> Paste everything below the line into Antigravity. Same project, same branch, same working protocol as the previous two prompts.

---

# 0. What happened

Your fix wave worked. Claude's Opus re-audit and device QA confirmed that both Critical defects are gone (0 navigation bounces in 76 taps; Home matches `dumpsys netstats`) and 26 of 30 findings are fixed. Two **Important** date defects remain, plus a few cheap Minors. Branch `m2-real-usage`, HEAD `887b21b`.

Reports (read the sections named, not everything): `.superpowers/sdd/2026-10-02-emberbyte-m2-fix-wave/m2-reaudit-report.md` (N-1, N-2, section 5 "false statements") and `m2-qa-rerun-report.md` (Q2-01).

- **N-1:** in time zones with an odd or half-hour offset (Cairo in summer, Kolkata ...), a day boundary falls inside a 2-hour NetworkStats window. `SeriesBuilder` gave the window's surplus to the hour that had not started yet, so "today" began with yesterday's bytes and then FELL.
- **N-2 / Q2-01:** `DayClock` only re-emitted when the DATE changed. After a time-zone change on the same date, ranges used the old zone and bucketing the new one: Home froze on a slice of yesterday and the week strip grew to 8 bars. The earlier device check only went eastward, which hides it.

# 1. Your job

Execute **`docs/superpowers/plans/2026-10-03-emberbyte-m2-fix-wave-2.md`**, tasks **G1, G2, G3, G4**, in order, on the same branch. Read the plan header and contract sections 9.1 and 9.2 first (already updated by Claude; do not edit specs). The plan contains the exact test code and the exact implementation; follow it. Where the plan lists tests to change, change those and only those and say so in the report.

Use the **same orchestration protocol as before** (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9 and templates A and B, plus `common-rules.md`): you orchestrate and write no code; a Gemini 3.8 Flash (low) implementer does one task; a fresh Gemini 3.8 Flash (high) tester verifies independently; ledger; at most 3 fix rounds; never push, merge, force or skip hooks.

- **Task ids:** `G1` ... `G4`. Extract briefs with:
  ```bash
  PLAN=docs/superpowers/plans/2026-10-03-emberbyte-m2-fix-wave-2.md
  N=G1
  WS=.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
- **Ledger:** append `## Fix wave 2 (plan docs/superpowers/plans/2026-10-03-emberbyte-m2-fix-wave-2.md)` to `$WS/progress.md`, then the usual lines. Reports are `task-G1-report.md`, `task-G1-test-report.md`, ...
- **Testers must run the gate** and, for G2, also the zone-change device checks of G4 Step 2 item 1 (westward AND eastward, same date) before they may return PASS. Restore every device setting (especially the time zone) and start each device session from a clean install.
- **G4** is the proof and the honest report. The first two completion reports contained false statements; section 5 of the re-audit lists 13. Quote real command output, use real test names, and write "not run" for anything you did not run.

# 2. Environment and quality bar

Unchanged (Windows, Git Bash, JDK 17, AVD `Violet_API_36`, `adb` under `$LOCALAPPDATA/Android/Sdk/platform-tools`, gate `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`, `--no-build-cache` for the final one). Claude will re-run a focused audit on `SeriesBuilder.build`, the day/zone flows and the completion report, plus a QA re-check of the zone scenarios.

# 3. When to stop and talk to me

A task failing after 3 fix rounds, a `BLOCKED` agent, a contradictory plan, a toolchain failure, or anything that breaks the rules. If you think the plan's rule is wrong for a case you can demonstrate, stop and show the evidence.

# 4. Finish

After G4 passes: `git status` shows only Claude-owned docs, nothing pushed. Print a short summary (commits, test counts, deviations, open concerns), then **stop** and say: **"M2 fix wave 2 is ready for the Claude final check on branch m2-real-usage."** Do not merge, push or start M3.

Start by reading, confirm in one short message, and begin Task G1.
