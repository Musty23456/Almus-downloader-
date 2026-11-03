package com.almus.downloader.tiktok

import com.almus.downloader.tiktok.android.OkHttpTikTokHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** The transport refuses every address except TikTok's oEmbed endpoint over https, before any connection is made. */
class OkHttpTikTokHttpTest {
    private fun refused(url: String): Boolean = try {
        runBlocking { OkHttpTikTokHttp().get(url, emptyMap()) }
        false
    } catch (_: TikTokNetworkException) {
        true
    }

    @Test fun onlyTheOfficialOEmbedAddressIsAllowed() {
        listOf(
            "http://www.tiktok.com/oembed?url=x",
            "https://tiktok.com/oembed?url=x",
            "https://m.tiktok.com/oembed?url=x",
            "https://www.tiktok.com/@user/video/6718335390845095173",
            "https://www.tiktok.com/oembed/other?url=x",
            "https://www.tiktok.com/",
            "https://tiktok.com.evil.example/oembed?url=x",
            "https://evil.example/oembed?url=x",
            "https://graph.facebook.com/oembed",
            "ftp://www.tiktok.com/oembed",
            "not a url",
            "",
        ).forEach { assertTrue("allowed: $it", refused(it)) }
    }
}
