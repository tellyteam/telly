package io.github.tellyteam.telly.data.store

import android.content.Context

/** How a picture is shaped on screen. [Custom] scales the original shape by width and height factors. */
sealed interface Aspect {
    val label: String

    data object Original : Aspect { override val label = "Original" }
    data object Zoom : Aspect { override val label = "Zoom (fill, crop edges)" }
    data object Stretch : Aspect { override val label = "Stretch to screen" }
    data object Wide : Aspect { override val label = "Force 16:9" }
    data object Classic : Aspect { override val label = "Force 4:3" }
    data class Custom(val width: Float = 1f, val height: Float = 1f) : Aspect {
        override val label = "Custom"
    }

    companion object {
        /** Order of the remote's Menu-button cycle. */
        val cycle: List<Aspect> = listOf(Original, Zoom, Stretch, Wide, Classic, Custom())

        fun next(current: Aspect): Aspect =
            cycle[(cycle.indexOfFirst { it::class == current::class } + 1) % cycle.size]
    }
}

/** The aspect chosen for each channel, film or series, so a mislabelled stream only needs fixing once. */
class AspectStore(context: Context) {
    private val prefs = context.getSharedPreferences("aspect", Context.MODE_PRIVATE)

    fun get(key: String): Aspect = decode(prefs.getString(key, null))

    fun save(key: String, aspect: Aspect) {
        if (aspect == Aspect.Original) prefs.edit().remove(key).apply() else prefs.edit().putString(key, encode(aspect)).apply()
    }

    internal companion object {
        fun encode(aspect: Aspect): String = when (aspect) {
            Aspect.Original -> "original"
            Aspect.Zoom -> "zoom"
            Aspect.Stretch -> "stretch"
            Aspect.Wide -> "16:9"
            Aspect.Classic -> "4:3"
            is Aspect.Custom -> "custom:${aspect.width}:${aspect.height}"
        }

        fun decode(text: String?): Aspect = when {
            text == null -> Aspect.Original
            text == "zoom" -> Aspect.Zoom
            text == "stretch" -> Aspect.Stretch
            text == "16:9" -> Aspect.Wide
            text == "4:3" -> Aspect.Classic
            text.startsWith("custom:") -> {
                val parts = text.split(':')
                Aspect.Custom(parts.getOrNull(1)?.toFloatOrNull() ?: 1f, parts.getOrNull(2)?.toFloatOrNull() ?: 1f)
            }
            else -> Aspect.Original
        }
    }
}
