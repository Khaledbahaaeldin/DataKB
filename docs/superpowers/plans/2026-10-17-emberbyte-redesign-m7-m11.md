# Emberbyte Expressive Redesign (M7 to M11) Implementation Plan

> **For the executing agents (Antigravity, run by the user):** follow `docs/superpowers/plans/2026-10-17-redesign-antigravity-prompt.md`. It tells you how to run this plan in one go: which tasks to hand to an implementer, which to a fresh tester, and the five points where you STOP and wait for Claude. Steps use checkbox syntax. Read the Rules block below before the first task and copy it into every brief.

**Goal:** turn Emberbyte from "Material 3 components" into a recognisably Expressive app: 12 themes with light and dark, tall condensed hero numbers, a shape language, real app icons, a bento Plans screen, a guided plan flow with mid-cycle support, grouped Settings, and a family of four widgets, all shipped as one release.

**Architecture:** a palette layer (`EmberbytePalette`, generated from `tools/palettes/palettes.json`) feeds both Material's `ColorScheme` (so stock components follow the theme) and a `LocalEmberbytePalette` for custom components; new components live in `:ui:design`, screens in `:app`, plan semantics in `:core:engine` and `:core:data` (Room v4). Screens are rebuilt on the same ViewModels and data flows as today. Widgets render hero numbers, rings and bars to small bitmaps and read the same theme from DataStore.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3 `1.5.0-alpha29` (opt-in `ExperimentalMaterial3ExpressiveApi`), Glance 1.1.1, Room 2.8.5, DataStore, Robolectric 4.17, JDK 17, Python 3 for the tools in `tools/`. No new runtime dependency.

**Spec:** `docs/superpowers/specs/2026-10-17-expressive-redesign-design.md` (design, sections 1 to 13) and `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` section 13 (exact names, storage, formats; **the contract wins** where they differ, and you stop and tell the user). Mockups: `docs/design/expressive/mockups/*.html` (read the HTML source or open it in a browser; `README.md` there names the approved option on each page). Colours and generator: `tools/palettes/`.

## Global Constraints

- Package root `io.github.khaledbahaaeldin.emberbyte`; `$PKG` below means `io/github/khaledbahaaeldin/emberbyte` in paths. Modules: `:app`, `:ui:design`, `:core:engine`, `:core:data`.
- minSdk 29, targetSdk 36, compileSdk 37, JDK 17 (no Java 18+ APIs), Kotlin official style, Compose.
- No `INTERNET` permission, no `VpnService`, no analytics. Release build stays unsigned and reproducible (M6 rules).
- No new runtime dependency (the `graphics-shapes` library arrives transitively with Material 3). Removing a dependency (Haze, task A13) is allowed.
- Roboto Flex is bundled at `ui/design/src/main/res/font/roboto_flex.ttf` and is the only font; variable axes through `FontVariation`.
- Colour only through the palette: no `Color(0x...)` literal outside `ui/design/.../theme/palette/` (enforced by test A6). Text contrast at least 4.5:1 (hero text 7:1 on background), marks and rings at least 3:1, in all 12 themes, both modes, and with AMOLED.
- Accessibility contract 12.2 applies to everything, plus: shaped app icons are hidden from semantics; the hero number is ONE node ("1.38 gigabytes used today, 12.4 gigabytes left"); every state also uses an icon or text; touch targets at least 48 dp; at font scale 2.0 nothing clips (the hero number shrinks inside its container); reduced motion snaps every animation (`LocalReduceMotion`); right-to-left mirrors layouts and keeps numbers with their units (`ForceLtr`).
- Database version 4 only in task C2; backup `contractVersion` 2 only in task C3 (contract 13.4 and 13.5).
- English strings only. One release train: versionName `1.0.0`, versionCode `1`; nothing is tagged or released by this plan.
- Test counts only grow: baseline at the start of this plan (`main`, M6 merged) is 1068 tests (`:app` 471, `:core:data` 323, `:core:engine` 125, `:ui:design` 149), lint 0 errors and 11 warnings (identical to before; add no warning).

## Review Focus

The input classes and conditions most likely to bite a user that the design implies but no single task would think of. Each line has its pinning test in the task that owns the code.

1. **A theme that is unreadable in one of its 25 combinations** (12 themes x light/dark, plus AMOLED): task A6 (`PaletteContrastTest`, `NoHardcodedColorsTest`) and every component task adds its pairs to the registry.
2. **A package without a loadable icon** (`uid:` pseudo-apps, apps uninstalled after being measured, work-profile or disabled apps, a huge icon): fallback letter on a shape, never a crash, never a slow list; task A9 (`AppIconProviderTest`, `AppIconTest`).
3. **A plan created mid-cycle in every combination**: entered amount larger than the cap, entered at the cycle's first minute, entered in one cycle and read in the next, an every-N-days cycle, a DST change inside the cycle, "starts next cycle" created on the last day; tasks C1 and C9.
4. **An upgrade from the M6 build with data**: Room 3 to 4 keeps every plan as `phone`, the DataStore theme migration (key absent, true, false), an old backup (`contractVersion` 1) restoring into the new schema, and an already placed Hero widget still rendering; tasks A4, C2, C3, E3, C10.
5. **Font scale 2.0 + right-to-left + reduced motion on the new shapes** (hero number, compact navbar, guided plan flow, bento tiles, dialogs with the keyboard open); tasks A7, A13, B2, C5, C7, D3 and every proof task.

---

## Rules (absolute; copy this block verbatim at the top of EVERY implementer and tester brief)

- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`. Use `git switch` for branches only. Never touch anything under `docs/superpowers/` (Claude's files; `git diff --stat HEAD -- docs/superpowers` must be empty after every subagent; if not, or `grep -c "## 13. Milestones 7 to 11" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user). Stage only your files by explicit path. Never add anything under `.superpowers/` to git. Never push, merge or tag.
- **Briefs come only from the `awk` command of the prompt and are never edited, shortened or summarised**; `diff` each brief against the plan section.
- **Truth rules.** Quote only what you ran, with the command and its raw output. Statements about the device come from `adb -s emulator-5554 exec-out screencap -p` images you opened and described, never from `uiautomator` bounds alone. Counts ("tests added", "removed", "deviations") are COMPUTED: `python tools/test-diff.py <base> HEAD` (task A1 creates it) and `git diff --stat`, pasted into the report. Before a report is final run `python tools/check-cited-tests.py <report>` and paste the exit code. Say "CANNOT-VERIFY" when true. No PASS line that is not computed. List every deviation from the plan and every pre-existing test you changed with the reason.
- **A test must be able to fail.** Show each new or changed test failing on the code before your change (temporary revert or a `git archive` copy; stash is forbidden) and quote the failure. A test that fails 1 time in 10 is failing: run new tests 10 times in isolation. Never delete or rename an existing test except where this plan says a component is replaced; then list each in the report with the test that now covers the same behaviour. Never `@Ignore`; never an `Assume` that can skip silently.
- No new dependency; JDK 17; privacy: no payload, package names, addresses or UIDs in logs; no `INTERNET`.
- Branch `redesign` (already exists with this plan committed). Use the git identity configured in your checkout. Commit messages explain WHY (a body of at least two lines) and end with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Gate (after every task, from the repository root, with `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2` on small machines): `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --stacktrace`. Between tasks of one module you may run just that module's `testDebugUnitTest` and `lintDebug`; run the full gate at the end of every task that touches more than one module and at every milestone end.
- Device work only on the AVD `Violet_API_36` with `adb -s emulator-5554` (a physical phone may be attached and must never be touched). Start exactly ONE emulator; one Gradle build at a time. Clean install per proof; restore every setting you change (font scale, density, animation scales, locale, night mode); `adb emu kill` at the end. Seed data with `tools/seed/make_backup.py` (task A1).
- Mockups are the reference for colours, shapes, copy and layout, not a pixel spec; exact dp values are yours to choose within the design document. Where a mockup and the design document disagree, the document wins.

## How the run is organised

- Task ids are `A1`..`A16` (M7 Foundation), `B1`..`B7` (M8 Core screens), `C1`..`C10` (M9 Plans), `D1`..`D7` (M10 Onboarding, Settings, system surfaces), `E1`..`E8` (M11 Widgets), `Z1`, `Z2` (finish).
- Five STOPS where you print the stop message of the prompt and wait for Claude: after **A15** (palette checkpoint), after **A16** (M7 audit), after **C10** (M9 audit), after **D7** (M10 audit), after **Z2** (final audit). Nothing else stops the run except the conditions in the prompt.
- Each task ends with one commit. A task with no production change and no test in its diff is not done (the proof and tooling tasks name their deliverables).
- Proof tasks (`A16`, `B7`, `C10`, `D7`, `E8`, `Z2`) are verified by a fresh tester; other tasks by the gate and the scripts.

---


# PART 1: M7 FOUNDATION (tasks A1 to A16)

Outcome: the app runs on the new palette layer with 12 themes, light and dark, an Appearance screen, the type and shape system, real app icons in the Apps list, the new components and the compact navbar; existing screens keep their layouts (they are rebuilt in M8 to M10) but already wear the new themes. Ends with a palette checkpoint and an audit stop.

### Task A1: Truth tooling and baseline

**Files:**
- Create: `tools/test-diff.py`, `tools/test_test_diff.py`, `tools/seed/make_backup.py`, `tools/seed/fixtures/small.emberbyte` (generated, committed), `core/data/src/test/kotlin/$PKG/data/backup/SeededBackupFixtureTest.kt`, `tools/device/README.md`
- Modify: `.github/workflows/ci.yml` (add the python checks)

**Interfaces:**
- Produces: `python tools/test-diff.py <base> <head> [--allow FILE]` prints one line per change: `REMOVED path::name`, `RENAMED path::old -> new`, `SHRUNK path::name asserts 5->3`, then `ADDED <n>`; exit 1 when a REMOVED or SHRUNK entry is not listed in the `--allow` file (lines `path::name  reason`). A test function is a `fun` directly preceded by `@Test` in a `*Test.kt` file.
- Produces: `python tools/seed/make_backup.py --out FILE --passphrase TEXT [--days N] [--scenario basic|midcycle]` writes an encrypted `.emberbyte` following `docs/backup-format.md` (needs `pip install argon2-cffi cryptography`, development only; `--selftest` decrypts its own output). `basic` = 1 plan (cap 20 GB, monthly on day 1), 1 budget, N days of hourly usage for 6 fake packages; `midcycle` adds the three first-cycle variants once task C3 defines the fields (until then it is the same as `basic`).

- [ ] **Step 1: Write the failing tests.** `tools/test_test_diff.py` builds a temp git repo with a Kotlin test file, commits, then (a) deletes a test, (b) renames one, (c) removes two `assert` lines from one, (d) adds one; asserts the output lines above and exit codes with and without `--allow`. `SeededBackupFixtureTest` restores `tools/seed/fixtures/small.emberbyte` (passphrase `test-passphrase`, 7 days, `basic`) through `DefaultBackupService` into the in-memory stores and asserts 1 plan, 1 budget and the usage row count printed by the script (`make_backup.py` prints `rows=<n>`; the test hard-codes that n).
- [ ] **Step 2: Run to verify they fail** (`python tools/test_test_diff.py`; `./gradlew :core:data:testDebugUnitTest --tests "*SeededBackupFixtureTest"`).
- [ ] **Step 3: Implement the two scripts** (signatures above), generate the fixture, write `tools/device/README.md` (how to start ONE emulator, restore a seeded backup through Settings, grant usage access with `appops set io.github.khaledbahaaeldin.emberbyte GET_USAGE_STATS allow`, set font scale, density, locale and animation scales, and the `screencap` command; every command restores what it changes).
- [ ] **Step 4: CI.** Add steps running `python tools/test_test_diff.py`, `python tools/test_check_cited_tests.py` and `python tools/palettes/generate_palettes.py --check` (the last one passes once A2 has generated its files; add it in A2, not here).
- [ ] **Step 5: Baseline.** Run the full gate on the untouched branch; write the per-module counts and the lint list into the ledger. They must equal the baseline in Global Constraints.
- [ ] **Step 6: Commit** `build(tools): add the test-diff and seeded-backup tools and record the redesign baseline`.

### Task A2: Palette types and the generated palettes

**Files:**
- Create: `ui/design/src/main/kotlin/$PKG/ui/design/theme/palette/EmberbytePalette.kt`, `.../palette/GeneratedPalettes.kt` (generated), `docs/design/expressive/palettes-report.md` (generated), `ui/design/src/test/kotlin/$PKG/ui/design/theme/palette/PalettesTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:** Produces `EmberbytePalette` and `GeneratedPalettes` exactly as contract 13.1.

- [ ] **Step 1: Write the failing test** `PalettesTest`: `generated_palettes_have_the_twelve_ids` (`GeneratedPalettes.keys == setOf("dusk","ember","monochrome","catppuccin","gruvbox","nord","rosepine","solarized","dracula","tokyonight","everforest","kanagawa")`); `every_theme_has_distinct_light_and_dark` (`dark.background != light.background`); `dark_backgrounds_are_dark_and_light_backgrounds_are_light` (luminance below 0.2 and above 0.5); `dusk_dark_matches_the_approved_reference` (`primary == Color(0xFFD0BCFF)`, `onPrimary == Color(0xFF2A1858)`, `background == Color(0xFF110F19)`, `accent == Color(0xFFC9F0D3)`).
- [ ] **Step 2: Run to verify it fails** (classes missing).
- [ ] **Step 3: Implement** `EmberbytePalette` (14 `Color` roles, `@Immutable`) and run `python tools/palettes/generate_palettes.py` (it writes `GeneratedPalettes.kt` and the report; do not edit them by hand).
- [ ] **Step 4: Run** the test, then `python tools/palettes/generate_palettes.py --check` (prints `up to date`); add that command to `ci.yml`.
- [ ] **Step 5: Commit** `feat(theme): add the palette type and the twelve generated palettes`.

### Task A3: Palette to ColorScheme and the new `EmberbyteTheme`

**Files:**
- Create: `.../theme/palette/PaletteMapping.kt`, `.../theme/EmberbyteThemeSpec.kt`
- Modify: `.../theme/EmberbyteTheme.kt`, delete `.../theme/EmberbyteColors.kt` (the old `EmberLightColors`/`EmberDarkColors` become the `ember` preset), tests `EmberbyteThemeTest.kt`, `EmberbyteSurfaceTest.kt` and every file that calls the old `EmberbyteTheme(darkTheme=, dynamicColor=, amoledBlack=)` (search `EmberbyteTheme(`)
- Test: `.../theme/palette/PaletteMappingTest.kt`

**Interfaces:**
- Consumes: `EmberbytePalette`, `GeneratedPalettes` (A2).
- Produces (contract 13.1): `data class EmberbyteThemeSpec(presetId, darkTheme, amoled, wallpaperColors)` with `companion val Default = EmberbyteThemeSpec("dusk", true, false, false)`; `fun EmberbytePalette.toColorScheme(dark: Boolean): ColorScheme`; `fun EmberbytePalette.Companion.fromDynamic(scheme: ColorScheme): EmberbytePalette`; `val LocalEmberbytePalette`; `@Composable fun EmberbyteTheme(spec: EmberbyteThemeSpec, content: @Composable () -> Unit)`; internal `fun resolvePalette(spec: EmberbyteThemeSpec, sdkInt: Int, dynamic: () -> ColorScheme): EmberbytePalette`.

- [ ] **Step 1: Write the failing tests.** `PaletteMappingTest`: mapping table (assert each pair): `primary=palette.primary`, `onPrimary`, `primaryContainer=primary`, `onPrimaryContainer=onPrimary`, `secondary=primary`, `secondaryContainer=surface2`, `onSecondaryContainer=text`, `tertiary=accent`, `onTertiary=onAccent`, `tertiaryContainer=accent`, `onTertiaryContainer=onAccent`, `error=danger`, `onError=onDanger`, `errorContainer=danger`, `onErrorContainer=onDanger`, `background=background`, `onBackground=text`, `surface=background`, `onSurface=text`, `surfaceVariant=surface2`, `onSurfaceVariant=muted`, `outline=muted`, `outlineVariant=track`, `surfaceContainerLowest=background`, `surfaceContainerLow=surface`, `surfaceContainer=surface`, `surfaceContainerHigh=surface2`, `surfaceContainerHighest=surface2`, `inverseSurface=text`, `inverseOnSurface=background`, `inversePrimary=primary`, `surfaceTint=primary`; `amoled_turns_dark_background_and_surface_pure_black_only_in_dark` (spec amoled=true: dark background `Color.Black`, light unchanged); `unknown_preset_id_falls_back_to_dusk`; `wallpaper_colors_use_the_dynamic_scheme_on_sdk_31_and_are_ignored_on_sdk_30` (via `resolvePalette` with a fake dynamic scheme); `wallpaper_palette_keeps_dusk_warn_and_danger`. `EmberbyteThemeTest` (Robolectric): the content sees `LocalEmberbytePalette.current == GeneratedPalettes["dusk"]!!.first` for `Default` and `MaterialTheme.colorScheme.primary == Color(0xFFD0BCFF)`; `LocalReduceMotion` still provided.
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** the mapping exactly as the table, `resolvePalette` (preset lookup, wallpaper only when `sdkInt >= 31`, AMOLED applied last on the dark palette), the theme function (keep `MaterialExpressiveTheme` and `MotionScheme.expressive()`, keep `rememberReduceMotion`). Update every caller to `EmberbyteThemeSpec` (tests use `EmberbyteThemeSpec.Default` or `.copy(...)`).
- [ ] **Step 4: Run** `:ui:design:testDebugUnitTest :app:testDebugUnitTest` (callers compile) then the full gate.
- [ ] **Step 5: Commit** `feat(theme): build the Material colour scheme and the theme from the palette layer`.

### Task A4: Theme settings and the one-time migration

**Files:**
- Modify: `core/data/src/main/kotlin/$PKG/data/SettingsRepository.kt`, `.../DataStoreSettingsRepository.kt`, `.../fake/FakeSettingsRepository.kt` (or the repository fake that exists; search), every use of `useDynamicColor` (search the repo; `SettingsViewModel`, `SettingsScreen`, `EmberbyteApp`, tests)
- Test: `core/data/src/test/kotlin/$PKG/data/DataStoreRepositoriesTest.kt`, new `.../data/ThemeSettingsMigrationTest.kt`

**Interfaces:** Produces contract 13.2 (`ThemeMode`, `Settings.themeId`, `themeMode`, `useWallpaperColors`, keys `theme_id`, `theme_mode`, `use_wallpaper_colors`, `theme_migrated`).

- [ ] **Step 1: Write the failing tests.** `ThemeSettingsMigrationTest` (real DataStore in a temp file like the existing tests): `fresh_install_gets_dusk_and_no_wallpaper_colors`; `legacy_true_keeps_wallpaper_colors_on`; `legacy_false_gets_dusk`; `migration_runs_once_even_if_the_legacy_key_changes_later`; `a_migrated_user_keeps_their_chosen_theme`. `DataStoreRepositoriesTest.every_setting_survives_a_round_trip` gains `themeId = "kanagawa"`, `themeMode = ThemeMode.LIGHT`, `useWallpaperColors = true`.
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** the model change, the keys, and the migration inside the first `observe()` read (write the keys and `theme_migrated` in one `edit`). Remove `useDynamicColor` everywhere; the old Settings "Dynamic colour" switch is replaced in A14, so until then `SettingsScreen` shows no theme switches (delete that row).
- [ ] **Step 4: Run** `:core:data:testDebugUnitTest :app:testDebugUnitTest`, then the full gate.
- [ ] **Step 5: Commit** `feat(settings): add the theme, mode and wallpaper-colour settings with a one-time migration`.

### Task A5: Wire the theme into the app

**Files:**
- Modify: `app/src/main/kotlin/$PKG/EmberbyteApp.kt`, `MainActivity.kt`, `AppGraph.kt` (a `themeSpec: StateFlow<EmberbyteThemeSpec?>` derived from `settingsState`)
- Test: `app/src/test/kotlin/$PKG/ThemeSpecMappingTest.kt`

**Interfaces:** Consumes A3, A4. Produces `internal fun themeSpecOf(settings: Settings, systemDark: Boolean, sdkInt: Int): EmberbyteThemeSpec` in `$PKG/ThemeSpec.kt`.

- [ ] **Step 1: Write the failing test** `ThemeSpecMappingTest`: SYSTEM follows `systemDark`; LIGHT and DARK ignore it; `amoled` only when dark and `amoledBlack`; `wallpaperColors` only when `useWallpaperColors && sdkInt >= 31`; `presetId = settings.themeId`.
- [ ] **Step 2: Run to verify it fails. Step 3: Implement** `themeSpecOf` and use it where `EmberbyteApp` sets the theme (collect `isSystemInDarkTheme()` there); status-bar icon colours follow `darkTheme`. **Step 4: Run** the gate. **Step 5: Commit** `feat(app): apply the chosen theme and mode`.

### Task A6: The contrast gate

**Files:**
- Create: `ui/design/src/main/kotlin/$PKG/ui/design/theme/palette/ContrastPairs.kt`, tests `ui/design/src/test/kotlin/$PKG/ui/design/theme/palette/PaletteContrastTest.kt`, `app/src/test/kotlin/$PKG/NoHardcodedColorsTest.kt`, `ui/design/src/test/kotlin/$PKG/ui/design/theme/NoHardcodedColorsDesignTest.kt`

**Interfaces:** Produces `internal data class ContrastPair(val name: String, val foreground: (EmberbytePalette) -> Color, val background: (EmberbytePalette) -> Color, val minRatio: Double)` and `internal object ContrastPairs { val all: List<ContrastPair>; fun register(pair: ContrastPair) }`; every component task adds its pairs here (hero text on background 7.0; muted on background and surface 4.5; text on surface and surface2 4.5; `onPrimary`/`primary`, `onAccent`/`accent`, `onWarn`/`warn`, `onDanger`/`danger` 4.5; `primary`/`background`, `primary`/`surface`, `track`/`primary` 3.0, and `accent`, `warn`, `danger` marks on `background` 3.0).

- [ ] **Step 1: Write the failing tests.** `PaletteContrastTest.every_pair_passes_in_every_theme_and_mode` loops 12 ids x dark/light (+ the AMOLED variant of every dark palette) over `ContrastPairs.all` and reports each failure as `theme/mode/pair ratio`; `contrast_ratio_matches_wcag_reference_values` (black on white 21.0, `#777777` on white about 4.48). `NoHardcodedColorsTest` (both modules) walks `src/main` sources and fails on the regex `Color\(0x` or `Color\.(Red|Blue|Green|Yellow|Gray|Cyan|Magenta)` outside `theme/palette/` and files listed in an allow-list constant (empty at the start; entries need a comment).
- [ ] **Step 2: Run to verify they fail** (the old code has literal colours in `UsageBarRow` or elsewhere: list them; fix by replacing with palette roles in this task, or add each to the allow-list with a `// replaced in B1` style comment and a matching removal task id).
- [ ] **Step 3: Implement** `ContrastPairs` and the tests. **Step 4: Run** the gate. **Step 5: Commit** `test(theme): enforce contrast in every theme and forbid hard-coded colours`.

### Task A7: Typography and the hero number

**Files:**
- Create: `.../theme/EmberbyteType.kt`, `.../number/HeroNumber.kt`, `.../tiles/UnitBadge.kt`
- Modify: `.../number/MorphingNumber.kt` (uses `EmberbyteType.hero` and `UnitBadge`; same public parameters), `NumberAxes`
- Test: `.../theme/EmberbyteTypeTest.kt`, `.../number/HeroNumberTest.kt`

**Interfaces:** Produces contract 13.1 `EmberbyteType`; `@Composable fun HeroNumber(value: String, unit: String, contentDescription: String, modifier: Modifier = Modifier, size: TextUnit = 96.sp, color: Color = LocalEmberbytePalette.current.primary)` (value and badge in one row; ONE semantics node with `contentDescription`; the value text shrinks (binary search over `size`) until the row fits the available width, minimum 36 sp, maxLines 1, `ForceLtr`); `@Composable fun UnitBadge(unit: String, size: Dp = 62.dp, modifier: Modifier = Modifier)` (cookie shape from A8 is not available yet: draw with `MaterialShapes.Cookie9Sided.toShape()`; the badge colour is `primary`, text `onPrimary`, ForceLtr, hidden from semantics because the parent describes it).

- [ ] **Step 1: Write the failing tests.** `EmberbyteTypeTest`: `hero_style_has_the_design_axes` (the style's `fontFamily` is non-null, `letterSpacing == (-0.04).em`, `fontSize == size`); `axis_table_is_exact` (an internal `EmberbyteType.axes(role)` returns wght/wdth/opsz exactly as contract 13.1: hero 800/36/144, badge 850/110/null, title 800/40/144, heading 800/105/null, value 850/75/null). `HeroNumberTest`: `exposes_one_node_with_the_description`; `fits_at_font_scale_2_in_a_220_dp_container` (bounds of the text node within the container); `shrinks_but_never_below_36sp`; `unit_badge_is_hidden_from_semantics`; `reduce_motion_snaps_the_morph` (the existing `MorphingNumberTest` cases stay green).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement.** Register the pairs `primary` hero text on `background` (7.0 already registered in A6 as "hero text").
- [ ] **Step 4: Run** `:ui:design:testDebugUnitTest`, then the gate. **Step 5: Commit** `feat(type): add the Roboto Flex type roles and the hero number`.

### Task A8: The shape system

**Files:**
- Create: `.../shapes/EmberbyteShapes.kt`, `.../shapes/ShapedBox.kt`
- Test: `.../shapes/EmberbyteShapesTest.kt`

**Interfaces:** Produces `enum class AppShape { Cookie, Squircle, Sunny, Burst }`; `fun AppShape.toShape(): Shape` (Cookie to `MaterialShapes.Cookie9Sided`, Sunny to `MaterialShapes.Sunny`, Burst to `MaterialShapes.SoftBurst`, each through `toShape()`; Squircle to `RoundedCornerShape(32)` percent); `enum class Corner { TopStart, TopEnd, BottomEnd, BottomStart }`; `fun mixedCorners(large: Dp, small: Dp, smallCorner: Corner): CornerBasedShape` (three corners `large`, one `small`; start/end flip in RTL because the shape is `RoundedCornerShape` with `CornerSize`); `@Composable fun ShapedBox(shape: Shape, color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)`; `fun shapeFor(packageName: String): AppShape` (stable hash: `(packageName.hashCode() and 0x7fffffff) % 4`).

- [ ] **Step 1: Write the failing tests** `EmberbyteShapesTest`: `shape_for_is_stable_and_uses_all_four_shapes_over_200_names`; `mixed_corners_gives_three_large_and_one_small` (resolve against a 100 x 100 px size and `Density`; assert radii 38/38/38/12 for the chosen corner); `mixed_corners_flip_in_rtl` (`LayoutDirection.Rtl` swaps TopStart to the right); `every_app_shape_resolves_to_a_nonempty_outline`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(shapes): add the app shape set and mixed-corner containers`.


### Task A9: Real app icons

**Files:**
- Create: `app/src/main/kotlin/$PKG/appicons/AppIconProvider.kt`, `ui/design/src/main/kotlin/$PKG/ui/design/tiles/AppIcon.kt`
- Modify: `ui/design/.../tiles/AppRow.kt` (the letter box becomes `AppIcon`), `app/.../apps/AppsScreen.kt` and `app/.../home/HomeScreen.kt` (supply icons through an `icons: (String) -> ImageBitmap?` lambda created with `produceState`), `AppGraph.kt` (`val appIcons: AppIconProvider`)
- Test: `app/src/test/kotlin/$PKG/appicons/AppIconProviderTest.kt`, `ui/design/src/test/kotlin/$PKG/ui/design/tiles/AppIconTest.kt`

**Interfaces:** Consumes `AppShape`, `shapeFor` (A8). Produces contract 13.6 with `shapeFor` living in `:ui:design` (A8) and `AppIconProvider.icon(packageName): ImageBitmap?` in `:app`.

- [ ] **Step 1: Write the failing tests.** `AppIconProviderTest` (Robolectric, shadow `PackageManager` with a registered package and a drawable): `returns_a_96px_bitmap_for_an_installed_package`; `returns_null_for_uid_pseudo_packages`; `returns_null_for_an_unknown_package_and_does_not_throw`; `second_call_is_served_from_the_cache` (loader invoked once); `cache_holds_64_entries_and_evicts_the_oldest`; `a_huge_icon_is_scaled_to_96px`. `AppIconTest`: `shows_the_first_letter_when_there_is_no_icon` (text node exists in the unmerged tree), `the_whole_icon_is_hidden_from_semantics` (merged tree has no node), `the_shape_comes_from_the_argument`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement** the provider (a `LruCache<String, ImageBitmap>` of 64, loading on `Dispatchers.IO`; `Drawable.toBitmap(96, 96)` through `androidx.core.graphics.drawable.toBitmap`), `AppIcon` (shape clip from `shape.toShape()`, letter on `primary`/`onPrimary` roles), and the wiring so Apps rows and Home's top apps show real icons.
- [ ] **Step 4: Run** the gate. **Step 5: Commit** `feat(icons): show each app's real icon in a stable shape`.

### Task A10: Connected button groups and expressive switches

**Files:**
- Create: `.../controls/ConnectedButtonGroup.kt`, `.../controls/ExpressiveSwitch.kt`
- Test: `.../controls/ConnectedButtonGroupTest.kt`, `.../controls/ExpressiveSwitchTest.kt`

**Interfaces:** Produces `@Composable fun ConnectedButtonGroup(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` (pill ends, 8 dp inner corners, the selected segment a full pill with a check mark and `primary`/`onPrimary`; unselected `surface2`/`muted`; semantics: `selectableGroup`, each segment `Role.RadioButton`, `selected`, min height 48 dp, labels in `ForceLtr`) and `@Composable fun ExpressiveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` (M3 `Switch` with `thumbContent` = a check icon when on; `Role.Switch`). Prefer the stock `ButtonGroup`/`ToggleButton` if they give this look on the pinned alpha; otherwise build on `Row` and `Surface` and note the reason in the report.

- [ ] **Step 1: Write the failing tests.** Group: `selected_segment_has_selected_state_and_a_check_icon`; `clicking_another_segment_calls_onSelect_with_its_index`; `every_segment_is_at_least_48dp_tall`; `three_segments_fit_in_a_280dp_container_at_font_scale_2` (no clipped label: widest label's bounds inside the container, labels may wrap to two lines); `rtl_reverses_the_segment_order`; `disabled_group_ignores_clicks`. Switch: `thumb_shows_a_check_only_when_on`; `toggle_calls_the_callback`; `has_role_switch_and_is_48dp`. Register contrast pairs (selected segment `onPrimary` on `primary`; unselected `muted` on `surface2`; `primary` vs `surface2` 3.0).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(controls): add connected button groups and expressive switches`.

### Task A11: Wavy ring, wavy bar and the budget ring migration

**Files:**
- Create: `.../progress/WavyRing.kt`, `.../progress/WavyBar.kt`
- Modify: `.../plan/BudgetRing.kt` (draws with `WavyRing`; same signature), `.../plan/PlanCard.kt` (colours from the palette)
- Test: `.../progress/WavyRingTest.kt`, `.../progress/WavyBarTest.kt`; the existing `BudgetRingTest`, `PlanCardAccessibilityTest`, `PlanCardTest` stay green

**Interfaces:** Produces `@Composable fun WavyRing(fraction: Float, state: BudgetStateUi, size: Dp, label: String, modifier: Modifier = Modifier, labelSize: TextUnit = 17.sp)` (a wavy 360 degree track in `track`, the arc in the state colour: Ok `primary`, Warning `warn`, Exceeded `danger`; 0 percent draws a thin `muted` outline ring; fractions above 1 draw a full ring; the label is `EmberbyteType.value`, ForceLtr, at font scale above 1.3 its size is capped as `BudgetRing` does today; a warning or exceeded state adds the burst "!" mark) and `@Composable fun WavyBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp)` (wavy filled part, flat dim remainder, round caps). Prefer the stock `CircularWavyProgressIndicator`/`LinearWavyProgressIndicator` if they accept these colours and a custom track; otherwise draw with `Canvas` and say so. Animation: `MaterialTheme.motionScheme.defaultSpatialSpec()`, `snap()` with reduced motion.

- [ ] **Step 1: Write the failing tests.** `WavyRingTest`: `state_colours_follow_the_palette` (via a test seam `internal fun ringColors(state, palette): Pair<Color, Color>`); `warning_and_exceeded_show_a_described_mark` (node with content description "Nearly used up" / "Over the cap"); `label_inside_the_ring_at_font_scale_2` (reuse the cap test approach of `BudgetRingTest`: read the drawn font size through `GetTextLayoutResult`); `zero_percent_is_visible` (outline colour contrast against `surface` at least 3.0, using the registry); `fraction_above_one_is_a_full_ring`; `reduce_motion_snaps`. `WavyBarTest`: `fraction_is_clamped`; `exposes_progress_semantics` (`ProgressBarRangeInfo`). Register pairs (`primary`/`warn`/`danger` arcs on `track` 3.0 and on `surface` 3.0).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(progress): add the wavy ring and bar and use them for budgets and plans`.

### Task A12: Tiles, brand mark and notices

**Files:**
- Create: `.../tiles/BrandMark.kt`, `.../tiles/NoticeLine.kt`, `.../tiles/NetworkBar.kt`
- Modify: `.../tiles/BentoTile.kt` (new `corner: Corner? = null` parameter and `container: TileContainer` extended with `Accent`, `Warn`, `Danger`, `Raised`), `Tiles.kt` (`SpeedTile`, `ForecastTile`, `EstimatedBadge` use the palette and mixed corners)
- Test: `.../tiles/TilesTest.kt` (extended), `.../tiles/NetworkBarTest.kt`, `.../tiles/NoticeLineTest.kt`, `.../tiles/BrandMarkTest.kt`

**Interfaces:** Produces `@Composable fun BrandMark(size: Dp, modifier: Modifier = Modifier)` (burst shape in `primary` with the flame in `onPrimary`; decorative, hidden from semantics); `@Composable fun NoticeLine(text: String, modifier: Modifier = Modifier)` (slim rounded line with an info icon, `muted` text on `surface`); `@Composable fun NetworkBar(mobileBytes: Long, wifiBytes: Long, mobileColor: Color, wifiColor: Color, modifier: Modifier = Modifier, height: Dp = 16.dp)` (two rounded segments with a 3 dp gap proportional to the bytes, a minimum visible width of 6 dp for a non-zero segment, spoken description "Mobile 43 kilobytes, Wi-Fi 1.3 gigabytes"); `BentoTile(title, modifier, container, corner, onClick, content)` keeps its old parameters (default `corner = null` keeps today's shape).

- [ ] **Step 1: Write the failing tests** (names as the files above): `network_bar_gives_a_tiny_nonzero_segment_a_visible_width`, `network_bar_all_wifi_shows_no_mobile_segment`, `network_bar_describes_both_values`, `notice_line_is_one_line_at_font_scale_1_and_wraps_at_2`, `brand_mark_is_hidden_from_semantics`, `bento_tile_corner_shape_has_one_small_corner`, `forecast_tile_uses_the_accent_container`. Register pairs for each tile container (`onAccent`/`accent`, `onWarn`/`warn`, `onDanger`/`danger`, `text`/`surface2`).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(tiles): add the brand mark, network bar, notice line and mixed-corner tiles`.

### Task A13: The compact navbar

**Files:**
- Create: `.../navbar/CompactNavBar.kt`, `.../navbar/PlusButton.kt`
- Modify: `app/.../EmberbyteApp.kt` (use `CompactNavBar`; on the Plans route pass `trailing`), `.../navbar/NavBarLogic.kt` (reuse the snap/drag maths), `ui/design/build.gradle.kts` and `gradle/libs.versions.toml` (remove Haze), delete `GlassSource.kt`, `FloatingPillNavBar.kt`, `NavBarVisibility.kt` only after their behaviour is covered (see Step 1)
- Test: `.../navbar/CompactNavBarTest.kt` plus adapted `NavBarLogicTest`, `NavBarVisibilityTest`, `NavBarFontScaleTest`, `NavBarHousekeepingTest`

**Interfaces:** Consumes `NavBarItem(id, label, icon, accent)` (keep). Produces `@Composable fun CompactNavBar(items: List<NavBarItem>, selectedId: String, onSelect: (String) -> Unit, visible: Boolean, modifier: Modifier = Modifier, hapticsEnabled: Boolean = true, trailing: (@Composable () -> Unit)? = null)`: a centred solid pill (palette `surface2`) with equal slots of 68 dp (icon over a 10.5 sp label, a 54 x 28 dp `primary` pill behind the selected icon), padding 6 dp, total width `68 * items.size + 20` dp for every selection; while dragging a `primary` pill stretches elastically between the pressed and the nearest slot (the existing `pillFollowSpec`), snapping with a light haptic tick; slides out when `visible` is false; with reduced motion no stretch, an instant jump; `PlusButton(onClick, contentDescription)` is a 56 dp `primary` circle with a plus, placed to the right of the bar when `trailing` is given.

- [ ] **Step 1: Write the failing tests** `CompactNavBarTest`: `total_width_is_identical_for_every_selected_item`; `each_slot_is_68dp_wide_and_at_least_48dp_tall`; `selected_item_is_a_tab_with_selected_state_and_the_others_are_not`; `tapping_another_item_calls_onSelect`; `dragging_from_item_0_to_item_2_selects_item_2` (LTR and RTL); `dragging_works_with_animations_off` (reduced motion: selection follows, no stretch); `haptic_tick_fires_once_per_snap` (fake `HapticFeedback`); `hidden_when_not_visible`; `at_font_scale_2_labels_stay_one_line_and_the_bar_does_not_overflow_a_360dp_screen`; `trailing_plus_button_sits_to_the_right_and_is_56dp`. **Replaced tests:** the old `FloatingPillNavBarTest` and `GlassNavBarTest` cases for glass blur are obsolete because the glass is removed; for every other old case (selection, drag-snap, hide/show, accessibility names, 48 dp, RTL) the new tests above must exist; list the mapping old test name to new test name in the report and in the `--allow` file for `tools/test-diff.py`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement** and migrate `EmberbyteApp` (the Plans route shows `PlusButton` that opens the new-plan flow; until C7 it calls the existing `navController.navigate(Routes.planEditor(0))`). Remove Haze and its `NOTICE` entry only if nothing else uses it (grep).
- [ ] **Step 4: Run** the full gate (release APK and `NoticeCoversDependenciesTest` included). **Step 5: Commit** `feat(navbar): replace the glass pill by a compact, fixed-width bar with equal slots`.

### Task A14: The Appearance screen

**Files:**
- Create: `app/src/main/kotlin/$PKG/settings/AppearanceScreen.kt`, `AppearanceViewModel.kt`
- Modify: `app/.../nav/Routes.kt` (`const val APPEARANCE = "settings/appearance"`), `EmberbyteApp.kt` (route), `settings/SettingsScreen.kt` (an "Appearance" entry row that opens it, showing the current theme name; the Haptics switch moves into the new screen), `SettingsViewModel.kt`
- Test: `app/src/test/kotlin/$PKG/settings/AppearanceViewModelTest.kt`, `AppearanceScreenTest.kt`

**Interfaces:** Consumes `Settings`, `ThemeMode`, `GeneratedPalettes` (a public read-only `val ThemePresets: List<ThemePreset>` with `data class ThemePreset(val id: String, val name: String, val dark: EmberbytePalette, val light: EmberbytePalette)` is added in `:ui:design` for the grid; names: Dusk, Ember, Monochrome, Catppuccin, Gruvbox, Nord, Rosé Pine, Solarized, Dracula, Tokyo Night, Everforest, Kanagawa). Produces `AppearanceUiState(themeId, themeMode, useWallpaperColors, amoled, haptics, wallpaperSupported: Boolean, amoledEnabled: Boolean)` and events `SetTheme(id)`, `SetMode(mode)`, `SetWallpaper(on)`, `SetAmoled(on)`, `SetHaptics(on)`.

- [ ] **Step 1: Write the failing tests.** ViewModel: `setting_a_theme_updates_settings`; `amoled_is_disabled_when_the_effective_mode_is_light`; `wallpaper_switch_is_unsupported_below_sdk_31` (injected sdkInt). Screen (Robolectric, mockup `03-themes-and-appearance.html` is the reference): `shows_twelve_theme_cards_with_names_and_the_selected_one_has_a_check_and_the_description_dusk_theme_selected`; `cards_preview_the_dark_colours_in_dark_mode_and_light_in_light_mode`; `mode_group_has_three_segments_in_this_order_system_dark_light`; `wallpaper_switch_hidden_on_sdk_30`; `all_targets_are_48dp`; `grid_has_two_columns_at_font_scale_2_and_three_otherwise`; `rtl_mirrors_the_grid`. Register no new pairs (cards show other themes' colours as swatches; the card label uses `text` on `surface2`).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement** (live apply: the theme changes the moment you tap, through `settingsState`). **Step 4: Run** the gate. **Step 5: Commit** `feat(settings): add the Appearance screen with twelve themes and modes`.

### Task A15: Palette checkpoint (then STOP S1)

**Files:**
- Create: `app/src/debug/kotlin/$PKG/ThemeGalleryActivity.kt` (debug only; registered in `app/src/debug/AndroidManifest.xml`, exported false, launched with `adb shell am start -n io.github.khaledbahaaeldin.emberbyte/.ThemeGalleryActivity --es theme dusk --ez dark true --ez amoled false`), `tools/device/capture_themes.sh`, `docs/design/expressive/palette-checkpoint.md` (committed)
- Test: `app/src/test/kotlin/$PKG/ThemeGalleryTest.kt` (the gallery renders for all 12 ids and both modes without exceptions)

**Interfaces:** The gallery shows, for the requested theme and mode: a Home-like preview (BrandMark, `HeroNumber` "1.38 GB", the plan line, a `WavyBar`, a Forecast tile, a Live tile, a `NetworkBar`, a `CompactNavBar`) built from the real components, plus a role swatch strip and the registry's contrast numbers for that palette.

- [ ] **Step 1: Write the failing test** `ThemeGalleryTest.renders_every_theme_in_both_modes` and `gallery_is_not_in_the_release_manifest` (release APK has no `ThemeGalleryActivity`; `verifyReleaseApk` already inspects classes: extend its forbidden list). **Step 2: Run to verify it fails. Step 3: Implement** the activity and `capture_themes.sh` (loops 12 themes x dark, light, plus AMOLED for every dark, so 36 PNGs, to `$WS/palette-shots/`, restoring nothing else on the device). **Step 4:** run the script on the emulator, open every PNG (at least six per theme family must be described: dark, light, AMOLED of Dusk, Catppuccin, Gruvbox light, Solarized light, Everforest light, Kanagawa light), and write `palette-checkpoint.md`: the generated table from `palettes-report.md`, the three provisional palettes that were not mocked (Dusk light, Ember, Monochrome) and the two derived light themes (Nord, Dracula), each with a screenshot reference path in the workspace. **Step 5: Commit** `feat(theme): add a debug theme gallery and the palette checkpoint`.
- [ ] **STOP S1.** Print the palette-checkpoint stop message of the prompt. Wait for Claude. If the user asks for colour changes they edit `tools/palettes/palettes.json`; you regenerate (`generate_palettes.py`), rerun `PaletteContrastTest`, and recommit as `fix(theme): adjust palettes after the checkpoint`.

### Task A16: M7 close: licences, credits, regression sweep and proof (then STOP S2)

**Files:**
- Create: `licenses/palettes/<theme>-LICENSE.txt` for the nine community palettes (download each upstream licence text: Catppuccin, Gruvbox, Nord, Rosé Pine, Solarized, Dracula, Tokyo Night, Everforest, Kanagawa; record the source URL and the licence name at the top of each file; if a licence is NOT MIT or is not a free software licence, STOP and tell the user, do not substitute silently)
- Modify: `NOTICE` (credit nine palettes and Roboto Flex), `docs/release/dependency-licences.txt` if it lists them, `README.md` (themes section, one paragraph), `docs/accessibility-audit.md` (new rows for the new components with test names and dated device checks; D-items closed here: none), `docs/design/expressive/mockups/README.md` untouched

- [ ] **Step 1: Regression sweep tests.** The existing screens (Home, Apps, App detail, History, Plans, Plan editor, Settings, Onboarding) still open in all 12 themes: add `ThemeSweepTest` (Robolectric) that composes each screen's root composable (with fakes already used by their tests) under 12 themes x dark/light and asserts no crash and that every `Text` node's colour differs from its container (cheap proxy: `PaletteContrastTest` covers the pairs). Fix any screen that used `MaterialTheme.colorScheme` roles in a way the mapping breaks (list each fix).
- [ ] **Step 2: Credits.** Update `NOTICE`; `NoticeCoversDependenciesTest` must stay green; add a test `notice_credits_all_nine_palettes_and_roboto_flex`.
- [ ] **Step 3: Full clean gate**, `python tools/palettes/generate_palettes.py --check`, `python tools/check-cited-tests.py docs/accessibility-audit.md`, `python tools/test-diff.py <base> HEAD --allow docs/redesign-test-changes.md` (the allow file lists the navbar replacement mapping and nothing else).
- [ ] **Step 4: Fresh tester, device proof** on `Violet_API_36`: clean install of the debug build, restore a seeded backup (`tools/seed/make_backup.py --days 61`), walk every existing screen in Dusk dark, Gruvbox light, Catppuccin Mocha with AMOLED, Solarized light; font scale 1.0 and 2.0; RTL (`ar` locale); animations off; open and describe each screenshot (at least 24); the Appearance screen: change theme, mode, wallpaper switch, AMOLED, haptics and confirm each applies at once; upgrade check: install the M6 build (`git archive 467c41b`), create a plan and set dynamic colour, then `adb install -r` this build: the theme migration keeps wallpaper colours on; and a clean install gets Dusk. Release APK smoke: install the signed (throwaway key) release APK, same Appearance checks, `logcat` crash count 0. The tester writes `task-A16-test-report.md`.
- [ ] **Step 5: Reports and commit** `docs: credit the theme palettes and close the foundation milestone`.
- [ ] **STOP S2.** Print the M7 stop message of the prompt.


---

# PART 2: M8 CORE SCREENS

Rebuild Home, Apps, App detail and History on the new components. ViewModels, repositories and data flows do not change; only presentation and the small view-state additions each task names. The mockups `01-home`, `04-apps`, `05-app-detail`, `06-history` are the reference (the approved options are named in the mockups README).

### Task B1: The usage bar row with a value bubble

**Files:**
- Modify: `ui/design/.../tiles/UsageBarRow.kt`, `AppRow.kt`
- Test: `.../tiles/UsageBarRowTest.kt` (extended), `AppRowTest.kt` (extended)

**Interfaces:** Consumes `AppIcon`, `WavyBar`, `NetworkBar` (A9, A11, A12). Produces `UsageBarRow(label: String, bytes: Long, fraction: Float, color: Color, valueText: String, modifier: Modifier = Modifier)` where the value sits in a rounded bubble (`surface2`, `EmberbyteType.value`, ForceLtr) at the end of a flat rounded bar whose filled part is `color`; the bar has `ProgressBarRangeInfo` semantics, the row is one merged node ("Chrome, 1.2 gigabytes, 43 percent of the total"). `AppRow(icon: ImageBitmap?, shape: AppShape, label, bytes, fraction, mobileBytes, wifiBytes, onClick)` shows the icon, name, a `NetworkBar` and the bubble; the whole row is one node with a click action and a 56 dp minimum height.

- [ ] **Step 1: Write the failing tests.** `row_is_one_semantic_node_with_label_value_and_percent`; `value_bubble_never_clips_at_font_scale_2` (bounds of the bubble inside the row); `fraction_zero_shows_a_visible_stub_of_at_least_4dp`; `very_long_app_name_ellipsises_and_keeps_the_value_visible`; `rtl_puts_the_bubble_at_the_start_edge_and_keeps_the_number_ltr`; `row_with_null_icon_shows_the_letter_shape`. Register pairs (`text` on `surface2` bubble, bar `color` on `track` 3.0).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:ui:design` tests and lint. **Step 5: Commit** `feat(tiles): redesign the usage row around the value bubble`.

### Task B2: Home

**Files:**
- Modify: `app/.../home/HomeScreen.kt`, `HomeUiState.kt` (view-state additions only), `HomeViewModel.kt` (only if a derived field is needed)
- Create: `app/.../home/HomeSections.kt` (the composables split out so the screen file stays small)
- Test: `app/src/test/kotlin/$PKG/home/HomeScreenTest.kt` (extended), `HomeLayoutTest.kt`

**Interfaces:** Consumes `HeroNumber`, `UnitBadge`, `BrandMark`, `WavyBar`, `BentoTile` with corners, `NetworkBar`, `NoticeLine`, `CompactNavBar` (hosted by the app scaffold), `PlanUrgency.front` is NOT used yet (C5 wires it; here the front plan is the current first plan). Produces the Home layout of the mockup `01-home` option B (Dusk): top row with `BrandMark`, title and date chip; hero card with the big number (today's total in the user's unit system) and its unit badge, the plan line under it ("12.4 GB left of 20 GB", or "Set up a plan" action when none), a `WavyBar` for the front plan; a two-tile bento row: Forecast (accent container: "On pace to finish at 61%" or the run-out date) and Live (current speed with the burst mark); top apps list with the first five `AppRow`s and a "See all" text button that goes to Apps; the network bar tile for mobile vs Wi-Fi; `NoticeLine` carrying the existing "estimated" and "no permission" notices (the `PermissionPrompt` card stays the first element when usage access is missing).

- [ ] **Step 1: Write the failing tests** (Robolectric, all existing Home tests stay and are adapted only where the text moved; list every adapted one): `hero_number_is_one_node_reading_used_and_left` ("1.38 gigabytes used today, 12.4 gigabytes left"); `no_plan_shows_the_set_up_a_plan_action_and_no_bar`; `permission_missing_shows_the_prompt_first_and_hides_the_hero_figures`; `estimated_data_shows_the_notice_line`; `forecast_tile_shows_the_run_out_date_when_projected_over_the_cap`; `top_apps_shows_five_rows_and_see_all_navigates`; `font_scale_2_shrinks_the_hero_number_without_clipping`; `rtl_mirrors_and_keeps_the_number_with_its_unit`; `reduce_motion_does_not_animate_the_hero_number`; `loading_state_shows_the_loading_indicator_not_a_zero`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate for `:app`, then a quick emulator look (one screenshot, opened and described) of Dusk dark and Dusk light. **Step 5: Commit** `feat(home): make today's number the hero of the Home screen`.

### Task B3: Apps

**Files:**
- Modify: `app/.../apps/AppsScreen.kt`, `AppsViewModel.kt` (adds `featured` and filter state), `AppsUiState.kt`
- Create: `app/.../apps/FiltersSheet.kt`, `FeaturedAppCard.kt`
- Test: `app/src/test/kotlin/$PKG/apps/AppsScreenTest.kt` (extended), `AppsViewModelTest.kt` (extended), `FiltersSheetTest.kt`

**Interfaces:** Consumes `AppRow`, `ConnectedButtonGroup`, `EmberbyteFilterChip` (kept for removable chips), `AppIconProvider`. Produces mockup `04-apps` option A with the user's changes: a featured card for the top app (large icon in its shape, big number, share of total), the list below, a search field, one "Filters" button opening a bottom sheet (`FiltersSheet`: period `ConnectedButtonGroup` Today / 7 days / 30 days / Cycle; network group All / Mobile / Wi-Fi; sort group Usage / Name; a "Show system apps" switch; "Apply" and "Reset") and, under the search field, removable chips for every non-default filter (tap the chip's close to remove that filter). `AppsViewModel` keeps the current filter semantics (same defaults, same persistence if any) and exposes `featured: AppUsage?` (the first of the sorted list when the list is non-empty and no search text is active).

- [ ] **Step 1: Write the failing tests.** ViewModel: `featured_is_the_top_app_and_absent_during_search`; `removing_a_chip_resets_only_that_filter`; `reset_returns_all_defaults`. Screen: `filters_button_opens_the_sheet_and_apply_updates_the_list`; `each_non_default_filter_shows_a_removable_chip_with_a_remove_action_described`; `featured_card_reads_as_one_node`; `empty_search_result_shows_the_empty_state_with_a_clear_action`; `sheet_is_keyboard_safe_and_scrolls_at_font_scale_2`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` gate. **Step 5: Commit** `feat(apps): add the featured app, the filters sheet and removable chips`.

### Task B4: App detail

**Files:**
- Modify: `app/.../apps/AppDetailScreen.kt` (and its state file)
- Test: `app/src/test/kotlin/$PKG/apps/AppDetailScreenTest.kt` (extended)

**Interfaces:** Produces mockup `05-app-detail` option B: a large icon in its shape, the app name, the period total as `HeroNumber`, a mobile vs Wi-Fi `NetworkBar` with both values, the daily chart (existing data; bars use `primary`, today's bar uses `accent`), foreground vs background split as two `BentoTile`s, and the existing "Add to plan" / "Ignore app" actions as a `ConnectedButtonGroup`-style pair of buttons (keep their existing behaviour and dialogs untouched until D3).

- [ ] **Step 1: Write the failing tests:** `total_is_one_node_with_unit`; `daily_chart_has_a_text_alternative_for_each_bar` (existing contract 12.2 kept); `unknown_package_shows_the_letter_icon_and_no_crash`; `foreground_background_tiles_show_both_values`; `font_scale_2_no_clip`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(apps): redesign the app detail screen`.

### Task B5: History

**Files:**
- Modify: `app/.../history/HistoryScreen.kt`
- Create: `app/.../history/HistoryHeader.kt`
- Test: `app/src/test/kotlin/$PKG/history/HistoryScreenTest.kt` (extended)

**Interfaces:** Produces mockup `06-history` option B (number-led): the period selector as a `ConnectedButtonGroup` (Day / Week / Month / Cycle), the period total as `HeroNumber` with the comparison against the previous period ("12 percent more than last week" with an icon, not colour alone), the chart (kept component, palette colours), the per-day list. Semantics unchanged from contract 12.2.

- [ ] **Step 1: Write the failing tests:** `period_group_selects_and_reloads`; `comparison_is_text_with_an_icon_and_never_only_colour`; `empty_history_shows_the_empty_state`; `total_is_one_node`; `font_scale_2_no_clip`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(history): lead with the period total and a connected period selector`.

### Task B6: Shared empty and loading states, and the QA polish items

**Files:**
- Create: `ui/design/.../states/EmptyState.kt`, `LoadingState.kt`; `docs/redesign-polish-closure.md`
- Modify: the screens that show an empty or loading state today (Home, Apps, History, Plans, App detail) to use them; whatever files the polish items below touch
- Test: `.../states/StatesTest.kt` plus one extended test per touched screen

**Interfaces:** Produces `@Composable fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null)` (a cookie-shaped icon container, title in `EmberbyteType.heading`, body in `muted`, one merged semantic node plus the action) and `@Composable fun LoadingState(label: String, modifier: Modifier = Modifier)` (the stock `LoadingIndicator`, described by `label`, a static mark when reduced motion is on).

The polish items. Read the M6 QA list in the repository (`docs/superpowers/plans/` and `docs/accessibility-audit.md`, section "Known polish items", items D5 to D14) and close each item that the redesign touches, or record why it is out of scope in `docs/redesign-polish-closure.md` (one line per item: closed in commit X by test Y, or "not applicable because ..."). If an item is not in those files, ask the user in the STOP S2 report; do not invent.

- [ ] **Step 1: Write the failing tests:** `empty_state_is_one_node_plus_the_action`; `empty_state_wraps_at_font_scale_2`; `loading_state_has_a_description_and_is_static_with_reduced_motion`; plus one failing test per polish item you close. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the full gate. **Step 5: Commit** `feat(states): share empty and loading states and close the polish items`.

### Task B7: M8 proof

**Files:**
- Create: `docs/redesign-m8-proof.md`
- Test: none new (verification task)

- [ ] **Step 1: Full clean gate** and `python tools/test-diff.py <base> HEAD --allow docs/redesign-test-changes.md`; `python tools/check-cited-tests.py docs/accessibility-audit.md`; `python tools/palettes/generate_palettes.py --check`.
- [ ] **Step 2: Fresh tester, device proof** on `Violet_API_36`: clean install, restore the seeded backup, walk Home, Apps (featured card, Filters sheet with each group, removable chips), App detail, History in Dusk dark, Catppuccin light and Gruvbox dark with AMOLED; font scale 2.0; RTL; animations off; open and describe at least 20 screenshots; real icons visible for installed apps and the letter fallback for a `uid:` row (seed one with `make_backup.py --pseudo-uid`); no crash in `logcat`. The tester writes `task-B7-test-report.md`.
- [ ] **Step 3: Reports and commit** `docs: record the core screens proof`.


---

# PART 3: M9 PLANS

Behaviour changes first (engine, database, backup, alerts), then the screens. Contract 13.3 to 13.5 is exact. The mockups `07-plans`, `08-plan-flow`, `09-plan-edit` are the reference.

### Task C1: Engine: first cycle and urgency

**Files:**
- Create: `core/engine/src/main/kotlin/$PKG/engine/model/FirstCycle.kt`, `core/engine/src/main/kotlin/$PKG/engine/plan/PlanUrgency.kt`
- Modify: `engine/model/Plan.kt` (adds `firstCycle`, `pinned` with defaults), `engine/plan/PlanState.kt` and the calculator that builds it (find with `grep -rn "PlanState(" core/engine/src/main`)
- Test: `core/engine/src/test/kotlin/$PKG/engine/plan/FirstCycleTest.kt`, `PlanUrgencyTest.kt`

**Interfaces:** Produces exactly contract 13.3 (`FirstCycle`, the `Plan` and `PlanState` additions, `PlanUrgency.front`, `PlanUrgency.order`). Existing callers compile unchanged because the new parameters have defaults.

- [ ] **Step 1: Write the failing tests** (JUnit, fixed `Clock`, zone `Europe/Cairo` plus one `America/New_York` DST case). `FirstCycleTest`: `phone_record_behaves_exactly_as_before` (golden: an existing plan fixture yields byte-identical state); `entered_adds_to_measured_bytes_after_enteredAt`; `bytes_measured_before_enteredAt_are_ignored`; `entered_above_the_cap_is_over_cap_and_remaining_is_zero_not_negative`; `entered_at_the_first_minute_of_the_cycle_equals_phone_record_plus_entered`; `entered_for_a_previous_cycle_is_ignored_in_the_next_cycle`; `entered_zero_equals_phone_record_after_enteredAt`; `every_n_days_cycle_entered_uses_the_window_start`; `dst_change_inside_the_cycle_keeps_the_window_start_and_the_sum`; `free_windows_apply_only_to_measured_bytes_not_to_the_entered_amount`; `starts_next_cycle_is_dormant_with_zero_used_no_forecast_and_startsAt_set`; `starts_next_cycle_created_on_the_last_day_becomes_active_tomorrow`; `starts_next_cycle_after_startsAt_behaves_as_phone_record_and_is_not_dormant`; `forecast_for_entered_uses_the_elapsed_time_since_cycle_start_not_since_enteredAt_for_the_entered_part`. `PlanUrgencyTest`: `pinned_active_plan_leads`; `pinned_dormant_plan_does_not_lead`; `over_cap_beats_run_out_beats_fraction_beats_id`; `all_dormant_gives_null_front`; `order_puts_front_first_active_next_dormant_last`; `ties_are_stable_by_id`.
- [ ] **Step 2: Run to verify they fail** (`./gradlew :core:engine:test --tests '*FirstCycleTest'`). **Step 3: Implement.** The forecast for an Entered plan projects the daily rate from `(entered + measured)` over the time since `cycleStart` (state this choice in KDoc; it keeps the forecast honest for a plan entered late). **Step 4: Run** `:core:engine:test` (all 125 plus the new ones green). **Step 5: Commit** `feat(engine): support plans created mid-cycle and rank plans by urgency`.

### Task C2: Database version 4

**Files:**
- Modify: `core/data/.../db/Entities.kt` (`PlanEntity`), `Migrations.kt` (`MIGRATION_3_4`), `EmberbyteDatabase.kt` (version 4, migration registered), the plan mapper (entity to `Plan` and back), `core/data/schemas/.../4.json` (exported by the build)
- Test: `core/data/src/test/kotlin/$PKG/data/db/MigrationTest.kt` (extended with 3 to 4), `PlanMapperTest.kt`

**Interfaces:** Consumes `FirstCycle` (C1). Produces contract 13.4 exactly. Mapping: `phone` to `PhoneRecord`; `entered` to `Entered(bytes, Instant.ofEpochMilli(at), Instant.ofEpochMilli(start))`; `later` to `StartsNextCycle(Instant.ofEpochMilli(start))`; a row with an unknown mode string or missing required column for its mode maps to `PhoneRecord` (never crashes).

- [ ] **Step 1: Write the failing tests.** `migration_3_to_4_keeps_every_plan_row_and_defaults_to_phone_and_unpinned` (use the existing `MigrationTestHelper` pattern: create v3 with three plans including an archived one, migrate, assert all fields); `migration_3_to_4_keeps_all_other_tables_row_counts`; `schema_4_validates_after_migration`; `mapper_roundtrips_each_first_cycle_mode`; `unknown_mode_maps_to_phone_record`; `entered_row_without_bytes_maps_to_phone_record`. Existing migrations 1 to 3 tests stay green unchanged.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** `:core:data:testDebugUnitTest`. **Step 5: Commit** `feat(data): store first-cycle mode and pinning in database version 4`.

### Task C3: Backup and export version 2

**Files:**
- Modify: `core/data/.../backup/BackupCodec.kt` and its payload model/serialiser, the JSON export writer, `docs/backup-format.md`
- Test: `core/data/src/test/kotlin/$PKG/data/backup/BackupRoundTripTest.kt` (extended), `BackupCompatibilityTest.kt`, `ExportJsonTest.kt` (extended)

**Interfaces:** Consumes C2 mapping. Produces contract 13.5 exactly. A fixture `core/data/src/test/resources/backup_v1_with_plans.emberbyte` (a real v1 backup produced by `git archive 467c41b` code or by `tools/seed/make_backup.py --contract 1`) is committed.

- [ ] **Step 1: Write the failing tests.** `v2_roundtrips_every_first_cycle_mode_and_pinned`; `v1_fixture_restores_all_plans_as_phone_and_unpinned`; `version_3_is_rejected_with_invalid_file_and_an_update_message`; `writer_writes_contract_version_2`; `corrupt_first_cycle_object_does_not_crash_and_is_reported_invalid` (missing `mode`, negative `usedBytes`, `later` without `startsAt`); `json_export_carries_the_new_plan_fields`; `csv_export_is_byte_identical_to_before` (golden). The wrong-passphrase and tamper tests of M3 stay green.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement and update `docs/backup-format.md`. Step 4: Run** `:core:data:testDebugUnitTest`. **Step 5: Commit** `feat(backup): carry the first-cycle fields in backup and export version 2`.

### Task C4: Dormant plans in alerts, notification and widget data, and pinning

**Files:**
- Modify: the alert evaluator and worker (find with `grep -rn "PlanState" app/src/main core/*/src/main | grep -i alert`), the live notification data builder, `app/.../widget/` data providers, the plans repository (add `suspend fun setPinned(id: Long, pinned: Boolean)`; at most one plan pinned: pinning one unpins the others in one transaction), `PlansViewModel`
- Test: `app/src/test/kotlin/$PKG/plans/PlanAlertDormantTest.kt`, `core/data/src/test/kotlin/$PKG/data/plan/PinnedPlanTest.kt`, `PlanNotificationDormantTest.kt`

**Interfaces:** Consumes `PlanState.isDormant`, `PlanUrgency`. Produces: dormant plans never raise an alert, never appear in the live notification or widgets as a figure (a widget shows "Starts 1 Nov" text from `startsAt`); the live notification and widgets use `PlanUrgency.front`.

- [ ] **Step 1: Write the failing tests:** `dormant_plan_never_alerts_even_when_measured_bytes_exceed_the_cap`; `plan_alert_resumes_on_the_first_evaluation_after_startsAt`; `notification_uses_the_front_plan_not_the_first`; `notification_ignores_a_dormant_front_candidate`; `pinning_one_plan_unpins_the_others_atomically`; `archiving_a_pinned_plan_clears_the_pin`; `entered_plan_over_cap_on_creation_alerts_once` (not repeatedly: existing alert de-duplication keys still apply).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the full gate. **Step 5: Commit** `feat(plans): silence dormant plans and let the user pin the front plan`.

### Task C5: The Plans screen

**Files:**
- Modify: `app/.../plans/PlansScreen.kt`, `PlansViewModel.kt`, `PlansUiState.kt`
- Create: `app/.../plans/PlansBento.kt`
- Test: `app/src/test/kotlin/$PKG/plans/PlansScreenTest.kt` (extended), `PlansViewModelTest.kt` (extended)

**Interfaces:** Consumes `PlanUrgency.order`, `PlanCard`, `PlanTile` (C6; create the signature stub here so the screen compiles: C6 fills it), `EmptyState`, `PlusButton` (A13). Produces mockup `07-plans` (a bento like the Google Wallet reference the user gave): the front plan as a large `PlanCard` spanning the width, the other active plans as 2-column `PlanTile`s, dormant plans last as dimmed tiles reading "Starts 1 Nov"; long-press or the overflow on a card offers Pin / Unpin, Edit, Archive; an "Archived" section collapsed by default; the empty state with a "New plan" action.

- [ ] **Step 1: Write the failing tests:** ViewModel `state_orders_plans_by_urgency_and_dormant_last`; `pin_action_calls_the_repository`. Screen `front_plan_is_the_large_card_and_others_are_tiles`; `dormant_tile_is_dimmed_and_describes_when_it_starts` (not by colour alone); `empty_state_has_the_new_plan_action`; `pin_is_available_through_a_custom_accessibility_action`; `font_scale_2_stacks_tiles_in_one_column`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(plans): show plans as an urgency-ordered bento`.

### Task C6: Plan card and tile

**Files:**
- Modify: `ui/design/.../plan/PlanCard.kt`; Create: `ui/design/.../plan/PlanTile.kt`, `DormantBadge.kt`
- Test: `.../plan/PlanCardTest.kt`, `PlanTileTest.kt`, `PlanCardAccessibilityTest.kt` (the existing ones adapted only where text moved; list each)

**Interfaces:** Consumes `WavyRing`, `UiModels` plan model. Produces `PlanTile(model: PlanCardUi, onClick: () -> Unit, modifier: Modifier = Modifier)` (ring, name, "X left"; one merged node with the same sentence the large card speaks); `PlanCardUi` gains `isDormant: Boolean`, `startsAtText: String?`, `enteredNote: String?` ("Includes 3 GB you entered") and `pinned: Boolean`; a pinned plan shows a pin icon with a description.

- [ ] **Step 1: Write the failing tests:** `tile_is_one_node_with_the_card_sentence`; `dormant_tile_shows_no_ring_and_the_start_text`; `entered_note_is_visible_and_spoken`; `pinned_shows_an_icon_with_a_description`; `tile_min_height_is_96dp_and_clickable_48dp`; `exceeded_plan_shows_the_mark_not_only_colour`; `font_scale_2_no_clip`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** `:ui:design` tests. **Step 5: Commit** `feat(plan): add the plan tile and the dormant and entered states`.

### Task C7: The guided plan flow

**Files:**
- Create: `app/.../plans/flow/PlanFlowScreen.kt`, `PlanFlowViewModel.kt`, `PlanFlowSteps.kt`
- Modify: `nav/Routes.kt` (`PLAN_NEW = "plans/new"`), `EmberbyteApp.kt`, the `PlusButton` target (A13 stub)
- Test: `app/src/test/kotlin/$PKG/plans/flow/PlanFlowViewModelTest.kt`, `PlanFlowScreenTest.kt`

**Interfaces:** Consumes `FirstCycle`, the plans repository `upsert`, `ConnectedButtonGroup`. Produces the five steps of mockup `08-plan-flow` option B: (1) Name and which SIM / network, (2) Cap (amount and unit, with quick chips 5 / 10 / 20 GB), (3) Cycle (monthly day picker, or every N days, with the next reset date shown), (4) **This cycle** (shown only when the cycle started before today): a three-way choice "Use my phone's record" (default), "I've used some already" (amount field; helper text shows the cap and the result "X left"), "Start tracking from the next cycle" (helper: "Starts 1 Nov. No alerts until then"), (5) Review with a summary and "Create plan". Back keeps entered values; the system back leaves the flow after a discard confirmation only if something was typed. State: `PlanFlowState(step, name, subscriptionId, capBytes, capUnit, cycle, firstCycleChoice, enteredBytes)` with `fun canContinue(): Boolean` per step; the entered amount above the cap is allowed but shows the warning "This is already over your cap" (does not block).

- [ ] **Step 1: Write the failing tests.** ViewModel: `continue_is_disabled_until_name_and_cap_are_valid`; `cap_zero_or_negative_is_rejected`; `this_cycle_step_is_skipped_when_the_cycle_starts_today`; `entered_choice_builds_Entered_with_now_and_the_current_cycle_start`; `later_choice_builds_StartsNextCycle_at_the_next_boundary`; `entered_above_cap_sets_the_over_cap_warning_but_can_continue`; `going_back_keeps_every_value`; `create_calls_upsert_once_even_if_tapped_twice` (double-tap guard). Screen: `five_steps_are_announced_as_step_n_of_5`; `focus_moves_to_the_step_title_on_each_step`; `keyboard_does_not_hide_the_continue_button_at_font_scale_2`; `rtl`; `reduce_motion_swaps_steps_without_animation`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(plans): add the guided plan flow with the mid-cycle step`.

### Task C8: The sectioned edit form

**Files:**
- Modify: `app/.../plans/PlanEditorScreen.kt`, `PlanEditorViewModel.kt` (existing); the editor keeps its route for editing
- Test: `app/src/test/kotlin/$PKG/plans/PlanEditorScreenTest.kt` (existing, extended), `PlanEditorViewModelTest.kt` (extended)

**Interfaces:** Produces mockup `09-plan-edit` option A: one scrollable form in sections (Basics, Cap and cycle, This cycle, Alerts, Danger zone: archive and delete). The "This cycle" section shows the current mode and lets the user change it (same three choices and helper texts as C7; changing to `Entered` sets `enteredAt = now`, `cycleStart = current window start`). Existing editor behaviour (validation, delete confirmation, free windows, rollover) stays.

- [ ] **Step 1: Write the failing tests:** `this_cycle_section_shows_the_current_mode`; `switching_to_entered_stamps_now_and_the_current_cycle_start`; `switching_back_to_phone_record_clears_the_entered_values`; `delete_still_requires_confirmation`; `sections_have_headings_for_talkback_navigation`; `font_scale_2_no_clip`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(plans): edit plans in sections and change the first-cycle mode`.

### Task C9: Mid-cycle integration tests

**Files:**
- Create: `app/src/test/kotlin/$PKG/plans/MidCycleIntegrationTest.kt`, `core/data/src/test/kotlin/$PKG/data/plan/MidCycleRepositoryTest.kt`

**Interfaces:** Consumes C1 to C8 only; no production code (if a bug shows, fix it in the owning task's file and say so).

- [ ] **Step 1: Write the tests** (they should pass if C1 to C8 are right; if one fails, that is a finding: fix and report): through the real repository on an in-memory Room database, a fake clock and fake usage rows: `plan_created_on_the_15th_with_entered_3gb_shows_3gb_plus_later_measured_bytes`; `plan_created_on_the_15th_starting_next_cycle_shows_nothing_until_the_1st_then_counts_from_zero`; `entered_plan_across_the_cycle_boundary_resets_to_phone_record`; `entered_over_cap_raises_one_alert_and_the_widget_data_says_over`; `restore_of_a_backup_with_entered_and_later_plans_gives_identical_states`; `pinned_plan_survives_backup_roundtrip_and_leads_the_urgency_order`.
- [ ] **Step 2: Run them; show any that fail first. Step 3: Fix findings. Step 4: Run** the full gate. **Step 5: Commit** `test(plans): pin mid-cycle behaviour from the database to the alert`.

### Task C10: M9 close: upgrade proof (then STOP S3)

**Files:**
- Create: `docs/redesign-m9-proof.md`, `app/src/test/kotlin/$PKG/DebugClockExtraTest.kt`
- Modify: `app/src/main/kotlin/$PKG/MainActivity.kt` (debug-only clock extra), `app/build.gradle.kts` (`verifyReleaseApk` forbidden strings)

- [ ] **Step 1: Full clean gate**, `test-diff`, `check-cited-tests`, palettes `--check`.
- [ ] **Step 2: Fresh tester, device proof** on `Violet_API_36`. (a) Upgrade: `git archive 467c41b` into a temp dir, build that M6 debug APK, install it, create two plans through its UI (one archived), restore the seeded backup; `adb install -r` this branch's debug APK over it; confirm both plans survive as phone-record, nothing crashes (`logcat`), the live notification still works. (b) Clean install, seeded backup: create a plan on the 15th (the emulator clock is not changed; this task adds a debug-only `--el debug_fake_now <epochMillis>` intent extra to `MainActivity`, behind `BuildConfig.DEBUG`, that sets the app's `Clock` for the process; add a failing test first, `DebugClockExtraTest`, and extend `verifyReleaseApk` so the release APK is proven free of it): walk the full flow with each of the three "This cycle" choices, over-cap entry, pin and unpin, archive, edit mode change, backup and restore of the result, and an old `contractVersion` 1 file. Font scale 2.0, RTL, animations off. Describe at least 24 screenshots. The tester writes `task-C10-test-report.md`.
- [ ] **Step 3: Reports and commit** `docs: record the plans proof`.
- [ ] **STOP S3.** Print the M9 stop message of the prompt.


---

# PART 4: M10 ONBOARDING, SETTINGS AND SYSTEM SURFACES

Mockups `02-onboarding`, `10-settings`, `11-dialogs`, `12-notification` are the reference.

### Task D1: Paged onboarding

**Files:**
- Modify: `app/.../onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt` (existing permission and completion logic stays)
- Create: `app/.../onboarding/OnboardingPages.kt`
- Test: `app/src/test/kotlin/$PKG/onboarding/OnboardingScreenTest.kt` (extended)

**Interfaces:** Produces mockup `02-onboarding` option "paged": a `HorizontalPager` of four pages (Welcome with `BrandMark` and the privacy promise; "See your data" with a sample hero number; "Stay under your cap" with a sample `WavyRing`; "Allow usage access" with the existing permission action and an explanation), page dots that are described ("Page 2 of 4"), Skip on pages 1 to 3, a primary button that reads Next, then "Allow access". Permission and completion behaviour is unchanged.

- [ ] **Step 1: Write the failing tests:** `four_pages_with_page_n_of_4_descriptions`; `next_advances_and_the_last_page_shows_allow_access`; `skip_jumps_to_the_permission_page_not_out_of_onboarding`; `swipe_works_and_buttons_work_without_swipe` (TalkBack users use buttons); `back_goes_to_the_previous_page`; `permission_granted_completes_onboarding_exactly_once`; `reduce_motion_changes_pages_without_animation`; `font_scale_2_no_clip`; `rtl_reverses_the_swipe_direction`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** `:app` tests. **Step 5: Commit** `feat(onboarding): replace the single page by a four-page intro`.

### Task D2: Grouped Settings

**Files:**
- Modify: `app/.../settings/SettingsScreen.kt`
- Create: `app/.../settings/SettingsGroups.kt` (`SettingsGroup`, `SettingsRow`, `SettingsSwitchRow`)
- Test: `app/src/test/kotlin/$PKG/settings/SettingsScreenTest.kt` (extended)

**Interfaces:** Produces mockup `10-settings`: grouped rounded containers with headings (Appearance entry, Notifications, Alerts, Data and backup, Units, About), rows with a leading shaped icon, `ExpressiveSwitch` for toggles, each row one node (label, value or state), 56 dp minimum. Existing actions (export, backup, restore, units, alerts) keep their behaviour and dialogs.

- [ ] **Step 1: Write the failing tests:** `every_setting_row_is_one_node_with_label_and_state`; `switch_rows_toggle_on_whole_row_click`; `group_headings_are_headings_for_talkback`; `appearance_row_shows_the_current_theme_name_and_navigates`; `all_existing_actions_are_still_reachable` (assert the export, backup, restore, units, spike alerts, notification toggles exist); `font_scale_2_no_clip`; `rtl`.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** `:app` tests. **Step 5: Commit** `feat(settings): group settings into rounded sections`.

### Task D3: Dialogs

**Files:**
- Create: `ui/design/.../dialog/EmberbyteDialog.kt`
- Modify: every `AlertDialog` / `Dialog` call site (find with `grep -rn "AlertDialog\|Dialog(" app/src/main ui/design/src/main`)
- Test: `.../dialog/EmberbyteDialogTest.kt`, plus one adapted test per migrated dialog

**Interfaces:** Produces `@Composable fun EmberbyteDialog(onDismiss: () -> Unit, title: String, icon: ImageVector?, confirm: DialogAction, dismiss: DialogAction?, modifier: Modifier = Modifier, destructive: Boolean = false, content: @Composable ColumnScope.() -> Unit)` with `data class DialogAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)`: a mixed-corner `surface2` container, a cookie icon, title in `EmberbyteType.heading`, scrollable content with `imePadding`, a destructive confirm in `danger`/`onDanger` with its icon. One migration list in the report: dialog name, file, test.

- [ ] **Step 1: Write the failing tests:** `title_is_a_heading_and_the_dialog_is_a_pane`; `destructive_confirm_has_danger_colours_and_an_icon`; `content_scrolls_and_the_buttons_stay_visible_at_font_scale_2`; `keyboard_open_keeps_the_focused_field_and_the_buttons_visible`; `dismiss_on_back_and_on_outside_tap_calls_onDismiss`; `confirm_disabled_ignores_clicks`; `rtl`. Register contrast pairs for both confirm styles.
- [ ] **Step 2: Run to verify they fail. Step 3: Implement and migrate every dialog. Step 4: Run** the full gate. **Step 5: Commit** `feat(dialog): one expressive dialog for every confirmation and form`.

### Task D4: Live notification colours

**Files:**
- Modify: the live notification builder (find with `grep -rn "ProgressStyle" app/src/main`)
- Test: the existing notification builder tests (extended)

**Interfaces:** Produces mockup `12-notification`: the notification accent colour (`setColor`) follows the palette state colour (Ok `primary`, Warning `warn`, Exceeded `danger`) of the ACTIVE theme resolved from DataStore at build time (light or dark by the current night mode); the segment colours of the `ProgressStyle` use the same three. The 12.1 rules (API guards, `setShortCriticalText`, promoted ongoing) are unchanged. The state is still also conveyed by text, never by colour alone.

- [ ] **Step 1: Write the failing tests:** `accent_follows_the_theme_state_colour_for_ok_warning_exceeded`; `accent_uses_the_light_palette_in_day_mode_and_dark_in_night_mode`; `text_still_names_the_state`; `api_guards_unchanged` (the existing tests stay green). **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(notification): colour the live update from the active theme`.

### Task D5: The licences screen

**Files:**
- Create: `app/.../settings/LicencesScreen.kt`, `LicencesViewModel.kt`, `tools/gen-licences.py`, `app/src/main/assets/licences.json` (generated), route `settings/licences`
- Modify: `SettingsScreen.kt` (an About entry "Open-source licences"), `build.gradle.kts` of `:app` (a `verifyLicencesAsset` task wired into `verifyReleaseApk` that fails when the asset is stale)
- Test: `app/src/test/kotlin/$PKG/settings/LicencesScreenTest.kt`, `LicencesAssetTest.kt`

**Interfaces:** Produces a screen listing every dependency and credit with its licence, from `licences.json` generated by `tools/gen-licences.py` out of `docs/release/dependency-licences.txt` and `NOTICE` (name, version, licence, URL as text; no link opens a browser unless the user taps and a browser exists; the palettes and Roboto Flex appear). Text is `ForceLtr` blocks inside a normal list. The app has no `INTERNET` permission, so URLs are plain text with a copy action.

- [ ] **Step 1: Write the failing tests:** `asset_lists_every_dependency_of_dependency_licences_txt` (compares counts and names); `asset_includes_the_nine_palettes_and_roboto_flex`; `screen_shows_the_list_and_a_search_filters_it`; `each_entry_is_one_node_with_name_and_licence`; `empty_search_shows_the_empty_state`; `font_scale_2_no_clip`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the full gate (`verifyReleaseApk` included). **Step 5: Commit** `feat(about): show open-source licences inside the app`.

### Task D6: Empty and loading polish across the app

**Files:**
- Modify: any screen still showing a bare spinner, a blank area, or old empty text (grep `CircularProgressIndicator`, `"No `)
- Test: one extended test per touched screen; `app/src/test/kotlin/$PKG/StatesEverywhereTest.kt`

**Interfaces:** Consumes `EmptyState`, `LoadingState` (B6).

- [ ] **Step 1: Write the failing tests:** `StatesEverywhereTest.no_screen_uses_a_bare_circular_progress_indicator` (source scan like `NoHardcodedColorsTest`, with an allow-list of zero), and one failing test per screen changed. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(states): use the shared empty and loading states everywhere`.

### Task D7: M10 close (then STOP S4)

**Files:**
- Create: `docs/redesign-m10-proof.md`

- [ ] **Step 1: Full clean gate**, `test-diff`, `check-cited-tests`, palettes `--check`.
- [ ] **Step 2: Fresh tester, device proof:** clean install, the full onboarding (all four pages, Skip, permission grant through `adb shell appops set ... GET_USAGE_STATS allow`), Settings (every group, every row), Appearance, all dialogs (open each one with the keyboard shown where it has a field, at font scale 2.0), the licences screen (search), live notification in each of the three states (screenshot of the notification shade AND of the status chip on an API 36.1 image if the AVD provides it; otherwise CANNOT-VERIFY with the reason), RTL, animations off, three themes (one light, one dark, one AMOLED). At least 24 screenshots described. The tester writes `task-D7-test-report.md`.
- [ ] **Step 3: Reports and commit** `docs: record the onboarding, settings and system surfaces proof`.
- [ ] **STOP S4.** Print the M10 stop message of the prompt.

---

# PART 5: M11 WIDGETS

Four widgets, one shared theme and image layer. Mockups `13-widgets` (and its README) are the reference. Glance cannot use variable font axes, so hero numbers, rings and bars are rendered to small bitmaps (design 13.4). Contract 13.7 is exact.

### Task E1: Widget theme and image renderer

**Files:**
- Create: `app/.../widget/WidgetTheme.kt`, `WidgetImages.kt`, `ImageBudget.kt`
- Test: `app/src/test/kotlin/$PKG/widget/WidgetThemeTest.kt`, `WidgetImagesTest.kt`

**Interfaces:** Produces `suspend fun WidgetTheme.resolve(context: Context): EmberbytePalette` (reads `themeId`, `themeMode`, `amoledBlack`, `useWallpaperColors` from the same DataStore as the app; mode SYSTEM follows the current night mode; wallpaper colours on SDK 31+ build the palette from `dynamicLightColorScheme`/`dynamicDarkColorScheme`), `object WidgetImages { fun heroNumber(text: String, unit: String, palette: EmberbytePalette, widthPx: Int, heightPx: Int): Bitmap; fun ring(fraction: Float, state: BudgetStateUi, palette: EmberbytePalette, sizePx: Int): Bitmap; fun bar(fraction: Float, color: Int, widthPx: Int, heightPx: Int, wavy: Boolean): Bitmap; fun appIcon(icon: Bitmap?, shape: AppShape, letter: String, sizePx: Int, palette: EmberbytePalette): Bitmap; fun chart(values: List<Long>, palette: EmberbytePalette, widthPx: Int, heightPx: Int): Bitmap }` and `class ImageBudget(maxBytes: Int = 1_572_864) { fun add(bitmap: Bitmap): Boolean; val used: Int }` (a widget builder asks `add` for every bitmap; when it returns false the builder uses the cheaper fallback). The hero number is drawn with Roboto Flex at wght 800 / wdth 36 using `Typeface.Builder(...).setFontVariationSettings(...)` (API 26+); when the typeface cannot be built it falls back to bold sans-serif.

- [ ] **Step 1: Write the failing tests (Robolectric, native graphics):** `resolve_follows_theme_id_and_mode`; `system_mode_follows_night_mode`; `amoled_makes_the_background_black_in_dark_only`; `wallpaper_colours_ignored_below_sdk_31`; `hero_number_bitmap_is_not_blank_and_has_the_requested_size`; `ring_zero_percent_draws_an_outline` (pixel check); `bar_fraction_clamps`; `app_icon_with_null_icon_draws_the_letter_shape`; `budget_rejects_a_bitmap_that_would_exceed_the_limit_and_counts_the_used_bytes`; `hero_number_fallback_font_is_used_when_the_variable_typeface_fails` (inject a failing builder).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the `:app` tests. **Step 5: Commit** `feat(widget): add the widget theme and the bitmap renderer`.

### Task E2: Widget building blocks

**Files:**
- Create: `app/.../widget/WidgetBlocks.kt` (Glance composables), `WidgetData.kt`
- Test: `app/src/test/kotlin/$PKG/widget/WidgetBlocksTest.kt`, `WidgetDataTest.kt`

**Interfaces:** Consumes E1, `PlanUrgency.front`. Produces `data class WidgetData(todayBytes: Long, plan: PlanState?, topApps: List<AppUsage>, mobileBytes: Long, wifiBytes: Long, dailyBytes: List<Long>, permissionGranted: Boolean, estimated: Boolean)` and `suspend fun loadWidgetData(graph: AppGraph, now: Instant): WidgetData` (front plan by urgency; dormant plans produce the text "Starts <date>" and no figure); Glance blocks `HeroBlock`, `RingBlock`, `AppRowsBlock(count)`, `NetworkBlock`, `ChartBlock`, `PermissionBlock` (each takes `WidgetData`, palette and `ImageBudget`; every image has a content description or is decorative; the root of a widget has a `clickable` action that opens the app or the matching deep link).

- [ ] **Step 1: Write the failing tests:** `data_uses_the_front_plan_by_urgency`; `dormant_front_candidate_is_skipped_and_text_is_shown_for_a_dormant_only_set`; `permission_missing_gives_the_permission_block_and_no_figures`; `estimated_data_sets_the_flag`; `blocks_have_descriptions_for_every_image` (collect the Glance tree with the test harness used by the existing Hero widget tests).
- [ ] **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** `:app` tests. **Step 5: Commit** `feat(widget): add shared widget data and building blocks`.

### Task E3: The Today widget (and the kept Hero receiver)

**Files:**
- Create: `app/.../widget/TodayWidget.kt`, `TodayWidgetReceiver.kt`, `app/src/main/res/xml/today_widget_info.xml`, `res/layout/widget_preview_today.xml` or a `previewImage` drawable
- Modify: `AndroidManifest.xml` (receiver), `HeroWidgetReceiver.kt` (renders the Today layout, class and manifest entry kept), `strings.xml` (label "Today")
- Test: `app/src/test/kotlin/$PKG/widget/TodayWidgetTest.kt`, `HeroWidgetCompatibilityTest.kt`

**Interfaces:** Produces the 2x2 Today widget: hero number bitmap, unit, the front plan line or "Set up a plan"; responsive sizes (`SizeMode.Responsive` with two layouts: compact 2x1 shows only the number); resize range 2x1 to 4x2; tap opens Home. `HeroWidgetReceiver` still resolves to the same provider component name so an already placed widget keeps working.

- [ ] **Step 1: Write the failing tests:** `renders_the_number_the_unit_and_the_plan_line`; `compact_size_shows_only_the_number`; `permission_missing_shows_the_permission_block`; `image_budget_is_respected` (sum of bitmaps below 1.5 MiB at 4x2); `hero_receiver_is_still_declared_and_renders_the_today_layout` (manifest scan plus render); `provider_info_declares_the_resize_range_and_a_preview`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(widget): add the Today widget and keep the Hero widget working`.

### Task E4: The Plan widget

**Files:**
- Create: `app/.../widget/PlanWidget.kt`, `PlanWidgetReceiver.kt`, `res/xml/plan_widget_info.xml`
- Modify: `AndroidManifest.xml`, `strings.xml`
- Test: `app/src/test/kotlin/$PKG/widget/PlanWidgetTest.kt`

**Interfaces:** Produces the 2x2 Plan widget: the wavy ring bitmap with the percentage, the plan name, "X left", the state mark for warning and exceeded; a dormant front plan shows its name and "Starts 1 Nov"; tap opens the Plans screen (deep link `emberbyte://plans`, verify the existing deep-link table in the contract and use the matching entry; if none exists, STOP and tell the user, do not add a new deep link silently).

- [ ] **Step 1: Write the failing tests:** `shows_ring_name_and_left_text`; `exceeded_shows_the_mark_and_the_text_over_the_cap`; `no_plan_shows_set_up_a_plan_and_opens_the_plan_flow`; `dormant_plan_shows_the_start_text_and_no_ring`; `entered_plan_note_is_not_shown_in_the_widget` (kept compact); `image_budget_is_respected`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(widget): add the Plan widget`.

### Task E5: The Bento widget

**Files:**
- Create: `app/.../widget/BentoWidget.kt`, `BentoWidgetReceiver.kt`, `res/xml/bento_widget_info.xml`
- Modify: `AndroidManifest.xml`, `strings.xml`
- Test: `app/src/test/kotlin/$PKG/widget/BentoWidgetTest.kt`

**Interfaces:** Produces the 4x2 Bento widget: left, the Today hero tile; right, two stacked tiles (front plan ring, network split bar); mixed-corner tile backgrounds drawn as bitmaps or Glance `cornerRadius`; the compact layout (width under 3 cells) drops the right column to one tile.

- [ ] **Step 1: Write the failing tests:** `shows_three_tiles_at_4x2`; `compact_width_shows_hero_and_one_tile`; `network_bar_has_a_description_with_both_values`; `permission_missing_shows_the_permission_block`; `image_budget_is_respected`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(widget): add the Bento widget`.

### Task E6: The Dashboard widget

**Files:**
- Create: `app/.../widget/DashboardWidget.kt`, `DashboardWidgetReceiver.kt`, `res/xml/dashboard_widget_info.xml`
- Modify: `AndroidManifest.xml`, `strings.xml`
- Test: `app/src/test/kotlin/$PKG/widget/DashboardWidgetTest.kt`

**Interfaces:** Produces the 4x4 Dashboard widget: header (brand mark, date), the hero number, the front plan bar, a 7-day `ChartBlock`, the top three apps with real icons in shapes (`AppIconProvider` bitmaps, letter fallback) and values; row taps open that app's detail (existing deep link for app detail; if none exists STOP and tell the user); narrower layouts drop the chart then the apps.

- [ ] **Step 1: Write the failing tests:** `shows_header_hero_plan_chart_and_three_apps_at_4x4`; `smaller_size_drops_chart_then_apps`; `app_without_an_icon_shows_the_letter_shape`; `uid_pseudo_app_never_requests_an_icon`; `each_app_row_has_a_deep_link_action`; `image_budget_is_respected_with_three_icons`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the gate. **Step 5: Commit** `feat(widget): add the Dashboard widget`.

### Task E7: Previews, refresh and the budget guard

**Files:**
- Modify: the widget refresh worker and scheduling (find with `grep -rn "GlanceAppWidget\|updateAll" app/src/main`), the four provider infos (`previewLayout`/`previewImage`), `strings.xml` (descriptions)
- Create: `app/src/test/kotlin/$PKG/widget/WidgetRefreshTest.kt`, `AllWidgetsBudgetTest.kt`

**Interfaces:** Produces: one refresh path updates all four widgets and the Hero receiver when data, plan, theme or the usage permission changes; a theme change in Appearance refreshes the widgets within the same action; no widget updates more often than the existing minimum interval.

- [ ] **Step 1: Write the failing tests:** `theme_change_triggers_a_refresh_of_all_widget_types`; `plan_change_triggers_a_refresh`; `refresh_is_rate_limited_to_the_existing_minimum_interval`; `every_provider_info_has_a_description_and_a_preview`; `AllWidgetsBudgetTest.each_widget_at_its_largest_size_stays_under_1_5_MiB_of_bitmaps`. **Step 2: Run to verify they fail. Step 3: Implement. Step 4: Run** the full gate. **Step 5: Commit** `feat(widget): refresh every widget on data, plan and theme changes`.

### Task E8: M11 proof

**Files:**
- Create: `app/src/debug/kotlin/$PKG/WidgetPreviewActivity.kt` (debug only; renders each widget at a chosen size into a `RemoteViews` host so the tester can screenshot every size and theme without a launcher), `tools/device/capture_widgets.sh`, `docs/redesign-m11-proof.md`

- [ ] **Step 1: Add a test** `WidgetPreviewActivity` is absent from the release manifest (extend `verifyReleaseApk`'s forbidden list), run it failing first.
- [ ] **Step 2: Fresh tester, device proof:** place each of the four widgets on the emulator launcher (AVD Pixel launcher; use `adb shell am start` of the widget picker only if needed; the harness activity is the fallback and the report must say which was used), in Dusk dark, Dusk light, Catppuccin light and Gruvbox with AMOLED; resize each between its smallest and largest size; a placed old Hero widget from the M6 build survives `adb install -r`; permission missing state; a dormant front plan; an over-cap plan; font scale 2.0; RTL. At least 24 screenshots described; `logcat` crash count 0. The tester writes `task-E8-test-report.md`.
- [ ] **Step 3: Reports and commit** `docs: record the widgets proof`.

---

# PART 6: FINISH

### Task Z1: Final documentation

**Files:**
- Modify: `README.md` (themes, widgets, plans mid-cycle, screenshots section), `CHANGELOG.md` (one `1.0.0` entry listing the redesign), `docs/store/` listing text and the screenshot list (names only; the images come from Z2), `docs/accessibility-audit.md` (rows for every new component with test names), `docs/backup-format.md` (checked, already updated in C3)
- Create: `docs/redesign-summary.md` (what changed, how to add a theme: edit `tools/palettes/palettes.json`, run the generator, run `PaletteContrastTest`)
- Test: `python tools/check-cited-tests.py docs/accessibility-audit.md` exits 0

- [ ] **Step 1: Write the docs. Step 2: Run** the citation checker and the markdown links check (`python tools/check-links.py` if it exists, else `grep` every relative link). **Step 3: Commit** `docs: describe the expressive redesign for users and contributors`.

### Task Z2: Final proof (then STOP S5)

**Files:**
- Create: `docs/redesign-final-proof.md`, store screenshots under `docs/store/screenshots/` (real captures from the emulator, Dusk dark and light, phone only)

- [ ] **Step 1: Clean checkout of the branch HEAD, full gate, release APK reproducibility check twice** (the M6 `tools/verify-reproducible.sh` if present; compare the two APK checksums), palettes `--check`, `test-diff` against `467c41b` (counts grow only), `check-cited-tests`.
- [ ] **Step 2: Fresh tester, end-to-end device pass** on `Violet_API_36` with the release APK (throwaway key): the whole app in three themes, the upgrade from the M6 build with data, mid-cycle plan creation in all three modes, backup and restore round trip, all four widgets, notification states, font scale 2.0, RTL, animations off; at least 30 screenshots described; crash count 0; the tester writes `task-Z2-test-report.md`.
- [ ] **Step 3: Capture the store screenshots, commit** `docs: add the final proof and the store screenshots`.
- [ ] **STOP S5.** Print the final stop message of the prompt. Nothing is tagged, merged or released by this plan; Claude audits, then the user decides.

