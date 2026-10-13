# 0001: Discard Live Lens (milestone 5)

## Status
Accepted

## Context
Live Lens was meant to show which app uses data right now and which servers it contacts. Android offers no API for
that to ordinary apps: per-app live counters are not readable for other apps, and destinations and DNS lookups are
not exposed. The only unprivileged route is a local `VpnService` that forwards every packet itself.

Milestone 5 therefore started with a spike: a user-space forwarder in Kotlin (`java.nio` sockets plus our own TCP
state machine). After an audit and a fix wave (end-of-stream spin, acknowledging unrelayed data, both TCP windows,
SNI across segments, allocations), the spike record on branch `m5-live-lens`
(`docs/spikes/lens-forwarder.md`) measured **12.30 to 17.44 CPU-seconds per 100 MiB** transferred, against a budget of
**2.0**, and a decision of `NATIVE NEEDED`.

## Decision
Discard the feature. Emberbyte does not use `VpnService`, has no `:feature:lens` module and ships no Lens screen,
deep link, setting, permission flag or notification channel. A native (C/Rust) packet engine would be a separate,
much larger project (JNI, an NDK build in CI, a licence review) and is not justified for an opt-in extra.

The spike code, its tests, the audit and QA records stay readable on the `m5-live-lens` branch and are not part of
`main`.

## Consequences
- The app stays small, battery-friendly and free of any network-facing code. It declares no `INTERNET` permission.
- Per-app history still comes from `NetworkStatsManager` in 2-hour buckets; a live per-app speed and per-domain view
  do not exist.
- A VPN running over Wi-Fi still has its tunnel bytes counted twice (a documented limitation).
- Milestone numbering is unchanged: M5 is "discarded" and the next milestone is M6 (polish).
- Reviving the idea means starting from a native engine, not from the Kotlin forwarder.
