package com.almus.downloader.tiktok

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.LinkDetection
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.ParsedUrl
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/**
 * Turns what the link detector already found into a validated content reference, and decides which addresses
 * the app may show or open. Text handling only: nothing is opened and no network is used.
 */
object TikTokLinks {
    private val idFormat = Regex("^[0-9]{15,20}$")
    private val handleFormat = Regex("^[A-Za-z0-9._]{1,24}$")

    /** Kinds of TikTok pages that make sense to open in TikTok itself (not sign-in pages, short links or file hosts). */
    private val openableKinds = setOf(LinkKind.Video, LinkKind.Photo, LinkKind.Live, LinkKind.Channel)

    /** TikTok's page host. Image and file hosts (tiktokcdn.com and others) are deliberately not in this list. */
    private const val PAGE_DOMAIN = "tiktok.com"

    /** Hosts a thumbnail may come from. A thumbnail on any other host is dropped, so no picture is ever fetched from it. */
    private val thumbnailDomains = listOf("tiktokcdn.com", "tiktokcdn-us.com", "tiktokcdn-eu.com", "muscdn.com")

    /** True for 15 to 20 digits (TikTok video ids are 64-bit numbers; real ones have 18 or 19 digits). */
    fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    /** True for 1 to 24 letters, digits, underscores or periods (the "@" is not part of the name). */
    fun isValidHandle(handle: String?): Boolean = handle != null && handleFormat.matches(handle)

    /** The video behind [detection], or null when it is not a plain, usable TikTok video address. */
    fun contentRef(detection: LinkDetection): TikTokContentRef? {
        if (detection.platform != MediaPlatform.TikTok) return null
        if (detection.kind != LinkKind.Video) return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        val id = detection.contentId
        if (!isValidContentId(id)) return null
        return TikTokContentRef(id!!, handleOf(detection.url))
    }

    /** Detects and validates [raw] in one step. */
    fun contentRef(raw: String?, detector: SmartLinkDetector = SmartLinkDetector()): TikTokContentRef? =
        contentRef(detector.detect(raw))

    /**
     * The address of a recognised TikTok page that may be handed to TikTok (its app or a browser), or null. For a
     * video it is rebuilt from the validated id and name. For a photo post, live page or profile it is the address the
     * person entered, upgraded to https, with the query and fragment removed (they carry share and tracking data).
     * Sign-in pages, short links, file hosts and look-alike hosts return null.
     */
    fun openableUrl(detection: LinkDetection): String? {
        if (detection.platform != MediaPlatform.TikTok) return null
        if (detection.kind !in openableKinds) return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        if (detection.kind == LinkKind.Video) return contentRef(detection)?.canonicalUrl
        return toOfficialHttps(detection.url)
    }

    /** True for an https address on tiktok.com (or a sub-domain) without credentials, query or fragment. */
    fun isOfficialPageUrl(url: String?): Boolean = url != null && toOfficialHttps(url) == url

    /** Thumbnails are only ever fetched from TikTok's image hosts over https. */
    fun isTrustedThumbnail(url: String?): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        return valid.isSecure && thumbnailDomains.any { UrlSafety.hostIs(valid.host, it) }
    }

    /** A creator's profile address built from a validated name, or null. */
    fun profileUrlFor(handle: String?): String? =
        if (isValidHandle(handle)) "https://www.tiktok.com/@$handle" else null

    /**
     * The creator name in a profile address the service returned ("https://www.tiktok.com/@name"), or null when it is
     * not an https tiktok.com profile address with a valid name.
     */
    fun handleFromProfileUrl(url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        if (!valid.isSecure || !UrlSafety.hostIs(valid.host, PAGE_DOMAIN)) return null
        val segments = ParsedUrl.parse(valid).segments
        if (segments.size != 1 || !segments[0].startsWith("@")) return null
        val name = segments[0].removePrefix("@")
        return if (isValidHandle(name)) name else null
    }

    /** The creator name of a `/@name/video/ID` address, or null for every other shape. */
    private fun handleOf(url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        val segments = ParsedUrl.parse(valid).segments
        if (segments.size < 3 || segments[1].lowercase() != "video" || !segments[0].startsWith("@")) return null
        val name = segments[0].removePrefix("@")
        return if (isValidHandle(name)) name else null
    }

    private fun toOfficialHttps(url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        if (!UrlSafety.hostIs(valid.host, PAGE_DOMAIN)) return null
        val bare = valid.url.substringBefore('#').substringBefore('?')
        return if (valid.isSecure) bare else "https://" + bare.removePrefix("http://")
    }
}
