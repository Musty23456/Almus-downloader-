package com.almus.downloader.youtube

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.LinkDetection
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/**
 * Turns what the link detector already found into a validated video reference.
 * Text handling only: nothing is opened and no network is used.
 */
object YouTubeLinks {
    private val idFormat = Regex("^[A-Za-z0-9_-]{11}$")
    private val channelIdFormat = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val videoKinds = setOf(LinkKind.Video, LinkKind.ShortVideo, LinkKind.Live)

    /** True for an 11-character video id made only of letters, digits, '_' and '-'. */
    fun isValidVideoId(id: String?): Boolean = id != null && idFormat.matches(id)

    fun isValidChannelId(id: String?): Boolean = id != null && channelIdFormat.matches(id)

    /** The video behind [detection], or null when it is not a plain, usable YouTube video address. */
    fun videoRef(detection: LinkDetection): YouTubeVideoRef? {
        if (detection.platform != MediaPlatform.YouTube) return null
        if (detection.kind !in videoKinds) return null
        if (detection.verdict == LinkVerdict.Restricted) return null
        val id = detection.contentId
        if (!isValidVideoId(id)) return null
        return YouTubeVideoRef(id!!, detection.kind)
    }

    /** Detects and validates [raw] in one step. */
    fun videoRef(raw: String?, detector: SmartLinkDetector = SmartLinkDetector()): YouTubeVideoRef? =
        videoRef(detector.detect(raw))

    /** Thumbnails and channel pages are only ever used from official YouTube hosts over https. */
    fun isTrustedThumbnail(url: String?): Boolean = isHttpsOn(url, "ytimg.com")

    fun isTrustedChannelUrl(url: String?): Boolean = isHttpsOn(url, "youtube.com")

    private fun isHttpsOn(url: String?, domain: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        return valid.isSecure && UrlSafety.hostIs(valid.host, domain)
    }
}
