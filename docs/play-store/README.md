# Google Play – Vorbereitung (Übersicht)

Ziel: „Audio-Transkript“ (`de.ilianp.audiotranskript`) zusätzlich über Google Play an mehrere
Menschen verteilen. Jeder Nutzer bringt seinen eigenen API-Key mit. GitHub-Releases und
Obtainium laufen parallel weiter.

Stand: 10.11.2026. Die Recherche stützt sich auf developer.android.com (gelesen) und Auszüge aus
der Play-Console-Hilfe (support.google.com war aus der Session nicht direkt abrufbar). Bei
Abweichungen gilt, was die Console beim Ausfüllen anzeigt.

| Teil | Inhalt |
|---|---|
| [01-registrierung.md](01-registrierung.md) | Checkliste Registrierung, Kontotyp, öffentliche Daten, Testpflicht 12 × 14 mit Testerplan, Entwicklerverifizierung |
| [02-technik.md](02-technik.md) | targetSdk 36, AAB, Play App Signing mit eigenem Schlüssel, Updates zwischen Play und GitHub, Vordergrunddienst-Erklärung |
| [03-store-eintrag.md](03-store-eintrag.md) | Store-Texte DE/EN, Grafiken, Datensicherheit, Inhaltseinstufung, App-Zugriff für Prüfer |
| [04-ablauf-bis-produktion.md](04-ablauf-bis-produktion.md) | Reihenfolge in der Play Console bis zur Veröffentlichung |
| [05-kritische-fragen.md](05-kritische-fragen.md) | Impressum, Haftung, Support, Keys, Name/Marke, Daten Dritter |
| [../anleitung-api-keys.md](../anleitung-api-keys.md) | Anleitung für Endnutzer: API-Key anlegen und eintragen |
| [../datenschutz.md](../datenschutz.md) | Datenschutzerklärung (**Entwurf**) |

## Das Wichtigste in fünf Sätzen

1. **Privates Konto** reicht (25 USD einmalig). Es verlangt aber vor dem Start einen
   **geschlossenen Test mit 12 Testern über 14 Tage**. Rechne mit gut drei Wochen von der
   Registrierung bis zur Veröffentlichung.
2. Als **Nicht-Händler** (EU-DSA) sind im Store nur E-Mail und Land sichtbar, nicht deine
   Adresse. Diese Erklärung ist neu und bis 1.2.2027 Pflicht.
3. **targetSdk muss auf 36** (seit 31.8.2026), und Play nimmt nur **AAB**. Beides steht im PR.
4. **Beim Anlegen der App den eigenen Release-Schlüssel als App-Signaturschlüssel hochladen,
   nicht den von Google erzeugten nehmen.** Nur dann können Play und GitHub/Obtainium dieselbe
   Installation aktualisieren. Diese Wahl ist endgültig.
5. Datenschutzerklärung (öffentliche URL **und** Link in der App), Datensicherheitsformular,
   Inhaltseinstufung, Vordergrunddienst-Video und ein Review-Key für die Prüfer sind Pflicht.
   Entwürfe liegen hier.

## Offene Punkte

Kennungen bleiben fest. „Du“ = nur du kannst es tun, „KI“ = kann eine Claude-Session erledigen.

### Entscheidungen (Du)

| ID | Entscheidung | Empfehlung | Status |
|---|---|---|---|
| D1 | Kontotyp privat oder Organisation | **privat** (keine D-U-N-S, kein Gewerbe nötig; dafür Testpflicht) | offen |
| D2 | Händler oder Nicht-Händler (EU-DSA) | **Nicht-Händler** (kostenlos, keine Monetarisierung). Im Zweifel rechtlich absichern, siehe A1 | offen |
| D3 | Store-Sprachen und Länder | Deutsch als Hauptsprache, englischer Eintrag mit Hinweis „UI nur Deutsch“, alle Länder; oder vorsichtig nur DACH | offen |
| D4 | Entwicklername und öffentliche Kontakt-E-Mail | eigener Name oder neutrales Kürzel; **eigene E-Mail-Adresse nur für die App** | offen |
| D5 | Signatur: eigener Schlüssel (A) oder Google-Schlüssel (B) | **A**, sonst kein Wechsel zwischen GitHub und Play ohne Datenverlust. Nicht umkehrbar | offen |
| D6 | Wo die Datenschutzerklärung und Anleitung liegen | **GitHub Pages** aus dem Ordner `/docs` auf `main`. URL dann `https://ilianp.github.io/android-voice-message-transcriber/datenschutz` | offen |

### Aufgaben

| ID | Wer | Aufgabe | Wartet auf | Status |
|---|---|---|---|---|
| A1 | Du | Rechtsfragen klären: Impressum (§ 5 DDG), Händlerstatus, Pflichtangaben der Datenschutzerklärung, Daten Dritter ([05](05-kritische-fragen.md) K1, K5) | – | offen |
| A2 | Du | Namensrecherche: Play-Suche, DPMA, EUIPO nach „Audio-Transkript“ (K6) | – | offen |
| A3 | Du | Registrierung nach Checkliste [01](01-registrierung.md) | D1, D2, D4 | offen |
| A4 | Du | GitHub Pages aktivieren: *Repo → Settings → Pages → Branch `main`, Ordner `/docs`* | D6 | offen |
| A5 | Du | PEPK-Export des Release-Keys hochladen, Upload-Key erzeugen, Upload-Secrets anlegen ([02](02-technik.md)) | D5, A3 | offen |
| A6 | Du | Liste mit 15–20 Testern (Google-Konto-Adressen), Google Group anlegen | – | offen |
| A7 | Du | Kostenlosen Groq-Key `play-review` für die Prüfer anlegen | – | offen |
| A8 | Du | Video für die Vordergrunddienst-Erklärung aufnehmen | PR gemergt | offen |
| A9 | Du | Gerätetest mit targetSdk 36 auf Android 16 (Zurück-Geste, Player-Leiste, Benachrichtigung) | PR gemergt | offen |
| A10 | Du | Nach Upload: Fingerabdruck in der Console = CLAUDE.md-Wert, Gültigkeit des Release-Keys > 22.10.2033 | A5 | offen |
| A11 | Du | Status der Entwicklerverifizierung in der Console prüfen (deckt sie die GitHub-APKs mit ab?) | A3 | offen |
| A12 | Du | Grafiken: Symbol 512 px, Vorstellungsgrafik 1024×500, 4–8 Screenshots mit Beispielnachricht | – | offen |
| A13 | Du | PR „Play-Store-Vorbereitung“ prüfen und mergen | A14 | offen |
| A14 | KI | PR durch die CI bringen (targetSdk 36 mit AGP 8.5.2 ist ungetestet, ggf. AGP-Update) | – | in Arbeit |
| A15 | KI | Datenschutzerklärung finalisieren, ⚠️-Stellen einarbeiten, Links in Store-Texten und App setzen | A1, D6 | offen |

### Ideen

| ID | Idee |
|---|---|
| I1 | AAB automatisch nach Play hochladen (Play Developer API, Dienstkonto), erst nach dem ersten manuellen Release |
| I2 | Hinweis in der App: „Transkripte und Zusammenfassungen können Fehler enthalten“ |
| I3 | OpenRouter-Transkription mit `data_collection: deny` / `zdr: true` wie bei der Zusammenfassung |
| I4 | `security-crypto` (veraltet) ablösen |
| I5 | App-Oberfläche auf Englisch übersetzen (`strings.xml`), wenn der englische Store-Eintrag kommt |

### Warnungen

| ID | Warnung |
|---|---|
| W1 | Die Play Console schlägt standardmäßig einen **Google-erzeugten** Signaturschlüssel vor. Einmal veröffentlicht, ist das nicht mehr änderbar (→ D5) |
| W2 | versionCode = `run_number` von `release.yml`. Den Workflow nie umbenennen oder neu anlegen, und in Play nur das AAB aus demselben Lauf hochladen |
| W3 | Frist Händlerstatus-Erklärung **1.2.2027** |
| W4 | Play-Hilfeseiten nur als Suchauszug geprüft; Werte (Testerzahl, öffentliche Daten, Gerätezahl begrenzte Verteilung 20 vs. 50) beim Registrieren gegenlesen |
