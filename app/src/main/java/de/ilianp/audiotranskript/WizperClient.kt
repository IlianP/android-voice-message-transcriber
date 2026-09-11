package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException

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
            attempt("MAI-Transcribe-2 (OpenRouter)", errors) {
                OpenRouterClient.transcribe(payload, openRouterApiKey, languageCode)
            }?.let { return it }
        }

        if (groqApiKey.isNotBlank()) {
            attempt("Groq", errors) {
                GroqClient.transcribe(payload, groqApiKey, languageCode)
            }?.let { return it }
        }

        if (sonioxApiKey.isNotBlank()) {
            attempt("Soniox-Fallback", errors) {
                SonioxClient.transcribe(payload, sonioxApiKey, languageCode, onSonioxJob)
            }?.let { return it }
        }

        throw WizperException(errors.joinToString("\n\n"))
    }

    /**
     * Runs one provider, returning null once its failure has been recorded so the next one can
     * take over.
     *
     * A cancellation is not such a failure and is rethrown: the blocking HTTP calls only notice
     * that the job is gone once they return, and swallowing that here would walk the remaining
     * providers, report "Abbrechen" as a transcription error, and let the abandoned run write
     * its state over a newer one.
     */
    private suspend fun attempt(
        label: String,
        errors: MutableList<String>,
        block: suspend () -> String,
    ): String? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += "$label fehlgeschlagen: ${e.message}"
        null
    }
}
