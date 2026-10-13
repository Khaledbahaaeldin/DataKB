package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.lens.android.LensVpnService.Companion.NOTIFICATION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LensVpnServiceTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowCm: ShadowConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowCm = shadowOf(connectivityManager)
        LensStartResult.set(null)
    }

    class TestableLensVpnService(
        var pfdToReturn: ParcelFileDescriptor? = null
    ) : LensVpnService() {
        var underlyingNetworksList: List<Array<out Network>?> = mutableListOf()

        public override fun establish(): ParcelFileDescriptor? {
            return pfdToReturn
        }

        public override fun setUnderlyingNetworks(networks: Array<out Network>?): Boolean {
            (underlyingNetworksList as MutableList).add(networks)
            return super.setUnderlyingNetworks(networks)
        }
    }

    @Test
    fun establish_returning_null_posts_error_notification_records_result_and_stops_self() {
        val controller = Robolectric.buildService(TestableLensVpnService::class.java)
        val service = controller.create().get()
        service.pfdToReturn = null

        val startIntent = LensVpnService.startIntent(context)
        service.onStartCommand(startIntent, 0, 1)

        // D3 assertions:
        // 1. Publishes a failed start through LensStartResult
        val result = LensStartResult.get()
        assertNotNull("LensStartResult must be published on failure", result)
        assertEquals(LensStartResult.NoConsent, result)

        // 2. Posts a notification on the 'lens' channel
        val notification = shadowNotificationManager.getNotification(NOTIFICATION_ID)
        assertNotNull("Notification must be posted", notification)
        assertEquals(LensVpnService.CHANNEL_ID, notification.channelId)
        assertEquals("Lens could not start", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Open Emberbyte and turn Lens on again.", notification.extras.getString(Notification.EXTRA_TEXT))

        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, contentIntent.action)
        assertEquals("emberbyte://lens", contentIntent.data.toString())

        // 3. Stops itself: shadowOf(service).isStoppedBySelf
        val shadowService = shadowOf(service)
        assertTrue("Service must call stopSelf()", shadowService.isStoppedBySelf)
    }

    @Test
    fun running_notification_has_d5_title_and_text() {
        val dummyPfd = ParcelFileDescriptor.createPipe()[0]
        val controller = Robolectric.buildService(TestableLensVpnService::class.java)
        val service = controller.create().get()
        service.pfdToReturn = dummyPfd

        service.onStartCommand(LensVpnService.startIntent(context), 0, 1)

        val notification = shadowNotificationManager.getNotification(NOTIFICATION_ID)
        assertNotNull(notification)
        // D5: Title "Lens is on", text "Watching which apps use data"
        assertEquals("Lens is on", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Watching which apps use data", notification.extras.getString(Notification.EXTRA_TEXT))

        service.onStartCommand(LensVpnService.stopIntent(context), 0, 2)
        dummyPfd.close()
    }

    @Test
    fun d6_underlying_networks_helper_registers_callback_and_forwards_network_changes() {
        var notifiedNetwork: Network? = null
        val helper = UnderlyingNetworks(connectivityManager) { net ->
            notifiedNetwork = net
        }

        helper.start()

        // ShadowConnectivityManager maintains registered network callbacks
        val callbacks = shadowCm.networkCallbacks
        assertTrue("Network callback must be registered", callbacks.isNotEmpty())

        val networkCallbacks = callbacks.toList()
        // Simulate a network becoming available
        val dummyNetwork = Shadow.newInstanceOf(Network::class.java)
        for (cb in networkCallbacks) {
            cb.onAvailable(dummyNetwork)
        }
        assertEquals(dummyNetwork, notifiedNetwork)
        assertEquals(dummyNetwork, helper.currentUnderlyingNetwork)

        // Simulate network lost
        for (cb in networkCallbacks) {
            cb.onLost(dummyNetwork)
        }
        assertNull(notifiedNetwork)
        assertNull(helper.currentUnderlyingNetwork)

        helper.stop()
        assertTrue("Network callback must be unregistered", shadowCm.networkCallbacks.isEmpty())
    }
}
