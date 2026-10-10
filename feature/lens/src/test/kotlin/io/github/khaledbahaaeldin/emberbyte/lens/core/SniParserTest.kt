package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class SniParserTest {

    @Test
    fun standardClientHello_extractsServerName() {
        val record = PacketBuilder.clientHello("example.com")
        val sni = SniParser.serverName(record)
        assertEquals("example.com", sni)
    }

    @Test
    fun greaseExtensions_andOrderTolerant() {
        // GREASE extension: 0x0a0a with some dummy data
        val grease1 = 0x0a0a to byteArrayOf(0, 0)
        val grease2 = 0x1a1a to byteArrayOf(1, 2, 3)
        val alpnExt = 0x0010 to byteArrayOf(0, 2, 'h'.code.toByte(), '2'.code.toByte())

        // Place GREASE before and after, SNI not first
        val nameBytes = "my-site.org".toByteArray(Charsets.US_ASCII)
        val sniData = ByteBuffer.allocate(2 + 1 + 2 + nameBytes.size).order(ByteOrder.BIG_ENDIAN)
        sniData.putShort((nameBytes.size + 3).toShort())
        sniData.put(0.toByte()) // host_name
        sniData.putShort(nameBytes.size.toShort())
        sniData.put(nameBytes)
        val sniExt = 0x0000 to sniData.array()

        val extensions = listOf(grease1, alpnExt, sniExt, grease2)
        val record = PacketBuilder.clientHello(serverName = null, extensions = extensions)

        val sni = SniParser.serverName(record)
        assertEquals("my-site.org", sni)
    }

    @Test
    fun upperCaseInput_returnsLowerCase() {
        val record = PacketBuilder.clientHello("WwW.ExAmPlE.CoM")
        val sni = SniParser.serverName(record)
        assertEquals("www.example.com", sni)
    }

    @Test
    fun multipleNamesInList_firstHostNameWins() {
        // Build SNI extension with two entries
        val name1 = "first.com".toByteArray(Charsets.US_ASCII)
        val name2 = "second.com".toByteArray(Charsets.US_ASCII)

        val sniData = ByteBuffer.allocate(2 + (3 + name1.size) + (3 + name2.size)).order(ByteOrder.BIG_ENDIAN)
        sniData.putShort(((3 + name1.size) + (3 + name2.size)).toShort())
        // entry 1: host_name type 0
        sniData.put(0.toByte())
        sniData.putShort(name1.size.toShort())
        sniData.put(name1)
        // entry 2: host_name type 0
        sniData.put(0.toByte())
        sniData.putShort(name2.size.toShort())
        sniData.put(name2)

        val record = PacketBuilder.clientHello(serverName = null, extensions = listOf(0 to sniData.array()))
        val sni = SniParser.serverName(record)
        assertEquals("first.com", sni)
    }

    @Test
    fun malformedOrNoSni_returnsNull() {
        // No SNI extension
        val noSni = PacketBuilder.clientHello(serverName = null, extensions = listOf(0x0010 to byteArrayOf(1, 2)))
        assertNull(SniParser.serverName(noSni))

        // Truncated record
        val full = PacketBuilder.clientHello("example.com")
        assertNull(SniParser.serverName(full.copyOf(10)))

        // Record that is not handshake (e.g. 0x17 application data)
        val notHs = full.clone()
        notHs[0] = 0x17
        assertNull(SniParser.serverName(notHs))

        // Handshake type not ClientHello (e.g. 0x02 ServerHello)
        val notClientHello = full.clone()
        notClientHello[5] = 0x02
        assertNull(SniParser.serverName(notClientHello))

        // SNI containing non-ASCII bytes or spaces
        val invalidNameRecord = PacketBuilder.clientHello("invalid name.com")
        assertNull(SniParser.serverName(invalidNameRecord))

        // SNI longer than 255
        val longName = "a".repeat(256) + ".com"
        val longRecord = PacketBuilder.clientHello(longName)
        assertNull(SniParser.serverName(longRecord))

        // ClientHello split across records (record len < handshake len)
        val splitRecord = full.clone()
        // Record len at index 3..4
        splitRecord[3] = 0
        splitRecord[4] = 20 // shorter than actual handshake length
        assertNull(SniParser.serverName(splitRecord))
    }

    @Test
    fun fuzzTest_5kRandomByteArrays_doesNotThrow() {
        val rand = Random(12345L)
        for (i in 0 until 5_000) {
            val len = rand.nextInt(300)
            val bytes = ByteArray(len)
            rand.nextBytes(bytes)
            // Must not throw
            SniParser.serverName(bytes)
        }
    }
}
