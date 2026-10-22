package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.Series

/** Favourite channels, films and series, newest first, and when each was added or removed (for sync). */
class FavouritesStore(private val context: Context, scope: String? = null) {
    private var prefs = open(scope)
    private val _keys = MutableStateFlow(loadKeys())
    val keys: StateFlow<List<String>> = _keys

    /** Key → (changed at, on the list). Older favourites have no entry. */
    var changes: Map<String, Pair<Long, Boolean>> = loadChanges()
        private set

    /** Switches to another provider's list. */
    fun use(scope: String?) {
        prefs = open(scope)
        changes = loadChanges()
        _keys.value = loadKeys()
    }

    private fun open(scope: String?) = context.getSharedPreferences(Scoped.name("favourites", scope), Context.MODE_PRIVATE)

    private fun loadKeys() = prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    fun toggle(key: String, now: Long = System.currentTimeMillis()) {
        val current = _keys.value
        val on = key !in current
        replace(if (on) current + key else current - key, changes + (key to (now to on)))
    }

    /** Sets the whole list (newest first) and its change times, after a sync. */
    fun replace(keys: List<String>, changes: Map<String, Pair<Long, Boolean>>) {
        prefs.edit()
            .putString(KEY, keys.joinToString("\n"))
            .putString(CHANGES, json.encodeToString(changesSerializer, changes))
            .apply()
        this.changes = changes
        _keys.value = keys
    }

    private fun loadChanges(): Map<String, Pair<Long, Boolean>> =
        prefs.getString(CHANGES, null)?.let { runCatching { json.decodeFromString(changesSerializer, it) }.getOrNull() }.orEmpty()

    companion object {
        private const val KEY = "keys"
        private const val CHANGES = "changes"
        private val json = Json { ignoreUnknownKeys = true }
        private val changesSerializer = MapSerializer(String.serializer(), PairSerializer(Long.serializer(), Boolean.serializer()))

        fun live(channel: Channel) = "live:${channel.id}"
        fun film(film: Channel) = "film:${film.id}"
        fun series(series: Series) = "series:${series.id}"
    }
}
