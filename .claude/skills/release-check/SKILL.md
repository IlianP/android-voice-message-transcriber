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
   `Accept: application/octet-stream`:
   ```bash
   ASSET_ID=$(curl -s https://api.github.com/repos/IlianP/android-voice-message-transcriber/releases/latest \
     | python3 -c 'import sys,json;print(json.load(sys.stdin)["assets"][0]["id"])')
   curl -sL -o app.apk \
     "https://api.github.com/repos/IlianP/android-voice-message-transcriber/releases/assets/$ASSET_ID" \
     -H "Accept: application/octet-stream"
   ```
   Danach `ls -la app.apk` prüfen — unter 1 MB ist es keine echte APK, sondern
   vermutlich eine JSON-Fehlerantwort.

3. **Signatur-Fingerabdruck extrahieren.** Das Release-APK trägt nur v2/v3-
   Signaturen (kein `META-INF/*.RSA`), `apksigner` ist in einer frischen Session
   meist nicht installiert. Robuster: den APK Signing Block direkt parsen. Ein
   fertiges Skript dafür liegt nicht im Repo — bei Bedarf neu schreiben:
   End-of-Central-Directory suchen (`PK\x05\x06`), von dort den
   `APK Sig Block 42`-Header rückwärts finden, das v2-Zertifikat (Block-ID
   `0x7109871a`) herausziehen und mit `openssl x509` bzw. SHA-256 auswerten.
   (Der genaue Code wurde in einer früheren Session für dieses Repo bereits
   einmal geschrieben und verifiziert — im Zweifel ähnlich neu aufbauen.)

4. **Mit dem erwarteten Fingerabdruck vergleichen**, der in `CLAUDE.md` unter
   „Release-Signierung" hinterlegt ist. Stimmt er nicht überein: **nicht**
   zur Installation empfehlen, sondern melden, dass die Repository-Secrets
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
   geprüft werden müssen.

5. **Version prüfen.** `versionCode`/`versionName` aus dem Manifest lesen,
   z. B. mit `pyaxmlparser` (`pip install pyaxmlparser`, dann `APK(...).version_code`
   / `.version_name`) oder `aapt dump badging`, falls ein Android-SDK zur
   Verfügung steht. Erwartung: `versionName` folgt `1.0.<Lauf-Nummer>`, und die
   Nummer ist höher als beim zuletzt geprüften Release.

6. **Ergebnis kurz zusammenfassen:** Tag, Fingerabdruck (passt/passt nicht),
   versionCode/versionName, ob das Release zur Installation geeignet ist.

## Wann eher nicht reicht

Dieser Skill prüft nur das fertige Artefakt, nicht den Workflow-Lauf selbst.
Ist der Workflow fehlgeschlagen (rotes Kreuz in den Actions), zuerst die
Logs ansehen — meist fehlt eines der vier Secrets, der Workflow bricht dann
schon im ersten Schritt „Keystore-Secrets pruefen" kontrolliert ab.
