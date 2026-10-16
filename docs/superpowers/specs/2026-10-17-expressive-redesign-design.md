# Emberbyte expressive redesign: design

Status: draft for the user's approval. Written 2026-10-17 from a design session with the user. Every screen below was mocked up and approved; the mockups are in `docs/design/expressive/mockups/` (open in a browser; `README.md` there names the approved option on each page). Where this document and a mockup disagree, this document wins. The API contract (`2026-10-01-datakb-api-contract.md`) still wins on names and storage, and section 12.2 (accessibility) still applies to everything here.

## 1. Intent and why

Emberbyte 1.0 uses Material 3 Expressive's components but not its spirit: default Roboto everywhere, every card the same rounded rectangle at the same weight, flat surfaces, letters instead of app icons, bland widgets. Google's own research (46 studies, 18,000+ people) found that size, placement, shape, colour contrast and grouping make key things easier to find, and make an app feel emotional rather than generic. The goal is an app that feels alive and recognisable, still reads at a glance, and keeps every familiar pattern (lists, labels, three tabs).

The one idea behind every decision: **the number is the hero**. Usage and what is left are what people open the app for, so they get the biggest, boldest type, and everything else supports them.

## 2. Goals, non-goals, anti-goals

Goals
- A recognisable visual identity: tall condensed numbers, a small family of organic shapes, bold colour per state.
- 12 selectable themes, each with a light and a dark mode, plus an optional wallpaper-colour mode.
- Real app icons in the Apps list, App detail, Home top apps and the widgets.
- A widget family of four.
- Fix the logical gap in plans that start mid-cycle.

Non-goals
- No new data features beyond the mid-cycle plan option and the urgent-plan front card with a pin.
- No change to measuring, forecasting maths, backup format or export formats (except what section 7 states).
- No VPN, no network permission, no analytics.
- No localisation work (English only, as now).

Anti-goals (would make this a failure even if it ships)
- Shapes or colour that hide information: a state must never rely on colour alone, text must stay readable at font scale 2.0, and contrast rules (section 9) must hold in all 24 theme and mode combinations.
- A louder app that is slower to use: familiar patterns stay (ranked list, labelled tabs, a chart with a value).
- A theme that looks unlike its namesake, or an unlicensed palette.

## 3. Constraints

- Android minSdk 29, targetSdk 36, Compose BOM 2026.09.00, Material 3 `1.5.0-alpha29` (already contains `MaterialShapes` with `cookie9Sided`, `sunny`, `verySunny`, `burst`, `softBurst`, `clover4Leaf`, `pill`, wavy progress indicators, `ButtonGroup`, `ToggleButton`, `FloatingToolbar`, and the `androidx.graphics:graphics-shapes` library). Opt in with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` as the theme already does.
- Prefer no new runtime dependency. Roboto Flex is already bundled (OFL); `graphics-shapes` arrives transitively.
- F-Droid rules: everything free software, no `INTERNET` permission, reproducible unsigned release build (unchanged from M6).
- Privacy unchanged: nothing leaves the device.
- Each theme palette is a third-party community palette (Catppuccin, Gruvbox, Nord, Rosé Pine, Solarized, Dracula, Tokyo Night, Everforest, Kanagawa; all believed MIT-licensed). The builder must verify each licence text and credit all of them in `NOTICE` and on the licences screen.
- Accessibility contract 12.2 applies to every screen, plus section 9 below.
- Reduced motion: every animation in this document snaps when animator duration scale is 0 (`LocalReduceMotion`).

## 4. Visual language

Typography (Roboto Flex, variable axes `wght`, `wdth`, `opsz`)
| Role | Axes | Used for |
|---|---|---|
| Hero number | wght 800, wdth 36, opsz 144, tight tracking | the big usage numbers (Home, App detail, History, Plans front card, widgets) |
| Unit badge | wght 850, wdth 110 | "GB" inside a cookie badge |
| Screen title | wght 800, wdth 36 to 40 | "Apps", "History", "Settings" |
| Title and row name | wght 700 to 800, wdth 104 to 110 | cards, rows, dialog titles |
| Body | default Roboto Flex | all other text |
Widgets cannot use variable axes (section 8): hero numbers there are drawn into images.

Shapes (all from `MaterialShapes`, rendered through `toShape()` or drawn as paths)
- Cookie (`cookie9Sided`): unit badges, app icons, section icons, theme swatches.
- Sunny / very sunny, burst / soft burst: warning marks (burst with "!"), accents, some app icons.
- Squircle and pill: some app icons, buttons, groups.
- Mixed-corner containers: a large radius (30 to 38 dp) on three corners and a small one (10 to 12 dp) on the fourth, rotating which corner, so no two neighbouring tiles look identical.
- Connected button groups: pill ends, 8 dp inner joins; the selected segment is a full pill with a check mark.

Colour roles (every theme defines them)
background; surface; surface2 (raised); text; muted text; primary (hero numbers, selected states) with onPrimary; accent (mint in Dusk; the "fine / forecast safe" colour) with onAccent; warn (amber) with onWarn; danger (coral) with onDanger; track (inactive ring and bar). State colours: fine = primary or accent, nearly out = warn plus a burst "!", over cap = danger plus a burst "!", dormant = surface with a dashed outline. A state is never colour only.

Motion
- Material 3 Expressive spring scheme (already set in the theme).
- The hero number morphs (existing `MorphingNumber`); a wavy ring/bar fills with a spring; shapes may morph on state change (cookie to burst when a plan becomes nearly out) using `Morph` from graphics-shapes; the navbar pill stretches elastically while dragging, with a light haptic tick on snap.
- Everything snaps with reduced motion.

App icons
- Show each app's real launcher icon through `PackageManager.getApplicationIcon` (the app already holds the permission to list packages), clipped into a cookie, squircle, sunny or burst shape chosen deterministically from the package name so an app keeps its shape. Cache small bitmaps; fall back to the letter on a coloured shape (current behaviour) for `uid:` pseudo-entries and when the icon cannot be loaded. Never log package names (existing rule).

## 5. Theme system

Twelve presets, each with a light and a dark version: Dusk (default), Ember, Monochrome, Catppuccin (Latte and Mocha), Gruvbox (light and dark), Nord, Rosé Pine (Dawn), Solarized, Dracula (light version derived), Tokyo Night (Day), Everforest, Kanagawa (Lotus). Nord and Dracula have no strongly canonical light theme: their light variants are derived by the builder from the dark palette's lightest shades and must be shown to the user before release.

Settings (new Appearance screen, `docs/design/expressive/mockups/03-themes-and-appearance.html`)
- Mode: System, Dark or Light (new).
- Theme: a three-column grid of 12 cards (each in its dark colours when the mode is dark, in its light colours when light; the user's mode decides what the cards preview).
- Wallpaper colours: a switch, Android 12 and newer, replacing the theme with the system's dynamic colours.
- Pure black (AMOLED): a switch, dark mode only.
- Haptics: moved here from the old Appearance group.
Default: Dusk, System mode. Existing installs: the old `useDynamicColor` default of true is replaced; if the key was never set, the user gets Dusk. If the user had explicitly stored it as true, keep wallpaper colours on (their choice).

Implementation notes
- A script (kept in `tools/`) generates one Kotlin file of `ColorScheme` constants per preset and mode from the palette definitions, so palettes are reviewable and no runtime library is needed. The mapping from each community palette to the roles above is the builder's call, constrained by section 9.
- Reference values for Dusk dark (normative): background `#110F19`, surface `#1D1929`, surface2 `#2A2440`, primary `#D0BCFF` on `#2A1858`, accent `#C9F0D3` on `#0D3A1F`, warn `#FFD98A` on `#3B2A00`, danger `#FFB4AB` on `#4D0B0B`, track `#4A4360`. Dusk light, Ember and Monochrome (both modes) were not mocked: design them to the same rules and show the user.
- Persist: `theme` (preset id), `themeMode` (system, light, dark), `useWallpaperColors`, existing `amoledBlack`. DataStore only; no Room change.

## 6. Screens (all approved)

Home (`01-home.html`, direction B "Dusk Expressive"): header with the brand mark (burst with the flame) and "Today"; the hero number in tall condensed type with a cookie unit badge; "12.4 GB left of 20 GB, renews in 18 days" and a wavy plan-progress line; two tiles (Live speed with one sharp corner; Forecast in the accent colour with a different sharp corner); a segmented Mobile/Wi-Fi bar; top apps with real icons in shapes; the "not measured" notice demoted to a slim line. Without a plan the left line reads "Set up a plan" as today.

Onboarding (`02-onboarding.html`, option 2, paged story): welcome page with a large cookie, burst and sunny illustration and the headline "Your data stays on your phone."; then one page per permission (usage access, notifications, plan), each with a large shape illustration, a tall headline, one sentence, "Step n of 3" and a pill-morphing page indicator (the active dot stretches into a pill), a Skip or "Not now" on every page and one big pill button. Permissions, order and the skip behaviour stay as they are.

Apps (`04-apps.html`, `04b-apps-controls.html`): tall "Apps" title with the total; the period as a connected pill group (Today, 7 days, This month, check on the selected); a pill search field with one "Filters" button carrying a count badge (network All, Mobile, Wi-Fi, and sort live behind it); active filters as removable chips; one large featured card for the biggest app (icon, name, share, huge number, thick Mobile/Wi-Fi bar); then the ranked list with shaped real icons, a thicker split bar sized to the share, and the total in heavier type.

App detail (`05-app-detail.html`, A with B's bubble): large shaped icon and tall name; the hero number with a cookie badge; thick Mobile/Wi-Fi bar; a spike card directly under the number when a spike exists (coral, burst "!", Dismiss pill); the budget card with a wavy ring and Edit and Remove as a connected pair; the 7-day chart with a speech-bubble value on the selected bar (fixes QA D7); an App settings pill.

History (`06-history.html`, B number-led): one huge number that follows the selection (a day, week or month, or the total when none is selected), previous and next arrows beside the date, a "Show total" chip, a compact chart strip with a hint line, and the Total and "Average per active day" tiles below.

Plans (`07-plans.html`): a Google-Wallet-style bento grid. One large front card (full story: ring, what is left, days, forecast, a note when the first cycle was entered by hand) and compact two-column tiles (small wavy ring, name, one number, one line; a tile may be wide). State colours as in section 4. Archived plans collapse into a dashed tile. The front card is the most urgent plan (nearest to running out); the user can pin another plan to override. Tapping a tile opens the plan editor as a full-screen sheet in that tile's colour. The round "+" button sits beside the navbar on this screen (section 9.1).

Plan editor (`08-plan-editor.html`): creating a plan is a guided flow, one question per screen with a wavy progress bar and Back/Next (name and SIM, cap and cycle, "this cycle" when it applies, extras, alerts, review). Editing an existing plan is a sectioned form: a pinned live preview, section cards with shaped icons (name, SIM, cap with a big number and MB/GB/TB group, cycle, rollover, add-on packs, free windows, alert thresholds, "this cycle" when active), "What if I use more?" as a mint card with a slider, a floating Save pill and a quiet Archive link. Two UIs to build; one form model.

Settings (`10-settings.html`): grouped cards with shaped icons; Notifications (Live notification, Show speed, Spike alerts) as Material 3 Expressive switches with a check in the thumb; Appearance as a hero entry showing swatches and "12 themes"; Units as a connected group; Data as a 2x2 tile grid (Export CSV, Export JSON, Create backup, Restore in coral); a mint Background card with one Battery settings button; About with the brand mark, version, privacy line, and Source code and Open-source licences buttons.

Dialogs, live notification, licences (`12-dialogs-notification-licences.html`): dialogs get a shaped icon, a tall title and pill buttons, danger actions coral, choices in connected groups, all keyboard-safe at font scale 2.0 (the M6 fix stays). The live notification keeps the Android 16 live update (progress bar plus status-bar chip) and recolours the bar by state (lilac, amber, coral); older Android keeps the ordinary ongoing notification. The licences screen is a short grouped list ("Built with", "Fonts and designs") with a licence pill on each entry, our own licence first in a lilac card, full text always left-to-right, generated from `docs/release/dependency-licences.txt` and `NOTICE`, and it credits the nine theme palettes.

### 6.1 Floating navbar (`13-navbar.html`, option 2)
Three equal slots (68 dp each, about 224 dp total) in a centred, solid pill; each slot shows an icon over its label; the selected slot has a lilac pill behind the icon. The bar's total width never changes; nothing moves between tabs, only colour. While dragging, a lilac pill stretches elastically between two slots and snaps to the nearest with a haptic tick (no stretch with reduced motion). It slides away on scroll down and returns on scroll up. Touch targets at least 48 dp tall. On Plans a round lilac "+" (56 dp) sits beside the bar.

## 7. Plans: behaviour changes

7.1 Mid-cycle plans. When a plan is created and today is not the first day of its cycle, ask how to count the first cycle, with these choices (the default is the first):
1. Use what the phone recorded since the cycle start (today's behaviour, keeps the "Estimated" label).
2. I'll enter what I've used: the user types an amount (value and unit) taken from the carrier's app; the plan's used amount is that figure plus what the phone measures after the entry time, until the cycle ends.
3. Start next cycle: the plan is dormant until the next cycle start. It counts nothing, shows "Starts 1 Nov" in a muted dashed tile, never turns amber or coral, and raises no alerts (plan alerts, budget alerts, and any notification text for that plan). The live notification ignores it.
The edit form shows a "This cycle" section while a first cycle is in progress, to switch the choice or correct the entered amount ("Correct it to match my carrier"). The choice applies to the first cycle only; the next cycle starts from zero by itself. The forecast of a plan with an entered amount has confidence Low until about seven days of measured data exist after the entry.

7.2 Data model. The plan needs: a first-cycle mode (phone's record, entered, starts later), the entered amount in bytes, the time of entry, and (for starts later) nothing else (the start is the next cycle boundary). Room database version 4 with a tested migration and an exported schema; `1.json` to `3.json` unchanged. Backup and CSV/JSON export formats gain the new fields (a version bump of the backup format with a reader that still accepts version 1).

7.3 Engine. `PlanState` computation, forecast, alert evaluation and the widgets must all treat the three modes; a dormant plan returns a state the UI shows as dormant and that the alert evaluator skips. Add unit tests for each mode across a cycle boundary, for entering an amount larger than the cap, for entering at the cycle start, and for editing the amount later.

7.4 Front card and pin. The front card is chosen by urgency (the plan whose projected run-out is soonest; a plan already over cap beats one nearly out; dormant plans never lead). A pin stored per plan overrides the choice; only one plan is pinned at a time.

## 8. Widgets (`11-widgets.html`, all four approved)

Today 2x2 (tall number, cookie unit badge, "what is left", brand mark); Plan 2x2 (wavy ring and what is left, state colours, dormant dashed "Starts 1 Nov"); Bento 4x2 (number and plan line left, forecast tile and the top three apps with real icons right); Dashboard 4x4 (number, plan ring, 7-day mini chart, top four apps, forecast and live speed). Missing-data states: "Allow usage access", "Set up a plan", and "No connection" with the time of the last update. Each widget switches layout at defined sizes (for example Bento at 4x1 becomes a strip).
Build notes: Glance cannot use variable-font axes, so hero numbers are drawn into small images with Roboto Flex (with a spoken description); shapes are vector drawables behind the content; the wavy ring and chart bars are drawn images; the widgets read the chosen theme and mode (wallpaper-colours mode uses the system's colours); app icons are small cached bitmaps within the RemoteViews memory limit; each part opens its screen through the existing deep links (number to Home, ring to Plans, an app icon to its detail); widgets refresh when the app measures (about once a minute while it runs) and show the last update time when offline; each widget has a generated picker preview on Android 15 and newer and a static one before.

## 9. Quality gates

9.1 Contrast: every foreground and background pair used for text (4.5:1) and for graphics and state indicators (3:1), in all 12 themes in both modes, with and without AMOLED, checked by a unit test that builds each scheme and the components' real colours (the pairs include hero number on background, tile text on each tile colour, ring arc against track and card, selected against unselected states, and warn and danger marks on their containers). A theme that cannot meet the rules is adjusted, not exempted.
9.2 Accessibility: contract 12.2 (names, 48 dp targets, chart summary and per-bar nodes, font scale 2.0 reflow with the hero number allowed to shrink inside its container but never clip, reduced motion, right-to-left, focus order). New: shaped app icons are decorative (hidden from semantics, the row name carries the label); the wavy ring exposes the percentage as text; theme swatches have names.
9.3 Visual proof on the emulator for each screen in at least: Dusk dark, one light theme, font scale 1.0 and 2.0, right-to-left, reduced motion; screenshots described in the report.
9.4 Release checks unchanged: no `INTERNET`, reproducible unsigned release APK, `NOTICE` complete (now including the nine palettes and Roboto Flex).

## 10. What is left to the builder (and my calls)

My calls (the user may overrule): Roboto Flex everywhere; cookie, squircle, sunny and burst as the shape set; Dusk as the default; "System, Light, Dark" as a new setting; generating colour schemes by script into Kotlin constants; deterministic shape per package; the existing-install theme rule in section 5; confidence Low after a manual entry; the urgency rule for the front card.
Left to the builder: exact dp sizes and tracking values (the mockups are a reference, not a measurement); the colour mapping of each community palette (within section 9); Dusk light, Ember and Monochrome palettes; shape-morph timings; widget layout breakpoints; the guided plan flow's copy; test strategy per component.

## 11. Suggested milestones (my call; the user decides the order)

1. M7 Foundation: theme system and Appearance screen (12 themes), typography roles, shape library, expressive components (connected groups, switches, wavy ring and progress, bento tile, dialog style, compact navbar), app icon provider; migrate existing screens to the new components behind the same layouts.
2. M8 Core screens: Home, Apps, App detail, History.
3. M9 Plans: bento Plans, plan editor (create flow and sectioned edit), mid-cycle plans with the DB migration and engine changes, urgency and pin.
4. M10 Onboarding, Settings, dialogs, live notification, licences.
5. M11 Widgets (four), including the image-rendering helper and previews.
Each milestone ends with the usual audit and device QA. One release train: nothing is released until all five are done (section 12).

## 12. Answers from the user (2026-10-17)

- **One release train.** M7 to M11 land together and ship as one release; each milestone is still audited and device-tested and may be merged to `main` as it passes, but nothing is released (no version tag) until the redesign is complete, and `v1.0.0` waits for it. So the builder migrates screens in place and does not need to keep old and new components side by side beyond a milestone.
- **The condensed look in widgets is a preference.** The bold system-font fallback described in 13.4 is accepted if the image approach costs too much.
- **Palette checkpoint before M7 closes.** All 12 themes in both modes are shown to the user (a preview page with the real contrast numbers) and approved before M7 is merged; until then the palettes are provisional. The first preview is `docs/design/expressive/mockups/14-palettes.html`.

## 13. Decisions added after the builder check (my calls; the user may overrule)

13.1 Entered amount in a mid-cycle plan. Used bytes = entered amount + bytes measured after the entry time. It counts against the plan's effective cap (cap plus rollover plus add-on packs) exactly like measured usage. Free windows still apply to bytes measured after the entry (the entered figure is the carrier's own count, already net). Editing the amount later replaces the old figure: the entry time becomes the moment of the edit, so the carrier's new figure covers everything before it and nothing is double counted or dropped. An amount above the cap makes the plan over cap immediately. Per-app budgets use measured bytes only (they are per app and the user cannot enter per-app figures). The forecast uses the combined used amount with measured daily use since the entry (confidence Low, section 7.1). Once the cycle rolls over, the first-cycle mode is cleared and the "This cycle" section disappears, so an entry can never refer to a past cycle; editing after the boundary is not offered.

13.2 Dormant plans. Measuring never stops: usage records exist for the dormant period, so History and Apps still show them. A dormant plan counts nothing toward any plan figure, raises no alerts, and does not claim SIM data from other plans on the same SIM. If every plan is dormant, Home's "left" line reads "Plan starts 1 Nov" (the earliest start), the live notification shows no plan figure and no progress bar, and the Today and Plan widgets show the dormant state. Otherwise the first active plan is used wherever the app shows one plan (live notification, Home hero, widgets), chosen by the same urgency rule as the front card.

13.3 Components. Prefer stock Material 3 Expressive components where they are close (wavy progress, button groups, toggle buttons, switches); build custom only for what the mockups need and stock cannot give: the elastic navbar pill (the navbar is already custom), the mixed-corner containers (plain `RoundedCornerShape` with different corners), shape morphs on state change (`Morph`), and the app icon shaping. The builder verifies each stock API on the pinned alpha before relying on it and records any that needs a custom replacement. No hard frame-time budget, but the Home screen must not regress against the M6 build on the emulator and on a mid-range phone; the builder reports measured jank.

13.4 Widgets. Glance runs in the app's own process, so the widgets read the chosen theme and mode from the same DataStore as the app. If drawing the hero number as an image exceeds RemoteViews limits or costs noticeable battery, fall back to a bold system font with the same layout (the condensed look is a preference, not a requirement). If the memory limit is hit, drop in this order: app icons, chart, then shrink the number image.

13.5 Contrast versus fidelity. Contrast wins. Each community palette supplies its own lighter and darker shades, so use those for text and marks to meet 4.5:1 and 3:1 and keep the namesake feel through backgrounds and hues. The contrast test builds the schemes and reads the colours from the components' own colour functions (a central registry of pairs that each component contributes to), not from a hand-kept list, and fails when a component draws a pair that is not registered.

13.6 Existing installs. On first launch after the update the app checks whether the old `useDynamicColor` key exists in DataStore (key absent means never changed): absent gives Dusk, present and true keeps wallpaper colours on, present and false gives Dusk. A one-time migration is acceptable.

13.7 Pin. The pin is a flag on the plan row (Room v4), included in the encrypted backup and the JSON export. Archiving or deleting the plan clears it. A dormant plan can carry a pin but it only takes effect once the plan is active (dormant plans never lead).

13.8 Backup and export formats. New plan fields (first-cycle mode, entered amount, entry time, pin) are added to the encrypted backup and the JSON export, with the backup format version bumped; the new app opens every older backup, an older app opens a newer backup only to say it needs an update. CSV is unchanged unless it currently contains plan rows, in which case the new columns are appended at the end.

13.9 Palette licences. If a palette's licence turns out to be incompatible with F-Droid, the builder replaces that theme with the closest free alternative and tells the user; the count of 12 holds.
