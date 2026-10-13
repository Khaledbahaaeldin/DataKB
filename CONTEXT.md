# Emberbyte (DataKB)

Native Android mobile data-usage monitor and policy manager with Material 3 UI, providing real-time cellular and Wi-Fi accounting, period tracking, and quota alerts.

## Language

### Core Tracking & Accounting

**DataEntry**:
A recorded discrete data-usage measurement for a specific network interface, subscriber, and time interval.
_Avoid_: UsageRecord, TrafficStat, NetworkLog

**Bucket**:
An aggregated time window of data usage categorized by transport type and subscriber identity.
_Avoid_: Chunk, UsageBin, TimeWindow

**Plan**:
A user-configured cellular or Wi-Fi data quota with a defined allocation limit, billing cycle, and renewal schedule.
_Avoid_: Subscription, Package, Tariff

**Cycle**:
The active recurring date period during which a data plan's quota is tracked before resetting.
_Avoid_: BillingPeriod, ResetInterval, QuotaSpan

**Policy**:
A configured alert or enforcement threshold triggering notifications or warnings when plan usage reaches a defined percentage.
_Avoid_: Rule, Trigger, LimitAction

### Connectivity & Hardware

**SimSlot**:
The physical or electronic SIM slot index (e.g., slot 0 or 1) representing a distinct cellular subscription on dual-SIM devices.
_Avoid_: SimCard, Line, ModemPort

**Session**:
A contiguous interval of active network connectivity across a single network transport interface.
_Avoid_: Connection, Tunnel, Flow
