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
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Fallback transcription via the Soniox async speech-to-text API.
 *
 * Flow: upload the audio to Soniox storage, submit an async transcription job, poll until it
 * finishes, then fetch the text. Upload and job are deleted again afterwards — Soniox keeps
 * both until they are explicitly removed, so the audio must not be left behind on their side.
 */
object SonioxClient {
    /** Overridable only so tests can point at a [okhttp3.mockwebserver.MockWebServer] instead. */
    internal var baseUrl = "https://api.soniox.com/v1"
    private const val MODEL = "stt-async-v5"

    /** Overridable so tests don't have to wait out real polling/timeout delays. */
    internal var pollIntervalMs = 1000L
    internal var timeoutMs = 120_000L

    /**
     * Tags everything this app creates, so [cleanUpLeftovers] can tell our uploads apart from
     * anything else living in the same Soniox project (Playground files, other integrations).
     */
    private const val CLIENT_REF = "audio-transkript-app"

    /** Leftovers are only swept once they are far past [timeoutMs], never while still in flight. */
    private const val LEFTOVER_MIN_AGE_MS = 10 * 60 * 1000L

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
                transcriptionId?.let { delete("$baseUrl/transcriptions/$it", apiKey) }
                delete("$baseUrl/files/$fileId", apiKey)
            }
        }
    }

    /**
     * Removes uploads and jobs that a previous run failed to clean up — the app process being
     * killed mid-transcription, or a DELETE that never made it out.
     *
     * Deliberately narrow: an API key belongs to exactly one Soniox project, so nothing outside
     * that project is reachable in the first place. Within it, only entries tagged with
     * [CLIENT_REF] and older than [minAgeMs] are touched, which leaves Playground files, other
     * integrations sharing the key, and this app's own in-flight transcription alone.
     *
     * Returns the number of deleted entries. Best effort: any failure just leaves the leftover
     * for the next attempt.
     */
    suspend fun cleanUpLeftovers(
        apiKey: String,
        minAgeMs: Long = LEFTOVER_MIN_AGE_MS,
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - minAgeMs
        var deleted = 0

        // Jobs first: a transcription still referencing a file cannot be deleted while it runs.
        for (job in listAll("transcriptions", apiKey)) {
            val status = job.optString("status")
            if (status != "completed" && status != "error") continue
            if (!isOurs(job, cutoff)) continue
            if (delete("$baseUrl/transcriptions/${job.optString("id")}", apiKey)) deleted++
        }

        for (file in listAll("files", apiKey)) {
            if (!isOurs(file, cutoff)) continue
            if (delete("$baseUrl/files/${file.optString("id")}", apiKey)) deleted++
        }

        deleted
    }

    /** True only for entries this app created and that are old enough to not be in flight. */
    private fun isOurs(entry: JSONObject, cutoff: Long): Boolean {
        if (entry.optString("client_reference_id") != CLIENT_REF) return false
        val createdAt = parseTimestamp(entry.optString("created_at")) ?: return false
        return createdAt < cutoff
    }

    /** Soniox timestamps are ISO-8601 UTC, e.g. `2026-08-04T05:16:53.645Z`. */
    private fun parseTimestamp(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun listAll(collection: String, apiKey: String): List<JSONObject> {
        val all = mutableListOf<JSONObject>()
        var cursor: String? = null

        do {
            val url = "$baseUrl/$collection?limit=1000" + (cursor?.let { "&cursor=$it" } ?: "")
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val page = runCatching {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
                }
            }.getOrNull() ?: return all

            val items = page.optJSONArray(collection) ?: return all
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { all += it }
            }
            cursor = page.optString("next_page_cursor").ifBlank { null }
        } while (cursor != null)

        return all
    }

    private fun uploadAudio(payload: AudioPayload, apiKey: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                payload.filename,
                payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull()),
            )
            .addFormDataPart("client_reference_id", CLIENT_REF)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/files")
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
            put("client_reference_id", CLIENT_REF)
            // No hint means Soniox detects the language itself.
            if (languageCode.isNotBlank()) put("language_hints", JSONArray().put(languageCode))
        }

        val req = Request.Builder()
            .url("$baseUrl/transcriptions")
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
        val deadline = System.currentTimeMillis() + timeoutMs

        while (true) {
            val req = Request.Builder()
                .url("$baseUrl/transcriptions/$transcriptionId")
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
                    delay(pollIntervalMs)
                }
                else -> throw WizperException("Unerwarteter Status: $status")
            }
        }
    }

    private fun fetchTranscript(transcriptionId: String, apiKey: String): String {
        val req = Request.Builder()
            .url("$baseUrl/transcriptions/$transcriptionId/transcript")
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

    /** Returns true when the entry is gone; failures are swallowed and simply retried later. */
    private fun delete(url: String, apiKey: String): Boolean = runCatching {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .delete()
            .build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Soniox reports errors as JSON with a readable `message`; fall back to the raw body. */
    private fun describe(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return if (message.isNullOrBlank()) "HTTP $code: $body" else "HTTP $code: $message"
    }
}
