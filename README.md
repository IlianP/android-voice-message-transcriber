# Audio-Transkript

Android-App, die geteilte **Sprachnachrichten / Audiodateien transkribiert**. Man teilt
eine Audiodatei (z. B. eine WhatsApp-Sprachnachricht) mit „Audio-Transkript", die App
schickt sie an einen Transkriptionsdienst und zeigt den Text an – zum Kopieren, Teilen
und **parallel Anhören**.

## Funktionen

- **Teilen → Transkribieren**: reagiert auf `ACTION_SEND` mit `audio/*`.
- **Transkription** über:
  - **OpenRouter** (`microsoft/mai-transcribe-2`) – primär. Microsofts MAI-Transcribe-2 führt
    den FLEURS-Benchmark an und kostet 0,10 $ pro Stunde Audio; OpenRouter reicht den
    Listenpreis von Microsoft durch, spart aber den Azure-Account. Audio geht base64-kodiert
    in einem einzigen Request raus, es bleibt nichts beim Anbieter liegen.
  - **Groq** (`whisper-large-v3-turbo`) – optionaler Fallback.
  - **Soniox** (`stt-async-v5`) – letzter Fallback (kurzer Upload, wird direkt nach dem
    Abruf des Texts wieder gelöscht; Soniox selbst löscht Uploads nie automatisch).
    Beim App-Start werden zusätzlich Reste abgeräumt, falls die App vorher abgestürzt ist –
    ausschließlich eigene Uploads, erkennbar an `client_reference_id`, und erst ab 10 Minuten
    Alter, damit eine laufende Transkription nicht getroffen wird.
- **▶️ Nachricht anhören** direkt beim Lesen, mit einstellbarem Tempo **1× / 1,5× / 2× / 2,5×**,
  Play/Pause, Fortschrittsleiste und Zeitanzeige.
- Transkript **kopieren** / **teilen**, Text ist markierbar.
- **Verlauf**: die letzten 10 Transkriptionen bleiben samt Audio auf dem Gerät (höchstens
  sieben Tage). Beim Öffnen aus dem App-Drawer ist die zuletzt transkribierte Nachricht
  wieder da, ältere holt man über die Liste zurück – jeweils ohne erneuten API-Aufruf.
- **Eigener Eintrag in der Übersicht der laufenden Apps** – auch wenn die App aus einer
  Sprachnachricht heraus geöffnet wurde.
- Sprache wählbar: automatisch / Deutsch / Englisch.
- API-Keys werden lokal in `SharedPreferences` gespeichert.

## Aufbau des Bildschirms

Keys trägt man einmal ein, danach geht es nur noch um Transkript und Player. Der Bildschirm ist
entsprechend sortiert:

1. **Einstellungen** – flache, zuklappbare Zeile ganz oben. Standardmäßig zugeklappt; sie zeigt
   dann nur eine Zusammenfassung („MAI-Transcribe-2 · Deutsch"). Aufgeklappt startet sie nur,
   wenn noch kein Key gesetzt ist, und klappt sich nach dem Speichern wieder weg.
2. **Verlauf** – ebenfalls zugeklappt, erscheint erst, wenn etwas gespeichert ist. Zeigt
   zugeklappt nur das Alter der letzten Nachricht; aufgeklappt eine Liste, aus der sich jede
   gespeicherte Nachricht mit Text und Audio zurückholen lässt.
3. **Transkript** – der eigentliche Inhalt, mit Kopieren / Teilen / Neu. Scrollt frei.
4. **Player** – als `bottomBar` des `Scaffold` fest am unteren Rand verankert. Er bleibt sichtbar
   und bedienbar, egal wie weit das Transkript darüber gescrollt ist, und liegt in Daumenreichweite.
   Die Leiste zeichnet ihr eigenes `navigationBarsPadding()`, weil die App unter Android 15
   zwangsweise edge-to-edge läuft.

Der Player erscheint nur, wenn tatsächlich eine Audiodatei geteilt, ausgewählt oder aus dem
Verlauf geladen wurde – sonst gibt es keine bottomBar und der Inhalt bekommt die volle Höhe.

## Verlauf und Wiedervorlage

`TranscriptHistory.kt` legt jede fertige Transkription unter `filesDir/history/` ab: den Text in
einer kleinen `index.json`, das Audio als Kopie daneben. Die Kopie ist nötig, weil die
`content://`-URI einer geteilten WhatsApp-Nachricht nur eine befristete Leseberechtigung mitbringt,
die mit dem Task verfällt und sich nicht dauerhaft übernehmen lässt (`ACTION_SEND` vergibt keine
persistierbare Berechtigung). Ohne Kopie gäbe es beim Zurückholen also nichts mehr abzuspielen.
Die Bytes liegen für den Upload ohnehin schon im Speicher, deshalb reicht `WizperClient` sie als
`AudioPayload` durch, statt die Datei ein zweites Mal zu lesen.

Aufgeräumt wird beim Lesen und Schreiben: höchstens `MAX_ENTRIES` (10) Einträge, nichts älter als
`MAX_AGE_MS` (7 Tage), und Audiodateien, auf die kein Eintrag mehr zeigt, verschwinden mit. Wird
dieselbe Nachricht erneut transkribiert, ersetzt das ihren Eintrag (erkannt am SHA-256 der Audio-
Bytes), statt eine zweite Kopie anzulegen.

Der Verlauf wird ausdrücklich **nicht** ins Cloud-Backup übernommen: `backup_rules.xml` und
`data_extraction_rules.xml` schließen das Verzeichnis aus, sodass Transkripte und Sprachnachrichten
das Gerät nicht verlassen.

## Eigener Eintrag in den „letzten Apps"

Ohne Zutun landet eine per `ACTION_SEND` gestartete Activity **im Task der teilenden App** – die
App taucht dann in der Übersicht der laufenden Apps nicht als eigene Karte auf, sondern nur unter
WhatsApp. `MainActivity` läuft deshalb mit `android:launchMode="singleTask"`: sie bekommt einen
eigenen Task und damit eine eigene Karte, und eine zweite geteilte Nachricht erreicht dieselbe
Instanz über `onNewIntent` (statt eine weitere zu starten). Die neue Nachricht ersetzt dort die
angezeigte und wird sofort transkribiert; ein einfacher Start aus dem App-Drawer bringt dagegen die
zuletzt gespeicherte Nachricht zurück.

## Der Player (neu)

`MessagePlayer.kt` kapselt einen `MediaPlayer` in `MessagePlayerController` und stellt den
Zustand (Position, Dauer, Tempo, Play/Pause) als Compose-State bereit. `MessagePlayerBar`
rendert die Bedienelemente als fixierte Leiste am unteren Bildschirmrand, sobald eine
Sprachnachricht geteilt wurde. Das Tempo wird über `MediaPlayer.playbackParams.setSpeed(...)` gesetzt
(API 23+). Damit lässt sich die Nachricht **gleichzeitig lesen und hören**.

## Projektstruktur

```
app/src/main/java/de/ilianp/audiotranskript/
├── MainActivity.kt      # UI (Compose): Scaffold, zuklappbare Einstellungen, Verlauf, Transkriptions-Panel
├── MessagePlayer.kt     # ▶️ Audio-Player mit Tempo 1×–2,5×, fix am unteren Rand
├── TranscriptHistory.kt # Verlauf: Transkripte + Audiokopien in filesDir, 10 Einträge / 7 Tage
├── WizperClient.kt      # Orchestrierung: OpenRouter, dann Groq, dann Soniox
├── OpenRouterClient.kt  # OpenRouter STT API (MAI-Transcribe-2)
├── GroqClient.kt        # Groq Whisper API
├── SonioxClient.kt      # Soniox Async API (Upload → Job → Poll → Ergebnis → Aufräumen)
├── AudioInput.kt        # Liest die geteilte Audiodatei + MIME-/Endungs-Erkennung
├── Settings.kt          # SharedPreferences + Sprachoptionen
└── DebugLog.kt          # Soniox-Job-Log (nur Debug-Builds)
```

## Installation

Fertige APKs liegen unter [Releases](../../releases). Zwei Wege:

**Empfohlen: Obtainium** (automatische Updates)

1. [Obtainium](https://github.com/ImranR98/Obtainium/releases) installieren.
2. In Obtainium auf „Add App" und die URL dieses Repos einfügen.
3. Ab jetzt meldet Obtainium jedes neue Release und installiert es auf Tippen.

**Manuell**

Neuestes `Audio-Transkript-*.apk` von der Releases-Seite laden und öffnen. Android
fragt einmalig nach der Erlaubnis, Apps aus dieser Quelle zu installieren.

Ein Deinstallieren der alten Version ist nicht nötig – alle Releases sind mit
demselben Schlüssel signiert und installieren sich als reguläres Update.

## Build

```bash
./gradlew assembleDebug     # Debug-APK   -> app/build/outputs/apk/debug/
./gradlew assembleRelease   # Release-APK -> app/build/outputs/apk/release/
./gradlew test              # Unit-Tests (offline, kein API-Key nötig)
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'   # Screenshots -> app/build/screenshots/
```

- `minSdk` 26, `targetSdk`/`compileSdk` 35
- Jetpack Compose (Material 3), OkHttp, Kotlin Coroutines
- `versionCode`/`versionName` kommen aus den Umgebungsvariablen `VERSION_CODE`
  und `VERSION_NAME`; ohne sie baut Gradle als `1` / `1.0-dev`.

## Releases

`.github/workflows/release.yml` baut bei jedem Push auf `main` ein signiertes
Release-APK und veröffentlicht es unter einem Tag `v1.0.<Lauf-Nummer>`. Die
Release-Notes werden aus den Commits seit dem letzten Tag erzeugt. Der Workflow
lässt sich in den Actions auch manuell starten.

### Release-Signierung einrichten

Einmalig nötig, sonst bricht der Workflow mit einer Fehlermeldung ab.

**1. Keystore erzeugen** – lokal, nicht in einer Cloud-Session:

```bash
keytool -genkeypair -v -keystore release.jks -alias release \
  -keyalg RSA -keysize 2048 -validity 10000
```

> **Diese Datei und die Passwörter gut sichern.** Geht der Schlüssel verloren,
> lässt sich für die bestehende Installation nie wieder ein Update ausliefern –
> Nutzer müssten die App deinstallieren und neu einrichten. Ein Backup an einem
> Ort, der nicht das Repo ist (Passwortmanager, verschlüsseltes Backup).

**2. Als Base64 kodieren:**

```bash
base64 -w0 release.jks    # macOS: base64 -i release.jks
```

**3. Repository-Secrets anlegen** unter *Settings → Secrets and variables →
Actions → New repository secret*:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | die Base64-Ausgabe aus Schritt 2 |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | `release` |
| `KEY_PASSWORD` | Passwort des Schlüssels (oft identisch mit dem Keystore-Passwort) |

**Lokal signiert bauen** (optional): eine `keystore.properties` neben
`settings.gradle.kts` anlegen – sie ist über `.gitignore` ausgeschlossen:

```properties
storeFile=/absoluter/pfad/zu/release.jks
storePassword=...
keyAlias=release
keyPassword=...
```

Fehlt beides, fällt der Release-Build auf den Debug-Schlüssel zurück und warnt
dabei. Solche APKs eignen sich zum Testen, aber nicht zum Verteilen.

## Tests

### Optische Tests (Screenshots ohne Emulator)

`app/src/testDebug/.../ScreenshotTest.kt` rendert den echten Bildschirm auf der JVM und legt
PNGs unter `app/build/screenshots/` ab:

```bash
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
```

Möglich macht das Robolectric im `NATIVE`-Graphics-Modus – echte Pixel, kein Emulator, keine
Hardwarebeschleunigung nötig. Damit lässt sich ein UI-Umbau auch dort ansehen und in CI prüfen,
wo kein Gerät und kein KVM verfügbar ist. Der Ablauf ist der echte: der Transkriptionsaufruf geht
gegen einen `MockWebServer` (über `OpenRouterClient.baseUrl`), der Player bekommt über
`ShadowMediaPlayer` eine Dauer, und die Einstellungen kommen aus den echten `SharedPreferences`.

Abgedeckte Zustände: Erststart mit offenen Einstellungen, zugeklappte Einstellungen mit
Zusammenfassung, wieder aufgeklappt per Klick, Transkript mit fixierter Player-Leiste sowie der
gescrollte Zustand. Die Bilder sind Review-Artefakte, keine Golden Files – geprüft wird per
Assertion nur, was ein Bild allein nicht zeigt, etwa dass die letzte Inhaltszeile über der
Player-Leiste endet und nicht dahinter verschwindet.

**Grenze:** Robolectric meldet keine System-Bar-Insets. Statusleiste und Gestenleiste tauchen in
den Screenshots also nicht auf, und das `navigationBarsPadding()` der Player-Leiste lässt sich
damit nicht nachweisen – das bleibt ein Check am echten Gerät.

Die Tests liegen im `testDebug`-Source-Set: die Host-Activity stammt aus `ui-test-manifest`, einer
reinen Debug-Abhängigkeit.

`app/src/test/.../OpenRouterClientTest.kt` deckt `OpenRouterClient` gegen einen `MockWebServer` ab:
Request-Form (Modell, base64-Audio, Format-Mapping, Sprache), beide OpenRouter-Fehlerformen
(HTTP-Fehler und `error`-Objekt mit HTTP 200) sowie leere und kaputte Antworten.

`app/src/test/.../SonioxClientTest.kt` deckt `SonioxClient` gegen einen `MockWebServer` ab:
Upload → Poll → Transkript → Aufräumen im Erfolgsfall, beide Soniox-Fehlerformen (HTTP-Fehler
mit `message`-Feld und `status: error`), Timeout, sowie alle Filterregeln des Start-Sweeps
(`cleanUpLeftovers`) – eigener vs. fremder `client_reference_id`, Alters-Schwelle, laufende
Jobs, Pagination. Läuft komplett offline über `./gradlew test`, ohne echten Soniox-Key.
`SonioxClient.baseUrl`/`pollIntervalMs`/`timeoutMs` sind dafür `internal var` statt `const val`,
damit Tests sie umbiegen können.

## Einrichtung

Beim ersten Start in den Feldern oben einen **Groq-API-Key** (und optional einen
**Soniox-API-Key**) eintragen und speichern. Danach eine Sprachnachricht aus einer anderen
App mit „Audio-Transkript" teilen.

Die Keys werden ausschließlich in der App eingegeben und verschlüsselt auf dem Gerät
abgelegt – sie stehen an keiner Stelle im Code oder im Build.

## Datenschutz

Die App hat keinen eigenen Server. Geteilte Audiodateien werden zur Transkription
direkt an **Groq** und – falls als Fallback konfiguriert – an **Soniox** gesendet;
dort gelten deren Datenschutzbestimmungen. Soniox-Uploads löscht die App direkt nach
dem Abruf des Transkripts wieder, Reste werden beim nächsten App-Start abgeräumt.
API-Keys und Einstellungen bleiben lokal auf dem Gerät. Es findet keine Analyse,
kein Tracking und keine Übertragung an Dritte darüber hinaus statt.

Für den Verlauf speichert die App Transkripte **und Kopien der Sprachnachrichten** im privaten
App-Verzeichnis (`filesDir/history/`), auf das andere Apps keinen Zugriff haben. Beides wird nach
sieben Tagen automatisch gelöscht, spätestens aber wenn der elfte Eintrag dazukommt; „Verlauf
löschen" räumt sofort alles weg. Vom Cloud-Backup und von der Geräteübertragung ist das
Verzeichnis ausgenommen. Beim Deinstallieren verschwindet es mit den übrigen App-Daten.
