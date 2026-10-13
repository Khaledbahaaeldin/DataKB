package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.time.Clock
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface SocketChannelFactory {
    fun open(): SocketChannel
    companion object {
        val SYSTEM = SocketChannelFactory { SocketChannel.open() }
    }
}

class BoundedLruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V {
        val existing = map[key]
        if (existing != null) return existing
        val computed = mappingFunction(key)
        map[key] = computed
        return computed
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun clear() = map.clear()
}

class Forwarder(
    val tun: TunDevice,
    val protector: SocketProtector,
    val owner: OwnerResolver,
    val listener: FlowListener,
    val clock: Clock = Clock.systemUTC(),
    val maxFlows: Int = 1024,
    val mtu: Int = 1400,
    val channelFactory: SocketChannelFactory = SocketChannelFactory.SYSTEM
) {
    val evictedFlows = AtomicLong(0)
    val errors = AtomicLong(0)
    val selectorWakeups = AtomicLong(0)
    val droppedSegments = AtomicLong(0)
    val oversizedDatagrams = AtomicLong(0)
    val protectFailures = AtomicLong(0)

    private val uidCache = BoundedLruCache<FlowKey, Int>(4096)
    internal val uidCacheSize: Int get() = uidCache.size()
    internal fun uidCacheSize(): Int = uidCache.size()
    internal val flowListener: FlowListener get() = internalListener
    internal fun registerFlowForTest(key: FlowKey, uid: Int = 10042) {
        uidCache.computeIfAbsent(key) { uid }
    }

    internal val tunReaderThreadInstance: Thread? get() = tunReaderThread
    internal val networkThreadInstance: Thread? get() = networkThread

    private val running = AtomicBoolean(false)
    private val tunWriteLock = Any()
    private val selector = Selector.open()
    private val flowTable = FlowTable(maxFlows, clock, evictedFlows)

    val openFlows: Int get() = flowTable.size()

    val directBufferPool = DirectBufferPool(bufferCapacity = 2048, maxPoolSize = 1024)
    val byteArrayPool = ByteArrayPool(arrayCapacity = 2048, maxPoolSize = 1024)

    private val flowKeys = ConcurrentHashMap<Long, FlowKey>()

    private val internalListener = object : FlowListener {
        override fun onFlowOpened(flow: FlowInfo) {
            flowKeys[flow.flowId] = FlowKey(flow.protocol, flow.local, flow.remote)
            listener.onFlowOpened(flow)
        }

        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {
            listener.onBytes(flowId, rxBytes, txBytes)
        }

        override fun onName(flowId: Long, name: String) {
            listener.onName(flowId, name)
        }

        override fun onDns(answers: List<DnsAnswer>) {
            listener.onDns(answers)
        }

        override fun onFlowClosed(flowId: Long) {
            val key = flowKeys.remove(flowId)
            if (key != null) {
                uidCache.remove(key)
            }
            listener.onFlowClosed(flowId)
        }
    }

    private val packetParser = PacketParser()

    data class NetworkPacketTask(
        val packet: ParsedPacket,
        val raw: ByteArray,
        val length: Int,
        val isPooled: Boolean
    )

    private val networkTasks = java.util.concurrent.ArrayBlockingQueue<NetworkPacketTask>(512)

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
        uidCache.clear()
        flowKeys.clear()
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
                    continue
                }

                readBuffer.position(0)
                val isPooled = bytesRead <= 2048
                val raw = if (isPooled) byteArrayPool.acquire() else ByteArray(bytesRead)
                readBuffer.get(raw, 0, bytesRead)

                val task = NetworkPacketTask(parsed, raw, bytesRead, isPooled)
                while (running.get()) {
                    if (networkTasks.offer(task, 10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        break
                    }
                    try {
                        selector.wakeup()
                    } catch (_: Exception) {}
                }
                try {
                    selector.wakeup()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                if (!running.get()) break
                errors.incrementAndGet()
            }
        }
    }

    private fun handleTcpPacket(tcp: ParsedPacket.Tcp, raw: ByteArray) {
        val key = FlowKey(6, tcp.src, tcp.dst)
        var flow = flowTable.getTcp(key)

        if (flow == null) {
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
                    listener = internalListener,
                    clock = clock,
                    mtu = mtu,
                    selector = selector,
                    tunWriteLock = tunWriteLock,
                    droppedSegmentsCounter = droppedSegments,
                    channelFactory = channelFactory,
                    protectFailuresCounter = protectFailures,
                    directBufferPool = directBufferPool
                )
                flowTable.putTcp(key, newFlow)
                newFlow.startConnect(tcp.mss, tcp.seq)
            } else {
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
                listener = internalListener,
                clock = clock,
                mtu = mtu,
                selector = selector,
                tunWriteLock = tunWriteLock,
                oversizedDatagramsCounter = oversizedDatagrams,
                protectFailuresCounter = protectFailures
            )
            flowTable.putUdp(key, newFlow)
            flow = newFlow
        }

        flow.handlePacketFromTun(udp, raw)
    }

    private fun networkLoop() {
        while (running.get()) {
            try {
                drainNetworkTasks()

                val selectResult = try {
                    selector.select(100)
                } catch (e: ClosedSelectorException) {
                    break
                }
                selectorWakeups.incrementAndGet()

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
                when (val pkt = task.packet) {
                    is ParsedPacket.Tcp -> handleTcpPacket(pkt, task.raw)
                    is ParsedPacket.Udp -> handleUdpPacket(pkt, task.raw)
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                if (task.isPooled) {
                    byteArrayPool.release(task.raw)
                }
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
