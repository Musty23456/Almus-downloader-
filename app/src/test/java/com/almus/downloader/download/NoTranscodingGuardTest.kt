package com.almus.downloader.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the project's own files and fails if the rules for audio and video downloads are broken:
 * the original file is saved as is (no converting), no downloader or extractor library and no native
 * binary is added, and no login, cookie or referrer header is ever sent. Comments are ignored.
 * Gradle runs unit tests with the module folder (app/) as the working directory.
 */
class NoTranscodingGuardTest {
    private fun firstExisting(vararg paths: String): File =
        paths.map { File(it) }.firstOrNull { it.exists() } ?: error("none of ${paths.toList()} found from ${File(".").absolutePath}")

    private val sources: List<Pair<String, String>> by lazy {
        val root = firstExisting("src/main/java", "app/src/main/java")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.path to stripComments(it.readText()) }
            .toList()
    }

    private fun stripComments(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("(?m)(^|\\s)//[^\\n]*"), " ")

    private fun assertNone(description: String, pattern: Regex) {
        val hits = sources.filter { pattern.containsMatchIn(it.second) }.map { it.first }
        assertTrue("$description found in: $hits", hits.isEmpty())
    }

    @Test fun sourcesWereFound() {
        assertTrue(sources.any { it.first.endsWith("LinkInspector.kt") })
        assertTrue(sources.size > 20)
    }

    @Test fun mediaIsNeverConvertedOrRepacked() {
        assertNone("media converting API", Regex("MediaMuxer|MediaCodec|MediaTranscoder|MediaRecorder|MediaExtractor"))
        assertNone("ffmpeg", Regex("(?i)ffmpeg|ffprobe|libav"))
    }

    @Test fun noSiteExtractorOrDownloaderLibraryIsUsed() {
        assertNone("site extractor", Regex("(?i)yt-?dlp|youtube-?dl|ytdlp|newpipe|you-?get"))
    }

    @Test fun noExternalProgramOrNativeLibraryIsStarted() {
        assertNone("starting a program", Regex("ProcessBuilder|Runtime\\.getRuntime"))
        assertNone("loading native or dynamic code", Regex("System\\.load(Library)?\\s*\\(|DexClassLoader|PathClassLoader"))
    }

    @Test fun onlyPlainRequestHeadersAreEverSet() {
        val allowed = setOf("User-Agent", "Accept-Encoding", "Accept", "Range")
        val used = sources.flatMap { (_, text) ->
            Regex("(?:setRequestProperty|addRequestHeader)\\s*\\(\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("no request headers found, so this guard would check nothing", used.isNotEmpty())
        assertEquals("unexpected request headers: ${used - allowed}", emptySet<String>(), used - allowed)
    }

    @Test fun onlyReviewedDependenciesAreDeclared() {
        val catalog = firstExisting("../gradle/libs.versions.toml", "gradle/libs.versions.toml").readText()
        val groups = Regex("group\\s*=\\s*\"([^\"]+)\"").findAll(catalog).map { it.groupValues[1] }.toList()
        assertTrue("no libraries found in the version catalog", groups.isNotEmpty())
        val unreviewed = groups.filterNot { it.startsWith("androidx.") || it == "org.jetbrains.kotlinx" || it == "junit" }
        assertEquals("add a safety and licence review before allowing: $unreviewed", emptyList<String>(), unreviewed)

        val build = firstExisting("build.gradle.kts", "app/build.gradle.kts").readText()
        val literal = Regex("(implementation|api|ksp|kapt|testImplementation|androidTestImplementation)\\s*\\(\\s*\"").containsMatchIn(build)
        assertTrue("dependencies must come from the reviewed version catalog", !literal)
    }
}
