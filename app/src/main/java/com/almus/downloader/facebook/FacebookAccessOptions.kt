package com.almus.downloader.facebook

enum class FacebookOpenKind { OpenInApp, OpenInBrowser, ShareLink }

/** One official way to see or pass on the content. [url] is always an https address on a Facebook page host. */
data class FacebookOpenOption(val kind: FacebookOpenKind, val title: String, val description: String, val url: String)

/** The official viewing options. None of them saves anything, and none of them signs in for the person. */
object FacebookAccessOptions {
    /** Options for a recognised video, Reel or live video. The address is built from the validated id. */
    fun forContent(ref: FacebookContentRef, lookup: FacebookLookup?): List<FacebookOpenOption> {
        val notShared = lookup is FacebookLookup.Unavailable
        val appText = if (notShared) {
            "Opens the link in Facebook's own app. If you are signed in there and the owner lets you see it, Facebook will show it."
        } else {
            "Plays the ${ref.noun} in Facebook's own app, with your account and the owner's settings."
        }
        return build(ref.canonicalUrl, appText)
    }

    /**
     * Options for restricted content (a group post, a story, a page that needs sign-in). Empty when [url] is not an
     * official Facebook page address. The address is the one the person entered, upgraded to https.
     */
    fun forRestrictedUrl(url: String?): List<FacebookOpenOption> {
        if (!FacebookLinks.isOfficialPageUrl(url)) return emptyList()
        return build(
            url!!,
            "Opens the link in Facebook's own app. Whether you can see it depends on your account and on the owner's or " +
                "group's settings. Almus Downloader does not sign in for you or get around those settings.",
        )
    }

    private fun build(url: String, appText: String): List<FacebookOpenOption> = listOf(
        FacebookOpenOption(FacebookOpenKind.OpenInApp, "Open in the Facebook app", appText, url),
        FacebookOpenOption(
            FacebookOpenKind.OpenInBrowser,
            "Open on facebook.com",
            "Opens the official Facebook page in a browser or another app you choose.",
            url,
        ),
        FacebookOpenOption(
            FacebookOpenKind.ShareLink,
            "Share the link",
            "Sends the link to another app. Nothing is downloaded.",
            url,
        ),
    )
}
