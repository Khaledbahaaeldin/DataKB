# Task S1 Report: Packet and name parsers (`…lens.core`)

## Status
DONE

## Summary of Changes
Implemented pure JVM packet and name parsing core for Milestone 5 Live Lens under `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/` and comprehensive unit tests under `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/`:
- **`feature/lens/build.gradle.kts`**: Added `testImplementation(libs.junit)`.
- **`ParsedPacket.kt` & `TcpFlags.kt`**: Defined `ParsedPacket` sealed interface with `Tcp` and `Udp` data classes, properties `src`, `dst`, `protocol`, `payloadOffset`, `payloadLength`, `seq`, `ack`, `flags`, `window`, `mss`, and `TcpFlags` constants.
- **`Checksums.kt`**: Implemented RFC 1071 one's complement sum calculation for IPv4 header and IPv4/IPv6 TCP/UDP pseudo-header checksums.
- **`PacketParser.kt`**: Implemented strict packet bounds checking, IPv4/IPv6 decoding, extension header skipping (0, 43, 60), fragment dropping (MF / non-zero offset / IPv6 fragment header 44), TCP options parsing (MSS extraction), UDP zero-checksum handling on IPv4, and socket address creation without DNS resolution.
- **`DnsParser.kt`**: Implemented DNS response answer parser with support for A and AAAA records, compression pointer traversal (loop protection limited to 16 jumps), CNAME chain resolution mapping back to the query/first name, bounds checks, and RCODE verification.
- **`SniParser.kt`**: Implemented TLS ClientHello record parser extracting `host_name` from SNI extension, tolerant to GREASE and arbitrary extension order, strictly validating allowed characters `[A-Za-z0-9.-_]`, checking bounds/split records, and returning lower-cased server names.
- **`PacketBuilder.kt`**: Test helper crafting synthetic IPv4/IPv6 TCP/UDP packets with valid or corrupted checksums, DNS response messages, and TLS ClientHello records.
- **Unit Tests**:
  - `PacketParserTest.kt`: IPv4 TCP SYN with MSS, IPv6 TCP segment, UDP packets, IP options with IHL > 5, TCP options with data offset > 5, drop cases (wrong version, invalid lengths, bad IP/TCP/UDP checksums, fragments, ICMP, truncated headers, fragment extension headers, empty/1-byte buffers), and a 20,000-sample fuzz test.
  - `ChecksumsTest.kt`: RFC 1071 example verification, odd-length payload checksum, and IPv6 pseudo-header checksum.
  - `DnsParserTest.kt`: RFC sample message, AAAA response, CNAME chain tracking, pointer loop/out-of-bounds termination, queries/truncated/NXDOMAIN handling, and TTL 0 handling.
  - `SniParserTest.kt`: ClientHello extraction, GREASE extension handling, upper-to-lower case transformation, multiple names in list, corrupted/truncated/split records handling, and a 5,000-sample fuzz test.

## Verification
1. **Red Test Failure Verification**:
   - Ran `./gradlew :feature:lens:testDebugUnitTest` prior to core class implementation.
   - Result: BUILD FAILED (Compilation error: Unresolved references `Checksums`, `PacketParser`, `ParsedPacket`, `DnsParser`, `SniParser`).
2. **Green Test Verification**:
   - Ran `./gradlew :feature:lens:testDebugUnitTest`.
   - Result: BUILD SUCCESSFUL (all 21 unit tests passed, including fuzz runs).
3. **Full Gate Verification**:
   - Ran `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`.
   - Result: BUILD SUCCESSFUL in 1m 7s (223 actionable tasks).

## Git Commit
- Commit: `b19f1ed` (`feat(lens): parse IP, TCP, UDP, DNS and TLS server names without trusting the input`)
- Staged explicitly by path; `docs/superpowers` remained untouched.
