package io.github.tellyteam.telly.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    @Test
    fun `reads the version and the telly apk asset`() {
        val release = ReleaseParser.parse(
            Json.parseToJsonElement(
                """
                {"tag_name":"v1.2.0","draft":false,"prerelease":false,"body":"Faster Live TV.",
                 "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                           {"name":"telly.apk","browser_download_url":"https://x/telly.apk"}]}
                """.trimIndent(),
            ),
        )
        assertEquals(Release("1.2.0", "https://x/telly.apk", "Faster Live TV."), release)
    }

    @Test
    fun `ignores pre-releases and releases without the apk`() {
        assertNull(ReleaseParser.parse(Json.parseToJsonElement("""{"tag_name":"v2.0.0","prerelease":true,"assets":[{"name":"telly.apk","browser_download_url":"https://x"}]}""")))
        assertNull(ReleaseParser.parse(Json.parseToJsonElement("""{"tag_name":"v2.0.0","assets":[]}""")))
        assertNull(ReleaseParser.parse(Json.parseToJsonElement("""{"message":"Not Found"}""")))
    }

    @Test
    fun `compares versions numerically`() {
        assertTrue(ReleaseParser.isNewer("1.10.0", "1.9.3"))
        assertTrue(ReleaseParser.isNewer("v2.0", "1.99.99"))
        assertFalse(ReleaseParser.isNewer("1.0.0", "1.0.0"))
        assertFalse(ReleaseParser.isNewer("1.0.0", "1.0.1"))
        assertFalse(ReleaseParser.isNewer("1.0.0-beta", "1.0.0"))
    }
}
