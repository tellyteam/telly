package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the user got to in each film and episode, so playback resumes there. */
class WatchProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)

    /** Bumped on every save so screens can redraw progress bars. */
    val changes: StateFlow<Int> = _changes

    data class Progress(val positionMs: Long, val durationMs: Long) {
        val fraction: Float get() = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val finished: Boolean get() = durationMs - positionMs < endMargin(durationMs)
    }

    fun get(streamUrl: String): Progress? = prefs.getString(key(streamUrl), null)?.let(::decode)

    fun save(streamUrl: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        prefs.edit().putString(key(streamUrl), encode(Progress(positionMs.coerceIn(0, durationMs), durationMs))).apply()
        _changes.value++
    }

    fun markFinished(streamUrl: String, durationMs: Long) = save(streamUrl, durationMs, durationMs)

    /** Where to start: the saved position, unless barely started or already finished. */
    fun resumePosition(streamUrl: String): Long {
        val p = get(streamUrl) ?: return 0
        return if (p.positionMs < MIN_RESUME_MS || p.finished) 0 else p.positionMs
    }

    companion object {
        /**
         * The "next episode" prompt shows in the last minute. Streaming credits typically run
         * 60–90 s, broadcast 20–40 s.
         */
        const val END_MARGIN_MS = 60_000L

        /**
         * How close to the end counts as watched: the last minute, or the last 5% when that's longer.
         * Long episodes often run two or three minutes of credits (a 45-minute episode: 2¼ minutes).
         */
        fun endMargin(durationMs: Long): Long = maxOf(END_MARGIN_MS, durationMs / 20)

        private const val MIN_RESUME_MS = 15_000L

        /**
         * Xtream stream URLs embed the username and password (`/series/user/pass/123.mkv`), so the
         * key keeps only the kind and file name: `series/123.mkv`.
         */
        internal fun key(streamUrl: String): String {
            val segments = streamUrl.toHttpUrlOrNull()?.pathSegments ?: return streamUrl
            return if (segments.size >= 4) "${segments[segments.size - 4]}/${segments.last()}" else streamUrl
        }

        internal fun encode(p: Progress) = "${p.positionMs}/${p.durationMs}"

        internal fun decode(text: String): Progress? {
            val (position, duration) = text.split('/').takeIf { it.size >= 2 } ?: return null
            return Progress(position.toLongOrNull() ?: return null, duration.toLongOrNull() ?: return null)
        }
    }
}
