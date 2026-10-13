package io.github.tellyteam.telly.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesTest {
    @Test
    fun `channel names lose stray separators`() {
        assertEquals("BBC One FHD", ChannelNames.channel("BBC One | FHD |"))
        assertEquals("Sky Sports Main Event", ChannelNames.channel("Sky Sports Main Event"))
        assertEquals("ABC News", ChannelNames.channel("|ABC| News"))
        assertEquals("|", ChannelNames.channel("|"))
    }

    @Test
    fun `categories keep their tags with a softer separator`() {
        assertEquals("UK · Free To Air", ChannelNames.category("UK | Free To Air"))
        assertEquals("BBC · Regionals FTA", ChannelNames.category("BBC | Regionals FTA"))
        assertEquals("★ Favourites", ChannelNames.category("★ Favourites"))
    }
}
