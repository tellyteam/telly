package io.github.tellyteam.telly.data.store

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePreferenceTest {
    @Suppress("DEPRECATION")
    private val base = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()

    @Test
    fun `off ignores the file's default flag but keeps forced subtitles possible`() {
        val p = SubtitlePreference.parameters(base, null)
        assertEquals(C.SELECTION_FLAG_DEFAULT, p.ignoredTextSelectionFlags)
        assertTrue(p.preferredTextLanguages.isEmpty())
        assertFalse(C.TRACK_TYPE_TEXT in p.disabledTrackTypes)
    }

    @Test
    fun `unlabelled subtitles can be chosen too`() {
        // (A language like "en" goes through Media3's language normalising, which needs a device.)
        val p = SubtitlePreference.parameters(base, SubtitlePreference.UNDETERMINED)
        assertTrue(p.selectUndeterminedTextLanguage)
        assertTrue(p.preferredTextLanguages.isEmpty())
        assertEquals(C.SELECTION_FLAG_DEFAULT, p.ignoredTextSelectionFlags)
    }
}
