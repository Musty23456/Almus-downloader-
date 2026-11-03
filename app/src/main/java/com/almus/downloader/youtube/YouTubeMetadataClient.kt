package com.almus.downloader.youtube

import java.net.URLEncoder

/** One answer of an HTTP GET. [body] is already limited in size by the implementation. */
data class HttpReply(val status: Int, val body: String)

/** Thrown by a [YouTubeHttp] when the service cannot be reached (offline, timeout, blocked address, too large). */
class YouTubeNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The only door to the network for this module. Tests replace it. */
interface YouTubeHttp {
    suspend fun get(url: String, headers: Map<String, String>): HttpReply
}

/**
 * Asks YouTube's own public services about one video:
 * 1. the YouTube Data API v3 (`videos.list`), only when the person saved their own API key. It gives title,
 *    channel, thumbnail, duration, licence, embedding and age / region flags;
 * 2. YouTube oEmbed (no key needed): title, channel and thumbnail only.
 * It reads public information only. It never signs in, never sends cookies and never touches any media stream.
 */
class YouTubeMetadataClient(
    private val http: YouTubeHttp,
    private val apiKey: () -> String?,
) {
    suspend fun lookup(ref: YouTubeVideoRef): YouTubeLookup {
        val notices = mutableListOf<String>()
        val saved = apiKey()?.trim()?.takeIf { it.isNotEmpty() }
        var usableKey: String? = null
        if (saved != null) {
            if (YouTubeApiKey.isValidFormat(saved)) {
                usableKey = saved
            } else {
                notices += "The saved YouTube API key does not look valid, so it was not used. Check it in Settings."
            }
        }
        return try {
            if (usableKey != null) {
                when (val outcome = viaDataApi(ref, usableKey)) {
                    is ApiOutcome.Done -> return outcome.lookup.withNotices(notices)
                    is ApiOutcome.Fallback -> notices += outcome.notice
                }
            } else if (saved == null) {
                notices += NO_KEY_NOTICE
            }
            viaOEmbed(ref, notices)
        } catch (_: YouTubeNetworkException) {
            YouTubeLookup.Failed(LookupProblem.Network, "Could not reach YouTube. Check your connection and try again.")
        }
    }

    private sealed interface ApiOutcome {
        data class Done(val lookup: YouTubeLookup) : ApiOutcome

        data class Fallback(val notice: String) : ApiOutcome
    }

    private suspend fun viaDataApi(ref: YouTubeVideoRef, key: String): ApiOutcome {
        val url = DATA_API + "?part=snippet,contentDetails,status&id=" + ref.videoId +
            "&fields=" + URLEncoder.encode(FIELDS, "UTF-8")
        val reply = http.get(url, mapOf("x-goog-api-key" to key, "Accept" to "application/json"))
        return when {
            reply.status == 200 -> {
                val parsed = YouTubeResponseParser.parseDataApi(reply.body, ref)
                if (parsed != null) {
                    ApiOutcome.Done(parsed)
                } else {
                    ApiOutcome.Fallback("The YouTube Data API gave an answer that could not be read, so fewer details are shown.")
                }
            }
            reply.status == 400 || reply.status == 401 || reply.status == 403 -> {
                val reason = YouTubeResponseParser.apiErrorReason(reply.body)
                if (reason != null && reason in QUOTA_REASONS) {
                    ApiOutcome.Fallback(
                        "The daily limit of your YouTube API key is used up, so the duration cannot be shown right now. " +
                            "Basic details come from YouTube's oEmbed service.",
                    )
                } else {
                    ApiOutcome.Fallback(
                        "YouTube did not accept the saved API key (it may be wrong, restricted or not enabled for the " +
                            "YouTube Data API v3), so fewer details are shown.",
                    )
                }
            }
            else -> ApiOutcome.Fallback("The YouTube Data API is not answering right now, so fewer details are shown.")
        }
    }

    private suspend fun viaOEmbed(ref: YouTubeVideoRef, notices: List<String>): YouTubeLookup {
        val url = OEMBED + "?format=json&url=" + URLEncoder.encode(ref.watchUrl, "UTF-8")
        val reply = http.get(url, mapOf("Accept" to "application/json"))
        return when (reply.status) {
            200 -> {
                val info = YouTubeResponseParser.parseOEmbed(reply.body, ref)
                    ?: return YouTubeLookup.Failed(LookupProblem.BadResponse, "YouTube gave an answer that could not be read.")
                YouTubeLookup.Found(info, notices)
            }
            401, 403 -> YouTubeLookup.Unavailable(UnavailableReason.PrivateOrRestricted, notices)
            404 -> YouTubeLookup.Unavailable(UnavailableReason.NotFound, notices)
            429 -> YouTubeLookup.Failed(LookupProblem.QuotaExceeded, "YouTube is limiting requests right now. Try again later.")
            else -> YouTubeLookup.Failed(LookupProblem.ServiceError, "YouTube answered with status ${reply.status}. Try again later.")
        }
    }

    private fun YouTubeLookup.withNotices(extra: List<String>): YouTubeLookup {
        val self = this
        return when {
            extra.isEmpty() -> self
            self is YouTubeLookup.Found -> self.copy(notices = extra + self.notices)
            self is YouTubeLookup.Unavailable -> self.copy(notices = extra + self.notices)
            else -> self
        }
    }

    companion object {
        const val DATA_API = "https://www.googleapis.com/youtube/v3/videos"
        const val OEMBED = "https://www.youtube.com/oembed"

        const val NO_KEY_NOTICE =
            "Duration and age or region details need a free YouTube API key. You can add one in Settings."

        private const val FIELDS =
            "items(id,snippet(title,channelId,channelTitle,liveBroadcastContent,thumbnails)," +
                "contentDetails(duration,regionRestriction,contentRating),status(uploadStatus,privacyStatus,embeddable,license))"

        private val QUOTA_REASONS = setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded")
    }
}
