# Emberbyte API Contract (v1)

Status: draft for review. Source of truth for every name used in the
[frontend spec](2026-10-01-datakb-frontend-spec.md) and
[backend spec](2026-10-01-datakb-backend-spec.md). If a name differs between documents, this file wins.

Emberbyte has no server. The "API" is the set of typed boundaries between modules, plus the Android
component, storage and file-format contracts. Root package: `io.github.khaledbahaaeldin.emberbyte`
(omitted below; written `…`).

## 1. Conventions

- Kotlin, `java.time` types (minSdk 29 supports them natively), `kotlinx.coroutines.flow.Flow`.
- `Bytes` is `Long` (always bytes, never KB/MB). Rates are `Long` bytes per second.
- Time is `Instant` for points in time and `LocalTime`/`DayOfWeek`/`ZoneId` for plan rules.
- **Observation** methods return `Flow<T>`. They never throw and never complete on error; problems are
  modelled in `T` (see `CoverageStatus`, `PermissionState`).
- **One-shot** methods are `suspend` and return `Outcome<T>`. They never throw for expected failures.
- Module dependency direction (no cycles): `:core:engine` ← `:core:data` ← `:app`;
  `:ui:design` depends on nothing in the domain (it takes primitives and its own UI models);
  `:app` depends on all.

```kotlin
const val CONTRACT_VERSION = 1

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: EmberbyteError) : Outcome<Nothing>
}

sealed interface EmberbyteError {
    data object MissingUsageAccess : EmberbyteError
    data object MissingPhoneState : EmberbyteError
    data object NotFound : EmberbyteError
    data class Invalid(val field: String, val reason: String) : EmberbyteError
    data class Storage(val cause: String) : EmberbyteError
    data class Unexpected(val cause: String) : EmberbyteError
}
```

## 2. Domain types (module `:core:engine`, pure Kotlin/JVM)

```kotlin
enum class NetworkKind { MOBILE, WIFI }

// ---- Plans ----
data class Plan(
    val id: Long,                       // 0 = not yet persisted
    val name: String,
    val subscriptionId: Int?,           // Android subscription id; null = not bound to a SIM
    val capBytes: Long,
    val cycle: Cycle,
    val rollover: Rollover,
    val archived: Boolean = false,
)

sealed interface Cycle {
    /** Renews on the same day each month; days beyond month length clamp to the last day. */
    data class MonthlyOnDay(val dayOfMonth: Int /*1..31*/, val time: LocalTime, val zone: ZoneId) : Cycle
    /** Renews every N days from an anchor date. */
    data class EveryNDays(val days: Int /*1..366*/, val anchor: LocalDate, val time: LocalTime, val zone: ZoneId) : Cycle
}

sealed interface Rollover {
    data object None : Rollover
    data object Full : Rollover
    data class Capped(val maxBytes: Long) : Rollover
}

data class AddOn(
    val id: Long, val planId: Long, val label: String,
    val bytes: Long, val validFrom: Instant, val validUntil: Instant,
)

/** Traffic matching a rule is NOT counted against the plan. A window may cross midnight. */
data class FreeRule(
    val id: Long, val planId: Long, val label: String,
    val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime,
    val packageName: String?,           // null = all apps
)

data class CycleWindow(val start: Instant, val end: Instant)

data class PlanState(
    val plan: Plan,
    val window: CycleWindow,
    val effectiveCapBytes: Long,        // cap + rolledOver + active add-ons
    val usedBytes: Long,                // after removing free-rule traffic
    val remainingBytes: Long,           // never negative
    val rolledOverBytes: Long,
    val addOnBytes: Long,
    val freeBytes: Long,                // traffic excluded by FreeRules
    val daysLeft: Int,
    val fractionUsed: Float,            // usedBytes / effectiveCapBytes, may exceed 1f
    val isApproximate: Boolean,         // true when per-SIM attribution is estimated (see backend §5)
)

// ---- Forecast ----
enum class Confidence { LOW, MEDIUM, HIGH }

data class Forecast(
    val planId: Long,
    val runOutEarliest: Instant?,       // all three null => projected NOT to run out this cycle
    val runOutExpected: Instant?,
    val runOutLatest: Instant?,
    val projectedCycleEndBytes: Long,
    val confidence: Confidence,
    val historyDays: Int,
)

// ---- Usage ----
data class LiveSpeed(val rxBps: Long, val txBps: Long, val network: NetworkKind?, val at: Instant)
// network == null means offline.

data class UsageFilter(val network: NetworkKind? = null, val subscriptionId: Int? = null)

enum class Granularity { HOUR, DAY, WEEK, MONTH }

data class DateRange(val from: Instant, val to: Instant)

data class UsagePoint(
    val start: Instant, val mobileBytes: Long, val wifiBytes: Long,
) { val totalBytes get() = mobileBytes + wifiBytes }

data class DayUsage(
    val date: LocalDate, val mobileBytes: Long, val wifiBytes: Long,
) { val totalBytes get() = mobileBytes + wifiBytes }

data class AppUsage(
    val packageName: String, val label: String, val uid: Int,
    val mobileBytes: Long, val wifiBytes: Long, val screenTimeMs: Long?,
) { val totalBytes get() = mobileBytes + wifiBytes }

enum class AppSort { BYTES_DESC, BYTES_ASC, NAME, SCREEN_TIME_DESC }

// ---- Alerts, budgets, spikes ----
data class AlertRule(val id: Long, val planId: Long, val thresholdPercent: Int /*1..100*/, val enabled: Boolean)

enum class BudgetPeriod { DAILY, CYCLE }

data class AppBudget(
    val id: Long, val packageName: String, val limitBytes: Long,
    val period: BudgetPeriod, val planId: Long?, val warnAtPercent: Int = 80, val enabled: Boolean = true,
)

data class BudgetStatus(val budget: AppBudget, val usedBytes: Long, val state: BudgetState)
enum class BudgetState { OK, WARNING, EXCEEDED }

data class SpikeEvent(
    val id: Long, val packageName: String, val detectedAt: Instant,
    val windowMinutes: Int, val bytes: Long, val baselineBytes: Long,
    val multiple: Float, val dismissed: Boolean,
)

// ---- Data health ----
data class CoverageGap(val from: Instant, val to: Instant, val reason: GapReason)
enum class GapReason { SERVICE_KILLED, REBOOT, COUNTER_RESET, PERMISSION_MISSING, DEVICE_ASLEEP }   // DEVICE_ASLEEP added in the M2 fix wave

data class CoverageStatus(val gaps: List<CoverageGap>, val lastSampleAt: Instant?)
```

### Engine functions (pure, deterministic, all unit-tested)

```kotlin
object CycleCalculator {
    fun windowAt(cycle: Cycle, at: Instant): CycleWindow
    fun previousWindow(cycle: Cycle, window: CycleWindow): CycleWindow
}

object PlanCalculator {
    fun state(
        plan: Plan, at: Instant,
        usage: List<UsageSlice>,        // already scoped to plan window + subscription
        addOns: List<AddOn>, freeRules: List<FreeRule>,
        previousCycleUnusedBytes: Long, isApproximate: Boolean,
    ): PlanState
}
data class UsageSlice(val start: Instant, val end: Instant, val packageName: String?, val bytes: Long)

object ForecastEngine {
    fun forecast(
        state: PlanState, dailyHistory: List<DayUsage>, now: Instant, zone: ZoneId,
    ): Forecast
}

object SpikeDetector {
    fun detect(
        recent: List<UsageSlice>, baselinePerWindowBytes: Map<String, Long>,
        windowMinutes: Int = 20, minBytes: Long = 100_000_000, minMultiple: Float = 5f,
    ): List<SpikeEvent>
}

object CounterReconciler {
    /** Turns raw cumulative counter readings into non-negative deltas; see section 9.1 for the exact reset rules. */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta
}
data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)
data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)
```

## 3. Repository contract (module `:core:data`, public API)

All repositories are interfaces with a Room/DataStore-backed implementation, provided by the hand-written `AppGraph` (no DI framework).

```kotlin
interface UsageRepository {
    fun observeLiveSpeed(): Flow<LiveSpeed>
    fun observeToday(filter: UsageFilter = UsageFilter()): Flow<DayUsage>
    fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter = UsageFilter()): Flow<List<UsagePoint>>
    fun observeApps(range: DateRange, filter: UsageFilter = UsageFilter(), sort: AppSort = AppSort.BYTES_DESC): Flow<List<AppUsage>>
    fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>>
    fun observeCoverage(): Flow<CoverageStatus>
    suspend fun refreshNow(): Outcome<Unit>
}

interface PlanRepository {
    fun observePlans(includeArchived: Boolean = false): Flow<List<Plan>>
    fun observePlanState(planId: Long): Flow<PlanState?>         // null if plan missing
    fun observeActivePlanStates(): Flow<List<PlanState>>          // one per non-archived plan
    fun observeForecast(planId: Long): Flow<Forecast?>
    fun observeAddOns(planId: Long): Flow<List<AddOn>>
    fun observeFreeRules(planId: Long): Flow<List<FreeRule>>
    suspend fun upsertPlan(plan: Plan): Outcome<Long>
    suspend fun archivePlan(planId: Long): Outcome<Unit>
    suspend fun upsertAddOn(addOn: AddOn): Outcome<Long>
    suspend fun deleteAddOn(id: Long): Outcome<Unit>
    suspend fun upsertFreeRule(rule: FreeRule): Outcome<Long>
    suspend fun deleteFreeRule(id: Long): Outcome<Unit>
    /** Cap-what-if: forecast with extra daily usage added from `from` onward. Not persisted. */
    suspend fun whatIf(planId: Long, extraBytesPerDay: Long, from: Instant): Outcome<Forecast>
    /** M3: state and forecast of an UNSAVED plan draft (the plan editor's live preview). Pure, not persisted. */
    suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview>
}

interface AlertRepository {
    fun observeRules(planId: Long): Flow<List<AlertRule>>
    suspend fun upsertRule(rule: AlertRule): Outcome<Long>
    suspend fun deleteRule(id: Long): Outcome<Unit>
}

interface BudgetRepository {
    fun observeStatuses(): Flow<List<BudgetStatus>>
    suspend fun upsert(budget: AppBudget): Outcome<Long>
    suspend fun delete(id: Long): Outcome<Unit>
}

interface SpikeRepository {
    fun observeSpikes(includeDismissed: Boolean = false): Flow<List<SpikeEvent>>
    suspend fun dismiss(id: Long): Outcome<Unit>
}

data class Settings(
    val liveNotificationEnabled: Boolean = true,
    val notificationShowsSpeed: Boolean = true,
    val spikeAlertsEnabled: Boolean = true,
    val amoledBlack: Boolean = false,
    val useDynamicColor: Boolean = true,
    val unitSystem: UnitSystem = UnitSystem.DECIMAL,        // 1 GB = 1000 MB; BINARY = GiB
    val hapticsEnabled: Boolean = true,
)
enum class UnitSystem { DECIMAL, BINARY }

interface SettingsRepository {
    fun observe(): Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings): Outcome<Unit>
}

data class PermissionState(
    val usageAccess: Boolean, val notifications: Boolean, val phoneState: Boolean,
)
interface PermissionRepository {
    fun observe(): Flow<PermissionState>
    suspend fun recheck()
}

interface ExportService {
    suspend fun exportCsv(range: DateRange, out: OutputStream): Outcome<Unit>
    suspend fun exportJson(out: OutputStream): Outcome<Unit>
}
interface BackupService {
    suspend fun createBackup(out: OutputStream, passphrase: CharArray): Outcome<Unit>
    suspend fun restoreBackup(input: InputStream, passphrase: CharArray): Outcome<Unit>
}
```

## 4. Removed: Lens contract

The Lens contract (`LensController`, `LensState`, `LensSnapshot`, `lens_domain_hit`, `LensVpnService`, the `lens` notification
channel and the `emberbyte://lens` link) was discarded with Live Lens (ADR 0001, backend spec section 7). Nothing in this document
references it any more. The section number is kept so older references resolve.

## 5. Storage contract (Room, database `emberbyte.db`, version 1)

| Table | Key | Columns |
|---|---|---|
| `total_minute` | (`minuteStart`, `network`, `subscriptionId`) | `rxBytes`, `txBytes` |
| `usage_hourly` | (`hourStart`, `uid`, `network`, `subscriptionId`) | `rxBytes`, `txBytes` |
| `app_meta` | `uid` | `packageName`, `label`, `updatedAt` |
| `plan` | `id` | `name`, `subscriptionId`, `capBytes`, `cycleJson`, `rolloverJson`, `archived` |
| `add_on` | `id` | `planId`, `label`, `bytes`, `validFrom`, `validUntil` |
| `free_rule` | `id` | `planId`, `label`, `daysMask`, `startMinute`, `endMinute`, `packageName?` |
| `alert_rule` | `id` | `planId`, `thresholdPercent`, `enabled` |
| `app_budget` | `id` | `packageName`, `limitBytes`, `period`, `planId?`, `warnAtPercent`, `enabled` |
| `spike_event` | `id` | `packageName`, `detectedAt`, `windowMinutes`, `bytes`, `baselineBytes`, `multiple`, `dismissed` |
| `coverage_gap` | `id` | `fromAt`, `toAt`, `reason` |
| `counter_checkpoint` | `source` | `at`, `rxBytes`, `txBytes`, `bootId` |

**Database version 1 contains only the usage tables** (`total_minute`, `usage_hourly`, `app_meta`, `coverage_gap`,
`counter_checkpoint`); the plan, add-on, free-rule, alert, budget and spike tables arrive with their milestones as
Migration(1, 2) and later. **`usage_hourly` rows are window rows:** `hourStart` is the start of a 2-hour window aligned to
even UTC hours (the platform's usage-history bucket), and the bytes cover that window (or the part of it that has elapsed).
The name is historical.
Times are stored as epoch milliseconds (`Long`). `network` is `0 = MOBILE`, `1 = WIFI`.
`subscriptionId` is `-1` for Wi-Fi or unknown. Settings live in Jetpack DataStore, not Room.
Retention: `total_minute` 7 days, `usage_hourly` 13 months, `coverage_gap` 13 months. Every schema change ships a tested migration
and an exported schema JSON.

## 6. Android component contract

| Component | Name | Contract |
|---|---|---|
| Foreground service | `SamplerService` | Type `specialUse`. Owns the 1 Hz live sampler, flushes deltas to Room every 60 s. Started on boot and app launch. |
| Worker | `CatchUpWorker` | Unique periodic work, 15 min. Runs the catch-up. Android 12+ forbids starting a foreground service from the background, so when `SamplerService` is not running it first TRIES to start it and, when Android refuses, posts the `status` notification "Measurement paused - tap to resume". A force-stop also cancels WorkManager jobs until the next launch, so this notification only appears after OS or OEM kills. |
| Alert evaluation | `AlertRunner` (M3: plan alerts; M4 adds budgets and spikes) | See section 10.3: called every 60 s by `SamplerService` and after every `CatchUpWorker` run. There is no separate `AlertWorker`. |
| Receiver | `BootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → start the sampler **only if onboarding is complete** (the sampler records the `REBOOT` gap itself). |
| Widgets | `HeroWidget`, `BentoWidget` | Glance. Read `PlanRepository.observeActivePlanStates()` and `UsageRepository.observeToday()`; refresh cadence in section 10.3. |

Notification channels: `live` (low importance, ongoing), `status` (low importance, "Measurement paused"), `alerts` (default importance),
`spikes` (default importance).

Deep links: `emberbyte://home`, `emberbyte://apps`, `emberbyte://apps/{packageName}`, `emberbyte://plans`,
`emberbyte://plans/{planId}`, `emberbyte://settings`. Notifications and widgets use only these.

## 7. File formats

**JSON export** (`emberbyte-export-v1.json`):

```json
{
  "contractVersion": 1,
  "exportedAt": "2026-10-01T12:00:00Z",
  "plans": [ { "id": 1, "name": "...", "subscriptionId": 3, "capBytes": 10000000000,
               "cycle": { "type": "monthlyOnDay", "dayOfMonth": 12, "time": "00:00", "zone": "Africa/Cairo" },
               "rollover": { "type": "capped", "maxBytes": 2000000000 },
               "addOns": [], "freeRules": [], "alertRules": [] } ],
  "appBudgets": [],
  "usageHourly": [ { "hourStart": "2026-10-01T10:00:00Z", "package": "com.example", "network": "MOBILE",
                     "subscriptionId": 3, "rxBytes": 1, "txBytes": 1 } ]
}
```

**CSV export** header: `hourStart,package,network,subscriptionId,rxBytes,txBytes`.

**Backup file** (`.emberbyte`): the JSON export, gzip-compressed, then encrypted with AES-256-GCM using a key
derived from the passphrase with Argon2id. Restore fails with `EmberbyteError.Invalid("passphrase", …)` on
a wrong passphrase and never partially applies.

## 8. Change rules

1. Any change to a type or method signature here updates this file in the same commit as the code.
2. A breaking change bumps `CONTRACT_VERSION` and the JSON `contractVersion`, and adds a migration or
   an import shim for older exports.
3. Frontend and backend specs reference these names verbatim and never redefine them.

## 9. Milestone 2 additions (data pipeline)

These types are internal building blocks of the real data layer. They extend, and never contradict,
sections 1-6. Package roots: `…engine.*` in `:core:engine`, `…data.*` in `:core:data`.

### 9.1 Engine (`:core:engine`, pure Kotlin)

```kotlin
// …engine.counter
data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)
data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)

object CounterReconciler {
    /** previous == null -> zero delta, wasReset = false.
     *  Different bootId -> the counters restarted from zero: delta = current reading, wasReset = true.
     *  Same boot but EITHER counter decreased (an interface vanished, e.g. mobile data went away) -> delta = 0,
     *  wasReset = true: re-baseline WITHOUT inventing usage. Otherwise current - previous. */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta
}

// …engine.usage
data class MinuteTotal(val minuteStart: Instant, val network: NetworkKind, val subscriptionId: Int,
                       val rxBytes: Long, val txBytes: Long) { val totalBytes: Long }
data class HourlyUsage(val hourStart: Instant, val uid: Int, val network: NetworkKind, val subscriptionId: Int,
                       val rxBytes: Long, val txBytes: Long) { val totalBytes: Long }
data class AppMeta(val uid: Int, val packageName: String, val label: String)

object SeriesBuilder {
    fun bucketStart(at: Instant, granularity: Granularity, zone: ZoneId): Instant
    fun nextBucketStart(start: Instant, granularity: Granularity, zone: ZoneId): Instant
    const val WINDOW_SECONDS = 7_200L                       // the platform's usage-history bucket
    fun windowStart(at: Instant): Instant                   // floor to a multiple of 2 h since the epoch
    /**
     * Zero-filled buckets covering [range]. Sources are merged per (2-hour window, network), never per hour:
     * total = max(sampler minute sum, system window sum); the surplus over the minute sum is booked on the window's FIRST hour
     * (the same window-start rule the per-app rows use). Never on a later hour: an hour that has not happened yet must not receive
     * bytes, or a day boundary inside a window would make "today" start with yesterday's bytes and then fall.
     * An hour contributes to the bucket that contains its START instant; hours that start before the first bucket or at/after
     * range.to are ignored, so every surface (Home, Apps, History) uses the same rule. An empty range gives no buckets.
     */
    fun build(range: DateRange, granularity: Granularity, zone: ZoneId,
              minuteRows: List<MinuteTotal>, hourlyRows: List<HourlyUsage>,
              filter: UsageFilter = UsageFilter()): List<UsagePoint>
}

object AppAggregator {
    fun aggregate(hourlyRows: List<HourlyUsage>, meta: Map<Int, AppMeta>,
                  filter: UsageFilter = UsageFilter(), sort: AppSort = AppSort.BYTES_DESC): List<AppUsage>
}

// HourlyReconciler was REMOVED in the M2 fix wave: scaling per-app rows to the sampler hid real differences and misbehaved.

/** Subscription id with the most mobile bytes among [minuteRows] (the rows of one 2-hour window); -1 when none. */
fun dominantSubscriptionId(minuteRows: List<MinuteTotal>): Int
```

### 9.2 Data layer (`:core:data`)

```kotlin
// …data.store
interface UsageStore {
    suspend fun addMinute(row: MinuteTotal)                       // ADDS rx/tx to an existing row (atomic)
    suspend fun upsertHourly(rows: List<HourlyUsage>)             // replaces rows with the same key
    /** Atomically deletes ALL rows whose hourStart == [hourStart] (any uid/network/subscription), then inserts [rows]. */
    suspend fun replaceHourly(hourStart: Instant, rows: List<HourlyUsage>)
    suspend fun upsertAppMeta(meta: List<AppMeta>)
    suspend fun insertGap(gap: CoverageGap)
    suspend fun saveCheckpoint(source: String, reading: CounterReading)
    suspend fun loadCheckpoint(source: String): CounterReading?
    suspend fun minuteRows(from: Instant, to: Instant): List<MinuteTotal>      // from inclusive, to exclusive
    suspend fun hourlyRows(from: Instant, to: Instant): List<HourlyUsage>
    suspend fun appMeta(): List<AppMeta>
    suspend fun gaps(from: Instant, to: Instant): List<CoverageGap>              // overlapping [from, to]
    fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>>
    fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>>
    fun observeAppMeta(): Flow<List<AppMeta>>
    fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>>
    fun observeLastSample(): Flow<Instant?>                      // newest total_minute.minuteStart
    suspend fun prune(now: Instant)                              // retention from section 5
}
object UsageStores { fun create(context: Context): UsageStore }  // Room-backed; Room types never leak

// …data.source  (thin Android wrappers, faked in tests)
data class CounterSnapshot(val at: Instant, val mobileRxBytes: Long, val mobileTxBytes: Long,
                           val totalRxBytes: Long, val totalTxBytes: Long, val bootId: String)
interface CounterSource { fun read(): CounterSnapshot }
interface NetworkKindSource { val current: StateFlow<NetworkKind?> }
interface SubscriptionSource { fun defaultDataSubscriptionId(): Int }        // -1 when unknown
data class UidUsage(val uid: Int, val rxBytes: Long, val txBytes: Long)
interface NetworkStatsSource { suspend fun query(network: NetworkKind, from: Instant, to: Instant): List<UidUsage> }
interface AppInfoSource { fun resolve(uid: Int): AppMeta? }
interface UsageAccess { fun isGranted(): Boolean }

// …data.sampler
class SamplerEngine(counters: CounterSource, network: NetworkKindSource, subscription: SubscriptionSource,
                    store: UsageStore, tickMillis: Long = 1_000L) {
    val liveSpeed: SharedFlow<LiveSpeed>
    suspend fun start(); suspend fun tick(); suspend fun stop(); suspend fun run()
}
sealed interface CatchUpResult { data class Done(val hoursUpdated: Int /* 2-hour windows refreshed */) : CatchUpResult; data object MissingUsageAccess : CatchUpResult }
class HourlyCatchUp(source: NetworkStatsSource, store: UsageStore, appInfo: AppInfoSource, access: UsageAccess,
                    clock: Clock, maxBackfill: Duration = Duration.ofDays(7)) { suspend fun run(): CatchUpResult }

// …data.util
class DayClock(clock: Clock, pollMillis: Long = 30_000L) { fun dates(): Flow<LocalDate> }   // emits now, then whenever the WALL-CLOCK date OR the clock's time zone changes (the same date may be emitted again after a zone change); polled
class SystemZoneClock : Clock()                                  // re-reads ZoneId.systemDefault() on every call, so a time-zone change is followed

// …data (settings side)
interface OnboardingRepository { fun observeCompleted(): Flow<Boolean?>; suspend fun complete() }   // null = still loading
/** Settings + onboarding backed by ONE DataStore file; keeps DataStore types out of `:app`. */
class AppPreferences(val settings: SettingsRepository, val onboarding: OnboardingRepository) {
    companion object { fun create(context: Context): AppPreferences }
}
// …data.store
val OPEN_END: Instant                       // 2100-01-01T00:00:00Z, the open upper bound for lower-bounded queries
object UsageRetention { val MINUTES: Duration; val HOURLY: Duration; val GAPS: Duration }   // 7 d, 395 d, 395 d
```

Checkpoint sources: `"sampler.mobile"`, `"sampler.total"`, `"catchup.hourly"`.
Constants: gap threshold 90 000 ms; live-speed EMA alpha 0.5; `NO_SUBSCRIPTION = -1`.

### 9.3 Real repositories (replace the M1 fakes in production; fakes stay for tests and previews)

| Interface | M2 implementation | Notes |
|---|---|---|
| `UsageRepository` | `RoomUsageRepository` | live speed from `SamplerEngine.liveSpeed`; `refreshNow()` runs `HourlyCatchUp`, `MissingUsageAccess` -> `Outcome.Failure(EmberbyteError.MissingUsageAccess)` |
| `SettingsRepository` | `DataStoreSettingsRepository` | Jetpack DataStore (Preferences) |
| `PermissionRepository` | `AndroidPermissionRepository` | `recheck()` re-reads usage access, notifications, `READ_PHONE_STATE` |
| `PlanRepository` | `NoPlanRepository` (M2 only) | replaced by `RoomPlanRepository` in M3 (section 10); `NoPlanRepository` is deleted |
| `OnboardingRepository` | `DataStoreOnboardingRepository` | |

### 9.4 Data semantics (corrected in the M2 fix wave)

- **One source per span, merged per 2-hour window.** Android keeps per-UID history in buckets of 2 hours and `querySummary`
  interpolates a bucket over the hours it is asked for, so per-hour comparisons double count. The catch-up therefore queries
  **one 2-hour window at a time** (clamped to `now` for the open window) and stores ONE row set per window. A series value for a
  (window, network) is `max(sampler minute sum, system window sum)`; see `SeriesBuilder`.
- **Replacing, not adding:** each catch-up run replaces the whole window atomically (`replaceHourly`), so a changed dominant SIM or a
  re-queried open window can never leave duplicate rows. Catch-up runs are serialised (one at a time).
- **Per-app data** comes only from `usage_hourly` window rows (needs Usage Access); an app row belongs to the range that contains its
  window START. Home, Apps and History use the same start-instant rule, so they can differ only by the traffic of a window that
  straddles a day boundary (at most 2 hours of data; documented, accepted).
- **Mobile/Wi-Fi attribution (sampler):** mobile delta = min(mobile counter delta, total counter delta) per direction (the total
  includes mobile; a mobile counter that vanishes and returns must not create usage). Wi-Fi delta = total - mobile **only while the
  default network is Wi-Fi**, else 0 (so offline noise, tethering and cellular-side VPN traffic are not booked as Wi-Fi). Known
  limitation: a VPN running over Wi-Fi counts its tunnel bytes twice (accepted; Live Lens, which would have addressed it, was discarded).
- **Late ticks:** if more than 90 s passed since the previous tick (device sleep, frozen process), the delta is NOT added to a minute
  row; the sampler re-baselines and records a `DEVICE_ASLEEP` gap. The catch-up fills those hours from system data. `DEVICE_ASLEEP`
  gaps are stored but never shown in the Home banner.
- **Coverage evidence:** the sampler still writes a `total_minute` row for BOTH networks every minute, even at zero.
- **Gaps on start:** a restart more than 90 s after the last checkpoint records `SERVICE_KILLED` (or `REBOOT` when the boot id changed);
  the bytes in between are not added to minute totals.
- **Dual-SIM:** mobile minute rows are tagged with `SubscriptionManager.getDefaultDataSubscriptionId()` at sample time; mobile window
  rows get the dominant subscription of that window. No `READ_PHONE_STATE` is needed in M2.
- **Day boundary:** `DayClock` polls the wall-clock date (every 30 s) and the clock follows the system time zone, so the notification,
  Home and History roll over correctly after sleep, a clock change or a time-zone change.

## 10. Milestone 3 additions (plans, alerts, forecast, widgets)

These extend sections 1-9 and never contradict them. Package roots: `…engine.*` in `:core:engine`, `…data.*` in `:core:data`.

### 10.1 Engine (`:core:engine`, pure Kotlin)

```kotlin
// …engine.model (Plan.kt)
data class AlertRule(val id: Long, val planId: Long, val thresholdPercent: Int /*1..100*/, val enabled: Boolean)
data class PlanPreview(val state: PlanState, val forecast: Forecast)

// …engine.plan
/** packageName == null: TOTAL mobile traffic of the plan's SIM. packageName != null: a per-app breakdown of that same traffic,
 *  used ONLY to evaluate package-scoped FreeRules; it is never added to the total. A slice may overlap the cycle window
 *  partially; its bytes are then clipped proportionally to the overlap. */
data class UsageSlice(val start: Instant, val end: Instant, val packageName: String?, val bytes: Long)

object CycleCalculator {
    fun windowAt(cycle: Cycle, at: Instant): CycleWindow           // start inclusive, end exclusive, start <= at < end
    fun previousWindow(cycle: Cycle, window: CycleWindow): CycleWindow   // == windowAt(cycle, window.start - 1 ms)
}

object PlanCalculator {
    fun state(plan: Plan, at: Instant, usage: List<UsageSlice>, addOns: List<AddOn>, freeRules: List<FreeRule>,
              previousCycleUnusedBytes: Long, isApproximate: Boolean): PlanState
}

// …engine.forecast
object ForecastEngine {
    val DEFAULT_HOUR_CURVE: DoubleArray     // 24 weights, share of a day's usage per LOCAL hour (normalised internally)
    fun forecast(state: PlanState, dailyHistory: List<DayUsage>, now: Instant, zone: ZoneId,
                 extraBytesPerDay: Long = 0L, extraFrom: Instant = now,
                 hourCurve: DoubleArray = DEFAULT_HOUR_CURVE): Forecast
}
```

Rules (these are the contract; the section 2 / backend section 4 / section 6 prose is the summary):

- **`CycleCalculator`.** `MonthlyOnDay`: the renewal of a month is `min(dayOfMonth, lengthOfMonth)` at `time` in `zone`; the window containing `at` runs from the latest renewal `<= at` to the next one. `EveryNDays`: renewals are `anchor + k*days` at `time` in `zone` for every integer k (also negative); same containment rule. Local times that do not exist (DST gap) move forward as `ZonedDateTime` does.
- **`PlanCalculator.state`.** The window is `CycleCalculator.windowAt(plan.cycle, at)`; all calendar logic uses the zone of `plan.cycle`.
  `totalBytes` = sum of the clipped bytes of slices with `packageName == null`.
  `freeBytes` = bytes of total slices inside the union of the plan's all-apps `FreeRule` intervals, PLUS bytes of per-app slices of package `p` inside the intervals of `p`'s rules **minus** the all-apps intervals (so nothing is excluded twice). A slice's free share is proportional to the time overlap. A `FreeRule` interval starts on a local date whose `DayOfWeek` is in `days`, runs from `start` to `end` on that date, or to `end` on the NEXT date when `end <= start` (crosses midnight; `22:00-00:00` is valid); `start == end` is an empty interval.
  `usedBytes = max(0, totalBytes - freeBytes)`. `rolledOverBytes`: `None` = 0, `Full` = `previousCycleUnusedBytes`, `Capped(max)` = `min(previousCycleUnusedBytes, max)`.
  `addOnBytes` = sum of `bytes` of add-ons whose `[validFrom, validUntil)` overlaps the window. `effectiveCapBytes = capBytes + rolledOverBytes + addOnBytes`. `remainingBytes = max(0, effectiveCap - used)`. `fractionUsed = used / effectiveCap` (0 when the cap is 0). `daysLeft = ceil((window.end - at) / 24 h)`, never negative.
- **Rollover input (data layer).** `previousCycleUnusedBytes` = `remainingBytes` of the PREVIOUS window evaluated with `Rollover.None` (no chained rollover) and WITHOUT add-on packs (add-ons are never part of rollover, backend spec section 4: an add-on that spans a renewal must not raise the cap of both cycles), at `previousWindow.end - 1 ms`; 0 when the plan has no rollover.
- **`ForecastEngine.forecast`.** History = `dailyHistory` entries dated before today in `zone`, newest 28. Per weekday the mean and population standard deviation of `mobileBytes`; a weekday with n < 3 samples is blended `n/3 * weekday + (1 - n/3) * overall`; with no history at all every day is 0.
  Projection for multiplier k in {+1, 0, -1}: day bytes = `max(0, mean + k * std) (+ extraBytesPerDay on days on or after extraFrom's date)`. Today contributes only the share of the day still ahead: `1 - cumulativeShare(hourCurve, local time of now)`; every later local day contributes fully; the last day is cut at `state.window.end` proportionally to time. Bytes inside a day are spread linearly over the segment. `runOut` = instant at which the cumulative projected bytes reach `state.remainingBytes`; **k=+1 gives `runOutEarliest`, k=0 `runOutExpected`, k=-1 `runOutLatest`**; null when not reached before the window ends. `remainingBytes == 0` gives `now` for all three. `projectedCycleEndBytes = state.usedBytes + expected projection until window end`. `historyDays` = number of history entries used. `Confidence`: `LOW` under 7 days, `HIGH` with 21+ days and coefficient of variation (overall std / overall mean, 0 when the mean is 0) below 0.6, else `MEDIUM`.
  Known v1 limits (documented, accepted): free-window traffic is not subtracted from the history; the hour curve is the built-in default (a personal curve is M4).

### 10.2 Data layer (`:core:data`)

```kotlin
// …data.plan
interface PlanStore {                                               // Room-backed in production, in-memory in tests
    fun observePlans(): Flow<List<Plan>>                            // ALL plans including archived, ordered by id
    fun observePlan(id: Long): Flow<Plan?>
    suspend fun plan(id: Long): Plan?
    suspend fun upsertPlan(plan: Plan): Long                        // id == 0 inserts; returns the id
    suspend fun setArchived(id: Long, archived: Boolean): Boolean   // false when the plan does not exist
    fun observeAddOns(planId: Long): Flow<List<AddOn>>
    suspend fun upsertAddOn(addOn: AddOn): Long
    suspend fun deleteAddOn(id: Long)
    fun observeFreeRules(planId: Long): Flow<List<FreeRule>>
    suspend fun upsertFreeRule(rule: FreeRule): Long
    suspend fun deleteFreeRule(id: Long)
    fun observeAlertRules(planId: Long): Flow<List<AlertRule>>
    suspend fun alertRules(planId: Long): List<AlertRule>
    suspend fun upsertAlertRule(rule: AlertRule): Long
    suspend fun deleteAlertRule(id: Long)
    suspend fun lastFiredPercent(planId: Long, windowStart: Instant): Int?
    suspend fun setFiredPercent(planId: Long, windowStart: Instant, percent: Int)
}
object PlanStores { fun create(context: Context): PlanStore }       // shares ONE Room database instance with UsageStores

// PlanRepository (section 3) gains one method; NoPlanRepository is deleted and RoomPlanRepository replaces it
suspend fun previewState(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>): Outcome<PlanPreview>   // pure, not persisted

interface SimInfoSource {
    fun activeSims(): List<SimInfo>                                 // empty without READ_PHONE_STATE
    fun defaultDataSubscriptionId(): Int                            // -1 when unknown; needs no permission
}
data class SimInfo(val subscriptionId: Int, val slotIndex: Int, val displayName: String)

// …data.alert
data class AlertNotice(val planId: Long, val planName: String, val thresholdPercent: Int, val usedBytes: Long,
                       val effectiveCapBytes: Long, val remainingBytes: Long, val runOutExpected: Instant?)
class AlertEvaluator(plans: PlanRepository, store: PlanStore) { suspend fun evaluate(): List<AlertNotice> }
```

- **Validation** (`upsertPlan`, `upsertAddOn`, `upsertFreeRule`, `upsertRule`) returns `Outcome.Failure(EmberbyteError.Invalid(field, reason))` and saves nothing. Fields: `name` (blank or over 40 chars), `capBytes` (not in 1..10^15), `cycle.dayOfMonth` (not 1..31), `cycle.days` (not 1..366), `rollover.maxBytes` (not positive), `subscriptionId` (negative); add-on: `bytes` (not positive), `validUntil` (not after `validFrom`), `label` (blank); free rule: `days` (empty), `time` (start == end), `label` (blank); alert rule: `thresholdPercent` (not 1..100) and `threshold` ("Already set" for a duplicate on the same plan). A missing plan on a child upsert gives `NotFound`.
- **Storage.** Database version 2 adds `plan`, `add_on`, `free_rule`, `alert_rule` (contract section 5 columns) and `alert_fired(planId, windowStart, percent)` with primary key (`planId`, `windowStart`) through `Migration(1, 2)`; the exported schema `2.json` is committed. `cycleJson`/`rolloverJson` use the shapes of section 7 (`"type": "monthlyOnDay" | "everyNDays"`, `"none" | "full" | "capped"`, `time` as `HH:mm`).
- **Plan pipeline (`RoomPlanRepository`).** Only mobile traffic counts: `UsageFilter(MOBILE, plan.subscriptionId)`; a plan with `subscriptionId == null` counts all mobile traffic. Total slices come from `SeriesBuilder.build(..., Granularity.HOUR, ...)` (so the first-hour surplus rule of 9.1 applies; hours that start before the window are ignored); per-app slices are the 2-hour window rows mapped through `app_meta` and exist only when a package-scoped rule exists. `isApproximate` is true when, inside the current window, mobile minute rows carry bytes for a subscription other than the plan's bound one (or for `-1` while the plan is bound), or a `coverage_gap` of reason `SERVICE_KILLED`, `REBOOT` or `PERMISSION_MISSING` overlaps the window. State and forecast are recomputed whenever the store changes and once a minute (windows roll over at the renewal instant). Daily history for the forecast is the mobile `Granularity.DAY` series of the last 28 days in the plan's zone with leading zero days trimmed. The catch-up backfill (`HourlyCatchUp.maxBackfill`) is 28 days so a first forecast has history.
- **Alerts.** `AlertEvaluator.evaluate()` looks at every active plan: `percent = usedBytes * 100 / effectiveCapBytes` (integer division); among enabled rules with `threshold <= percent` and `threshold > lastFiredPercent(plan, window.start) (default 0)` it emits ONE notice for the highest, and records it with `setFiredPercent`. Each threshold therefore fires at most once per cycle window; raising the cap later never re-fires.

### 10.3 Android components (replaces the `AlertWorker` row of section 6)

| Component | Contract |
|---|---|
| Alert evaluation | `AlertRunner.run()` in `:app`: skips when notifications are disabled (so nothing is consumed unseen), otherwise calls `AlertEvaluator.evaluate()` and posts one `alerts` notification per notice (id = plan id, tag `plan-alert`, tap opens `emberbyte://plans/{planId}`). Called every 60 s by `SamplerService` and after every `CatchUpWorker` run (covers the service-down case). There is no separate `AlertWorker`. |
| Widgets | `HeroWidget` and `BentoWidget` (Glance). `WidgetUpdater.request(context)` is called after each catch-up cycle: every 5 minutes while the service runs, every 15 minutes through `CatchUpWorker` otherwise. Content: Hero = today's number + remaining data (or "Set up a plan"); Bento = today, forecast, live speed, top two apps. Deep link `emberbyte://home`. |
| Channels | `alerts` (default importance, "Plan alerts") is created together with `live` and `status`. |
| Deep links | `MainActivity` declares an `<intent-filter>` for scheme `emberbyte` with hosts `home`, `apps`, `plans`, `settings`; navigation destinations declare the matching `navDeepLink`s of section 6. |
| Permission | `READ_PHONE_STATE` is declared (optional, requested from the plan editor only). |

### 10.4 UI-facing additions

- `:ui:design` gains `BudgetRing(fraction, state: BudgetStateUi)`, `PlanCard(model: PlanCardUi, onClick)`, `BudgetStateUi { Ok, Warning, Exceeded }` and `PlanCardUi` (primitives only). The ring never relies on colour alone (an icon and the percentage text accompany the warning and exceeded states).
- `ForecastUi` mapping (`:app`): never runs out → "Safe / Won't run out this cycle"; run-out already reached → "Out / data used up"; expected is null but earliest is not → "Probably safe / could run out as early as <day>"; otherwise as in M2.
- Routes: `plans` (list) and `plans/{planId}` (editor; `0` = new plan). Home gets a plan chip row when there are two or more plans, and a "Set up your data plan" tile (opens the editor) when there is none. Onboarding gains a third, skippable step "Your data plan".

### 10.5 Fix-wave additions (after the first M3 audit)

- `PlanRepository.validateDraft(plan, addOns, freeRules): Map<String, String>` (pure, not suspend): every problem of the draft at once, keyed `name`, `capBytes`, `cycle.dayOfMonth`, `cycle.days`, `rollover.maxBytes`, `subscriptionId`, `addOn.<index>.<field>`, `freeRule.<index>.<field>`; empty when valid. The editor calls it BEFORE any write, so a draft with an invalid child saves nothing. Saving is validated first and then written step by step; it is not a database transaction.
- `PlanState.percentUsed(): Int` = `usedBytes * 100 / effectiveCapBytes` (integer division, 0 when the cap is 0). The plan card, the ring and `AlertEvaluator` all use it, so the percent on a card equals the percent of an alert.
- `PlanState.isApproximate` is also true when (a) the plan is bound to a SIM and the window has mobile traffic whose subscription is unknown (`-1`: backfilled or pre-install traffic), or (b) the window starts more than one platform window (2 h) before the earliest stored measurement (for example without usage access, or a cycle older than the backfill).
- `forecastToUi(..., remainingBytes: Long? = null)`: "Out / Data used up" only when the expected instant has passed AND no data remains; with data remaining it reads "runs out today". When `runOutLatest` is null the sentence reads "runs out <day> (could be later)". One text for "nothing left" everywhere: "No data left".
- Deep links opened while the app is running are pushed onto the current back stack (`navigate(uri)`); the `emberbyte` filter is not `BROWSABLE` (web pages cannot open the app; notifications and widgets use explicit intents).
- `AlertRunner` also stays quiet when only the `alerts` channel is blocked, and `AlertEvaluator.evaluate()` is serialised by a mutex.
- `BentoWidgetModel.compactApps`: the 4x2 Bento widget shows the top two apps on one line.

## 11. Milestone 4 additions (insight: budgets, spikes, what-if, export and backup, personal curve)

These extend sections 1-10 and never contradict them, EXCEPT where they say so: **the spike rule of section 2 / backend section 8
(20-minute windows) is replaced by 11.1**, because Android keeps per-app history in 2-hour buckets (section 9.4), so 20-minute per-app
usage does not exist on stock Android (Live Lens, which would have measured it, was discarded). Package roots as before.

### 11.1 Engine (`:core:engine`)

```kotlin
// …engine.model (replaces the SpikeEvent of section 2)
data class SpikeEvent(
    val id: Long, val packageName: String,
    val windowStart: Instant,           // start of the 2-hour platform window that spiked
    val detectedAt: Instant,
    val windowMinutes: Int,             // 120
    val bytes: Long,                    // mobile bytes of that app in that window, at detection time
    val baselineBytes: Long,            // median mobile bytes of that app per 2-hour window over the 14 days before it
    val multiple: Float,                // bytes / max(baselineBytes, 1 MB)
    val dismissed: Boolean,
)

// …engine.budget
object BudgetCalculator {
    /** percent = used * 100 / limit (integer). OK below warnAtPercent; WARNING from warnAtPercent up to and including the limit; EXCEEDED above the limit. */
    fun state(budget: AppBudget, usedBytes: Long): BudgetState
    /** DAILY: the local day containing [now] in [zone]. CYCLE: [planWindow] when given, otherwise the calendar month containing [now]. */
    fun period(budget: AppBudget, now: Instant, zone: ZoneId, planWindow: CycleWindow?): CycleWindow
}

// …engine.spike (replaces SpikeDetector of section 2)
object SpikeDetector {
    /** [recent]: one slice per (app, 2-hour window) with packageName != null and start/end = the window. [baselinePerWindowBytes]: package -> baseline;
     *  a package WITHOUT an entry is skipped (not enough history). A slice is a spike when bytes >= minBytes AND bytes / max(baseline, 1 MB) >= minMultiple. */
    fun detect(recent: List<UsageSlice>, baselinePerWindowBytes: Map<String, Long>, detectedAt: Instant,
               windowMinutes: Int = 120, minBytes: Long = 100_000_000L, minMultiple: Float = 5f): List<SpikeEvent>   // id = 0, dismissed = false
}

// …engine.forecast
object HourCurves {
    /** 24 weights summing to 1. From the mobile minute rows of the last 7 complete local days before today: personal share per local hour p[h];
     *  days = number of those days with traffic; fewer than 3 days -> [base]; otherwise w = min(days, 7) / 7 and result = w * p + (1 - w) * base. */
    fun personal(minuteRows: List<MinuteTotal>, now: Instant, zone: ZoneId, base: DoubleArray = ForecastEngine.DEFAULT_HOUR_CURVE): DoubleArray
}
```

- **`ForecastEngine` extra usage (`extraBytesPerDay` / `extraFrom`) is now exact to the instant** (the M3 audit's M-17): on the day containing `extraFrom`
  only the part of the day after `extraFrom` (by the hour curve) is added; on today's segment the added part is also limited to what is still ahead
  (`1 - cumulativeShare(max(now, extraFrom))`); earlier days get nothing, later days the full amount.
- **`PlanCalculator.freeBytes(plan, usage, freeRules, window): Long`** is public (the existing free-window maths), so the data layer can remove free traffic
  from the forecast history per day.
- **Forecast history excludes free-window traffic** (removes the v1 limit of 10.1): each history day is its mobile bytes minus `freeBytes` of that day.

### 11.2 Data layer (`:core:data`)

Database **version 3** (`Migration(2, 3)`, exported `3.json`) adds:

| Table | Key | Columns |
|---|---|---|
| `app_budget` | `id` (autoincrement) | `packageName`, `limitBytes`, `period` (`DAILY`/`CYCLE`), `planId?`, `warnAtPercent`, `enabled`; index on `packageName` |
| `spike_event` | `id` (autoincrement) | `packageName`, `windowStart`, `detectedAt`, `windowMinutes`, `bytes`, `baselineBytes`, `multiple` (REAL), `dismissed`; UNIQUE (`packageName`, `windowStart`) |
| `budget_state` | (`budgetId`, `periodStart`) | `state` (the last state a notification was posted for) |
| `spike_notice` | `packageName` | `notifiedAt` (rate limit: at most one spike notification per app per hour) |

```kotlin
// …data.budget
interface BudgetStore {
    fun observeBudgets(): Flow<List<AppBudget>>
    suspend fun budgets(): List<AppBudget>
    suspend fun upsertBudget(budget: AppBudget): Long
    suspend fun deleteBudget(id: Long)
    suspend fun lastState(budgetId: Long, periodStart: Instant): BudgetState?
    suspend fun setState(budgetId: Long, periodStart: Instant, state: BudgetState)
}
class RoomBudgetRepository(budgets: BudgetStore, plans: PlanStore, usage: UsageStore, clock: Clock, ticks: Flow<Instant>) : BudgetRepository {
    suspend fun currentPeriodStatuses(): List<BudgetPeriodStatus>      // for the evaluator
}
data class BudgetPeriodStatus(val status: BudgetStatus, val periodStart: Instant, val label: String)
data class BudgetNotice(val budget: AppBudget, val label: String, val usedBytes: Long, val state: BudgetState)
class BudgetEvaluator(statuses: suspend () -> List<BudgetPeriodStatus>, store: BudgetStore) { suspend fun evaluate(): List<BudgetNotice> }

// …data.spike
interface SpikeStore {
    fun observeSpikes(includeDismissed: Boolean): Flow<List<SpikeEvent>>          // newest first
    suspend fun insertIfAbsent(event: SpikeEvent): Boolean                         // false when (package, windowStart) exists
    suspend fun dismiss(id: Long): Boolean
    suspend fun lastNoticeAt(packageName: String): Instant?
    suspend fun markNotified(packageName: String, at: Instant)
}
class RoomSpikeRepository(store: SpikeStore) : SpikeRepository
class SpikeEvaluator(usage: UsageStore, store: SpikeStore, clock: Clock) { suspend fun evaluate(): List<SpikeEvent> }   // newly stored events

// PlanRepository / PlanStore: atomic save (replaces the step-by-step save of the editor)
suspend fun PlanRepository.savePlan(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>, alertThresholds: Set<Int>): Outcome<Long>
suspend fun PlanStore.savePlanWithChildren(plan: Plan, addOns: List<AddOn>, freeRules: List<FreeRule>, alertThresholds: Set<Int>): Long   // ONE database transaction
```

- **Budgets count MOBILE bytes** of the app (all its UIDs, via `app_meta`) in the period, taken from the 2-hour window rows by the window-start rule (the rule
  of Apps and Home). `planId != null` also filters on the plan's subscription (`-1`/unknown rows are not counted). Validation (`Invalid(field, reason)`):
  `packageName` (blank, or already has a budget: "Already set"), `limitBytes` (not positive), `warnAtPercent` (not 1..99), `planId` (a CYCLE budget naming a
  missing plan -> `NotFound`; a DAILY budget stores `planId = null`). Budget notifications: a transition to a worse state than the one stored for the
  period is posted once (OK to WARNING: warning, anything to EXCEEDED: exceeded); a better state is stored silently.
- **Spikes**: evaluated after every catch-up (service loop 5 min, worker 15 min) for the current and the previous two 2-hour windows. Baseline = median of
  the app's mobile bytes per window over the 14 days before the window (168 windows, missing windows count as 0); an app is skipped unless its oldest stored
  window row is at least 3 days older than the window. An event is stored once per (package, window). A spike notification is posted only when
  `Settings.spikeAlertsEnabled`, notifications are enabled, the `spikes` channel is not blocked and the app had no spike notification in the last hour.
- **Export** (`ExportService`, streaming, formats of section 7): `exportCsv(range, out)` writes `hourStart,package,network,subscriptionId,rxBytes,txBytes`
  for rows whose window start is in range (`package` is the package name, or `uid:<n>` when unknown). `exportJson(out)` writes the section 7 document
  (`contractVersion`, `exportedAt`, `plans` with `archived`, `addOns`/`freeRules`/`alertRules`, `appBudgets`, `usageHourly`, in this order: the parser reads the usage rows last, as a stream). Both are streamed and never hold all rows in memory.
- **Backup file** (`.emberbyte`): bytes `EMBBKP01` (8) | `salt` (16) | `memoryKiB` int32 | `iterations` int32 | `parallelism` int32 | `nonce` (12) | ciphertext+tag,
  big-endian; ciphertext = AES-256-GCM(gzip(export JSON)) with the whole header as associated data; key = Argon2id(passphrase, salt, memory 65536 KiB,
  iterations 3, parallelism 1) -> 32 bytes. On restore the header parameters are used but bounded (memory <= 65536 KiB, which is exactly what this version writes; iterations <= 10; parallelism <= 4)
  so a hostile file cannot exhaust the phone (a larger memory cost needs a new magic/version byte). A key derivation that still runs out of memory (`OutOfMemoryError`) is reported as `Invalid("file", ...)`, never a crash. Backup creation and restore run OFF the main thread (`Dispatchers.Default`). Argon2id comes from Bouncy Castle's lightweight API (`org.bouncycastle:bcprov-jdk18on`, MIT licence, credited in
  `NOTICE`); no other crypto library.
```kotlin
data class RestoreSummary(val plans: Int, val budgets: Int, val usageRows: Int, val skippedUsageRows: Int)
interface BackupService {                       // replaces section 3's BackupService
    suspend fun createBackup(out: OutputStream, passphrase: CharArray): Outcome<Unit>              // Invalid("passphrase", ...) under 8 characters
    suspend fun restoreBackup(input: InputStream, passphrase: CharArray): Outcome<RestoreSummary>
}
```
  Errors: wrong passphrase or a damaged ciphertext -> `Invalid("passphrase", "Wrong passphrase or damaged file")` (GCM tag failure; the two cannot be told
  apart); bad magic, truncated header, parameters out of bounds, unknown `contractVersion` -> `Invalid("file", reason)`. **Restore replaces, in ONE database
  transaction,** plans, add-ons, free rules, alert rules, budgets and `usage_hourly`, and clears `alert_fired`, `budget_state`, `spike_event` and `spike_notice`;
  it never partially applies (any failure rolls everything back). `usage_hourly` rows are keyed by UID, which is device specific: a row is restored only when its
  package resolves to a UID on this device (`uid:<n>` rows keep their UID); the others are counted in `skippedUsageRows`. The passphrase array is zeroed after use.
- Channels: `spikes` (default importance, "Spike alerts") is created with `live`, `status` and `alerts`. Budget notifications use `alerts`.
  Taps open `emberbyte://apps/{package}`; both add an action "Data settings" that opens the system screen for that app
  (`Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS` with `package:<name>`, falling back to the app-details screen).

### 11.3 UI-facing additions

- App detail gains a **Budget** section (ring + used/limit + edit/remove, or "Set a budget"; dialog: limit + unit, period Daily / Plan cycle with the plan choice or
  "Calendar month", warn-at 50/80/90) and a **Spikes** section for that app (window, bytes, "5.2x usual", Dismiss). Home shows a "Spike detected" tile for the
  newest undismissed spike. Settings gets "Spike alerts" (existing setting) and a **Data** section: Export CSV (7 days / 30 days / all), Export JSON,
  Create backup, Restore backup (passphrase dialogs; a confirmation says restore replaces plans, budgets and history on this device).
- The plan editor of an EXISTING plan gets a **What if** section: an extra amount per day (+ unit) from now; it shows the current forecast sentence and the
  what-if sentence (`PlanRepository.whatIf`) side by side.
- Right-to-left fixes: numbers and their words stay together in `ForecastTile`, `GapBanner`, bar labels and the Apps range chips.

## 12. Milestone 6 additions (polish: accessibility, Android 16 live update, release)

These extend sections 1-11 and never contradict them. Package roots as before. Milestone 5 (Live Lens) was discarded (ADR 0001); nothing here depends on it.

### 12.1 Live notification as an Android 16 live update (`:app`, `sampler`)

`LiveNotificationText` gains `val progress: LiveProgress? = null` where `data class LiveProgress(val fractionUsed: Float, val chip: String)`.
`liveNotificationText(...)` takes an extra `progress: LiveProgress?` parameter (default `null`, so existing callers and tests still compile) and
copies it through. `SamplerService` fills it from the FIRST active `PlanState`: `fractionUsed.coerceIn(0f, 1f)` and `chip = ` the remaining data
as a SHORT string for the status-bar chip (for example `3.2 GB`; at most 7 characters, never the word "left"; when the plan is exhausted `0 B`).
No active plan, or `liveNotificationEnabled == false` (the minimal "Measuring" notification), means `progress = null`.

`LiveNotificationBuilder.build(text)`:
- `Build.VERSION.SDK_INT >= 36` AND `text.progress != null`: build with the platform `android.app.Notification.Builder(context, NotificationChannels.LIVE)`
  (NOT `NotificationCompat`, which has no `ProgressStyle`): `setStyle(Notification.ProgressStyle().setProgress(round(fractionUsed * 1000)).addProgressSegment(Notification.ProgressStyle.Segment(1000)))`,
  `setShortCriticalText(chip)`, plus everything the standard notification already has (small icon `ic_stat_ember`, title,
  text, ongoing, only-alert-once, no timestamp, `CATEGORY_SERVICE`, foreground-service behaviour immediate, the Home content intent). No custom `RemoteViews`,
  no colorization (they disqualify promotion). The class and method names above were checked in the API 37 `android.jar`.
- **API levels are not all 36.** `Notification.ProgressStyle`, `Notification.Builder.setShortCriticalText` and `NotificationManager.canPostPromotedNotifications()` exist
  since API 36 (Android 16, `Build.VERSION_CODES.BAKLAVA`). `Notification.Builder.setRequestPromotedOngoing(boolean)` and the permission
  `POST_PROMOTED_NOTIFICATIONS` exist only since **API 36.1** (`Build.VERSION_CODES_FULL.BAKLAVA_1`); the API 36 `android.jar` does not contain the method, so calling it
  on a 36.0 device throws `NoSuchMethodError` (an `Error`, which `Exception` handlers do not catch; the sampler service is `START_STICKY`, so the process would crash in a loop).
  Therefore: the ProgressStyle notification with the chip is built when `SDK_INT >= 36`; `setRequestPromotedOngoing(true)` is called ONLY when
  `Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1` (`SDK_INT_FULL` exists since API 36). No reflection, no `catch (Error)`:
  the version guard is the protection, and a unit test proves each call is made only at the versions that have it. Both values are injectable into
  `LiveNotificationBuilder` (default: the real ones) so the tests can run every combination (35, 36.0, 36.1, 37) on the JVM; unit tests run on JDK 17, where Robolectric has no
  API 36 platform, so the proof that the REAL platform accepts the calls is the device dump on a 36.1+ emulator (and the guard test for 36.0).
- Every other case: the existing `NotificationCompat` notification, unchanged (API 29 to 35 behave exactly as today).
- The manifest declares `android.permission.POST_PROMOTED_NOTIFICATIONS` (normal permission, no prompt; an unknown permission on 36.0 is ignored). Whether Android promotes the notification is the
  system's decision (`NotificationManager.canPostPromotedNotifications()`, and the user's per-app switch); the app never asks the user to enable it, never
  fails when it is off (the notification is then an ordinary ongoing one that still shows the progress bar), and never loops on it.
- Update rate stays at most once per second. The progress value is quantised to 0.1% steps (0 to 1000) so a tiny change does not re-post.

### 12.2 Accessibility rules (testable, applies to every screen and dialog)

1. Every interactive element has a role and a non-empty accessible name that says what it does (not "button"); icon-only controls have `contentDescription`;
   decorative icons have none (`null`) and are hidden from the semantics tree.
2. Touch targets are at least 48 x 48 dp (`Modifier.minimumInteractiveComponentSize()` or equivalent); small visual controls keep the large hit area.
3. Charts (`UsageBarRow` and the History charts) expose ONE summary node ("Daily usage, last 7 days, highest Wednesday, 2.1 gigabytes") and each bar
   is reachable in order with a node such as "Wednesday, 2.1 gigabytes"; selecting a bar by accessibility click does what a tap does.
4. State is never colour-only: warning, over-cap, estimated and selected states also use text or an icon with a description.
5. Text scales with the system font size up to 200% without clipping or overlap: tiles reflow to ONE column when `fontScale >= 1.5`, no `maxLines` truncation of
   a number or its unit, the navbar labels stay readable (they may switch to icon + one line). Display size ("Display size" in Settings) must not break layouts.
6. Reduce motion (`animator_duration_scale == 0`, or the system "Remove animations") disables the springs, the morphing number tween and the navbar drag
   animation, using the existing `ReduceMotionObserver`; the information shown stays identical.
7. Right-to-left (pseudo-locale `ar-XB` or a real RTL locale) mirrors layout; numbers with their units stay together (existing `ForceLtr`), and no hard-coded left/right.
8. Contrast meets WCAG AA (4.5:1 text, 3:1 large text and graphics) for the dynamic AND static palettes, light and dark, AMOLED black included; the existing
   `TileContrastTest` approach is extended to every foreground/background pair used by text and by state indicators.
9. Focus order follows reading order; a keyboard (Tab, arrows, Enter/Space) can operate every control; dialogs move focus in and return it on close; no focus trap.
10. Notification texts are complete sentences a screen reader can read ("12 gigabytes left, about 3 days" style, no raw symbols such as arrows alone).

### 12.3 Release build and distribution (`:app`, repo root)

- `versionName = "1.0.0"`, `versionCode = 1` in `app/build.gradle.kts` (the first public release; later releases increase `versionCode` by one).
- `release` build type: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-rules.pro` with ONLY the keep rules proven necessary (Room, Glance, WorkManager,
  Bouncy Castle `Argon2BytesGenerator`, kotlinx.serialization/JSON classes if any, reflection used by Robolectric is test-only and not kept). **No signing config in the
  repository**: the release APK is unsigned; F-Droid and the maintainer sign it. No secrets, keystores or `local.properties` content are committed.
- The release APK declares NO `INTERNET` permission and no `VpnService`; debug-only code (`src/debug`) is absent from it (checked with `aapt2 dump badging` and
  a class listing). The build is reproducible: two clean builds of the same commit yield APKs whose entries (ignoring the signature files) are byte-identical.
- CI (`.github/workflows/ci.yml`) additionally runs `assembleRelease` and `lintRelease`; both must pass.
- Dependencies must all be free software from Maven Central or Google Maven (F-Droid rule); `NOTICE` lists every bundled third-party component with its licence and
  matches the real dependency tree (an unused entry is removed, a missing one is added).
- Store metadata lives in `fastlane/metadata/android/en-US/` (`title.txt`, `short_description.txt` at most 80 characters, `full_description.txt` at most 4000,
  `changelogs/1.txt` at most 500 bytes, `images/icon.png` 512x512, `images/phoneScreenshots/1.png` .. at least 4 real screenshots). A draft of the F-Droid
  metadata file is kept at `docs/release/fdroiddata-io.github.khaledbahaaeldin.emberbyte.yml` for the maintainer to submit; the task never opens a merge request itself.
- Settings, About: "Open-source licences" opens a scrolling screen (route `licenses`, no deep link) that shows the contents of `NOTICE` (copied into a raw resource at
  build time by a Gradle copy task, so the app and the file cannot drift) and the Roboto Flex licence text.
