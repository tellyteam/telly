package io.github.tellyteam.telly.data.guide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.data.net.fetch
import io.github.tellyteam.telly.data.xtream.XtreamParser
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

data class Programme(val title: String, val description: String?, val startMs: Long, val endMs: Long) {
    fun progress(nowMs: Long): Float = ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

data class NowNext(val now: Programme?, val next: Programme?)

/** Parsing for Xtream's `get_short_epg`; titles and descriptions usually arrive base64-encoded. */
object GuideParser {

    fun programmes(root: JsonElement): List<Programme> {
        val listings = (root as? JsonObject)?.get("epg_listings") as? JsonArray ?: return emptyList()
        return listings.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val start = o.long("start_timestamp") ?: return@mapNotNull null
            val end = o.long("stop_timestamp") ?: o.long("end_timestamp") ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val title = o.text("title")?.let(::maybeBase64)?.trim()?.ifBlank { null } ?: return@mapNotNull null
            Programme(title, o.text("description")?.let(::maybeBase64)?.trim()?.ifBlank { null }, start * 1000, end * 1000)
        }.sortedBy { it.startMs }
    }

    fun nowNext(programmes: List<Programme>, nowMs: Long): NowNext {
        val i = programmes.indexOfFirst { nowMs >= it.startMs && nowMs < it.endMs }
        return if (i >= 0) {
            NowNext(programmes[i], programmes.getOrNull(i + 1))
        } else {
            NowNext(null, programmes.firstOrNull { it.startMs > nowMs })
        }
    }

    /** Decodes base64 if the text is valid base64 of readable UTF-8; otherwise returns it as-is. */
    fun maybeBase64(text: String): String {
        val decoded = Base64Text.decode(text.trim()) ?: return text
        val s = decoded.toString(Charsets.UTF_8)
        val readable = s.isNotEmpty() && s.count { it == '�' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') } == 0
        return if (readable) s else text
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String) = text(key)?.trim()?.toLongOrNull()
}

/** Minimal standard-alphabet base64 decoder (java.util.Base64 needs Android 8; Fire OS 6 is 7.1). */
internal object Base64Text {
    private val table = IntArray(128) { -1 }.also { t ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> t[c.code] = i }
    }

    fun decode(s: String): ByteArray? {
        val clean = s.trimEnd('=')
        if (clean.isEmpty() || s.length % 4 != 0) return null
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = if (c.code < 128) table[c.code] else -1
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

/**
 * Now/next for live channels, fetched per channel on demand (Xtream `get_short_epg`) rather than
 * downloading a full XMLTV guide, which is far too large for a TV stick. Results are kept in memory
 * until the current programme ends; at most four requests run at once.
 */
class GuideRepository(private val http: OkHttpClient) {
    private class Entry(val programmes: List<Programme>, val validUntilMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val gate = Semaphore(4)

    /** What is cached right now, without any network call. */
    fun cached(channel: Channel, nowMs: Long = System.currentTimeMillis()): NowNext? =
        cache[channel.id]?.takeIf { nowMs < it.validUntilMs }?.let { GuideParser.nowNext(it.programmes, nowMs) }

    suspend fun nowNext(provider: Provider, channel: Channel): NowNext? {
        cached(channel)?.let { return it }
        val x = provider as? Provider.Xtream ?: return null // M3U guides need XMLTV; not supported yet
        return gate.withPermit {
            cached(channel) ?: runCatching { fetch(x, channel) }.getOrNull()
        }
    }

    private suspend fun fetch(p: Provider.Xtream, channel: Channel): NowNext = withContext(Dispatchers.IO) {
        val url = XtreamParser.apiUrl(
            XtreamParser.serverUrl(p.server), p.username, p.password, "get_short_epg",
            mapOf("stream_id" to channel.id, "limit" to "6"),
        )
        val programmes = http.fetch(url).use { r -> GuideParser.programmes(Json.parseToJsonElement(r.body!!.string())) }
        val now = System.currentTimeMillis()
        val current = GuideParser.nowNext(programmes, now)
        // Valid until the current programme ends (or 20 minutes when the guide is empty).
        val until = current.now?.endMs ?: current.next?.startMs ?: (now + 20 * 60_000)
        cache[channel.id] = Entry(programmes, until.coerceAtMost(now + 2 * 60 * 60_000))
        current
    }
}
