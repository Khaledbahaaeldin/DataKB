# Emberbyte M3 Fix Wave Implementation Plan (after the first M3 audit and QA)

> **For the executing agents (Antigravity):** follow `docs/superpowers/plans/2026-10-05-m3-fixwave-antigravity-prompt.md`
> (implementer and a fresh tester per task, a ledger, at most 3 fix rounds). Steps use checkbox (`- [ ]`) syntax.
> Do the tasks in order: H1 to H6.

**Goal:** Close what the first M3 audit (Opus, verdict NOT CLOSED) and the device QA (verdict FAIL) found, so that M3 can close.
Reports: `.superpowers/sdd/2026-10-04-emberbyte-m3-plans/m3-audit-report.md` (C-1, I-1 to I-8, M-1 to M-20) and `m3-qa-report.md`
(D3-01 to D3-09). Read both completely. The ids below refer to them.

**What was good:** the engine maths (hand-verified across DST and odd zones), the migration (byte-identical to `2.json`, M2 data kept on
the device), plan numbers equal `netstats`, alerts fire once per threshold, widgets work, zone independence holds, M2 carry-over items pass.

**What this wave fixes:** one Important device defect (D3-01: a deep link replaces the whole back stack while the app is open), plan-number
honesty (rollover with add-ons, missing "Estimated" flags), editor correctness (silent or half-saved saves), consistency and layout
polish, the process failures (deleted test, missing tests, unreported lint growth, false statements) and the missing contract section.

**Contract.** `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` section 10 now exists in your working tree (UNCOMMITTED, Claude's
file). In the first M3 run it vanished from the tree and your reports then cited text that did not exist (audit C-1). **Do not touch,
revert, stash, checkout, restore or commit anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.** Never run
`git checkout -- <path>`, `git restore`, `git stash`, `git clean` or `git reset --hard` in this repository. If a file of Claude's seems to be
missing, stop and tell the user. Section 10 was extended for this wave (10.1 rollover excludes add-ons, 10.2 `validateDraft`, new 10.5 with the other fix-wave rules).

## Global Constraints (unchanged from the M3 plan, plus)

- Same stack and module rules as `docs/superpowers/plans/2026-10-04-emberbyte-m3-plans.md` "Global Constraints" (JDK 17: no Java 18+ APIs; `:core:engine` pure Kotlin; `:ui:design` has no `:core:*`; no new dependency).
- Branch `m3-plans-forecast-widgets` (HEAD `7eda5b3`). Never push, merge, force or skip hooks. Identity `Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`. Commit messages explain WHY and end with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`. Stage only your files by path.
- **Tests:** tests that encode old behaviour are named in the task that changes them. Change exactly those and list them. Never delete or overwrite a test to make room for another; add new tests next to existing ones. Never `@Ignore`.
- **Lint:** at the end of H5 the `:app` lint warning count must be back to at most the 6 of M2, or each remaining new warning is justified in writing in the report.
- Gate after every task: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`; H6 uses `--no-build-cache`. Baseline at `7eda5b3` (audit run): 628 tests (`:app` 235, `:core:data` 189, `:core:engine` 96, `:ui:design` 108), 0 lint errors, 14 `:app` lint warnings.
- Every UI task is proven on the emulator by its tester with quoted evidence (clean install; restore every device setting).

---

### Task H1: Navigation (D3-01 deep link replaces the back stack, D3-06 hidden navbar, M-11 browsable filter)

**Files:** modify `app/src/main/kotlin/$PKG/EmberbyteApp.kt` (new-intent listener, navbar visibility), `app/src/main/AndroidManifest.xml`; create `app/src/main/kotlin/$PKG/nav/DeepLinkNavigation.kt`; tests `app/src/test/kotlin/$PKG/nav/DeepLinkNavigationTest.kt` (+ `NavBarVisibility` test next to the existing ones in `ui/design`).

**Cause (QA D3-01):** the listener in `EmberbyteApp.AppScaffold` removes `FLAG_ACTIVITY_NEW_TASK` and calls `navController.handleDeepLink(...)`. In that mode Navigation rebuilds the stack from the link, so the target is the ONLY entry: one Back goes to the launcher, and after saving in the editor `popBackStack()` has nothing to return to. A cold start is fine.

- [ ] **Step 1: Write the failing tests** `DeepLinkNavigationTest.kt` (Robolectric Compose with a `TestNavHostController` and a tiny `NavHost` that declares `home`, `apps`, `plans`, `plans/{planId}` (Long), `settings` with the same `navDeepLink` patterns as `DeepLinkPatterns`; start on `home`):
```kotlin
    @Test fun a_deep_link_is_pushed_onto_the_current_stack() {
        // start at home, then
        val handled = nav.openDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://plans/1")))
        assertTrue(handled); assertEquals("plans/{planId}", nav.currentDestination?.route)
        assertTrue(nav.popBackStack()); assertEquals("home", nav.currentDestination?.route)        // Back returns to where the user was
    }
    @Test fun a_link_to_the_current_destination_does_not_stack_a_copy() {
        nav.openDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://home")))
        assertFalse(nav.popBackStack())                                                              // only the start destination is on the stack
    }
    @Test fun settings_opened_from_a_link_closes_back_to_the_previous_screen() { /* same shape with emberbyte://settings */ }
    @Test fun a_malformed_or_unknown_link_is_ignored_without_a_crash() {
        assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://plans/abc"))))
        assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("emberbyte://nowhere"))))
        assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/plans/1"))))
        assertFalse(nav.openDeepLink(Intent(Intent.ACTION_VIEW)))                                    // no data
        assertEquals("home", nav.currentDestination?.route)
    }
```
Navbar test (in `ui/design`, next to `NavBarVisibilityTest`): a new `NavBarVisibility.show()` (or the existing equivalent; read the class first) sets `visible = true` after the bar was hidden by a downward scroll.

- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
```kotlin
// nav/DeepLinkNavigation.kt
package io.github.khaledbahaaeldin.emberbyte.nav

/** Opens an emberbyte:// link ON TOP of the current back stack (the user can go Back). Returns false when the link is not ours or has no destination. */
internal fun NavController.openDeepLink(intent: Intent): Boolean {
    val uri = intent.data ?: return false
    if (uri.scheme != DeepLinks.SCHEME) return false
    return try {
        navigate(uri, navOptions { launchSingleTop = true })
        true
    } catch (_: IllegalArgumentException) {
        false                                           // no destination matches (unknown host, planId that is not a number)
    }
}
```
`EmberbyteApp.kt`: the `addOnNewIntentListener` body becomes `Consumer<Intent> { intent -> navController.openDeepLink(intent) }` (delete the `inPlaceIntent` copy). Add `LaunchedEffect(route) { visibility.show() }` (call whatever resets the hide-on-scroll state; add the method to the visibility holder if it has none) so the navbar is visible after every destination change (D3-06). `AndroidManifest.xml`: remove `<category android:name="android.intent.category.BROWSABLE" />` from the `emberbyte` filter (M-11: web pages must not open the app; notifications and widgets use explicit intents, `adb shell am start -a VIEW -d` still works). `DeepLinksTest.every_documented_link_resolves_to_the_main_activity_through_the_manifest` must still pass (it does not need BROWSABLE).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator):** with the app already open on Home fire `emberbyte://plans/1`, then `emberbyte://settings`: each time ONE Back returns to the previous screen and a second Back reaches Home, then the launcher; open the editor through a plan-alert notification (create one as in M3 task 21 item 4) and press Save: the editor closes and the Plans screen shows; `emberbyte://plans/abc` and `emberbyte://nowhere` do nothing and do not crash (`logcat` clean); after returning from the editor the navbar is visible without a swipe; a cold start link still works.
- [ ] **Step 5: Commit** `fix(nav): push deep links onto the back stack and keep the navbar visible` (why: opening a plan from a notification must not trap the user or lose Back).

---

### Task H2: Plan number honesty (I-1, I-2, I-3, M-5, M-6, M-19, D3-08)

**Files:** modify `core/engine/.../plan/PlanCalculator.kt` (+`percentUsed`), `core/data/.../plan/PlanStateAssembler.kt`, `core/data/.../alert/AlertEvaluator.kt`, `app/.../home/HomeMapping.kt`, `app/.../plans/PlansMapping.kt`, `app/.../sampler/LiveNotification.kt`, `app/.../widget/WidgetModels.kt`, `ui/design/.../plan/BudgetRing.kt`, new `app/.../common/RemainingText.kt`; tests: additions to `PlanCalculatorTest`, `PlanStateAssemblerTest`, `AlertEvaluatorTest`, `HomeMappingM3Test`, `PlansMappingTest`, `LiveNotificationTest`, `WidgetModelsTest`, `BudgetRingTest`.

- [ ] **Step 1: Write the failing tests.**
  * **I-1 (rollover without add-ons).** `PlanStateAssemblerTest`: plan cap 10 GB with `Rollover.Full`; an add-on of 2 GB valid `2026-09-20` to `2026-10-10` (it spans the renewal on 1 Oct); usage in the previous window (September) 3 GB. The previous cycle's unused bytes are `10 - 3 = 7 GB` (the old code evaluated September WITH the add-on and carried 9 GB), so assert `rolledOverBytes == 7_000_000_000L`, `addOnBytes == 2_000_000_000L` (the add-on still counts once, in the current window) and `effectiveCapBytes == 19_000_000_000L` (the old code gave 21 GB). A second case: the add-on valid ONLY in September gives `rolledOverBytes == 7 GB` and `addOnBytes == 0`.
  * **I-2 (SIM-bound plan, unknown subscription).** `PlanStateAssemblerTest`: a plan bound to subscription 2 and a mobile hourly (window) row with subscription `-1` inside the window: `isApproximate == true`; the same rows for an UNBOUND plan: `false`; a bound plan with only subscription-2 rows: `false` (exact).
  * **I-3 (history older than the first measurement).** The flag also becomes true when the plan window starts more than one platform window (2 h) before the earliest measurement in `PlanRows` (`min` of every minute row start and hourly row start, any network). Tests: rows start 10 days after the window start: `true`; rows cover the window start: `false`; no rows at all: `false`.
  * **M-5 (same percent everywhere).** `PlanCalculatorTest`: `PlanState.percentUsed()` is `usedBytes * 100 / effectiveCapBytes` as integer division, `0` when the cap is 0; `4_900_000_000 / 10_000_000_000` gives `49` (not 48).
  * **M-6.** `HomeMappingM3Test`: a forecast with `runOutEarliest`, `runOutExpected` set and `runOutLatest == null` reads `"runs out Mon (could be later)"`.
  * **M-19.** `HomeMappingM3Test`: `forecastToUi(forecast, now, zone, locale, remainingBytes = 500)` with `runOutExpected <= now` does NOT say "Data used up": `kind == RunsOut`, sentence `"runs out today"`; with `remainingBytes = 0` it is `Out`; with `remainingBytes = null` (no plan state) the old behaviour holds.
  * **D3-08.** `RemainingTextTest` (new): `remainingText(0, units) == "No data left"`, `remainingText(3_800_000_000L, DECIMAL) == "3.80 GB left"`; Home's subtitle for `remainingBytes = 0` is exactly `"No data left"` (no forecast sentence appended), the live notification text (`remainingBytes = 0`) is `"No data left"`, the widgets' hero caption and the plan card use the same function.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
  * `PlanStateAssembler.state`: evaluate the previous window with NO add-ons: `PlanCalculator.state(plan.copy(rollover = Rollover.None), previous.end.minusMillis(1), slices(...previous...), emptyList(), rules, 0L, false).remainingBytes`.
  * `PlanStateAssembler.approximate(...)`: keep the existing two rules (other SIM in minute rows, `SERVICE_KILLED`/`REBOOT`/`PERMISSION_MISSING` gap) and add `unknownSimForBoundPlan = bound != null && (rows.hourly + minute rows with subscriptionId == -1)` having bytes in the window (hourly rows count when their window start is in `[SeriesBuilder.windowStart(window.start), min(now, window.end))`) and `startsBeforeFirstMeasurement`: `earliest != null && earliest > window.start.plusSeconds(SeriesBuilder.WINDOW_SECONDS)` where `earliest` is the smallest of all minute row starts and hourly row starts (null when there are no rows). Existing `PlanStateAssemblerTest` cases whose rows start long after the window start and assert `isApproximate == false` (`a_bound_plan_whose_only_traffic_is_its_own_sim_is_exact`, `an_unbound_plan...`, and any other that fails) encode the old rule: give their fixtures an early row (for example a zero-byte minute row at the window start) and list each changed test.
  * Engine: `fun PlanState.percentUsed(): Int = if (effectiveCapBytes <= 0L) 0 else (usedBytes * 100 / effectiveCapBytes).coerceAtMost(9_999L).toInt()` in `PlanCalculator.kt`; `AlertEvaluator` and `planCardUi` use it (`percentText = "${state.percentUsed()}%"`, ring `Warning` at `percentUsed >= 80`, `Exceeded` at `>= 100`); `BudgetRing` gets an optional `percentText: String? = null` that overrides its own float-derived text and description number (default behaviour unchanged).
  * `common/RemainingText.kt`: `internal fun remainingText(bytes: Long, units: ByteUnits): String = if (bytes <= 0L) "No data left" else "${plainBytes(bytes, units)} left"`; Home subtitle (`left` part; when `remainingBytes == 0` the subtitle is just that text), `planCardUi.remainingText`, `liveNotificationText`, `heroWidgetModel.caption` call it.
  * `forecastToUi(forecast, now, zone, locale, remainingBytes: Long? = null)`: `Out` only when `expected <= now && (remainingBytes == null || remainingBytes <= 0)`; `expected <= now` with `remainingBytes > 0` gives `ForecastUi("today", "runs out", confidence, ForecastKind.RunsOut)`; in the `RunsOut` path, when `runOutLatest == null` the detail is `"runs out (could be later)"`. Callers with a `PlanState` (Home, plan card, widgets) pass `state.remainingBytes`. The M3 test `HomeMappingM3Test` expecting `"0 B left · data used up"` changes to `"No data left"`; list it.
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator):** a plan over its cap reads "No data left" on Home subtitle, the Plans card, the live notification and both widgets (`dumpsys notification`, `uiautomator` texts); a plan bound to a SIM with only pre-install (backfilled) traffic shows the "Estimated" badge; the percent on the card equals the percent in the alert text for 4.9 GB of a 10 GB cap (seed minute rows through real traffic or note CANNOT-VERIFY and rely on the unit test).
- [ ] **Step 5: Commit** `fix: keep add-ons out of rollover and say "Estimated" when the plan's history is incomplete`.

---

### Task H3: Plan editor correctness (I-4, D3-03, D3-02, M-7, M-13)

**Files:** modify `core/data/.../PlanRepository.kt` (+`validateDraft`), `core/data/.../plan/RoomPlanRepository.kt`, `core/data/.../fake/FakePlanRepository.kt`, `app/.../plans/{PlanEditorViewModel,PlanEditorScreen,EditorFields}.kt`, `app/.../AppGraph.kt`, `app/.../nav/PermissionActions.kt`; create `app/.../plans/PlanEditorFormJson.kt`; tests: `RoomPlanRepositoryTest`, `PlanEditorViewModelTest`, `PlanEditorContentTest`, `PlanEditorFormJsonTest`, a `PermissionActions` policy test.

**Interfaces:** `PlanRepository.validateDraft(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Map<String, String>` (pure, not suspend): the empty map when valid, otherwise EVERY problem keyed with the editor's error keys: the contract field names for the plan (`name`, `capBytes`, `cycle.dayOfMonth`, `cycle.days`, `rollover.maxBytes`, `subscriptionId`) and `addOn.<index>.<field>` / `freeRule.<index>.<field>` for list items. (Contract 10.2.)

- [ ] **Step 1: Write the failing tests.**
  * `RoomPlanRepositoryTest`: `validateDraft` with a good draft is empty; with a blank name AND an add-on with zero bytes AND a free rule with no days it returns exactly `{name, addOn.0.bytes, freeRule.0.days}`; it never persists anything.
  * `PlanEditorViewModelTest` (add; keep the existing tests): (a) **nothing is written when a child is invalid**: a form with a valid plan and a free window with no days: after `Save` the plan list is EMPTY (the first M3 build saved the plan and then failed), `errors` contains `freeRule.0.days`, and the plan is not duplicated by a second `Save`; (b) all keys at once: name blank + add-on `validUntil` before `validFrom` + free window `start == end` -> `errors.keys == {name, addOn.0.validUntil, freeRule.0.time}`; (c) editing an EXISTING plan with an invalid add-on leaves the stored plan unchanged (the rollover of the stored plan is not modified); (d) **restoring unsaved input:** build a `PlanEditorViewModel` with a `SavedStateHandle`, `SetName("Draft")`, `SetCap("7")`, add an add-on; build a SECOND view model with the SAME handle: its form has the same name, cap and add-on draft (this simulates process death); a saved plan clears the stored form (a third view model starts fresh).
  * `PlanEditorFormJsonTest` (Robolectric): every field of `PlanEditorForm` (all enums, dates, times, sets, nullable package) survives `encode` then `decode`; `decode` of garbage returns `null`.
  * `PlanEditorContentTest` additions: every error key the view model can produce is shown as text somewhere on screen: for keys `freeRule.0.days`, `freeRule.0.time`, `addOn.0.validUntil`, `addOn.0.bytes`, `addOn.0.label`, `freeRule.0.label`, `name`, `capBytes`, `cycle.dayOfMonth`, `cycle.days`, `rollover.maxBytes`, `form` set `errors = mapOf(key to "Problem $key")` (with the matching section visible in the form) and assert `onNodeWithText("Problem $key")` exists; an error key the screen does not know is shown in a summary line above Save (`"Problem unknown.key"`).
  * Time and date fields follow the device: `TimeField` shows the time with `DateFormat.getTimeFormat(context)`; the M3 test `the_time_field_opens_a_dialog_that_can_be_cancelled` clicked `"Renews at 00:00"`: it now finds the node by the content description `"Renews at"` prefix or `substring = true` (list the change); `DateField` shows `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` of the date in the device locale.
  * M-13 (`PermissionActions`): `asked` is set to true only in the launcher's RESULT callback, never before launching (a dismissed dialog must not send the user to Settings next time). Cover it by moving the decision into a small pure class `AskedTracker` (`markResult(permission)`, `asked(permission)`) with a test.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
  * `RoomPlanRepository.validateDraft`: `PlanValidation.plan(plan)?.let { map[it.field] = it.reason }` and, for each child index, `PlanValidation.addOn(a)` -> `"addOn.$i.${e.field}"`, `PlanValidation.freeRule(r)` -> `"freeRule.$i.${e.field}"`. `FakePlanRepository.validateDraft` returns `emptyMap()`.
  * `PlanEditorViewModel.save()`: after `toDomain` succeeds call `plans.validateDraft(ok.plan, ok.addOns, ok.freeRules)` FIRST; a non-empty map sets `errors` and returns without calling any repository write. Remove the per-child error mapping that only fires after the plan was written (keep a generic `form` error for storage failures). Honest wording in code comments and in the report: the save is validated first and then written step by step; it is NOT a database transaction (the first M3 report called it transactional, which was wrong). Alert thresholds that collide ("Already set") are impossible from the form because it keeps a `Set`; map a `threshold` repository error to `form`.
  * Saved state: `PlanEditorViewModel(..., savedStateHandle: SavedStateHandle = SavedStateHandle())`; after every form change write `savedStateHandle["form"] = PlanEditorFormJson.encode(form)`; in `init`, for BOTH new and existing plans: when the handle holds a decodable form, use it instead of the freshly loaded one (but still fill `loadedAddOnIds`, `loadedFreeRuleIds`, `loadedAlertRules` from the repository); remove the key after a successful save or archive. `AppGraph.planEditorViewModelFactory(planId)`: `initializer { PlanEditorViewModel(planId, ..., savedStateHandle = createSavedStateHandle()) }` (`androidx.lifecycle.createSavedStateHandle` from the `CreationExtras` receiver). `PlanEditorFormJson` (org.json, internal) encodes enums by name, dates and times as ISO strings, `days` as a list of names.
  * Screen: render every error under its field (the keys listed above); after the last section and before Save show one `Text` per error key that no field consumed (sorted), in the error colour.
  * `TimeField`/`DateField` as described above.
  * `PermissionActions`: use `AskedTracker` (SharedPreferences `permission_prefs`, written in the result callback).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator, mandatory):** (1) in the editor deselect every day of a free window and press Save: a visible error under that window, NO plan is created (Plans tab empty); (2) with an existing plan, make an add-on's end date earlier than its start and press Save: error under the add-on, the stored plan unchanged; (3) type a name and a cap, `adb shell am kill io.github.khaledbahaaeldin.emberbyte` (process death while in the background after pressing Home), reopen: the editor still holds the typed values; (4) the date and time fields follow `settings put system time_12_24 12` and `24`; restore the setting.
- [ ] **Step 5: Commit** `fix(plans): validate the whole draft before saving and keep unsaved editor input`.

---

### Task H4: Layout, right-to-left and plumbing polish (D3-04, D3-05, D3-07, D3-09, M-1, M-2, M-3)

**Files:** `app/.../plans/PlanEditorScreen.kt`, `ui/design/.../plan/PlanCard.kt`, `app/.../widget/{WidgetModels,BentoWidget}.kt`, `app/.../sampler/{AlertRunner,SamplerService,CatchUpWorker}.kt`, new `app/.../sampler/AlertGate.kt`, `core/data/.../alert/AlertEvaluator.kt`; tests: `PlanCardTest`, `PlanEditorContentTest`, `WidgetModelsTest`, `AlertRunnerTest`/new `AlertGateTest`, `AlertEvaluatorTest`.

- [ ] **Step 1: Write the failing tests.**
  * **D3-09 (widget).** `WidgetModelsTest`: `BentoWidgetModel` gains `compactApps: String` = the top apps joined with `"  ·  "` (empty when there are none): `"Video · 610 MB  ·  Chat · 50.0 MB"` for the two-app fixture.
  * **M-1 (channel gate).** `AlertGateTest` (Robolectric): `AlertGate.isEnabled(context)` is true by default (after `NotificationChannels.ensure`), and false when the `alerts` channel exists with `IMPORTANCE_NONE` (create the channel yourself with that importance BEFORE `ensure`, as Android keeps the first importance), and false when `areNotificationsEnabled()` is false. `AlertRunner.create` uses it for `enabled`.
  * **M-2 (parallel evaluation).** `AlertEvaluatorTest`: two concurrent `evaluate()` calls (`coroutineScope { awaitAll(async { e.evaluate() }, async { e.evaluate() }) }`, a plan at 52% with a 50% rule) return exactly ONE notice in total. Implementation: a `Mutex` inside `AlertEvaluator` around the whole body.
  * **D3-04 / D3-07 (layout).** `PlanEditorContentTest`: with the free-window day chips in a 320 dp wide container every chip node has width at least 48 dp (they wrap onto a second line instead of squeezing: `FlowRow`); `PlanCardTest`: under `fontScale = 2f` (`CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f))`) the card still shows all texts and the node `"100%"` of a `BudgetRing`/chip is at most one line (height at most 64 dp), and `"28 days left"` is not split into three lines (its node height is at most that of two text lines at that scale, 96 dp).
  * **D3-05 (right-to-left).** `PlanCardTest` with `LocalLayoutDirection provides LayoutDirection.Rtl`: the `Row` that shows the remaining data and the days left keeps each number next to its own words: assert, through the node text, that `"3.80 GB left"` and `"9 days left"` exist as separate unbroken nodes (the same `ForceLtr` wrapping the M2 fix used for the Home numbers).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.**
  * `PlanEditorScreen`: the day chips and the unit chips go into a `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`), `softWrap = false` on the chip labels.
  * `PlanCard`: wrap the used text, the remaining/days row and the forecast text each in `ForceLtr { ... }` (the module already has it); the remaining/days row becomes a `FlowRow` so at large font scales the second item moves down instead of breaking inside its words; chip and percent texts `maxLines = 1`.
  * `BentoWidget`: at the 4x2 size show `model.compactApps` (one line, omitted when empty) under the forecast/speed row; at the 4x4 size keep the list. Contract 10.3: Bento = today, forecast, speed, top two apps.
  * `AlertGate.kt`: `object AlertGate { fun isEnabled(context: Context): Boolean }` (app level enabled AND `getNotificationChannel(NotificationChannels.ALERTS)?.importance != NotificationManager.IMPORTANCE_NONE`; a missing channel counts as enabled because `ensure` creates it).
  * `AlertEvaluator`: `private val mutex = Mutex()`; `evaluate()` = `mutex.withLock { ... }`.
  * M-3: replace each `runCatching { ... }` around `WidgetUpdater.updateAll` (service and worker) and `AlertRunner.run()` (worker) by `try { ... } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "widget update failed", e) }` (and the alert variant).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator):** (D3-04) the free-window day chips are all readable at font scale 1.0 and 2.0; (D3-05) `adb shell setprop persist.sys.locale ar-EG` (or the system language setting; restore it) shows the plan card numbers beside their own words (`uiautomator dump`: the node texts "… GB left" and "… days left" intact); (D3-07) at `settings put system font_scale 2.0` the card is readable, "28 days left" over at most two lines, the "100%" chip on one line; (D3-09) a Bento widget at 4x2 shows the top apps line; (M-1) block ONLY the "Plan alerts" channel (`adb shell cmd notification` or the app's notification settings), cross a threshold, unblock: the alert still fires once afterwards.
- [ ] **Step 5: Commit** `fix: wrap the editor chips, keep plan-card numbers readable in any direction, serialise alert runs`.

---

### Task H5: Hygiene (I-5, I-6, I-7 partly, I-8, C-1 citations, M-12, M-14, M-15, M-18, M-20)

**Files:** tests `core/data/.../RoomUsageRepositoryTest.kt`, `app/.../history/HistoryTest.kt`, `app/.../home/HomeMappingEdgeTest.kt`, `app/.../sampler`/`core/data/.../sampler/SamplerEngineTest.kt`; `app/src/main/res/xml/{hero,bento}_widget_info.xml` + new `res/xml-v31/` copies; `gradle/libs.versions.toml`; `app/.../nav/PermissionActions.kt`; `app/.../home/HomeViewModel.kt` (unused import); `README.md`; `docs/manual-checks/dual-sim-m3.md`; the git-ignored ledger reports.

- [ ] **Step 1: Restore and add the tests.**
  * **I-5.** Restore `RoomUsageRepositoryTest.the_week_strip_never_grows_beyond_its_range_after_a_zone_change` exactly as it was at commit `ae9bf29` (`git show ae9bf29:core/data/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/data/RoomUsageRepositoryTest.kt`), next to the coverage-window test that replaced it (BOTH must exist). Add the three tests M3 task 19 required in `HistoryTest`: `the_caveat_is_hidden_when_usage_access_is_granted`, `the_caveat_is_shown_without_usage_access`, `the_average_counts_only_days_with_data` (2 days with data in a 14-day range: the average is the sum divided by 2). If History already behaves this way (the audit says the caveat part was already in M2) the tests must simply pass; do not change History to make a test fail first, and say which of them passed immediately.
  * Rename `HomeMappingEdgeTest.forecast_without_expected_falls_back_to_earliest_and_uses_the_latest_spread` so the name matches its body (`forecast_without_expected_says_probably_safe_and_names_the_earliest_day`).
  * M-20: strengthen `SamplerEngineTest.two_engine_starts_without_flush_do_not_produce_overlapping_gap_rows` to assert what its name says (every pair of stored gap rows satisfies `next.from >= previous.to`), and confirm it fails on the old code by reasoning from the diff of that fix (say so).
- [ ] **Step 2: Lint (I-6).** Run `./gradlew :app:lintDebug` and fix each of the 8 new warnings, or justify it in the report with the lint id and location:
  * `UnusedAttribute` x4 (`hero_widget_info.xml`, `bento_widget_info.xml`, lines 5 and 6: `targetCellWidth`/`targetCellHeight` need API 31): keep `res/xml/*_widget_info.xml` WITHOUT those two attributes and add `res/xml-v31/*_widget_info.xml` WITH them (same file names); the manifest `@xml/...` reference stays.
  * `GradleDependency` x2 (`libs.versions.toml:18`, Glance): bump to the newest STABLE Glance release if it builds and every test stays green; otherwise leave the warning, and state in the report the version lint suggests and why it was not taken. Do not suppress it with a lint configuration.
  * `UseKtx` x2 (`PermissionActions.kt:60`, `:82`): use `prefs.edit { putBoolean(key, true) }` (`androidx.core.content.edit`).
  * Remove the unused import in `HomeViewModel.kt` (M-18).
- [ ] **Step 3: README and checklist.** README: remove every statement that Live Lens has shipped (status line, feature list) and restore "milestone 5" in the privacy paragraph; fix the other README inaccuracies listed in audit M-12; replace the known-limitations line "The forecast ignores free-window traffic in its history" by "The forecast's history includes free-window traffic, so plans with free windows may be projected to run out too early" (M-15); add two limitations: a plan bound to a SIM is marked "Estimated" when part of its history has no SIM information (pre-install or backfilled traffic) or the window starts before Emberbyte's first measurement (for example without usage access), and "plan alerts need the app notification setting AND the 'Plan alerts' channel enabled". `docs/manual-checks/dual-sim-m3.md`: fix audit M-14 (step 3 must not say "Clear app data": that deletes the plans made in step 2; use "force-stop" instead) and read the rest of M-14.
- [ ] **Step 4: Ledger honesty (C-1).** Do not delete anything in the old reports. Append a `## Correction (first M3 audit)` section to `task-1-test-report.md` and `task-13-report.md` (and any other report that cites "contract 10.x"): "This report cited contract section 10 text that did not exist in the repository at the time (it was lost from the working tree). The section now exists; see the API contract."
- [ ] **Step 5: Run to verify** — gate green; `lintDebug` `:app` warnings at most 6 (or each remaining justified); `git diff 7eda5b3 --stat -- '*Test.kt'` shows no deleted test (list `git diff 7eda5b3 -U0 -- '*Test.kt' | grep '^-.*fun '` in your report and account for every removed `fun`).
- [ ] **Step 6: Commit** `chore: restore the deleted test, add the missing History tests, fix lint, correct the README`.

---

### Task H6: Final proof and an honest completion report

No production changes in this task; a defect found here goes back to its task for a fix round first.

- [ ] **Step 1: Full gate, no cache.** `./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace`: record per-module test counts as printed (baseline 628), `:app` lint warnings with ids and locations, Kotlin warning counts, and that `git diff --stat main..HEAD -- core/data/schemas` shows only the new `2.json`.
- [ ] **Step 2: Emulator proof** (clean installs; short-path worktrees; restore every setting; `adb emu kill`): re-run each emulator line of H1 to H4 as written there, plus: (a) upgrade from the M2 build (`ae9bf29`) still keeps the data (`PRAGMA user_version` is 2); (b) alerts: one notification per threshold, tap opens the plan, no repeat after `force-stop`, including when two alert runs overlap; (c) navigation stress, 10 taps per tab, 0 bounces; (d) `logcat -d | grep -E "AndroidRuntime|FATAL"` empty at the end. Say CANNOT-VERIFY for dual SIM, Android 16 live updates and OEM kills.
- [ ] **Step 3: Completion report** `.superpowers/sdd/2026-10-05-emberbyte-m3-fix-wave/m3-fixwave-completion-report.md`: the task table with commit ranges and tester verdicts (write "self-verified, no separate tester report" wherever a tester report does not exist); exact gate numbers; the lint warning list; every deviation from this plan with the reason; the list of tests changed, restored and added (real names, `grep`ed); the I-/M-/D3- ids fixed, with the commit; the ones NOT fixed (deferred) with the reason: M-4, M-7 only if not done, M-9, M-10, M-16, M-17, D3-03 true atomicity (a repository-level transaction is M4), and the two-notifications note (the 50% and 100% alerts share one notification id by design). Rules: only what you ran; "not run" when not run; no "immediately" for anything that takes a poll or a refresh.
- [ ] **Step 4: Stop.** `git status` shows only Claude's docs (modified or untracked), nothing pushed; say **"M3 fix wave is ready for the Claude re-audit on branch m3-plans-forecast-widgets."**

---

## Deferred on purpose (not in this wave)

M-4 (caps above 9.2e15 bytes reload in MB), M-9 (plan pipeline recomputed each minute: measured in the re-audit), M-10 (boundary hours in half-hour zones: documented approximation), M-16 (SIM-bound plan without READ_PHONE_STATE should be "Estimated": needs the permission state in the repository), M-17 (`whatIf` start granularity), true database-transaction saving (M4), personal hour-of-day curve (M4), budgets, spikes, export and backup (M4), Live Lens (M5).
