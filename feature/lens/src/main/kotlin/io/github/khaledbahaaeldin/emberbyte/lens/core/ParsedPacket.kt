package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress

sealed interface ParsedPacket {
    val src: InetSocketAddress
    val dst: InetSocketAddress
    val protocol: Int // 6 TCP, 17 UDP
    val payloadOffset: Int
    val payloadLength: Int

    data class Tcp(
        override val src: InetSocketAddress,
        override val dst: InetSocketAddress,
        val seq: Long,
        val ack: Long,
        val flags: Int,
        val window: Int,
        val mss: Int?,
        override val payloadOffset: Int,
        override val payloadLength: Int
    ) : ParsedPacket {
        override val protocol: Int get() = 6
    }

    data class Udp(
        override val src: InetSocketAddress,
        override val dst: InetSocketAddress,
        override val payloadOffset: Int,
        override val payloadLength: Int
    ) : ParsedPacket {
        override val protocol: Int get() = 17
    }
}

object TcpFlags {
    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10
}
