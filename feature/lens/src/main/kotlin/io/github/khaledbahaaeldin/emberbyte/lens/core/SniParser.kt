package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

object SniParser {

    /**
     * Extracts the TLS Server Name Indication (SNI) host_name from the first TLS record.
     * Accepts only host_name entries made of [A-Za-z0-9.-_], lower-cases the result.
     * Returns null if not a ClientHello, malformed, truncated, or no SNI extension found.
     */
    fun serverName(firstRecord: ByteArray): String? {
        if (firstRecord.size < 5) return null

        val buf = ByteBuffer.wrap(firstRecord).order(ByteOrder.BIG_ENDIAN)

        // TLS Record Header:
        // contentType (1 byte), version (2 bytes), length (2 bytes)
        val contentType = buf.get().toInt() and 0xFF
        if (contentType != 0x16) return null // Must be Handshake (0x16)

        val recordVersion = buf.short.toInt() and 0xFFFF
        val recordLength = buf.short.toInt() and 0xFFFF
        if (recordLength > buf.remaining()) return null // Truncated or incomplete record

        // Inside Handshake record:
        // handshakeType (1 byte), length (3 bytes)
        if (buf.remaining() < 4) return null
        val handshakeType = buf.get().toInt() and 0xFF
        if (handshakeType != 0x01) return null // Must be ClientHello (0x01)

        val lenHigh = buf.get().toInt() and 0xFF
        val lenLow = buf.short.toInt() and 0xFFFF
        val handshakeLength = (lenHigh shl 16) or lenLow
        if (recordLength < 4 + handshakeLength || handshakeLength > buf.remaining()) return null // ClientHello split across records or truncated

        // ClientHello body:
        // clientVersion (2 bytes)
        // random (32 bytes)
        if (buf.remaining() < 34) return null
        buf.position(buf.position() + 34)

        // sessionID (1 byte length + length bytes)
        if (buf.remaining() < 1) return null
        val sessionLen = buf.get().toInt() and 0xFF
        if (buf.remaining() < sessionLen) return null
        buf.position(buf.position() + sessionLen)

        // cipherSuites (2 bytes length + length bytes)
        if (buf.remaining() < 2) return null
        val cipherSuitesLen = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < cipherSuitesLen) return null
        buf.position(buf.position() + cipherSuitesLen)

        // compressionMethods (1 byte length + length bytes)
        if (buf.remaining() < 1) return null
        val compressionLen = buf.get().toInt() and 0xFF
        if (buf.remaining() < compressionLen) return null
        buf.position(buf.position() + compressionLen)

        // extensions (2 bytes length + length bytes)
        if (buf.remaining() < 2) return null
        val extensionsLen = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < extensionsLen) return null

        val extensionsEnd = buf.position() + extensionsLen

        // Parse extensions list
        while (buf.position() + 4 <= extensionsEnd) {
            val extType = buf.short.toInt() and 0xFFFF
            val extDataLen = buf.short.toInt() and 0xFFFF
            if (buf.position() + extDataLen > extensionsEnd) return null

            val extEnd = buf.position() + extDataLen

            if (extType == 0) { // server_name extension (0x0000)
                // SNI extension data:
                // server_name_list_length (2 bytes)
                if (buf.remaining() < 2) return null
                val listLen = buf.short.toInt() and 0xFFFF
                val listEnd = buf.position() + listLen
                if (listEnd > extEnd) return null

                // ServerName entries:
                // name_type (1 byte), name_length (2 bytes), host_name (name_length bytes)
                while (buf.position() + 3 <= listEnd) {
                    val nameType = buf.get().toInt() and 0xFF
                    val nameLen = buf.short.toInt() and 0xFFFF
                    if (buf.position() + nameLen > listEnd) return null

                    if (nameType == 0) { // host_name
                        if (nameLen == 0 || nameLen > 255) return null
                        val nameBytes = ByteArray(nameLen)
                        buf.get(nameBytes)

                        // Check valid characters: [A-Za-z0-9.-_]
                        for (b in nameBytes) {
                            val c = b.toInt().toChar()
                            val isValid = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '.' || c == '-' || c == '_'
                            if (!isValid) return null
                        }

                        val nameStr = String(nameBytes, Charsets.US_ASCII)
                        return nameStr.lowercase()
                    } else {
                        // Skip non-host_name
                        buf.position(buf.position() + nameLen)
                    }
                }
            } else {
                buf.position(extEnd)
            }
        }

        return null
    }
}
