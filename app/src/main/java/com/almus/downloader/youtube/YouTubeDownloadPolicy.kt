package com.almus.downloader.youtube

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.UrlSafety

/** A file the owner or an official YouTube mechanism explicitly offers for saving. [fileUrl] is a direct file address. */
data class AuthorizedDownloadOffer(val label: String, val fileUrl: String, val authorizedBy: String)

/**
 * The only door through which a YouTube video could ever become downloadable here: a provider that represents an
 * official YouTube download mechanism, or an explicit offer by the content owner. NO provider is shipped, because
 * YouTube publishes no download API for third-party apps. Offers that point back at YouTube's own hosts are dropped.
 */
fun interface AuthorizedDownloadProvider {
    fun offersFor(info: YouTubeVideoInfo): List<AuthorizedDownloadOffer>
}

sealed interface DownloadDecision {
    /** Explicit, permitted files exist. The address goes through the normal link check and approval. */
    data class Offered(val offers: List<AuthorizedDownloadOffer>) : DownloadDecision

    data class NotAvailable(
        val headline: String,
        val reasons: List<String>,
        val alternatives: List<String>,
    ) : DownloadDecision
}

class YouTubeDownloadPolicy(private val providers: List<AuthorizedDownloadProvider> = emptyList()) {

    fun decide(lookup: YouTubeLookup): DownloadDecision {
        val info = (lookup as? YouTubeLookup.Found)?.info
        if (info != null) {
            val offers = providers
                .flatMap { runCatching { it.offersFor(info) }.getOrDefault(emptyList()) }
                .filter { isPermittedOffer(it.fileUrl) }
            if (offers.isNotEmpty()) return DownloadDecision.Offered(offers)
        }

        val reasons = mutableListOf(
            "YouTube offers no public download service or API that apps like Almus Downloader may use. " +
                "Saving a video would mean taking it out of YouTube's streaming system, which Almus Downloader does not do.",
        )
        when (lookup) {
            is YouTubeLookup.Found -> reasons += reasonsFor(lookup.info)
            is YouTubeLookup.Unavailable -> reasons += "This video is not available to apps right now, so there is nothing to save."
            is YouTubeLookup.Failed -> Unit
        }
        return DownloadDecision.NotAvailable(
            headline = "Downloading from YouTube is not available here",
            reasons = reasons,
            alternatives = listOf(
                "Use the download feature inside the YouTube app where YouTube and the owner allow it " +
                    "(YouTube Premium may be needed). The video then stays inside the YouTube app.",
                "If the owner publishes the video as a file themselves, paste that direct file link into Almus Downloader.",
            ),
        )
    }

    private fun reasonsFor(info: YouTubeVideoInfo): List<String> {
        val list = mutableListOf<String>()
        if (info.liveState != LiveState.None) list += "Live streams and upcoming premieres cannot be saved."
        if (info.ageRestricted) {
            list += "This video is age-restricted. YouTube only shows it to signed-in adults inside its own apps. " +
                "Almus Downloader never signs in for you and never goes around age checks."
        }
        if (info.regionRestricted) {
            list += "YouTube limits the countries where this video can be watched. Almus Downloader does not work around regional limits."
        }
        if (info.license == VideoLicense.CreativeCommons) {
            list += "The owner marked this video with a Creative Commons licence. That licence describes what reuse the owner " +
                "permits, but YouTube still offers no download service for apps, so Almus Downloader cannot fetch it. " +
                "Read the licence terms before reusing any video."
        }
        return list
    }

    companion object {
        private val youTubeOwned = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "ytimg.com", "googlevideo.com")

        /** An offer is kept only when it is a secure, public address that is NOT one of YouTube's own hosts. */
        fun isPermittedOffer(url: String): Boolean {
            val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
            if (!valid.isSecure) return false
            if (UrlSafety.isLocalOrPrivate(valid.host)) return false
            return youTubeOwned.none { UrlSafety.hostIs(valid.host, it) }
        }
    }
}
