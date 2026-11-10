# 1 – Registrierung: Checkliste zum Abarbeiten

**Quellenlage (10.11.2026):** Die Hilfeseiten der Play Console (support.google.com) waren aus der
Recherche-Session nur über Suchmaschinen-Auszüge erreichbar, nicht im Volltext. Gelesen wurde
developer.android.com. Was nur aus Auszügen stammt, ist mit *(Auszug)* markiert und sollte beim
Registrieren am Bildschirm kurz gegengeprüft werden. Quellen stehen am Ende.

## Vorab entscheiden

### Privates Konto oder Organisation?

| | **Privat** (Empfehlung) | **Organisation** |
|---|---|---|
| Voraussetzung | Ausweis, Zahlungsmittel auf deinen Namen | Eingetragene Organisation (z. B. Gewerbe/GmbH/Verein) **und D-U-N-S-Nummer**. Kostenlos, aber bis zu ~30 Tage Wartezeit |
| Testpflicht vor dem Start | **Ja**: 12 Tester × 14 Tage geschlossener Test | Nein |
| „Gerät verifizieren“ | Ja (Play-Console-App auf eigenem Android-Gerät) | Nein |
| Sichtbar im Store | Entwicklername, Land, Kontakt-E-Mail. Adresse/Telefon nur als „Händler“, siehe unten | Name der Organisation, Adresse, Telefon, Website |
| Wechsel später | Nur über Übertragung der App auf ein neues Konto | – |

Für eine kostenlose Hobby-App einer Privatperson ist **privat** der realistische Weg. Für ein
Organisationskonto bräuchtest du eine Organisation mit D-U-N-S-Nummer, und eine solche Gründung
nur für Play lohnt nicht. Siehe Entscheidung **D1**.

### Händler oder Nicht-Händler (EU Digital Services Act)?

Neu seit Anfang November 2026 *(Auszug)*: Jedes Konto muss erklären, ob es **Händler (trader)**
im Sinne von EWR-/UK-Recht ist. Ort: *Play Console → Konto verwalten → Kontodetails → Über
dich*. Laut Hilfeseite gilt die Frist **bis 1.2.2027**, eine Policy-Ankündigung vom 29.10.2026
sagt „ab November 2026“. Am besten gleich bei der Registrierung erledigen.

- **Nicht-Händler:** Im Store sind nur **E-Mail und Land** sichtbar. EU-Nutzer sehen den
  Hinweis, dass für Verträge mit dir kein Verbraucherschutzrecht gilt. Bei einer kostenlosen
  App gibt es keinen Kaufvertrag, der Hinweis schadet also kaum.
- **Händler:** **Name, Anschrift, Telefon, E-Mail** sind öffentlich.
- Google beschreibt Hobby- bzw. nicht-kommerzielle Tätigkeit als Anhaltspunkt gegen den
  Händlerstatus. Eine falsche Erklärung kann zur Sperrung führen. Rechtlich bindend einordnen
  kann das nur eine Beratung → **D2**, siehe auch [K1](05-kritische-fragen.md).

## Schritt für Schritt

| # | Schritt | Was du brauchst | Zeit |
|---|---|---|---|
| 1 | **Google-Konto wählen**, am besten ein eigenes für die Entwicklung statt des privaten Hauptkontos. Der Kontoinhaber lässt sich später nicht einfach wechseln | Google-Konto | 10 min |
| 2 | **2-Schritt-Verifizierung** an diesem Google-Konto einschalten (Authenticator-App oder Passkey, nicht nur SMS). Ob Play sie zwingend verlangt, war nicht im Volltext prüfbar. Für das Konto, an dem Signatur und App hängen, ist sie ohnehin Pflicht aus Vernunft | Handy | 5 min |
| 3 | **Kontakt-E-Mail festlegen**, die **öffentlich** im Store steht, z. B. eine eigene Adresse für die App. Google verifiziert sie per Code | Postfach | 5 min |
| 4 | **Telefonnummer** für Google. Sie wird verifiziert, ist aber als Nicht-Händler nicht öffentlich | Handy | 5 min |
| 5 | **Entwicklername** wählen: Er steht unter dem App-Namen im Store. Bei privaten Konten darf er vom bürgerlichen Namen abweichen, aber nicht irreführend sein oder Marken Dritter enthalten. Vorschlag: dein Name oder ein neutrales Kürzel → **D4** | – | – |
| 6 | Auf **play.google.com/console/signup** Kontotyp **„Für mich selbst“ (privat)** wählen | – | – |
| 7 | **Payments-Profil**: rechtlicher Name und **Adresse**. Sie werden verifiziert, sind aber als Nicht-Händler nicht öffentlich *(Auszug)*. Name exakt wie im Ausweis | Ausweis daneben legen | 10 min |
| 8 | **25 USD bezahlen**, einmalig, keine Jahresgebühr. Kredit- oder Debitkarte auf **deinen Namen**. Nicht erstattbar, falls die Identitätsprüfung scheitert *(Auszug)* | Karte | 5 min |
| 9 | **Identitätsprüfung**: Foto von Personalausweis oder Reisepass, ggf. Adressnachweis. Name muss mit dem Payments-Profil übereinstimmen | Ausweis, evtl. Meldebescheinigung/Rechnung | 15 min + Wartezeit (Stunden bis einige Tage; offizielle Angabe nicht gefunden) |
| 10 | **Gerät verifizieren**: Play-Console-App auf einem echten, nicht gerooteten Android-Gerät (ab Android 10) installieren, mit demselben Google-Konto anmelden, QR-Code scannen → „Verify“. Kommt **vor** der Telefonverifizierung *(Auszug)* | Android-Handy | 5 min |
| 11 | **Händlerstatus erklären** (siehe oben) | Entscheidung D2 | 5 min |
| 12 | Fragebogen zu Erfahrung/Absicht ausfüllen (Google fragt bei privaten Konten nach Erfahrung mit Android und geplanten Apps) | – | 10 min |

Danach: App in der Console anlegen, siehe [04-ablauf-bis-produktion.md](04-ablauf-bis-produktion.md).

## Testpflicht: 12 Tester × 14 Tage

**Stand** *(Auszug, support.google.com/…/answer/14151465)*: Private Konten, die nach dem
13.11.2023 angelegt wurden, brauchen vor dem Antrag auf Produktionszugriff einen **geschlossenen
Test mit mindestens 12 Testern, die 14 Tage ununterbrochen eingetragen sind**. Früher waren es
20 Tester. Die Senkung auf 12 ist laut Community-Quellen von Dezember 2024 und offiziell nicht
datiert.

Was zählt:

- Ein Tester zählt nur, wenn er **opted-in** ist, also den Opt-in-Link angenommen hat. Wer vor
  Ablauf austritt, zählt nicht. Wer aus- und wieder eintritt, beginnt die 14 Tage neu.
- Google kann **weitere Tests** verlangen, wenn die Tester die App kaum nutzen. Die Tester sollten
  die App also wirklich installieren und ein paar Mal benutzen, idealerweise mit eigenem Key.
- Jeder Tester braucht ein **Google-Konto** und ein Android-Gerät mit Play Store.

**Plan:**

1. **Liste mit 15–20 Personen** anlegen (Puffer, falls jemand abspringt). Gut geeignet sind
   Menschen, die die App ohnehin nutzen wollen, also die künftigen Nutzer. Pro Person die
   **Gmail-/Google-Konto-Adresse** notieren, die auf ihrem Handy im Play Store angemeldet ist.
   → **A6** (du)
2. **Google Group** anlegen (z. B. `audio-transkript-test@googlegroups.com`). Das ist einfacher als
   eine E-Mail-Liste: Tester treten der Gruppe bei, du trägst in Play nur die Gruppe ein.
3. In der Play Console: *Testen → Geschlossener Test → Track anlegen → Tester: Google Group*.
   Erstes AAB hochladen, Store-Eintrag-Pflichtangaben ausfüllen, Track **zur Prüfung
   einreichen**.
4. Nach der Freigabe erscheint der **Opt-in-Link**. Ihn mit einer kurzen Anleitung an alle
   schicken. Vorlage unten.
5. **Tag 0 notieren**, wenn 12 Tester eingetragen sind. Nach 14 Tagen zeigt das Dashboard den
   Button „Produktionszugriff beantragen“. Dort fragt Google nach Testverlauf, Feedback und
   Änderungen. Kurz und ehrlich beantworten.
6. Während des Tests: mindestens **ein Update** in den Test-Track schieben, z. B. eine kleine
   Korrektur nach Tester-Feedback. Das belegt aktive Pflege, und die Fragen im Antrag beziehen
   sich darauf.

**Einladungstext für Tester (Vorlage):**

> Hallo! Ich bringe meine App „Audio-Transkript“ in den Play Store. Google verlangt vorher einen
> 14-tägigen Test mit mindestens 12 Personen, und ich würde mich freuen, wenn du dabei bist.
> 1. Tritt dieser Gruppe bei: ⚠️ <Link zur Google Group>. Nimm das Google-Konto, das auf deinem
>    Handy im Play Store angemeldet ist.
> 2. Öffne danach diesen Link und tippe auf „Tester werden“: ⚠️ <Opt-in-Link>
> 3. Installiere die App über den Play Store und richte sie mit einem kostenlosen Groq-Key ein:
>    ⚠️ <Link zur Anleitung>
> 4. Bitte **14 Tage nicht austreten** und die App ab und zu benutzen. Feedback gern an mich.
> Wenn du sie schon über GitHub/Obtainium installiert hast: Das passt, siehe unten.

⚠️ Hinweis zu Testern mit GitHub-Installation: siehe [02-technik.md](02-technik.md), Abschnitt
„Installationsquellen“. Mit gleichem Signaturschlüssel ist der Wechsel ohne Datenverlust
möglich.

## Android-Entwicklerverifizierung (gilt auch außerhalb von Play)

*(gelesen: developer.android.com/developer-verification)*

- Ab **30.9.2026** gilt die Pflicht in Brasilien, Indonesien, Singapur und Thailand, **weltweit ab
  2027**. Ein genaues Datum hat Google noch nicht genannt. Ab dann installieren zertifizierte
  Geräte nur noch Apps verifizierter Entwickler, auch per Sideload.
- **Mit Play-Konto:** Apps mit Play App Signing werden **automatisch** registriert. Für Apps
  außerhalb von Play nutzt man dieselbe Play Console, ggf. mit Nachweis über den privaten
  Schlüssel. Wenn Play-APKs und GitHub-APKs **denselben Signaturschlüssel** haben (Empfehlung in
  02), sollte die Registrierung die GitHub-APKs mit abdecken. Das ist eine Schlussfolgerung,
  ausdrücklich steht es so nicht da. Nach der Registrierung im Bereich
  „Entwicklerverifizierung“ der Console prüfen → **A11**.
- **Ohne Play:** kostenloses Konto „begrenzte Verteilung“ in der Android Developer Console,
  ohne Ausweis, aber mit Google-Konto, 2-Schritt-Verifizierung und Payments-Profil. Laut
  Leitfaden für **50 Geräte**, laut Hauptseite und FAQ vom Juni 2026 für **20 Geräte**
  (widersprüchlich). Für „mehrere Menschen“ würde das reichen, ist aber eine Sackgasse: kein
  Store, und ein Upgrade auf das volle Konto kostet ebenfalls 25 USD.
- ADB-Installationen bleiben ohne Verifizierung möglich, für Endnutzer ist das aber keine Option.

## Quellen

- Testpflicht: <https://support.google.com/googleplay/android-developer/answer/14151465> *(Auszug)*
- Tester einladen: <https://support.google.com/googleplay/android-developer/answer/9845334> *(Auszug)*
- Kontotyp / Identität: <https://support.google.com/googleplay/android-developer/answer/13628312>, …/answer/13634885 *(Auszug)*
- Gerät verifizieren: <https://support.google.com/googleplay/android-developer/answer/14316361> *(Auszug)*
- Händlerstatus: <https://support.google.com/googleplay/android-developer/answer/17594752>, Ankündigung …/answer/18100766 *(Auszug)*
- Entwicklerverifizierung: <https://developer.android.com/developer-verification>, <https://developer.android.com/developer-verification/guides/limited-distribution>, <https://developer.android.com/developer-verification/guides/google-play-console>, <https://developer.android.com/developer-verification/guides/faq>
