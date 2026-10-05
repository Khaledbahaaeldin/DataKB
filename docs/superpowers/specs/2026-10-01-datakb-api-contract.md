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
- Module dependency direction (no cycles): `:core:engine` ← `:core:data` ← `:feature:lens`, `:app`;
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
    val lensHistoryHours: Int = 24,                         // rolling retention of domain hits
    val hapticsEnabled: Boolean = true,
)
enum class UnitSystem { DECIMAL, BINARY }

interface SettingsRepository {
    fun observe(): Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings): Outcome<Unit>
}

data class PermissionState(
    val usageAccess: Boolean, val notifications: Boolean, val phoneState: Boolean,
    val vpnConsentGranted: Boolean,
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

## 4. Lens contract (module `:feature:lens`, public API)

```kotlin
sealed interface LensState {
    data object Off : LensState
    data object NeedsConsent : LensState
    data object Starting : LensState
    data class Running(val since: Instant) : LensState
    data class Conflict(val reason: LensConflict) : LensState
    data class Error(val message: String) : LensState
}
enum class LensConflict { OTHER_VPN_ACTIVE, ALWAYS_ON_VPN_BLOCKING }

data class LensApp(
    val uid: Int, val packageName: String, val label: String,
    val rxBps: Long, val txBps: Long, val topDomains: List<LensDomain>,
)
data class LensDomain(val name: String, val bytes: Long, val lastSeen: Instant)
data class LensSnapshot(val at: Instant, val apps: List<LensApp>)   // sorted by rx+tx rate desc

interface LensController {
    val state: StateFlow<LensState>
    fun observeSnapshot(): Flow<LensSnapshot>
    /** Intent for the system VPN consent dialog, or null if consent is already granted. */
    fun prepareConsentIntent(): Intent?
    suspend fun start(): Outcome<Unit>
    suspend fun stop(): Outcome<Unit>
    suspend fun clearHistory(): Outcome<Unit>
}
```

Guarantees: while `state != Running`, `observeSnapshot()` emits nothing. `stop()` tears down the tunnel
and the foreground service before returning. Lens never writes to the usage tables; it only owns
`lens_domain_hit`.

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
| `lens_domain_hit` | (`uid`, `domain`, `hourStart`) | `bytes`, `lastSeen` |
| `counter_checkpoint` | `source` | `at`, `rxBytes`, `txBytes`, `bootId` |

**Database version 1 contains only the usage tables** (`total_minute`, `usage_hourly`, `app_meta`, `coverage_gap`,
`counter_checkpoint`); the plan, add-on, free-rule, alert, budget, spike and Lens tables arrive with their milestones as
Migration(1, 2) and later. **`usage_hourly` rows are window rows:** `hourStart` is the start of a 2-hour window aligned to
even UTC hours (the platform's usage-history bucket), and the bytes cover that window (or the part of it that has elapsed).
The name is historical.
Times are stored as epoch milliseconds (`Long`). `network` is `0 = MOBILE`, `1 = WIFI`.
`subscriptionId` is `-1` for Wi-Fi or unknown. Settings live in Jetpack DataStore, not Room.
Retention: `total_minute` 7 days, `usage_hourly` 13 months, `lens_domain_hit` per
`Settings.lensHistoryHours`, `coverage_gap` 13 months. Every schema change ships a tested migration
and an exported schema JSON.

## 6. Android component contract

| Component | Name | Contract |
|---|---|---|
| Foreground service | `SamplerService` | Type `specialUse`. Owns the 1 Hz live sampler, flushes deltas to Room every 60 s. Started on boot and app launch. |
| Foreground service | `LensVpnService` | Extends `VpnService`. Only started by `LensController.start()`. |
| Worker | `CatchUpWorker` | Unique periodic work, 15 min. Runs the catch-up. Android 12+ forbids starting a foreground service from the background, so when `SamplerService` is not running it first TRIES to start it and, when Android refuses, posts the `status` notification "Measurement paused - tap to resume". A force-stop also cancels WorkManager jobs until the next launch, so this notification only appears after OS or OEM kills. |
| Alert evaluation | `AlertRunner` (M3: plan alerts; M4 adds budgets and spikes) | See section 10.3: called every 60 s by `SamplerService` and after every `CatchUpWorker` run. There is no separate `AlertWorker`. |
| Receiver | `BootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → start the sampler **only if onboarding is complete** (the sampler records the `REBOOT` gap itself). |
| Widgets | `HeroWidget`, `BentoWidget` | Glance. Read `PlanRepository.observeActivePlanStates()` and `UsageRepository.observeToday()`; refresh cadence in section 10.3. |

Notification channels: `live` (low importance, ongoing), `status` (low importance, "Measurement paused"), `alerts` (default importance),
`lens` (low importance, ongoing), `spikes` (default importance).

Deep links: `emberbyte://home`, `emberbyte://apps`, `emberbyte://apps/{packageName}`, `emberbyte://plans`,
`emberbyte://plans/{planId}`, `emberbyte://lens`, `emberbyte://settings`. Notifications and widgets use only these.

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
| `PermissionRepository` | `AndroidPermissionRepository` | `recheck()` re-reads usage access, notifications, `READ_PHONE_STATE`; `vpnConsentGranted` is always false until M5 |
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
  limitation: a VPN running over Wi-Fi counts its tunnel bytes twice (Live Lens, M5, will address it).
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
| Deep links | `MainActivity` declares an `<intent-filter>` for scheme `emberbyte` with hosts `home`, `apps`, `plans`, `lens`, `settings`; navigation destinations declare the matching `navDeepLink`s of section 6. |
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
