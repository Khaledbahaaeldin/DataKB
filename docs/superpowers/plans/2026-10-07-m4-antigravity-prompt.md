# Prompt for Antigravity: Milestone 4 "Insight"

> Paste everything below the line into Antigravity, in the project directory. It is the fourth milestone prompt: same repository, same working protocol as the M2 and M3 prompts, plus the hard-won rules of M3.

---

# 0. Where the project stands

**Emberbyte** (repo codename DataKB) is an open-source (GPL-3.0-or-later) native Android data-usage monitor with a Material 3 Expressive UI. Claude is the mastermind (specs, plans, audits); you implement. M1, M2 and M3 are **closed, merged to `main` and pushed** (latest merge `1fcce79`, CI green). `main` today has: live sampler, exact totals per 2-hour window, flexible data plans with per-SIM binding, forecast with a range, plan alerts, Glance widgets, deep links, 680 unit tests, 0 lint errors, 9 `:app` lint warnings.

Facts you must not break: the data semantics of contract section 9.4 (2-hour NetworkStats windows, per-window merge, first-hour surplus, zone-aware day) and section 10 (plans); Home, Apps, History, the plan card, the notification and the widgets already agree with each other and with `dumpsys netstats`.

# 1. What M2 and M3 taught us (read this twice)

1. **Claude's files were destroyed twice** because a subagent ran `git checkout` on them. Absolute rules, copied VERBATIM at the top of EVERY implementer and tester brief you write:
   - Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A` in this repository. To change branches use `git switch <branch>` only.
   - Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/` (they are Claude's, committed or not).
   - Stage only the files you changed, by explicit path. If a build or test needs a clean tree, ask the orchestrator; never clean it yourself.
   After EVERY subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` BEFORE trusting its report. If anything under `docs/superpowers` shows a change, or the contract's section 11 is gone (`grep -c "## 11. Milestone 4" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` must print 1), STOP and tell the user.
2. **A task with no production code and no test in its diff is not done**, whatever the subagent says. Verify by reading the diff.
3. **Reports are checked line by line.** Quote only what you ran. Use real test names (`grep` them). Write "not run" / "CANNOT-VERIFY" when true. Never write "tester PASS" without a tester report file (write "self-verified, no separate tester report"). Never write that something happens "immediately" when it takes a poll or a refresh cycle. Never call a step-by-step save "transactional". List EVERY deviation and EVERY pre-existing test you changed; "Deviations: None" is almost never true.
4. **UI, permission, navigation and lifecycle work is not done until it works on the emulator**, with quoted evidence (command output, `uiautomator` text, screenshot paths you actually OPENED and described). Restore every device setting you change.
5. **Never delete or overwrite a test** to make room for another. Never `@Ignore`. No new dependency except the one named in the plan.
6. **Use the implementer model the plan names** (Gemini 3.8 Flash, low). Do not substitute a smaller model for an implementer.

# 2. Your job

Branch: **`m4-insight`** (already created from `main`; `git switch m4-insight`). Do not create another branch.

Execute **`docs/superpowers/plans/2026-10-07-emberbyte-m4-insight.md`**: tasks **1 to 19**, in order. Before task 1 read: the plan's header and Global Constraints; **API contract section 11** (the plan implements it one to one; where plan and contract differ the contract wins and you stop and tell the user) and section 7 (file formats); backend spec section 8; frontend spec sections 6.2 and 6.5.

One design change you must know about: the old spike rule (20-minute windows) is replaced in contract 11.1 by a per-2-hour-window rule, because Android keeps per-app history in 2-hour buckets. That is intended, not a mistake.

# 3. Orchestration protocol (same as before)

You are the orchestrator and write no code yourself: a **Gemini 3.8 Flash (low)** implementer does one task at a time; a **fresh Gemini 3.8 Flash (high)** tester verifies it independently; the ledger records progress; at most **3 fix rounds** per task; never push, merge, force or skip hooks. The full protocol, the two prompt templates (A implementer, B tester) and the common rules are in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` (sections 5 to 9) and `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/common-rules.md`. Reuse them, adding the block of rule 1 above.

- **Workspace:** `WS=.superpowers/sdd/2026-10-07-emberbyte-m4-insight` (git-ignored). Create it, copy `common-rules.md`, keep the ledger in `$WS/progress.md` (first line `## M4 (plan docs/superpowers/plans/2026-10-07-emberbyte-m4-insight.md)`, then `Task 1: started ...` / `complete ...`).
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-07-emberbyte-m4-insight.md
  N=1
  WS=.superpowers/sdd/2026-10-07-emberbyte-m4-insight
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Every brief also gets the plan's "Global Constraints" and contract section 11 (tell implementer and tester to read them), and the rule block of section 1.
- **Reports:** `task-N-report.md` and `task-N-test-report.md`. **Commits:** one per task with the message in the task (it explains WHY), ending with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`; identity check first (`git config user.name` = `Khaledbahaaeldin`, `user.email` = `khaled.bahaaeldin@aiu.edu.eg`, repo-local only).
- **Device sessions** (AVD `Violet_API_36`, one at a time, clean install each, short-path worktrees such as `C:/dkbm3` for the second build, restore every setting, `adb emu kill` at the end): needed by tasks 7 (channel only), 8, 12, 13, 14, 15, 16, 17 and 19. Tasks 1 to 6, 9 to 11 and 18 are verified by the gate; say so explicitly in their test reports.

# 4. Environment and traps

- Windows 11, Git Bash, JDK 17 (**no Java 18+ APIs**), Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk`, `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, Python 3 for reading copied SQLite files. Long Gradle runs are allowed up to 10 minutes.
- Room migrations (tasks 4): the SQL must be copied from the generated `schemas/.../3.json`; the migration test builds the old database from `2.json` itself. `1.json` and `2.json` must not change.
- The new dependency is Bouncy Castle (task 10); use only the lightweight Argon2 API, never register its provider. The RFC 9106 test vector in the plan proves the usage.
- The Storage Access Framework pickers (task 14) are the system Documents UI: drive them with `uiautomator dump` + `adb shell input`. If a step cannot be driven, report `CANNOT-VERIFY` for it; never skip silently.
- Spike seeding on the device (task 19) edits a COPY of the database of a debuggable build; never add test hooks to production code.
- The emulator can run low on storage; free space by uninstalling old builds and say so; do not silently change system thresholds.
- Compose tests: one `setContent` per test; `performScrollTo` only on nodes that exist (scrolling forms are plain `Column`s with `verticalScroll`, not lazy lists).

# 5. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a toolchain failure; a dependency that will not resolve; any missing or changed file of Claude's; anything that would break the rules above; or a case where you believe a rule of the plan is wrong and can demonstrate it (show the evidence).

# 6. What Claude will check afterwards

An Opus auditor and an Opus lead QA engineer repeat the work: contract section 11 line by line, the migration and the upgrade path from the M3 build, the budget and spike maths by hand, "once per transition" notifications, the backup format byte by byte (and the Python recovery example), the one-transaction restore and its rollback, plan/budget/forecast numbers against `dumpsys netstats`, the deleted/changed test diff, the lint list, and every line of your completion report. Please make that boring.

# 7. Finish

After task 19 passes: `git status` shows nothing modified except git-ignored files, nothing was pushed, you are on `m4-insight`. Print a short summary (commits, test counts per module, deviations, open concerns), then **stop** and say: **"M4 is ready for the Claude audit on branch m4-insight."** Do not audit yourself, merge, push, or start M5.

Start by reading, confirm in one short message, and begin task 1.
