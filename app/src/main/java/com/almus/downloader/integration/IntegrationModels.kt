package com.almus.downloader.integration

import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform

/**
 * One recognised and validated piece of content on Instagram, X, Vimeo, Dailymotion, Reddit or Pinterest.
 * [contentId] has been checked against the platform's own id format. [handle] is a validated account or community
 * name when the address carried one (an X account, a subreddit) and null otherwise. Every address built later (the
 * official page, the service request, the "open" address) comes from these validated parts only, never from the
 * text the person entered, so share and tracking parameters are never passed on.
 */
data class ContentRef(
    val platform: MediaPlatform,
    val kind: LinkKind,
    val contentId: String,
    val handle: String? = null,
) {
    /** Identifies this content across platforms (two platforms may use the same id). */
    val key: String get() = platform.name + ":" + contentId
}

/**
 * What the platform's public oEmbed service said about one piece of content. Only what the service returned (and
 * what passed the address checks) is filled in; everything else stays null and is never guessed.
 */
data class ContentInfo(
    val platform: MediaPlatform,
    val contentId: String,
    /** The title, or for X the text of the post. */
    val title: String?,
    val authorName: String?,
    /** Always an https address on one of the platform's own page domains, or null. */
    val authorUrl: String?,
    /** Always an https address on one of the platform's image hosts, or null. */
    val thumbnailUrl: String?,
    val thumbnailWidthPx: Int?,
    val thumbnailHeightPx: Int?,
    val durationSeconds: Int?,
    /** "yyyy-MM-dd" when the service gave a publishing date. */
    val publishedOn: String?,
    val sourceLabel: String,
) {
    /** "Portrait", "Landscape" or "Square" (from the thumbnail's size) when the size is known. */
    val orientation: String?
        get() {
            val w = thumbnailWidthPx ?: return null
            val h = thumbnailHeightPx ?: return null
            if (w <= 0 || h <= 0) return null
            return when {
                h > w -> "Portrait"
                w > h -> "Landscape"
                else -> "Square"
            }
        }

    /** "3:25" or "1:02:03" when a length is known. */
    val durationText: String?
        get() {
            val total = durationSeconds ?: return null
            if (total <= 0) return null
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) {
                h.toString() + ":" + m.toString().padStart(2, '0') + ":" + s.toString().padStart(2, '0')
            } else {
                m.toString() + ":" + s.toString().padStart(2, '0')
            }
        }
}

/** Why a platform gave no information about content. Platforms do not say which of several reasons applies. */
enum class UnavailableReason { NotShared, NotFoundOrUnsupported }

/** Why the lookup itself failed (as opposed to the content being unavailable). */
enum class LookupProblem { Network, RateLimited, BadResponse, ServiceError }

sealed interface ContentLookup {
    /** [notices] are cautions for the person. */
    data class Found(val info: ContentInfo, val notices: List<String> = emptyList()) : ContentLookup

    data class Unavailable(
        val reason: UnavailableReason,
        val message: String,
        val notices: List<String> = emptyList(),
    ) : ContentLookup

    data class Failed(val problem: LookupProblem, val message: String) : ContentLookup
}

/** A plain-language answer to "what kind of media is this?" for one address. It never claims more than is known. */
data class MediaIdentification(val summary: String, val details: List<String> = emptyList())

/** Plain instructions for getting a copy of the person's OWN content with the platform's official tools. */
data class OwnContentGuide(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val helpLabel: String,
    val helpUrl: String,
)
