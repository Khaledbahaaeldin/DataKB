package io.github.khaledbahaaeldin.emberbyte.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal object SettingsKeys {
    val LIVE_NOTIFICATION = booleanPreferencesKey("live_notification_enabled")
    val NOTIFICATION_SHOWS_SPEED = booleanPreferencesKey("notification_shows_speed")
    val SPIKE_ALERTS = booleanPreferencesKey("spike_alerts_enabled")
    val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val UNIT_SYSTEM = stringPreferencesKey("unit_system")
    val LENS_HISTORY_HOURS = intPreferencesKey("lens_history_hours")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        liveNotificationEnabled = this[SettingsKeys.LIVE_NOTIFICATION] ?: d.liveNotificationEnabled,
        notificationShowsSpeed = this[SettingsKeys.NOTIFICATION_SHOWS_SPEED] ?: d.notificationShowsSpeed,
        spikeAlertsEnabled = this[SettingsKeys.SPIKE_ALERTS] ?: d.spikeAlertsEnabled,
        amoledBlack = this[SettingsKeys.AMOLED_BLACK] ?: d.amoledBlack,
        useDynamicColor = this[SettingsKeys.USE_DYNAMIC_COLOR] ?: d.useDynamicColor,
        unitSystem = this[SettingsKeys.UNIT_SYSTEM]
            ?.let { name -> runCatching { UnitSystem.valueOf(name) }.getOrNull() } ?: d.unitSystem,
        lensHistoryHours = this[SettingsKeys.LENS_HISTORY_HOURS] ?: d.lensHistoryHours,
        hapticsEnabled = this[SettingsKeys.HAPTICS] ?: d.hapticsEnabled,
    )
}

internal fun MutablePreferences.write(settings: Settings) {
    this[SettingsKeys.LIVE_NOTIFICATION] = settings.liveNotificationEnabled
    this[SettingsKeys.NOTIFICATION_SHOWS_SPEED] = settings.notificationShowsSpeed
    this[SettingsKeys.SPIKE_ALERTS] = settings.spikeAlertsEnabled
    this[SettingsKeys.AMOLED_BLACK] = settings.amoledBlack
    this[SettingsKeys.USE_DYNAMIC_COLOR] = settings.useDynamicColor
    this[SettingsKeys.UNIT_SYSTEM] = settings.unitSystem.name
    this[SettingsKeys.LENS_HISTORY_HOURS] = settings.lensHistoryHours
    this[SettingsKeys.HAPTICS] = settings.hapticsEnabled
}

private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override fun observe(): Flow<Settings> = store.data.safe().map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings): Outcome<Unit> = try {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        Outcome.Success(Unit)
    } catch (error: IOException) {
        Outcome.Failure(EmberbyteError.Storage(error.message ?: "I/O error"))
    }
}

interface OnboardingRepository {
    /** `null` while the stored value is still loading. */
    fun observeCompleted(): Flow<Boolean?>
    suspend fun complete()
}

class DataStoreOnboardingRepository(private val store: DataStore<Preferences>) : OnboardingRepository {
    override fun observeCompleted(): Flow<Boolean?> = store.data.safe()
        .map<Preferences, Boolean?> { it[SettingsKeys.ONBOARDING_COMPLETED] ?: false }
        .onStart { emit(null) }

    override suspend fun complete() {
        store.edit { it[SettingsKeys.ONBOARDING_COMPLETED] = true }
    }
}
