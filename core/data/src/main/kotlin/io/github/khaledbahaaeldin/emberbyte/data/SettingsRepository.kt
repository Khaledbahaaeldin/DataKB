package io.github.khaledbahaaeldin.emberbyte.data

import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import kotlinx.coroutines.flow.Flow

enum class UnitSystem { DECIMAL, BINARY }

data class Settings(
    val liveNotificationEnabled: Boolean = true,
    val notificationShowsSpeed: Boolean = true,
    val spikeAlertsEnabled: Boolean = true,
    val amoledBlack: Boolean = false,
    val useDynamicColor: Boolean = true,
    val unitSystem: UnitSystem = UnitSystem.DECIMAL,
    val lensHistoryHours: Int = 24,
    val hapticsEnabled: Boolean = true,
)

interface SettingsRepository {
    fun observe(): Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings): Outcome<Unit>
}
