package com.almus.downloader.youtube

enum class ViewingKind { OpenInApp, OpenInBrowser, ShareLink }

/** One official way to watch or pass on the video. Every [url] is the youtube.com page built from the validated id. */
data class ViewingOption(val kind: ViewingKind, val title: String, val description: String, val url: String)

/** The official viewing options. None of them saves anything. */
object YouTubeViewingOptions {
    fun forVideo(ref: YouTubeVideoRef, lookup: YouTubeLookup?): List<ViewingOption> {
        val url = ref.watchUrl
        val unavailable = lookup is YouTubeLookup.Unavailable
        return listOf(
            ViewingOption(
                ViewingKind.OpenInApp,
                "Open in the YouTube app",
                if (unavailable) {
                    "Opens the link in YouTube's own app. If the video is yours, or you can sign in there, YouTube may still show it."
                } else {
                    "Plays the video in YouTube's own app, with your account, age checks and offline features if you use them there."
                },
                url,
            ),
            ViewingOption(
                ViewingKind.OpenInBrowser,
                "Open on youtube.com",
                "Opens the official YouTube page in a browser or another app you choose.",
                url,
            ),
            ViewingOption(
                ViewingKind.ShareLink,
                "Share the link",
                "Sends the youtube.com link to another app. Nothing is downloaded.",
                url,
            ),
        )
    }

    /** A sentence about embedded players, or null when it is not known. */
    fun embeddingNote(info: YouTubeVideoInfo): String? = when (info.embeddable) {
        true -> "The owner allows this video to be shown in other apps' players."
        false -> "The owner turned off embedding, so other apps cannot show this video in their own player. It can still be watched on YouTube."
        null -> null
    }
}
