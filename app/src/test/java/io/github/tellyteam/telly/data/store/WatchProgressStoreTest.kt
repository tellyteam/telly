package io.github.tellyteam.telly.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressStoreTest {

    @Test
    fun `key drops credentials from xtream urls`() {
        assertEquals("series/201.mkv", WatchProgressStore.key("https://tv.example:8080/series/me/secret/201.mkv"))
        assertEquals("movie/55.mp4", WatchProgressStore.key("https://tv.example/movie/me/secret/55.mp4"))
    }

    @Test
    fun `progress round-trips and rejects junk`() {
        val p = WatchProgressStore.Progress(90_000, 2_400_000)
        assertEquals(p, WatchProgressStore.decode(WatchProgressStore.encode(p)))
        assertNull(WatchProgressStore.decode("nonsense"))
    }

    @Test
    fun `last minute counts as finished`() {
        assertTrue(WatchProgressStore.Progress(2_345_000, 2_400_000).finished)
        assertFalse(WatchProgressStore.Progress(2_330_000, 2_400_000).finished)
    }
}
