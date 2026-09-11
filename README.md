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
- Sprache wählbar: automatisch / Deutsch / Englisch.
- API-Keys werden lokal in `SharedPreferences` gespeichert.

## Aufbau des Bildschirms

Keys trägt man einmal ein, danach geht es nur noch um Transkript und Player. Der Bildschirm ist
entsprechend sortiert:

1. **Einstellungen** – flache, zuklappbare Zeile ganz oben. Standardmäßig zugeklappt; sie zeigt
   dann nur eine Zusammenfassung („MAI-Transcribe-2 · Deutsch"). Aufgeklappt startet sie nur,
   wenn noch kein Key gesetzt ist, und klappt sich nach dem Speichern wieder weg.
2. **Player** – direkt über dem Transkript, damit er seinen Platz behält und nicht mitwandert,
   wenn darunter ein langes Transkript erscheint.
3. **Transkript** – der eigentliche Inhalt, mit Kopieren / Teilen / Neu.

## Der Player (neu)

`MessagePlayer.kt` kapselt einen `MediaPlayer` in `MessagePlayerController` und stellt den
Zustand (Position, Dauer, Tempo, Play/Pause) als Compose-State bereit. `MessagePlayerCard`
rendert die Bedienelemente und erscheint unter dem Transkript, sobald eine Sprachnachricht
geteilt wurde. Das Tempo wird über `MediaPlayer.playbackParams.setSpeed(...)` gesetzt
(API 23+). Damit lässt sich die Nachricht **gleichzeitig lesen und hören**.

## Projektstruktur

```
app/src/main/java/de/ilianp/audiotranskript/
├── MainActivity.kt      # UI (Compose): zuklappbare Einstellungen, Player, Transkriptions-Panel
├── MessagePlayer.kt     # ▶️ Audio-Player mit Tempo 1×–2,5×  (neu)
├── WizperClient.kt      # Orchestrierung: OpenRouter, dann Groq, dann Soniox
├── OpenRouterClient.kt  # OpenRouter STT API (MAI-Transcribe-2)
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
