package com.almus.downloader.tiktok

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPolicyAndOptionsTest {
    private val id = "6718335390845095173"
    private val ref = TikTokContentRef(id, "scout2015")
    private val info = TikTokVideoInfo(id, "caption", "Scout", "scout2015", "https://www.tiktok.com/@scout2015", null, null, null)
    private val found = TikTokLookup.Found(info)
    private val unavailable = TikTokLookup.Unavailable(TikTokUnavailableReason.NotShared)

    private fun offerOf(url: String) = TikTokDownloadProvider { listOf(TikTokDownloadOffer("file", url, "the owner")) }

    // ---------------------------------------------------------------- download policy

    @Test fun withoutAProviderNothingIsEverOffered() {
        listOf(found, unavailable, TikTokLookup.Failed(TikTokProblem.Network, "offline")).forEach {
            val decision = TikTokDownloadPolicy().decide(it)
            assertTrue(decision is TikTokDownloadDecision.NotAvailable)
        }
    }

    @Test fun theExplanationNamesTheReasonAndTheOfficialAlternatives() {
        val d = TikTokDownloadPolicy().decide(found) as TikTokDownloadDecision.NotAvailable
        assertTrue(d.headline.contains("not available"))
        assertTrue(d.reasons.any { it.contains("no public download service") })
        assertTrue(d.alternatives.any { it.contains("Save video") })
        assertTrue(d.alternatives.any { it.contains("Your own videos") })
        assertTrue(d.alternatives.any { it.contains("direct file link") })
    }

    @Test fun anUnavailableVideoGetsAnExtraReason() {
        val plain = TikTokDownloadPolicy().decide(found) as TikTokDownloadDecision.NotAvailable
        val gone = TikTokDownloadPolicy().decide(unavailable) as TikTokDownloadDecision.NotAvailable
        assertEquals(plain.reasons.size + 1, gone.reasons.size)
        assertTrue(gone.reasons.last().contains("nothing to save"))
    }

    @Test fun anOwnerPublishedFileOnAnotherHostIsOffered() {
        val d = TikTokDownloadPolicy(listOf(offerOf("https://cdn.example.org/clips/video.mp4"))).decide(found)
        val offered = d as TikTokDownloadDecision.Offered
        assertEquals("https://cdn.example.org/clips/video.mp4", offered.offers.single().fileUrl)
    }

    @Test fun offersPointingAtTikTokItselfOrUnsafeAddressesAreDropped() {
        listOf(
            "https://www.tiktok.com/@user/video/$id",
            "https://v16.tiktokcdn.com/abc/video.mp4",
            "https://v16.tiktokcdn-us.com/abc/video.mp4",
            "https://api.tiktokv.com/video.mp4",
            "https://p16.muscdn.com/video.mp4",
            "https://vm.tiktok.com/ZMabc123/",
            "http://cdn.example.org/video.mp4",
            "https://192.168.1.5/video.mp4",
            "https://localhost/video.mp4",
            "https://nas/video.mp4",
            "https://user:pw@cdn.example.org/video.mp4",
            "javascript:alert(1)",
            "ftp://cdn.example.org/video.mp4",
            "",
        ).forEach {
            val d = TikTokDownloadPolicy(listOf(offerOf(it))).decide(found)
            assertTrue("offer kept for $it", d is TikTokDownloadDecision.NotAvailable)
        }
        // A look-alike host is not TikTok's, so a file there is judged like any other file address.
        assertTrue(TikTokDownloadPolicy().isPermittedOffer("https://tiktok.com.evil.example/video.mp4"))
        assertFalse(TikTokDownloadPolicy().isPermittedOffer("https://www.tiktok.com/@user/video/$id"))
    }

    @Test fun aProviderOfferNeedsAFoundVideoAndAProviderThatWorks() {
        val provider = offerOf("https://cdn.example.org/video.mp4")
        assertTrue(TikTokDownloadPolicy(listOf(provider)).decide(unavailable) is TikTokDownloadDecision.NotAvailable)
        val broken = TikTokDownloadProvider { error("boom") }
        assertTrue(TikTokDownloadPolicy(listOf(broken)).decide(found) is TikTokDownloadDecision.NotAvailable)
        val both = TikTokDownloadPolicy(listOf(broken, provider)).decide(found)
        assertTrue(both is TikTokDownloadDecision.Offered)
    }

    // ---------------------------------------------------------------- open options

    @Test fun aVideoGetsThreeOfficialWaysToOpenIt() {
        val options = TikTokAccessOptions.forContent(ref, found)
        assertEquals(listOf(TikTokOpenKind.OpenInApp, TikTokOpenKind.OpenInBrowser, TikTokOpenKind.ShareLink), options.map { it.kind })
        options.forEach {
            assertEquals("https://www.tiktok.com/@scout2015/video/$id", it.url)
            assertTrue(TikTokLinks.isOfficialPageUrl(it.url))
            assertTrue(it.title.isNotBlank() && it.description.isNotBlank())
        }
        assertTrue(options[0].title.contains("TikTok app"))
        assertTrue(options[1].title.contains("tiktok.com"))
    }

    @Test fun theCreatorNameFromTikTokCompletesAnAddressThatHadNone() {
        val noName = TikTokContentRef(id, null)
        assertEquals("https://www.tiktok.com/@/video/$id", TikTokAccessOptions.forContent(noName, null).first().url)
        assertEquals("https://www.tiktok.com/@/video/$id", TikTokAccessOptions.forContent(noName, unavailable).first().url)
        assertEquals("https://www.tiktok.com/@scout2015/video/$id", TikTokAccessOptions.forContent(noName, found).first().url)
    }

    @Test fun anAddressNameWinsOverTheAnswer() {
        val other = TikTokLookup.Found(info.copy(creatorHandle = "someoneelse"))
        assertEquals("https://www.tiktok.com/@scout2015/video/$id", TikTokAccessOptions.forContent(ref, other).first().url)
    }

    @Test fun theAppTextIsCarefulWhenTikTokSharesNothing() {
        val shared = TikTokAccessOptions.forContent(ref, found).first().description
        val notShared = TikTokAccessOptions.forContent(ref, unavailable).first().description
        assertTrue(shared.contains("Plays the video"))
        assertTrue(notShared.contains("If the owner lets you see it"))
    }

    @Test fun otherPagesOnlyGetOptionsForCleanOfficialAddresses() {
        assertEquals(3, TikTokAccessOptions.forPageUrl("https://www.tiktok.com/@user/photo/$id").size)
        listOf(null, "", "http://www.tiktok.com/@user", "https://www.tiktok.com/@user?x=1", "https://evil.example/@user", "javascript:alert(1)")
            .forEach { assertTrue("options for $it", TikTokAccessOptions.forPageUrl(it).isEmpty()) }
    }

    // ---------------------------------------------------------------- export guide

    @Test fun theExportGuideIsHonestAboutWhatTheDataFileHolds() {
        val guide = TikTokExport.all.single()
        assertTrue(TikTokLinks.isOfficialPageUrl(guide.helpUrl))
        assertTrue(guide.steps.any { it.contains("Download your data") })
        assertTrue(guide.steps.any { it.contains("may not contain the video files") })
        assertTrue(guide.steps.any { it.contains("Save video") })
        assertTrue(guide.steps.any { it.contains("Choose a folder") })
        assertTrue(guide.intro.contains("your content and nothing else"))
    }

    // ---------------------------------------------------------------- integration

    @Test fun inspectCombinesLookupOptionsAndDecision() {
        val http = object : TikTokHttp {
            override suspend fun get(url: String, headers: Map<String, String>) = TikTokReply(404, "")
        }
        val report = runBlocking { TikTokIntegration(TikTokMetadataClient(http)).inspect(ref) }
        assertTrue(report.lookup is TikTokLookup.Unavailable)
        assertEquals(3, report.openOptions.size)
        assertTrue(report.download is TikTokDownloadDecision.NotAvailable)
        assertEquals(TikTokExport.all, report.exportGuides)
    }

    @Test fun inspectNeverThrowsOnUnexpectedFailures() {
        val http = object : TikTokHttp {
            override suspend fun get(url: String, headers: Map<String, String>): TikTokReply = throw IllegalStateException("boom")
        }
        val report = runBlocking { TikTokIntegration(TikTokMetadataClient(http)).inspect(ref) }
        val failed = report.lookup as TikTokLookup.Failed
        assertEquals(TikTokProblem.BadResponse, failed.problem)
        assertEquals(3, report.openOptions.size)
    }
}
