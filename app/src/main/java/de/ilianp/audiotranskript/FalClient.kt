package de.ilianp.audiotranskript

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fallback transcription via the fal.ai Wizper queue.
 *
 * Flow: obtain a short-lived upload token, upload the audio to fal storage (auto-expires
 * after [EXPIRATION_SECONDS]), submit a Wizper job, poll until done, then fetch the text.
 */
object FalClient {
    private const val QUEUE = "https://queue.fal.run/fal-ai/wizper"
    private const val REST = "https://rest.fal.ai"
    private const val EXPIRATION_SECONDS = 300
    private const val POLL_INTERVAL_MS = 1500L
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
        onUpload: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val audioUrl = uploadAudio(payload, apiKey)
        onUpload(audioUrl)
        val requestId = submit(audioUrl, apiKey, languageCode)
        pollUntilDone(requestId, apiKey)
        fetchResult(requestId, apiKey)
    }

    private fun uploadAudio(payload: AudioPayload, apiKey: String): String {
        val tokenReq = Request.Builder()
            .url("$REST/storage/auth/token?storage_type=fal-cdn-v3")
            .addHeader("Authorization", "Key $apiKey")
            .addHeader("Accept", "application/json")
            .post("{}".toRequestBody(JSON))
            .build()

        val (token, tokenType, baseUrl) = http.newCall(tokenReq).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("Upload-Token HTTP ${resp.code}: $text")
            val o = JSONObject(text)
            Triple(
                o.optString("token"),
                o.optString("token_type").ifBlank { "Bearer" },
                o.optString("base_url"),
            )
        }

        if (token.isBlank() || baseUrl.isBlank()) {
            throw WizperException("Ungültige Upload-Token-Antwort.")
        }

        val lifecycle = JSONObject().put("expiration_duration_seconds", EXPIRATION_SECONDS).toString()
        val uploadReq = Request.Builder()
            .url("$baseUrl/files/upload")
            .addHeader("Authorization", "$tokenType $token")
            .addHeader("X-Fal-File-Name", payload.filename)
            .addHeader("X-Fal-Object-Lifecycle-Preference", lifecycle)
            .post(payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull()))
            .build()

        return http.newCall(uploadReq).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("Upload HTTP ${resp.code}: $text")
            JSONObject(text).optString("access_url")
                .ifBlank { throw WizperException("Keine access_url erhalten: $text") }
        }
    }

    private fun submit(audioUrl: String, apiKey: String, languageCode: String): String {
        val body = JSONObject().apply {
            put("audio_url", audioUrl)
            put("task", "transcribe")
            if (languageCode.isNotBlank()) put("language", languageCode) else put("language", JSONObject.NULL)
        }

        val req = Request.Builder()
            .url(QUEUE)
            .addHeader("Authorization", "Key $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("HTTP ${resp.code}: $text")
            JSONObject(text).optString("request_id")
                .ifBlank { throw WizperException("Keine request_id erhalten: $text") }
        }
    }

    private suspend fun pollUntilDone(requestId: String, apiKey: String) {
        val statusUrl = "$QUEUE/requests/$requestId/status"
        val deadline = System.currentTimeMillis() + TIMEOUT_MS

        while (true) {
            val req = Request.Builder()
                .url(statusUrl)
                .addHeader("Authorization", "Key $apiKey")
                .get()
                .build()

            val status = http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw WizperException("Statusabfrage HTTP ${resp.code}: $text")
                JSONObject(text).optString("status")
            }

            when (status) {
                "COMPLETED" -> return
                "IN_QUEUE", "IN_PROGRESS" -> {
                    if (System.currentTimeMillis() > deadline) {
                        throw WizperException("Zeitüberschreitung bei der Transkription.")
                    }
                    delay(POLL_INTERVAL_MS)
                }
                else -> throw WizperException("Unerwarteter Status: $status")
            }
        }
    }

    private fun fetchResult(requestId: String, apiKey: String): String {
        val req = Request.Builder()
            .url("$QUEUE/requests/$requestId")
            .addHeader("Authorization", "Key $apiKey")
            .get()
            .build()

        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("Ergebnis HTTP ${resp.code}: $text")
            JSONObject(text).optString("text").trim()
                .ifBlank { "(Keine Transkription erhalten)" }
        }
    }
}
