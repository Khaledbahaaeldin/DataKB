# 0001: Defer Pure-Kotlin Forwarder for Live Lens (Milestone 5)

## Status
Accepted

## Context
Milestone 5 (Live Lens) explored whether a local Android `VpnService` forwarder could be built purely in user-space Kotlin using `java.nio` non-blocking sockets and an in-process TCP state machine. The spike fix wave (Tasks Q1–Q5) resolved selector spin, unacknowledged FIN handling, bidirectional TCP window flow control, SNI fragmentation reassembly, and buffer allocation overhead. However, empirical benchmarking on device runtime ART revealed that processing user-space TCP state, checksum recalculations, and NIO selector context switching incurs 12.30 to 17.44 CPU-seconds per 100 MiB of transferred data (against the budgeted threshold of <= 2.0 CPU-seconds per 100 MiB).

## Decision
We decided to accept the spike verdict **`NATIVE NEEDED`** and **defer** the Live Lens feature module from production release. The forwarder spike code and test suite remain isolated within `:feature:lens`, while production builds and the application UI remain scoped to Milestone 4 (Insight) functionality. When Live Lens is resumed in the future, it must be implemented via a native C/Rust packet engine (e.g. lwIP / tun2socks) rather than a JVM user-space stack.

## Consequences
- The application remains clean, stable, and battery-friendly without high CPU overhead from JVM packet translation.
- Release compilation (`compileReleaseKotlin`) contains no spike receivers or debug activities.
- Future work on Live Lens will require JNI bindings and native compilation rather than extending the Kotlin engine.
