package com.almus.downloader.tiktok

import com.almus.downloader.youtube.MiniJson

/** The machine-readable part of an error answer. [code] may be a number or a word, kept as text. */
internal data class TikTokApiError(val code: String?, val message: String?)

/**
 * Reads the answer of TikTok's public oEmbed service. Pure text handling; a null result means "not understood".
 * Only documented oEmbed fields are read. Every address taken from an answer is checked before it is kept. The
 * embed markup (`html`) is never shown, loaded or run: it is read only to check that the answer is about the video
 * that was asked for.
 */
internal object TikTokResponseParser {
    private const val MAX_CAPTION = 300
    private const val MAX_NAME = 120
    private const val MAX_THUMBNAIL_SIDE = 10_000
    private val embeddedVideoId = Regex("data-video-id=\"([0-9]{1,25})\"")

    fun parseOEmbed(body: String, ref: TikTokContentRef): TikTokVideoInfo? {
        val root = MiniJson.parseObject(body) ?: return null
        val type = root.text("type")
        if (type != null && type != "video") return null

        val html = root.text("html")
        val embeddedId = html?.let { embeddedVideoId.find(it)?.groupValues?.get(1) }
        if (embeddedId != null && embeddedId != ref.contentId) return null

        val caption = clean(root.text("title"), MAX_CAPTION)
        val name = clean(root.text("author_name"), MAX_NAME)
        val handle = TikTokLinks.handleFromProfileUrl(root.text("author_url"))
        val thumbnail = root.text("thumbnail_url")?.takeIf { TikTokLinks.isTrustedThumbnail(it) }
        val width = root.number("thumbnail_width")?.takeIf { it in 1..MAX_THUMBNAIL_SIDE }
        val height = root.number("thumbnail_height")?.takeIf { it in 1..MAX_THUMBNAIL_SIDE }

        if (html.isNullOrBlank() && caption == null && name == null && thumbnail == null) return null
        return TikTokVideoInfo(
            contentId = ref.contentId,
            caption = caption,
            creatorName = name,
            creatorHandle = handle,
            creatorUrl = TikTokLinks.profileUrlFor(handle),
            thumbnailUrl = thumbnail,
            thumbnailWidthPx = width,
            thumbnailHeightPx = height,
        )
    }

    /** The error of a failed answer (`{"code":..,"message":..}` or `{"error":{..}}`), or null when there is none. */
    fun apiError(body: String): TikTokApiError? {
        val root = MiniJson.parseObject(body) ?: return null
        val nested = root["error"] as? Map<*, *>
        val source: Map<*, *> = nested ?: root
        val code = when (val raw = source["code"]) {
            is Double -> raw.toLong().toString()
            is String -> raw
            else -> null
        }
        val message = source["message"] as? String
        if (code == null && message == null) return null
        return TikTokApiError(code, message)
    }

    private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

    /** A whole number given as a JSON number or as digits in a string, or null. */
    private fun Map<String, Any?>.number(key: String): Int? = when (val raw = this[key]) {
        is Double -> if (raw >= 0.0 && raw <= MAX_THUMBNAIL_SIDE.toDouble()) raw.toInt() else null
        is String -> raw.toIntOrNull()
        else -> null
    }

    /** Trims, drops control characters other than line breaks, and limits the length. Empty becomes null. */
    private fun clean(text: String?, max: Int): String? {
        val cleaned = text?.filter { !it.isISOControl() || it == '\n' }?.trim().orEmpty()
        return cleaned.take(max).takeIf { it.isNotEmpty() }
    }
}
