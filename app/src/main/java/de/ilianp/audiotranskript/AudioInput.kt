package de.ilianp.audiotranskript

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Raw audio bytes plus the metadata the transcription APIs need. */
data class AudioPayload(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPayload) return false
        return bytes.contentEquals(other.bytes) &&
            filename == other.filename &&
            mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

private val ALLOWED_EXTENSIONS =
    setOf("flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "opus", "wav", "webm")

private val MIME_TO_EXT = mapOf(
    "audio/flac" to "flac",
    "audio/mpeg" to "mp3",
    "audio/mp3" to "mp3",
    "audio/mp4" to "m4a",
    "audio/m4a" to "m4a",
    "audio/x-m4a" to "m4a",
    "audio/aac" to "m4a",
    "audio/ogg" to "ogg",
    "audio/opus" to "opus",
    "audio/wav" to "wav",
    "audio/x-wav" to "wav",
    "audio/webm" to "webm",
    "video/mp4" to "mp4",
    "video/webm" to "webm",
)

private val EXT_TO_MIME = mapOf(
    "flac" to "audio/flac",
    "mp3" to "audio/mpeg",
    "mpeg" to "audio/mpeg",
    "mpga" to "audio/mpeg",
    "mp4" to "audio/mp4",
    "m4a" to "audio/mp4",
    "ogg" to "audio/ogg",
    "opus" to "audio/ogg",
    "wav" to "audio/wav",
    "webm" to "audio/webm",
)

/** Reads the shared audio [uri] into an [AudioPayload], guessing a sensible filename/MIME. */
fun readAudio(context: Context, uri: Uri): AudioPayload {
    val resolver = context.contentResolver
    val rawType = resolver.getType(uri) ?: ""
    val mime = rawType.substringBefore(';').trim().lowercase(Locale.ROOT)

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw WizperException("Audio-Datei konnte nicht gelesen werden.")

    val nameExt = queryDisplayName(resolver, uri)
        ?.substringAfterLast('.', "")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it in ALLOWED_EXTENSIONS }

    val ext = nameExt ?: MIME_TO_EXT[mime] ?: "ogg"

    val cleanMime = if (mime.startsWith("audio/") || mime.startsWith("video/")) {
        mime
    } else {
        EXT_TO_MIME[ext] ?: "audio/ogg"
    }

    return AudioPayload(bytes, "audio.$ext", cleanMime)
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
