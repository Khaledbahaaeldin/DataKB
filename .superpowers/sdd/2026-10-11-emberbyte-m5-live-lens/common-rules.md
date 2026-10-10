# Common Rules for Emberbyte M5 Live Lens

## 1. Absolute Git & File Protection Rules (VERBATIM)
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A` in this repository. To change branches use `git switch <branch>` only.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/` (Claude's files, committed or not).
- Stage only the files you changed, by explicit path. If a build or test needs a clean tree, ask the orchestrator.
- After EVERY subagent returns, run `git status --short` and `git diff --stat HEAD -- docs/superpowers` BEFORE trusting its report; if anything under `docs/superpowers` changed, or `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.

## 2. Testing & Quality Invariants
- A task with no production code and no test in its diff is not done, whatever the subagent says. Read the diff.
- Reports are checked line by line. Quote only what you ran, with the commands. Real test names (`grep` them). "not run" / "CANNOT-VERIFY" when true. Never "tester PASS" without a tester report file ("self-verified, no separate tester report"). Never "immediately" for something that takes a poll or a refresh. List EVERY deviation and EVERY pre-existing test you changed; "Deviations: None" is almost never true.
- Tests must be able to fail. When a plan says "prove it fails without the fix", do it and quote the commands; a test that passes with the fix reverted is worthless.
- UI, VPN, lifecycle and network work is not done until it works on the emulator, with quoted evidence (command output, `uiautomator` text, screenshot paths you actually OPENED and described). Restore every device setting you change. Always use `adb -s emulator-5554`: a physical phone may be attached and must never be touched.
- Never delete or overwrite a test, never `@Ignore`, no new dependency without the user's approval, JDK 17 (no Java 18+ APIs), never push or merge.
- Gate command after every task: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`

## 3. Privacy Rules of Lens (Critical Defect on Violation)
- Payload bytes are never stored, logged, or kept after the packet is relayed.
- Logs never contain IP addresses, domain names or other apps' UIDs (counts and sizes only).
- Lens is off by default and never starts without explicit user consent.

## 4. Git Identity & Commit Formatting
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com` (global account).
- Branch: `m5-live-lens`. Never push, never merge.
- Every commit message must explain WHY and end with:
  `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`
