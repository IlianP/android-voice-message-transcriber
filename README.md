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
2. **Transkript** – der eigentliche Inhalt, mit Kopieren / Teilen / Neu. Scrollt frei.
3. **Player** – als `bottomBar` des `Scaffold` fest am unteren Rand verankert. Er bleibt sichtbar
   und bedienbar, egal wie weit das Transkript darüber gescrollt ist, und liegt in Daumenreichweite.
   Die Leiste zeichnet ihr eigenes `navigationBarsPadding()`, weil die App unter Android 15
   zwangsweise edge-to-edge läuft.

Der Player erscheint nur, wenn tatsächlich eine Audiodatei geteilt oder ausgewählt wurde – sonst
gibt es keine bottomBar und der Inhalt bekommt die volle Höhe.

## Der Player (neu)

`MessagePlayer.kt` kapselt einen `MediaPlayer` in `MessagePlayerController` und stellt den
Zustand (Position, Dauer, Tempo, Play/Pause) als Compose-State bereit. `MessagePlayerBar`
rendert die Bedienelemente als fixierte Leiste am unteren Bildschirmrand, sobald eine
Sprachnachricht geteilt wurde. Das Tempo wird über `MediaPlayer.playbackParams.setSpeed(...)` gesetzt
(API 23+). Damit lässt sich die Nachricht **gleichzeitig lesen und hören**.

## Projektstruktur

```
app/src/main/java/de/ilianp/audiotranskript/
├── MainActivity.kt      # UI (Compose): Scaffold, zuklappbare Einstellungen, Transkriptions-Panel
├── MessagePlayer.kt     # ▶️ Audio-Player mit Tempo 1×–2,5×, fix am unteren Rand
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
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'   # Screenshots -> app/build/screenshots/
```

- `minSdk` 26, `targetSdk`/`compileSdk` 35
- Jetpack Compose (Material 3), OkHttp, Kotlin Coroutines

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
