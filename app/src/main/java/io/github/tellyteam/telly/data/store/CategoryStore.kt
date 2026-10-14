package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class CategoryKind(val label: String) { LIVE("Live TV"), SERIES("Series"), FILMS("Films") }

/** Categories the user pinned to the top (in the order pinned) or hid, by name. */
@Serializable
data class CategoryPrefs(val pinned: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    fun isPinned(name: String) = name in pinned
    fun isHidden(name: String) = name in hidden

    /** Pinned categories first in the order they were pinned, then the provider's order; hidden ones left out. */
    fun <G> arrange(groups: List<G>, nameOf: (G) -> String): List<G> {
        if (pinned.isEmpty() && hidden.isEmpty()) return groups
        val byName = groups.associateBy(nameOf)
        val top = pinned.mapNotNull { byName[it] }
        return top + groups.filter { nameOf(it) !in hidden && nameOf(it) !in pinned }
    }

    /** Pinning shows a hidden category again; pinning again unpins it. */
    fun togglePinned(name: String) =
        if (name in pinned) copy(pinned = pinned - name) else CategoryPrefs(pinned + name, hidden - name)

    /** Hiding also unpins. */
    fun toggleHidden(name: String) =
        if (name in hidden) copy(hidden = hidden - name) else CategoryPrefs(pinned - name, hidden + name)
}

/** How the user arranged Live TV, film and series categories. Kept on this device only. */
class CategoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<CategoryKind, CategoryPrefs>> = _state

    operator fun get(kind: CategoryKind): CategoryPrefs = _state.value[kind] ?: CategoryPrefs()

    fun togglePinned(kind: CategoryKind, name: String) = update(kind) { it.togglePinned(name) }

    fun toggleHidden(kind: CategoryKind, name: String) = update(kind) { it.toggleHidden(name) }

    fun reset(kind: CategoryKind) = update(kind) { CategoryPrefs() }

    private fun update(kind: CategoryKind, change: (CategoryPrefs) -> CategoryPrefs) {
        val updated = _state.value + (kind to change(get(kind)))
        _state.value = updated
        prefs.edit().putString(KEY, json.encodeToString(serializer, updated)).apply()
    }

    private fun load(): Map<CategoryKind, CategoryPrefs> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "arrangement"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = MapSerializer(CategoryKind.serializer(), CategoryPrefs.serializer())
    }
}
