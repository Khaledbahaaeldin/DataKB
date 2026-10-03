# Prompt for Antigravity — M2 fix wave (after the failed audit)

> Paste everything below the line into Antigravity. It continues the same project and the same working protocol as the first M2 prompt.

---

# 0. What happened

You finished milestone M2 and reported it ready. Claude ran two independent checks on branch `m2-real-usage` (HEAD `6a5a8f5`):

- an **Opus auditor** (code, contract, plan conformance, data semantics): verdict **FAIL**, 2 Critical, 7 Important, 21 Minor;
- an **Opus lead QA engineer** (about 90 test cases on a real emulator): verdict **FAIL**, 2 Critical, 6 Important, 14 Minor.

Both reports are in `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/`: **`m2-audit-report.md`** and **`m2-qa-report.md`**. Read both completely.

Your code followed the plan almost line for line, which is why the audit mostly blames the plan: two of its data rules were wrong. The point of this wave is not to blame anyone; it is to make M2 correct. The headline problems:

1. **The app resets to Home** on a real device (about half of all navigation taps) because the UI collected a freshly created flow during composition. Unit tests could not see it. *Lesson: on this wave, UI and lifecycle fixes must also be proven on the emulator by the tester.*
2. **Totals are wrong**: the plan merged sampler data and Android's history per hour with `max()`, but Android keeps history in interpolated 2-hour buckets, so the same bytes were counted twice (Home +12 to +21 % and sometimes going DOWN).
3. **The sampler can invent gigabytes**: a mobile counter that disappears and returns was added as new usage; a late tick after device sleep was lumped into one minute; non-Wi-Fi bytes were booked as Wi-Fi.
4. Dark-theme text unreadable, AMOLED black does nothing, wrong digits in right-to-left languages, the navbar loses a tab at 200 % font, service loops that die silently, no recovery path, the boot receiver ignoring onboarding, and a few smaller defects.

# 1. Your job

Execute **`docs/superpowers/plans/2026-10-02-emberbyte-m2-fix-wave.md`** task by task: **F1, F2, F3, F4, F5, F6, F7**, in that order, on the **same branch** `m2-real-usage` (do not create a new one). The updated specs are already on disk (the API contract section 9 was rewritten for this wave); read the plan's header and the contract's sections 2, 5, 6 and 9 first. Where specs and plan seem to disagree, the contract wins; if it is genuinely contradictory, stop and tell me (section 4).

Use the **same orchestration protocol as before** (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md`, sections 5 to 9 and the two prompt templates A and B, and the rules in `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/common-rules.md`): you are the orchestrator and write no code yourself; a **Gemini 3.8 Flash (low)** implementer does one task at a time; a **fresh Gemini 3.8 Flash (high)** tester verifies it independently; the ledger records progress; at most 3 fix rounds per task; never push, merge, force or skip hooks; identity and commit rules as in `common-rules.md` (and `CLAUDE.md` / your converted rules).

Differences from the first run:

- **Task numbering:** the tasks are `F1` ... `F7`. Extract a brief with the same command, using the new plan file and a string id, for example:
  ```bash
  PLAN=docs/superpowers/plans/2026-10-02-emberbyte-m2-fix-wave.md
  N=F1
  WS=.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
- **Ledger:** keep using `$WS/progress.md`; append a line `## Fix wave (plan docs/superpowers/plans/2026-10-02-emberbyte-m2-fix-wave.md)` first and then the usual `Task F1: started ...` / `complete ...` lines. Reports are `task-F1-report.md`, `task-F1-test-report.md`, and so on.
- **First commit of the wave:** the working tree has a small uncommitted change to `.gitignore` (it adds `.agent/`, `AGENTS.md`, `GEMINI.md`). Include it in the F1 commit.
- **The old dev database is obsolete.** The data semantics changed (usage rows are now 2-hour window rows). The app is unreleased, so no migration is written; every device test starts with `adb shell pm clear io.github.khaledbahaaeldin.emberbyte` (or an uninstall).
- **Tests that must change:** the plan lists, task by task, the exact existing tests whose OLD rule the contract has replaced. Change those and only those, explain it in the report, and keep every other test untouched. Never weaken, skip or `@Ignore` a test to get green.
- **Stronger testers.** The first QA run found defects that 354 green unit tests missed. For the tasks below the tester must, in addition to the gate, run the matching checks of F7 Step 2 **on the emulator** (`Violet_API_36`, details in F7) and quote the evidence in its report before it may return PASS:
  - **F1** → F7 items 1 and 2 (navigation never resets; dark text; AMOLED pixel);
  - **F3** → F7 item 3 and 6 (data correctness against `dumpsys netstats`; no duplicate rows);
  - **F2** → F7 item 4 (airplane mode / data toggles create no phantom usage);
  - **F5** → F7 items 8 and 9;
  - **F6** → F7 items 10 and 11.
  The tester starts each device session with a clean install and restores every device setting it changes. Only one emulator session at a time.
- **F7 is the final proof.** Its tester runs the whole checklist and writes the truthful completion report (the first one contained several wrong statements; the plan lists them).

# 2. Quality bar (unchanged, and stricter on honesty)

The Claude auditor and QA engineer will repeat their work after you finish and re-run the failing scenarios. Specifically they will re-check: the navigation stress test, the data-correctness comparison with `dumpsys netstats` (within 5 %, never decreasing), airplane-mode and data-toggle behaviour, time-zone consistency (Asia/Kolkata), dark/AMOLED pixels, Arabic digits, 200 % font navbar, the notification text after relaunch, boot/force-stop behaviour, `usage_hourly` uniqueness, and the test-weakening diff against the plan. Reports must quote real command output. Anything you could not run must say so plainly.

# 3. Environment

Unchanged: Windows, Git Bash, JDK 17, Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk`, no `sdkmanager`, long Gradle runs allowed up to 10 minutes, `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` after every task, AVD `Violet_API_36`, `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. Run `--no-build-cache` for the final gate (a cached run once hid the real test counts).

# 4. When to stop and talk to me

Same conditions as before: a task failing after 3 fix rounds, a `BLOCKED` agent, a contradictory plan, a toolchain failure, or anything that would break the rules. In particular, if you believe a **platform assumption in the plan is wrong** (for example the 2-hour bucket size or the interpolation behaviour), stop and show the evidence from `dumpsys netstats` rather than working around it.

# 5. Finish

When F7 is complete and its tester says PASS: confirm `git status` is clean except the Claude-owned docs, nothing was pushed, and print a short summary (commits, test counts, deviations, open concerns). Then **stop** and tell me: **"M2 fix wave is ready for the Claude re-audit on branch m2-real-usage."** Do not audit yourself, merge, push, or start M3.

Start with the reading, confirm in one short message, and begin Task F1.
