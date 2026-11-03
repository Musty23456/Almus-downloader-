package com.almus.downloader.integration

import com.almus.downloader.platform.MediaPlatform

enum class OpenKind { OpenInApp, OpenInBrowser, ShareLink }

/** One official way to see or pass on the content. [url] is always an https address on one of the platform's page domains. */
data class OpenOption(
    val platform: MediaPlatform,
    val kind: OpenKind,
    val title: String,
    val description: String,
    val url: String,
)

/** The official viewing options. None of them saves anything, and none of them signs in for the person. */
object AccessOptions {
    /**
     * Options for recognised content. The address is built from the validated reference only. When the platform
     * answered that it does not share the content, the app option says so: the platform still decides what is shown.
     */
    fun forContent(ref: ContentRef, lookup: ContentLookup?): List<OpenOption> {
        val spec = PlatformSpecs.of(ref.platform) ?: return emptyList()
        val appText = if (lookup is ContentLookup.Unavailable) {
            "Opens the link in ${spec.name}'s own app. If the owner lets you see it, ${spec.name} will show it."
        } else {
            "Shows this in ${spec.name}'s own app, with your account and the owner's settings."
        }
        return build(spec, spec.canonicalUrl(ref), appText)
    }

    /**
     * Options for a profile, board or similar page. Empty when [url] is not an official page address of the platform.
     * The address is the one the person entered, upgraded to https, without query or fragment.
     */
    fun forPageUrl(platform: MediaPlatform, url: String?): List<OpenOption> {
        val spec = PlatformSpecs.of(platform) ?: return emptyList()
        if (!PlatformLinks.isOfficialPageUrl(spec, url)) return emptyList()
        return build(
            spec,
            url!!,
            "Opens the link in ${spec.name}'s own app. Whether you can see it depends on your account and on the " +
                "owner's settings. Almus Downloader does not sign in for you or get around those settings.",
        )
    }

    private fun build(spec: PlatformSpec, url: String, appText: String): List<OpenOption> = listOf(
        OpenOption(spec.platform, OpenKind.OpenInApp, "Open in the ${spec.name} app", appText, url),
        OpenOption(
            spec.platform,
            OpenKind.OpenInBrowser,
            "Open on ${spec.siteLabel}",
            "Opens the official ${spec.name} page in a browser or another app you choose.",
            url,
        ),
        OpenOption(
            spec.platform,
            OpenKind.ShareLink,
            "Share the link",
            "Sends the link to another app. Nothing is downloaded.",
            url,
        ),
    )
}
