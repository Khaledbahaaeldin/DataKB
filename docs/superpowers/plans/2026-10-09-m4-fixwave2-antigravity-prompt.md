# Prompt for Antigravity: M4 fix wave 2 (the last one)

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the previous M4 prompts.

---

# 0. Where we are

Claude's re-audit of your M4 fix wave found the L1 and L2 code **correct** (backup and restore off the main thread, the 64 MiB header bound, the out-of-memory mapping, integer spike comparison, oldest-row history rule, restore checks, withdrawn notification; 951 tests, 0 lint errors, 8 `:app` warnings) and a device QA re-run confirmed it (backup took about 1.6 s with no frame stalls, a hostile header gives a message instead of a crash, budgets equal the Apps "Mobile" figure, the 23.8 MB jump did not reproduce). What remains:

1. **A deep link that arrives while onboarding is already on screen** (through `onNewIntent`) is still replayed after "Continue" and opens Settings. Your `ColdStartLinkTest` NavHost test never gave the link to the NavHost, so it could not fail. Only the cold-start case was fixed.
2. **Rotating the device while the "Create backup" file picker is open** loses the passphrase and leaves a 0-byte backup file in Downloads (no crash).
3. Small items: the lock tests passed without the lock in the full run (nondeterministic), the overflow clamp in `BudgetCalculator` gives a wrong state for an absurd limit, a restore read that runs out of memory is not handled, a double tap can start two operations.
4. The completion report has false statements. **Claude will append the corrections to it; do not rewrite it.**

Reports: `.superpowers/sdd/2026-10-08-emberbyte-m4-fix-wave/m4-reaudit-report.md` and `m4-qa-rerun-report.md`. Read both.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; use `git switch` for branches only. Never touch anything under `docs/superpowers/` (Claude's files, committed or not). Stage only your files by explicit path. **Copy this rule block verbatim at the top of EVERY implementer and tester brief**; after every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`, and stop if anything there changed or `grep -c "## 11. Milestone 4" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
2. A task whose diff has no production code and no test is NOT done. Quote only what you ran; real test names; "not run"/"CANNOT-VERIFY" when true; no "tester PASS" without a tester report file (for M2 write exactly "self-verified, no separate tester report"); list every deviation and every changed pre-existing test.
3. Never delete or overwrite a test; no `@Ignore`; no new dependency; JDK 17.
4. UI work is proven on the emulator with quoted evidence; open and describe every screenshot; restore every device setting.
5. Implementer model: Gemini 3.8 Flash (low), tester: a fresh Gemini 3.8 Flash (high). Do not substitute a smaller model.

# 2. Your job

Execute **`docs/superpowers/plans/2026-10-09-emberbyte-m4-fix-wave-2.md`**: tasks **M1** then **M2**, on branch `m4-insight` (`git switch m4-insight`; no other branch). Read the plan and contract section 11.2 first.

Orchestration as before (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, templates A and B, `common-rules.md`; at most 3 fix rounds; never push or merge). `WS=.superpowers/sdd/2026-10-09-emberbyte-m4-fix-wave-2` (create it, copy `common-rules.md`, ledger `progress.md`). Briefs:
```bash
PLAN=docs/superpowers/plans/2026-10-09-emberbyte-m4-fix-wave-2.md
N=M1
WS=.superpowers/sdd/2026-10-09-emberbyte-m4-fix-wave-2
awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
```
One commit for M1 with the message in the task; M2 commits nothing. Device sessions (AVD `Violet_API_36`, clean installs, restore every setting, `adb emu kill`): M1 and M2.

# 3. Environment

Unchanged. Baseline at `0ac4ca1`: 951 tests, 0 lint errors, 8 `:app` lint warnings.

# 4. Finish

After M2: nothing tracked is modified, nothing pushed, you are on `m4-insight`. Print a short summary (commits, test counts, lint warnings, deviations), then **stop** and say: **"M4 fix wave 2 is ready for the Claude final check on branch m4-insight."** Do not merge, push or start M5.

Start by reading, confirm in one short message, and begin task M1.
