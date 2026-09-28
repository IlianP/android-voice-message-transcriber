package de.ilianp.audiotranskript

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Everything that is set once and then left alone: API keys, language, how much history to keep.
 *
 * A screen of its own behind the gear in the main screen's header, so none of it takes room from
 * the transcript. Keys and language are saved as they are typed - there is no "save" to forget.
 * The history limits are the exception: they are handed to [onClose] and only applied on leaving,
 * so stepping through the choices does not delete entries on the way, and the warning below them
 * can count what the new limits would drop before anything is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    history: TranscriptHistory,
    openRouterKey: String,
    onOpenRouterKeyChange: (String) -> Unit,
    groqKey: String,
    onGroqKeyChange: (String) -> Unit,
    sonioxKey: String,
    onSonioxKeyChange: (String) -> Unit,
    langCode: String,
    onLangSelect: (String) -> Unit,
    limits: HistoryLimits,
    onClearHistory: () -> Unit,
    onClose: (HistoryLimits) -> Unit,
) {
    var maxEntries by rememberSaveable { mutableIntStateOf(limits.maxEntries) }
    var maxAgeDays by rememberSaveable { mutableIntStateOf(limits.maxAgeDays) }
    val draft = HistoryLimits(maxEntries, maxAgeDays)
    val close = { onClose(draft) }
    BackHandler(onBack = close)

    // Looked up off the main thread: both walk the history directory.
    var storageBytes by remember { mutableLongStateOf(0L) }
    var dropped by remember { mutableIntStateOf(0) }
    var storedCount by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableIntStateOf(0) }
    LaunchedEffect(draft, cleared) {
        withContext(Dispatchers.IO) {
            storageBytes = history.storageBytes()
            dropped = history.countDroppedBy(draft)
            storedCount = history.entries().size
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Transkription")

            OutlinedTextField(
                value = openRouterKey,
                onValueChange = onOpenRouterKeyChange,
                label = { Text("OpenRouter API-Key") },
                supportingText = {
                    Text("Primäres Modell: MAI-Transcribe-2 von Microsoft, rund 0,10 $ pro Stunde Audio. Auch für die Zusammenfassung.")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = groqKey,
                onValueChange = onGroqKeyChange,
                label = { Text("Groq API-Key (Fallback, optional)") },
                supportingText = { Text("Greift nur, wenn MAI-Transcribe-2 fehlschlägt.") },
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionTitle("Verlauf")

            Stepper(
                label = "Einträge behalten",
                value = maxEntries,
                choices = HistoryLimits.ENTRY_CHOICES,
                format = { "$it" },
                onChange = { maxEntries = it },
            )
            Stepper(
                label = "Aufbewahren",
                value = maxAgeDays,
                choices = HistoryLimits.DAY_CHOICES,
                format = { if (it == 1) "1 Tag" else "$it Tage" },
                onChange = { maxAgeDays = it },
            )

            if (dropped > 0) {
                Text(
                    if (dropped == 1) {
                        "Beim Verlassen fällt 1 Eintrag aus dem Verlauf, samt Audio."
                    } else {
                        "Beim Verlassen fallen $dropped Einträge aus dem Verlauf, samt Audio."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                "Text und Audio bleiben nur auf diesem Gerät, auch nicht im Cloud-Backup. " +
                    "Belegt: ${formatBytes(storageBytes)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = { confirmClear = true }, enabled = storedCount > 0) {
                Text("Verlauf löschen")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Ganzen Verlauf löschen?") },
            text = {
                Text(
                    if (storedCount == 1) {
                        "1 Eintrag wird mit seinem Audio von diesem Gerät gelöscht."
                    } else {
                        "$storedCount Einträge werden mit ihrem Audio von diesem Gerät gelöscht."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                    cleared++
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Steps through [choices] with − and +; a value that is not among them snaps to its neighbours. */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    choices: List<Int>,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val lower = choices.lastOrNull { it < value }
    val higher = choices.firstOrNull { it > value }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { lower?.let(onChange) }, enabled = lower != null) {
            Icon(Icons.Filled.Remove, contentDescription = "$label: weniger")
        }
        Text(
            format(value),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        IconButton(onClick = { higher?.let(onChange) }, enabled = higher != null) {
            Icon(Icons.Filled.Add, contentDescription = "$label: mehr")
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Byte"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
}

internal fun languageLabelFor(code: String): String =
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
