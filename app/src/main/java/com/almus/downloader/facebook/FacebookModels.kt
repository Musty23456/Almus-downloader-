package com.almus.downloader.facebook

import com.almus.downloader.platform.LinkKind

/**
 * One recognised and validated Facebook video, Reel or live video. [contentId] is always 5 to 20 digits.
 * The addresses built here come from the validated id only, never from user text.
 */
data class FacebookContentRef(val contentId: String, val kind: LinkKind) {
    /** The official page address of this content. */
    val canonicalUrl: String
        get() = when (kind) {
            LinkKind.ShortVideo -> "https://www.facebook.com/reel/$contentId"
            LinkKind.Live -> "https://www.facebook.com/watch/live/?v=$contentId"
            else -> "https://www.facebook.com/watch/?v=$contentId"
        }

    /** A short noun for messages: "Reel", "live video" or "video". */
    val noun: String
        get() = when (kind) {
            LinkKind.ShortVideo -> "Reel"
            LinkKind.Live -> "live video"
            else -> "video"
        }
}

/** Which official Facebook service an answer came from. */
enum class FacebookMetadataSource(val label: String) {
    GraphApi("Facebook Graph API (with your access token)"),
    OEmbed("Facebook oEmbed (public information)"),
}

/**
 * What Facebook's official services say about one piece of content. Only what a service returned is filled in.
 * The public oEmbed service returns no title, owner or thumbnail, so those stay null unless the person's own
 * access token let the Graph API return them.
 */
data class FacebookVideoInfo(
    val contentId: String,
    val title: String?,
    val description: String?,
    val ownerName: String?,
    /** Always an https facebook.com address built from a numeric id, or null. */
    val ownerUrl: String?,
    /** Always an https address on Facebook's image host, or null. */
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    /** Publication date as "yyyy-MM-dd", or null. */
    val publishedDate: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    /** Null when unknown. False means the owner turned embedding off. */
    val embeddable: Boolean?,
    /**
     * True only when the Graph API returned this video for the person's own access token AND the video's owner is
     * the account that token belongs to. Nothing else ever sets it.
     */
    val ownedByViewer: Boolean = false,
    /**
     * The raw file address the Graph API returned for the owner's own video. Kept only when [ownedByViewer] is
     * true, and only ever used through [FacebookDownloadPolicy]. Null for every other video.
     */
    val ownerFileUrl: String? = null,
    val source: FacebookMetadataSource,
) {
    /** "Portrait", "Landscape" or "Square" when the size is known. */
    val orientation: String?
        get() {
            val w = widthPx ?: return null
            val h = heightPx ?: return null
            if (w <= 0 || h <= 0) return null
            return when {
                h > w -> "Portrait"
                w > h -> "Landscape"
                else -> "Square"
            }
        }
}

/** Why Facebook gave no information about a piece of content. Facebook rarely says which of several reasons applies. */
enum class FacebookUnavailableReason(val message: String) {
    NotPublic(
        "Facebook does not share this with apps. It is private, limited to certain people or a group, needs a " +
            "sign-in, or its owner turned sharing off. Facebook does not say which.",
    ),
    NotFoundOrUnsupported(
        "Facebook returned no information for this address. The content may have been removed, it may be private, " +
            "or it may be a kind of content Facebook does not share with apps (some Reels, for example). " +
            "Facebook does not say which.",
    ),
}

/** Why the lookup itself failed (as opposed to the content being unavailable). */
enum class FacebookProblem { Network, RateLimited, BadResponse, ServiceError }

sealed interface FacebookLookup {
    /** [notices] are cautions for the person, for example that the saved token was rejected and fewer details are shown. */
    data class Found(val info: FacebookVideoInfo, val notices: List<String> = emptyList()) : FacebookLookup

    data class Unavailable(val reason: FacebookUnavailableReason, val notices: List<String> = emptyList()) : FacebookLookup

    data class Failed(val problem: FacebookProblem, val message: String) : FacebookLookup
}

/** The shape of a Meta access token, checked before it is ever sent anywhere. */
object FacebookAccessToken {
    private val format = Regex("^[A-Za-z0-9_|\\-]{20,512}$")

    fun isValidFormat(token: String?): Boolean = token != null && format.matches(token)
}
