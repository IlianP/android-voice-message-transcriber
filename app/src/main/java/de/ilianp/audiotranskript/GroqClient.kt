package de.ilianp.audiotranskript

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Transcribes audio via Groq's OpenAI-compatible Whisper endpoint. */
object GroqClient {
    /** Overridable only so tests can point at a [okhttp3.mockwebserver.MockWebServer] instead. */
    internal var baseUrl = "https://api.groq.com/openai/v1"
    private const val MODEL = "whisper-large-v3-turbo"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        payload: AudioPayload,
        apiKey: String,
        languageCode: String,
    ): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL)
            .addFormDataPart(
                "file",
                payload.filename,
                payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull()),
            )
            .apply {
                if (languageCode.isNotBlank()) addFormDataPart("language", languageCode)
            }
            .build()

        val request = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("HTTP ${resp.code}: $text")
            JSONObject(text).optString("text").trim()
                .ifBlank { "(Keine Transkription erhalten)" }
        }
    }
}
