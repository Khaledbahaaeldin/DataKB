package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowConnectivityManager
import java.net.InetAddress
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ConnectivityOwnerResolverTest.ShadowTestConnectivityManager::class])
class ConnectivityOwnerResolverTest {

    @Implements(ConnectivityManager::class)
    class ShadowTestConnectivityManager : ShadowConnectivityManager() {
        var throwException = false
        val owners = mutableMapOf<Triple<Int, InetSocketAddress, InetSocketAddress>, Int>()

        @Implementation
        protected fun getConnectionOwnerUid(
            protocol: Int,
            local: InetSocketAddress,
            remote: InetSocketAddress
        ): Int {
            if (throwException) {
                throw SecurityException("Permission denied or resolver error")
            }
            return owners[Triple(protocol, local, remote)] ?: Process.INVALID_UID
        }
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowCm: ShadowTestConnectivityManager
    private lateinit var resolver: ConnectivityOwnerResolver

    private val localTcp = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54321)
    private val remoteTcp = InetSocketAddress(InetAddress.getByName("93.184.216.34"), 80)
    private val localUdp = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54322)
    private val remoteUdp = InetSocketAddress(InetAddress.getByName("8.8.8.8"), 53)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowCm = Shadow.extract(connectivityManager)
        resolver = ConnectivityOwnerResolver(connectivityManager)
    }

    @Test
    fun returnsMinusOneWhenOwnerIsUnknown() {
        val uid = resolver.uidOf(6, localTcp, remoteTcp)
        assertEquals(-1, uid)
    }

    @Test
    fun returnsMinusOneOnException() {
        shadowCm.throwException = true
        val uid = resolver.uidOf(6, localTcp, remoteTcp)
        assertEquals(-1, uid)
    }

    @Test
    fun returnsKnownUidForTcpProtocol() {
        shadowCm.owners[Triple(6, localTcp, remoteTcp)] = 10123
        val uid = resolver.uidOf(6, localTcp, remoteTcp)
        assertEquals(10123, uid)
    }

    @Test
    fun returnsKnownUidForUdpProtocol() {
        shadowCm.owners[Triple(17, localUdp, remoteUdp)] = 10124
        val uid = resolver.uidOf(17, localUdp, remoteUdp)
        assertEquals(10124, uid)
    }
}
