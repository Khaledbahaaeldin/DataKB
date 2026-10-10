package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress
import java.time.Instant

data class FlowInfo(
    val flowId: Long,
    val uid: Int,
    val protocol: Int,
    val local: InetSocketAddress,
    val remote: InetSocketAddress,
    val openedAt: Instant
)

interface FlowListener {
    fun onFlowOpened(flow: FlowInfo)
    fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long)
    fun onName(flowId: Long, name: String)
    fun onDns(answers: List<DnsAnswer>)
    fun onFlowClosed(flowId: Long)
}
