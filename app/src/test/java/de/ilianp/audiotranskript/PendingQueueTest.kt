package de.ilianp.audiotranskript

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The queue behind „Zwischenspeichern“. What matters is the order - a batch is read as one
 * conversation - and that nothing parked lingers long enough to end up in an unrelated one.
 */
@RunWith(AndroidJUnit4::class)
class PendingQueueTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir get() = File(context.filesDir, "pending")

    private lateinit var queue: PendingQueue

    private fun payload(marker: Byte, ext: String = "ogg") =
        AudioPayload(ByteArray(16) { marker }, "audio.$ext", "audio/ogg")

    @Before
    fun setUp() {
        dir.deleteRecursively()
        queue = PendingQueue(context)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `messages come out in the order they were parked`() {
        val now = 1_700_000_000_000L
        assertEquals(1, queue.add(payload(1), now))
        assertEquals(2, queue.add(payload(2, "m4a"), now + 1_000))
        // Shared in one go (SEND_MULTIPLE): same millisecond, order still kept.
        assertEquals(3, queue.add(payload(3), now + 1_000))

        val uris = queue.takeAll(now + 2_000)

        assertEquals(listOf<Byte>(1, 2, 3), uris.map { File(it.path!!).readBytes().first() })
        assertTrue("Endung verloren", uris[1].path!!.endsWith(".m4a"))
        assertEquals(0, queue.count(now + 2_000))
    }

    @Test
    fun `a taken batch stays playable until the next one replaces it`() {
        queue.add(payload(1))
        val first = queue.takeAll()
        assertTrue(File(first.single().path!!).exists())

        queue.add(payload(2))
        val second = queue.takeAll()

        assertFalse("Alter Stapel liegt noch herum", File(first.single().path!!).exists())
        assertTrue(File(second.single().path!!).exists())
    }

    @Test
    fun `an empty queue hands over nothing`() {
        assertTrue(queue.takeAll().isEmpty())
    }

    @Test
    fun `a message parked for too long is dropped`() {
        val now = 1_700_000_000_000L
        queue.add(payload(1), now)
        queue.add(payload(2), now + PendingQueue.MAX_AGE_MS)

        val later = now + PendingQueue.MAX_AGE_MS + 1_000
        assertEquals(1, queue.count(later))
        val uris = queue.takeAll(later)
        assertEquals(listOf<Byte>(2), uris.map { File(it.path!!).readBytes().first() })
    }

    @Test
    fun `discarding empties the queue but keeps the batch on screen`() {
        queue.add(payload(1))
        val onScreen = queue.takeAll()
        queue.add(payload(2))

        queue.clear()

        assertEquals(0, queue.count())
        assertTrue(File(onScreen.single().path!!).exists())
    }
}
