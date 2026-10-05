# Emberbyte

An open-source Android data monitor with a Material 3 Expressive interface: daily and real-time usage,
flexible data plans with per-SIM binding, add-on packs, free windows, rollover, a run-out forecast with a range, threshold alerts, and home-screen widgets.

> Status: milestone M3 (plans, forecast, widgets). Measures mobile and Wi-Fi data, flexible data plans, run-out forecast, threshold alerts, and home-screen widgets.
> "DataKB" is the project codename; the repository keeps that name.

## Features

- **Flexible Data Plans**: Custom billing cycles, per-SIM binding, add-on packs, free windows (e.g. night data), and usage rollover (capped or uncapped).
- **Run-Out Forecast**: Predicts when your data cap will run out with a confidence range based on recent usage curves.
- **Threshold Alerts**: Notifies you when you reach specified usage percentages (e.g. 50%, 80%, 100%).
- **Home-Screen Widgets**: Glance-based widgets showing your remaining data and status at a glance.
- **Per-App History**: Historical per-app mobile and Wi-Fi data usage.

## Known limitations

- New traffic can take up to about 5 minutes to appear in Today (O-1).
- "Measurement paused" only appears after the system or a vendor kills the service, and force-stopping Emberbyte stops measuring until it is opened again (N-10).
- The forecast's history includes free-window traffic, so plans with free windows may be projected to run out too early.
- The forecast uses a built-in hour-of-day curve, not yours.
- Per-SIM history is estimated when more than one SIM was used (backend spec section 5).
- A plan bound to a SIM is marked "Estimated" when part of its history has no SIM information (pre-install or backfilled traffic) or the window starts before Emberbyte's first measurement (for example without usage access).
- Plan alerts need the app notification setting AND the 'Plan alerts' channel enabled.
- A VPN running over Wi-Fi is counted twice.

## Privacy

Everything stays on your device. There are no accounts, no analytics and no cloud. The app
requests `QUERY_ALL_PACKAGES` only to show app names and `PACKAGE_USAGE_STATS` only for per-app
history, both optional for totals. The optional **Live Lens** feature (milestone 5) uses a
local-only VPN to show which app is using data right now; it is off unless you turn it on,
and nothing it sees leaves the phone.

## Build

Requirements: JDK 17 and the Android SDK with platform 37 (compileSdk 37; targetSdk 36) and build tools 36.0.0.

    ./gradlew assembleDebug
    ./gradlew testDebugUnitTest :core:engine:test

Create `local.properties` with `sdk.dir=<path to your Android SDK>` (it is git-ignored).

## Dependencies

The app currently uses Material 3 1.5.0-alpha29 (an alpha) because the Material 3 Expressive APIs are not
public in stable 1.4.0. It will move to stable 1.5.0 when that is released. It also uses Jetpack Glance for
home-screen widgets.

## Documentation

Design and specs live in `docs/superpowers/specs/`; implementation plans in `docs/superpowers/plans/`.

## Credits

The floating pill navigation bar is inspired by the design of Damantha126's
[Floating-Navbar-M3-Flutter](https://github.com/Damantha126/Floating-Navbar-M3-Flutter) (MIT).
The Compose implementation in this repository is original; no code was copied.
Full third-party notices are in `NOTICE`.

## Licence

Emberbyte is free software, licensed under the **GNU General Public License, version 3 or (at your option) any later
version** (SPDX: `GPL-3.0-or-later`). The licence text is in `LICENSE`; third-party credits are in `NOTICE`.
