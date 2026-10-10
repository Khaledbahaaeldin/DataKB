# Task S1 Brief: Packet and name parsers (`…lens.core`)

### Source: Task S1 of `docs/superpowers/plans/2026-10-11-emberbyte-m5-live-lens.md`

**Files:**
- Modify: `feature/lens/build.gradle.kts` (add `testImplementation(libs.junit)`; no Android dependency is needed by `…lens.core`)
- Create:
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/PacketParser.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/ParsedPacket.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/Checksums.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/DnsParser.kt`
  - `feature/lens/src/main/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/SniParser.kt`
- Test:
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/PacketParserTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/ChecksumsTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/DnsParserTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/SniParserTest.kt`
  - `feature/lens/src/test/kotlin/io/github/khaledbahaaeldin/emberbyte/lens/core/PacketBuilder.kt`

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

**Requirements:**

- [ ] **Step 1: Write the failing tests.**
  In test sources write `PacketBuilder` (test helper, also reused by S2): builds valid IPv4 and IPv6 packets carrying TCP or UDP with correct checksums from `(src, dst, flags, seq, ack, payload, options)`; helpers `dnsResponse(id, question, answers)` (encode names with and without compression pointers) and `clientHello(serverName, extensions)` with this exact layout: record `16 03 01 <len2>`; handshake `01 <len3>`; `03 03` + 32 random bytes; session-id length 1 byte + id; cipher-suite length 2 bytes + suites (`13 01`); compression length 1 byte + `00`; extensions length 2 bytes + extensions, each `<type2><len2><data>`; the SNI extension (`00 00`) data = `<listLen2> 00 <nameLen2> <name>`. Then:
  * `PacketParserTest`: an IPv4 TCP SYN with an MSS option parses to the right addresses, ports, seq, flags `SYN`, window, `mss`; an IPv6 TCP segment likewise; a UDP packet (v4 and v6) gives payload offset/length; an IPv4 packet WITH IP options (IHL 6) is parsed at the right payload offset; TCP data offset above 5 is honoured; **dropped (null):** wrong IP version, length field larger than the buffer, total length shorter than the header, IPv4 header checksum wrong, TCP/UDP checksum wrong (not for UDP checksum 0 on IPv4, which means "none"), fragmented IPv4 (MF flag or fragment offset non-zero), protocol ICMP/other, truncated TCP/UDP header, IPv6 with an extension header chain it cannot skip (hop-by-hop, routing and destination options headers ARE skipped; fragment header is dropped); an empty buffer and 1-byte buffer give null without an exception; a fuzz test feeds 20,000 random byte arrays (seeded) and asserts no exception and that every non-null result lies inside the buffer.
  * `ChecksumsTest`: RFC 1071 examples: the IPv4 header `45 00 00 73 00 00 40 00 40 11 b8 61 c0 a8 00 01 c0 a8 00 c7` has checksum field `0xb861` and `ipv4Header` over it with the checksum zeroed returns `0xb861`; odd-length payload; a pseudo-header checksum for IPv6.
  * `DnsParserTest`: the hex message `1234818000010001000000000765 78616d706c6503636f6d0000010001c00c00010001 0000012c00045db8d822` (remove spaces) gives exactly one answer `example.com` -> `93.184.216.34`, TTL 300; an AAAA answer; a CNAME chain (`www.example.com` CNAME `cdn.example.net` then A) yields the address with the QUESTION name AND the CNAME names recorded (`answers` returns one entry per address with `name` = the first name in the chain, which is the name the app asked for); compression pointers inside answers; a pointer loop and a pointer past the end terminate without an exception; truncated message, zero answers, a query (QR bit 0), RCODE != 0, and a name longer than 255 bytes give `emptyList()`; TTL 0 is returned as 0 (the caller clamps).
  * `SniParserTest`: a ClientHello with SNI `example.com` returns it; GREASE extensions (`0a0a`) before and after, many extensions, SNI not first, two names in the list (first host_name type 0 wins), upper-case input returns lower case; **null:** no SNI extension, truncated record, a record that is not a handshake, handshake type not ClientHello, an SNI longer than 255 or containing non-ASCII bytes or spaces, and a ClientHello split across two records (only the first record is given: returns null); 5,000 random byte arrays do not throw.
- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:lens:testDebugUnitTest` → FAIL (classes missing).
- [ ] **Step 3: Implement** the five files. Key rules: all reads are bounds-checked before use (a helper `ByteBuffer.getU8/getU16/getU32` returning `-1` or throwing a private `Malformed` that `parse` converts to `null`); IPv4 header length is `IHL * 4` (>= 20); fragments are dropped (documented limit); IPv6: fixed 40-byte header then skip extension headers 0, 43, 60 (length in 8-byte units + 8) until TCP (6) or UDP (17); verify checksums with `Checksums`; `src`/`dst` are `InetSocketAddress(InetAddress.getByAddress(bytes), port)` (never resolve); `DnsParser` limits: at most 64 answers, at most 16 pointer jumps per name; `SniParser` accepts only host_name entries made of `[A-Za-z0-9.-_]`, lower-cases the result.
- [ ] **Step 4: Run to verify they pass** — gate green.
- [ ] **Step 5: Commit** `feat(lens): parse IP, TCP, UDP, DNS and TLS server names without trusting the input`.

### Global Constraints & Rules:
- Never run `git checkout` on files, `git restore`, `git stash`, `git clean`, `git reset` (any form) or `git add .` / `git add -A`.
- Never touch anything under `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- Stage only files you changed by explicit path.
- Identity: `user.name` = `Omar Amin`, `user.email` = `omarsadiq2012@gmail.com`.
- Commit message ends with `Co-Authored-By: Gemini (Antigravity) <noreply@google.com>`.
- Privacy invariant: Payload bytes are never stored or logged.
