# Task A2 Test Report: History opens on the newest period (= task N2 of M4 fix wave 3)

## Verdict
**PASS**

## Spec Findings
All requirements from `.superpowers/sdd/2026-10-11-emberbyte-m5-live-lens/task-A2-brief.md` have been met:
1. **Multi-bar row opens scrolled to the end**: Verified in both component tests (`UsageBarRowTest.row_with_many_bars_opens_scrolled_to_end`) and screen-level tests (`HistoryContentTest.history_screen_opens_scrolled_to_the_newest_period`). With 60 bars (or 14 daily bars in HistoryScreen), the newest bar (Bar 60 / Wednesday, 7 October) is displayed without scrolling, and the oldest bar (Bar 1 / Thursday, 24 September) is not displayed.
2. **Switching granularity rescrolls to end**: Verified in both `UsageBarRowTest.switching_key_or_granularity_rescrolls_to_end` and `HistoryContentTest.switching_granularity_scrolls_to_the_newest_period_again`. Switching granularity from Day to Week triggers rescroll so that the newest week (Week of 5 October) is displayed and the oldest week (Week of 20 July) is not displayed.
3. **Selecting a bar does not jump scroll position**: Verified in `UsageBarRowTest.selecting_a_bar_does_not_jump_scroll_position`. Mutating `selectedIndex` leaves the scroll position unchanged at the end because `selectedIndex` is intentionally omitted from the `LaunchedEffect` keys (`LaunchedEffect(points.size, scrollKey)`).
4. **Home's 7-bar row behaviour is not broken or changed**: `UsageBarRowTest.seven_bars_fit_without_scrolling` continues to pass, and callers without `scrollKey` (such as `HomeScreen.kt` and `AppDetail.kt`) compile and execute cleanly with `scrollKey: Any? = null` default value.
5. **Commit Author & Message format**:
   - Commit: `f1f84bb14c31797bd0efd681fb86cb6a0b986d57`
   - Author: `Omar Amin <omarsadiq2012@gmail.com>`
   - Message ends with: `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`

## Run Results (with Quoted Output)

### 1. `UsageBarRowTest`
```
./gradlew :ui:design:testDebugUnitTest --rerun-tasks --tests io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRowTest
```
Output:
```
> Task :ui:design:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :ui:design:processDebugUnitTestJavaRes
> Task :ui:design:testDebugUnitTest

[Incubating] Problems report is available at: file:///C:/Fast%20Project/DataKB/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 33s
30 actionable tasks: 30 executed
```

### 2. `HistoryContentTest`
```
./gradlew :app:testDebugUnitTest --rerun-tasks --tests io.github.khaledbahaaeldin.emberbyte.history.HistoryContentTest
```
Output:
```
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

[Incubating] Problems report is available at: file:///C:/Fast%20Project/DataKB/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 1m 4s
78 actionable tasks: 78 executed
```

### 3. Full Gate Command
```
./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
```
Output:
```
BUILD SUCCESSFUL in 12s
218 actionable tasks: 12 executed, 2 from cache, 204 up-to-date
```

## Test-Quality Findings
- `UsageBarRowTest` uses `androidx.compose.ui.test.junit4.createComposeRule` running under Robolectric (`sdk = [35]`), explicitly asserting `assertIsDisplayed()` and `assertIsNotDisplayed()` on semantic content descriptions for both the newest and oldest bars in a fixed-size container (`Modifier.size(360.dp, 200.dp)`).
- `HistoryContentTest` tests the full `HistoryScreen` integration, asserting that with 14 daily bars spanning from 24 Sep to 7 Oct, today's bar is displayed and 24 Sep is not displayed. It also clicks the `Week` filter chip and asserts that the newest week (5 Oct) is displayed and the oldest week (20 Jul) is not displayed.
- Backward compatibility: Adding `scrollKey: Any? = null` with a default parameter maintains full compatibility for all other consumers (`HomeScreen.kt`, `AppDetail.kt`, `TilesEdgeCaseTest.kt`, `TilesTest.kt`).
- Invariants & safety checks:
  - No Java 18+ APIs used. Target SDK is 35, JDK compatibility 17.
  - Zero modifications under `docs/superpowers/`.
  - `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` returned `1`.

## Issues
- **Critical**: 0
- **Important**: 0
- **Minor**: 0
