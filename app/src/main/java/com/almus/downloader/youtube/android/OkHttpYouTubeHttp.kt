package com.almus.downloader.youtube.android

import com.almus.downloader.youtube.HttpReply
import com.almus.downloader.youtube.YouTubeHttp
import com.almus.downloader.youtube.YouTubeNetworkException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The module's only network code for metadata. It talks to two official addresses over https and nothing else:
 * no redirects are followed, no cookies are kept (OkHttp's default), answers are size-limited.
 */
class OkHttpYouTubeHttp(
    private val client: OkHttpClient = defaultClient(),
) : YouTubeHttp {

    override suspend fun get(url: String, headers: Map<String, String>): HttpReply = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || parsed.host !in ALLOWED_HOSTS) {
            throw YouTubeNetworkException("This address is not an official YouTube service.")
        }
        val builder = Request.Builder().url(parsed).get().header("User-Agent", USER_AGENT)
        for ((name, value) in headers) builder.header(name, value)
        try {
            client.newCall(builder.build()).execute().use { response ->
                val bytes = response.peekBody(MAX_BODY_BYTES + 1L).bytes()
                if (bytes.size > MAX_BODY_BYTES) throw YouTubeNetworkException("The answer is too large.")
                HttpReply(response.code, String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw YouTubeNetworkException("Could not reach YouTube.", e)
        }
    }

    companion object {
        private const val USER_AGENT = "AlmusDownloader/0.10 (YouTube metadata)"
        private const val MAX_BODY_BYTES = 256 * 1024
        private val ALLOWED_HOSTS = setOf("www.googleapis.com", "www.youtube.com")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
