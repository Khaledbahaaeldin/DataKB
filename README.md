# Emberbyte

An open-source Android data monitor with a Material 3 Expressive interface: daily and real-time usage,
flexible data plans with per-SIM binding, add-on packs, free windows, rollover, a personalized run-out forecast,
per-app data budgets, spike detection, "what if" planning, threshold alerts, encrypted backups, and home-screen widgets.

> Status: milestone M4 (insight: budgets, spikes, what-if, export and backup, personal curve).
> "DataKB" is the project codename; the repository keeps that name.

## Features

- **Flexible Data Plans**: Custom billing cycles, per-SIM binding, add-on packs, free windows (e.g. night data), and usage rollover (capped or uncapped).
- **Per-App Data Budgets**: Daily or plan-cycle mobile data budgets per app with configurable warning thresholds (50%, 80%, 90%) and alert notifications.
- **Spike Detection**: Detects unusual mobile data consumption per 2-hour window against a 14-day median baseline and alerts you.
- **"What If" Planner**: Scenario planning in the plan editor to project run-out dates under hypothetical extra daily usage.
- **Personalized Forecast**: Predicts when your data cap will run out using a learned hour-of-day curve from your past week of usage on the plan's SIM, including free-window traffic.
- **Threshold Alerts**: Notifies you when you reach specified usage percentages (e.g. 50%, 80%, 100%).
- **CSV and JSON Streamed Export**: Streamed export of historical usage and plan data without loading entire datasets into memory.
- **Encrypted Backup & Restore**: Secure `.emberbyte` backups protected by Argon2id key derivation and AES-256-GCM encryption.
- **Home-Screen Widgets**: Glance-based widgets showing your remaining data and status at a glance.
- **Per-App History**: Historical per-app mobile and Wi-Fi data usage.

## Known limitations

- New traffic can take up to about 5 minutes to appear in Today (O-1).
- "Measurement paused" only appears after the system or a vendor kills the service, and force-stopping Emberbyte stops measuring until it is opened again (N-10).
- Spikes are judged on whole 2-hour windows (Android keeps per-app history in 2-hour buckets), so a spike is noticed within one catch-up cycle after the window has enough traffic, not within minutes.
- Budgets and spikes count mobile data only.
- A backup cannot be recovered without its passphrase (there is no reset).
- Restoring replaces plans, budgets and history on this device and skips usage rows of apps that are not installed.
- Per-SIM history is estimated when more than one SIM was used (backend spec section 5).
- A plan bound to a SIM is marked "Estimated" when part of its history has no SIM information (pre-install or backfilled traffic) or the window starts before Emberbyte's first measurement (for example without usage access).
- Plan alerts need the app notification setting AND the 'Plan alerts' channel enabled.
- A VPN running over Wi-Fi is counted twice.

## Privacy

Everything stays on your device. There are no accounts, no analytics and no cloud. The app
requests `QUERY_ALL_PACKAGES` only to show app names and `PACKAGE_USAGE_STATS` only for per-app
history, both optional for totals. Emberbyte never uses a VPN and cannot see which servers an app contacts.

## Build

Requirements: JDK 17 and the Android SDK with platform 37 (compileSdk 37; targetSdk 36) and build tools 36.0.0.

    ./gradlew assembleDebug
    ./gradlew testDebugUnitTest :core:engine:test

Create `local.properties` with `sdk.dir=<path to your Android SDK>` (it is git-ignored).

## Dependencies

The app currently uses Material 3 1.5.0-alpha29 (an alpha) because the Material 3 Expressive APIs are not
public in stable 1.4.0. It will move to stable 1.5.0 when that is released. It also uses Jetpack Glance for
home-screen widgets, and Bouncy Castle (`bcprov-jdk18on`) for Argon2id key derivation in backups.

## Documentation

Design and specs live in `docs/superpowers/specs/`; implementation plans in `docs/superpowers/plans/`.
The backup format specification is in `docs/backup-format.md`.

## Credits

The floating pill navigation bar is inspired by the design of Damantha126's
[Floating-Navbar-M3-Flutter](https://github.com/Damantha126/Floating-Navbar-M3-Flutter) (MIT).
The Compose implementation in this repository is original; no code was copied.
Full third-party notices are in `NOTICE`.

## Licence

Emberbyte is free software, licensed under the **GNU General Public License, version 3 or (at your option) any later
version** (SPDX: `GPL-3.0-or-later`). The licence text is in `LICENSE`; third-party credits are in `NOTICE`.
