# Task S2 Report: Pure-userspace packet forwarder (`…lens.core`)

## Status
DONE

## Summary of Changes
Implemented pure JVM packet forwarding core for Milestone 5 Live Lens under `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/` and its complete integration test suite under `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/`:
- **`TunDevice.kt`**: Interface for reading and writing IP packets to the TUN device.
- **`SocketProtector.kt`**: Interface protecting sockets from loopback routing.
- **`OwnerResolver.kt`**: Interface mapping 5-tuple socket addresses to Android app UIDs.
- **`FlowListener.kt`**: Flow event callback interface reporting flow lifecycle (`onFlowOpened`, `onFlowClosed`), ephemeral byte traffic accounting (`onBytes`), and discovered names (`onName`, `onDns`).
- **`PacketWriter.kt`**: Low-level packet builder utility formatting IPv4/IPv6 TCP/UDP response packets with recalculated IP/TCP checksums.
- **`TcpFlow.kt`**: Pure userspace TCP relay implementing state machine (`SYN_RECEIVED`, `ESTABLISHED`, `FIN_WAIT_1`, `FIN_WAIT_2`, `TIME_WAIT`, `CLOSE_WAIT`, `LAST_ACK`, `CLOSED`), non-blocking Java NIO `SocketChannel`, connect handshake, sequence/ack tracking, sliding window buffer, SNI extraction on initial data segment, and RFC 793 RST generation.
- **`UdpFlow.kt`**: Pure userspace UDP relay managing non-blocking `DatagramChannel`, DNS response packet parsing (`DnsParser`), and port 53 / idle timeout management.
- **`FlowTable.kt`**: Concurrent map tracking active TCP and UDP flows, generating monotonically increasing 64-bit flow IDs, and enforcing max concurrent flow bounds via longest-idle LRU eviction.
- **`Forwarder.kt`**: Top-level coordinator binding `TunDevice`, `SocketProtector`, and Java NIO `Selector`. Houses two decoupled threads: `Forwarder-TunReader` reading incoming raw packets from TUN and parsing them, and `Forwarder-Network` running the selector event loop, dispatching network events, updating byte statistics, and sweeping dead/timed-out flows.
- **Integration Test Harness & Tests**:
  - `FakeTun.kt`: In-memory TUN device simulation supporting multi-client packet subscription and routing.
  - `TcpClientSim.kt`: Synthetic TCP client simulation handling 3-way handshakes, data segment exchange, ACK generation, out-of-order reassembly, and drop rate testing.
  - `ForwarderTcpTest.kt`: Thorough TCP tests covering handshake/echo, large 10 MB byte-identical transfer, 2 MB transfer under 5% packet drop conditions, half-close FIN handling, duplicate/out-of-order segment processing, zero-window flow control, SNI detection, byte accounting, idle timeouts, IPv6 TCP flows, and 200 parallel flows.
  - `ForwarderUdpTest.kt`: UDP tests covering bi-directional UDP echo, DNS query/answer extraction and routing to `FlowListener.onDns`, IPv6 UDP flows, port 53 10-second idle timeouts, and standard 60-second idle timeouts.
  - `ForwarderLimitsTest.kt`: Max flow bounds test verifying eviction of oldest idle flow on capacity exhaustion.

## Verification
1. **3 Consecutive Verification Passes (GREEN Gate)**:
   - Command: `./gradlew :feature:lens:testDebugUnitTest --rerun-tasks`
   - Run 1: BUILD SUCCESSFUL (28 actionable tasks, all 46 unit tests passed)
   - Run 2: BUILD SUCCESSFUL (28 actionable tasks, all 46 unit tests passed)
   - Run 3: BUILD SUCCESSFUL (28 actionable tasks, all 46 unit tests passed)
2. **Full Project Gate**:
   - Command: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
   - Result: BUILD SUCCESSFUL in 1m 16s (223 actionable tasks executed/up-to-date, 0 lint errors, assembleDebug passed).
3. **Privacy Compliance**:
   - Payload bytes ephemeral, never stored or persisted.

## Git Commit
- Target commit: `feat(lens): relay TCP and UDP flows in userspace and report each flow's bytes and names`
- Author: `Omar Amin <omarsadiq2012@gmail.com>`
- Co-Authored-By: `Gemini (Antigravity) <noreply@google.com>`
