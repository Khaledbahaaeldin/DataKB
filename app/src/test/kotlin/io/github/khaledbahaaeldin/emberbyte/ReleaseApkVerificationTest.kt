package io.github.khaledbahaaeldin.emberbyte

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseApkVerificationTest {

    internal fun resolveReleaseApk(
        candidates: List<File>,
        inReleaseVerification: Boolean,
    ): File? {
        val apk = candidates.firstOrNull { it.exists() }
        if (apk == null) {
            if (inReleaseVerification) {
                throw AssertionError("Release APK verification failed: expected release APK at ${candidates.firstOrNull()?.absolutePath} was not found.")
            } else {
                org.junit.Assume.assumeTrue("Release APK app-release-unsigned.apk not found; skipping in local unit test run", false)
                return null
            }
        }
        return apk
    }

    private fun findReleaseApk(): File {
        val candidates = listOf(
            File("build/outputs/apk/release/app-release-unsigned.apk"),
            File("app/build/outputs/apk/release/app-release-unsigned.apk"),
            File("../app/build/outputs/apk/release/app-release-unsigned.apk"),
        )
        return resolveReleaseApk(
            candidates = candidates,
            inReleaseVerification = System.getProperty("emberbyte.verifyReleaseApk") == "true",
        ) ?: throw AssertionError("Unreachable")
    }

    @Test(expected = AssertionError::class)
    fun release_verification_fails_loudly_when_apk_is_absent() {
        resolveReleaseApk(
            candidates = listOf(File("non_existent_path/app-release-unsigned.apk")),
            inReleaseVerification = true,
        )
    }

    @Test
    fun release_verification_skips_when_apk_is_absent_outside_verification_task() {
        var skipped = false
        try {
            resolveReleaseApk(
                candidates = listOf(File("non_existent_path/app-release-unsigned.apk")),
                inReleaseVerification = false,
            )
        } catch (e: org.junit.AssumptionViolatedException) {
            skipped = true
        }
        assertTrue("Expected AssumptionViolatedException when absent outside release verification", skipped)
    }

    @Test
    fun release_apk_exists_and_meets_contract_requirements() {
        val apkFile = findReleaseApk()
        assertTrue("Release APK must exist and be non-empty", apkFile.exists() && apkFile.length() > 0)

        ZipFile(apkFile).use { zip ->
            // 1. Unsigned: no META-INF/*.RSA, *.DSA, *.EC, *.SF
            val signatureEntries = zip.entries().asSequence()
                .map { it.name }
                .filter { name ->
                    val upper = name.uppercase()
                    upper.startsWith("META-INF/") && (
                        upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
                        upper.endsWith(".EC") || upper.endsWith(".SF")
                    )
                }
                .toList()
            assertTrue(
                "Release APK must be unsigned; found signature entries: $signatureEntries",
                signatureEntries.isEmpty()
            )

            // Check no APK Signing Block (v2/v3)
            assertFalse(
                "Release APK must not have APK Signing Block (v2/v3)",
                hasApkSigningBlock(apkFile)
            )

            // 2. Parse binary AndroidManifest.xml
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            assertNotNull("AndroidManifest.xml must exist in APK", manifestEntry)
            val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
            val manifest = parseBinaryManifest(manifestBytes)

            // 3. versionName == "1.0.0" and versionCode == 1
            assertEquals("versionName must be 1.0.0", "1.0.0", manifest.versionName)
            assertEquals("versionCode must be 1", 1, manifest.versionCode)

            // 4. android:debuggable absent or false
            assertFalse("android:debuggable must be absent or false", manifest.debuggable)

            // 5. allowBackup == false
            assertFalse("allowBackup must be false", manifest.allowBackup)

            // 6. No INTERNET or BIND_VPN_SERVICE permissions
            assertFalse(
                "Release APK must NOT declare INTERNET permission",
                manifest.permissions.contains("android.permission.INTERNET")
            )
            assertFalse(
                "Release APK must NOT declare BIND_VPN_SERVICE permission",
                manifest.permissions.any { it.contains("VPN", ignoreCase = true) }
            )

            // 7. Permission list matches documented list
            val expectedPermissions = setOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.POST_PROMOTED_NOTIFICATIONS",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.QUERY_ALL_PACKAGES",
                "android.permission.WAKE_LOCK",
                "io.github.khaledbahaaeldin.emberbyte.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            )
            assertEquals(
                "Release permissions must exactly match documented set",
                expectedPermissions,
                manifest.permissions
            )

            // 8. No classes from LensSpike, src/debug, or test libraries in dex
            val dexEntries = zip.entries().asSequence()
                .filter { it.name.endsWith(".dex") }
                .toList()
            assertTrue("Release APK must contain at least one dex file", dexEntries.isNotEmpty())

            val forbiddenClassPrefixes = listOf(
                "Lorg/junit/",
                "Lorg/robolectric/",
                "Landroidx/test/",
                "Landroidx/compose/ui/test/"
            )

            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val definedClasses = extractDefinedClassDescriptors(dexBytes)
                for (cls in definedClasses) {
                    assertFalse(
                        "Release APK must not define LensSpike class: $cls",
                        cls.contains("LensSpike")
                    )
                    assertFalse(
                        "Release APK must not contain debug classes: $cls",
                        cls.contains("/debug/")
                    )
                    for (forbidden in forbiddenClassPrefixes) {
                        assertFalse(
                            "Release APK must not contain test class from $forbidden: $cls",
                            cls.startsWith(forbidden)
                        )
                    }
                }
            }
        }
    }

    private fun extractDefinedClassDescriptors(bytes: ByteArray): List<String> {
        if (bytes.size < 0x70) return emptyList()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsSize = buffer.getInt(0x38)
        val stringIdsOff = buffer.getInt(0x3C)
        val typeIdsSize = buffer.getInt(0x40)
        val typeIdsOff = buffer.getInt(0x44)
        val classDefsSize = buffer.getInt(0x60)
        val classDefsOff = buffer.getInt(0x64)

        fun readString(idx: Int): String {
            if (idx < 0 || idx >= stringIdsSize) return ""
            val strDataOff = buffer.getInt(stringIdsOff + idx * 4)
            buffer.position(strDataOff)
            readUleb128(buffer)
            val start = buffer.position()
            while (buffer.hasRemaining() && buffer.get() != 0.toByte()) { }
            val end = buffer.position() - 1
            return String(bytes, start, end - start, Charsets.UTF_8)
        }

        fun readTypeDescriptor(typeIdx: Int): String {
            if (typeIdx < 0 || typeIdx >= typeIdsSize) return ""
            val descriptorIdx = buffer.getInt(typeIdsOff + typeIdx * 4)
            return readString(descriptorIdx)
        }

        val descriptors = ArrayList<String>(classDefsSize)
        for (i in 0 until classDefsSize) {
            val classDefOffset = classDefsOff + i * 32
            val classIdx = buffer.getInt(classDefOffset)
            val descriptor = readTypeDescriptor(classIdx)
            if (descriptor.isNotEmpty()) {
                descriptors.add(descriptor)
            }
        }
        return descriptors
    }

    private fun hasApkSigningBlock(file: File): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 32) return false
        // Search backwards for the End of Central Directory (EOCD) signature: 0x06054b50
        var eocdOffset = -1
        for (i in bytes.size - 22 downTo maxOf(0, bytes.size - 65557)) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x05.toByte() && bytes[i + 3] == 0x06.toByte()
            ) {
                eocdOffset = i
                break
            }
        }
        if (eocdOffset == -1) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val cdOffset = buffer.getInt(eocdOffset + 16)
        if (cdOffset < 16) return false
        // Magic string at cdOffset - 16: "APK Sig Block 42"
        val magic = String(bytes, cdOffset - 16, 16, Charsets.US_ASCII)
        return magic == "APK Sig Block 42"
    }

    data class ParsedManifest(
        val versionName: String?,
        val versionCode: Int?,
        val debuggable: Boolean,
        val allowBackup: Boolean,
        val permissions: Set<String>
    )

    private fun parseBinaryManifest(bytes: ByteArray): ParsedManifest {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buffer.int
        require(fileType == 0x00080003) { "Not an Android binary XML: $fileType" }
        buffer.int // fileSize

        var stringPool: List<String> = emptyList()
        var versionName: String? = null
        var versionCode: Int? = null
        var debuggable = false
        var allowBackup = true // default in Android if unspecified is true
        val permissions = mutableSetOf<String>()

        while (buffer.hasRemaining()) {
            val chunkPos = buffer.position()
            val chunkType = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int
            if (chunkSize <= 0) break

            when (chunkType) {
                0x0001 -> { // STRING_POOL
                    stringPool = parseStringPool(buffer, chunkPos, headerSize)
                    buffer.position(chunkPos + chunkSize)
                }
                0x0102 -> { // START_ELEMENT
                    buffer.position(chunkPos + 16) // lineNumber, comment
                    buffer.int // ns
                    val nameIdx = buffer.int
                    val tagName = stringPool.getOrNull(nameIdx)

                    buffer.short // attributeStart
                    val attrSize = buffer.short.toInt() and 0xFFFF
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    buffer.short // idIndex
                    buffer.short // classIndex
                    buffer.short // styleIndex

                    var currentAttrPos = chunkPos + headerSize + 20 // standard start of attributes
                    for (i in 0 until attrCount) {
                        buffer.position(currentAttrPos)
                        buffer.int // attrNs
                        val attrNameIdx = buffer.int
                        val attrRawValueIdx = buffer.int
                        buffer.short // typedValueSize
                        buffer.get() // res0
                        val dataType = buffer.get().toInt() and 0xFF
                        val data = buffer.int

                        val attrName = stringPool.getOrNull(attrNameIdx)
                        val attrStringVal = if (attrRawValueIdx in stringPool.indices) stringPool[attrRawValueIdx] else null

                        if (tagName == "manifest") {
                            if (attrName == "versionName") {
                                versionName = attrStringVal ?: data.toString()
                            } else if (attrName == "versionCode") {
                                versionCode = data
                            }
                        } else if (tagName == "application") {
                            if (attrName == "debuggable") {
                                debuggable = (dataType == 0x12 && data != 0)
                            } else if (attrName == "allowBackup") {
                                allowBackup = !(dataType == 0x12 && data == 0)
                            }
                        } else if (tagName == "uses-permission") {
                            if (attrName == "name") {
                                val perm = attrStringVal ?: stringPool.getOrNull(data)
                                if (perm != null) permissions.add(perm)
                            }
                        }

                        currentAttrPos += if (attrSize > 0) attrSize else 20
                    }
                    buffer.position(chunkPos + chunkSize)
                }
                else -> {
                    buffer.position(chunkPos + chunkSize)
                }
            }
        }

        return ParsedManifest(
            versionName = versionName,
            versionCode = versionCode,
            debuggable = debuggable,
            allowBackup = allowBackup,
            permissions = permissions
        )
    }

    private fun parseStringPool(buffer: ByteBuffer, chunkPos: Int, headerSize: Int): List<String> {
        buffer.position(chunkPos + 8)
        val stringCount = buffer.int
        buffer.int // styleCount
        val flags = buffer.int
        val isUtf8 = (flags and (1 shl 8)) != 0
        val stringsStart = buffer.int
        buffer.int // stylesStart

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.int
        }

        val strings = ArrayList<String>(stringCount)
        val stringsDataStart = chunkPos + stringsStart
        for (i in 0 until stringCount) {
            val strPos = stringsDataStart + offsets[i]
            buffer.position(strPos)
            if (isUtf8) {
                // Read 2 uleb128 (char len, byte len)
                readUleb128(buffer)
                val byteLen = readUleb128(buffer)
                val bytes = ByteArray(byteLen)
                buffer.get(bytes)
                strings.add(String(bytes, Charsets.UTF_8))
            } else {
                var len = buffer.short.toInt() and 0xFFFF
                if ((len and 0x8000) != 0) {
                    val nextLen = buffer.short.toInt() and 0xFFFF
                    len = ((len and 0x7FFF) shl 16) or nextLen
                }
                val chars = CharArray(len)
                for (c in 0 until len) {
                    chars[c] = buffer.short.toInt().toChar()
                }
                strings.add(String(chars))
            }
        }
        return strings
    }

    private fun readUleb128(buffer: ByteBuffer): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = buffer.get().toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }
}
