# DataKB API Contract (v1)

Status: draft for review. Source of truth for every name used in the
[frontend spec](2026-10-01-datakb-frontend-spec.md) and
[backend spec](2026-10-01-datakb-backend-spec.md). If a name differs between documents, this file wins.

DataKB has no server. The "API" is the set of typed boundaries between modules, plus the Android
component, storage and file-format contracts. Root package: `io.github.khaledbahaaeldin.datakb`
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
    data class Failure(val error: DataKbError) : Outcome<Nothing>
}

sealed interface DataKbError {
    data object MissingUsageAccess : DataKbError
    data object MissingPhoneState : DataKbError
    data object NotFound : DataKbError
    data class Invalid(val field: String, val reason: String) : DataKbError
    data class Storage(val cause: String) : DataKbError
    data class Unexpected(val cause: String) : DataKbError
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
enum class GapReason { SERVICE_KILLED, REBOOT, COUNTER_RESET, PERMISSION_MISSING }

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
    /** Turns raw cumulative counter readings into non-negative deltas; a decrease = reset (delta = new value). */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta
}
data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)
data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)
```

## 3. Repository contract (module `:core:data`, public API)

All repositories are interfaces with a Room/DataStore-backed implementation, provided through Hilt.

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

## 5. Storage contract (Room, database `datakb.db`, version 1)

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
| Worker | `CatchUpWorker` | Unique periodic work, 15 min. Reconciles `NetworkStatsManager` history, records gaps, restarts `SamplerService` if dead. |
| Worker | `AlertWorker` | Triggered after each flush. Evaluates `AlertRule`, `AppBudget`, `SpikeDetector`; posts notifications. |
| Receiver | `BootReceiver` | `BOOT_COMPLETED` → start sampler, enqueue workers, record a `REBOOT` gap. |
| Widgets | `HeroWidget`, `BentoWidget` | Glance. Read `PlanRepository.observeActivePlanStates()` and `UsageRepository.observeToday()`; refresh at most every 15 min plus on sampler flush. |

Notification channels: `live` (low importance, ongoing), `alerts` (default importance),
`lens` (low importance, ongoing), `spikes` (default importance).

Deep links: `datakb://home`, `datakb://apps`, `datakb://apps/{packageName}`, `datakb://plans`,
`datakb://plans/{planId}`, `datakb://lens`, `datakb://settings`. Notifications and widgets use only these.

## 7. File formats

**JSON export** (`datakb-export-v1.json`):

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

**Backup file** (`.datakb`): the JSON export, gzip-compressed, then encrypted with AES-256-GCM using a key
derived from the passphrase with Argon2id. Restore fails with `DataKbError.Invalid("passphrase", …)` on
a wrong passphrase and never partially applies.

## 8. Change rules

1. Any change to a type or method signature here updates this file in the same commit as the code.
2. A breaking change bumps `CONTRACT_VERSION` and the JSON `contractVersion`, and adds a migration or
   an import shim for older exports.
3. Frontend and backend specs reference these names verbatim and never redefine them.
