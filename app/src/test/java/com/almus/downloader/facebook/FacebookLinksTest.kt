package com.almus.downloader.facebook

import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.SmartLinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookLinksTest {
    private val id = "123456789012345"
    private val detector = SmartLinkDetector()

    private fun ref(url: String?) = FacebookLinks.contentRef(url)

    @Test fun recognisesVideosReelsAndLiveVideos() {
        val expectations = mapOf(
            "https://www.facebook.com/watch/?v=$id" to LinkKind.Video,
            "https://m.facebook.com/watch/?v=$id&ref=share" to LinkKind.Video,
            "https://www.facebook.com/video.php?v=$id" to LinkKind.Video,
            "https://www.facebook.com/somepage/videos/$id/" to LinkKind.Video,
            "https://www.facebook.com/reel/$id" to LinkKind.ShortVideo,
            "https://www.facebook.com/reels/$id" to LinkKind.ShortVideo,
            "https://www.facebook.com/watch/live/?v=$id" to LinkKind.Live,
        )
        expectations.forEach { (url, kind) ->
            val r = ref(url)
            assertEquals("id of $url", id, r?.contentId)
            assertEquals("kind of $url", kind, r?.kind)
        }
    }

    @Test fun addressesAreBuiltFromTheValidatedIdOnly() {
        assertEquals("https://www.facebook.com/watch/?v=$id", ref("https://m.facebook.com/watch/?v=$id&fbclid=tracking")!!.canonicalUrl)
        assertEquals("https://www.facebook.com/reel/$id", ref("https://www.facebook.com/reels/$id?s=x")!!.canonicalUrl)
        assertEquals("https://www.facebook.com/watch/live/?v=$id", ref("https://www.facebook.com/watch/live/?v=$id")!!.canonicalUrl)
    }

    @Test fun rejectsEverythingThatIsNotAPlainVideo() {
        listOf(
            "https://www.facebook.com/somepage",
            "https://www.facebook.com/somepage/posts/pfbid0abc",
            "https://www.facebook.com/photo.php?fbid=123456789",
            "https://www.facebook.com/groups/12345/posts/678",
            "https://www.facebook.com/stories/123/456",
            "https://www.facebook.com/login/?next=x",
            "https://www.facebook.com/watch/",
            "https://www.facebook.com/watch/?v=abc",
            "https://www.facebook.com/watch/?v=1234",
            "https://www.facebook.com/watch/?v=${"9".repeat(21)}",
            "https://fb.watch/abc123xyz/",
            "https://www.facebook.com/share/v/1AbCdEf/",
            "https://video.xx.fbcdn.net/v/t42/clip.mp4",
            "https://facebook.com.evil.example/watch/?v=$id",
            "https://notfacebook.com/watch/?v=$id",
            "ftp://www.facebook.com/watch/?v=$id",
            "javascript:alert(1)",
            "not a link",
            "",
        ).forEach { assertNull("should not be a video: $it", ref(it)) }
        assertNull(FacebookLinks.contentRef(null as String?))
    }

    @Test fun aVideoThatTurnedOutToBeRestrictedIsNoLongerUsable() {
        val d = detector.detect("https://www.facebook.com/watch/?v=$id", com.almus.downloader.platform.ObservedResponse(status = 403))
        assertNull(FacebookLinks.contentRef(d))
    }

    @Test fun idChecks() {
        assertTrue(FacebookLinks.isValidContentId("12345"))
        assertTrue(FacebookLinks.isValidContentId("1".repeat(20)))
        assertFalse(FacebookLinks.isValidContentId("1234"))
        assertFalse(FacebookLinks.isValidContentId("1".repeat(21)))
        assertFalse(FacebookLinks.isValidContentId("12345a"))
        assertFalse(FacebookLinks.isValidContentId("12345 "))
        assertFalse(FacebookLinks.isValidContentId(null))
        assertEquals("https://www.facebook.com/100001", FacebookLinks.profileUrlFor("100001"))
        assertNull(FacebookLinks.profileUrlFor("../evil"))
        assertNull(FacebookLinks.profileUrlFor(null))
    }

    @Test fun thumbnailsAndProfilesAreOnlyTrustedOnFacebooksOwnHosts() {
        assertTrue(FacebookLinks.isTrustedThumbnail("https://scontent.xx.fbcdn.net/v/t15/pic.jpg"))
        assertFalse(FacebookLinks.isTrustedThumbnail("http://scontent.xx.fbcdn.net/v/t15/pic.jpg"))
        assertFalse(FacebookLinks.isTrustedThumbnail("https://fbcdn.net.evil.example/pic.jpg"))
        assertFalse(FacebookLinks.isTrustedThumbnail("https://example.com/pic.jpg"))
        assertFalse(FacebookLinks.isTrustedThumbnail("https://www.facebook.com/pic.jpg"))
        assertFalse(FacebookLinks.isTrustedThumbnail(null))
        assertTrue(FacebookLinks.isTrustedProfileUrl("https://www.facebook.com/somepage"))
        assertFalse(FacebookLinks.isTrustedProfileUrl("https://example.com/somepage"))
        assertFalse(FacebookLinks.isTrustedProfileUrl("http://www.facebook.com/somepage"))
    }

    @Test fun restrictedContentMayBeOpenedInFacebookButNothingElse() {
        fun openable(url: String) = FacebookLinks.openableUrl(detector.detect(url))
        assertEquals("https://www.facebook.com/groups/12345/posts/678", openable("https://www.facebook.com/groups/12345/posts/678"))
        assertEquals("https://www.facebook.com/stories/123/456", openable("https://www.facebook.com/stories/123/456"))
        assertEquals("https://m.facebook.com/somepage", openable("http://m.facebook.com/somepage"))
        assertNull("sign-in pages are not content", openable("https://www.facebook.com/login/?next=x"))
        assertNull("file hosts are not pages", openable("https://video.xx.fbcdn.net/v/t42/clip.mp4"))
        assertNull("short links are opened only after they are resolved", openable("https://fb.watch/abc123xyz/"))
        assertNull("other platforms are never opened here", openable("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(openable("https://facebook.com.evil.example/groups/1/"))
        assertNull(openable("javascript:alert(1)"))
    }

    @Test fun officialPageAddressCheck() {
        assertTrue(FacebookLinks.isOfficialPageUrl("https://www.facebook.com/watch/?v=$id"))
        assertTrue(FacebookLinks.isOfficialPageUrl("https://fb.watch/abc/"))
        assertFalse(FacebookLinks.isOfficialPageUrl("http://www.facebook.com/watch/?v=$id"))
        assertFalse(FacebookLinks.isOfficialPageUrl("https://video.xx.fbcdn.net/v/clip.mp4"))
        assertFalse(FacebookLinks.isOfficialPageUrl("https://facebook.com.evil.example/x"))
        assertFalse(FacebookLinks.isOfficialPageUrl("https://user:pw@www.facebook.com/x"))
        assertFalse(FacebookLinks.isOfficialPageUrl(null))
    }

    @Test fun tokenShapeIsCheckedBeforeUse() {
        assertTrue(FacebookAccessToken.isValidFormat("EAABwzLixnjYBO0123456789abcdefGHIJKL"))
        assertTrue(FacebookAccessToken.isValidFormat("1234567890123456|AbCdEfGhIjKlMnOpQrStUvWxYz_-"))
        assertFalse(FacebookAccessToken.isValidFormat("short"))
        assertFalse(FacebookAccessToken.isValidFormat("has a space 0123456789abcdefghij"))
        assertFalse(FacebookAccessToken.isValidFormat("line\nbreak0123456789abcdefghij"))
        assertFalse(FacebookAccessToken.isValidFormat("a".repeat(513)))
        assertFalse(FacebookAccessToken.isValidFormat(null))
    }
}
