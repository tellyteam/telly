package io.github.tellyteam.telly.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CatalogCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val parseText = { input: java.io.InputStream -> input.bufferedReader().readText() }

    @Test
    fun `nothing cached reads as null`() {
        assertNull(CatalogCache(tmp.root).read("p", "films", parseText))
    }

    @Test
    fun `replace saves what parsed and read returns it`() {
        val cache = CatalogCache(tmp.root)
        assertEquals("v1", cache.replace("p", "films", { it.write("v1".toByteArray()) }, parseText))
        assertEquals("v1", cache.read("p", "films", parseText))
    }

    @Test
    fun `a download that fails to parse keeps the previous copy`() {
        val cache = CatalogCache(tmp.root)
        cache.replace("p", "films", { it.write("good".toByteArray()) }, parseText)
        try {
            cache.replace("p", "films", { it.write("bad".toByteArray()) }) { throw IOException("unparseable") }
        } catch (expected: IOException) {
        }
        assertEquals("good", cache.read("p", "films", parseText))
    }

    @Test
    fun `scope depends on server and user but is not the raw values`() {
        val a = CatalogCache.scope("xtream", "https://tv.example/", "me")
        assertEquals(a, CatalogCache.scope("xtream", "https://tv.example/", "me"))
        assertNotEquals(a, CatalogCache.scope("xtream", "https://tv.example/", "you"))
        assertEquals(24, a.length)
    }
}
