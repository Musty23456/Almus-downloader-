package com.almus.downloader.integration

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/** A file that an official mechanism of the platform or the content owner explicitly offers for saving. [fileUrl] is a direct file address. */
data class DownloadOffer(val label: String, val fileUrl: String, val authorizedBy: String)

/**
 * The only door through which content of these platforms could ever become downloadable here: a provider that
 * represents an official download mechanism of the platform, or an explicit offer by the content owner. NO provider
 * is shipped, because none of these platforms publishes a public download service for third-party apps. Offers that
 * point back at the platform's own hosts are dropped.
 */
fun interface DownloadProvider {
    fun offersFor(info: ContentInfo): List<DownloadOffer>
}

sealed interface DownloadDecision {
    /** Explicit, permitted files exist. The address goes through the normal link check and approval. */
    data class Offered(val offers: List<DownloadOffer>) : DownloadDecision

    data class NotAvailable(
        val headline: String,
        val reasons: List<String>,
        val alternatives: List<String>,
    ) : DownloadDecision
}

class DownloadPolicy(
    private val providers: Map<MediaPlatform, List<DownloadProvider>> = emptyMap(),
    private val detector: SmartLinkDetector = SmartLinkDetector(),
) {
    fun decide(ref: ContentRef, lookup: ContentLookup): DownloadDecision {
        val spec = PlatformSpecs.of(ref.platform)
        val name = spec?.name ?: ref.platform.displayName
        val info = (lookup as? ContentLookup.Found)?.info
        if (spec != null && info != null) {
            val offers = providers[ref.platform].orEmpty()
                .flatMap { runCatching { it.offersFor(info) }.getOrDefault(emptyList()) }
                .filter { isPermittedOffer(spec, it.fileUrl) }
            if (offers.isNotEmpty()) return DownloadDecision.Offered(offers)
        }

        val reasons = (spec?.downloadReasons ?: emptyList()).toMutableList()
        if (lookup is ContentLookup.Unavailable) {
            reasons += "$name shares nothing about this with apps, so there is nothing to save. If it is private or " +
                "restricted, only people with access can see it, inside $name."
        }
        return DownloadDecision.NotAvailable(
            headline = "Downloading from $name is not available here",
            reasons = reasons,
            alternatives = spec?.downloadAlternatives ?: emptyList(),
        )
    }

    /**
     * An offer is kept only when it is a secure, public address that is not one of the platform's own hosts (a page is
     * not a file, and its media hosts are its player) and that the app's own link check would accept as a file or an
     * ordinary server link.
     */
    internal fun isPermittedOffer(spec: PlatformSpec, url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        if (!valid.isSecure) return false
        if (UrlSafety.isLocalOrPrivate(valid.host)) return false
        if (spec.isOwnedHost(valid.host)) return false
        return detector.detect(url).canCheckAsFile
    }
}
