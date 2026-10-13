package io.github.tellyteam.telly.data.m3u

import io.github.tellyteam.telly.data.UNCATEGORISED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(text: String) = M3uParser.parse(text.lineSequence())

    @Test
    fun `reads attributes, title and url`() {
        val channels = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="https://img/bbc1.png" group-title="UK",BBC One HD
            https://cdn.example/bbc1.m3u8
            """.trimIndent(),
        )

        val c = channels.single()
        assertEquals("BBC One HD", c.name)
        assertEquals("UK", c.group)
        assertEquals("https://img/bbc1.png", c.logoUrl)
        assertEquals("bbc1.uk", c.epgId)
        assertEquals("https://cdn.example/bbc1.m3u8", c.streamUrl)
    }

    @Test
    fun `commas inside attribute values do not split the title`() {
        val c = parse(
            """
            #EXTINF:-1 tvg-name="News, Live" group-title="News, World",Sky News
            http://x/1.ts
            """.trimIndent(),
        ).single()

        assertEquals("Sky News", c.name)
        assertEquals("News, World", c.group)
    }

    @Test
    fun `falls back to EXTGRP, then uncategorised`() {
        val channels = parse(
            """
            #EXTINF:-1,One
            #EXTGRP:Sport
            http://x/1.ts
            #EXTINF:-1,Two
            http://x/2.ts
            """.trimIndent(),
        )

        assertEquals(listOf("Sport", UNCATEGORISED), channels.map { it.group })
    }

    @Test
    fun `bare urls and blank lines are tolerated`() {
        val channels = parse("\n#EXTM3U\n\nhttp://x/plain.ts\r\n#EXTVLCOPT:http-user-agent=foo\n")

        val c = channels.single()
        assertEquals("http://x/plain.ts", c.name)
        assertNull(c.logoUrl)
    }
}
