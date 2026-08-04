# Audio-Transkript

Android-App, die geteilte **Sprachnachrichten / Audiodateien transkribiert**. Man teilt
eine Audiodatei (z. B. eine WhatsApp-Sprachnachricht) mit „Audio-Transkript", die App
schickt sie an einen Transkriptionsdienst und zeigt den Text an – zum Kopieren, Teilen
und **parallel Anhören**.

## Funktionen

- **Teilen → Transkribieren**: reagiert auf `ACTION_SEND` mit `audio/*`.
- **Transkription** über:
  - **Groq** (`whisper-large-v3-turbo`) – schnell, primär.
  - **fal.ai Wizper** – optionaler Fallback (kurzer Upload, läuft nach ~5 Min automatisch ab).
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
├── WizperClient.kt      # Orchestrierung: Groq zuerst, dann fal.ai-Fallback
├── GroqClient.kt        # Groq Whisper API
├── FalClient.kt         # fal.ai Wizper Queue (Upload → Submit → Poll → Ergebnis)
├── AudioInput.kt        # Liest die geteilte Audiodatei + MIME-/Endungs-Erkennung
├── Settings.kt          # SharedPreferences + Sprachoptionen
└── DebugLog.kt          # fal-Upload-Log (nur Debug-Builds)
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

## Einrichtung

Beim ersten Start in den Feldern oben einen **Groq-API-Key** (und optional einen
**fal.ai-API-Key**) eintragen und speichern. Danach eine Sprachnachricht aus einer anderen
App mit „Audio-Transkript" teilen.

## Datenschutz

Die App hat keinen eigenen Server. Geteilte Audiodateien werden zur Transkription
direkt an **Groq** und – falls als Fallback konfiguriert – an **fal.ai** gesendet;
dort gelten deren Datenschutzbestimmungen. API-Keys und Einstellungen bleiben
lokal auf dem Gerät. Es findet keine Analyse, kein Tracking und keine Übertragung
an Dritte darüber hinaus statt.
