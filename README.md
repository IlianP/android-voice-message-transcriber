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
    Abruf des Texts wieder gelöscht).
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
```

- `minSdk` 26, `targetSdk`/`compileSdk` 35
- Jetpack Compose (Material 3), OkHttp, Kotlin Coroutines

## Einrichtung

Beim ersten Start in den Feldern oben einen **Groq-API-Key** (und optional einen
**Soniox-API-Key**) eintragen und speichern. Danach eine Sprachnachricht aus einer anderen
App mit „Audio-Transkript" teilen.

Die Keys werden ausschließlich in der App eingegeben und verschlüsselt auf dem Gerät
abgelegt – sie stehen an keiner Stelle im Code oder im Build.
