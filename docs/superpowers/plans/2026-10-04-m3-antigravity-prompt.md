# Prompt for Antigravity: Milestone 3 "Plans, forecast, alerts, widgets"

> Paste everything below the line into Antigravity, in the project directory. It is the third prompt of the project: same repository, same working protocol as the M2 prompts, a new milestone.

---

# 0. Where the project stands

**Emberbyte** (repo codename DataKB) is an open-source (GPL-3.0-or-later) native Android data-usage monitor with a Material 3 Expressive UI. Claude is the mastermind (specs, plans, audits); you implement. You already built M2 ("Real usage") plus two fix waves, and it is **closed and merged to `main`** (merge commits `9eeaf13` and `ae9bf29`, CI green: https://github.com/Khaledbahaaeldin/DataKB/actions). Current facts you must not break:

- Modules: `:app`, `:ui:design` (no `:core:*` dependency), `:core:engine` (pure Kotlin), `:core:data` (Room, DataStore, repositories, sampler), `:feature:lens` (empty until M5). Hand-written `AppGraph`, no DI framework.
- Data semantics are fixed in contract section 9 (2-hour NetworkStats windows, per-window merge, first-hour surplus, zone-aware day). Home, Apps, History and the notification already agree with each other and with `dumpsys netstats`. M3 must not change that; plans are built on top of the same rows.
- Gate: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace` (403 tests, 0 lint errors, 6 `:app` lint warnings at the end of M2).

# 1. What M2 taught us (read this twice)

The M2 audit and the device QA found two Critical defects that 354 green unit tests did not see (the app reset to Home on a real device; totals double counted). They also found completion reports with false statements. For M3:

1. **UI, lifecycle and permission work is not done until it works on the emulator.** Every UI task has a "Tester (emulator)" line: the tester must run it, quote evidence (command output, `uiautomator` texts, screenshot paths) and restore the device settings. No evidence, no PASS.
2. **Reports are checked line by line.** Quote only what you ran. Use real test names (`grep` them). Write "not run" or "CANNOT-VERIFY" when it is true. Never write that something happens "immediately" if it takes a poll or a refresh cycle. Never mark a task "tester PASS" without a tester report file.
3. **Never weaken tests.** Where a task says an existing test encodes old behaviour, change exactly that test and say so. Anything else stays untouched, never `@Ignore`d.
4. **Platform surprises are information.** If a platform assumption in the plan seems wrong (the Room migration, Glance, a permission), stop and show the evidence instead of working around it.

# 2. Your job

Branch: **`m3-plans-forecast-widgets`** (already created from `main`; `git checkout m3-plans-forecast-widgets`). Do not create another branch.

Execute **`docs/superpowers/plans/2026-10-04-emberbyte-m3-plans.md`**: tasks **1 to 21**, in order. Before task 1 read:

- the plan's header and "Global Constraints";
- **API contract section 10** (`docs/superpowers/specs/2026-10-01-datakb-api-contract.md`): the plan implements it one to one. Where plan and contract differ, the contract wins; if they genuinely contradict each other, stop and tell me (section 5);
- backend spec sections 4, 5, 6, 9 and 11; frontend spec sections 6.3, 6.6, 7 and 8.

**Files you must not touch:** the specs under `docs/superpowers/specs/`, the plans and prompts under `docs/superpowers/plans/`. They are Claude's and are uncommitted in your working tree on purpose. Stage only your own files by path (`git add core app ui gradle README.md docs/manual-checks`), never `git add .` or `git add -A`.

# 3. Orchestration protocol (same as M2)

You are the orchestrator and write no code yourself: a **Gemini 3.8 Flash (low)** implementer does one task at a time; a **fresh Gemini 3.8 Flash (high)** tester verifies it independently; the ledger records progress; at most **3 fix rounds** per task; never push, merge, force-push or skip hooks. The full protocol, the two prompt templates (A for the implementer, B for the tester) and the common rules are in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` (sections 5 to 9) and `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/common-rules.md`. Reuse them.

New for this milestone:

- **Workspace:** `WS=.superpowers/sdd/2026-10-04-emberbyte-m3-plans`. Create it (it is git-ignored), copy `common-rules.md` into it, and keep the ledger in `$WS/progress.md` (first line `## M3 (plan docs/superpowers/plans/2026-10-04-emberbyte-m3-plans.md)`, then `Task 1: started ...` / `complete ...` lines).
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-04-emberbyte-m3-plans.md
  N=1
  WS=.superpowers/sdd/2026-10-04-emberbyte-m3-plans
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Every brief must also be given the plan's "Global Constraints" section and contract section 10 (tell the implementer and tester to read them).
- **Reports:** `task-N-report.md` (implementer) and `task-N-test-report.md` (tester), as in M2.
- **Commits:** one commit per task with the message given in the task (it explains WHY), ending with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Identity check before every commit (`git config user.name` must be `Khaledbahaaeldin`, `user.email` `khaled.bahaaeldin@aiu.edu.eg`, repo-local only).
- **Device sessions:** only one emulator session at a time (AVD `Violet_API_36`). Each starts from a clean install (`adb shell pm clear` or uninstall) and restores every device setting it touched. Use short-path git worktrees (for example `C:/dkbm2`) where a task needs a second build, because Git Bash cannot delete long paths.
- **Tasks that need a device (the tester's mandatory emulator checks):** 10, 13, 14, 16, 17, 18, 19, 20, and 21 (the whole proof). Tasks 1 to 9, 11 (partly) and 12 are verified by the gate; say so explicitly in their test reports.

# 4. Environment and traps

- Windows 11, Git Bash, JDK 17 (so **no Java 18+ APIs**, for example `Math.ceilDiv`), Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk`, `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. Long Gradle runs are allowed up to 10 minutes. Python 3 is available for reading copied SQLite files.
- Room migration (task 4): the `Migration(1, 2)` SQL must be copied from the generated `schemas/.../2.json`; Room refuses to open a database whose schema differs by a single character. The migration test in the plan proves it.
- Material 3 is the alpha `1.5.0-alpha29` (Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`); `FlowRow` and others have their own opt-ins.
- Glance (task 18) is the only new dependency. Start from the version in the plan; if it does not resolve or compile, use the newest stable release and record it.
- Compose tests: a rule allows one `setContent` per test; use `performScrollTo` only on nodes that exist (the editor is a plain scrolling `Column` for that reason).

# 5. When to stop and talk to me

A task still failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a toolchain failure; a dependency that will not resolve; anything that would break the rules above; and any case where you believe a rule of the plan is wrong for a case you can demonstrate (show the evidence).

# 6. What Claude will check afterwards

An Opus auditor and an Opus lead QA engineer will repeat the work: contract section 10 line by line, the migration and the upgrade path from the M2 build (data kept, `user_version` 2), the engine maths against hand-computed cases, "once per threshold per cycle" alerts, plan numbers against `dumpsys netstats`, Home, plan card, notification and widget agreeing with each other, the diff for weakened tests, the Glance version, and every statement of your completion report. Please make that boring.

# 7. Finish

After task 21 passes: `git status` shows only the Claude-owned docs (modified or untracked), nothing was pushed, you are on `m3-plans-forecast-widgets`. Print a short summary (commits, test counts per module, deviations, open concerns), then **stop** and say: **"M3 is ready for the Claude audit on branch m3-plans-forecast-widgets."** Do not audit yourself, merge, push, or start M4.

Start by reading, confirm in one short message, and begin task 1.
