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

## Build

```bash
./gradlew assembleDebug     # Debug-APK -> app/build/outputs/apk/debug/
./gradlew test              # Unit-Tests (offline, kein API-Key nötig)
```

- `minSdk` 26, `targetSdk`/`compileSdk` 35
- Jetpack Compose (Material 3), OkHttp, Kotlin Coroutines

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
