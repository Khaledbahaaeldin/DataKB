package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.DatagramSocket
import java.net.Socket
import java.nio.channels.SelectableChannel

interface SocketProtector {
    fun protect(socket: Socket): Boolean
    fun protect(socket: DatagramSocket): Boolean
    fun protect(channel: SelectableChannel): Boolean
}
