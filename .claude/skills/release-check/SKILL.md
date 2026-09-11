---
name: release-check
description: Prüft ein Audio-Transkript-Release aus GitHub Releases — Signatur-Fingerabdruck, versionCode/versionName und ob es sich um den Release-Key statt den Debug-Key handelt. Benutzen, wenn jemand fragt, ob ein neuer Release-Build in Ordnung ist, bevor er installiert oder weitergegeben wird.
---

## Was dieser Skill prüft

Ein von `.github/workflows/release.yml` erzeugtes APK ist nur dann sicher zu
verteilen, wenn es mit dem echten Release-Keystore signiert ist — nicht mit
dem Debug-Fallback (siehe `CLAUDE.md`, Abschnitt „Release-Signierung"). Ein
falsch signiertes APK sieht auf den ersten Blick unauffällig aus: es baut
durch, installiert sich, funktioniert. Der Unterschied zeigt sich erst am
Signatur-Fingerabdruck.

## Ablauf

1. **Release ermitteln.** Ohne Vorgabe das neueste Release nehmen:
   ```
   https://api.github.com/repos/IlianP/android-voice-message-transcriber/releases/latest
   ```
   Daraus `tag_name` und die `browser_download_url` des `.apk`-Assets.

2. **APK herunterladen.** Der direkte `browser_download_url`-Link ist in
   manchen Umgebungen blockiert (Redirect auf `release-assets.githubusercontent.com`
   scheitert). Funktioniert zuverlässig: über die Asset-ID und die API mit
   `Accept: application/octet-stream`. Das Asset dabei **nach Namen filtern**,
   nicht `assets[0]` nehmen — sobald ein Release mal mehr als nur die APK
   enthält (z. B. Release-Notes als eigene Datei, Checksums-Datei), wäre
   sonst das falsche Asset dran:
   ```bash
   ASSET_ID=$(curl -s https://api.github.com/repos/IlianP/android-voice-message-transcriber/releases/latest \
     | python3 -c '
import sys, json
assets = json.load(sys.stdin)["assets"]
apk = [a for a in assets if a["name"].endswith(".apk")]
if not apk:
    sys.exit("Kein .apk-Asset im Release gefunden")
print(apk[0]["id"])')
   curl -sL -o app.apk \
     "https://api.github.com/repos/IlianP/android-voice-message-transcriber/releases/assets/$ASSET_ID" \
     -H "Accept: application/octet-stream"
   ```
   Danach `ls -la app.apk` als grobe Zusatzprüfung — unter 1 MB ist es
   vermutlich eine JSON-Fehlerantwort statt einer echten APK.

3. **Signatur kryptografisch verifizieren — nicht nur das Zertifikat auslesen.**
   Die Zertifikats-Bytes aus dem APK Signing Block herauszuziehen beweist
   noch nicht, dass die Signatur gültig ist: Ein manipuliertes APK könnte die
   erwarteten Zertifikats-Bytes in einem ansonsten ungültigen Signing Block
   enthalten, und ein reines Byte-Auslesen würde das nicht bemerken. Deshalb
   `apksigner` benutzen, das v2/v3-Signatur und Content-Digests tatsächlich
   prüft, statt den Block von Hand zu parsen:
   ```bash
   # apksigner kommt mit den Android SDK Build-Tools (nicht mit einem vollen
   # SDK-Setup) - falls ANDROID_HOME schon existiert, ggf. nur diese Zeile:
   # sdkmanager "build-tools;35.0.0"
   "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs app.apk
   ```
   Bei gültiger Signatur nennt die Ausgabe den SHA-256-Fingerabdruck direkt
   (`Signer #1 certificate SHA-256 digest: ...`). Schlägt `verify` fehl, ist
   die Signatur ungültig — unabhängig davon, was im Zertifikat steht.

   **Nur falls `apksigner` sich nicht besorgen lässt** (z. B. kein Zugriff auf
   die SDK-Repos): ersatzweise den Signing Block von Hand parsen (End-of-
   Central-Directory `PK\x05\x06` suchen, davor den `APK Sig Block 42`-Header
   finden, das v2-Zertifikat über Block-ID `0x7109871a` herausziehen, SHA-256
   davon bilden). Das liefert aber *nur* das Zertifikat, keinen Beweis, dass
   die Signatur über den Inhalt gültig ist — ein Ergebnis auf diesem Weg
   entsprechend vorsichtig kommunizieren („Zertifikat passt, Signatur nicht
   kryptografisch geprüft"), nicht als vollwertige Verifikation ausgeben.

4. **Mit dem erwarteten Fingerabdruck vergleichen**, der in `CLAUDE.md` unter
   „Release-Signierung" hinterlegt ist. Stimmt er nicht überein: **nicht**
   zur Installation empfehlen, sondern melden, dass die Repository-Secrets
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
   geprüft werden müssen.

5. **Version prüfen — versionCode ist die eigentliche Instanz.** `versionCode`
   und `versionName` aus dem Manifest lesen, z. B. mit `pyaxmlparser`
   (`pip install pyaxmlparser`, dann `APK(...).version_code` / `.version_name`)
   oder `aapt dump badging`, falls ein Android-SDK zur Verfügung steht.
   `versionName` sollte `1.0.<Lauf-Nummer>` folgen, aber das ist nur Kosmetik —
   Android entscheidet beim Update ausschließlich anhand von `versionCode`.
   Deshalb **`versionCode` explizit gegen das zuletzt geprüfte Release
   vergleichen** und sicherstellen, dass er strikt höher ist, nicht nur davon
   ausgehen, dass eine höhere `versionName`-Nummer das schon mit abdeckt.
   Der Workflow setzt `VERSION_CODE` auf `github.run_number`, das steigt bei
   jedem Lauf garantiert — aber genau das ist die Eigenschaft, die hier
   geprüft wird, nicht vorausgesetzt. Ein lokal gebauter Release-Build ohne
   gesetzte `VERSION_CODE`-Variable fiele z. B. auf `1` zurück, egal was der
   Dateiname suggeriert.

6. **Ergebnis kurz zusammenfassen:** Tag, Fingerabdruck (passt/passt nicht),
   versionCode/versionName, ob das Release zur Installation geeignet ist.

## Wann eher nicht reicht

Dieser Skill prüft nur das fertige Artefakt, nicht den Workflow-Lauf selbst.
Ist der Workflow fehlgeschlagen (rotes Kreuz in den Actions), zuerst die
Logs ansehen — meist fehlt eines der vier Secrets, der Workflow bricht dann
schon im ersten Schritt „Keystore-Secrets pruefen" kontrolliert ab.
