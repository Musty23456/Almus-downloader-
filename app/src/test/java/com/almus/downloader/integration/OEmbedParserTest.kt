package com.almus.downloader.integration

import com.almus.downloader.integration.specs.XSpec
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OEmbedParserTest {
    private val ref = ContentRef(MediaPlatform.X, LinkKind.Post, "1234567890123456789", "jack")

    private fun parse(body: String) = OEmbedParser.parse(body, ref, XSpec)

    @Test fun markupIsReadAsTextOnly() {
        assertEquals("a & b < c > d", MarkupText.plain("a &amp; b &lt; c &gt; d"))
        assertEquals("one\ntwo", MarkupText.plain("one<br>two"))
        assertEquals("one\ntwo", MarkupText.plain("one<br/>two"))
        assertEquals("say \"hi\" it's", MarkupText.plain("say &quot;hi&quot; it&#39;s"))
        assertEquals("é and é", MarkupText.plain("&#233; and &#xE9;"))
        assertEquals("click", MarkupText.plain("<b></b>click"))
        assertEquals("&amp;", MarkupText.plain("&amp;amp;"))
    }

    @Test fun badNumericEntitiesAreLeftAlone() {
        assertEquals("&#1;", MarkupText.plain("&#1;"))
        assertEquals("&#99999999;", MarkupText.plain("&#99999999;"))
        assertEquals("&#xD800;", MarkupText.plain("&#xD800;"))
    }

    @Test fun onlyTheFirstParagraphIsTaken() {
        val html = "<blockquote><p>first</p><p>second</p></blockquote>"
        assertEquals("first", MarkupText.firstParagraph(html))
        assertNull(MarkupText.firstParagraph("<div>none</div>"))
        assertNull(MarkupText.firstParagraph(null))
    }

    @Test fun aLongPostIsCutToALimitedLength() {
        val long = "x".repeat(1000)
        val body = """{"type":"rich","author_name":"Jack","html":"<blockquote><p>$long</p><a href=\"https://twitter.com/jack/status/1234567890123456789\">d</a></blockquote>"}"""
        assertEquals(300, parse(body)!!.title!!.length)
    }

    @Test fun controlCharactersAreRemovedFromTextButLineBreaksStay() {
        val body = """{"type":"rich","title":"a\u0007b\nc","author_name":"Jack"}"""
        assertEquals("ab\nc", parse(body)!!.title)
    }

    @Test fun sizesAndLengthsOutsideSaneLimitsAreIgnored() {
        val body = """{"type":"video","title":"t","duration":-5,"thumbnail_url":"https://pbs.twimg.com/a.jpg","thumbnail_width":0,"thumbnail_height":99999}"""
        val info = parse(body)!!
        assertNull(info.durationSeconds)
        assertNull(info.thumbnailWidthPx)
        assertNull(info.thumbnailHeightPx)
    }

    @Test fun lengthsAreWrittenAsMinutesOrHours() {
        fun info(seconds: Int?) = ContentInfo(MediaPlatform.Vimeo, "1", null, null, null, null, null, null, seconds, null, "s")
        assertEquals("0:05", info(5).durationText)
        assertEquals("1:00", info(60).durationText)
        assertEquals("9:56", info(596).durationText)
        assertEquals("1:02:03", info(3723).durationText)
        assertNull(info(null).durationText)
        assertNull(info(0).durationText)
    }

    @Test fun errorAnswersAreReadInBothShapes() {
        assertEquals("100", OEmbedParser.apiError("""{"error":{"message":"x","code":100}}""")!!.code)
        assertEquals("rate_limit", OEmbedParser.apiError("""{"code":"rate_limit","message":"x"}""")!!.code)
        assertNull(OEmbedParser.apiError("""{"title":"fine"}"""))
        assertNull(OEmbedParser.apiError("not json"))
    }
}
