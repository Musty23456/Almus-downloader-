package com.almus.downloader.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonAndDurationTest {
    @Test fun parsesNestedObjectsArraysAndEscapes() {
        val o = MiniJson.parseObject("""{"a":{"b":[1,2.5,"x\n\u0041\"",true,false,null]},"c":"é"}""")
        assertNotNull(o)
        val list = o!!.obj("a")!!.list("b")!!
        assertEquals(6, list.size)
        assertEquals(1.0, list[0])
        assertEquals("x\nA\"", list[2])
        assertEquals(true, list[3])
        assertNull(list[5])
        assertEquals("é", o.str("c"))
    }

    @Test fun rejectsBrokenJson() {
        listOf("", "[]", "{", """{"a":}""", """{"a":1,}""", """{"a":1} x""", """{"a":"unterminated}""", "null", "{'a':1}").forEach {
            assertNull("should reject: $it", MiniJson.parseObject(it))
        }
    }

    @Test fun rejectsTooDeepAndTooLarge() {
        val deep = "{\"a\":".repeat(MiniJson.MAX_DEPTH + 5) + "1" + "}".repeat(MiniJson.MAX_DEPTH + 5)
        assertNull(MiniJson.parseObject(deep))
        assertNull(MiniJson.parseObject("{\"a\":\"" + "x".repeat(MiniJson.MAX_CHARS) + "\"}"))
    }

    @Test fun parsesIsoDurations() {
        assertEquals(212L, IsoDuration.parseSeconds("PT3M32S"))
        assertEquals(3_723L, IsoDuration.parseSeconds("PT1H2M3S"))
        assertEquals(45L, IsoDuration.parseSeconds("PT45S"))
        assertEquals(86_400L + 60L, IsoDuration.parseSeconds("P1DT1M"))
        assertEquals(0L, IsoDuration.parseSeconds("P0D"))
    }

    @Test fun rejectsBadDurations() {
        listOf(null, "", "P", "PT", "P1DT", "3:32", "PT-5S", "PT1.5S", "pt3m", "PT3M32").forEach {
            assertNull("should reject: $it", IsoDuration.parseSeconds(it))
        }
    }

    @Test fun formatsDurations() {
        assertEquals("3:32", IsoDuration.format(212))
        assertEquals("0:05", IsoDuration.format(5))
        assertEquals("1:02:03", IsoDuration.format(3_723))
        assertEquals("25:00:00", IsoDuration.format(90_000))
        assertEquals("0:00", IsoDuration.format(-4))
        assertTrue(IsoDuration.format(59).startsWith("0:"))
    }
}
