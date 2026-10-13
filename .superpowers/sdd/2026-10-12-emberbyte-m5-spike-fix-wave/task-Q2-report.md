# Task Q2 Report: The Secondary Forwarder Guarantees (SNI, UDP Large Replies, UDP DNS Port Gating, Owner Cache LRU, onFlowClosed, Protect Failure, and Allocation Ceiling)

**Date**: 2026-10-13  
**Branch**: `m5-live-lens`  
**Spec Reference**: `docs/superpowers/specs/2026-10-01-datakb-api-contract.md` §12.2 (Milestone 5 Fix Wave)

---

## 1. Executive Summary

Task Q2 delivers the secondary forwarder guarantees mandated by the Emberbyte M5 spike fix wave:
1. **SNI across whole first TLS record**: TLS ClientHello SNI is parsed across multiple segmented, retransmitted, or out-of-order packets spanning the entire first TLS record. Non-TLS records (such as HTTP) never emit an SNI and immediately stop buffering.
2. **UDP oversized replies (> MTU)**: Datagrams exceeding the MTU boundary (calculated as `mtu - 28` for IPv4 and `mtu - 48` for IPv6) are dropped, counted in `oversizedDatagrams`, and do NOT terminate or close the UDP flow. Subsequent in-range datagrams continue to be forwarded normally.
3. **UDP DNS parsed only on port 53**: UDP payloads are parsed for DNS answers (via `DnsParser.answers`) strictly when the remote destination port is 53. Port 5353 (mDNS) and other ports (e.g., port 8000) carrying valid DNS structures never trigger `onDns`.
4. **Owner cache bounded at 4,096 (LRU)**: `BoundedLruCache` caps cached flow UIDs at 4,096 entries, evicting eldest entries when full. Flows explicitly clean up their entry upon closure. Validated against 100,000 short flows without socket or memory exhaustion.
5. **`onFlowClosed` called exactly once per flow**: In all termination paths (RST received, FIN teardown, protect failure, idle timeout, manual close), `onFlowClosed` is invoked exactly once per flow via atomic CAS guards.
6. **`protect()` failure handling**: Socket protector failures (`protect() == false` or throwing an exception) immediately abort connection, emit a stateless RST towards the client, never attempt backend connection, and increment `protectFailures`.
7. **Relay 50 MiB allocation ceiling ($\le 2\times$ payload)**: Relaying 50 MiB of payload through the forwarder allocates $\le 104,857,600$ bytes across the TUN reader and network selector threads (measured via `ThreadMXBean.getThreadAllocatedBytes`). Zero-copy buffer pooling (`DirectBufferPool`, `ByteArrayPool`) and in-place checksum computation achieved ~11.1 MiB total allocation under load (well below the 104.8 MiB cap).

All 7 test cases in `ForwarderQ2Test.kt` pass deterministically. 5 consecutive runs of `:feature:lens:testDebugUnitTest --rerun-tasks` completed with 0 failures, and the full repository verification gate (`testDebugUnitTest :core:engine:test lintDebug assembleDebug`) passed cleanly.

---

## 2. Deviations and Changed Pre-existing Tests

### `ForwarderUdpTest > dns_answer_relays_trigger_on_dns`
- **Deviation**: The pre-existing test sent a UDP DNS query to an ephemeral server destination port (`serverPort`), relying on the milestone 5 prototype behaviour where `DnsParser.answers` was unconditionally invoked on every UDP flow regardless of destination port.
- **Specification**: Task Q2 Requirement 3 explicitly states:
  > *"UDP parsed as DNS only on port 53. A UDP flow to port 5353 or 8000 carrying a valid DNS-looking payload does NOT call onDns; port 53 replies do."*
- **Resolution**: Updated `dns_answer_relays_trigger_on_dns` in `ForwarderUdpTest.kt` to send to destination port 53 (`InetSocketAddress("127.0.0.1", 53)`), matching the specification and contract comments already noted in the test file (`// Source port on app side: 53123, dest is serverPort (in test we simulate dest port 53 or server responding from port 53)`). The response is fed to the created UDP flow socket, asserting that `onDns` is invoked properly on port 53.

---

## 3. Implementation Details

### SNI Buffering (`TcpFlow.kt`)
- `TlsRecordBuffer`: Accumulates contiguous upstream segments from the initial sequence number up to the first TLS record length (`5 + recordLength`).
- Extracts SNI extensions with SNI type 0 (host name) once the full record is buffered.
- Non-TLS first bytes (`firstByte != 0x16`) or invalid versions immediately clear the buffer and permanently mark `tlsBuffered = true` to avoid unneeded heap retention.

### UDP Flow Management (`UdpFlow.kt` & `Forwarder.kt`)
- Added `oversizedDatagramsCounter: AtomicLong` and `protectFailuresCounter: AtomicLong`.
- In `UdpFlow.onSocketReadable()`:
  - If `readBytes > maxPayload`, increment `oversizedDatagramsCounter` and drop the packet without closing the flow.
  - Gate `DnsParser.answers(payload)` strictly on `isDnsPort` (`dst.port == 53`).
- In `UdpFlow.init`:
  - Enclose `protector.protect(dc)` in try-catch. If protection fails or throws, close the channel, notify `onFlowClosed`, increment `protectFailuresCounter`, and return without registering with the selector.
- Atomic CAS guard `closed.compareAndSet(false, true)` ensures `onFlowClosed` is fired exactly once across all close paths.

### Owner Cache LRU (`Forwarder.kt`)
- Implemented thread-safe `BoundedLruCache<FlowKey, Int>(4096)` backed by `LinkedHashMap(accessOrder = true)` overriding `removeEldestEntry`.
- Flow closure hooks (`internalListener.onFlowClosed`) remove closed keys from `uidCache` to prevent cache stagnation.

### Protect Failure RST (`TcpFlow.kt`)
- Socket channel creation and protection handled prior to connect.
- If `protector.protect(sc)` returns `false` or throws, `protectFailuresCounter` is incremented, an immediate TCP RST segment is sent back via `sendRst()`, and the flow is marked closed without connecting.

### Allocation Optimization ($\le 2\times$ payload for 50 MiB relay)
- Implemented `DirectBufferPool` (`ByteBuffer.allocateDirect(16384)`) for socket network I/O.
- Implemented `ByteArrayPool` (`ByteArray(16384)`) for packet assembly and TUN reads.
- Implemented zero-allocation in-place Internet Checksum calculators in `Checksums.kt`:
  - `computeIpChecksum(header: ByteArray, offset: Int, length: Int): Int`
  - `computeTcpChecksum(srcIp, dstIp, headerAndPayload: ByteArray, offset: Int, length: Int): Int`
- `PacketWriter`: Overloaded `writeTcp` and `writeUdp` to accept pooled arrays and write pseudo-headers without allocating intermediate byte arrays.
- Relay test results: Relaying 50 MiB payload allocated ~11.1 MiB across network and TUN threads (far below the 104.8 MiB budget).

---

## 4. Verification Evidence

### 1. `ForwarderQ2Test.kt` (7 Test Suite)
- `test1_sniOverFirstTlsRecord_splitAcrossSegmentsAndOutOfOrder`: PASS
- `test2_udpLargeRepliesDroppedAndFlowStaysAlive`: PASS
- `test3_udpParsedAsDnsOnlyOnPort53`: PASS
- `test4_ownerCacheBoundedAndCleanedOnFlowClose`: PASS
- `test5_onFlowClosedCalledExactlyOnce`: PASS
- `test6_protectFailureSendsRstAndIncrementsCounter`: PASS
- `test7_allocationCeilingDuring50MiBRelay`: PASS (Measured: 11,087,224 bytes $\le$ 104,857,600 bytes)

### 2. 5 Consecutive Runs of `:feature:lens:testDebugUnitTest --rerun-tasks`
- Run 1: SUCCESS (1m 22s) - 49 tasks executed
- Run 2: SUCCESS (1m 22s) - 49 tasks executed
- Run 3: SUCCESS (1m 22s) - 49 tasks executed
- Run 4: SUCCESS (1m 23s) - 49 tasks executed
- Run 5: SUCCESS (1m 17s) - 49 tasks executed

### 3. Full Repository Verification Gate
`./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
- 232 actionable tasks: 30 executed, 202 up-to-date
- BUILD SUCCESSFUL in 1m 21s
- 0 lint errors, 0 test failures.
