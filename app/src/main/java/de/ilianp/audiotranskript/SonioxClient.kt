package de.ilianp.audiotranskript

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fallback transcription via the Soniox async speech-to-text API.
 *
 * Flow: upload the audio to Soniox storage, submit an async transcription job, poll until it
 * finishes, then fetch the text. Upload and job are deleted again afterwards — Soniox keeps
 * both until they are explicitly removed, so the audio must not be left behind on their side.
 */
object SonioxClient {
    private const val BASE = "https://api.soniox.com/v1"
    private const val MODEL = "stt-async-v5"
    private const val POLL_INTERVAL_MS = 1000L
    private const val TIMEOUT_MS = 120_000L

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        payload: AudioPayload,
        apiKey: String,
        languageCode: String,
        onJob: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val fileId = uploadAudio(payload, apiKey)
        var transcriptionId: String? = null
        try {
            val id = createTranscription(fileId, apiKey, languageCode)
            transcriptionId = id
            onJob("file $fileId\njob $id")
            pollUntilDone(id, apiKey)
            fetchTranscript(id, apiKey)
        } finally {
            // Best effort: never let cleanup failures mask the real result or error.
            withContext(NonCancellable) {
                transcriptionId?.let { delete("$BASE/transcriptions/$it", apiKey) }
                delete("$BASE/files/$fileId", apiKey)
            }
        }
    }

    private fun uploadAudio(payload: AudioPayload, apiKey: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                payload.filename,
                payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull()),
            )
            .build()

        val req = Request.Builder()
            .url("$BASE/files")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("Upload ${describe(resp.code, text)}")
            JSONObject(text).optString("id")
                .ifBlank { throw WizperException("Keine Datei-ID erhalten: $text") }
        }
    }

    private fun createTranscription(fileId: String, apiKey: String, languageCode: String): String {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("file_id", fileId)
            // No hint means Soniox detects the language itself.
            if (languageCode.isNotBlank()) put("language_hints", JSONArray().put(languageCode))
        }

        val req = Request.Builder()
            .url("$BASE/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException(describe(resp.code, text))
            JSONObject(text).optString("id")
                .ifBlank { throw WizperException("Keine Transkriptions-ID erhalten: $text") }
        }
    }

    private suspend fun pollUntilDone(transcriptionId: String, apiKey: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS

        while (true) {
            val req = Request.Builder()
                .url("$BASE/transcriptions/$transcriptionId")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val job = http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw WizperException("Statusabfrage ${describe(resp.code, text)}")
                JSONObject(text)
            }

            when (val status = job.optString("status")) {
                "completed" -> return
                "error" -> throw WizperException(
                    job.optString("error_message").ifBlank { "Transkription fehlgeschlagen." },
                )
                "queued", "processing" -> {
                    if (System.currentTimeMillis() > deadline) {
                        throw WizperException("Zeitüberschreitung bei der Transkription.")
                    }
                    delay(POLL_INTERVAL_MS)
                }
                else -> throw WizperException("Unerwarteter Status: $status")
            }
        }
    }

    private fun fetchTranscript(transcriptionId: String, apiKey: String): String {
        val req = Request.Builder()
            .url("$BASE/transcriptions/$transcriptionId/transcript")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("Ergebnis ${describe(resp.code, text)}")
            JSONObject(text).optString("text").trim()
                .ifBlank { "(Keine Transkription erhalten)" }
        }
    }

    private fun delete(url: String, apiKey: String) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            http.newCall(req).execute().close()
        }
    }

    /** Soniox reports errors as JSON with a readable `message`; fall back to the raw body. */
    private fun describe(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return if (message.isNullOrBlank()) "HTTP $code: $body" else "HTTP $code: $message"
    }
}
