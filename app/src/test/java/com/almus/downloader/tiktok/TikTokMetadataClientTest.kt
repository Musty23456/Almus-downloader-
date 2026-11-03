package com.almus.downloader.tiktok

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTikTokHttp(private val script: (String) -> TikTokReply) : TikTokHttp {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): TikTokReply {
        urls += url
        this.headers += headers
        return script(url)
    }
}

class TikTokMetadataClientTest {
    private val id = "6718335390845095173"
    private val ref = TikTokContentRef(id, "scout2015")

    /** The example answer from TikTok's oEmbed documentation, with the markup shortened. */
    private fun body(
        type: String = "video",
        title: String = "Scramble up ur name & I’ll try to guess it #foryoupage",
        authorName: String = "Scout & Suki",
        authorUrl: String = "https://www.tiktok.com/@scout2015",
        html: String = "<blockquote class=\\\"tiktok-embed\\\" cite=\\\"https://www.tiktok.com/@scout2015/video/$id\\\" data-video-id=\\\"$id\\\"><section></section></blockquote>",
        thumbnail: String = "https://p16.muscdn.com/obj/tos-maliva-p-0068/06kv6rfcesljdjr45ukb0000d844090v0200010605",
        width: String = "720",
        height: String = "1280",
    ) = """{"version":"1.0","type":"$type","title":"$title","author_url":"$authorUrl","author_name":"$authorName",""" +
        """"width":"100%","height":"100%","html":"$html","thumbnail_width":$width,"thumbnail_height":$height,""" +
        """"thumbnail_url":"$thumbnail","provider_url":"https://www.tiktok.com","provider_name":"TikTok"}"""

    private fun lookup(http: FakeTikTokHttp, r: TikTokContentRef = ref) = runBlocking { TikTokMetadataClient(http).lookup(r) }

    // ---------------------------------------------------------------- the request

    @Test fun theRequestGoesToTheOfficialOEmbedAddressWithNoCredentials() {
        val http = FakeTikTokHttp { TikTokReply(200, body()) }
        lookup(http)
        assertEquals(1, http.urls.size)
        assertEquals(
            "https://www.tiktok.com/oembed?url=https%3A%2F%2Fwww.tiktok.com%2F%40scout2015%2Fvideo%2F$id",
            http.urls[0],
        )
        assertEquals(setOf("Accept"), http.headers[0].keys)
    }

    @Test fun aReferenceWithoutACreatorNameAsksForTheEmptyNameAddress() {
        val http = FakeTikTokHttp { TikTokReply(200, body()) }
        lookup(http, TikTokContentRef(id, null))
        assertTrue(http.urls[0].endsWith("url=https%3A%2F%2Fwww.tiktok.com%2F%40%2Fvideo%2F$id"))
    }

    // ---------------------------------------------------------------- a good answer

    @Test fun aPublicVideoGivesCaptionCreatorAndThumbnail() {
        val result = lookup(FakeTikTokHttp { TikTokReply(200, body()) }) as TikTokLookup.Found
        val info = result.info
        assertEquals(id, info.contentId)
        assertEquals("Scramble up ur name & I’ll try to guess it #foryoupage", info.caption)
        assertEquals("Scout & Suki", info.creatorName)
        assertEquals("scout2015", info.creatorHandle)
        assertEquals("https://www.tiktok.com/@scout2015", info.creatorUrl)
        assertEquals("https://p16.muscdn.com/obj/tos-maliva-p-0068/06kv6rfcesljdjr45ukb0000d844090v0200010605", info.thumbnailUrl)
        assertEquals(720, info.thumbnailWidthPx)
        assertEquals(1280, info.thumbnailHeightPx)
        assertEquals("Portrait", info.orientation)
        assertEquals(emptyList<String>(), result.notices)
    }

    @Test fun orientationFollowsTheThumbnailSize() {
        assertEquals("Landscape", (lookup(FakeTikTokHttp { TikTokReply(200, body(width = "1280", height = "720")) }) as TikTokLookup.Found).info.orientation)
        assertEquals("Square", (lookup(FakeTikTokHttp { TikTokReply(200, body(width = "500", height = "500")) }) as TikTokLookup.Found).info.orientation)
    }

    @Test fun anAnswerWithOnlySomeFieldsIsStillUsed() {
        val minimal = TikTokReply(200, """{"version":"1.0","type":"video","title":"Just a caption"}""")
        val info = (lookup(FakeTikTokHttp { minimal }) as TikTokLookup.Found).info
        assertEquals("Just a caption", info.caption)
        assertNull(info.creatorName)
        assertNull(info.creatorHandle)
        assertNull(info.thumbnailUrl)
        assertNull(info.orientation)
    }

    @Test fun anAnswerWithoutAnyTypeIsAccepted() {
        val noType = TikTokReply(200, """{"title":"No type given","author_name":"Someone"}""")
        assertTrue(lookup(FakeTikTokHttp { noType }) is TikTokLookup.Found)
    }

    @Test fun thumbnailSizesGivenAsTextOrNonsenseAreHandled() {
        val asText = (lookup(FakeTikTokHttp { TikTokReply(200, body(width = "\"720\"", height = "\"1280\"")) }) as TikTokLookup.Found).info
        assertEquals(720, asText.thumbnailWidthPx)
        assertEquals(1280, asText.thumbnailHeightPx)
        val bad = (lookup(FakeTikTokHttp { TikTokReply(200, body(width = "-5", height = "99999999")) }) as TikTokLookup.Found).info
        assertNull(bad.thumbnailWidthPx)
        assertNull(bad.thumbnailHeightPx)
        assertNull(bad.orientation)
        val words = (lookup(FakeTikTokHttp { TikTokReply(200, body(width = "\"wide\"", height = "null")) }) as TikTokLookup.Found).info
        assertNull(words.thumbnailWidthPx)
        assertNull(words.thumbnailHeightPx)
    }

    // ---------------------------------------------------------------- what is never trusted

    @Test fun aThumbnailOnAnUntrustedHostIsDropped() {
        listOf(
            "http://p16.tiktokcdn.com/x.jpeg",
            "https://evil.example/x.jpeg",
            "https://tiktokcdn.com.evil.example/x.jpeg",
            "https://www.tiktok.com/x.jpeg",
            "javascript:alert(1)",
        ).forEach {
            val info = (lookup(FakeTikTokHttp { TikTokReply(200, body(thumbnail = it)) }) as TikTokLookup.Found).info
            assertNull("thumbnail kept for $it", info.thumbnailUrl)
            assertEquals("Scout & Suki", info.creatorName)
        }
    }

    @Test fun aCreatorAddressOnAnotherHostGivesNoProfileLink() {
        listOf(
            "https://evil.example/@scout2015",
            "http://www.tiktok.com/@scout2015",
            "https://www.tiktok.com/@scout2015/video/$id",
            "https://www.tiktok.com/@bad%20name",
            "not a link",
        ).forEach {
            val info = (lookup(FakeTikTokHttp { TikTokReply(200, body(authorUrl = it)) }) as TikTokLookup.Found).info
            assertNull("name kept for $it", info.creatorHandle)
            assertNull("link kept for $it", info.creatorUrl)
        }
    }

    @Test fun anAnswerAboutAnotherVideoIsRejected() {
        val other = body(html = "<blockquote data-video-id=\\\"7234567890123456789\\\"></blockquote>")
        val result = lookup(FakeTikTokHttp { TikTokReply(200, other) })
        assertEquals(TikTokProblem.BadResponse, (result as TikTokLookup.Failed).problem)
    }

    @Test fun anAnswerThatIsNotAVideoIsRejected() {
        listOf("rich", "photo", "link").forEach { type ->
            val result = lookup(FakeTikTokHttp { TikTokReply(200, body(type = type)) })
            assertTrue("type $type", result is TikTokLookup.Failed)
        }
    }

    @Test fun theEmbedMarkupIsNeverKept() {
        val info = (lookup(FakeTikTokHttp { TikTokReply(200, body()) }) as TikTokLookup.Found).info
        val text = info.toString()
        assertFalse(text.contains("blockquote"))
        assertFalse(text.contains("embed.js"))
    }

    @Test fun textFromTheAnswerIsCleanedAndLimited() {
        val long = "x".repeat(500)
        val messy = "  Hello\\u0007 world\\u0000 \\nsecond line  "
        val info = (lookup(FakeTikTokHttp { TikTokReply(200, body(title = messy, authorName = long)) }) as TikTokLookup.Found).info
        assertEquals("Hello world \nsecond line", info.caption)
        assertEquals(120, info.creatorName!!.length)
        val longCaption = (lookup(FakeTikTokHttp { TikTokReply(200, body(title = long)) }) as TikTokLookup.Found).info
        assertEquals(300, longCaption.caption!!.length)
        val blank = (lookup(FakeTikTokHttp { TikTokReply(200, body(title = "   ")) }) as TikTokLookup.Found).info
        assertNull(blank.caption)
    }

    // ---------------------------------------------------------------- videos TikTok does not share

    @Test fun refusedAnswersMeanTheVideoIsNotShared() {
        listOf(401, 403).forEach { status ->
            val result = lookup(FakeTikTokHttp { TikTokReply(status, "") }) as TikTokLookup.Unavailable
            assertEquals("status $status", TikTokUnavailableReason.NotShared, result.reason)
        }
    }

    @Test fun missingOrBadRequestsMeanNoInformation() {
        listOf(400, 404, 410).forEach { status ->
            val result = lookup(FakeTikTokHttp { TikTokReply(status, """{"code":$status,"message":"Something went wrong"}""") }) as TikTokLookup.Unavailable
            assertEquals("status $status", TikTokUnavailableReason.NotFoundOrUnsupported, result.reason)
        }
    }

    @Test fun anErrorInsideASuccessfulAnswerMeansNoInformation() {
        listOf(
            """{"code":400,"message":"Invalid url"}""",
            """{"error":{"code":"url_not_found","message":"gone"}}""",
            """{"message":"Something went wrong"}""",
        ).forEach { text ->
            val result = lookup(FakeTikTokHttp { TikTokReply(200, text) })
            assertEquals("body $text", TikTokUnavailableReason.NotFoundOrUnsupported, (result as TikTokLookup.Unavailable).reason)
        }
    }

    @Test fun aMissingCreatorNameIsExplainedWhenTikTokGivesNothing() {
        val result = lookup(FakeTikTokHttp { TikTokReply(404, "") }, TikTokContentRef(id, null)) as TikTokLookup.Unavailable
        assertEquals(listOf(TikTokMetadataClient.NO_CREATOR_NOTICE), result.notices)
        val withName = lookup(FakeTikTokHttp { TikTokReply(404, "") }) as TikTokLookup.Unavailable
        assertEquals(emptyList<String>(), withName.notices)
    }

    // ---------------------------------------------------------------- failures of the lookup itself

    @Test fun tooManyRequestsAreReportedAsALimit() {
        assertEquals(TikTokProblem.RateLimited, (lookup(FakeTikTokHttp { TikTokReply(429, "") }) as TikTokLookup.Failed).problem)
        val byCode = lookup(FakeTikTokHttp { TikTokReply(400, """{"error":{"code":"rate_limit_exceeded","message":"slow down"}}""") })
        assertEquals(TikTokProblem.RateLimited, (byCode as TikTokLookup.Failed).problem)
    }

    @Test fun serverErrorsAreReportedWithTheirStatus() {
        listOf(500, 502, 503).forEach { status ->
            val failed = lookup(FakeTikTokHttp { TikTokReply(status, "") }) as TikTokLookup.Failed
            assertEquals(TikTokProblem.ServiceError, failed.problem)
            assertTrue(failed.message.contains(status.toString()))
        }
        val odd = lookup(FakeTikTokHttp { TikTokReply(302, "") }) as TikTokLookup.Failed
        assertEquals(TikTokProblem.ServiceError, odd.problem)
        assertTrue(odd.message.contains("302"))
    }

    @Test fun anUnreadableAnswerIsReportedAsSuch() {
        listOf("", "<html>Access denied</html>", "not json", "[]", "{", "null").forEach { text ->
            val failed = lookup(FakeTikTokHttp { TikTokReply(200, text) }) as TikTokLookup.Failed
            assertEquals("body $text", TikTokProblem.BadResponse, failed.problem)
        }
    }

    @Test fun offlineIsReportedAsANetworkProblem() {
        val http = object : TikTokHttp {
            override suspend fun get(url: String, headers: Map<String, String>): TikTokReply = throw TikTokNetworkException("offline")
        }
        val failed = runBlocking { TikTokMetadataClient(http).lookup(ref) } as TikTokLookup.Failed
        assertEquals(TikTokProblem.Network, failed.problem)
        assertTrue(failed.message.contains("Could not reach TikTok"))
    }
}
