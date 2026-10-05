# Prompt for Antigravity: M3 fix wave 2 (the last one)

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the previous M3 prompts.

---

# 0. Where we are

Claude's re-audit of your fix wave found **no Critical or Important code defect** (rollover, "Estimated" flags, the deep-link back stack, editor validation, saved input and the alert fixes were all verified correct, 672 tests, 0 lint errors). The device QA re-run also passed navigation, plan numbers, editor safety, alerts and regression. Two things remain:

1. **The completion report again contains false statements** (re-audit finding I-R1): five test names that do not exist, "Deviations: None" while there were several, an incomplete list of changed tests, wrong explanations of two lint warnings, and thin emulator evidence. This is the only thing that blocks closing M3.
2. **Five Minor device findings** from the QA re-run (`QA4-01`, `QA4-02`, `QA3-01`, `QA2-01`, `QA2-02`) and two removable lint warnings.

Reports: `.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave/m3-reaudit-report.md` and `m3-qa-rerun-report.md`. Read both.

# 1. Rules (absolute)

1. Never touch Claude's files: everything under `docs/superpowers/specs/` and `docs/superpowers/plans/`. NEVER run `git checkout`, `git restore`, `git stash`, `git clean`, `git reset` or `git add .` / `git add -A`. Stage only your files by explicit path. Copy this rule block verbatim at the top of EVERY implementer and tester brief. After every subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` before trusting its report; if anything under `docs/superpowers` changed, STOP and tell the user.
2. Quote only what you ran; real test names (`grep` them); "not run" / "CANNOT-VERIFY" when true; no "tester PASS" without a tester report file; a task with no production code or test in its diff is NOT done.
3. Never delete or overwrite a test; no new dependency; JDK 17.
4. UI work is proven on the emulator by its tester with quoted evidence; restore every device setting.

# 2. Your job

Execute **`docs/superpowers/plans/2026-10-06-emberbyte-m3-fix-wave-2.md`**: tasks **J1** then **J2**, on branch `m3-plans-forecast-widgets` (the contract, including section 10.5, is on disk and committed; it wins over the plan).

Same orchestration protocol as before: implementer Gemini 3.8 Flash (low) then a fresh tester Gemini 3.8 Flash (high) per task, ledger `progress.md` in `WS=.superpowers/sdd/2026-10-06-emberbyte-m3-fix-wave-2` (create it, copy `common-rules.md`), briefs extracted with:
```bash
PLAN=docs/superpowers/plans/2026-10-06-emberbyte-m3-fix-wave-2.md
N=J1
WS=.superpowers/sdd/2026-10-06-emberbyte-m3-fix-wave-2
awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
```
At most 3 fix rounds per task; never push or merge. One commit for J1 with the message in the task; J2 commits only the README sentence. Commit messages end with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`; identity check first.

# 3. Environment

Unchanged (Windows 11, Git Bash, JDK 17, AVD `Violet_API_36`, `adb` under `$LOCALAPPDATA/Android/Sdk/platform-tools`, Python 3). Baseline at `5e66f0e`: 672 tests, 0 lint errors, 11 `:app` lint warnings. If the emulator runs low on storage, free space by uninstalling old builds and say so.

# 4. Finish

After J2: nothing tracked is modified except your own commits, nothing pushed. Print a short summary (commits, test counts per module, lint warnings, deviations), then **stop** and say: **"M3 fix wave 2 is ready for the Claude final check on branch m3-plans-forecast-widgets."**

Start by reading, confirm in one short message, and begin J1.
