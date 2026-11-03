package com.almus.downloader.integration

import com.almus.downloader.youtube.MiniJson

/** The machine-readable part of an error answer. [code] may be a number or a word, kept as text. */
internal data class OEmbedError(val code: String?, val message: String?)

/**
 * Reads the answer of a platform's public oEmbed service. Pure text handling; a null result means "not understood".
 * Only documented oEmbed fields are read. Every address taken from an answer is checked before it is kept. The embed
 * markup (`html`) is never shown, loaded or run: it is read only to check that the answer is about the content that
 * was asked for and, for platforms whose answer has no title, to take the text of the first paragraph.
 */
internal object OEmbedParser {
    private const val MAX_TITLE = 300
    private const val MAX_NAME = 120
    private const val MAX_SIDE = 10_000
    private const val MAX_SECONDS = 359_999
    private val knownTypes = setOf("video", "rich", "photo", "link")
    private val dateStart = Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2})")

    fun parse(body: String, ref: ContentRef, spec: PlatformSpec): ContentInfo? {
        val root = MiniJson.parseObject(body) ?: return null
        val type = root.text("type")
        if (type != null && type.lowercase() !in knownTypes) return null

        val html = root.text("html")
        val pattern = spec.embeddedIdPattern
        if (pattern != null && html != null) {
            val embedded = pattern.find(html)?.groupValues?.getOrNull(1)
            if (embedded != null && !embedded.equals(ref.contentId, ignoreCase = true)) return null
        }

        var title = clean(root.text("title"), MAX_TITLE)
        if (title == null && spec.titleFromMarkup) title = clean(MarkupText.firstParagraph(html), MAX_TITLE)
        val name = clean(root.text("author_name"), MAX_NAME)
        val authorUrl = PlatformLinks.trustedAuthorUrl(spec, root.text("author_url"))
        val thumbnail = root.text("thumbnail_url")?.takeIf { PlatformLinks.isTrustedThumbnail(spec, it) }
        val width = side(root["thumbnail_width"])
        val height = side(root["thumbnail_height"])
        val seconds = seconds(root["duration"])
        val published = root.text("upload_date")?.let { dateStart.find(it)?.groupValues?.get(1) }

        if (html.isNullOrBlank() && title == null && name == null && thumbnail == null) return null
        return ContentInfo(
            platform = ref.platform,
            contentId = ref.contentId,
            title = title,
            authorName = name,
            authorUrl = authorUrl,
            thumbnailUrl = thumbnail,
            thumbnailWidthPx = if (thumbnail != null) width else null,
            thumbnailHeightPx = if (thumbnail != null) height else null,
            durationSeconds = seconds,
            publishedOn = published,
            sourceLabel = spec.sourceLabel,
        )
    }

    /** The error of a failed answer (`{"code":..,"message":..}` or `{"error":{..}}`), or null when there is none. */
    fun apiError(body: String): OEmbedError? {
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
        return OEmbedError(code, message)
    }

    private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

    /** A pixel size given as a JSON number or as digits in a string, or null. */
    private fun side(raw: Any?): Int? = when (raw) {
        is Double -> if (raw >= 1.0 && raw <= MAX_SIDE.toDouble()) raw.toInt() else null
        is String -> raw.toIntOrNull()?.takeIf { it in 1..MAX_SIDE }
        else -> null
    }

    /** A length in whole seconds given as a JSON number or as digits in a string, or null. */
    private fun seconds(raw: Any?): Int? = when (raw) {
        is Double -> if (raw >= 1.0 && raw <= MAX_SECONDS.toDouble()) raw.toInt() else null
        is String -> raw.toIntOrNull()?.takeIf { it in 1..MAX_SECONDS }
        else -> null
    }

    /** Trims, drops control characters other than line breaks, and limits the length. Empty becomes null. */
    private fun clean(text: String?, max: Int): String? {
        val cleaned = text?.filter { !it.isISOControl() || it == '\n' }?.trim().orEmpty()
        return cleaned.take(max).takeIf { it.isNotEmpty() }
    }
}
