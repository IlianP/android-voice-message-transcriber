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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUri = extractSharedAudio(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(sharedUri)
                }
            }
        }
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

@Composable
fun AppScreen(sharedUri: Uri?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }

    var openRouterKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var sonioxKey by remember { mutableStateOf(settings.sonioxApiKey) }
    var langCode by remember { mutableStateOf(settings.languageCode) }
    var savedHint by remember { mutableStateOf(false) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var debugLog by remember { mutableStateOf(DebugLog.get(context)) }

    val hasKey = openRouterKey.isNotBlank() || groqKey.isNotBlank() || sonioxKey.isNotBlank()
    val activeUri = sharedUri ?: pickedUri

    // Keys are entered once, so the settings stay folded away - except on a fresh install, where
    // there is nothing to transcribe with yet and the user has to get to them.
    var settingsExpanded by remember { mutableStateOf(!hasKey) }

    fun startTranscription(uri: Uri) {
        if (running) return
        running = true
        result = null
        error = null
        elapsedSeconds = 0
        job = scope.launch {
            try {
                result = WizperClient.transcribe(
                    context,
                    uri,
                    openRouterKey,
                    groqKey,
                    sonioxKey,
                    langCode,
                ) { info ->
                    DebugLog.addSonioxJob(context, info)
                    debugLog = DebugLog.get(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unbekannter Fehler"
            } finally {
                running = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            result = null
            error = null
            if (hasKey) startTranscription(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (sharedUri != null && hasKey) startTranscription(sharedUri)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        OutlinedButton(
            onClick = { picker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Audiodatei auswählen")
        }

        if (activeUri != null) {
            Spacer(Modifier.height(8.dp))
            // The player sits above the transcript so it keeps its place instead of being pushed
            // around as the transcript below it grows.
            MessagePlayerCard(uri = activeUri)
            TranscriptionPanel(
                running = running,
                result = result,
                error = error,
                hasKey = hasKey,
                elapsedSeconds = elapsedSeconds,
                onStart = { startTranscription(activeUri) },
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
                        Text("Primäres Modell: MAI-Transcribe-2 von Microsoft, rund 0,10 $ pro Stunde Audio.")
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
                    Button(onClick = onStart) { Text("Erneut versuchen") }
                }

                result != null -> {
                    Text("Transkription", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Text(result)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                        OutlinedButton(onClick = onShare) { Text("Teilen") }
                        OutlinedButton(onClick = onStart) { Text("Neu") }
                    }
                }

                else -> {
                    if (!hasKey) {
                        Text("Bitte zuerst oben unter \"Einstellungen\" einen API-Key eintragen und speichern.")
                    }
                    Button(onClick = onStart, enabled = hasKey) { Text("Transkribieren") }
                }
            }
        }
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
