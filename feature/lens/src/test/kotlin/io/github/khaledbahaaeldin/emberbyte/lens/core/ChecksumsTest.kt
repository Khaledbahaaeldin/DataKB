package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChecksumsTest {

    @Test
    fun ipv4Header_rfc1071Example() {
        // From RFC 1071 / brief:
        // 45 00 00 73 00 00 40 00 40 11 b8 61 c0 a8 00 01 c0 a8 00 c7
        // has checksum field 0xb861 and ipv4Header over it with checksum zeroed returns 0xb861
        val hex = "450000730000400040110000c0a80001c0a800c7"
        val bytes = hexToBytes(hex)
        val computed = Checksums.ipv4Header(bytes, 0, bytes.size)
        assertEquals(0xb861, computed)

        // When computed over the original header containing 0xb861, the 1's complement sum is 0 (or 0xffff inverted to 0)
        val origBytes = hexToBytes("45000073000040004011b861c0a80001c0a800c7")
        val verify = Checksums.ipv4Header(origBytes, 0, origBytes.size)
        assertEquals(0, verify)
    }

    @Test
    fun transport_oddLengthPayload() {
        val src = InetSocketAddress(InetAddress.getByName("192.168.1.1"), 12345)
        val dst = InetSocketAddress(InetAddress.getByName("192.168.1.2"), 80)
        val oddPayload = byteArrayOf(1, 2, 3, 4, 5) // 5 bytes

        val packet = PacketBuilder.ipv4Tcp(
            src = src,
            dst = dst,
            seq = 100L,
            ack = 200L,
            flags = TcpFlags.ACK,
            payload = oddPayload
        )

        // Verify checksum in packet is valid when checked over the packet
        val check = Checksums.transport(packet, 0, false, 6, 20, packet.size - 20)
        assertEquals(0, check)
    }

    @Test
    fun transport_ipv6PseudoHeader() {
        val src = InetSocketAddress(InetAddress.getByName("2001:db8::1"), 54321)
        val dst = InetSocketAddress(InetAddress.getByName("2001:db8::2"), 443)
        val payload = byteArrayOf(10, 20, 30, 40)

        val packet = PacketBuilder.ipv6Tcp(
            src = src,
            dst = dst,
            seq = 500L,
            ack = 600L,
            flags = TcpFlags.ACK or TcpFlags.PSH,
            payload = payload
        )

        val check = Checksums.transport(packet, 0, true, 6, 40, packet.size - 40)
        assertEquals(0, check)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val len = clean.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
        }
        return data
    }
}
