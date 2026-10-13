package io.github.tellyteam.telly.data.store

import io.github.tellyteam.telly.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderStoreTest {

    @Test
    fun `xtream provider round-trips`() {
        val p = Provider.Xtream("Home", "https://tv.example", "me", "p\"ss", allowCleartext = true)
        assertEquals(p, ProviderStore.decode(ProviderStore.encode(p)))
    }

    @Test
    fun `m3u provider round-trips`() {
        val p = Provider.M3u("List", "https://example.com/list.m3u", allowCleartext = false)
        assertEquals(p, ProviderStore.decode(ProviderStore.encode(p)))
    }
}
