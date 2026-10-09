# Emberbyte M5 "Live Lens" Implementation Plan

> **For the executing agents (Antigravity):** follow `docs/superpowers/plans/2026-10-11-m5-antigravity-prompt.md` (an implementer and a fresh tester per task,
> a ledger, at most 3 fix rounds per task). Steps use checkbox (`- [ ]`) syntax. The plan has THREE PARTS and a hard stop between part 2 and part 3:
> **Part 1** (tasks A1, A2) carry-over from M4; **Part 2** (tasks S1, S2, S3) the forwarder spike, ending with a decision record that Claude reviews;
> **Part 3** (tasks B1 to B8) the build. Do NOT start part 3 until the user tells you the spike decision was confirmed.

**Goal:** an opt-in "Live Lens": a local VPN that shows which app uses data right now and which servers it contacts, entirely on the device, with a
Lens screen, a foreground service, a short rolling domain history, and correct totals (no double counting of VPN traffic).

**Architecture:** a pure-JVM packet forwarder core (`:feature:lens`, package `…lens.core`: parsers, flow table, TCP/UDP relay) tested against an in-process fake
TUN; Android glue (`LensVpnService`, `AndroidLensController`, owner-UID resolver, socket protector); a Compose Lens screen in `:feature:lens`; domain hits stored in
the shared Room database (version 4). The forwarder language (Kotlin or native) is decided by the spike in part 2; this plan assumes Kotlin and defines the gate
that would change it.

**Tech Stack:** unchanged (Kotlin 2.4.20, AGP 9.4.0, Compose BOM 2026.09.00 with Material 3 `1.5.0-alpha29`, Room 2.8.5, DataStore, WorkManager, Robolectric 4.17,
Glance, Bouncy Castle). No new dependency is planned; the spike may recommend a native library, and that needs the user's approval first.

**Read first:** `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` **section 12** (this plan implements it one to one; where plan and contract differ the
contract wins; if they truly contradict each other, stop and tell the user), section 4 (the Lens contract), section 9.4 (usage semantics, must not change),
backend spec section 7 and 13, frontend spec section 6.4.

## Global Constraints

- minSdk 29, compileSdk 37, targetSdk 36, Java/Kotlin 17 (**no Java 18+ APIs**). No version bumps. Module rules: `:core:engine` pure Kotlin; `:ui:design` has no `:core:*` dependency; Room types never leave `:core:data`; `…lens.core` has no Android classes.
- Package root `io.github.khaledbahaaeldin.emberbyte`; `$PKG` below means `io/github/khaledbahaaeldin/emberbyte`.
- **Absolute git rules (earlier waves lost files to subagents):** never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`; to change branches use `git switch <branch>` only; never touch anything under `docs/superpowers/` (Claude's files); stage only your files by explicit path; never push, merge, force or skip hooks. Copy this rule block verbatim at the top of EVERY implementer and tester brief. After every subagent returns run `git status --short` and `git diff --stat HEAD -- docs/superpowers`; if anything under `docs/superpowers` changed, or `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is not 1, STOP and tell the user.
- Test-first. Tests that encode old behaviour are named in the task that changes them; change exactly those and list them. Never delete or overwrite a test; never `@Ignore`. A task with no production code and no test in its diff is not done.
- **Privacy rules of Lens (a violation is a Critical defect):** payload bytes are never stored, logged, or kept after the packet is relayed; logs never contain IP addresses, domain names or UIDs of other apps (log counts and sizes only); only (uid, name, hour, bytes, lastSeen) are stored; Lens is off by default and never starts without the user's tap and the system consent dialog.
- Branch `m5-live-lens` (created from `main`, which includes M1 to M4). Identity `Khaledbahaaeldin` / `khaled.bahaaeldin@aiu.edu.eg`; commit messages explain WHY and end with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Gate after every task: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`; the last task uses `--no-build-cache`. Baseline on `main`: 965 tests (`:app` 400, `:core:data` 324, `:core:engine` 125, `:ui:design` 116); 0 lint errors; `:app` lint warnings: 8 code/config warnings plus up to 3 "newer version available" notices on build files (state the exact list with ids and locations, mark notices; add no code warning).
- Reports: quote only what you ran; real test names; "not run"/"CANNOT-VERIFY" when true; never "tester PASS" without a tester report file (write "self-verified, no separate tester report"); list EVERY deviation and EVERY pre-existing test changed; no "immediately" for anything that takes a poll or a refresh cycle; no "transactional" for anything that is not one database transaction.
- UI, VPN, permission, lifecycle and network work is proven on the emulator by its tester with quoted evidence (clean install; restore every device setting; always `adb -s emulator-5554`, a physical phone may be attached and must never be touched; open and describe every screenshot you cite).

---

# PART 1: carry-over from M4

### Task A1: Two tests that really can fail (= task N1 of the unused M4 wave 3)

Execute task **N1** of `docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md` exactly as written there (brief:
`PLAN=docs/superpowers/plans/2026-10-10-emberbyte-m4-fix-wave-3.md; N=N1`, the same `awk` command as for any other task). In short: make
`AlertEvaluatorTest.concurrent_evaluate_calls_return_exactly_one_notice` deterministic (it still passed in 2 of 3 full `:core:data` runs with the `Mutex` removed; prove it
fails every time without the lock, quoting the commands), and add a Robolectric `MainActivityNewIntentTest` for the one line that fixes the onboarding link
(`setIntent(launchIntentFor(...))` in `MainActivity.onNewIntent`: reverting it must fail the new test). Commit message from that task. The M4 rules of that file apply.

### Task A2: History opens on the newest period (= task N2 of the unused M4 wave 3)

Execute task **N2** of the same file exactly as written (`N=N2`): after restoring about 2 months of history the Day and Week charts opened scrolled to the OLDEST period; they must open on the
newest, keep that when switching Day/Week/Month, not jump when a bar is selected, and leave Home's 7-bar row unchanged. Commit message from that task. (Task N3 of that file is NOT run; M5's own final task replaces it.)

---

# PART 2: the forwarder spike

The spike answers one question with numbers: **can a pure-Kotlin userspace forwarder carry a phone's traffic correctly and cheaply enough, or does Lens need a native
forwarder?** Its code is not thrown away: the parsers (S1) and the forwarder core (S2) are the production base if Kotlin passes. Read open-source VPN firewalls for
DESIGN LESSONS only (for example NetGuard, GPL-3.0; Intra, Apache-2.0): **do not copy or translate their code**; Emberbyte is GPL-3.0-or-later and code under GPL-3.0-only
would change that. Record what you read and what you learned (no code) in the decision record.

### Task S1: Packet and name parsers (`…lens.core`)

**Files:**
- Modify: `feature/lens/build.gradle.kts` (add `testImplementation(libs.junit)`; no Android dependency is needed by `…lens.core`)
- Create: `feature/lens/src/main/kotlin/$PKG/lens/core/{PacketParser,ParsedPacket,Checksums,DnsParser,SniParser}.kt`
- Test: `feature/lens/src/test/kotlin/$PKG/lens/core/{PacketParserTest,ChecksumsTest,DnsParserTest,SniParserTest,PacketBuilder}.kt`

**Interfaces (contract 12.2):**
```kotlin
sealed interface ParsedPacket {
    val src: InetSocketAddress; val dst: InetSocketAddress; val protocol: Int          // 6 TCP, 17 UDP
    val payloadOffset: Int; val payloadLength: Int                                    // into the buffer that was parsed
    data class Tcp(override val src: InetSocketAddress, override val dst: InetSocketAddress, val seq: Long, val ack: Long, val flags: Int,
                   val window: Int, val mss: Int?, override val payloadOffset: Int, override val payloadLength: Int) : ParsedPacket
    data class Udp(override val src: InetSocketAddress, override val dst: InetSocketAddress, override val payloadOffset: Int, override val payloadLength: Int) : ParsedPacket
}
class PacketParser { fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? }          // null for anything that is not a valid IPv4/IPv6 TCP/UDP packet
object Checksums { fun ipv4Header(buf: ByteArray, off: Int, headerLen: Int): Int; fun transport(buf: ByteArray, ipOff: Int, ipv6: Boolean, protocol: Int, segOff: Int, segLen: Int): Int }
data class DnsAnswer(val name: String, val address: InetAddress, val ttlSeconds: Long)
object DnsParser { fun answers(payload: ByteArray): List<DnsAnswer> }
object SniParser { fun serverName(firstRecord: ByteArray): String? }
object TcpFlags { const val FIN = 0x01; const val SYN = 0x02; const val RST = 0x04; const val PSH = 0x08; const val ACK = 0x10 }
```

- [ ] **Step 1: Write the failing tests.** In the test sources write `PacketBuilder` (test helper, also reused by S2): builds valid IPv4 and IPv6 packets carrying TCP or UDP with correct checksums from `(src, dst, flags, seq, ack, payload, options)`; helpers `dnsResponse(id, question, answers)` (encode names with and without compression pointers) and `clientHello(serverName, extensions)` with this exact layout: record `16 03 01 <len2>`; handshake `01 <len3>`; `03 03` + 32 random bytes; session-id length 1 byte + id; cipher-suite length 2 bytes + suites (`13 01`); compression length 1 byte + `00`; extensions length 2 bytes + extensions, each `<type2><len2><data>`; the SNI extension (`00 00`) data = `<listLen2> 00 <nameLen2> <name>`. Then:
  * `PacketParserTest`: an IPv4 TCP SYN with an MSS option parses to the right addresses, ports, seq, flags `SYN`, window, `mss`; an IPv6 TCP segment likewise; a UDP packet (v4 and v6) gives payload offset/length; an IPv4 packet WITH IP options (IHL 6) is parsed at the right payload offset; TCP data offset above 5 is honoured; **dropped (null):** wrong IP version, length field larger than the buffer, total length shorter than the header, IPv4 header checksum wrong, TCP/UDP checksum wrong (not for UDP checksum 0 on IPv4, which means "none"), fragmented IPv4 (MF flag or fragment offset non-zero), protocol ICMP/other, truncated TCP/UDP header, IPv6 with an extension header chain it cannot skip (hop-by-hop, routing and destination options headers ARE skipped; fragment header is dropped); an empty buffer and 1-byte buffer give null without an exception; a fuzz test feeds 20,000 random byte arrays (seeded) and asserts no exception and that every non-null result lies inside the buffer.
  * `ChecksumsTest`: RFC 1071 examples: the IPv4 header `45 00 00 73 00 00 40 00 40 11 b8 61 c0 a8 00 01 c0 a8 00 c7` has checksum field `0xb861` and `ipv4Header` over it with the checksum zeroed returns `0xb861`; odd-length payload; a pseudo-header checksum for IPv6.
  * `DnsParserTest`: the hex message `1234818000010001000000000765 78616d706c6503636f6d0000010001c00c00010001 0000012c00045db8d822` (remove spaces) gives exactly one answer `example.com` -> `93.184.216.34`, TTL 300; an AAAA answer; a CNAME chain (`www.example.com` CNAME `cdn.example.net` then A) yields the address with the QUESTION name AND the CNAME names recorded (`answers` returns one entry per address with `name` = the first name in the chain, which is the name the app asked for); compression pointers inside answers; a pointer loop and a pointer past the end terminate without an exception; truncated message, zero answers, a query (QR bit 0), RCODE != 0, and a name longer than 255 bytes give `emptyList()`; TTL 0 is returned as 0 (the caller clamps).
  * `SniParserTest`: a ClientHello with SNI `example.com` returns it; GREASE extensions (`0a0a`) before and after, many extensions, SNI not first, two names in the list (first host_name type 0 wins), upper-case input returns lower case; **null:** no SNI extension, truncated record, a record that is not a handshake, handshake type not ClientHello, an SNI longer than 255 or containing non-ASCII bytes or spaces, and a ClientHello split across two records (only the first record is given: returns null); 5,000 random byte arrays do not throw.
- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:lens:testDebugUnitTest` → FAIL (classes missing).
- [ ] **Step 3: Implement** the five files. Key rules: all reads are bounds-checked before use (a helper `ByteBuffer.getU8/getU16/getU32` returning `-1` or throwing a private `Malformed` that `parse` converts to `null`); IPv4 header length is `IHL * 4` (>= 20); fragments are dropped (documented limit); IPv6: fixed 40-byte header then skip extension headers 0, 43, 60 (length in 8-byte units + 8) until TCP (6) or UDP (17); verify checksums with `Checksums`; `src`/`dst` are `InetSocketAddress(InetAddress.getByAddress(bytes), port)` (never resolve); `DnsParser` limits: at most 64 answers, at most 16 pointer jumps per name; `SniParser` accepts only host_name entries made of `[A-Za-z0-9.-_]`, lower-cases the result.
- [ ] **Step 4: Run to verify they pass** — gate green.
- [ ] **Step 5: Commit** `feat(lens): parse IP, TCP, UDP, DNS and TLS server names without trusting the input`.

---

### Task S2: The forwarder core (`Forwarder`, flow table, TCP and UDP relay) on the JVM

**Files:**
- Create: `feature/lens/src/main/kotlin/$PKG/lens/core/{Forwarder,FlowTable,TcpFlow,UdpFlow,TunDevice,SocketProtector,OwnerResolver,FlowListener,PacketWriter}.kt`
- Test: `feature/lens/src/test/kotlin/$PKG/lens/core/{FakeTun,TcpClientSim,ForwarderTcpTest,ForwarderUdpTest,ForwarderLimitsTest}.kt`

**Interfaces:** the ones in contract 12.2 (`TunDevice`, `SocketProtector`, `OwnerResolver`, `FlowListener`, `Forwarder(tun, protector, owner, listener, clock)` with `run()` blocking until `stop()` or the TUN closes; `stop()` is idempotent and `run()` returns within 2 seconds of it). Add `PacketWriter` (builds the IP/TCP/UDP packets the forwarder writes back; reuse the S1 builder logic in `main`, not the test helper) and `FlowInfo(flowId: Long, uid: Int, protocol: Int, local: InetSocketAddress, remote: InetSocketAddress, openedAt: Instant)`.

**Design (the invariants the tests check):**
1. One reader thread reads the TUN; per-flow work for TCP/UDP uses `java.nio` selectors on ONE network thread (no thread per flow); writes to the TUN are serialised.
2. **TCP flow.** On the app's SYN: create the flow, open a non-blocking protected `SocketChannel` to the destination and connect; when connected send SYN-ACK (MSS = `mtu - 40` for IPv4, `mtu - 60` for IPv6; advertised window 65535, no window scaling; if the connection fails answer with RST+ACK). Client data -> write to the socket (buffered at most 256 KiB, if full stop acknowledging: the app's TCP slows down); socket data -> segments of at most MSS toward the app, limited by the app's advertised window and by 256 KiB in flight; **unacknowledged segments are retransmitted** after an RTO of 1 s (doubling, at most 5 attempts, then RST); ACKs from the app advance the send window; FIN from the app -> half-close the socket output, ACK; EOF from the server -> FIN to the app; RST either way tears the flow down; both FINs acknowledged -> remove after 30 s; idle flows are closed after 5 minutes; duplicate and out-of-window segments are re-acknowledged and otherwise ignored; sequence arithmetic is modulo 2^32.
3. **UDP flow.** One protected `DatagramChannel` per (src, dst) pair; datagrams forwarded both ways; responses are written to the TUN as UDP packets with the original addressing swapped; idle timeout 60 s (10 s when the destination port is 53).
4. **Limits.** At most 1024 flows; when full, the idle-longest flow is closed first and a counter `evictedFlows` increments. Every exception in a flow closes that flow only; an exception in the loop is counted (`errors`) and never ends `run()`.
5. **Observation.** `FlowListener.onFlowOpened(FlowInfo)` once per flow (the owner uid comes from `OwnerResolver.uidOf`, called once per flow, results cached; `-1` when unknown), `onBytes(flowId, rx, tx)` for payload bytes in each direction as they are relayed (rx = toward the app), `onName(flowId, name)` when the SNI of the first client record is parsed (TCP, any port) and when a DNS answer is seen (UDP, source port 53, names reported with `flowId = -1` as `onDnsAnswer` via a second method `onDns(answers: List<DnsAnswer>)`), `onFlowClosed(flowId)`. Payload is never retained beyond the relay buffers.

- [ ] **Step 1: Write the failing tests.** `FakeTun` = two `LinkedBlockingQueue<ByteArray>` (app to forwarder, forwarder to app) implementing `TunDevice`, `close()` unblocks `read`. `TcpClientSim` = a tiny TCP client over `FakeTun` (active open, sends data with correct seq, ACKs what it receives, FIN/RST, optional drop probability for packets from the forwarder with a seeded `Random`). A `LoopbackProtector` that does nothing. Local `ServerSocket`s (IPv4 `127.0.0.1` and IPv6 `::1` where the JVM supports it, otherwise skip those tests with `Assume`) provide echo, discard and "send N bytes then close" servers. Tests (every one has a 10 s timeout):
  * `ForwarderTcpTest`: three-way handshake completes and the first data returns echoed; **10 MB** upload+download through an echo server arrive byte-identical (compare SHA-256); the same with 5% of downlink packets dropped (retransmission) and a **2 MB** transfer; half-close (client FIN, server still sends, then FIN); server closes first; server RST (`SO_LINGER 0`) becomes RST at the client; connecting to a closed port gives RST+ACK to the SYN; 200 parallel flows each echoing 20 KiB all succeed; sequence numbers wrap (start the client at `0xFFFFFF00`); duplicate segment is re-ACKed and not delivered twice; an out-of-order segment is not delivered early; a client window of 0 pauses the server data and resumes when it reopens; IPv6 flow; the first client record with a ClientHello triggers `onName`; bytes reported by `onBytes` equal the payload sizes in both directions; idle timeout closes a flow (inject a `Clock`/ticker so the test does not wait 5 minutes).
  * `ForwarderUdpTest`: a UDP echo round trip (v4 and v6); a DNS answer relayed from a local fake DNS server triggers `onDns` with the right answer; replies from a different source are not delivered to the flow; idle timeout (60 s and 10 s for port 53, injected clock); ICMP and malformed packets are dropped without ending `run()`.
  * `ForwarderLimitsTest`: with the limit set to 8 (constructor parameter for tests) opening a 9th flow evicts the idle-longest one and increments `evictedFlows`; garbage packets (20,000 random, seeded) interleaved with a normal transfer do not break it and increment `errors` or are silently dropped; `stop()` makes `run()` return within 2 s even while a flow is blocked and releases every socket (assert no open channels via a counting `SocketProtector`).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement** to the design above (about 1,500 lines is normal). Keep each class under about 400 lines (`TcpFlow` holds the state machine, `FlowTable` the maps and eviction, `Forwarder` the loops). Use direct `ByteBuffer`s from a small pool for packets (no allocation per packet in the hot path); never log addresses or names (counts only).
- [ ] **Step 4: Run to verify they pass** — all new tests green on three consecutive runs of `./gradlew :feature:lens:testDebugUnitTest --rerun-tasks` (report the three results; a flaky test is a failure); gate green.
- [ ] **Step 5: Commit** `feat(lens): relay TCP and UDP flows in userspace and report each flow's bytes and names`.


### Task S3: Android glue, device measurement and the decision record (then STOP)

**Files:**
- Create: `feature/lens/src/main/AndroidManifest.xml` (the `LensVpnService` declaration: `android:permission="android.permission.BIND_VPN_SERVICE"`, `android:exported="false"`, intent filter `android.net.VpnService`, `android:foregroundServiceType="specialUse"` with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property; the `INTERNET` and `FOREGROUND_SERVICE` permissions go into the APP manifest in B3, and for the spike into the debug manifest below)
- Create: `feature/lens/src/main/kotlin/$PKG/lens/android/{LensVpnService,ParcelTunDevice,VpnSocketProtector,ConnectivityOwnerResolver}.kt`
- Create (DEBUG source set only, never shipped): `app/src/debug/AndroidManifest.xml` (adds `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, the `LensSpikeActivity` and the `LensSpikeReceiver`), `app/src/debug/kotlin/$PKG/lens/spike/{LensSpikeActivity,LensSpikeReceiver}.kt`
- Create: `docs/spikes/lens-forwarder.md` (decision record), `docs/spikes/measure-lens.sh` (the measurement script, runnable by a person on a real phone too)
- Test: `feature/lens/src/test/kotlin/$PKG/lens/android/ConnectivityOwnerResolverTest.kt` (Robolectric, with a shadow `ConnectivityManager`)

**Interfaces:** `LensVpnService : VpnService` with `companion { fun startIntent(context): Intent; fun stopIntent(context): Intent }`; `ParcelTunDevice(pfd: ParcelFileDescriptor) : TunDevice` (blocking `FileInputStream`/`FileOutputStream` on the descriptor; `close()` closes both and unblocks); `VpnSocketProtector(service: VpnService) : SocketProtector`; `ConnectivityOwnerResolver(cm: ConnectivityManager) : OwnerResolver` (`uidOf(protocol, local, remote)` = `cm.getConnectionOwnerUid(protocol, local, remote)`, `-1` on any exception or `Process.INVALID_UID`, wrapped in `try/catch (SecurityException/IllegalArgumentException)`).

- [ ] **Step 1: Test and build.** `ConnectivityOwnerResolverTest`: unknown owner (`INVALID_UID`) gives -1; an exception gives -1; a known uid is returned for TCP (6) and UDP (17). Implement the glue: `LensVpnService.onStartCommand(ACTION_START)` builds the TUN exactly as contract 12.3 says (session "Emberbyte Lens", MTU 1400, `10.7.0.2/32`, `fd7a:115c:a1e0::2/128`, routes `0.0.0.0/0` and `::/0`, no DNS server, `setBlocking(true)`), calls `startForeground` with a minimal ongoing notification on a channel `lens` (create it here), starts `Forwarder.run()` on a dedicated thread, and `ACTION_STOP` stops the forwarder (join <= 2 s), closes the TUN and calls `stopSelf()`; `onRevoke()` does the same. The debug `LensSpikeActivity` calls `VpnService.prepare(this)` and shows the system dialog; the debug `LensSpikeReceiver` handles `adb shell am broadcast -a io.github.khaledbahaaeldin.emberbyte.debug.LENS_START` / `LENS_STOP` / `LENS_DUMP` (DUMP logs counters only: flows open, flows evicted, errors, tun rx/tx bytes, never addresses or names). A `FlowListener` that only counts is enough for the spike.
- [ ] **Step 2: Gate** — green (the debug source set compiles; the release APK contains no `LensSpike*` class: `unzip -l app/build/outputs/apk/release/*.apk` is not possible without signing, so check `./gradlew :app:compileReleaseKotlin` and `grep -r LensSpike app/build/intermediates/*release*` returns nothing; report what you ran).
- [ ] **Step 3: Device measurement** (AVD `Violet_API_36`, `adb -s emulator-5554`, clean install of the debug build, grant nothing special; the host serves files on `10.0.2.2` with `python -m http.server 8000` from a folder that contains a 100 MB random file `big.bin` and a 100 KB `small.bin`). Run `docs/spikes/measure-lens.sh` (write it: it takes the serial and the host address, runs each step, and prints one line per result). Steps, each repeated 3 times, baseline (Lens off) and Lens on:
  1. **Correctness suite** with `adb shell curl`: HTTP small file (bytes identical, compare `sha256sum`), HTTP 100 MB file (identical), HTTPS to a public site (`https://example.com`, status 200), a name lookup through DNS (the same HTTPS request exercises it; additionally `adb shell nslookup example.com` or `getent` if present), 200 parallel small HTTP downloads from the host (all succeed), a download interrupted by `kill` and restarted (`curl -C -`), an upload of 20 MB (`curl -T`) to a tiny python PUT server on the host, a UDP exchange (DNS counts; add a UDP echo to the host script if you can), IPv6 (CANNOT-VERIFY on the emulator if it has no IPv6 route: say so; the JVM tests cover the logic), `ping` (expected to fail while Lens is on: record it), toggling Wi-Fi off/on and airplane mode on/off while Lens runs (Lens must come back or stop cleanly, record which), `adb shell am force-stop` of the app while Lens is on (the VPN must disappear: `dumpsys connectivity | grep -i vpn`).
  2. **Performance:** `curl -o /dev/null -w "%{speed_download}"` of `big.bin` baseline vs Lens on; process CPU time of Emberbyte for the Lens run (`/proc/<pid>/stat` fields 14+15 before and after, clock ticks per second = `getconf CLK_TCK`, 100 on Android), memory (`dumpsys meminfo io.github.khaledbahaaeldin.emberbyte`, total PSS before, during and after), flow table size from `LENS_DUMP` after the 200-flow test (must return to ~0 after 60 s of idleness).
  3. **Platform facts to record:** does `startForeground` with type `specialUse` work for the VPN service on API 36 (`dumpsys activity services io.github.khaledbahaaeldin.emberbyte | grep -i foreground`)? How many flows had a known owner uid (`LENS_DUMP` counts of `uid == -1` versus total)? Does the system show the key icon, and does the consent dialog appear only once?
  4. **Battery:** the emulator cannot measure it. `measure-lens.sh` has a `--battery` mode (a person on a real phone runs it for 30 minutes of YouTube and records `dumpsys batterystats --charged` before/after); mark it NOT RUN here.
- [ ] **Step 4: Write the decision record** `docs/spikes/lens-forwarder.md`: environment (emulator image, host CPU as far as known), the exact commands and raw numbers, the correctness table (PASS/FAIL per item), the design lessons from reading open-source VPN firewalls (names only, NO code, and say you copied nothing), the licence note, and a section `## Decision` with exactly one of: **`KOTLIN CONFIRMED`** when ALL of these hold: every correctness item passes (except the documented ones: ping, IPv6 on the emulator), Lens-on throughput is at least 40 Mbit/s (5 MB/s) AND at least 60% of the baseline, CPU is at most 2.0 CPU-seconds per 100 MB transferred, no process crash or ANR in `logcat`, and flows return to near zero when idle; or **`NATIVE NEEDED`** with the failing numbers and a proposal (language, library, licence, NDK/CI impact) for the user to approve. Also list open risks (vendor ROM `getConnectionOwnerUid`, battery on a real phone, IPv6 on a real network).
- [ ] **Step 5: Commit** `feat(lens): VPN service glue and the forwarder spike with its decision record`.
- [ ] **Step 6: STOP.** Print the decision section and the key numbers, then say: **"The Lens spike is done: decision <KOTLIN CONFIRMED|NATIVE NEEDED>. Waiting for Claude to review docs/spikes/lens-forwarder.md before part 3."** Do not start part 3. If the decision is `NATIVE NEEDED`, do not write any native code: wait for the user.

---

# PART 3: the build (only after the user confirms the spike decision)

### Task B1: Lens history storage (database version 4) and retention

**Files:**
- Modify: `core/data/src/main/kotlin/$PKG/data/db/{Entities,EmberbyteDatabase,Migrations,EmberbyteDatabases}.kt`, `core/data/src/main/kotlin/$PKG/data/DataStoreSettingsRepository.kt` (+`SettingsRepository.kt` doc)
- Create: `core/data/src/main/kotlin/$PKG/data/db/LensDao.kt`, `data/lens/{LensHistoryStore,RoomLensHistoryStore}.kt`, `data/fake/InMemoryLensHistoryStore.kt`; generated `core/data/schemas/.../4.json`
- Test: `core/data/src/test/kotlin/$PKG/data/db/Migration4Test.kt` (reuse the `SchemaFile` helper of `Migration3Test`: the old database is built from `3.json`), `data/lens/{LensHistoryStoreContractTest,InMemoryLensHistoryStoreTest,RoomLensHistoryStoreTest}.kt`, `data/SettingsLensHistoryTest.kt`

**Interfaces:**
```kotlin
data class LensDomainHit(val uid: Int, val domain: String, val hourStart: Instant, val bytes: Long, val lastSeen: Instant)
interface LensHistoryStore {
    suspend fun add(hits: List<LensDomainHit>)                       // ADDS bytes to an existing (uid, domain, hourStart) row, lastSeen = max; atomic like addMinute
    suspend fun clear()
    suspend fun prune(olderThan: Instant)                            // deletes rows with hourStart < olderThan
    fun observeTop(uid: Int, since: Instant, limit: Int): Flow<List<LensDomainHit>>     // summed per domain over hourStart >= since, by bytes desc
    suspend fun count(): Long
}
object LensHistoryStores { fun create(context: Context): LensHistoryStore }
val LENS_HISTORY_CHOICES: List<Int> = listOf(0, 1, 24, 72, 168)       // hours; 0 = keep nothing
```
Settings: `DataStoreSettingsRepository` coerces `lensHistoryHours` to the nearest allowed choice on read and write (24 when unreadable).

- [ ] **Step 1: Tests first.** `Migration4Test`: the version-3 file built from `3.json` (with a plan, a budget and a usage row) opens with Room v4 + `MIGRATION_1_2..3_4` keeping every row, the new table accepts rows, and the path 1 to 4 works. The store contract tests (both stores): additive `add` (two adds to one key sum the bytes, keep the later `lastSeen`), `clear`, `prune` boundary (a row at exactly `olderThan` stays), `observeTop` (sum per domain across hours, order by bytes, limit, uid isolation, `since` boundary), an IP-literal domain string round-trips. Settings test: values are coerced to the allowed choice with the smallest absolute difference (ties go to the smaller choice); test 5 -> 1, 2 -> 1, 12 -> 1, 13 -> 24, 48 -> 24, 49 -> 72, 100 -> 72, 150 -> 168, 1000 -> 168, -3 -> 0.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** Entity `LensDomainHitEntity` table `lens_domain_hit` with primary key (`uid`, `domain`, `hourStart`), columns `bytes`, `lastSeen`, index on `hourStart`; `LensDao` with `@Transaction addAll` (read-modify-write per key inside one transaction), `deleteAll`, `deleteBefore`, `observeTop` (`SELECT uid, domain, MAX(hourStart) AS hourStart, SUM(bytes) AS bytes, MAX(lastSeen) AS lastSeen FROM lens_domain_hit WHERE uid = :uid AND hourStart >= :since GROUP BY domain ORDER BY bytes DESC LIMIT :limit` mapped to a small result class); version 4 and `MIGRATION_3_4` with SQL copied from the generated `4.json`; register it in `EmberbyteDatabases`; `1.json`, `2.json`, `3.json` unchanged.
- [ ] **Step 4: Run to verify they pass** — gate green.
- [ ] **Step 5: Commit** `feat(data): store which server names each app contacted, for a few hours only`.

---

### Task B2: `LensEngine` (flow table aggregation, rates, names, domain hits)

**Files:**
- Create: `feature/lens/src/main/kotlin/$PKG/lens/core/{LensEngine,NameCache,RateTracker,LensModels}.kt` (`LensModels.kt` holds the public types of contract section 4: `LensState`, `LensConflict`, `LensApp`, `LensDomain`, `LensSnapshot`)
- Test: `feature/lens/src/test/kotlin/$PKG/lens/core/{LensEngineTest,NameCacheTest,RateTrackerTest}.kt`

**Interfaces:**
```kotlin
class NameCache(clock: Clock) { fun put(answers: List<DnsAnswer>); fun nameFor(address: InetAddress): String? }     // ttl clamp: max(ttl, 60 s), at most 10 min; at most 4096 entries (oldest out)
class LensEngine(clock: Clock, appLabel: (uid: Int) -> String?, packageOf: (uid: Int) -> String?) : FlowListener {
    fun snapshot(): LensSnapshot                                   // called once per second by the controller
    fun drainHits(): List<LensDomainHit>                           // aggregated since the last drain, hourStart = UTC hour of the bytes
    fun tunCounters(): TunCounters                                 // see contract 12.3 (set by the forwarder glue through onTunBytes)
    fun onTunBytes(rx: Long, tx: Long)
}
```
- [ ] **Step 1: Tests first.** `NameCacheTest`: TTL 0 is kept 60 s, TTL 86400 is capped at 10 min, expiry by the injected clock, an address with two names returns the most recent, the 4097th entry evicts the oldest. `RateTrackerTest`: bytes in one second give that rate, the EMA (alpha 0.5) decays to ~0 after silence, a second of no data after traffic halves the rate. `LensEngineTest` (scripted `FlowListener` calls, a fake clock): (a) two flows of uid 10001 and one of uid 10002: the snapshot lists apps sorted by `rxBps + txBps` descending and only apps with traffic in the last 30 s; (b) a flow with SNI `video.example` and another with only an address that a DNS answer named `cdn.example.net` and a third with neither: `topDomains` are `video.example`, `cdn.example.net` and the IP literal, sorted by bytes, at most 5, window 15 minutes (older bytes drop out); (c) SNI wins over the DNS name for the same flow; (d) `drainHits()` aggregates per (uid, name, UTC hour), a second drain right away is empty, bytes across an hour boundary are split by the time they were relayed; (e) uid `-1` shows as the pseudo app `uid:0` ("Unknown") and still counts; (f) `onFlowClosed` keeps the flow's bytes in the aggregates; (g) 1,000 flows over 60 simulated minutes keep the engine's memory bounded (the number of remembered names is capped at 500 per app and flows are forgotten when closed; assert sizes through `internal` counters); (h) `tunCounters()` sums `onTunBytes`, keeps a stable `sessionId` per engine instance and a different one for a new engine.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** The engine lives entirely on the forwarder/controller threads: protect shared state with one lock or confine it to a single-thread executor (state your choice); `appLabel`/`packageOf` are injected so the engine has no Android dependency. Names are lower-cased; the IP literal form is `address.hostAddress` without brackets (strip the `%scope` suffix of IPv6).
- [ ] **Step 4: Run to verify they pass** — gate green.
- [ ] **Step 5: Commit** `feat(lens): turn relayed flows into per-app rates, top server names and hourly hits`.

---

### Task B3: `AndroidLensController`, `LensVpnService`, conflicts, notification, manifest, wiring

**Files:**
- Modify/Create: `feature/lens/src/main/kotlin/$PKG/lens/android/{LensVpnService,AndroidLensController,LensNotification,ConflictDetector}.kt`, `feature/lens/src/main/kotlin/$PKG/lens/LensController.kt` (the public interface of contract section 4), `app/src/main/AndroidManifest.xml` (`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`; remove the debug-only duplicates), `app/src/main/res/values/strings.xml`, `core/data/.../AndroidPermissionRepository.kt` (`vpnConsentGranted`), `app/.../AppGraph.kt`, `app/.../sampler/NotificationChannels.kt` (the `lens` channel, low importance), `app/.../MainActivity.kt`/`nav` (the Stop action intent)
- Test: `feature/lens/src/test/kotlin/$PKG/lens/android/{AndroidLensControllerTest,ConflictDetectorTest,LensNotificationTest}.kt`, `app/src/test/.../NoNetworkOutsideLensTest.kt`, `core/data/src/test/.../AndroidPermissionRepositoryTest.kt` (ADD)

**Interfaces:** the contract section 4 types and `LensController` exactly (`state: StateFlow<LensState>`, `observeSnapshot()`, `prepareConsentIntent()`, `suspend start()`, `suspend stop()`, `suspend clearHistory()`); `class AndroidLensController(context, history: LensHistoryStore, settings: SettingsRepository, scope: CoroutineScope, clock: Clock, conflicts: ConflictDetector, bridge: LensServiceBridge)` where `LensServiceBridge` is a small interface the service implements so the controller can be unit-tested without a service (`startService(): Boolean`, `stopService()`, `engine: LensEngine?`); `ConflictDetector { fun detect(): LensConflict? }` with `AndroidConflictDetector(cm, contentResolver, ownPackage)`.

- [ ] **Step 1: Tests first** (the controller with a fake bridge, fake detector, test dispatcher and `Clock`):
  * state machine: `Off` -> `start()` without consent (`prepareConsentIntent() != null`) -> `NeedsConsent` and nothing starts; with consent -> `Starting` -> `Running(since)` when the bridge reports the TUN is up; `start()` while `Running` is a no-op returning `Success`; `stop()` -> `Off` and returns only after `bridge.stopService()` completed (the fake records ordering); the bridge failing -> `Error(message)` with no leaked state and `start()` can be tried again; `onRevoke` simulated by the bridge -> `Off`.
  * conflicts: `OTHER_VPN_ACTIVE` when the detector says so (state `Conflict`, the bridge is NOT started); `ALWAYS_ON_VPN_BLOCKING` likewise; detector tests (`ConflictDetectorTest`, Robolectric): a network with `TRANSPORT_VPN` that is not ours -> `OTHER_VPN_ACTIVE`; `Settings.Secure` `always_on_vpn_app` = another package and `always_on_vpn_lockdown` = 1 -> `ALWAYS_ON_VPN_BLOCKING`; our own VPN network or our own package never conflicts; unreadable settings (SecurityException) -> no conflict.
  * snapshots: `observeSnapshot()` emits nothing unless `Running`; while `Running` it emits the engine's snapshot every second (test scheduler) and stops emitting after `stop()`.
  * history: every 60 s the controller drains the engine hits into `history.add` when `Settings.lensHistoryHours > 0`, never when 0; `prune` runs hourly and when the setting changes; `clearHistory()` calls `history.clear()` AND clears the engine's pending hits.
  * `LensNotificationTest`: channel `lens` low importance, ongoing, title "Lens is on", text "<n> apps active", a Stop action whose `PendingIntent` is an explicit service/receiver intent, tap opens `emberbyte://lens`.
  * `NoNetworkOutsideLensTest` (JVM, reads the source tree): no file under `app/src/main`, `core/*/src/main`, `ui/design/src/main` mentions `java.net.Socket`, `ServerSocket`, `URL(`, `HttpURLConnection`, `OkHttp`, `DatagramSocket`, `SocketChannel` (the only networking is under `feature/lens/src/main/kotlin/**/lens/core` and `lens/android`); the test fails with the offending file names.
  * `AndroidPermissionRepositoryTest`: `vpnConsentGranted` is false when `VpnService.prepare` returns an intent, true when it returns null (shadow it).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** `LensVpnService` becomes the production service of contract 12.3 (reuse the S3 glue; remove the debug-only receiver paths from release; the debug `LensSpikeReceiver` may stay in `src/debug`): it owns the `Forwarder` thread and the `LensEngine`, implements `LensServiceBridge` through a process-wide holder (`LensServiceHolder`, an `AtomicReference`), calls `setUnderlyingNetworks` with the current default non-VPN network and updates it on network changes (`registerNetworkCallback` for `NOT_VPN` + `INTERNET`), publishes engine counters, and on any fatal exception tears down, marks state `Error`, and stops itself. `AndroidLensController` is a singleton in `AppGraph` (`val lens: LensController`). Strings: notification texts and channel name "Live Lens" / description "Shows while Lens is running". The manifest merges the service from `:feature:lens` (check the merged manifest contains `BIND_VPN_SERVICE`, `specialUse` and exactly the permissions `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` that are new).
- [ ] **Step 4: Run to verify they pass** — gate green; `lintDebug` adds no code warning (a `ForegroundServiceType` or `MissingPermission` finding is fixed, not suppressed, unless the reason is written in the report). **Tester (emulator):** from the debug build start Lens through the new controller path is not possible without the UI of B5: here only verify with the debug harness (`am broadcast` of S3) that the production service starts and stops, the notification channel `lens` exists, the merged manifest is as stated, and `dumpsys connectivity | grep -i vpn` shows the VPN only while Lens runs.
- [ ] **Step 5: Commit** `feat(lens): start and stop Lens safely, refuse to start next to another VPN, and say so`.

---

### Task B4: No double counting while Lens runs

**Files:**
- Modify: `core/data/src/main/kotlin/$PKG/data/sampler/SamplerEngine.kt`, `core/data/src/main/kotlin/$PKG/data/source/Sources.kt` (+`LensCounters`, `TunCounters`), `core/data/src/main/kotlin/$PKG/data/android/ConnectivityNetworkKindSource.kt`, `app/.../AppGraph.kt`, `feature/lens/.../LensEngine.kt` (implements `LensCounters` via an adapter)
- Test: `core/data/src/test/kotlin/$PKG/data/sampler/SamplerEngineLensTest.kt`, `core/data/src/test/kotlin/$PKG/data/android/UnderlyingNetworkKindTest.kt`

**Interfaces (contract 12.3):** `interface LensCounters { fun read(): TunCounters }`, `data class TunCounters(val sessionId: Long, val rxBytes: Long, val txBytes: Long)`, `object NoLensCounters : LensCounters` (returns zeros), `SamplerEngine(..., lens: LensCounters = NoLensCounters)`.

- [ ] **Step 1: Tests first** (`SamplerEngineLensTest`, scripted counters like the existing `SamplerEngineTest`, read it first and reuse its helpers):
  * Wi-Fi default network, total rx grows by 2,000 and tx by 600 in one tick, mobile 0, the TUN counters grow by rx 1,000 and tx 300 in the same tick: booked Wi-Fi is rx 1,000 and tx 300 (NOT 2,000 / 600); with Lens off the same ticks book 2,000 / 600 (unchanged behaviour: existing tests still pass).
  * Mobile default network: mobile and total both grow by 5,000 rx while the TUN grows by 5,000: mobile books 5,000 and Wi-Fi 0 (unchanged rule: Wi-Fi only while the default network is Wi-Fi).
  * The TUN delta larger than the total delta clamps Wi-Fi to 0 (never negative).
  * A new `sessionId` between ticks counts the previous reading as zero: after Lens stops and restarts, the first tick subtracts only the new session's bytes; a decrease within one session also re-baselines instead of subtracting a negative number.
  * Lens stopping between ticks (readings go from (7, 5000, 900) to (0, 0, 0)): the tick after it subtracts nothing and books no phantom bytes.
  * `UnderlyingNetworkKindTest` (Robolectric or a small `NetworkSnapshot` abstraction fed by the test): while the default network has `TRANSPORT_VPN`, the kind is that of the best underlying network (Wi-Fi preferred over cellular when both are connected and validated), `null` when none; with no VPN the behaviour is unchanged.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** In `SamplerEngine.tick`: read `lens.read()`, compute `tunRxDelta/tunTxDelta` against the remembered previous reading (rule above), and `wifiDelta = max(0, totalDelta - mobileDelta - tunDelta)` per direction only while the kind is Wi-Fi. `ConnectivityNetworkKindSource`: keep its default-network callback and add `registerNetworkCallback(NOT_VPN + INTERNET)` to track underlying networks; when the default network is a VPN, resolve through them. `AppGraph` passes `lens = LensCountersAdapter(lensEngineHolder)`; the adapter returns zeros when Lens is not running. Contract 9.4 gets NO change (this is an addition recorded in section 12).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator, mandatory):** with the debug harness of S3 (or the final UI once B5 exists) run Lens on Wi-Fi, download exactly 50 MB through it, and compare Home's Wi-Fi figure growth, the Apps screen growth for the shell/browser app, and `dumpsys netstats` for the same period: Home's Wi-Fi growth must be within 10% of 50 MB (before this task it was about 100 MB); repeat with Lens off for the baseline; with mobile data only (Wi-Fi off) mobile growth is unchanged by Lens.
- [ ] **Step 5: Commit** `fix(data): do not count the VPN's own tunnel traffic twice while Lens runs`.

---

### Task B5: The Lens screen

**Files:**
- Modify: `feature/lens/build.gradle.kts` (add `alias(libs.plugins.kotlin.compose)`, `buildFeatures { compose = true }`, `implementation(project(":ui:design"))`, the Compose BOM, `androidx.compose.ui`, `androidx.compose.material3`, `material-icons-extended`, `androidx.lifecycle.runtime.compose`, `androidx.lifecycle.viewmodel.compose`, `androidx.activity.compose`, and the same `testImplementation`/`debugImplementation` Compose test dependencies as `:app`), `app/.../EmberbyteApp.kt` (replace the placeholder), `app/.../AppGraph.kt` (`lensViewModelFactory`)
- Create: `feature/lens/src/main/kotlin/$PKG/lens/ui/{LensViewModel,LensUiState,LensMapping,LensScreen}.kt`
- Test: `feature/lens/src/test/kotlin/$PKG/lens/ui/{LensMappingTest,LensViewModelTest,LensContentTest}.kt`

**Interfaces:**
```kotlin
data class LensAppRowUi(val uid: Int, val packageName: String, val label: String, val rateText: String, val rxText: String, val txText: String, val domains: List<LensDomainRowUi>)
data class LensDomainRowUi(val name: String, val isIp: Boolean, val bytesText: String, val lastSeenText: String)
data class LensUiState(val state: LensState, val rows: List<LensAppRowUi>, val expandedUid: Int?, val units: ByteUnits, val busy: Boolean)
sealed interface LensEvent { data object TurnOn; data object ConsentGranted; data object ConsentDenied; data object Stop; data object ClearHistory; data object Retry; data class Toggle(val uid: Int) }
class LensViewModel(controller: LensController, settings: SettingsRepository, labelOf: (Int) -> LabelInfo, clock: Clock, locale: Locale) : ViewModel()
@Composable fun LensScreen(viewModel: LensViewModel, onOpenVpnSettings: () -> Unit, modifier: Modifier = Modifier)
@Composable internal fun LensContent(state: LensUiState, onEvent: (LensEvent) -> Unit, onOpenVpnSettings: () -> Unit, launchConsent: (Intent) -> Unit, modifier: Modifier = Modifier)
```

- [ ] **Step 1: Tests first.** `LensMappingTest`: rate text uses `formatBytes` with "/s" (`"4.20 MB/s"`), the combined rate sorts descending, unknown uid row label "Unknown", IP literal rows flagged `isIp` and shown with the word "IP" in the content description, domain `lastSeenText` is a relative text ("just now", "3 min ago") with an injectable clock. `LensViewModelTest` (fake `LensController` with a controllable `MutableStateFlow` and snapshot flow, test dispatcher): `Off` + `TurnOn` -> calls `start()`; when the controller reports `NeedsConsent` the view model exposes a one-shot "launch consent" with `prepareConsentIntent()`; `ConsentGranted` calls `start()` again; `ConsentDenied` returns to `Off`; `Running` maps snapshots to rows (reorder keeps the same `expandedUid`; an expanded app that disappears collapses); `Stop` and `ClearHistory` call the controller; rows update at most once per snapshot; units follow the settings; the view model never touches snapshots while the state is not `Running`. `LensContentTest` (Robolectric Compose, dark static theme): all six states render their texts exactly as contract 12.4 says (`Off`: the explainer sentence and a `"Turn on"` button; `NeedsConsent`: a progress text `"Waiting for permission"`; `Starting`: skeleton rows with content description `"Starting Lens"`; `Running`: header `"Lens is on"`, a `"Stop"` button, app rows, tapping a row expands its domains and sends `Toggle`, the overflow menu offers `"Clear history"`; `Conflict(OTHER_VPN_ACTIVE)`: text naming the other VPN and a button `"Open VPN settings"` calling `onOpenVpnSettings`; `Conflict(ALWAYS_ON_VPN_BLOCKING)`: the always-on explanation; `Error`: the message and `"Try again"` sending `Retry`); rows are reachable by content description `"<label>, <rate>"`; with reduced motion no reordering animation (the existing `LocalReduceMotion`); every number/word pair inside `ForceLtr`; touch targets at least 48 dp.
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement.** The consent round trip uses `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` (`RESULT_OK` -> `ConsentGranted`). Rows animate position with `animateItem()` and `MaterialTheme.motionScheme.defaultSpatialSpec` (no hand-written durations). `EmberbyteApp`: the `Destination.Lens` route shows `LensScreen` (keep its deep link `emberbyte://lens`); `onOpenVpnSettings` fires `Settings.ACTION_VPN_SETTINGS`. Update the existing tests that asserted the Lens placeholder text (name them: `NavigationTest`, the placeholder text test).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator, mandatory):** (1) the Lens tab on a clean install shows the explainer and "Turn on"; (2) tap "Turn on": the system VPN dialog appears once, OK -> the screen shows "Lens is on", the key icon is in the status bar, the notification `lens` is shown; (3) generate traffic from two apps (browser and the shell via `adb shell curl` looks like uid 2000): rows appear sorted by rate, expand to domain names (HTTPS site shows its host name; a plain IP request shows the IP with "IP"); (4) Stop returns to Off, the key icon disappears (`dumpsys connectivity | grep -ci vpn` is 0); (5) with another VPN app active (if none is installed, CANNOT-VERIFY and say so) the Conflict screen appears; (6) rotation while Running keeps the state; (7) font scale 2.0 and Arabic render the rows without clipped or reordered numbers (screenshots, described); (8) `uiautomator` content descriptions exist for rows and buttons; (9) 40 navigation taps over the five destinations: 0 bounces.
- [ ] **Step 5: Commit** `feat(lens): show which app uses data right now and which servers it talks to`.

---

### Task B6: Settings, privacy statement and documentation

**Files:**
- Modify: `app/.../settings/{SettingsScreen,SettingsViewModel}.kt` (read first), `app/.../AppGraph.kt` (Settings factory gets `lens` and `history`), `README.md`, `NOTICE` (only if a credit changes), `docs/privacy.md` (create)
- Test: `app/src/test/.../settings/{SettingsLensTest,SettingsContentTest}.kt` (ADD)

- [ ] **Step 1: Tests first.** A section `"Privacy"` with: a row `"Lens history"` offering `Off`, `1 hour`, `24 hours`, `3 days`, `7 days` (maps to `LENS_HISTORY_CHOICES`), the current choice selected, choosing one updates `Settings.lensHistoryHours`; `"Clear Lens history"` (asks `"Delete all stored Lens history?"` with `"Delete"` / `"Cancel"`, `"Delete"` calls `LensController.clearHistory()` and shows the message `"Lens history deleted"`); the statement text of contract 12.4 verbatim; lowering the retention prunes at once (the view model asks the history store to `prune`, tested with the in-memory store). Existing Settings content tests keep passing; those that construct `SettingsContent` get the new parameters (name them).
- [ ] **Step 2: Run to verify they fail** → FAIL.
- [ ] **Step 3: Implement** the Privacy section, then the documents: `docs/privacy.md` (what is stored: package-level usage, plans, budgets; with Lens on: app, server name, hour, bytes; what is never stored; where; the INTERNET permission is used only to relay the user's own traffic; how to delete everything), README (Live Lens listed as available; limits: encrypted DNS and Encrypted Client Hello hide names, ping does not work while Lens runs, another VPN blocks Lens, extra battery use, IPv6 supported; the "your data never leaves this device" sentence stays and is explained by the privacy document; remove "Live Lens is not available" statements everywhere: `grep -rn -i "milestone 5\|not available\|later update" README.md app/src/main`).
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator):** the Privacy section works; choose "1 hour", generate traffic, check the DB copy (`adb exec-out run-as ... cat databases/emberbyte.db`, Python `sqlite3`) holds `lens_domain_hit` rows only for the chosen retention, "Off" stores nothing new, "Clear Lens history" empties the table; open the documents in the repo and quote one sentence of each to show they match the app's behaviour.
- [ ] **Step 5: Commit** `feat(settings): choose how long Lens remembers server names, and say plainly what it stores`.

---

### Task B7: Hardening (network changes, revocation, limits, teardown)

**Files (read first):** `feature/lens/.../android/LensVpnService.kt`, `AndroidLensController.kt`, `core/Forwarder.kt`, `FlowTable.kt`; tests next to each.

- [ ] **Step 1: Tests first** (JVM/Robolectric where possible, device lines below for the rest): (1) `Forwarder` survives a protected-socket `protect()` returning false (flow closed, counter, loop alive); (2) `connect` timeouts (injected) close the flow with RST after 10 s; (3) a flood of 5,000 SYNs in one second never exceeds the flow limit and never blocks the TUN reader longer than 100 ms per packet (measure with a fake clock hook or counters); (4) `LensEngine`/controller memory: running 100,000 simulated flow events keeps heap use bounded (assert collection sizes); (5) `stop()` during an active transfer returns in under 2 s, leaves no thread named `lens-*` alive (`Thread.getAllStackTraces()`), and a second `start()` works; (6) the controller turns `Running` into `Off` when the bridge reports revocation or service death and does not re-start by itself; (7) the foreground notification is removed on every stop path.
- [ ] **Step 2: Run to verify they fail** where the behaviour is new → FAIL.
- [ ] **Step 3: Implement** what the tests demand, plus: on a default-network change call `setUnderlyingNetworks`, close flows whose socket errors (the apps reconnect), and keep the TUN; `onTrimMemory`/low memory closes the oldest half of idle flows; any uncaught exception in the forwarder thread is caught at the thread boundary (`Thread.setUncaughtExceptionHandler`), tears the VPN down cleanly, publishes `Error("Lens stopped unexpectedly")`, and logs only the exception class and a counter.
- [ ] **Step 4: Run to verify they pass** — gate green. **Tester (emulator, mandatory):** while Lens runs and a download is in progress: toggle Wi-Fi off/on, toggle airplane mode on/off, `adb shell am force-stop` the app, turn the VPN off from the system VPN settings, kill the app's process (`adb shell run-as <pkg> kill -9 -1` or `am kill`), start a second VPN-capable app if available: in each case the phone's connectivity returns (curl works within 15 s), the key icon disappears or stays only while Lens truly runs, no crash dialog, the app reopens in a sane state (Off), `logcat -d | grep -E "AndroidRuntime|FATAL"` clean for the app's own process (name the process of any FATAL line).
- [ ] **Step 5: Commit** `fix(lens): recover cleanly from network changes, revocation and crashes`.

---

### Task B8: Final proof, vendor checklist and an honest report

No production changes here; a defect found goes back to its task for a fix round first.

- [ ] **Step 1: Full gate, no cache** (`./gradlew clean testDebugUnitTest :core:engine:test lintDebug assembleDebug --no-build-cache --stacktrace`): per-module counts as printed (baseline 965 + what A1/A2 added), the `:app` and `:feature:lens` lint warning lists with ids and locations (mark online version notices), Kotlin warnings with the real breakdown, the schema directory (`git diff --stat main..HEAD -- core/data/schemas` shows only `3.json` untouched and a new `4.json`), `git diff --stat main..HEAD -- docs/superpowers` (nothing), the merged release manifest permissions (`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `BIND_VPN_SERVICE` on the service only; no others new), that `NoNetworkOutsideLensTest` passes, and `git log --oneline main..HEAD | wc -l`.
- [ ] **Step 2: Emulator proof** (clean installs; restore every setting; `adb emu kill`): the tester lines of B3, B4, B5, B6, B7 re-run by a fresh tester, plus an upgrade from the M4 build (`e2763da`) keeping every row (user_version 4, counts before/after), Lens off by default after a fresh install and after an upgrade, Lens never starting without the consent tap (`adb shell am start ... emberbyte://lens` shows Off), the privacy invariants (a DB copy after a Lens session has no payload-like columns; `logcat -d` of a Lens session contains no domain names, IP addresses of remote hosts, or foreign UIDs: grep for the host names you used and report the count), M1 to M4 flows still working (plans, alerts once per threshold, budgets, spikes, export/backup/restore) and a 40-tap navigation stress with 0 bounces.
- [ ] **Step 3: Manual checklist for a real phone** `docs/manual-checks/lens-real-device.md` (for the project owner; the emulator cannot do these): install, turn Lens on, open ten apps and verify the owner is right for each (and what fraction shows "Unknown": the open verification item of backend section 13.4, vendor ROM specific), IPv6-only carrier network, a captive Wi-Fi, 30 minutes of video with `measure-lens.sh --battery`, another VPN installed, work profile, Always-on VPN with lockdown, DoH in the browser (names fall back to IPs), battery saver and OEM background killers; each step has an expected result.
- [ ] **Step 4: Completion report** `.superpowers/sdd/2026-10-11-emberbyte-m5-live-lens/m5-completion-report.md`: the task table (id, commit range, what changed, tester verdict or "self-verified, no separate tester report"), the exact gate numbers, the lint list, every deviation from this plan with the reason, every pre-existing test changed (from `git diff main -U0 -- '*Test.kt' | grep -E '^[-+].*fun '`, every removed `fun` accounted for), the spike numbers with a pointer to the decision record, the Step 2 results with evidence (or CANNOT-VERIFY), the deferred items (battery on a real phone, vendor ROM owner reliability, IPv6 on a real network, QUIC names, DoH/ECH names, the M4 leftovers: personal curve includes free-window traffic, date and weekday names keep the old locale until restart, History bar descriptions mixing languages, M-4, M-9, M-10, M-16, Estimated flicker), and a "Corrections" section. Rules: only what you ran; "not run" when not run.
- [ ] **Step 5: Stop.** `git status` shows nothing modified except git-ignored files, nothing pushed, you are on `m5-live-lens`. Print a short summary and say: **"M5 is ready for the Claude audit on branch m5-live-lens."** Do not merge, push or start M6.

---

## Self-review (done by the plan's author)

**Spec coverage.** Design M5 = forwarder spike first (S1 to S3) then the VPN engine and the Lens screen (B2 to B5); backend section 7: pipeline (S2, B2), passthrough with `protect()` (S2, S3), per-flow table and owner UID (S2, B2), names from DNS and SNI (S1, S2, B2), IP fallback (B2), isolation in `LensVpnService` started by `LensController.start()` after consent (B3), conflicts (B3), retention (B1, B6), the open items 13.3 (decided in S3) and 13.4 (checked on the emulator, listed for a real phone in B8); contract section 4 (B2, B3), section 5 `lens_domain_hit` (B1), section 6 components (B3), the double-counting limit of 9.4 (B4); frontend 6.4 six states (B5) and 6.5 Privacy (B6). Carry-over from M4: A1, A2.
**Deliberately not here:** per-app blocking or firewalling (out of scope for v1), QUIC name extraction, battery optimisation work beyond measurement, polish and the F-Droid submission (M6).
**Names (checked):** `TunDevice`, `SocketProtector`, `OwnerResolver`, `FlowListener`, `Forwarder`, `LensEngine`, `LensCounters`/`TunCounters`, `LensHistoryStore`/`LensDomainHit`, `LensController`, `LensServiceBridge`, `ConflictDetector`, `LensViewModel`/`LensEvent` are defined in the task that first needs them and used with the same signatures later.
**Risks the executing agents must watch:** the forwarder is the largest piece of work in the project (a TCP state machine): test it on the JVM before touching the device; never log addresses or names; Room migration SQL must be copied from the generated `4.json`; `getConnectionOwnerUid` may return -1 on some ROMs (the UI must cope); the emulator has no IPv6 route and no battery model (say CANNOT-VERIFY, do not guess); if the spike says `NATIVE NEEDED`, stop and wait for the user.
