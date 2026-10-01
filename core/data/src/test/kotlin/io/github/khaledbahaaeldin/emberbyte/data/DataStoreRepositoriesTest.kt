package io.github.khaledbahaaeldin.emberbyte.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreRepositoriesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") }

    @Test fun an_empty_store_gives_the_default_settings() = runTest {
        assertEquals(Settings(), DataStoreSettingsRepository(store()).observe().first())
    }

    @Test fun every_setting_survives_a_round_trip() = runTest {
        val repo = DataStoreSettingsRepository(store())
        val changed = Settings(
            liveNotificationEnabled = false, notificationShowsSpeed = false, spikeAlertsEnabled = false,
            amoledBlack = true, useDynamicColor = false, unitSystem = UnitSystem.BINARY,
            lensHistoryHours = 6, hapticsEnabled = false,
        )
        repo.update { changed }
        assertEquals(changed, repo.observe().first())
    }

    @Test fun update_receives_the_current_settings() = runTest {
        val repo = DataStoreSettingsRepository(store())
        repo.update { it.copy(amoledBlack = true) }
        repo.update { it.copy(hapticsEnabled = false) }
        val settings = repo.observe().first()
        assertTrue(settings.amoledBlack); assertEquals(false, settings.hapticsEnabled)
    }

    @Test fun onboarding_starts_unknown_then_false_then_true_after_complete() = runTest {
        val repo = DataStoreOnboardingRepository(store())
        assertEquals(listOf<Boolean?>(null, false), repo.observeCompleted().take(2).toList())
        repo.complete()
        assertEquals(true, repo.observeCompleted().first { it != null })
    }

    @Test fun an_unknown_unit_value_falls_back_to_decimal() = runTest {
        val dataStore = store()
        val repo = DataStoreSettingsRepository(dataStore)
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().also { it[SettingsKeys.UNIT_SYSTEM] = "NOT_A_UNIT" }
        }
        assertEquals(UnitSystem.DECIMAL, repo.observe().first().unitSystem)
    }
}
