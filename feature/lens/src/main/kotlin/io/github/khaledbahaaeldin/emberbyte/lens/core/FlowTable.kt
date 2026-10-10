package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class FlowKey(
    val protocol: Int,
    val src: InetSocketAddress,
    val dst: InetSocketAddress
)

class FlowTable(
    val maxFlows: Int,
    val clock: Clock,
    val evictedFlows: AtomicLong
) {
    private val nextFlowId = AtomicLong(1)
    val tcpFlows = ConcurrentHashMap<FlowKey, TcpFlow>()
    val udpFlows = ConcurrentHashMap<FlowKey, UdpFlow>()

    fun size(): Int = tcpFlows.size + udpFlows.size

    fun generateFlowId(): Long = nextFlowId.getAndIncrement()

    fun getTcp(key: FlowKey): TcpFlow? = tcpFlows[key]
    fun getUdp(key: FlowKey): UdpFlow? = udpFlows[key]

    fun putTcp(key: FlowKey, flow: TcpFlow) {
        checkEviction()
        tcpFlows[key] = flow
    }

    fun putUdp(key: FlowKey, flow: UdpFlow) {
        checkEviction()
        udpFlows[key] = flow
    }

    private fun checkEviction() {
        if (size() >= maxFlows) {
            evictIdleLongest()
        }
    }

    private fun evictIdleLongest() {
        var oldestTcpKey: FlowKey? = null
        var oldestTcpFlow: TcpFlow? = null
        var oldestTcpTime: Instant? = null

        for ((key, flow) in tcpFlows) {
            if (oldestTcpTime == null || flow.lastActiveAt.isBefore(oldestTcpTime)) {
                oldestTcpTime = flow.lastActiveAt
                oldestTcpKey = key
                oldestTcpFlow = flow
            }
        }

        var oldestUdpKey: FlowKey? = null
        var oldestUdpFlow: UdpFlow? = null
        var oldestUdpTime: Instant? = null

        for ((key, flow) in udpFlows) {
            if (oldestUdpTime == null || flow.lastActiveAt.isBefore(oldestUdpTime)) {
                oldestUdpTime = flow.lastActiveAt
                oldestUdpKey = key
                oldestUdpFlow = flow
            }
        }

        val evictTcp = when {
            oldestTcpTime != null && oldestUdpTime != null -> oldestTcpTime.isBefore(oldestUdpTime)
            oldestTcpTime != null -> true
            oldestUdpTime != null -> false
            else -> false
        }

        if (evictTcp && oldestTcpKey != null) {
            val removed = tcpFlows.remove(oldestTcpKey)
            if (removed != null) {
                removed.close()
                evictedFlows.incrementAndGet()
            }
        } else if (oldestUdpKey != null) {
            val removed = udpFlows.remove(oldestUdpKey)
            if (removed != null) {
                removed.close()
                evictedFlows.incrementAndGet()
            }
        }
    }

    fun cleanupDeadFlows(now: Instant) {
        val tcpIterator = tcpFlows.entries.iterator()
        while (tcpIterator.hasNext()) {
            val entry = tcpIterator.next()
            if (entry.value.isDead(now)) {
                entry.value.close()
                tcpIterator.remove()
            }
        }

        val udpIterator = udpFlows.entries.iterator()
        while (udpIterator.hasNext()) {
            val entry = udpIterator.next()
            if (entry.value.isDead(now)) {
                entry.value.close()
                udpIterator.remove()
            }
        }
    }

    fun closeAll() {
        for (flow in tcpFlows.values) {
            flow.close()
        }
        tcpFlows.clear()

        for (flow in udpFlows.values) {
            flow.close()
        }
        udpFlows.clear()
    }
}
