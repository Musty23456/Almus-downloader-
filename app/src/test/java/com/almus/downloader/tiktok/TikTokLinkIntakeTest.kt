package com.almus.downloader.tiktok

import com.almus.downloader.download.InspectResult
import com.almus.downloader.platform.IntakeResult
import com.almus.downloader.platform.LinkIntake
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.RedirectTrace
import com.almus.downloader.platform.SmartLinkDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole path for TikTok links: short link, safe redirect, detection, validated reference. Nothing is downloaded. */
class TikTokLinkIntakeTest {
    private val id = "6718335390845095173"
    private val long = "https://www.tiktok.com/@scout2015/video/$id?_r=1&_t=ZT-abc&u_code=secret&share_app_id=1233"

    private class Calls {
        val resolved = mutableListOf<String>()
        val inspected = mutableListOf<String>()
    }

    private fun intake(calls: Calls, resolve: (String) -> RedirectTrace) = LinkIntake(
        detector = SmartLinkDetector(),
        resolve = { calls.resolved += it; resolve(it) },
        inspect = { calls.inspected += it; InspectResult.Failure("not expected") },
        isDuplicate = { false },
    )

    private fun check(i: LinkIntake, url: String) = runBlocking { i.check(url) }

    @Test fun aShortLinkIsResolvedAndBecomesACleanVideoReference() {
        listOf("https://vm.tiktok.com/ZMabc123/", "https://vt.tiktok.com/ZSabc123/", "https://www.tiktok.com/t/ZTabc123/").forEach { short ->
            val calls = Calls()
            val result = check(intake(calls) { RedirectTrace.Resolved(long, listOf(it, long), 200) }, short) as IntakeResult.Detected
            assertEquals(listOf(short), calls.resolved)
            assertEquals(emptyList<String>(), calls.inspected)
            assertEquals(MediaPlatform.TikTok, result.detection.platform)
            val ref = TikTokLinks.contentRef(result.detection)!!
            assertEquals(id, ref.contentId)
            assertEquals("scout2015", ref.handle)
            assertEquals("https://www.tiktok.com/@scout2015/video/$id", ref.canonicalUrl)
        }
    }

    @Test fun aBotProtectionOrMissingPageStatusDoesNotHideTheVideoFromTikTokItself() {
        listOf(401, 403, 404, 410, 429, 500).forEach { status ->
            val result = check(intake(Calls()) { RedirectTrace.Resolved(long, listOf(it, long), status) }, "https://vm.tiktok.com/ZMabc123/") as IntakeResult.Detected
            assertEquals("status $status", LinkVerdict.PageNotDownloadable, result.detection.verdict)
            assertNotNull("status $status", TikTokLinks.contentRef(result.detection))
        }
    }

    @Test fun aPlainVideoLinkNeedsNoRedirectCheck() {
        val calls = Calls()
        val result = check(intake(calls) { error("no redirect check expected") }, long) as IntakeResult.Detected
        assertEquals(emptyList<String>(), calls.resolved)
        assertEquals(emptyList<String>(), calls.inspected)
        assertEquals(id, TikTokLinks.contentRef(result.detection)?.contentId)
    }

    @Test fun aShortLinkThatLeadsToASignInPageIsRestrictedAndGivesNoReference() {
        val login = "https://www.tiktok.com/login?redirect_url=https%3A%2F%2Fwww.tiktok.com%2F%40scout2015%2Fvideo%2F$id"
        val result = check(intake(Calls()) { RedirectTrace.Resolved(login, listOf(it, login), 200) }, "https://vm.tiktok.com/ZMabc123/") as IntakeResult.Detected
        assertEquals(LinkVerdict.Restricted, result.detection.verdict)
        assertNull(TikTokLinks.contentRef(result.detection))
        assertNull(TikTokLinks.openableUrl(result.detection))
    }

    @Test fun aShortLinkThatLeadsToAPhotoOrProfileIsOpenableWithoutTrackingParts() {
        val photo = "https://www.tiktok.com/@scout2015/photo/$id?_r=1&u_code=secret"
        val result = check(intake(Calls()) { RedirectTrace.Resolved(photo, listOf(it, photo), 200) }, "https://vm.tiktok.com/ZMabc123/") as IntakeResult.Detected
        assertNull(TikTokLinks.contentRef(result.detection))
        assertEquals("https://www.tiktok.com/@scout2015/photo/$id", TikTokLinks.openableUrl(result.detection))
    }

    @Test fun aShortLinkThatLeadsToAnotherSiteIsNeverTreatedAsTikTok() {
        val elsewhere = "https://evil.example/@scout2015/video/$id"
        val calls = Calls()
        val result = check(intake(calls) { RedirectTrace.Resolved(elsewhere, listOf(it, elsewhere), 200) }, "https://vm.tiktok.com/ZMabc123/")
        // The ordinary file check decides (here it refuses); the TikTok dialog is never reached.
        assertTrue(result is IntakeResult.Rejected)
        assertEquals(listOf(elsewhere), calls.inspected)
        assertNull(TikTokLinks.contentRef(elsewhere))
    }

    @Test fun aShortLinkThatLeadsToAnotherShortLinkIsRefused() {
        val result = check(intake(Calls()) { RedirectTrace.Resolved("https://vt.tiktok.com/ZSnext/", listOf(it), 200) }, "https://vm.tiktok.com/ZMabc123/")
        assertTrue(result is IntakeResult.Rejected)
    }

    @Test fun aFailedRedirectCheckIsShownAsAMessage() {
        val result = check(intake(Calls()) { RedirectTrace.Failed("The link redirects too many times.") }, "https://vm.tiktok.com/ZMabc123/")
        assertEquals("The link redirects too many times.", (result as IntakeResult.Rejected).message)
    }

    @Test fun aTikTokVideoPageIsNeverSentToTheFileCheck() {
        val calls = Calls()
        check(intake(calls) { error("no redirect check expected") }, long)
        assertEquals(emptyList<String>(), calls.inspected)
    }
}
