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
import java.util.concurrent.atomic.AtomicLong

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
    val tunWriteLock: Any,
    val droppedSegmentsCounter: AtomicLong? = null
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

    // Client receive window (advertised by client / app)
    var clientWindow: Int = 65535
    var sndWnd: Int = 65535

    // Buffers:
    // 1) App to socket (Upstream / TX):
    // Client sends data -> buffer up to 256 KiB -> write to SocketChannel
    private val upstreamQueue = ArrayDeque<ByteArray>()
    private var upstreamBufferedBytes = 0
    private val maxBufferSize = 256 * 1024

    // Out-of-order queue for segments from client
    // key = seq, value = data
    private val outOfOrderSegments = TreeMap<Long, ByteArray> { a, b ->
        (a - b).toInt()
    }
    // Out-of-order FIN sequence number if received ahead of time
    private var pendingClientFinSeq: Long? = null

    // Last advertised rcvWnd to client
    private var lastAdvertisedRcvWnd: Int = 65535

    // 2) Socket to app (Downstream / RX):
    // Downstream buffer holding data read from server socket not yet sent to TUN
    private val downstreamQueue = ArrayDeque<ByteArray>()
    private var downstreamQueueOffset = 0 // offset within first chunk of downstreamQueue
    private var downstreamBufferedBytes = 0

    // Retransmission queue (in flight packets sent to TUN, unacknowledged by client)
    data class InFlightSegment(
        val seq: Long,
        val payload: ByteArray,
        val isFin: Boolean,
        var sendTimeMs: Long = System.currentTimeMillis(),
        var rtoMs: Long = 200L,
        var attempts: Int = 1
    )
    private val inFlightSegments = ArrayDeque<InFlightSegment>()
    private var inFlightBytes = 0

    // Zero-window probe tracking
    private var zeroWindowProbeSentTimeMs: Long = 0L
    private var zeroWindowRtoMs: Long = 1000L

    var channel: SocketChannel? = null
    var selectionKey: SelectionKey? = null

    private var parsedSni = false
    private val writeBuffer = ByteBuffer.allocate(mtu + 200)

    private var serverEofProcessed = false

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
                updateSelectionInterest()
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
        sndWnd = tcp.window

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

        // Send any pending downstream data or update interest ops
        sendPendingDownstreamData()
        updateSelectionInterest()
    }

    private var lastAckNum: Long = -1L
    private var dupAckCount: Int = 0

    private fun handleAckFromClient(ackNum: Long) {
        if (ackNum == lastAckNum && inFlightSegments.isNotEmpty()) {
            dupAckCount++
            if (dupAckCount == 3) {
                // Fast retransmit the head unacknowledged segment immediately
                val headSeg = inFlightSegments.first()
                sendSegmentToTun(headSeg)
                headSeg.sendTimeMs = System.currentTimeMillis()
            }
        } else if (lastAckNum == -1L || (ackNum - lastAckNum).toInt() > 0) {
            lastAckNum = ackNum
            dupAckCount = 0
        }

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

        // Space in inFlightBytes might have opened up, try sending queued downstream data
        sendPendingDownstreamData()
    }

    private fun processClientData(seq: Long, data: ByteArray) {
        val diff = (seq - myAck).toInt()
        if (diff == 0) {
            // In order
            acceptClientPayload(data)
            myAck = (myAck + data.size) and 0xFFFFFFFFL

            // Drain out-of-order queue if contiguous
            while (outOfOrderSegments.isNotEmpty()) {
                val nextEntry = outOfOrderSegments.firstEntry() ?: break
                val nextDiff = (nextEntry.key - myAck).toInt()
                if (nextDiff == 0) {
                    acceptClientPayload(nextEntry.value)
                    outOfOrderSegments.pollFirstEntry()
                    myAck = (myAck + nextEntry.value.size) and 0xFFFFFFFFL
                } else if (nextDiff < 0) {
                    outOfOrderSegments.pollFirstEntry()
                } else {
                    break
                }
            }

            // Check if pending client FIN can now be accepted
            checkPendingClientFin()

            // Send ACK back to client
            sendAck()
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

    private fun acceptClientPayload(data: ByteArray) {
        upstreamQueue.addLast(data)
        upstreamBufferedBytes += data.size
        listener.onBytes(flowId, rxBytes = 0, txBytes = data.size.toLong())
        flushUpstreamToSocket()
    }

    private fun calculateRcvWnd(): Int {
        val freeSpace = maxBufferSize - upstreamBufferedBytes
        return minOf(65535, maxOf(0, freeSpace))
    }

    private fun checkWindowUpdateAfterDrain(oldRcvWnd: Int) {
        val newRcvWnd = calculateRcvWnd()
        val mss = minOf(localMss, peerMss)
        if ((oldRcvWnd < mss && newRcvWnd >= mss) || (oldRcvWnd > 0 && newRcvWnd >= oldRcvWnd * 2)) {
            sendAck()
        }
    }

    fun flushUpstreamToSocket() {
        val ch = channel ?: return
        val oldRcvWnd = calculateRcvWnd()
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

            checkWindowUpdateAfterDrain(oldRcvWnd)

            if (upstreamQueue.isEmpty()) {
                checkPendingClientFin()
                if (state == State.CLOSE_WAIT) {
                    try {
                        ch.shutdownOutput()
                    } catch (_: Exception) {}
                }
            }
            updateSelectionInterest()
        } catch (e: Exception) {
            sendRst()
            close()
        }
    }

    private fun handleFinFromClient(seq: Long, payloadLen: Int) {
        val finSeq = (seq + payloadLen) and 0xFFFFFFFFL
        val diff = (finSeq - myAck).toInt()
        if (diff == 0) {
            if (upstreamQueue.isEmpty()) {
                applyClientFin()
                sendAck()
            } else {
                pendingClientFinSeq = finSeq
                // Do not ACK FIN yet: unrelayed data is still in upstreamQueue!
                sendAck()
            }
        } else if (diff > 0) {
            // Out-of-order FIN: missing data before it. Hold it until gap filled.
            pendingClientFinSeq = finSeq
            sendAck()
        } else {
            // Duplicate FIN: re-ACK
            sendAck()
        }
    }

    private fun checkPendingClientFin() {
        val finSeq = pendingClientFinSeq ?: return
        val diff = (finSeq - myAck).toInt()
        if (diff == 0 && upstreamQueue.isEmpty()) {
            pendingClientFinSeq = null
            applyClientFin()
            sendAck()
        }
    }

    private fun applyClientFin() {
        myAck = (myAck + 1) and 0xFFFFFFFFL
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
        updateSelectionInterest()
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
            // EOF from server -> clear OP_READ, send FIN to client
            onServerEof()
            return
        }

        if (readBytes > 0) {
            readBuf.flip()
            val data = ByteArray(readBytes)
            readBuf.get(data)
            listener.onBytes(flowId, rxBytes = readBytes.toLong(), txBytes = 0)
            downstreamQueue.addLast(data)
            downstreamBufferedBytes += readBytes
            sendPendingDownstreamData()
            updateSelectionInterest()
        }
    }

    private fun sendPendingDownstreamData() {
        val effectiveWindow = minOf(sndWnd, 256 * 1024)
        val mss = minOf(localMss, peerMss)

        while (downstreamQueue.isNotEmpty()) {
            val allowedInFlight = effectiveWindow - inFlightBytes
            if (allowedInFlight <= 0) break

            val firstChunk = downstreamQueue.first()
            val chunkRemaining = firstChunk.size - downstreamQueueOffset
            val toSend = minOf(mss, minOf(allowedInFlight, chunkRemaining))
            if (toSend <= 0) break

            val payload = ByteArray(toSend)
            System.arraycopy(firstChunk, downstreamQueueOffset, payload, 0, toSend)
            downstreamQueueOffset += toSend
            downstreamBufferedBytes -= toSend

            if (downstreamQueueOffset >= firstChunk.size) {
                downstreamQueue.removeFirst()
                downstreamQueueOffset = 0
            }

            val seg = InFlightSegment(
                seq = mySeq,
                payload = payload,
                isFin = false
            )
            inFlightSegments.addLast(seg)
            inFlightBytes += toSend
            mySeq = (mySeq + toSend) and 0xFFFFFFFFL

            sendSegmentToTun(seg)
        }

        // If downstream buffer has emptied and server gave EOF, emit FIN if not yet emitted
        if (serverEofProcessed && downstreamQueue.isEmpty() && state == State.ESTABLISHED) {
            emitServerFin()
        }
    }

    private fun onServerEof() {
        if (serverEofProcessed) return
        serverEofProcessed = true
        updateSelectionInterest()

        if (downstreamQueue.isEmpty()) {
            emitServerFin()
        }
    }

    private fun emitServerFin() {
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
        updateSelectionInterest()
    }

    private fun enterTimeWait() {
        state = State.TIME_WAIT
        closedAt = clock.instant()
        updateSelectionInterest()
    }

    private fun sendSegmentToTun(seg: InFlightSegment) {
        val flags = if (seg.isFin) (TcpFlags.FIN or TcpFlags.ACK) else (TcpFlags.ACK or TcpFlags.PSH)
        sendTcpPacket(flags = flags, seq = seg.seq, ack = myAck, payload = seg.payload)
    }

    fun pumpSocketToTun() {
        sendPendingDownstreamData()
        updateSelectionInterest()
    }

    fun updateSelectionInterest() {
        val key = selectionKey ?: return
        if (!key.isValid) return

        var ops = 0
        if (upstreamQueue.isNotEmpty()) {
            ops = ops or SelectionKey.OP_WRITE
        }

        val effectiveWindow = minOf(sndWnd, 256 * 1024)
        val canReadDownlink = !serverEofProcessed &&
                state != State.CLOSED &&
                state != State.TIME_WAIT &&
                state != State.FIN_WAIT_1 &&
                state != State.FIN_WAIT_2 &&
                state != State.CLOSING &&
                (downstreamBufferedBytes + inFlightBytes < 256 * 1024) &&
                (effectiveWindow > inFlightBytes || (sndWnd == 0 && downstreamBufferedBytes < 64 * 1024))

        if (canReadDownlink) {
            ops = ops or SelectionKey.OP_READ
        }

        try {
            key.interestOps(ops)
        } catch (_: Exception) {}
    }

    fun checkRetransmissions() {
        val nowMs = System.currentTimeMillis()

        // 1. Regular retransmissions of unacknowledged in-flight segments
        for (seg in inFlightSegments) {
            val elapsed = nowMs - seg.sendTimeMs
            if (elapsed >= seg.rtoMs) {
                if (seg.attempts >= 10) {
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

        // 2. Zero-window probing:
        // When client advertised window is 0 and we have buffered downstream data waiting to be sent
        if (sndWnd == 0 && downstreamQueue.isNotEmpty()) {
            if (zeroWindowProbeSentTimeMs == 0L) {
                zeroWindowProbeSentTimeMs = nowMs
            }
            val elapsed = nowMs - zeroWindowProbeSentTimeMs
            if (elapsed >= zeroWindowRtoMs) {
                // Send 1-byte probe from downstream buffer without removing from queue
                val firstChunk = downstreamQueue.first()
                val probeByte = byteArrayOf(firstChunk[downstreamQueueOffset])
                // Sequence number is mySeq
                sendTcpPacket(
                    flags = TcpFlags.ACK or TcpFlags.PSH,
                    seq = mySeq,
                    ack = myAck,
                    payload = probeByte
                )
                zeroWindowProbeSentTimeMs = nowMs
                zeroWindowRtoMs = minOf(30000L, zeroWindowRtoMs * 2)
            }
        } else {
            zeroWindowProbeSentTimeMs = 0L
            zeroWindowRtoMs = 1000L
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
        val rcvWnd = calculateRcvWnd()
        lastAdvertisedRcvWnd = rcvWnd

        synchronized(tunWriteLock) {
            writeBuffer.clear()
            val totalLen = PacketWriter.writeTcp(
                buffer = writeBuffer,
                src = dst, // swap: from remote dst to local src
                dst = src,
                seq = seq,
                ack = ack,
                flags = flags,
                window = rcvWnd,
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
