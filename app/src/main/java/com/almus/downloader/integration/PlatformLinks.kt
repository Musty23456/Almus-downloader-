package com.almus.downloader.integration

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.LinkDetection
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.platform.ParsedUrl
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/**
 * Turns what the link detector already found into a validated [ContentRef], and decides which addresses the app may
 * show, open or fetch a picture from. Text handling only: nothing is opened and no network is used. The same checks
 * serve every platform; what differs lives in the platform's [PlatformSpec].
 */
object PlatformLinks {

    /** The content behind [detection], or null when it is not a plain, usable address of one of these platforms. */
    fun contentRef(detection: LinkDetection): ContentRef? {
        val platform = detection.platform ?: return null
        val spec = PlatformSpecs.of(platform) ?: return null
        if (detection.kind !in spec.contentKinds) return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        val id = detection.contentId
        if (!spec.isValidContentId(id)) return null
        return ContentRef(platform, detection.kind, id!!, handleOf(spec, detection.url))
    }

    /** Detects and validates [raw] in one step. */
    fun contentRef(raw: String?, detector: SmartLinkDetector = SmartLinkDetector()): ContentRef? =
        contentRef(detector.detect(raw))

    /**
     * The address of a recognised page that may be handed to the platform (its app or a browser), or null. For content
     * it is rebuilt from the validated reference. For a profile, board or similar page it is the address the person
     * entered, upgraded to https, with the query and fragment removed (they carry share and tracking data). Sign-in
     * pages, stories, short links, file hosts and look-alike hosts return null.
     */
    fun openableUrl(detection: LinkDetection): String? {
        val platform = detection.platform ?: return null
        val spec = PlatformSpecs.of(platform) ?: return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        return when (detection.kind) {
            in spec.contentKinds -> contentRef(detection)?.let { spec.canonicalUrl(it) }
            in spec.openableKinds -> officialPageUrl(spec, detection.url)
            else -> null
        }
    }

    /** True for an https address on the platform's page domains without credentials, port, query or fragment. */
    fun isOfficialPageUrl(spec: PlatformSpec, url: String?): Boolean = url != null && officialPageUrl(spec, url) == url

    /** True only for the Help Center address the platform's own guide names. */
    fun isOfficialHelpUrl(spec: PlatformSpec, url: String?): Boolean = url != null && url == spec.ownContent.helpUrl

    /** Thumbnails are only ever fetched from the platform's image hosts over https. */
    fun isTrustedThumbnail(spec: PlatformSpec, url: String?): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        return valid.isSecure && spec.thumbnailDomains.any { UrlSafety.hostIs(valid.host, it) }
    }

    /** True when [url] is trusted as a thumbnail for any of the platforms in [PlatformSpecs]. */
    fun isTrustedThumbnailAnywhere(url: String?): Boolean = PlatformSpecs.all.any { isTrustedThumbnail(it, url) }

    /** A creator address from a service answer: kept only when it is an https page address of the platform. */
    fun trustedAuthorUrl(spec: PlatformSpec, url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        if (!valid.isSecure) return null
        return officialPageUrl(spec, valid.url)
    }

    internal fun officialPageUrl(spec: PlatformSpec, url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        if (!spec.isPageHost(valid.host)) return null
        if (ParsedUrl.parse(valid).port != null) return null
        val bare = valid.url.substringBefore('#').substringBefore('?')
        return if (valid.isSecure) bare else "https://" + bare.removePrefix("http://")
    }

    private fun handleOf(spec: PlatformSpec, url: String?): String? {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return null
        return spec.handleOf(ParsedUrl.parse(valid))
    }
}
