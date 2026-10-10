package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.io.IOException
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.TreeMap

class TcpFlow(
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
    enum class State {
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT, // Client sent FIN
        FIN_WAIT_1, // Server closed, sent FIN to client
        FIN_WAIT_2, // Client ACKed server FIN, waiting for client FIN
        CLOSING,    // Both FINs sent, waiting for final ACKs
        TIME_WAIT,  // Both FINs ACKed
        CLOSED
    }

    var state = State.SYN_RECEIVED
    val openedAt: Instant = clock.instant()
    var lastActiveAt: Instant = openedAt
    var closedAt: Instant? = null

    // Addressing
    val isIpv6: Boolean = src.address is Inet6Address
    val localMss: Int = if (isIpv6) mtu - 60 else mtu - 40
    var peerMss: Int = localMss

    // Sequence space
    // TUN side (we send packets from dst to src):
    // mySeq: next sequence number we send to client
    // myAck: next sequence number we expect from client (acknowledging client's seq)
    var mySeq: Long = 100000L
    var myAck: Long = 0L

    // Client receive window
    var clientWindow: Int = 65535

    // Buffers:
    // 1) App to socket (Upstream / TX):
    // Client sends data -> we buffer up to 256 KiB -> write to SocketChannel
    private val upstreamQueue = ArrayDeque<ByteArray>()
    private var upstreamBufferedBytes = 0
    private val maxBufferSize = 256 * 1024

    // Out-of-order queue for segments from client
    // key = seq, value = data
    private val outOfOrderSegments = TreeMap<Long, ByteArray> { a, b ->
        val diff = (a - b).toInt()
        diff
    }

    // 2) Socket to app (Downstream / RX):
    // SocketChannel read -> chunk into MSS segments -> in-flight queue waiting for client ACK
    // Retransmission queue
    data class InFlightSegment(
        val seq: Long,
        val payload: ByteArray,
        val isFin: Boolean,
        var sendTimeMs: Long = System.currentTimeMillis(),
        var rtoMs: Long = 1000L,
        var attempts: Int = 1
    )
    private val inFlightSegments = ArrayDeque<InFlightSegment>()
    private var inFlightBytes = 0

    var channel: SocketChannel? = null
    var selectionKey: SelectionKey? = null

    private var parsedSni = false
    private val writeBuffer = ByteBuffer.allocate(mtu + 200)

    init {
        listener.onFlowOpened(FlowInfo(flowId, ownerUid, 6, src, dst, openedAt))
    }

    fun startConnect(initialMss: Int?, initialSeq: Long) {
        if (initialMss != null && initialMss > 0) {
            peerMss = minOf(peerMss, initialMss)
        }
        myAck = (initialSeq + 1) and 0xFFFFFFFFL

        try {
            val sc = SocketChannel.open()
            channel = sc
            sc.configureBlocking(false)
            protector.protect(sc)

            val connecting = sc.connect(dst)
            selectionKey = sc.register(selector, SelectionKey.OP_CONNECT, this)

            if (connecting) {
                onConnected()
            }
        } catch (e: Exception) {
            sendRstAck()
            close()
        }
    }

    fun onConnected() {
        try {
            if (channel?.finishConnect() == true) {
                state = State.ESTABLISHED
                selectionKey?.interestOps(SelectionKey.OP_READ)
                sendSynAck()
            } else {
                sendRstAck()
                close()
            }
        } catch (e: Exception) {
            sendRstAck()
            close()
        }
    }

    private fun sendSynAck() {
        val options = PacketWriter.buildMssOption(localMss)
        sendTcpPacket(flags = TcpFlags.SYN or TcpFlags.ACK, seq = mySeq, ack = myAck, options = options)
        mySeq = (mySeq + 1) and 0xFFFFFFFFL
    }

    private fun sendRstAck() {
        sendTcpPacket(flags = TcpFlags.RST or TcpFlags.ACK, seq = mySeq, ack = myAck)
    }

    fun sendRst() {
        sendTcpPacket(flags = TcpFlags.RST, seq = mySeq, ack = myAck)
    }

    fun handlePacketFromTun(tcp: ParsedPacket.Tcp, raw: ByteArray) {
        lastActiveAt = clock.instant()

        if ((tcp.flags and TcpFlags.RST) != 0) {
            close()
            return
        }

        // Update client window
        clientWindow = tcp.window

        // Handle ACK
        if ((tcp.flags and TcpFlags.ACK) != 0) {
            handleAckFromClient(tcp.ack)
        }

        // Process client payload
        if (tcp.payloadLength > 0) {
            val payload = ByteArray(tcp.payloadLength)
            System.arraycopy(raw, tcp.payloadOffset, payload, 0, tcp.payloadLength)

            // Check SNI on first payload if not yet parsed
            if (!parsedSni) {
                parsedSni = true
                val sni = SniParser.serverName(payload)
                if (sni != null) {
                    listener.onName(flowId, sni)
                }
            }

            processClientData(tcp.seq, payload)
        }

        // Handle FIN from client
        if ((tcp.flags and TcpFlags.FIN) != 0) {
            handleFinFromClient(tcp.seq, tcp.payloadLength)
        }

        // Try pumping downstream data if window opened
        pumpSocketToTun()
    }

    private fun handleAckFromClient(ackNum: Long) {
        // Remove acknowledged segments from inFlightSegments
        while (inFlightSegments.isNotEmpty()) {
            val seg = inFlightSegments.first()
            val segEnd = (seg.seq + seg.payload.size + (if (seg.isFin) 1 else 0)) and 0xFFFFFFFFL
            val diff = (ackNum - segEnd).toInt()
            if (diff >= 0) {
                inFlightSegments.removeFirst()
                inFlightBytes -= seg.payload.size
            } else {
                break
            }
        }

        if (state == State.FIN_WAIT_1 && inFlightSegments.none { it.isFin }) {
            state = State.FIN_WAIT_2
        } else if (state == State.CLOSING && inFlightSegments.none { it.isFin }) {
            enterTimeWait()
        }
    }

    private fun processClientData(seq: Long, data: ByteArray) {
        val diff = (seq - myAck).toInt()
        if (diff == 0) {
            // In order
            if (acceptClientPayload(data)) {
                myAck = (myAck + data.size) and 0xFFFFFFFFL

                // Drain out-of-order queue if contiguous
                while (outOfOrderSegments.isNotEmpty()) {
                    val nextEntry = outOfOrderSegments.firstEntry() ?: break
                    val nextDiff = (nextEntry.key - myAck).toInt()
                    if (nextDiff == 0) {
                        if (acceptClientPayload(nextEntry.value)) {
                            outOfOrderSegments.pollFirstEntry()
                            myAck = (myAck + nextEntry.value.size) and 0xFFFFFFFFL
                        } else {
                            break
                        }
                    } else if (nextDiff < 0) {
                        outOfOrderSegments.pollFirstEntry()
                    } else {
                        break
                    }
                }
                // Send ACK back to client
                sendAck()
            }
            // If buffer full, do not advance myAck and do not ACK, app TCP will pause
        } else if (diff < 0) {
            // Duplicate segment: re-acknowledge and ignore
            sendAck()
        } else {
            // Out of order: buffer if not too many
            if (outOfOrderSegments.size < 64) {
                outOfOrderSegments[seq] = data
            }
            // Re-ACK current myAck
            sendAck()
        }
    }

    private fun acceptClientPayload(data: ByteArray): Boolean {
        flushUpstreamToSocket()
        if (upstreamBufferedBytes + data.size <= maxBufferSize) {
            upstreamQueue.addLast(data)
            upstreamBufferedBytes += data.size
            listener.onBytes(flowId, rxBytes = 0, txBytes = data.size.toLong())
            flushUpstreamToSocket()
            return true
        }
        return false
    }

    fun flushUpstreamToSocket() {
        val ch = channel ?: return
        try {
            while (upstreamQueue.isNotEmpty()) {
                val chunk = upstreamQueue.first()
                val buf = ByteBuffer.wrap(chunk)
                val written = ch.write(buf)
                if (written > 0) {
                    upstreamBufferedBytes -= written
                    if (buf.remaining() == 0) {
                        upstreamQueue.removeFirst()
                    } else {
                        val remaining = ByteArray(buf.remaining())
                        buf.get(remaining)
                        upstreamQueue.removeFirst()
                        upstreamQueue.addFirst(remaining)
                        break
                    }
                } else {
                    break
                }
            }

            if (upstreamQueue.isNotEmpty()) {
                selectionKey?.interestOps((selectionKey?.interestOps() ?: 0) or SelectionKey.OP_WRITE)
            } else {
                selectionKey?.interestOps((selectionKey?.interestOps() ?: 0) and SelectionKey.OP_WRITE.inv())
                if (state == State.CLOSE_WAIT) {
                    // Client had sent FIN and we finished writing client data to socket
                    try {
                        ch.shutdownOutput()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            sendRst()
            close()
        }
    }

    private fun handleFinFromClient(seq: Long, payloadLen: Int) {
        myAck = (seq + payloadLen + 1) and 0xFFFFFFFFL
        sendAck()

        when (state) {
            State.ESTABLISHED -> {
                state = State.CLOSE_WAIT
                if (upstreamQueue.isEmpty()) {
                    try {
                        channel?.shutdownOutput()
                    } catch (_: Exception) {}
                }
            }
            State.FIN_WAIT_2 -> {
                enterTimeWait()
            }
            State.FIN_WAIT_1 -> {
                state = State.CLOSING
            }
            else -> {}
        }
    }

    fun onSocketReadable() {
        lastActiveAt = clock.instant()
        val ch = channel ?: return
        val readBuf = ByteBuffer.allocate(32768)
        val readBytes: Int
        try {
            readBytes = ch.read(readBuf)
        } catch (e: Exception) {
            sendRst()
            close()
            return
        }

        if (readBytes == -1) {
            // EOF from server -> send FIN to client
            onServerEof()
            return
        }

        if (readBytes > 0) {
            readBuf.flip()
            val data = ByteArray(readBytes)
            readBuf.get(data)
            listener.onBytes(flowId, rxBytes = readBytes.toLong(), txBytes = 0)
            queueDownstreamData(data)
        }
    }

    private fun queueDownstreamData(data: ByteArray) {
        var offset = 0
        val mss = minOf(localMss, peerMss)
        while (offset < data.size) {
            val len = minOf(mss, data.size - offset)
            val chunk = ByteArray(len)
            System.arraycopy(data, offset, chunk, 0, len)
            val seg = InFlightSegment(
                seq = mySeq,
                payload = chunk,
                isFin = false
            )
            inFlightSegments.addLast(seg)
            inFlightBytes += len
            mySeq = (mySeq + len) and 0xFFFFFFFFL
            offset += len

            // Send segment immediately if window permits
            sendSegmentToTun(seg)
        }
    }

    private fun onServerEof() {
        val seg = InFlightSegment(
            seq = mySeq,
            payload = ByteArray(0),
            isFin = true
        )
        inFlightSegments.addLast(seg)
        mySeq = (mySeq + 1) and 0xFFFFFFFFL

        sendSegmentToTun(seg)

        when (state) {
            State.ESTABLISHED -> state = State.FIN_WAIT_1
            State.CLOSE_WAIT -> enterTimeWait()
            else -> {}
        }
    }

    private fun enterTimeWait() {
        state = State.TIME_WAIT
        closedAt = clock.instant()
    }

    private fun sendSegmentToTun(seg: InFlightSegment) {
        val flags = if (seg.isFin) (TcpFlags.FIN or TcpFlags.ACK) else (TcpFlags.ACK or TcpFlags.PSH)
        sendTcpPacket(flags = flags, seq = seg.seq, ack = myAck, payload = seg.payload)
    }

    fun pumpSocketToTun() {
        // Can pause or unpause selector reading if inFlightBytes is too high
        val ch = channel ?: return
        if (inFlightBytes >= maxBufferSize || clientWindow == 0) {
            selectionKey?.interestOps((selectionKey?.interestOps() ?: 0) and SelectionKey.OP_READ.inv())
        } else {
            selectionKey?.interestOps((selectionKey?.interestOps() ?: 0) or SelectionKey.OP_READ)
        }
    }

    fun checkRetransmissions() {
        val nowMs = System.currentTimeMillis()
        for (seg in inFlightSegments) {
            val elapsed = nowMs - seg.sendTimeMs
            if (elapsed >= seg.rtoMs) {
                if (seg.attempts >= 5) {
                    sendRst()
                    close()
                    return
                }
                seg.attempts++
                seg.rtoMs *= 2
                seg.sendTimeMs = nowMs
                sendSegmentToTun(seg)
            }
        }
    }

    private fun sendAck() {
        sendTcpPacket(flags = TcpFlags.ACK, seq = mySeq, ack = myAck)
    }

    private fun sendTcpPacket(
        flags: Int,
        seq: Long,
        ack: Long,
        payload: ByteArray = ByteArray(0),
        options: ByteArray = ByteArray(0)
    ) {
        synchronized(tunWriteLock) {
            writeBuffer.clear()
            val totalLen = PacketWriter.writeTcp(
                buffer = writeBuffer,
                src = dst, // swap: from remote dst to local src
                dst = src,
                seq = seq,
                ack = ack,
                flags = flags,
                window = 65535,
                payload = payload,
                options = options
            )
            writeBuffer.flip()
            tun.write(writeBuffer, totalLen)
        }
    }

    fun close() {
        state = State.CLOSED
        try {
            selectionKey?.cancel()
            channel?.close()
        } catch (_: Exception) {}
        listener.onFlowClosed(flowId)
    }

    fun isDead(now: Instant): Boolean {
        if (state == State.CLOSED) return true
        if (state == State.TIME_WAIT) {
            val ca = closedAt ?: return true
            if (Duration.between(ca, now).seconds >= 30) {
                return true
            }
        }
        // Idle timeout 5 minutes
        if (Duration.between(lastActiveAt, now).seconds >= 300) {
            return true
        }
        return false
    }
}
