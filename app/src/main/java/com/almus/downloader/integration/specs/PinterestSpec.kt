package com.almus.downloader.integration.specs

import com.almus.downloader.integration.ContentInfo
import com.almus.downloader.integration.ContentRef
import com.almus.downloader.integration.MediaIdentification
import com.almus.downloader.integration.OEmbedService
import com.almus.downloader.integration.OwnContentGuide
import com.almus.downloader.integration.PlatformSpec
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.LinkKind

/**
 * Pinterest pins (on any of Pinterest's country domains; the official page address is always www.pinterest.com).
 * Public details come from Pinterest's documented oEmbed service: the pin's creator and, when given, a title and
 * thumbnail.
 */
object PinterestSpec : PlatformSpec {
    override val platform = MediaPlatform.Pinterest
    override val siteLabel = "pinterest.com"
    override val contentKinds = setOf(LinkKind.Post)
    override val openableKinds = setOf(LinkKind.Channel)
    override val pageDomains = listOf("pinterest.com")
    override val thumbnailDomains = listOf("pinimg.com")
    override val ownedDomains = listOf("pin.it", "pinimg.com", "pinterest.com")
    override val appPackages = listOf("com.pinterest")
    override val oembed = OEmbedService("www.pinterest.com", "/oembed.json")
    override val sourceLabel = "Pinterest oEmbed (public information)"
    override val embeddedIdPattern: Regex? = Regex("/pin/([0-9]+)")

    private val idFormat = Regex("^[0-9]{5,25}$")

    /** pinterest.com and Pinterest's country domains, with any sub-domains. */
    private val pageHost = Regex(
        "^(?:[a-z0-9-]+\\.)*pinterest\\.(?:com|co\\.uk|ca|fr|de|es|it|jp|ru|pt|at|ch|nl|se|dk|fi|ie|com\\.au|" +
            "com\\.mx|com\\.br|co\\.in|in|co\\.kr|ph|cl|pl|nz|com\\.pe|com\\.co|com\\.ar)$",
    )

    override fun isPageHost(host: String): Boolean = pageHost.matches(host.lowercase().trimEnd('.'))

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    override fun canonicalUrl(ref: ContentRef): String = "https://www.pinterest.com/pin/" + ref.contentId + "/"

    override fun dialogTitle(ref: ContentRef): String = "Pinterest pin"

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification = MediaIdentification(
        "Pin (an image or a video)",
        listOf(
            "Pinterest's public service does not say which, so Almus Downloader does not guess.",
            "Pins often point to content owned by someone else.",
        ),
    )

    override val downloadReasons = listOf(
        "Pinterest offers apps no public download service for pins. It shows images and video through its own app " +
            "and site, and Almus Downloader does not take media out of them or get around Pinterest's access rules.",
        "Pins often point to content owned by someone else, who decides whether it may be saved.",
    )

    override val downloadAlternatives = listOf(
        "Open the pin in Pinterest. It shows the original website, where the owner may offer the file.",
        "If this is your own content, use Pinterest's own tools (see \"Your own content\").",
        "If the owner publishes the media as a file themselves, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own content",
        intro = "Pinterest lets you request a copy of your own data. This is Pinterest's own tool, so it works " +
            "for your account and nothing else.",
        steps = listOf(
            "On pinterest.com or in the Pinterest app, open your Settings and find the privacy and data options.",
            "Choose the option to request your data and submit the request.",
            "Pinterest prepares the file and tells you when it is ready. Download it before it expires.",
            "To see saved files in Almus Downloader, open Files, tap Choose a folder and pick the folder where they were saved.",
            "Menu names change from time to time, so Pinterest's Help Center is the reference.",
        ),
        helpLabel = "Open Pinterest's Help Center",
        helpUrl = "https://help.pinterest.com/",
    )
}
