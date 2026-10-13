package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class PacketParser {

    /**
     * Parses an IPv4 or IPv6 TCP/UDP packet.
     * Returns null for anything that is not a valid IPv4/IPv6 TCP/UDP packet,
     * or on any bounds or checksum failure.
     */
    private val scratchArray = ThreadLocal.withInitial { ByteArray(65535) }

    private var lastSrc4Int = 0
    private var lastSrc4Address: InetAddress? = null
    private var lastDst4Int = 0
    private var lastDst4Address: InetAddress? = null

    private var lastSrc4Port = -1
    private var lastSrc4SocketAddress: InetSocketAddress? = null
    private var lastDst4Port = -1
    private var lastDst4SocketAddress: InetSocketAddress? = null

    fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20 || buffer.remaining() < length) return null

        val startPos = buffer.position()
        if (length > buffer.limit() - startPos) return null

        val rawBytes = scratchArray.get()!!
        val origPos = buffer.position()
        buffer.get(rawBytes, 0, length)
        buffer.position(origPos)

        val firstByte = rawBytes[0].toInt() and 0xFF
        val version = firstByte ushr 4

        return when (version) {
            4 -> parseIpv4(rawBytes, length)
            6 -> parseIpv6(rawBytes, length)
            else -> null
        }
    }

    private fun parseIpv4(bytes: ByteArray, length: Int): ParsedPacket? {
        val ihl = bytes[0].toInt() and 0x0F
        val ipHeaderLen = ihl * 4
        if (ipHeaderLen < 20 || ipHeaderLen > length) return null

        val totalLength = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        if (totalLength < ipHeaderLen || totalLength > length) return null

        // Check flags and fragment offset
        val flagsAndFrag = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        val mf = (flagsAndFrag and 0x2000) != 0
        val fragOffset = flagsAndFrag and 0x1FFF
        if (mf || fragOffset != 0) {
            // Fragmented IPv4 dropped
            return null
        }

        // Verify IPv4 header checksum
        if (Checksums.ipv4Header(bytes, 0, ipHeaderLen) != 0) {
            return null
        }

        val protocol = bytes[9].toInt() and 0xFF
        val srcIpInt = ((bytes[12].toInt() and 0xFF) shl 24) or
                ((bytes[13].toInt() and 0xFF) shl 16) or
                ((bytes[14].toInt() and 0xFF) shl 8) or
                (bytes[15].toInt() and 0xFF)
        val dstIpInt = ((bytes[16].toInt() and 0xFF) shl 24) or
                ((bytes[17].toInt() and 0xFF) shl 16) or
                ((bytes[18].toInt() and 0xFF) shl 8) or
                (bytes[19].toInt() and 0xFF)

        val srcAddress = if (srcIpInt == lastSrc4Int && lastSrc4Address != null) {
            lastSrc4Address!!
        } else {
            val srcIp = ByteArray(4)
            System.arraycopy(bytes, 12, srcIp, 0, 4)
            val addr = InetAddress.getByAddress(srcIp)
            lastSrc4Int = srcIpInt
            lastSrc4Address = addr
            addr
        }

        val dstAddress = if (dstIpInt == lastDst4Int && lastDst4Address != null) {
            lastDst4Address!!
        } else {
            val dstIp = ByteArray(4)
            System.arraycopy(bytes, 16, dstIp, 0, 4)
            val addr = InetAddress.getByAddress(dstIp)
            lastDst4Int = dstIpInt
            lastDst4Address = addr
            addr
        }

        val transportLen = totalLength - ipHeaderLen
        return parseTransport(
            bytes = bytes,
            ipOff = 0,
            ipv6 = false,
            protocol = protocol,
            segOff = ipHeaderLen,
            segLen = transportLen,
            srcAddress = srcAddress,
            dstAddress = dstAddress
        )
    }

    private fun parseIpv6(bytes: ByteArray, length: Int): ParsedPacket? {
        if (length < 40) return null
        val payloadLenField = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val totalLength = 40 + payloadLenField
        if (totalLength > length) return null

        var nextHeader = bytes[6].toInt() and 0xFF
        val srcIp = ByteArray(16)
        System.arraycopy(bytes, 8, srcIp, 0, 16)
        val dstIp = ByteArray(16)
        System.arraycopy(bytes, 24, dstIp, 0, 16)

        val srcAddress = InetAddress.getByAddress(srcIp)
        val dstAddress = InetAddress.getByAddress(dstIp)

        var currOff = 40
        val endOff = totalLength

        // Skip extension headers: hop-by-hop (0), routing (43), destination options (60)
        while (currOff < endOff) {
            when (nextHeader) {
                6, 17 -> {
                    // TCP or UDP reached
                    val segLen = endOff - currOff
                    return parseTransport(
                        bytes = bytes,
                        ipOff = 0,
                        ipv6 = true,
                        protocol = nextHeader,
                        segOff = currOff,
                        segLen = segLen,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress
                    )
                }
                0, 43, 60 -> {
                    // Extension header
                    if (currOff + 2 > endOff) return null
                    val nextNext = bytes[currOff].toInt() and 0xFF
                    val hdrExtLen = bytes[currOff + 1].toInt() and 0xFF
                    val extLen = (hdrExtLen + 1) * 8
                    if (currOff + extLen > endOff) return null
                    nextHeader = nextNext
                    currOff += extLen
                }
                else -> {
                    // Fragment header (44), ICMP, or unknown -> drop
                    return null
                }
            }
        }

        return null
    }

    private fun parseTransport(
        bytes: ByteArray,
        ipOff: Int,
        ipv6: Boolean,
        protocol: Int,
        segOff: Int,
        segLen: Int,
        srcAddress: InetAddress,
        dstAddress: InetAddress
    ): ParsedPacket? {
        when (protocol) {
            6 -> { // TCP
                if (segLen < 20) return null
                // Verify TCP checksum
                if (Checksums.transport(bytes, ipOff, ipv6, 6, segOff, segLen) != 0) {
                    return null
                }

                val srcPort = ((bytes[segOff].toInt() and 0xFF) shl 8) or (bytes[segOff + 1].toInt() and 0xFF)
                val dstPort = ((bytes[segOff + 2].toInt() and 0xFF) shl 8) or (bytes[segOff + 3].toInt() and 0xFF)
                val seq = (readU32(bytes, segOff + 4)) and 0xFFFFFFFFL
                val ack = (readU32(bytes, segOff + 8)) and 0xFFFFFFFFL

                val dataOffset = (bytes[segOff + 12].toInt() and 0xFF) ushr 4
                val tcpHeaderLen = dataOffset * 4
                if (tcpHeaderLen < 20 || tcpHeaderLen > segLen) return null

                val flags = bytes[segOff + 13].toInt() and 0xFF
                val window = ((bytes[segOff + 14].toInt() and 0xFF) shl 8) or (bytes[segOff + 15].toInt() and 0xFF)

                // Parse TCP options for MSS (kind 2, len 4)
                var mss: Int? = null
                var optIdx = segOff + 20
                val optEnd = segOff + tcpHeaderLen
                while (optIdx < optEnd) {
                    val kind = bytes[optIdx].toInt() and 0xFF
                    if (kind == 0) { // End of option list
                        break
                    } else if (kind == 1) { // NOP
                        optIdx++
                    } else {
                        if (optIdx + 1 >= optEnd) break
                        val optLen = bytes[optIdx + 1].toInt() and 0xFF
                        if (optLen < 2 || optIdx + optLen > optEnd) break
                        if (kind == 2 && optLen == 4) {
                            mss = ((bytes[optIdx + 2].toInt() and 0xFF) shl 8) or (bytes[optIdx + 3].toInt() and 0xFF)
                        }
                        optIdx += optLen
                    }
                }

                val payloadOffset = segOff + tcpHeaderLen
                val payloadLength = segLen - tcpHeaderLen

                val srcSocketAddress = if (srcAddress === lastSrc4Address && srcPort == lastSrc4Port && lastSrc4SocketAddress != null) {
                    lastSrc4SocketAddress!!
                } else {
                    val sa = InetSocketAddress(srcAddress, srcPort)
                    if (srcAddress === lastSrc4Address) {
                        lastSrc4Port = srcPort
                        lastSrc4SocketAddress = sa
                    }
                    sa
                }

                val dstSocketAddress = if (dstAddress === lastDst4Address && dstPort == lastDst4Port && lastDst4SocketAddress != null) {
                    lastDst4SocketAddress!!
                } else {
                    val sa = InetSocketAddress(dstAddress, dstPort)
                    if (dstAddress === lastDst4Address) {
                        lastDst4Port = dstPort
                        lastDst4SocketAddress = sa
                    }
                    sa
                }

                return ParsedPacket.Tcp(
                    src = srcSocketAddress,
                    dst = dstSocketAddress,
                    seq = seq,
                    ack = ack,
                    flags = flags,
                    window = window,
                    mss = mss,
                    payloadOffset = payloadOffset,
                    payloadLength = payloadLength
                )
            }
            17 -> { // UDP
                if (segLen < 8) return null
                val udpLen = ((bytes[segOff + 4].toInt() and 0xFF) shl 8) or (bytes[segOff + 5].toInt() and 0xFF)
                if (udpLen < 8 || udpLen > segLen) return null

                val chkField = ((bytes[segOff + 6].toInt() and 0xFF) shl 8) or (bytes[segOff + 7].toInt() and 0xFF)
                if (chkField == 0 && !ipv6) {
                    // UDP checksum 0 on IPv4 means none; allowed!
                } else {
                    if (Checksums.transport(bytes, ipOff, ipv6, 17, segOff, segLen) != 0) {
                        return null
                    }
                }

                val srcPort = ((bytes[segOff].toInt() and 0xFF) shl 8) or (bytes[segOff + 1].toInt() and 0xFF)
                val dstPort = ((bytes[segOff + 2].toInt() and 0xFF) shl 8) or (bytes[segOff + 3].toInt() and 0xFF)

                val payloadOffset = segOff + 8
                val payloadLength = udpLen - 8

                val srcSocketAddress = if (srcAddress === lastSrc4Address && srcPort == lastSrc4Port && lastSrc4SocketAddress != null) {
                    lastSrc4SocketAddress!!
                } else {
                    val sa = InetSocketAddress(srcAddress, srcPort)
                    if (srcAddress === lastSrc4Address) {
                        lastSrc4Port = srcPort
                        lastSrc4SocketAddress = sa
                    }
                    sa
                }

                val dstSocketAddress = if (dstAddress === lastDst4Address && dstPort == lastDst4Port && lastDst4SocketAddress != null) {
                    lastDst4SocketAddress!!
                } else {
                    val sa = InetSocketAddress(dstAddress, dstPort)
                    if (dstAddress === lastDst4Address) {
                        lastDst4Port = dstPort
                        lastDst4SocketAddress = sa
                    }
                    sa
                }

                return ParsedPacket.Udp(
                    src = srcSocketAddress,
                    dst = dstSocketAddress,
                    payloadOffset = payloadOffset,
                    payloadLength = payloadLength
                )
            }
            else -> return null
        }
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long {
        return (((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF))
    }
}
