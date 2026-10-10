package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketBuilder {

    fun ipv4Tcp(
        src: InetSocketAddress,
        dst: InetSocketAddress,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray = ByteArray(0),
        options: ByteArray = ByteArray(0),
        badIpChecksum: Boolean = false,
        badTcpChecksum: Boolean = false,
        ihl: Int = 5,
        identification: Int = 0,
        flagsAndFragOffset: Int = 0x4000 // Don't Fragment
    ): ByteArray {
        val srcIp = src.address as? Inet4Address ?: throw IllegalArgumentException("src must be IPv4")
        val dstIp = dst.address as? Inet4Address ?: throw IllegalArgumentException("dst must be IPv4")

        val ipHeaderLen = ihl * 4
        val tcpHeaderLen = 20 + options.size
        // Ensure options padding to 4 bytes if any
        val paddedTcpHeaderLen = ((tcpHeaderLen + 3) / 4) * 4
        val totalLen = ipHeaderLen + paddedTcpHeaderLen + payload.size

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header
        val verIhl = (4 shl 4) or ihl
        buf.put(verIhl.toByte())
        buf.put(0.toByte()) // DSCP/ECN
        buf.putShort(totalLen.toShort())
        buf.putShort(identification.toShort())
        buf.putShort(flagsAndFragOffset.toShort())
        buf.put(64.toByte()) // TTL
        buf.put(6.toByte()) // Protocol TCP
        val ipChecksumPos = buf.position()
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.put(srcIp.address)
        buf.put(dstIp.address)

        // Pad IP options if ihl > 5
        while (buf.position() < ipHeaderLen) {
            buf.put(0.toByte())
        }

        // Calculate and write IP checksum
        val ipChecksum = if (badIpChecksum) 0x1234 else Checksums.ipv4Header(buf.array(), 0, ipHeaderLen)
        buf.putShort(ipChecksumPos, ipChecksum.toShort())

        // TCP Header
        val tcpOffset = buf.position()
        buf.putShort(src.port.toShort())
        buf.putShort(dst.port.toShort())
        buf.putInt(seq.toInt())
        buf.putInt(ack.toInt())
        val dataOffset = paddedTcpHeaderLen / 4
        buf.put((dataOffset shl 4).toByte())
        buf.put(flags.toByte())
        buf.putShort(window.toShort())
        val tcpChecksumPos = buf.position()
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.putShort(0.toShort()) // Urgent pointer

        buf.put(options)
        while (buf.position() < tcpOffset + paddedTcpHeaderLen) {
            buf.put(0.toByte()) // Pad TCP options
        }

        buf.put(payload)

        // Calculate and write TCP checksum
        val tcpLen = paddedTcpHeaderLen + payload.size
        val tcpChecksum = if (badTcpChecksum) 0x5678 else Checksums.transport(buf.array(), 0, false, 6, tcpOffset, tcpLen)
        buf.putShort(tcpChecksumPos, tcpChecksum.toShort())

        return buf.array()
    }

    fun ipv4Udp(
        src: InetSocketAddress,
        dst: InetSocketAddress,
        payload: ByteArray = ByteArray(0),
        badIpChecksum: Boolean = false,
        badUdpChecksum: Boolean = false,
        zeroUdpChecksum: Boolean = false,
        ihl: Int = 5,
        identification: Int = 0,
        flagsAndFragOffset: Int = 0
    ): ByteArray {
        val srcIp = src.address as? Inet4Address ?: throw IllegalArgumentException("src must be IPv4")
        val dstIp = dst.address as? Inet4Address ?: throw IllegalArgumentException("dst must be IPv4")

        val ipHeaderLen = ihl * 4
        val udpLen = 8 + payload.size
        val totalLen = ipHeaderLen + udpLen

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header
        val verIhl = (4 shl 4) or ihl
        buf.put(verIhl.toByte())
        buf.put(0.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(identification.toShort())
        buf.putShort(flagsAndFragOffset.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // Protocol UDP
        val ipChecksumPos = buf.position()
        buf.putShort(0.toShort())
        buf.put(srcIp.address)
        buf.put(dstIp.address)

        while (buf.position() < ipHeaderLen) {
            buf.put(0.toByte())
        }

        val ipChecksum = if (badIpChecksum) 0x1234 else Checksums.ipv4Header(buf.array(), 0, ipHeaderLen)
        buf.putShort(ipChecksumPos, ipChecksum.toShort())

        // UDP Header
        val udpOffset = buf.position()
        buf.putShort(src.port.toShort())
        buf.putShort(dst.port.toShort())
        buf.putShort(udpLen.toShort())
        val udpChecksumPos = buf.position()
        buf.putShort(0.toShort())

        buf.put(payload)

        val udpChecksum = when {
            zeroUdpChecksum -> 0
            badUdpChecksum -> 0x5678
            else -> Checksums.transport(buf.array(), 0, false, 17, udpOffset, udpLen)
        }
        buf.putShort(udpChecksumPos, udpChecksum.toShort())

        return buf.array()
    }

    fun ipv6Tcp(
        src: InetSocketAddress,
        dst: InetSocketAddress,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray = ByteArray(0),
        options: ByteArray = ByteArray(0),
        extensionHeaders: List<Pair<Int, ByteArray>> = emptyList(), // Pair(nextHeader, extData)
        badTcpChecksum: Boolean = false
    ): ByteArray {
        val srcIp = src.address as? Inet6Address ?: throw IllegalArgumentException("src must be IPv6")
        val dstIp = dst.address as? Inet6Address ?: throw IllegalArgumentException("dst must be IPv6")

        val tcpHeaderLen = 20 + options.size
        val paddedTcpHeaderLen = ((tcpHeaderLen + 3) / 4) * 4
        val tcpLen = paddedTcpHeaderLen + payload.size

        var extHeadersLen = 0
        for (ext in extensionHeaders) {
            extHeadersLen += ext.second.size + 2 // nextHeader (1) + len (1) + data
        }

        val payloadLenField = extHeadersLen + tcpLen
        val totalLen = 40 + payloadLenField

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
        buf.putShort(payloadLenField.toShort())
        val firstNextHeader = if (extensionHeaders.isNotEmpty()) extensionHeaders.first().first else 6
        buf.put(firstNextHeader.toByte())
        buf.put(64.toByte()) // Hop Limit
        buf.put(srcIp.address)
        buf.put(dstIp.address)

        // Extension headers
        for (i in extensionHeaders.indices) {
            val nextHdr = if (i + 1 < extensionHeaders.size) extensionHeaders[i + 1].first else 6
            val data = extensionHeaders[i].second
            buf.put(nextHdr.toByte())
            // RFC 8200: Hdr Ext Len is length in 8-octet units, not including the first 8 octets.
            // Total ext len = data.size + 2. So (total ext len - 8) / 8.
            val hdrExtLen = (data.size + 2 - 8) / 8
            buf.put(hdrExtLen.toByte())
            buf.put(data)
        }

        val tcpOffset = buf.position()
        buf.putShort(src.port.toShort())
        buf.putShort(dst.port.toShort())
        buf.putInt(seq.toInt())
        buf.putInt(ack.toInt())
        val dataOffset = paddedTcpHeaderLen / 4
        buf.put((dataOffset shl 4).toByte())
        buf.put(flags.toByte())
        buf.putShort(window.toShort())
        val tcpChecksumPos = buf.position()
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(options)
        while (buf.position() < tcpOffset + paddedTcpHeaderLen) {
            buf.put(0.toByte())
        }

        buf.put(payload)

        val tcpChecksum = if (badTcpChecksum) 0x5678 else Checksums.transport(buf.array(), 0, true, 6, tcpOffset, tcpLen)
        buf.putShort(tcpChecksumPos, tcpChecksum.toShort())

        return buf.array()
    }

    fun ipv6Udp(
        src: InetSocketAddress,
        dst: InetSocketAddress,
        payload: ByteArray = ByteArray(0),
        extensionHeaders: List<Pair<Int, ByteArray>> = emptyList(),
        badUdpChecksum: Boolean = false
    ): ByteArray {
        val srcIp = src.address as? Inet6Address ?: throw IllegalArgumentException("src must be IPv6")
        val dstIp = dst.address as? Inet6Address ?: throw IllegalArgumentException("dst must be IPv6")

        val udpLen = 8 + payload.size
        var extHeadersLen = 0
        for (ext in extensionHeaders) {
            extHeadersLen += ext.second.size + 2
        }

        val payloadLenField = extHeadersLen + udpLen
        val totalLen = 40 + payloadLenField

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000)
        buf.putShort(payloadLenField.toShort())
        val firstNextHeader = if (extensionHeaders.isNotEmpty()) extensionHeaders.first().first else 17
        buf.put(firstNextHeader.toByte())
        buf.put(64.toByte())
        buf.put(srcIp.address)
        buf.put(dstIp.address)

        for (i in extensionHeaders.indices) {
            val nextHdr = if (i + 1 < extensionHeaders.size) extensionHeaders[i + 1].first else 17
            val data = extensionHeaders[i].second
            buf.put(nextHdr.toByte())
            val hdrExtLen = (data.size + 2 - 8) / 8
            buf.put(hdrExtLen.toByte())
            buf.put(data)
        }

        val udpOffset = buf.position()
        buf.putShort(src.port.toShort())
        buf.putShort(dst.port.toShort())
        buf.putShort(udpLen.toShort())
        val udpChecksumPos = buf.position()
        buf.putShort(0.toShort())

        buf.put(payload)

        val udpChecksum = if (badUdpChecksum) 0x5678 else Checksums.transport(buf.array(), 0, true, 17, udpOffset, udpLen)
        buf.putShort(udpChecksumPos, udpChecksum.toShort())

        return buf.array()
    }

    fun buildMssOption(mss: Int): ByteArray {
        return byteArrayOf(
            0x02, // Kind = 2 (MSS)
            0x04, // Length = 4
            (mss shr 8).toByte(),
            mss.toByte()
        )
    }

    /**
     * Helper to encode DNS domain name into labels.
     */
    fun encodeDnsName(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        if (name.isNotEmpty()) {
            for (label in name.split('.')) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
        }
        out.write(0)
        return out.toByteArray()
    }

    /**
     * Builds a DNS response packet payload.
     * question: Pair(qname, qtype)
     * answers: List of Triple(name or pointerOffset, type, data)
     */
    fun dnsResponse(
        id: Int,
        questions: List<Pair<String, Int>> = emptyList(),
        answers: List<DnsRawAnswer> = emptyList(),
        rcode: Int = 0,
        isResponse: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        header.putShort(id.toShort())
        var flags = 0
        if (isResponse) flags = flags or 0x8000 // QR = 1
        flags = flags or (rcode and 0x0F)
        header.putShort(flags.toShort())
        header.putShort(questions.size.toShort())
        header.putShort(answers.size.toShort())
        header.putShort(0.toShort()) // NSCOUNT
        header.putShort(0.toShort()) // ARCOUNT
        out.write(header.array())

        for (q in questions) {
            out.write(encodeDnsName(q.first))
            val qbuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            qbuf.putShort(q.second.toShort()) // QTYPE
            qbuf.putShort(1.toShort()) // QCLASS IN
            out.write(qbuf.array())
        }

        for (ans in answers) {
            when (ans.nameRef) {
                is NameRef.Literal -> out.write(encodeDnsName(ans.nameRef.name))
                is NameRef.Pointer -> {
                    val p = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                    p.putShort((0xC000 or (ans.nameRef.offset and 0x3FFF)).toShort())
                    out.write(p.array())
                }
            }
            val abuf = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            abuf.putShort(ans.type.toShort())
            abuf.putShort(1.toShort()) // CLASS IN
            abuf.putInt(ans.ttl.toInt())
            abuf.putShort(ans.rdata.size.toShort())
            out.write(abuf.array())
            out.write(ans.rdata)
        }

        return out.toByteArray()
    }

    sealed interface NameRef {
        data class Literal(val name: String) : NameRef
        data class Pointer(val offset: Int) : NameRef
    }

    data class DnsRawAnswer(
        val nameRef: NameRef,
        val type: Int,
        val ttl: Long,
        val rdata: ByteArray
    )

    fun clientHello(
        serverName: String?,
        extensions: List<Pair<Int, ByteArray>> = emptyList()
    ): ByteArray {
        val extStream = ByteArrayOutputStream()

        // If serverName is provided and not already in extensions, add SNI
        val allExtensions = extensions.toMutableList()
        if (serverName != null && allExtensions.none { it.first == 0 }) {
            val nameBytes = serverName.toByteArray(Charsets.US_ASCII)
            val sniData = ByteBuffer.allocate(2 + 1 + 2 + nameBytes.size).order(ByteOrder.BIG_ENDIAN)
            sniData.putShort((nameBytes.size + 3).toShort()) // list length
            sniData.put(0.toByte()) // host_name type = 0
            sniData.putShort(nameBytes.size.toShort())
            sniData.put(nameBytes)
            allExtensions.add(0 to sniData.array())
        }

        for (ext in allExtensions) {
            val ebuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            ebuf.putShort(ext.first.toShort())
            ebuf.putShort(ext.second.size.toShort())
            extStream.write(ebuf.array())
            extStream.write(ext.second)
        }
        val extBytes = extStream.toByteArray()

        val handshakeBody = ByteArrayOutputStream()
        val hsHeader = ByteBuffer.allocate(34 + 35 + 4 + 2).order(ByteOrder.BIG_ENDIAN)
        // Client version 03 03 (TLS 1.2 in ClientHello)
        hsHeader.putShort(0x0303.toShort())
        // 32 random bytes
        hsHeader.put(ByteArray(32) { it.toByte() })
        // Session ID length 32 + 32 bytes ID
        hsHeader.put(32.toByte())
        hsHeader.put(ByteArray(32) { 0x01.toByte() })
        // Cipher suites length 2 + 13 01 (TLS_AES_128_GCM_SHA256)
        hsHeader.putShort(2.toShort())
        hsHeader.putShort(0x1301.toShort())
        // Compression methods length 1 + 00
        hsHeader.put(1.toByte())
        hsHeader.put(0.toByte())
        // Extensions length 2
        hsHeader.putShort(extBytes.size.toShort())
        handshakeBody.write(hsHeader.array())
        handshakeBody.write(extBytes)

        val hsBytes = handshakeBody.toByteArray()

        // Handshake header: type 01 (ClientHello) + len (3 bytes)
        val handshakeRecord = ByteArrayOutputStream()
        val hsTypeLen = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        hsTypeLen.put(1.toByte())
        hsTypeLen.put((hsBytes.size shr 16).toByte())
        hsTypeLen.putShort((hsBytes.size and 0xFFFF).toShort())
        handshakeRecord.write(hsTypeLen.array())
        handshakeRecord.write(hsBytes)

        val recBytes = handshakeRecord.toByteArray()

        // TLS Record Header: 16 03 01 <len2>
        val record = ByteBuffer.allocate(5 + recBytes.size).order(ByteOrder.BIG_ENDIAN)
        record.put(0x16.toByte()) // Handshake
        record.putShort(0x0301.toShort()) // Record TLS 1.0 (03 01)
        record.putShort(recBytes.size.toShort())
        record.put(recBytes)

        return record.array()
    }
}
