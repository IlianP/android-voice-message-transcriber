package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri

/** Raised for any user-facing transcription failure. */
class WizperException(message: String) : Exception(message)

/**
 * Orchestrates transcription: tries Groq first (fast, cheap) and falls back to the
 * Soniox async API if Groq is unavailable or fails.
 */
object WizperClient {

    suspend fun transcribe(
        context: Context,
        audioUri: Uri,
        groqApiKey: String,
        sonioxApiKey: String,
        languageCode: String,
        onSonioxJob: (String) -> Unit = {},
    ): String {
        if (groqApiKey.isBlank() && sonioxApiKey.isBlank()) {
            throw WizperException("Kein API-Key gesetzt. Bitte in den Einstellungen eintragen.")
        }

        val payload = readAudio(context, audioUri)
        val errors = mutableListOf<String>()

        if (groqApiKey.isNotBlank()) {
            try {
                return GroqClient.transcribe(payload, groqApiKey, languageCode)
            } catch (e: Exception) {
                errors += "Groq fehlgeschlagen: ${e.message}"
            }
        }

        if (sonioxApiKey.isNotBlank()) {
            try {
                return SonioxClient.transcribe(payload, sonioxApiKey, languageCode, onSonioxJob)
            } catch (e: Exception) {
                errors += "Soniox-Fallback fehlgeschlagen: ${e.message}"
            }
        }

        throw WizperException(errors.joinToString("\n\n"))
    }
}
