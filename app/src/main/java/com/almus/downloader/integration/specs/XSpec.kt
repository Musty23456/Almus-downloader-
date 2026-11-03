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
 * X (Twitter) posts. Public details come from X's oEmbed service, which returns the author and the embed markup of
 * a public post but no title and no thumbnail. The post's own text is read from that markup as plain text; the
 * markup itself is never shown or run.
 */
object XSpec : PlatformSpec {
    override val platform = MediaPlatform.X
    override val siteLabel = "x.com"
    override val contentKinds = setOf(LinkKind.Post)
    override val openableKinds = setOf(LinkKind.Channel)
    override val pageDomains = listOf("x.com", "twitter.com")
    override val thumbnailDomains = listOf("twimg.com")
    override val ownedDomains = listOf("x.com", "twitter.com", "t.co", "twimg.com")
    override val appPackages = listOf("com.twitter.android")
    override val oembed = OEmbedService(
        host = "publish.twitter.com",
        path = "/oembed",
        fixedParams = listOf("omit_script" to "true", "dnt" to "true"),
    )
    override val sourceLabel = "X oEmbed (public information)"
    override val titleFromMarkup: Boolean = true
    override val embeddedIdPattern: Regex? = Regex("/status/([0-9]{5,25})")

    private val idFormat = Regex("^[0-9]{10,20}$")
    private val handleFormat = Regex("^[A-Za-z0-9_]{1,15}$")
    private val notAccounts = setOf("i", "web", "home", "intent", "share", "search")

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    private fun validHandle(handle: String?): String? =
        handle?.takeIf { handleFormat.matches(it) && it.lowercase() !in notAccounts }

    /** The account name of a `/name/status/ID` address, or null (for `/i/status/ID` and every other shape). */
    override fun handleOf(url: ParsedUrl): String? {
        if (url.segments.size < 3 || url.seg(1) != "status") return null
        return validHandle(url.segments[0])
    }

    override fun canonicalUrl(ref: ContentRef): String =
        "https://x.com/" + (validHandle(ref.handle) ?: "i") + "/status/" + ref.contentId

    /** The oEmbed service documents twitter.com addresses, so that is what it is asked about. */
    override fun oembedSubject(ref: ContentRef): String =
        "https://twitter.com/" + (validHandle(ref.handle) ?: "i") + "/status/" + ref.contentId

    override fun dialogTitle(ref: ContentRef): String = "X (Twitter) post"

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification = MediaIdentification(
        "Post (text, and possibly images, GIFs or a video)",
        listOf(
            "X's public service returns the post's text and author, but does not say which media the post has, " +
                "so Almus Downloader does not guess.",
            "X plays video in its own player. It is not offered as a file.",
        ),
    )

    override val downloadReasons = listOf(
        "X offers apps no public download service for the media in posts. It delivers video and images through its " +
            "own app and player, and Almus Downloader does not take media out of them or get around X's access rules.",
        "Posts from protected accounts are visible only to approved followers, and some content is limited by age " +
            "or region. Almus Downloader never signs in or works around that.",
    )

    override val downloadAlternatives = listOf(
        "Open the post in the X app or on x.com. X shows there what is available to you.",
        "If this is your own content, use X's own tools (see \"Your own content\").",
        "If the author publishes the media as a file themselves, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own content",
        intro = "X lets you request an archive of your own account data, which includes your posts and the media " +
            "you uploaded. This is X's own tool, so it works for your account and nothing else.",
        steps = listOf(
            "In the X app or on x.com, open Settings and privacy, then Your account, then Download an archive of your data.",
            "Confirm your identity as X asks, then request the archive.",
            "X prepares the file and tells you when it is ready. Download it before the link expires.",
            "To see saved files in Almus Downloader, open Files, tap Choose a folder and pick the folder where they were saved.",
            "Menu names change from time to time, so X's Help Center is the reference.",
        ),
        helpLabel = "Open X's Help Center",
        helpUrl = "https://help.x.com/",
    )
}
