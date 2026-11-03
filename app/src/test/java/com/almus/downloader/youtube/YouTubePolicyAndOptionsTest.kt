package com.almus.downloader.youtube

import com.almus.downloader.platform.LinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class YouTubePolicyAndOptionsTest {
    private val ref = YouTubeVideoRef("dQw4w9WgXcQ", LinkKind.Video)

    private fun info(
        live: LiveState = LiveState.None,
        age: Boolean = false,
        region: Boolean = false,
        license: VideoLicense = VideoLicense.Standard,
        embeddable: Boolean? = true,
    ) = YouTubeVideoInfo(
        videoId = ref.videoId, title = "T", channelTitle = "C", channelUrl = null, thumbnailUrl = null,
        durationSeconds = 10, liveState = live, license = license, embeddable = embeddable,
        ageRestricted = age, regionRestricted = region, source = MetadataSource.DataApi,
    )

    private fun notAvailable(d: DownloadDecision) = d as DownloadDecision.NotAvailable

    @Test fun downloadingIsNeverOfferedWithoutAnAuthorizedProvider() {
        val policy = YouTubeDownloadPolicy()
        val decisions = listOf(
            YouTubeLookup.Found(info()),
            YouTubeLookup.Found(info(license = VideoLicense.CreativeCommons)),
            YouTubeLookup.Found(info(live = LiveState.Live)),
            YouTubeLookup.Found(info(age = true)),
            YouTubeLookup.Unavailable(UnavailableReason.NotFound),
            YouTubeLookup.Failed(LookupProblem.Network, "x"),
        ).map { policy.decide(it) }
        assertTrue(decisions.all { it is DownloadDecision.NotAvailable })
        decisions.forEach { assertTrue(notAvailable(it).alternatives.isNotEmpty()) }
    }

    @Test fun reasonsExplainTheSituation() {
        val policy = YouTubeDownloadPolicy()
        fun reasons(lookup: YouTubeLookup) = notAvailable(policy.decide(lookup)).reasons.joinToString(" ")
        assertTrue(reasons(YouTubeLookup.Found(info())).contains("no public download service"))
        assertTrue(reasons(YouTubeLookup.Found(info(age = true))).contains("age-restricted"))
        assertTrue(reasons(YouTubeLookup.Found(info(region = true))).contains("regional"))
        assertTrue(reasons(YouTubeLookup.Found(info(live = LiveState.Upcoming))).contains("Live streams"))
        assertTrue(reasons(YouTubeLookup.Found(info(license = VideoLicense.CreativeCommons))).contains("Creative Commons"))
        assertTrue(reasons(YouTubeLookup.Unavailable(UnavailableReason.Removed)).contains("not available"))
    }

    @Test fun anAuthorizedOfferIsAcceptedOnlyForARealThirdPartyFile() {
        val good = AuthorizedDownloadOffer("Video file", "https://files.example.org/talk.mp4", "the channel owner")
        val provider = AuthorizedDownloadProvider { listOf(good) }
        val decision = YouTubeDownloadPolicy(listOf(provider)).decide(YouTubeLookup.Found(info()))
        assertEquals(DownloadDecision.Offered(listOf(good)), decision)
    }

    @Test fun offersPointingAtYouTubeOrUnsafeAddressesAreDropped() {
        val bad = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://rr1---sn-abc.googlevideo.com/videoplayback",
            "https://i.ytimg.com/vi/x/hqdefault.jpg",
            "https://www.youtube-nocookie.com/embed/x",
            "http://files.example.org/talk.mp4",
            "https://192.168.1.5/talk.mp4",
            "https://localhost/talk.mp4",
            "javascript:alert(1)",
            "not a link",
        ).map { AuthorizedDownloadOffer("x", it, "someone") }
        val decision = YouTubeDownloadPolicy(listOf(AuthorizedDownloadProvider { bad })).decide(YouTubeLookup.Found(info()))
        assertTrue(decision is DownloadDecision.NotAvailable)
    }

    @Test fun aFailingProviderDoesNotBreakTheDecision() {
        val provider = AuthorizedDownloadProvider { error("boom") }
        assertTrue(YouTubeDownloadPolicy(listOf(provider)).decide(YouTubeLookup.Found(info())) is DownloadDecision.NotAvailable)
    }

    @Test fun noOfferIsMadeWhenTheVideoIsUnavailable() {
        val provider = AuthorizedDownloadProvider { listOf(AuthorizedDownloadOffer("f", "https://files.example.org/a.mp4", "o")) }
        val decision = YouTubeDownloadPolicy(listOf(provider)).decide(YouTubeLookup.Unavailable(UnavailableReason.Private))
        assertTrue(decision is DownloadDecision.NotAvailable)
    }

    @Test fun viewingOptionsAreOfficialAndAlwaysOffered() {
        listOf<YouTubeLookup?>(
            null,
            YouTubeLookup.Found(info()),
            YouTubeLookup.Unavailable(UnavailableReason.PrivateOrRestricted),
            YouTubeLookup.Failed(LookupProblem.Network, "x"),
        ).forEach { lookup ->
            val options = YouTubeViewingOptions.forVideo(ref, lookup)
            assertEquals(listOf(ViewingKind.OpenInApp, ViewingKind.OpenInBrowser, ViewingKind.ShareLink), options.map { it.kind })
            assertTrue(options.all { it.url == "https://www.youtube.com/watch?v=dQw4w9WgXcQ" })
        }
    }

    @Test fun embeddingNote() {
        assertTrue(YouTubeViewingOptions.embeddingNote(info(embeddable = true))!!.contains("allows"))
        assertTrue(YouTubeViewingOptions.embeddingNote(info(embeddable = false))!!.contains("turned off"))
        assertEquals(null, YouTubeViewingOptions.embeddingNote(info(embeddable = null)))
    }

    @Test fun integrationCombinesEverythingAndNeverThrows() {
        val http = object : YouTubeHttp {
            override suspend fun get(url: String, headers: Map<String, String>): HttpReply =
                throw YouTubeNetworkException("offline")
        }
        val report = runBlocking { YouTubeIntegration(YouTubeMetadataClient(http) { null }).inspect(ref) }
        assertTrue(report.lookup is YouTubeLookup.Failed)
        assertEquals(3, report.viewing.size)
        assertFalse(report.download is DownloadDecision.Offered)
    }

    @Test fun apiKeyShape() {
        assertTrue(YouTubeApiKey.isValidFormat("AIzaSyA-1234567890abcdefghijklmnopqrstu"))
        assertFalse(YouTubeApiKey.isValidFormat("short"))
        assertFalse(YouTubeApiKey.isValidFormat("has space in it 1234567890"))
        assertFalse(YouTubeApiKey.isValidFormat(null))
    }
}
