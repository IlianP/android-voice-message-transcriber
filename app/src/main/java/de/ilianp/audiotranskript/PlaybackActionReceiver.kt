package de.ilianp.audiotranskript

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Takes the taps on the playback notification's buttons (Android 12 and older; later versions
 * send them through the media session) and on swiping it away.
 *
 * Declared in the manifest rather than registered by [PlaybackNotifier], because a paused
 * notification can outlive the app: Android may reclaim the process in the background and leave
 * the notification behind. A tap then starts a fresh process with no player in it - nothing left
 * to resume, since the player lived on the screen - so the notification is taken down instead of
 * leaving buttons that do nothing.
 */
class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifier = PlaybackNotifier.active
        if (notifier != null) {
            notifier.handle(action)
        } else {
            NotificationManagerCompat.from(context).cancel(PlaybackNotifier.NOTIFICATION_ID)
        }
    }
}
