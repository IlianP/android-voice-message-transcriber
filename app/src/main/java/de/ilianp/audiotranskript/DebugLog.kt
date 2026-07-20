package de.ilianp.audiotranskript

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny persisted log of fal.ai uploads, shown only in debug builds so the temporary
 * upload URLs can be checked (they should expire after ~5 minutes).
 */
object DebugLog {
    private const val PREFS = "debug_log"
    private const val KEY = "fal_uploads"
    private const val MAX_ENTRIES = 20
    private val TS = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

    fun addFalUpload(context: Context, url: String) {
        if (!BuildConfig.DEBUG) return
        val now = TS.format(Date())
        Log.d("AudioTranskript", "fal upload @ $now -> $url")

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entry = "$now (+5 Min Ablauf)\n$url"
        val old = prefs.getString(KEY, "").orEmpty()
        val entries = (listOf(entry) + old.split("\n\n").filter { it.isNotBlank() }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, entries.joinToString("\n\n")).apply()
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun latestUrl(context: Context): String? =
        get(context).lineSequence().firstOrNull { it.startsWith("http") }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
