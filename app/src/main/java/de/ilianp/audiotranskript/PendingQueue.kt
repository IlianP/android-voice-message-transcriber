package de.ilianp.audiotranskript

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Voice messages shared with „Zwischenspeichern“, waiting for the one shared with
 * „Transkript starten“ that closes the batch.
 *
 * Several messages from the same person often arrive back to back. Sharing each one on its own
 * would mean one transcript per message; instead the earlier ones are parked here, and the last
 * one picks them all up and transcribes them together, in the order they were shared.
 *
 * The audio is copied, not remembered by URI, for the same reason as in [TranscriptHistory]: the
 * read grant on a shared `content://` URI ends with the activity that received it, which for a
 * queued message is gone a moment later.
 *
 * [takeAll] moves the queue into an "active" folder rather than deleting it, because the batch
 * still has to be played and, if a provider fails, transcribed again. That folder is replaced by
 * the next batch, so at most one batch's worth of audio sits there.
 */
class PendingQueue(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR_NAME)
    private val activeDir: File get() = File(dir, ACTIVE_DIR)

    /** Parks a copy of [payload] and returns how many messages are now waiting. */
    fun add(payload: AudioPayload, now: Long = System.currentTimeMillis()): Int =
        addAll(listOf(payload), now)

    /**
     * Parks several messages shared in one go, all or none: if one cannot be written, the ones
     * already written go again, so a retried share does not park them twice. Returns how many
     * messages are now waiting.
     */
    fun addAll(payloads: List<AudioPayload>, now: Long = System.currentTimeMillis()): Int =
        synchronized(LOCK) {
            dir.mkdirs()
            val waiting = pendingFiles(now).size
            val written = mutableListOf<File>()
            try {
                payloads.forEachIndexed { index, payload ->
                    val ext = payload.filename.substringAfterLast('.', "ogg")
                    // Time first, so a plain name sort is the order of sharing; the counter keeps
                    // messages shared in one go (ACTION_SEND_MULTIPLE) in their order, too. The
                    // random tail keeps a name from ever coming back in a later batch, where the
                    // player would take the same URI for the same audio and keep playing the old one.
                    val tail = UUID.randomUUID().toString().take(8)
                    val file = File(dir, "%013d-%03d-%s.%s".format(now, waiting + index, tail, ext))
                    written += file
                    file.writeBytes(payload.bytes)
                }
            } catch (e: Exception) {
                written.forEach { it.delete() }
                throw e
            }
            waiting + payloads.size
        }

    /** How many messages are waiting. Expired ones are swept off disk on the way. */
    fun count(now: Long = System.currentTimeMillis()): Int = synchronized(LOCK) {
        pendingFiles(now).size
    }

    /**
     * Hands the waiting messages over to a new batch, oldest first, as `file://` URIs that stay
     * valid until the next call. Empties the queue.
     */
    fun takeAll(now: Long = System.currentTimeMillis()): List<Uri> = synchronized(LOCK) {
        val waiting = pendingFiles(now)
        runCatching { activeDir.deleteRecursively() }
            .onFailure { Log.w(TAG, "Alter Stapel konnte nicht geloescht werden", it) }
        if (waiting.isEmpty()) return emptyList()
        activeDir.mkdirs()
        waiting.mapNotNull { file ->
            val target = File(activeDir, file.name)
            if (file.renameTo(target)) Uri.fromFile(target) else null
        }
    }

    /** Throws the waiting messages away. The batch currently on screen is left alone. */
    fun clear() = synchronized(LOCK) {
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        Unit
    }

    /**
     * The queue, oldest first. A message older than [MAX_AGE_MS] is dropped: whatever it was
     * waiting for has evidently not come, and it must not slip unnoticed into an unrelated
     * conversation's transcript days later.
     */
    private fun pendingFiles(now: Long): List<File> {
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty().sortedBy { it.name }
        val (fresh, expired) = files.partition { now - sharedAt(it) <= MAX_AGE_MS }
        expired.forEach { it.delete() }
        return fresh
    }

    private fun sharedAt(file: File): Long =
        file.name.substringBefore('-').toLongOrNull() ?: file.lastModified()

    companion object {
        /** Guards the queue between the share activity adding and the screen taking. */
        private val LOCK = Any()

        /** How long a parked message waits for the rest of its batch. */
        val MAX_AGE_MS: Long = TimeUnit.HOURS.toMillis(24)

        private const val TAG = "AudioTranskript"
        private const val DIR_NAME = "pending"
        private const val ACTIVE_DIR = "active"
    }
}
