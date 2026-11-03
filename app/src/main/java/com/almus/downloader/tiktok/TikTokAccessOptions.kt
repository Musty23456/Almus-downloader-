package com.almus.downloader.tiktok

enum class TikTokOpenKind { OpenInApp, OpenInBrowser, ShareLink }

/** One official way to see or pass on the content. [url] is always an https address on tiktok.com. */
data class TikTokOpenOption(val kind: TikTokOpenKind, val title: String, val description: String, val url: String)

/** The official viewing options. None of them saves anything, and none of them signs in for the person. */
object TikTokAccessOptions {
    /**
     * Options for a recognised video. The address is built from the validated id and, when known, the creator's name
     * (from the address entered, or from TikTok's own answer).
     */
    fun forContent(ref: TikTokContentRef, lookup: TikTokLookup?): List<TikTokOpenOption> {
        val info = (lookup as? TikTokLookup.Found)?.info
        val handle = ref.handle ?: info?.creatorHandle
        val url = ref.copy(handle = handle).canonicalUrl
        val appText = if (lookup is TikTokLookup.Unavailable) {
            "Opens the link in TikTok's own app. If the owner lets you see it, TikTok will show it."
        } else {
            "Plays the video in TikTok's own app, with your account and the creator's settings."
        }
        return build(url, appText)
    }

    /**
     * Options for a photo post, a live page or a profile. Empty when [url] is not an official TikTok page address.
     * The address is the one the person entered, upgraded to https, without query or fragment.
     */
    fun forPageUrl(url: String?): List<TikTokOpenOption> {
        if (!TikTokLinks.isOfficialPageUrl(url)) return emptyList()
        return build(
            url!!,
            "Opens the link in TikTok's own app. Whether you can see it depends on your account and on the creator's " +
                "settings. Almus Downloader does not sign in for you or get around those settings.",
        )
    }

    private fun build(url: String, appText: String): List<TikTokOpenOption> = listOf(
        TikTokOpenOption(TikTokOpenKind.OpenInApp, "Open in the TikTok app", appText, url),
        TikTokOpenOption(
            TikTokOpenKind.OpenInBrowser,
            "Open on tiktok.com",
            "Opens the official TikTok page in a browser or another app you choose.",
            url,
        ),
        TikTokOpenOption(
            TikTokOpenKind.ShareLink,
            "Share the link",
            "Sends the link to another app. Nothing is downloaded.",
            url,
        ),
    )
}
