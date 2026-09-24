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
 * A batch of several messages transcribed together is one entry: [audioFileNames] holds one file
 * per message and [segments] one transcript per message, in the same order; [transcript] is
 * those joined, the text as copied or shared. A single message has one of each.
 *
 * The file names are plain names inside the history directory, not paths, so the entry survives
 * the app's data directory moving (backup/restore, app cloning). The list is empty when the copy
 * could not be written - the text is still worth keeping on its own.
 *
 * [summary] is the summary made on request, if there is one - kept, so that opening the entry
 * again does not pay for it a second time.
 */
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val transcript: String,
    val audioFileNames: List<String>,
    val audioHash: String?,
    val segments: List<String> = listOf(transcript),
    val summary: String? = null,
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
    ): List<HistoryEntry> = add(listOf(transcript), listOfNotNull(payload), now)

    /**
     * Stores a batch: one transcript per message in [segments], one audio copy per message in
     * [payloads] (same order), kept together as a single entry.
     */
    fun add(
        segments: List<String>,
        payloads: List<AudioPayload>,
        now: Long = System.currentTimeMillis(),
    ): List<HistoryEntry> = synchronized(LOCK) {
        val hash = hashOf(payloads)
        val existing = entries(now)
        val previous = hash?.let { h -> existing.firstOrNull { it.audioHash == h } }
        val id = previous?.id ?: UUID.randomUUID().toString().take(8)

        // The previous copies hold the very same bytes, so they are reused rather than rewritten.
        val audioNames = previous?.audioFileNames
            ?.takeIf { names -> names.isNotEmpty() && names.all { File(dir, it).exists() } }
            ?: writeAudio(id, payloads)

        val entry = HistoryEntry(
            id = id,
            createdAt = now,
            transcript = WizperClient.joinTranscripts(segments),
            audioFileNames = audioNames,
            audioHash = hash,
            segments = segments,
        )
        val updated = prune(listOf(entry) + existing.filterNot { it.id == id }, now)
        write(updated)
        updated
    }

    /**
     * Attaches [summary] to the entry [id], and returns the resulting history. A no-op if the
     * entry has gone in the meantime (pruned, or the history cleared while summarizing).
     */
    fun setSummary(id: String, summary: String, now: Long = System.currentTimeMillis()): List<HistoryEntry> =
        synchronized(LOCK) {
            val existing = entries(now)
            if (existing.none { it.id == id }) return existing
            val updated = existing.map { if (it.id == id) it.copy(summary = summary) else it }
            write(updated)
            updated
        }

    /**
     * `file://` URIs for the stored audio, one per message - or none at all if any of them is
     * missing, since a batch with a gap would no longer line up with its transcripts.
     */
    fun audioUris(entry: HistoryEntry): List<Uri> = synchronized(LOCK) {
        val files = entry.audioFileNames.map { File(dir, it) }
        if (files.isEmpty() || !files.all { it.exists() }) emptyList() else files.map { Uri.fromFile(it) }
    }

    /** The stored audio of a single-message entry, or null if it has none (any more). */
    fun audioUri(entry: HistoryEntry): Uri? = audioUris(entry).firstOrNull()

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
        val referenced = kept.flatMap { it.audioFileNames }.toSet() + INDEX_NAME
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.name !in referenced) file.delete()
            }
        }.onFailure { Log.w(TAG, "Alte Audiodateien konnten nicht aufgeraeumt werden", it) }
    }

    /** Writes one copy per payload; all or nothing, so the files match the segments. */
    private fun writeAudio(id: String, payloads: List<AudioPayload>): List<String> = runCatching {
        dir.mkdirs()
        payloads.mapIndexed { index, payload ->
            val ext = payload.filename.substringAfterLast('.', "bin")
            val name = if (payloads.size == 1) "$id.$ext" else "$id-${index + 1}.$ext"
            File(dir, name).writeBytes(payload.bytes)
            name
        }
    }.onFailure { Log.w(TAG, "Audio konnte nicht gesichert werden", it) }.getOrDefault(emptyList())

    private fun read(): List<HistoryEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val transcript = o.optString(FIELD_TRANSCRIPT).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            // Entries from before batches knew a single "audio" name and no segments.
            val audios = o.optJSONArray(FIELD_AUDIOS)?.strings()
                ?: listOfNotNull(o.optString(FIELD_AUDIO).takeIf { it.isNotBlank() })
            val segments = o.optJSONArray(FIELD_SEGMENTS)?.strings()?.takeIf { it.isNotEmpty() }
                ?: listOf(transcript)
            HistoryEntry(
                id = o.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                createdAt = o.optLong(FIELD_CREATED_AT),
                transcript = transcript,
                audioFileNames = audios,
                audioHash = o.optString(FIELD_HASH).takeIf { it.isNotBlank() },
                segments = segments,
                summary = o.optString(FIELD_SUMMARY).takeIf { it.isNotBlank() },
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
                        .put(FIELD_AUDIOS, JSONArray(entry.audioFileNames))
                        .put(FIELD_HASH, entry.audioHash)
                        .apply {
                            if (entry.segments.size > 1) put(FIELD_SEGMENTS, JSONArray(entry.segments))
                            entry.summary?.let { put(FIELD_SUMMARY, it) }
                        },
                )
            }
            indexFile.writeText(array.toString())
        }.onFailure { Log.w(TAG, "Verlauf konnte nicht gespeichert werden", it) }
    }

    /**
     * Identifies the audio behind an entry. A single message hashes its bytes, as it always has;
     * a batch hashes the hashes of its messages, so the same messages in the same order match.
     */
    private fun hashOf(payloads: List<AudioPayload>): String? = when (payloads.size) {
        0 -> null
        1 -> sha256(payloads.single().bytes)
        else -> sha256(payloads.joinToString(",") { sha256(it.bytes) }.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

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
        private const val FIELD_AUDIOS = "audios"
        private const val FIELD_SEGMENTS = "segments"
        private const val FIELD_HASH = "audioHash"
        private const val FIELD_SUMMARY = "summary"
    }
}
