package de.ilianp.audiotranskript

import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.util.concurrent.TimeUnit

/**
 * A batch plays as one timeline across several files. These cover the seams: seeking into
 * another message, jumping to one, and running on from one message into the next.
 */
@RunWith(AndroidJUnit4::class)
class MessagePlayerControllerTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var controller: MessagePlayerController

    @Before
    fun setUp() {
        // Three messages of 60 s, 30 s and 45 s.
        val durations = mapOf("a" to 60_000, "b" to 30_000, "c" to 45_000)
        val uris = durations.keys.map { Uri.parse("file:///nachrichten/$it.ogg") }
        uris.zip(durations.values).forEach { (uri, ms) ->
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(context, uri),
                ShadowMediaPlayer.MediaInfo(ms, 0),
            )
        }
        controller = MessagePlayerController(context, uris)
        controller.prepare()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        controller.release()
    }

    @Test
    fun `the timeline spans all messages`() {
        assertTrue(controller.isPrepared)
        assertEquals(135_000, controller.durationMs)
        assertEquals(3, controller.segmentCount)
    }

    @Test
    fun `seeking lands in the message that holds the position`() {
        controller.seekTo(70_000)
        assertEquals(1, controller.currentIndex)
        assertEquals(70_000, controller.positionMs)

        // Exactly at a boundary belongs to the message that starts there.
        controller.seekTo(90_000)
        assertEquals(2, controller.currentIndex)

        controller.skip(-40_000)
        assertEquals(0, controller.currentIndex)
        assertEquals(50_000, controller.positionMs)
    }

    @Test
    fun `jumping to a message plays it from its start`() {
        controller.playSegment(2)

        assertEquals(2, controller.currentIndex)
        assertEquals(90_000, controller.positionMs)
        assertTrue(controller.isPlaying)
    }

    @Test
    fun `playback runs on into the next message`() {
        controller.seekTo(55_000)
        controller.togglePlayPause()
        assertTrue(controller.isPlaying)

        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)

        assertEquals(1, controller.currentIndex)
        assertTrue("Nach dem Übergang steht die Wiedergabe", controller.isPlaying)
    }
}
