# Emberbyte Release Guide

This document describes how to build, verify, and release reproducible unsigned APKs for Emberbyte, as well as the F-Droid inclusion requirements, permission audit, and release checklist.

---

## 1. Reproducible Build Instructions

Emberbyte release builds are deterministic. Clean checkouts of the same commit yield byte-for-byte identical unzipped APK contents (excluding standard zip archive timestamp metadata).

### Prerequisites
- **JDK**: Eclipse Temurin OpenJDK 17 (`java -version` -> `17.x.x`)
- **Android SDK Platform**: API Level 37 (`compileSdk = 37`, targeting Android 16 / API 36)
- **Build Tools**: `36.1.0` (or newer)
- **Gradle**: Managed via included wrapper (`gradle/wrapper/gradle-wrapper.properties`, Gradle 9.6.0)

### Clean Build Command
Run from the root of a clean repository checkout:

```bash
./gradlew :app:assembleRelease --no-build-cache --no-daemon
```

### Build Artifact
The build produces an unsigned release APK:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 2. Release Build Configuration

The release build configuration is defined in [`app/build.gradle.kts`](../../app/build.gradle.kts) and [`app/proguard-rules.pro`](../../app/proguard-rules.pro):

- **Shrinking & Minification**: `isMinifyEnabled = true` with R8 code shrinking and full mode optimizations.
- **Resource Shrinking**: `isShrinkResources = true`; AGP and AAPT2 automatically analyze referenced drawables, launcher icons, and widget layouts without requiring custom resource keep rules.
- **Narrow Keep Rules**: Library consumer rules automatically protect Room entities/databases and WorkManager workers; R8 bytecode tracing preserves direct calls to Bouncy Castle Argon2 and Glance app widgets.
- **Third-Party Dependency Licences**: Checked-in at [`docs/release/dependency-licences.txt`](dependency-licences.txt) and validated by `NoticeCoversDependenciesTest`. Generated from:
  ```bash
  ./gradlew :app:dependencies --configuration releaseRuntimeClasspath
  ```
- **Version Metadata**:
  - `versionCode = 1`
  - `versionName = "1.0.0"`

---

## 3. F-Droid Distribution & Signing

Emberbyte is built as an open-source, reproducible Android application ready for F-Droid inclusion:
- **Build from Source**: F-Droid build recipes fetch source code from tagged git releases and execute `./gradlew :app:assembleRelease`.
- **Reproducible Builds**: F-Droid build recipes build the application from source in an isolated environment and sign the binary with the F-Droid signing key (or verify binary reproducibility against maintainer builds when reproducible build metadata is published).
- **Signing**: F-Droid signs the resulting APK with the official F-Droid signing key for F-Droid client users. For standalone GitHub releases, maintainers sign the unsigned release APK using `apksigner`:
  ```bash
  apksigner sign --ks <release-keystore.jks> \
    --ks-key-alias <alias> \
    --in app/build/outputs/apk/release/app-release-unsigned.apk \
    --out emberbyte-1.0.0-signed.apk
  ```

---

## 4. Declared Permissions Audit

Every permission declared in [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) is required for core functionality, documented in the Privacy section of the README, and scoped strictly to local on-device operations:

| Permission | Type | Justification / Usage |
|---|---|---|
| `android.permission.PACKAGE_USAGE_STATS` | Special App Access | Required to query per-application daily and hourly data usage buckets via `NetworkStatsManager`. Granted explicitly by user in Android Settings > Usage Access. |
| `android.permission.QUERY_ALL_PACKAGES` | System / Store Policy | Required to resolve installed app names, icons, and package visibility across UIDs for data usage breakdown on Android 11+ (API 30+). |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Required to observe active network connectivity (Wi-Fi vs Cellular vs Metered) and network capabilities. |
| `android.permission.READ_PHONE_STATE` | Dangerous / Runtime | Required on multi-SIM devices to detect active subscription IDs and correlate cellular usage with the user's configured data plan SIM slot. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Required to post threshold warning notifications (50%, 80%, 90%, 100% plan quota) and promoted ongoing live sampler notifications. |
| `android.permission.POST_PROMOTED_NOTIFICATIONS` | Normal (API 36+) | Required for Android 16+ Promoted Ongoing live status bar indicator showing remaining plan data (e.g. 3.2 GB or 0 B). |
| `android.permission.FOREGROUND_SERVICE` | Normal | Required to run `SamplerService` as a background foreground service for periodic network rate sampling and threshold checks. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Foreground Service Type (API 34+) | Declares the specific foreground service subtype (`data_metering_sampler`) for continuous background network traffic monitoring under modern platform restrictions. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Allows `BootReceiver` to restore `SamplerService` and alarms immediately when the device boots, preventing unmonitored data leaks after restart. |
| `android.permission.WAKE_LOCK` | Normal | Merged by AndroidX WorkManager runtime to allow background periodic maintenance and catch-up jobs to acquire wake locks during execution. |
| `io.github.khaledbahaaeldin.emberbyte.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature | Merged by AndroidX Core library to protect internal dynamic broadcast receivers from unauthorized external broadcasts on modern Android versions. |

---

## 5. Reproducibility Proof & Verification

To verify that two clean checkouts produce identical builds:

1. Clone or archive two clean copies of the commit into separate directories `build1` and `build2`:
   ```bash
   git archive --format=tar <COMMIT-HASH> | tar -x -C /tmp/build1
   git archive --format=tar <COMMIT-HASH> | tar -x -C /tmp/build2
   ```
2. Build unsigned release APKs in both:
   ```bash
   (cd /tmp/build1 && ./gradlew :app:assembleRelease --no-build-cache --no-daemon)
   (cd /tmp/build2 && ./gradlew :app:assembleRelease --no-build-cache --no-daemon)
   ```
3. Run `tools/compare-apks.py` to compare APK zip entries by name and SHA-256 (ignoring signature files in `META-INF/`):
   ```bash
   python tools/compare-apks.py \
     /tmp/build1/app/build/outputs/apk/release/app-release-unsigned.apk \
     /tmp/build2/app/build/outputs/apk/release/app-release-unsigned.apk
   ```
   *(Note: The traditional `unzip` + `diff -r` approach silently drops 178 entries whose names differ only by case on case-insensitive filesystems like Windows and macOS APFS; `tools/compare-apks.py` inspects the zip central directory directly).*

   **Verified Baseline Figures**:
   - Release APK Size: ~5.96 MB (5,956,431 bytes)
   - Total Zip Entries: 1,088
   - Non-META-INF Entries Compared: 998 (100% matched)
   - Unsigned Release APK SHA-256: `f36e56126243a3b6f09e5c005b4e6a838651801263b310be1dbc59533980a8ea` *(Note: The whole-file APK SHA-256 varies between commits and between git checkouts vs archive tarballs because AGP embeds VCS revision metadata into `META-INF/version-control-info.textproto`. Tools like `compare-apks.py` verify all functional non-META-INF zip entries match byte-for-byte).*

---

## 6. Release Cut Checklist

When cutting a new production release:

1. **Gate Verification**:
   - Ensure the full verification suite passes cleanly:
     ```bash
     ./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease verifyReleaseApk --stacktrace
     ```
2. **Version Bump**:
   - In `app/build.gradle.kts`, bump `versionCode` (monotonically incrementing integer) and `versionName` (semantic version string, e.g. `"1.0.1"`).
   - In `ReleaseApkVerificationTest.kt`, update expected version test constants if bumping version.
3. **Commit & Tag**:
   - Commit version bump: `release: cut v1.0.0`.
   - Create annotated signed git tag: `git tag -s v1.0.0 -m "Release v1.0.0"`.
4. **CI Release Workflow**:
   - Push commit and tag to GitHub.
   - Verify GitHub Actions CI runs `assembleRelease`, `lintRelease`, and `verifyReleaseApk`, uploading `emberbyte-release-unsigned`.
5. **Sign & Publish**:
   - Download the unsigned APK artifact.
   - Sign using release keystore with `apksigner`.
   - Publish GitHub Release with changelog and SHA256 checksum of the signed APK.

