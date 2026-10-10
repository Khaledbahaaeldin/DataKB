# Task S2 Test Report: Pure-userspace packet forwarder (`…lens.core`)

## Verdict
**PASS**

## Spec Findings
All requirements and interfaces specified in contract section 12.2 and task S2 brief were checked against the commit `30875ea89b65440045e393f871b90c73861947b7`:
1. **Interfaces in contract 12.2**:
   - `TunDevice`: [TunDevice.kt](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/TunDevice.kt) defines `fun read(buffer: ByteBuffer): Int`, `fun write(buffer: ByteBuffer, length: Int)`, `fun close()`.
   - `SocketProtector`: [SocketProtector.kt](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/SocketProtector.kt) defines protection for `Socket`, `DatagramSocket`, and `SelectableChannel`.
   - `OwnerResolver`: [OwnerResolver.kt](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/OwnerResolver.kt) defines `fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int`.
   - `FlowListener` & `FlowInfo`: [FlowListener.kt](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/FlowListener.kt) defines `FlowInfo` and callbacks `onFlowOpened`, `onBytes`, `onName`, `onDns`, `onFlowClosed`.
   - `Forwarder`: [Forwarder.kt](file:///c:/Fast%20Project/DataKB/feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/Forwarder.kt) implements `run()`, `stop()`, atomic counters `evictedFlows` and `errors`.
2. **Pure JVM**:
   - Grep verification showed zero Android imports (`import android.*`) in `feature/lens/src/main/kotlin/.../lens/core/`.
3. **Forwarder Design Invariants**:
   - Single reader thread (`Forwarder-TunReader`) and single network NIO loop thread (`Forwarder-Network`) via `java.nio.channels.Selector`. Serialized TUN writes using a monitor lock `tunWriteLock`.
   - TCP state machine implemented with RFC 793 transitions (`SYN_RECEIVED`, `ESTABLISHED`, `CLOSE_WAIT`, `FIN_WAIT_1`, `FIN_WAIT_2`, `CLOSING`, `TIME_WAIT`, `CLOSED`).
   - Sliding window buffer: buffers up to 256 KiB upstream, chunked into MSS downstream segments.
   - Retransmission: exponential backoff doubling RTO from 1s up to 5 attempts before RST.
   - Sequence arithmetic modulo 2^32 (`and 0xFFFFFFFFL`).
   - Half-close handling: client FIN triggers socket `shutdownOutput()` and enters `CLOSE_WAIT`; server EOF triggers downstream FIN segment.
   - UDP flows: single protected `DatagramChannel` per (src, dst) pair; 10s idle timeout for port 53 and 60s idle timeout for other ports. DNS answers extracted via `DnsParser.answers(payload)` and dispatched to `listener.onDns(answers)`.
   - Capacity & Eviction: maximum flow limit enforced by `FlowTable`, evicting longest-idle flow and incrementing `evictedFlows`.
   - Graceful termination: `stop()` unblocks TUN, interrupts threads, closes selector and active sockets within 2 seconds.
4. **Git Identity**:
   - Commit `30875ea89b65440045e393f871b90c73861947b7` authored by `Omar Amin <omarsadiq2012@gmail.com>`.

## Run Results (With Quoted Output)

### 1. Three Consecutive Runs of `:feature:lens:testDebugUnitTest --rerun-tasks`
**Run 1**:
```text
> Task :feature:lens:testDebugUnitTest
BUILD SUCCESSFUL in 1m 5s
28 actionable tasks: 28 executed
```

**Run 2**:
```text
> Task :feature:lens:testDebugUnitTest
BUILD SUCCESSFUL in 58s
28 actionable tasks: 28 executed
```

**Run 3**:
```text
> Task :feature:lens:testDebugUnitTest
BUILD SUCCESSFUL in 57s
28 actionable tasks: 28 executed
```

### 2. Full Gate Command
Command: `./gradlew testDebugUnitTest :core:engine:test lintDebug assembleDebug --stacktrace`
```text
> Task :core:data:lintDebug
> Task :feature:lens:lintDebug
> Task :app:testDebugUnitTest UP-TO-DATE
> Task :ui:design:lintDebug
> Task :app:lintDebug
> Task :app:assembleDebug UP-TO-DATE

BUILD SUCCESSFUL in 11s
223 actionable tasks: 4 executed, 219 up-to-date
```

## Test-Quality Findings
Inspected `ForwarderTcpTest.kt`, `ForwarderUdpTest.kt`, and `ForwarderLimitsTest.kt`:
1. **Asserts real behaviour**:
   - `transfer_10mb_byte_identical`: Transmits 10 MB of random bytes over the userspace forwarder relay to an echoing ServerSocket. Asserts client received size matches 10 MB, and verifies byte-identical payload integrity via SHA-256 digest comparison between sender, server, and received buffers.
   - `transfer_2mb_with_5_percent_drops`: Transmits 2 MB through `TcpClientSim` configured with a 5% drop rate (`dropRate = 0.05`). Asserts end-to-end receipt of full 2 MB and byte-identical integrity via SHA-256 digest comparison.
   - `parallel_200_flows_each_20kb`: Concurrently executes 200 distinct TCP client connections in thread batches against an echo server, each transferring 20 KiB. Asserts all 200 flows succeed.
   - Sequence wrap-around test: Sets initial sequence number to `0xFFFFFF00L` and transfers 1000 bytes across boundary, verifying matching bytes.
   - Out-of-order segment test: Injects segment 2 prior to segment 1, verifying server receives nothing until segment 1 is provided, at which point contiguous stream is delivered in order.
   - Zero-window test: Advertises window 0, verifying pause in downstream transmission until window opens.
   - Eviction test: Sets max flows to 8, opens 8 flows sequentially, injects 9th flow, and confirms eviction of oldest-idle flow while incrementing `evictedFlows`.
   - Garbage packet resilience: Interleaves 20,000 malformed/random packets during active TCP transfer, verifying that transfer finishes unaffected and forwarder remains running.
   - Stop deadline: Asserts `stop()` finishes within 2 seconds.

## Invariants & Safety Check
- **No Java 18+ APIs**: Code uses standard Java NIO (`java.nio.channels.Selector`, `SocketChannel`, `DatagramChannel`), `ByteBuffer`, and `java.time` APIs.
- **Privacy Compliance**:
   - Grep verification showed zero loggers or `println`/`print` calls in `feature/lens/src/main/kotlin/.../lens/core/`.
   - No IP addresses, domain names, or payload bytes are logged or retained.
- **Git & Spec Safety**:
   - `git diff --stat HEAD -- docs/superpowers` produced empty diff (0 modified files).
   - `git grep -c "## 12. Milestone 5" docs/superpowers/specs/2026-10-01-datakb-api-contract.md` returned exactly `1`.
   - `git status --short` is clean (only untracked `docs/agents/` pre-existing).

## Issues
- **Critical**: 0
- **Important**: 0
- **Minor**: 0
