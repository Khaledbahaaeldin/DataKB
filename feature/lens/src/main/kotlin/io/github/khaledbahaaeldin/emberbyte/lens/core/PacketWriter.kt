package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketWriter {

    fun buildMssOption(mss: Int): ByteArray {
        return byteArrayOf(
            0x02, // Kind = 2 (MSS)
            0x04, // Length = 4
            (mss shr 8).toByte(),
            mss.toByte()
        )
    }

    fun writeIpv4Tcp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray? = null,
        payloadOffset: Int = 0,
        payloadLength: Int = payload?.size ?: 0,
        options: ByteArray = ByteArray(0),
        srcIpBytes: ByteArray? = null,
        dstIpBytes: ByteArray? = null
    ): Int {
        val srcIp = src.address as? Inet4Address
        val dstIp = dst.address as? Inet4Address

        val ipHeaderLen = 20
        val tcpHeaderLen = 20 + options.size
        val paddedTcpHeaderLen = ((tcpHeaderLen + 3) / 4) * 4
        val totalLen = ipHeaderLen + paddedTcpHeaderLen + payloadLength

        val startPos = buffer.position()
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header
        buffer.put(0x45.toByte()) // Ver 4, IHL 5
        buffer.put(0.toByte()) // DSCP/ECN
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0.toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Don't fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(6.toByte()) // Protocol TCP
        val ipChecksumPos = buffer.position()
        buffer.putShort(0.toShort())
        if (srcIpBytes != null) buffer.put(srcIpBytes) else buffer.put(srcIp!!.address)
        if (dstIpBytes != null) buffer.put(dstIpBytes) else buffer.put(dstIp!!.address)

        val tcpOffset = buffer.position()
        buffer.putShort(src.port.toShort())
        buffer.putShort(dst.port.toShort())
        buffer.putInt(seq.toInt())
        buffer.putInt(ack.toInt())
        val dataOffset = paddedTcpHeaderLen / 4
        buffer.put((dataOffset shl 4).toByte())
        buffer.put(flags.toByte())
        buffer.putShort(window.toShort())
        val tcpChecksumPos = buffer.position()
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort()) // Urgent pointer

        if (options.isNotEmpty()) {
            buffer.put(options)
        }
        while (buffer.position() < tcpOffset + paddedTcpHeaderLen) {
            buffer.put(0.toByte())
        }

        if (payload != null && payloadLength > 0) {
            buffer.put(payload, payloadOffset, payloadLength)
        }

        val endPos = startPos + totalLen
        buffer.position(endPos)

        // Checksums calculation
        val ipChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.ipv4Header(raw, arrayOffset + startPos, ipHeaderLen)
        } else {
            Checksums.ipv4Header(buffer, startPos, ipHeaderLen)
        }
        buffer.putShort(ipChecksumPos, ipChecksum.toShort())

        val tcpLen = paddedTcpHeaderLen + payloadLength
        val tcpChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.transport(raw, arrayOffset + startPos, false, 6, arrayOffset + tcpOffset, tcpLen)
        } else {
            Checksums.transport(buffer, startPos, false, 6, tcpOffset, tcpLen)
        }
        buffer.putShort(tcpChecksumPos, tcpChecksum.toShort())

        buffer.position(endPos)
        return totalLen
    }

    fun writeIpv6Tcp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray? = null,
        payloadOffset: Int = 0,
        payloadLength: Int = payload?.size ?: 0,
        options: ByteArray = ByteArray(0)
    ): Int {
        val srcIp = src.address as Inet6Address
        val dstIp = dst.address as Inet6Address

        val ipHeaderLen = 40
        val tcpHeaderLen = 20 + options.size
        val paddedTcpHeaderLen = ((tcpHeaderLen + 3) / 4) * 4
        val tcpLen = paddedTcpHeaderLen + payloadLength
        val totalLen = ipHeaderLen + tcpLen

        val startPos = buffer.position()
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IPv6 Header
        buffer.putInt(0x60000000)
        buffer.putShort(tcpLen.toShort())
        buffer.put(6.toByte()) // Next header TCP
        buffer.put(64.toByte()) // Hop limit
        buffer.put(srcIp.address)
        buffer.put(dstIp.address)

        val tcpOffset = buffer.position()
        buffer.putShort(src.port.toShort())
        buffer.putShort(dst.port.toShort())
        buffer.putInt(seq.toInt())
        buffer.putInt(ack.toInt())
        val dataOffset = paddedTcpHeaderLen / 4
        buffer.put((dataOffset shl 4).toByte())
        buffer.put(flags.toByte())
        buffer.putShort(window.toShort())
        val tcpChecksumPos = buffer.position()
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort())

        if (options.isNotEmpty()) {
            buffer.put(options)
        }
        while (buffer.position() < tcpOffset + paddedTcpHeaderLen) {
            buffer.put(0.toByte())
        }

        if (payload != null && payloadLength > 0) {
            buffer.put(payload, payloadOffset, payloadLength)
        }

        val endPos = startPos + totalLen
        buffer.position(endPos)

        val tcpChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.transport(raw, arrayOffset + startPos, true, 6, arrayOffset + tcpOffset, tcpLen)
        } else {
            Checksums.transport(buffer, startPos, true, 6, tcpOffset, tcpLen)
        }
        buffer.putShort(tcpChecksumPos, tcpChecksum.toShort())

        buffer.position(endPos)
        return totalLen
    }

    fun writeIpv4Udp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        payload: ByteArray,
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size
    ): Int {
        val srcIp = src.address as Inet4Address
        val dstIp = dst.address as Inet4Address

        val ipHeaderLen = 20
        val udpLen = 8 + payloadLength
        val totalLen = ipHeaderLen + udpLen

        val startPos = buffer.position()
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.put(0x45.toByte())
        buffer.put(0.toByte())
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort())
        buffer.put(64.toByte())
        buffer.put(17.toByte()) // UDP
        val ipChecksumPos = buffer.position()
        buffer.putShort(0.toShort())
        buffer.put(srcIp.address)
        buffer.put(dstIp.address)

        val udpOffset = buffer.position()
        buffer.putShort(src.port.toShort())
        buffer.putShort(dst.port.toShort())
        buffer.putShort(udpLen.toShort())
        val udpChecksumPos = buffer.position()
        buffer.putShort(0.toShort())

        if (payloadLength > 0) {
            buffer.put(payload, payloadOffset, payloadLength)
        }

        val endPos = buffer.position()

        val ipChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.ipv4Header(raw, arrayOffset + startPos, ipHeaderLen)
        } else {
            Checksums.ipv4Header(buffer, startPos, ipHeaderLen)
        }
        buffer.putShort(ipChecksumPos, ipChecksum.toShort())

        val udpChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.transport(raw, arrayOffset + startPos, false, 17, arrayOffset + udpOffset, udpLen)
        } else {
            Checksums.transport(buffer, startPos, false, 17, udpOffset, udpLen)
        }
        buffer.putShort(udpChecksumPos, (if (udpChecksum == 0) 0xFFFF else udpChecksum).toShort())

        buffer.position(endPos)
        return totalLen
    }

    fun writeIpv6Udp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        payload: ByteArray,
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size
    ): Int {
        val srcIp = src.address as Inet6Address
        val dstIp = dst.address as Inet6Address

        val ipHeaderLen = 40
        val udpLen = 8 + payloadLength
        val totalLen = ipHeaderLen + udpLen

        val startPos = buffer.position()
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(0x60000000)
        buffer.putShort(udpLen.toShort())
        buffer.put(17.toByte()) // UDP
        buffer.put(64.toByte())
        buffer.put(srcIp.address)
        buffer.put(dstIp.address)

        val udpOffset = buffer.position()
        buffer.putShort(src.port.toShort())
        buffer.putShort(dst.port.toShort())
        buffer.putShort(udpLen.toShort())
        val udpChecksumPos = buffer.position()
        buffer.putShort(0.toShort())

        if (payloadLength > 0) {
            buffer.put(payload, payloadOffset, payloadLength)
        }

        val endPos = buffer.position()

        val udpChecksum = if (buffer.hasArray()) {
            val raw = buffer.array()
            val arrayOffset = buffer.arrayOffset()
            Checksums.transport(raw, arrayOffset + startPos, true, 17, arrayOffset + udpOffset, udpLen)
        } else {
            Checksums.transport(buffer, startPos, true, 17, udpOffset, udpLen)
        }
        buffer.putShort(udpChecksumPos, (if (udpChecksum == 0) 0xFFFF else udpChecksum).toShort())

        buffer.position(endPos)
        return totalLen
    }

    fun writeTcp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray? = null,
        payloadOffset: Int = 0,
        payloadLength: Int = payload?.size ?: 0,
        options: ByteArray = ByteArray(0),
        srcIpBytes: ByteArray? = null,
        dstIpBytes: ByteArray? = null
    ): Int {
        return if (src.address is Inet6Address) {
            writeIpv6Tcp(buffer, src, dst, seq, ack, flags, window, payload, payloadOffset, payloadLength, options)
        } else {
            writeIpv4Tcp(buffer, src, dst, seq, ack, flags, window, payload, payloadOffset, payloadLength, options, srcIpBytes, dstIpBytes)
        }
    }

    fun writeUdp(
        buffer: ByteBuffer,
        src: InetSocketAddress,
        dst: InetSocketAddress,
        payload: ByteArray,
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size
    ): Int {
        return if (src.address is Inet6Address) {
            writeIpv6Udp(buffer, src, dst, payload, payloadOffset, payloadLength)
        } else {
            writeIpv4Udp(buffer, src, dst, payload, payloadOffset, payloadLength)
        }
    }
}
