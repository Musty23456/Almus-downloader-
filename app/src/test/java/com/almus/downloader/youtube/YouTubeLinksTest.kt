package com.almus.downloader.youtube

import com.almus.downloader.platform.LinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"

    private fun ref(url: String) = YouTubeLinks.videoRef(url)

    @Test fun recognisesEveryVideoShape() {
        val expectations = mapOf(
            "https://www.youtube.com/watch?v=$id" to LinkKind.Video,
            "https://youtube.com/watch?v=$id&list=PL123&t=42s" to LinkKind.Video,
            "https://m.youtube.com/watch?v=$id" to LinkKind.Video,
            "https://music.youtube.com/watch?v=$id" to LinkKind.Video,
            "https://youtu.be/$id" to LinkKind.Video,
            "https://youtu.be/$id?si=abc" to LinkKind.Video,
            "https://www.youtube.com/embed/$id" to LinkKind.Video,
            "https://www.youtube-nocookie.com/embed/$id" to LinkKind.Video,
            "https://www.youtube.com/shorts/$id" to LinkKind.ShortVideo,
            "https://www.youtube.com/live/$id" to LinkKind.Live,
        )
        expectations.forEach { (url, kind) ->
            val r = ref(url)
            assertEquals("id of $url", id, r?.videoId)
            assertEquals("kind of $url", kind, r?.kind)
        }
    }

    @Test fun watchUrlIsBuiltFromTheValidatedIdOnly() {
        assertEquals("https://www.youtube.com/watch?v=$id", ref("https://youtu.be/$id?si=tracking&x=1")!!.watchUrl)
    }

    @Test fun rejectsEverythingThatIsNotAPlainVideo() {
        listOf(
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/@somechannel",
            "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw",
            "https://www.youtube.com/results?search_query=cats",
            "https://www.youtube.com/",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=has%20space1",
            "https://www.youtube.com/watch?v=$id" + "x",
            "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1",
            "https://consent.youtube.com/m?continue=x",
            "https://accounts.youtube.com/signin",
            "https://youtube.com.evil.example/watch?v=$id",
            "https://notyoutube.com/watch?v=$id",
            "ftp://youtu.be/$id",
            "javascript:alert(1)",
            "not a link",
            "",
        ).forEach { assertNull("should not be a video: $it", ref(it)) }
        assertNull(YouTubeLinks.videoRef(null as String?))
    }

    @Test fun idAndHostChecks() {
        assertTrue(YouTubeLinks.isValidVideoId("a_B-9xyz012"))
        assertFalse(YouTubeLinks.isValidVideoId("a_B-9xyz01"))
        assertFalse(YouTubeLinks.isValidVideoId("a_B-9xyz01!"))
        assertFalse(YouTubeLinks.isValidVideoId(null))
        assertTrue(YouTubeLinks.isValidChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"))
        assertFalse(YouTubeLinks.isValidChannelId("UCshort"))
        assertTrue(YouTubeLinks.isTrustedThumbnail("https://i.ytimg.com/vi/$id/mqdefault.jpg"))
        assertFalse(YouTubeLinks.isTrustedThumbnail("http://i.ytimg.com/vi/$id/mqdefault.jpg"))
        assertFalse(YouTubeLinks.isTrustedThumbnail("https://ytimg.com.evil.example/x.jpg"))
        assertFalse(YouTubeLinks.isTrustedThumbnail("https://example.com/x.jpg"))
        assertFalse(YouTubeLinks.isTrustedThumbnail(null))
        assertTrue(YouTubeLinks.isTrustedChannelUrl("https://www.youtube.com/@name"))
        assertFalse(YouTubeLinks.isTrustedChannelUrl("https://example.com/@name"))
    }
}
