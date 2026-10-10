package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.time.Clock
import java.time.Duration
import java.time.Instant

class UdpFlow(
    val flowId: Long,
    val src: InetSocketAddress,
    val dst: InetSocketAddress,
    val ownerUid: Int,
    val tun: TunDevice,
    val protector: SocketProtector,
    val listener: FlowListener,
    val clock: Clock,
    val mtu: Int,
    val selector: Selector,
    val tunWriteLock: Any
) {
    val openedAt: Instant = clock.instant()
    var lastActiveAt: Instant = openedAt
    var isClosed = false

    var channel: DatagramChannel? = null
    var selectionKey: SelectionKey? = null

    val isDnsPort: Boolean = dst.port == 53
    val idleTimeoutSeconds: Long = if (isDnsPort) 10L else 60L

    private val writeBuffer = ByteBuffer.allocate(mtu + 200)

    init {
        listener.onFlowOpened(FlowInfo(flowId, ownerUid, 17, src, dst, openedAt))
        try {
            val dc = DatagramChannel.open()
            channel = dc
            dc.configureBlocking(false)
            protector.protect(dc)
            dc.connect(dst) // connect so only packets from dst are received
            selectionKey = dc.register(selector, SelectionKey.OP_READ, this)
        } catch (e: Exception) {
            close()
        }
    }

    fun handlePacketFromTun(udp: ParsedPacket.Udp, raw: ByteArray) {
        lastActiveAt = clock.instant()
        val ch = channel ?: return
        if (udp.payloadLength <= 0) return

        try {
            val buf = ByteBuffer.wrap(raw, udp.payloadOffset, udp.payloadLength)
            ch.write(buf)
            listener.onBytes(flowId, rxBytes = 0, txBytes = udp.payloadLength.toLong())
        } catch (e: Exception) {
            close()
        }
    }

    fun onSocketReadable() {
        lastActiveAt = clock.instant()
        val ch = channel ?: return
        val readBuf = ByteBuffer.allocate(65535)

        try {
            val readBytes = ch.read(readBuf)
            if (readBytes > 0) {
                readBuf.flip()
                val payload = ByteArray(readBytes)
                readBuf.get(payload)

                // Report bytes
                listener.onBytes(flowId, rxBytes = readBytes.toLong(), txBytes = 0)

                // Check DNS answers
                val answers = DnsParser.answers(payload)
                if (answers.isNotEmpty()) {
                    listener.onDns(answers)
                }

                // Write packet back to TUN
                synchronized(tunWriteLock) {
                    writeBuffer.clear()
                    val totalLen = PacketWriter.writeUdp(
                        buffer = writeBuffer,
                        src = dst, // from dst to src
                        dst = src,
                        payload = payload
                    )
                    writeBuffer.flip()
                    tun.write(writeBuffer, totalLen)
                }
            }
        } catch (e: Exception) {
            close()
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            selectionKey?.cancel()
            channel?.close()
        } catch (_: Exception) {}
        listener.onFlowClosed(flowId)
    }

    fun isDead(now: Instant): Boolean {
        if (isClosed) return true
        if (Duration.between(lastActiveAt, now).seconds >= idleTimeoutSeconds) {
            return true
        }
        return false
    }
}
