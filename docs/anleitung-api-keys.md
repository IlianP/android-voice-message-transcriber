---
title: Audio-Transkript einrichten – eigenen API-Key anlegen
---

# Audio-Transkript einrichten: eigenen API-Key anlegen

Audio-Transkript schickt deine Sprachnachricht an einen **Transkriptionsdienst** und zeigt dir den
Text an. Die App hat keinen eigenen Server und kein Abo. Du meldest dich deshalb selbst bei
einem Dienst an und trägst dessen **API-Key** in der App ein. Ein API-Key ist ein langes Passwort,
an dem der Dienst erkennt, dass die Anfrage von dir kommt und über dein Konto abgerechnet wird.

> **Kurz gesagt:** Ein Key genügt. Kostenlos und ohne Kreditkarte geht es mit **Groq**.
> Die beste Erkennung und die Zusammenfassung bekommst du mit **OpenRouter** (5 $ Guthaben reichen
> für viele Stunden Sprachnachrichten).

*Stand: November 2026. Preise und die Beschriftung der Webseiten ändern sich gelegentlich. Wenn
etwas anders aussieht als hier beschrieben, findest du den Punkt meist unter „API Keys“ oder
„Keys“ im Menü.*

---

## Welchen Anbieter brauche ich?

| | **OpenRouter** (empfohlen) | **Groq** (kostenlos) | **Soniox** (Reserve) |
|---|---|---|---|
| Wofür in der App | Transkription (erste Wahl) **und Zusammenfassung** | Transkription, wenn OpenRouter fehlt oder ausfällt | Transkription, wenn beide anderen ausfallen |
| Kosten | Guthaben im Voraus, ab 5 $ (zzgl. ca. 5,5 % Gebühr). Transkription rund **0,10 $ pro Stunde Audio** | **Kostenloser Tarif** mit Tageslimit, keine Kreditkarte nötig | rund 0,10 $ pro Stunde Audio, Zahlungsmittel nötig |
| Was kostet eine 1-Minuten-Nachricht? | ca. 0,2 Cent | nichts (im Gratis-Rahmen) | ca. 0,2 Cent |
| Erkennungsqualität | sehr gut (Microsoft MAI-Transcribe-2) | gut (Whisper) | sehr gut |
| Anmeldung | E-Mail oder Google-Konto | E-Mail oder Google-Konto | E-Mail |

**Empfehlung:**

- **Du willst nichts bezahlen:** nur Groq. Damit funktioniert die Transkription. Die
  Zusammenfassung langer Nachrichten gibt es dann nicht, weil sie über OpenRouter läuft.
- **Du willst das Beste:** OpenRouter und zusätzlich Groq als kostenlose Reserve. Fällt
  OpenRouter einmal aus oder ist das Guthaben leer, springt Groq automatisch ein.
- **Soniox** brauchst du normalerweise nicht.

Das Gratis-Kontingent von Groq liegt laut Drittquellen derzeit bei mehreren Stunden Audio pro Tag.
Für Sprachnachrichten reicht das in aller Regel. Die genauen Grenzen zeigt Groq in seiner
Konsole unter „Limits“ an.

---

## Variante A: Groq (kostenlos)

1. Öffne im Browser **[console.groq.com](https://console.groq.com)**.
2. Melde dich an: mit deinem Google-Konto oder mit E-Mail-Adresse und Bestätigungslink.
3. Wähle im Menü **„API Keys“** und tippe auf **„Create API Key“**.
4. Gib dem Key einen Namen, zum Beispiel `Audio-Transkript`, und bestätige.
5. Der Key wird **nur einmal** angezeigt. Er beginnt mit `gsk_`. Kopiere ihn mit dem
   Kopier-Symbol.
6. Weiter mit [Key in der App eintragen](#key-in-der-app-eintragen). Trage ihn dort ins Feld
   **„Groq API-Key“** ein.

## Variante B: OpenRouter (beste Qualität, mit Zusammenfassung)

1. Öffne **[openrouter.ai](https://openrouter.ai)** und tippe auf **„Sign in“**. Anmelden kannst du
   dich mit Google, GitHub oder E-Mail.
2. **Guthaben aufladen:** Menü → **„Credits“** → **„Add Credits“**. Der kleinste Betrag liegt
   bei 5 $. Dazu kommt eine Gebühr von rund 5,5 %. Bezahlen kannst du mit Karte und je nach Land
   mit weiteren Mitteln. Es gibt **kein Abo**: Abgebucht wird nur, was du benutzt. Nicht verbrauchtes Guthaben kann
   laut OpenRouter nach einem Jahr verfallen.
3. **Key anlegen:** Menü → **„Keys“** → **„Create Key“**. Gib ihm einen Namen, zum Beispiel
   `Audio-Transkript`.
4. **Tipp:** Setze beim Anlegen ein **Ausgabelimit** („Credit limit“), zum Beispiel 2 $. Gerät
   der Key in falsche Hände, kann damit nicht mehr ausgegeben werden.
5. Kopiere den Key. Er beginnt mit `sk-or-` und wird nur einmal angezeigt.
6. Trage ihn in der App ins Feld **„OpenRouter API-Key“** ein.

## Variante C: Soniox (optional)

1. Öffne **[soniox.com](https://soniox.com)** und registriere dich. Der Weg führt in die
   Soniox-Konsole.
2. Hinterlege unter „Billing“ ein Zahlungsmittel bzw. lade Guthaben auf.
3. Lege unter **„API Keys“** einen neuen Key an und kopiere ihn.
4. Trage ihn in der App ins Feld **„Soniox API-Key“** ein.

---

## Key in der App eintragen

1. Öffne **Audio-Transkript**.
2. Tippe auf **„Einstellungen öffnen“** oder auf das **Zahnrad** oben rechts.
3. Tippe lange in das passende Feld und wähle **„Einfügen“**.
4. Fertig. Der Key wird sofort gespeichert, einen Speichern-Knopf gibt es nicht. Mit dem Pfeil
   oben links kommst du zurück.

**Ausprobieren:** Öffne in WhatsApp (oder einer anderen App) eine Sprachnachricht. Tippe lange
darauf, dann auf **Teilen** und wähle **„Audio-Transkript → Transkript starten“**.

---

## Häufige Fragen

**Was kostet mich das im Monat?**
Bei OpenRouter etwa 0,10 $ pro Stunde Sprachnachrichten. Wer täglich zehn Nachrichten zu je einer
Minute bekommt, zahlt grob **2 Cent im Monat**. Eine Zusammenfassung kostet Bruchteile eines Cents.
Die 5 $ Startguthaben reichen also sehr lange. Mit Groq allein ist es im Gratis-Rahmen kostenlos.

**Wer sieht meine Sprachnachrichten?**
Nur der Dienst, dessen Key du einträgst. Die App schickt das Audio direkt von deinem Handy dorthin,
ohne Umweg über einen Server des App-Entwicklers. Für die Verarbeitung gelten die
Datenschutzbestimmungen des jeweiligen Dienstes. Details stehen in der
[Datenschutzerklärung](datenschutz.md).

**Ist mein Key sicher?**
Die App speichert ihn verschlüsselt auf deinem Gerät und schickt ihn nur an den zugehörigen
Dienst. Gib ihn trotzdem nicht weiter: Wer deinen Key hat, kann auf deine Kosten Anfragen stellen.
Bei OpenRouter begrenzt ein Ausgabelimit den möglichen Schaden.

**Ich habe meinen Key verloren oder vermute Missbrauch.**
Lösche den Key auf der Webseite des Anbieters (gleiche Seite wie beim Anlegen) und lege einen neuen
an. Den neuen Key trägst du in der App ein, der alte ist damit wertlos.

**Kann ich den Key eines Freundes benutzen?**
Technisch ja, aber dann zahlt dein Freund für deine Nachrichten und sieht sie in seinem Verbrauch.
Besser legt jeder seinen eigenen Key an.

**Die App meldet einen Fehler mit „401“ oder „Unauthorized“.**
Der Key ist falsch eingefügt (Leerzeichen, abgeschnitten) oder wurde gelöscht. Kopiere ihn erneut
oder lege einen neuen an.

**Die App meldet „402“, „insufficient credits“ oder „rate limit“.**
Bei OpenRouter ist das Guthaben aufgebraucht oder das Ausgabelimit erreicht. Bei Groq ist das
Gratis-Kontingent für heute verbraucht. Ist ein zweiter Anbieter eingetragen, übernimmt er
automatisch.
