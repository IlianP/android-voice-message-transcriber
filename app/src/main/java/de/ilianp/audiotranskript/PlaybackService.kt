package de.ilianp.audiotranskript

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app in the foreground while a message plays with the app out of sight.
 *
 * The playing itself stays with [MessagePlayerController]; this service does nothing but hold the
 * playback notification of [PlaybackNotifier] as a foreground notification. That is what makes
 * it non-dismissible while the message plays, and what keeps Android from reclaiming the app
 * halfway through a long message once it is in the background.
 *
 * It runs only while playing. On pause it leaves the foreground again but leaves the notification
 * behind, as music apps do: still there to resume from, but now dismissible.
 */
class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifier = PlaybackNotifier.active
        // A service started with startForegroundService() must go to the foreground, even if
        // there is nothing left to show by the time it gets here - otherwise the app crashes.
        val notification = notifier?.buildNotification() ?: PlaybackNotifier.placeholder(this)
        ServiceCompat.startForeground(
            this,
            PlaybackNotifier.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        when {
            notifier == null -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // Paused between being asked to start and getting here: nothing to hold on to.
            !notifier.isPlaying -> leave()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Stops, but leaves the notification standing for [PlaybackNotifier] to update or remove. */
    private fun leave() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
        if (running === this) running = null
    }

    companion object {
        private var running: PlaybackService? = null

        val isRunning: Boolean get() = running != null

        /**
         * Asks for the foreground. False when Android refuses: from Android 12 on, an app in the
         * background may not start a foreground service except in reply to the user (a tap on
         * the notification counts) - resuming after a phone call, say, does not. Playback goes on
         * regardless; the notification is then just an ordinary one.
         */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            true
        } catch (_: IllegalStateException) {
            false
        }

        fun stop() {
            running?.leave()
        }
    }
}
