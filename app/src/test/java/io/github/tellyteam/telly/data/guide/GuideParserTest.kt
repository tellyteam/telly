package io.github.tellyteam.telly.data.guide

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideParserTest {

    @Test
    fun `short epg listings decode base64 and sort by start`() {
        val json = Json.parseToJsonElement(
            """
            {"epg_listings":[
              {"title":"RXZlbmluZyBOZXdz","description":"VGhlIGxhdGVzdCBoZWFkbGluZXMu","start_timestamp":"1800","stop_timestamp":"3600"},
              {"title":"Breakfast","description":"","start_timestamp":"0","stop_timestamp":"1800"},
              {"title":"","start_timestamp":"3600","stop_timestamp":"5400"}
            ]}
            """.trimIndent(),
        )
        val programmes = GuideParser.programmes(json)
        assertEquals(listOf("Breakfast", "Evening News"), programmes.map { it.title })
        assertEquals("The latest headlines.", programmes[1].description)
        assertNull(programmes[0].description)
        assertEquals(1_800_000L, programmes[1].startMs)
    }

    @Test
    fun `now and next pick the airing programme`() {
        val a = Programme("A", null, 0, 1000)
        val b = Programme("B", null, 1000, 2000)
        assertEquals(NowNext(a, b), GuideParser.nowNext(listOf(a, b), 500))
        assertEquals(NowNext(b, null), GuideParser.nowNext(listOf(a, b), 1500))
        assertEquals(NowNext(null, a), GuideParser.nowNext(listOf(a, b), -10))
        assertEquals(0.5f, b.progress(1500))
    }

    @Test
    fun `plain text titles are left alone`() {
        assertEquals("News", GuideParser.maybeBase64("News"))
        assertEquals("Match of the Day", GuideParser.maybeBase64("Match of the Day"))
        assertEquals("Hello", GuideParser.maybeBase64("SGVsbG8="))
    }
}
