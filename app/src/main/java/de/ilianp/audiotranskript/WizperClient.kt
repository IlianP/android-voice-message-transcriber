package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

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
        // Checked before the file is touched, so a missing key reports itself as such rather
        // than as whatever reading the audio happens to run into.
        requireAnyKey(openRouterApiKey, groqApiKey, sonioxApiKey)
        return transcribe(
            readAudio(context, audioUri),
            openRouterApiKey,
            groqApiKey,
            sonioxApiKey,
            languageCode,
            onSonioxJob,
        )
    }

    /**
     * Transcribes audio that has already been read.
     *
     * The caller keeps the [AudioPayload] because the bytes are needed twice: once for the
     * upload, and once for the copy [TranscriptHistory] keeps so the message stays playable
     * after the shared URI's grant is gone.
     */
    suspend fun transcribe(
        payload: AudioPayload,
        openRouterApiKey: String,
        groqApiKey: String,
        sonioxApiKey: String,
        languageCode: String,
        onSonioxJob: (String) -> Unit = {},
    ): String {
        requireAnyKey(openRouterApiKey, groqApiKey, sonioxApiKey)

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
     * Transcribes a batch of messages - several voice messages in a row from the same chat - and
     * returns one transcript per message, in the order given.
     *
     * One request per message rather than one for stitched-together audio: the files may not
     * even share a codec, and cutting audio on the device buys nothing the caller cannot get by
     * joining the texts. The requests run side by side (at most [MAX_PARALLEL] at a time, to
     * stay clear of provider rate limits), each walking the provider chain on its own. One
     * message failing fails the batch, naming the message, and cancels the rest.
     *
     * [onProgress] reports how many messages are done so far.
     */
    suspend fun transcribeAll(
        payloads: List<AudioPayload>,
        openRouterApiKey: String,
        groqApiKey: String,
        sonioxApiKey: String,
        languageCode: String,
        onProgress: (done: Int) -> Unit = {},
        onSonioxJob: (String) -> Unit = {},
    ): List<String> {
        requireAnyKey(openRouterApiKey, groqApiKey, sonioxApiKey)
        val done = AtomicInteger(0)
        val permits = Semaphore(MAX_PARALLEL)
        return coroutineScope {
            payloads.mapIndexed { index, payload ->
                async {
                    val text = permits.withPermit {
                        try {
                            transcribe(
                                payload,
                                openRouterApiKey,
                                groqApiKey,
                                sonioxApiKey,
                                languageCode,
                                onSonioxJob,
                            )
                        } catch (e: WizperException) {
                            if (payloads.size == 1) throw e
                            throw WizperException("Nachricht ${index + 1} von ${payloads.size}:\n${e.message}")
                        }
                    }
                    onProgress(done.incrementAndGet())
                    text
                }
            }.awaitAll()
        }
    }

    /** Joins a batch's transcripts into the one text that is copied or shared. */
    fun joinTranscripts(texts: List<String>): String = texts.joinToString("\n\n")

    /** How many messages of a batch are sent to a provider at the same time. */
    private const val MAX_PARALLEL = 3

    private fun requireAnyKey(openRouterApiKey: String, groqApiKey: String, sonioxApiKey: String) {
        if (openRouterApiKey.isBlank() && groqApiKey.isBlank() && sonioxApiKey.isBlank()) {
            throw WizperException("Kein API-Key gesetzt. Bitte in den Einstellungen eintragen.")
        }
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
