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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    val tunWriteLock: Any,
    val oversizedDatagramsCounter: AtomicLong? = null,
    val protectFailuresCounter: AtomicLong? = null
) {
    val openedAt: Instant = clock.instant()
    var lastActiveAt: Instant = openedAt
    val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    val isIpv6: Boolean = src.address is Inet6Address
    val maxPayload: Int = if (isIpv6) mtu - 48 else mtu - 28

    var channel: DatagramChannel? = null
    var selectionKey: SelectionKey? = null

    val isDnsPort: Boolean = dst.port == 53
    val idleTimeoutSeconds: Long = if (isDnsPort) 10L else 60L

    private val writeBuffer = ByteBuffer.allocate(mtu + 200)
    private val readBuffer = ByteBuffer.allocate(65535)

    init {
        listener.onFlowOpened(FlowInfo(flowId, ownerUid, 17, src, dst, openedAt))
        try {
            val dc = DatagramChannel.open()
            channel = dc
            dc.configureBlocking(false)

            val protected = try {
                protector.protect(dc)
            } catch (_: Exception) {
                false
            }

            if (!protected) {
                protectFailuresCounter?.incrementAndGet()
                try { dc.close() } catch (_: Exception) {}
                close()
            } else {
                if (!isDnsPort) {
                    dc.connect(dst) // connect so only packets from dst are received
                } else {
                    if (dst.address.isLoopbackAddress) {
                        dc.bind(InetSocketAddress(dst.address, 0))
                    } else {
                        dc.bind(null)
                    }
                }
                selectionKey = dc.register(selector, SelectionKey.OP_READ, this)
            }
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
            if (ch.isConnected) {
                ch.write(buf)
            } else {
                ch.send(buf, dst)
            }
            listener.onBytes(flowId, rxBytes = 0, txBytes = udp.payloadLength.toLong())
        } catch (e: Exception) {
            close()
        }
    }

    fun onSocketReadable() {
        lastActiveAt = clock.instant()
        val ch = channel ?: return

        try {
            readBuffer.clear()
            val readBytes = if (ch.isConnected) {
                ch.read(readBuffer)
            } else {
                val senderAddr = ch.receive(readBuffer)
                if (senderAddr == null) 0 else readBuffer.position()
            }
            if (readBytes > 0) {
                readBuffer.flip()

                // If reply exceeds TUN MTU, drop it and count, keep flow alive
                if (readBytes > maxPayload) {
                    oversizedDatagramsCounter?.incrementAndGet()
                    return
                }

                val payload = ByteArray(readBytes)
                readBuffer.get(payload)

                // Report bytes
                listener.onBytes(flowId, rxBytes = readBytes.toLong(), txBytes = 0)

                // Only parse as DNS on port 53
                if (isDnsPort) {
                    val answers = DnsParser.answers(payload)
                    if (answers.isNotEmpty()) {
                        listener.onDns(answers)
                    }
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
        } catch (_: java.net.PortUnreachableException) {
            // Remote port unreachable (e.g. ICMP port unreachable on Windows when query packet was sent to port without listener).
            // Keep UDP flow alive.
        } catch (e: Exception) {
            close()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
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
