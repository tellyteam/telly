package io.github.tellyteam.telly.ui.player

import io.github.tellyteam.telly.data.store.Aspect
import io.github.tellyteam.telly.data.store.AspectStore
import org.junit.Assert.assertEquals
import org.junit.Test

class AspectScalerTest {
    // A 4:3 picture fitted to a 1920x1080 screen is 1440x1080.
    private fun scale(aspect: Aspect) = AspectScaler.scale(aspect, 1440f, 1080f, 1920f, 1080f)

    @Test
    fun `original leaves the picture alone`() {
        assertEquals(AspectScaler.Scale(1f, 1f), scale(Aspect.Original))
    }

    @Test
    fun `stretch and force 16x9 fill a 16x9 screen`() {
        assertEquals(AspectScaler.Scale(1920f / 1440f, 1f), scale(Aspect.Stretch))
        assertEquals(AspectScaler.Scale(1920f / 1440f, 1f), scale(Aspect.Wide))
    }

    @Test
    fun `zoom scales evenly until the screen is covered`() {
        val s = scale(Aspect.Zoom)
        assertEquals(s.x, s.y)
        assertEquals(1920f / 1440f, s.x)
    }

    @Test
    fun `force 4x3 turns a squashed widescreen picture back into 4x3`() {
        // A 16:9 picture (1920x1080) that should really be 4:3.
        assertEquals(AspectScaler.Scale(1440f / 1920f, 1f), AspectScaler.scale(Aspect.Classic, 1920f, 1080f, 1920f, 1080f))
    }

    @Test
    fun `nothing laid out yet means no scaling`() {
        assertEquals(AspectScaler.Scale(1f, 1f), AspectScaler.scale(Aspect.Zoom, 0f, 0f, 1920f, 1080f))
    }

    @Test
    fun `aspects round-trip through storage and cycle in order`() {
        listOf(Aspect.Original, Aspect.Zoom, Aspect.Stretch, Aspect.Wide, Aspect.Classic, Aspect.Custom(1.04f, 0.98f)).forEach {
            assertEquals(it, AspectStore.decode(AspectStore.encode(it)))
        }
        assertEquals(Aspect.Original, AspectStore.decode("junk"))
        assertEquals(Aspect.Zoom, Aspect.next(Aspect.Original))
        assertEquals(Aspect.Original, Aspect.next(Aspect.Custom(1.2f, 1f)))
    }
}
