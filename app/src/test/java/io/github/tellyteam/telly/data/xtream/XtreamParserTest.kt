package io.github.tellyteam.telly.data.xtream

import kotlinx.serialization.json.Json
import io.github.tellyteam.telly.data.Details
import io.github.tellyteam.telly.data.UNCATEGORISED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class XtreamParserTest {

    private val server = XtreamParser.serverUrl("https://tv.example:8080")
    private fun stream(text: String) = text.trimIndent().byteInputStream()

    @Test
    fun `server url defaults to https and tolerates trailing slash`() {
        assertEquals("https://example.com:8080/", XtreamParser.serverUrl("example.com:8080/").toString())
        assertEquals("http://example.com/", XtreamParser.serverUrl(" http://example.com ").toString())
    }

    @Test
    fun `api url encodes credentials and extra parameters`() {
        val url = XtreamParser.apiUrl(
            XtreamParser.serverUrl("https://example.com:8080/"), "me", "p&ss word", "get_series_info", mapOf("series_id" to "9"),
        )
        assertEquals(
            "https://example.com:8080/player_api.php?username=me&password=p%26ss%20word&action=get_series_info&series_id=9",
            url.toString(),
        )
    }

    @Test
    fun `account reads auth flag and formats`() {
        val ok = XtreamParser.account(
            Json.parseToJsonElement("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["m3u8","ts"]}}"""),
        )
        assertTrue(ok.authenticated)
        assertEquals("m3u8", XtreamParser.preferredFormat(ok.outputFormats))

        assertFalse(XtreamParser.account(Json.parseToJsonElement("""{"user_info":{"auth":0}}""")).authenticated)
        assertFalse(XtreamParser.account(Json.parseToJsonElement("[]")).authenticated)
        assertEquals("ts", XtreamParser.preferredFormat(listOf("ts")))
    }

    @Test
    fun `categories keep provider order and accept numeric ids`() {
        val categories = XtreamParser.categories(stream("""[{"category_id":"7","category_name":"News"},{"category_id":3,"category_name":" Sport "}]"""))
        assertEquals(listOf("7" to "News", "3" to "Sport"), categories.toList())
    }

    @Test
    fun `live channels map categories and build stream urls`() {
        val channels = XtreamParser.liveChannels(
            server, "me", "secret", "m3u8", mapOf("7" to "News"),
            stream(
                """
                [
                  {"name":"Channel A","stream_id":101,"stream_icon":"https://img/a.png","epg_channel_id":"a.uk","category_id":"7","num":1,"tv_archive":0},
                  {"name":"Channel B","stream_id":"102","stream_icon":"","epg_channel_id":null,"category_id":null},
                  {"name":"No id"}
                ]
                """,
            ),
        )

        assertEquals(2, channels.size)
        val (a, b) = channels
        assertEquals("News", a.group)
        assertEquals("https://tv.example:8080/live/me/secret/101.m3u8", a.streamUrl)
        assertEquals("a.uk", a.epgId)
        assertEquals(UNCATEGORISED, b.group)
        assertNull(b.logoUrl)
        assertNull(b.epgId)
    }

    @Test
    fun `films use their container extension`() {
        val films = XtreamParser.films(
            server, "me", "secret", mapOf("1" to "Action"),
            stream("""[{"stream_id":55,"name":"Film","stream_icon":"https://img/p.jpg","category_id":"1","container_extension":"mkv","rating":7.5,"category_ids":[1]}]"""),
        )
        assertEquals("https://tv.example:8080/movie/me/secret/55.mkv", films.single().streamUrl)
        assertEquals("Action", films.single().group)
    }

    @Test
    fun `series list reads cover and plot`() {
        val series = XtreamParser.series(
            mapOf("2" to "Drama"),
            stream("""[{"series_id":9,"name":"Show","cover":"https://img/c.jpg","plot":"A plot.","category_id":"2","backdrop_path":["x"]}]"""),
        ).single()
        assertEquals("9", series.id)
        assertEquals("Drama", series.group)
        assertEquals("https://img/c.jpg", series.coverUrl)
    }

    @Test
    fun `seasons parse object-keyed episodes in numeric order`() {
        val info = Json.parseToJsonElement(
            """
            {"episodes":{
              "10":[{"id":"1001","episode_num":1,"title":"Late","container_extension":"mp4","info":[]}],
              "2":[
                {"id":"201","episode_num":1,"title":"Pilot","container_extension":"mkv","info":{"movie_image":"https://img/e.jpg"}},
                {"id":"202","episode_num":"2","title":"Second","container_extension":"mkv"}
              ]
            }}
            """,
        )
        val seasons = XtreamParser.seasons(server, "me", "secret", info)
        assertEquals(listOf("Season 2", "Season 10"), seasons.map { it.name })
        val pilot = seasons[0].episodes[0]
        assertEquals("E1  Pilot", pilot.name)
        assertEquals("https://tv.example:8080/series/me/secret/201.mkv", pilot.streamUrl)
        assertEquals("https://img/e.jpg", pilot.logoUrl)
        assertEquals(1, seasons[1].episodes.size) // `info: []` must not drop the episode
    }

    @Test
    fun `details read film and series info leniently`() {
        val film = XtreamParser.details(
            Json.parseToJsonElement(
                """{"info":{"plot":" A heist. ","genre":"Thriller","releasedate":"2024-03-01","rating":"7.43","duration":"01:52:00","backdrop_path":["https://img/b.jpg"]}}""",
            ),
        )
        assertEquals("A heist.", film.plot)
        assertEquals("2024", film.year)
        assertEquals("7.4", film.rating)
        assertEquals("1h 52m", film.runtime)
        assertEquals("https://img/b.jpg", film.backdropUrl)

        val series = XtreamParser.details(
            Json.parseToJsonElement("""{"info":{"releaseDate":"2020","rating_5based":4.1,"episode_run_time":"45","backdrop_path":[],"cover_big":"https://img/c.jpg"}}"""),
        )
        assertEquals("8.2", series.rating)
        assertEquals("45 min", series.runtime)
        assertEquals("https://img/c.jpg", series.backdropUrl)
        assertEquals(Details(), XtreamParser.details(Json.parseToJsonElement("[]")))
    }

    @Test
    fun `episode titles drop the repeated show name and code`() {
        assertEquals("Being Alive", XtreamParser.episodeTitle("Ted Lasso - S04E10 - Being Alive"))
        assertEquals("Pilot", XtreamParser.episodeTitle("Show: S1 E1 | Pilot"))
        assertEquals("The Letters", XtreamParser.episodeTitle("The Letters"))
        assertEquals("Show - S02E03", XtreamParser.episodeTitle("Show - S02E03"))
    }

    @Test
    fun `seasons also accept a list of lists`() {
        val info = Json.parseToJsonElement("""{"episodes":[[{"id":"1","season":1,"episode_num":1,"title":"A"}]]}""")
        assertEquals("Season 1", XtreamParser.seasons(server, "me", "secret", info).single().name)
    }

    @Test(expected = IOException::class)
    fun `an object instead of a list is a clear error`() {
        XtreamParser.films(server, "me", "secret", emptyMap(), stream("""{"user_info":{"auth":0}}"""))
    }
}
