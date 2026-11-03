package com.almus.downloader.integration.android

import com.almus.downloader.integration.OEmbedHttp
import com.almus.downloader.integration.OEmbedNetworkException
import com.almus.downloader.integration.OEmbedReply
import com.almus.downloader.integration.PlatformSpecs
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The module's only network code for metadata. It talks to the official oEmbed addresses listed in [PlatformSpecs]
 * (one fixed host and path per platform) over https and nothing else: no redirects are followed, no cookies are
 * kept (OkHttp's default), no credentials are sent, and answers are size-limited.
 */
class OkHttpOEmbedHttp(
    private val client: OkHttpClient = defaultClient(),
) : OEmbedHttp {

    override suspend fun get(url: String, headers: Map<String, String>): OEmbedReply = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || !isOfficialService(parsed.host, parsed.encodedPath)) {
            throw OEmbedNetworkException("This address is not an official oEmbed service.")
        }
        val builder = Request.Builder().url(parsed).get().header("User-Agent", USER_AGENT)
        for ((name, value) in headers) builder.header(name, value)
        try {
            client.newCall(builder.build()).execute().use { response ->
                // Error answers can carry the reason as JSON, so the body is read for every status.
                val bytes = response.peekBody(MAX_BODY_BYTES + 1L).bytes()
                if (bytes.size > MAX_BODY_BYTES) throw OEmbedNetworkException("The answer is too large.")
                OEmbedReply(response.code, String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw OEmbedNetworkException("Could not reach the service.", e)
        }
    }

    companion object {
        private const val USER_AGENT = "AlmusDownloader/0.13 (oEmbed metadata)"
        private const val MAX_BODY_BYTES = 256 * 1024

        /** True only for the exact host and path of a platform's official oEmbed service. */
        fun isOfficialService(host: String, path: String): Boolean =
            PlatformSpecs.all.any { it.oembed.host == host && it.oembed.path == path }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
