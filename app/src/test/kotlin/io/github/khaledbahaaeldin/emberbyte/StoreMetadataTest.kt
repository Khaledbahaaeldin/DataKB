package io.github.khaledbahaaeldin.emberbyte

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMetadataTest {

    private fun findRepoRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File("."),
            File(".."),
            File(userDir),
            File(userDir, "..")
        )
        return candidates.firstOrNull { File(it, "gradlew").exists() }
            ?: candidates.firstOrNull { File(it, "fastlane/metadata/android/en-US").exists() }
            ?: File(".")
    }

    private fun findMetadataDir(): File {
        return File(findRepoRoot(), "fastlane/metadata/android/en-US")
    }

    private fun checkNoBom(file: File) {
        val bytes = file.readBytes()
        if (bytes.size >= 3) {
            val hasBom = (bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            assertFalse("${file.name} must not contain a UTF-8 BOM", hasBom)
        }
    }

    private fun getPngDimensions(file: File): Pair<Int, Int> {
        file.inputStream().use { stream ->
            val header = ByteArray(8)
            val readHeader = stream.read(header)
            assertEquals("File too short for PNG: ${file.name}", 8, readHeader)
            val expectedPngHeader = byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
            )
            assertTrue("Invalid PNG magic for ${file.name}", header.contentEquals(expectedPngHeader))

            val ihdrLengthBytes = ByteArray(4)
            assertEquals("File too short for IHDR length: ${file.name}", 4, stream.read(ihdrLengthBytes))

            val ihdrChunkType = ByteArray(4)
            assertEquals("File too short for IHDR chunk type: ${file.name}", 4, stream.read(ihdrChunkType))
            assertEquals("First chunk must be IHDR: ${file.name}", "IHDR", String(ihdrChunkType, Charsets.US_ASCII))

            val widthBytes = ByteArray(4)
            val heightBytes = ByteArray(4)
            assertEquals("File too short for width: ${file.name}", 4, stream.read(widthBytes))
            assertEquals("File too short for height: ${file.name}", 4, stream.read(heightBytes))

            val width = ByteBuffer.wrap(widthBytes).order(ByteOrder.BIG_ENDIAN).int
            val height = ByteBuffer.wrap(heightBytes).order(ByteOrder.BIG_ENDIAN).int
            return Pair(width, height)
        }
    }

    @Test
    fun title_txt_meets_store_contract() {
        val titleFile = File(findMetadataDir(), "title.txt")
        assertTrue("title.txt must exist at ${titleFile.absolutePath}", titleFile.exists())
        checkNoBom(titleFile)
        val title = titleFile.readText(Charsets.UTF_8).trim()
        assertEquals("Title must be 'Emberbyte: Data Monitor'", "Emberbyte: Data Monitor", title)
        assertTrue("Title must be at most 30 characters", title.length <= 30)
    }

    @Test
    fun short_description_txt_meets_store_contract() {
        val shortDescFile = File(findMetadataDir(), "short_description.txt")
        assertTrue("short_description.txt must exist at ${shortDescFile.absolutePath}", shortDescFile.exists())
        checkNoBom(shortDescFile)
        val rawText = shortDescFile.readText(Charsets.UTF_8)
        val shortDesc = rawText.trim()
        assertTrue("short_description must be at most 80 characters (found ${shortDesc.length})", shortDesc.length <= 80)
        assertFalse("short_description must be single line", shortDesc.contains("\n") || shortDesc.contains("\r"))
        assertFalse("short_description must not have trailing period", shortDesc.endsWith("."))
    }

    @Test
    fun full_description_txt_meets_store_contract() {
        val fullDescFile = File(findMetadataDir(), "full_description.txt")
        assertTrue("full_description.txt must exist at ${fullDescFile.absolutePath}", fullDescFile.exists())
        checkNoBom(fullDescFile)
        val fullDesc = fullDescFile.readText(Charsets.UTF_8)
        assertTrue("full_description must be at most 4000 characters (found ${fullDesc.length})", fullDesc.length <= 4000)

        val lower = fullDesc.lowercase()

        // Mentions nothing leaves the device
        val mentionsNothingLeaves = lower.contains("nothing leaves") ||
            lower.contains("leaves the device") ||
            lower.contains("stays on your device") ||
            lower.contains("stays strictly on your device")
        assertTrue("full_description must mention that nothing leaves the device", mentionsNothingLeaves)

        // Mentions reasons for permissions
        val mentionsUsageStats = lower.contains("usage access") || lower.contains("usage stats") || lower.contains("package_usage_stats")
        assertTrue("full_description must explain usage stats permission", mentionsUsageStats)

        val mentionsPackageList = lower.contains("package") || lower.contains("installed app") || lower.contains("query_all_packages")
        assertTrue("full_description must explain package list permission", mentionsPackageList)

        val mentionsPhoneState = lower.contains("phone state") || lower.contains("read_phone_state") || lower.contains("sim")
        assertTrue("full_description must explain phone state / SIM permission", mentionsPhoneState)

        val mentionsNotifications = lower.contains("notification") || lower.contains("post_notifications")
        assertTrue("full_description must explain notifications permission", mentionsNotifications)

        // Does NOT mention VPN, Lens, cloud, or account as features
        assertFalse("full_description must not mention Lens", lower.contains("lens"))

        val featuresSection = if (fullDesc.contains("Features:")) {
            fullDesc.substringAfter("Features:").substringBefore("\n\n")
        } else {
            fullDesc
        }
        val featureLines = featuresSection.lines().filter { line ->
            val l = line.trim().lowercase()
            l.startsWith("-") || l.startsWith("•") || l.startsWith("*")
        }
        for (line in featureLines) {
            val l = line.lowercase()
            assertFalse("Feature line '$line' must not list VPN as a feature", l.contains("vpn"))
            assertFalse("Feature line '$line' must not list cloud as a feature", l.contains("cloud"))
            assertFalse("Feature line '$line' must not list account as a feature", l.contains("account"))
        }

        assertFalse("Must not advertise VPN service", lower.contains("vpn service"))
        assertFalse("Must not advertise cloud storage/sync", lower.contains("cloud sync") || lower.contains("cloud storage") || lower.contains("cloud backup"))
        assertFalse("Must not advertise user accounts", lower.contains("create an account") || lower.contains("user account") || lower.contains("sign in") || lower.contains("log in"))
    }

    @Test
    fun changelog_1_meets_store_contract() {
        val changelogFile = File(findMetadataDir(), "changelogs/1.txt")
        assertTrue("changelogs/1.txt must exist at ${changelogFile.absolutePath}", changelogFile.exists())
        checkNoBom(changelogFile)
        val bytes = changelogFile.readBytes()
        assertTrue("changelogs/1.txt must be non-empty", bytes.isNotEmpty())
        assertTrue("changelogs/1.txt must be at most 500 bytes UTF-8 (found ${bytes.size})", bytes.size <= 500)
    }

    @Test
    fun icon_png_meets_store_contract() {
        val iconFile = File(findMetadataDir(), "images/icon.png")
        assertTrue("images/icon.png must exist at ${iconFile.absolutePath}", iconFile.exists())
        val (w, h) = getPngDimensions(iconFile)
        assertEquals("Icon width must be 512", 512, w)
        assertEquals("Icon height must be 512", 512, h)
    }

    @Test
    fun phone_screenshots_meet_store_contract() {
        val screenshotsDir = File(findMetadataDir(), "images/phoneScreenshots")
        assertTrue("phoneScreenshots directory must exist at ${screenshotsDir.absolutePath}", screenshotsDir.exists() && screenshotsDir.isDirectory)
        val screenshots = screenshotsDir.listFiles { file -> file.extension.lowercase() == "png" }?.sortedBy { it.name } ?: emptyList()
        assertTrue("Must have at least 4 phone screenshots (found ${screenshots.size})", screenshots.size >= 4)

        val dimensions = screenshots.map { file ->
            val (w, h) = getPngDimensions(file)
            assertTrue("Screenshot ${file.name} short side must be at least 320px (found ${minOf(w, h)})", minOf(w, h) >= 320)
            val ratio = minOf(w, h).toDouble() / maxOf(w, h).toDouble()
            // 1:2.4 is ~0.4167, 1:1.6 is 0.625
            assertTrue(
                "Screenshot ${file.name} aspect ratio ($ratio) must be between 1:2.4 and 1:1.6",
                ratio in (1.0 / 2.4)..(1.0 / 1.6)
            )
            Pair(w, h)
        }

        // All screenshots must have matching aspect ratio
        val firstRatio = minOf(dimensions[0].first, dimensions[0].second).toDouble() / maxOf(dimensions[0].first, dimensions[0].second).toDouble()
        for (i in 1 until dimensions.size) {
            val ratio = minOf(dimensions[i].first, dimensions[i].second).toDouble() / maxOf(dimensions[i].first, dimensions[i].second).toDouble()
            assertEquals("Screenshot aspect ratios must match (${screenshots[0].name} vs ${screenshots[i].name})", firstRatio, ratio, 0.01)
        }
    }
}
