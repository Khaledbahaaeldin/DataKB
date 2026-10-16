# Emberbyte

An open-source Android data monitor with a Material 3 Expressive interface: real-time transfer speed,
daily and total data consumption by mobile and Wi-Fi, per-app usage history, flexible data plans with
per-SIM binding, add-on packs, free windows, rollover, personalized quota run-out forecasts, threshold
alerts, home-screen widgets, per-app budgets, spike detection, "what if" planning, data export, and
encrypted local backups.

> "DataKB" is the project codename; the repository retains that name.

---

## Screenshots

| Home | Apps | Plans | Settings |
|:---:|:---:|:---:|:---:|
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png" width="200" alt="Home Screen" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_apps.png" width="200" alt="Apps Screen" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_plans.png" width="200" alt="Plans Screen" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_settings.png" width="200" alt="Settings Screen" /> |

---

## Features

- **Live Speed Indicator**: Ongoing foreground notification displaying real-time download speed and remaining plan data.
- **Totals by Mobile & Wi-Fi**: Clear separation of cellular mobile data and Wi-Fi network consumption.
- **Per-App History**: Breakdown of data usage per application across daily and 2-hour measurement windows.
- **Flexible Data Plans with Forecast & Alerts**: Custom billing cycles (monthly or every N days), SIM binding, add-on quota packs, free data windows (e.g. night data), and usage rollover (capped or uncapped). Predicts run-out dates based on your learned hourly usage curve over the last 28 days and alerts at 50%, 80%, 90%, and 100% quota.
- **Home-Screen Widgets**: Jetpack Glance widgets (Hero and Bento). Hero shows today's usage and the remaining data; Bento adds the run-out forecast caption, the download speed as of the last refresh, and the top apps.
- **Per-App Data Budgets**: Set daily or billing-cycle cellular budgets per application with warning notifications.
- **Spike Detector**: Alerts when an app consumes an unusual surge of mobile data against a 14-day median baseline.
- **"What If" Scenario Planner**: Explore how hypothetical extra daily usage alters your projected run-out date.
- **CSV and JSON Streamed Export**: Stream historical traffic metrics without memory spikes.
- **Encrypted Local Backup & Restore**: Secure `.emberbyte` archive backups of plans, budgets, and hourly usage protected with Argon2id and AES-256-GCM.

> [!NOTE]
> An in-depth packet inspection feature ("Live Lens") was explored during development and discarded due to CPU and battery constraints; see [ADR 0001: Discard Live Lens](docs/adr/0001-discard-live-lens.md). Emberbyte avoids VPN tunnels entirely.

---

## Privacy Guarantee

Everything stays on your device. Emberbyte operates 100% offline and declares NO `android.permission.INTERNET`. There are no accounts, no telemetry, no tracking, and no external servers. All metrics and configuration are stored locally in Room databases and encrypted archives.

---

## Declared Permissions

Every permission declared in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) is required for core local features and documented in [RELEASE.md](docs/release/RELEASE.md):

| Permission | Type | Justification / Usage |
|---|---|---|
| `android.permission.PACKAGE_USAGE_STATS` | Special App Access | Required to query per-application daily and hourly data usage buckets via `NetworkStatsManager`. Granted explicitly by user in Android Settings > Usage Access. |
| `android.permission.QUERY_ALL_PACKAGES` | System / Store Policy | Required to resolve installed app names, icons, and package visibility across UIDs for data usage breakdown on Android 11+ (API 30+). |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Required to observe active network connectivity (Wi-Fi vs Cellular vs Metered) and network capabilities. |
| `android.permission.READ_PHONE_STATE` | Dangerous / Runtime | Required on multi-SIM devices to detect active subscription IDs and correlate cellular usage with the user's configured data plan SIM slot. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Required to post plan alerts (thresholds chosen per plan from 50%, 80%, 90% and 100%; default 80% and 100%), budget and spike alerts, and the ongoing live notification. |
| `android.permission.POST_PROMOTED_NOTIFICATIONS` | Normal (API 36+) | Required for Android 16+ Promoted Ongoing live status bar indicator showing remaining plan data (e.g. 3.2 GB or 0 B). |
| `android.permission.FOREGROUND_SERVICE` | Normal | Required to run `SamplerService` as a background foreground service for periodic network rate sampling and threshold checks. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Foreground Service Type (API 34+) | Declares the specific foreground service subtype (`data_metering_sampler`) for continuous background network traffic monitoring under modern platform restrictions. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Allows `BootReceiver` to start `SamplerService` after a reboot or an app update (only once onboarding is complete), so measuring continues without opening the app. |
| `android.permission.WAKE_LOCK` | Normal | Merged by AndroidX WorkManager runtime to allow background periodic maintenance and catch-up jobs to acquire wake locks during execution. |
| `io.github.khaledbahaaeldin.emberbyte.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature | Merged by AndroidX Core library to protect internal dynamic broadcast receivers from unauthorized external broadcasts on modern Android versions. |

---

## Known Limitations

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

---

## Installation

- **F-Droid**: Coming soon once listed in the official F-Droid repository. See [F-Droid draft recipe](docs/release/fdroiddata-io.github.khaledbahaaeldin.emberbyte.yml).
- **GitHub Releases**: Download maintainer-signed APKs or inspect reproducible unsigned release APK artifacts on the [Releases](https://github.com/Khaledbahaaeldin/DataKB/releases) page. (Unsigned APKs are reproducible build verification artifacts and cannot be directly installed on Android devices without signing).

---

## Build

### Requirements
- JDK 17 (Eclipse Temurin recommended)
- Android SDK Platform API 37 (compileSdk 37, targeting Android 16 / API 36)
- Android Build Tools `36.1.0` (or newer)

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest :core:engine:test

# Release APK build (deterministic & reproducible)
./gradlew :app:assembleRelease --no-build-cache --no-daemon
```

---

## Contributing

Contributions, bug reports, and suggestions are welcome!
1. Fork the repository and create a feature branch.
2. Ensure strict code formatting and run the verification test suite before opening a pull request:
   ```bash
   ./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug
   ```
3. Explain why changes are needed in commit messages.

---

## Licence

Emberbyte is free software, licensed under the **GNU General Public License, version 3 or (at your option) any later version** (SPDX: `GPL-3.0-or-later`).
See [LICENSE](LICENSE) for the full licence text and [NOTICE](NOTICE) for third-party software credits.
