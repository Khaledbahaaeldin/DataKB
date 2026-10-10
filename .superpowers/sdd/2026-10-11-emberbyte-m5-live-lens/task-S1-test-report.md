# Task S1 Test Report: Packet and Name Parsers (`…lens.core`)

## Verdict
**PASS**

## Issues Summary
- **Critical:** 0
- **Important:** 0
- **Minor:** 1

### Minor Issues
1. **DnsParserTest coverage gap on name > 255 bytes:** `DnsParser.kt` correctly enforces RFC length limits by returning `null` (causing `answers()` to return `emptyList()`) if `totalLength > 255`, but `DnsParserTest.kt` does not include an explicit test case exercising a synthetic DNS response containing a name longer than 255 bytes.

---

## Spec Findings

1. **Contract 12.2 & Brief Signatures and Types:**
   - `ParsedPacket`: `sealed interface ParsedPacket` declares `src: InetSocketAddress`, `dst: InetSocketAddress`, `protocol: Int`, `payloadOffset: Int`, and `payloadLength: Int`.
   - `ParsedPacket.Tcp`: Declares `src`, `dst`, `seq: Long`, `ack: Long`, `flags: Int`, `window: Int`, `mss: Int?`, `payloadOffset: Int`, `payloadLength: Int`, with `override val protocol: Int get() = 6`.
   - `ParsedPacket.Udp`: Declares `src`, `dst`, `payloadOffset: Int`, `payloadLength: Int`, with `override val protocol: Int get() = 17`.
   - `TcpFlags`: Constant values `FIN = 0x01`, `SYN = 0x02`, `RST = 0x04`, `PSH = 0x08`, `ACK = 0x10` match contract requirements.
   - `DnsAnswer`: Data class with `name: String`, `address: InetAddress`, `ttlSeconds: Long`.
   - `DnsParser.answers(payload: ByteArray): List<DnsAnswer>` matches signature.
   - `SniParser.serverName(firstRecord: ByteArray): String?` matches signature.
   - `Checksums`: `ipv4Header(buf: ByteArray, off: Int, headerLen: Int): Int` and `transport(buf: ByteArray, ipOff: Int, ipv6: Boolean, protocol: Int, segOff: Int, segLen: Int): Int` match signature.
   - Pure JVM Kotlin: No Android imports (`android.*`) exist in `ParsedPacket.kt`, `Checksums.kt`, `PacketParser.kt`, `DnsParser.kt`, or `SniParser.kt`.

2. **PacketParser Validation & Behaviour:**
   - Valid IPv4 TCP SYN with MSS option parsed correctly.
   - Valid IPv6 TCP segment parsed correctly.
   - Valid IPv4 and IPv6 UDP packets parsed correctly with correct payload offsets.
   - IP options (IHL = 6, 24 bytes header) and TCP options (data offset > 5) parsed with correct payload offset adjustments.
   - Non-IPv4/IPv6 versions return `null`.
   - Length fields exceeding buffer bounds or total length shorter than header return `null`.
   - Invalid IPv4 header checksum returns `null`.
   - Invalid TCP/UDP checksum returns `null`.
   - UDP checksum of `0` on IPv4 is permitted per RFC 768 / brief requirement.
   - Fragmented IPv4 (MF flag or fragment offset > 0) returns `null`.
   - Protocols other than TCP/UDP (e.g. ICMP = 1) return `null`.
   - Truncated TCP/UDP headers return `null`.
   - IPv6 extension headers: hop-by-hop (0), routing (43), destination options (60) are skipped; fragment header (44) returns `null`.
   - Empty buffers and 1-byte buffers return `null` without throwing exceptions.
   - `InetSocketAddress` instances created using `InetAddress.getByAddress(...)` to avoid blocking DNS resolution.

3. **Checksums RFC 1071 Behaviour:**
   - Verified against RFC 1071 test vector `450000730000400040110000c0a80001c0a800c7` yielding `0xb861`.
   - Verified one's complement sum over header containing checksum field returns `0`.
   - Odd-length payload transport checksum verified.
   - IPv6 pseudo-header checksum verified.

4. **DnsParser Behaviour:**
   - Sample RFC DNS response decoded: `example.com` -> `93.184.216.34`, TTL 300.
   - A and AAAA record responses parsed.
   - CNAME chains map the address back to the question name (the name requested by the app).
   - Compression pointer traversal handles single/multiple hops, detects loops (limited to 16 jumps), and handles pointers past payload bounds gracefully without exceptions.
   - Truncated messages, queries (QR = 0), non-zero RCODEs (e.g., NXDOMAIN = 3), and zero answers return `emptyList()`.
   - TTL 0 returned as `0`.

5. **SniParser Behaviour:**
   - TLS ClientHello record with SNI extracted.
   - Lower-cases extracted host names.
   - Tolerant to GREASE extension types (e.g. `0x0a0a`, `0x1a1a`) and arbitrary extension ordering.
   - Selects first `host_name` (type 0) entry when multiple are present.
   - Returns `null` on missing SNI, truncated record, non-handshake record (e.g. Application Data 0x17), non-ClientHello handshake (e.g. ServerHello 0x02), host name containing non-ASCII characters or spaces, host name > 255 chars, or split ClientHello records.

6. **Git Identity & Invariants:**
   - Commit: `b19f1edfb7b3c3d4b87dc74b128427c2e98c0840`.
   - Author: `Omar Amin <omarsadiq2012@gmail.com>`.
   - Commit message explains WHY and ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
   - `docs/superpowers` untouched (`git diff --stat HEAD -- docs/superpowers` is empty).
   - `grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` is exactly `1`.
   - JDK 17 compatibility verified: no Java 18+ APIs used.

---

## Run Results

### 1. Isolated Unit Tests (`:feature:lens:testDebugUnitTest --rerun-tasks`)
Executed command:
```bash
./gradlew :feature:lens:testDebugUnitTest --rerun-tasks
```

Output:
```text
> Task :feature:lens:compileDebugKotlin
> Task :feature:lens:compileDebugJavaWithJavac NO-SOURCE
> Task :feature:lens:processDebugJavaRes
> Task :feature:lens:bundleLibRuntimeToJarDebug
> Task :feature:lens:bundleLibCompileToJarDebug
> Task :feature:lens:compileDebugUnitTestKotlin
> Task :feature:lens:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :feature:lens:processDebugUnitTestJavaRes
> Task :feature:lens:testDebugUnitTest

[Incubating] Problems report is available at: file:///C:/Fast%20Project/DataKB/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 40s
28 actionable tasks: 28 executed
```

Test Results Breakdown (all 21 unit tests passed):
- `ChecksumsTest`: 3 tests passed
  - `transport_ipv6PseudoHeader` (0.010s)
  - `transport_oddLengthPayload` (0.001s)
  - `ipv4Header_rfc1071Example` (0.009s)
- `DnsParserTest`: 5 tests passed
  - `rfcExample_aRecord` (0.002s)
  - `aaaaRecord` (0.001s)
  - `cnameChain` (0.010s)
  - `pointerLoopAndPastTheEnd` (0.000s)
  - `malformedAndEdgeCases` (0.000s)
- `PacketParserTest`: 7 tests passed
  - `fuzzTest_20kRandomByteArrays` (0.012s)
  - `udpPacket_ipv4AndIpv6` (0.001s)
  - `ipv6TcpSegment` (0.001s)
  - `droppedPackets` (0.000s)
  - `ipv4TcpSynWithMssOption` (0.001s)
  - `ipv4WithIpOptions_ihl6` (0.000s)
  - `tcpDataOffsetAbove5` (0.000s)
- `SniParserTest`: 6 tests passed
  - `multipleNamesInList_firstHostNameWins` (0.001s)
  - `malformedOrNoSni_returnsNull` (0.000s)
  - `standardClientHello_extractsServerName` (0.000s)
  - `greaseExtensions_andOrderTolerant` (0.001s)
  - `upperCaseInput_returnsLowerCase` (0.000s)
  - `fuzzTest_5kRandomByteArrays_doesNotThrow` (0.002s)

### 2. Full Verification Gate
Executed command:
```bash
./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace
```

Output:
```text
> Task :feature:lens:lintDebug
> Task :core:data:lintDebug
> Task :ui:design:lintDebug
> Task :app:lintDebug
> Task :app:assembleDebug UP-TO-DATE

BUILD SUCCESSFUL in 11s
223 actionable tasks: 4 executed, 219 up-to-date
```

---

## Test-Quality Findings

1. **Real Behavior Assertions:**
   - Tests verify exact TCP sequence/acknowledgement numbers, flags, window sizes, MSS option parsing, address parsing, and payload offsets.
   - Negative cases assert `null` or `emptyList()` across 12 distinct packet drop conditions.
2. **Extensive Fuzz Testing:**
   - `PacketParserTest.fuzzTest_20kRandomByteArrays`: 20,000 seeded random buffers verified to never throw exceptions and ensure any non-null parsed packet has payload offsets and lengths strictly contained within the buffer boundaries.
   - `SniParserTest.fuzzTest_5kRandomByteArrays_doesNotThrow`: 5,000 seeded random buffers verified to never throw unhandled exceptions.
3. **No Mocks or Android Shadows:**
   - Tests execute against pure JVM data representations and real RFC packet layouts generated via `PacketBuilder`.
