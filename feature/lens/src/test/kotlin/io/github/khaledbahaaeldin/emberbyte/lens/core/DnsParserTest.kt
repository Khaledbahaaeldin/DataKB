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

    @Test
    fun dnsNameOver255Bytes_dropped() {
        // Construct a DNS message with question section having total wire length > 255 bytes (e.g., 5 labels of 60 bytes = 300 bytes)
        val out = java.io.ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        hdr.putShort(0x1111.toShort())
        hdr.putShort(0x8180.toShort()) // response
        hdr.putShort(1.toShort()) // 1 question
        hdr.putShort(1.toShort()) // 1 answer
        hdr.putShort(0.toShort())
        hdr.putShort(0.toShort())
        out.write(hdr.array())

        // Question name: 5 labels of 60 bytes
        for (i in 0 until 5) {
            out.write(60)
            out.write("a".repeat(60).toByteArray(Charsets.US_ASCII))
        }
        out.write(0) // terminator
        val qTail = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        qTail.putShort(1.toShort()) // Type A
        qTail.putShort(1.toShort()) // Class IN
        out.write(qTail.array())

        // Answer record
        val ansHdr = ByteBuffer.allocate(2 + 2 + 2 + 4 + 2 + 4).order(ByteOrder.BIG_ENDIAN)
        ansHdr.putShort(0xC00C.toShort()) // Pointer to offset 12 (the oversized question name)
        ansHdr.putShort(1.toShort()) // Type A
        ansHdr.putShort(1.toShort()) // Class IN
        ansHdr.putInt(300) // TTL
        ansHdr.putShort(4.toShort()) // rdLength
        ansHdr.put(byteArrayOf(1, 2, 3, 4))
        out.write(ansHdr.array())

        val answers = DnsParser.answers(out.toByteArray())
        assertTrue("DNS names over 255 bytes must be dropped / return empty", answers.isEmpty())
    }

    @Test
    fun pointerJumpLimit_16_enforced() {
        // Construct a DNS message with a chain of compression pointers:
        // Header: 12 bytes
        // In DnsParser, when reading an answer name that starts with a pointer to offset 12,
        // that initial pointer is jump 1!
        // Each subsequent pointer at offset 12, 14, ... is an additional jump.
        // Total jumps = 1 (from answer pointer) + numInternalJumps.
        // For total 16 jumps: numInternalJumps = 15.
        // For total 17 jumps: numInternalJumps = 16.
        fun buildPointerChainPayload(totalJumps: Int): ByteArray {
            val numInternalJumps = totalJumps - 1
            val out = java.io.ByteArrayOutputStream()
            val hdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            hdr.putShort(0x1234.toShort())
            hdr.putShort(0x8180.toShort()) // standard response, no error
            hdr.putShort(0.toShort()) // 0 questions
            hdr.putShort(1.toShort()) // 1 answer
            hdr.putShort(0.toShort())
            hdr.putShort(0.toShort())
            out.write(hdr.array())

            // Structure of payload:
            // Header: bytes 0..11
            // Answer record: bytes 12..27
            //   Name: pointer to offset 28 (0xC01C) (Jump 1)
            //   Type: 1 (A)
            //   Class: 1 (IN)
            //   TTL: 300
            //   rdLength: 4
            //   rdata: 93.184.216.34
            // Pointer chain: bytes 28 onwards
            //   Chain has (totalJumps - 1) pointers.
            //   Pointer at offset 28 + 2*i points to 28 + 2*(i+1).
            // Terminal name at offset 28 + 2*(totalJumps - 1): "\x07example\x03com\x00"
            val chainStartOffset = 12 + 16 // 28
            val ansHdr = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            ansHdr.putShort((0xC000 or chainStartOffset).toShort()) // pointer to chain start (Jump 1)
            ansHdr.putShort(1.toShort()) // Type A
            ansHdr.putShort(1.toShort()) // Class IN
            ansHdr.putInt(300) // TTL
            ansHdr.putShort(4.toShort()) // rdLength
            ansHdr.put(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte()))
            out.write(ansHdr.array())

            for (i in 0 until numInternalJumps) {
                val nextOffset = chainStartOffset + 2 * (i + 1)
                val ptr = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                ptr.putShort((0xC000 or nextOffset).toShort())
                out.write(ptr.array())
            }

            val nameBytes = PacketBuilder.encodeDnsName("example.com")
            out.write(nameBytes)

            return out.toByteArray()
        }

        val chain16 = buildPointerChainPayload(16)
        val answers16 = DnsParser.answers(chain16)
        assertEquals("16 pointer jumps should be accepted", 1, answers16.size)
        assertEquals("example.com", answers16[0].name)

        val chain17 = buildPointerChainPayload(17)
        val answers17 = DnsParser.answers(chain17)
        assertTrue("17 pointer jumps must exceed the 16-pointer limit and be dropped", answers17.isEmpty())
    }

    @Test
    fun answerCap_64_answers_enforced() {
        // Construct a DNS message containing 80 answers
        val rawAnswers = (1..80).map { i ->
            PacketBuilder.DnsRawAnswer(
                nameRef = PacketBuilder.NameRef.Literal("host$i.example.com"),
                type = 1,
                ttl = 300L,
                rdata = byteArrayOf(10, 0, (i shr 8).toByte(), (i and 0xFF).toByte())
            )
        }
        val payload = PacketBuilder.dnsResponse(
            id = 0x2222,
            questions = listOf("host1.example.com" to 1),
            answers = rawAnswers
        )

        val answers = DnsParser.answers(payload)
        assertEquals("At most 64 answers must be parsed due to the 64-answer cap", 64, answers.size)
    }

    @Test
    fun fuzzTest_rawRandomByteArrays() {
        val rand = java.util.Random(42L)
        for (i in 0 until 5_000) {
            val len = rand.nextInt(300)
            val bytes = ByteArray(len)
            rand.nextBytes(bytes)
            // Must not throw any unhandled exceptions
            DnsParser.answers(bytes)
        }
    }

    @Test
    fun structureAwareFuzzing_20kMutations() {
        val rand = java.util.Random(54321L)

        // Seed valid DNS responses
        val resp1 = PacketBuilder.dnsResponse(
            id = 0x1234,
            questions = listOf("example.com" to 1),
            answers = listOf(
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("example.com"),
                    type = 1,
                    ttl = 300,
                    rdata = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())
                )
            )
        )
        val resp2 = PacketBuilder.dnsResponse(
            id = 0x5678,
            questions = listOf("ipv6.example.org" to 28),
            answers = listOf(
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("ipv6.example.org"),
                    type = 28,
                    ttl = 600,
                    rdata = ByteArray(16) { 0x20.toByte() }
                )
            )
        )
        val resp3 = PacketBuilder.dnsResponse(
            id = 0x9abc,
            questions = listOf("cname.example.com" to 1),
            answers = listOf(
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("cname.example.com"),
                    type = 5,
                    ttl = 120,
                    rdata = PacketBuilder.encodeDnsName("target.example.net")
                ),
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("target.example.net"),
                    type = 1,
                    ttl = 120,
                    rdata = byteArrayOf(1, 2, 3, 4)
                )
            )
        )

        val seedResponses = listOf(resp1, resp2, resp3)
        var nonNullCount = 0
        val iterations = 20_000

        for (i in 0 until iterations) {
            val base = seedResponses[i % seedResponses.size].clone()
            val mutationType = rand.nextInt(4)
            val mutatedBytes: ByteArray

            when (mutationType) {
                0 -> {
                    // Truncation at arbitrary length
                    val truncLen = rand.nextInt(base.size + 1)
                    mutatedBytes = base.copyOf(truncLen)
                }
                1 -> {
                    // Random bit / byte flips
                    val flips = rand.nextInt(3) + 1
                    for (f in 0 until flips) {
                        val idx = rand.nextInt(base.size)
                        base[idx] = (base[idx].toInt() xor (1 shl rand.nextInt(8))).toByte()
                    }
                    mutatedBytes = base
                }
                2 -> {
                    // Mutate ID or TTL without breaking record structure
                    if (base.size >= 12) {
                        base[0] = rand.nextInt(256).toByte() // ID
                        base[1] = rand.nextInt(256).toByte()
                    }
                    mutatedBytes = base
                }
                3 -> {
                    // Mutate RDATA bytes
                    val rdataOffset = base.size - 4
                    if (rdataOffset >= 12) {
                        base[rdataOffset] = rand.nextInt(256).toByte()
                    }
                    mutatedBytes = base
                }
                else -> mutatedBytes = base
            }

            // Must never throw
            val answers = DnsParser.answers(mutatedBytes)
            if (answers.isNotEmpty()) {
                nonNullCount++
                for (ans in answers) {
                    assertTrue("Domain name not empty", ans.name.isNotEmpty())
                    assertTrue("TTL >= 0", ans.ttlSeconds >= 0)
                }
            }
        }

        val nonNullRatio = nonNullCount.toDouble() / iterations
        assertTrue("At least 30% of mutated inputs must parse to non-empty result (was ${nonNullRatio * 100}%, $nonNullCount/$iterations)",
            nonNullRatio >= 0.30)
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
