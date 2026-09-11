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

## Build & Test

```bash
./gradlew assembleDebug     # Debug-APK
./gradlew assembleRelease   # Release-APK (Signierung s. u.)
./gradlew test              # Unit-Tests, offline, kein echter API-Key nötig
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'   # UI-Screenshots via Robolectric
```

Alle Provider-Clients haben `internal var baseUrl`, damit Tests sie gegen einen
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

## Releases

`.github/workflows/release.yml` baut bei jedem Push auf `main` automatisch ein
signiertes APK und veröffentlicht es als GitHub Release (`v1.0.<Lauf-Nummer>`,
Release-Notes automatisch generiert). Ein `paths-ignore` überspringt den Build,
wenn ein Push ausschließlich Markdown-Dateien oder `.claude/**` ändert — reine
Doku-/Tooling-Commits erzeugen so kein Leerlauf-Release.

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
