package io.github.khaledbahaaeldin.emberbyte.settings

import android.net.Uri
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPendingStateTest {

    private val dummyScope = SaverScope { true }

    @Test
    fun pending_state_survives_saveable_state_registry_round_trip() {
        val original = SettingsPendingState(
            initialRange = ExportRange.ALL,
            initialRestoreUri = Uri.parse("content://downloads/backup.emberbyte"),
        )
        original.pendingCreatePassphrase = "secretPassphrase".toCharArray()

        val registry = SaveableStateRegistry(emptyMap()) { true }
        registry.registerProvider("pending_state") {
            with(dummyScope) {
                with(SettingsPendingState.Saver) {
                    save(original)
                }
            }
        }

        val savedData = registry.performSave()
        assertNotNull(savedData["pending_state"])

        val restoredRegistry = SaveableStateRegistry(savedData) { true }
        val raw = restoredRegistry.consumeRestored("pending_state")
        @Suppress("UNCHECKED_CAST")
        val restoredMap = (raw as? List<*>)?.firstOrNull() as? Map<String, Any?> ?: raw as? Map<String, Any?>
        assertNotNull(restoredMap)

        val restored = with(SettingsPendingState.Saver) {
            restore(restoredMap!!)
        }
        assertNotNull(restored)
        assertEquals(ExportRange.ALL, restored!!.pendingRange)
        assertEquals(Uri.parse("content://downloads/backup.emberbyte"), restored.pendingRestoreUri)
        // Passphrase must NOT be restored across recreation
        assertNull(restored.pendingCreatePassphrase)
    }

    @Test
    fun no_passphrase_is_ever_saved() {
        val state = SettingsPendingState(
            initialRange = ExportRange.LAST_30_DAYS,
            initialRestoreUri = Uri.parse("content://downloads/test.emberbyte"),
        )
        state.pendingCreatePassphrase = "superSecretPassword123".toCharArray()

        @Suppress("UNCHECKED_CAST")
        val savedMap = with(dummyScope) {
            with(SettingsPendingState.Saver) {
                save(state)
            }
        } as Map<String, Any?>

        // Assert that no key or value contains the passphrase
        assertFalse(savedMap.containsKey("passphrase"))
        assertFalse(savedMap.containsKey("pendingCreatePassphrase"))
        assertFalse(savedMap.values.any { it?.toString()?.contains("superSecretPassword123") == true })
    }
}
