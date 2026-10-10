# Task A2 Brief: History opens on the newest period (= task N2 of M4 fix wave 3)

### Source: Task N2 of `docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md`

**Files (read first):**
- `app/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/history/History.kt` (the Day/Week/Month chart and its scroll state)
- `ui/design/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/ui/design/tiles/UsageBarRow.kt`
- Test: `app/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/history/HistoryTest.kt` (ADD) and any tile tests in `:ui:design`.

**Background:**
The QA restored about 2 months of history and found that History opens scrolled to the OLDEST period (the Day chart showed 26 Sep to 2 Oct, the Week chart started 20 Jul), so today's bar needs a swipe. With under a week of data nobody saw it.

**Requirements:**

- [ ] **Step 1: Write the failing tests.**
  A bar row with MORE bars than fit (for example 60 day bars at 360 dp, 48 dp minimum width) opens scrolled to the END: after composition the last bar is displayed without scrolling (`onNodeWithContentDescription(<last bar>).assertIsDisplayed()`) and the first is not; switching Day/Week/Month scrolls to the end again; selecting a bar does not jump the scroll position; a row with few bars is unchanged. If `UsageBarRow` is shared with Home (7 bars), Home's behaviour must not change (test).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
  In `UsageBarRow` (or the History screen if the row has no scroll state API) use `rememberScrollState()` and `LaunchedEffect(points.size, key) { scrollState.scrollTo(scrollState.maxValue) }` only for scrolling rows; keep `horizontalScroll` as is; the key includes the granularity so a switch rescrolls but a selection does not.
- [ ] **Step 4: Run to verify they pass** — gate green.
- [ ] **Step 5: Commit** `fix(history): open the charts on the newest period`.

### Global Constraints & Rules:
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- Stage only files you changed by explicit path.
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com`.
- Commit message ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Gate command: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
