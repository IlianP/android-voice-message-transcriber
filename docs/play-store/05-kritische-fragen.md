# 5 – Kritische Fragen vor dem Store-Start

Diese Punkte sollten geklärt sein, **bevor** die App öffentlich in Play steht. Danach lassen sich
manche nur noch schwer ändern (Kontotyp, Paketname, angezeigte Adresse, Signaturschlüssel).
Rechtliche Einschätzungen hier sind **keine Rechtsberatung**, sondern benennen die Frage.
Wo „offen“ steht, habe ich bewusst nichts behauptet.

## K1 – Impressum und angezeigte Adresse

- In Deutschland verlangt § 5 DDG (früher § 5 TMG) ein Impressum für „geschäftsmäßige, in der
  Regel gegen Entgelt angebotene digitale Dienste“. Ob eine **kostenlose, werbefreie
  Open-Source-App einer Privatperson** darunter fällt, ist **offen**. Die Auslegung von
  „geschäftsmäßig“ ist weit, eine verbindliche Antwort gibt nur eine Rechtsberatung.
- Unabhängig davon verlangt Google bei Konten mit EU-Bezug eine Erklärung, ob du **Händler
  (trader)** im Sinne des EU-Digital-Services-Act bist. Wer „Händler“ angibt, dessen Adresse und
  Telefonnummer werden **öffentlich** angezeigt. Für eine kostenlose Hobby-App ist „kein Händler“
  naheliegend, aber Google gibt hier keine Rechtsauskunft. Details in
  [01-registrierung.md](01-registrierung.md).
- **Frage an dich:** Bist du bereit, deine Wohnanschrift zu veröffentlichen, falls es nötig
  wird? Wenn nicht: Impressum-Service/ladungsfähige Zustelladresse (kostenpflichtig) oder
  Verzicht auf Play.

## K2 – Haftung

- Die App gibt Transkripte und KI-Zusammenfassungen aus. Beide können **falsch** sein, etwa
  falsch verstandene Zahlen, Namen oder Verneinungen.
- Die Lizenz im Repo (`LICENSE`) schließt die Gewährleistung für den Quellcode aus. Für Nutzer aus
  dem Store gilt sie nicht automatisch. ⚠️ Offen: Braucht es einen kurzen Hinweis in der App oder
  in der Store-Beschreibung („Transkripte können Fehler enthalten, wichtige Inhalte bitte im
  Original anhören“)? **Empfehlung:** Ja, ein Satz kostet nichts.
- Kosten: Nutzer zahlen selbst beim Anbieter. Erzeugt ein Fehler in der App versehentlich viele
  Anfragen, z. B. durch eine Wiederholungsschleife, zahlt der Nutzer. Heute macht die App pro
  Nachricht höchstens einen Versuch je Anbieter. **Empfehlung:** In der Anleitung zum
  Ausgabelimit bei OpenRouter raten (steht schon drin).

## K3 – Support

- Store-Nutzer erwarten eine Antwort per E-Mail, und die Kontakt-E-Mail ist Pflicht und öffentlich.
  Bewertungen mit „funktioniert nicht“ entstehen fast immer, weil kein Key eingetragen ist.
- **Fragen an dich:** Welche E-Mail-Adresse soll öffentlich stehen? Empfehlung: eine eigene
  Adresse nur für die App, nicht die private. Wie viel Zeit willst du für Support aufwenden?
  Sollen Fragen lieber über GitHub-Issues laufen? Das setzt bei Endnutzern ein GitHub-Konto voraus.
- **Empfehlung:** FAQ in der Anleitung (vorhanden), Hinweis auf den Key ganz oben in der
  Store-Beschreibung, Hilfe-Link in der App (siehe Vorschlag im PR).

## K4 – Missbrauch fremder Keys / Key-Sicherheit

- Jeder Nutzer bringt seinen eigenen Key mit, die App enthält keinen. Gut so: **Niemals einen
  eigenen Key in die App einbauen.** Er wäre aus dem APK auslesbar.
- Risiko für Nutzer: Wer das entsperrte Handy hat, kann den Key aus den Einstellungen nicht
  ablesen (Passwortfeld), aber die App benutzen. Bei Root/Backup-Extraktion ist der Key durch
  `EncryptedSharedPreferences` geschützt; der Rückfall auf unverschlüsselte Preferences auf
  defekten Geräten ist dokumentiert.
- Familie/Freunde, die sich einen Key teilen: Abrechnung und Einsicht in den Verbrauch liegen
  beim Key-Inhaber. Das steht in der Anleitung.
- ⚠️ Offen: Erlauben die Nutzungsbedingungen von OpenRouter/Groq/Soniox, dass Endnutzer ihren Key
  in eine Drittanbieter-App eintragen? Bei Diensten mit „Bring your own key“ ist das üblich, aber
  vor dem Start einmal in deren AGB nachsehen.

## K5 – Daten Dritter in Sprachnachrichten

- Eine Sprachnachricht enthält die Stimme und die Aussagen einer **anderen Person**, nämlich des
  Absenders. Sie wird an einen US-Dienst geschickt.
- ⚠️ Offen: Ob und wie das in der Datenschutzerklärung zu behandeln ist. Die Verantwortung für das
  Teilen liegt praktisch beim Nutzer, die App kann das nicht prüfen.
- Google Play: Die Datensicherheitsangaben beziehen sich auf Daten, die die App erhebt. Dazu
  gehört auch der Audioinhalt, egal wessen Stimme es ist.

## K6 – Name und Marke „Audio-Transkript“

- Beschreibende Begriffe wie „Audio-Transkript“ sind als Marke meist nicht eintragungsfähig. Das
  heißt aber auch: Andere dürfen ähnlich heißen, und Verwechslungen sind möglich.
- **Vor dem Start prüfen:** Play-Store-Suche nach „Audio Transkript“ und „Audio-Transkript“,
  DPMA-Register (<https://register.dpma.de>) und EUIPO (<https://euipo.europa.eu/eSearch>) nach
  Marken mit „Transkript“ in Klasse 9/42. ⚠️ Ergebnis offen, die Recherche ist aus dieser Session
  nicht möglich.
- Der **Paketname** `de.ilianp.audiotranskript` lässt sich nach dem ersten Upload **nie mehr
  ändern**. Der Titel dagegen schon. `de.ilianp…` setzt formal voraus, dass dir `ilianp.de`
  gehört. Google prüft das nicht, aber Konflikte sind möglich. Das bestehende APK nutzt den
  Namen schon, und ein Wechsel würde die Update-Kette brechen. **Empfehlung: beibehalten.**
- Namen Dritter in der Store-Beschreibung (WhatsApp, Signal, Telegram, Microsoft, OpenRouter …):
  Nennung als Kompatibilitätshinweis ist üblich. Google verbietet aber, im Eintrag den Eindruck
  einer Partnerschaft zu erwecken, und Markennamen im Titel. **Empfehlung:** nicht in Titel oder
  Kurzbeschreibung, keine Logos in Screenshots oder Grafik.

## K7 – Kontotyp und Identität

- Ein **privates** Konto zeigt deinen verifizierten Namen bzw. den gewählten Entwicklernamen,
  Land und Kontakt-E-Mail. Ein späterer Wechsel zu einem Organisationskonto ist möglich, aber
  aufwendig (App-Übertragung auf ein neues Konto). Siehe [01-registrierung.md](01-registrierung.md).

## K8 – Zwei Vertriebswege dauerhaft parallel

- GitHub und Play mit **demselben Schlüssel** bedeuten: ein einziges Geheimnis für beide. Geht es
  verloren oder wird es kompromittiert, sind beide Wege betroffen. Details und Alternative in
  [02-technik.md](02-technik.md).
- Doppelte Pflege: Release-Notes, Datenschutzerklärung und Testpflicht gelten für Play. Lohnt
  sich das für „mehrere Menschen“? Alternative: weiter nur GitHub/Obtainium und die künftige
  Entwicklerverifizierung (siehe 01) außerhalb von Play erledigen.

## K9 – Datenschutz beim primären Anbieter

- Die **Zusammenfassung** erzwingt bei OpenRouter `data_collection: deny` und `zdr: true`.
  Die **Transkription** über OpenRouter (`OpenRouterClient.kt`) setzt diese Routing-Vorgaben
  **nicht**. Für die Store-Angabe „Daten werden nicht gespeichert“ wäre es konsistent, das
  nachzuziehen, sofern MAI-Transcribe-2 dann noch erreichbar ist. Idee I3 in der Übersicht.
- Die README-Sektion „Datenschutz“ nannte bis zu dieser Überarbeitung noch Groq als
  Hauptanbieter. Sie ist jetzt korrigiert.
