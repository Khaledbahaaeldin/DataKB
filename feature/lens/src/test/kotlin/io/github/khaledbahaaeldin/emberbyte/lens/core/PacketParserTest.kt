package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Random

class PacketParserTest {

    private val parser = PacketParser()

    @Test
    fun ipv4TcpSynWithMssOption() {
        val src = InetSocketAddress(InetAddress.getByName("192.168.1.10"), 49152)
        val dst = InetSocketAddress(InetAddress.getByName("93.184.216.34"), 443)
        val mss = 1460
        val mssOpt = PacketBuilder.buildMssOption(mss)

        val packet = PacketBuilder.ipv4Tcp(
            src = src,
            dst = dst,
            seq = 1000L,
            ack = 0L,
            flags = TcpFlags.SYN,
            window = 65535,
            options = mssOpt
        )

        val parsed = parser.parse(ByteBuffer.wrap(packet), packet.size)
        assertNotNull(parsed)
        assertTrue(parsed is ParsedPacket.Tcp)
        val tcp = parsed as ParsedPacket.Tcp

        assertEquals(src, tcp.src)
        assertEquals(dst, tcp.dst)
        assertEquals(6, tcp.protocol)
        assertEquals(1000L, tcp.seq)
        assertEquals(0L, tcp.ack)
        assertEquals(TcpFlags.SYN, tcp.flags)
        assertEquals(65535, tcp.window)
        assertEquals(1460, tcp.mss)
        assertEquals(packet.size, tcp.payloadOffset)
        assertEquals(0, tcp.payloadLength)
    }

    @Test
    fun ipv6TcpSegment() {
        val src = InetSocketAddress(InetAddress.getByName("2001:db8::1"), 51234)
        val dst = InetSocketAddress(InetAddress.getByName("2001:db8::2"), 80)
        val payload = "Hello TCP IPv6".toByteArray()

        val packet = PacketBuilder.ipv6Tcp(
            src = src,
            dst = dst,
            seq = 2000L,
            ack = 1000L,
            flags = TcpFlags.ACK or TcpFlags.PSH,
            window = 32768,
            payload = payload
        )

        val parsed = parser.parse(ByteBuffer.wrap(packet), packet.size)
        assertNotNull(parsed)
        assertTrue(parsed is ParsedPacket.Tcp)
        val tcp = parsed as ParsedPacket.Tcp

        assertEquals(src, tcp.src)
        assertEquals(dst, tcp.dst)
        assertEquals(6, tcp.protocol)
        assertEquals(2000L, tcp.seq)
        assertEquals(1000L, tcp.ack)
        assertEquals(TcpFlags.ACK or TcpFlags.PSH, tcp.flags)
        assertEquals(32768, tcp.window)
        assertNull(tcp.mss)
        assertEquals(payload.size, tcp.payloadLength)
        assertEquals(packet.size - payload.size, tcp.payloadOffset)
    }

    @Test
    fun udpPacket_ipv4AndIpv6() {
        val src4 = InetSocketAddress(InetAddress.getByName("10.0.0.2"), 5353)
        val dst4 = InetSocketAddress(InetAddress.getByName("10.0.0.1"), 53)
        val payload = "DNS Query".toByteArray()

        val packet4 = PacketBuilder.ipv4Udp(src = src4, dst = dst4, payload = payload)
        val parsed4 = parser.parse(ByteBuffer.wrap(packet4), packet4.size)
        assertNotNull(parsed4)
        assertTrue(parsed4 is ParsedPacket.Udp)
        val udp4 = parsed4 as ParsedPacket.Udp
        assertEquals(src4, udp4.src)
        assertEquals(dst4, udp4.dst)
        assertEquals(17, udp4.protocol)
        assertEquals(payload.size, udp4.payloadLength)
        assertEquals(28, udp4.payloadOffset) // 20 IP + 8 UDP

        val src6 = InetSocketAddress(InetAddress.getByName("fd00::2"), 5353)
        val dst6 = InetSocketAddress(InetAddress.getByName("fd00::1"), 53)
        val packet6 = PacketBuilder.ipv6Udp(src = src6, dst = dst6, payload = payload)
        val parsed6 = parser.parse(ByteBuffer.wrap(packet6), packet6.size)
        assertNotNull(parsed6)
        assertTrue(parsed6 is ParsedPacket.Udp)
        val udp6 = parsed6 as ParsedPacket.Udp
        assertEquals(src6, udp6.src)
        assertEquals(dst6, udp6.dst)
        assertEquals(17, udp6.protocol)
        assertEquals(payload.size, udp6.payloadLength)
        assertEquals(48, udp6.payloadOffset) // 40 IP + 8 UDP
    }

    @Test
    fun ipv4WithIpOptions_ihl6() {
        val src = InetSocketAddress(InetAddress.getByName("192.168.1.1"), 1234)
        val dst = InetSocketAddress(InetAddress.getByName("192.168.1.2"), 5678)
        val payload = "Payload with IP options".toByteArray()
        val ipOptions = byteArrayOf(1, 1, 1, 1) // 4 bytes of NOP options -> IHL = 6 (24 bytes)

        val packet = PacketBuilder.ipv4Tcp(
            src = src,
            dst = dst,
            seq = 1L,
            ack = 1L,
            flags = TcpFlags.ACK,
            payload = payload,
            options = ByteArray(0),
            ihl = 6
        )

        val parsed = parser.parse(ByteBuffer.wrap(packet), packet.size)
        assertNotNull(parsed)
        val tcp = parsed as ParsedPacket.Tcp
        // IP header is 24 bytes, TCP header is 20 bytes -> payload offset = 44
        assertEquals(44, tcp.payloadOffset)
        assertEquals(payload.size, tcp.payloadLength)
    }

    @Test
    fun tcpDataOffsetAbove5() {
        val src = InetSocketAddress(InetAddress.getByName("192.168.1.1"), 1234)
        val dst = InetSocketAddress(InetAddress.getByName("192.168.1.2"), 5678)
        val payload = "Payload with TCP options".toByteArray()
        val tcpOptions = byteArrayOf(1, 1, 1, 1) // NOPs -> TCP header becomes 24 bytes (dataOffset = 6)

        val packet = PacketBuilder.ipv4Tcp(
            src = src,
            dst = dst,
            seq = 1L,
            ack = 1L,
            flags = TcpFlags.ACK,
            payload = payload,
            options = tcpOptions
        )

        val parsed = parser.parse(ByteBuffer.wrap(packet), packet.size)
        assertNotNull(parsed)
        val tcp = parsed as ParsedPacket.Tcp
        assertEquals(20 + 24, tcp.payloadOffset) // 44
        assertEquals(payload.size, tcp.payloadLength)
    }

    @Test
    fun droppedPackets() {
        val src = InetSocketAddress(InetAddress.getByName("192.168.1.1"), 1234)
        val dst = InetSocketAddress(InetAddress.getByName("192.168.1.2"), 5678)

        // 1. Wrong IP version (e.g. 5)
        val pBadVer = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK)
        pBadVer[0] = 0x55.toByte()
        assertNull("Wrong IP version should be dropped", parser.parse(ByteBuffer.wrap(pBadVer), pBadVer.size))

        // 2. Length field larger than buffer
        val pLenLarger = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK)
        // Set total length to packet.size + 10
        val bigLen = pLenLarger.size + 10
        pLenLarger[2] = (bigLen shr 8).toByte()
        pLenLarger[3] = (bigLen and 0xFF).toByte()
        assertNull("Length larger than buffer should be dropped", parser.parse(ByteBuffer.wrap(pLenLarger), pLenLarger.size))

        // 3. Total length shorter than header
        val pLenShorter = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK)
        pLenShorter[2] = 0
        pLenShorter[3] = 10 // only 10 bytes, but IP header is 20
        assertNull("Total length shorter than header should be dropped", parser.parse(ByteBuffer.wrap(pLenShorter), pLenShorter.size))

        // 4. IPv4 header checksum wrong
        val pBadIpChk = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK, badIpChecksum = true)
        assertNull("Bad IP checksum should be dropped", parser.parse(ByteBuffer.wrap(pBadIpChk), pBadIpChk.size))

        // 5. TCP checksum wrong
        val pBadTcpChk = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK, badTcpChecksum = true)
        assertNull("Bad TCP checksum should be dropped", parser.parse(ByteBuffer.wrap(pBadTcpChk), pBadTcpChk.size))

        // 6. UDP checksum wrong
        val pBadUdpChk = PacketBuilder.ipv4Udp(src, dst, badUdpChecksum = true)
        assertNull("Bad UDP checksum should be dropped", parser.parse(ByteBuffer.wrap(pBadUdpChk), pBadUdpChk.size))

        // 7. UDP checksum 0 on IPv4 is ALLOWED (means none)
        val pZeroUdpChk = PacketBuilder.ipv4Udp(src, dst, zeroUdpChecksum = true)
        assertNotNull("UDP checksum 0 on IPv4 is allowed", parser.parse(ByteBuffer.wrap(pZeroUdpChk), pZeroUdpChk.size))

        // 8. Fragmented IPv4 (MF flag or fragment offset non-zero)
        val pMf = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK, flagsAndFragOffset = 0x2000) // MF = 1
        assertNull("MF flag set should be dropped", parser.parse(ByteBuffer.wrap(pMf), pMf.size))

        val pFragOff = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK, flagsAndFragOffset = 0x0001) // Offset > 0
        assertNull("Frag offset > 0 should be dropped", parser.parse(ByteBuffer.wrap(pFragOff), pFragOff.size))

        // 9. Protocol ICMP (protocol 1)
        val pIcmp = PacketBuilder.ipv4Udp(src, dst)
        pIcmp[9] = 1.toByte() // ICMP
        // recompute IP checksum
        pIcmp[10] = 0
        pIcmp[11] = 0
        val c = Checksums.ipv4Header(pIcmp, 0, 20)
        pIcmp[10] = (c shr 8).toByte()
        pIcmp[11] = c.toByte()
        assertNull("ICMP protocol should be dropped", parser.parse(ByteBuffer.wrap(pIcmp), pIcmp.size))

        // 10. Truncated TCP/UDP header
        val pTrunc = PacketBuilder.ipv4Tcp(src, dst, 1, 1, TcpFlags.ACK)
        assertNull("Truncated buffer should be dropped", parser.parse(ByteBuffer.wrap(pTrunc), 25)) // only 5 bytes of TCP

        // 11. IPv6 extension headers: hop-by-hop (0), routing (43), dest opts (60) are skipped; fragment (44) is dropped
        val src6 = InetSocketAddress(InetAddress.getByName("2001:db8::1"), 1234)
        val dst6 = InetSocketAddress(InetAddress.getByName("2001:db8::2"), 5678)

        // Hop-by-hop (0) extension header of 8 bytes (data = 6 bytes)
        val hopByHopExt = 0 to ByteArray(6)
        val pSkipExt = PacketBuilder.ipv6Tcp(src6, dst6, 1, 1, TcpFlags.ACK, extensionHeaders = listOf(hopByHopExt))
        assertNotNull("IPv6 hop-by-hop extension header should be skipped", parser.parse(ByteBuffer.wrap(pSkipExt), pSkipExt.size))

        // Fragment extension header (44)
        val fragExt = 44 to ByteArray(6)
        val pFragExt = PacketBuilder.ipv6Tcp(src6, dst6, 1, 1, TcpFlags.ACK, extensionHeaders = listOf(fragExt))
        assertNull("IPv6 fragment extension header should be dropped", parser.parse(ByteBuffer.wrap(pFragExt), pFragExt.size))

        // 12. Empty and 1-byte buffers give null without exception
        assertNull(parser.parse(ByteBuffer.allocate(0), 0))
        assertNull(parser.parse(ByteBuffer.wrap(byteArrayOf(0x45)), 1))
    }

    @Test
    fun fuzzTest_20kRandomByteArrays() {
        val rand = Random(42L)
        for (i in 0 until 20_000) {
            val len = rand.nextInt(200)
            val bytes = ByteArray(len)
            rand.nextBytes(bytes)
            val buf = ByteBuffer.wrap(bytes)
            val parsed = parser.parse(buf, len)
            if (parsed != null) {
                assertTrue("payloadOffset >= 0", parsed.payloadOffset >= 0)
                assertTrue("payloadLength >= 0", parsed.payloadLength >= 0)
                assertTrue("payload inside buffer", parsed.payloadOffset + parsed.payloadLength <= len)
            }
        }
    }
}
