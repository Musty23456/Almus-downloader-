package com.almus.downloader.facebook.android

import com.almus.downloader.facebook.FacebookHttp
import com.almus.downloader.facebook.FacebookNetworkException
import com.almus.downloader.facebook.FacebookReply
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The module's only network code for metadata. It talks to one official address over https and nothing else:
 * no redirects are followed (so a token can never be forwarded to another host), no cookies are kept (OkHttp's
 * default), answers are size-limited.
 */
class OkHttpFacebookHttp(
    private val client: OkHttpClient = defaultClient(),
) : FacebookHttp {

    override suspend fun get(url: String, headers: Map<String, String>): FacebookReply = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || parsed.host !in ALLOWED_HOSTS) {
            throw FacebookNetworkException("This address is not an official Facebook service.")
        }
        val builder = Request.Builder().url(parsed).get().header("User-Agent", USER_AGENT)
        for ((name, value) in headers) builder.header(name, value)
        try {
            client.newCall(builder.build()).execute().use { response ->
                // Error answers carry the reason as JSON, so the body is read for every status.
                val bytes = response.peekBody(MAX_BODY_BYTES + 1L).bytes()
                if (bytes.size > MAX_BODY_BYTES) throw FacebookNetworkException("The answer is too large.")
                FacebookReply(response.code, String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw FacebookNetworkException("Could not reach Facebook.", e)
        }
    }

    companion object {
        private const val USER_AGENT = "AlmusDownloader/0.11 (Facebook metadata)"
        private const val MAX_BODY_BYTES = 256 * 1024
        private val ALLOWED_HOSTS = setOf("graph.facebook.com")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
