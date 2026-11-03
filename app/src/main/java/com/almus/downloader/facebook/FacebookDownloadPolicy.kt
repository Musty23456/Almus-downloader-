package com.almus.downloader.facebook

import com.almus.downloader.download.UrlValidation
import com.almus.downloader.download.UrlValidator
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.platform.UrlSafety

/** A file that Facebook's official API or the content owner explicitly offers for saving. [fileUrl] is a direct file address. */
data class FacebookDownloadOffer(val label: String, val fileUrl: String, val authorizedBy: String)

/**
 * The only door through which Facebook content can become downloadable here: a provider that represents an
 * official Facebook mechanism, or an explicit offer by the content owner.
 */
fun interface FacebookDownloadProvider {
    fun offersFor(info: FacebookVideoInfo): List<FacebookDownloadOffer>
}

/**
 * The one provider that ships. It offers a file only for a video that the Graph API returned for the person's OWN
 * access token and whose owner is the account of that token ([FacebookVideoInfo.ownedByViewer]). Facebook itself
 * decides what a token may read; this app adds the owner check on top and never offers anyone else's video.
 */
object OwnVideoProvider : FacebookDownloadProvider {
    override fun offersFor(info: FacebookVideoInfo): List<FacebookDownloadOffer> {
        val file = info.ownerFileUrl
        if (!info.ownedByViewer || file == null) return emptyList()
        return listOf(
            FacebookDownloadOffer(
                label = "your own video (original file)",
                fileUrl = file,
                authorizedBy = "Facebook Graph API, using your own access token",
            ),
        )
    }
}

sealed interface FacebookDownloadDecision {
    /** Explicit, permitted files exist. The address goes through the normal link check and approval. */
    data class Offered(val offers: List<FacebookDownloadOffer>) : FacebookDownloadDecision

    data class NotAvailable(
        val headline: String,
        val reasons: List<String>,
        val alternatives: List<String>,
    ) : FacebookDownloadDecision
}

class FacebookDownloadPolicy(
    private val providers: List<FacebookDownloadProvider> = listOf(OwnVideoProvider),
    private val detector: SmartLinkDetector = SmartLinkDetector(),
) {
    fun decide(lookup: FacebookLookup): FacebookDownloadDecision {
        val info = (lookup as? FacebookLookup.Found)?.info
        if (info != null) {
            val offers = providers
                .flatMap { runCatching { it.offersFor(info) }.getOrDefault(emptyList()) }
                .filter { isPermittedOffer(it.fileUrl) }
            if (offers.isNotEmpty()) return FacebookDownloadDecision.Offered(offers)
        }

        val reasons = mutableListOf(
            "Facebook offers apps no public download service for other people's videos. It delivers video through its " +
                "own player, and Almus Downloader does not take videos out of that player or around Facebook's access rules.",
        )
        when (lookup) {
            is FacebookLookup.Found ->
                if (lookup.info.ownedByViewer) {
                    reasons += "Facebook did not return a file for your video. The official export below always works for your own content."
                }
            is FacebookLookup.Unavailable ->
                reasons += "Facebook shares nothing about this content with apps, so there is nothing to save. If it is private " +
                    "or limited to a group, only people with access can watch it, inside Facebook."
            is FacebookLookup.Failed -> Unit
        }
        return FacebookDownloadDecision.NotAvailable(
            headline = "Downloading from Facebook is not available here",
            reasons = reasons,
            alternatives = listOf(
                "If this is your own video or Reel, get a copy with Facebook's official export (see \"Your own videos\").",
                "Open it in the Facebook app or on facebook.com, where the owner's settings and your account decide what you can see.",
                "If the owner publishes the video as a file themselves, paste that direct file link into Almus Downloader.",
            ),
        )
    }

    /**
     * An offer is kept only when it is a secure, public address that is not one of Facebook's page addresses
     * (a page is not a file) and that the app's own link check would accept as a file or an ordinary server link.
     */
    internal fun isPermittedOffer(url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidation.Valid ?: return false
        if (!valid.isSecure) return false
        if (UrlSafety.isLocalOrPrivate(valid.host)) return false
        if (pagePrefixes.any { UrlSafety.hostIs(valid.host, it) }) return false
        return detector.detect(url).canCheckAsFile
    }

    private companion object {
        val pagePrefixes = listOf("facebook.com", "fb.com", "fb.watch", "fb.me", "messenger.com", "m.me")
    }
}
