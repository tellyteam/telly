package io.github.tellyteam.telly.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import io.github.tellyteam.telly.data.m3u.M3uParser
import io.github.tellyteam.telly.data.net.fetch
import io.github.tellyteam.telly.data.store.CatalogCache
import io.github.tellyteam.telly.data.xtream.XtreamParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InputStream

/** Where a catalogue comes from: the copy saved last time, or a fresh download (which updates that copy). */
enum class Source { CACHE, NETWORK }

/**
 * Loads catalogues from the user's provider. This is the only place provider data is fetched.
 * Every method returns null for [Source.CACHE] when nothing has been saved yet.
 */
class ChannelRepository(private val http: OkHttpClient, private val cache: CatalogCache) {

    suspend fun liveGroups(provider: Provider, source: Source): List<ChannelGroup>? = withContext(Dispatchers.IO) {
        val groups = when (provider) {
            is Provider.Xtream -> {
                val x = XtreamSession(provider, source)
                val format = x.format() ?: return@withContext null
                val categories = x.categories("get_live_categories") ?: return@withContext null
                val channels = x.list("get_live_streams") {
                    XtreamParser.liveChannels(x.server, provider.username, provider.password, format, categories, it)
                } ?: return@withContext null
                groupInOrder(channels, categories.values.toList(), Channel::group, ::ChannelGroup)
            }
            is Provider.M3u -> {
                val url = provider.url.trim().toHttpUrl()
                val scope = CatalogCache.scope("m3u", url.toString())
                val parse = { input: InputStream -> input.bufferedReader().useLines { M3uParser.parse(it) } }
                val channels = when (source) {
                    Source.CACHE -> cache.read(scope, "playlist", parse) ?: return@withContext null
                    Source.NETWORK -> download(url, scope, "playlist", parse)
                }
                // groupBy keeps first-seen order, which matches the playlist's own ordering.
                channels.groupBy { it.group }.map { (name, list) -> ChannelGroup(name, list) }
            }
        }
        if (groups.isEmpty() && source == Source.NETWORK) throw IOException("The provider returned no channels.")
        groups
    }

    suspend fun filmGroups(provider: Provider.Xtream, source: Source): List<ChannelGroup>? = withContext(Dispatchers.IO) {
        val x = XtreamSession(provider, source)
        val categories = x.categories("get_vod_categories") ?: return@withContext null
        val films = x.list("get_vod_streams") {
            XtreamParser.films(x.server, provider.username, provider.password, categories, it)
        } ?: return@withContext null
        groupInOrder(films, categories.values.toList(), Channel::group, ::ChannelGroup)
    }

    suspend fun seriesGroups(provider: Provider.Xtream, source: Source): List<SeriesGroup>? = withContext(Dispatchers.IO) {
        val x = XtreamSession(provider, source)
        val categories = x.categories("get_series_categories") ?: return@withContext null
        val series = x.list("get_series") { XtreamParser.series(categories, it) } ?: return@withContext null
        groupInOrder(series, categories.values.toList(), Series::group, ::SeriesGroup)
    }

    suspend fun seriesData(provider: Provider.Xtream, series: Series, source: Source): SeriesData? = withContext(Dispatchers.IO) {
        val x = XtreamSession(provider, source)
        x.list("get_series_info", mapOf("series_id" to series.id), cacheName = "series_info_${series.id}") { input ->
            val info = parseJson(input)
            SeriesData(XtreamParser.details(info), XtreamParser.seasons(x.server, provider.username, provider.password, info))
        }
    }

    suspend fun filmDetails(provider: Provider.Xtream, film: Channel, source: Source): Details? = withContext(Dispatchers.IO) {
        val x = XtreamSession(provider, source)
        x.list("get_vod_info", mapOf("vod_id" to film.id), cacheName = "vod_info_${film.id}") { XtreamParser.details(parseJson(it)) }
    }

    /** The subscription's details (end date, connections), straight from the provider. */
    suspend fun account(provider: Provider.Xtream): XtreamParser.Account = withContext(Dispatchers.IO) {
        val url = XtreamParser.apiUrl(XtreamParser.serverUrl(provider.server), provider.username, provider.password)
        http.fetch(url).use { r -> XtreamParser.account(parseJson(r.body!!.byteStream())) }
    }

    /** Forgets every saved catalogue; the next load downloads afresh. */
    suspend fun clearSavedCatalogues() = withContext(Dispatchers.IO) { cache.clearAll() }

    private inner class XtreamSession(private val p: Provider.Xtream, private val source: Source) {
        val server: HttpUrl = XtreamParser.serverUrl(p.server)
        private val scope = CatalogCache.scope("xtream", server.toString(), p.username)

        /** Stream format for live channels; logging in when online (which also checks the account). */
        fun format(): String? = when (source) {
            Source.CACHE -> cache.readText(scope, "format")
            Source.NETWORK -> {
                val account = fetch(apiUrl(null)) { XtreamParser.account(parseJson(it)) }
                if (!account.authenticated) {
                    throw IOException("Login was rejected" + (account.status?.let { " (status: $it)" } ?: "") + ".")
                }
                XtreamParser.preferredFormat(account.outputFormats).also { cache.writeText(scope, "format", it) }
            }
        }

        fun categories(action: String): Map<String, String>? = list(action, parse = XtreamParser::categories)

        fun <T> list(
            action: String,
            extra: Map<String, String> = emptyMap(),
            cacheName: String = action,
            parse: (InputStream) -> T,
        ): T? = when (source) {
            Source.CACHE -> cache.read(scope, cacheName, parse)
            Source.NETWORK -> download(apiUrl(action, extra), scope, cacheName, parse)
        }

        private fun apiUrl(action: String?, extra: Map<String, String> = emptyMap()) =
            XtreamParser.apiUrl(server, p.username, p.password, action, extra)
    }

    /** Downloads to the cache, replacing the saved copy only if the new one parses. */
    private fun <T> download(url: HttpUrl, scope: String, name: String, parse: (InputStream) -> T): T =
        http.fetch(url).use { response ->
            cache.replace(scope, name, write = { out -> response.body!!.byteStream().copyTo(out) }, parse = parse)
        }

    private fun <T> fetch(url: HttpUrl, parse: (InputStream) -> T): T =
        http.fetch(url).use { response -> response.body!!.byteStream().use(parse) }

    private fun parseJson(input: InputStream): JsonElement = try {
        Json.parseToJsonElement(input.bufferedReader().readText())
    } catch (e: IllegalArgumentException) {
        throw IOException("The provider did not return valid JSON. Check the server address.", e)
    }
}
