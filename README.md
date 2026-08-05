# Audio-Transkript

Android-App, die geteilte **Sprachnachrichten / Audiodateien transkribiert**. Man teilt
eine Audiodatei (z. B. eine WhatsApp-Sprachnachricht) mit „Audio-Transkript", die App
schickt sie an einen Transkriptionsdienst und zeigt den Text an – zum Kopieren, Teilen
und **parallel Anhören**.

## Funktionen

- **Teilen → Transkribieren**: reagiert auf `ACTION_SEND` mit `audio/*`.
- **Transkription** über:
  - **Groq** (`whisper-large-v3-turbo`) – schnell, primär.
  - **Soniox** (`stt-async-v5`) – optionaler Fallback (kurzer Upload, wird direkt nach dem
    Abruf des Texts wieder gelöscht; Soniox selbst löscht Uploads nie automatisch).
    Beim App-Start werden zusätzlich Reste abgeräumt, falls die App vorher abgestürzt ist –
    ausschließlich eigene Uploads, erkennbar an `client_reference_id`, und erst ab 10 Minuten
    Alter, damit eine laufende Transkription nicht getroffen wird.
- **▶️ Nachricht anhören** direkt beim Lesen, mit einstellbarem Tempo **1× / 1,5× / 2× / 2,5×**,
  Play/Pause, Fortschrittsleiste und Zeitanzeige.
- Transkript **kopieren** / **teilen**, Text ist markierbar.
- Sprache wählbar: automatisch / Deutsch / Englisch.
- API-Keys werden lokal in `SharedPreferences` gespeichert.

## Der Player (neu)

`MessagePlayer.kt` kapselt einen `MediaPlayer` in `MessagePlayerController` und stellt den
Zustand (Position, Dauer, Tempo, Play/Pause) als Compose-State bereit. `MessagePlayerCard`
rendert die Bedienelemente und erscheint unter dem Transkript, sobald eine Sprachnachricht
geteilt wurde. Das Tempo wird über `MediaPlayer.playbackParams.setSpeed(...)` gesetzt
(API 23+). Damit lässt sich die Nachricht **gleichzeitig lesen und hören**.

## Projektstruktur

```
app/src/main/java/de/ilianp/audiotranskript/
├── MainActivity.kt      # UI (Compose): Einstellungen, Transkriptions-Panel, Player-Einbindung
├── MessagePlayer.kt     # ▶️ Audio-Player mit Tempo 1×–2,5×  (neu)
├── WizperClient.kt      # Orchestrierung: Groq zuerst, dann Soniox-Fallback
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
