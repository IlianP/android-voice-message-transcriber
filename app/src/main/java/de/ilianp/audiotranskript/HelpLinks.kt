package de.ilianp.audiotranskript

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Hilfe- und Datenschutz-Seiten im Browser. Google Play verlangt einen Link zur
 * Datenschutzerklärung in der App selbst, nicht nur im Store-Eintrag.
 *
 * Zeigen vorerst auf die Markdown-Dateien im Repo; sobald GitHub Pages läuft, auf die Pages-URL
 * umstellen (docs/play-store/README.md, A15).
 */
object HelpLinks {
    const val KEY_GUIDE =
        "https://github.com/IlianP/android-voice-message-transcriber/blob/main/docs/anleitung-api-keys.md"
    const val PRIVACY =
        "https://github.com/IlianP/android-voice-message-transcriber/blob/main/docs/datenschutz.md"

    /** Ohne Browser (selten, z. B. Kiosk-Geräte) gibt es einen Hinweis statt eines Absturzes. */
    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Kein Browser gefunden: $url", Toast.LENGTH_LONG).show()
        }
    }
}
