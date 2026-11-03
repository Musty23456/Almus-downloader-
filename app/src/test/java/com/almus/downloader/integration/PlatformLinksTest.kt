package com.almus.downloader.integration

import com.almus.downloader.integration.specs.InstagramSpec
import com.almus.downloader.integration.specs.PinterestSpec
import com.almus.downloader.integration.specs.RedditSpec
import com.almus.downloader.integration.specs.VimeoSpec
import com.almus.downloader.integration.specs.XSpec
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.ObservedResponse
import com.almus.downloader.platform.SmartLinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformLinksTest {
    private val detector = SmartLinkDetector()

    private fun ref(url: String?) = PlatformLinks.contentRef(url, detector)

    private fun canonical(url: String): String {
        val r = ref(url)
        assertNotNull("no reference for $url", r)
        return PlatformSpecs.of(r!!.platform)!!.canonicalUrl(r)
    }

    private fun openable(url: String): String? = PlatformLinks.openableUrl(detector.detect(url))

    // ---------------------------------------------------------------- the registry

    @Test fun sixPlatformsAreHandledAndTheOtherModulesAreNot() {
        assertEquals(
            listOf(
                MediaPlatform.Instagram, MediaPlatform.X, MediaPlatform.Vimeo,
                MediaPlatform.Dailymotion, MediaPlatform.Reddit, MediaPlatform.Pinterest,
            ),
            PlatformSpecs.all.map { it.platform },
        )
        assertNull(PlatformSpecs.of(MediaPlatform.YouTube))
        assertNull(PlatformSpecs.of(MediaPlatform.Facebook))
        assertNull(PlatformSpecs.of(MediaPlatform.TikTok))
        assertNull(PlatformSpecs.of(MediaPlatform.DirectLink))
        assertNull(PlatformSpecs.of(null))
    }

    @Test fun everyPlatformHasItsOwnServiceAddress() {
        val hosts = PlatformSpecs.all.map { it.oembed.host }
        assertEquals(hosts.size, hosts.toSet().size)
        PlatformSpecs.all.forEach { spec ->
            assertTrue(spec.oembed.path.startsWith("/"))
            assertTrue(spec.siteLabel.isNotBlank())
            assertTrue(spec.appPackages.isNotEmpty())
            assertTrue(spec.downloadReasons.isNotEmpty())
            assertTrue(spec.downloadAlternatives.isNotEmpty())
        }
    }

    // ---------------------------------------------------------------- platform identification and validation

    @Test fun instagramPostsReelsAndVideosAreRecognisedAndRebuilt() {
        assertEquals("https://www.instagram.com/p/CxYz123AbCd/", canonical("https://www.instagram.com/p/CxYz123AbCd/"))
        assertEquals("https://www.instagram.com/reel/Cabc12345_x/", canonical("https://www.instagram.com/reel/Cabc12345_x/?igsh=abc"))
        assertEquals("https://www.instagram.com/reel/Cabc12345_x/", canonical("https://www.instagram.com/someuser/reel/Cabc12345_x/"))
        assertEquals("https://www.instagram.com/tv/Cabc12345/", canonical("https://www.instagram.com/tv/Cabc12345/"))
        assertEquals(LinkKind.ShortVideo, ref("https://www.instagram.com/reel/Cabc12345_x/")!!.kind)
    }

    @Test fun instagramPagesThatAreNotContentGetNoReference() {
        listOf(
            "https://www.instagram.com/stories/someuser/123456/",
            "https://www.instagram.com/accounts/login/",
            "https://www.instagram.com/someuser/",
            "https://www.instagram.com/share/reel/abc",
            "https://www.instagram.com/p/ab/",
            "https://instagram.com.evil.example/p/CxYz123AbCd/",
        ).forEach { assertNull("reference for $it", ref(it)) }
    }

    @Test fun xPostsKeepTheAccountNameWhenThereIsOne() {
        val withName = ref("https://x.com/jack/status/1234567890123456789?s=20&t=abc")!!
        assertEquals("jack", withName.handle)
        assertEquals("https://x.com/jack/status/1234567890123456789", XSpec.canonicalUrl(withName))
        assertEquals("https://twitter.com/jack/status/1234567890123456789", XSpec.oembedSubject(withName))

        val anonymous = ref("https://twitter.com/i/status/1234567890123456789")!!
        assertNull(anonymous.handle)
        assertEquals("https://x.com/i/status/1234567890123456789", XSpec.canonicalUrl(anonymous))
    }

    @Test fun xPagesThatAreNotPostsGetNoReference() {
        listOf(
            "https://x.com/jack",
            "https://x.com/i/spaces/1abc",
            "https://t.co/abc123",
            "https://x.com/home",
            "https://x.com/i/flow/login",
            "https://x.com/jack/status/123",
            "https://pbs.twimg.com/media/abc?format=jpg&name=small",
        ).forEach { assertNull("reference for $it", ref(it)) }
    }

    @Test fun vimeoVideosInAllTheirShapes() {
        assertEquals("https://vimeo.com/76979871", canonical("https://vimeo.com/76979871"))
        assertEquals("https://vimeo.com/76979871", canonical("https://player.vimeo.com/video/76979871?h=abc"))
        assertEquals("https://vimeo.com/76979871", canonical("https://vimeo.com/channels/staffpicks/76979871"))
        assertNull(ref("https://vimeo.com/12"))
        assertNull(ref("https://vimeo.com/user123"))
        assertNull(ref("https://vimeo.com/ondemand/film"))
    }

    @Test fun dailymotionVideosInAllTheirShapes() {
        val expected = "https://www.dailymotion.com/video/x8abc12"
        assertEquals(expected, canonical("https://www.dailymotion.com/video/x8abc12"))
        assertEquals(expected, canonical("https://dai.ly/x8abc12"))
        assertEquals(expected, canonical("https://www.dailymotion.com/video/x8abc12_some-title"))
        assertEquals(expected, canonical("https://geo.dailymotion.com/player.html?video=x8abc12"))
        assertNull(ref("https://www.dailymotion.com/someuser"))
    }

    @Test fun redditPostsKeepTheCommunityWhenThereIsOne() {
        val withCommunity = ref("https://www.reddit.com/r/kotlin/comments/abc123/some_title/")!!
        assertEquals("kotlin", withCommunity.handle)
        assertEquals("https://www.reddit.com/r/kotlin/comments/abc123/", RedditSpec.canonicalUrl(withCommunity))
        assertEquals("https://www.reddit.com/r/kotlin/comments/abc123/", canonical("https://old.reddit.com/r/kotlin/comments/abc123/x/"))
        assertEquals("https://www.reddit.com/comments/abc123/", canonical("https://redd.it/abc123"))
        assertEquals("https://www.reddit.com/comments/abc123/", canonical("https://www.reddit.com/gallery/abc123"))
    }

    @Test fun aRedditVideoAddressIsNotAPostAndGetsNoReference() {
        // The id on v.redd.it is the video's, not a post id: asking the post service about it would be wrong.
        assertNull(ref("https://v.redd.it/abcd1234"))
        assertNull(ref("https://i.redd.it/abcd1234.jpg"))
        assertNull(ref("https://www.reddit.com/r/kotlin/"))
    }

    @Test fun pinterestPinsOnAnyCountryDomainBecomeOneOfficialAddress() {
        val expected = "https://www.pinterest.com/pin/123456789012345678/"
        assertEquals(expected, canonical("https://www.pinterest.com/pin/123456789012345678/"))
        assertEquals(expected, canonical("https://www.pinterest.fr/pin/some-title--123456789012345678/"))
        assertNull(ref("https://pin.it/abc"))
        assertNull(ref("https://www.pinterest.com/someuser/"))
        assertNull(ref("https://pinterest.com.evil.example/pin/123456789012345678/"))
    }

    @Test fun otherPlatformsAndPlainLinksGetNoReference() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "https://example.com/video.mp4",
            "not a link",
            "",
            null,
        ).forEach { assertNull("reference for $it", ref(it)) }
    }

    @Test fun aPageThePlatformAnsweredAsGoneGetsNoReference() {
        val detection = detector.detect("https://vimeo.com/76979871", ObservedResponse(status = 404))
        assertNull(PlatformLinks.contentRef(detection))
        assertNull(PlatformLinks.openableUrl(detection))
    }

    // ---------------------------------------------------------------- addresses that may be opened

    @Test fun contentIsOpenedAtTheRebuiltAddress() {
        assertEquals("https://vimeo.com/76979871", openable("http://vimeo.com/76979871?utm_source=x#t=10s"))
        assertEquals("https://www.reddit.com/r/kotlin/comments/abc123/", openable("https://www.reddit.com/r/kotlin/comments/abc123/x/?utm_name=y"))
    }

    @Test fun profilesAndBoardsAreOpenedAtTheEnteredAddressWithoutQueryOrFragment() {
        assertEquals("https://x.com/jack", openable("https://x.com/jack?s=09"))
        assertEquals("https://www.instagram.com/someuser/", openable("http://www.instagram.com/someuser/?hl=en#top"))
        assertEquals("https://vimeo.com/user123", openable("https://vimeo.com/user123"))
        assertEquals("https://www.pinterest.fr/someuser/board/", openable("https://www.pinterest.fr/someuser/board/"))
    }

    @Test fun signInPagesStoriesShortLinksFileHostsAndLookAlikesAreNeverOpened() {
        listOf(
            "https://www.instagram.com/accounts/login/",
            "https://www.instagram.com/stories/someuser/123456/",
            "https://t.co/abc123",
            "https://pin.it/abc",
            "https://i.vimeocdn.com/video/1_295x166.jpg",
            "https://x.com.evil.example/jack",
            "https://x.com/home",
            "https://www.youtube.com/@somechannel",
        ).forEach { assertNull("openable $it", openable(it)) }
    }

    @Test fun officialPageAddressesAreStrict() {
        assertTrue(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://vimeo.com/76979871"))
        assertTrue(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://player.vimeo.com/video/76979871"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://vimeo.com/76979871?x=1"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://vimeo.com/76979871#t=1"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "http://vimeo.com/76979871"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://vimeo.com:8443/76979871"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://vimeo.com.evil.example/76979871"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, "https://x.com/jack"))
        assertFalse(PlatformLinks.isOfficialPageUrl(VimeoSpec, null))
        assertTrue(PlatformLinks.isOfficialPageUrl(PinterestSpec, "https://www.pinterest.co.uk/pin/123456789012345678/"))
        assertFalse(PlatformLinks.isOfficialPageUrl(PinterestSpec, "https://www.pinterest.evil/pin/1/"))
    }

    // ---------------------------------------------------------------- thumbnails and creators

    @Test fun thumbnailsAreTrustedOnlyFromTheirOwnPlatformsImageHostsOverHttps() {
        assertTrue(PlatformLinks.isTrustedThumbnail(VimeoSpec, "https://i.vimeocdn.com/video/452001751_295x166.jpg"))
        assertTrue(PlatformLinks.isTrustedThumbnail(PinterestSpec, "https://i.pinimg.com/236x/aa/bb/cc.jpg"))
        assertTrue(PlatformLinks.isTrustedThumbnail(XSpec, "https://pbs.twimg.com/media/abc.jpg"))
        assertTrue(PlatformLinks.isTrustedThumbnail(RedditSpec, "https://preview.redd.it/abc.jpg"))
        assertTrue(PlatformLinks.isTrustedThumbnail(RedditSpec, "https://external-preview.redd.it/abc.jpg"))
        assertTrue(PlatformLinks.isTrustedThumbnail(InstagramSpec, "https://scontent.cdninstagram.com/v/abc.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnail(VimeoSpec, "http://i.vimeocdn.com/video/1.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnail(VimeoSpec, "https://vimeocdn.com.evil.example/1.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnail(VimeoSpec, "https://i.pinimg.com/236x/aa.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnail(VimeoSpec, "https://192.168.0.5/1.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnail(VimeoSpec, null))
        assertTrue(PlatformLinks.isTrustedThumbnailAnywhere("https://i.pinimg.com/236x/aa.jpg"))
        assertFalse(PlatformLinks.isTrustedThumbnailAnywhere("https://i.ytimg.com/vi/x/hqdefault.jpg"))
    }

    @Test fun creatorAddressesAreKeptOnlyOnTheirOwnPlatformOverHttps() {
        assertEquals("https://twitter.com/jack", PlatformLinks.trustedAuthorUrl(XSpec, "https://twitter.com/jack"))
        assertEquals("https://vimeo.com/user1", PlatformLinks.trustedAuthorUrl(VimeoSpec, "https://vimeo.com/user1?x=1"))
        assertNull(PlatformLinks.trustedAuthorUrl(XSpec, "http://twitter.com/jack"))
        assertNull(PlatformLinks.trustedAuthorUrl(XSpec, "https://evil.example/jack"))
        assertNull(PlatformLinks.trustedAuthorUrl(XSpec, "https://twitter.com.evil.example/jack"))
        assertNull(PlatformLinks.trustedAuthorUrl(XSpec, null))
    }
}
