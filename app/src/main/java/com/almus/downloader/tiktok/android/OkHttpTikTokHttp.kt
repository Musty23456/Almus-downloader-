package com.almus.downloader.tiktok.android

import com.almus.downloader.tiktok.TikTokHttp
import com.almus.downloader.tiktok.TikTokNetworkException
import com.almus.downloader.tiktok.TikTokReply
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The module's only network code for metadata. It talks to one official address (the oEmbed endpoint on
 * www.tiktok.com) over https and nothing else: no redirects are followed, no cookies are kept (OkHttp's default),
 * no credentials are sent, and answers are size-limited.
 */
class OkHttpTikTokHttp(
    private val client: OkHttpClient = defaultClient(),
) : TikTokHttp {

    override suspend fun get(url: String, headers: Map<String, String>): TikTokReply = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || parsed.host !in ALLOWED_HOSTS || parsed.encodedPath != ALLOWED_PATH) {
            throw TikTokNetworkException("This address is not an official TikTok service.")
        }
        val builder = Request.Builder().url(parsed).get().header("User-Agent", USER_AGENT)
        for ((name, value) in headers) builder.header(name, value)
        try {
            client.newCall(builder.build()).execute().use { response ->
                // Error answers can carry the reason as JSON, so the body is read for every status.
                val bytes = response.peekBody(MAX_BODY_BYTES + 1L).bytes()
                if (bytes.size > MAX_BODY_BYTES) throw TikTokNetworkException("The answer is too large.")
                TikTokReply(response.code, String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw TikTokNetworkException("Could not reach TikTok.", e)
        }
    }

    companion object {
        private const val USER_AGENT = "AlmusDownloader/0.12 (TikTok metadata)"
        private const val MAX_BODY_BYTES = 256 * 1024
        private const val ALLOWED_PATH = "/oembed"
        private val ALLOWED_HOSTS = setOf("www.tiktok.com")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
