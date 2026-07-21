package de.ilianp.audiotranskript

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Playback speeds offered for listening while reading. */
enum class PlaybackSpeed(val factor: Float, val label: String) {
    X1(1.0f, "1×"),
    X1_5(1.5f, "1,5×"),
    X2(2.0f, "2×"),
    X2_5(2.5f, "2,5×");

    companion object {
        fun fromFactor(factor: Float): PlaybackSpeed =
            entries.firstOrNull { it.factor == factor } ?: X1
    }
}

private const val SKIP_MS = 10_000

/** Volume used while another app briefly ducks us (e.g. a navigation prompt). */
private const val DUCK_VOLUME = 0.2f

/**
 * Holds a [MediaPlayer] and exposes its state to Compose. Speed is applied only while
 * playing (setting [MediaPlayer.setPlaybackParams] on a paused player can auto-resume on
 * some devices), and re-applied whenever playback starts.
 */
@Stable
class MessagePlayerController(
    private val context: Context,
    private val uri: Uri,
    initialSpeed: PlaybackSpeed = PlaybackSpeed.X1,
) {
    var isPrepared by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0)
        private set
    var positionMs by mutableStateOf(0)
        private set
    var speed by mutableStateOf(initialSpeed)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** True while we currently hold audio focus, so we never abandon a request we never made. */
    private var hasAudioFocus = false

    /** Set when a transient focus loss paused us, so we resume once focus returns. */
    private var resumeOnFocusGain = false

    // Declared as spoken media. On Android Auto (and some Bluetooth setups) the car keeps the
    // media channel muted until an app both declares proper attributes AND holds audio focus,
    // so without this the playback is silent unless something else is already holding the
    // channel open. Requesting focus is what makes the car route and unmute our stream.
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Someone took over for good (another media app): stop and let focus go.
                pausePlayback()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Brief interruption (call, navigation prompt): pause, resume afterwards.
                resumeOnFocusGain = isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player?.setVolume(DUCK_VOLUME, DUCK_VOLUME)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(1f, 1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    startPlayback()
                }
            }
        }
    }

    // Transient gain: we only need focus for the length of the message, and background music
    // should resume once we are done rather than being stopped outright.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    fun prepare() {
        if (player != null) return
        val mp = MediaPlayer()
        mp.setAudioAttributes(audioAttributes)
        try {
            mp.setDataSource(context, uri)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Wiedergabe nicht möglich."
            mp.release()
            return
        }
        // These lambdas take a MediaPlayer parameter (not a receiver), so property
        // assignments below resolve to this controller, not to the MediaPlayer.
        mp.setOnPreparedListener { prepared ->
            isPrepared = true
            durationMs = prepared.duration.coerceAtLeast(0)
        }
        mp.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
            abandonAudioFocus()
        }
        mp.setOnErrorListener { _, what, extra ->
            errorMessage = "Wiedergabe nicht möglich (Code $what/$extra)."
            isPlaying = false
            abandonAudioFocus()
            true
        }
        player = mp
        mp.prepareAsync()
    }

    fun togglePlayPause() {
        val mp = player ?: return
        if (!isPrepared) return
        if (mp.isPlaying) {
            pausePlayback()
            abandonAudioFocus()
        } else {
            if (durationMs > 0 && positionMs >= durationMs) {
                mp.seekTo(0)
                positionMs = 0
            }
            if (requestAudioFocus()) {
                errorMessage = null
                startPlayback()
            } else {
                errorMessage =
                    "Wiedergabe momentan nicht möglich – Audio wird gerade anderweitig genutzt."
            }
        }
    }

    fun changeSpeed(newSpeed: PlaybackSpeed) {
        speed = newSpeed
        val mp = player ?: return
        if (isPlaying) applySpeed(mp)
    }

    fun seekTo(ms: Int) {
        val mp = player ?: return
        if (!isPrepared) return
        val clamped = ms.coerceIn(0, durationMs)
        mp.seekTo(clamped)
        positionMs = clamped
    }

    /** Seeks [deltaMs] relative to the current position (clamped to the clip bounds). */
    fun skip(deltaMs: Int) {
        if (!isPrepared) return
        seekTo(positionMs + deltaMs)
    }

    /** Called periodically while playing to keep the progress bar in sync. */
    fun syncPosition() {
        val mp = player ?: return
        if (isPlaying && mp.isPlaying) positionMs = mp.currentPosition
    }

    fun release() {
        abandonAudioFocus()
        player?.release()
        player = null
        isPlaying = false
        isPrepared = false
    }

    private fun startPlayback() {
        val mp = player ?: return
        applySpeed(mp)
        mp.setVolume(1f, 1f)
        mp.start()
        isPlaying = true
    }

    private fun pausePlayback() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        isPlaying = false
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        hasAudioFocus = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        resumeOnFocusGain = false
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
    }

    private fun applySpeed(mp: MediaPlayer) {
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(speed.factor)
        } catch (_: IllegalStateException) {
            // Player not in a valid state for playback params; ignore.
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000.0).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * A compact audio player for the shared voice message: a play/pause button, a seek bar
 * with elapsed/total time, and speed chips (1× … 2,5×) so you can listen while reading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessagePlayerCard(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val controller = remember(uri) {
        MessagePlayerController(context, uri, PlaybackSpeed.fromFactor(settings.playbackSpeedFactor))
    }

    DisposableEffect(uri) {
        controller.prepare()
        onDispose { controller.release() }
    }

    LaunchedEffect(controller.isPlaying) {
        while (controller.isPlaying) {
            controller.syncPosition()
            delay(100)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sprachnachricht anhören", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val atEnd = controller.durationMs > 0 && controller.positionMs >= controller.durationMs
                IconButton(
                    onClick = { controller.skip(-SKIP_MS) },
                    enabled = controller.isPrepared,
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = "10 Sekunden zurück")
                }
                FilledIconButton(
                    onClick = { controller.togglePlayPause() },
                    enabled = controller.isPrepared,
                    modifier = Modifier.size(56.dp),
                ) {
                    val icon = when {
                        controller.isPlaying -> Icons.Filled.Pause
                        atEnd -> Icons.Filled.Replay
                        else -> Icons.Filled.PlayArrow
                    }
                    val desc = when {
                        controller.isPlaying -> "Pause"
                        atEnd -> "Erneut abspielen"
                        else -> "Abspielen"
                    }
                    Icon(icon, contentDescription = desc)
                }
                IconButton(
                    onClick = { controller.skip(SKIP_MS) },
                    enabled = controller.isPrepared,
                ) {
                    Icon(Icons.Filled.Forward10, contentDescription = "10 Sekunden vor")
                }

                Column(modifier = Modifier.weight(1f)) {
                    val range = controller.durationMs.coerceAtLeast(1).toFloat()
                    Slider(
                        value = controller.positionMs.coerceIn(0, controller.durationMs).toFloat(),
                        onValueChange = { controller.seekTo(it.roundToInt()) },
                        valueRange = 0f..range,
                        enabled = controller.isPrepared && controller.durationMs > 0,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatTime(controller.positionMs), style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(controller.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Tempo", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaybackSpeed.entries.forEach { option ->
                        FilterChip(
                            selected = controller.speed == option,
                            onClick = {
                                controller.changeSpeed(option)
                                settings.playbackSpeedFactor = option.factor
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            controller.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
