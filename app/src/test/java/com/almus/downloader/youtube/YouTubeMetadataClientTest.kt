package com.almus.downloader.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHttp(private val script: (String) -> HttpReply) : YouTubeHttp {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): HttpReply {
        urls += url
        this.headers += headers
        return script(url)
    }
}

class YouTubeMetadataClientTest {
    private val ref = YouTubeVideoRef("dQw4w9WgXcQ", com.almus.downloader.platform.LinkKind.Video)
    private val key = "AIzaSyA-1234567890abcdefghijklmnopqrstu"

    private val oembedOk = HttpReply(
        200,
        """{"title":"Sample video","author_name":"Sample Channel","author_url":"https://www.youtube.com/@sample",""" +
            """"thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg","type":"video"}""",
    )

    private fun apiBody(snippetExtra: String = "", status: String = """"uploadStatus":"processed","privacyStatus":"public","embeddable":true,"license":"youtube"""", details: String = """"duration":"PT3M32S"""") =
        """{"items":[{"id":"dQw4w9WgXcQ","snippet":{"title":"Sample video","channelId":"UCuAXFkgsw1L7xaCfnd5JJOw",""" +
            """"channelTitle":"Sample Channel","liveBroadcastContent":"none"$snippetExtra,""" +
            """"thumbnails":{"default":{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg"},""" +
            """"medium":{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"}}},""" +
            """"contentDetails":{$details},"status":{$status}}]}"""

    private fun lookup(http: FakeHttp, apiKey: String? = null) = runBlocking { YouTubeMetadataClient(http) { apiKey }.lookup(ref) }

    @Test fun withoutAKeyOnlyOEmbedIsUsed() {
        val http = FakeHttp { oembedOk }
        val result = lookup(http) as YouTubeLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].startsWith("https://www.youtube.com/oembed?"))
        assertTrue(http.urls[0].contains("url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ"))
        assertEquals("Sample video", result.info.title)
        assertEquals("Sample Channel", result.info.channelTitle)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", result.info.thumbnailUrl)
        assertNull(result.info.durationSeconds)
        assertEquals(MetadataSource.OEmbed, result.info.source)
        assertTrue(result.notices.contains(YouTubeMetadataClient.NO_KEY_NOTICE))
        assertEquals(setOf("Accept"), http.headers[0].keys)
    }

    @Test fun dataApiGivesDurationAndFlags() {
        val http = FakeHttp { apiBody() .let { b -> HttpReply(200, b) } }
        val result = lookup(http, key) as YouTubeLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].startsWith("https://www.googleapis.com/youtube/v3/videos?"))
        assertFalse("the key must never be put in the address", http.urls[0].contains(key))
        assertEquals(key, http.headers[0]["x-goog-api-key"])
        val info = result.info
        assertEquals(212L, info.durationSeconds)
        assertEquals("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", info.channelUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", info.thumbnailUrl)
        assertEquals(VideoLicense.Standard, info.license)
        assertEquals(true, info.embeddable)
        assertFalse(info.ageRestricted)
        assertFalse(info.regionRestricted)
        assertEquals(MetadataSource.DataApi, info.source)
        assertTrue(result.notices.isEmpty())
    }

    @Test fun ageRegionLicenceAndEmbeddingFlagsAreRead() {
        val details = """"duration":"PT10S","regionRestriction":{"blocked":["DE"]},"contentRating":{"ytRating":"ytAgeRestricted"}"""
        val status = """"uploadStatus":"processed","embeddable":false,"license":"creativeCommon""""
        val info = (lookup(FakeHttp { HttpReply(200, apiBody(status = status, details = details)) }, key) as YouTubeLookup.Found).info
        assertTrue(info.ageRestricted)
        assertTrue(info.regionRestricted)
        assertEquals(false, info.embeddable)
        assertEquals(VideoLicense.CreativeCommons, info.license)
    }

    @Test fun liveVideosHaveNoDuration() {
        val body = apiBody(details = """"duration":"P0D"""").replace(""""liveBroadcastContent":"none"""", """"liveBroadcastContent":"live"""")
        val info = (lookup(FakeHttp { HttpReply(200, body) }, key) as YouTubeLookup.Found).info
        assertEquals(LiveState.Live, info.liveState)
        assertNull(info.durationSeconds)
    }

    @Test fun emptyItemsMeansDeletedPrivateOrNotShared() {
        val result = lookup(FakeHttp { HttpReply(200, """{"items":[]}""") }, key)
        assertEquals(UnavailableReason.NotFoundOrPrivate, (result as YouTubeLookup.Unavailable).reason)
    }

    @Test fun removedAndPrivateStatesAreReported() {
        val removed = apiBody(status = """"uploadStatus":"rejected"""")
        assertEquals(UnavailableReason.Removed, (lookup(FakeHttp { HttpReply(200, removed) }, key) as YouTubeLookup.Unavailable).reason)
        val private = apiBody(status = """"uploadStatus":"processed","privacyStatus":"private"""")
        assertEquals(UnavailableReason.Private, (lookup(FakeHttp { HttpReply(200, private) }, key) as YouTubeLookup.Unavailable).reason)
    }

    @Test fun quotaErrorFallsBackToOEmbedWithANotice() {
        val http = FakeHttp { url ->
            if (url.contains("googleapis")) HttpReply(403, """{"error":{"code":403,"errors":[{"reason":"quotaExceeded"}]}}""") else oembedOk
        }
        val result = lookup(http, key) as YouTubeLookup.Found
        assertEquals(2, http.urls.size)
        assertEquals(MetadataSource.OEmbed, result.info.source)
        assertTrue(result.notices.any { it.contains("daily limit") })
    }

    @Test fun rejectedKeyFallsBackToOEmbedWithANotice() {
        val http = FakeHttp { url ->
            if (url.contains("googleapis")) HttpReply(400, """{"error":{"code":400,"errors":[{"reason":"keyInvalid"}]}}""") else oembedOk
        }
        val result = lookup(http, key) as YouTubeLookup.Found
        assertTrue(result.notices.any { it.contains("did not accept") })
        assertFalse(result.notices.contains(YouTubeMetadataClient.NO_KEY_NOTICE))
    }

    @Test fun unreadableApiAnswerFallsBack() {
        val http = FakeHttp { url -> if (url.contains("googleapis")) HttpReply(200, "<html>") else oembedOk }
        assertTrue(lookup(http, key) is YouTubeLookup.Found)
        val server = FakeHttp { url -> if (url.contains("googleapis")) HttpReply(503, "") else oembedOk }
        assertTrue(lookup(server, key) is YouTubeLookup.Found)
    }

    @Test fun malformedKeyIsNeverSent() {
        val http = FakeHttp { oembedOk }
        val result = lookup(http, "not a key!") as YouTubeLookup.Found
        assertEquals(1, http.urls.size)
        assertTrue(http.urls[0].contains("oembed"))
        assertTrue(result.notices.any { it.contains("does not look valid") })
        assertTrue(http.headers.none { it.containsKey("x-goog-api-key") })
    }

    @Test fun oEmbedStatusesMapToClearOutcomes() {
        assertEquals(UnavailableReason.NotFound, (lookup(FakeHttp { HttpReply(404, "") }) as YouTubeLookup.Unavailable).reason)
        assertEquals(UnavailableReason.PrivateOrRestricted, (lookup(FakeHttp { HttpReply(401, "") }) as YouTubeLookup.Unavailable).reason)
        assertEquals(UnavailableReason.PrivateOrRestricted, (lookup(FakeHttp { HttpReply(403, "") }) as YouTubeLookup.Unavailable).reason)
        assertEquals(LookupProblem.QuotaExceeded, (lookup(FakeHttp { HttpReply(429, "") }) as YouTubeLookup.Failed).problem)
        assertEquals(LookupProblem.ServiceError, (lookup(FakeHttp { HttpReply(500, "") }) as YouTubeLookup.Failed).problem)
        assertEquals(LookupProblem.BadResponse, (lookup(FakeHttp { HttpReply(200, "nope") }) as YouTubeLookup.Failed).problem)
    }

    @Test fun networkFailureIsAFailedLookupNotACrash() {
        val http = FakeHttp { throw YouTubeNetworkException("offline") }
        assertEquals(LookupProblem.Network, (lookup(http, key) as YouTubeLookup.Failed).problem)
        assertEquals(LookupProblem.Network, (lookup(http) as YouTubeLookup.Failed).problem)
    }

    @Test fun untrustedThumbnailAndChannelAddressesAreDropped() {
        val body = """{"title":"T","author_name":"A","author_url":"https://evil.example/x","thumbnail_url":"https://evil.example/t.jpg"}"""
        val info = (lookup(FakeHttp { HttpReply(200, body) }) as YouTubeLookup.Found).info
        assertNull(info.thumbnailUrl)
        assertNull(info.channelUrl)
    }

    @Test fun aDifferentVideoIdInTheAnswerIsRejected() {
        val body = apiBody().replace("\"id\":\"dQw4w9WgXcQ\"", "\"id\":\"AAAAAAAAAAA\"")
        val http = FakeHttp { url -> if (url.contains("googleapis")) HttpReply(200, body) else oembedOk }
        assertEquals(MetadataSource.OEmbed, (lookup(http, key) as YouTubeLookup.Found).info.source)
    }
}
