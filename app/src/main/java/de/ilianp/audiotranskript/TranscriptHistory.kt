package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * One finished transcription plus the local copy of the audio it was made from.
 *
 * [audioFileName] is a plain file name inside the history directory, not a path, so the entry
 * survives the app's data directory moving (backup/restore, app cloning). It is null when the
 * copy could not be written - the text is still worth keeping on its own.
 */
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val transcript: String,
    val audioFileName: String?,
    val audioHash: String?,
)

/**
 * Keeps the last few transcriptions - text and audio - in the app's private storage.
 *
 * The audio has to be copied: a voice message shared from WhatsApp arrives as a `content://`
 * URI with a temporary read grant that dies with the task, and it is not persistable (the
 * sender does not offer [android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]). So
 * without a copy, a restored transcript would have nothing left to play. The bytes are already
 * in memory for the upload, which is why [add] takes the [AudioPayload] rather than the URI.
 *
 * Kept deliberately small: at most [MAX_ENTRIES] entries, none older than [MAX_AGE_MS]. Every
 * file operation is best-effort - a broken index costs the history, never the app.
 */
class TranscriptHistory(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR_NAME)
    private val indexFile: File get() = File(dir, INDEX_NAME)

    /** The stored entries, newest first, with anything expired already swept off disk. */
    fun entries(now: Long = System.currentTimeMillis()): List<HistoryEntry> = synchronized(LOCK) {
        val stored = read()
        val kept = prune(stored, now)
        if (kept != stored) write(kept)
        kept
    }

    /**
     * Stores [transcript] together with a copy of [payload], and returns the resulting history.
     *
     * Re-running the same message (the "Neu" button) updates its existing entry instead of
     * piling up near-identical copies of the same audio.
     */
    fun add(
        transcript: String,
        payload: AudioPayload?,
        now: Long = System.currentTimeMillis(),
    ): List<HistoryEntry> = synchronized(LOCK) {
        val hash = payload?.let { sha256(it.bytes) }
        val existing = entries(now)
        val previous = hash?.let { h -> existing.firstOrNull { it.audioHash == h } }
        val id = previous?.id ?: UUID.randomUUID().toString().take(8)

        // The previous copy holds the very same bytes, so it is reused rather than rewritten.
        val audioName = previous?.audioFileName?.takeIf { File(dir, it).exists() }
            ?: payload?.let { writeAudio(id, it) }

        val entry = HistoryEntry(id, now, transcript, audioName, hash)
        val updated = prune(listOf(entry) + existing.filterNot { it.id == id }, now)
        write(updated)
        updated
    }

    /** A `file://` URI for the stored audio, or null if this entry has none (any more). */
    fun audioUri(entry: HistoryEntry): Uri? = synchronized(LOCK) {
        entry.audioFileName
            ?.let { File(dir, it) }
            ?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it) }
    }

    /** Drops every entry and its audio. */
    fun clear() = synchronized(LOCK) {
        runCatching { dir.deleteRecursively() }
            .onFailure { Log.w(TAG, "Verlauf konnte nicht geloescht werden", it) }
        Unit
    }

    // ---- storage ------------------------------------------------------------------------

    private fun prune(entries: List<HistoryEntry>, now: Long): List<HistoryEntry> {
        val kept = entries
            .sortedByDescending { it.createdAt }
            .filter { now - it.createdAt <= MAX_AGE_MS }
            .take(MAX_ENTRIES)
        deleteUnreferencedAudio(kept)
        return kept
    }

    /** Sweeps audio left behind by expired entries, and by runs that died before indexing. */
    private fun deleteUnreferencedAudio(kept: List<HistoryEntry>) {
        val referenced = kept.mapNotNull { it.audioFileName }.toSet() + INDEX_NAME
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.name !in referenced) file.delete()
            }
        }.onFailure { Log.w(TAG, "Alte Audiodateien konnten nicht aufgeraeumt werden", it) }
    }

    private fun writeAudio(id: String, payload: AudioPayload): String? = runCatching {
        val ext = payload.filename.substringAfterLast('.', "bin")
        val file = File(dir, "$id.$ext")
        dir.mkdirs()
        file.writeBytes(payload.bytes)
        file.name
    }.onFailure { Log.w(TAG, "Audio konnte nicht gesichert werden", it) }.getOrNull()

    private fun read(): List<HistoryEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val transcript = o.optString(FIELD_TRANSCRIPT).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            HistoryEntry(
                id = o.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                createdAt = o.optLong(FIELD_CREATED_AT),
                transcript = transcript,
                audioFileName = o.optString(FIELD_AUDIO).takeIf { it.isNotBlank() },
                audioHash = o.optString(FIELD_HASH).takeIf { it.isNotBlank() },
            )
        }
    }.onFailure { Log.w(TAG, "Verlauf konnte nicht gelesen werden", it) }.getOrDefault(emptyList())

    private fun write(entries: List<HistoryEntry>) {
        runCatching {
            dir.mkdirs()
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put(FIELD_ID, entry.id)
                        .put(FIELD_CREATED_AT, entry.createdAt)
                        .put(FIELD_TRANSCRIPT, entry.transcript)
                        .put(FIELD_AUDIO, entry.audioFileName)
                        .put(FIELD_HASH, entry.audioHash),
                )
            }
            indexFile.writeText(array.toString())
        }.onFailure { Log.w(TAG, "Verlauf konnte nicht gespeichert werden", it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * Guards the whole read-prune-write cycle, across instances.
         *
         * The screen reads the history while a finished transcription writes to it, both off
         * the main thread. Unserialized, a prune working from an older snapshot would delete
         * the audio file that the write just put there, or overwrite its index - so every
         * operation below takes this, and the index and the audio files stay in step.
         */
        private val LOCK = Any()

        /** How many transcriptions are kept before the oldest one drops out. */
        const val MAX_ENTRIES = 10

        /** How long an entry (and its audio) survives, counted from the transcription. */
        val MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(7)

        private const val TAG = "AudioTranskript"
        private const val DIR_NAME = "history"
        private const val INDEX_NAME = "index.json"
        private const val FIELD_ID = "id"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_TRANSCRIPT = "transcript"
        private const val FIELD_AUDIO = "audio"
        private const val FIELD_HASH = "audioHash"
    }
}
