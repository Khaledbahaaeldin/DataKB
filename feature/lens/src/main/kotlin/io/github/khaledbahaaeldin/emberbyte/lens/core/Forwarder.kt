package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Forwarder(
    val tun: TunDevice,
    val protector: SocketProtector,
    val owner: OwnerResolver,
    val listener: FlowListener,
    val clock: Clock = Clock.systemUTC(),
    val maxFlows: Int = 1024,
    val mtu: Int = 1400
) {
    val evictedFlows = AtomicLong(0)
    val errors = AtomicLong(0)

    private val running = AtomicBoolean(false)
    private val tunWriteLock = Any()
    private val selector = Selector.open()
    private val flowTable = FlowTable(maxFlows, clock, evictedFlows)

    val openFlows: Int get() = flowTable.size()

    // Uid cache per 5-tuple / connection
    private val uidCache = ConcurrentHashMap<FlowKey, Int>()

    private val packetParser = PacketParser()
    private val networkTasks = ConcurrentLinkedQueue<() -> Unit>()

    private var tunReaderThread: Thread? = null
    private var networkThread: Thread? = null

    fun run() {
        if (!running.compareAndSet(false, true)) return

        val netThread = Thread({ networkLoop() }, "Forwarder-Network")
        networkThread = netThread
        netThread.start()

        val reader = Thread({ tunReaderLoop() }, "Forwarder-TunReader")
        tunReaderThread = reader
        reader.start()

        try {
            reader.join()
        } catch (_: InterruptedException) {
        } finally {
            stop()
            try {
                netThread.join(1000)
            } catch (_: InterruptedException) {}
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            // Already stopped or stopping, ensure resources closed
            tun.close()
            try {
                selector.wakeup()
            } catch (_: Exception) {}
            return
        }

        tun.close()
        try {
            selector.wakeup()
        } catch (_: Exception) {}

        tunReaderThread?.interrupt()
        networkThread?.interrupt()

        try {
            selector.close()
        } catch (_: Exception) {}

        flowTable.closeAll()
    }

    private fun tunReaderLoop() {
        val readBuffer = ByteBuffer.allocateDirect(65535)

        while (running.get()) {
            try {
                readBuffer.clear()
                val bytesRead = tun.read(readBuffer)
                if (bytesRead <= 0) {
                    break
                }
                readBuffer.flip()

                val parsed = packetParser.parse(readBuffer, bytesRead)
                if (parsed == null) {
                    // Malformed or non-TCP/UDP packet dropped silently
                    continue
                }

                // Copy raw bytes to pass to network thread
                readBuffer.position(0)
                val raw = ByteArray(bytesRead)
                readBuffer.get(raw)

                dispatchPacketToNetwork(parsed, raw)
            } catch (e: Exception) {
                if (!running.get()) break
                errors.incrementAndGet()
            }
        }
    }

    private fun dispatchPacketToNetwork(packet: ParsedPacket, raw: ByteArray) {
        networkTasks.offer {
            try {
                when (packet) {
                    is ParsedPacket.Tcp -> handleTcpPacket(packet, raw)
                    is ParsedPacket.Udp -> handleUdpPacket(packet, raw)
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            }
        }
        try {
            selector.wakeup()
        } catch (_: Exception) {}
    }

    private fun handleTcpPacket(tcp: ParsedPacket.Tcp, raw: ByteArray) {
        val key = FlowKey(6, tcp.src, tcp.dst)
        var flow = flowTable.getTcp(key)

        if (flow == null) {
            // New TCP flow only on SYN (and not RST)
            if ((tcp.flags and TcpFlags.SYN) != 0 && (tcp.flags and TcpFlags.RST) == 0) {
                val uid = uidCache.computeIfAbsent(key) {
                    owner.uidOf(6, tcp.src, tcp.dst)
                }
                val flowId = flowTable.generateFlowId()
                val newFlow = TcpFlow(
                    flowId = flowId,
                    src = tcp.src,
                    dst = tcp.dst,
                    ownerUid = uid,
                    tun = tun,
                    protector = protector,
                    listener = listener,
                    clock = clock,
                    mtu = mtu,
                    selector = selector,
                    tunWriteLock = tunWriteLock
                )
                flowTable.putTcp(key, newFlow)
                newFlow.startConnect(tcp.mss, tcp.seq)
            } else {
                // Non-SYN packet for unknown flow: respond with RST+ACK or RST
                sendStatelessRst(tcp)
            }
        } else {
            flow.handlePacketFromTun(tcp, raw)
            if (flow.state == TcpFlow.State.CLOSED) {
                flowTable.tcpFlows.remove(key)
            }
        }
    }

    private fun sendStatelessRst(tcp: ParsedPacket.Tcp) {
        if ((tcp.flags and TcpFlags.RST) != 0) return
        val seq = if ((tcp.flags and TcpFlags.ACK) != 0) tcp.ack else 0L
        val ack = (tcp.seq + tcp.payloadLength + (if ((tcp.flags and TcpFlags.SYN) != 0 || (tcp.flags and TcpFlags.FIN) != 0) 1 else 0)) and 0xFFFFFFFFL
        val flags = if ((tcp.flags and TcpFlags.ACK) != 0) TcpFlags.RST else (TcpFlags.RST or TcpFlags.ACK)

        synchronized(tunWriteLock) {
            val buf = ByteBuffer.allocate(100)
            val totalLen = PacketWriter.writeTcp(
                buffer = buf,
                src = tcp.dst,
                dst = tcp.src,
                seq = seq,
                ack = ack,
                flags = flags,
                window = 0
            )
            buf.flip()
            tun.write(buf, totalLen)
        }
    }

    private fun handleUdpPacket(udp: ParsedPacket.Udp, raw: ByteArray) {
        val key = FlowKey(17, udp.src, udp.dst)
        var flow = flowTable.getUdp(key)

        if (flow == null) {
            val uid = uidCache.computeIfAbsent(key) {
                owner.uidOf(17, udp.src, udp.dst)
            }
            val flowId = flowTable.generateFlowId()
            val newFlow = UdpFlow(
                flowId = flowId,
                src = udp.src,
                dst = udp.dst,
                ownerUid = uid,
                tun = tun,
                protector = protector,
                listener = listener,
                clock = clock,
                mtu = mtu,
                selector = selector,
                tunWriteLock = tunWriteLock
            )
            flowTable.putUdp(key, newFlow)
            flow = newFlow
        }

        flow.handlePacketFromTun(udp, raw)
    }

    private fun networkLoop() {
        var lastCleanupTime = clock.instant()

        while (running.get()) {
            try {
                // Drain network tasks first
                drainNetworkTasks()

                val selectResult = try {
                    selector.select(100)
                } catch (e: ClosedSelectorException) {
                    break
                }

                drainNetworkTasks()

                val selectedKeys = selector.selectedKeys()
                val keyIterator = selectedKeys.iterator()

                while (keyIterator.hasNext()) {
                    val key = keyIterator.next()
                    keyIterator.remove()

                    if (!key.isValid) continue

                    val attachment = key.attachment()
                    if (attachment is TcpFlow) {
                        handleTcpSelectionKey(key, attachment)
                    } else if (attachment is UdpFlow) {
                        handleUdpSelectionKey(key, attachment)
                    }
                }

                // Check TCP retransmissions and idle flow cleanups
                for (flow in flowTable.tcpFlows.values) {
                    flow.checkRetransmissions()
                }

                val now = clock.instant()
                flowTable.cleanupDeadFlows(now)
            } catch (e: Exception) {
                if (!running.get()) break
                errors.incrementAndGet()
            }
        }
    }

    private fun drainNetworkTasks() {
        while (true) {
            val task = networkTasks.poll() ?: break
            try {
                task.invoke()
            } catch (e: Exception) {
                errors.incrementAndGet()
            }
        }
    }

    private fun handleTcpSelectionKey(key: SelectionKey, flow: TcpFlow) {
        try {
            if (key.isConnectable) {
                flow.onConnected()
            }
            if (key.isValid && key.isReadable) {
                flow.onSocketReadable()
            }
            if (key.isValid && key.isWritable) {
                flow.flushUpstreamToSocket()
            }
        } catch (e: Exception) {
            flow.sendRst()
            flow.close()
        }
    }

    private fun handleUdpSelectionKey(key: SelectionKey, flow: UdpFlow) {
        try {
            if (key.isValid && key.isReadable) {
                flow.onSocketReadable()
            }
        } catch (e: Exception) {
            flow.close()
        }
    }
}
