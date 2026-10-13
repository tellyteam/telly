package io.github.tellyteam.telly.ui.live

/**
 * Tidies provider names for display, keeping every word: "BBC One | FHD |" → "BBC One FHD",
 * "UK | Free To Air" → "UK · Free To Air". Country and network tags stay, so "UK · Movies" and
 * "US · Movies" remain distinct. Matching and storage keep using the original names.
 */
object ChannelNames {
    private val separators = Regex("""\s*[|•]+\s*""")

    fun channel(raw: String): String = parts(raw).joinToString(" ").ifBlank { raw.trim() }

    fun category(raw: String): String = parts(raw).joinToString(" · ").ifBlank { raw.trim() }

    private fun parts(s: String) = s.split(separators).map { it.trim() }.filter { it.isNotEmpty() }
}
