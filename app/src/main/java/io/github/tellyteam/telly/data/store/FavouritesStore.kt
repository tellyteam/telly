package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.Series

/** Favourite channels, films and series, in the order they were added. */
class FavouritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty())
    val keys: StateFlow<List<String>> = _keys

    fun toggle(key: String) {
        val current = _keys.value
        val updated = if (key in current) current - key else current + key
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
        _keys.value = updated
    }

    companion object {
        private const val KEY = "keys"

        fun live(channel: Channel) = "live:${channel.id}"
        fun film(film: Channel) = "film:${film.id}"
        fun series(series: Series) = "series:${series.id}"
    }
}
