package io.github.tellyteam.telly.ui.player

import io.github.tellyteam.telly.data.store.Aspect
import kotlin.math.max

/**
 * Scale factors that turn the player's natural "fit" picture into the chosen [Aspect].
 *
 * The player always lays the picture out at its own shape, fitted to the screen ([frameW] x [frameH]
 * inside [screenW] x [screenH]); the chosen aspect is then applied as an X/Y scale on that frame, so
 * it keeps working when the stream's size changes.
 */
object AspectScaler {
    data class Scale(val x: Float, val y: Float)

    fun scale(aspect: Aspect, frameW: Float, frameH: Float, screenW: Float, screenH: Float): Scale {
        if (frameW <= 0f || frameH <= 0f || screenW <= 0f || screenH <= 0f) return Scale(1f, 1f)
        return when (aspect) {
            Aspect.Original -> Scale(1f, 1f)
            Aspect.Zoom -> max(screenW / frameW, screenH / frameH).let { Scale(it, it) }
            Aspect.Stretch -> Scale(screenW / frameW, screenH / frameH)
            Aspect.Wide -> forced(16f / 9f, frameW, frameH, screenW, screenH)
            Aspect.Classic -> forced(4f / 3f, frameW, frameH, screenW, screenH)
            is Aspect.Custom -> Scale(aspect.width, aspect.height)
        }
    }

    /** The largest box of [ratio] that fits the screen. */
    private fun forced(ratio: Float, frameW: Float, frameH: Float, screenW: Float, screenH: Float): Scale {
        val (w, h) = if (screenW / screenH > ratio) screenH * ratio to screenH else screenW to screenW / ratio
        return Scale(w / frameW, h / frameH)
    }
}
