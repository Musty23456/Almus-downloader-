package com.almus.downloader.integration

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the integration module's own source and fails if it ever stops being "official services only": no page
 * scraping, no stream, player, manifest or signature handling, no sign-in, cookie, token or password handling, no
 * address outside the reviewed official hosts, only reviewed request headers, no embed script or web view, and a
 * plain-Kotlin core. Comments are ignored. Gradle runs unit tests with the module folder (app/) as the working directory.
 */
class IntegrationSourceGuardTest {
    private val root: File = listOf("src/main/java/com/almus/downloader/integration", "app/src/main/java/com/almus/downloader/integration")
        .map { File(it) }.firstOrNull { it.exists() } ?: error("integration sources not found from ${File(".").absolutePath}")

    private val sources: List<Pair<String, String>> by lazy {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.path to strip(it.readText()) }.toList()
    }

    private fun strip(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("(?m)(^|\\s)//[^\\n]*"), " ")

    private fun hits(pattern: Regex, include: (String) -> Boolean = { true }): List<String> =
        sources.filter { include(it.first) && pattern.containsMatchIn(it.second) }.map { File(it.first).name }.sorted()

    @Test fun sourcesWereFound() {
        assertTrue(sources.size >= 19)
        assertTrue(sources.any { it.first.endsWith("OEmbedClient.kt") })
        assertEquals(6, sources.count { it.first.contains("${File.separator}specs${File.separator}") })
    }

    @Test fun noPageScrapingOrStreamExtraction() {
        val pattern = Regex("(?i)og:video|og:image|jsoup|Jsoup|\\.select\\(|getElementBy|querySelector|\\bscrape|\\bmanifest|\\bm3u8|\\bmpd\\b|videoplayback|\\bembed\\.js\\b|\\bconfig_url|player_config|fallback_url|reddit_video|dash_url|hls_url|DASHPlaylist|guest_token|_signature|\\bwatermark|playAddr|downloadAddr|/api/v1/|\\.json\\?raw_json|__PWS_INITIAL|window\\.__")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun noSignInCookiesPasswordsOrTokens() {
        val pattern = Regex("(?i)(?<!no )cookie|CookieJar|CookieManager|setAuthenticator|\\w*pass(?:word|wd)\\w*\\s*[=(:]|(?:val|var)\\s+\\w*pass(?:word|wd)|sessionid|csrf|\\bauthorization\\b|\\bbearer\\b|oauth|access_?token|refresh_?token|client_?(?:key|secret)|api_?key|/login|WebViewClient|shouldInterceptRequest")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun onlyOfficialHostsAreAddressed() {
        val allowed = setOf(
            // oEmbed services (one per platform)
            "vimeo.com", "publish.twitter.com", "www.dailymotion.com", "www.reddit.com", "www.pinterest.com", "graph.facebook.com",
            // official pages
            "www.instagram.com", "x.com", "twitter.com",
            // Help Centers
            "help.instagram.com", "help.x.com", "help.vimeo.com", "faq.dailymotion.com", "support.reddithelp.com", "help.pinterest.com",
        )
        val found = sources.flatMap { (_, text) ->
            Regex("https?://([A-Za-z0-9.\\-]+)").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("no addresses found, so this guard would check nothing", found.isNotEmpty())
        assertEquals("unexpected hosts: ${found - allowed}", emptySet<String>(), found - allowed)
    }

    @Test fun onlyReviewedRequestHeadersAreSet() {
        val hyphenated = sources.flatMap { (_, text) ->
            Regex("\"([A-Za-z]+(?:-[A-Za-z]+)+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals("unexpected headers: ${hyphenated - setOf("User-Agent")}", emptySet<String>(), hyphenated - setOf("User-Agent"))
        val direct = sources.flatMap { (_, text) ->
            Regex("\\.header\\(\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("User-Agent"), direct)
        val maps = sources.flatMap { (_, text) ->
            Regex("mapOf\\(\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("Accept"), maps)
    }

    @Test fun theCoreStaysPlainKotlin() {
        val core = { path: String -> !path.contains("${File.separator}android${File.separator}") }
        assertEquals(emptyList<String>(), hits(Regex("import\\s+(android|androidx|okhttp3|okio)\\."), core))
        assertEquals(emptyList<String>(), hits(Regex("WebView|\\bIntent\\b|startActivity|loadUrl|evaluateJavascript|ProcessBuilder|Runtime\\.getRuntime|Class\\.forName|ClassLoader|System\\.load"), core))
        assertEquals(emptyList<String>(), hits(Regex("HttpURLConnection|openConnection|java\\.net\\.URL\\b|java\\.net\\.Socket"), core))
    }

    @Test fun networkCodeLivesOnlyInTheAndroidFolder() {
        assertEquals(listOf("OkHttpOEmbedHttp.kt", "PlatformThumbnailLoader.kt"), hits(Regex("import\\s+okhttp3\\.")))
    }

    @Test fun noWebViewIsUsedAnywhereInTheModule() {
        assertEquals(emptyList<String>(), hits(Regex("WebView|loadUrl|evaluateJavascript|addJavascriptInterface")))
    }

    @Test fun noDownloadOfAnythingIsWrittenToStorage() {
        val pattern = Regex("FileOutputStream|openFileOutput|MediaStore|DownloadManager|ContentResolver|File\\(|writeBytes|\\.copyTo")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun theModuleDoesNotReachIntoOtherPlatformModulesOrAdapters() {
        val imports = sources.flatMap { (_, text) ->
            Regex("import\\s+com\\.almus\\.downloader\\.youtube\\.(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("MiniJson"), imports)
        assertEquals(emptyList<String>(), hits(Regex("import\\s+com\\.almus\\.downloader\\.(facebook|tiktok)\\.")))
        assertEquals(emptyList<String>(), hits(Regex("import\\s+com\\.almus\\.downloader\\.platform\\.adapters\\.")))
    }

    @Test fun specsHoldDataAndTextHandlingOnly() {
        val specs = { path: String -> path.contains("${File.separator}specs${File.separator}") }
        assertEquals(emptyList<String>(), hits(Regex("suspend|kotlinx|okhttp3|java\\.net|import\\s+android"), specs))
    }
}
