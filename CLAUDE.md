# Audio-Transkript

Android-App (Kotlin, Jetpack Compose). Nimmt eine geteilte Sprachnachricht per
`ACTION_SEND` entgegen, schickt sie an einen Transkriptionsdienst und zeigt den
Text an, mit paralleler Wiedergabe über eine fest verankerte Player-Leiste.
Details zu Funktionen, Bildschirmaufbau und Datenschutz stehen in der README —
diese Datei ergänzt nur, was für die Arbeit am Repo selbst wichtig ist.

## Transkriptions-Provider

`WizperClient.kt` orchestriert drei Anbieter, in dieser Reihenfolge, jeweils nur
falls ein Key gesetzt ist:

1. **OpenRouter** (`OpenRouterClient.kt`) – primär, `microsoft/mai-transcribe-2`.
2. **Groq** (`GroqClient.kt`) – Fallback, `whisper-large-v3-turbo`.
3. **Soniox** (`SonioxClient.kt`) – letzter Fallback, asynchron (Upload → Poll →
   Ergebnis → Löschen). Räumt beim App-Start eigene liegen gebliebene Uploads ab.

Ein `CancellationException` (Nutzer bricht ab) wird in `WizperClient.attempt()`
bewusst nicht als Provider-Fehler behandelt, sondern durchgereicht — siehe den
Kommentar dort, falls das mal wieder auffällt.

## Verlauf (Persistenz)

`TranscriptHistory.kt` hält die letzten 10 Transkriptionen (max. 7 Tage) in
`filesDir/history/`. Das Audio muss **kopiert** werden: die `content://`-URI aus
einem `ACTION_SEND` trägt nur eine befristete, nicht persistierbare Lese-
berechtigung — ein `takePersistableUriPermission` gibt es dafür nicht. Deshalb
reicht `WizperClient` den bereits gelesenen `AudioPayload` an den Aufrufer durch,
statt die Datei für die Kopie ein zweites Mal zu lesen.

`MainActivity` läuft mit `launchMode="singleTask"`, damit die App eine eigene
Karte in den „letzten Apps" bekommt statt im Task der teilenden App zu landen —
zweite geteilte Nachrichten kommen dadurch über `onNewIntent` herein und müssen
dort verarbeitet werden. Dabei wird nicht die URI allein in den State gelegt,
sondern zusammen mit einem hochzählenden `deliveryId`: Compose-State vergleicht
über `equals`, und dieselbe Nachricht zweimal geteilt sähe sonst wie „keine
Änderung" aus und würde still ignoriert. Der Verlauf ist in `backup_rules.xml` /
`data_extraction_rules.xml` vom Cloud-Backup ausgenommen.

## Stapel: „Zwischenspeichern" / „Transkript starten"

Im Teilen-Dialog gibt es zwei Ziele: der `SEND`-Filter von `MainActivity` (Label
„Transkript starten" am `intent-filter`) und `QueueShareActivity` („Zwischenspeichern").
Letztere ist unsichtbar, hat `taskAffinity=""` (sonst holt ihr `finish()` den Hauptbildschirm
nach vorne statt zum Chat zurückzukehren) und kopiert das Audio **vor** `finish()` nach
`filesDir/pending/` — die URI-Berechtigung stirbt mit der Activity. `MainActivity.deliver`
holt die Warteschlange per `PendingQueue.takeAll()` vor die geteilte Nachricht, und zwar
**nur einmal pro Share**: bei Neuerstellung (Drehen) kommt der Stapel aus dem
`savedInstanceState` zurück, und `AppScreen` merkt sich per `rememberSaveable`, welchen
Share (`deliveryId`) es schon verarbeitet hat — ein zweites `takeAll()` würde den laufenden
Stapel löschen. `readBatch` begrenzt einen Stapel auf `MAX_BATCH_BYTES` (50 MB);
`WizperClient.transcribeAll` macht einen Request pro Nachricht, `MessagePlayerController`
spielt die Dateien als eine durchgehende Zeitleiste. Ein Stapel ist im Verlauf ein Eintrag
(`audioFileNames`/`segments`); alte Einträge mit einzelnem `audio`-Feld werden weiter gelesen.
Ideen für später stehen in `ROADMAP.md`.

## Zusammenfassung

`SummaryClient.kt` fasst ein Transkript über OpenRouter (`/chat/completions`) mit
`deepseek/deepseek-v4.1-flash` zusammen – gewählt über die Benchmark-Heaven-API
(`https://benchmarkheaven.com/api/dataset`, Doku im GitHub-Repo `fstandhartinger/model-market-comparison`,
`API.md`), falls das Modell mal neu bewertet werden soll. Nur auf Tipp, nie automatisch; ab
`SUGGEST_FROM_MS` (2 min, Dauer vom `MessagePlayerController`) wird sie als Karte angeboten. Der
Request erzwingt `data_collection: deny` + `zdr: true`. Das Ergebnis landet per
`TranscriptHistory.setSummary` im Verlaufseintrag; `add()` für dieselbe Nachricht verwirft es.

## Build & Test

```bash
./gradlew assembleDebug     # Debug-APK
./gradlew assembleRelease   # Release-APK (Signierung s. u.)
./gradlew test              # Unit-Tests, offline, kein echter API-Key nötig
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'   # UI-Screenshots via Robolectric
```

Alle Provider-Clients (OpenRouter, Groq, Soniox, Summary) haben `internal var baseUrl`, damit Tests sie gegen einen
`MockWebServer` umbiegen können, statt echte Keys zu brauchen.

Das Android SDK ist in einer frischen Cloud-Session nicht vorinstalliert und
muss vor dem ersten Build geladen werden (Setup-Script in der Cloud-Umgebung
einrichten, um das künftig zu vermeiden — siehe unten).

## Release-Signierung

`app/build.gradle.kts` liest die Signing-Config aus `keystore.properties`
(lokal, nicht committet) oder aus `KEYSTORE_*`-Umgebungsvariablen (CI). Fehlen
beide, fällt der Build **mit Warnung** auf den Debug-Key zurück — so ein APK
lässt sich nicht über eine bestehende Installation aktualisieren, ist also
niemals zum Verteilen gedacht.

`versionCode`/`versionName` kommen aus `VERSION_CODE`/`VERSION_NAME`; lokal
ohne diese Variablen baut Gradle als `1` / `1.0-dev`.

**Erwarteter Release-Signatur-Fingerabdruck** (SHA-256, verifiziert am APK von
Release v1.0.1):

```
E3:D1:59:C1:A7:E1:66:45:DD:D8:5F:04:1A:BB:F8:EB:2D:AA:CD:82:B9:55:54:86:73:B5:E1:22:5E:CC:11:F1
```

Weicht der Fingerabdruck eines Release-APKs hiervon ab, wurde es nicht mit dem
richtigen Keystore signiert — dann Repository-Secrets (`KEYSTORE_BASE64` u. a.)
prüfen, bevor irgendwer das APK installiert. Ausführliche Anleitung inkl.
Keystore-Erzeugung (auch ohne Rechner, über Termux) steht in der README unter
„Release-Signierung einrichten".

## Automatische Pruefungen

`.github/workflows/tests.yml` laeuft bei **jedem Pull Request** (und von Hand
ueber „Run workflow") und fuehrt `./gradlew test assembleDebug` aus — Unit-Tests
inklusive der Robolectric-Screenshot-Tests, plus Debug-Build fuer Manifest- und
Ressourcen-Fehler. Kein Keystore, kein Release; das bleibt `release.yml`
vorbehalten, das **keine** Tests ausfuehrt. Ein Fehler laesst den PR rot werden,
die Testberichte haengen dann als Artefakt am Lauf.

## Releases

`.github/workflows/release.yml` baut bei jedem Push auf `main` automatisch ein
signiertes APK und veröffentlicht es als GitHub Release (`v1.0.<Lauf-Nummer>`,
Release-Notes automatisch generiert). Ein `paths-ignore` überspringt den Build,
wenn ein Push ausschließlich Markdown-Dateien, `.claude/**` oder `.github/**`
ändert — reine Doku-/Tooling-Commits erzeugen so kein Leerlauf-Release. Manuell
geht der Workflow weiterhin über „Run workflow".

Der `/release-check`-Skill (siehe `.claude/skills/release-check/`) prüft ein
fertiges Release: Signatur kryptografisch verifiziert (nicht nur ausgelesen),
Fingerabdruck-Abgleich gegen den oben hinterlegten Wert, sowie versionCode
(nicht nur versionName) gegen das vorherige Release. Er prüft **nicht**, ob
die konfigurierten Transkriptions-Provider erreichbar sind — das wurde hier
ursprünglich fälschlich behauptet und war nie Teil des Skills.

## Cloud-Umgebung beschleunigen (optional, noch nicht eingerichtet)

Ein Setup-Script in der Cloud-Umgebung (claude.ai/code → Zahnrad bei der
Umgebung → „Setup script") könnte das Android SDK einmalig installieren und
danach cachen, statt es in jeder Session neu herunterzuladen (~7 Minuten
Ersparnis pro Session). Noch nicht umgesetzt, weil das nur über die
claude.ai-Oberfläche geht, nicht aus einer Session heraus.
