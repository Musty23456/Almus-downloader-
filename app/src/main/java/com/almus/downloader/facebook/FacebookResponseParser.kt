package com.almus.downloader.facebook

import com.almus.downloader.youtube.MiniJson

/** What the Graph API returned for a video, before the owner check. [ownerId] and [fileUrl] are not trusted yet. */
internal data class GraphVideo(val info: FacebookVideoInfo, val ownerId: String?, val fileUrl: String?)

/** The machine-readable part of a Graph API error answer. */
internal data class GraphError(val code: Int?, val subcode: Int?, val message: String?)

/**
 * Reads the answers of Facebook's official services. Pure text handling; a null result means "not understood".
 * Only documented fields are read. Every address taken from an answer is checked before it is kept.
 */
internal object FacebookResponseParser {
    private const val MAX_TITLE = 300
    private const val MAX_DESCRIPTION = 600
    private const val MAX_NAME = 120
    private val dateFormat = Regex("^(\\d{4}-\\d{2}-\\d{2})")

    /** A Graph API `/{video-id}` answer for [ref]. */
    fun parseGraphVideo(body: String, ref: FacebookContentRef): GraphVideo? {
        val root = MiniJson.parseObject(body) ?: return null
        val id = root.text("id")
        if (id != null && id != ref.contentId) return null

        val from = root["from"] as? Map<*, *>
        val ownerId = from?.get("id") as? String
        val ownerName = clean(from?.get("name") as? String, MAX_NAME)
        val title = clean(root.text("title"), MAX_TITLE)
        val description = clean(root.text("description"), MAX_DESCRIPTION)
        val thumbnail = root.text("picture")?.takeIf { FacebookLinks.isTrustedThumbnail(it) }
        val seconds = (root["length"] as? Double)?.toLong()?.takeIf { it > 0L }
        if (id == null && title == null && description == null && thumbnail == null && ownerName == null && seconds == null) return null

        val info = FacebookVideoInfo(
            contentId = ref.contentId,
            title = title,
            description = description,
            ownerName = ownerName,
            ownerUrl = FacebookLinks.profileUrlFor(ownerId),
            thumbnailUrl = thumbnail,
            durationSeconds = seconds,
            publishedDate = root.text("created_time")?.let { dateFormat.find(it)?.groupValues?.get(1) },
            widthPx = null,
            heightPx = null,
            embeddable = root["embeddable"] as? Boolean,
            source = FacebookMetadataSource.GraphApi,
        )
        return GraphVideo(info, ownerId, root.text("source"))
    }

    /** The public oEmbed answer. It gives the embed size only: no title, owner or thumbnail. */
    fun parseOEmbed(body: String, ref: FacebookContentRef): FacebookVideoInfo? {
        val root = MiniJson.parseObject(body) ?: return null
        val html = root.text("html")
        val width = (root["width"] as? Double)?.toInt()?.takeIf { it > 0 }
        val height = (root["height"] as? Double)?.toInt()?.takeIf { it > 0 }
        if (html.isNullOrBlank() && width == null && height == null) return null
        return FacebookVideoInfo(
            contentId = ref.contentId,
            title = null,
            description = null,
            ownerName = null,
            ownerUrl = null,
            thumbnailUrl = null,
            durationSeconds = null,
            publishedDate = null,
            widthPx = width,
            heightPx = height,
            embeddable = true,
            source = FacebookMetadataSource.OEmbed,
        )
    }

    /** The error of a failed answer, or null when the body has no readable `error` object. */
    fun apiError(body: String): GraphError? {
        val error = MiniJson.parseObject(body)?.get("error") as? Map<*, *> ?: return null
        return GraphError(
            code = (error["code"] as? Double)?.toInt(),
            subcode = (error["error_subcode"] as? Double)?.toInt(),
            message = error["message"] as? String,
        )
    }

    /** The account id of a `/me` answer, or null. */
    fun parseMeId(body: String): String? =
        MiniJson.parseObject(body)?.text("id")?.takeIf { FacebookLinks.isValidContentId(it) }

    private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

    /** Trims, drops control characters other than line breaks, and limits the length. Empty becomes null. */
    private fun clean(text: String?, max: Int): String? {
        val cleaned = text?.filter { !it.isISOControl() || it == '\n' }?.trim().orEmpty()
        return cleaned.take(max).takeIf { it.isNotEmpty() }
    }
}
