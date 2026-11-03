package com.almus.downloader.facebook

import com.almus.downloader.platform.LinkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookPolicyAndOptionsTest {
    private val id = "123456789012345"
    private val ref = FacebookContentRef(id, LinkKind.Video)
    private val file = "https://video.xx.fbcdn.net/v/t42/clip.mp4?sig=1"
    private val policy = FacebookDownloadPolicy()

    private fun info(
        owned: Boolean = false,
        fileUrl: String? = null,
        source: FacebookMetadataSource = FacebookMetadataSource.GraphApi,
    ) = FacebookVideoInfo(
        contentId = id, title = "T", description = null, ownerName = "N", ownerUrl = null, thumbnailUrl = null,
        durationSeconds = 10, publishedDate = null, widthPx = null, heightPx = null, embeddable = true,
        ownedByViewer = owned, ownerFileUrl = fileUrl, source = source,
    )

    private fun found(i: FacebookVideoInfo) = FacebookLookup.Found(i)

    // ---------------------------------------------------------------- download policy

    @Test fun anOwnVideoWithAFileIsOffered() {
        val decision = policy.decide(found(info(owned = true, fileUrl = file)))
        val offered = decision as FacebookDownloadDecision.Offered
        assertEquals(file, offered.offers.single().fileUrl)
        assertTrue(offered.offers.single().authorizedBy.contains("your own access token"))
    }

    @Test fun nothingIsOfferedForAVideoThatIsNotOwned() {
        listOf(
            info(owned = false, fileUrl = file),
            info(owned = true, fileUrl = null),
            info(owned = false, fileUrl = null),
            info(owned = false, source = FacebookMetadataSource.OEmbed),
        ).forEach { assertTrue(policy.decide(found(it)) is FacebookDownloadDecision.NotAvailable) }
    }

    @Test fun unsafeOrPageAddressesAreNeverOffered() {
        listOf(
            "http://video.xx.fbcdn.net/v/t42/clip.mp4",
            "https://www.facebook.com/watch/?v=$id",
            "https://fb.watch/abc123xyz/",
            "https://video.xx.fbcdn.net/v/t42/noextension",
            "https://192.168.1.5/clip.mp4",
            "https://localhost/clip.mp4",
            "https://user:pw@video.xx.fbcdn.net/clip.mp4",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "file:///sdcard/x.mp4",
            "not a link",
            "",
        ).forEach { url ->
            assertTrue("must not be offered: $url", policy.decide(found(info(owned = true, fileUrl = url))) is FacebookDownloadDecision.NotAvailable)
        }
    }

    @Test fun anOwnerPublishedFileOnAnotherSiteCanBeOfferedByAProvider() {
        val provider = FacebookDownloadProvider { listOf(FacebookDownloadOffer("owner file", "https://cdn.example.org/video.mp4", "the owner")) }
        val decision = FacebookDownloadPolicy(listOf(provider)).decide(found(info()))
        assertEquals("https://cdn.example.org/video.mp4", (decision as FacebookDownloadDecision.Offered).offers.single().fileUrl)
    }

    @Test fun aBrokenProviderCannotBreakTheDecision() {
        val broken = FacebookDownloadProvider { throw IllegalStateException("boom") }
        assertTrue(FacebookDownloadPolicy(listOf(broken)).decide(found(info())) is FacebookDownloadDecision.NotAvailable)
    }

    @Test fun theExplanationIsHonestAndOffersAlternatives() {
        val d = policy.decide(found(info())) as FacebookDownloadDecision.NotAvailable
        assertTrue(d.headline.contains("not available"))
        assertTrue(d.reasons.first().contains("no public download service"))
        assertTrue(d.alternatives.any { it.contains("official export") })
        assertTrue(d.alternatives.any { it.contains("Facebook app") })
        assertTrue(d.alternatives.any { it.contains("direct file link") })
    }

    @Test fun everyLookupOutcomeGetsAMeaningfulDecision() {
        val unavailable = policy.decide(FacebookLookup.Unavailable(FacebookUnavailableReason.NotPublic)) as FacebookDownloadDecision.NotAvailable
        assertTrue(unavailable.reasons.any { it.contains("shares nothing") })
        val failed = policy.decide(FacebookLookup.Failed(FacebookProblem.Network, "x")) as FacebookDownloadDecision.NotAvailable
        assertEquals(1, failed.reasons.size)
        val ownedNoFile = policy.decide(found(info(owned = true))) as FacebookDownloadDecision.NotAvailable
        assertTrue(ownedNoFile.reasons.any { it.contains("did not return a file") })
    }

    // ---------------------------------------------------------------- opening in Facebook

    @Test fun contentOptionsAreBuiltFromTheValidatedId() {
        val options = FacebookAccessOptions.forContent(ref, found(info()))
        assertEquals(listOf(FacebookOpenKind.OpenInApp, FacebookOpenKind.OpenInBrowser, FacebookOpenKind.ShareLink), options.map { it.kind })
        assertTrue(options.all { it.url == "https://www.facebook.com/watch/?v=$id" })
        assertTrue(options.all { FacebookLinks.isOfficialPageUrl(it.url) })
        val reel = FacebookAccessOptions.forContent(FacebookContentRef(id, LinkKind.ShortVideo), null)
        assertTrue(reel.all { it.url == "https://www.facebook.com/reel/$id" })
        assertTrue(reel.first().description.contains("Reel"))
    }

    @Test fun unavailableContentCanStillBeOpenedInFacebook() {
        val options = FacebookAccessOptions.forContent(ref, FacebookLookup.Unavailable(FacebookUnavailableReason.NotPublic))
        assertEquals(3, options.size)
        assertTrue(options.first().description.contains("signed in"))
    }

    @Test fun restrictedAddressOptionsRequireAnOfficialAddress() {
        val options = FacebookAccessOptions.forRestrictedUrl("https://www.facebook.com/groups/12345/posts/678")
        assertEquals(3, options.size)
        assertTrue(options.all { it.url == "https://www.facebook.com/groups/12345/posts/678" })
        assertTrue(options.first().description.contains("does not sign in for you"))
        assertEquals(emptyList<FacebookOpenOption>(), FacebookAccessOptions.forRestrictedUrl(null))
        assertEquals(emptyList<FacebookOpenOption>(), FacebookAccessOptions.forRestrictedUrl("https://evil.example/groups/1"))
        assertEquals(emptyList<FacebookOpenOption>(), FacebookAccessOptions.forRestrictedUrl("http://www.facebook.com/groups/1"))
        assertEquals(emptyList<FacebookOpenOption>(), FacebookAccessOptions.forRestrictedUrl("javascript:alert(1)"))
    }

    // ---------------------------------------------------------------- official export guidance

    @Test fun exportGuidesPointOnlyToFacebooksOwnHelpPages() {
        assertEquals(2, FacebookExport.all.size)
        FacebookExport.all.forEach { guide ->
            assertTrue(FacebookLinks.isOfficialPageUrl(guide.helpUrl))
            assertTrue(guide.steps.size >= 2)
            assertTrue(guide.title.isNotBlank() && guide.intro.isNotBlank() && guide.helpLabel.isNotBlank())
        }
        assertTrue(FacebookExport.yourAccount.steps.any { it.contains("password in Facebook, not here") })
        assertTrue(FacebookExport.yourAccount.steps.any { it.contains("Files") })
    }

    // ---------------------------------------------------------------- the whole module

    private fun integration(script: (String) -> FacebookReply): FacebookIntegration {
        val http = object : FacebookHttp {
            override suspend fun get(url: String, headers: Map<String, String>) = script(url)
        }
        return FacebookIntegration(FacebookMetadataClient(http) { null })
    }

    @Test fun inspectCombinesLookupOptionsDecisionAndExportGuides() {
        val report = runBlocking {
            integration { FacebookReply(200, """{"html":"<div></div>","width":267,"height":476}""") }.inspect(ref)
        }
        assertTrue(report.lookup is FacebookLookup.Found)
        assertEquals(3, report.openOptions.size)
        assertTrue(report.download is FacebookDownloadDecision.NotAvailable)
        assertEquals(FacebookExport.all, report.exportGuides)
    }

    @Test fun inspectNeverThrowsOnAServiceFailure() {
        val offline = runBlocking { integration { throw FacebookNetworkException("offline") }.inspect(ref) }
        assertTrue(offline.lookup is FacebookLookup.Failed)
        assertEquals(3, offline.openOptions.size)
        val surprise = runBlocking { integration { throw IllegalStateException("unexpected") }.inspect(ref) }
        val failed = surprise.lookup as FacebookLookup.Failed
        assertEquals(FacebookProblem.BadResponse, failed.problem)
        assertNull((surprise.download as? FacebookDownloadDecision.Offered))
    }

    @Test fun orientationNeedsBothSides() {
        assertNull(info().orientation)
        assertEquals("Landscape", info().copy(widthPx = 640, heightPx = 360).orientation)
        assertNull(info().copy(widthPx = 0, heightPx = 360).orientation)
        assertFalse(info().ownedByViewer)
    }
}
