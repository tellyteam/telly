package io.github.tellyteam.telly.data.m3u

import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.UNCATEGORISED

/** Parses extended M3U playlists (`#EXTINF` with tvg-* and group-title attributes). */
object M3uParser {
    private val attribute = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    private data class Info(val name: String, val attrs: Map<String, String>, val extGroup: String? = null)

    fun parse(lines: Sequence<String>): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pending: Info? = null

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseInfo(line)
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pending = pending?.copy(extGroup = line.substringAfter(':').trim())
                line.startsWith("#") -> Unit
                else -> {
                    channels += toChannel(pending, line)
                    pending = null
                }
            }
        }
        return channels
    }

    private fun parseInfo(line: String): Info {
        val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        // Attribute values may contain commas, so drop them before looking for the title separator.
        val title = attribute.replace(line, "").substringAfter(',', "").trim()
        return Info(title, attrs)
    }

    private fun toChannel(info: Info?, url: String): Channel {
        val attrs = info?.attrs.orEmpty()
        val name = info?.name?.ifBlank { null } ?: attrs["tvg-name"]?.ifBlank { null } ?: url
        val group = attrs["group-title"]?.ifBlank { null } ?: info?.extGroup?.ifBlank { null } ?: UNCATEGORISED
        return Channel(
            id = url,
            name = name,
            group = group,
            logoUrl = attrs["tvg-logo"]?.ifBlank { null },
            streamUrl = url,
            epgId = attrs["tvg-id"]?.ifBlank { null },
        )
    }
}
