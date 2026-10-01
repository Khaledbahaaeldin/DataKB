# Emberbyte Design (master)

Status: draft for review. Date: 2026-10-01.

Emberbyte is an open-source Android data-usage monitor with a Material 3 Expressive interface: daily and
real-time usage, flexible data plans, a forecast of when the cap runs out, and an opt-in live per-app view.

## Documents

| Document | Covers |
|---|---|
| This file | Decisions, scope, architecture summary, milestones, risks |
| [Frontend spec](2026-10-01-datakb-frontend-spec.md) | Design tokens, components, navigation, screens, accessibility, UI tests |
| [Backend spec](2026-10-01-datakb-backend-spec.md) | On-device engine, sampling, plan/forecast maths, Live Lens, permissions, error handling |
| [API contract](2026-10-01-datakb-api-contract.md) | Typed module interfaces, storage schema, Android components, file formats. Wins on any naming conflict |

## Decisions

| Topic | Decision |
|---|---|
| Name | **Emberbyte** (store title "Emberbyte: Data Monitor"). "DataKB" is the codename: the GitHub repo and these spec file names keep it. Package and `applicationId`: `io.github.khaledbahaaeldin.emberbyte`. Name check (web search, not a formal trademark search) found no clash on GitHub, F-Droid or Google Play |
| Audience | Open source on GitHub and F-Droid |
| Licence | GPL-3.0 |
| Platform | Native Android, Kotlin + Jetpack Compose, minSdk 29 |
| Real-time engine | Hybrid: light by default (`TrafficStats` + `NetworkStatsManager`), opt-in Live Lens (local VPN) |
| Plan model | Flexible: per-SIM plans, renewal cycles, rollover, add-on packs, free windows |
| Home screen | "The Number" hero with Bento tiles beneath |
| Navigation | Floating glass pill navbar, recreated in Compose from the Floating-Navbar-M3-Flutter design (MIT, credited) |
| Structure | Lean multi-module: `:app`, `:ui:design`, `:core:engine`, `:core:data`, `:feature:lens` |
| Cloud | None. No accounts, analytics or sync. Everything stays on the device |
| Out of scope for v1 | Carrier SMS parsing, an app-blocking firewall, cloud sync |

## Architecture summary

```
:app  (screens, ViewModels, navigation, widgets, DI: hand-written graph in M1, Hilt from M2)
  ├── :ui:design      theme, MorphingNumber, FloatingPillNavBar, Bento tiles (no domain dependency)
  ├── :feature:lens   LensVpnService, packet pipeline, LensController, Lens screen
  └── :core:data      Room, DataStore, repositories, SamplerService, workers, notifications
         ▲
      :core:engine    domain types, cycle/plan/forecast/spike/reconcile maths (pure Kotlin)
```

Data flow: a foreground sampler reads cumulative counters every second for live speed and writes minute
totals; a periodic worker reconciles per-app history from `NetworkStatsManager`; repositories expose
`Flow`s of plan state, forecast and usage; every surface (app, widgets, notification) reads the same flows.
Live Lens, when on, adds per-app live rates and domain names from a passthrough tunnel and is the only part
that touches `VpnService`.

## Feature set

**Core:** daily/weekly/monthly/all-time usage split by mobile and Wi-Fi, per-app usage and screen time,
per-SIM plans, threshold alerts, live-speed notification, widgets, network diagnostics.

**Extra:** the morphing live Number; run-out forecast with a confidence range; Live Lens (per-app and
per-domain, on-device); per-app budgets; spike detective; "what if" planner; Android 16 live-update
notification; CSV/JSON export and encrypted local backup.

## Milestones (each shippable)

1. **M1 Foundation:** module skeleton, CI, theme, floating pill navbar, the Number on a fake data source.
2. **M2 Real usage:** samplers, Room, daily/weekly/monthly views, per-app list, live notification.
   Starts with the dual-SIM and foreground-service-type spikes (backend §13).
3. **M3 Plans:** plan editor, dual-SIM handling, alerts, forecast v1, widgets.
4. **M4 Insight:** budgets, spike detective, "what if" planner, export and backup.
5. **M5 Live Lens:** forwarder spike, then the opt-in VPN engine and Lens screen.
6. **M6 Polish:** accessibility pass, Android 16 live updates, F-Droid submission.

## Risks

| Risk | Mitigation |
|---|---|
| Per-SIM data not separable on API 29+ without privileged access | Attribute live data to the default-data SIM, mark estimates, verify on device in M2 |
| Userspace packet forwarding for Live Lens is large and battery-sensitive | Opt-in, isolated module, M5 begins with a measured spike; last milestone before polish |
| Foreground-service rules differ across Android 14–16 | Verify types on real devices in M2; document in the backend spec |
| OEM battery killers stop the sampler | Gap detection, `CatchUpWorker` backfill, honest gap display, in-app guidance |
| Encrypted DNS/ECH hides domains in Lens | Fall back to IP, label it clearly, document the limit |
| Navbar blur is costly on low-end devices | Tint fallback on API 29–30 and a reduced-effects path for the system "remove animations" setting |

## Repository conventions

- Specs in `docs/superpowers/specs/`; decisions from spikes recorded under `docs/`.
- Conventional, small commits that explain why; no commits without being asked (see `CLAUDE.md`, which is
  git-ignored and personal).
- `NOTICE` and the README credit the navbar design reference and third-party fonts and libraries.
