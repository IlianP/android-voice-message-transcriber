package de.ilianp.audiotranskript

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The history is what makes a transcript survive the app being swiped away, so what is tested
 * here is mostly the part that is easy to get wrong: that pruning takes the audio with it and
 * that a damaged index costs the history instead of the app.
 */
@RunWith(AndroidJUnit4::class)
class TranscriptHistoryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val historyDir get() = File(context.filesDir, "history")

    private lateinit var history: TranscriptHistory

    private fun payload(marker: Byte, ext: String = "ogg") =
        AudioPayload(ByteArray(32) { marker }, "audio.$ext", "audio/ogg")

    /** Audio files only, so the index does not count as one. */
    private fun storedAudioFiles(): List<String> =
        historyDir.listFiles().orEmpty().map { it.name }.filterNot { it == "index.json" }.sorted()

    @Before
    fun setUp() {
        history = TranscriptHistory(context)
        history.clear()
    }

    @After
    fun tearDown() {
        history.clear()
    }

    @Test
    fun `a stored transcription comes back with its audio`() {
        history.add("Hallo, kommst du morgen?", payload(1))

        val entry = TranscriptHistory(context).entries().single()
        assertEquals("Hallo, kommst du morgen?", entry.transcript)

        val uri = history.audioUri(entry)
        assertNotNull("Audio-Kopie fehlt", uri)
        assertEquals(32, File(uri!!.path!!).length())
    }

    @Test
    fun `entries come back newest first`() {
        val now = System.currentTimeMillis()
        history.add("aelter", payload(1), now - TimeUnit.HOURS.toMillis(2))
        history.add("neuer", payload(2), now)

        assertEquals(listOf("neuer", "aelter"), history.entries(now).map { it.transcript })
    }

    @Test
    fun `re-transcribing the same message updates its entry instead of duplicating it`() {
        val now = System.currentTimeMillis()
        history.add("erster Versuch", payload(7), now - TimeUnit.MINUTES.toMillis(5))
        history.add("zweiter Versuch", payload(7), now)

        val entries = history.entries(now)
        assertEquals(1, entries.size)
        assertEquals("zweiter Versuch", entries.single().transcript)
        assertEquals(1, storedAudioFiles().size)
    }

    @Test
    fun `only the newest entries are kept, and the audio of the rest is deleted`() {
        val now = System.currentTimeMillis()
        repeat(TranscriptHistory.MAX_ENTRIES + 3) { i ->
            history.add("Nachricht $i", payload(i.toByte()), now - (100L - i) * 1000L)
        }

        val entries = history.entries(now)
        assertEquals(TranscriptHistory.MAX_ENTRIES, entries.size)
        assertEquals("Nachricht 12", entries.first().transcript)
        assertFalse(entries.any { it.transcript == "Nachricht 0" })
        assertEquals(
            "Verwaiste Audiodateien liegen noch da",
            TranscriptHistory.MAX_ENTRIES,
            storedAudioFiles().size,
        )
    }

    @Test
    fun `entries past the retention window disappear on the next read`() {
        val now = System.currentTimeMillis()
        history.add("uralt", payload(1), now - TranscriptHistory.MAX_AGE_MS - 1000L)
        history.add("frisch", payload(2), now)

        assertEquals(listOf("frisch"), history.entries(now).map { it.transcript })
        assertEquals(1, storedAudioFiles().size)
    }

    @Test
    fun `a damaged index costs the history, not the app`() {
        history.add("verloren", payload(1))
        File(historyDir, "index.json").writeText("{kein gueltiges JSON")

        assertTrue(TranscriptHistory(context).entries().isEmpty())
    }

    @Test
    fun `a transcription without audio is still kept`() {
        history.add("nur Text", null)

        val entry = history.entries().single()
        assertEquals("nur Text", entry.transcript)
        assertNull(history.audioUri(entry))
    }

    @Test
    fun `parallel writes do not lose entries or their audio`() {
        // The screen reads the history while a finished transcription writes to it. Without a
        // lock around read-prune-write, a prune working from an older snapshot deletes the
        // audio the write just put there - or drops the entry altogether.
        val writers = 8
        val ready = java.util.concurrent.CountDownLatch(writers)
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(writers)

        repeat(writers) { i ->
            Thread {
                ready.countDown()
                start.await()
                history.add("Nachricht $i", payload(i.toByte()))
                // A concurrent reader prunes as well, which is the other half of the race.
                history.entries()
                done.countDown()
            }.start()
        }

        ready.await()
        start.countDown()
        assertTrue("Schreiber haengen", done.await(30, TimeUnit.SECONDS))

        val entries = TranscriptHistory(context).entries()
        assertEquals(writers, entries.size)
        entries.forEach { entry ->
            assertNotNull("Audio von '${entry.transcript}' fehlt", history.audioUri(entry))
        }
        assertEquals(writers, storedAudioFiles().size)
    }

    @Test
    fun `clearing removes the transcripts and the audio`() {
        history.add("weg damit", payload(1))
        history.clear()

        assertTrue(history.entries().isEmpty())
        assertFalse(historyDir.exists())
    }

    @Test
    fun `a batch is one entry with one audio file per message`() {
        history.add(listOf("Erste", "Zweite", "Dritte"), listOf(payload(1), payload(2), payload(3)))

        val entry = TranscriptHistory(context).entries().single()
        assertEquals(listOf("Erste", "Zweite", "Dritte"), entry.segments)
        assertEquals("Erste\n\nZweite\n\nDritte", entry.transcript)
        val uris = history.audioUris(entry)
        assertEquals(3, uris.size)
        // In the order given, so the player's messages line up with the transcript's.
        assertEquals(listOf<Byte>(1, 2, 3), uris.map { File(it.path!!).readBytes().first() })
    }

    @Test
    fun `a batch with a missing audio file plays none of it`() {
        history.add(listOf("Erste", "Zweite"), listOf(payload(1), payload(2)))
        val entry = history.entries().single()
        File(history.audioUris(entry)[1].path!!).delete()

        assertTrue(history.audioUris(entry).isEmpty())
        // The text still is what it was.
        assertEquals(listOf("Erste", "Zweite"), history.entries().single().segments)
    }

    @Test
    fun `entries written before batches still load`() {
        historyDir.mkdirs()
        File(historyDir, "abc.ogg").writeBytes(ByteArray(8))
        File(historyDir, "index.json").writeText(
            """[{"id":"abc","createdAt":${System.currentTimeMillis()},""" +
                """"transcript":"Alt","audio":"abc.ogg","audioHash":"x"}]""",
        )

        val entry = history.entries().single()
        assertEquals(listOf("Alt"), entry.segments)
        assertEquals(listOf("abc.ogg"), entry.audioFileNames)
        assertNotNull(history.audioUri(entry))
    }

    @Test
    fun `a summary is stored with its entry and survives a reload`() {
        val id = history.add("Langer Text", payload(1)).single().id

        history.setSummary(id, "• Kurz")

        assertEquals("• Kurz", TranscriptHistory(context).entries().single().summary)
    }

    @Test
    fun `a summary for an entry that is gone changes nothing`() {
        history.add("Text", payload(1))

        val entries = history.setSummary("weg", "• Kurz")

        assertNull(entries.single().summary)
    }

    @Test
    fun `re-transcribing a message drops its old summary`() {
        val now = System.currentTimeMillis()
        val id = history.add("erster Versuch", payload(7), now - TimeUnit.MINUTES.toMillis(5)).single().id
        history.setSummary(id, "• zum ersten Versuch", now - TimeUnit.MINUTES.toMillis(4))

        history.add("zweiter Versuch", payload(7), now)

        // The summary belonged to a transcript that is no longer there.
        assertNull(history.entries(now).single().summary)
    }
}
