package de.ilianp.audiotranskript

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny persisted log of Soniox jobs, shown only in debug builds so it can be verified that
 * every upload and transcription is cleaned up again after the text has been fetched.
 */
object DebugLog {
    private const val PREFS = "debug_log"
    private const val KEY = "soniox_jobs"
    private const val MAX_ENTRIES = 20
    private val TS = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

    fun addSonioxJob(context: Context, info: String) {
        if (!BuildConfig.DEBUG) return
        val now = TS.format(Date())
        Log.d("AudioTranskript", "soniox job @ $now -> $info")

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entry = "$now (wird nach Abruf gelöscht)\n$info"
        val old = prefs.getString(KEY, "").orEmpty()
        val entries = (listOf(entry) + old.split("\n\n").filter { it.isNotBlank() }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, entries.joinToString("\n\n")).apply()
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
