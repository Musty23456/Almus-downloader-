package com.almus.downloader.tiktok

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/** A file that an official TikTok mechanism or the content owner explicitly offers for saving. [fileUrl] is a direct file address. */
data class TikTokDownloadOffer(val label: String, val fileUrl: String, val authorizedBy: String)

/**
 * The only door through which a TikTok video could ever become downloadable here: a provider that represents an
 * official TikTok download mechanism, or an explicit offer by the content owner. NO provider is shipped, because
 * TikTok publishes no download API for third-party apps (its Display API returns cover images, descriptions and embed
 * links, never the video file). Offers that point back at TikTok's own hosts are dropped.
 */
fun interface TikTokDownloadProvider {
    fun offersFor(info: TikTokVideoInfo): List<TikTokDownloadOffer>
}

sealed interface TikTokDownloadDecision {
    /** Explicit, permitted files exist. The address goes through the normal link check and approval. */
    data class Offered(val offers: List<TikTokDownloadOffer>) : TikTokDownloadDecision

    data class NotAvailable(
        val headline: String,
        val reasons: List<String>,
        val alternatives: List<String>,
    ) : TikTokDownloadDecision
}

class TikTokDownloadPolicy(
    private val providers: List<TikTokDownloadProvider> = emptyList(),
    private val detector: SmartLinkDetector = SmartLinkDetector(),
) {
    fun decide(lookup: TikTokLookup): TikTokDownloadDecision {
        val info = (lookup as? TikTokLookup.Found)?.info
        if (info != null) {
            val offers = providers
                .flatMap { runCatching { it.offersFor(info) }.getOrDefault(emptyList()) }
                .filter { isPermittedOffer(it.fileUrl) }
            if (offers.isNotEmpty()) return TikTokDownloadDecision.Offered(offers)
        }

        val reasons = mutableListOf(
            "TikTok offers apps no public download service. It delivers video through its own player, and Almus " +
                "Downloader does not take videos out of that player, remove marks from them or get around TikTok's access rules.",
            "Creators choose whether other people may save their videos, and some accounts (private ones, for example) " +
                "cannot be saved from at all.",
        )
        if (lookup is TikTokLookup.Unavailable) {
            reasons += "TikTok shares nothing about this video with apps, so there is nothing to save. If it is private " +
                "or restricted, only people with access can watch it, inside TikTok."
        }
        return TikTokDownloadDecision.NotAvailable(
            headline = "Downloading from TikTok is not available here",
            reasons = reasons,
            alternatives = listOf(
                "In the TikTok app, tap Share and then Save video. TikTok shows that button only when the creator allows saving.",
                "If this is your own video, get your copy from your own profile in the TikTok app, or request your data (see \"Your own videos\").",
                "If the owner publishes the video as a file themselves, paste that direct file link into Almus Downloader.",
            ),
        )
    }

    /**
     * An offer is kept only when it is a secure, public address that is not one of TikTok's own hosts (a TikTok page
     * is not a file, and its media hosts are its player) and that the app's own link check would accept as a file or an
     * ordinary server link.
     */
    internal fun isPermittedOffer(url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        if (!valid.isSecure) return false
        if (UrlSafety.isLocalOrPrivate(valid.host)) return false
        if (tiktokOwned.any { UrlSafety.hostIs(valid.host, it) }) return false
        return detector.detect(url).canCheckAsFile
    }

    private companion object {
        val tiktokOwned = listOf(
            "tiktok.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokcdn-eu.com", "tiktokv.com", "tiktokv.us",
            "muscdn.com", "musical.ly", "byteoversea.com", "ibytedtos.com", "ibyteimg.com",
        )
    }
}
