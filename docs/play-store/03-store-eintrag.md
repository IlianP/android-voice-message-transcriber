# 3 – Store-Eintrag: Texte, Datensicherheit, Einstufung (Entwürfe)

Alles hier sind **Entwürfe** zum Kopieren in die Play Console. Mit ⚠️ markierte Stellen musst du
selbst entscheiden oder prüfen. Längengrenzen der Play Console: Titel 30 Zeichen,
Kurzbeschreibung 80, Langbeschreibung 4.000.

## 3.1 Titel

| Sprache | Vorschlag | Zeichen |
|---|---|---|
| de-DE | `Audio-Transkript` | 16 |
| en-US | `Audio-Transkript – Voice to Text` oder schlicht `Audio-Transkript` | 32 → zu lang, also z. B. `Audio-Transkript: Voice Notes` (29) |

⚠️ Der Name ist beschreibend und wahrscheinlich nicht schutzfähig. Gleichnamige Apps prüfen,
siehe [05-kritische-fragen.md](05-kritische-fragen.md), Frage K6.
Google verbietet im Titel Emojis, Großbuchstaben-Folgen ohne Markenbezug, Ranking-Wörter wie
„Beste“ oder „Nr. 1“ und Preis-Hinweise wie „kostenlos“.

## 3.2 Kurzbeschreibung (max. 80 Zeichen)

- **de:** `Sprachnachrichten teilen, als Text lesen und dabei schneller anhören.` (70)
- **en:** `Share voice messages, read them as text and listen faster at the same time.` (76)

## 3.3 Langbeschreibung – Deutsch

```
Keine Zeit, eine lange Sprachnachricht anzuhören? Teile sie mit Audio-Transkript und lies sie
als Text, auf Wunsch während sie in 1,5- oder 2-facher Geschwindigkeit läuft.

SO FUNKTIONIERT ES
• Sprachnachricht in WhatsApp, Signal, Telegram oder einer anderen App lange drücken → Teilen →
  „Transkript starten“.
• Wenige Sekunden später steht der Text da. Du kannst ihn kopieren, teilen oder markieren.
• Mehrere Nachrichten hintereinander? Mit „Zwischenspeichern“ sammeln und als ein Transkript
  lesen.

FUNKTIONEN
• Transkription in vielen Sprachen, automatische Spracherkennung
• Player mit 1× / 1,5× / 2× / 2,5×, Steuerung auch über Benachrichtigung, Sperrbildschirm und
  Kopfhörer
• Zusammenfassung langer Nachrichten in Stichpunkten, nur wenn du darauf tippst
• Verlauf der letzten Nachrichten auf dem Gerät, Dauer und Anzahl einstellbar
• Keine Werbung, kein Konto bei uns, kein Tracking

DEIN EIGENER API-KEY – WICHTIG
Audio-Transkript hat keinen eigenen Server und kein Abo. Die Transkription übernimmt ein
Dienst deiner Wahl, bei dem du dich selbst anmeldest:
• Groq – kostenloser Tarif mit Tageslimit, keine Kreditkarte nötig
• OpenRouter – beste Erkennung und Zusammenfassung, Guthaben im Voraus (ab 5 $; eine Minute Audio
  kostet etwa 0,2 Cent)
• Soniox – optional als Reserve
Ohne einen solchen Key funktioniert die App nicht. Eine Schritt-für-Schritt-Anleitung findest du
unter: ⚠️ <Link zur Anleitungsseite>

DATENSCHUTZ
Deine Sprachnachricht geht nur, wenn du sie teilst, direkt von deinem Handy an den Dienst, dessen
Key du eingetragen hast. Sie läuft nicht über einen Server des Entwicklers. Verlauf und Keys
bleiben verschlüsselt bzw. privat auf dem Gerät und sind vom Cloud-Backup ausgenommen.

Open Source: ⚠️ <Link zum GitHub-Repository>
```

## 3.4 Langbeschreibung – Englisch (optional)

```
No time to listen to a long voice message? Share it with Audio-Transkript and read it as text,
optionally while it plays at 1.5× or 2× speed.

HOW IT WORKS
• Long-press a voice message in WhatsApp, Signal, Telegram or any other app → Share →
  "Transkript starten" (start transcript).
• A few seconds later the text appears. Copy it, share it or select parts of it.
• Several messages in a row? Collect them with "Zwischenspeichern" (queue) and read them as one
  transcript.

FEATURES
• Transcription in many languages with automatic language detection
• Player with 1× / 1.5× / 2× / 2.5×, controls in the notification, on the lock screen and
  on headphones
• Bullet-point summary of long messages, only when you tap for it
• On-device history of recent messages, retention configurable
• No ads, no account with us, no tracking

YOUR OWN API KEY – IMPORTANT
Audio-Transkript has no server of its own and no subscription. Transcription is done by a service
of your choice that you sign up for yourself:
• Groq – free tier with a daily limit, no credit card needed
• OpenRouter – best accuracy plus summaries, prepaid credits (from $5; one minute of audio costs
  about $0.002)
• Soniox – optional fallback
The app does not work without such a key. Step-by-step guide: ⚠️ <link>

PRIVACY
Your voice message is sent only when you share it, directly from your phone to the service whose
key you entered, never through a server run by the developer. History and keys stay on your
device and are excluded from cloud backup.

Note: the app interface is currently German only.
```

⚠️ Die App-Oberfläche ist nur auf Deutsch. Ein englischer Eintrag ist möglich, sollte das aber
sagen (siehe letzter Satz), sonst gibt es enttäuschte Bewertungen. Alternative: Eintrag nur auf
Deutsch und Verfügbarkeit auf DACH-Länder beschränken. Siehe Entscheidung D3 in der Übersicht.

## 3.5 Grafiken, die du brauchst

| Element | Vorgabe | Woher |
|---|---|---|
| App-Symbol | 512 × 512 PNG, 32 Bit, max. 1 MB | aus `ic_launcher` neu exportieren ⚠️ (es gibt nur die Mipmaps im Repo) |
| Vorstellungsgrafik | 1024 × 500 PNG/JPG | neu gestalten |
| Screenshots Telefon | mind. 2, besser 4–8; Seitenverhältnis max. 2:1, 320–3840 px | echtes Gerät. Die Robolectric-Screenshots zeigen keine Statusleiste und keine echten Inhalte, eignen sich höchstens als Vorlage |

Für Screenshots eine **erfundene, harmlose Beispiel-Nachricht** verwenden, keine echten Chats.

## 3.6 Datensicherheit (Data safety)

Grundlage (*Auszug* aus support.google.com/…/answer/10787469): „Erheben“ (collect) heißt, die
App überträgt Daten **vom Gerät weg**, ausdrücklich auch an Dritte. Dass der Nutzer seinen
eigenen API-Key mitbringt, ist **keine** Ausnahme, die gefunden wurde. Audio an
OpenRouter/Groq/Soniox ist also als **erhoben** anzugeben. Für „Teilen“ (share) gibt es eine
Ausnahme bei Übertragungen, die der Nutzer **gezielt auslöst und erwartet**. Das passt hier,
weil jede Transkription durch Teilen oder Auswählen und jede Zusammenfassung durch einen Tipp
ausgelöst wird. Was nur auf dem Gerät bleibt (Verlauf, Warteschlange, Einstellungen), gilt
**nicht** als erhoben.

### Fragebogen – Vorschlag

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App erforderliche Nutzerdatentypen? | **Ja** |
| Werden alle erhobenen Daten bei der Übertragung verschlüsselt? | **Ja** (alle Anbieter-Endpunkte nur per HTTPS) |
| Bietet ihr Nutzern eine Möglichkeit, das Löschen ihrer Daten zu beantragen? | ⚠️ offen. Beim Entwickler liegen keine Daten. Lokale Daten löscht der Nutzer selbst (Verlauf löschen/deinstallieren), beim Anbieter in seinem eigenen Konto. Vorschlag: **„Nein“** und in der Datenschutzerklärung erklären, warum. Alternativ „Ja“ mit Verweis auf die In-App-Löschung. Vor dem Absenden die Hilfetexte im Formular lesen |

### Datentypen

| Kategorie → Typ | Erhoben | Geteilt | Kurzlebig verarbeitet? | Erforderlich/optional | Zweck |
|---|---|---|---|---|---|
| **Audio → Sprach- oder Tonaufnahmen** | Ja | Nein (Ausnahme: vom Nutzer ausgelöste Übertragung) | ⚠️ Für die App selbst ja (nur im Speicher, bis die Antwort da ist). Beim Anbieter gelten dessen Regeln, Soniox: Upload wird direkt gelöscht. Vorschlag: **Ja** | Erforderlich (Kernfunktion) | App-Funktionalität |
| **App-Aktivität → Andere nutzergenerierte Inhalte** (Transkript-Text für die Zusammenfassung) | Ja | Nein (wie oben) | Ja | **Optional** (nur auf Tipp) | App-Funktionalität |

**Nicht** angeben: Standort, Kontakte, Identifikatoren, Gerätekennungen, Absturzberichte,
Diagnose, Finanzdaten. Die App enthält keine Analytics-, Werbe- oder Crash-SDKs (geprüft an
`app/build.gradle.kts`: Compose, OkHttp, Coroutines, androidx.media, security-crypto).

⚠️ Offen: Ob der **API-Key** selbst eine Angabe braucht. Er ist ein Zugangsschlüssel des Nutzers
zu einem Drittdienst und geht nur an genau diesen Dienst. In den Datentypen gibt es dafür keine
passende Kategorie. Vorschlag: nicht angeben, aber in der Datenschutzerklärung erwähnen (steht
dort schon).

## 3.7 Inhaltseinstufung (IARC-Fragebogen)

Pflicht für jede App. Ohne Einstufung entfernt Google die App (*Auszug*, Klarstellung vom
15.7.2026).

- **Kategorie:** „Alle anderen App-Typen“ (*All Other App Types*). Gemeint ist: kein Spiel,
  kein soziales Netzwerk, kein Browser. (Nicht offiziell belegt, aber die übliche Wahl für
  Werkzeuge.)
- **Antworten:** Gewalt, Sexualität, Sprache, Drogen, Glücksspiel: **Nein**.
  - Können Nutzer **in der App** miteinander kommunizieren oder Inhalte austauschen? **Nein**.
    Das Teilen über das Android-Teilen-Menü ist keine In-App-Kommunikation.
  - Teilt die App den Standort? **Nein**. Digitale Käufe? **Nein**. Uneingeschränkter
    Internetzugang (Browser)? **Nein**.
  - ⚠️ Falls der Fragebogen nach **generativer KI** fragt: **Ja**, die optionale Zusammenfassung
    ist KI-generierter Text aus dem Transkript des Nutzers. Die Transkription selbst ist
    Spracherkennung.
- **Erwartetes Ergebnis:** USK 0 / PEGI 3 / „Everyone“.

**Zielgruppe** (*App-Inhalte → Zielgruppe und Inhalte*): **18+** empfohlen. Die App richtet sich
nicht an Kinder, und die Anbieter haben eigene Mindestalter (⚠️ in deren AGB prüfen: OpenRouter,
Groq, Soniox). Kein Kinder-Schwerpunkt, also gilt die Familienrichtlinie nicht.

**KI-Inhalte-Richtlinie** (*Auszug* …/answer/14094294): Apps mit generativer KI brauchen eine
Meldefunktion in der App. **Ausgenommen** sind ausdrücklich Apps, die nur nicht-KI-Inhalte
zusammenfassen, sowie Produktivitäts-Apps, die eine bestehende Funktion mit KI verbessern. Das
trifft hier zu: Die App fasst das Transkript des Nutzers zusammen. Voraussichtlich ist also
keine Meldefunktion nötig. ⚠️ Beim Ausfüllen der Console-Seite „KI-generierte Inhalte
erklären“ (…/answer/17262077) gegenprüfen.

## 3.8 App-Zugriff für Prüfer

Die Kernfunktion ist ohne API-Key nicht erreichbar. Google verlangt für eingeschränkte Funktionen
dauerhaft gültige, wiederverwendbare Zugangsdaten **auf Englisch** (*Auszug*
…/answer/15748846). Ausdrücklich zu „Bring your own API key“ war nichts zu finden. Prüfer
müssen die Kernfunktion aber erreichen können, sonst droht eine Ablehnung.

**Vorschlag:** einen **eigenen kostenlosen Groq-Key nur für die Prüfung** anlegen, z. B. mit dem
Namen `play-review`. Der Gratis-Tarif kostet nichts, und den Key kannst du jederzeit löschen
und ersetzen → **A7**.

*App-Inhalte → App-Zugriff → „Alle oder einige Funktionen sind eingeschränkt“ → Anleitung
hinzufügen:*

```
Name: Transcription API key (Groq)
Username: (not needed)
Password: gsk_⚠️<review key>
Instructions:
The app has no account system. It needs an API key for a transcription service, which each
user creates themselves (guide: ⚠️<link>). For review, please use the Groq key above:
1. Open the app and tap "Einstellungen öffnen" (Open settings) or the gear icon (top right).
2. Paste the key into the field "Groq API-Key" and go back with the arrow (top left).
   It is saved automatically.
3. Record a short voice memo with any recorder app (or use any audio file), then share it
   via the Android share sheet and choose "Audio-Transkript → Transkript starten".
   Alternatively tap the folder icon in the app and pick an audio file.
4. The transcript appears; tap play to listen. The summary feature needs an OpenRouter key
   and is optional.
The app UI is in German.
```
