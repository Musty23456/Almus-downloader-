package com.almus.downloader.integration

import java.net.URLEncoder

/** One answer of an HTTP GET. [body] is already limited in size by the implementation. */
data class OEmbedReply(val status: Int, val body: String)

/** Thrown by an [OEmbedHttp] when the service cannot be reached (offline, timeout, refused address, too large). */
class OEmbedNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The only door to the network for this module's metadata. Tests replace it. */
interface OEmbedHttp {
    suspend fun get(url: String, headers: Map<String, String>): OEmbedReply
}

/**
 * Asks a platform's own public oEmbed service about one piece of content. It returns what the platform chooses to
 * share for content that is public and embeddable. The request carries no credentials, cookies or token, the module
 * never signs in, never reads a web page and never touches a media stream. What the service refuses to say stays
 * unknown, and every refusal is reported as it is: this class never tries another route.
 */
class OEmbedClient(private val http: OEmbedHttp) {

    suspend fun lookup(ref: ContentRef): ContentLookup {
        val spec = PlatformSpecs.of(ref.platform)
            ?: return ContentLookup.Failed(LookupProblem.BadResponse, "This kind of link is not handled here.")
        val name = spec.name
        val reply = try {
            http.get(requestUrl(spec, ref), mapOf("Accept" to "application/json"))
        } catch (_: OEmbedNetworkException) {
            return ContentLookup.Failed(LookupProblem.Network, "Could not reach $name. Check your connection and try again.")
        }

        if (reply.status == 200) {
            val info = OEmbedParser.parse(reply.body, ref, spec)
            if (info != null) return ContentLookup.Found(info)
            return if (OEmbedParser.apiError(reply.body) != null) {
                unavailable(spec, UnavailableReason.NotFoundOrUnsupported)
            } else {
                ContentLookup.Failed(LookupProblem.BadResponse, "$name gave an answer that could not be read.")
            }
        }

        val errorCode = OEmbedParser.apiError(reply.body)?.code
        return when {
            reply.status == 429 || (errorCode != null && errorCode in spec.rateLimitCodes) ->
                ContentLookup.Failed(LookupProblem.RateLimited, "$name is limiting requests right now. Try again later.")
            reply.status == 401 || reply.status == 403 -> unavailable(spec, UnavailableReason.NotShared)
            reply.status == 400 || reply.status == 404 || reply.status == 410 ->
                unavailable(spec, UnavailableReason.NotFoundOrUnsupported)
            reply.status in 500..599 ->
                ContentLookup.Failed(LookupProblem.ServiceError, "$name is not answering right now (status ${reply.status}). Try again later.")
            else ->
                ContentLookup.Failed(LookupProblem.ServiceError, "$name answered with status ${reply.status}. Try again later.")
        }
    }

    private fun unavailable(spec: PlatformSpec, reason: UnavailableReason): ContentLookup =
        ContentLookup.Unavailable(reason, spec.unavailableMessage(reason))

    companion object {
        /** The full request address: the platform's fixed service address, its fixed parameters, then `url`. */
        fun requestUrl(spec: PlatformSpec, ref: ContentRef): String {
            val sb = StringBuilder("https://")
            sb.append(spec.oembed.host).append(spec.oembed.path).append('?')
            for (param in spec.oembed.fixedParams) {
                sb.append(param.first).append('=').append(URLEncoder.encode(param.second, "UTF-8")).append('&')
            }
            sb.append("url=").append(URLEncoder.encode(spec.oembedSubject(ref), "UTF-8"))
            return sb.toString()
        }
    }
}
