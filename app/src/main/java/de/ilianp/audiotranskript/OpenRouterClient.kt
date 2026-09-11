package de.ilianp.audiotranskript

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Primary transcription via OpenRouter's speech-to-text endpoint, running Microsoft's
 * MAI-Transcribe-2.
 *
 * OpenRouter bills the model at Microsoft's own list price, so this is the same rate as calling
 * Azure directly, but without needing an Azure subscription and Foundry resource. The audio is
 * sent inline as base64 in a single JSON request — no upload/poll/delete cycle like Soniox, and
 * nothing is left behind on the provider side.
 */
object OpenRouterClient {
    /** Overridable only so tests can point at a [okhttp3.mockwebserver.MockWebServer] instead. */
    internal var baseUrl = "https://openrouter.ai/api/v1"
    private const val MODEL = "microsoft/mai-transcribe-2"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * OpenRouter accepts a fixed set of format names. Our extensions are broader (they follow
     * what Groq's multipart endpoint takes), so map the extras onto the container that actually
     * carries them.
     */
    /**
     * Formats that the file extension alone cannot express. [readAudio] renames a raw AAC file
     * to `audio.m4a`, because `aac` is not one of the extensions Groq's endpoint takes - but
     * those bytes are ADTS AAC, not an MP4 container, and OpenRouter is told the format outright
     * rather than sniffing it. So the MIME type has the last word where the two disagree.
     */
    private val MIME_TO_FORMAT = mapOf(
        "audio/aac" to "aac",
    )

    private val EXT_TO_FORMAT = mapOf(
        "flac" to "flac",
        "mp3" to "mp3",
        "mpeg" to "mp3",
        "mpga" to "mp3",
        "mp4" to "m4a",
        "m4a" to "m4a",
        "aac" to "aac",
        "ogg" to "ogg",
        "opus" to "ogg",
        "wav" to "wav",
        "webm" to "webm",
    )

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
        val body = JSONObject().apply {
            put("model", MODEL)
            put(
                "input_audio",
                JSONObject().apply {
                    put("data", Base64.getEncoder().encodeToString(payload.bytes))
                    put("format", formatOf(payload))
                },
            )
            if (languageCode.isNotBlank()) put("language", languageCode)
        }

        val request = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw WizperException("HTTP ${resp.code}: ${errorOf(raw, raw)}")

            val json = runCatching { JSONObject(raw) }
                .getOrElse { throw WizperException("Unerwartete Antwort: $raw") }
            // OpenRouter also reports upstream failures as HTTP 200 with an `error` object.
            json.optJSONObject("error")?.let { throw WizperException(errorOf(raw, raw)) }

            json.optString("text").trim().ifBlank { "(Keine Transkription erhalten)" }
        }
    }

    private fun formatOf(payload: AudioPayload): String {
        MIME_TO_FORMAT[payload.mimeType.lowercase(Locale.ROOT)]?.let { return it }
        val ext = payload.filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return EXT_TO_FORMAT[ext] ?: "ogg"
    }

    /** Pulls `error.message` out of an OpenRouter error body, falling back to [fallback]. */
    private fun errorOf(raw: String, fallback: String): String = runCatching {
        JSONObject(raw).getJSONObject("error").getString("message")
    }.getOrDefault(fallback)
}
