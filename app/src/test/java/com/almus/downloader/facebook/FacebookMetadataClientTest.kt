package com.almus.downloader.facebook

import com.almus.downloader.platform.LinkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFbHttp(private val script: (String) -> FacebookReply) : FacebookHttp {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): FacebookReply {
        urls += url
        this.headers += headers
        return script(url)
    }
}

class FacebookMetadataClientTest {
    private val id = "123456789012345"
    private val ref = FacebookContentRef(id, LinkKind.Video)
    private val token = "EAABwzLixnjYBO0123456789abcdefGHIJKL"
    private val ownerId = "100001234567890"

    private val oembedOk = FacebookReply(200, """{"html":"<iframe src=\"x\"></iframe>","width":476,"height":476,"provider_name":"Facebook","type":"video","version":"1.0"}""")
    private val oembedPortrait = FacebookReply(200, """{"html":"<div></div>","width":267,"height":476,"type":"video"}""")

    private fun graphBody(
        from: String = """"from":{"name":"Sample Page","id":"$ownerId"}""",
        extra: String = "",
    ) = """{"id":"$id","title":"Sample video","description":"A short description","picture":"https://scontent.xx.fbcdn.net/v/t15/pic.jpg",""" +
        """"length":212.4,"created_time":"2026-03-04T10:11:12+0000","embeddable":true,$from$extra}"""

    private fun error(code: Int, message: String = "msg") = """{"error":{"message":"$message","type":"OAuthException","code":$code,"fbtrace_id":"x"}}"""

    private fun lookup(http: FakeFbHttp, tokenValue: String? = null) =
        runBlocking { FacebookMetadataClient(http) { tokenValue }.lookup(ref) }

    private fun isGraph(url: String) = url.contains("/$id?")
    private fun isOEmbed(url: String) = url.contains("/oembed_video?")
    private fun isMe(url: String) = url.contains("/me?")

    // ---------------------------------------------------------------- without a token

    @Test fun withoutATokenOnlyPublicOEmbedIsUsedAndItCarriesNoCredentials() {
        val http = FakeFbHttp { oembedOk }
        val result = lookup(http) as FacebookLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].startsWith("https://graph.facebook.com/${FacebookMetadataClient.GRAPH_VERSION}/oembed_video?"))
        assertTrue(http.urls[0].contains("url=https%3A%2F%2Fwww.facebook.com%2Fwatch%2F%3Fv%3D$id"))
        assertEquals(setOf("Accept"), http.headers[0].keys)
        assertEquals(476, result.info.widthPx)
        assertNull(result.info.title)
        assertNull(result.info.thumbnailUrl)
        assertNull(result.info.durationSeconds)
        assertEquals(FacebookMetadataSource.OEmbed, result.info.source)
        assertFalse(result.info.ownedByViewer)
        assertTrue(result.notices.contains(FacebookMetadataClient.NO_TOKEN_NOTICE))
    }

    @Test fun oEmbedSizeTellsPortraitFromLandscape() {
        assertEquals("Portrait", (lookup(FakeFbHttp { oembedPortrait }) as FacebookLookup.Found).info.orientation)
        assertEquals("Square", (lookup(FakeFbHttp { oembedOk }) as FacebookLookup.Found).info.orientation)
    }

    @Test fun reelsAndLiveVideosAskForTheirOwnOfficialAddress() {
        val http = FakeFbHttp { oembedOk }
        runBlocking { FacebookMetadataClient(http) { null }.lookup(FacebookContentRef(id, LinkKind.ShortVideo)) }
        assertTrue(http.urls[0].contains("url=https%3A%2F%2Fwww.facebook.com%2Freel%2F$id"))
    }

    // ---------------------------------------------------------------- with a token

    @Test fun theTokenGoesInAHeaderNeverInTheAddress() {
        val http = FakeFbHttp { graphBody().let { FacebookReply(200, it) } }
        lookup(http, token)
        assertTrue(http.urls.isNotEmpty())
        assertTrue("the token must never be put in an address", http.urls.none { it.contains(token) || it.contains("access_token") })
        assertEquals("Bearer $token", http.headers[0]["Authorization"])
    }

    @Test fun graphApiGivesTitleOwnerThumbnailAndLength() {
        val http = FakeFbHttp { FacebookReply(200, graphBody()) }
        val result = lookup(http, token) as FacebookLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].startsWith("https://graph.facebook.com/${FacebookMetadataClient.GRAPH_VERSION}/$id?fields="))
        val info = result.info
        assertEquals("Sample video", info.title)
        assertEquals("A short description", info.description)
        assertEquals("Sample Page", info.ownerName)
        assertEquals("https://www.facebook.com/$ownerId", info.ownerUrl)
        assertEquals("https://scontent.xx.fbcdn.net/v/t15/pic.jpg", info.thumbnailUrl)
        assertEquals(212L, info.durationSeconds)
        assertEquals("2026-03-04", info.publishedDate)
        assertEquals(true, info.embeddable)
        assertEquals(FacebookMetadataSource.GraphApi, info.source)
        assertTrue(result.notices.isEmpty())
        assertFalse(info.ownedByViewer)
        assertNull(info.ownerFileUrl)
    }

    @Test fun anotherPersonsVideoNeverKeepsItsFileAddress() {
        // The API returned a file address, but the video's owner is not the account of the token.
        val file = "https://video.xx.fbcdn.net/v/t42/clip.mp4?sig=1"
        val http = FakeFbHttp { url ->
            when {
                isMe(url) -> FacebookReply(200, """{"id":"999999999999999"}""")
                else -> FacebookReply(200, graphBody(extra = ""","source":"$file""""))
            }
        }
        val info = (lookup(http, token) as FacebookLookup.Found).info
        assertFalse(info.ownedByViewer)
        assertNull(info.ownerFileUrl)
    }

    @Test fun theOwnersVideoKeepsItsFileAddress() {
        val file = "https://video.xx.fbcdn.net/v/t42/clip.mp4?sig=1"
        val http = FakeFbHttp { url ->
            when {
                isMe(url) -> FacebookReply(200, """{"id":"$ownerId","name":"Sample Page"}""")
                else -> FacebookReply(200, graphBody(extra = ""","source":"$file""""))
            }
        }
        val info = (lookup(http, token) as FacebookLookup.Found).info
        assertTrue(info.ownedByViewer)
        assertEquals(file, info.ownerFileUrl)
        assertEquals("the owner check is made with the same token, in a header", "Bearer $token", http.headers.last()["Authorization"])
    }

    @Test fun aFailedOwnerCheckMeansNoFileAddress() {
        val file = "https://video.xx.fbcdn.net/v/t42/clip.mp4?sig=1"
        val cases = listOf<(String) -> FacebookReply>(
            { url -> if (isMe(url)) FacebookReply(400, error(190)) else FacebookReply(200, graphBody(extra = ""","source":"$file"""")) },
            { url -> if (isMe(url)) FacebookReply(200, "garbage") else FacebookReply(200, graphBody(extra = ""","source":"$file"""")) },
            { url -> if (isMe(url)) throw FacebookNetworkException("offline") else FacebookReply(200, graphBody(extra = ""","source":"$file"""")) },
        )
        cases.forEach { script ->
            val result = lookup(FakeFbHttp(script), token) as FacebookLookup.Found
            assertFalse(result.info.ownedByViewer)
            assertNull(result.info.ownerFileUrl)
            assertEquals("the details already read are kept", "Sample video", result.info.title)
        }
    }

    @Test fun noOwnerCheckIsMadeWhenThereIsNoFileAddress() {
        val http = FakeFbHttp { FacebookReply(200, graphBody()) }
        lookup(http, token)
        assertTrue(http.urls.none { isMe(it) })
    }

    @Test fun anUntrustedThumbnailIsDropped() {
        val body = graphBody().replace("https://scontent.xx.fbcdn.net/v/t15/pic.jpg", "https://evil.example/pic.jpg")
        val info = (lookup(FakeFbHttp { FacebookReply(200, body) }, token) as FacebookLookup.Found).info
        assertNull(info.thumbnailUrl)
    }

    @Test fun anAnswerForADifferentVideoIsRejected() {
        val body = graphBody().replace("\"id\":\"$id\"", "\"id\":\"555555555555555\"")
        val http = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(200, body) else oembedOk }
        assertEquals(FacebookMetadataSource.OEmbed, (lookup(http, token) as FacebookLookup.Found).info.source)
    }

    @Test fun anUnknownFieldIsRetriedWithFewerFields() {
        var graphCalls = 0
        val http = FakeFbHttp { url ->
            if (isGraph(url)) {
                graphCalls++
                if (graphCalls == 1) FacebookReply(400, error(100, "(#100) Tried accessing nonexisting field (length) on node type (Video)"))
                else FacebookReply(200, """{"id":"$id","description":"d","from":{"name":"N","id":"$ownerId"}}""")
            } else {
                oembedOk
            }
        }
        val result = lookup(http, token) as FacebookLookup.Found
        assertEquals(2, graphCalls)
        assertEquals(FacebookMetadataSource.GraphApi, result.info.source)
        assertEquals("d", result.info.description)
        assertFalse(http.urls[1].contains("length"))
    }

    // ---------------------------------------------------------------- the token is a helper, never a requirement

    @Test fun aRejectedTokenFallsBackToPublicInformationWithANotice() {
        val http = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(400, error(190)) else oembedOk }
        val result = lookup(http, token) as FacebookLookup.Found
        assertEquals(FacebookMetadataSource.OEmbed, result.info.source)
        assertTrue(result.notices.any { it.contains("did not accept your saved access token") })
        assertFalse(result.notices.contains(FacebookMetadataClient.NO_TOKEN_NOTICE))
    }

    @Test fun aTokenThatMayNotReadTheVideoFallsBackWithANotice() {
        listOf(error(10) to 403, error(200) to 403, error(100, "Unsupported get request") to 400, error(299) to 403).forEach { (body, status) ->
            val http = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(status, body) else oembedOk }
            val result = lookup(http, token) as FacebookLookup.Found
            assertEquals(FacebookMetadataSource.OEmbed, result.info.source)
            assertTrue(result.notices.any { it.contains("not allowed to read this video") })
        }
    }

    @Test fun aRateLimitedTokenFallsBackWithANotice() {
        listOf(4, 17, 32, 613).forEach { code ->
            val http = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(400, error(code)) else oembedOk }
            val result = lookup(http, token) as FacebookLookup.Found
            assertTrue(result.notices.any { it.contains("limiting requests from your access token") })
        }
    }

    @Test fun unreadableOrFailingGraphAnswersFallBack() {
        val unreadable = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(200, "<html>") else oembedOk }
        assertTrue((lookup(unreadable, token) as FacebookLookup.Found).notices.any { it.contains("could not be read") })
        val server = FakeFbHttp { url -> if (isGraph(url)) FacebookReply(503, "") else oembedOk }
        assertTrue((lookup(server, token) as FacebookLookup.Found).notices.any { it.contains("not answering") })
    }

    @Test fun aMalformedTokenIsNeverSent() {
        val http = FakeFbHttp { oembedOk }
        val result = lookup(http, "not a token!") as FacebookLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(isOEmbed(http.urls[0]))
        assertTrue(result.notices.any { it.contains("does not look valid") })
        assertTrue(http.headers.none { it.containsKey("Authorization") })
    }

    @Test fun aBlankTokenCountsAsNoToken() {
        val http = FakeFbHttp { oembedOk }
        val result = lookup(http, "   ") as FacebookLookup.Found
        assertTrue(result.notices.contains(FacebookMetadataClient.NO_TOKEN_NOTICE))
    }

    // ---------------------------------------------------------------- error handling: meaningful outcomes, never a crash

    @Test fun notPublicContentIsReportedAsNotPublic() {
        listOf(FacebookReply(400, error(200)), FacebookReply(400, error(10)), FacebookReply(403, ""), FacebookReply(401, "")).forEach { reply ->
            val result = lookup(FakeFbHttp { reply }) as FacebookLookup.Unavailable
            assertEquals(FacebookUnavailableReason.NotPublic, result.reason)
            assertTrue(result.reason.message.contains("private"))
        }
    }

    @Test fun missingOrUnsupportedContentIsReportedAsSuch() {
        listOf(FacebookReply(400, error(100, "Invalid parameter")), FacebookReply(404, "")).forEach { reply ->
            val result = lookup(FakeFbHttp { reply }) as FacebookLookup.Unavailable
            assertEquals(FacebookUnavailableReason.NotFoundOrUnsupported, result.reason)
        }
    }

    @Test fun unavailableAnswersStillCarryTheNoTokenNotice() {
        val result = lookup(FakeFbHttp { FacebookReply(400, error(200)) }) as FacebookLookup.Unavailable
        assertTrue(result.notices.contains(FacebookMetadataClient.NO_TOKEN_NOTICE))
    }

    @Test fun rateLimitsAndServerErrorsAreFailuresWithAMessage() {
        listOf(FacebookReply(429, ""), FacebookReply(400, error(4)), FacebookReply(400, error(613))).forEach { reply ->
            val failed = lookup(FakeFbHttp { reply }) as FacebookLookup.Failed
            assertEquals(FacebookProblem.RateLimited, failed.problem)
            assertTrue(failed.message.contains("Try again later"))
        }
        val server = lookup(FakeFbHttp { FacebookReply(500, "") }) as FacebookLookup.Failed
        assertEquals(FacebookProblem.ServiceError, server.problem)
        assertTrue(server.message.contains("500"))
        assertEquals(FacebookProblem.ServiceError, (lookup(FakeFbHttp { FacebookReply(400, "not json") }) as FacebookLookup.Failed).problem)
    }

    @Test fun anUnreadableSuccessAnswerIsAFailureNotACrash() {
        listOf("nope", "{}", "[]", """{"html":""}""", "").forEach { body ->
            val failed = lookup(FakeFbHttp { FacebookReply(200, body) }) as FacebookLookup.Failed
            assertEquals("body: $body", FacebookProblem.BadResponse, failed.problem)
        }
    }

    @Test fun networkFailureIsAFailedLookupNotACrash() {
        val http = FakeFbHttp { throw FacebookNetworkException("offline") }
        val failed = lookup(http) as FacebookLookup.Failed
        assertEquals(FacebookProblem.Network, failed.problem)
        assertTrue(failed.message.contains("connection"))
        assertEquals(FacebookProblem.Network, (lookup(http, token) as FacebookLookup.Failed).problem)
    }

    @Test fun hugeOrControlCharacterTextIsCleaned() {
        val long = "x".repeat(2000)
        val body = graphBody().replace("Sample video", "A\u0000B\u0007 $long")
        val info = (lookup(FakeFbHttp { FacebookReply(200, body) }, token) as FacebookLookup.Found).info
        assertTrue(info.title!!.length <= 300)
        assertTrue(info.title!!.none { it.isISOControl() })
        assertTrue(info.title!!.startsWith("AB x"))
    }
}
