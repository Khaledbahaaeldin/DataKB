# Lens Mutation & Fuzzing Report

This document records the mutant tests, kill assertions, and structure-aware fuzzing strategies implemented for Emberbyte M5 lens forwarder and parsers (Task Q3).

---

## 1. Forwarder Mutant Kills

The table below catalogs surviving mutants identified during review and the exact tests added/verified to kill them.

| Mutant ID | File & Line Reference | Mutant Description | Killer Test | Status |
|:---|:---|:---|:---|:---:|
| **MUT-TCP-01** | `TcpFlow.kt:62` | Initial TCP sequence number wraps around `0xFFFFFFFFL` boundary without modulo arithmetic; upload/download desynchronizes | `ForwarderTcpTest > tcp_sequence_wraparound_upload_and_download_at_0xFFFFFFF0` | **KILLED** |
| **MUT-TCP-02** | `TcpFlow.kt:749` | Forwarder does not pause server download when client advertises receive window 0; sends data despite `sndWnd == 0` | `ForwarderTcpTest > client_window_zero_pauses_server_data` | **KILLED** |
| **MUT-TCP-03** | `TcpFlow.kt:737-744` | RTO does not double on timeout (1s, 2s, 4s, 8s, 16s) or does not emit RST and transition to `CLOSED` after retransmissions exhausted | `ForwarderTcpTest > rst_after_5_retransmissions_and_rto_doubling` | **KILLED** |
| **MUT-TCP-04** | `TcpFlow.kt:394-397` | Upstream buffer unconstrained or advertised window does not drop below 65535 when upload buffer fills | `ForwarderTcpTest > upload_buffer_256k_limit_and_advertised_window_zero` | **KILLED** |
| **MUT-TCP-05** | `TcpFlow.kt:841-846` | `TIME_WAIT` state expires prematurely before 30 seconds or fails to clean up dead flows after 30s | `ForwarderTcpTest > time_wait_expiry_30s_after_both_fins` | **KILLED** |
| **MUT-UDP-01** | `Forwarder.kt:294` | UDP DNS flow closes prematurely before 10s idle timeout (e.g. at 5s) or fails to close at 10s | `ForwarderUdpTest > udp_dns_flow_timeout_10s` | **KILLED** |
| **MUT-LIM-01** | `Forwarder.kt:130` | `Forwarder.stop()` fails to close all OS socket channels, specifically flows blocked in asynchronous connect | `ForwarderLimitsTest > stop_releases_all_sockets_including_flows_blocked_in_connect` | **KILLED** |
| **MUT-LIM-02** | `Forwarder.kt:47` | Owner cache (`BoundedLruCache`) unbounded, leaking memory past 4096 entries | `ForwarderLimitsTest > owner_cache_bounded_at_4096_entries` | **KILLED** |
| **MUT-PKT-01** | `PacketParser.kt:98-124` | IPv6 extension headers (Hop-by-Hop 0, Routing 43, Destination Options 60) incorrectly dropped instead of skipped | `PacketParserTest > ipv6_extension_headers_hopbyhop_routing_destopts_skipped` | **KILLED** |
| **MUT-PKT-02** | `PacketParser.kt:120` | IPv6 Fragment header (protocol 44) parsed instead of being dropped | `PacketParserTest > ipv6_fragment_header_dropped` | **KILLED** |
| **MUT-PKT-03** | `PacketParser.kt:143` | Truncated TCP header (< 20 bytes) accepted instead of dropped | `PacketParserTest > truncated_tcp_header_dropped` | **KILLED** |
| **MUT-DNS-01** | `DnsParser.kt:190` | DNS names with wire representation > 255 bytes accepted instead of dropped | `DnsParserTest > dnsNameOver255Bytes_dropped` | **KILLED** |
| **MUT-DNS-02** | `DnsParser.kt:183` | DNS pointer compression loop / jump count limit (> 16 jumps) not enforced | `DnsParserTest > pointerJumpLimit_16_enforced` | **KILLED** |
| **MUT-DNS-03** | `DnsParser.kt:55` | DNS responses with > 64 answers parsed past the 64-answer cap | `DnsParserTest > answerCap_64_answers_enforced` | **KILLED** |

---

## 2. Structure-Aware Fuzzing Across Parsers

For each parser, structure-aware fuzzers were added to complement (and preserve) purely random byte fuzzers.

### 2.1 PacketParser (`PacketParserTest.kt`)
- **Seeded Structures**:
  - Valid IPv4 TCP (HTTP GET payload)
  - Valid IPv4 UDP (DNS query payload)
  - Valid IPv6 TCP
  - Valid IPv6 UDP
- **Mutation Engine**:
  - 20,000 iterations seeded with deterministic pseudo-random generator (`Random(12345L)`).
  - Structure-aware mutations: bit flips (1..4 bits), byte substitution, random truncations (1..40 bytes), header length mutations (IHL, total length, IPv6 payload length, TCP data offset), and random insertions/deletions.
- **Assertions**:
  - `assertNotNull/no unhandled exceptions` for all 20,000 iterations.
  - Non-null parsed rate $\ge 30\%$ verified (actual $\approx 40\text{--}55\%$), confirming mutations preserve realistic header structures.

### 2.2 DnsParser (`DnsParserTest.kt`)
- **Seeded Structures**:
  - Valid DNS response with standard A record (`example.com -> 93.184.216.34`)
  - Valid DNS response with CNAME alias chain (`alias.example.com -> target.example.com -> 1.2.3.4`)
  - Valid DNS response with AAAA IPv6 record (`ipv6.example.com -> 2606:2800:220:1:248:1893:25c8:1946`)
- **Mutation Engine**:
  - 20,000 iterations (`Random(54321L)`).
  - Mutates ID, flags (QR, Opcode, AA, TC, RD, RA, RCODE), question/answer counts, label lengths, pointers (offset corruptions), TTL, and RDATA bytes.
- **Assertions**:
  - 0 unhandled exceptions across all 20,000 runs.
  - $\ge 30\%$ non-null/non-empty answer yield maintained.
  - Preserved raw random byte array fuzzer (`fuzzTest_rawRandomByteArrays`, 5,000 iterations).

### 2.3 SniParser (`SniParserTest.kt`)
- **Seeded Structures**:
  - Valid TLS 1.2 ClientHello (`example.com`)
  - Valid TLS 1.3 ClientHello (`sub.example.com`)
  - Valid ClientHello with long domain name (`long-domain-name-for-testing-purposes.com`)
- **Mutation Engine**:
  - 20,000 iterations (`Random(98765L)`).
  - Mutates TLS record header (content type, legacy version, record length), Handshake header (type, length), session ID lengths, cipher suite lengths, extension list lengths, SNI extension type/lengths, server name type, and server name string bytes.
- **Assertions**:
  - 0 unhandled exceptions across all 20,000 runs.
  - $\ge 30\%$ non-null server name yield maintained.
  - Preserved raw random byte array fuzzer (`fuzzRawByteArrays`, 5,000 iterations).
