package com.almus.downloader.integration

import com.almus.downloader.integration.specs.PinterestSpec
import com.almus.downloader.integration.specs.VimeoSpec
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyAndOptionsTest {
    private val vimeo = ContentRef(MediaPlatform.Vimeo, LinkKind.Video, "76979871")
    private val pinterest = ContentRef(MediaPlatform.Pinterest, LinkKind.Post, "123456789012345678")

    private fun info(ref: ContentRef) =
        ContentInfo(ref.platform, ref.contentId, "t", null, null, null, null, null, null, null, "s")

    private fun found(ref: ContentRef) = ContentLookup.Found(info(ref))

    private fun unavailable() = ContentLookup.Unavailable(UnavailableReason.NotShared, "no")

    // ---------------------------------------------------------------- download policy

    @Test fun withoutAProviderNothingIsDownloadableOnAnyPlatform() {
        PlatformSpecs.all.forEach { spec ->
            val ref = ContentRef(spec.platform, LinkKind.Post, "123456789012345678")
            val decision = DownloadPolicy().decide(ref, found(ref)) as DownloadDecision.NotAvailable
            assertTrue(decision.headline.contains(spec.name))
            assertTrue(decision.reasons.isNotEmpty())
            assertTrue(decision.alternatives.isNotEmpty())
        }
    }

    @Test fun anUnavailableAnswerAddsAReason() {
        val plain = DownloadPolicy().decide(vimeo, found(vimeo)) as DownloadDecision.NotAvailable
        val refused = DownloadPolicy().decide(vimeo, unavailable()) as DownloadDecision.NotAvailable
        assertEquals(plain.reasons.size + 1, refused.reasons.size)
    }

    @Test fun anOwnersExplicitFileOfferIsKeptAndGoesThroughTheNormalCheck() {
        val offer = DownloadOffer("MP4", "https://files.example.com/film.mp4", "the video's owner")
        val policy = DownloadPolicy(mapOf(MediaPlatform.Vimeo to listOf(DownloadProvider { listOf(offer) })))
        val decision = policy.decide(vimeo, found(vimeo))
        assertEquals(DownloadDecision.Offered(listOf(offer)), decision)
    }

    @Test fun offersThatPointBackAtThePlatformOrAreUnsafeAreDropped() {
        val bad = listOf(
            "https://vimeo.com/76979871",
            "https://player.vimeo.com/video/76979871",
            "https://i.vimeocdn.com/video/1.mp4",
            "http://files.example.com/film.mp4",
            "https://192.168.0.10/film.mp4",
            "https://localhost/film.mp4",
            "https://nas/film.mp4",
            "ftp://files.example.com/film.mp4",
            "not a link",
        ).map { DownloadOffer("x", it, "owner") }
        val policy = DownloadPolicy(mapOf(MediaPlatform.Vimeo to listOf(DownloadProvider { bad })))
        assertTrue(policy.decide(vimeo, found(vimeo)) is DownloadDecision.NotAvailable)
    }

    @Test fun pinterestsCountryDomainsAndImageHostCountAsPinterestsOwn() {
        val policy = DownloadPolicy()
        assertFalse(policy.isPermittedOffer(PinterestSpec, "https://www.pinterest.fr/pin/1/"))
        assertFalse(policy.isPermittedOffer(PinterestSpec, "https://i.pinimg.com/originals/aa.jpg"))
        assertFalse(policy.isPermittedOffer(PinterestSpec, "https://pin.it/abc"))
        assertTrue(policy.isPermittedOffer(PinterestSpec, "https://files.example.com/aa.jpg"))
    }

    @Test fun aProviderForAnotherPlatformIsNotUsedAndAThrowingOneIsIgnored() {
        val offer = DownloadOffer("MP4", "https://files.example.com/film.mp4", "owner")
        val other = DownloadPolicy(mapOf(MediaPlatform.Reddit to listOf(DownloadProvider { listOf(offer) })))
        assertTrue(other.decide(vimeo, found(vimeo)) is DownloadDecision.NotAvailable)
        val throwing = DownloadPolicy(mapOf(MediaPlatform.Vimeo to listOf(DownloadProvider { error("boom") })))
        assertTrue(throwing.decide(vimeo, found(vimeo)) is DownloadDecision.NotAvailable)
    }

    @Test fun offersAreOnlyConsideredWhenThePlatformConfirmedTheContent() {
        val offer = DownloadOffer("MP4", "https://files.example.com/film.mp4", "owner")
        val policy = DownloadPolicy(mapOf(MediaPlatform.Vimeo to listOf(DownloadProvider { listOf(offer) })))
        assertTrue(policy.decide(vimeo, unavailable()) is DownloadDecision.NotAvailable)
    }

    // ---------------------------------------------------------------- official fallback

    @Test fun contentGetsThreeOfficialOptionsAllAtTheSameRebuiltAddress() {
        val options = AccessOptions.forContent(vimeo, found(vimeo))
        assertEquals(listOf(OpenKind.OpenInApp, OpenKind.OpenInBrowser, OpenKind.ShareLink), options.map { it.kind })
        assertEquals(setOf("https://vimeo.com/76979871"), options.map { it.url }.toSet())
        assertTrue(options[0].title.contains("Vimeo"))
        assertTrue(options[1].title.contains("vimeo.com"))
        options.forEach { assertTrue(PlatformLinks.isOfficialPageUrl(VimeoSpec, it.url)) }
    }

    @Test fun aRefusedAnswerStillOffersTheOfficialRoute() {
        val options = AccessOptions.forContent(vimeo, unavailable())
        assertEquals(3, options.size)
        assertTrue(options[0].description.contains("If the owner lets you see it"))
    }

    @Test fun pageOptionsNeedAnOfficialAddressOfThatPlatform() {
        assertEquals(3, AccessOptions.forPageUrl(MediaPlatform.Vimeo, "https://vimeo.com/user123").size)
        assertTrue(AccessOptions.forPageUrl(MediaPlatform.Vimeo, "https://vimeo.com/user123?x=1").isEmpty())
        assertTrue(AccessOptions.forPageUrl(MediaPlatform.Vimeo, "https://x.com/jack").isEmpty())
        assertTrue(AccessOptions.forPageUrl(MediaPlatform.Vimeo, "https://vimeo.com.evil.example/a").isEmpty())
        assertTrue(AccessOptions.forPageUrl(MediaPlatform.YouTube, "https://www.youtube.com/@x").isEmpty())
        assertTrue(AccessOptions.forPageUrl(MediaPlatform.Vimeo, null).isEmpty())
    }

    // ---------------------------------------------------------------- own content and media identification

    @Test fun everyPlatformHasAHelpCentreAddressThatIsSafeToOpen() {
        PlatformSpecs.all.forEach { spec ->
            val guide = spec.ownContent
            assertTrue(guide.helpUrl.startsWith("https://"))
            assertTrue(guide.steps.isNotEmpty())
            assertTrue(PlatformLinks.isOfficialHelpUrl(spec, guide.helpUrl))
            assertFalse(PlatformLinks.isOfficialHelpUrl(spec, "https://evil.example/"))
        }
    }

    @Test fun mediaIsNeverClaimedBeyondWhatIsKnown() {
        val withLength = info(vimeo).copy(durationSeconds = 596)
        val media = VimeoSpec.identifyMedia(vimeo, withLength)
        assertEquals("Video", media.summary)
        assertTrue(media.details.contains("Length: 9:56"))

        val post = ContentRef(MediaPlatform.Instagram, LinkKind.Post, "CxYz123AbCd")
        val reel = ContentRef(MediaPlatform.Instagram, LinkKind.ShortVideo, "CxYz123AbCd")
        val spec = PlatformSpecs.of(MediaPlatform.Instagram)!!
        assertTrue(spec.identifyMedia(post, null).summary.startsWith("Post"))
        assertTrue(spec.identifyMedia(reel, null).summary.startsWith("Reel"))
        assertTrue(spec.identifyMedia(post, null).details.single().contains("does not guess"))
    }

    // ---------------------------------------------------------------- the whole entry point

    @Test fun inspectCombinesEverythingAndNeverThrowsForAnOfflineService() {
        val http = object : OEmbedHttp {
            override suspend fun get(url: String, headers: Map<String, String>): OEmbedReply =
                throw OEmbedNetworkException("offline")
        }
        val report = runBlocking { PlatformIntegration(OEmbedClient(http)).inspect(vimeo) }
        assertEquals("Vimeo video", report.title)
        assertTrue(report.lookup is ContentLookup.Failed)
        assertEquals(3, report.openOptions.size)
        assertTrue(report.download is DownloadDecision.NotAvailable)
        assertEquals("Your own videos", report.ownContent.title)
    }
}
