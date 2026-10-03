package io.github.khaledbahaaeldin.emberbyte.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPermissionRepositoryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun phone_state_follows_the_granted_permission_after_recheck() = runBlocking {
        val repo = AndroidPermissionRepository(app)
        assertFalse(repo.observe().first().phoneState)
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        repo.recheck()
        assertTrue(repo.observe().first().phoneState)
    }

    @Test fun vpn_consent_is_always_false_until_lens_exists() = runBlocking {
        assertEquals(false, AndroidPermissionRepository(app).observe().first().vpnConsentGranted)
    }
}
