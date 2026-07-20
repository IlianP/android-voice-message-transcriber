package de.ilianp.audiotranskript

import android.content.Context

/** A selectable transcription language. An empty [code] means "auto-detect". */
data class LanguageOption(val label: String, val code: String)

val LANGUAGE_OPTIONS = listOf(
    LanguageOption("Automatisch erkennen", ""),
    LanguageOption("Deutsch", "de"),
    LanguageOption("Englisch", "en"),
)

/** Thin wrapper around SharedPreferences for the app's persisted settings. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var groqApiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("api_key", value.trim()).apply()
        }

    var falApiKey: String
        get() = prefs.getString("fal_api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("fal_api_key", value.trim()).apply()
        }

    var languageCode: String
        get() = prefs.getString("language", "") ?: ""
        set(value) {
            prefs.edit().putString("language", value).apply()
        }
}
