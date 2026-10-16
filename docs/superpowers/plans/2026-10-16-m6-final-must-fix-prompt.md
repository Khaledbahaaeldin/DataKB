# Prompt for Antigravity: M6 final must-fix items (direct, single agent)

> Paste everything below the line into Antigravity, in the project directory. **No subagents, no briefs, no tester, no ledger.** You fix the two items below yourself and stop. Claude tests afterwards.

---

You are working directly on Emberbyte (Android, Kotlin, Compose, JDK 17), branch `m6-polish` (local, not pushed), HEAD `bb815ea`. Do not spawn subagents.

## Rules

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`. Stage only your files by explicit path. Never touch anything under `docs/superpowers/` (Claude's files). Never add anything under `.superpowers` to git. Never push, merge or tag.
2. No new dependency, JDK 17, Room schema stays at 3, no `INTERNET`, no `VpnService`.
3. Do not touch the user's visual polish (Home 7-day row, History bar value readout, App detail row start, widget layout, translucent navbar, licences typography, Plans empty state, archive confirmation, store screenshots).
4. Every behaviour you fix gets a test that FAILS on the old code (temporarily revert, run, see it fail, put the change back) and passes 10 runs in isolation. Never delete or rename a test.
5. One emulator only (`Violet_API_36`, `adb -s emulator-5554`; a physical phone is attached and must never be touched). One Gradle build at a time with `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2`. Restore device settings and `adb emu kill` at the end. Statements about the device come from `adb -s emulator-5554 exec-out screencap -p` images you opened and looked at.
6. Commit each item separately; the message explains WHY in at least two lines and ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
7. Gate after each item: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --stacktrace` (baseline 1065 tests, 11 lint warnings, no new warning).

## Item 1: the budget dialogs hide Save and Cancel under the keyboard at font scale 2.0 (QA defect M1)

Repro: font_scale 2.0, Apps > an app > "Set a budget" or "Edit", tap the Limit field. The action row sits below the keyboard (keyboard top about y 1505, buttons at y 1672 to 1819) and a swipe inside the dialog changes nothing. Pressing Back hides only the keyboard and the buttons appear. This is the same defect D8 had in the backup dialog; you fixed D8 in the passphrase dialogs (commit `2f1ef7d`) but not in `BudgetEditorDialog` (an `AlertDialog`).

- Reproduce with a screencap first and look at it.
- Write a Compose test that **injects real IME window insets** (about 40% of the screen height) at `fontScale = 2f`, then asserts Save and Cancel end up above the IME top or are reachable (`performScrollTo()` then above the IME), and that clicking Save fires the callback once the limit is valid. It must fail on `bb815ea` and fail again if you remove `imePadding` or the scroll container from the fix. (The audit found that the D8 test you wrote does NOT inject insets and survives removing `imePadding`: fix that test in the same way, so it fails without `imePadding`, and add a mutant check for the BudgetRing label cap: removing the cap must fail a test.)
- Fix the same way as the passphrase dialogs (content in a vertically scrolling column with `imePadding()`, buttons inside it or pinned above the IME, dialog window insets handled). Reuse the helper if you made one.
- Device proof: screencaps at font 2.0 with the keyboard open for "Set a budget" and "Edit" (buttons visible above the keyboard or one scroll away; save a budget), plus font 1.0.

## Item 2: shipped documents that are wrong

These files ship in the repository, so they must be true.
- `docs/accessibility-audit.md`: delete the invented D16 row; remove every old screenshot that is relabelled as evidence of a new fix (use a screenshot from the current build or mark the row "no screenshot"); a defect is marked fixed only with a test the checker accepts or a current screencap path that exists; D8 and M1 fixed only after your screencaps exist; D18 is "deferred to polish: no confirmation dialog exists". Then run `python tools/check-cited-tests.py docs/accessibility-audit.md` and paste the exit code.
- `docs/release/RELEASE.md`: remove the commit-specific hash and size, or replace them by the current ones (`sha256sum` and `ls -l` of a fresh release build) with a note that they change with every commit because the APK embeds `version-control-info.textproto`; the stable check is `tools/compare-apks.py`.
- `CHANGELOG.md`: replace "comprehensive accessibility support" (or similar) by the true list of what was done.
- `README.md`: the Glance widgets sentence says they show "remaining data, progress and renewal days". Read `widget/HeroWidget.kt` and `widget/BentoWidget.kt` and write what they really draw (usage, remaining or forecast caption, speed, top apps), no progress bar, no renewal days. Check every other widget or feature sentence of the README against the code in the same way and fix any other mismatch.

## Finish

Do not push, merge or tag. Run the full gate once from clean (`./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --no-build-cache --stacktrace`). Print a SHORT summary: the commits (`git log --oneline bb815ea..HEAD`), per-module test counts as Gradle prints them, the lint warning count, any test you deleted or renamed (none expected), anything you could not verify. Then say exactly: **"M6 must-fix items are done. Waiting for Claude to test on branch m6-polish."**
