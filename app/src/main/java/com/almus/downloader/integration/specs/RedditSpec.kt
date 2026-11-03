package com.almus.downloader.integration.specs

import com.almus.downloader.integration.ContentInfo
import com.almus.downloader.integration.ContentRef
import com.almus.downloader.integration.MediaIdentification
import com.almus.downloader.integration.OEmbedService
import com.almus.downloader.integration.OwnContentGuide
import com.almus.downloader.integration.PlatformSpec
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.ParsedUrl

/**
 * Reddit posts (including gallery posts and short `redd.it` links). Public details come from Reddit's oEmbed
 * service: the post title and the author's name. A video address on `v.redd.it` is NOT a post and is not handled
 * here: its id is the video's, not a post id.
 */
object RedditSpec : PlatformSpec {
    override val platform = MediaPlatform.Reddit
    override val siteLabel = "reddit.com"
    override val contentKinds = setOf(LinkKind.Post)
    override val openableKinds = setOf(LinkKind.Channel)
    override val pageDomains = listOf("reddit.com")
    override val thumbnailDomains = listOf("redditmedia.com", "redd.it")
    override val ownedDomains = listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com")
    override val appPackages = listOf("com.reddit.frontpage")
    override val oembed = OEmbedService("www.reddit.com", "/oembed")
    override val sourceLabel = "Reddit oEmbed (public information)"
    override val embeddedIdPattern: Regex? = Regex("/comments/([A-Za-z0-9]+)")

    private val idFormat = Regex("^[A-Za-z0-9]{4,10}$")
    private val communityFormat = Regex("^[A-Za-z0-9_]{2,21}$")

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    /** The community of an `/r/name/comments/ID` address, or null for every other shape. */
    override fun handleOf(url: ParsedUrl): String? {
        if (url.seg(0) != "r" || url.seg(2) != "comments") return null
        return url.segments.getOrNull(1)?.takeIf { communityFormat.matches(it) }
    }

    override fun canonicalUrl(ref: ContentRef): String {
        val community = ref.handle?.takeIf { communityFormat.matches(it) }
        return if (community != null) {
            "https://www.reddit.com/r/" + community + "/comments/" + ref.contentId + "/"
        } else {
            "https://www.reddit.com/comments/" + ref.contentId + "/"
        }
    }

    override fun dialogTitle(ref: ContentRef): String = "Reddit post"

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification = MediaIdentification(
        "Post (text, a link, images, a gallery or a video)",
        listOf(
            "Reddit's public service does not say which of these the post holds, so Almus Downloader does not guess.",
            "Reddit stores video and sound as separate streams, so a post's video is not a single file.",
        ),
    )

    override val downloadReasons = listOf(
        "Reddit publishes no download service for third-party apps. A post is a web page, and Reddit delivers its " +
            "video and sound as separate streams. Almus Downloader does not take media out of Reddit or get around " +
            "Reddit's access rules.",
        "Some communities are private, quarantined or limited to adults and need a sign-in. Almus Downloader never " +
            "signs in or works around that.",
    )

    override val downloadAlternatives = listOf(
        "Open the post in the Reddit app or on reddit.com. Reddit shows there what is available to you.",
        "If this is your own content, use Reddit's own tools (see \"Your own content\").",
        "If the poster published the media as a plain file somewhere else, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own content",
        intro = "Reddit lets you request a copy of your own account data. This is Reddit's own tool, so it works " +
            "for your account and nothing else.",
        steps = listOf(
            "On reddit.com or in the Reddit app, open your Settings and find the option to request your data (it is under Privacy).",
            "Submit the request. Reddit prepares the file and tells you when it is ready.",
            "Download the file before the link expires.",
            "To see saved files in Almus Downloader, open Files, tap Choose a folder and pick the folder where they were saved.",
            "Menu names change from time to time, so Reddit's Help Center is the reference.",
        ),
        helpLabel = "Open Reddit's Help Center",
        helpUrl = "https://support.reddithelp.com/",
    )
}
