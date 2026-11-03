package com.almus.downloader.facebook

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the Facebook module's own source and fails if it ever stops being "official services only":
 * no page scraping, no stream or player extraction, no sign-in, cookie or password handling, no address outside
 * the official Facebook hosts, only reviewed request headers, a token that never enters an address, and a
 * plain-Kotlin core. Comments are ignored. Gradle runs unit tests with the module folder (app/) as the working directory.
 */
class FacebookSourceGuardTest {
    private val root: File = listOf("src/main/java/com/almus/downloader/facebook", "app/src/main/java/com/almus/downloader/facebook")
        .map { File(it) }.firstOrNull { it.exists() } ?: error("facebook sources not found from ${File(".").absolutePath}")

    private val sources: List<Pair<String, String>> by lazy {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.path to strip(it.readText()) }.toList()
    }

    private fun strip(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("(?m)(^|\\s)//[^\\n]*"), " ")

    private fun hits(pattern: Regex, include: (String) -> Boolean = { true }): List<String> =
        sources.filter { include(it.first) && pattern.containsMatchIn(it.second) }.map { File(it.first).name }.sorted()

    @Test fun sourcesWereFound() {
        assertTrue(sources.size >= 9)
        assertTrue(sources.any { it.first.endsWith("FacebookMetadataClient.kt") })
    }

    @Test fun noPageScrapingOrStreamExtraction() {
        val pattern = Regex("(?i)hd_src|sd_src|playable_url|browser_native|__a=1|/api/graphql|graphql/query|document_id|\\bdoc_id|og:video|og:image|jsoup|Jsoup|\\.select\\(|getElementBy|querySelector|\\bscrape|\\bmanifest|videoplayback|\\bm3u8|\\bmpd\\b|fb_dtsg|lsd=")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun noSignInCookiesPasswordsOrTokenHarvesting() {
        // "password" is matched only where code uses one (an identifier or an assignment). The user-facing sentence
        // "confirm with your password in Facebook, not here" is an instruction, not password handling.
        val pattern = Regex("(?i)(?<!no )cookie|CookieJar|CookieManager|setAuthenticator|\\w*pass(?:word|wd)\\w*\\s*[=(:]|(?:val|var)\\s+\\w*pass(?:word|wd)|c_user|\\bxs=|datr|fb_dtsg|oauth/|dialog/oauth|/login|WebViewClient|shouldInterceptRequest|\\blogin_?token|refresh_?token|client_?secret|app_?secret")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun theTokenNeverEntersAnAddress() {
        // The only place the token is read is the header; "access_token" must never be built into a query.
        assertEquals(emptyList<String>(), hits(Regex("access_token")))
        assertTrue("the Authorization header is not used, so this guard would check nothing", hits(Regex("\"Authorization\"")).isNotEmpty())
    }

    @Test fun onlyOfficialHostsAreAddressed() {
        val allowed = setOf("graph.facebook.com", "www.facebook.com")
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
    }

    @Test fun theCoreStaysPlainKotlin() {
        val core = { path: String -> !path.contains("${File.separator}android${File.separator}") }
        assertEquals(emptyList<String>(), hits(Regex("import\\s+(android|androidx|okhttp3|okio)\\."), core))
        assertEquals(emptyList<String>(), hits(Regex("WebView|\\bIntent\\b|startActivity|loadUrl|evaluateJavascript|ProcessBuilder|Runtime\\.getRuntime|Class\\.forName|ClassLoader|System\\.load"), core))
        assertEquals(emptyList<String>(), hits(Regex("HttpURLConnection|openConnection|java\\.net\\.URL\\b|java\\.net\\.Socket"), core))
    }

    @Test fun networkCodeLivesOnlyInTheAndroidFolder() {
        assertEquals(listOf("FacebookThumbnailLoader.kt", "OkHttpFacebookHttp.kt"), hits(Regex("import\\s+okhttp3\\.")))
    }

    @Test fun noDownloadOfAnythingIsWrittenToStorage() {
        val pattern = Regex("FileOutputStream|openFileOutput|MediaStore|DownloadManager|ContentResolver|File\\(|writeBytes|\\.copyTo")
        assertEquals(emptyList<String>(), hits(pattern))
    }

    @Test fun theModuleDoesNotReachIntoOtherPlatformModules() {
        // The YouTube module's JSON reader is shared on purpose; nothing else from it may be used.
        val imports = sources.flatMap { (_, text) ->
            Regex("import\\s+com\\.almus\\.downloader\\.youtube\\.(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("MiniJson"), imports)
        assertEquals(emptyList<String>(), hits(Regex("import\\s+com\\.almus\\.downloader\\.platform\\.adapters\\.")))
    }
}
