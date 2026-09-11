package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri

/** Raised for any user-facing transcription failure. */
class WizperException(message: String) : Exception(message)

/**
 * Orchestrates transcription and walks the configured providers in order of preference:
 * OpenRouter (MAI-Transcribe-2) first, then Groq, then the Soniox async API. Each step is
 * skipped when its key is missing, so a partially configured app still works.
 */
object WizperClient {

    suspend fun transcribe(
        context: Context,
        audioUri: Uri,
        openRouterApiKey: String,
        groqApiKey: String,
        sonioxApiKey: String,
        languageCode: String,
        onSonioxJob: (String) -> Unit = {},
    ): String {
        if (openRouterApiKey.isBlank() && groqApiKey.isBlank() && sonioxApiKey.isBlank()) {
            throw WizperException("Kein API-Key gesetzt. Bitte in den Einstellungen eintragen.")
        }

        val payload = readAudio(context, audioUri)
        val errors = mutableListOf<String>()

        if (openRouterApiKey.isNotBlank()) {
            try {
                return OpenRouterClient.transcribe(payload, openRouterApiKey, languageCode)
            } catch (e: Exception) {
                errors += "MAI-Transcribe-2 (OpenRouter) fehlgeschlagen: ${e.message}"
            }
        }

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
