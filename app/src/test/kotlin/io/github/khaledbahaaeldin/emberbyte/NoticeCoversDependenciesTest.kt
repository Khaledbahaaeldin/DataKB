package io.github.khaledbahaaeldin.emberbyte

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeCoversDependenciesTest {

    private fun findRepoRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File("."),
            File(".."),
            File(userDir),
            File(userDir, "..")
        )
        return candidates.firstOrNull { File(it, "docs/release/dependency-licences.txt").exists() }
            ?: candidates.firstOrNull { File(it, "gradlew").exists() }
            ?: File(".")
    }

    private fun findNoticeFile(): File {
        val root = findRepoRoot()
        val candidates = listOf(
            File(root, "NOTICE"),
            File("NOTICE"),
            File("../NOTICE")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("NOTICE file not found in candidates: $candidates")
    }

    @Test
    fun notice_covers_every_runtime_dependency_component() {
        val root = findRepoRoot()
        val depLicencesFile = File(root, "docs/release/dependency-licences.txt")
        assertTrue(
            "docs/release/dependency-licences.txt must exist at ${depLicencesFile.absolutePath}",
            depLicencesFile.exists()
        )

        val noticeText = findNoticeFile().readText(Charsets.UTF_8)
        val missingComponents = mutableListOf<String>()

        depLicencesFile.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split("|").map { it.trim() }
            if (parts.size >= 2) {
                val component = parts[0]
                val searchTokens = when (component) {
                    "guava listenablefuture" -> listOf("listenablefuture")
                    "shaded protobuf" -> listOf("protobuf", "Protocol Buffers")
                    "JetBrains Compose Multiplatform" -> listOf("JetBrains Compose", "org.jetbrains.compose")
                    "kotlinx-serialization" -> listOf("kotlinx-serialization", "kotlinx.serialization")
                    "JetBrains Annotations" -> listOf("org.jetbrains:annotations")
                    "AndroidX Support Components" -> listOf("androidx.arch.core", "androidx.autofill", "androidx.customview", "androidx.navigationevent")
                    else -> listOf(component)
                }
                val covered = when (component) {
                    "AndroidX Support Components" -> searchTokens.all { token -> noticeText.contains(token, ignoreCase = true) }
                    else -> searchTokens.any { token -> noticeText.contains(token, ignoreCase = true) }
                }
                if (!covered) {
                    missingComponents.add(component)
                }
            }
        }

        assertTrue(
            "NOTICE is missing bundled runtime components: $missingComponents",
            missingComponents.isEmpty()
        )
    }
}
