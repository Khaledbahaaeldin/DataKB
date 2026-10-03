# Emberbyte

An open-source Android data monitor with a Material 3 Expressive interface: daily and real-time usage,
flexible data plans, a forecast of when your cap runs out, and an opt-in live per-app view.

> Status: milestone M1 (foundation). The app runs on fake data. Real measurement arrives in M2.
> "DataKB" is the project codename; the repository keeps that name.

## Privacy

Everything stays on your device. There are no accounts, no analytics and no cloud. The optional
**Live Lens** feature (milestone 5) uses a local-only VPN to show which app is using data right now; it is
off unless you turn it on, and nothing it sees leaves the phone.

## Build

Requirements: JDK 17 and the Android SDK with platform 37 (compileSdk 37; targetSdk 36) and build tools 36.0.0.

    ./gradlew assembleDebug
    ./gradlew testDebugUnitTest :core:engine:test

Create `local.properties` with `sdk.dir=<path to your Android SDK>` (it is git-ignored).

## Dependencies

The app currently uses Material 3 1.5.0-alpha29 (an alpha) because the Material 3 Expressive APIs are not
public in stable 1.4.0. It will move to stable 1.5.0 when that is released.

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
