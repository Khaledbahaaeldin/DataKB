# Task S3 Report: Android glue, device measurement and the decision record

**Task:** S3 of `docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md`  
**Status:** DONE  
**Commit:** `4bdac72bc54add3ac04b28f7b079950000ca5821`  
**Commit Subject:** `feat(lens): VPN service glue and the forwarder spike with its decision record`  
**Decision:** `KOTLIN CONFIRMED`

---

## 1. Work Completed

1. **Android VPN Service Glue & Components Implemented:**
   - [AndroidManifest.xml](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/AndroidManifest.xml): Declared `LensVpnService` with `android.permission.BIND_VPN_SERVICE`, `exported="false"`, `foregroundServiceType="specialUse"`, `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
   - [`LensVpnService`](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/LensVpnService.kt): Handles `ACTION_START` (TUN builder session "Emberbyte Lens", MTU 1400, addresses `10.7.0.2/32`, `fd7a:115c:a1e0::2/128`, routes `0.0.0.0/0` and `::/0`, blocking mode, ongoing notification, starting forwarder) and `ACTION_STOP` / `onRevoke()`.
   - [`ParcelTunDevice`](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ParcelTunDevice.kt): Wraps `ParcelFileDescriptor` with non-leaking streams.
   - [`VpnSocketProtector`](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/VpnSocketProtector.kt): Bridges `VpnService.protect()`.
   - [`ConnectivityOwnerResolver`](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ConnectivityOwnerResolver.kt): Queries `cm.getConnectionOwnerUid()` with exception and `INVALID_UID` safety.
   - Debug harness: [`LensSpikeActivity`](file:///c:/Fast%20Project/DataKB/app/src/debug/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/spike/LensSpikeActivity.kt) and [`LensSpikeReceiver`](file:///c:/Fast%20Project/DataKB/app/src/debug/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/spike/LensSpikeReceiver.kt) handling `LENS_START`, `LENS_STOP`, and `LENS_DUMP`.

2. **Unit & Robolectric Testing:**
   - [`ConnectivityOwnerResolverTest`](file:///c:/Fast%20Project/DataKB/feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ConnectivityOwnerResolverTest.kt) passes with ShadowConnectivityManager (unknown uid -> -1, exception -> -1, known uid for TCP/UDP -> uid).
   - `./gradlew :feature:lens:testDebugUnitTest`: All 50 tests PASS.

3. **Release Compilation Check:**
   - `./gradlew :app:compileReleaseKotlin`: SUCCESS (no debug spike classes present in release classpath).

4. **On-Device Measurement on `emulator-5554`:**
   - Python & bash test harnesses: [docs/spikes/measure_lens.py](file:///c:/Fast%20Project/DataKB/docs/spikes/measure_lens.py) and [docs/spikes/measure-lens.sh](file:///c:/Fast%20Project/DataKB/docs/spikes/measure-lens.sh).
   - Small file HTTP (100 KB): SHA-256 identical (`24579de84a480f0baf28c16bfdcb6cf2f6722e90ad7c8d1dd411a4e7247e2e65`)
   - Large file HTTP (100 MB): SHA-256 identical (`1766ba2079424ef06fb72c4d8a8a20daa2a0b54b096a70d771775562390de92b`)
   - Parallel requests: 200 concurrent downloads handled with 0 errors.
   - Upload: 20 MB PUT verified (HTTP 200).
   - UDP echo: `ECHO_TEST` verified.
   - Force-stop: Clean unmount verified via `dumpsys connectivity`.
   - Throughput: Baseline `1,464,487 B/s` (~1.40 MB/s) vs Lens ON `1,481,913 B/s` (~1.41 MB/s) (0% degradation).
   - Memory PSS: Flat at ~108-111 MB total process footprint (<1 MB variation before vs after 100 MB streaming).
   - Idle table cleanup: Flow count returns to baseline `~0..1` after 60s idle.

5. **Decision Record:**
   - Authored [docs/spikes/lens-forwarder.md](file:///c:/Fast%20Project/DataKB/docs/spikes/lens-forwarder.md) with exact commands, test table, open-source firewall lessons, and conclusion `KOTLIN CONFIRMED`.

6. **Quality Gate Command:**
   - `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`: ALL PASSED.
