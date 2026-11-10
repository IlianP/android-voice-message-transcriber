# 4 – Ablauf in der Play Console bis zur Veröffentlichung

Reihenfolge, wie sie nach der Registrierung ([01](01-registrierung.md)) abzuarbeiten ist.

1. **App anlegen:** Name `Audio-Transkript`, Standardsprache Deutsch, App (kein Spiel),
   kostenlos. Bestätigen, dass die Richtlinien und US-Exportgesetze eingehalten werden.
   *Kostenlos* lässt sich später nicht mehr in *kostenpflichtig* ändern.
2. **App-Signatur festlegen, bevor irgendetwas hochgeladen wird**: Variante A, eigener Schlüssel
   per PEPK, und Upload-Zertifikat registrieren. Siehe [02](02-technik.md#t3t4--signatur-play-und-github-mit-demselben-schlüssel).
   Danach Fingerabdruck vergleichen.
3. **App-Inhalte** (*Richtlinien → App-Inhalte*) ausfüllen, Vorlagen in
   [03](03-store-eintrag.md):
   - Datenschutzerklärung (URL), App-Zugriff (Review-Key), Werbung: keine,
     Inhaltseinstufung, Zielgruppe 18+, Datensicherheit, Vordergrunddienste (Video),
     Behörden-App: nein, Finanzfunktionen: keine, Gesundheit: keine, ggf. KI-Erklärung.
4. **Store-Eintrag:** Texte, Symbol 512 px, Vorstellungsgrafik, Screenshots, Kategorie
   *Produktivität* (Alternative: *Tools*), Kontakt-E-Mail, Website (Repo oder Anleitungsseite).
5. **Geschlossener Test:** Track anlegen, Google Group als Tester, AAB aus dem letzten
   `release.yml`-Lauf hochladen (Artefakt `Audio-Transkript-<Version>-aab`), Länder wählen,
   zur Prüfung senden. Die erste Prüfung kann **mehrere Tage** dauern.
6. **Opt-in-Link** an die Tester. **14 Tage** mit ≥ 12 Testern laufen lassen, zwischendurch ein
   Update hochladen.
7. **Produktionszugriff beantragen** (Dashboard). Fragen zu Test und Feedback beantworten. Die
   Prüfung dauert laut Erfahrungsberichten bis etwa eine Woche.
8. **Produktions-Release:** dasselbe oder ein neueres AAB in *Produktion*, Länder wählen,
   ggf. gestaffelt ausrollen (z. B. 20 %), zur Prüfung senden.
9. **Danach bei jedem Update:** AAB aus dem `release.yml`-Lauf hochladen, Release-Notes kurz auf
   Deutsch. Das GitHub-Release erscheint wie bisher automatisch.

**Fristen im Blick behalten:**

- Händlerstatus-Erklärung bis **1.2.2027** (falls nicht bei der Registrierung erledigt).
- Ziel-API-Level: Google hebt die Anforderung jedes Jahr an, typischerweise zum 31. August.
  Für 2027 ist API 37 zu erwarten, eine offizielle Ankündigung war am 10.11.2026 noch nicht
  auffindbar.
- Entwicklerverifizierung weltweit „2027“: Status in der Console prüfen.
