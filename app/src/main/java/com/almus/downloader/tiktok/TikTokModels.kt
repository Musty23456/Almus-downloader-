package com.almus.downloader.tiktok

/**
 * One recognised and validated TikTok video. [contentId] is always 15 to 20 digits. [handle] is the creator's
 * TikTok name without the "@" when the address carried a valid one, else null (embed, player and legacy addresses
 * carry none). The addresses built here come from these validated parts only, never from user text, so share and
 * tracking parameters of the original link are never passed on.
 */
data class TikTokContentRef(val contentId: String, val handle: String? = null) {
    /** The official page address of this video. Without a creator name the name part stays empty ("/@/video/ID"). */
    val canonicalUrl: String
        get() = "https://www.tiktok.com/@" + (handle ?: "") + "/video/" + contentId
}

/**
 * What TikTok's public oEmbed service says about one video. Only what the service returned is filled in.
 * [caption] is the text the creator wrote (TikTok's oEmbed calls it the title). TikTok returns no length, date or
 * view counts, so none are shown.
 */
data class TikTokVideoInfo(
    val contentId: String,
    val caption: String?,
    val creatorName: String?,
    /** The creator's TikTok name without the "@", validated, or null. */
    val creatorHandle: String?,
    /** Always an https tiktok.com profile address built from a validated name, or null. */
    val creatorUrl: String?,
    /** Always an https address on one of TikTok's image hosts, or null. */
    val thumbnailUrl: String?,
    val thumbnailWidthPx: Int?,
    val thumbnailHeightPx: Int?,
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

    companion object {
        const val SOURCE_LABEL = "TikTok oEmbed (public information)"
    }
}

/** Why TikTok gave no information about a video. TikTok does not say which of several reasons applies. */
enum class TikTokUnavailableReason(val message: String) {
    NotShared(
        "TikTok does not share this video with apps. It may be private, limited to certain people, removed, " +
            "restricted for your region or age group, or TikTok may have refused the request. TikTok does not say which.",
    ),
    NotFoundOrUnsupported(
        "TikTok returned no information for this address. The video may have been removed, it may be private, " +
            "or it may be a kind of post TikTok does not share with apps. TikTok does not say which.",
    ),
}

/** Why the lookup itself failed (as opposed to the video being unavailable). */
enum class TikTokProblem { Network, RateLimited, BadResponse, ServiceError }

sealed interface TikTokLookup {
    /** [notices] are cautions for the person. */
    data class Found(val info: TikTokVideoInfo, val notices: List<String> = emptyList()) : TikTokLookup

    data class Unavailable(val reason: TikTokUnavailableReason, val notices: List<String> = emptyList()) : TikTokLookup

    data class Failed(val problem: TikTokProblem, val message: String) : TikTokLookup
}
