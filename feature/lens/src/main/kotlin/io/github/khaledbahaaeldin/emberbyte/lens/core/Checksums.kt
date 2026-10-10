package io.github.khaledbahaaeldin.emberbyte.lens.core

object Checksums {

    /**
     * Calculates the standard 16-bit Internet Checksum (RFC 1071) over an IPv4 header.
     * @param buf byte buffer containing the header
     * @param off offset where the header starts
     * @param headerLen length of the header in bytes
     * @return the 16-bit one's complement sum
     */
    fun ipv4Header(buf: ByteArray, off: Int, headerLen: Int): Int {
        var sum = 0L
        var i = off
        val end = off + headerLen
        while (i < end) {
            val b1 = buf[i].toInt() and 0xFF
            val b2 = if (i + 1 < end) buf[i + 1].toInt() and 0xFF else 0
            val word = (b1 shl 8) or b2
            sum += word
            i += 2
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.toInt().inv()) and 0xFFFF
    }

    /**
     * Calculates the transport layer (TCP or UDP) checksum using the appropriate pseudo-header.
     * @param buf byte buffer containing the packet
     * @param ipOff offset where the IP header starts
     * @param ipv6 true if IPv6, false if IPv4
     * @param protocol 6 for TCP, 17 for UDP
     * @param segOff offset where the transport segment (header + payload) starts
     * @param segLen length of the transport segment
     * @return the 16-bit one's complement sum
     */
    fun transport(buf: ByteArray, ipOff: Int, ipv6: Boolean, protocol: Int, segOff: Int, segLen: Int): Int {
        var sum = 0L

        if (!ipv6) {
            // IPv4 Pseudo-header:
            // Source IP (4 bytes, offset ipOff + 12)
            // Dest IP (4 bytes, offset ipOff + 16)
            // Zero (1 byte) + Protocol (1 byte)
            // TCP/UDP Length (2 bytes)
            val srcIpOff = ipOff + 12
            val dstIpOff = ipOff + 16

            // Src IP
            sum += ((buf[srcIpOff].toInt() and 0xFF) shl 8) or (buf[srcIpOff + 1].toInt() and 0xFF)
            sum += ((buf[srcIpOff + 2].toInt() and 0xFF) shl 8) or (buf[srcIpOff + 3].toInt() and 0xFF)
            // Dst IP
            sum += ((buf[dstIpOff].toInt() and 0xFF) shl 8) or (buf[dstIpOff + 1].toInt() and 0xFF)
            sum += ((buf[dstIpOff + 2].toInt() and 0xFF) shl 8) or (buf[dstIpOff + 3].toInt() and 0xFF)
            // Protocol
            sum += protocol and 0xFFFF
            // Length
            sum += segLen and 0xFFFF
        } else {
            // IPv6 Pseudo-header:
            // Source IP (16 bytes, offset ipOff + 8)
            // Dest IP (16 bytes, offset ipOff + 24)
            // Upper-Layer Packet Length (4 bytes, 32-bit int)
            // Zero (3 bytes) + Next Header (1 byte)
            val srcIpOff = ipOff + 8
            val dstIpOff = ipOff + 24

            for (k in 0 until 16 step 2) {
                sum += ((buf[srcIpOff + k].toInt() and 0xFF) shl 8) or (buf[srcIpOff + k + 1].toInt() and 0xFF)
            }
            for (k in 0 until 16 step 2) {
                sum += ((buf[dstIpOff + k].toInt() and 0xFF) shl 8) or (buf[dstIpOff + k + 1].toInt() and 0xFF)
            }
            // 32-bit Upper-Layer Packet Length
            sum += (segLen ushr 16) and 0xFFFF
            sum += segLen and 0xFFFF
            // Next Header
            sum += protocol and 0xFFFF
        }

        // Now sum the transport segment
        var i = segOff
        val end = segOff + segLen
        while (i < end) {
            val b1 = buf[i].toInt() and 0xFF
            val b2 = if (i + 1 < end) buf[i + 1].toInt() and 0xFF else 0
            val word = (b1 shl 8) or b2
            sum += word
            i += 2
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.toInt().inv()) and 0xFFFF
        // In UDP, RFC 768: if the computed checksum is zero, it is transmitted as all ones (0xFFFF).
        return result
    }
}
