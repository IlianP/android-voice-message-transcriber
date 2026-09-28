package de.ilianp.audiotranskript

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * The playback notification: when it comes and goes, and that its buttons steer the player.
 *
 * The buttons are pressed the way Android 12 and older deliver them, as the notification's own
 * broadcasts: Robolectric does not route a media controller's commands to the session. Android 13
 * and later send the same actions through the session, into the same handler.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackNotifierTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var controller: MessagePlayerController
    private lateinit var notifier: PlaybackNotifier
    private lateinit var session: MediaControllerCompat

    @Before
    fun setUp() {
        val uri = Uri.parse("file:///nachrichten/lang.ogg")
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, uri),
            ShadowMediaPlayer.MediaInfo(60_000, 0),
        )
        controller = MessagePlayerController(context, listOf(uri))
        notifier = PlaybackNotifier(context, controller)
        controller.prepare()
        idle()
        session = MediaControllerCompat(context, notifier.sessionToken)
    }

    @After
    fun tearDown() {
        notifier.release()
        controller.release()
    }

    @Test
    fun `nothing shows before the message is played`() {
        assertNull(shadowOf(context as Application).nextStartedService)
    }

    @Test
    fun `playing starts the foreground service`() {
        controller.togglePlayPause()

        val started = shadowOf(context as Application).nextStartedService
        assertEquals(PlaybackService::class.java.name, started.component?.className)
        assertEquals(PlaybackStateCompat.STATE_PLAYING, session.playbackState.state)
    }

    @Test
    fun `the session's buttons skip five seconds either way`() {
        controller.seekTo(20_000)

        press(FORWARD)
        assertEquals(25_000, controller.positionMs)

        press(REWIND)
        press(REWIND)
        assertEquals(15_000, controller.positionMs)
    }

    @Test
    fun `the session's buttons step the speed and stop at the ends`() {
        press(FASTER)
        assertEquals(PlaybackSpeed.X1_5, controller.speed)
        assertEquals("Remembered for the next message", 1.5f, Settings(context).playbackSpeedFactor, 0f)

        repeat(5) { press(FASTER) }
        assertEquals(PlaybackSpeed.X2_5, controller.speed)

        repeat(5) { press(SLOWER) }
        assertEquals(PlaybackSpeed.X1, controller.speed)
    }

    @Test
    fun `play and pause from outside reach the player`() {
        press(PLAY_PAUSE)
        assertTrue(controller.isPlaying)

        press(PLAY_PAUSE)
        assertFalse(controller.isPlaying)
        assertEquals(PlaybackStateCompat.STATE_PAUSED, session.playbackState.state)
    }

    @Test
    fun `paused, the notification stays but can be swiped away`() {
        controller.togglePlayPause()
        controller.togglePlayPause()

        val shown = notificationManager().activeNotifications.single()
        assertTrue(shown.isClearable)
    }

    @Test
    fun `played to the end, the notification goes away`() {
        controller.seekTo(58_000)
        controller.togglePlayPause()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertFalse(controller.isPlaying)
        assertTrue(notificationManager().activeNotifications.isEmpty())
    }

    @Test
    fun `a notification left behind by a reclaimed process goes away on a tap`() {
        controller.togglePlayPause()
        controller.togglePlayPause()
        // What survives of the old process: its notification, but no player behind it.
        val leftOver = notificationManager().activeNotifications.single().notification
        notifier.release()
        notificationManager().notify(PlaybackNotifier.NOTIFICATION_ID, leftOver)

        press(PLAY_PAUSE)

        assertTrue(notificationManager().activeNotifications.isEmpty())
    }

    @Test
    fun `a new player clears a notification left behind`() {
        controller.togglePlayPause()
        controller.togglePlayPause()
        val leftOver = notificationManager().activeNotifications.single().notification
        notifier.release()
        notificationManager().notify(PlaybackNotifier.NOTIFICATION_ID, leftOver)

        notifier = PlaybackNotifier(context, controller)

        assertTrue(notificationManager().activeNotifications.isEmpty())
    }

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun press(action: String) {
        context.sendBroadcast(Intent(context, PlaybackActionReceiver::class.java).setAction(action))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val PLAY_PAUSE = "de.ilianp.audiotranskript.playback.PLAY_PAUSE"
        const val REWIND = "de.ilianp.audiotranskript.playback.REWIND"
        const val FORWARD = "de.ilianp.audiotranskript.playback.FORWARD"
        const val SLOWER = "de.ilianp.audiotranskript.playback.SLOWER"
        const val FASTER = "de.ilianp.audiotranskript.playback.FASTER"
    }
}
