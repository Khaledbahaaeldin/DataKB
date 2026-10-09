# Prompt for Antigravity: Milestone 5 "Live Lens"

> Paste everything below the line into Antigravity, in the project directory. It is the fifth milestone prompt: same repository and working protocol as M2 to M4, plus the hard-won rules below, plus ONE hard stop in the middle (after the forwarder spike).

---

# 0. Where the project stands

**Emberbyte** (repo codename DataKB) is an open-source (GPL-3.0-or-later) native Android data-usage monitor with a Material 3 Expressive UI. Claude is the mastermind (specs, plans, audits); you implement. **M1 to M4 are closed, merged to `main` and pushed** (latest merge `e2763da`): live sampler, exact totals per 2-hour window, plans with forecast and alerts, widgets, per-app budgets, a spike detector, "what if", CSV/JSON export and encrypted backup/restore. `main` has 965 unit tests, 0 lint errors.

M5 is **Live Lens**: an opt-in local VPN that shows which app uses data right now and which servers it contacts, entirely on the device. It is the largest and riskiest piece of the project (a userspace TCP/UDP forwarder), so the plan starts with a **spike** and a **decision record**, and you must **stop for Claude's review after the spike**.

# 1. What M2 to M4 taught us (read this twice)

1. **Claude's files were destroyed twice** because a subagent ran `git checkout` on them. Absolute rules, copied VERBATIM at the top of EVERY implementer and tester brief:
   - Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A` in this repository. To change branches use `git switch <branch>` only.
   - Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/` (Claude's files, committed or not).
   - Stage only the files you changed, by explicit path. If a build or test needs a clean tree, ask the orchestrator.
   After EVERY subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` BEFORE trusting its report; if anything under `docs/superpowers` changed, or `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.
2. **A task with no production code and no test in its diff is not done**, whatever the subagent says. Read the diff.
3. **Reports are checked line by line.** Quote only what you ran, with the commands. Real test names (`grep` them). "not run" / "CANNOT-VERIFY" when true. Never "tester PASS" without a tester report file ("self-verified, no separate tester report"). Never "immediately" for something that takes a poll or a refresh. List EVERY deviation and EVERY pre-existing test you changed; "Deviations: None" is almost never true.
4. **Tests must be able to fail.** When a plan says "prove it fails without the fix", do it and quote the commands; a test that passes with the fix reverted is worthless (twice in M4 a test was vacuous or timing-dependent).
5. **UI, VPN, lifecycle and network work is not done until it works on the emulator**, with quoted evidence (command output, `uiautomator` text, screenshot paths you actually OPENED and described). Restore every device setting you change. Always use `adb -s emulator-5554`: a physical phone may be attached and must never be touched.
6. **Never delete or overwrite a test**, never `@Ignore`, no new dependency without the user's approval, JDK 17 (no Java 18+ APIs), never push or merge.
7. **Privacy is a feature of Lens.** Payload bytes are never stored or logged; logs never contain IP addresses, domain names or other apps' UIDs (counts and sizes only). Violations are Critical defects.
8. Use the implementer model the plan names (Gemini 3.8 Flash, low); do not substitute a smaller model.

# 2. Your job

Branch: **`m5-live-lens`** (already created from `main`; `git switch m5-live-lens`). Do not create another.

Execute **`docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md`**. It has three parts:

- **Part 1:** tasks **A1, A2** (carry-over from M4). They reuse tasks N1 and N2 of `docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md`; extract their briefs from THAT file with the usual `awk` command and `PLAN=docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md`, `N=N1` / `N=N2`. (Task N3 of that file is NOT run.)
- **Part 2:** tasks **S1, S2, S3**: the forwarder spike. After S3 you **STOP** and say exactly: *"The Lens spike is done: decision <KOTLIN CONFIRMED|NATIVE NEEDED>. Waiting for Claude to review docs/spikes/lens-forwarder.md before part 3."* Do NOT start part 3 until the user tells you the decision was confirmed. If the decision is `NATIVE NEEDED`, write no native code.
- **Part 3:** tasks **B1 to B8**: the build and the final proof.

Before task A1 read: the plan's header and Global Constraints; **API contract section 12** (the plan implements it one to one; where plan and contract differ the contract wins and you stop and tell the user) and section 4; backend spec section 7 and 13; frontend spec 6.4. For the spike, read open-source VPN firewalls for design lessons only; **copy or translate no code** (some are GPL-3.0-only, which would change Emberbyte's GPL-3.0-or-later licence); record what you learned, without code, in the decision record.

# 3. Orchestration protocol (same as before)

You are the orchestrator and write no code yourself: a **Gemini 3.8 Flash (low)** implementer does one task at a time; a **fresh Gemini 3.8 Flash (high)** tester verifies it independently; the ledger records progress; at most **3 fix rounds** per task; never push, merge, force or skip hooks. The full protocol, the two prompt templates (A implementer, B tester) and the common rules are in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` (sections 5 to 9) and `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/common-rules.md`. Reuse them, adding the rule block of section 1.

- **Workspace:** `WS=.superpowers/sdd/2026-10-11-emberbyte-m5-live-lens` (git-ignored). Create it, copy `common-rules.md`, keep the ledger in `$WS/progress.md` (first line `## M5 (plan docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md)`, then `Task A1: started ...` / `complete ...`).
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md
  N=S1
  WS=.superpowers/sdd/2026-10-11-emberbyte-m5-live-lens
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Every brief also gets the plan's "Global Constraints", contract section 12, and the rule block of section 1.
- **Reports:** `task-N-report.md` and `task-N-test-report.md`. **Commits:** one per task with the message in the task (it explains WHY), ending with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`; identity check first (`git config user.name` = `Khaledbahaaeldin`, `user.email` = `khaled.bahaaeldin@aiu.edu.eg`, repo-local only).
- **Device sessions** (AVD `Violet_API_36`, one at a time, clean install each, restore every setting, `adb emu kill` at the end; short-path git worktrees such as `C:/dkbm5` if you need a second build): needed by A2, S3, B3 (partly), B4, B5, B6, B7, B8. Tasks A1, S1, S2, B1, B2 are verified by the gate; say so in their test reports. For S3 and anything with a host file server, run `python -m http.server` on the host and stop it afterwards (check `netstat -ano | findstr :8000` on Windows; a stray server from an earlier QA may still be running).

# 4. Environment and traps

- Windows 11, Git Bash, JDK 17, Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk`, `adb` at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, Python 3. Long Gradle runs are allowed up to 10 minutes.
- The forwarder core (`…lens.core`) is plain JVM Kotlin: its tests run on the JVM with an in-process fake TUN and local sockets; run them three times (`--rerun-tasks`): a flaky network test is a failure. Never use fixed sleeps in tests; use bounded waits and injected clocks.
- Room migrations: the SQL must be copied from the generated `schemas/.../4.json`; `1.json` to `3.json` must not change.
- The emulator has no IPv6 route and no battery model: say CANNOT-VERIFY, do not guess. The JVM tests cover the IPv6 logic.
- Emulator storage can run low: free space by uninstalling old builds and say so; do not silently change system thresholds. If setting the language with `setprop` is refused, use the Settings app.

# 5. When to stop and talk to the user

After S3 (always); a task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a toolchain failure; a dependency that will not resolve; the spike saying `NATIVE NEEDED`; any missing or changed file of Claude's; anything that would break the rules above; or a case where you believe a rule of the plan is wrong and can demonstrate it (show the evidence).

# 6. What Claude will check afterwards

An Opus auditor and an Opus lead QA engineer repeat the work: contract section 12 line by line, the spike decision record against its raw numbers, the forwarder's TCP state machine by hand and by test (including that the tests fail when the code is broken), the privacy invariants (no payload, no addresses or names in logs), the migration and upgrade from the M4 build, the double-counting fix against `dumpsys netstats`, teardown on every stop path, the deleted/changed test diff, the lint list, and every line of your completion report. Please make that boring.

# 7. Finish

After task B8: `git status` shows nothing modified except git-ignored files, nothing was pushed, you are on `m5-live-lens`. Print a short summary (commits, test counts per module, deviations, open concerns), then **stop** and say: **"M5 is ready for the Claude audit on branch m5-live-lens."** Do not audit yourself, merge, push, or start M6.

Start by reading, confirm in one short message, and begin task A1.
