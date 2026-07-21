package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri

/** Raised for any user-facing transcription failure. */
class WizperException(message: String) : Exception(message)

/**
 * Orchestrates transcription: tries Groq first (fast, cheap) and falls back to the
 * fal.ai Wizper queue if Groq is unavailable or fails.
 */
object WizperClient {

    suspend fun transcribe(
        context: Context,
        audioUri: Uri,
        groqApiKey: String,
        falApiKey: String,
        languageCode: String,
        onFalUpload: (String) -> Unit = {},
    ): String {
        if (groqApiKey.isBlank() && falApiKey.isBlank()) {
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

        if (falApiKey.isNotBlank()) {
            try {
                return FalClient.transcribe(payload, falApiKey, languageCode, onFalUpload)
            } catch (e: Exception) {
                errors += "fal.ai-Fallback fehlgeschlagen: ${e.message}"
            }
        }

        throw WizperException(errors.joinToString("\n\n"))
    }
}
