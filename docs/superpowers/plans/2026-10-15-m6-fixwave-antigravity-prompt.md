# Prompt for Antigravity: M6 fix wave

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the M6 prompt (`docs/superpowers/plans/2026-10-14-m6-antigravity-prompt.md`). Nothing is released or pushed.

---

# 0. What happened

You finished M6 (P1 to P7, HEAD `36f878e`). Claude ran an Opus audit and a device QA on it. Read both reports completely first: `.superpowers/sdd/2026-10-14-emberbyte-m6-polish/m6-audit-report.md` (findings 1 to 22) and `m6-qa-report.md` (defects D1 to D24).

- **Sound:** the clean gate (1040 tests, 0 lint errors, 11 warnings identical to `main`), the byte-identical release APK, the release smoke path on the emulator, the upgrade data path, the licences screen.
- **Not accepted:** the accessibility proof is partly vacuous (mutants survive, a test performs the focus return itself, 7 cited tests do not exist); the live-update test runs on SDK 35 with shadows and the production code hides platform calls behind catches; the release documents say things that are not true (notification "upload", backup "settings", chip "throughput", missing permissions and licences, no privacy policy exists); and your reports contain false statements ("Deviations: None", "5 clean screenshots", "16.6 MB", "852 entries", 22 test names that do not exist).
- **Not in this wave:** the visual polish the user is doing separately, and the store screenshots (recaptured after that polish). Do not touch them.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; `git switch` for branches only. Never touch `docs/superpowers/` (Claude's files; the file `docs/superpowers/plans/2026-10-15-emberbyte-m6-fix-wave.md` is uncommitted and must stay as it is). **Copy this rule block verbatim at the top of EVERY implementer and tester brief.** After every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`; stop if a tracked file changed or `grep -c "## 12. Milestone 6" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
2. **Briefs are never edited.** Produce them ONLY with the `awk` command below and `diff` them against the plan. Never paraphrase a brief.
3. Quote only what you ran; "not run"/"CANNOT-VERIFY" when true; no "tester PASS" without a tester report file; list EVERY deviation and every changed pre-existing test. **Before you write a report run `python tools/check-cited-tests.py <report files>` (task F4 creates it; until then `grep -rn "fun <name>"` for every cited name) and paste its output.** No PASS line that is not computed.
4. **Every new or changed test must be shown failing on the unfixed code** (revert the production lines temporarily or use a throwaway `git archive` copy) and quoted. A test that passes both ways is rejected. The whole suite passes five consecutive runs for tests touching animation or timing.
5. No new dependency, JDK 17, Room schema stays at 3, no `INTERNET`, no `VpnService`, never push, merge or tag. Nothing under `.superpowers` is ever added to git.
6. Device work only on `Violet_API_36` with `adb -s emulator-5554`; a physical phone is attached and must never be touched. Clean install, restore every setting, `adb emu kill`.
7. Implementer Gemini 3.8 Flash (low), tester a fresh Gemini 3.8 Flash (high).

# 2. Your job

You are on branch `m6-polish` (local, not pushed; do not switch). Record `git rev-parse HEAD` as START. Execute **`docs/superpowers/plans/2026-10-15-emberbyte-m6-fix-wave.md`**: tasks **F1, F2, F3, F4, F5**, in order. Same orchestration as before (protocol and templates in `docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, `common-rules.md`; at most 3 fix rounds per task).

- **Workspace:** `WS=.superpowers/sdd/2026-10-15-emberbyte-m6-fix-wave` (git-ignored). Ledger `$WS/progress.md`, first line `## M6 fix wave (plan docs/superpowers/plans/2026-10-15-emberbyte-m6-fix-wave.md), START=<sha>`.
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-15-emberbyte-m6-fix-wave.md
  N=F1
  WS=.superpowers/sdd/2026-10-15-emberbyte-m6-fix-wave
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Each brief also gets the plan's header (the paragraphs "Why", "Scope" and "Rules"), contract section 12, and the audit/QA finding numbers it names.
- One commit per task with the message in the task. Device sessions: F1 (notification dump), F2, F3 (release smoke), F5.

# 3. When to stop and talk to the user

A task failing after 3 fix rounds; a `BLOCKED` agent; Robolectric having no usable API 36 platform (F1: show the failing output); a fix that would change visual design beyond what the task says (say what and why); R8 problems you cannot solve; anything breaking the rules above.

# 4. Finish

After F5: tree clean except git-ignored files, nothing pushed, no tag, branch `m6-polish`. Print a short summary (commits, per-module test counts, mutants table, deviations, CANNOT-VERIFY) and say exactly: **"The M6 fix wave is done. Waiting for Claude to re-audit on branch m6-polish."** Do not audit yourself, merge, push or tag.

Start by reading, confirm in one short message, and begin task F1.
