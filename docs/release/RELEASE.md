# Emberbyte Release Guide

This document describes how to build, verify, and release reproducible unsigned APKs for Emberbyte, as well as the F-Droid inclusion requirements, permission audit, and release checklist.

---

## 1. Reproducible Build Instructions

Emberbyte release builds are deterministic. Clean checkouts of the same commit yield byte-for-byte identical unzipped APK contents (excluding standard zip archive timestamp metadata).

### Prerequisites
- **JDK**: Eclipse Temurin OpenJDK 17 (`java -version` -> `17.x.x`)
- **Android SDK Platform**: API Level 36 (Android 16 DP/Vanilla Ice Cream preview / API 36)
- **Build Tools**: `35.0.0`
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

The release build configuration is defined in [`app/build.gradle.kts`](../../app/build.gradle.kts#L39-L55) and [`app/proguard-rules.pro`](../../app/proguard-rules.pro):

- **Shrinking & Minification**: `isMinifyEnabled = true` with R8 code shrinking and full mode optimizations.
- **Resource Shrinking**: `isShrinkResources = true` with strict keep rules for launcher icons, notification drawables, and widget assets.
- **Narrow Keep Rules**:
  - Keep Room entities, DAOs, and migrations (`androidx.room.**`).
  - Keep Argon2id encryption engine (`org.bouncycastle.crypto.generators.Argon2BytesGenerator`).
  - Keep Glance widget receivers and compose entry points (`androidx.glance.**`, `io.github.khaledbahaaeldin.emberbyte.widget.**`).
  - Keep WorkManager background workers and factories.
  - Keep variable fonts (`roboto_flex.ttf`) and asset files (`notice.txt`).
- **Version Metadata**:
  - `versionCode = 1`
  - `versionName = "1.0.0"`

---

## 3. F-Droid Distribution & Signing

Emberbyte is built as an open-source, reproducible Android application ready for F-Droid inclusion:
- **Build from Source**: F-Droid build recipes fetch source code from tagged git releases and execute `./gradlew :app:assembleRelease`.
- **Reproducible Builds**: F-Droid's verification bots build the release binary in an isolated container and compare the output against our published unsigned APK.
- **Signing**: F-Droid signs the resulting APK with the official F-Droid signing key. For standalone GitHub releases, maintainers sign the unsigned release APK using `apksigner`:
  ```bash
  apksigner sign --ks <release-keystore.jks> \
    --ks-key-alias <alias> \
    --in app/build/outputs/apk/release/app-release-unsigned.apk \
    --out emberbyte-1.0.0-signed.apk
  ```

---

## 4. Declared Permissions Audit

Every permission declared in [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) is required for core functionality, documented in the in-app privacy policy, and scoped strictly to local on-device operations:

| Permission | Type | Justification / Usage |
|---|---|---|
| `android.permission.PACKAGE_USAGE_STATS` | Special App Access | Required to query per-application daily and hourly data usage buckets via `NetworkStatsManager`. Granted explicitly by user in Android Settings > Usage Access. |
| `android.permission.QUERY_ALL_PACKAGES` | System / Store Policy | Required to resolve installed app names, icons, and package visibility across UIDs for data usage breakdown on Android 11+ (API 30+). |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Required to observe active network connectivity (Wi-Fi vs Cellular vs Metered) and network capabilities. |
| `android.permission.READ_PHONE_STATE` | Dangerous / Runtime | Required on multi-SIM devices to detect active subscription IDs and correlate cellular usage with the user's configured data plan SIM slot. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Required to post threshold warning notifications (80%, 90%, 100% plan quota) and promoted ongoing live sampler notifications. |
| `android.permission.POST_PROMOTED_NOTIFICATIONS` | Normal (API 36+) | Required for Android 16+ Promoted Ongoing live status bar indicator showing real-time network transfer throughput. |
| `android.permission.FOREGROUND_SERVICE` | Normal | Required to run `SamplerService` as a background foreground service for periodic network rate sampling and threshold checks. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Foreground Service Type (API 34+) | Declares the specific foreground service subtype (`data_metering_sampler`) for continuous background network traffic monitoring under modern platform restrictions. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Allows `BootReceiver` to restore `SamplerService` and alarms immediately when the device boots, preventing unmonitored data leaks after restart. |

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
3. Unzip both APKs and remove zip signature metadata (`META-INF/`):
   ```bash
   unzip /tmp/build1/app/build/outputs/apk/release/app-release-unsigned.apk -d /tmp/apk1
   unzip /tmp/build2/app/build/outputs/apk/release/app-release-unsigned.apk -d /tmp/apk2
   rm -rf /tmp/apk1/META-INF /tmp/apk2/META-INF
   ```
4. Compare directory contents recursively:
   ```bash
   diff -r /tmp/apk1 /tmp/apk2
   ```
   **Expected Result**: Zero differences across all 1,200+ compiled classes (`classes.dex`), resources (`resources.arsc`), layouts, and compiled XML files.

---

## 6. Release Cut Checklist

When cutting a new production release:

1. **Gate Verification**:
   - Ensure the full verification suite passes cleanly:
     ```bash
     ./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug assembleRelease lintRelease --stacktrace
     ```
2. **Version Bump**:
   - In `app/build.gradle.kts`, bump `versionCode` (monotonically incrementing integer) and `versionName` (semantic version string, e.g. `"1.0.1"`).
   - In `ReleaseApkVerificationTest.kt`, update expected version test constants if bumping version.
3. **Commit & Tag**:
   - Commit version bump: `release: cut v1.0.0`.
   - Create annotated signed git tag: `git tag -s v1.0.0 -m "Release v1.0.0"`.
4. **CI Release Workflow**:
   - Push commit and tag to GitHub.
   - Verify GitHub Actions CI runs `assembleRelease` and `lintRelease`, uploading `emberbyte-release-unsigned`.
5. **Sign & Publish**:
   - Download the unsigned APK artifact.
   - Sign using release keystore with `apksigner`.
   - Publish GitHub Release with changelog and SHA256 checksum of the signed APK.
