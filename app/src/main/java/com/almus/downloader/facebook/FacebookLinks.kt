package com.almus.downloader.facebook

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.LinkDetection
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/**
 * Turns what the link detector already found into a validated content reference, and decides which addresses
 * the app may show or open. Text handling only: nothing is opened and no network is used.
 */
object FacebookLinks {
    private val idFormat = Regex("^[0-9]{5,20}$")
    private val contentKinds = setOf(LinkKind.Video, LinkKind.ShortVideo, LinkKind.Live)

    /** Kinds of Facebook pages that make sense to open in Facebook itself (not sign-in pages or file hosts). */
    private val openableKinds = setOf(
        LinkKind.Video, LinkKind.ShortVideo, LinkKind.Live, LinkKind.Post, LinkKind.Photo,
        LinkKind.Story, LinkKind.Group, LinkKind.Channel,
    )

    /** Facebook's page hosts. Image and file hosts (fbcdn.net) are deliberately not in this list. */
    private val pageDomains = listOf("facebook.com", "fb.com", "fb.watch")

    /** True for 5 to 20 digits. */
    fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    /** The video, Reel or live video behind [detection], or null when it is not a plain, usable Facebook video address. */
    fun contentRef(detection: LinkDetection): FacebookContentRef? {
        if (detection.platform != MediaPlatform.Facebook) return null
        if (detection.kind !in contentKinds) return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        val id = detection.contentId
        if (!isValidContentId(id)) return null
        return FacebookContentRef(id!!, detection.kind)
    }

    /** Detects and validates [raw] in one step. */
    fun contentRef(raw: String?, detector: SmartLinkDetector = SmartLinkDetector()): FacebookContentRef? =
        contentRef(detector.detect(raw))

    /**
     * The address of a recognised Facebook page that may be handed to Facebook (its app or a browser), or null.
     * It is the address the person entered, upgraded to https; it is never rebuilt or guessed. Sign-in pages, file
     * hosts and look-alike hosts return null. Used for restricted content that cannot be shown here but that the
     * person may be allowed to see inside Facebook.
     */
    fun openableUrl(detection: LinkDetection): String? {
        if (detection.platform != MediaPlatform.Facebook) return null
        if (detection.kind !in openableKinds) return null
        val url = detection.url ?: return null
        return toOfficialHttps(url)
    }

    /** True for an https address on facebook.com, fb.com or fb.watch (or a sub-domain of them) without credentials. */
    fun isOfficialPageUrl(url: String?): Boolean = url != null && toOfficialHttps(url) == url

    /** Thumbnails are only ever fetched from Facebook's image host over https. */
    fun isTrustedThumbnail(url: String?): Boolean = isHttpsOn(url, "fbcdn.net")

    /** A profile or page address from the API is only used when it is an https facebook.com address. */
    fun isTrustedProfileUrl(url: String?): Boolean = isHttpsOn(url, "facebook.com")

    /** A numeric owner id from the API turned into an official profile address, or null. */
    fun profileUrlFor(ownerId: String?): String? =
        if (ownerId != null && idFormat.matches(ownerId)) "https://www.facebook.com/$ownerId" else null

    private fun toOfficialHttps(url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        if (pageDomains.none { UrlSafety.hostIs(valid.host, it) }) return null
        return if (valid.isSecure) valid.url else "https://" + valid.url.removePrefix("http://")
    }

    private fun isHttpsOn(url: String?, domain: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        return valid.isSecure && UrlSafety.hostIs(valid.host, domain)
    }
}
