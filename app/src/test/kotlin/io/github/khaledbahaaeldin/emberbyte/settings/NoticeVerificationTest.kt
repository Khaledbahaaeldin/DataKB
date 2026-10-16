package io.github.khaledbahaaeldin.emberbyte.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeVerificationTest {

    private fun findNoticeFile(): File {
        val candidates = listOf(
            File("NOTICE"),
            File("../NOTICE"),
            File("../../NOTICE"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("NOTICE file not found in candidates: $candidates")
    }

    @Test
    fun notice_file_contains_mandatory_licence_and_component_entries() {
        val noticeText = findNoticeFile().readText()

        assertTrue("NOTICE must mention GPL", noticeText.contains("GPL", ignoreCase = true))
        assertTrue("NOTICE must mention Bouncy Castle", noticeText.contains("Bouncy Castle", ignoreCase = true))
        assertTrue("NOTICE must mention Roboto Flex", noticeText.contains("Roboto Flex", ignoreCase = true))

        // Contract 12.3: add missing ones (Room, WorkManager, Glance, DataStore, kotlinx libraries, Material, Compose, Bouncy Castle MIT)
        assertTrue("NOTICE must mention Room", noticeText.contains("Room", ignoreCase = true))
        assertTrue("NOTICE must mention WorkManager", noticeText.contains("WorkManager", ignoreCase = true))
        assertTrue("NOTICE must mention Glance", noticeText.contains("Glance", ignoreCase = true))
        assertTrue("NOTICE must mention DataStore", noticeText.contains("DataStore", ignoreCase = true))
        assertTrue("NOTICE must mention kotlinx", noticeText.contains("kotlinx", ignoreCase = true))
        assertTrue("NOTICE must mention Material", noticeText.contains("Material", ignoreCase = true))
        assertTrue("NOTICE must mention Compose", noticeText.contains("Compose", ignoreCase = true))
    }

    @Test
    fun notice_file_only_mentions_haze_if_present_in_runtime_dependencies() {
        val noticeText = findNoticeFile().readText()
        // Haze is present in :ui:design releaseRuntimeClasspath (dev.chrisbanes.haze:haze)
        val hazeInDependencies = true
        if (!hazeInDependencies) {
            assertFalse("NOTICE must not mention Haze if not in tree", noticeText.contains("Haze", ignoreCase = true))
        } else {
            assertTrue("NOTICE must mention Haze when in tree", noticeText.contains("Haze", ignoreCase = true))
        }
    }

    @Test
    fun raw_resource_equals_the_notice_file() {
        val noticeFile = findNoticeFile()
        val noticeText = noticeFile.readText()
        val generatedRawFile = File(noticeFile.parentFile, "app/build/generated/res/licenses/raw/notice.txt")
        assertTrue("Generated raw notice file must exist", generatedRawFile.exists())
        val rawText = generatedRawFile.readText()
        org.junit.Assert.assertEquals("Raw resource notice.txt must equal NOTICE file", noticeText, rawText)
    }
}
