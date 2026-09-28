package de.ilianp.audiotranskript

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle

/** How far the notification's skip buttons jump - shorter than the app's ±10 s, as asked for. */
internal const val NOTIFICATION_SKIP_MS = 5_000

/**
 * Carries a [MessagePlayerController] out of the app: a media session, plus the playback
 * notification that goes with it, as music apps have. From there - notification shade, lock
 * screen, headset buttons - the message can be paused and resumed, moved ±5 s, and sped up or
 * slowed down, while the app itself sits in the background.
 *
 * The notification appears with the first play and stays until the message has played to the end
 * or the screen lets go of the player. While playing it is held by [PlaybackService] and cannot be
 * swiped away; paused, it can be.
 *
 * Its buttons, left to right: −5 s, play/pause, +5 s, slower, faster. Android 13 and later lay
 * them out from the session instead of the notification: play/pause from the playback state, the
 * other four from its custom actions in that order - ±5 s take the places of previous/next, which
 * a voice message has no use for, and the speed buttons the two slots after them. The seek bar
 * with elapsed and total time comes from the session as well, from Android 10 on.
 */
class PlaybackNotifier(context: Context, private val controller: MessagePlayerController) {

    private val context = context.applicationContext
    private val settings = Settings(this.context)
    private val manager = NotificationManagerCompat.from(this.context)

    /** Played since the notification last went away - it comes with playing, not with a paused player. */
    private var shown = false

    /** Swiped away while paused: stays away until the message is played again. */
    private var dismissed = false

    /** What the notification last showed. Seeking changes none of it, so it is not reposted then. */
    private var shownLook: Look? = null

    private data class Look(
        val playing: Boolean,
        val speed: PlaybackSpeed,
        val index: Int,
        val durationMs: Int,
    )

    val isPlaying: Boolean get() = controller.isPlaying

    /** For tests, to press the buttons the way the system does. */
    internal val sessionToken: MediaSessionCompat.Token get() = session.sessionToken

    private val openApp: PendingIntent = PendingIntent.getActivity(
        this.context,
        0,
        // Not a share, so the activity only comes to the front and leaves the screen as it is.
        Intent(this.context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    // What the system, the lock screen and headset buttons send. Kept apart from the session:
    // inside its apply block, `controller` would be the session's own MediaControllerCompat.
    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (!controller.isPlaying) controller.togglePlayPause()
        }

        override fun onPause() {
            if (controller.isPlaying) controller.togglePlayPause()
        }

        override fun onStop() = onPause()

        override fun onSeekTo(pos: Long) = controller.seekTo(pos.toInt())

        override fun onCustomAction(action: String, extras: Bundle?) = handle(action)
    }

    private val session = MediaSessionCompat(this.context, "Audio-Transkript").apply {
        setSessionActivity(openApp)
        setCallback(sessionCallback)
    }

    init {
        // A new player means the one before is gone. A notification still standing now was left
        // behind by a process Android reclaimed while paused, and its buttons lead nowhere.
        manager.cancel(NOTIFICATION_ID)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Wiedergabe")
                .setDescription("Steuerung der Sprachnachricht, solange sie abgespielt wird")
                .setShowBadge(false)
                .build(),
        )
        controller.onChange = ::update
        active = this
    }

    fun release() {
        controller.onChange = null
        if (active === this) active = null
        hide()
        session.release()
    }

    /** Follows the player; called by it after every change the outside world should hear about. */
    fun update() {
        if (!controller.isPrepared) return
        publishState()

        val playing = controller.isPlaying
        if (playing) {
            shown = true
            dismissed = false
        }
        if (!shown) return

        val atEnd = controller.durationMs > 0 && controller.positionMs >= controller.durationMs
        if (!playing && atEnd) {
            // Heard to the end: nothing left to control from outside the app.
            hide()
            return
        }

        val look = Look(playing, controller.speed, controller.currentIndex, controller.durationMs)
        val looksChanged = look != shownLook
        if (looksChanged) publishMetadata()
        session.isActive = true

        when {
            playing && !PlaybackService.isRunning -> {
                // The service posts the notification itself, as its foreground notification.
                shownLook = look
                if (!PlaybackService.start(context)) post()
            }
            !playing && PlaybackService.isRunning -> {
                PlaybackService.stop()
                // Leaving the foreground keeps the notification but not its latest state.
                if (!dismissed) post()
                shownLook = look
            }
            looksChanged && !dismissed -> {
                post()
                shownLook = look
            }
        }
    }

    fun buildNotification(): Notification {
        val playing = controller.isPlaying
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title())
            .setContentText(speedLine())
            .setContentIntent(openApp)
            .setDeleteIntent(broadcast(ACTION_DISMISSED))
            .setOngoing(playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_replay_5, "5 Sekunden zurück", broadcast(ACTION_REWIND))
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Abspielen",
                broadcast(ACTION_PLAY_PAUSE),
            )
            .addAction(R.drawable.ic_forward_5, "5 Sekunden vor", broadcast(ACTION_FORWARD))
            .addAction(R.drawable.ic_speed_slower, "Langsamer", broadcast(ACTION_SLOWER))
            .addAction(R.drawable.ic_speed_faster, "Schneller", broadcast(ACTION_FASTER))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    // Folded up there is room for three: the skips and play/pause between them.
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    /** One button, from the notification (via [PlaybackActionReceiver]) or the media session. */
    internal fun handle(action: String) {
        when (action) {
            ACTION_REWIND -> controller.skip(-NOTIFICATION_SKIP_MS)
            ACTION_FORWARD -> controller.skip(NOTIFICATION_SKIP_MS)
            ACTION_PLAY_PAUSE -> controller.togglePlayPause()
            ACTION_SLOWER -> changeSpeed(controller.speed.slower())
            ACTION_FASTER -> changeSpeed(controller.speed.faster())
            ACTION_DISMISSED -> dismissed = true
        }
    }

    private fun changeSpeed(speed: PlaybackSpeed) {
        controller.changeSpeed(speed)
        // Same as a tap on the chips in the app: the next message starts at this speed too.
        settings.playbackSpeedFactor = speed.factor
    }

    private fun publishState() {
        val playing = controller.isPlaying
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO,
                )
                // With the speed, the system moves the seek bar along on its own between updates.
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    controller.positionMs.toLong(),
                    controller.speed.factor,
                    SystemClock.elapsedRealtime(),
                )
                .addCustomAction(ACTION_REWIND, "5 Sekunden zurück", R.drawable.ic_replay_5)
                .addCustomAction(ACTION_FORWARD, "5 Sekunden vor", R.drawable.ic_forward_5)
                .addCustomAction(ACTION_SLOWER, "Langsamer", R.drawable.ic_speed_slower)
                .addCustomAction(ACTION_FASTER, "Schneller", R.drawable.ic_speed_faster)
                .build(),
        )
    }

    private fun publishMetadata() {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, speedLine())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, controller.durationMs.toLong())
                .build(),
        )
    }

    private fun title(): String =
        if (controller.segmentCount > 1) {
            "Nachricht ${controller.currentIndex + 1} von ${controller.segmentCount}"
        } else {
            "Sprachnachricht"
        }

    // The speed buttons are bare symbols, so the speed they lead to is spelled out here.
    private fun speedLine(): String = "Tempo ${controller.speed.label}"

    // Android 13 and later show media notifications without the notification permission, and
    // before that no permission is needed at all - so there is nothing to ask the user for.
    @SuppressLint("MissingPermission")
    private fun post() {
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun hide() {
        PlaybackService.stop()
        manager.cancel(NOTIFICATION_ID)
        session.isActive = false
        shown = false
        shownLook = null
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PlaybackActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback"

        private const val ACTION_REWIND = "de.ilianp.audiotranskript.playback.REWIND"
        private const val ACTION_PLAY_PAUSE = "de.ilianp.audiotranskript.playback.PLAY_PAUSE"
        private const val ACTION_FORWARD = "de.ilianp.audiotranskript.playback.FORWARD"
        private const val ACTION_SLOWER = "de.ilianp.audiotranskript.playback.SLOWER"
        private const val ACTION_FASTER = "de.ilianp.audiotranskript.playback.FASTER"
        private const val ACTION_DISMISSED = "de.ilianp.audiotranskript.playback.DISMISSED"

        /** The notifier of the player on screen, for [PlaybackService] to take its notification from. */
        var active: PlaybackNotifier? = null
            private set

        /** For a service that finds no player any more; it is removed straight away again. */
        fun placeholder(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Sprachnachricht")
                .setSilent(true)
                .build()
    }
}
