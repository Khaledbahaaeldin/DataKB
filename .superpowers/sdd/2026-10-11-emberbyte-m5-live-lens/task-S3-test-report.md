# Task S3 Test Report: Android Glue, Device Measurement, and Decision Record

**Target Commit:** `4bdac72bc54add3ac04b28f7b079950000ca5821`  
**Commit Author:** `Omar Amin <omarsadiq2012@gmail.com>`  
**Co-Authored-By:** `Gemini (Antigravity) <noreply@google.com>`  
**Branch:** `m5-live-lens`  
**Verdict:** **PASS**

---

## 1. Verdict

**PASS** — All brief requirements, invariants, unit tests, release separation constraints, and full gates have been verified independently.

- **Critical Issues:** 0
- **Important Issues:** 0
- **Minor Issues:** 0

---

## 2. Spec Findings

### 2.1 Android Glue Components
1. **Manifest Service Declaration (`feature/lens/src/main/AndroidManifest.xml`):**
   - Declares `LensVpnService`.
   - `android:permission="android.permission.BIND_VPN_SERVICE"` present.
   - `android:exported="false"` present.
   - Intent-filter for `android.net.VpnService` present.
   - `android:foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property configured per Android 14+ requirements.
2. **`LensVpnService.kt` (`feature/lens/src/main/kotlin/...`):**
   - Extends `VpnService`.
   - Exposes companion helper functions `startIntent(context)` and `stopIntent(context)`.
   - `onStartCommand(ACTION_START)` establishes TUN interface matching Contract 12.3:
     - Session: `"Emberbyte Lens"`
     - MTU: `1400`
     - Addresses: `10.7.0.2/32`, `fd7a:115c:a1e0::2/128`
     - Routes: `0.0.0.0/0`, `::/0`
     - DNS: No DNS server set on TUN (falls back to underlying network)
     - Blocking mode enabled (`setBlocking(true)`)
   - Starts foreground service with persistent notification on channel `lens`.
   - Starts `Forwarder.run()` on dedicated thread `"LensVpnService-Forwarder"`.
   - `ACTION_STOP` and `onRevoke()` stop forwarder (`join(2000)`), close TUN device, remove notification, and call `stopSelf()`.
3. **`ParcelTunDevice.kt`:**
   - Implements `TunDevice` wrapping `ParcelFileDescriptor`.
   - Uses `FileInputStream` and `FileOutputStream` with synchronized write locking.
   - Tracks atomic `rxBytes` and `txBytes` counters without logging payload or endpoints.
   - Safe `close()` cleans up streams and file descriptor.
4. **`VpnSocketProtector.kt`:**
   - Implements `SocketProtector`.
   - Protects `Socket`, `DatagramSocket`, and `SelectableChannel` (`SocketChannel`, `DatagramChannel`) by delegating to `VpnService.protect()`.
5. **`ConnectivityOwnerResolver.kt`:**
   - Implements `OwnerResolver`.
   - Calls `cm.getConnectionOwnerUid(protocol, local, remote)` wrapped in `try/catch`.
   - Returns `-1` if result is `Process.INVALID_UID` or if an exception is thrown.

### 2.2 Debug Harness Separation
1. **`app/src/debug/AndroidManifest.xml`:**
   - Lives strictly in debug variant (`app/src/debug/`).
   - Declares `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`.
   - Declares `LensSpikeActivity` and `LensSpikeReceiver`.
2. **`LensSpikeActivity.kt`:**
   - Invokes `VpnService.prepare(this)` and requests VPN consent.
3. **`LensSpikeReceiver.kt`:**
   - Handles `io.github.khaledbahaaeldin.emberbyte.debug.LENS_START`, `LENS_STOP`, and `LENS_DUMP`.
   - Privacy compliant: `LENS_DUMP` logs and reports only aggregated numeric counters (`open`, `evicted`, `errors`, `tunRx`, `tunTx`, `opened`, `knownUid`, `unknownUid`), never IPs, server names, or external UIDs.
4. **Release Compilation Check:**
   - `./gradlew :app:compileReleaseKotlin` succeeds without error.
   - Verified that `app/build/` contains zero `LensSpike*` classes in release compilation artifacts.

### 2.3 Decision Record (`docs/spikes/lens-forwarder.md`)
- Author: `Omar Amin <omarsadiq2012@gmail.com>`.
- Target Architecture Decision: `KOTLIN CONFIRMED`.
- Contains open-source architectural analysis (NetGuard, RethinkDNS, PCAPdroid).
- Contains test environment description (Pixel AVD API 34 on `emulator-5554`, local test server on `10.0.2.2:8000`).
- Contains 11-point correctness evaluation suite table with specific SHA-256 hashes and details.
- Contains measured benchmarks:
  - Throughput: Baseline 1.40 MB/s vs Lens ON 1.41 MB/s (0% throughput degradation).
  - CPU: ~1.35 virtual core utilization during 100 MB stream.
  - Memory (PSS): Flat footprint at ~108–111 MB total process memory (<1 MB delta before vs after 100 MB stream).
  - Teardown: Flow table drops back to `0..1` flows after 60s idle timeout.
- Rationale clearly documented with conclusion that native layer is not needed.

---

## 3. Run Results

### 3.1 Feature Unit Tests (`./gradlew :feature:lens:testDebugUnitTest --rerun-tasks`)
```
> Task :feature:lens:compileDebugUnitTestKotlin
> Task :feature:lens:testDebugUnitTest

BUILD SUCCESSFUL in 1m 19s
49 actionable tasks: 49 executed
```
Test results summary:
- `ConnectivityOwnerResolverTest`: 4 tests passed, 0 failures, 0 errors, 0 skipped.
- Total `:feature:lens:` tests: 50 tests passed, 0 failures, 0 errors, 0 skipped.

### 3.2 App Release Compilation (`./gradlew :app:compileReleaseKotlin --rerun-tasks`)
```
> Task :feature:lens:compileReleaseKotlin
> Task :feature:lens:bundleLibCompileToJarRelease
> Task :app:compileReleaseKotlin

BUILD SUCCESSFUL in 59s
33 actionable tasks: 33 executed
```
- Release artifacts inspected: zero `LensSpike` classes present.

### 3.3 Full Project Quality Gate (`./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`)
```
> Task :feature:lens:testDebugUnitTest UP-TO-DATE
> Task :core:data:testDebugUnitTest UP-TO-DATE
> Task :ui:design:testDebugUnitTest UP-TO-DATE
> Task :app:testDebugUnitTest UP-TO-DATE
> Task :feature:lens:lintDebug
> Task :app:lintDebug
> Task :app:assembleDebug UP-TO-DATE

BUILD SUCCESSFUL in 1m 4s
232 actionable tasks: 16 executed, 216 up-to-date
```

### 3.4 Live Emulator Inspection
- Target device `emulator-5554` online and responsive.
- Broadcast receiver test via `adb`:
```
$ adb -s emulator-5554 shell "am broadcast -a io.github.khaledbahaaeldin.emberbyte.debug.LENS_DUMP -p io.github.khaledbahaaeldin.emberbyte"
Broadcasting: Intent { act=io.github.khaledbahaaeldin.emberbyte.debug.LENS_DUMP flg=0x400000 pkg=io.github.khaledbahaaeldin.emberbyte }
Broadcast completed: result=0, data="open=0 evicted=0 errors=0 tunRx=0 tunTx=0 opened=0 knownUid=0 unknownUid=0 (inactive)"
```

---

## 4. Test-Quality Findings

1. **`ConnectivityOwnerResolverTest`:**
   - Uses Robolectric (`@Config(sdk = [34])`) with a custom `ShadowTestConnectivityManager` extending `ShadowConnectivityManager`.
   - Tests boundary conditions:
     - `returnsMinusOneWhenOwnerIsUnknown`: verifies `Process.INVALID_UID` maps to `-1`.
     - `returnsMinusOneOnException`: verifies that `SecurityException` thrown by platform method is caught and safely returns `-1`.
     - `returnsKnownUidForTcpProtocol`: verifies TCP protocol (`6`) correctly maps to known UID (`10123`).
     - `returnsKnownUidForUdpProtocol`: verifies UDP protocol (`17`) correctly maps to known UID (`10124`).
2. **Deterministic & Non-trivial:**
   - Tests execute assertions on return values and fail immediately if behavior deviates.

---

## 5. Invariants Check

1. **Java Version:**
   - Toolchain and compatibility set to Java 17.
   - No Java 18+ APIs used.
2. **Privacy Invariant:**
   - No payload logged or stored.
   - Logs and broadcast results only contain numeric counters. No IP addresses, domain names, or external UIDs logged.
3. **Repository Cleanliness & Safety:**
   - `git diff --stat HEAD -- docs/superpowers` is empty.
   - Contract section 12 count: `git grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` = `1`.
   - No prohibited git commands executed.
