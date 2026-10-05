package io.github.khaledbahaaeldin.emberbyte

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.khaledbahaaeldin.emberbyte.data.AndroidPermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.AppPreferences
import io.github.khaledbahaaeldin.emberbyte.data.BudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.RoomUsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.SpikeRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertEvaluator
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertRepository
import io.github.khaledbahaaeldin.emberbyte.data.alert.RoomAlertRepository
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetEvaluator
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetStores
import io.github.khaledbahaaeldin.emberbyte.data.budget.RoomBudgetRepository
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanStores
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.spike.RoomSpikeRepository
import io.github.khaledbahaaeldin.emberbyte.data.spike.SpikeEvaluator
import io.github.khaledbahaaeldin.emberbyte.data.spike.SpikeStores
import io.github.khaledbahaaeldin.emberbyte.data.android.AndroidSimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.android.AppOpsUsageAccess
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.android.ConnectivityNetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.data.android.NetworkStatsManagerSource
import io.github.khaledbahaaeldin.emberbyte.data.android.PackageManagerAppInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.android.TelephonySubscriptionSource
import io.github.khaledbahaaeldin.emberbyte.data.android.TrafficStatsCounterSource
import io.github.khaledbahaaeldin.emberbyte.data.sampler.HourlyCatchUp
import io.github.khaledbahaaeldin.emberbyte.data.sampler.SamplerEngine
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStores
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.apps.AppDetailViewModel
import io.github.khaledbahaaeldin.emberbyte.apps.AppsViewModel
import io.github.khaledbahaaeldin.emberbyte.history.HistoryViewModel
import io.github.khaledbahaaeldin.emberbyte.home.HomeViewModel
import io.github.khaledbahaaeldin.emberbyte.onboarding.OnboardingViewModel
import io.github.khaledbahaaeldin.emberbyte.plans.PlanEditorViewModel
import io.github.khaledbahaaeldin.emberbyte.plans.PlansViewModel
import io.github.khaledbahaaeldin.emberbyte.settings.SettingsViewModel
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Hand-written dependency graph (no DI framework). One instance per process, owned by [EmberbyteApplication].
 * Production wiring only: the `Fake*` classes in `:core:data` are for tests and previews.
 */
class AppGraph(context: Context, val clock: Clock = SystemZoneClock()) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences.create(appContext)
    private val store: UsageStore = UsageStores.create(appContext)

    val settings: SettingsRepository = preferences.settings
    val onboarding: OnboardingRepository = preferences.onboarding
    val permissions: PermissionRepository = AndroidPermissionRepository(appContext)
    val simInfo: SimInfoSource = AndroidSimInfoSource(appContext)
    internal val planStore = PlanStores.create(appContext)
    val plans: PlanRepository = RoomPlanRepository(planStore, store, clock)
    val alerts: AlertRepository = RoomAlertRepository(planStore)
    val alertEvaluator = AlertEvaluator(plans, planStore)

    val budgetStore = BudgetStores.create(appContext)
    val spikeStore = SpikeStores.create(appContext)
    private val budgetsImpl = RoomBudgetRepository(budgetStore, planStore, store, clock)
    val budgets: BudgetRepository = budgetsImpl
    val spikes: SpikeRepository = RoomSpikeRepository(spikeStore)
    val budgetEvaluator = BudgetEvaluator({ budgetsImpl.currentPeriodStatuses() }, budgetStore)
    val spikeEvaluator = SpikeEvaluator(store, spikeStore, clock)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Hot cache of the onboarding flag. `null` only until the first read; collecting it never restarts the underlying flow. */
    val onboardingCompleted: StateFlow<Boolean?> =
        onboarding.observeCompleted().stateIn(appScope, SharingStarted.Eagerly, null)

    /** Hot cache of the settings. `null` only until the first read; collecting it never restarts the underlying flow. */
    val settingsState: StateFlow<Settings?> =
        settings.observe().map<Settings, Settings?> { it }.stateIn(appScope, SharingStarted.Eagerly, null)

    val sampler = SamplerEngine(
        counters = TrafficStatsCounterSource(appContext, clock),
        network = ConnectivityNetworkKindSource(appContext),
        subscription = TelephonySubscriptionSource(),
        store = store,
    )
    val catchUp = HourlyCatchUp(
        source = NetworkStatsManagerSource(appContext),
        store = store,
        appInfo = PackageManagerAppInfoSource(appContext),
        access = AppOpsUsageAccess(appContext),
        clock = clock,
    )
    val usage: UsageRepository = RoomUsageRepository(store, sampler.liveSpeed, DayClock(clock), clock) { catchUp.run() }

    /** Deletes rows older than the retention windows (cheap; called from the service loop). */
    suspend fun prune() = store.prune(clock.instant())

    fun homeViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(usage, plans, settings, permissions, clock = clock, is24Hour = { DateFormat.is24HourFormat(appContext) }) }
    }

    fun appsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppsViewModel(usage, settings, permissions) }
    }

    fun appDetailViewModelFactory(packageName: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppDetailViewModel(packageName, usage, settings) }
    }

    fun historyViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HistoryViewModel(usage, settings, permissions) }
    }

    fun settingsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settings) }
    }

    fun onboardingViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { OnboardingViewModel(onboarding, permissions, plans) }
    }

    fun plansViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { PlansViewModel(plans, settings, simInfo, clock) }
    }

    fun planEditorViewModelFactory(planId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            PlanEditorViewModel(
                planId = planId,
                plans = plans,
                alerts = alerts,
                settings = settings,
                permissions = permissions,
                sims = simInfo,
                clock = clock,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }
}
