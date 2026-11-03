package com.almus.downloader.tiktok

import com.almus.downloader.platform.SmartLinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validation of ids, creator names, content references, openable addresses and trusted thumbnail hosts. */
class TikTokLinksTest {
    private val detector = SmartLinkDetector()
    private val id = "7234567890123456789"

    private fun ref(url: String) = TikTokLinks.contentRef(url, detector)
    private fun openable(url: String) = TikTokLinks.openableUrl(detector.detect(url))

    // ---------------------------------------------------------------- ids and names

    @Test fun contentIdsMustBeFifteenToTwentyDigits() {
        listOf("7234567890123456789", "6718335390845095173", "123456789012345", "12345678901234567890")
            .forEach { assertTrue("valid $it", TikTokLinks.isValidContentId(it)) }
        listOf(null, "", "1", "12345678901234", "123456789012345678901", "72345678901234567a9", " 7234567890123456789",
            "7234567890123456789 ", "-7234567890123456789", "7234567890123456789.0", "７２３４５６７８９０１２３４５６７８９")
            .forEach { assertFalse("invalid $it", TikTokLinks.isValidContentId(it)) }
    }

    @Test fun creatorNamesAreLettersDigitsUnderscoresAndPeriods() {
        listOf("user", "a", "scout2015", "some.user_01", "User.Name", "a".repeat(24), "_", "1234", "a.b.c")
            .forEach { assertTrue("valid $it", TikTokLinks.isValidHandle(it)) }
        listOf(null, "", "@user", "a".repeat(25), "us er", "user/", "user?x", "user#x", "us-er", "üser", "user%40", "user\n", "<b>", "../x")
            .forEach { assertFalse("invalid $it", TikTokLinks.isValidHandle(it)) }
    }

    // ---------------------------------------------------------------- content references

    @Test fun aVideoAddressBecomesAReferenceWithTheCreatorName() {
        val r = ref("https://www.tiktok.com/@Scout.2015/video/$id")!!
        assertEquals(id, r.contentId)
        assertEquals("Scout.2015", r.handle)
        assertEquals("https://www.tiktok.com/@Scout.2015/video/$id", r.canonicalUrl)
    }

    @Test fun shareAndTrackingPartsOfTheAddressNeverReachTheReference() {
        val r = ref("https://www.tiktok.com/@user/video/$id?_r=1&_t=ZT-abc&u_code=secret&sec_user_id=zzz&share_link_id=q#top")!!
        assertEquals("https://www.tiktok.com/@user/video/$id", r.canonicalUrl)
        assertFalse(r.canonicalUrl.contains("secret"))
        assertFalse(r.canonicalUrl.contains("?"))
        assertFalse(r.canonicalUrl.contains("#"))
    }

    @Test fun everyHostFormOfAVideoAddressIsAccepted() {
        listOf(
            "https://tiktok.com/@user/video/$id",
            "https://m.tiktok.com/@user/video/$id",
            "https://us.tiktok.com/@user/video/$id",
            "http://www.tiktok.com/@user/video/$id",
            "https://WWW.TIKTOK.COM/@user/video/$id",
            "  https://www.tiktok.com/@user/video/$id  ",
        ).forEach {
            val r = ref(it)
            assertEquals("ref for $it", id, r?.contentId)
            assertEquals("handle for $it", "user", r?.handle)
        }
    }

    @Test fun addressesWithoutACreatorNameStillGiveAReference() {
        listOf(
            "https://www.tiktok.com/embed/v2/$id",
            "https://www.tiktok.com/embed/$id",
            "https://www.tiktok.com/player/v1/$id?music_info=1",
            "https://m.tiktok.com/v/$id.html",
            "https://www.tiktok.com/v/$id",
            "https://www.tiktok.com/@/video/$id",
        ).forEach {
            val r = ref(it)
            assertEquals("ref for $it", id, r?.contentId)
            assertNull("no name for $it", r?.handle)
            assertEquals("https://www.tiktok.com/@/video/$id", r?.canonicalUrl)
        }
    }

    @Test fun anInvalidCreatorNameIsDroppedButTheVideoIsKept() {
        val r = ref("https://www.tiktok.com/@${"a".repeat(30)}/video/$id")!!
        assertEquals(id, r.contentId)
        assertNull(r.handle)
    }

    @Test fun onlyPlainVideoAddressesGiveAReference() {
        listOf(
            "https://www.tiktok.com/@user/photo/$id",
            "https://www.tiktok.com/@user/live",
            "https://www.tiktok.com/@user",
            "https://www.tiktok.com/",
            "https://vm.tiktok.com/ZMabc123/",
            "https://www.tiktok.com/t/ZTabc123",
            "https://www.tiktok.com/login",
            "https://www.tiktok.com/@user/video/abc",
            "https://www.tiktok.com/@user/video/12345",
            "https://www.tiktok.com/@user/video/123456789012345678901",
            "https://www.tiktok.com/tag/cats",
            "https://v16.tiktokcdn.com/abc/video.mp4",
        ).forEach { assertNull("no ref for $it", ref(it)) }
    }

    @Test fun otherPlatformsLookAlikesAndBadInputGiveNoReference() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.facebook.com/reel/123456789012345",
            "https://tiktok.com.evil.com/@user/video/$id",
            "https://nottiktok.com/@user/video/$id",
            "https://evil.com/www.tiktok.com/@user/video/$id",
            "https://user:pass@www.tiktok.com/@user/video/$id",
            "javascript:alert(1)",
            "www.tiktok.com/@user/video/$id",
            "not a link",
            "",
        ).forEach { assertNull("no ref for $it", ref(it)) }
        assertNull(TikTokLinks.contentRef(null as String?))
    }

    @Test fun aRestrictedDetectionNeverGivesAReference() {
        // A detection that was turned into "restricted" by a sign-in page is never treated as a usable video.
        assertNull(ref("https://www.tiktok.com/login?redirect_url=https%3A%2F%2Fwww.tiktok.com%2F%40user%2Fvideo%2F$id"))
    }

    // ---------------------------------------------------------------- openable addresses

    @Test fun aVideoIsOpenedThroughItsCleanOfficialAddress() {
        assertEquals(
            "https://www.tiktok.com/@user/video/$id",
            openable("https://m.tiktok.com/@user/video/$id?_r=1&u_code=secret"),
        )
        assertEquals("https://www.tiktok.com/@/video/$id", openable("https://www.tiktok.com/embed/v2/$id"))
        assertEquals("https://www.tiktok.com/@user/video/$id", openable("http://www.tiktok.com/@user/video/$id"))
    }

    @Test fun photoLiveAndProfileAddressesAreUpgradedAndStripped() {
        assertEquals("https://www.tiktok.com/@user/photo/$id", openable("http://www.tiktok.com/@user/photo/$id?_r=1#x"))
        assertEquals("https://www.tiktok.com/@user/live", openable("https://www.tiktok.com/@user/live?utm_source=share"))
        assertEquals("https://www.tiktok.com/live", openable("https://www.tiktok.com/live"))
        assertEquals("https://www.tiktok.com/@user", openable("https://www.tiktok.com/@user?lang=en"))
    }

    @Test fun nothingElseIsOpenable() {
        listOf(
            "https://www.tiktok.com/login",
            "https://www.tiktok.com/signup",
            "https://vm.tiktok.com/ZMabc123/",
            "https://www.tiktok.com/t/ZTabc123",
            "https://www.tiktok.com/",
            "https://www.tiktok.com/tag/cats",
            "https://v16.tiktokcdn.com/abc/video.mp4",
            "https://tiktok.com.evil.com/@user/video/$id",
            "https://www.facebook.com/watch/?v=123456789012345",
            "https://example.com/a.zip",
            "javascript:alert(1)",
        ).forEach { assertNull("not openable: $it", openable(it)) }
    }

    @Test fun onlyCleanOfficialPageAddressesAreAccepted() {
        listOf(
            "https://www.tiktok.com/@user/video/$id",
            "https://www.tiktok.com/@user",
            "https://tiktok.com/",
            "https://support.tiktok.com/",
        ).forEach { assertTrue("official $it", TikTokLinks.isOfficialPageUrl(it)) }
        listOf(
            null,
            "",
            "http://www.tiktok.com/@user",
            "https://www.tiktok.com/@user?x=1",
            "https://www.tiktok.com/@user#x",
            "https://user@www.tiktok.com/@user",
            "https://tiktok.com.evil.com/@user",
            "https://nottiktok.com/@user",
            "https://evil.com/www.tiktok.com/",
            "https://v16.tiktokcdn.com/a",
            "https://www.facebook.com/",
            "javascript:alert(1)",
            "intent://www.tiktok.com/@user#Intent;scheme=https;end",
            "snssdk1233://user/profile/1",
        ).forEach { assertFalse("not official $it", TikTokLinks.isOfficialPageUrl(it)) }
    }

    // ---------------------------------------------------------------- thumbnails and profiles from answers

    @Test fun thumbnailsComeOnlyFromTikTokImageHostsOverHttps() {
        listOf(
            "https://p16.muscdn.com/obj/tos-maliva-p-0068/06kv6rfcesljdjr45ukb0000d844090v0200010605",
            "https://p16-sign-va.tiktokcdn.com/tos-maliva-p-0068/abc~tplv-noop.image?x-expires=1&x-signature=Fj%2B",
            "https://p19-sign.tiktokcdn-us.com/obj/x.jpeg",
            "https://p16-sign.tiktokcdn-eu.com/obj/x.jpeg",
            "https://TIKTOKCDN.COM/obj/x",
        ).forEach { assertTrue("trusted $it", TikTokLinks.isTrustedThumbnail(it)) }
        listOf(
            null,
            "",
            "http://p16-sign-va.tiktokcdn.com/x.jpeg",
            "https://www.tiktok.com/x.jpeg",
            "https://tiktokcdn.com.evil.com/x.jpeg",
            "https://evil.com/tiktokcdn.com/x.jpeg",
            "https://nottiktokcdn.com/x.jpeg",
            "https://user:pass@p16.tiktokcdn.com/x.jpeg",
            "https://example.com/x.jpeg",
            "data:image/png;base64,AAAA",
            "file:///sdcard/x.jpg",
            "//p16.tiktokcdn.com/x.jpeg",
        ).forEach { assertFalse("untrusted $it", TikTokLinks.isTrustedThumbnail(it)) }
    }

    @Test fun profileAddressesFromAnswersNeedAValidNameOnTikTok() {
        assertEquals("scout2015", TikTokLinks.handleFromProfileUrl("https://www.tiktok.com/@scout2015"))
        assertEquals("a.b_c", TikTokLinks.handleFromProfileUrl("https://tiktok.com/@a.b_c"))
        listOf(
            null,
            "",
            "http://www.tiktok.com/@scout2015",
            "https://www.tiktok.com/@scout2015/video/$id",
            "https://www.tiktok.com/scout2015",
            "https://www.tiktok.com/@",
            "https://www.tiktok.com/@bad%20name",
            "https://www.tiktok.com/@${"a".repeat(25)}",
            "https://evil.com/@scout2015",
            "https://tiktok.com.evil.com/@scout2015",
        ).forEach { assertNull("no name in $it", TikTokLinks.handleFromProfileUrl(it)) }
        assertEquals("https://www.tiktok.com/@scout2015", TikTokLinks.profileUrlFor("scout2015"))
        assertNull(TikTokLinks.profileUrlFor("bad name"))
        assertNull(TikTokLinks.profileUrlFor(null))
    }
}
