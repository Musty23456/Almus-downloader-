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
 * Vimeo videos. Public details come from Vimeo's documented oEmbed service: title, owner, length, publishing date
 * and a thumbnail for videos that are public and embeddable. Private, password-protected and domain-restricted
 * videos are not shared by that service, and nothing here tries to get past those settings.
 */
object VimeoSpec : PlatformSpec {
    override val platform = MediaPlatform.Vimeo
    override val siteLabel = "vimeo.com"
    override val contentKinds = setOf(LinkKind.Video)
    override val openableKinds = setOf(LinkKind.Channel, LinkKind.Playlist)
    override val pageDomains = listOf("vimeo.com")
    override val thumbnailDomains = listOf("vimeocdn.com")
    override val ownedDomains = listOf("vimeo.com", "vimeocdn.com")
    override val appPackages = listOf("com.vimeo.android.videoapp")
    override val oembed = OEmbedService("vimeo.com", "/api/oembed.json")
    override val sourceLabel = "Vimeo oEmbed (public information)"
    override val embeddedIdPattern: Regex? = Regex("vimeo\\.com/video/([0-9]+)")

    private val idFormat = Regex("^[0-9]{3,12}$")

    override fun isValidContentId(id: String?): Boolean = id != null && idFormat.matches(id)

    override fun canonicalUrl(ref: ContentRef): String = "https://vimeo.com/" + ref.contentId

    override fun dialogTitle(ref: ContentRef): String = "Vimeo video"

    override fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification {
        val details = mutableListOf<String>()
        info?.durationText?.let { details += "Length: $it" }
        details += "Vimeo plays videos in its own player. The owner decides whether a video may be downloaded."
        return MediaIdentification("Video", details)
    }

    override val downloadReasons = listOf(
        "Vimeo gives apps no public way to save a video. Each owner decides whether people may download a video, " +
            "and Vimeo offers that on the video's own page. Almus Downloader does not take videos out of Vimeo's player.",
        "Videos that are private, password-protected or limited to certain websites cannot be seen or saved by this " +
            "app, and Almus Downloader does not get around those settings.",
    )

    override val downloadAlternatives = listOf(
        "Open the video on vimeo.com. If the owner allows downloads, Vimeo shows a download option there, and the " +
            "file it gives you can be found in Files after you pick the folder.",
        "If you are the owner, check the video's settings in your Vimeo account (see \"Your own videos\").",
        "If the owner publishes the video as a file themselves, paste that direct file link into Almus Downloader.",
    )

    override val ownContent = OwnContentGuide(
        title = "Your own videos",
        intro = "If you own the video, you control whether it can be downloaded. These steps use Vimeo's own " +
            "website, with you signed in there; Almus Downloader sees none of it.",
        steps = listOf(
            "Sign in at vimeo.com and open your video from your library.",
            "Check the video's privacy and download settings. Vimeo shows a download option only for videos where downloading is switched on.",
            "If a download option is shown, use it. The file is saved by your browser or phone as usual.",
            "To see the saved file in Almus Downloader, open Files, tap Choose a folder and pick the folder where it was saved.",
            "Menu names change from time to time, so Vimeo's Help Center is the reference.",
        ),
        helpLabel = "Open Vimeo's Help Center",
        helpUrl = "https://help.vimeo.com/",
    )
}
