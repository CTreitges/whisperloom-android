# WhisperLoom im Google Play Store veröffentlichen

Recherchestand: **8. September 2026**. Quellen unten. Diese Anleitung listet, was du für die
Erstveröffentlichung von `com.chris.whisperloom` im Play Store tun musst, und was am Projekt bereits
dafür vorbereitet wurde.

---

## 0. Die vier kritischen Punkte vorweg

1. **targetSdk 36 ist Pflicht.** Seit 31.08.2026 nimmt Play für neue Apps/Updates nur noch
   `targetSdkVersion 36` (Android 16) an. → **erledigt** (im Projekt gesetzt).
2. **Android App Bundle (.aab), kein APK.** → **erledigt** (CI baut jetzt ein signiertes AAB).
3. **Neuer „Personal"-Account: 12 Tester × 14 Tage Closed Testing**, bevor Production freigeschaltet
   wird. Das ist der größte Zeitfaktor (nicht abkürzbar außer über einen Organisations-Account).
4. **Der Accessibility-Service ist der wahrscheinlichste Ablehnungsgrund.** Hier musst du eine
   bewusste Entscheidung treffen → siehe Abschnitt 5.

---

## 1. Entwicklerkonto & Identität

- **Konto anlegen** unter play.google.com/console, **25 USD einmalige Gebühr** (kein Abo).
- **Kontotyp „Personal"** (Einzelperson). Keine D-U-N-S-Nummer nötig (die brauchen nur Organisationen).
- **Identitätsverifizierung:** amtliches Ausweisdokument (Perso/Reisepass/Führerschein), Adresse,
  Telefonnummer, öffentliche Entwickler-E-Mail. **Wichtig:** Name auf dem Ausweis muss **exakt** dem
  Namen im Google-Payments-Profil und auf der Zahlungskarte entsprechen, sonst Ablehnung.
- **Dauer:** typisch 2–5 Werktage. Ohne abgeschlossene Verifizierung kein Production-Zugang.

## 2. Closed Testing (der Zeitfresser)

### Gilt die Pflicht überhaupt? (zuerst prüfen)

| Fall | Testpflicht |
|---|---|
| Personal-Account, erstellt **vor** dem 13.11.2023 | **nein** — komplett befreit |
| Personal-Account, erstellt **nach** dem 13.11.2023 | **ja**, pro App |
| Organization-Account (echte Firma + D-U-N-S) | **nein** |

**Zuerst nachsehen, ob schon ein altes Play-Konto existiert** (play.google.com/console mit den
eigenen Google-Konten einloggen). Ein Konto von vor dem 13.11.2023 spart die kompletten 14 Tage.

Der Organization-Weg ist für Solo-Entwickler meist **kein** sinnvoller Ausweg: Google verlangt eine
verifizierbare juristische Person (D-U-N-S plus Handelsregister-/Gewerbenachweis). Ein Einzelunternehmen
ohne eigene Rechtsform fällt in der Regel durch die Prüfung; die D-U-N-S-Beantragung dauert bis zu
30 Tage (Express ~229 USD, ~8 Werktage). Lohnt nur mit bereits bestehender GmbH/UG. Eine Firma zum
Schein zu erfinden ist ein ToS-Verstoß mit Sperr-Risiko.

### Regeln (wenn die Pflicht gilt)

- **Mindestens 12 Tester, durchgehend („continuously opted in") mindestens 14 Tage.**
  Wörtlich: „At least 12 testers must be opted in to your closed test when you apply for production
  access, and they must have been opted in continuously for the preceding 14 days."
- **12 eindeutige Google-Konten**, die über den **Opt-in-Link** beitreten und die App auf einem echten
  Gerät installieren. Alle 12 müssen sich im **selben** durchgehenden 14-Tage-Fenster überlappen.
- Wer vorher aussteigt, zählt nicht; bei Wiedereinstieg müssen die 14 Tage **am Stück** laufen. Fällt
  die Zahl unter 12, kann das Fenster neu beginnen.
- **2026 zusätzlich: Google prüft echte Nutzung**, nicht nur das Opt-in. Regelmäßige Sessions über die
  14 Tage sind das Ziel, nicht ein einmaliges Öffnen.
- **Interner Test zählt NICHT** — nur der Track „Closed testing".
- Danach **Production-Zugang beantragen** (Fragebogen) → Review „7 Tage oder weniger".

### Häufigste Falle

**Den Opt-in-Link teilen, nicht den Store-Link.** Installationen, die direkt aus dem Play Store kommen,
zählen nicht für den Test.

### Wie man auf 12 Tester kommt

1. **Eigener Kreis** (Freunde, Familie, Kollegen, Verein): zählt und ist bei WhisperLoom glaubwürdig,
   weil die App echten Alltagsnutzen hat. Als **alleinige** Quelle riskant — Google erkennt Häufungen
   verbundener Konten.
2. **Reziproke Tester-Communities**: r/AndroidAppTesters und r/TestersCommunity auf Reddit,
   Discord-/Telegram-Gruppen, testerscommunity.com (Credit-System: fremde Apps testen, dann die eigene
   posten). Kostenlos, dauert etwas Organisation.
3. **Kleine Entwickler-Pools**: mit zwei bis drei Entwicklern zusammentun, die dasselbe Gate brauchen,
   jeder bringt vier bis fünf Leute mit; alle testen alle Apps.
4. **Bezahldienste** (~15–20 USD für 15+ Tester): nur mit echten Konten seriös. Bots/Fake-Accounts sind
   ein Policy-Verstoß mit Sperr-Risiko — Finger weg.

Empfehlung für WhisperLoom: 6–8 Leute aus dem eigenen Umfeld, die die App wirklich zum Diktieren nutzen,
plus den Rest über eine Reddit-/Tester-Community. Die 14 Tage laufen im Hintergrund — Konto-Verifizierung,
Store-Assets und die Console-Formulare parallel erledigen. Die Verteilung über GitHub-Releases und das
eigene F-Droid-Repo läuft davon unberührt weiter.

## 3. Was bereits vorbereitet ist (dieser Repo-Stand)

- [x] **targetSdk 36** in `app/build.gradle.kts`.
- [x] **AAB-Build** in CI (`.github/workflows/build.yml`): signiertes `app-release.aab` als Artefakt
      `whisperloom-release-aab`, mit Native-Lib-Check (arm64-v8a, kein Modell im Bundle).
- [x] **Datenschutzerklärung** (DE/EN) unter `docs/privacy.html`, öffentlich erreichbar:
      **https://ctreitges.de/fdroid/privacy.html**
- [x] **Store-Texte** DE/EN in `fastlane/metadata/android/` (Titel, Kurz-/Volltext, Changelog).
- [x] **App-Icon** 512×512 (`fastlane/.../images/icon.png`).
- [x] `<uses-feature microphone required="false">` — App bleibt auf mikrofonlosen Geräten
      installierbar (Sprachnachricht-Transkription funktioniert dort weiter).
- [x] Sauber: kein `QUERY_ALL_PACKAGES`, kein Analytics/Crash-SDK, alle Komponenten `exported`
      explizit, `allowBackup=false`.

## 4. Technische To-dos vor dem Upload

- [ ] **Play App Signing einrichten** (Abschnitt 7).
- [x] **Prominent-Disclosure-Dialog** — erledigt (`ui/components/ProminentDisclosure.kt`): ein eigener
      Consent-Dialog erscheint vor dem Mikrofon-Request **und** vor dem Öffnen der
      Bedienungshilfe-Einstellungen (Setup + Einstellungen → Knopf & Tastatur), beschreibt Aufnahme und
      optionalen Anbieter-Versand, fortfahren nur nach aktiver Zustimmung. **Noch offen: Demo-Video**
      dieses Dialogs für das Accessibility-/FGS-Formular (am Gerät aufnehmen).
- [ ] **Screenshots** (mind. 2 Telefon-Screenshots) und **Feature-Graphic 1024×500** erzeugen —
      brauchen ein echtes Gerät/einen Emulator (siehe Abschnitt 8).
- [ ] **Gerätetest** der v3-Generation generell (bislang nur Build-verifiziert, kein Lauf auf einem
      Telefon) — inkl. Android-16-Verhalten (erzwungenes edge-to-edge).
- [x] **Accessibility-Service**: Weg A gewählt (behalten + deklarieren) — Disclosure-Flow eingebaut.
      Offen: Accessibility-Deklarationsformular + Demo-Video in der Play Console.

## 5. Der Accessibility-Service — zentrale Entscheidung

WhisperLoom nutzt `TextInserterAccessibilityService`, um diktierten Text **app-übergreifend** in das
fokussierte Feld einzufügen (der schwebende Knopf funktioniert über allen Apps). Google Play schränkt
`BIND_ACCESSIBILITY_SERVICE` für Nicht-Behinderten-Zwecke stark ein — das ist bei Diktier-Apps der
häufigste Ablehnungsgrund.

**2026-Regel:** Autonome Automatisierung über die Accessibility-API ist verboten; **erlaubt bleibt
deterministische, nutzer-ausgelöste Automatisierung nach festem Skript**. Das Einfügen von Diktat nach
Knopfdruck fällt in den erlaubten Bereich — braucht aber volle Deklaration.

Drei Wege:

| Weg | Funktion | Play-Risiko | Aufwand |
|-----|----------|-------------|---------|
| **A – behalten + deklarieren** | Overlay-Knopf fügt überall direkt ein | mittel-hoch (Reviewer-Ermessen) | Prominent-Disclosure + Accessibility-Formular + Demo-Video |
| **B – für Play entfernen** | Overlay fügt via Zwischenablage ein; Direkteinfügen nur über die WhisperLoom-Tastatur (IME) | niedrig | Product-Flavor (Play ohne a11y, F-Droid mit a11y) |
| **C – zuerst ohne, später nachrüsten** | wie B für den ersten Release | niedrig | wie B, a11y später als Update |

- **A** verlangt: das **Accessibility-Deklarationsformular** (App content) mit Zweck, Datentypen und
  **Demo-Video der In-App-Disclosure**; `isAccessibilityTool` **nicht** auf `true`.
- **B/C**: die App hat bereits eine vollwertige **Diktier-Tastatur (IME)** als regelkonformen
  Einfügeweg; der Overlay-Knopf bliebe mit Zwischenablage-Fallback nutzbar.

## 6. Play-Console-Formulare (App content)

- [ ] **Datenschutz-URL** eintragen: `https://ctreitges.de/fdroid/privacy.html`.
- [ ] **Data-Safety-Formular**: „Audio recordings" als **erhoben** deklarieren (weil optional an
      Online-Anbieter gesendet), als **optional** markieren. „Sharing": bei kostenlosen Anbieter-Tarifen,
      die Daten fürs Training nutzen könnten, sicherheitshalber als „geteilt" angeben. Muss
      **deckungsgleich** mit der Datenschutzerklärung sein (Abweichung = Ablehnung).
- [ ] **Foreground-Service-Deklaration**: Typ `microphone` (laufende Aufnahme) und `dataSync`
      (Modell-Download) je begründen, mit **Demo-Video** des nutzer-initiierten Einsatzes. Hinweis:
      Google empfiehlt für reine Downloads einen „user-initiated data transfer job" statt `dataSync` —
      für den Erst-Release ist die `dataSync`-Deklaration mit Video akzeptabel.
- [ ] **Content-Rating** (IARC-Fragebogen) ausfüllen — für eine Diktier-App unkritisch.
- [ ] **Permissions Declaration Form** im Release-Flow: Mikrofon-Nutzung mit klarem Zweck begründen.
- [ ] **Accessibility-Deklaration** (nur bei Weg A, Abschnitt 5).

## 7. Build & Upload (AAB, Play App Signing)

- **Play App Signing** (Pflicht bei AAB), zwei Schlüssel:
  - **App-Signing-Key** → beim ersten Release **Google generieren lassen** (Standard, sicherste
    Option; der Key verlässt Googles Infrastruktur nie).
  - **Upload-Key** → damit signierst du die `.aab` vor dem Upload. Der aktuelle Keystore
    `keystore/whisperloom-release.p12` (Alias `whisperloom`) **kann als Upload-Key dienen** — die CI
    signiert das AAB bereits damit.
  - Empfehlung: Upload- und App-Signing-Key getrennt halten (Upload-Key bei Verlust zurücksetzbar).
- **Hinweis zur Signatur:** Wenn Google den App-Signing-Key generiert, hat die **Play-Version eine
  andere Signatur als die F-Droid-/GitHub-Version**. Nutzer können nicht ohne Deinstallation zwischen
  den Kanälen wechseln. Willst du identische Signaturen, müsstest du deinen bestehenden Key als
  App-Signing-Key hochladen (weniger sicher, nicht zurücksetzbar).
- **AAB beziehen:** CI-Lauf → Artefakt `whisperloom-release-aab` → `app-release.aab` herunterladen und
  in der Play Console in den Testkanal hochladen. (Lokal auf dem aarch64-VPS ist **kein** vollständiges
  AAB baubar, weil das NDK für whisper.cpp fehlt — der Native-Build läuft nur in GitHub Actions.)
- Optional später: automatischer Upload via `fastlane supply` + Play-Service-Account-JSON.

## 8. Store-Eintrag (Grafiken & Texte)

- **Vorhanden:** Titel, Kurz-/Volltext (DE/EN), Changelog, Icon 512×512.
- **Fehlt (Gerät nötig):**
  - **≥2 Telefon-Screenshots** (empfohlen 4–8): Home mit großem Start, Overlay-Knopf im Einsatz,
    Sprachnachricht-Transkription, Einstellungen/Provider-Auswahl. Auf einem echten Gerät aufnehmen.
  - **Feature-Graphic 1024×500 px** (Marketing-Banner mit Logo + Slogan).
- **Markenhinweis:** „Whisper" und Anbieternamen (OpenAI etc.) nicht so prominent verwenden, dass eine
  offizielle Verbindung suggeriert wird — sonst Risiko wegen Impersonation/Marke.

## 9. Realistischer Zeitplan

| Schritt | Dauer |
|---|---|
| Konto + Identitätsverifizierung | 2–5 Werktage |
| Closed Testing (12 Tester × 14 Tage) | ~2–3 Wochen (inkl. Tester-Findung) |
| Production-Zugang (Google-Review) | ≤ 7 Tage |
| App-Review je Release | Stunden bis ~7 Tage (Erstprüfung länger) |
| **Gesamt bis Live** | **ca. 3–5 Wochen**, dominiert von der Testpflicht |

## 10. Quellen (offiziell, abgerufen 08.09.2026)

- Konto/Gebühr: https://support.google.com/googleplay/android-developer/answer/6112435
- Identität: https://support.google.com/googleplay/android-developer/answer/10841920 · DE-Dokumente: …/answer/15633622
- Closed Testing (12/14): https://support.google.com/googleplay/android-developer/answer/14151465
- targetSdk 36: https://developer.android.com/google/play/requirements/target-sdk
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756 · AAB-FAQ: https://developer.android.com/guide/app-bundle/faq
- Datenschutz/User Data: https://support.google.com/googleplay/android-developer/answer/10144311 · Data Safety: …/answer/10787469
- AccessibilityService: https://support.google.com/googleplay/android-developer/answer/10964491 · Prominent Disclosure: …/answer/11150561
- Foreground Services: https://support.google.com/googleplay/android-developer/answer/13392821 · https://developer.android.com/develop/background-work/services/fgs/service-types
- Content Rating: https://support.google.com/googleplay/android-developer/answer/9859655
- Sensible Permissions: https://support.google.com/googleplay/android-developer/answer/16558241
