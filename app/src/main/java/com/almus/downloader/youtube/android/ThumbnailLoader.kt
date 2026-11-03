package com.almus.downloader.youtube.android

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.almus.downloader.youtube.YouTubeLinks
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads the thumbnail address YouTube's own services returned, so it can be shown next to the title.
 * Only https addresses on YouTube's image host are fetched; the picture is shown, never saved.
 */
object ThumbnailLoader {
    private const val MAX_BYTES = 2 * 1024 * 1024

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** The decoded picture, or null when the address is not trusted or the picture cannot be loaded. */
    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!YouTubeLinks.isTrustedThumbnail(url)) return@withContext null
        try {
            val request = Request.Builder().url(url).get().header("User-Agent", "AlmusDownloader/0.10 (thumbnail)").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.peekBody(MAX_BYTES + 1L).bytes()
                if (bytes.size > MAX_BYTES) return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (_: IOException) {
            null
        }
    }
}
