package de.ilianp.audiotranskript

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /**
     * The message currently shared into the app.
     *
     * Held as state instead of being read once, because the activity runs in `singleTask`
     * mode: a second share reaches this very instance through [onNewIntent] rather than
     * starting a new one. That launch mode is also what gives the app its own entry in the
     * recents list - launched plainly, it would be stacked into the sharing app's task and
     * only ever show up under WhatsApp's card.
     */
    private val shared = mutableStateOf<SharedAudio?>(null)

    private var shareCount = 0

    /**
     * A shared batch together with the number of the delivery it arrived in.
     *
     * [uris] is the whole batch - the messages parked by „Zwischenspeichern“ first, then the
     * shared one(s) - resolved once when the share arrives, see [deliver].
     *
     * The counter is what makes the same message shared twice two separate events: state
     * compares by equality, so a bare URI assigned a second time would look unchanged and
     * the screen would sit on the old transcript instead of starting over.
     */
    private data class SharedAudio(val uris: List<Uri>, val deliveryId: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated (rotation, dark mode): the share was already delivered, and its queue already
        // taken. Delivering the intent again would take the queue a second time - by then empty,
        // and taking it clears the batch taken the first time - so the batch comes back from the
        // saved state instead.
        val restored = savedInstanceState?.let { state ->
            val uris = savedUris(state)
            shareCount = state.getInt(KEY_SHARE_COUNT)
            if (uris.isNullOrEmpty()) null else SharedAudio(uris, shareCount)
        }
        if (savedInstanceState == null) deliver(intent) else shared.value = restored
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val message = shared.value
                    AppScreen(
                        sharedUris = message?.uris.orEmpty(),
                        shareDeliveryId = message?.deliveryId ?: 0,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    /** Takes the message out of [intent], if it carries one - a plain launcher tap does not,
     *  and must not wipe what is on screen. */
    private fun deliver(intent: Intent?) {
        val uris = sharedAudioUris(intent).ifEmpty { return }
        val batch = PendingQueue(this).takeAll() + uris
        shared.value = SharedAudio(batch, ++shareCount)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SHARE_COUNT, shareCount)
        shared.value?.let { outState.putParcelableArrayList(KEY_BATCH, ArrayList(it.uris)) }
    }

    private fun savedUris(state: Bundle): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) {
            state.getParcelableArrayList(KEY_BATCH, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            state.getParcelableArrayList(KEY_BATCH)
        }

    private companion object {
        const val KEY_BATCH = "shared_batch"
        const val KEY_SHARE_COUNT = "share_count"
    }
}

/**
 * [sharedUris] is the batch that reached the app through „Transkript starten“: the messages
 * parked with „Zwischenspeichern“ first, then the shared one(s). The activity takes them out of
 * the [PendingQueue] when the share arrives, so that a recreated activity gets the same batch
 * back instead of taking the queue a second time.
 *
 * [shareDeliveryId] rises with every share reaching the app, so that the same message shared
 * twice in a row is still handled twice. It has no meaning of its own beyond being different.
 */
@Composable
fun AppScreen(sharedUris: List<Uri>, shareDeliveryId: Int = 0) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    // Reads the limits on every access, so ones changed in the settings apply straight away.
    val history = remember { TranscriptHistory(context) { settings.historyLimits } }
    val queue = remember { PendingQueue(context) }

    var openRouterKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var sonioxKey by remember { mutableStateOf(settings.sonioxApiKey) }
    var langCode by remember { mutableStateOf(settings.languageCode) }
    // A screen of its own, swapped in for this one - see [SettingsScreen].
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Hoisted, so a trip to the settings does not throw the transcript back to its top.
    val scrollState = rememberScrollState()

    // What is on screen survives the activity being recreated (rotation, dark mode): otherwise
    // the transcript would vanish, and a run cut off by it would never finish.
    var running by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    // One transcript per message of the batch; [result] is these joined.
    var segments by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(emptyList()) }
    var doneCount by remember { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var debugLog by remember { mutableStateOf(DebugLog.get(context)) }

    // What the player plays: the message(s) just shared or picked, or the stored copies of an
    // entry taken from the history. More than one for a batch.
    var activeUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(emptyList()) }
    val player = rememberMessagePlayer(activeUris)
    var pendingCount by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var historyExpanded by remember { mutableStateOf(false) }

    // Set while the transcript on screen comes from the history instead of this run, so the
    // panel can say so rather than passing off an old message as a fresh one.
    var restoredAt by rememberSaveable { mutableStateOf<Long?>(null) }

    // The share last acted on. Saved, so a recreated screen does not take the same share for a
    // new one and start it over - by then the batch on screen may be a different one entirely.
    var handledDelivery by rememberSaveable { mutableIntStateOf(-1) }

    // The summary of the transcript on screen, made only on request. [entryId] is its history
    // entry, so that a finished summary is stored alongside the transcript it belongs to.
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    var summary by rememberSaveable { mutableStateOf<String?>(null) }
    var summaryError by rememberSaveable { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryJob by remember { mutableStateOf<Job?>(null) }

    val hasKey = openRouterKey.isNotBlank() || groqKey.isNotBlank() || sonioxKey.isNotBlank()

    /** Drops the summary on screen, and one still being made, along with the transcript it was for. */
    fun resetSummary() {
        summaryJob?.cancel()
        summarizing = false
        summary = null
        summaryError = null
        entryId = null
    }

    /**
     * Takes the transcript off the screen once its history entry is gone - deleted, or dropped by
     * tighter limits. Only the entry [entryId] points at: its stored audio went with it, and a
     * player left pointing at a deleted file would have nothing to play.
     */
    fun dropIfGone(remaining: List<HistoryEntry>) {
        val shown = entryId ?: return
        if (remaining.any { it.id == shown }) return
        job?.cancel()
        running = false
        result = null
        segments = emptyList()
        error = null
        activeUris = emptyList()
        restoredAt = null
        resetSummary()
    }

    fun closeSettings(limits: HistoryLimits) {
        settingsOpen = false
        // Typed without trimming; the store trims, so read the keys back as they will be used.
        openRouterKey = settings.openRouterApiKey
        groqKey = settings.groqApiKey
        sonioxKey = settings.sonioxApiKey
        if (limits != settings.historyLimits) {
            settings.historyLimits = limits
            scope.launch {
                entries = withContext(Dispatchers.IO) { history.entries() }
                dropIfGone(entries)
            }
        }
    }

    /** Puts a stored transcription back on screen, audio included - no provider involved. */
    fun showFromHistory(entry: HistoryEntry) {
        job?.cancel()
        running = false
        resetSummary()
        activeUris = history.audioUris(entry)
        result = entry.transcript
        segments = entry.segments
        error = null
        restoredAt = entry.createdAt
        entryId = entry.id
        summary = entry.summary
    }

    fun startSummary() {
        val texts = segments.ifEmpty { listOfNotNull(result) }
        if (summarizing || texts.isEmpty() || openRouterKey.isBlank()) return
        summarizing = true
        summaryError = null
        val forEntry = entryId
        summaryJob = scope.launch {
            try {
                val text = SummaryClient.summarize(texts, openRouterKey)
                summary = text
                if (forEntry != null) {
                    entries = withContext(Dispatchers.IO) { history.setSummary(forEntry, text) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                summaryError = e.message ?: "Unbekannter Fehler"
            } finally {
                // Same as for the transcription: a cancelled run leaves the flag to its successor.
                if (isActive) summarizing = false
            }
        }
    }

    fun startTranscription(uris: List<Uri>) {
        if (running || uris.isEmpty()) return
        running = true
        result = null
        segments = emptyList()
        error = null
        restoredAt = null
        resetSummary()
        elapsedSeconds = 0
        doneCount = 0
        job = scope.launch {
            try {
                val payloads = withContext(Dispatchers.IO) { readBatch(context, uris) }
                val texts = WizperClient.transcribeAll(
                    payloads,
                    openRouterKey,
                    groqKey,
                    sonioxKey,
                    langCode,
                    onProgress = { doneCount = it },
                ) { info ->
                    DebugLog.addSonioxJob(context, info)
                    debugLog = DebugLog.get(context)
                }
                segments = texts
                result = WizperClient.joinTranscripts(texts)
                // Stored right away, audio and all: from here on the message survives the app
                // being swiped away, which the grant on a shared URI does not.
                entries = withContext(Dispatchers.IO) { history.add(texts, payloads) }
                // The entry just written is the newest one.
                entryId = entries.firstOrNull()?.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unbekannter Fehler"
            } finally {
                // Only a run that finished on its own owns this flag. A cancelled one lands
                // here too, but by then a newer message may already be running, and clearing
                // the flag would let the screen claim nothing is going on.
                if (isActive) running = false
            }
        }
    }

    /**
     * Hands the screen over to another message - or batch - dropping whatever was still running
     * for the previous one. With [withQueue], the messages parked by „Zwischenspeichern“ go
     * first, in the order they were shared, and the queue is empty afterwards.
     */
    fun takeOver(uris: List<Uri>, withQueue: Boolean) {
        job?.cancel()
        running = false
        val batch = if (withQueue) queue.takeAll() + uris else uris
        if (withQueue) pendingCount = 0
        if (batch.isEmpty()) return
        activeUris = batch
        result = null
        segments = emptyList()
        error = null
        restoredAt = null
        resetSummary()
        if (hasKey) startTranscription(batch)
    }

    // Several files at once are one batch, just like several shared together.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) takeOver(uris, withQueue = false)
    }

    // Keyed on the intent's message, not on Unit: a share arriving while the app is already
    // open (onNewIntent) replaces what is on screen and starts straight away, even if the
    // previous message is still being transcribed.
    LaunchedEffect(sharedUris, shareDeliveryId) {
        if (sharedUris.isNotEmpty() && shareDeliveryId != handledDelivery) {
            handledDelivery = shareDeliveryId
            takeOver(sharedUris, withQueue = false)
        }
    }

    // A run that the recreation cut off (its coroutine died with the old screen) starts again
    // for the same batch. Read from the restored state before any effect runs: by the time this
    // one does, a fresh share may already have set the flag for a run of its own.
    val cutOffRun = remember { running }
    LaunchedEffect(Unit) {
        if (cutOffRun) {
            running = false
            startTranscription(activeUris)
        }
    }

    // „Zwischenspeichern“ adds to the queue without ever showing this screen, so the count is
    // looked up again whenever the app comes back to the front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) pendingCount = queue.count()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Opened from the launcher rather than from a share: bring the last transcription back,
    // so the app is not simply blank after Android cleared it out of memory.
    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) { history.entries() }
        entries = stored
        pendingCount = queue.count()
        if (sharedUris.isEmpty() && activeUris.isEmpty() && result == null) {
            stored.firstOrNull()?.let { showFromHistory(it) }
        }
    }

    // Safety net: if a previous run was killed before it could clean up, the audio would sit on
    // Soniox indefinitely (they never expire uploads themselves). Sweep it here, off the hot path.
    LaunchedEffect(Unit) {
        val key = settings.sonioxApiKey
        if (key.isBlank()) return@LaunchedEffect
        val removed = runCatching { SonioxClient.cleanUpLeftovers(key) }.getOrDefault(0)
        if (removed > 0) {
            DebugLog.addSonioxJob(context, "$removed Reste beim Start aufgeräumt")
            debugLog = DebugLog.get(context)
        }
    }

    LaunchedEffect(running) {
        if (running) {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    if (settingsOpen) {
        SettingsScreen(
            history = history,
            openRouterKey = openRouterKey,
            onOpenRouterKeyChange = { openRouterKey = it; settings.openRouterApiKey = it },
            groqKey = groqKey,
            onGroqKeyChange = { groqKey = it; settings.groqApiKey = it },
            sonioxKey = sonioxKey,
            onSonioxKeyChange = { sonioxKey = it; settings.sonioxApiKey = it },
            langCode = langCode,
            onLangSelect = { langCode = it; settings.languageCode = it },
            limits = settings.historyLimits,
            onClearHistory = {
                history.clear()
                entries = emptyList()
                historyExpanded = false
                dropIfGone(entries)
            },
            onClose = { closeSettings(it) },
        )
        return
    }

    Scaffold(
        bottomBar = {
            // Pinned to the bottom: the message stays playable however far the transcript below
            // it has been scrolled, and the controls stay in reach of the thumb.
            player?.let { MessagePlayerBar(controller = it) }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One line for everything that is not the message itself: history, file, settings.
            HeaderWithHistory(
                entries = entries,
                expanded = historyExpanded,
                onToggle = { historyExpanded = !historyExpanded },
                onSelect = { entry ->
                    showFromHistory(entry)
                    historyExpanded = false
                },
                onDelete = { entry ->
                    scope.launch {
                        entries = withContext(Dispatchers.IO) { history.delete(entry.id) }
                        if (entries.isEmpty()) historyExpanded = false
                        dropIfGone(entries)
                    }
                },
                onPickFile = { picker.launch(arrayOf("audio/*")) },
                onOpenSettings = { settingsOpen = true },
            )

            // The rest of the column keeps its old right margin; only the header's icons move
            // out to the edge, so their touch targets do not eat into the row.
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasKey) {
                    MissingKeyCard(onOpenSettings = { settingsOpen = true })
                }

                if (pendingCount > 0) {
                    PendingSection(
                        count = pendingCount,
                        onStart = { takeOver(emptyList(), withQueue = true) },
                        onDiscard = {
                            queue.clear()
                            pendingCount = 0
                        },
                    )
                }

                val uris = activeUris
                // Also shown without audio: an entry whose copy could not be written, or whose
                // file is gone, still has its text - and that is the part worth reading.
                if (uris.isNotEmpty() || result != null) {
                    // Above the transcript: whoever wants the summary wants to read it first.
                    if (result != null && !running && error == null && openRouterKey.isNotBlank()) {
                        SummarySection(
                            summary = summary,
                            summarizing = summarizing,
                            error = summaryError,
                            totalDurationMs = player?.durationMs ?: 0,
                            messageCount = segments.size,
                            onSummarize = { startSummary() },
                            onCancel = { summaryJob?.cancel(); summarizing = false },
                            onCopy = { summary?.let { clipboard.setText(AnnotatedString(it)) } },
                            onShare = { summary?.let { shareText(context, it, "Zusammenfassung teilen") } },
                        )
                    }
                    TranscriptionPanel(
                        running = running,
                        result = result,
                        segments = segments,
                        doneCount = doneCount,
                        totalCount = uris.size,
                        playingSegment = player?.takeIf { it.isPlaying }?.currentIndex,
                        onPlaySegment = { player?.playSegment(it) },
                        error = error,
                        hasKey = hasKey,
                        elapsedSeconds = elapsedSeconds,
                        restoredAt = restoredAt,
                        hasAudio = uris.isNotEmpty(),
                        onStart = { startTranscription(uris) },
                        onCancel = { job?.cancel(); running = false },
                        onCopy = { result?.let { clipboard.setText(AnnotatedString(it)) } },
                        onShare = { result?.let { shareText(context, it) } },
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Teile eine Sprachnachricht oder Audio-Datei aus einer anderen App (z. B. WhatsApp) mit „Audio-Transkript“ → „Transkript starten“ – oder wähle über das Ordner-Symbol oben eine Datei aus – um sie zu transkribieren. " +
                            "Mehrere Nachrichten am Stück: alle bis auf die letzte mit „Zwischenspeichern“ teilen, die letzte mit „Transkript starten“.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(8.dp))
                    DebugPanel(
                        log = debugLog,
                        onCopy = { clipboard.setText(AnnotatedString(debugLog)) },
                        onClear = { DebugLog.clear(context); debugLog = "" },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptionPanel(
    running: Boolean,
    result: String?,
    segments: List<String>,
    doneCount: Int,
    totalCount: Int,
    playingSegment: Int?,
    onPlaySegment: (Int) -> Unit,
    error: String?,
    hasKey: Boolean,
    elapsedSeconds: Int,
    restoredAt: Long?,
    hasAudio: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                running -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        val progress = if (totalCount > 1) " $doneCount von $totalCount fertig" else ""
                        Text("Wird transkribiert …$progress (${elapsedSeconds}s)")
                    }
                    OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
                }

                error != null -> {
                    Text("Fehler", style = MaterialTheme.typography.titleMedium)
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onStart, enabled = hasAudio) { Text("Erneut versuchen") }
                }

                result != null -> {
                    val batch = segments.size > 1
                    Text(
                        if (batch) "Transkription · ${segments.size} Nachrichten" else "Transkription",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (restoredAt != null) {
                        Text(
                            "Aus dem Verlauf · ${relativeTime(restoredAt)}" +
                                if (!hasAudio) " · Audio nicht mehr vorhanden" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (batch) {
                        segments.forEachIndexed { index, text ->
                            SegmentHeader(
                                number = index + 1,
                                playing = playingSegment == index,
                                enabled = hasAudio,
                                onClick = { onPlaySegment(index) },
                            )
                            SelectionContainer { Text(text) }
                        }
                    } else {
                        SelectionContainer {
                            Text(result)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                        OutlinedButton(onClick = onShare) { Text("Teilen") }
                        if (hasAudio) OutlinedButton(onClick = onStart) { Text("Neu") }
                    }
                }

                else -> {
                    if (!hasKey) {
                        Text("Bitte zuerst in den Einstellungen (Zahnrad oben rechts) einen API-Key eintragen.")
                    }
                    Button(onClick = onStart, enabled = hasKey && hasAudio) { Text("Transkribieren") }
                }
            }
        }
    }
}

/**
 * The summary of the transcript below it - offered, being made, failed, or done.
 *
 * From [SummaryClient.SUGGEST_FROM_MS] of audio on (one message or the batch as a whole) the
 * offer is a card of its own; below that it is a plain text button, there if wanted but not in
 * the way. Nothing is summarized without a tap: it sends the transcript to one more provider.
 * [totalDurationMs] is 0 while the player is still preparing, or when there is no audio left.
 */
@Composable
private fun SummarySection(
    summary: String?,
    summarizing: Boolean,
    error: String?,
    totalDurationMs: Int,
    messageCount: Int,
    onSummarize: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    when {
        summary != null -> Card(modifier = Modifier.fillMaxWidth()) {
            var expanded by rememberSaveable { mutableStateOf(true) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Zusammenfassung",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) {
                            "Zusammenfassung zuklappen"
                        } else {
                            "Zusammenfassung aufklappen"
                        },
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectionContainer { Text(summary) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                            OutlinedButton(onClick = onShare) { Text("Teilen") }
                        }
                    }
                }
            }
        }

        summarizing -> Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Wird zusammengefasst …", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
            }
        }

        error != null -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Zusammenfassung fehlgeschlagen", style = MaterialTheme.typography.titleSmall)
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onSummarize) { Text("Erneut versuchen") }
            }
        }

        totalDurationMs >= SummaryClient.SUGGEST_FROM_MS -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val length = formatTime(totalDurationMs)
                Text(
                    if (messageCount > 1) {
                        "$messageCount Nachrichten · $length insgesamt"
                    } else {
                        "Lange Nachricht · $length"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Soll zusätzlich eine kurze Zusammenfassung erstellt werden?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSummarize) { Text("Zusammenfassen") }
            }
        }

        else -> TextButton(onClick = onSummarize) { Text("Zusammenfassung erstellen") }
    }
}

/**
 * Labels one message inside a batch transcript. Tapping it plays the batch from the start of
 * that message; the label of the message playing right now is set in bold.
 */
@Composable
private fun SegmentHeader(number: Int, playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (enabled) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Nachricht $number",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Messages parked with „Zwischenspeichern“, waiting for the last one. Normally that one arrives
 * through „Transkript starten“ and takes them along; this is the way out when it never does -
 * the last message was parked as well, or the batch is not wanted after all.
 */
@Composable
private fun PendingSection(count: Int, onStart: () -> Unit, onDiscard: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (count == 1) "1 Nachricht zwischengespeichert" else "$count Nachrichten zwischengespeichert",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Wird mit der nächsten Nachricht, die du mit „Transkript starten“ teilst, " +
                    "zusammen transkribiert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) { Text("Jetzt transkribieren") }
                OutlinedButton(onClick = onDiscard) { Text("Verwerfen") }
            }
        }
    }
}

/** Shown instead of a silent „Transkribieren“ button that could never work: nothing to send with. */
@Composable
private fun MissingKeyCard(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Noch kein API-Key eingetragen", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ohne Key kann nichts transkribiert werden. Die Keys stehen in den Einstellungen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings) { Text("Einstellungen öffnen") }
        }
    }
}

/**
 * The screen's only header: the history, folded away behind one line, with the file picker and
 * the settings as icons beside it. Everything else on the screen belongs to the message.
 *
 * Tapping an entry puts it back on screen with its audio, straight from local storage - no
 * second trip through a transcription API, and no cost. A long press offers to delete it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderWithHistory(
    entries: List<HistoryEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onPickFile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Verlauf (${entries.size})", style = MaterialTheme.typography.titleSmall)
                    Text(
                        " · ${relativeTime(entries.first().createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Verlauf zuklappen" else "Verlauf aufklappen",
                    )
                }
            } else {
                Text(
                    "Audio-Transkript",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            IconButton(onClick = onPickFile) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Audiodatei auswählen")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
            }
        }

        AnimatedVisibility(visible = expanded && entries.isNotEmpty()) {
            Column(modifier = Modifier.padding(end = 12.dp, bottom = 4.dp)) {
                entries.forEach { entry ->
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelect(entry) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingDelete = entry
                                },
                                onLongClickLabel = "Eintrag löschen",
                            )
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            entryLabel(entry),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            entry.transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(end = 12.dp))
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eintrag löschen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        entryLabel(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(entry.transcript, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Text und Audio werden von diesem Gerät gelöscht.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(entry)
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

private fun entryLabel(entry: HistoryEntry): String {
    val size = entry.segments.size
    return relativeTime(entry.createdAt) + if (size > 1) " · $size Nachrichten" else ""
}

/** Ages in German, independent of the device locale - the rest of the app speaks it too. */
private fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp) / 60_000L).coerceAtLeast(0)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "gerade eben"
        minutes == 1L -> "vor 1 Minute"
        minutes < 60 -> "vor $minutes Minuten"
        hours == 1L -> "vor 1 Stunde"
        hours < 24 -> "vor $hours Stunden"
        days == 1L -> "gestern"
        else -> "vor $days Tagen"
    }
}

@Composable
private fun DebugPanel(
    log: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Debug: Soniox-Jobs", style = MaterialTheme.typography.titleMedium)
            if (log.isBlank()) {
                Text(
                    "Noch keine Jobs. Greift erst, wenn der Soniox-Fallback genutzt wird.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(log, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                    OutlinedButton(onClick = onClear) { Text("Leeren") }
                }
                Text(
                    "Tipp: IDs in der Soniox-Console prüfen – Datei und Job sollten dort nicht mehr auftauchen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun shareText(context: Context, text: String, title: String = "Transkription teilen") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

private val UriListSaver = Saver<List<Uri>, ArrayList<String>>(
    save = { uris -> ArrayList(uris.map { it.toString() }) },
    restore = { saved -> saved.map { Uri.parse(it) } },
)

private val StringListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it },
)
