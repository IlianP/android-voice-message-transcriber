---
title: Datenschutzerklärung – Audio-Transkript
---

> **ENTWURF, nicht veröffentlichen, bevor die mit ⚠️ markierten Punkte geklärt sind.**
> Dieser Text wurde ohne Rechtsberatung erstellt. Er beschreibt, was die App technisch tut,
> Stand Version 1.0.13 (Oktober 2026). Die juristische Einordnung (Rollen nach DSGVO,
> Rechtsgrundlagen, Pflichtangaben) muss vor der Veröffentlichung geprüft werden, z. B. mit einem
> seriösen Generator oder einer Beratung. Diesen Hinweis-Kasten vor dem Veröffentlichen entfernen.

# Datenschutzerklärung für die App „Audio-Transkript“

Stand: ⚠️ *Datum einsetzen*

## 1. Wer ist verantwortlich?

⚠️ *Name und ladungsfähige Anschrift bzw. eine Kontaktadresse, E-Mail-Adresse.* Ob hier eine
Wohnanschrift stehen muss, ist offen, siehe `docs/play-store/05-kritische-fragen.md`.

## 2. Das Wichtigste in Kürze

- Die App hat **keinen eigenen Server**. Der Entwickler erhält weder deine Sprachnachrichten noch
  Transkripte, API-Keys, Nutzungsdaten oder Absturzberichte.
- Eine Sprachnachricht verlässt dein Gerät nur, wenn **du** sie zur Transkription teilst oder
  auswählst. Sie geht dann **direkt** an den Transkriptionsdienst, dessen API-Key **du** in der
  App eingetragen hast.
- Die App enthält **keine Werbung, kein Tracking und keine Analyse-Bibliotheken**.
- Verlauf, Einstellungen und Keys bleiben auf deinem Gerät und sind vom Cloud-Backup
  ausgenommen.

## 3. Welche Daten verarbeitet die App und wohin gehen sie?

### 3.1 Sprachnachrichten (Audio)

Teilst du eine Audiodatei mit der App oder wählst du sie in der App aus, liest die App die Datei
und sendet sie verschlüsselt (HTTPS) an **einen** der folgenden Dienste. Die Reihenfolge ist fest:
Der erste Dienst, für den du einen Key hinterlegt hast, wird benutzt. Schlägt er fehl, versucht
die App den nächsten.

| Dienst | Anbieter | Was übertragen wird | Speicherung beim Anbieter |
|---|---|---|---|
| OpenRouter (Modell Microsoft MAI-Transcribe-2) | OpenRouter, Inc., USA | Audio, gewählte Sprache | ⚠️ laut OpenRouter-Richtlinien prüfen und zitieren |
| Groq (Modell Whisper) | Groq, Inc., USA | Audio, gewählte Sprache | ⚠️ laut Groq-Richtlinien prüfen und zitieren |
| Soniox | Soniox, Inc., USA | Audio | Die App lädt die Datei hoch und löscht sie samt Transkript beim Anbieter direkt nach dem Abruf. Reste nach einem Absturz räumt sie beim nächsten Start ab. |

Die Übermittlung geschieht über **dein eigenes Konto** bei dem jeweiligen Dienst und unter dessen
Nutzungs- und Datenschutzbedingungen, denen du bei der Anmeldung zugestimmt hast:

- OpenRouter: <https://openrouter.ai/privacy>
- Groq: <https://groq.com/privacy-policy>
- Soniox: <https://soniox.com/privacy>

⚠️ *Links vor Veröffentlichung prüfen.* ⚠️ *Drittlandübermittlung (USA): Hinweis und
Rechtsgrundlage klären.* Der Vertrag besteht zwischen dir und dem Anbieter, nicht mit dem
Entwickler.

Sprachnachrichten können Daten Dritter enthalten, etwa die Stimme und Aussagen der Person, die
sie aufgenommen hat. ⚠️ *Ob und wie darauf hingewiesen werden muss, klären.*

### 3.2 Zusammenfassung (Text)

Tippst du auf „Zusammenfassen“, sendet die App den **Text** des Transkripts (nicht das Audio)
über OpenRouter an das Sprachmodell DeepSeek V4.1 Flash. Die Anfrage verlangt von OpenRouter,
nur Anbieter zu nutzen, die die Daten **weder speichern noch zum Training verwenden**
(Einstellungen `data_collection: deny` und `zdr: true`). Ohne Tipp wird nie etwas
zusammengefasst.

### 3.3 API-Keys

Deine API-Keys gibst du selbst in der App ein. Sie werden **verschlüsselt** auf dem Gerät
gespeichert (Android Keystore) und ausschließlich an den jeweiligen Dienst gesendet, um die
Anfrage zu autorisieren.

### 3.4 Verlauf und zwischengespeicherte Nachrichten (nur auf dem Gerät)

- **Verlauf:** Die letzten Transkriptionen speichert die App samt einer Kopie der Audiodatei und
  ggf. der Zusammenfassung im privaten App-Speicher. Andere Apps haben darauf keinen Zugriff.
  Gelöscht wird automatisch nach der eingestellten Dauer bzw. Anzahl (Standard: 7 Tage /
  10 Einträge), manuell per langem Druck oder über „Verlauf löschen“.
- **Zwischenspeichern:** Nachrichten, die du mit „Zwischenspeichern“ teilst, liegen bis zur
  Transkription im privaten App-Speicher, höchstens 24 Stunden.
- Beide Verzeichnisse sind vom Android-Cloud-Backup und von der Übertragung auf ein neues Gerät
  **ausgenommen**. Beim Deinstallieren der App werden sie gelöscht.

### 3.5 Wiedergabe-Benachrichtigung

Während du eine Nachricht anhörst, zeigt die App eine Medien-Benachrichtigung an. Sie wird nur
lokal angezeigt.

### 3.6 Bezug über Google Play bzw. GitHub

Lädst du die App über Google Play oder GitHub herunter, verarbeiten diese Plattformen Daten nach
ihren eigenen Datenschutzbestimmungen, z. B. für Download und Updates. Darauf hat der Entwickler
keinen Einfluss. Statistiken, die Google Play dem Entwickler anzeigt (z. B. Installationszahlen,
Absturzberichte über Android vitals), sind zusammengefasst. ⚠️ *Prüfen, ob die Play Console
Absturzberichte mit Gerätedaten zeigt, und ob das hier erwähnt werden muss.*

## 4. Berechtigungen

| Berechtigung | Zweck |
|---|---|
| Internet | Senden der Audiodatei bzw. des Textes an den gewählten Dienst |
| Vordergrunddienst (Medienwiedergabe) | Wiedergabe läuft weiter, wenn die App im Hintergrund ist; zeigt die Wiedergabe-Benachrichtigung |

Die App fragt **nicht** nach Mikrofon, Kontakten, Standort oder Speicherzugriff. Sie sieht nur die
Dateien, die du ihr ausdrücklich teilst oder auswählst.

## 5. Deine Rechte

⚠️ *Abschnitt zu Betroffenenrechten (Auskunft, Löschung usw.) und Beschwerderecht bei einer
Aufsichtsbehörde je nach rechtlicher Einordnung ergänzen.* Praktisch gilt: Daten auf deinem
Gerät löschst du selbst (Verlauf löschen oder App deinstallieren). Daten bei den
Transkriptionsdiensten verwaltest du in deinem Konto dort, und Anfragen dazu richtest du an den
jeweiligen Anbieter.

## 6. Kinder

Die App richtet sich nicht an Kinder. ⚠️ *Zielgruppe in der Play Console entsprechend angeben
(18+ oder 13+), passend zu den Mindestalter-Regeln der Anbieter.*

## 7. Änderungen

Ändert sich die Datenverarbeitung, etwa durch einen neuen Anbieter, wird diese Erklärung
angepasst. Den Verlauf der Änderungen siehst du in der Versionsgeschichte dieser Datei auf GitHub.

## 8. Kontakt

⚠️ *E-Mail-Adresse für Datenschutzanfragen.*
