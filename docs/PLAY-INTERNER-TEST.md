# WhisperLoom: erster interner Test in der Google Play Console

Stand: **9. Oktober 2026**, Version **3.9.0 (versionCode 19)**, Play-Paket **`com.whisperloom`**.
Das ist die Klick-Anleitung mit allen Texten zum Einfügen. Hintergrund, Zeitplan bis Production und
die 12-Tester-Regel stehen in [PLAYSTORE-RELEASE.md](PLAYSTORE-RELEASE.md).

Kennzeichnung: **[Forum]** = nur aus Erfahrungsberichten belegt, **[Annahme]** = eigene Schlussfolgerung.
Alles andere stützt sich auf die offiziellen Google-Seiten unter [Quellen](#quellen).

---

## 0. Auf einen Blick

| Erledigt (im Repo) | Musst du tun (Console, Gerät) |
|---|---|
| Play-AAB als `com.whisperloom`, signiert mit dem Upload-Key, von der CI geprüft | App in der Console anlegen, **bevor** du irgendetwas anderes machst (Paketname sichern) |
| GitHub-/F-Droid-APKs bleiben `com.chris.whisperloom` (keine Neuinstallation für bisherige Nutzer) | 3 kurze Demo-Videos aufnehmen und als „Nicht gelistet“ auf YouTube hochladen |
| Link „Datenschutzerklärung“ im Über-Fenster der App (Pflicht laut Google) | App-Inhalte ausfüllen (Texte unten) |
| Datenschutzerklärung nennt beide Paket-IDs | Testerliste anlegen, AAB hochladen, Versionshinweise einfügen, einführen |
| Versionshinweise DE/EN, Formular- und Video-Texte (dieses Dokument) | Opt-in-Link an die Tester schicken |

**Zeitbedarf:** etwa 1,5 h in der Console plus Videos. Dazu kommt die **Prüfung durch Google: einige
Stunden bis 7 Tage**. Grund: Die Bedienungshilfe braucht eine Freigabe, auch im internen Test (Abschnitt 6.9).

---

## 1. Vorher klären (einmalig)

1. **Konto bereit?** Identität bestätigt und, bei neuen Personal-Konten, das **Gerät verifiziert**
   (Play-Console-App auf einem echten Android-10+-Gerät). Vorher lässt sich nichts einreichen. **[Forum]**
   Laut Anleitungen von Drittanbietern bleibt „Einführen“ sonst ausgegraut.
2. **Kein `com.whisperloom` auf ein Gerät sideloaden, bevor die App in der Console angelegt ist.**
   Laut Googles Walkthrough zur Entwicklerverifizierung verlangt die Console sonst beim Anlegen einen
   Besitznachweis für den Schlüssel. Das Dokument ist in Zukunftsform verfasst, daher ist das eine
   Vorsichtsregel. Die GitHub-/F-Droid-Version (`com.chris.whisperloom`) ist davon nicht betroffen.
3. **Wenige interne Tester nehmen.** Wer am internen Test teilnimmt, kann nicht gleichzeitig am
   geschlossenen Test teilnehmen. Für die spätere 12-Tester/14-Tage-Pflicht müssen sich diese Leute erst
   wieder abmelden. Empfehlung: du plus ein bis zwei Personen.
4. **Upload-Key:** Das AAB ist mit `keystore/whisperloom-release.p12` signiert (RSA 2048, gültig bis 2053,
   Inhaber noch `CN=WhisperBar`, rein kosmetisch). Der erste Upload macht ihn zum Upload-Key. Google
   empfiehlt zwar getrennte Schlüssel für Upload und App-Signatur. Den App-Signaturschlüssel der
   Play-Version erzeugt aber ohnehin Google, und der Upload-Key lässt sich bei Verlust über den Support
   zurücksetzen. Die Entscheidung aus PLAYSTORE-RELEASE.md §7 bleibt deshalb bestehen.

---

## 2. Das AAB

- **Quelle:** GitHub Actions, Workflow „Build & Test“ auf Branch `feat/play-internal-test`, Artefakt
  **`whisperloom-play-aab`**. Die ZIP enthält `app-release.aab`.
- **Geprüft:** Die CI prüft am AAB bei jedem Lauf `package=com.whisperloom` per bundletool, Native-Lib
  nur arm64, kein Modell im Bundle sowie R8-Mapping und Native-Symbole in `BUNDLE-METADATA/`. Das
  16-KB-Alignment der Native-Libs prüft sie am Release-APK, das dieselben Libs enthält.
  Play liest Mapping und Symbole selbst aus dem Bundle, ein separater Upload ist nicht nötig.
- **Selbst nachprüfen** (optional, bundletool 1.18.3):
  ```bash
  java -jar bundletool-all-1.18.3.jar dump manifest --bundle app-release.aab --xpath /manifest/@package   # com.whisperloom
  keytool -printcert -jarfile app-release.aab | grep SHA256                                            # 94:E9:FC:22:…:DF:3C
  ```
- **Nie hochladen:** ein lokal auf dem VPS gebautes AAB. Es ist unsigniert, enthält kein `libwhisperloom.so`
  und hat fremde ABIs.
- **versionCode 19** ist nach dem ersten Upload verbraucht, auch wenn der Release nur ein Entwurf bleibt.
  Jeder weitere Upload braucht 20 oder höher.

---

## 3. Schritt für Schritt in der Console

1. **App erstellen** (*Alle Apps → App erstellen*)
   - App-Name: `WhisperLoom`
   - Standardsprache: **Deutsch (Deutschland) – de-DE**
   - App oder Spiel: **App** · Kostenlos oder kostenpflichtig: **Kostenlos** (lässt sich später nicht ändern)
   - Kontakt-E-Mail: deine öffentliche Entwickler-Adresse
   - Fragt der Dialog nach dem **Paketnamen** (laut Google-Walkthrough seit März 2026): `com.whisperloom`.
     Meldet die Console den Namen als vergeben, abbrechen und melden, denn dann muss die CI-Property geändert werden.
   - Erklärungen anhaken (Programmrichtlinien, US-Exportbestimmungen, Play App-Signatur) → *App erstellen*
2. **Tester** (*Testen und veröffentlichen → Testen → Interner Test → Tab „Tester“*)
   - *E-Mail-Liste erstellen* → Name z. B. `Intern` → Google-Konten der Tester (kommagetrennt) → speichern
   - Liste anhaken
   - **Feedback-URL oder E-Mail:** `https://github.com/CTreitges/whisperloom-android/issues` oder deine E-Mail
   - *Änderungen speichern*
3. **AAB als Entwurf hochladen** (*Interner Test → Neuen Release erstellen*)
   - Play App Signing: **von Google generierten Schlüssel** übernehmen (Standard)
   - `app-release.aab` hochladen
   - Release-Name und Versionshinweise aus [Abschnitt 4](#4-release-name-und-versionshinweise)
   - **Als Entwurf speichern**, noch nicht einführen. **[Forum]** Die Erklärung zu den Vordergrunddiensten
     erscheint unter App-Inhalte oft erst, wenn ein Bundle mit diesen Berechtigungen hochgeladen ist.
4. **App-Inhalte ausfüllen** (*Richtlinie und Programme → App-Inhalte*), alle Punkte aus [Abschnitt 6](#6-app-inhalte).
   Die Videos aus [Abschnitt 7](#7-demo-videos) brauchst du dafür schon.
5. **Release einführen:** zurück zum Entwurf → *Weiter* → **„Vorschau anzeigen und bestätigen“**.
   **Fehler** blockieren, **Warnungen** nicht. → *Einführung starten*. Bietet die Console stattdessen
   *Speichern* an: *Veröffentlichungsübersicht → Zur Überprüfung senden*.
6. **Warten auf die Prüfung** (einige Stunden bis 7 Tage). Der Status steht unter *Veröffentlichungsübersicht*.
7. **Opt-in-Link** (*Interner Test → Tester → „Link zum Teilen“*). Er erscheint erst, wenn der Status
   „Veröffentlicht“ ist. Dann den Link mit dem Text aus [Abschnitt 5](#5-text-für-die-tester) verschicken.
   Bis zu 48 Stunden sehen Tester noch einen vorläufigen Namen statt des Store-Eintrags.

Ein **Store-Eintrag** (Beschreibung, Screenshots, Feature-Grafik) ist für den internen Test **nicht nötig**.
Er wird erst für den geschlossenen Test gebraucht. Die Texte liegen schon unter `fastlane/metadata/android/`.

---

## 4. Release-Name und Versionshinweise

**Release-Name** (nur in der Console sichtbar): `3.9.0 (19)`

**Versionshinweise** (maximal 500 Zeichen je Sprache; die Tags müssen auf eigenen Zeilen stehen). Die Console
zeigt nur Tags für Sprachen, die der Store-Eintrag hat, also zunächst nur `de-DE`:

```
<de-DE>
WhisperLoom 3.9.0 – erster interner Test
• Diktieren in jede App: schwebender Knopf, Diktat-Tastatur oder Sprachnachricht teilen
• Erkennung offline auf dem Gerät (whisper.cpp) oder online mit eigenem API-Key
• Text glätten, verschönern oder zusammenfassen – auch offline mit Gemma 4
• Verlauf deiner Diktate, nur auf dem Gerät
Bitte testen: Einrichtung inkl. Mikrofon-Hinweis, Diktat in Messenger und Mail, Offline-Modell laden. Fehler bitte mit Gerät und Android-Version melden.
</de-DE>
```

Nur falls du unter *Store-Eintrag* eine englische Übersetzung anlegst, kommt zusätzlich hinzu:

```
<en-US>
WhisperLoom 3.9.0 – first internal test
• Dictate into any app: floating button, dictation keyboard or share a voice message
• Recognition offline on the device (whisper.cpp) or online with your own API key
• Smooth, polish or summarise text – offline too with Gemma 4
• History of your dictations, on the device only
Please test: setup incl. microphone notice, dictating in messenger and mail, downloading an offline model. Please report bugs with device and Android version.
</en-US>
```

(480 bzw. 476 Zeichen. Für spätere Updates sind die Release-Highlights in `fastlane/…/changelogs/<versionCode>.txt` die Vorlage.)

---

## 5. Text für die Tester

> Hallo! Du bist Tester für die Play-Version von WhisperLoom. So geht's:
> 1. Diesen Link öffnen, angemeldet mit dem Google-Konto, das ich eingetragen habe: **<Opt-in-Link>**
> 2. „Tester werden“ antippen, dann über den Link auf der Seite die App im Play Store installieren.
>
> Gut zu wissen:
> - Die App läuft auf 64-Bit-Android-Handys ab Android 8. Offline-Erkennung braucht ein neueres Gerät
>   (Armv8.2-CPU, grob ab 2018) mit mindestens 3 GB RAM, sonst geht nur online mit eigenem API-Key.
> - Hast du WhisperLoom schon von GitHub oder F-Droid: Die Play-Version ist eine **eigene App**. Keys,
>   Modelle und Verlauf werden nicht übernommen. Laufen beide parallel, erscheinen Tastatur, Bedienungshilfe,
>   Teilen-Ziel und Widget doppelt. Am einfachsten die alte Version vorher deinstallieren.
> - Fehler bitte mit Handy-Modell, Android-Version und kurzer Beschreibung an mich (oder als GitHub-Issue).

---

## 6. App-Inhalte

Pfad: *Richtlinie und Programme → App-Inhalte*. Google schreibt für die Formulare keine Sprache vor.
Prüfer arbeiten international, deshalb die **englischen** Texte eintragen. Die App selbst ist nur deutsch.

### 6.1 Datenschutzerklärung
`https://ctreitges.de/fdroid/privacy.html`. Wegen `RECORD_AUDIO` gilt sie schon für den internen Test als
Pflicht **[Forum]**. Den Link in der App selbst verlangt Google ebenfalls; er ist in diesem Build unter *Einstellungen → Über WhisperLoom* vorhanden.

### 6.2 Werbung
**Nein**, die App enthält keine Werbung.

### 6.3 Werbe-ID
**Nein.** Im Manifest steht kein `AD_ID`. Laut Forum blockiert die fehlende Angabe Releases für
targetSdk 33+, sie dauert eine Minute **[Forum]**.

### 6.4 App-Zugriff
**„Alle oder einige Funktionen sind eingeschränkt“.** Grund: Die Online-Funktionen brauchen den API-Key
eines Fremdanbieters. Anleitung (unter „Weitere Informationen“ einfügen; Google verlangt Englisch):

> No account or login. The app UI is German only. Core functionality works without any key or network after a one-time model download (needs an arm64 CPU with FP16/dot-product support, i.e. Armv8.2 or newer, and at least 3 GB RAM — e.g. any recent Pixel): On first start, tap "Los geht's" (let's go), then setup step "Wie soll WhisperLoom Sprache erkennen?" (how to recognise speech) → choose "Offline auf dem Gerät" (offline on device) → load model "Small" (190 MB) or "Tiny" (32 MB) → for text refinement choose "Überspringen" (skip). Then allow the microphone. Dictation keyboard: setup step "Diktat-Tastatur", enable and select it, then hold the mic key to speak. Floating button: home screen "Mikro-Knopf starten" (needs "display over other apps"; the accessibility service is optional — without it the text goes to the clipboard). Voice messages: share an audio file to WhisperLoom. Online recognition and text refinement use the user's own API key from a third-party provider (e.g. a free key from console.groq.com/keys), entered in setup step "Zugang zum Dienst". "Pro Widgets" (Settings → "Erweitert", off by default) send text to a server the user runs himself; they are not needed for any other feature.

**Empfehlung:** Leg einen eigenen, kostenlosen Groq-Key nur für die Prüfer an und trag ihn zusätzlich ein
(„A free test key for Groq: …“). Laut Google müssen Prüfer alle Funktionen erreichen können, und auf
einem älteren Prüfgerät gäbe es ohne Key nur die Online-Erkennung. Den Key nach der Prüfung nicht
löschen: Google prüft auch spätere Releases damit. Er gehört nie ins Repo, in Chats oder ins RLM.

### 6.5 Einstufung des Inhalts (IARC)
*Einstufung des Inhalts → Start*. E-Mail-Adresse eintragen.
- Kategorie: **„Dienstprogramm, Produktivität, Kommunikation oder Sonstiges“** (nicht Social).
- Gewalt, Angst, Sexualität, vulgäre Sprache, Drogen, derber Humor, Glücksspiel: **Nein**.
- Nutzer interagieren oder tauschen Inhalte aus: **Nein**. Standort teilen: **Nein**. Digitale Käufe: **Nein**.
  Uneingeschränkter Internetzugang (Browser): **Nein**.
- Falls nach generativer KI gefragt wird: **Ja, nur Überarbeitung des eigenen Diktats** (kein Chatbot, keine Bilder).
- Erwartet wird die niedrigste Stufe (USK 0 / PEGI 3). Das ist eine Prognose, den Wortlaut der Fragen am Bildschirm prüfen.

### 6.6 Zielgruppe und Inhalte
Nur **„18 und älter“**. Jüngere Gruppen ziehen die Familienrichtlinie nach sich.
„Spricht die App unbeabsichtigt Kinder an?“: **Nein**.

### 6.7 Behörden-App, Finanzfunktionen, Gesundheit, Nachrichten-App
**Nein** bzw. „Meine App bietet keine …“. Konto und Kontolöschung: Es gibt **kein Konto**.

### 6.8 Vordergrunddienste (Foreground-Service-Berechtigungen)
Zwei Typen. Je Typ gibt es Anwendungsfall, Beschreibung, Auswirkung und einen Video-Link.

**Typ `microphone`.** Anwendungsfall: **Background Audio Access** (Audio aufnehmen). Video: V2.

Description:
> WhisperLoom is a speech-to-text dictation app. Two user-started features use a microphone foreground service. (1) Floating dictation button: the user turns it on inside the app ("Mikro-Knopf starten" on the home screen, or at the end of setup). A small button is shown over other apps; the user taps it to start recording and taps again to stop, and the transcribed text is inserted into the field the user is typing in. The service is started by the user while WhisperLoom is in the foreground, because Android does not allow a microphone foreground service to be started from the background, but the button is used while the user is in other apps (on Android versions before 14, Android may re-create the button after it ended the app process; recording still only starts with a tap). The microphone is only open between the user's start and stop taps and is never opened without a tap. The ongoing notification has a "Beenden" (stop) action, and the button can be turned off in the app at any time. (2) Home-screen widget "Sprach-Command" (voice command, off by default): a tap on the widget starts one recording; the service runs only during that recording (it ends on "Senden"/send, on optional automatic pause detection or after 5 minutes at most) and then stops.

User impact if deferred or interrupted:
> Deferred: dictation is real time. If recording does not start the moment the user taps the button or widget, the beginning of what the user says is lost. Interrupted: recording stops mid-sentence, the spoken text is lost and the user has to dictate again; the floating button would also disappear from the screen while the user is typing in another app.

**Typ `dataSync`.** Anwendungsfall: **Network transfer: Upload or download**. Video: V3.

Description:
> The user can download offline speech-recognition models (32–574 MB) and an optional on-device text model (2.6–3.7 GB). Each download starts only from an explicit tap while the app is open — in the model list (Settings → "Offline-Modelle" → "Laden"), in the setup wizard or on the "Textmodell laden" card; on mobile data only after a confirmation dialog. The foreground service keeps the download running when the user leaves the screen; its notification shows the progress and a "Abbrechen" (cancel) action. It downloads exactly one model and stops as soon as the download finishes, fails or is cancelled. There is no periodic or automatic sync.

User impact if deferred or interrupted:
> Deferred: the user is waiting for a model they just requested; offline dictation cannot be used until it has finished. Interrupted: a download of up to several gigabytes stops; the partial file is kept, but the user has to tap "Erneut" (retry) to resume, which costs time and possibly mobile data.

### 6.9 Bedienungshilfen-API (AccessibilityService)
`isAccessibilityTool` ist **nicht** gesetzt, und das ist richtig. Hauptzweck ist allgemeines Diktat für
alle Nutzer, keine Hilfe für Menschen mit Behinderung. Laut Google gilt diese Erklärung für interne,
geschlossene und offene Tests; die App wird erst nach Freigabe veröffentlicht.

| Frage | Antwort |
|---|---|
| Why does your app need the Accessibility Services API? | **App functionality** |
| Do you collect and/or share personal or sensitive data using the accessibility capabilities? | **No** |
| Video of the prominent disclosure | V1 |

Freitext, falls die Console ein Feld anbietet:
> WhisperLoom's accessibility service ("WhisperLoom Text-Einfügen", text insertion) performs one fixed, user-triggered action: after the user has started and stopped a dictation with the floating button, it inserts the transcribed text at the cursor of the currently focused editable field. For this it reads only that field's text, cursor position and whether it is a password field. It reads no other screen content, does not store or transmit the field content and performs no other actions. Each dictation the user starts and stops leads to exactly one insertion into the field the user chose; the service never acts on its own and ignores the accessibility events it receives. If the service is off, the text is copied to the clipboard instead. A separate in-app disclosure with "Fortfahren"/"Abbrechen" (continue/cancel) is shown before the user is sent to the system accessibility settings.

Begründung für das „No“: Feldinhalte werden nur auf dem Gerät gelesen, um einzufügen, und nie gespeichert
oder übertragen. Das Einfügen in die fremde App löst der Nutzer selbst aus, und es landet in dem Feld, das
er gewählt hat. Diktate vom Knopf kommen in den lokalen Verlauf, sie stammen aber vom Mikrofon und nicht
von der Bedienungshilfe.

### 6.10 Datensicherheit
**Für den internen Test nicht nötig.** Laut Google brauchen Apps, die nur im internen Track aktiv sind,
das Formular nicht. Vor dem geschlossenen Test ist es Pflicht, die Antworten stehen im [Anhang](#anhang-datensicherheit-erst-vor-dem-geschlossenen-test).

### 6.11 Keine eigenen Formulare
`RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, die Tastatur (`BIND_INPUT_METHOD`), `POST_NOTIFICATIONS` und das
Widget brauchen kein eigenes Formular. Dasselbe gilt für die von AndroidX/WorkManager ergänzten
`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` und `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, die die Console
mit auflistet.

---

## 7. Demo-Videos

**Vorbereitung:** Die Videos nimmst du mit deiner installierten GitHub-/F-Droid-Version auf. Die
Oberfläche ist identisch, der Paketname ist im Video nicht zu sehen. Für V1 *App-Daten löschen*, nicht
neu installieren: Eine per Browser oder Dateimanager installierte APK lässt unter Android 13+ die
Bedienungshilfe nur als „eingeschränkte Einstellung“ zu. Vor V1 die Bedienungshilfe „WhisperLoom
Text-Einfügen“ in den Systemeinstellungen ausschalten. Ist sie an, zeigt der Assistent nur „Weiter“ und
keinen Hinweis-Dialog. Gesprochen wird ein **deutscher** Testsatz, weil die Erkennungssprache ab Werk
Deutsch ist; der Untertitel übersetzt ihn. Aufnahme mit der System-Bildschirmaufnahme
(Schnelleinstellungen), vorher *Entwickleroptionen → Berührungen anzeigen*. **Keine API-Keys filmen**
und nur Testtext diktieren. Englische Untertitel z. B. als YouTube-Untertitelspur.
**Upload:** YouTube **„Nicht gelistet“** (nicht „Privat“, sonst sieht der Prüfer nichts).

**V1 – Bedienungshilfe (ca. 2,5 min)**
1. WhisperLoom öffnen und den Assistenten langsam bis zum Schritt „Text automatisch einfügen“ durchgehen.
2. „Bedienungshilfe aktivieren“ → den **Hinweis-Dialog** 5 s stehen lassen. Untertitel: englische
   Übersetzung des Dialogtexts.
3. **„Abbrechen“** → die Bedienungshilfe bleibt aus. Untertitel: „User declines – nothing is enabled“.
4. Noch einmal „Bedienungshilfe aktivieren“ → **„Fortfahren“** → in den Systemeinstellungen „WhisperLoom
   Text-Einfügen“ einschalten → „Zulassen“.
5. Zurück in die App, Assistent beenden, dann **das Tutorial durchblättern oder „Überspringen“**. Danach
   startet der Mikro-Knopf.
6. Notizen- oder Nachrichten-App → Textfeld antippen → Knopf antippen → „Das ist ein Test“ sprechen →
   Knopf antippen → der Text erscheint im Feld. Untertitel: „The accessibility service inserts the
   dictated text into the focused field – only after the user's taps“.

**V2 – Vordergrunddienst `microphone` (ca. 2 min)**
1. Startbildschirm der App → „Mikro-Knopf starten“ → der Knopf erscheint. Benachrichtigungsleiste
   herunterziehen: Dauer-Benachrichtigung mit „Beenden“. Untertitel: „Started by the user; the microphone
   is NOT recording yet“.
2. In eine andere App wechseln → Knopf antippen → sprechen → antippen → der Text wird eingefügt.
3. Benachrichtigung → „Beenden“ → Knopf und Benachrichtigung verschwinden.
4. Widget: *Einstellungen → Erweitert → Pro Widgets* an, Profil mit deinem Server (z. B. hermes-bridge)
   → Widget „Sprach-Command“ auf den Startbildschirm → antippen → Benachrichtigung „Nimmt auf …“ mit
   „Senden“ → sprechen → „Senden“ → die Aufnahme-Benachrichtigung verschwindet. Untertitel: „Service
   runs only while recording“.

**V3 – Vordergrunddienst `dataSync` (ca. 1 min)**
1. *Einstellungen → Offline-Modelle* → kleinstes Modell („Tiny“) → „Laden (…)“. Bei mobilen Daten
   zuerst die Rückfrage zeigen.
2. Benachrichtigung mit Fortschritt und „Abbrechen“ zeigen.
3. Home-Taste, andere App: Der Download läuft weiter. Untertitel: „User-initiated download continues in
   the background“.
4. Fertig-Meldung, die Dienst-Benachrichtigung ist weg.

---

## 8. Danach und später

- **Updates** im internen Track werden normalerweise nicht geprüft und sind in Minuten bei den Testern.
  Ausnahme: Wurde eine Einreichung abgelehnt, muss auch die nächste geprüft werden.
- **Vor dem geschlossenen Test:** Store-Eintrag mit mindestens 2 Screenshots und Feature-Grafik
  1024×500, Datensicherheit (Anhang), dann 12 Tester × 14 Tage (siehe PLAYSTORE-RELEASE.md §2).
- **Entwicklerverifizierung:** `com.whisperloom` registriert die Console beim Anlegen automatisch.
  `com.chris.whisperloom` (GitHub/F-Droid) musst du bis zur weltweiten Durchsetzung 2027 selbst
  registrieren: *Identitätsbestätigung für Android-Entwickler → Paketnamen*, mit Nachweis-APK.
- **Offene Code-Punkte aus der Prüfung** (für den internen Test nicht blockierend, nicht geändert):
  - Die Bedienungshilfe abonniert `typeViewFocused|typeWindowContentChanged`, ignoriert die Ereignisse aber.
    Weniger Ereignistypen würden die Prüfung erleichtern; vorher am Gerät testen.
  - `FloatingMicService` gibt `START_STICKY` zurück. Unter Android < 14 kann der Knopf nach einem
    Prozess-Ende von selbst zurückkommen, ohne aufzunehmen.
  - `ModelDownloadService.onTimeout` stoppt bei laufendem Download erst nach dem Lese-Timeout
    (bis zu 30 s). Android 15 verlangt „within a few seconds“.
  - Der Store-Text sagt „Aufnahmen speichert WhisperLoom nicht“. Ein Pro-Widget-Auftrag liegt aber als
    `voice_task.pcm` auf dem Gerät, bis er gesendet ist.

---

## Anhang: Datensicherheit (erst vor dem geschlossenen Test)

| Frage | Antwort | Begründung |
|---|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Ja** | Online-Erkennung und Textverbesserung übertragen Audio und Text an den gewählten Anbieter |
| Alle Daten bei der Übertragung verschlüsselt? | **Entscheidung offen** | Cloud-Anbieter: HTTPS erzwungen. Eigener Server, Ollama und Pro-Widget-Server erlauben auch `http://`. „Nein“ ist wörtlich korrekt. „Ja“ wäre über Googles Passage zu Übertragungen an vom Nutzer gewählte Dienste vertretbar, dann aber Datenschutzerklärung angleichen |
| Kontoerstellung | **Kein Konto** | – |
| Löschung beantragbar? | **Nein** | Kein eigener Server, keine gespeicherten Daten beim Entwickler |

| Datentyp | Erhoben | Geteilt | Vorübergehend | Erforderlich | Zweck |
|---|---|---|---|---|---|
| Sprach- oder Tonaufnahmen | Ja | Ja (konservativ) | Nein | **Erforderlich** (auf Geräten ohne Offline-Erkennung gibt es nur online) | App-Funktionen |
| Sonstige Audiodateien (geteilte Sprachnachrichten) | Ja | Ja | Nein | Optional | App-Funktionen |
| Sonstige von Nutzern erstellte Inhalte (Text an das Sprachmodell, Vokabular, Pro-Widget-Auftrag) | Ja | Ja | Nein | Optional | App-Funktionen |

Bewusst **nicht** angegeben, weil nur lokal verarbeitet: Verlauf, Feldinhalte (Bedienungshilfe, Tastatur),
Widget-Bilder. API-Key und Widget-Token sind Zugangsschlüssel, die nur an ihren Aussteller gehen. Es gibt
keine SDKs für Analyse, Absturzberichte oder Werbung.

---

## Quellen

Offiziell (support.google.com/googleplay/android-developer/answer/…, abgerufen 09.10.2026):
[9845334](https://support.google.com/googleplay/android-developer/answer/9845334) Tests einrichten ·
[9859348](https://support.google.com/googleplay/android-developer/answer/9859348) Release vorbereiten ·
[9859654](https://support.google.com/googleplay/android-developer/answer/9859654) Prüfung ·
[9842756](https://support.google.com/googleplay/android-developer/answer/9842756) Play App-Signatur ·
[9859152](https://support.google.com/googleplay/android-developer/answer/9859152) App erstellen ·
[14151465](https://support.google.com/googleplay/android-developer/answer/14151465) Testanforderungen ·
[9214102](https://support.google.com/googleplay/android-developer/answer/9214102) Berechtigungserklärungen ·
[10964491](https://support.google.com/googleplay/android-developer/answer/10964491) AccessibilityService ·
[13392821](https://support.google.com/googleplay/android-developer/answer/13392821) Vordergrunddienste ·
[10787469](https://support.google.com/googleplay/android-developer/answer/10787469) Datensicherheit ·
[9859455](https://support.google.com/googleplay/android-developer/answer/9859455) App-Inhalte ·
[15748846](https://support.google.com/googleplay/android-developer/answer/15748846) Zugangsdaten für die Prüfung ·
[9867159](https://support.google.com/googleplay/android-developer/answer/9867159) Zielgruppe ·
[9848633](https://support.google.com/googleplay/android-developer/answer/9848633) Deobfuskierung ·
[14316361](https://support.google.com/googleplay/android-developer/answer/14316361) Geräteverifizierung ·
[16761053](https://support.google.com/googleplay/android-developer/answer/16761053) Paketnamen registrieren.
developer.android.com: [app-signing](https://developer.android.com/studio/publish/app-signing) ·
[developer-verification](https://developer.android.com/developer-verification/guides/google-play-console) ·
[include-native-symbols](https://developer.android.com/build/include-native-symbols) ·
[fgs/timeout](https://developer.android.com/develop/background-work/services/fgs/timeout).
Erfahrungsberichte **[Forum]**: forum.bubble.io (Datenschutz-URL wegen RECORD_AUDIO im internen Track, 10/2025) ·
discuss.bitrise.io und github.com/Detour-app/Detour#351 (FGS-Erklärung fehlt → Upload in den internen Track scheitert) ·
forum.volt.build (Werbe-ID-Erklärung).
