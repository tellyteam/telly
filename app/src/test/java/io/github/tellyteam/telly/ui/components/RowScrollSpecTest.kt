package io.github.tellyteam.telly.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class RowScrollSpecTest {
    // A 1000px row with 50px of leading padding; cards are 200px.
    private val spec = RowScrollSpec(leadingPx = 50f)

    @Test
    fun `cards comfortably inside the row don't scroll it`() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 50f, size = 200f, containerSize = 1000f))
        assertEquals(0f, spec.calculateScrollDistance(offset = 500f, size = 200f, containerSize = 1000f))
    }

    @Test
    fun `nearing the right edge scrolls early, keeping room ahead`() {
        // Room ahead is 22% (220px): a card ending at 900 scrolls by 120 to end at 780.
        assertEquals(120f, spec.calculateScrollDistance(offset = 700f, size = 200f, containerSize = 1000f))
    }

    @Test
    fun `going left lines the card up with the row's start`() {
        assertEquals(-80f, spec.calculateScrollDistance(offset = -30f, size = 200f, containerSize = 1000f))
    }
}
