# Prompt for Antigravity: the Expressive Redesign (Milestones 7 to 11, one run)

> Paste everything below the line into Antigravity, in the project directory. It runs five milestones in one session with **five planned stops** where you wait for Claude. Between stops you do not ask the user anything the plan already decides.

---

# 0. Where the project stands

**Emberbyte** (repo codename DataKB) is an open-source (GPL-3.0-or-later) native Android data-usage monitor. Claude is the mastermind (specs, plans, audits); you orchestrate and a Gemini implementer builds. **M1 to M4 and M6 are closed and merged to `main`** (merge commit `467c41b`, CI green, 1068 unit tests, lint 0 errors and 11 warnings). M5 (Live Lens) was discarded (`docs/adr/0001-discard-live-lens.md`): **never add a VPN, `INTERNET` permission or network code.** Nothing is tagged or released: versionName `1.0.0`, versionCode `1` stay; the redesign ships in the same release.

This run is the **Expressive Redesign**, five milestones, one branch:

| Milestone | Tasks | What |
|---|---|---|
| M7 Foundation | A1 to A16 | 12 themes (light, dark, AMOLED), typography, shapes, app icons, new components, compact navbar, Appearance screen |
| M8 Core screens | B1 to B7 | Home, Apps, App detail, History, shared states |
| M9 Plans | C1 to C10 | mid-cycle plans, Room v4, backup v2, urgency and pin, bento Plans, guided flow |
| M10 Surfaces | D1 to D7 | onboarding, Settings, dialogs, live notification colours, licences screen |
| M11 Widgets | E1 to E8 | four widgets |
| Finish | Z1, Z2 | docs, store screenshots, final proof |

# 1. What M2 to M6 taught us (read this twice)

1. **Claude's files were destroyed twice** because a subagent ran `git checkout` on them. The absolute rules are the **Rules block in the plan header** (`docs/superpowers/plans/2026-10-17-emberbyte-redesign-m7-m11.md`, section "Rules"). Copy that block VERBATIM at the top of EVERY implementer and tester brief. After EVERY subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` BEFORE trusting its report. If anything under `docs/superpowers` changed, or `grep -c "## 13. Milestones 7 to 11" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.
2. **Briefs are never edited.** Produce each brief ONLY with the `awk` command of section 3, then `diff` it against the plan section. Never paraphrase or summarise.
3. **A task with no production code and no test in its diff is not done** (except the proof and tooling tasks, whose deliverables the plan names). Read the diff.
4. **Reports are checked line by line.** In M6 the implementer's reports repeatedly contained false claims (invented test names, wrong counts, a "PASS" that was never run). So: counts come from `python tools/test-diff.py` and `git diff --stat` (task A1 creates the tool; until then count with `git diff` yourself and say so), cited tests are checked with `python tools/check-cited-tests.py <report>` and the exit code is pasted, device claims come from screenshots you OPENED and described, and anything you could not verify is written `CANNOT-VERIFY` with the reason. **No PASS line that is not computed.**
5. **Tests must be able to fail.** Show every new or changed test failing on the code before the change (temporary revert in a `git archive` copy; never stash) and quote it. Run new tests 10 times in isolation for flakiness. Never delete, rename, weaken or `@Ignore` an existing test, except where the plan says a component is replaced (task A13): list each such test with the test that now covers the behaviour, in the report and in `docs/redesign-test-changes.md`.
6. **UI work is not done until it works on the emulator**, with screenshots you opened and described. Restore every device setting you change (font scale, density, animation scales, locale, night mode).
7. **One emulator only, on the AVD `Violet_API_36`, always `adb -s emulator-5554`.** The user's machine has limited RAM: duplicate emulators were killed before. A physical phone may be attached and must never be touched. Never run two Gradle builds at once. On small machines use `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2`.
8. Never push, merge or tag. No new dependency (removing Haze in A13 is allowed). JDK 17. Privacy: no payloads, package names, addresses or UIDs in logs.
9. Use the models the plan names: implementer Gemini 3.8 Flash (low), tester a fresh Gemini 3.8 Flash (high). Do not substitute a smaller model. Tasks A15's palette checkpoint and every `STOP` are for Claude and the user, not for you to decide.

# 2. Your job

Branch **`redesign`**. It should already exist on origin with the design, plan, contract section 13, mockups and palette tools committed: `git fetch origin && git switch redesign`. If it does not exist, STOP and tell the user (do not create it, do not use `main`). Record `git rev-parse HEAD` as START in the first ledger line and compute the test baseline (`:app` 471, `:core:data` 323, `:core:engine` 125, `:ui:design` 149 = 1068) once to confirm it.

Before task A1 read, in this order: the plan header (Goal, Global Constraints, Review Focus, Rules, "How the run is organised"); **API contract section 13** (the plan implements it one to one; where plan and contract differ the contract wins and you stop and tell the user); the design document `docs/superpowers/specs/2026-10-17-expressive-redesign-design.md`; the mockups README `docs/design/expressive/mockups/README.md` and then, per task, the mockup HTML the task names.

Execute **every task of the plan in order: A1 to A16, B1 to B7, C1 to C10, D1 to D7, E1 to E8, Z1, Z2.** Do not reorder. Do not skip a task because it looks done.

# 3. Orchestration protocol

You are the orchestrator and write no production code yourself. One task at a time: implementer, then (for proof tasks A16, B7, C10, D7, E8, Z2) a fresh tester, then the gate, then the ledger.

- **Workspace:** `WS=.superpowers/sdd/2026-10-17-emberbyte-redesign` (git-ignored; never `git add` anything under `.superpowers`). Ledger `$WS/progress.md`, first line `## Redesign (plan docs/superpowers/plans/2026-10-17-emberbyte-redesign-m7-m11.md), START=<sha>`, then `Task A1: started <time>` and `Task A1: complete <sha>`; one line per fix round.
- **Briefs (the only way to make one):**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-17-emberbyte-redesign-m7-m11.md
  WS=.superpowers/sdd/2026-10-17-emberbyte-redesign
  N=A1
  mkdir -p "$WS"
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&(/^### Task /||/^# PART /){exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Every brief is built as: the Rules block (verbatim) + the plan's Global Constraints + the task text from `awk` + contract section 13 + (for UI tasks) the path of the mockup to read. Then `diff` the task text in the brief against the plan.
- **Implementer template (A):** "You are implementing task <N> of the plan. Follow its steps in order: failing test first, show it failing, implement, show it passing, run the gate lines the task names, commit with the message in the task plus a body of at least two lines explaining WHY and the trailer `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Report in `task-<N>-report.md`: what you did, the raw output of each command you ran, the computed test counts (`tools/test-diff.py`), every deviation, every pre-existing test you changed and why, anything CANNOT-VERIFY. If a step cannot be done as written, stop and report BLOCKED with the reason; do not improvise a different design."
- **Tester template (B), proof tasks only:** "You did not write this code. Verify task <N> independently: re-run the gate yourself, read the diff against the task text, run the device proof steps the task lists on a CLEAN install, open and describe every screenshot, try to break it (font scale 2.0, RTL, animations off, rotation, process death), and write `task-<N>-test-report.md` with raw evidence, defects found (with severity) and CANNOT-VERIFY items. You may not edit production code."
- **Fix rounds:** at most 3 per task. After 3, STOP and tell the user.
- **Device sessions:** one at a time, clean install each, restore every setting, `adb emu kill` at the end of the session.
- **Commits:** one per task (A15 and D-style fix commits as the plan names), identity of your own checkout, never `--no-verify`.

# 4. Environment and traps

- Windows 11, Git Bash for the plan's commands, JDK 17, Android SDK at `C:\Users\Khaled\AppData\Local\Android\Sdk` (adjust to your machine), Python 3. Gradle runs of up to 10 minutes are normal.
- The `GeneratedPalettes.kt` file is generated: change `tools/palettes/palettes.json` and run `python tools/palettes/generate_palettes.py`, never edit the Kotlin by hand.
- Material 3 `1.5.0-alpha29` Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Where a stock component does not give the look, build on `Canvas`/`Surface` and say so in the report.
- Release signing: never commit a keystore or password; sign test APKs with a throwaway key outside the repository.
- A stray emulator, `python -m http.server` or `adb forward` from an earlier session may hold resources: check and clean up first.
- Do not leave docs half-written between tasks: each task commits complete files.

# 5. The five planned STOPS

At each, print the message below (fill the fields from your ledger and the computed numbers), make sure `git status` shows nothing modified apart from git-ignored files, nothing is pushed, then **wait**. Claude (and the user) will answer in this session.

- **S1, after A15, palette checkpoint:** "PALETTE CHECKPOINT on branch redesign. 12 themes x light/dark/AMOLED generated; the contrast gate is green. Screenshots: <WS>/palette-shots/ (36 images), report docs/design/expressive/palette-checkpoint.md. Waiting for Claude and the user to review the colours." Do not start A16 until you are told to continue (and, if colours change, only the palette JSON, the regenerated Kotlin and the contrast results change).
- **S2, after A16, M7 audit:** "M7 FOUNDATION is ready for the Claude audit on branch redesign." Add: commits, test counts per module (computed), deviations, CANNOT-VERIFY items, the replaced-test mapping, open concerns, and the open question about polish items D5 to D14 if the plan's task B6 could not find them.
- **S3, after C10, M9 audit:** "M9 PLANS is ready for the Claude audit on branch redesign." Same fields, plus the upgrade-from-M6 result.
- **S4, after D7, M10 audit:** "M10 SURFACES is ready for the Claude audit on branch redesign." Same fields.
- **S5, after Z2, final audit:** "THE REDESIGN is ready for the final Claude audit on branch redesign." Same fields, plus: what the user must still do (tag `v1.0.0`, optional GitHub release, the fdroiddata merge request, the real-device checklist). Do not audit yourself, merge, push or tag.

Between stops M8 (B1 to B7) and M10 (D1 to D7) and M11 (E1 to E8) are NOT stops: proof tasks B7 and E8 run, you continue straight on. After a stop, continue with the next task only when told; if told "fix X", fix only X in a new commit and stop again.

# 6. Other reasons to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; a deep link or API the plan says to verify and cannot be found (E4, E6); a palette licence that is not a free-software licence (A16); a changed or missing file of Claude's; a dependency that will not resolve; the emulator unable to start; or anything that would break the rules above. Do not ask the user for decisions the plan already makes.

# 7. What Claude will check afterwards

An Opus auditor and a device QA engineer repeat the work at each stop: contract section 13 line by line; the contrast registry and `NoHardcodedColorsTest` by breaking a colour on purpose; every test by mutation (a test that passes both ways is rejected); the replaced-test mapping; the Room 3 to 4 upgrade with data; the backup `contractVersion` 1 and 2 files; the mid-cycle plan cases; the four widgets on the device and their image budget; the live notification; the release APK (no `INTERNET`, no debug classes, reproducible by building twice); font scale 2.0, RTL and animations off on every screen; the test diff; the lint list; and every line of your completion reports. Please make that boring.

Start by reading, confirm in one short message, and begin task A1.
