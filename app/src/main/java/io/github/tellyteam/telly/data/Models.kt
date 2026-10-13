package io.github.tellyteam.telly.data

/** Where the channel list comes from. Credentials never leave the device except to this provider. */
sealed interface Provider {
    val name: String

    /** Plain-HTTP requests are refused unless the user turns this on (many IPTV providers lack HTTPS). */
    val allowCleartext: Boolean

    data class Xtream(
        override val name: String,
        val server: String,
        val username: String,
        val password: String,
        override val allowCleartext: Boolean,
    ) : Provider

    data class M3u(
        override val name: String,
        val url: String,
        override val allowCleartext: Boolean,
    ) : Provider
}

/** Anything the player can play: a live channel, a film or a series episode. */
data class Channel(
    val id: String,
    val name: String,
    val group: String,
    /** Channel logo, film poster or episode still. */
    val logoUrl: String?,
    val streamUrl: String,
    /** XMLTV channel id, for the programme guide (live channels only). */
    val epgId: String? = null,
    /** When the provider added it (epoch seconds; 0 if unknown), for "Recently added". */
    val added: Long = 0L,
)

data class ChannelGroup(val name: String, val channels: List<Channel>)

data class Series(
    val id: String,
    val name: String,
    val group: String,
    val coverUrl: String?,
    val plot: String?,
    /** When the provider last added or updated it (epoch seconds; 0 if unknown). */
    val added: Long = 0L,
)

data class SeriesGroup(val name: String, val series: List<Series>)

data class Season(val name: String, val episodes: List<Channel>)

/** Extra information for a detail page; every field is optional because panels vary. */
data class Details(
    val plot: String? = null,
    val genre: String? = null,
    val year: String? = null,
    /** Already formatted, e.g. "7.4". */
    val rating: String? = null,
    /** Already formatted, e.g. "1h 52m" or "45 min". */
    val runtime: String? = null,
    val cast: String? = null,
    val backdropUrl: String? = null,
)

data class SeriesData(val details: Details, val seasons: List<Season>)

const val UNCATEGORISED = "Uncategorised"

/**
 * Groups [items] in the provider's category order (which providers curate), then any
 * categories it didn't list, keeping each group's items in their original order.
 */
fun <T, G> groupInOrder(
    items: List<T>,
    categoryOrder: List<String>,
    groupOf: (T) -> String,
    build: (String, List<T>) -> G,
): List<G> {
    val byGroup = items.groupBy(groupOf)
    val ordered = categoryOrder.filter { it in byGroup } + byGroup.keys.filter { it !in categoryOrder }
    return ordered.distinct().map { build(it, byGroup.getValue(it)) }
}
