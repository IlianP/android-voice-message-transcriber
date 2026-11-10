# 2 – Technik: Lücken für Play und wie GitHub-Verteilung parallel weiterläuft

Stand des Repos: Version 1.0.13 (versionCode 13), `compileSdk`/`targetSdk` 35, `minSdk` 26,
AGP 8.5.2, nur APK-Build, ein Release-Keystore (RSA 2048, Gültigkeit 10.000 Tage laut
README-Anleitung).

## Übersicht der Lücken

| # | Lücke | Pflicht? | Wo gelöst |
|---|---|---|---|
| T1 | **targetSdk 36**: seit 31.8.2026 Pflicht für neue Apps und Updates (Verlängerung war bis 1.11.2026 möglich). Mit 35 lässt sich **keine neue App einreichen** | **Ja** | PR (Branch `claude/play-store-vorbereitung`) |
| T2 | **AAB** (Android App Bundle) statt APK: Pflicht für neue Apps seit 2021 | **Ja** | PR: `release.yml` baut zusätzlich `bundleRelease` |
| T3 | **Play App Signing** mit eigenem Schlüssel einrichten, siehe unten | **Ja** (Play App Signing ist Pflicht, eigener Schlüssel ist die Empfehlung) | Du, in der Play Console |
| T4 | **Upload-Key** (separater Schlüssel zum Hochladen) | empfohlen | PR: Workflow signiert das AAB optional mit einem Upload-Key |
| T5 | **Datenschutzerklärung**: öffentliche URL (kein PDF) **und** Link oder Text in der App | **Ja** | Entwurf `docs/datenschutz.md`, Link in der App im PR |
| T6 | **Erklärung zum Vordergrunddienst** `mediaPlayback` in der Console (*App-Inhalte → Vordergrunddienste*), mit Beschreibung und **Video** | **Ja** | Du: Video aufnehmen, Text unten |
| T7 | **16-KB-Seitengröße**: Pflicht für Apps mit nativen Bibliotheken (`.so`) ab targetSdk 35 | voraussichtlich erfüllt | Das Projekt hat keine eigenen `.so`, die Abhängigkeiten (Compose, OkHttp, Tink) sind reines Java/Kotlin. Nach dem ersten AAB-Upload zeigt die Console Probleme an, sonst mit dem APK Analyzer prüfen |
| T8 | **versionCode** muss über GitHub und Play hinweg streng steigen | **Ja** | Gleiches AAB/APK aus demselben Workflow-Lauf, siehe unten |
| T9 | Hilfe-Link zur Key-Anleitung in der App | nein, aber senkt Supportaufwand | PR |

Nicht nötig: keine Berechtigungen mit Sonderprüfung (kein Mikrofon, kein Standort, keine
SMS/Anruflisten, kein `QUERY_ALL_PACKAGES`, kein `MANAGE_EXTERNAL_STORAGE`). Keine Werbung, keine
Konten in der App, also kein Löschlink für Konten. `POST_NOTIFICATIONS` wird nicht gebraucht, weil
Medien-Benachrichtigungen ausgenommen sind.

## T1 – targetSdk 36: was sich ändert

Verhaltensänderungen von Android 16 für Apps mit targetSdk 36, geprüft gegen diese App:

| Änderung | Betroffen? |
|---|---|
| Edge-to-edge lässt sich nicht mehr abschalten | Nein. Die App läuft seit targetSdk 35 bereits edge-to-edge (`navigationBarsPadding()` in der Player-Leiste) |
| Vorhersagende Zurück-Geste standardmäßig an, `onBackPressed()` wird nicht mehr aufgerufen | Nein. Die App nutzt Compose-`BackHandler`, kein `onBackPressed` |
| Ausrichtung/Größenbeschränkung wird auf großen Bildschirmen (≥ 600 dp) ignoriert | Nein. Keine Ausrichtungssperre im Manifest. Auf Tablets/Faltgeräten einmal ansehen |
| Strengere Regeln für Intents/Hintergrundjobs | Keine Jobs, keine impliziten internen Intents gefunden |

Im PR außerdem: Robolectric-Tests laufen weiter auf API 35 (`robolectric.properties`), weil
Robolectric 4.14 API 36 noch nicht simuliert. Die App selbst wird gegen 36 gebaut. AGP 8.5.2
kennt API 36 noch nicht offiziell, deshalb setzt der PR
`android.suppressUnsupportedCompileSdk=36`. Läuft die CI damit nicht sauber, ist der nächste
Schritt ein AGP-Update (ab 8.9/8.10, mit Gradle 8.11+).

**Ein Gerätetest ist nötig** (Robolectric zeigt keine System-Leisten): auf einem Android-16-Gerät
Zurück-Geste, Player-Leiste über der Gestenleiste und die Benachrichtigung prüfen → **A9**.

## T3/T4 – Signatur: Play und GitHub mit demselben Schlüssel

### Warum das wichtig ist

Android installiert ein Update nur, wenn es **mit demselben Zertifikat signiert** ist wie die
installierte App und einen **gleich hohen oder höheren versionCode** hat. Woher das Update
kommt, spielt dafür keine Rolle. Laut Google gibt es „keinen eingebauten Schutz dagegen, dass
verschiedene Installer eine App aktualisieren“.

Bei Play signiert **Google** die APKs, die auf den Geräten landen, mit dem
*App-Signaturschlüssel*. Du lädst nur ein AAB hoch, signiert mit dem *Upload-Key*.

| Variante | Folge |
|---|---|
| **A: Eigenen Release-Key als App-Signaturschlüssel hochladen** (empfohlen) | Play-APKs und GitHub-APKs haben **dieselbe Signatur** (Fingerabdruck `E3:D1:59:…:11:F1` aus CLAUDE.md). Bestehende Installationen bleiben gültig, beide Wege können einander aktualisieren |
| B: Google erzeugt einen neuen Schlüssel (Standard der Console!) | Play-APKs haben eine **andere** Signatur. Wer von GitHub zu Play wechseln will, muss deinstallieren und verliert Verlauf und Keys. Obtainium kann eine Play-Installation nicht aktualisieren. **Nicht rückgängig zu machen**, sobald die erste Version veröffentlicht ist |

**Achtung:** Die Play Console schlägt beim Anlegen der App standardmäßig **B** vor. Vor dem
ersten Upload unter *Testen und veröffentlichen → App-Integrität → App-Signatur → „Schlüssel für
App-Signatur ändern“* die Option **„Aus Java-Keystore exportieren und hochladen“** wählen → **D5**.

### So geht Variante A (Ablauf, auf deinem Rechner, nicht in einer Cloud-Session)

1. In der Console beim Punkt „Aus Java-Keystore exportieren und hochladen“ das **PEPK-Tool**
   (`pepk.jar`) und den angezeigten **Verschlüsselungsschlüssel** herunterladen. Der Befehl wird
   dort fertig angezeigt.
2. `java -jar pepk.jar --keystore=release.jks --alias=release --output=encrypted_key.zip
   --include-cert --rsa-aes-encryption --encryption-key-path=<von Google>` (genauer Befehl laut
   Console). Das Keystore-Passwort wird abgefragt.
3. `encrypted_key.zip` hochladen.
4. **Upload-Key** anlegen (getrennt vom Release-Key):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   und das Zertifikat exportieren:
   `keytool -export -rfc -keystore upload.jks -alias upload -file upload_certificate.pem`.
   Die `.pem` in der Console als Upload-Zertifikat hochladen.
5. `upload.jks` als Repository-Secrets hinterlegen (`UPLOAD_KEYSTORE_BASE64`,
   `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`). Der Workflow im PR
   signiert das AAB dann damit. Fehlen die Secrets, signiert er das AAB mit dem Release-Key.
6. Prüfen: Die Console zeigt unter *App-Signatur* den SHA-256 des App-Signaturschlüssels. Er muss
   `E3:D1:59:C1:A7:E1:66:45:DD:D8:5F:04:1A:BB:F8:EB:2D:AA:CD:82:B9:55:54:86:73:B5:E1:22:5E:CC:11:F1`
   lauten → **A10**.

**Warum ein separater Upload-Key?** Geht er verloren oder leakt er, kann Google ihn zurücksetzen.
Den App-Signaturschlüssel kann niemand ersetzen, ohne die Update-Kette zu brechen. Er wird nach
dem Hochladen nur noch für die GitHub-APKs gebraucht.

### Nachteile von Variante A (bewusst in Kauf nehmen)

- Google hat danach eine Kopie deines Schlüssels. Wer ihn bei Google kompromittieren könnte, könnte
  auch GitHub-Updates fälschen. Das ist theoretisch, aber ein Restrisiko.
- Ein **Schlüssel-Upgrade** über Play (z. B. auf einen neuen, stärkeren Schlüssel) gilt nur auf
  Android 13+, und die GitHub-APKs müssten dieselbe Rotation (Proof-of-Rotation, `apksigner
  rotate`) mitmachen, sonst laufen die Wege auseinander. Bis auf Weiteres also **kein Upgrade**.
- Laufzeit: Google verlangt, dass der Schlüssel nach dem **22.10.2033** gültig ist. Mit 10.000 Tagen
  Gültigkeit ab 2026 reicht er bis ca. 2053. Prüfen mit `keytool -list -v -keystore release.jks`
  („Gültig bis“) → Teil von **A10**. Eine Mindestgröße (RSA 2048) war nicht offiziell
  verifizierbar. RSA 2048 ist aber der Standard, den Android Studio selbst erzeugt.

## Installationsquellen: Kann Play ein GitHub-APK aktualisieren und umgekehrt?

Mit Variante A und gemeinsamem versionCode-Schema:

| Situation | Ergebnis |
|---|---|
| App von GitHub/Obtainium installiert, Nutzer öffnet die Play-Seite und tippt „Installieren“ | Play installiert **über** die bestehende App, wenn der Play-versionCode ≥ dem installierten ist. Daten bleiben. Ab dann steht die App in der Play-Bibliothek des Nutzers und wird automatisch aktualisiert. Ist der installierte versionCode höher als der in Play, schlägt die Installation fehl (kein Downgrade) |
| App von GitHub installiert, Nutzer tut in Play nichts | Play aktualisiert sie **nicht** von sich aus. Play kümmert sich nur um Apps, die in der Bibliothek des Nutzers stehen |
| App über Play installiert, Obtainium meldet ein neueres GitHub-Release | Obtainium kann aktualisieren (gleiche Signatur, höherer versionCode). Ab Android 14 kann eine **Rückfrage** kommen, wenn Play die „Update-Ownership“ beansprucht hat. Ob Play das tut, war nicht verifizierbar. Die Rückfrage ist harmlos, ein Tipp genügt |
| Gemischt: Play hängt hinterher (z. B. noch in Prüfung) | Kein Problem. Die Quelle mit dem höheren versionCode setzt sich durch, die andere zieht später nach |

**Regel für versionCode (T8):** Play lehnt jeden versionCode ab, der schon einmal hochgeladen
wurde, und Android lehnt niedrigere ab. Heute ist versionCode = `github.run_number` von
`release.yml`. Deshalb:

- In Play **immer das AAB aus demselben Workflow-Lauf** hochladen wie das GitHub-Release. Der PR
  hängt es als Artefakt an den Lauf. Dann sind beide Codes identisch.
- `release.yml` nicht umbenennen oder neu anlegen: Dabei beginnt `run_number` wieder bei 1, und
  alle neuen Builds wären „älter“ als die installierten. Ist das doch einmal nötig, einen
  festen Versatz addieren (z. B. `VERSION_CODE = 1000 + run_number`).
- Nicht jedes GitHub-Release muss nach Play. Lücken im versionCode sind erlaubt.

## T6 – Vordergrunddienst-Erklärung (mediaPlayback)

In der Console unter *App-Inhalte → Vordergrunddienste*:

- **Typ:** Medienwiedergabe (`mediaPlayback`)
- **Beschreibung (Vorschlag, Englisch, weil die Prüfer international sind):**
  > The app plays back voice messages that the user has shared with it, so they can listen while
  > reading the transcript. Playback is always started by the user tapping the play button. The
  > foreground service only runs while audio is playing, shows a media-style notification with
  > play/pause, seek and speed controls, and leaves the foreground as soon as playback is paused
  > or finished. Without it, Android would stop playback of a long message when the user switches
  > to another app.
- **Video (30–60 s, Bildschirmaufnahme am Handy, z. B. als nicht gelistetes YouTube-Video):**
  1. In einer Chat-App (oder Dateien-App) eine Beispiel-Sprachnachricht teilen → „Transkript
     starten“.
  2. Transkript erscheint, **Play** antippen.
  3. Home-Taste: Die Wiedergabe läuft weiter, die Benachrichtigung ist sichtbar.
  4. Benachrichtigung aufziehen, Pause/±5 s/Tempo zeigen.
  5. Pause → Benachrichtigung lässt sich wegwischen.
  → **A8**. Nur eine Beispielnachricht verwenden, keine echten Chats.

## Was im Build/Workflow fehlt (umgesetzt im PR)

1. `targetSdk`/`compileSdk` 36 (+ `suppressUnsupportedCompileSdk`, Robolectric auf 35).
2. `release.yml`: zusätzlich `./gradlew bundleRelease`. Optional wird das AAB mit dem Upload-Key
   neu signiert. Das AAB hängt als Workflow-Artefakt am Lauf, **nicht** als Release-Asset, damit
   Obtainium/Nutzer nicht versehentlich eine `.aab` erwischen.
3. In-App: Links „Anleitung: API-Key anlegen“ und „Datenschutzerklärung“ in den Einstellungen,
   Hinweis auf die Anleitung in der Karte „Noch kein API-Key eingetragen“.
4. `.github/scripts/cloud-setup.sh` installiert zusätzlich `platforms;android-36`.

**Nicht** im PR, als Idee vermerkt:

- Automatischer Upload nach Play (Google Play Developer API über ein Dienstkonto, z. B. mit der
  Action `r0adkll/upload-google-play`). Lohnt sich erst nach dem ersten manuellen Upload, denn
  die erste Version muss ohnehin von Hand hochgeladen werden → **I1**.
- Fehlerhinweis „Transkripte können Fehler enthalten“ in der App → **I2**.
- OpenRouter-Transkription mit denselben Datenschutz-Routing-Vorgaben wie die Zusammenfassung
  (`data_collection: deny`, `zdr: true`) → **I3**.
- `androidx.security:security-crypto` ist von Google inzwischen als veraltet markiert (heute
  `1.1.0-alpha06`). Funktioniert, sollte aber mittelfristig ersetzt werden, etwa durch eine
  direkte Keystore-Verschlüsselung → **I4**.
