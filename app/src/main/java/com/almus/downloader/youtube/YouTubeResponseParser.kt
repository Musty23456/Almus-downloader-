package com.almus.downloader.youtube

/** Reads the answers of the two official services. Pure text handling; a null result means "not understood". */
internal object YouTubeResponseParser {
    private val removedStates = setOf("deleted", "rejected", "failed")
    private val thumbnailPreference = listOf("medium", "high", "standard", "default", "maxres")

    /** The Data API `videos.list` answer for [ref]. */
    fun parseDataApi(body: String, ref: YouTubeVideoRef): YouTubeLookup? {
        val root = MiniJson.parseObject(body) ?: return null
        val items = root.list("items") ?: return null
        if (items.isEmpty()) return YouTubeLookup.Unavailable(UnavailableReason.NotFoundOrPrivate)
        val item = asObject(items.first()) ?: return null
        val id = item.str("id")
        if (id != null && id != ref.videoId) return null

        val status = item.obj("status")
        val uploadStatus = status?.str("uploadStatus")
        if (uploadStatus != null && uploadStatus in removedStates) {
            return YouTubeLookup.Unavailable(UnavailableReason.Removed)
        }
        if (status?.str("privacyStatus") == "private") {
            return YouTubeLookup.Unavailable(UnavailableReason.Private)
        }

        val snippet = item.obj("snippet") ?: return null
        val title = snippet.str("title")?.trim().orEmpty()
        if (title.isEmpty()) return null

        val details = item.obj("contentDetails")
        val live = when (snippet.str("liveBroadcastContent")) {
            "live" -> LiveState.Live
            "upcoming" -> LiveState.Upcoming
            else -> LiveState.None
        }
        val seconds = IsoDuration.parseSeconds(details?.str("duration"))
        val duration = if (live != LiveState.None || seconds == null || seconds <= 0L) null else seconds

        val channelId = snippet.str("channelId")
        val info = YouTubeVideoInfo(
            videoId = ref.videoId,
            title = title,
            channelTitle = snippet.str("channelTitle")?.trim()?.takeIf { it.isNotEmpty() },
            channelUrl = if (YouTubeLinks.isValidChannelId(channelId)) "https://www.youtube.com/channel/$channelId" else null,
            thumbnailUrl = pickThumbnail(snippet.obj("thumbnails")),
            durationSeconds = duration,
            liveState = live,
            license = when (status?.str("license")) {
                "creativeCommon" -> VideoLicense.CreativeCommons
                "youtube" -> VideoLicense.Standard
                else -> VideoLicense.Unknown
            },
            embeddable = status?.bool("embeddable"),
            ageRestricted = details?.obj("contentRating")?.str("ytRating") == "ytAgeRestricted",
            regionRestricted = details?.obj("regionRestriction") != null,
            source = MetadataSource.DataApi,
        )
        return YouTubeLookup.Found(info)
    }

    /** The oEmbed answer. oEmbed gives no duration, so none is filled in. */
    fun parseOEmbed(body: String, ref: YouTubeVideoRef): YouTubeVideoInfo? {
        val root = MiniJson.parseObject(body) ?: return null
        val title = root.str("title")?.trim().orEmpty()
        if (title.isEmpty()) return null
        return YouTubeVideoInfo(
            videoId = ref.videoId,
            title = title,
            channelTitle = root.str("author_name")?.trim()?.takeIf { it.isNotEmpty() },
            channelUrl = root.str("author_url")?.takeIf { YouTubeLinks.isTrustedChannelUrl(it) },
            thumbnailUrl = root.str("thumbnail_url")?.takeIf { YouTubeLinks.isTrustedThumbnail(it) },
            durationSeconds = null,
            source = MetadataSource.OEmbed,
        )
    }

    /** The machine-readable reason of a Data API error answer, such as "quotaExceeded", or null. */
    fun apiErrorReason(body: String): String? {
        val root = MiniJson.parseObject(body) ?: return null
        val errors = root.obj("error")?.list("errors") ?: return null
        return asObject(errors.firstOrNull())?.str("reason")
    }

    private fun pickThumbnail(thumbnails: Map<String, Any?>?): String? {
        if (thumbnails == null) return null
        for (name in thumbnailPreference) {
            val url = thumbnails.obj(name)?.str("url")
            if (YouTubeLinks.isTrustedThumbnail(url)) return url
        }
        return null
    }
}
