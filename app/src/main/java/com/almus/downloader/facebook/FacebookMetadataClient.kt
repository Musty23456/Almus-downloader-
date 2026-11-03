package com.almus.downloader.facebook

import java.net.URLEncoder

/** One answer of an HTTP GET. [body] is already limited in size by the implementation. */
data class FacebookReply(val status: Int, val body: String)

/** Thrown by a [FacebookHttp] when the service cannot be reached (offline, timeout, blocked address, too large). */
class FacebookNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The only door to the network for this module. Tests replace it. */
interface FacebookHttp {
    suspend fun get(url: String, headers: Map<String, String>): FacebookReply
}

/**
 * Asks Facebook's own official services about one video, Reel or live video:
 * 1. the Graph API video node, only when the person saved their own access token. It can return title, owner,
 *    thumbnail and length, and Facebook decides what that token may read. For a video that belongs to the
 *    account of the token, it can also return the file itself;
 * 2. Facebook oEmbed (no token needed): tells whether the content is public and embeddable, plus its size.
 * It reads what Facebook chooses to return. It never signs in, never sends cookies, never reads a web page and
 * never touches a media stream. oEmbed requests carry no credentials at all.
 */
class FacebookMetadataClient(
    private val http: FacebookHttp,
    private val accessToken: () -> String?,
) {
    suspend fun lookup(ref: FacebookContentRef): FacebookLookup {
        val notices = mutableListOf<String>()
        val saved = accessToken()?.trim()?.takeIf { it.isNotEmpty() }
        var usable: String? = null
        if (saved != null) {
            if (FacebookAccessToken.isValidFormat(saved)) {
                usable = saved
            } else {
                notices += "The saved Facebook access token does not look valid, so it was not used. Check it in Settings."
            }
        }
        return try {
            if (usable != null) {
                when (val outcome = viaGraph(ref, usable)) {
                    is GraphOutcome.Done -> return outcome.lookup.withNotices(notices)
                    is GraphOutcome.Fallback -> notices += outcome.notice
                }
            } else if (saved == null) {
                notices += NO_TOKEN_NOTICE
            }
            viaOEmbed(ref, notices)
        } catch (_: FacebookNetworkException) {
            FacebookLookup.Failed(FacebookProblem.Network, "Could not reach Facebook. Check your connection and try again.")
        }
    }

    private sealed interface GraphOutcome {
        data class Done(val lookup: FacebookLookup) : GraphOutcome

        data class Fallback(val notice: String) : GraphOutcome
    }

    private suspend fun viaGraph(ref: FacebookContentRef, token: String): GraphOutcome {
        val headers = authHeaders(token)
        var reply = http.get(videoUrl(ref, FIELDS_FULL), headers)
        if (reply.status != 200 && isUnknownFieldError(reply.body)) {
            // Facebook rejects a whole request when one field name is not known to this API version. Ask for less.
            reply = http.get(videoUrl(ref, FIELDS_CORE), headers)
        }
        if (reply.status == 200) {
            val video = FacebookResponseParser.parseGraphVideo(reply.body, ref)
                ?: return GraphOutcome.Fallback(
                    "The Facebook Graph API gave an answer that could not be read, so fewer details are shown.",
                )
            return GraphOutcome.Done(FacebookLookup.Found(withOwnership(video, headers)))
        }
        return GraphOutcome.Fallback(noticeFor(reply))
    }

    /**
     * A file address is kept only when the video's owner is the account the token belongs to. Anything else
     * (another person's public video, a failed check) loses the address, so it can never become an offer.
     */
    private suspend fun withOwnership(video: GraphVideo, headers: Map<String, String>): FacebookVideoInfo {
        val ownerId = video.ownerId
        val file = video.fileUrl
        if (ownerId == null || file == null) return video.info
        val meId = try {
            val reply = http.get(ME_URL, headers)
            if (reply.status == 200) FacebookResponseParser.parseMeId(reply.body) else null
        } catch (_: FacebookNetworkException) {
            null
        }
        return if (meId != null && meId == ownerId) {
            video.info.copy(ownedByViewer = true, ownerFileUrl = file)
        } else {
            video.info
        }
    }

    private suspend fun viaOEmbed(ref: FacebookContentRef, notices: List<String>): FacebookLookup {
        val url = "$GRAPH/oembed_video?url=" + URLEncoder.encode(ref.canonicalUrl, "UTF-8") + "&omitscript=true"
        val reply = http.get(url, mapOf("Accept" to "application/json"))
        if (reply.status == 200) {
            val info = FacebookResponseParser.parseOEmbed(reply.body, ref)
                ?: return FacebookLookup.Failed(FacebookProblem.BadResponse, "Facebook gave an answer that could not be read.")
            return FacebookLookup.Found(info, notices)
        }
        val error = FacebookResponseParser.apiError(reply.body)
        return when {
            error?.code in RATE_LIMIT_CODES || reply.status == 429 ->
                FacebookLookup.Failed(
                    FacebookProblem.RateLimited,
                    "Facebook is limiting requests right now (its public service allows a limited number per hour). Try again later.",
                )
            error?.code == 200 || error?.code == 10 || reply.status == 401 || reply.status == 403 ->
                FacebookLookup.Unavailable(FacebookUnavailableReason.NotPublic, notices)
            error?.code == 100 || reply.status == 404 ->
                FacebookLookup.Unavailable(FacebookUnavailableReason.NotFoundOrUnsupported, notices)
            else ->
                FacebookLookup.Failed(FacebookProblem.ServiceError, "Facebook answered with status ${reply.status}. Try again later.")
        }
    }

    private fun noticeFor(reply: FacebookReply): String {
        val code = FacebookResponseParser.apiError(reply.body)?.code
        return when {
            code == 190 ->
                "Facebook did not accept your saved access token (it may have expired or been revoked), so only public " +
                    "information is shown. Save a new token in Settings."
            code in RATE_LIMIT_CODES || reply.status == 429 ->
                "Facebook is limiting requests from your access token right now, so only public information is shown."
            code == 10 || code == 200 || code == 299 || code == 100 || reply.status == 403 ->
                "Your access token is not allowed to read this video (Facebook lets a token read only what its account " +
                    "may see), so only public information is shown."
            else -> "The Facebook Graph API is not answering right now, so fewer details are shown."
        }
    }

    private fun isUnknownFieldError(body: String): Boolean {
        val error = FacebookResponseParser.apiError(body) ?: return false
        return error.code == 100 && error.message?.contains("nonexisting field", ignoreCase = true) == true
    }

    private fun videoUrl(ref: FacebookContentRef, fields: String): String =
        "$GRAPH/${ref.contentId}?fields=" + URLEncoder.encode(fields, "UTF-8")

    /** The token travels in a header, never in the address. */
    private fun authHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")

    private fun FacebookLookup.withNotices(extra: List<String>): FacebookLookup {
        val self = this
        return when {
            extra.isEmpty() -> self
            self is FacebookLookup.Found -> self.copy(notices = extra + self.notices)
            self is FacebookLookup.Unavailable -> self.copy(notices = extra + self.notices)
            else -> self
        }
    }

    companion object {
        /** Facebook's Graph API version. Facebook retires old versions after about two years. */
        const val GRAPH_VERSION = "v26.0"
        const val GRAPH = "https://graph.facebook.com/$GRAPH_VERSION"
        const val ME_URL = "$GRAPH/me?fields=id"

        const val NO_TOKEN_NOTICE =
            "Title, owner and thumbnail need your own Facebook access token. You can add one in Settings. " +
                "Without it, Facebook's public service only says whether the content is public, and its size."

        private const val FIELDS_FULL = "id,title,description,picture,length,from,created_time,embeddable,source"
        private const val FIELDS_CORE = "id,description,picture,from,created_time,source"

        /** Graph API error codes for "too many calls". */
        private val RATE_LIMIT_CODES = setOf(4, 17, 32, 613)
    }
}
