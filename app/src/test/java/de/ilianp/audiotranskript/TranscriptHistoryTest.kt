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
    fun `clearing removes the transcripts and the audio`() {
        history.add("weg damit", payload(1))
        history.clear()

        assertTrue(history.entries().isEmpty())
        assertFalse(historyDir.exists())
    }
}
