package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsParserTest {

    @Test
    fun rfcExample_aRecord() {
        // Hex message from brief:
        // 123481800001000100000000076578616d706c6503636f6d0000010001c00c000100010000012c00045db8d822
        // gives exactly one answer example.com -> 93.184.216.34, TTL 300
        val hex = "123481800001000100000000076578616d706c6503636f6d0000010001c00c000100010000012c00045db8d822"
        val payload = hexToBytes(hex)

        val answers = DnsParser.answers(payload)
        assertEquals(1, answers.size)
        val ans = answers[0]
        assertEquals("example.com", ans.name)
        assertEquals(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())), ans.address)
        assertEquals(300L, ans.ttlSeconds)
    }

    @Test
    fun aaaaRecord() {
        val ipv6Bytes = InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946").address
        val rawAns = PacketBuilder.DnsRawAnswer(
            nameRef = PacketBuilder.NameRef.Literal("example.org"),
            type = 28, // AAAA
            ttl = 600L,
            rdata = ipv6Bytes
        )
        val payload = PacketBuilder.dnsResponse(
            id = 0x55aa,
            questions = listOf("example.org" to 28),
            answers = listOf(rawAns)
        )

        val answers = DnsParser.answers(payload)
        assertEquals(1, answers.size)
        assertEquals("example.org", answers[0].name)
        assertEquals(InetAddress.getByAddress(ipv6Bytes), answers[0].address)
        assertEquals(600L, answers[0].ttlSeconds)
    }

    @Test
    fun cnameChain() {
        // CNAME chain: www.example.com CNAME cdn.example.net then cdn.example.net A 1.2.3.4
        // answers returns one entry per address with name = the first name in the chain (www.example.com)
        // AND ALSO according to brief:
        // "yields the address with the QUESTION name AND the CNAME names recorded
        //  (answers returns one entry per address with name = the first name in the chain, which is the name the app asked for)"
        val qName = "www.example.com"
        val cnameTarget = "cdn.example.net"
        val ipTarget = byteArrayOf(1, 2, 3, 4)

        val ans1 = PacketBuilder.DnsRawAnswer(
            nameRef = PacketBuilder.NameRef.Literal(qName),
            type = 5, // CNAME
            ttl = 300L,
            rdata = PacketBuilder.encodeDnsName(cnameTarget)
        )
        val ans2 = PacketBuilder.DnsRawAnswer(
            nameRef = PacketBuilder.NameRef.Literal(cnameTarget),
            type = 1, // A
            ttl = 120L,
            rdata = ipTarget
        )

        val payload = PacketBuilder.dnsResponse(
            id = 0x1111,
            questions = listOf(qName to 1),
            answers = listOf(ans1, ans2)
        )

        val answers = DnsParser.answers(payload)
        assertEquals(1, answers.size)
        assertEquals(qName, answers[0].name)
        assertEquals(InetAddress.getByAddress(ipTarget), answers[0].address)
        assertEquals(120L, answers[0].ttlSeconds)
    }

    @Test
    fun pointerLoopAndPastTheEnd() {
        // Compression pointer loop: pointer pointing to itself
        // Header: 12 bytes
        // Ans 1 starts at 12: C0 0C (pointer to 12)
        val loopPayload = hexToBytes("123481800000000100000000c00c000100010000012c000401020304")
        val loopAnswers = DnsParser.answers(loopPayload)
        assertTrue(loopAnswers.isEmpty())

        // Pointer past end: C0 FF
        val pastEndPayload = hexToBytes("123481800000000100000000c0ff000100010000012c000401020304")
        val pastEndAnswers = DnsParser.answers(pastEndPayload)
        assertTrue(pastEndAnswers.isEmpty())
    }

    @Test
    fun malformedAndEdgeCases() {
        // Truncated message
        assertTrue(DnsParser.answers(byteArrayOf(0x12, 0x34)).isEmpty())

        // Zero answers
        val noAnswers = PacketBuilder.dnsResponse(id = 1, questions = listOf("example.com" to 1), answers = emptyList())
        assertTrue(DnsParser.answers(noAnswers).isEmpty())

        // Query (QR bit 0)
        val query = PacketBuilder.dnsResponse(id = 1, questions = listOf("example.com" to 1), answers = emptyList(), isResponse = false)
        assertTrue(DnsParser.answers(query).isEmpty())

        // RCODE != 0 (e.g. NXDOMAIN = 3)
        val nxdomain = PacketBuilder.dnsResponse(id = 1, rcode = 3)
        assertTrue(DnsParser.answers(nxdomain).isEmpty())

        // TTL 0 is returned as 0
        val rawAnsTtl0 = PacketBuilder.DnsRawAnswer(
            nameRef = PacketBuilder.NameRef.Literal("zero.com"),
            type = 1,
            ttl = 0L,
            rdata = byteArrayOf(1, 1, 1, 1)
        )
        val ttl0Payload = PacketBuilder.dnsResponse(id = 1, questions = listOf("zero.com" to 1), answers = listOf(rawAnsTtl0))
        val answers = DnsParser.answers(ttl0Payload)
        assertEquals(1, answers.size)
        assertEquals(0L, answers[0].ttlSeconds)
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
