# Prompt for Antigravity — Emberbyte milestone M2 ("Real Usage")

> Paste everything below the line into Antigravity as the FIRST message in the project folder
> `C:\Users\Khaled\Documents\Personal Projects\DataKB`. It is written to be read by an AI agent that knows
> nothing about this project.

---

# 0. FIRST, before anything else: migrate the project's Claude configuration to Antigravity

This project was built so far with **Claude Code**, which keeps its instructions in files that Antigravity does not read.
Do this first, in this order, and tell me what you did in two or three lines when you are done:

1. Look in the repository root for a **`.claude/`** folder (it may not exist) and for **`CLAUDE.md`** (it exists, and is
   git-ignored on purpose because it is personal). Read them completely. They contain the project's standing rules,
   above all the **git identity and commit rules** (repeated in section 7 below).
2. Convert what you find into Antigravity's own workspace configuration. Antigravity reads workspace rules from
   **`.agent/rules/*.md`** (and reusable procedures from **`.agent/workflows/*.md`**); it also honours a root
   **`AGENTS.md`** / **`GEMINI.md`**. Use whatever your installed version of Antigravity actually uses (check your own
   documentation; do not guess). At minimum create one rule file, for example `.agent/rules/emberbyte-git-and-quality.md`,
   containing: the git identity rules, the "never push / never force / never --no-verify" rules, the commit-message style,
   and the quality rules from sections 7 and 8 of this prompt. If you find skills or commands in `.claude/`, port the ones that
   are relevant to Android/Kotlin development as workflows; ignore the rest.
3. **Do not delete, move or edit `CLAUDE.md` or anything under `.claude/`.** Claude Code will come back later to audit your work
   and relies on them. Convert by copying. Make sure the new Antigravity files are git-ignored as well (add `.agent/`, `AGENTS.md`
   and `GEMINI.md` to `.gitignore` only if they are not already ignored; `.gitignore` itself is a tracked file, so this edit goes
   in your first commit on the branch described below).

# 1. Who you are working for, and what this project is

The owner is **Khaled** (GitHub account `Khaledbahaaeldin`). The product is **Emberbyte**: an open-source (GPL-3.0-or-later) **Android data-usage
monitor** with a modern Material 3 Expressive interface. It shows how much mobile and Wi-Fi data you use today, in real time,
per app, with history, a live notification and (later) data plans, a run-out forecast, widgets and an opt-in per-app live view.
Everything stays on the device: no accounts, no cloud, no analytics.

- The repository folder and GitHub repo are still named **DataKB** (that was the codename). The product name, the Android
  `applicationId` and every package is **Emberbyte** / `io.github.khaledbahaaeldin.emberbyte`.
- Stack: Kotlin 2.4.20, Jetpack Compose with Material 3 Expressive, Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17,
  minSdk 29, compileSdk 37, targetSdk 36. Native Android only. No DI framework (a hand-written `AppGraph`).
- Modules: `:app`, `:ui:design`, `:core:engine`, `:core:data`, `:feature:lens` (empty until M5).

# 2. The documents that define the work (read all of them before doing anything)

All under `docs/superpowers/`. **Where they disagree, the API contract wins.**

| File | What it is |
|---|---|
| `specs/2026-10-01-datakb-design.md` | Master design: decisions, architecture, milestones M1–M6, risks |
| `specs/2026-10-01-datakb-api-contract.md` | **Typed contract between modules, storage schema, Android components, file formats. Section 9 is the M2 contract.** |
| `specs/2026-10-01-datakb-backend-spec.md` | The on-device engine: sampling, plan/forecast maths, Live Lens, permissions, errors |
| `specs/2026-10-01-datakb-frontend-spec.md` | Design tokens, components, navigation, screens, accessibility |
| `plans/2026-10-01-emberbyte-m1-foundation.md` | The finished M1 plan (history, and the style the M2 plan follows) |
| **`plans/2026-10-01-emberbyte-m2-real-usage.md`** | **THE PLAN YOU MUST EXECUTE: 21 tasks with complete code, tests and commands** |

Claude's execution records for M1 are in `.superpowers/sdd/2026-10-01-emberbyte-m1-foundation/` (git-ignored scratch): the ledger
`progress.md`, per-task reports, the two audit reports `m1-audit-report.md` and `m1-reaudit-report.md`. Read the re-audit report: it
tells you what was judged important last time.

# 3. What happened in M1 (the starting point)

M1 ("Foundation") is **done, audited and merged to `main`**, and its GitHub Actions run is green. It delivered:

- the Gradle skeleton with five modules and CI;
- `:core:engine`: the pure-Kotlin domain types from the contract (usage, plans, forecast, outcomes);
- `:ui:design`: the ember theme (dynamic colour on Android 12+, ember static palette below), `MorphingNumber` (a big number whose font
  weight/width follows live throughput), the **floating glass pill navbar** (spring motion, drag-to-snap, hide on scroll, Haze
  backdrop blur), Bento tiles, a bar row, app rows, byte formatting;
- `:core:data`: the repository **interfaces** and **in-memory fakes** only (no real measurement yet);
- `:app`: the Home screen (the big number with Bento tiles under it) running on fake data, plus the navbar and three placeholder tabs.

Numbers at the end of M1: 141 unit tests, lint with 0 errors, the app ran on an emulator without crashes. Known and accepted facts:
`compileSdk` is 37 because the dependencies require it; Material 3 is pinned to the **alpha** `1.5.0-alpha29` because the Expressive
APIs are not public in 1.4.0 (do not change that); Hilt was dropped.

The auditor found 8 Important issues in M1 (reduce-motion, accessibility labels, drag hit-testing, glass tint, transitions, run-out
date wording, README credit, missing tests) and they were all fixed. **Expect the same kind of scrutiny on M2.**

# 4. Your mission: milestone M2 "Real Usage"

Execute `docs/superpowers/plans/2026-10-01-emberbyte-m2-real-usage.md` **task by task, in order, from Task 1 to Task 21**. In short, you
replace the fake data with real measurement: a foreground **sampler service** (live speed + per-minute totals from the system traffic
counters), hourly **per-app history** from `NetworkStatsManager`, **Room** storage, a **live notification**, **DataStore** settings,
and the screens: Home on real data, Apps, App detail, History, Settings, first-run Onboarding. The plan also folds in the
small fixes the M1 audit deferred.

Not in M2: data plans and forecast (M3), widgets (M3), budgets/alerts/export (M4), Live Lens (M5), per-app screen time.

Before Task 1, read the **whole** plan once (it is long; the "Global Constraints", "Verified facts" and "Deviations" sections at the top
bind every task). Then scan it for contradictions between tasks or with the specs. If you find any, list them ALL to me in one message and
wait for my answer; if there are none, say nothing and start.

# 5. How you must work: one orchestrator, two sub-agents

You are the **orchestrator** ("mastermind") for this milestone. **You do not write production code or tests yourself.** You run two
sub-agents (use Antigravity's agent manager / multiple agents; if your version cannot run named sub-agents, run each role as a clearly
separated, fresh conversation/session and copy the prompts below):

| Role | Model | Reasoning | Job |
|---|---|---|---|
| **Implementer** | Gemini 3.8 Flash | **low** | Implements exactly one task from the plan: writes the failing test, sees it fail, writes the code, sees it pass, commits. |
| **Tester** | Gemini 3.8 Flash | **high** | A different, fresh agent. Verifies that task independently: re-runs the commands, checks the diff against the plan and the contract, hunts for bugs and missing tests. |

(If your model picker names the models differently, choose the closest "Gemini Flash" with the low / high thinking setting.)

**The loop for every task N, strictly sequential (never run two implementers at once):**

1. **Brief.** Extract the task text into a file the sub-agents will read (never paste the whole plan into a prompt):
   ```bash
   PLAN=docs/superpowers/plans/2026-10-01-emberbyte-m2-real-usage.md
   N=7   # the task number
   WS=.superpowers/sdd/2026-10-01-emberbyte-m2-real-usage     # git-ignored workspace; create it once
   mkdir -p "$WS"
   awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
   ```
2. **Record the base.** `BASE=$(git rev-parse --short HEAD)` and write it in the ledger.
3. **Dispatch the implementer** with the prompt template A below (fill in N, the brief path, the report path `$WS/task-$N-report.md`, and
   any decisions from earlier tasks that the brief cannot know).
4. **Read its short reply.** Statuses: `DONE`, `DONE_WITH_CONCERNS` (read the concerns; fix correctness ones before testing),
   `NEEDS_CONTEXT` (answer and re-dispatch), `BLOCKED` (give more context, or re-dispatch on a stronger model; if the plan itself
   is wrong, stop and tell me).
5. **Dispatch the tester** (a fresh agent) with template B: brief + implementer report + `BASE..HEAD`. The tester writes
   `$WS/task-$N-test-report.md` and returns `PASS` or `FAIL` with counts of Critical / Important / Minor issues.
6. **Fix loop.** On `FAIL` (any Critical or Important), send the implementer the findings verbatim, let it fix, then re-dispatch the tester
   for a scoped re-check of the fix commits only. **At most 3 fix rounds per task**; if still failing, stop and tell me with the evidence.
   Minor findings are not fixed in the loop: copy them into the ledger line `Task N: minor (deferred): ...`.
7. **Close the task.** Append to the ledger `Task N: complete (commits <base>..<head>, tester PASS)` and go to the next task.
   Do not stop to ask me whether to continue; the only reasons to stop are in section 9.

**The ledger** is `$WS/progress.md`. First line: `# Ledger — plan: docs/superpowers/plans/2026-10-01-emberbyte-m2-real-usage.md`. It is your
memory: if your context is ever reset, trust the ledger and `git log` over your recollection and **never re-dispatch a task that is marked complete**.

**Why two agents and not one:** the implementer is cheap and fast and follows the plan literally; the tester is the safety net and must
be sceptical and independent. Never let the same agent test its own work, and never accept a tester report that did not actually run the
commands (it must quote command output).

## Template A — implementer prompt

```
You are the IMPLEMENTER for Task <N> of the Emberbyte M2 plan. The plan contains the complete code; copy it
verbatim, do not improvise or redesign. Work efficiently.

Read first, completely:
1. <WS>/common-rules.md            (the project rules - section 7 and 8 of the orchestrator prompt)
2. <WS>/task-<N>-brief.md          (your requirements, with the exact code and commands)
Keep docs/superpowers/specs/2026-10-01-datakb-api-contract.md open; names and signatures there are the truth.

Context: <one line on where this task fits, plus decisions from earlier tasks the brief cannot know>

Do the steps in the brief IN ORDER. Where the brief says to write a failing test first, write it, RUN it and confirm it fails for
the stated reason, then implement, then run it again and confirm it passes. Run the module's full test task before committing.
If code in the brief does not compile because of tool/version drift, make the SMALLEST fix and record exactly what changed and why.
Never weaken, delete or skip a test to get a green build. If something in the brief seems wrong or ambiguous, stop and report
NEEDS_CONTEXT instead of guessing.

Work in the repository on branch m2-real-usage. Commit as the brief says (identity check first, see common-rules). Never push.

Write your full report to <WS>/task-<N>-report.md: what you did, every command you ran with the relevant output, test counts, files
changed, deviations from the brief and why, concerns. (For TDD steps include a RED and a GREEN section with real output.)
Reply with ONLY: Status (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT), commits (short SHA + subject), a one-line test
summary, concerns, and the report path.
```

## Template B — tester prompt

```
You are the TESTER for Task <N> of the Emberbyte M2 plan. You did NOT write this code. Your job is to try to prove the task is NOT
done, and to report honestly.

Read first, completely: <WS>/common-rules.md, <WS>/task-<N>-brief.md (requirements), <WS>/task-<N>-report.md (the implementer's claims:
treat them as unverified). Commit range to verify: <BASE>..HEAD on branch m2-real-usage.

1. SPEC CHECK: walk the brief requirement by requirement (files, signatures, values, test names, expected output). Compare every type
   and signature with docs/superpowers/specs/2026-10-01-datakb-api-contract.md (section 9 for M2). Note anything missing, extra or renamed.
2. RUN CHECK: run the verification commands the brief names YOURSELF (never trust the report's output) and quote the relevant output
   (pass/fail counts). Always include the module test task and, for the last step of a task, the full gate:
   ./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
3. TEST QUALITY: do the tests assert real behaviour? Would a plausible bug make them fail (try it on a scratch copy if cheap, and restore
   the files)? If an important behaviour has no test you MAY add a focused test in a separate commit "test: ..." (identity check first).
   You must NOT change production code; report production problems instead.
4. RULES: no hand-written animation durations (only MaterialTheme.motionScheme), no Room/DataStore types in :app, :ui:design has no
   :core:* dependency, no network permission, no leftover Fake* in production wiring, git status clean at the end.

Write the full report to <WS>/task-<N>-test-report.md with sections: Verdict, Spec findings, Run results (with quoted output),
Test-quality findings, Issues (each Critical / Important / Minor with file:line and a concrete fix).
Verdict is PASS (no Critical or Important) or FAIL. Reply with ONLY: Verdict, counts of Critical/Important/Minor, a one-line run
summary, commits you made, and the report path.
```

# 6. The environment (facts you do not need to rediscover)

- Windows 11. Use **Git Bash** for the commands in the plan (`./gradlew`, `awk`, `curl`, ...). PowerShell is fine for extra tooling.
- JDK 17 (Temurin) is on `PATH`. The Android SDK is at `C:\Users\Khaled\AppData\Local\Android\Sdk` (platforms 34–37, build-tools 36.x);
  `local.properties` with `sdk.dir` already exists (git-ignored). There is **no** `sdkmanager`.
- Gradle: use the wrapper `./gradlew`. The first run of a task can take several minutes; give commands generous timeouts (up to 10 minutes)
  and run them in the foreground. Tools like Room's KSP and Robolectric download artifacts the first time.
- Emulator: the AVD is named **`Violet_API_36`** (`$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Violet_API_36 -no-snapshot-save`);
  `adb` is `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. Only Task 21 needs it.
- Room + KSP were verified to work on this exact toolchain (the recipe is in Task 1); do not use `kapt`.
- Line endings: `.gitattributes` forces LF; git may print harmless "LF will be replaced by CRLF" warnings.

# 7. Rules for every agent (sections 6, 7 and 8 are already saved as `<WS>/common-rules.md`, which both templates point to; if it is missing, recreate it from these three sections)

**Git** (the project's `CLAUDE.md` applies in full):
- Work on branch **`m2-real-usage`**, created from `main` (`git checkout -b m2-real-usage main`) before Task 1. Stay on it.
- **Before EVERY commit** run `git config user.name` (must be `Khaledbahaaeldin`) and `git config user.email`
  (must be `khaled.bahaaeldin@aiu.edu.eg`). If wrong, fix with **repo-local** `git config` only (never `--global`). Also check
  `git remote -v` shows `git@github.com:Khaledbahaaeldin/DataKB.git`.
- Khaled has authorised the commits that the plan's steps call for, on this branch only. Prefer small, focused commits. Commit messages
  explain **why**, not what. End each commit message with the line
  `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- **Never** push, force-push, amend a pushed commit, merge into `main`, rebase published history, or use `--no-verify`. Never run
  `git clean`/`git reset --hard`. Never commit `local.properties`, `build/`, `.gradle/`, `.superpowers/`, `CLAUDE.md`, `.agent/`.

**Scope and honesty**
- Do exactly what the plan says. Do not add features, dependencies, abstractions or "improvements". Do not change AGP, Kotlin, Gradle,
  Compose or Material versions. If something forces a deviation, make the smallest one and record it in the task report **and** the ledger.
- Never weaken, delete or skip a test to get green. If a test is wrong because the plan is wrong, say so and stop (section 9).
- Report outcomes faithfully: failing tests, skipped steps and things you could not verify must be stated plainly.
- Do not edit the files under `docs/superpowers/specs/` (Claude owns the specs). If a spec seems wrong, write the question in
  `$WS/spec-questions.md` and continue with the plan.

# 8. Quality bar (what the auditor, Claude on Opus, will check against)

Your work will be audited against the specs and the API contract, with the same rigour as M1. Typical findings to avoid:

- A name, signature, default or nullability that differs from the contract (compare field by field).
- Motion that does not go through `MaterialTheme.motionScheme`, or that ignores the system "remove animations" setting.
- Missing accessibility: every interactive element needs a text label or content description, touch targets of at least 48 dp, state not
  conveyed by colour alone, sensible semantics (a chart needs a text alternative).
- Architecture leaks: Room or DataStore types visible in `:app`; `:ui:design` depending on `:core:*`; Android classes in `:core:engine`.
- Tests that assert nothing, or behaviours with no test (especially edge cases, error paths and time/zone logic).
- Data honesty: gaps, estimates and missing permissions must be visible, never silently hidden.
- Hard-coded user-visible strings are acceptable in M2 (string resources are scheduled for M3), but they must be plain, correct English.

After **every** task the full gate must still pass: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
(lint ERRORS are failures; warnings are noted).

# 9. When to stop and talk to me

Stop and report to me (with evidence: the failing command output, the plan text it collides with, and what you tried) ONLY when:

1. A task is still failing after 3 fix rounds;
2. an agent reports `BLOCKED` and more context or a stronger model does not help;
3. the plan is internally contradictory or contradicts the contract;
4. a toolchain problem makes the build impossible (for example Room/KSP, AGP, an emulator that will not start);
5. you would have to do something the rules forbid (push, change versions, edit specs, weaken a test).

Otherwise keep going without asking "should I continue?".

# 10. Finish

When Task 21 is done and its tester report says PASS:

1. Make sure `git status` is clean, you are on `m2-real-usage`, and nothing was pushed.
2. Write `$WS/m2-completion-report.md`: the list of tasks with commit ranges and tester verdicts, the final test counts per module, every
   deviation from the plan (with the reason), every deferred minor finding, every open concern, the Task 21 checklist results, and the
   hand-off list for real devices.
3. Print a short summary for me: number of commits, test counts, the deviations and anything I must decide.
4. **Stop.** Do **not** audit your own work, do **not** merge, do **not** push, do **not** start M3. Tell me:
   "M2 is ready for the Claude audit on branch m2-real-usage." Khaled will then ask Claude (Opus) to run the milestone auditor and the lead
   QA spec engineer; Claude will also write the plan for the next milestone.

Start now with section 0, then confirm in one short message and begin Task 1.
