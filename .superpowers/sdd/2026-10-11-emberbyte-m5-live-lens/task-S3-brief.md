# Task S3 Brief: Android glue, device measurement and the decision record (then STOP)

### Source: Task S3 of `docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md`

**Files:**
- Create:
  - `feature/lens/src/main/AndroidManifest.xml` (the `LensVpnService` declaration: `android:permission="android.permission.BIND_VPN_SERVICE"`, `android:exported="false"`, intent filter `android.net.VpnService`, `android:foregroundServiceType="specialUse"` with property `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`)
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/LensVpnService.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ParcelTunDevice.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/VpnSocketProtector.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ConnectivityOwnerResolver.kt`
  - Debug only: `app/src/debug/AndroidManifest.xml` (adds `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `LensSpikeActivity`, `LensSpikeReceiver`)
  - Debug only: `app/src/debug/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/spike/LensSpikeActivity.kt`
  - Debug only: `app/src/debug/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/spike/LensSpikeReceiver.kt`
  - `docs/spikes/lens-forwarder.md` (decision record)
  - `docs/spikes/measure-lens.sh` (measurement script)
- Test:
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/android/ConnectivityOwnerResolverTest.kt` (Robolectric with ShadowConnectivityManager)

**Interfaces (contract 12.3):**
- `LensVpnService : VpnService` with `companion { fun startIntent(context): Intent; fun stopIntent(context): Intent }`
- `ParcelTunDevice(pfd: ParcelFileDescriptor) : TunDevice` (blocking `FileInputStream`/`FileOutputStream`, `close()` closes both and unblocks)
- `VpnSocketProtector(service: VpnService) : SocketProtector`
- `ConnectivityOwnerResolver(cm: ConnectivityManager) : OwnerResolver` (`cm.getConnectionOwnerUid(protocol, local, remote)`, wrapped in `try/catch`, returns -1 on `Process.INVALID_UID` or exception).

**Requirements:**
- [ ] **Step 1: Test and build.**
  - `ConnectivityOwnerResolverTest`: unknown owner (`INVALID_UID`) gives -1; exception gives -1; known uid returned for TCP (6) and UDP (17).
  - Implement glue:
    - `LensVpnService.onStartCommand(ACTION_START)` builds TUN per contract 12.3 (session "Emberbyte Lens", MTU 1400, `10.7.0.2/32`, `fd7a:115c:a1e0::2/128`, routes `0.0.0.0/0` and `::/0`, no DNS server, `setBlocking(true)`). Calls `startForeground` with minimal ongoing notification on channel `lens`. Starts `Forwarder.run()` on dedicated thread.
    - `ACTION_STOP` stops forwarder (join <= 2s), closes TUN, calls `stopSelf()`. `onRevoke()` does the same.
    - Debug `LensSpikeActivity`: calls `VpnService.prepare(this)` and shows system dialog.
    - Debug `LensSpikeReceiver`: handles `adb shell am broadcast -a io.github.khaledbahaaeldin.emberbyte.debug.LENS_START` / `LENS_STOP` / `LENS_DUMP` (DUMP logs counters only: flows open, evicted, errors, tun rx/tx bytes, never addresses or names).
- [ ] **Step 2: Gate** — green. Check release compilation has no `LensSpike*` classes (`./gradlew :app:compileReleaseKotlin`).
- [ ] **Step 3: Device measurement**
  - Run `docs/spikes/measure-lens.sh` on attached emulator `emulator-5554`.
  - Host files served on `10.0.2.2:8000` (`python -m http.server 8000`).
  - Measure correctness (curl downloads, SHA-256 integrity, upload, 200 parallel requests) and performance (speed, CPU time, memory, flow table teardown).
- [ ] **Step 4: Write decision record** `docs/spikes/lens-forwarder.md`:
  - Exactly one of: `KOTLIN CONFIRMED` or `NATIVE NEEDED`.
- [ ] **Step 5: Commit** `feat(lens): VPN service glue and the forwarder spike with its decision record`.
- [ ] **Step 6: STOP.**
  - Print decision section and key numbers, then say:
    **"The Lens spike is done: decision <KOTLIN CONFIRMED|NATIVE NEEDED>. Waiting for Claude to review docs/spikes/lens-forwarder.md before part 3."**

### Global Constraints & Rules:
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- Stage only files you changed by explicit path.
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com`.
- Commit message ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Privacy invariant: Payload bytes are never stored or logged.
