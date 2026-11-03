package com.almus.downloader.youtube

import com.almus.downloader.platform.LinkKind

/** One recognised and validated YouTube video address. [videoId] is always 11 safe characters. */
data class YouTubeVideoRef(val videoId: String, val kind: LinkKind) {
    /** The official page address. Built from the validated id only, never copied from user text. */
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

/** Which official service an answer came from. */
enum class MetadataSource(val label: String) {
    DataApi("YouTube Data API"),
    OEmbed("YouTube oEmbed"),
}

enum class LiveState { None, Live, Upcoming }

enum class VideoLicense { Standard, CreativeCommons, Unknown }

/** Public facts about one video, as the official services report them. Only what the services returned is filled in. */
data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val channelTitle: String?,
    val channelUrl: String?,
    /** Always an https address on a YouTube image host, or null. */
    val thumbnailUrl: String?,
    /** Null when the service does not tell (oEmbed never does) or the video is live. */
    val durationSeconds: Long?,
    val liveState: LiveState = LiveState.None,
    val license: VideoLicense = VideoLicense.Unknown,
    /** Null when unknown. False means the owner turned embedding off. */
    val embeddable: Boolean? = null,
    val ageRestricted: Boolean = false,
    val regionRestricted: Boolean = false,
    val source: MetadataSource,
)

/** Why a video could not be shown. YouTube often does not say which of several reasons applies; the text says so. */
enum class UnavailableReason(val message: String) {
    Removed("YouTube reports that this video was removed, rejected or never finished processing."),
    Private("This video is private. Only people the owner chose can watch it."),
    NotFound("YouTube says this video does not exist. It may have been deleted, or the link may be wrong."),
    NotFoundOrPrivate(
        "YouTube returned no information for this video. It was deleted, is private, or is not shared with apps. " +
            "YouTube does not say which.",
    ),
    PrivateOrRestricted(
        "YouTube does not share information about this video with apps. It may be private or age-restricted, or " +
            "the owner may have turned off embedding. YouTube does not say which.",
    ),
}

/** Why the lookup itself failed (as opposed to the video being unavailable). */
enum class LookupProblem { Network, QuotaExceeded, BadResponse, ServiceError }

sealed interface YouTubeLookup {
    /** [notices] are cautions for the person, for example that the API key was rejected and fewer details are shown. */
    data class Found(val info: YouTubeVideoInfo, val notices: List<String> = emptyList()) : YouTubeLookup

    data class Unavailable(val reason: UnavailableReason, val notices: List<String> = emptyList()) : YouTubeLookup

    data class Failed(val problem: LookupProblem, val message: String) : YouTubeLookup
}

/** The shape of a Google API key, checked before it is ever sent anywhere. */
object YouTubeApiKey {
    private val format = Regex("^[A-Za-z0-9_-]{20,80}$")

    fun isValidFormat(key: String?): Boolean = key != null && format.matches(key)
}
