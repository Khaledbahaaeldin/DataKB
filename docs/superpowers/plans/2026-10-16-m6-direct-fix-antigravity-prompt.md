# Prompt for Antigravity: M6 final fixes (direct, single agent)

> Paste everything below the line into Antigravity, in the project directory. **No subagents, no briefs, no tester, no ledger.** You fix the list below yourself, in order, and stop. Claude audits and tests afterwards.

---

You are working directly on Emberbyte (Android, Kotlin, Compose, JDK 17), branch `m6-polish` (local, not pushed). HEAD is `e09c0f1`. Do not spawn subagents and do not write briefs or reports beyond the short summary at the end.

## Context (read these first, they are short to skim)

- Claude's re-audit: `.superpowers/sdd/2026-10-15-emberbyte-m6-fix-wave/m6-reaudit-report.md` (findings R-1 to R-20) and the device re-check `m6-qa2-report.md` (D8 and N1 to N5).
- Contract section 12.1 in `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` was corrected by Claude: it now says exactly which Android API each live-update call needs. Do not edit it.

## Rules

1. Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`. Stage only your files by explicit path. Never touch anything under `docs/superpowers/` (Claude's files). Never add anything under `.superpowers` to git. Never push, merge or tag.
2. No new dependency. JDK 17. Room schema stays at 3. No `INTERNET` permission, no `VpnService`.
3. Do not touch the visual polish the user is doing separately (Home 7-day row, History bar value readout, App detail row start, widget layout and previews, translucent navbar, licences typography, Plans empty state, archive confirmation, store screenshots).
4. Every behaviour you fix gets a test that FAILS on the old code: temporarily revert your production change, run the test, see it fail, put the change back. A test that fails 1 time in 10 is a failing test: run each test you add or touch 10 times in isolation. Never delete or rename an existing test; if one is obsolete, restore it anyway unless you can show a replacement kills the same mutants.
5. One emulator only (`Violet_API_36`, always `adb -s emulator-5554`; a physical phone is attached and must never be touched). One Gradle build at a time. Restore device settings and `adb emu kill` at the end.
6. For anything you claim about the device, take `adb -s emulator-5554 exec-out screencap -p > file.png`, open it and look at it. `uiautomator` node bounds are not what is on screen.
7. Commit after each numbered fix below, with a message that explains WHY in at least two lines and ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Use your checkout's git identity.
8. Gate after each fix: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --stacktrace`. Baseline: 1053 tests (`:app` 464, `:core:data` 323, `:core:engine` 125, `:ui:design` 141), lint 0 errors and 11 warnings, no new warning.

## The fixes, in this order

**1. Critical crash (R-1).** `setRequestPromotedOngoing` and the permission `POST_PROMOTED_NOTIFICATIONS` exist only since Android 16 **QPR2 = API 36.1**. `ProgressStyle`, `setShortCriticalText` and `canPostPromotedNotifications()` exist since API 36. Today `LiveNotification.kt` calls `setRequestPromotedOngoing` when `SDK_INT >= 36`, so on a 36.0 phone it throws `NoSuchMethodError`, nothing catches it, and the START_STICKY sampler crash-loops.
- Call `setRequestPromotedOngoing(true)` only when `Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1`. Build the ProgressStyle and the chip at `SDK_INT >= 36`. No reflection, no `catch (Error)`.
- Make `sdkInt` and `sdkIntFull` injectable into `LiveNotificationBuilder`, extract the decision into a pure function and test the whole table (29, 35 -> standard; 36.0 -> progress + chip, no promoted request; 36.1 and 37 -> promoted; no progress -> standard). Add a recording seam (a tiny interface for the three platform calls, real implementation in production, recorder in tests) proving the promoted call is never made at 36.0. Show the test failing on `e09c0f1`.
- Delete the shadow-based test pieces (`ShadowProgressStyle` and the interceptor that fakes API 36 calls): they are flaky (NPE, 4 of 10 runs) and they test a fake. Do not write anywhere that a unit test "runs against the real API 36 platform": Robolectric needs Java 21 for API 36 and the project uses JDK 17. Keep the tests that run on the real frameworks of API 29/35.
- Remove every `@SuppressLint("NewApi")` from the live-update code; guard so lint itself accepts it. If lint truly cannot, keep ONE suppression on the one line with a comment quoting the lint message.
- Make the service survive a failing builder: if building the live notification throws, post the standard `NotificationCompat` notification once instead (log the class name only). Test with a builder that throws `NoSuchMethodError`.
- Device check on the emulator: `adb -s emulator-5554 shell dumpsys notification --noredact` shows ProgressStyle, progress/max, `shortCriticalText`, and the promoted flag for a plan with data; "0 B" chip when the plan is exhausted; no ProgressStyle when the live notification is off or the plan is archived.

**2. D8: backup dialog at font scale 2.0 (R-3).** The keyboard covers Create and Cancel and a swipe inside the dialog does nothing (Settings > Create backup > tap Passphrase). Your own earlier screenshots show it; the old test passes even with the fix reverted.
- Reproduce first with a screencap (font_scale 2.0), and look at it.
- Write a Compose test that can see the keyboard: dispatch IME window insets (about 40% of the screen height) to the host view at `fontScale = 2f`, then assert Create and Cancel end up above the IME top or are reachable (`performScrollTo()` then above the IME), and that clicking Create fires the callback. It must fail on the old code and fail again if you remove the fix.
- Fix: content in a vertically scrolling column with `imePadding()`, buttons inside it (or pinned above the IME), in a dialog with `decorFitsSystemWindows = false` / full-width properties as needed. Apply the same to the Restore dialog and the budget editor dialog only if the same test fails for them.
- Device proof with screencaps at font 2.0 AND font 1.0, keyboard open, Create and Cancel visible or one scroll away; complete a backup with a passphrase.
- N1: at font 2.0 the Plans card ring label ("137%") overflows the ring. Keep it inside (limit its font scaling inside the ring or shrink to fit); test `fontScale = 2f` that the label bounds are within the ring; screencap light and dark.

**3. Ring and chip contrast (R-6, R-7).** The ring track is now `outline`, which makes the coloured arc only 1.50:1 against the track in light theme (1.71:1 for dark error).
- Pick a track (or add a thin outline) so every arc colour (primary, tertiary, error) is at least 3:1 against what it is drawn on, in light, dark, AMOLED, static and dynamic. Unselected bar graphics are 1.43 to 2.46:1 against the card: raise them to at least 3:1 or add an outline; selected vs unselected bars must differ by more than colour.
- Test the components' real colours (call their own colour functions), not a hand copy. Add the missing pairs to `TileContrastTest`. Reverting the ring track or the chip back to the old tint-only state must fail a test (do it and look).
- A component test for the selected chip: check icon present, container colour is the selected one, contrast to unselected at least 3:1.

**4. Tests that protect the earlier accessibility fixes (R-5, R-14, R-15, R-16).**
- Navbar reduced motion: the mutant "item spring ignores reduce motion" at the `NavItem` call site survives. Pass the resolved spec down and assert on it, or test frame by frame with `mainClock.autoAdvance = false`.
- `UsageBarRow`: you changed the default `contentPadding` from 0 to 4 dp (unrequested, polish area). Revert the default or include the padding in the width calculation; test 384, 388 and 392 dp containers: every bar at least 48 dp wide, first bar inside the viewport.
- History selection: reset only when the bar list's identity changes (period, day rollover), not on every data refresh. Test both ways.
- Semantics tests: app-row avatar hidden from semantics; navbar tab `contentDescription` only when its label is hidden; plan/budget editor errors announced once.

**5. Release hygiene (R-11, R-13, R-17, R-18).**
- `ReleaseApkVerificationTest`: no silent `Assume` skip inside `verifyReleaseApk`; test `findReleaseApk` through a seam (set and present, set and missing -> fails, unset -> only non-APK checks); delete the tautology `release_verification_fails_loudly_when_apk_is_absent`. Declare the APK as an input of the verification test so a release-only change re-runs it.
- `NoticeCoversDependenciesTest`: match whole `group:name` coordinates, not substrings, with a commented allow-list for group lines (for example "androidx.*: Apache-2.0"); cover arch.core, autofill, customview-poolingcontainer, navigationevent, `org.jetbrains:annotations`, `org.jetbrains.androidx.*` in `NOTICE`. Removing "Kotlin" from `NOTICE` must fail the test.
- `docs/release/RELEASE.md`: delete the sentences about nonexistent resource keep rules and "compare against our unsigned APK" (F-Droid builds from the tagged source and compares with the developer-signed APK only if one is provided); explain the whole-file hash includes `version-control-info.textproto`, so give the stable per-entry comparison; remove the commit-specific hash. `docs/release/real-device-checklist.md` step 2: drag works with animations off now. `CHANGELOG.md`: replace "full accessibility support" by the true list.
- Remove the unused `FilterChip` import (6 files); make the test-only `onMotionResolved` parameters `internal`.

**6. The audit document and the checker (R-8, R-12).** These are shipped files, so they must be true.
- `tools/check-cited-tests.py`: a cited name passes only if a `fun` directly preceded by `@Test` exists in the named class; helpers do not count; a missing input file exits non-zero; zero citations from a non-empty file exits 1. Add a small test script with fixtures (good name, helper name, wrong class, missing file) and run it.
- `docs/accessibility-audit.md`: rebuild the rows from tests that the checker accepts; D8 marked fixed only with its screencap; remove the invented D16; D18 is "deferred to polish" (no confirmation dialog exists); remove old screenshots relabelled as new evidence; honest "NOT COVERED" where it is.
- Run `python tools/check-cited-tests.py docs/accessibility-audit.md` and paste the exit code.

## Finish

Do not push, merge or tag. Run the full gate once more from clean (`./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --no-build-cache --stacktrace`), then run the `:app` and `:ui:design` test tasks three more times (`--rerun-tasks`). Print a SHORT summary: the commits (`git log --oneline e09c0f1..HEAD`), per-module test counts as Gradle prints them, the lint warning count, the output of `git diff --stat e09c0f1..HEAD -- '*Test.kt' | tail -1`, any test you deleted or renamed (with the reason), anything you could not verify, and anything you did differently from this list. Then say exactly: **"M6 direct fixes are done. Waiting for Claude to test on branch m6-polish."**
