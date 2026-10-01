# Emberbyte Backend Spec (on-device engine and data layer)

Status: draft for review. Names and signatures come from the
[API contract](2026-10-01-datakb-api-contract.md). Companion: [frontend spec](2026-10-01-datakb-frontend-spec.md).

Emberbyte has no server. "Backend" means everything below the UI: `:core:engine`, `:core:data`,
`:feature:lens`, and the Android services and workers that feed them. Nothing leaves the device.

## 1. Module responsibilities

| Module | Type | Owns | Depends on |
|---|---|---|---|
| `:core:engine` | Kotlin/JVM, no Android | Domain types, cycle/plan/forecast/spike/reconcile maths | nothing |
| `:core:data` | Android library | Room, DataStore, repositories, samplers, workers, notifications, export/backup | `:core:engine` |
| `:feature:lens` | Android library | `LensVpnService`, packet pipeline, `LensController`, `lens_domain_hit` | `:core:engine`, `:core:data` (settings and permissions only) |
| `:app` | Android app | Dependency graph (hand-written in M1, Hilt from M2), manifest, widgets, navigation host | all |

`:core:engine` having no Android dependency is a hard rule: it keeps the money-critical maths
(cycles, caps, forecast) runnable in fast JVM unit tests.

## 2. Data sources and what each one can and cannot do

| Source | Gives | Limits |
|---|---|---|
| `TrafficStats` (total mobile/all rx/tx) | Cheap system-wide cumulative counters, polled at 1 Hz for live speed | Per-UID counters for other apps are not readable on API 24+, so it is used for totals only |
| `NetworkStatsManager` | Per-UID history in time buckets, split mobile/Wi-Fi | Needs Usage Access; buckets are coarse (minutes); no reliable per-SIM key (see §5) |
| `UsageStatsManager` | Per-app foreground time | Needs Usage Access |
| `ConnectivityManager` | Active network kind, metered flag, default-network callbacks | Network kind only |
| `SubscriptionManager` | Default-data subscription id, SIM list | Needs `READ_PHONE_STATE` |
| Lens tunnel (opt-in) | Per-connection owner (UID), bytes, DNS and TLS SNI names | Only while Lens runs; encrypted DNS/ECH hide names (§7) |

## 3. Sampling pipeline

```
TrafficStats ──1 Hz──► LiveTrafficSource ──► in-memory LiveSpeed (not persisted)
                              │
                              └─ every 60 s ─► CounterReconciler ─► total_minute (per network, per SIM)

NetworkStatsManager ─ CatchUpWorker (15 min) ─► usage_hourly (per UID) ─► reconcile against total_minute
```

1. **Live speed.** `SamplerService` reads cumulative counters each second, differences them with
   `CounterReconciler`, smooths with a 3-sample EMA, and publishes `LiveSpeed`. Network kind comes from the
   current default network callback; `null` means offline.
2. **Minute totals.** The service accumulates deltas and writes one `total_minute` row per network and
   subscription each minute (or on network change, or on service stop). These feed "today" and the Number
   with minute freshness and no `NetworkStatsManager` latency.
3. **Per-app history.** `CatchUpWorker` calls `NetworkStatsManager.querySummary` per network type for the
   window since its last checkpoint and writes `usage_hourly`. The per-hour total for all UIDs is compared
   with the matching `total_minute` sum. If they disagree by more than 5%, the per-app rows are scaled
   proportionally so app totals add up to the trusted system total.
4. **Counter resets and reboots.** `CounterReconciler.delta` treats any decrease or a changed `bootId` as a
   reset: the new reading is the delta, `wasReset = true`, and a `coverage_gap` with
   `COUNTER_RESET` or `REBOOT` is recorded.
5. **Gaps.** If `SamplerService` is killed, `CatchUpWorker` detects the missing minutes, records a
   `SERVICE_KILLED` gap, and backfills per-app data from `NetworkStatsManager`. Minute totals inside a gap are
   not invented; `observeCoverage()` exposes the gap and the UI states it honestly.

## 4. Plan engine

All functions are in `:core:engine` and take plain data.

- **`CycleCalculator.windowAt`.** `MonthlyOnDay` clamps day 29–31 to the month's last day and uses the
  plan's zone and time. `EveryNDays` steps whole days from the anchor. A plan edit never rewrites past
  windows: usage is always re-queried per window.
- **`PlanCalculator.state`.**
  `effectiveCap = capBytes + rolledOverBytes + sum(active addOns in window)`.
  `usedBytes = totalInWindow − freeBytes`.
  `freeBytes` sums slices that fall inside a `FreeRule` (day set, time window including windows that
  cross midnight, and optional package scope).
  `remaining = max(0, effectiveCap − usedBytes)`.
  `rolledOverBytes` per `Rollover`: `None` = 0; `Full` = unused bytes of the previous window;
  `Capped(max)` = `min(unused, max)`. Add-ons are not part of rollover.
- **Free windows and Wi-Fi.** Only mobile traffic on the plan's subscription counts toward a plan.
  Wi-Fi never counts.
- **Alert rules.** `AlertWorker` fires when `fractionUsed` first crosses a rule's `thresholdPercent` in a
  window. It stores the last-fired percent per plan and window, so each threshold fires once per cycle.

## 5. Dual-SIM and the attribution constraint

Android 10+ withholds the subscriber id (IMSI) that `NetworkStatsManager` uses to separate SIMs from
non-privileged apps. So per-SIM history cannot be queried directly. Emberbyte handles this as follows:

1. **Live and minute data is attributed per SIM.** When the sampler flushes a mobile delta it tags it with
   `SubscriptionManager`'s current default-data subscription id. This is accurate for as long as the app
   runs.
2. **Per-app history is system-wide for mobile.** `usage_hourly` for mobile is queried without a
   subscriber and attributed to the default-data subscription in effect for each hour, using the
   `total_minute` record of which SIM was active.
3. **Honesty flag.** Whenever a plan's window includes hours where more than one SIM was used, or sampler
   coverage is incomplete, `PlanState.isApproximate = true` and the UI shows an "estimated" marker.
4. **Verification gate.** M2 starts with a spike that confirms this behaviour on a real dual-SIM device. If
   the platform exposes a better key, §5 changes and `isApproximate` becomes rarer; the contract does not.

## 6. Forecast engine

`ForecastEngine.forecast(state, dailyHistory, now, zone)`:

1. Take daily mobile usage for the last 28 days (fewer allowed).
2. Compute a day-of-week profile: the mean and standard deviation per weekday, shrunk toward the overall
   mean when a weekday has under 3 samples.
3. Roll the remaining days of the cycle forward day by day with the profile; today's partial usage is
   extrapolated from the hour-of-day curve.
4. Run expected, low (mean − 1σ) and high (mean + 1σ) projections. `runOutEarliest/Expected/Latest` are the
   instants at which each projection crosses `remainingBytes`, or `null` if it never does this cycle.
5. `confidence`: `LOW` under 7 days of history, `MEDIUM` 7–20 days, `HIGH` 21 or more days and a
   coefficient of variation under 0.6. The UI shows a range, never a single falsely precise date.
6. `PlanRepository.whatIf` re-runs the same projection with `extraBytesPerDay` added from `from`. It is
   pure and not persisted.

## 7. Live Lens (`:feature:lens`)

**Intent.** Show which app is using data right now and which domains it contacts, entirely on-device.

**Pipeline.**
```
App traffic ─► TUN interface ─► packet loop ─► per-flow table ─► forward via protect()ed sockets ─► network
                                  │
                                  ├─ owner UID: ConnectivityManager.getConnectionOwnerUid(...)
                                  ├─ names: DNS queries/answers + TLS ClientHello SNI
                                  └─► LensSnapshot (1 Hz) and lens_domain_hit (hourly rows)
```

- The tunnel is passthrough: it forwards all traffic to the real network with sockets excluded from the
  VPN by `protect()`. Lens never alters, blocks or stores payload.
- Per-flow table maps (protocol, local, remote) to UID and an accumulating byte count; 1 Hz diffs produce
  `rxBps`/`txBps` per app.
- Domains come from observed DNS answers (name ↔ IP) and TLS SNI. Where DNS-over-HTTPS or ECH hides the
  name, the app falls back to the remote IP and shows it as such.
- **Isolation.** Lens runs in `LensVpnService`, a foreground service, started only by
  `LensController.start()` after the user accepts the system VPN dialog.
- **Conflicts.** If another VPN is active, or Always-on VPN with lockdown is set, state becomes
  `Conflict(...)` and nothing starts.
- **Retention.** `lens_domain_hit` rows older than `Settings.lensHistoryHours` are deleted hourly and on
  `clearHistory()`.
- **Implementation risk.** A userspace packet forwarder is the largest single piece of work. M5 begins with
  a spike comparing writing the forwarder in Kotlin against a small native (C/Rust) forwarder, and reading
  prior GPL-3.0 VPN-based firewalls for design lessons. The decision and its battery measurements are
  recorded in `docs/` before the build.

## 8. Alerts, budgets and spikes

- **Budgets.** `AlertWorker` computes each `AppBudget` against `usage_hourly` for its period. State:
  `OK` below `warnAtPercent`, `WARNING` from there to 100%, `EXCEEDED` above. Transitions post one
  notification each. The notification action opens that app's system data-usage settings.
- **Spikes.** After each flush, `SpikeDetector` compares each app's last 20 minutes with its median 20-minute
  usage over the previous 14 days. A spike needs at least 100 MB and 5× the baseline, and apps with fewer
  than 3 days of history are skipped. A detected spike is stored once, and one app can alert at most once
  per hour.

## 9. Notifications and widgets

- **Live notification** (`live` channel): ongoing; title is today's usage, text is remaining data and speed
  when `notificationShowsSpeed`. On Android 16+ it uses the progress-style (live update) template;
  earlier versions use a standard ongoing notification. Updated at most once per second.
- **Widgets** read repositories through `HeroWidget` and `BentoWidget` (contract §6). They are never the
  only holder of state: killing the app does not blank them.

## 10. Permissions and manifest

| Permission | Why | When requested |
|---|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access, via settings screen) | Per-app history and screen time | Onboarding, with explanation; the app works with totals only until granted |
| `POST_NOTIFICATIONS` (API 33+) | Live notification and alerts | After first plan is created |
| `READ_PHONE_STATE` | Default-data SIM id for dual-SIM attribution | When the user adds a SIM-bound plan; optional |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | `SamplerService` | Install time |
| `BIND_VPN_SERVICE` on `LensVpnService`, plus system VPN consent | Live Lens | Only when the user enables Lens |
| `RECEIVE_BOOT_COMPLETED` | Restart the sampler after reboot | Install time |
| `INTERNET` | Required by the VPN to forward traffic; used by nothing else | Install time |

Foreground service types must be verified on a target API 34+ device during M2 (including the Android 15
timeout behaviour of `dataSync`, which is why `specialUse` is chosen). If the platform changes the
allowed type for a VPN service, update this table and the manifest together.

## 11. Error handling

| Situation | Behaviour |
|---|---|
| Usage Access missing | `Outcome.Failure(MissingUsageAccess)` from per-app refresh; totals continue; UI shows an inline prompt |
| `READ_PHONE_STATE` missing | SIM-bound plans use the default subscription and set `isApproximate` |
| Sampler killed by OS | `CatchUpWorker` restarts it and records a `SERVICE_KILLED` gap |
| Counter decrease | Treated as reset, gap recorded, no negative or inflated delta |
| Plan with invalid cycle | `upsertPlan` returns `Invalid(field, reason)`; nothing is saved |
| Room migration failure | App opens a recovery screen offering export of the last backup; never silently wipes data |
| Backup wrong passphrase | `Invalid("passphrase", …)`; database untouched |

## 12. Testing

- **`:core:engine` (JVM):** table-driven tests for month-end clamping, DST transitions, add-on overlap,
  rollover modes, free windows crossing midnight, counter reset, forecast confidence bands, and spike
  thresholds. No Android needed.
- **`:core:data`:** Room in-memory tests for every DAO and migration (exported schemas), repository tests
  with a fake clock and fake data sources, and a `SamplerService` test with scripted counter readings.
- **`:feature:lens`:** packet-pipeline unit tests on captured sample packet bytes (DNS, TLS ClientHello),
  and a manual device checklist for conflicts and teardown.
- **Fake data source:** a seedable `FakeUsageSource` generates realistic weeks of data for demos,
  screenshot tests and M1.
- **CI:** all of the above except the manual Lens checklist run on every push.

## 13. Open verification items (resolved by spikes, not by guessing)

1. Per-SIM behaviour on API 29+ for non-privileged apps (§5), tested in M2.
2. Allowed foreground service type for the VPN service and the sampler on API 34+ (§10), tested in M2.
3. Kotlin versus native packet forwarder for Lens (§7), decided in M5.
4. Whether `getConnectionOwnerUid` is reliable across vendor ROMs for Lens (§7), tested in M5.
