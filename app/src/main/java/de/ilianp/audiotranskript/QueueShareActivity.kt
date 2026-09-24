package de.ilianp.audiotranskript

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The „Zwischenspeichern“ share target: parks the shared message in [PendingQueue] and gets out
 * of the way again, without ever showing the app.
 *
 * The user is in the middle of going through a chat, so this is deliberately invisible (a
 * translucent theme, no content) and runs in a task of its own (`taskAffinity=""` in the
 * manifest) - otherwise finishing it could bring the app's main screen to the front instead of
 * returning to the chat. A toast is the only trace.
 *
 * The copy is made before [finish]: the read grant on the shared URI belongs to this activity
 * and ends with it.
 */
class QueueShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated mid-copy (rotation): the first instance already took care of it.
        val uris = if (savedInstanceState == null) sharedAudioUris(intent) else emptyList()
        if (uris.isEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val waiting = withContext(Dispatchers.IO) {
                runCatching {
                    // Everything is read before anything is parked: one unreadable message
                    // fails the whole share instead of leaving the others half queued.
                    val payloads = uris.map { readAudio(applicationContext, it) }
                    PendingQueue(applicationContext).addAll(payloads)
                }
            }
            val message = waiting.fold(
                onSuccess = { count ->
                    val what = if (count == 1) "1 Nachricht" else "$count Nachrichten"
                    "$what zwischengespeichert – die letzte mit „Transkript starten“ teilen."
                },
                onFailure = { "Zwischenspeichern fehlgeschlagen: ${it.message}" },
            )
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
