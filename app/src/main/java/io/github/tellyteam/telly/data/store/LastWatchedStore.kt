package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import io.github.tellyteam.telly.data.Channel

/**
 * Recently watched things, newest first, each with its whole playlist (a live group or a season)
 * so "pick up where you left off" and the Continue watching row reopen them exactly. One entry per
 * series, per film and for live TV. Encrypted, because Xtream stream URLs embed the login.
 */
class LastWatchedStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_watched", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("telly_last_watched")
    private val _history = MutableStateFlow(load())

    /** Newest first. */
    val history: StateFlow<List<LastWatched>> = _history

    @Serializable
    data class Item(val id: String, val name: String, val group: String, val logoUrl: String?, val streamUrl: String)

    @Serializable
    data class LastWatched(
        /** "Live TV", "Film", or the series name. */
        val heading: String,
        val live: Boolean,
        val index: Int,
        val items: List<Item>,
        /** Series only; older history has none and is matched by [heading]. */
        val seriesId: String? = null,
        /** When this entry was saved (epoch ms; 0 in older history). */
        val savedAt: Long = 0,
        /** The provider added episodes after the last one watched; cleared once something is played. */
        val newEpisodes: Boolean = false,
    ) {
        val current: Item get() = items[index.coerceIn(items.indices)]
        val isFilm: Boolean get() = !live && heading == FILM

        /** One history slot per series, per film, and one for live TV. */
        val slot: String get() = when {
            live -> "live"
            isFilm -> "film:${current.id}"
            else -> "series:$heading"
        }

        /**
         * Where "pick up where you left off" should go: the next episode if this one was left in
         * its last minute (the credits; and there is a next one), otherwise this item.
         */
        fun continueIndex(progress: WatchProgressStore): Int {
            val i = index.coerceIn(items.indices)
            if (live || i + 1 >= items.size) return i
            return if (progress.get(items[i].streamUrl)?.finished == true) i + 1 else i
        }

        fun channels(): List<Channel> = items.map { Channel(it.id, it.name, it.group, it.logoUrl, it.streamUrl) }

        /** A series watched to the end of everything it had: its last known episode is finished. */
        fun caughtUp(progress: WatchProgressStore): Boolean =
            !live && !isFilm && index >= items.lastIndex && progress.get(current.streamUrl)?.finished == true
    }

    fun save(
        heading: String,
        live: Boolean,
        playlist: List<Channel>,
        index: Int,
        seriesId: String? = null,
        newEpisodes: Boolean = false,
        toFront: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ) {
        if (playlist.isEmpty()) return
        val items = playlist.map { Item(it.id, it.name, it.group, it.logoUrl, it.streamUrl) }
        // Keep the series id when an older entry (or a caller that doesn't know it) saves again.
        val id = seriesId ?: _history.value.firstOrNull { !live && it.heading == heading && !it.isFilm }?.seriesId
        store(LastWatched(heading, live, index, items, id, now, newEpisodes), toFront)
    }

    /** Takes an entry out of Continue watching. */
    fun remove(slot: String) {
        persist(_history.value.filter { it.slot != slot })
    }

    /** Newest first; with [toFront] false an existing entry is updated where it is. */
    private fun store(entry: LastWatched, toFront: Boolean = true) {
        val current = _history.value
        val at = current.indexOfFirst { it.slot == entry.slot }
        val updated = if (toFront || at < 0) listOf(entry) + current.filter { it.slot != entry.slot } else current.toMutableList().apply { set(at, entry) }
        persist(updated.take(MAX_ENTRIES))
    }

    private fun persist(updated: List<LastWatched>) {
        _history.value = updated
        prefs.edit().putString(KEY, cipher.encrypt(json.encodeToString(listSerializer, updated))).apply()
    }

    fun clear() {
        _history.value = emptyList()
        prefs.edit().remove(KEY).remove(OLD_KEY).apply()
    }

    private fun load(): List<LastWatched> {
        prefs.getString(KEY, null)?.let { stored ->
            runCatching { json.decodeFromString(listSerializer, cipher.decrypt(stored)) }.getOrNull()?.let { list ->
                return list.filter { it.items.isNotEmpty() }
            }
        }
        // Earlier versions kept a single entry.
        return prefs.getString(OLD_KEY, null)?.let { stored ->
            runCatching { json.decodeFromString(LastWatched.serializer(), cipher.decrypt(stored)) }.getOrNull()
        }?.takeIf { it.items.isNotEmpty() }?.let(::listOf).orEmpty()
    }

    companion object {
        const val FILM = "Film"
        private const val KEY = "history"
        private const val OLD_KEY = "last"
        private const val MAX_ENTRIES = 20
        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(LastWatched.serializer())
    }
}
