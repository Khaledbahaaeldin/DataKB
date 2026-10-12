package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.io.ByteArrayOutputStream
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TcpClientSim(
    val tun: FakeTun,
    val src: InetSocketAddress,
    val dst: InetSocketAddress,
    var seq: Long = 1000L,
    var ack: Long = 0L,
    var advertisedWindow: Int = 65535,
    val dropRate: Double = 0.0,
    val random: Random = Random(42),
    val mss: Int? = null
) {
    val isIpv6: Boolean = src.address is Inet6Address
    val receivedData = ByteArrayOutputStream()
    val pendingSegments = java.util.TreeMap<Long, ByteArray> { a, b -> (a - b).toInt() }
    var lastPeerAck: Long = 0L
    val parser = PacketParser()
    val closed = AtomicBoolean(false)
    val handshakedLatch = CountDownLatch(1)
    val finReceivedLatch = CountDownLatch(1)
    val rstReceivedLatch = CountDownLatch(1)
    var autoAck: Boolean = true
    private val clientIncomingQueue = LinkedBlockingQueue<ByteArray>()

    private val packetSubscriber: (ByteArray) -> Unit = { pkt ->
        if (!closed.get() && pkt.size >= 20) {
            // Quick check destination IP and port matching this client's src IP/port
            val isV4 = (pkt[0].toInt().ushr(4) and 0x0F) == 4
            val matches = if (!isIpv6 && isV4 && pkt.size >= 40) {
                // IPv4 destination address starts at offset 16 (4 bytes)
                val dstIpMatches = pkt[16] == src.address.address[0] &&
                        pkt[17] == src.address.address[1] &&
                        pkt[18] == src.address.address[2] &&
                        pkt[19] == src.address.address[3]
                // TCP dst port is at IP header len (usually 20) + 2
                val ihl = (pkt[0].toInt() and 0x0F) * 4
                val dstPort = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
                dstIpMatches && dstPort == src.port
            } else if (isIpv6 && !isV4 && pkt.size >= 60) {
                // IPv6 destination address starts at offset 24 (16 bytes)
                val dstPort = ((pkt[42].toInt() and 0xFF) shl 8) or (pkt[43].toInt() and 0xFF)
                dstPort == src.port
            } else {
                false
            }

            if (matches) {
                clientIncomingQueue.offer(pkt)
            }
        }
    }

    private val readerThread = Thread({
        val buf = ByteBuffer.allocate(65535)
        while (!closed.get()) {
            val pktBytes = try {
                clientIncomingQueue.poll(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                null
            } ?: continue
            if (dropRate > 0.0 && random.nextDouble() < dropRate) {
                // dropped
                continue
            }
            buf.clear()
            buf.put(pktBytes)
            buf.flip()
            val parsed = parser.parse(buf, pktBytes.size) ?: continue
            if (parsed is ParsedPacket.Tcp && parsed.src == dst && parsed.dst == src) {
                handlePacket(parsed, pktBytes)
            }
        }
    }, "TcpClientSim-Reader")

    fun start() {
        tun.subscribe(packetSubscriber)
        readerThread.start()
    }

    fun stop() {
        closed.set(true)
        tun.unsubscribe(packetSubscriber)
        readerThread.interrupt()
        try {
            readerThread.join(1000)
        } catch (_: Exception) {}
    }

    private fun handlePacket(tcp: ParsedPacket.Tcp, raw: ByteArray) {
        if ((tcp.flags and TcpFlags.RST) != 0) {
            rstReceivedLatch.countDown()
            return
        }

        if ((tcp.flags and TcpFlags.ACK) != 0) {
            lastPeerAck = tcp.ack
        }

        if ((tcp.flags and TcpFlags.SYN) != 0 && (tcp.flags and TcpFlags.ACK) != 0) {
            ack = (tcp.seq + 1) and 0xFFFFFFFFL
            // Send ACK to complete handshake
            sendPacket(flags = TcpFlags.ACK)
            handshakedLatch.countDown()
            return
        }

        if (tcp.payloadLength > 0) {
            val payload = ByteArray(tcp.payloadLength)
            System.arraycopy(raw, tcp.payloadOffset, payload, 0, tcp.payloadLength)
            val diff = (tcp.seq - ack).toInt()
            if (diff == 0) {
                synchronized(receivedData) {
                    receivedData.write(payload)
                }
                ack = (ack + tcp.payloadLength) and 0xFFFFFFFFL
                // Drain any contiguous segments from pending
                while (pendingSegments.isNotEmpty()) {
                    val nextEntry = pendingSegments.firstEntry()
                    if (nextEntry != null) {
                        val d = (nextEntry.key - ack).toInt()
                        if (d == 0) {
                            pendingSegments.pollFirstEntry()
                            synchronized(receivedData) {
                                receivedData.write(nextEntry.value)
                            }
                            ack = (ack + nextEntry.value.size) and 0xFFFFFFFFL
                        } else if (d < 0) {
                            pendingSegments.pollFirstEntry()
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            } else if (diff > 0) {
                pendingSegments[tcp.seq] = payload
            }
            if (autoAck) {
                sendPacket(flags = TcpFlags.ACK)
            }
        }

        if ((tcp.flags and TcpFlags.FIN) != 0) {
            val diff = (tcp.seq - ack).toInt()
            if (diff <= 0) {
                ack = (ack + 1) and 0xFFFFFFFFL
                sendPacket(flags = TcpFlags.ACK)
                finReceivedLatch.countDown()
            }
        }
    }

    fun connect(timeoutSeconds: Long = 5): Boolean {
        start()
        val options = if (mss != null) PacketBuilder.buildMssOption(mss) else ByteArray(0)
        sendPacket(flags = TcpFlags.SYN, options = options)
        seq = (seq + 1) and 0xFFFFFFFFL
        return handshakedLatch.await(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun sendData(data: ByteArray) {
        val clientMss = mss ?: if (isIpv6) 1340 else 1360
        var offset = 0
        while (offset < data.size) {
            val len = minOf(clientMss, data.size - offset)
            val chunk = ByteArray(len)
            System.arraycopy(data, offset, chunk, 0, len)
            val flags = if (offset + len == data.size) (TcpFlags.ACK or TcpFlags.PSH) else TcpFlags.ACK
            sendPacket(flags = flags, payload = chunk)
            seq = (seq + len) and 0xFFFFFFFFL
            offset += len
        }
    }

    fun sendFin() {
        sendPacket(flags = TcpFlags.FIN or TcpFlags.ACK)
        seq = (seq + 1) and 0xFFFFFFFFL
    }

    fun sendRst() {
        sendPacket(flags = TcpFlags.RST)
    }

    fun updateWindow(window: Int) {
        advertisedWindow = window
        sendPacket(flags = TcpFlags.ACK)
    }

    fun sendRawPacket(flags: Int, seqNum: Long, ackNum: Long, payload: ByteArray = ByteArray(0), options: ByteArray = ByteArray(0)) {
        val bytes = if (!isIpv6) {
            PacketBuilder.ipv4Tcp(
                src = src,
                dst = dst,
                seq = seqNum,
                ack = ackNum,
                flags = flags,
                window = advertisedWindow,
                payload = payload,
                options = options
            )
        } else {
            PacketBuilder.ipv6Tcp(
                src = src,
                dst = dst,
                seq = seqNum,
                ack = ackNum,
                flags = flags,
                window = advertisedWindow,
                payload = payload,
                options = options
            )
        }
        tun.toForwarder.offer(bytes)
    }

    private fun sendPacket(flags: Int, payload: ByteArray = ByteArray(0), options: ByteArray = ByteArray(0)) {
        sendRawPacket(flags, seq, ack, payload, options)
    }
}
