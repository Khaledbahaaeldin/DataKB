package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsAnswer(val name: String, val address: InetAddress, val ttlSeconds: Long)

object DnsParser {

    private const val MAX_ANSWERS = 64
    private const val MAX_POINTER_JUMPS = 16

    fun answers(payload: ByteArray): List<DnsAnswer> {
        if (payload.size < 12) return emptyList()

        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val id = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF

        val qr = (flags and 0x8000) != 0
        if (!qr) return emptyList() // Not a response

        val rcode = flags and 0x000F
        if (rcode != 0) return emptyList() // Non-zero RCODE

        val qdCount = buf.short.toInt() and 0xFFFF
        val anCount = buf.short.toInt() and 0xFFFF
        val nsCount = buf.short.toInt() and 0xFFFF
        val arCount = buf.short.toInt() and 0xFFFF

        if (anCount == 0) return emptyList()

        // Read questions
        val questionNames = mutableListOf<String>()
        try {
            for (i in 0 until qdCount) {
                val qName = readDomainName(payload, buf) ?: return emptyList()
                if (buf.remaining() < 4) return emptyList()
                val qType = buf.short.toInt() and 0xFFFF
                val qClass = buf.short.toInt() and 0xFFFF
                questionNames.add(qName)
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val primaryQuestionName = questionNames.firstOrNull()

        // Read answers
        val cnameMap = mutableMapOf<String, String>() // alias -> canonical
        val directAnswers = mutableListOf<RawDnsRecord>()

        try {
            val countToRead = minOf(anCount, MAX_ANSWERS)
            for (i in 0 until countToRead) {
                if (buf.remaining() < 2) return emptyList()
                val aName = readDomainName(payload, buf) ?: return emptyList()
                if (buf.remaining() < 10) return emptyList()
                val type = buf.short.toInt() and 0xFFFF
                val clazz = buf.short.toInt() and 0xFFFF
                val ttl = buf.int.toLong() and 0xFFFFFFFFL
                val rdLength = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < rdLength) return emptyList()

                val rdataStart = buf.position()

                when (type) {
                    1 -> { // A
                        if (rdLength == 4) {
                            val ip = ByteArray(4)
                            buf.get(ip)
                            val addr = InetAddress.getByAddress(ip)
                            directAnswers.add(RawDnsRecord(aName, addr, ttl))
                        } else {
                            buf.position(rdataStart + rdLength)
                        }
                    }
                    28 -> { // AAAA
                        if (rdLength == 16) {
                            val ip = ByteArray(16)
                            buf.get(ip)
                            val addr = InetAddress.getByAddress(ip)
                            directAnswers.add(RawDnsRecord(aName, addr, ttl))
                        } else {
                            buf.position(rdataStart + rdLength)
                        }
                    }
                    5 -> { // CNAME
                        val cnameTarget = readDomainName(payload, buf)
                        if (cnameTarget != null) {
                            cnameMap[aName.lowercase()] = cnameTarget.lowercase()
                        }
                        buf.position(rdataStart + rdLength)
                    }
                    else -> {
                        buf.position(rdataStart + rdLength)
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val result = mutableListOf<DnsAnswer>()
        for (rec in directAnswers) {
            // Find the origin name in the CNAME chain
            // If primaryQuestionName resolved via CNAME to rec.name, use primaryQuestionName!
            val resolvedName = findChainRoot(rec.name, cnameMap, primaryQuestionName)
            result.add(DnsAnswer(resolvedName, rec.address, rec.ttl))
        }

        return result
    }

    private fun findChainRoot(
        leafName: String,
        cnameMap: Map<String, String>,
        primaryQuestionName: String?
    ): String {
        // If primaryQuestionName is present and leads to leafName through cnames, return primaryQuestionName
        if (primaryQuestionName != null) {
            var curr = primaryQuestionName.lowercase()
            val seen = mutableSetOf<String>()
            while (seen.add(curr)) {
                if (curr.equals(leafName, ignoreCase = true)) {
                    return primaryQuestionName
                }
                curr = cnameMap[curr] ?: break
            }
        }

        // Otherwise check reverse chain: if some name alias points to leafName, trace backwards if possible,
        // or return leafName
        // Contract: "answers returns one entry per address with name = the first name in the chain,
        // which is the name the app asked for"
        // If primaryQuestionName was set, we checked above.
        // What if primaryQuestionName was not the one or null, check if any key leads to leafName:
        for ((alias, canonical) in cnameMap) {
            var curr = canonical
            val seen = mutableSetOf(alias)
            while (seen.add(curr)) {
                if (curr.equals(leafName, ignoreCase = true)) {
                    return alias
                }
                curr = cnameMap[curr] ?: break
            }
        }

        return leafName
    }

    private data class RawDnsRecord(val name: String, val address: InetAddress, val ttl: Long)

    private fun readDomainName(payload: ByteArray, buf: ByteBuffer): String? {
        val parts = mutableListOf<String>()
        var jumped = false
        var jumpsCount = 0
        var totalLength = 0
        var currentPos = buf.position()

        while (true) {
            if (currentPos >= payload.size) return null
            val len = payload[currentPos].toInt() and 0xFF

            if (len == 0) {
                if (!jumped) {
                    buf.position(currentPos + 1)
                }
                break
            }

            if ((len and 0xC0) == 0xC0) {
                // Compression pointer
                if (currentPos + 1 >= payload.size) return null
                val pointerOffset = ((len and 0x3F) shl 8) or (payload[currentPos + 1].toInt() and 0xFF)
                if (pointerOffset >= payload.size) return null
                if (!jumped) {
                    buf.position(currentPos + 2)
                    jumped = true
                }
                jumpsCount++
                if (jumpsCount > MAX_POINTER_JUMPS) return null // Loop detected or exceeded max jumps
                currentPos = pointerOffset
            } else if ((len and 0xC0) == 0) {
                // Label
                currentPos++
                if (currentPos + len > payload.size) return null
                totalLength += len + 1
                if (totalLength > 255) return null // RFC name length exceeded

                val labelBytes = ByteArray(len)
                System.arraycopy(payload, currentPos, labelBytes, 0, len)
                parts.add(String(labelBytes, Charsets.US_ASCII))
                currentPos += len
                if (!jumped) {
                    buf.position(currentPos)
                }
            } else {
                // Reserved 0x40 or 0x80 label types
                return null
            }
        }

        return parts.joinToString(".")
    }
}
