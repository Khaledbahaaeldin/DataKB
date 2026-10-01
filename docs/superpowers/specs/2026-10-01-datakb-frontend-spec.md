# Emberbyte Frontend Spec

Status: draft for review. Data types and repository methods come from the
[API contract](2026-10-01-datakb-api-contract.md); behaviour of the engine is in the
[backend spec](2026-10-01-datakb-backend-spec.md).

Stack: Kotlin, Jetpack Compose, Material 3 Expressive (`androidx.compose.material3`), Glance for widgets,
Hilt, Navigation Compose. minSdk 29, targetSdk latest stable.

## 1. Principles

1. **The number is the product.** The first thing seen is how much data was used today, large and alive.
2. **Alive, not noisy.** Motion expresses real data (throughput, thresholds). Nothing moves for decoration.
3. **Honest.** Estimates are marked, gaps are shown, forecasts are ranges.
4. **Calm by default, powerful on request.** Live Lens and budgets are opt-in and never block the basics.
5. **Local-first.** No spinner waits on a network because there is none.

## 2. Module split

- `:ui:design`: theme, tokens and reusable components. Takes primitives and its own UI models only.
  It has no dependency on `:core:*`.
- `:app`: screens, `ViewModel`s, navigation, mapping contract types → UI models.
- `:feature:lens`: the Lens screen composables and `LensViewModel`, since the screen is tied to the
  service lifecycle.

## 3. Design tokens

**Colour.** Dynamic colour (Android 12+, `Settings.useDynamicColor`) with a hand-tuned static fallback on
API 29–30 from seed `#6750A4`-family violet. Roles used: `primary`, `secondary`, `tertiary`,
`surfaceContainer*`, `error`. Dark is the primary design target; light is fully supported; `amoledBlack`
overrides `surface` to `#000000`. Per-tab accent for the navbar: Home → `primary`, Apps → `tertiary`,
Plans → `secondary`, Lens → `error`-leaning warm accent derived from the palette (never a fixed hex).

**Typography.** Roboto Flex (variable, OFL licence, bundled subset) for the display number, using the
weight (`wght` 400–900) and width (`wdth` 100–125) axes. All other text uses M3 Expressive type roles
(`displayLarge` … `labelSmall`, with emphasised variants for headings). Only the Number is tied to the
variable axes.

**Shape.** M3 Expressive shape library: full pill for the navbar, `ExtraLarge` for tiles, and
morph-capable shapes (`RoundedPolygon` via `androidx.graphics.shapes`) for the speed tile and the
budget ring warning state.

**Motion.** All motion uses `MaterialTheme.motionScheme` specs, never hand-written durations:
`fastSpatialSpec`, `defaultSpatialSpec`, `slowSpatialSpec` for movement and shape, and
`fastEffectsSpec`, `defaultEffectsSpec`, `slowEffectsSpec` for colour and alpha. Spatial springs may
overshoot; effects springs never do. If `Settings`-independent system "remove animations" is on, springs
are replaced by 150 ms fades and the Number stops reacting to throughput (see §9).

**Spacing and layout.** 4 dp base grid; 16 dp screen margins; max content width 600 dp on larger screens
(single column centred; the navbar stays bottom-centred). Edge-to-edge with correct inset handling.

## 4. Components (`:ui:design`)

```kotlin
@Composable fun MorphingNumber(
    bytes: Long,                      // value to show, formatted by UnitSystem outside or via `formatter`
    throughputBps: Long,              // drives weight/width; 0 = at rest
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
)

@Composable fun FloatingPillNavBar(
    items: List<NavBarItem>,          // 4 items; each has id, label, icon, accent role
    selectedId: String,
    onSelect: (String) -> Unit,
    visible: Boolean,                 // driven by scroll state (see below)
    modifier: Modifier = Modifier,
)
data class NavBarItem(val id: String, val label: String, val icon: ImageVector, val accent: NavAccent)

@Composable fun BentoTile(
    title: String, modifier: Modifier = Modifier, span: TileSpan = TileSpan.Half,
    container: TileContainer = TileContainer.Default, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable fun SpeedTile(rxBps: Long, txBps: Long, network: NetworkKindUi?, modifier: Modifier = Modifier)
@Composable fun ForecastTile(model: ForecastUi, modifier: Modifier = Modifier)
@Composable fun BudgetRing(fraction: Float, state: BudgetStateUi, modifier: Modifier = Modifier)
@Composable fun UsageBarRow(points: List<BarUi>, selectedIndex: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)
@Composable fun AppRow(model: AppRowUi, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun EstimatedBadge(modifier: Modifier = Modifier)     // "Estimated" marker for isApproximate
@Composable fun GapBanner(model: GapUi, modifier: Modifier = Modifier)
@Composable fun PermissionPrompt(model: PermissionPromptUi, onAction: () -> Unit, modifier: Modifier = Modifier)
```

### 4.1 MorphingNumber

- Digits roll with `defaultSpatialSpec` when the value changes.
- `throughputBps` is normalised on a log scale between 10 KB/s (rest) and 20 MB/s (max) to `t ∈ [0,1]`,
  smoothed with `defaultEffectsSpec`, and mapped to `wght = 520 + 380·t` and `wdth = 100 + 25·t`. The text
  never changes size, so layout does not jump.
- At rest (`throughputBps == 0` or `animate == false`) it renders at `wght 520`, `wdth 100`.
- Semantics: a single node with a plain-language description (§9).

### 4.2 FloatingPillNavBar (design reference: Floating-Navbar-M3-Flutter, MIT)

Recreated in Compose from the public design, with credit in the README and `NOTICE`. Behaviour:

- **Shape and placement.** A floating pill 64 dp tall, horizontally inset 16 dp, lifted 12 dp above the
  bottom system-bar inset. Corner radius full.
- **Glass.** On API 31+ a real blur (`RenderEffect`/`Modifier.blur` on a backdrop layer) with a
  translucent `surfaceContainerHigh` tint. On API 29–30 a flat tint at 92% opacity with a subtle outline.
- **Selected pill.** The selected item expands to show icon + label inside a filled pill using the item's
  accent container colour. Unselected items show only an icon. Width, position and colour animate with
  `fastSpatialSpec` and `fastEffectsSpec`; spatial overshoot is allowed.
- **Magnetic snap.** The pill can be dragged horizontally. While dragging it follows the finger with a
  resistance curve; on release it springs to the nearest item, firing `onSelect`.
- **Hide on scroll.** A `NestedScrollConnection` hides the bar (slides down, fades) after 40 dp of downward
  scroll and shows it on any upward scroll or when the list reaches the top. `visible` is derived by the
  screen from that connection.
- **Haptics.** A light tick when the pill snaps to a new item, if `Settings.hapticsEnabled`.
- **Accessibility.** Each item is a `Role.Tab` with `selected` state; the bar is a `tablist` container
  with a content description. Touch targets are at least 48 dp. With reduced motion, no drag-morph and no
  hide animation: it just changes selection and stays visible.

## 5. Navigation shell

Single-activity `MainActivity`. Four top-level destinations in the pill navbar:

| id | Route | Screen | Module |
|---|---|---|---|
| `home` | `home` | HomeScreen | `:app` |
| `apps` | `apps` | AppsScreen → `apps/{packageName}` AppDetailScreen | `:app` |
| `plans` | `plans` | PlansScreen → `plans/{planId}` PlanEditorScreen | `:app` |
| `lens` | `lens` | LensScreen | `:feature:lens` |

Outside the bar: `settings` (reached from the top-right icon on Home), `onboarding`, and
`export/backup` dialogs inside Settings. Deep links follow contract §6. Top-level switches preserve each
destination's state (`saveState`/`restoreState`). Predictive back is supported on all detail screens.

## 6. Screens

For every screen: one `ViewModel` exposes a single `StateFlow<…UiState>` and accepts `…Event`s. UI models
are plain data built in the ViewModel from contract types. Loading uses a content placeholder, not a spinner.

### 6.1 Home

```
[ Today ]                                   [Settings ⚙]
        1.24
        GB                ← MorphingNumber (hero, ~72sp display)
 3.8 GB left · runs out Thu (±1d)  [Estimated]
 ┌───────────────┬───────────────┐
 │ Live ↓4.2MB/s │ Forecast Thu  │   ← Bento tiles, mixed spans
 ├───────────────┴───────────────┤
 │ Mobile 0.9 GB · Wi-Fi 0.3 GB  │
 ├───────────────────────────────┤
 │ Top apps now: YouTube 610 MB… │
 └───────────────────────────────┘
 [ week bar row — pull-down to expand ]
```

Data: `UsageRepository.observeToday`, `observeLiveSpeed`, `observeApps` (today),
`PlanRepository.observeActivePlanStates` + `observeForecast`, `observeCoverage`, `PermissionRepository`.

```kotlin
data class HomeUiState(
    val todayBytes: Long, val mobileBytes: Long, val wifiBytes: Long,
    val throughputBps: Long, val live: LiveSpeed?,
    val plan: PlanSummaryUi?,         // null => no plan yet (empty state)
    val forecast: ForecastUi?,
    val topApps: List<AppRowUi>,
    val week: List<BarUi>,
    val gap: GapUi?, val prompts: List<PermissionPromptUi>,
    val unitSystem: UnitSystem,
)
sealed interface HomeEvent { data object OpenSettings; data class OpenApp(val pkg: String); data class SelectDay(val index: Int); data object RefreshNow }
```

Behaviour: the hero shows "today" by default; tapping a week bar switches the hero to that day (number
rolls). When a plan is selected the subtitle shows remaining data and the forecast range. With several
plans, a chip row above the tiles switches the plan context. `isApproximate` shows `EstimatedBadge`. A gap
shows `GapBanner` ("Not measured 02:10–03:40 because the app was stopped").

States: *no plan* → hero shows today's use, a Bento "Set up your data plan" tile; *no usage access* →
`PermissionPrompt` tile, top-apps tile shows locked state; *offline* → speed tile shows "Offline".

### 6.2 Apps

List of `AppRow` (icon, name, mobile/Wi-Fi split bar, total, optional screen time). Controls: range chips
(Today, 7 days, Cycle, Month), network filter (All / Mobile / Wi-Fi), sort menu (`AppSort`), search.
Row tap → App detail.

```kotlin
data class AppsUiState(val range: RangeUi, val filter: UsageFilter, val sort: AppSort,
    val query: String, val apps: List<AppRowUi>, val totalBytes: Long, val needsUsageAccess: Boolean)
```

**App detail:** header (icon, name, total), `UsageBarRow` over the chosen range, mobile/Wi-Fi split,
screen time, the app's `BudgetStatus` with a `BudgetRing`, and "Set budget" (writes `AppBudget`). Spike
history for that app is listed. A shortcut opens the system app-data settings.

### 6.3 Plans

Cards per active plan: name, SIM label, cap ring (`fractionUsed`), remaining, days left, forecast, add-on
chips. FAB-style "New plan". Archived plans behind a toggle.

**Plan editor** (one scrolling form, saves on "Save", validates inline against `EmberbyteError.Invalid`):
name; SIM (from `SubscriptionManager`, or "Not tied to a SIM"); cap (value + unit); cycle (segmented:
*Monthly on day N* / *Every N days from date*, plus time); rollover (None / Full / Capped with amount);
**Add-on packs** list (label, bytes, valid from/until); **Free windows** list (days, start, end, optional
app, "doesn't count toward plan"); **Alerts** (threshold chips 50/80/90/100 %). A live preview card at the
top shows the resulting `PlanState` as fields change, using `PlanRepository.whatIf` for the forecast.

### 6.4 Lens (`:feature:lens`)

Driven by `LensController.state`:

| State | Screen |
|---|---|
| `Off` | Explainer: what Lens does, "all on your device", a plain statement that it uses a local VPN, "Turn on" button |
| `NeedsConsent` | System VPN dialog launched via `prepareConsentIntent()`, then back to this screen |
| `Starting` | Skeleton list |
| `Running` | Live list of `LensApp` sorted by rate, each row expandable to `topDomains`; reorders with spatial springs; header shows "Lens is on" with a Stop button; "Clear history" in the overflow |
| `Conflict` | Explains `OTHER_VPN_ACTIVE` or `ALWAYS_ON_VPN_BLOCKING` with the exact system setting to change |
| `Error` | Message and "Try again" |

```kotlin
data class LensUiState(val state: LensState, val snapshot: LensSnapshot?, val expandedUid: Int?)
```

### 6.5 Settings

Sections: Notifications (live notification, show speed, spike alerts), Appearance (dynamic colour,
AMOLED black, haptics), Units (decimal/binary), Privacy (Lens history hours, clear Lens history, privacy
statement), Data (export CSV/JSON, backup/restore, with a passphrase dialog), Permissions (current status
and quick fixes), About (version, GPL-3.0 licence, source link, third-party notices).

### 6.6 Onboarding

Three steps with skippable pages: 1) what Emberbyte measures and that it is local-only; 2) Usage Access
(explain, open system settings, detect grant on return); 3) create the first plan (guided minimal form,
more options later). Notifications are requested right after the first plan is saved, not up front.

## 7. Notification and widgets

- **Live notification** (contract channel `live`): text format "1.24 GB today · 3.8 GB left · ↓4.2 MB/s".
  Tapping opens `emberbyte://home`.
- **HeroWidget** (Glance, 2×2 and 4×2): the Number plus remaining data. **BentoWidget** (4×2 and 4×4):
  today, forecast, speed, top two apps. Both use the app's dynamic colours, refresh per contract §6, and
  open `emberbyte://home`. A widget with no plan shows "Set up a plan".

## 8. Formatting rules

- Bytes: one function `formatBytes(bytes, UnitSystem)` in `:ui:design` returning value and unit
  separately. Decimal: KB/MB/GB/TB (1000); binary: KiB/MiB/GiB/TiB (1024). Values ≥ 100 show no decimal,
  10–99 one decimal, below 10 two decimals.
- Rates append "/s". Times follow the device locale and 12/24-hour setting. Run-out dates show weekday
  and, beyond 6 days, the date; ranges read "Thu (±1 day)" derived from `runOutEarliest/Latest`.
- The forecast says "Won't run out this cycle" when all three `runOut*` are null.

## 9. Accessibility

- Contrast meets WCAG AA in dynamic and static palettes, light and dark.
- No information is colour-only: states also use shape, icon or text.
- `MorphingNumber` exposes one node: "1.2 gigabytes used today, 3.8 gigabytes left".
- Charts expose a text alternative ("Daily usage, last 7 days, highest Wednesday, 2.1 gigabytes") and are
  navigable by point.
- Respects system font scale up to 200% (tiles reflow to one column) and the system "remove animations"
  setting (§3 Motion, §4.2).
- All interactive elements ≥ 48 dp, labelled, and reachable with TalkBack and keyboard.

## 10. Testing

- **Compose UI tests:** `FloatingPillNavBar` (selection, drag-snap, hide/show), `MorphingNumber`
  (semantics and axis mapping), plan editor validation, Lens state rendering for all six `LensState`s.
- **Screenshot tests** (Roborazzi/Paparazzi) for Home, Apps, Plans, Lens in light/dark, dynamic/static,
  font scale 1.0 and 2.0, driven by `FakeUsageSource`.
- **ViewModel tests** with fake repositories and a test dispatcher.
- **Previews** for every component in `:ui:design` with a sample-data object.
- **Manual device pass** before each release: real navbar blur on API 31+ and the tint fallback on an API
  29/30 device or emulator.

## 11. Credits and licensing

The app is GPL-3.0. The floating pill navbar design is credited to
[Floating-Navbar-M3-Flutter](https://github.com/Damantha126/Floating-Navbar-M3-Flutter) (MIT) in the README
and `NOTICE`; the Compose implementation is original. Roboto Flex is OFL-licensed and listed in the
third-party notices.
