package io.github.tellyteam.telly.data.store

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters

/**
 * Subtitles start off, whatever the file says: many provider files flag a subtitle track as
 * "default", which players otherwise obey. Forced subtitles (foreign-language lines) still show.
 * Choosing a subtitle language makes it the preference for every film and episode until "Off".
 */
class SubtitlePreference(context: Context) {
    private val prefs = context.getSharedPreferences("subtitles", Context.MODE_PRIVATE)

    /** The chosen language (e.g. "en"), [UNDETERMINED] for unlabelled tracks, or null for off. */
    var language: String?
        get() = prefs.getString(KEY, null)
        set(value) = prefs.edit().putString(KEY, value).apply()

    companion object {
        private const val KEY = "language"
        const val UNDETERMINED = "und"

        /** Track selection for [language]: off unless chosen, never from the file's "default" flag. */
        fun parameters(base: TrackSelectionParameters, language: String?): TrackSelectionParameters =
            base.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setPreferredTextLanguage(language?.takeIf { it != UNDETERMINED })
                .setSelectUndeterminedTextLanguage(language == UNDETERMINED)
                .build()
    }
}
