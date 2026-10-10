package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.net.VpnService
import io.github.khaledbahaaeldin.emberbyte.lens.core.SocketProtector
import java.net.DatagramSocket
import java.net.Socket
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectableChannel
import java.nio.channels.SocketChannel

class VpnSocketProtector(
    private val service: VpnService
) : SocketProtector {

    override fun protect(socket: Socket): Boolean = service.protect(socket)

    override fun protect(socket: DatagramSocket): Boolean = service.protect(socket)

    override fun protect(channel: SelectableChannel): Boolean {
        return when (channel) {
            is SocketChannel -> service.protect(channel.socket())
            is DatagramChannel -> service.protect(channel.socket())
            else -> false
        }
    }
}
