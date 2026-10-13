package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.io.ByteArrayOutputStream
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
import java.util.concurrent.atomic.AtomicBoolean
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
    val droppedSegmentsCounter: AtomicLong? = null,
    val channelFactory: SocketChannelFactory = SocketChannelFactory.SYSTEM,
    val protectFailuresCounter: AtomicLong? = null,
    val directBufferPool: DirectBufferPool? = null
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
    val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    // Addressing
    val isIpv6: Boolean = src.address is Inet6Address
    val localMss: Int = if (isIpv6) mtu - 60 else mtu - 40
    var peerMss: Int = localMss
    val srcIpBytes: ByteArray = (src.address as? java.net.Inet4Address)?.address ?: src.address.address
    val dstIpBytes: ByteArray = (dst.address as? java.net.Inet4Address)?.address ?: dst.address.address

    // Sequence space
    var mySeq: Long = 100000L
    var myAck: Long = 0L

    // Client receive window
    var clientWindow: Int = 65535
    var sndWnd: Int = 65535

    // Buffers:
    // 1) Upstream (App to socket)
    private val upstreamQueue = ArrayDeque<ByteArray>()
    private var upstreamBufferedBytes = 0
    private val maxBufferSize = 256 * 1024

    // Out-of-order queue for segments from client
    private val outOfOrderSegments = TreeMap<Long, ByteArray> { a, b ->
        (a - b).toInt()
    }
    private var pendingClientFinSeq: Long? = null

    // SNI reassembly buffer (buffers up to 16 KiB, 3 segments, or 1s simulated time)
    private var sniBuffer: ByteArrayOutputStream? = ByteArrayOutputStream()
    private var sniSegmentCount = 0
    private var sniFinished = false
    private val sniReported = AtomicBoolean(false)

    // Last advertised rcvWnd to client
    private var lastAdvertisedRcvWnd: Int = 65535

    // 2) Downstream (Socket to app)
    data class DownstreamChunk(
        val directBuf: ByteBuffer? = null,
        val byteArray: ByteArray? = null,
        val payloadLen: Int
    )
    private val downstreamQueue = ArrayDeque<DownstreamChunk>()
    private var downstreamBufferedBytes = 0

    // Retransmission queue
    data class InFlightSegment(
        val seq: Long,
        val payload: ByteArray? = null,
        val directBuf: ByteBuffer? = null,
        val totalLen: Int = 0,
        val payloadLen: Int = payload?.size ?: 0,
        val isFin: Boolean = false,
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

    private val writeBuffer = ByteBuffer.allocate(mtu + 200)
    private val socketReadBuffer = ByteBuffer.allocateDirect(65536)
    private val socketWriteBuffer = ByteBuffer.allocateDirect(65536)

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
            val sc = channelFactory.open()
            channel = sc
            sc.configureBlocking(false)

            val protected = try {
                protector.protect(sc)
            } catch (e: Exception) {
                false
            }

            if (!protected) {
                protectFailuresCounter?.incrementAndGet()
                sendRstAck()
                try { sc.close() } catch (_: Exception) {}
                close()
                return
            }

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
            processClientPayload(tcp.seq, raw, tcp.payloadOffset, tcp.payloadLength)
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
                val headSeg = inFlightSegments.first()
                sendSegmentToTun(headSeg)
                headSeg.sendTimeMs = System.currentTimeMillis()
            }
        } else if (lastAckNum == -1L || (ackNum - lastAckNum).toInt() > 0) {
            lastAckNum = ackNum
            dupAckCount = 0
        }

        while (inFlightSegments.isNotEmpty()) {
            val seg = inFlightSegments.first()
            val segEnd = (seg.seq + seg.payloadLen + (if (seg.isFin) 1 else 0)) and 0xFFFFFFFFL
            val diff = (ackNum - segEnd).toInt()
            if (diff >= 0) {
                inFlightSegments.removeFirst()
                inFlightBytes -= seg.payloadLen
                if (seg.directBuf != null && directBufferPool != null) {
                    directBufferPool.release(seg.directBuf)
                }
            } else {
                break
            }
        }

        if (state == State.FIN_WAIT_1 && inFlightSegments.none { it.isFin }) {
            state = State.FIN_WAIT_2
        } else if (state == State.CLOSING && inFlightSegments.none { it.isFin }) {
            enterTimeWait()
        }

        sendPendingDownstreamData()
    }

    private fun processClientPayload(seq: Long, raw: ByteArray, offset: Int, length: Int) {
        val diff = (seq - myAck).toInt()
        val ch = channel
        if (diff == 0 && outOfOrderSegments.isEmpty() && upstreamQueue.isEmpty() && sniFinished && ch != null) {
            socketWriteBuffer.clear()
            val toWrite = minOf(length, socketWriteBuffer.remaining())
            socketWriteBuffer.put(raw, offset, toWrite)
            socketWriteBuffer.flip()

            val written = try {
                ch.write(socketWriteBuffer)
            } catch (e: Exception) {
                sendRst()
                close()
                return
            }

            if (written > 0) {
                listener.onBytes(flowId, rxBytes = 0, txBytes = written.toLong())
                myAck = (myAck + written) and 0xFFFFFFFFL

                if (written < length) {
                    val remaining = ByteArray(length - written)
                    System.arraycopy(raw, offset + written, remaining, 0, remaining.size)
                    upstreamQueue.addLast(remaining)
                    upstreamBufferedBytes += remaining.size
                }
                checkPendingClientFin()
                sendAck()
                return
            }
        }

        val payload = ByteArray(length)
        System.arraycopy(raw, offset, payload, 0, length)
        processClientData(seq, payload)
    }

    private fun processClientData(seq: Long, data: ByteArray) {
        val diff = (seq - myAck).toInt()
        if (diff == 0) {
            acceptClientPayload(data)
            myAck = (myAck + data.size) and 0xFFFFFFFFL

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

            checkPendingClientFin()
            sendAck()
        } else if (diff < 0) {
            sendAck()
        } else {
            if (outOfOrderSegments.size < 64) {
                outOfOrderSegments[seq] = data
            }
            sendAck()
        }
    }

    private fun checkSni(data: ByteArray) {
        if (sniFinished) return
        if (Duration.between(openedAt, clock.instant()).toMillis() >= 1000) {
            finishSni()
            return
        }

        val buf = sniBuffer ?: return
        buf.write(data)
        sniSegmentCount++

        val raw = buf.toByteArray()
        // Non-TLS check: first byte must be 0x16 (Handshake)
        if (raw.isNotEmpty() && raw[0] != 0x16.toByte()) {
            finishSni()
            return
        }

        if (raw.size >= 5) {
            val recordLen = ((raw[3].toInt() and 0xFF) shl 8) or (raw[4].toInt() and 0xFF)
            val totalRecordSize = 5 + recordLen
            if (totalRecordSize > 16384) {
                finishSni()
                return
            }
            if (raw.size >= totalRecordSize) {
                val completeRecord = if (raw.size == totalRecordSize) raw else raw.copyOf(totalRecordSize)
                val name = SniParser.serverName(completeRecord)
                if (name != null && sniReported.compareAndSet(false, true)) {
                    listener.onName(flowId, name)
                }
                finishSni()
                return
            }
        }

        if (buf.size() >= 16384 || sniSegmentCount >= 3) {
            finishSni()
        }
    }

    private fun finishSni() {
        sniFinished = true
        sniBuffer = null
    }

    private fun acceptClientPayload(data: ByteArray) {
        checkSni(data)
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
                socketWriteBuffer.clear()
                val toWrite = minOf(chunk.size, socketWriteBuffer.remaining())
                socketWriteBuffer.put(chunk, 0, toWrite)
                socketWriteBuffer.flip()

                val written = ch.write(socketWriteBuffer)
                if (written > 0) {
                    upstreamBufferedBytes -= written
                    if (written == chunk.size) {
                        upstreamQueue.removeFirst()
                    } else {
                        val remaining = ByteArray(chunk.size - written)
                        System.arraycopy(chunk, written, remaining, 0, remaining.size)
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
                sendAck()
            }
        } else if (diff > 0) {
            pendingClientFinSeq = finSeq
            sendAck()
        } else {
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
        socketReadBuffer.clear()
        val readBytes: Int
        try {
            readBytes = ch.read(socketReadBuffer)
        } catch (e: Exception) {
            sendRst()
            close()
            return
        }

        if (readBytes == -1) {
            onServerEof()
            return
        }

        if (readBytes > 0) {
            socketReadBuffer.flip()
            listener.onBytes(flowId, rxBytes = readBytes.toLong(), txBytes = 0)

            val effectiveWindow = minOf(sndWnd, 256 * 1024)
            val mss = minOf(localMss, peerMss)

            while (socketReadBuffer.hasRemaining()) {
                val allowedInFlight = effectiveWindow - inFlightBytes
                val toSend = minOf(mss, socketReadBuffer.remaining())
                val pktBuf = if (directBufferPool != null) directBufferPool.acquire() else null

                if (pktBuf != null) {
                    val headerLen = if (isIpv6) 60 else 40
                    pktBuf.position(headerLen)
                    val oldLimit = socketReadBuffer.limit()
                    socketReadBuffer.limit(socketReadBuffer.position() + toSend)
                    pktBuf.put(socketReadBuffer)
                    socketReadBuffer.limit(oldLimit)

                    if (allowedInFlight >= toSend && downstreamQueue.isEmpty()) {
                        pktBuf.position(0)
                        val totalLen = if (isIpv6) {
                            PacketWriter.writeIpv6Tcp(pktBuf, dst, src, seq = mySeq, ack = myAck, flags = TcpFlags.ACK or TcpFlags.PSH, window = calculateRcvWnd(), payloadLength = toSend)
                        } else {
                            PacketWriter.writeIpv4Tcp(pktBuf, dst, src, seq = mySeq, ack = myAck, flags = TcpFlags.ACK or TcpFlags.PSH, window = calculateRcvWnd(), payloadLength = toSend, srcIpBytes = dstIpBytes, dstIpBytes = srcIpBytes)
                        }
                        pktBuf.position(0)
                        synchronized(tunWriteLock) {
                            tun.write(pktBuf, totalLen)
                        }

                        val seg = InFlightSegment(
                            seq = mySeq,
                            directBuf = pktBuf,
                            totalLen = totalLen,
                            payloadLen = toSend,
                            isFin = false
                        )
                        inFlightSegments.addLast(seg)
                        inFlightBytes += toSend
                        mySeq = (mySeq + toSend) and 0xFFFFFFFFL
                    } else {
                        downstreamQueue.addLast(DownstreamChunk(directBuf = pktBuf, payloadLen = toSend))
                        downstreamBufferedBytes += toSend
                    }
                } else {
                    val payload = ByteArray(toSend)
                    socketReadBuffer.get(payload)
                    if (allowedInFlight >= toSend && downstreamQueue.isEmpty()) {
                        val seg = InFlightSegment(
                            seq = mySeq,
                            payload = payload,
                            payloadLen = toSend,
                            isFin = false
                        )
                        inFlightSegments.addLast(seg)
                        inFlightBytes += toSend
                        mySeq = (mySeq + toSend) and 0xFFFFFFFFL
                        sendSegmentToTun(seg)
                    } else {
                        downstreamQueue.addLast(DownstreamChunk(byteArray = payload, payloadLen = toSend))
                        downstreamBufferedBytes += toSend
                    }
                }
            }

            sendPendingDownstreamData()
            updateSelectionInterest()
        }
    }

    private fun sendPendingDownstreamData() {
        val effectiveWindow = minOf(sndWnd, 256 * 1024)

        while (downstreamQueue.isNotEmpty()) {
            val allowedInFlight = effectiveWindow - inFlightBytes
            if (allowedInFlight <= 0) break

            val firstChunk = downstreamQueue.first()
            if (allowedInFlight < firstChunk.payloadLen) break

            downstreamQueue.removeFirst()
            downstreamBufferedBytes -= firstChunk.payloadLen

            val toSend = firstChunk.payloadLen
            if (firstChunk.directBuf != null) {
                val pktBuf = firstChunk.directBuf
                pktBuf.position(0)
                val totalLen = if (isIpv6) {
                    PacketWriter.writeIpv6Tcp(pktBuf, dst, src, seq = mySeq, ack = myAck, flags = TcpFlags.ACK or TcpFlags.PSH, window = calculateRcvWnd(), payloadLength = toSend)
                } else {
                    PacketWriter.writeIpv4Tcp(pktBuf, dst, src, seq = mySeq, ack = myAck, flags = TcpFlags.ACK or TcpFlags.PSH, window = calculateRcvWnd(), payloadLength = toSend, srcIpBytes = dstIpBytes, dstIpBytes = srcIpBytes)
                }
                pktBuf.position(0)
                synchronized(tunWriteLock) {
                    tun.write(pktBuf, totalLen)
                }

                val seg = InFlightSegment(
                    seq = mySeq,
                    directBuf = pktBuf,
                    totalLen = totalLen,
                    payloadLen = toSend,
                    isFin = false
                )
                inFlightSegments.addLast(seg)
                inFlightBytes += toSend
                mySeq = (mySeq + toSend) and 0xFFFFFFFFL
            } else {
                val payload = firstChunk.byteArray ?: ByteArray(0)
                val seg = InFlightSegment(
                    seq = mySeq,
                    payload = payload,
                    payloadLen = toSend,
                    isFin = false
                )
                inFlightSegments.addLast(seg)
                inFlightBytes += toSend
                mySeq = (mySeq + toSend) and 0xFFFFFFFFL
                sendSegmentToTun(seg)
            }
        }

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
            payloadLen = 0,
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
        if (seg.directBuf != null) {
            synchronized(tunWriteLock) {
                seg.directBuf.position(0)
                tun.write(seg.directBuf, seg.totalLen)
            }
            return
        }
        val flags = if (seg.isFin) (TcpFlags.FIN or TcpFlags.ACK) else (TcpFlags.ACK or TcpFlags.PSH)
        sendTcpPacket(flags = flags, seq = seg.seq, ack = myAck, payload = seg.payload ?: ByteArray(0))
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

        if (sndWnd == 0 && downstreamQueue.isNotEmpty()) {
            if (zeroWindowProbeSentTimeMs == 0L) {
                zeroWindowProbeSentTimeMs = nowMs
            }
            val elapsed = nowMs - zeroWindowProbeSentTimeMs
            if (elapsed >= zeroWindowRtoMs) {
                val firstChunk = downstreamQueue.first()
                val probeByte = if (firstChunk.directBuf != null) {
                    val headerLen = if (isIpv6) 60 else 40
                    byteArrayOf(firstChunk.directBuf.get(headerLen))
                } else {
                    byteArrayOf(firstChunk.byteArray!![0])
                }
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

    private val emptyBytes = ByteArray(0)
    private fun sendTcpPacket(
        flags: Int,
        seq: Long,
        ack: Long,
        payload: ByteArray = emptyBytes,
        options: ByteArray = emptyBytes
    ) {
        val rcvWnd = calculateRcvWnd()
        lastAdvertisedRcvWnd = rcvWnd

        synchronized(tunWriteLock) {
            writeBuffer.clear()
            val totalLen = PacketWriter.writeTcp(
                buffer = writeBuffer,
                src = dst,
                dst = src,
                seq = seq,
                ack = ack,
                flags = flags,
                window = rcvWnd,
                payload = payload,
                options = options,
                srcIpBytes = dstIpBytes,
                dstIpBytes = srcIpBytes
            )
            writeBuffer.flip()
            tun.write(writeBuffer, totalLen)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        state = State.CLOSED
        try {
            selectionKey?.cancel()
            channel?.close()
        } catch (_: Exception) {}

        // Release any pooled buffers held in inFlightSegments and downstreamQueue
        if (directBufferPool != null) {
            for (seg in inFlightSegments) {
                if (seg.directBuf != null) {
                    directBufferPool.release(seg.directBuf)
                }
            }
            for (chunk in downstreamQueue) {
                if (chunk.directBuf != null) {
                    directBufferPool.release(chunk.directBuf)
                }
            }
        }
        inFlightSegments.clear()
        downstreamQueue.clear()

        listener.onFlowClosed(flowId)
    }

    fun isDead(now: Instant): Boolean {
        if (isClosed) return true
        if (state == State.TIME_WAIT) {
            val ca = closedAt ?: return true
            if (Duration.between(ca, now).seconds >= 30) {
                return true
            }
        }
        if (Duration.between(lastActiveAt, now).seconds >= 300) {
            return true
        }
        return false
    }
}
