package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.lens.core.DnsAnswer
import io.github.khaledbahaaeldin.emberbyte.lens.core.FlowInfo
import io.github.khaledbahaaeldin.emberbyte.lens.core.FlowListener
import io.github.khaledbahaaeldin.emberbyte.lens.core.Forwarder
import java.util.concurrent.atomic.AtomicLong

class LensVpnService : VpnService() {

    private var tunPfd: ParcelFileDescriptor? = null
    private var tunDevice: ParcelTunDevice? = null
    private var forwarder: Forwarder? = null
    private var forwarderThread: Thread? = null

    val spikeListener = SpikeFlowListener()

    companion object {
        const val ACTION_START = "io.github.khaledbahaaeldin.emberbyte.lens.action.START"
        const val ACTION_STOP = "io.github.khaledbahaaeldin.emberbyte.lens.action.STOP"
        const val CHANNEL_ID = "lens"
        const val NOTIFICATION_ID = 2001

        fun startIntent(context: Context): Intent =
            Intent(context, LensVpnService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, LensVpnService::class.java).apply { action = ACTION_STOP }

        @Volatile
        var activeInstance: LensVpnService? = null
            internal set
    }

    class SpikeFlowListener : FlowListener {
        val flowsOpened = AtomicLong(0)
        val knownUidFlows = AtomicLong(0)
        val unknownUidFlows = AtomicLong(0)
        val flowsClosed = AtomicLong(0)
        val rxPayloadBytes = AtomicLong(0)
        val txPayloadBytes = AtomicLong(0)

        override fun onFlowOpened(flow: FlowInfo) {
            flowsOpened.incrementAndGet()
            if (flow.uid != -1) {
                knownUidFlows.incrementAndGet()
            } else {
                unknownUidFlows.incrementAndGet()
            }
        }

        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {
            rxPayloadBytes.addAndGet(rxBytes)
            txPayloadBytes.addAndGet(txBytes)
        }

        override fun onName(flowId: Long, name: String) {}

        override fun onDns(answers: List<DnsAnswer>) {}

        override fun onFlowClosed(flowId: Long) {
            flowsClosed.incrementAndGet()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (action == ACTION_START) {
            startVpn()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        activeInstance = null
        super.onDestroy()
    }

    private fun startVpn() {
        if (forwarder != null) return

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @android.annotation.SuppressLint("MissingPermission")
            val activeNet = cm.activeNetwork

            val builder = Builder()
                .setSession("Emberbyte Lens")
                .setMtu(1400)
                .addAddress("10.7.0.2", 32)
                .addAddress("fd7a:115c:a1e0::2", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setBlocking(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeNet != null) {
                builder.setUnderlyingNetworks(arrayOf(activeNet))
            }

            val pfd = builder.establish()

            if (pfd == null) {
                stopSelf()
                return
            }

            if (activeNet != null) {
                setUnderlyingNetworks(arrayOf(activeNet))
            }

            tunPfd = pfd
            val device = ParcelTunDevice(pfd)
            tunDevice = device

            val ownerResolver = ConnectivityOwnerResolver(cm)
            val socketProtector = VpnSocketProtector(this)

            val fwd = Forwarder(
                tun = device,
                protector = socketProtector,
                owner = ownerResolver,
                listener = spikeListener,
                mtu = 1400
            )
            forwarder = fwd

            val t = Thread({ fwd.run() }, "LensVpnService-Forwarder")
            forwarderThread = t
            t.start()
        } catch (_: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        val fwd = forwarder
        forwarder = null

        fwd?.stop()
        try {
            forwarderThread?.join(2000)
        } catch (_: InterruptedException) {}
        forwarderThread = null

        tunDevice?.close()
        tunDevice = null

        try {
            tunPfd?.close()
        } catch (_: Throwable) {}
        tunPfd = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    fun getCounters(): DumpCounters {
        val fwd = forwarder
        val dev = tunDevice
        return DumpCounters(
            openFlows = fwd?.openFlows ?: 0,
            evictedFlows = fwd?.evictedFlows?.get() ?: 0L,
            errors = fwd?.errors?.get() ?: 0L,
            tunRxBytes = dev?.rxBytes?.get() ?: 0L,
            tunTxBytes = dev?.txBytes?.get() ?: 0L,
            flowsOpened = spikeListener.flowsOpened.get(),
            knownUidFlows = spikeListener.knownUidFlows.get(),
            unknownUidFlows = spikeListener.unknownUidFlows.get()
        )
    }

    data class DumpCounters(
        val openFlows: Int,
        val evictedFlows: Long,
        val errors: Long,
        val tunRxBytes: Long,
        val tunTxBytes: Long,
        val flowsOpened: Long,
        val knownUidFlows: Long,
        val unknownUidFlows: Long
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lens",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Lens is running"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Lens is on")
            .setContentText("Lens is on")
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
