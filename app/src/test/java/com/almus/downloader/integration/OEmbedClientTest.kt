package com.almus.downloader.integration

import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeOEmbedHttp(private val script: (String) -> OEmbedReply) : OEmbedHttp {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): OEmbedReply {
        urls += url
        this.headers += headers
        return script(url)
    }
}

class OEmbedClientTest {
    private val vimeo = ContentRef(MediaPlatform.Vimeo, LinkKind.Video, "76979871")
    private val x = ContentRef(MediaPlatform.X, LinkKind.Post, "1234567890123456789", "jack")
    private val dailymotion = ContentRef(MediaPlatform.Dailymotion, LinkKind.Video, "x8abc12")
    private val reddit = ContentRef(MediaPlatform.Reddit, LinkKind.Post, "abc123", "kotlin")
    private val pinterest = ContentRef(MediaPlatform.Pinterest, LinkKind.Post, "123456789012345678")
    private val instagram = ContentRef(MediaPlatform.Instagram, LinkKind.Post, "CxYz123AbCd")

    // ---------------------------------------------------------------- example answers (shortened)

    private fun vimeoBody(
        thumbnail: String = "https://i.vimeocdn.com/video/452001751_295x166.jpg",
        authorUrl: String = "https://vimeo.com/blender",
        videoId: String = "76979871",
        type: String = "video",
    ) = """{"type":"$type","version":"1.0","provider_name":"Vimeo","provider_url":"https://vimeo.com/",""" +
        """"title":"Big Buck Bunny","author_name":"Blender","author_url":"$authorUrl",""" +
        """"html":"<iframe src=\"https://player.vimeo.com/video/$videoId?h=abc\" width=\"640\"></iframe>",""" +
        """"width":640,"height":360,"duration":596,"thumbnail_url":"$thumbnail","thumbnail_width":295,""" +
        """"thumbnail_height":166,"upload_date":"2013-12-03 10:11:12","video_id":76979871}"""

    private val xBody = """{"url":"https://twitter.com/jack/status/1234567890123456789","author_name":"Jack",""" +
        """"author_url":"https://twitter.com/jack","type":"rich","provider_name":"Twitter","version":"1.0",""" +
        """"html":"<blockquote class=\"twitter-tweet\"><p lang=\"en\" dir=\"ltr\">Hello &amp; welcome<br>second line """ +
        """<a href=\"https://t.co/x\">pic.twitter.com/x</a></p>&mdash; Jack (@jack) """ +
        """<a href=\"https://twitter.com/jack/status/1234567890123456789\">March 21, 2006</a></blockquote>"}"""

    private val dailymotionBody = """{"type":"video","version":"1.0","provider_name":"Dailymotion",""" +
        """"title":"A short film","author_name":"Some Studio","author_url":"https://www.dailymotion.com/somestudio",""" +
        """"width":480,"height":270,"html":"<iframe src=\"https://www.dailymotion.com/embed/video/x8abc12\"></iframe>",""" +
        """"thumbnail_url":"https://s1.dmcdn.net/v/abc/x240","thumbnail_width":427,"thumbnail_height":240}"""

    private val redditBody = """{"version":"1.0","type":"rich","provider_name":"reddit","title":"A question about coroutines",""" +
        """"author_name":"someuser","author_url":"https://www.reddit.com/user/someuser",""" +
        """"html":"<blockquote class=\"reddit-embed-bq\"><a href=\"https://www.reddit.com/r/kotlin/comments/abc123/a_question/\">A question</a></blockquote>",""" +
        """"width":240,"height":null}"""

    private val pinterestBody = """{"version":"1.0","type":"rich","provider_name":"Pinterest","title":"Nice chair",""" +
        """"author_name":"Designer","author_url":"https://www.pinterest.com/designer/",""" +
        """"thumbnail_url":"https://i.pinimg.com/236x/aa/bb/cc.jpg","thumbnail_width":236,"thumbnail_height":354,""" +
        """"html":"<a data-pin-do=\"embedPin\" href=\"https://www.pinterest.com/pin/123456789012345678/\"></a>"}"""

    private fun instagramBody(code: String = "CxYz123AbCd") =
        """{"version":"1.0","provider_name":"Instagram","provider_url":"https://www.instagram.com",""" +
            """"html":"<blockquote class=\"instagram-media\" data-instgrm-permalink=\"https://www.instagram.com/p/$code/?utm_source=ig_embed\"></blockquote>",""" +
            """"width":658,"type":"rich"}"""

    private fun lookup(http: FakeOEmbedHttp, ref: ContentRef) = runBlocking { OEmbedClient(http).lookup(ref) }

    private fun reply(status: Int, body: String = "") = FakeOEmbedHttp { OEmbedReply(status, body) }

    // ---------------------------------------------------------------- the request

    @Test fun everyRequestGoesToTheOfficialAddressWithNoCredentials() {
        val expected = mapOf(
            vimeo to "https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F76979871",
            x to "https://publish.twitter.com/oembed?omit_script=true&dnt=true&url=https%3A%2F%2Ftwitter.com%2Fjack%2Fstatus%2F1234567890123456789",
            dailymotion to "https://www.dailymotion.com/services/oembed?format=json&url=https%3A%2F%2Fwww.dailymotion.com%2Fvideo%2Fx8abc12",
            reddit to "https://www.reddit.com/oembed?url=https%3A%2F%2Fwww.reddit.com%2Fr%2Fkotlin%2Fcomments%2Fabc123%2F",
            pinterest to "https://www.pinterest.com/oembed.json?url=https%3A%2F%2Fwww.pinterest.com%2Fpin%2F123456789012345678%2F",
            instagram to "https://graph.facebook.com/v26.0/instagram_oembed?url=https%3A%2F%2Fwww.instagram.com%2Fp%2FCxYz123AbCd%2F",
        )
        expected.forEach { (ref, url) ->
            val http = reply(200, "{}")
            lookup(http, ref)
            assertEquals(1, http.urls.size)
            assertEquals(url, http.urls[0])
            assertEquals(setOf("Accept"), http.headers[0].keys)
        }
    }

    // ---------------------------------------------------------------- good answers

    @Test fun vimeoGivesTitleOwnerLengthDateAndThumbnail() {
        val result = lookup(reply(200, vimeoBody()), vimeo) as ContentLookup.Found
        val info = result.info
        assertEquals("Big Buck Bunny", info.title)
        assertEquals("Blender", info.authorName)
        assertEquals("https://vimeo.com/blender", info.authorUrl)
        assertEquals("https://i.vimeocdn.com/video/452001751_295x166.jpg", info.thumbnailUrl)
        assertEquals(295, info.thumbnailWidthPx)
        assertEquals(166, info.thumbnailHeightPx)
        assertEquals("Landscape", info.orientation)
        assertEquals(596, info.durationSeconds)
        assertEquals("9:56", info.durationText)
        assertEquals("2013-12-03", info.publishedOn)
        assertEquals("Vimeo oEmbed (public information)", info.sourceLabel)
    }

    @Test fun xGivesTheTextOfThePostAsPlainText() {
        val info = (lookup(reply(200, xBody), x) as ContentLookup.Found).info
        assertEquals("Hello & welcome\nsecond line pic.twitter.com/x", info.title)
        assertFalse(info.title!!.contains('<'))
        assertEquals("Jack", info.authorName)
        assertEquals("https://twitter.com/jack", info.authorUrl)
        assertNull(info.thumbnailUrl)
    }

    @Test fun dailymotionGivesTitleOwnerAndThumbnail() {
        val info = (lookup(reply(200, dailymotionBody), dailymotion) as ContentLookup.Found).info
        assertEquals("A short film", info.title)
        assertEquals("Some Studio", info.authorName)
        assertEquals("https://s1.dmcdn.net/v/abc/x240", info.thumbnailUrl)
        assertEquals("Landscape", info.orientation)
    }

    @Test fun redditGivesTitleAndAuthorButNoThumbnail() {
        val info = (lookup(reply(200, redditBody), reddit) as ContentLookup.Found).info
        assertEquals("A question about coroutines", info.title)
        assertEquals("someuser", info.authorName)
        assertEquals("https://www.reddit.com/user/someuser", info.authorUrl)
        assertNull(info.thumbnailUrl)
    }

    @Test fun pinterestGivesTitleCreatorAndPortraitThumbnail() {
        val info = (lookup(reply(200, pinterestBody), pinterest) as ContentLookup.Found).info
        assertEquals("Nice chair", info.title)
        assertEquals("Designer", info.authorName)
        assertEquals("https://i.pinimg.com/236x/aa/bb/cc.jpg", info.thumbnailUrl)
        assertEquals("Portrait", info.orientation)
    }

    @Test fun instagramConfirmsPublicContentButGivesNoTitleCreatorOrThumbnail() {
        val info = (lookup(reply(200, instagramBody()), instagram) as ContentLookup.Found).info
        assertNull(info.title)
        assertNull(info.authorName)
        assertNull(info.thumbnailUrl)
        assertEquals("Meta oEmbed for Instagram (public information)", info.sourceLabel)
    }

    // ---------------------------------------------------------------- answers that are not trusted

    @Test fun aThumbnailOnAnUnknownHostIsDroppedWithItsSize() {
        val body = vimeoBody(thumbnail = "https://evil.example/x.jpg")
        val info = (lookup(reply(200, body), vimeo) as ContentLookup.Found).info
        assertNull(info.thumbnailUrl)
        assertNull(info.thumbnailWidthPx)
        assertNull(info.thumbnailHeightPx)
        assertEquals("Big Buck Bunny", info.title)
    }

    @Test fun aPlainHttpThumbnailIsDropped() {
        val body = vimeoBody(thumbnail = "http://i.vimeocdn.com/video/1.jpg")
        assertNull((lookup(reply(200, body), vimeo) as ContentLookup.Found).info.thumbnailUrl)
    }

    @Test fun aCreatorAddressOnAnotherSiteIsDropped() {
        val body = vimeoBody(authorUrl = "https://evil.example/blender")
        val info = (lookup(reply(200, body), vimeo) as ContentLookup.Found).info
        assertNull(info.authorUrl)
        assertEquals("Blender", info.authorName)
    }

    @Test fun anAnswerAboutOtherContentIsNotAccepted() {
        assertTrue(lookup(reply(200, vimeoBody(videoId = "999")), vimeo) is ContentLookup.Failed)
        assertTrue(lookup(reply(200, instagramBody(code = "OtherCode99")), instagram) is ContentLookup.Failed)
    }

    @Test fun anUnknownResourceTypeIsNotAccepted() {
        val result = lookup(reply(200, vimeoBody(type = "audio")), vimeo)
        assertEquals(LookupProblem.BadResponse, (result as ContentLookup.Failed).problem)
    }

    // ---------------------------------------------------------------- the platform says no

    @Test fun refusalsAreReportedAsTheyAre() {
        val notShared = lookup(reply(403), vimeo) as ContentLookup.Unavailable
        assertEquals(UnavailableReason.NotShared, notShared.reason)
        assertTrue(notShared.message.contains("Vimeo"))
        assertEquals(UnavailableReason.NotShared, (lookup(reply(401), vimeo) as ContentLookup.Unavailable).reason)
        listOf(400, 404, 410).forEach {
            assertEquals(UnavailableReason.NotFoundOrUnsupported, (lookup(reply(it), x) as ContentLookup.Unavailable).reason)
        }
    }

    @Test fun anErrorBodyOnAnOtherwiseGoodStatusIsUnavailableNotBroken() {
        val body = """{"error":{"message":"Invalid parameter","type":"OAuthException","code":100}}"""
        val result = lookup(reply(200, body), instagram) as ContentLookup.Unavailable
        assertEquals(UnavailableReason.NotFoundOrUnsupported, result.reason)
    }

    @Test fun rateLimitsAreRecognisedByStatusAndByCode() {
        assertEquals(LookupProblem.RateLimited, (lookup(reply(429), reddit) as ContentLookup.Failed).problem)
        val meta = """{"error":{"message":"Application request limit reached","code":4}}"""
        assertEquals(LookupProblem.RateLimited, (lookup(reply(400, meta), instagram) as ContentLookup.Failed).problem)
        val named = """{"code":"rate_limit_exceeded","message":"slow down"}"""
        assertEquals(LookupProblem.RateLimited, (lookup(reply(400, named), pinterest) as ContentLookup.Failed).problem)
    }

    @Test fun serverErrorsAndOddStatusesAreServiceErrors() {
        listOf(500, 502, 503, 302, 418).forEach {
            assertEquals(LookupProblem.ServiceError, (lookup(reply(it), dailymotion) as ContentLookup.Failed).problem)
        }
    }

    @Test fun anAnswerThatCannotBeReadIsABadResponse() {
        assertEquals(LookupProblem.BadResponse, (lookup(reply(200, "<html>not json</html>"), vimeo) as ContentLookup.Failed).problem)
        assertEquals(LookupProblem.BadResponse, (lookup(reply(200, "{}"), vimeo) as ContentLookup.Failed).problem)
    }

    @Test fun beingOfflineIsANetworkProblem() {
        val http = FakeOEmbedHttp { throw OEmbedNetworkException("offline") }
        val result = lookup(http, pinterest) as ContentLookup.Failed
        assertEquals(LookupProblem.Network, result.problem)
        assertTrue(result.message.contains("Pinterest"))
    }

    @Test fun aReferenceForAPlatformWithoutASpecFails() {
        val result = lookup(reply(200, "{}"), ContentRef(MediaPlatform.TikTok, LinkKind.Video, "6718335390845095173"))
        assertTrue(result is ContentLookup.Failed)
    }
}
