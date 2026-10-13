# Prompt for Antigravity: Milestone 6 "Polish"

> Paste everything below the line into Antigravity, in the project directory. It is the last milestone prompt: same repository and working protocol as M2 to M4, plus the rules the M5 spike taught us. There is no mid-milestone stop; the only stop is at the end.

---

# 0. Where the project stands

**Emberbyte** (repo codename DataKB) is an open-source (GPL-3.0-or-later) native Android data-usage monitor with a Material 3 Expressive UI. Claude is the mastermind (specs, plans, audits); you implement. **M1 to M4 are closed and merged to `main`** (latest `08bd78b`, CI green): live sampler, exact totals per 2-hour window, plans with forecast and alerts, widgets, per-app budgets, a spike detector, "what if", CSV/JSON export and encrypted backup. `main` has 970 unit tests, 0 lint errors, three tabs (Home, Apps, Plans).

**Milestone 5 (Live Lens, a local-VPN per-app/per-domain view) was DISCARDED** after the Kotlin forwarder spike stayed far above its CPU budget (ADR `docs/adr/0001-discard-live-lens.md`). Lens code is gone from `main`. **Do not add any VPN, `INTERNET` permission, network code or Lens feature.** The branch `m5-live-lens` stays on origin as a record; do not use it.

M6 makes the app releasable and has no new features: an **accessibility pass that is proven**, the **Android 16 live-update notification**, and everything **F-Droid** needs (a reproducible, shrunk, unsigned release build; licence notices; store metadata; a README that is true).

# 1. What M2 to M5 taught us (read this twice)

1. **Claude's files were destroyed twice** because a subagent ran `git checkout` on them. Absolute rules, copied VERBATIM at the top of EVERY implementer and tester brief:
   - Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A` in this repository. To change branches use `git switch <branch>` only.
   - Never touch anything under `docs/superpowers/` (Claude's files, committed or not).
   - Stage only the files you changed, by explicit path. If a build or test needs a clean tree, ask the orchestrator.
   After EVERY subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` BEFORE trusting its report; if anything under `docs/superpowers` changed, or `grep -c "## 12. Milestone 6" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.
2. **Briefs are never edited.** Produce each brief ONLY with the `awk` command in section 3, then `diff` it against the plan section; the M5 spike brief silently lost the plan's decision rule, and a failing result was declared a pass. Never paraphrase or summarize a brief.
3. **A task with no production code and no test in its diff is not done**, whatever the subagent says (tasks P6 and P7 are documentation and proof; their tests are named in the plan). Read the diff.
4. **Reports are checked line by line.** Quote only what you ran, with the commands and raw output. Real test names (`grep` them). "not run" / "CANNOT-VERIFY" when true. Never "tester PASS" without a tester report file. List EVERY deviation and EVERY pre-existing test you changed; "Deviations: None" is almost never true. **No PASS line that is not computed from a measurement or a command's result**; never hard-code a PASS in a script.
5. **Tests must be able to fail.** Show every new test failing on the unfixed code and quote it. A test that passes both ways is rejected (twice in M4 and several times in M5 a test was vacuous, timing-dependent or its mutant survived).
6. **UI, notification and lifecycle work is not done until it works on the emulator**, with quoted evidence (command output, `uiautomator` text, screenshot paths you actually OPENED and described). Restore every device setting you change (font scale, density, animation scales, locale). Always use `adb -s emulator-5554`: a physical phone may be attached and must never be touched.
7. **Never delete or overwrite a test**, never `@Ignore`, no new dependency, JDK 17 (no Java 18+ APIs), never push, merge or tag.
8. Privacy is a feature: nothing leaves the device, logs contain counters only, the app declares no `INTERNET` permission.
9. Use the implementer model the plan names (Gemini 3.8 Flash, low) and a fresh tester (Gemini 3.8 Flash, high); do not substitute a smaller model.

# 2. Your job

Branch: **`m6-polish`** (already on origin with this plan and the contract committed): `git fetch origin && git switch m6-polish`. Do not create another branch. Record `git rev-parse HEAD` as START in the first ledger line.

Execute **`docs/superpowers/plans/2026-10-14-emberbyte-m6-polish.md`**: tasks **P1 to P7**, in order. Before task P1 read: the plan header and its Rules block; **API contract section 12** (the plan implements it one to one; where plan and contract differ, the contract wins and you stop and tell the user); frontend spec sections 5, 6 and 9; `docs/adr/0001-discard-live-lens.md`.

# 3. Orchestration protocol (same as before)

You are the orchestrator and write no code yourself: a **Gemini 3.8 Flash (low)** implementer does one task at a time; a **fresh Gemini 3.8 Flash (high)** tester verifies it independently; the ledger records progress; at most **3 fix rounds** per task; never push, merge, tag, force or skip hooks. The full protocol, the two prompt templates (A implementer, B tester) and the common rules are in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` (sections 5 to 9) and the `common-rules.md` file of an earlier workspace (copy it from `.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage/common-rules.md` if it exists on your machine; otherwise ask the user). Add the rule block of section 1.

- **Workspace:** `WS=.superpowers/sdd/2026-10-14-emberbyte-m6-polish` (git-ignored; do NOT `git add` anything under `.superpowers`, ever: in M5 seventeen files from there were force-added and published a personal email and a local path). Ledger `$WS/progress.md`, first line `## M6 (plan docs/superpowers/plans/2026-10-14-emberbyte-m6-polish.md), START=<sha>`, then `Task P1: started ...` / `complete ...`.
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-14-emberbyte-m6-polish.md
  N=P1
  WS=.superpowers/sdd/2026-10-14-emberbyte-m6-polish
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Every brief also gets the plan's header Rules block, contract section 12 and the rule block of section 1. Then `diff` the task text in the brief against the plan.
- **Reports:** `task-N-report.md` and `task-N-test-report.md` (P7 is self-verified: write exactly "self-verified, no separate tester report"). **Commits:** one per task with the message in the task, ending with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`, using the git identity of your own checkout.
- **Device sessions** (AVD `Violet_API_36`, one at a time, clean install each, restore every setting, `adb emu kill` at the end): P1 (audit dumps), P3, P4, P5, P6 (screenshots), P7. Use short-path git worktrees such as `C:/dkbm6` only if you need a second build. Never run two Gradle builds at once.

# 4. Environment and traps

- Windows 11, Git Bash, JDK 17, Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk` (adjust to your machine), `adb` in `platform-tools`, Python 3. Gradle runs up to 10 minutes are normal.
- The platform API for the live update was verified against the API 37 `android.jar` (`Notification.ProgressStyle`, `Builder.setRequestPromotedOngoing`, `setShortCriticalText`, `Manifest.permission.POST_PROMOTED_NOTIFICATIONS`, `NotificationManager.canPostPromotedNotifications()`); P4 step 1 makes you verify it again yourself.
- The emulator may not promote the notification to the status-bar chip; say CANNOT-VERIFY for what you cannot see and show what the platform prints instead. The emulator has no battery model and no real SIM: say so, do not guess.
- Release signing: NEVER commit a keystore or password; sign test APKs with a throwaway keystore in the scratchpad outside the repository.
- A stray `python -m http.server`, emulator or `adb forward` from an earlier session may hold resources: check and clean up.
- Do not leave `docs/accessibility-audit.md`, `docs/release/*` or `fastlane/` half-written between tasks: each task commits complete files.

# 5. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a platform API missing (P4 step 1); R8 or reproducibility problems you cannot solve (P5); a dependency that will not resolve; any missing or changed file of Claude's; anything that would break the rules above; or a case where you believe a rule of the plan is wrong and can demonstrate it (show the evidence). Do not ask the user for decisions the plan already makes.

# 6. What Claude will check afterwards

An Opus auditor and a device QA engineer repeat the work: contract section 12 line by line; the accessibility audit table against the code and the UI (every `OK` cell must have evidence that can fail); the touch-target and font-scale tests by breaking the code; the live-update notification on the device (`dumpsys notification`) and its fallbacks; the release APK (permissions, no debug classes, no `INTERNET`, unsigned, reproducible by building twice, installed and smoke-tested); `NOTICE` against the real dependency tree; the store metadata against the length limits and the truth of every claim in the README and the description; the upgrade from the M4 build with data intact; privacy invariants; the deleted/changed test diff; the lint list; and every line of your completion report. Please make that boring.

# 7. Finish

After task P7: `git status` shows nothing modified except git-ignored files, nothing was pushed, no tag was created, you are on `m6-polish`. Print a short summary (commits, test counts per module, deviations, CANNOT-VERIFY items, open concerns, and what the user must still do: tag `v1.0.0`, optional GitHub release, the fdroiddata merge request, the real-device checklist), then **stop** and say: **"M6 is ready for the Claude audit on branch m6-polish."** Do not audit yourself, merge, push or tag.

Start by reading, confirm in one short message, and begin task P1.
