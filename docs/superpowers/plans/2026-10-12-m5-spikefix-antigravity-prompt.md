# Prompt for Antigravity: M5 spike fix wave

> Paste everything below the line into Antigravity, in the project directory. Same project, branch and protocol as the M5 prompt. Part 3 of M5 stays blocked.

---

# 0. What happened

You (Omar, with Antigravity) finished M5 parts 1 and 2 (A1, A2, S1, S2, S3) and stopped at the spike gate, as asked. Claude ran an Opus audit and a device QA on branch `m5-live-lens` (HEAD `5094b22`). Read both reports completely: `.superpowers/sdd/2026-10-11-emberbyte-m5-live-lens/m5-audit-report.md` and `m5-qa-report.md` (they are in your working tree if you pull; if not, ask the user for them).

- **A1 and A2 are accepted** (the lock test fails 3 of 3 without the `Mutex`; the History charts open on the newest period), with Minor follow-ups.
- **The spike decision "KOTLIN CONFIRMED" is NOT accepted.** The decision record's own numbers fail the plan's rule: 11.9 Mbit/s against 40, and 95.7 CPU-seconds per 100 MiB against 2.0. The S3 brief given to the implementer and tester had silently dropped the decision rule, so nobody applied it. The device QA re-measured 25.9 to 32.9 CPU-seconds per 100 MiB.
- **The cause is a fixable Kotlin bug, not a limit of Kotlin:** after the server's end of stream the socket stays registered for reading, so the selector spins and writes thousands of FIN packets per second. The audit also found that a FIN acknowledges data that was never relayed (silent upload truncation), that the app's TCP window is not honoured, that names are read only from the first segment, and more (C1, C2, I1 to I8).
- 15 of 23 mutants of the forwarder survive the tests; the parser fuzz tests are nearly vacuous; one 10 MB test is flaky (failed 2 of 5 runs on the unmodified code).

So the status is **"not decided yet"**. This wave fixes the forwarder test-first and produces an honest, reproducible measurement.

# 1. Rules (absolute)

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; use `git switch` for branches only. Never touch anything under `docs/superpowers/` (Claude's files). Stage only your files by explicit path. **Copy this rule block verbatim at the top of EVERY implementer and tester brief**, and after every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`; stop if anything there changed or `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1.
2. **Briefs are never edited.** Produce each brief ONLY with the `awk` command below and verify with a `diff` against the plan section that nothing is missing (the first S3 brief lost the whole decision rule). Never paraphrase a brief.
3. A task with no production code and no test in its diff is not done. Quote only what you ran, with commands and raw output; real test names; "not run"/"CANNOT-VERIFY" when true; no "tester PASS" without a tester report file; no PASS line that is not computed from a measurement; list every deviation and every changed pre-existing test.
4. **Every new test must be shown to fail on the unfixed code** (use a throwaway copy made with `git archive`, never `git stash`/`checkout`) and quoted; the whole `:feature:lens` suite must pass five consecutive runs.
5. No new dependency; JDK 17; never push or merge; use the git identity configured in your checkout; privacy rules (no payload, addresses, names or foreign UIDs in logs or stored data).
6. UI/VPN/lifecycle work is proven on the emulator (`Violet_API_36`, always `adb -s emulator-5554`, never touch a physical phone), with opened and described screenshots; restore every device setting.
7. Implementer: Gemini 3.8 Flash (low); tester: a fresh Gemini 3.8 Flash (high); do not substitute a smaller model.

# 2. Your job

Branch `m5-live-lens` (`git switch m5-live-lens`; no other branch). Execute **`docs/superpowers/plans/2026-10-12-emberbyte-m5-spike-fix-wave.md`**: tasks **Q1, Q2, Q3, Q4, Q5**, in order. Read the plan header and contract section 12 first. Same orchestration as before (`docs/superpowers/plans/2026-10-01-m2-antigravity-prompt.md` sections 5 to 9, templates A and B, `common-rules.md`; at most 3 fix rounds).

- **Workspace:** `WS=.superpowers/sdd/2026-10-12-emberbyte-m5-spike-fix-wave` (git-ignored; do NOT `git add` anything under `.superpowers`). Ledger `progress.md`, first line `## M5 spike fix wave (plan docs/superpowers/plans/2026-10-12-emberbyte-m5-spike-fix-wave.md)`.
- **Briefs:**
  ```bash
  PLAN=docs/superpowers/plans/2026-10-12-emberbyte-m5-spike-fix-wave.md
  N=Q1
  WS=.superpowers/sdd/2026-10-12-emberbyte-m5-spike-fix-wave
  awk -v n="$N" 'BEGIN{p="### Task " n ":"} index($0,p)==1{f=1;print;next} f&&/^### Task /{exit} f' "$PLAN" > "$WS/task-$N-brief.md"
  ```
  Each brief also gets the plan's "Rules" block (it is part of the plan header) and contract section 12.
- One commit per task with the message in the task. Device sessions: Q4 and Q5.

# 3. Environment

Unchanged (Windows 11, Git Bash, JDK 17, Android SDK, Python 3). The AVD's `10.0.2.2` path alters TCP streams larger than about 16 KB even without Lens, and the image has no `curl`: Q5 explains how to measure around it. A stray `python -m http.server` from earlier runs may hold port 8000: check `netstat -ano | findstr :8000` before starting yours, and stop yours afterwards.

# 4. When to stop and talk to the user

After Q5 (always); a task failing after 3 fix rounds; a `BLOCKED` agent; a contradiction between plan and contract; the measurement saying `NATIVE NEEDED`; any missing or changed file of Claude's; anything that breaks the rules above.

# 5. Finish

After Q5: nothing modified except git-ignored files, nothing pushed, you are on `m5-live-lens`. Print the Decision section and the per-gate table, then **stop** and say exactly: **"The Lens spike fix wave is done: decision <KOTLIN CONFIRMED | KOTLIN CONFIRMED PENDING REAL-PHONE RUN | NATIVE NEEDED>. Waiting for Claude to review docs/spikes/lens-forwarder.md before part 3."**

Start by reading, confirm in one short message, and begin task Q1.
