package io.github.tellyteam.telly.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPrefsTest {
    private val provider = listOf("News", "Sport", "Kids", "Movies", "Adult")

    @Test
    fun `nothing chosen keeps the provider's order`() {
        assertEquals(provider, CategoryPrefs().arrange(provider) { it })
    }

    @Test
    fun `pinned come first in the order pinned, hidden are left out`() {
        val prefs = CategoryPrefs().togglePinned("Sport").togglePinned("Kids").toggleHidden("Adult")
        assertEquals(listOf("Sport", "Kids", "News", "Movies"), prefs.arrange(provider) { it })
    }

    @Test
    fun `hiding unpins, pinning unhides, and a second press undoes`() {
        val hidden = CategoryPrefs().togglePinned("Sport").toggleHidden("Sport")
        assertTrue(hidden.isHidden("Sport"))
        assertFalse(hidden.isPinned("Sport"))
        val pinned = hidden.togglePinned("Sport")
        assertTrue(pinned.isPinned("Sport"))
        assertFalse(pinned.isHidden("Sport"))
        assertEquals(CategoryPrefs(), pinned.togglePinned("Sport"))
    }

    @Test
    fun `categories the provider dropped are ignored`() {
        val prefs = CategoryPrefs(pinned = listOf("Gone", "Kids"), hidden = setOf("Also gone"))
        assertEquals(listOf("Kids", "News", "Sport", "Movies", "Adult"), prefs.arrange(provider) { it })
    }
}
