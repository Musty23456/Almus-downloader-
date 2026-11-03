package com.almus.downloader.integration

import com.almus.downloader.integration.android.OkHttpOEmbedHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The transport refuses every address except a platform's official oEmbed endpoint over https, before any connection is made. */
class OkHttpOEmbedHttpTest {
    private fun refused(url: String): Boolean = try {
        runBlocking { OkHttpOEmbedHttp().get(url, emptyMap()) }
        false
    } catch (_: OEmbedNetworkException) {
        true
    }

    @Test fun onlyTheSixOfficialEndpointsAreKnown() {
        assertTrue(OkHttpOEmbedHttp.isOfficialService("vimeo.com", "/api/oembed.json"))
        assertTrue(OkHttpOEmbedHttp.isOfficialService("publish.twitter.com", "/oembed"))
        assertTrue(OkHttpOEmbedHttp.isOfficialService("www.dailymotion.com", "/services/oembed"))
        assertTrue(OkHttpOEmbedHttp.isOfficialService("www.reddit.com", "/oembed"))
        assertTrue(OkHttpOEmbedHttp.isOfficialService("www.pinterest.com", "/oembed.json"))
        assertTrue(OkHttpOEmbedHttp.isOfficialService("graph.facebook.com", "/v26.0/instagram_oembed"))
        assertFalse(OkHttpOEmbedHttp.isOfficialService("vimeo.com", "/"))
        assertFalse(OkHttpOEmbedHttp.isOfficialService("player.vimeo.com", "/api/oembed.json"))
        assertFalse(OkHttpOEmbedHttp.isOfficialService("graph.facebook.com", "/v26.0/me"))
        assertFalse(OkHttpOEmbedHttp.isOfficialService("www.reddit.com", "/r/kotlin/.json"))
    }

    @Test fun everyOtherAddressIsRefused() {
        listOf(
            "http://vimeo.com/api/oembed.json?url=x",
            "https://www.vimeo.com/api/oembed.json?url=x",
            "https://vimeo.com/76979871",
            "https://vimeo.com.evil.example/api/oembed.json",
            "https://www.reddit.com/r/kotlin/comments/abc123/.json",
            "https://old.reddit.com/oembed?url=x",
            "https://graph.facebook.com/v26.0/me?fields=id",
            "https://i.vimeocdn.com/video/1.jpg",
            "https://evil.example/oembed?url=x",
            "ftp://vimeo.com/api/oembed.json",
            "not a url",
            "",
        ).forEach { assertTrue("allowed: $it", refused(it)) }
    }
}
