package de.ilianp.audiotranskript

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var falKey by remember { mutableStateOf(settings.falApiKey) }
    var langCode by remember { mutableStateOf(settings.languageCode) }
    var savedHint by remember { mutableStateOf(false) }

    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var debugLog by remember { mutableStateOf(DebugLog.get(context)) }

    val hasKey = groqKey.isNotBlank() || falKey.isNotBlank()

    fun startTranscription() {
        if (sharedUri == null || running) return
        running = true
        result = null
        error = null
        scope.launch {
            try {
                result = WizperClient.transcribe(context, sharedUri, groqKey, falKey, langCode) { url ->
                    DebugLog.addFalUpload(context, url)
                    debugLog = DebugLog.get(context)
                }
            } catch (e: Exception) {
                error = e.message ?: "Unbekannter Fehler"
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (sharedUri != null && hasKey) startTranscription()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Audio-Transkript", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = groqKey,
            onValueChange = { groqKey = it; savedHint = false },
            label = { Text("Groq API-Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = falKey,
            onValueChange = { falKey = it; savedHint = false },
            label = { Text("fal.ai API-Key (Fallback, optional)") },
            supportingText = {
                Text("Greift nur, wenn Groq fehlschlägt. Audio wird kurz hochgeladen und nach 5 Min automatisch gelöscht.")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        LanguageDropdown(selectedCode = langCode, onSelect = { langCode = it; savedHint = false })

        Button(
            onClick = {
                settings.groqApiKey = groqKey
                settings.falApiKey = falKey
                settings.languageCode = langCode
                groqKey = settings.groqApiKey
                falKey = settings.falApiKey
                savedHint = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Einstellungen speichern")
        }

        if (savedHint) {
            Text("Gespeichert.", style = MaterialTheme.typography.bodySmall)
        }

        if (sharedUri != null) {
            Spacer(Modifier.height(8.dp))
            TranscriptionPanel(
                running = running,
                result = result,
                error = error,
                hasKey = hasKey,
                onStart = { startTranscription() },
                onCopy = { result?.let { clipboard.setText(AnnotatedString(it)) } },
                onShare = { result?.let { shareText(context, it) } },
            )
            // Listen to the shared voice message while reading the transcript.
            MessagePlayerCard(uri = sharedUri)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Teile eine Sprachnachricht oder Audio-Datei aus einer anderen App (z. B. WhatsApp) mit \"Audio-Transkript\", um sie zu transkribieren.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            DebugPanel(
                log = debugLog,
                onOpenLatest = { DebugLog.latestUrl(context)?.let { openUrl(context, it) } },
                onCopy = { clipboard.setText(AnnotatedString(debugLog)) },
                onClear = { DebugLog.clear(context); debugLog = "" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selectedCode: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = LANGUAGE_OPTIONS.firstOrNull { it.code == selectedCode }?.label
        ?: LANGUAGE_OPTIONS.first().label

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
    onStart: () -> Unit,
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
                        Text("Wird transkribiert …")
                    }
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
                        Text("Bitte zuerst den API-Key eintragen und speichern.")
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
    onOpenLatest: () -> Unit,
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
            Text("Debug: fal.ai-Uploads", style = MaterialTheme.typography.titleMedium)
            if (log.isBlank()) {
                Text(
                    "Noch keine Uploads. Greift erst, wenn der fal-Fallback genutzt wird.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(log, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenLatest) { Text("Neueste öffnen") }
                    OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                    OutlinedButton(onClick = onClear) { Text("Leeren") }
                }
                Text(
                    "Tipp: URL nach 5 Min im Browser öffnen – sollte dann nicht mehr abrufbar sein.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(Intent.createChooser(view, "URL öffnen"))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Transkription teilen"))
}
