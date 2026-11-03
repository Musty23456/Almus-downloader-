package com.almus.downloader.youtube

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the YouTube module's own source and fails if it ever stops being "official services only":
 * no stream or player extraction, no sign-in, cookie or token handling, no address outside the three official
 * hosts, only reviewed request headers, and a plain-Kotlin core. Comments are ignored.
 * Gradle runs unit tests with the module folder (app/) as the working directory.
 */
class YouTubeSourceGuardTest {
    private val root: File = listOf("src/main/java/com/almus/downloader/youtube", "app/src/main/java/com/almus/downloader/youtube")
        .map { File(it) }.firstOrNull { it.exists() } ?: error("youtube sources not found from ${File(".").absolutePath}")

    private val sources: List<Pair<String, String>> by lazy {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.path to strip(it.readText()) }.toList()
    }

    private fun strip(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("(?m)(^|\\s)//[^\\n]*"), " ")

    private fun hits(pattern: Regex, include: (String) -> Boolean = { true }): List<String> =
        sources.filter { include(it.first) && pattern.containsMatchIn(it.second) }.map { File(it.first).name }.sorted()

    @Test fun sourcesWereFound() {
        assertTrue(sources.size >= 12)
        assertTrue(sources.any { it.first.endsWith("YouTubeMetadataClient.kt") })
    }

    @Test fun noStreamPlayerOrSignatureExtraction() {
        val pattern = Regex("(?i)streamingData|adaptiveFormats|player_?response|ytInitial|signatureCipher|/youtubei/|videoplayback|get_video_info|itag|\\bcipher\\b|manifest")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun noSignInCookiesOrTokens() {
        val pattern = Regex("(?i)(?<!no)cookie|authorization|bearer|oauth|setAuthenticator|CookieJar|access_?token|refresh_?token|\\bsapisid|\\bpapisid|visitor_?data|po_?token")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun onlyOfficialHostsAreAddressed() {
        val allowed = setOf("www.googleapis.com", "www.youtube.com")
        val found = sources.flatMap { (_, text) ->
            Regex("https?://([A-Za-z0-9.\\-]+)").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("no addresses found, so this guard would check nothing", found.isNotEmpty())
        assertEquals("unexpected hosts: ${found - allowed}", emptySet<String>(), found - allowed)
    }

    @Test fun onlyReviewedRequestHeadersAreSet() {
        // Header names are hyphenated literals ("User-Agent", "x-goog-api-key") or the plain "Accept".
        val hyphenated = sources.flatMap { (_, text) ->
            Regex("\"([A-Za-z]+(?:-[A-Za-z]+)+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals("unexpected headers: ${hyphenated - setOf("User-Agent", "x-goog-api-key")}", emptySet<String>(), hyphenated - setOf("User-Agent", "x-goog-api-key"))
        assertTrue("the API key header is not used, so this guard would check nothing", "x-goog-api-key" in hyphenated)
        val direct = sources.flatMap { (_, text) ->
            Regex("\\.header\\(\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("User-Agent"), direct)
    }

    @Test fun theCoreStaysPlainKotlin() {
        val core = { path: String -> !path.contains("${File.separator}android${File.separator}") }
        assertEquals(emptyList<String>(), hits(Regex("import\\s+(android|androidx|okhttp3|okio)\\."), core))
        assertEquals(emptyList<String>(), hits(Regex("WebView|\\bIntent\\b|startActivity|loadUrl|evaluateJavascript|ProcessBuilder|Runtime\\.getRuntime|Class\\.forName|ClassLoader|System\\.load"), core))
        assertEquals(emptyList<String>(), hits(Regex("HttpURLConnection|openConnection|java\\.net\\.URL\\b|java\\.net\\.Socket"), core))
    }

    @Test fun networkCodeLivesOnlyInTheAndroidFolder() {
        val net = hits(Regex("import\\s+okhttp3\\."))
        assertEquals(listOf("OkHttpYouTubeHttp.kt", "ThumbnailLoader.kt"), net)
    }

    @Test fun noDownloadOfAnythingIsWrittenToStorage() {
        val pattern = Regex("FileOutputStream|openFileOutput|MediaStore|DownloadManager|ContentResolver|File\\(|writeBytes|\\.copyTo")
        assertEquals(emptyList<String>(), hits(pattern))
    }
}
