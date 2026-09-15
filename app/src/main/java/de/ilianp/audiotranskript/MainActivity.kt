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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
     * A shared message together with the number of the delivery it arrived in.
     *
     * The counter is what makes the same message shared twice two separate events: state
     * compares by equality, so a bare URI assigned a second time would look unchanged and
     * the screen would sit on the old transcript instead of starting over.
     */
    private data class SharedAudio(val uri: Uri, val deliveryId: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deliver(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val message = shared.value
                    AppScreen(
                        sharedUri = message?.uri,
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
        val uri = extractSharedAudio(intent) ?: return
        shared.value = SharedAudio(uri, ++shareCount)
    }

    private fun extractSharedAudio(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

/**
 * [shareDeliveryId] rises with every share reaching the app, so that the same message shared
 * twice in a row is still handled twice. It has no meaning of its own beyond being different.
 */
@Composable
fun AppScreen(sharedUri: Uri?, shareDeliveryId: Int = 0) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    val history = remember { TranscriptHistory(context) }

    var openRouterKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var sonioxKey by remember { mutableStateOf(settings.sonioxApiKey) }
    var langCode by remember { mutableStateOf(settings.languageCode) }
    var savedHint by remember { mutableStateOf(false) }

    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var debugLog by remember { mutableStateOf(DebugLog.get(context)) }

    // What the player plays: the message just shared or picked, or the stored copy of an
    // entry taken from the history.
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var historyExpanded by remember { mutableStateOf(false) }

    // Set while the transcript on screen comes from the history instead of this run, so the
    // panel can say so rather than passing off an old message as a fresh one.
    var restoredAt by remember { mutableStateOf<Long?>(null) }

    val hasKey = openRouterKey.isNotBlank() || groqKey.isNotBlank() || sonioxKey.isNotBlank()

    // Keys are entered once, so the settings stay folded away - except on a fresh install, where
    // there is nothing to transcribe with yet and the user has to get to them.
    var settingsExpanded by remember { mutableStateOf(!hasKey) }

    /** Puts a stored transcription back on screen, audio included - no provider involved. */
    fun showFromHistory(entry: HistoryEntry) {
        job?.cancel()
        running = false
        activeUri = history.audioUri(entry)
        result = entry.transcript
        error = null
        restoredAt = entry.createdAt
    }

    fun startTranscription(uri: Uri) {
        if (running) return
        running = true
        result = null
        error = null
        restoredAt = null
        elapsedSeconds = 0
        job = scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) { readAudio(context, uri) }
                val text = WizperClient.transcribe(
                    payload,
                    openRouterKey,
                    groqKey,
                    sonioxKey,
                    langCode,
                ) { info ->
                    DebugLog.addSonioxJob(context, info)
                    debugLog = DebugLog.get(context)
                }
                result = text
                // Stored right away, audio and all: from here on the message survives the app
                // being swiped away, which the grant on a shared URI does not.
                entries = withContext(Dispatchers.IO) { history.add(text, payload) }
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

    /** Hands the screen over to another message, dropping whatever was still running for the
     *  previous one. */
    fun takeOver(uri: Uri) {
        job?.cancel()
        running = false
        activeUri = uri
        result = null
        error = null
        restoredAt = null
        if (hasKey) startTranscription(uri)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) takeOver(uri)
    }

    // Keyed on the intent's message, not on Unit: a share arriving while the app is already
    // open (onNewIntent) replaces what is on screen and starts straight away, even if the
    // previous message is still being transcribed.
    LaunchedEffect(sharedUri, shareDeliveryId) {
        if (sharedUri != null) takeOver(sharedUri)
    }

    // Opened from the launcher rather than from a share: bring the last transcription back,
    // so the app is not simply blank after Android cleared it out of memory.
    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) { history.entries() }
        entries = stored
        if (sharedUri == null && activeUri == null && result == null) {
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

    Scaffold(
        bottomBar = {
            // Pinned to the bottom: the message stays playable however far the transcript below
            // it has been scrolled, and the controls stay in reach of the thumb.
            activeUri?.let { MessagePlayerBar(uri = it) }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Audio-Transkript", style = MaterialTheme.typography.headlineSmall)

            SettingsSection(
                expanded = settingsExpanded,
                onToggle = { settingsExpanded = !settingsExpanded; savedHint = false },
                summary = settingsSummary(openRouterKey, groqKey, sonioxKey, langCode),
                openRouterKey = openRouterKey,
                onOpenRouterKeyChange = { openRouterKey = it; savedHint = false },
                groqKey = groqKey,
                onGroqKeyChange = { groqKey = it; savedHint = false },
                sonioxKey = sonioxKey,
                onSonioxKeyChange = { sonioxKey = it; savedHint = false },
                langCode = langCode,
                onLangSelect = { langCode = it; savedHint = false },
                savedHint = savedHint,
                onSave = {
                    settings.openRouterApiKey = openRouterKey
                    settings.groqApiKey = groqKey
                    settings.sonioxApiKey = sonioxKey
                    settings.languageCode = langCode
                    openRouterKey = settings.openRouterApiKey
                    groqKey = settings.groqApiKey
                    sonioxKey = settings.sonioxApiKey
                    savedHint = true
                    // Saved settings have served their purpose - give the screen back to the transcript.
                    settingsExpanded = false
                },
            )

            if (entries.isNotEmpty()) {
                HistorySection(
                    entries = entries,
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded },
                    onSelect = { entry ->
                        showFromHistory(entry)
                        historyExpanded = false
                    },
                    onClear = {
                        history.clear()
                        entries = emptyList()
                        historyExpanded = false
                        // The stored audio goes with it, so a transcript that came from there
                        // cannot stay on screen with a player pointing at a deleted file.
                        if (restoredAt != null) {
                            result = null
                            activeUri = null
                            restoredAt = null
                        }
                    },
                )
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Audiodatei auswählen")
            }

            val uri = activeUri
            // Also shown without audio: an entry whose copy could not be written, or whose
            // file is gone, still has its text - and that is the part worth reading.
            if (uri != null || result != null) {
                Spacer(Modifier.height(8.dp))
                TranscriptionPanel(
                    running = running,
                    result = result,
                    error = error,
                    hasKey = hasKey,
                    elapsedSeconds = elapsedSeconds,
                    restoredAt = restoredAt,
                    hasAudio = uri != null,
                    onStart = { uri?.let { startTranscription(it) } },
                    onCancel = { job?.cancel(); running = false },
                    onCopy = { result?.let { clipboard.setText(AnnotatedString(it)) } },
                    onShare = { result?.let { shareText(context, it) } },
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Teile eine Sprachnachricht oder Audio-Datei aus einer anderen App (z. B. WhatsApp) mit \"Audio-Transkript\" – oder wähle oben eine Datei aus – um sie zu transkribieren.",
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

/**
 * API keys and the language choice, folded behind a flat header.
 *
 * These are entered once and then barely touched, so they should not take up the top of the
 * screen on every run - the transcript and the player are what the app is actually for.
 */
@Composable
private fun SettingsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String,
    openRouterKey: String,
    onOpenRouterKeyChange: (String) -> Unit,
    groqKey: String,
    onGroqKeyChange: (String) -> Unit,
    sonioxKey: String,
    onSonioxKeyChange: (String) -> Unit,
    langCode: String,
    onLangSelect: (String) -> Unit,
    savedHint: Boolean,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Einstellungen", style = MaterialTheme.typography.titleSmall)
                if (!expanded) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    "Einstellungen zuklappen"
                } else {
                    "Einstellungen aufklappen"
                },
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = onOpenRouterKeyChange,
                    label = { Text("OpenRouter API-Key") },
                    supportingText = {
                        Text("Primäres Modell: MAI-Transcribe-2 von Microsoft, rund 0,10 $ pro Stunde Audio.")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = groqKey,
                    onValueChange = onGroqKeyChange,
                    label = { Text("Groq API-Key (Fallback, optional)") },
                    supportingText = {
                        Text("Greift nur, wenn MAI-Transcribe-2 fehlschlägt.")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = sonioxKey,
                    onValueChange = onSonioxKeyChange,
                    label = { Text("Soniox API-Key (letzter Fallback, optional)") },
                    supportingText = {
                        Text("Greift nur, wenn auch Groq fehlschlägt. Audio wird kurz hochgeladen und direkt nach der Transkription wieder gelöscht.")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                LanguageDropdown(selectedCode = langCode, onSelect = onLangSelect)

                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Einstellungen speichern")
                }
            }
        }

        // Sits outside the fold: saving collapses the section, and the confirmation still needs
        // somewhere to show up.
        if (savedHint) {
            Text(
                "Gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        HorizontalDivider()
    }
}

/** One line describing the folded settings: which provider runs, and in which language. */
private fun settingsSummary(
    openRouterKey: String,
    groqKey: String,
    sonioxKey: String,
    langCode: String,
): String {
    val provider = when {
        openRouterKey.isNotBlank() -> "MAI-Transcribe-2"
        groqKey.isNotBlank() -> "Groq Whisper"
        sonioxKey.isNotBlank() -> "Soniox"
        else -> return "Kein API-Key gesetzt"
    }
    return "$provider · ${languageLabelFor(langCode)}"
}

private fun languageLabelFor(code: String): String =
    LANGUAGE_OPTIONS.firstOrNull { it.code == code }?.label ?: LANGUAGE_OPTIONS.first().label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selectedCode: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = languageLabelFor(selectedCode)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sprache") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LANGUAGE_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TranscriptionPanel(
    running: Boolean,
    result: String?,
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
                        Text("Wird transkribiert … (${elapsedSeconds}s)")
                    }
                    OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
                }

                error != null -> {
                    Text("Fehler", style = MaterialTheme.typography.titleMedium)
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onStart, enabled = hasAudio) { Text("Erneut versuchen") }
                }

                result != null -> {
                    Text("Transkription", style = MaterialTheme.typography.titleMedium)
                    if (restoredAt != null) {
                        Text(
                            "Aus dem Verlauf · ${relativeTime(restoredAt)}" +
                                if (!hasAudio) " · Audio nicht mehr vorhanden" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SelectionContainer {
                        Text(result)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                        OutlinedButton(onClick = onShare) { Text("Teilen") }
                        if (hasAudio) OutlinedButton(onClick = onStart) { Text("Neu") }
                    }
                }

                else -> {
                    if (!hasKey) {
                        Text("Bitte zuerst oben unter \"Einstellungen\" einen API-Key eintragen und speichern.")
                    }
                    Button(onClick = onStart, enabled = hasKey && hasAudio) { Text("Transkribieren") }
                }
            }
        }
    }
}

/**
 * The last few transcriptions, folded away behind a one-line header.
 *
 * Tapping an entry puts it back on screen with its audio, straight from local storage - no
 * second trip through a transcription API, and no cost.
 */
@Composable
private fun HistorySection(
    entries: List<HistoryEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Verlauf (${entries.size})", style = MaterialTheme.typography.titleSmall)
                if (!expanded) {
                    Text(
                        "Zuletzt: ${relativeTime(entries.first().createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Verlauf zuklappen" else "Verlauf aufklappen",
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                entries.forEach { entry ->
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            relativeTime(entry.createdAt),
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
                HorizontalDivider()

                Text(
                    "Bleibt nur auf diesem Gerät: die letzten ${TranscriptHistory.MAX_ENTRIES} " +
                        "Nachrichten, höchstens sieben Tage lang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Verlauf löschen")
                }
            }
        }

        HorizontalDivider()
    }
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

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Transkription teilen"))
}
