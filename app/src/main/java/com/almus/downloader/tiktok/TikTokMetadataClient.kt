package com.almus.downloader.tiktok

import java.net.URLEncoder

/** One answer of an HTTP GET. [body] is already limited in size by the implementation. */
data class TikTokReply(val status: Int, val body: String)

/** Thrown by a [TikTokHttp] when the service cannot be reached (offline, timeout, refused address, too large). */
class TikTokNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The only door to the network for this module's metadata. Tests replace it. */
interface TikTokHttp {
    suspend fun get(url: String, headers: Map<String, String>): TikTokReply
}

/**
 * Asks TikTok's own public oEmbed service about one video (`https://www.tiktok.com/oembed?url=...`, documented by
 * TikTok for embedding videos). It returns the creator's caption and name and a thumbnail for videos that are public
 * and embeddable. The request carries no credentials, cookies or token. The module never signs in, never reads a
 * web page and never touches a media stream. What the service refuses to say stays unknown.
 */
class TikTokMetadataClient(private val http: TikTokHttp) {

    suspend fun lookup(ref: TikTokContentRef): TikTokLookup {
        val url = OEMBED_URL + "?url=" + URLEncoder.encode(ref.canonicalUrl, "UTF-8")
        val notices = if (ref.handle == null) listOf(NO_CREATOR_NOTICE) else emptyList()
        val reply = try {
            http.get(url, mapOf("Accept" to "application/json"))
        } catch (_: TikTokNetworkException) {
            return TikTokLookup.Failed(TikTokProblem.Network, "Could not reach TikTok. Check your connection and try again.")
        }

        if (reply.status == 200) {
            val info = TikTokResponseParser.parseOEmbed(reply.body, ref)
            if (info != null) return TikTokLookup.Found(info)
            return if (TikTokResponseParser.apiError(reply.body) != null) {
                TikTokLookup.Unavailable(TikTokUnavailableReason.NotFoundOrUnsupported, notices)
            } else {
                TikTokLookup.Failed(TikTokProblem.BadResponse, "TikTok gave an answer that could not be read.")
            }
        }

        val error = TikTokResponseParser.apiError(reply.body)
        return when {
            reply.status == 429 || error?.code in RATE_LIMIT_CODES ->
                TikTokLookup.Failed(TikTokProblem.RateLimited, "TikTok is limiting requests right now. Try again later.")
            reply.status == 401 || reply.status == 403 ->
                TikTokLookup.Unavailable(TikTokUnavailableReason.NotShared, notices)
            reply.status == 400 || reply.status == 404 || reply.status == 410 ->
                TikTokLookup.Unavailable(TikTokUnavailableReason.NotFoundOrUnsupported, notices)
            reply.status in 500..599 ->
                TikTokLookup.Failed(TikTokProblem.ServiceError, "TikTok is not answering right now (status ${reply.status}). Try again later.")
            else ->
                TikTokLookup.Failed(TikTokProblem.ServiceError, "TikTok answered with status ${reply.status}. Try again later.")
        }
    }

    companion object {
        const val OEMBED_URL = "https://www.tiktok.com/oembed"

        const val NO_CREATOR_NOTICE =
            "This address has no creator name, so TikTok may not recognise it. Open the video in TikTok to see it."

        /** Error codes that mean "too many requests". */
        private val RATE_LIMIT_CODES = setOf("rate_limit_exceeded", "rate_limit", "too_many_requests")
    }
}
