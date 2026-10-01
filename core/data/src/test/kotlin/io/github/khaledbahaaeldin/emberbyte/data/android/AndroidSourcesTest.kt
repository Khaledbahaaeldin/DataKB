package io.github.khaledbahaaeldin.emberbyte.data.android

import android.app.usage.NetworkStats
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSourcesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun special_uids_have_friendly_names() {
        assertEquals(AppMeta(NetworkStats.Bucket.UID_REMOVED, "uid:removed", "Removed apps"), specialUidMeta(NetworkStats.Bucket.UID_REMOVED))
        assertEquals(AppMeta(NetworkStats.Bucket.UID_TETHERING, "uid:tethering", "Hotspot & tethering"), specialUidMeta(NetworkStats.Bucket.UID_TETHERING))
        assertEquals(AppMeta(1000, "android", "Android system"), specialUidMeta(1000))
        assertNull(specialUidMeta(10_123))
    }

    @Test fun network_kind_source_constructs_and_reports_a_known_value() {
        // Robolectric's default connectivity may or may not expose capabilities; either is fine, a crash is not.
        val value = ConnectivityNetworkKindSource(context).current.value
        assertTrue(value == null || value == NetworkKind.WIFI || value == NetworkKind.MOBILE)
    }

    @Test fun subscription_source_never_throws_and_returns_minus_one_or_an_id() {
        assertTrue(TelephonySubscriptionSource().defaultDataSubscriptionId() >= -1)
    }
}
