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
 * Dailymotion videos. Public details come from Dailymotion's documented oEmbed service: title, owner and a
 * thumbnail for videos that are public and embeddable.
 */
object DailymotionSpec : PlatformSpec {
    override val platform = MediaPlatform.Dailymotion
    override val siteLabel = "dailymotion.com"
    override val contentKinds = setOf(LinkKind.Video)
    override val openableKinds = setOf(LinkKind.Channel, LinkKind.Playlist)
    override val pageDomains = listOf("dailymotion.com")
    override val thumbnailDomains = listOf("dmcdn.net")
    override val ownedDomains = listOf("dailymotion.com", "dai.ly", "dmcdn.net")
    override val appPackages = listOf("com.dailymotion.dailymotion")
    override val oembed = OEmbedService(
        host = "www.dailymotion.com",
        path = "/services/oembed",
        fixedParams = listOf("format" to "json"),
    )
    override val sourceLabel = "Dailymotion oEmbed (public information)"
    override val embeddedIdPattern: Regex? = Regex("video/([A-Za-z0-9]+)")

    private val idFormat = Regex("^[A-Za-z0-9]{4,12}$")

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    override fun canonicalUrl(ref: ContentRef): String = "https://www.dailymotion.com/video/" + ref.contentId

    override fun dialogTitle(ref: ContentRef): String = "Dailymotion video"

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification = MediaIdentification(
        "Video",
        listOf(
            "Dailymotion plays videos in its own player as streams made of many small pieces, not as one file.",
            "Some videos are limited by region, age or the owner's settings.",
        ),
    )

    override val downloadReasons = listOf(
        "Dailymotion publishes no download service for apps. It delivers video as streams through its own player, " +
            "and Almus Downloader does not take videos out of that player or get around Dailymotion's access rules.",
        "Videos can be limited by region, age or the owner's settings, and private videos can be watched only by " +
            "the people the owner chose. Almus Downloader never signs in or works around that.",
    )

    override val downloadAlternatives = listOf(
        "Open the video in the Dailymotion app or on dailymotion.com. Dailymotion shows there what is available to you.",
        "If you are the owner, see Dailymotion's help for what can be done with your own videos (see \"Your own videos\").",
        "If the owner publishes the video as a file themselves, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own videos",
        intro = "If you own the video, manage it with Dailymotion's own tools. These steps use Dailymotion's own " +
            "website or app, with you signed in there; Almus Downloader sees none of it.",
        steps = listOf(
            "Sign in at dailymotion.com and open the area where you manage your own videos.",
            "Dailymotion's Help Center describes which download or export options exist for your own videos. They depend on your account.",
            "If a download option is shown for your video, use it. The file is saved by your browser or phone as usual.",
            "To see the saved file in Almus Downloader, open Files, tap Choose a folder and pick the folder where it was saved.",
        ),
        helpLabel = "Open Dailymotion's Help Center",
        helpUrl = "https://faq.dailymotion.com/",
    )
}
