# Task S2 Brief: The forwarder core (`Forwarder`, flow table, TCP and UDP relay) on the JVM

### Source: Task S2 of `docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md`

**Files:**
- Create:
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/Forwarder.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/FlowTable.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/TcpFlow.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/UdpFlow.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/TunDevice.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/SocketProtector.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/OwnerResolver.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/FlowListener.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/PacketWriter.kt`
- Test:
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/FakeTun.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/TcpClientSim.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/ForwarderTcpTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/ForwarderUdpTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/ForwarderLimitsTest.kt`

**Interfaces (contract 12.2):**
```kotlin
interface TunDevice { fun read(buffer: ByteBuffer): Int; fun write(buffer: ByteBuffer, length: Int); fun close() }
interface SocketProtector {
    fun protect(socket: Socket): Boolean
    fun protect(socket: DatagramSocket): Boolean
    fun protect(channel: java.nio.channels.SelectableChannel): Boolean
}
interface OwnerResolver { fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int }
data class FlowInfo(val flowId: Long, val uid: Int, val protocol: Int, val local: InetSocketAddress, val remote: InetSocketAddress, val openedAt: Instant)
interface FlowListener {
    fun onFlowOpened(flow: FlowInfo)
    fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long)
    fun onName(flowId: Long, name: String)
    fun onDns(answers: List<DnsAnswer>)
    fun onFlowClosed(flowId: Long)
}
class Forwarder(
    tun: TunDevice,
    protector: SocketProtector,
    owner: OwnerResolver,
    listener: FlowListener,
    clock: java.time.Clock = java.time.Clock.systemUTC(),
    maxFlows: Int = 1024,
    mtu: Int = 1400,
) {
    fun run()
    fun stop()
    val evictedFlows: java.util.concurrent.atomic.AtomicLong
    val errors: java.util.concurrent.atomic.AtomicLong
}
```

**Design & Invariants:**
1. One reader thread reads the TUN; per-flow work for TCP/UDP uses `java.nio` selectors on ONE network thread (no thread per flow); writes to the TUN are serialised.
2. **TCP flow:**
   - On app's SYN: create flow, open non-blocking protected `SocketChannel` to destination and connect.
   - When connected: send SYN-ACK (MSS = `mtu - 40` for IPv4, `mtu - 60` for IPv6; window 65535, no window scaling; if connection fails answer with RST+ACK).
   - Client data -> write to socket (buffer max 256 KiB; if full stop acknowledging so app TCP slows down).
   - Socket data -> segments of at most MSS toward app, limited by app's advertised window and 256 KiB in flight.
   - Retransmission: unacknowledged segments retransmitted after RTO of 1 s (doubling, up to 5 attempts, then RST).
   - Sequence arithmetic modulo 2^32.
   - FIN from app -> half-close socket output, ACK; EOF from server -> FIN to app; RST either way tears down flow.
   - Both FINs acknowledged -> remove after 30 s. Idle flows closed after 5 minutes.
3. **UDP flow:**
   - One protected `DatagramChannel` per (src, dst) pair; datagrams forwarded both ways.
   - Responses written to TUN as UDP packets with original addressing swapped.
   - Idle timeout 60 s (10 s when destination port is 53).
4. **Limits:**
   - At most `maxFlows` (default 1024); when full, idle-longest flow is closed and `evictedFlows` increments.
   - Exception in a flow closes that flow only. An exception in loop increments `errors` and never ends `run()`.
5. **Observation:**
   - `FlowListener.onFlowOpened(FlowInfo)` once per flow (owner uid cached; -1 when unknown).
   - `onBytes(flowId, rx, tx)` for payload bytes as relayed (rx = toward app).
   - `onName(flowId, name)` when SNI of first client record is parsed.
   - `onDns(answers)` when DNS answers seen in UDP port 53.
   - `onFlowClosed(flowId)`.
   - Payload is never retained or logged.

**Requirements:**
- [ ] **Step 1: Write the failing tests.**
  - `FakeTun`: two `LinkedBlockingQueue<ByteArray>` implementing `TunDevice`, `close()` unblocks `read`.
  - `TcpClientSim`: tiny TCP client over `FakeTun` (active open, sends data, ACKs received data, FIN/RST, optional drop probability).
  - Local `ServerSocket`s (IPv4 `127.0.0.1` and IPv6 `::1` if supported, else skip IPv6 with `Assume`).
  - `ForwarderTcpTest`: handshake, 10 MB transfer byte-identical (SHA-256), 2 MB transfer with 5% packet drops, half-close, server closes first, server RST, connect to closed port -> RST+ACK, 200 parallel flows each 20 KiB, sequence wrap around 0xFFFFFF00, duplicate segment re-ACKed, out-of-order segment handling, zero window handling, IPv6 flow, SNI triggers `onName`, `onBytes` equals payload sizes, idle timeout closes flow with injected clock.
  - `ForwarderUdpTest`: UDP echo roundtrip (v4 and v6), DNS answer relays trigger `onDns`, responses from wrong source discarded, idle timeout (60s / 10s port 53), ICMP/malformed dropped without stopping `run()`.
  - `ForwarderLimitsTest`: limit set to 8, 9th flow evicts idle-longest and increments `evictedFlows`, 20,000 random garbage packets interleaved with normal transfer, `stop()` returns within 2s and closes all channels.
- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:lens:testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement** the nine files cleanly. Direct `ByteBuffer` pool for packet transfers in hot path.
- [ ] **Step 4: Run to verify they pass** — 3 consecutive runs of `./gradlew :feature:lens:testDebugUnitTest --rerun-tasks` must pass cleanly. Full gate passes.
- [ ] **Step 5: Commit** `feat(lens): relay TCP and UDP flows in userspace and report each flow's bytes and names`.

### Global Constraints & Rules:
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- Stage only files you changed by explicit path.
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com`.
- Commit message ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Privacy invariant: Payload bytes are never stored or logged.
