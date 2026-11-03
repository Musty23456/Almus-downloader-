package com.almus.downloader.integration.specs

import com.almus.downloader.integration.ContentInfo
import com.almus.downloader.integration.ContentRef
import com.almus.downloader.integration.MediaIdentification
import com.almus.downloader.integration.OEmbedService
import com.almus.downloader.integration.OwnContentGuide
import com.almus.downloader.integration.PlatformSpec
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform

/**
 * Instagram posts, Reels and videos. Public details come from Meta's oEmbed service for Instagram, which needs no
 * token since 15 June 2026 and returns only technical data (embed markup and size): since November 2025 it no longer
 * returns a title, author or thumbnail. So the dialog can confirm that content is public, but cannot preview it.
 */
object InstagramSpec : PlatformSpec {
    override val platform = MediaPlatform.Instagram
    override val siteLabel = "instagram.com"
    override val contentKinds = setOf(LinkKind.Post, LinkKind.ShortVideo, LinkKind.Video)
    override val openableKinds = setOf(LinkKind.Channel)
    override val pageDomains = listOf("instagram.com")
    override val thumbnailDomains = listOf("cdninstagram.com", "fbcdn.net")
    override val ownedDomains = listOf("instagram.com", "instagr.am", "cdninstagram.com", "fbcdn.net", "facebook.com")
    override val appPackages = listOf("com.instagram.android")
    override val oembed = OEmbedService("graph.facebook.com", "/v26.0/instagram_oembed")
    override val sourceLabel = "Meta oEmbed for Instagram (public information)"

    /** Meta reports rate limits with numeric codes. */
    override val rateLimitCodes: Set<String> =
        setOf("rate_limit_exceeded", "rate_limit", "too_many_requests", "4", "17", "32", "613")

    override val embeddedIdPattern: Regex? =
        Regex("instagram\\.com/(?:[A-Za-z0-9._]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")

    private val idFormat = Regex("^[A-Za-z0-9_-]{5,30}$")

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    override fun canonicalUrl(ref: ContentRef): String {
        val type = when (ref.kind) {
            LinkKind.ShortVideo -> "reel"
            LinkKind.Video -> "tv"
            else -> "p"
        }
        return "https://www.instagram.com/" + type + "/" + ref.contentId + "/"
    }

    override fun dialogTitle(ref: ContentRef): String = when (ref.kind) {
        LinkKind.ShortVideo -> "Instagram Reel"
        LinkKind.Video -> "Instagram video"
        else -> "Instagram post"
    }

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification = when (ref.kind) {
        LinkKind.ShortVideo -> MediaIdentification(
            "Reel (a short video)",
            listOf("Instagram plays Reels in its own player. They are not offered as a file."),
        )
        LinkKind.Video -> MediaIdentification(
            "Video",
            listOf("Instagram plays videos in its own player. They are not offered as a file."),
        )
        else -> MediaIdentification(
            "Post (a photo, a video or a carousel of several)",
            listOf(
                "Instagram's public service does not say which of these this post holds, so Almus Downloader " +
                    "does not guess.",
            ),
        )
    }

    override val downloadReasons = listOf(
        "Instagram offers apps no public download service. It delivers photos and video through its own app and " +
            "player, and Almus Downloader does not take media out of them or get around Instagram's access rules.",
        "Account owners decide who can see their content. Private accounts are visible only to approved followers, " +
            "and Almus Downloader never signs in or works around that.",
    )

    override val downloadAlternatives = listOf(
        "If this is your own content, use Instagram's own tools (see \"Your own content\").",
        "If the owner publishes the media as a file themselves, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own content",
        intro = "Instagram lets you request a copy of your own information, including your photos and videos. " +
            "This is Instagram's own tool, so it works for your account and nothing else.",
        steps = listOf(
            "In the Instagram app or on instagram.com, open Settings, then Accounts Center, then Your information and permissions, then Download your information.",
            "Choose what to include, a format and the quality of photos and videos, then submit the request.",
            "Instagram prepares the file and tells you when it is ready. Download it before it expires.",
            "To see the saved files in Almus Downloader, open Files, tap Choose a folder and pick the folder where they were saved.",
            "Menu names change from time to time, so Instagram's Help Center is the reference.",
        ),
        helpLabel = "Open Instagram's Help Center",
        helpUrl = "https://help.instagram.com/",
    )
}
