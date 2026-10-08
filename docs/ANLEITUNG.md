# WhisperLoom — Anleitung

Version 3.9.0 · Stand 2026-10-08 · Für Android 8.0 (API 26) und neuer

Diese Anleitung richtet sich an Anwender, die WhisperLoom installieren, einrichten und im Alltag nutzen wollen. Entwickler finden Bau- und Architektur-Hinweise in der [README](../README.md); was sich von Version zu Version geändert hat, steht im [CHANGELOG](../CHANGELOG.md).

Alle Bezeichnungen in dieser Anleitung („Mikro-Knopf starten", „Zugang prüfen", „Füllwörter ausblenden" …) sind die Bezeichnungen, die in der App selbst stehen. Preise und Modellnamen der Online-Anbieter: **Stand 09/2026, Textmodelle 10/2026, ohne Gewähr** — die Anbieter ändern beides laufend.

---

## Inhalt

1. [Was WhisperLoom kann](#1-was-whisperloom-kann)
2. [Installation](#2-installation)
3. [Erste Einrichtung — der Assistent Schritt für Schritt](#3-erste-einrichtung--der-assistent-schritt-für-schritt)
4. [Diktieren mit dem schwebenden Knopf](#4-diktieren-mit-dem-schwebenden-knopf)
5. [Diktieren mit der Tastatur](#5-diktieren-mit-der-tastatur)
6. [Sprachnachrichten abtippen](#6-sprachnachrichten-abtippen)
7. [Anbieter und API-Keys](#7-anbieter-und-api-keys)
8. [Textverbesserung](#8-textverbesserung) — [Diktat](#81-diktat) · [Sprachnachrichten](#82-sprachnachrichten) · [KI-Zugang](#83-ki-zugang) · [Modell je Stufe](#84-modell-je-stufe) · [Bei Offline-Erkennung](#85-bei-offline-erkennung) · [Feste Regeln](#86-feste-regeln) · [Wörterbuch und Sprache](#87-wörterbuch-und-sprache)
9. [Offline-Modus](#9-offline-modus) — mit [Lokales Textmodell (Gemma 4)](#97-lokales-textmodell-gemma-4)
10. [Eigener Server](#10-eigener-server)
11. [Verlauf](#11-verlauf)
12. [Datenschutz](#12-datenschutz)
13. [Wenn etwas nicht klappt](#13-wenn-etwas-nicht-klappt)
14. [Pro Widgets (Sprach-Command-Widgets)](#14-pro-widgets-sprach-command-widgets)
15. [Häufige Fragen](#15-häufige-fragen)

---

## 1. Was WhisperLoom kann

WhisperLoom ist eine Diktier-App für Android. Du sprichst, WhisperLoom erkennt den Text und tippt ihn in das Feld, in dem gerade der Cursor steht — in WhatsApp, Gmail, im Browser, in Notizen, überall.

Dafür gibt es drei Wege:

| Weg | Wann sinnvoll | Kurz gesagt |
|---|---|---|
| **Schwebender Knopf** | Der Normalfall. Deine gewohnte Tastatur (z. B. Gboard) bleibt aktiv. | Ein runder Knopf liegt über allen Apps. Antippen = Aufnahme, nochmal antippen = fertig, der Text landet im aktiven Feld. |
| **Diktat-Tastatur** | Wenn eine App kein Overlay erlaubt, oder wenn du lieber ohne Knopf arbeitest. | WhisperLoom als eigene Android-Tastatur: Mikrofon gedrückt halten, sprechen, loslassen. |
| **Sprachnachrichten abtippen** | Für Nachrichten, die andere dir schicken. | In WhatsApp, Telegram, Signal & Co. eine Sprachnachricht teilen → WhisperLoom zeigt den Text in Absätzen, zum Lesen, Kopieren oder Weitergeben. |

Die Spracherkennung selbst läuft wahlweise

- **online** über einen Dienst deiner Wahl (OpenAI, Groq, Mistral, ElevenLabs, Together AI, DeepInfra, OpenRouter oder ein eigener Server). Du brauchst dafür einmalig einen API-Key des Anbieters; bei Groq ist das kostenlos. Beste Qualität, schnell.
- **offline** direkt auf dem Gerät mit einem einmalig heruntergeladenen Whisper-Modell (32–574 MB). Die Aufnahme verlässt das Telefon nicht, die Erkennung dauert dafür einige Sekunden.

Dazu kommt eine optionale **Textverbesserung**: Ein Sprachmodell (KI) glättet Zeichensetzung und Groß-/Kleinschreibung, formuliert verständlicher oder fasst zusammen — in vier Stufen von „Aus" bis „Zusammenfassen". Das Sprachmodell kann bei einem Online-Anbieter laufen, bei **Ollama Cloud** oder auf deinem eigenen **Ollama** zu Hause (lokal / Homeserver). Erkennst du offline, verbessert ein **lokales Textmodell** (Gemma 4) den Text direkt auf dem Gerät — ohne Netz und ohne Key ([9.7](#97-lokales-textmodell-gemma-4)). Ein **Vokabular** mit Namen und Fachbegriffen — als Liste in der App oder als verknüpfte Textdatei — hilft der Erkennung bei Eigennamen. Unabhängig davon räumt WhisperLoom lokal auf: Füllwörter („ähm", „äh") entfernen, Satzanfänge groß schreiben, ein Leerzeichen anhängen.

Den Text deiner Diktate aus Knopf und Tastatur hebt der **Verlauf** auf dem Gerät auf, zum Wiederfinden, Kopieren und nachträglichen Verbessern ([11](#11-verlauf)); ab Werk an, abschaltbar.

Was WhisperLoom **nicht** tut: Es speichert keine Aufnahmen (einzige Ausnahme: ein noch nicht gesendeter Auftrag eines Pro Widgets, siehe [12](#12-datenschutz)), es liest nicht mit, was du sonst tippst, und dein API-Key bleibt auf dem Gerät.

---

## 2. Installation

WhisperLoom wird nicht über den Play Store verteilt, sondern als APK-Datei über die GitHub-Releases-Seite des Projekts.

### 2.1 Voraussetzungen

- Android 8.0 oder neuer.
- Für den **Offline-Modus** zusätzlich: ein 64-Bit-ARM-Gerät (arm64-v8a), dessen Prozessor FP16-Vektorrechnung und DotProd beherrscht (praktisch alle Geräte ab etwa 2018, siehe [Kapitel 9](#9-offline-modus)). Auf anderen Geräten steht nur der Online-Modus zur Verfügung — WhisperLoom zeigt das im Assistenten als „Auf diesem Gerät nicht verfügbar" an.
- Für das **lokale Textmodell** (Textverbesserung offline) zusätzlich mindestens 6 GB Arbeitsspeicher, für Gemma 4 E4B 8 GB, und 3,4 bzw. 4,8 GB freier Speicherplatz ([9.7](#97-lokales-textmodell-gemma-4)).

### 2.2 Installation über F-Droid (eigenes Repository, empfohlen für Updates)

WhisperLoom liegt in einem eigenen F-Droid-Repository. Damit bekommst du Updates wie aus einem App-Store — signiert mit demselben Entwickler-Schlüssel wie die GitHub-Releases.

1. F-Droid-Client installieren (https://f-droid.org).
2. Auf dem Handy diese Seite öffnen und auf „Repo mit einem Tipp hinzufügen" tippen: https://ctreitges.de/fdroid/ — oder im F-Droid-Client unter *Einstellungen → Paketquellen → +* eintragen:
   - Adresse: `https://ctreitges.de/fdroid/repo`
   - Fingerprint: `f07ab6293f13c3d637aaa24eb048f2df9bc55013fa3d92e065d72e4b00fe89d0`
3. Nach dem Aktualisieren der Paketquellen erscheint „WhisperLoom"; installieren wie jede andere App. Updates meldet der F-Droid-Client automatisch (das Repo gleicht sich stündlich mit den GitHub-Releases ab).

### 2.3 APK installieren

1. Auf der [Releases-Seite](https://github.com/CTreitges/whisperloom-android/releases) des Projekts das neueste Release (aktuell **3.9.0**) öffnen und die APK-Datei auf das Telefon laden (direkt im Browser des Telefons ist am einfachsten).
2. Die heruntergeladene Datei antippen. Android fragt beim ersten Mal, ob der Browser (bzw. der Dateimanager) **unbekannte Apps installieren** darf — das Wording heißt je nach Hersteller „Unbekannte Apps installieren", „Aus dieser Quelle zulassen" oder „Unbekannte Quellen". Erlauben, zurück, erneut „Installieren" antippen.
3. Google Play Protect prüft die App ggf. beim Installieren. Das ist normal für Apps außerhalb des Play Stores.
4. Nach der Installation **WhisperLoom** öffnen — der Einrichtungs-Assistent startet ([Kapitel 3](#3-erste-einrichtung--der-assistent-schritt-für-schritt)).

### 2.4 Updates

- **Ab 3.2.0 — neuer Name WhisperLoom, neue Paket-ID `com.chris.whisperloom`:** WhisperLoom wird als neue App installiert; die vorherige Version deinstallieren und API-Key/Einstellungen einmal neu eingeben. Signaturschlüssel unverändert.
- Eine neue Version wird einfach **über die alte installiert** (APK herunterladen, antippen, „Aktualisieren"). Alle Einstellungen, der API-Key und heruntergeladene Offline-Modelle bleiben erhalten.
- Was neu ist, zeigt das **?** neben der Versionsnummer.
- Beim Sprung von Version 2.x auf 3.0.0 werden die alten Einstellungen automatisch übernommen (Details in den [Häufigen Fragen](#15-häufige-fragen)).
- Voraussetzung dafür ist derselbe **Signaturschlüssel**: Alle offiziellen Releases werden mit demselben Schlüssel signiert. Meldet Android „App nicht installiert" oder „Paket steht in Konflikt", stammt die bereits installierte Version aus einer anders signierten Quelle (z. B. ein selbst gebautes Debug-APK). Dann bleibt nur: alte Version deinstallieren (Einstellungen gehen dabei verloren) und das Release neu installieren.

---

## 3. Erste Einrichtung — der Assistent Schritt für Schritt

Beim ersten Start öffnet WhisperLoom die **Einrichtung** — einen Assistenten mit bis zu sieben Schritten. Die Kopfzeile zeigt „Schritt x von y" (7 Schritte ab Android 13, sonst 6, weil es dort keinen Benachrichtigungs-Schritt gibt). Über das Listen-Symbol oben rechts („Alle Schritte anzeigen") kannst du jederzeit zu einem anderen Schritt springen. Jeder Schritt zeigt oben ein Bild dazu und trägt einen Status-Chip: **Erledigt**, **Fehlt noch**, **Optional** oder **Übersprungen**.

Der Assistent merkt sich, was erledigt ist. Wenn du ihn verlässt und WhisperLoom später wieder öffnest, landest du auf dem ersten noch offenen Pflichtschritt. Kehrst du aus einem Systemdialog zurück, prüft WhisperLoom den Status automatisch neu — du musst nichts bestätigen.

Vor dem ersten Schritt begrüßt dich eine Willkommensseite („Diktiere in jede App.") mit dem Button **Los geht's**.

### Schritt 1 — Erkennungsweg: „Wie soll WhisperLoom Sprache erkennen?"

Zwei Karten, du wählst eine:

- **Online-Dienst** (Empfohlen) — „Beste Qualität, schnell. Audio wird an den gewählten Anbieter gesendet. Braucht einen API-Key (bei Groq kostenlos)."
- **Offline auf dem Gerät** — „Die Aufnahme bleibt auf dem Gerät. Modell einmalig laden (32–574 MB), Erkennung dauert einige Sekunden." Ist dein Gerät nicht geeignet, ist die Karte ausgegraut und trägt den Hinweis „Auf diesem Gerät nicht verfügbar".

Du kannst später jederzeit wechseln (Einstellungen → Spracherkennung). Dann **Weiter**.

### Schritt 2 — Zugang oder Modell

Der Inhalt hängt von Schritt 1 ab.

**2a · Zugang zum Dienst** (bei Online-Dienst):

1. **Anbieter** wählen: OpenAI · Groq (kostenlos) · Mistral · ElevenLabs (Scribe) · Together AI · DeepInfra · OpenRouter · Eigener Server. Unter dem Feld steht die Base-URL des Anbieters. Nur bei „Eigener Server" gibst du die **Base-URL** selbst ein ([Kapitel 10](#10-eigener-server)).
2. **API-Key** einfügen. Das Feld ist maskiert; das Auge-Symbol zeigt den Key an, das Einfügen-Symbol holt ihn aus der Zwischenablage. „Wird nur auf diesem Gerät gespeichert." Beim Eigenen Server heißt das Feld „API-Key (optional)".
3. **Modell** wählen. Die Liste zeigt die Modelle des Anbieters mit dem empfohlenen Modell an erster Stelle; der letzte Eintrag **Eigenes Modell …** öffnet ein Feld für eine beliebige Modell-ID („Genau so, wie der Anbieter die ID nennt."). Mit der Pro-Funktion **Modelle vom Server** öffnet das Feld stattdessen eine Auswahl mit Suche und der aktuellen Liste des Anbieters ([7.6](#76-modelle-vom-server-pro-funktion)).
4. Du hast noch keinen Key? **Wo bekomme ich einen Key?** öffnet die Kurzanleitung je Anbieter mit Link zur Key-Seite (ausführlich in [Kapitel 7](#7-anbieter-und-api-keys)).
5. Optional **Zugang prüfen**: WhisperLoom schickt eine Sekunde Stille an den Anbieter. Erfolg zeigt „Verbunden · x s", ein Fehler nennt den Grund („Key ungültig (401)", „Guthaben aufgebraucht (402) — beim Anbieter aufladen", „Limit erreicht (429) — später erneut", „Keine Verbindung", „Server antwortet nicht (Zeitüberschreitung)" …).

Darunter steht der Datenschutz-Hinweis „Audio wird zur Erkennung an {Anbieter} gesendet." Sobald Key (bzw. beim Eigenen Server eine gültige URL) vorliegt, wird der Chip **Erledigt** und **Weiter** aktiv.

**2b · Offline-Modell laden** (bei Offline):

„Erst das Modell für die Erkennung, dann die Textverbesserung ohne Netz. Empfehlung: Small (190 MB) und Gemma 4 E2B." Die Liste zeigt Tiny, Base, Small und Large v3 Turbo mit Größe, Speicherbedarf und Hinweis. **Laden (190 MB)** startet den Download; er läuft im Hintergrund weiter und zeigt den Fortschritt in einer Benachrichtigung. Über mobile Daten fragt WhisperLoom vorher nach („Über mobile Daten laden?"). Alles Weitere zu Modellen in [Kapitel 9](#9-offline-modus).

Darunter unter **Textverbesserung ohne Netz** zwei Karten:

- **Lokales Textmodell** — „Gemma 4 E2B verbessert den Text auf dem Gerät und schaltet „Glätten" ein. Einmal laden: 2,6 GB · +800 MB beim ersten Start." Ein Tipp startet den Download des Textmodells ([9.7](#97-lokales-textmodell-gemma-4)) und stellt die KI-Stufe fürs Diktat auf „Glätten" — ab Werk steht sie auf „Aus", und ohne Stufe bliebe das Textmodell ungenutzt. Ist schon eine Stufe gewählt (fürs Diktat oder für geteilte Sprachnachrichten), bleibt sie, und der Zusatz „und schaltet „Glätten" ein" fehlt. Passt auch Gemma 4 E4B ins Gerät (ab 8 GB RAM), lädt der Tipp noch nichts: Er stellt die Regel und „Glätten" ein, und darunter stehen beide Textmodelle wie in den Offline-Modellen, Gemma 4 E2B (empfohlen, vorgewählt) und Gemma 4 E4B, über ihnen die Warnkarte „Offline ohne Textmodell" mit **Überspringen**. Ein Tipp auf E2B oder E4B lädt dieses Modell und wählt es; ab dann ist „Lokales Textmodell" gewählt und **Weiter** frei. Passt nur E2B, startet der Tipp gleich dessen Download; die Zeile von E4B ist dann ausgegraut („Für dieses Gerät zu groß").
- **Überspringen** — „Nur die Erkennung — offline kommt der Text ohne KI. Ein Textmodell kannst du später unter Offline-Modelle laden."

**Weiter** wird aktiv, sobald das Whisper-Modell geladen ist und du eine der beiden Karten gewählt hast. Das Textmodell muss dafür nicht fertig sein: „Lädt im Hintergrund weiter — du kannst schon weitermachen." Ist offline schon eine KI-Stufe eingeschaltet und lädt das Textmodell nicht gerade, steht an Stelle der beiden Karten die Karte **Offline ohne Textmodell** mit **Überspringen**, darunter die Textmodelle zum Laden ([9.7](#97-lokales-textmodell-gemma-4)). Scheitert der Download (z. B. „Nicht genug Speicherplatz" oder kein Netz), zeigt die Zeile des Modells den Grund („Fehlgeschlagen: …"); **Erneut** versucht es noch einmal, **Weiter** ist bis zu einer Wahl wieder gesperrt. Auf Geräten mit weniger als 6 GB Arbeitsspeicher ist „Lokales Textmodell" ausgegraut („Für dieses Gerät zu groß") und „Überspringen" schon gewählt: Dort passt kein Textmodell, die Regel wirkt wie „Überspringen" ([9.7](#97-lokales-textmodell-gemma-4)).

### Schritt 3 — Mikrofon erlauben (Pflicht)

„Ohne Mikrofon kein Diktat. Android fragt dich gleich — bitte „Bei Nutzung der App" wählen." Button **Mikrofon erlauben** öffnet den Android-Dialog.

Hast du das Mikrofon schon zweimal abgelehnt, zeigt Android den Dialog nicht mehr. WhisperLoom erkennt das („Du hast das Mikrofon abgelehnt. Bitte in den App-Einstellungen erlauben.") und bietet **App-Einstellungen öffnen** an: dort unter Berechtigungen → Mikrofon → „Nur bei Nutzung der App zulassen".

### Schritt 4 — Über anderen Apps anzeigen (Pflicht, mit Alternative)

Der schwebende Knopf liegt über anderen Apps — dafür braucht Android deine Erlaubnis. **Einstellung öffnen** führt in die Systemeinstellung; dort den Schalter **„Über anderen Apps anzeigen"** für WhisperLoom einschalten und mit der Zurück-Taste zurückkehren. WhisperLoom prüft automatisch. Der aufklappbare Text **Was passiert dabei?** beschreibt die drei Schritte.

**Nur Tastatur nutzen:** Wenn du keinen schwebenden Knopf willst (oder ihn nicht erlauben kannst), überspringst du den Schritt mit diesem Button. Dann funktioniert nur die Tastatur-Variante — Schritt 7 wird deshalb zum Pflichtschritt. Der Chip zeigt **Übersprungen**; du kannst die Erlaubnis später jederzeit unter Einstellungen → Knopf & Tastatur → Berechtigungen nachholen.

### Schritt 5 — Text automatisch einfügen (empfohlen)

Die **Bedienungshilfe** „WhisperLoom Text-Einfügen" fügt den diktierten Text ins gerade fokussierte Feld ein, damit du beim Diktieren nicht die Tastatur wechseln musst. Dafür liest sie nur dieses Feld (Text und Cursor) und prüft, ob es ein Passwortfeld ist; andere Bildschirminhalte liest sie nicht, den Feldinhalt speichert sie nicht. Die Diktate vom Knopf kommen in den Verlauf, wenn er an ist ([11](#11-verlauf)).

Ohne diesen Schritt landet der Text in der **Zwischenablage** — du fügst ihn dann selbst ein (langes Drücken im Textfeld → Einfügen). Das funktioniert, ist aber ein Handgriff mehr.

**Bedienungshilfe aktivieren** öffnet die Bedienungshilfe-Einstellungen von Android: Installierte Apps (bzw. „Heruntergeladene Apps") → WhisperLoom → Ein → Bestätigen.

**„Eingeschränkte Einstellung":** Bei Apps, die nicht aus dem Play Store stammen, blockiert Android ab Version 13 das Einschalten einer Bedienungshilfe zunächst mit diesem Hinweis. Lösung: App-Info von WhisperLoom öffnen (in den Android-Einstellungen → Apps → WhisperLoom, oder App-Symbol lange drücken → ⓘ) → Menü **⋮** oben rechts → **Eingeschränkte Einstellungen zulassen** → danach die Bedienungshilfe erneut aktivieren. Der aufklappbare Text „Was passiert dabei?" im Assistenten nennt genau diese Reihenfolge.

Dieser Schritt lässt sich mit **Überspringen** auslassen; auf dem Startbildschirm erinnert dann ein Banner daran.

### Schritt 6 — Beenden per Benachrichtigung (ab Android 13, empfohlen)

„Solange der Knopf läuft, zeigt Android eine stille Benachrichtigung mit „Beenden". Ohne Erlaubnis ist sie unsichtbar — beenden kannst du dann in der App." Button **Benachrichtigungen erlauben**; bei dauerhafter Ablehnung wieder **App-Einstellungen öffnen**. Auch dieser Schritt ist überspringbar.

### Schritt 7 — Diktat-Tastatur (optional; Pflicht, wenn du in Schritt 4 „Nur Tastatur nutzen" gewählt hast)

Zwei Zeilen, in dieser Reihenfolge:

1. **1 · Tastatur aktivieren** → **Aktivieren** öffnet die Android-Tastatureinstellungen. Dort „WhisperLoom Diktat" einschalten (Android warnt bei jeder Drittanbieter-Tastatur, dass sie Eingaben sehen könnte — WhisperLoom liest nichts mit).
2. **2 · Tastatur auswählen** → **Auswählen** öffnet die Tastatur-Auswahl von Android. Dieser Button ist erst aktiv, wenn Schritt 1 erledigt ist.

Darunter ein Probierfeld („Zum Diktieren hierher tippen …"), um die Tastatur gleich zu testen. Als erledigt gilt der Schritt, sobald die Tastatur aktiviert ist.

### Fertig — „Bereit zum Diktieren"

Die Abschlussseite fasst unter **Deine Einrichtung** alles zusammen (Erkennung, Mikrofon, Über anderen Apps, Bedienungshilfe, Benachrichtigungen, Tastatur). Übersprungene Zeilen sind antippbar und führen zum jeweiligen Schritt. Darunter die Kurzanleitung:

1. Knopf antippen = Aufnahme
2. Nochmal antippen = fertig & einfügen
3. Auf ✕ ziehen = verwerfen

**Knopf starten & los** zeigt beim ersten Mal ein kurzes, bebildertes **Tutorial** (vier Seiten: Diktieren mit dem Knopf · Diktat-Tastatur · Sprachnachrichten abtippen · Der Text ist da); **Überspringen** ist jederzeit möglich. Erst danach startet der schwebende Knopf (damit er nicht über dem Tutorial schwebt), und du landest auf dem Startbildschirm; bei „Nur Tastatur" heißt der Button **Zum Start**.

Das Tutorial ist erneut aufrufbar unter **Anleitung & Hilfe → Tutorial erneut ansehen**; **Mehr** beim Hinweis „Sprachnachrichten abtippen" auf dem Startbildschirm springt direkt zur Seite über Sprachnachrichten.

### Der Startbildschirm

Danach zeigt WhisperLoom beim Öffnen den Startbildschirm:

- Ganz oben die Karte **Schwebender Knopf** mit dem großen Button **Mikro-Knopf starten** bzw. **Mikro-Knopf beenden**. Fehlt eine Pflicht-Berechtigung, ist der Button gesperrt und ein Chip („Mikrofon fehlt — beheben" / „„Über anderen Apps anzeigen" fehlt — beheben") führt in den passenden Schritt.
- Ein Banner **Noch nicht optimal** mit Button **Beheben**, wenn die Bedienungshilfe aus ist, Offline gewählt aber kein Modell geladen ist, offline eine KI-Stufe an ist, aber das Textmodell fehlt („Offline ohne Textmodell — der Text kommt ohne KI.", bei „Online, ohne Netz lokal" mit eigenem Zugang „… — ohne Netz kommt der Text ohne KI."; führt zu den Offline-Modellen), oder Benachrichtigungen verweigert sind.
- Die Karte **Status** mit den Zeilen **Spracherkennung**, **Textverbesserung**, **Berechtigungen**, **Diktat-Tastatur** und (sobald relevant) **Offline-Modelle**. Jede Zeile führt in die passende Einstellung.
- Ein Hinweis auf das Abtippen von Sprachnachrichten und ganz unten die Fußzeile: die Versionsnummer mit **?** daneben und **Einrichtung erneut öffnen**. Das **?** öffnet die **Patchnotes**: oben die neueste Version mit dem Wichtigsten und „Alle … Änderungen im Detail“ zum Aufklappen, darunter **Frühere Versionen** und **Vor Version 3** zum Antippen, ganz unten das vollständige Changelog auf GitHub. Dasselbe **?** steht in **Anleitung & Hilfe** (unten) und unter **Über WhisperLoom**.
- Oben rechts: das Verlauf-Symbol ([11](#11-verlauf)), **?** (Anleitung und Hilfe) und **⚙** (Einstellungen).

Die **Einstellungen** sind nach Gegenständen in sechs Gruppen mit Überschrift geteilt. Jede Zeile zeigt darunter, was gerade eingestellt ist, und ihr Name ist der Titel der Seite, die sie öffnet:

| Gruppe | Einträge |
|---|---|
| **TEXT** | **Textverbesserung** (Stufen fürs Diktat und für Sprachnachrichten, je Stufe eine Seite) · **Wörterbuch & Regeln** (Vokabular, feste Regeln) — [Kapitel 8](#8-textverbesserung) |
| **MODELLE & ZUGÄNGE** | **Spracherkennung** (online oder offline, Anbieter, Key, Modell, Sprache) · **KI-Zugang** (Zugang für die Textverbesserung, [8.3](#83-ki-zugang)) · **Offline-Modelle** (Whisper- und Textmodelle, [9](#9-offline-modus)) |
| **BEDIENUNG** | **Knopf & Tastatur** · **Widgets** |
| **VERLAUF** | **Verlauf** ([11](#11-verlauf)) |
| **PRO** | **Erweitert** — Pro-Funktionen für Entwickler und Bastler ([Pro Widgets](#14-pro-widgets-sprach-command-widgets), [Stufe „Prompt“](#81-diktat), [Modelle vom Server](#76-modelle-vom-server-pro-funktion)); für das normale Diktieren brauchst du hier nichts |
| **INFO** | **Anleitung & Hilfe** · **Über WhisperLoom** |

Änderungen werden sofort gespeichert; es gibt keinen „Speichern"-Button.

---

## 4. Diktieren mit dem schwebenden Knopf

Der Knopf (68 dp groß) schwebt über allen Apps, solange er läuft. Er startet über **Mikro-Knopf starten** auf dem Startbildschirm oder am Ende des Assistenten und läuft weiter, wenn du WhisperLoom verlässt. Solange er läuft, zeigt Android die stille Benachrichtigung „WhisperLoom-Diktat aktiv" mit der Aktion **Beenden**; ein Tipp auf die Benachrichtigung öffnet WhisperLoom.

### 4.1 Die vier Zustände

| Zustand | So sieht er aus | Tippen | Ziehen |
|---|---|---|---|
| **Bereit** | dunkler Kreis mit türkisem Ring, Mikrofon-Symbol | Aufnahme starten | Knopf verschieben |
| **Nimmt auf** | roter Kreis, pulsierender Ring, Stopp-Symbol, darunter der Timer „● 0:07" | Aufnahme beenden und senden | Knopf verschieben — dabei erscheint unten das Abbrechen-Ziel |
| **Sendet** | dunkeltürkiser Kreis mit rotierendem Bogen, Label „sendet …", während der Textverbesserung „verbessert … tippen = ohne KI" | bei „sendet …" ignoriert; bei „verbessert …" sofort ohne KI einfügen ([9.7](#97-lokales-textmodell-gemma-4)) | Knopf verschieben |
| **Fehler** | dunkelroter Kreis, Wiederholen-Symbol, Label „tippen = erneut" | erneut senden — das Audio ist noch da | aufs Abbrechen-Ziel ziehen = verwerfen |

Der Ablauf im Normalfall: **antippen → sprechen → nochmal antippen**. Kurz darauf steht der Text im Feld, in dem der Cursor stand. Wichtig: Der Cursor muss in einem Textfeld stehen, *bevor* du die Aufnahme beendest — WhisperLoom fügt dort ein, wo gerade der Fokus ist. Außerdem landet der Text im Verlauf ([11](#11-verlauf)), außer die Bedienungshilfe meldet das Feld als Passwortfeld.

Beim Diktieren gilt: normal sprechen, ohne Kunstpausen. Satzzeichen musst du nicht diktieren — die Online-Modelle setzen sie selbst; mit der Stufe „Glätten" der Textverbesserung ([Kapitel 8](#8-textverbesserung)) werden Zeichensetzung und Groß-/Kleinschreibung zusätzlich korrigiert. Vor dem Senden schneidet WhisperLoom Stille am Anfang und Ende weg.

### 4.2 Verschieben und Abbrechen

- **Verschieben:** Knopf gedrückt halten und ziehen. Er bleibt, wo du ihn loslässt (kein Einrasten am Rand), und merkt sich die Position. Er rutscht nie aus dem Bildschirm.
- **Abbrechen:** Während der Aufnahme (oder im Fehlerzustand) den Knopf nach unten ziehen. Am unteren Rand erscheint das Abbrechen-Ziel **✕ Verwerfen**. Sobald der Knopf darüber ist, wächst das Ziel, färbt sich rot und zeigt **Loslassen zum Verwerfen** — loslassen, und das Diktat ist weg („Diktat verworfen"). Der Knopf springt an seine alte Position zurück.
- **Position zurücksetzen:** Ist der Knopf mal an einer unpraktischen Stelle gelandet (z. B. nach dem Drehen des Bildschirms), setzt Einstellungen → Knopf & Tastatur → **Position zurücksetzen** ihn an die Startposition.

### 4.3 Fehler und erneut senden

Schlägt die Erkennung fehl — kein Netz, Server überlastet, Zeitüberschreitung —, wechselt der Knopf in den Fehlerzustand, wackelt kurz und zeigt „tippen = erneut". **Das Diktat geht nicht verloren:** Das Audio bleibt gepuffert, ein Tipp sendet es erneut. Erst das Ziehen aufs ✕ verwirft es.

Bei Fehlern, die sich durch Wiederholen nicht beheben lassen (z. B. ungültiger Key, unbekanntes Modell), zeigt WhisperLoom den Grund als kurze Meldung; in dem Fall hilft ein Blick in Einstellungen → Spracherkennung ([Kapitel 13](#13-wenn-etwas-nicht-klappt)).

### 4.4 Ohne Bedienungshilfe: Zwischenablage

Ist die Bedienungshilfe nicht aktiv, kann WhisperLoom den Text nicht direkt einfügen. Er wird stattdessen in die **Zwischenablage** kopiert; der Knopf zeigt zwei Sekunden lang „Kopiert — einfügen". Dann im Textfeld lange drücken → Einfügen. Auf dem Startbildschirm erinnert das Banner „Ohne Bedienungshilfe landet der Text nur in der Zwischenablage." mit **Beheben** an den fehlenden Schritt. Unter Einstellungen → Knopf & Tastatur zeigt die Zeile **Textausgabe**, welcher Weg gerade gilt („Bedienungshilfe: fügt direkt ins Feld ein“ oder „Zwischenablage (Bedienungshilfe aus)“); ein Tipp darauf führt zur Bedienungshilfe, beim Einschalten mit dem Hinweis vorab. Ohne Bedienungshilfe kennt der Knopf das Zielfeld nicht und erkennt deshalb auch kein Passwortfeld: Das Diktat kommt dann immer in den Verlauf, wenn er an ist.

### 4.5 Beenden

**Mikro-Knopf beenden** auf dem Startbildschirm, oder **Beenden** in der Benachrichtigung. Beim nächsten Start steht der Knopf wieder an seiner gemerkten Position.

---

## 5. Diktieren mit der Tastatur

Die **Diktat-Tastatur** ist eine eigene Android-Eingabemethode („WhisperLoom Diktat"). Sie ersetzt deine normale Tastatur nicht — du wechselst bei Bedarf hin und wieder zurück.

### 5.1 Aktivieren und wechseln

Aktivieren und auswählen wie in [Schritt 7](#schritt-7--diktat-tastatur-optional-pflicht-wenn-du-in-schritt-4-nur-tastatur-nutzen-gewählt-hast) des Assistenten, oder später unter Einstellungen → Knopf & Tastatur → **Diktat-Tastatur**. Zwischen den Tastaturen wechselst du wie bei jeder Android-Tastatur: über das Tastatur-Symbol in der Navigationsleiste, oder direkt in der WhisperLoom-Tastatur über die Globus-Taste („Eingabemethode wechseln").

### 5.2 Bedienung

Die Tastatur besteht aus drei Zonen:

- **Statuszeile** oben: „Halte das Mikrofon gedrückt und sprich" — während der Aufnahme „Höre zu … rechts wischen stellt fest", dann „Wird übertragen …" und, mit KI-Stufe, „Text wird verbessert … antippen = ohne KI einfügen": Ein Tipp auf die Statuszeile oder das Mikrofon fügt den erkannten Text dann sofort ohne KI ein. Fehlt das Mikrofon oder der Zugang, steht dort ein Warntext („Mikrofon-Berechtigung fehlt — tippe zum Einrichten" / „Kein Zugang eingerichtet — tippe zum Einrichten"); ein Tipp darauf öffnet den passenden Schritt der Einrichtung.
- **Pegelband und Mikrofon-Taste**: Die große Taste in der Mitte funktioniert mit **Halten-zum-Sprechen** — gedrückt halten, sprechen, loslassen. Das Pegelband darüber zeigt während der Aufnahme deine Lautstärke. Die Taste zeigt dieselben vier Zustände wie der schwebende Knopf (bereit, nimmt auf, sendet, Fehler); festgestellt ist sie zusätzlich Pause und Weiter ([5.3](#53-ohne-halten-diktieren-wisch-geste-und-pause)).
- **Tastenreihe** unten: Globus (Eingabemethode wechseln; gesperrt, solange ein Diktat offen ist) · Komma · Leertaste · Punkt · Löschen · Eingabe · (nur nach einem Fehler:) **Erneut senden** · Zahnrad (WhisperLoom-Einstellungen).

Rechts über dem Mikrofon sitzt außerdem ein **Zauberstab** — er klappt die Textverbesserung auf, siehe [5.4](#54-textverbesserung-direkt-in-der-tastatur).

Der erkannte Text wird direkt an der Cursor-Position eingefügt — die Bedienungshilfe ist bei der Tastatur nicht nötig — und vorher in den Verlauf geschrieben ([11](#11-verlauf)), außer aus Passwort- und Inkognito-Feldern. Nach einem Fehler („Fehler bei der Erkennung — erneut versuchen") bleibt das Audio erhalten; die Taste **Erneut senden** wiederholt den Versuch. Seit 3.9.0 gilt das auch, wenn beim Senden der Zugang („Kein Zugang eingerichtet — tippe zum Einrichten“) oder das Offline-Modell fehlt. Für Zugang oder Modell darfst du dafür in die App wechseln: Zurück im selben Feld steht **Erneut senden** noch da. Wechselst du in ein anderes Feld, verfällt das Audio eines Fehlers.

Wenn du „Leerzeichen nach Diktat anhängen" (Einstellungen → Knopf & Tastatur → Einfügen) aktiv hast, kannst du mehrere Diktate direkt hintereinander sprechen, ohne zwischendurch ein Leerzeichen zu tippen.

### 5.3 Ohne Halten diktieren: Wisch-Geste und Pause

Für längere Diktate musst du das Mikrofon nicht die ganze Zeit gedrückt halten. Halte es an, fang an zu sprechen — und **zieh den Finger nach rechts**, ohne loszulassen. Links und rechts neben dem Mikrofon erscheinen zwei Kreise: links ✕ zum Verwerfen, rechts ein Schloss zum Feststellen. Sobald der Kreis sich einfärbt, sagt die Statuszeile, was das Loslassen tut („Loslassen stellt die Aufnahme fest"). Jetzt loslassen.

Die Aufnahme läuft dann **freihändig weiter**. Die Statuszeile zählt die Dauer mit („Aufnahme 0:42 — Pause, senden, verwerfen"), und aus den beiden Kreisen werden Tasten:

- **Senden** (rechts, Papierflieger ➤) beendet die Aufnahme und fügt den Text ein.
- **Verwerfen** (links, ✕) wirft die Aufnahme weg — nichts wird übertragen, nichts eingefügt.
- Die große **Mikro-Taste** in der Mitte ist jetzt **Pause** (Pause-Symbol). Bis 3.8.6 sendete ein Tipp darauf; gesendet wird seit 3.9.0 nur noch über ➤.

**Nach links ziehen** statt nach rechts verwirft sofort, ohne Umweg über das Feststellen. Und wenn du einfach loslässt, ohne zu ziehen, wird wie bisher direkt gesendet — die Geste ändert daran nichts. Ziehst du stark nach oben oder unten weg, gilt das nicht mehr als Wischen und es rastet nichts ein.

**Pause.** Ein Tipp auf die Mikro-Taste hält die festgestellte Aufnahme an. In der Pause ist das Mikrofon wirklich frei — der Mikrofon-Punkt von Android geht aus, ein Anruf oder eine andere App bekommt es —, Uhr und Pegelband stehen still, und die Mikro-Taste zeigt wieder das Mikrofon. Die Statuszeile sagt „Pausiert bei 0:42 — weiter, senden, verwerfen“. Ein Tipp auf die Mikro-Taste setzt fort; schon das Aufsetzen des Fingers zählt, damit kein Wort verloren geht, wenn du aus Gewohnheit hältst und sprichst. Danach läuft die Aufnahme festgestellt weiter. Aus der Pause heraus kannst du genauso senden (➤) oder verwerfen (✕). Die Teile vor und nach der Pause gehen als eine Aufnahme an die Erkennung, ohne Stille dazwischen und ohne zusätzliche Kosten. Pausieren geht nur im festgestellten Zustand — beim Halten liegt der Finger ja auf der Taste.

**Tastatur zu, anderes Feld:**

- **Pausiert** bleibt die Pause, wenn du die Tastatur schließt oder in eine andere App wechselst, etwa um etwas nachzuschlagen. Beim nächsten Öffnen steht wieder „Pausiert bei …“, und du machst weiter, sendest oder verwirfst. Wechselst du in der Pause das Feld, fügt **Senden** in das Feld ein, das dann gerade offen ist. Das Audio liegt bis dahin nur im Arbeitsspeicher; beendet Android die App in der Pause, ist es weg.
- **Läuft** die festgestellte Aufnahme noch, wenn du die Tastatur schließt, wird sie wie bisher fertig übertragen und eingefügt, solange das Eingabefeld erhalten bleibt. Ist auch das Feld weg (Eingabe beendet), wird die Aufnahme verworfen. Kommt ein fertiger Text in kein Feld mehr, liegt er im Verlauf ([11](#11-verlauf)); ist der Verlauf aus, in der Zwischenablage. Aus Passwort- und Inkognito-Feldern hebt WhisperLoom ihn nirgends auf, die Statuszeile sagt dann „Feld nicht mehr da — Text nicht eingefügt“.
- Solange ein Diktat offen ist (laufend oder pausiert), ist der **Globus gesperrt**: Ein Tipp sagt „Erst senden oder verwerfen“. Wechselst du trotzdem über das Tastatur-Symbol der Navigationsleiste oder die Systemeinstellungen, endet die Diktat-Tastatur. Das offene Diktat überträgt WhisperLoom dann im Hintergrund wie beim Senden und legt den Text im Verlauf ab, ohne Hinweis. Ist der Verlauf aus, war das Feld ein Passwort- oder Inkognito-Feld oder scheitert die Erkennung (etwa ohne Netz), ist das Diktat weg.

**Lange Diktate (nur Online-Erkennung):** Ab 10 Minuten Aufnahmezeit sagt die Statuszeile „Aufnahme 10:00 — bald senden, höchstens 12:00“. Bei 12 Minuten pausiert die Aufnahme von selbst: „Höchstlänge erreicht — senden oder verwerfen“, weitersprechen geht dann nicht mehr. Gezählt wird nur die aufgenommene Zeit, Pausen nicht. Grund: OpenAI lehnt Aufnahmen ab etwa 13 Minuten ab, und das Audio wäre dann weg. Offline gibt es diese Grenze nicht. Stellst du in der Pause über das Zahnrad die Spracherkennung um, prüft WhisperLoom neu: Ist die Aufnahme für die Online-Erkennung zu lang (etwa offline über 12 Minuten aufgenommen), sind **Senden** und **Weiter** gesperrt, und die Statuszeile sagt „Zu lang für Online-Erkennung — offline senden oder verwerfen“. Fehlt beim **Senden** der Zugang, bleibt das Diktat ebenfalls pausiert („Kein Zugang eingerichtet — tippe zum Einrichten“). Zurück auf Offline geht es ohne Höchstlänge weiter.

**Mit TalkBack:** Gedrückthalten funktioniert dort nicht. Deshalb **startet ein Antippen** des Mikrofons die Aufnahme, und sie ist sofort festgestellt. Der Ablauf: Tipp = Start, Tipp = Pause, Tipp = Weiter. Gesendet und verworfen wird über die Aktionen der Mikro-Taste — im TalkBack-Aktionsmenü „Aufnahme senden“ und „Aufnahme verwerfen“ —, ohne dass du den Fokus auf die kleinen Tasten daneben bringen musst; die Tasten Verwerfen und Senden sind aber auch ganz normal ansteuerbar. Die Mikro-Taste sagt ihren Zustand an („Aufnahme festgestellt, 0:42 — antippen zum Pausieren“, „Aufnahme pausiert, 0:42 — antippen zum Weitersprechen“), der gesperrte Globus „Eingabemethode wechseln — erst senden oder verwerfen“.

### 5.4 Textverbesserung direkt in der Tastatur

Die Stufe der Textverbesserung musst du nicht in den Einstellungen suchen. Ein Tipp auf den **Zauberstab** rechts über dem Mikrofon klappt eine Zeile mit den vier Stufen auf:

**Aus · Glätten · Schöner · Kürzen**

(Das sind dieselben Stufen wie unter Einstellungen → Textverbesserung → Beim Diktieren, dort heißen sie ausgeschrieben „Verschönern" und „Zusammenfassen" — in der schmalen Tastenzeile passen nur die Kurzfassungen.)

Die aktuelle Stufe ist hervorgehoben. Ein Tipp wählt eine andere — sie gilt ab dem nächsten Diktat, ohne Umweg über die Einstellungen. Änderst du sie hier, steht sie in den Einstellungen genauso. Das Modell folgt der Stufe: Hast du unter **Modell je Stufe** ([8.4](#84-modell-je-stufe)) etwa fürs Zusammenfassen ein eigenes Modell gewählt, rechnet „Kürzen“ damit. Bereinigung, Absätze und Form gelten, wie du sie auf den Seiten der Stufen eingestellt hast ([8.1](#81-diktat)). Ein zweiter Tipp auf den Zauberstab klappt die Zeile wieder ein; solange sie zu ist, ist die Tastatur genauso hoch wie vorher.

Sobald du zu diktieren anfängst, verschwindet der Zauberstab — an seiner Stelle erscheinen dann die Ziele der Wisch-Geste; auch in der Pause bleibt er verborgen. Eine offene Stufen-Zeile klappt dabei zu.

Die drei oberen Stufen brauchen einen KI-Zugang. Hast du keinen eingerichtet, sind sie ausgegraut und die Statuszeile sagt „KI-Zugang fehlt — tippe zum Einrichten“ — ein Tipp darauf öffnet den KI-Zugang ([8.3](#83-ki-zugang)). Bei Offline-Erkennung entscheidet die Regel aus [9.7](#97-lokales-textmodell-gemma-4): Mit geladenem Textmodell sind die Stufen frei; fehlt es, steht dort „Offline ohne Textmodell — tippe zum Laden", und der Tipp führt zu den Offline-Modellen. **Aus** bleibt immer wählbar.

Hast du unter Einstellungen → Erweitert die **Stufe „Prompt“** eingeschaltet, steht sie als fünfte Taste am Ende der Zeile ([Kapitel 8.1](#81-diktat)).

---

## 6. Sprachnachrichten abtippen

WhisperLoom erscheint im **Teilen-Menü** von Android für Audiodateien („Mit WhisperLoom transkribieren"). Damit lässt sich jede Sprachnachricht in Text verwandeln — ideal für lange Nachrichten oder wenn du gerade nicht hören kannst.

### 6.1 So geht's

- **WhatsApp:** Sprachnachricht lange drücken → **Teilen** (bzw. ⋮ → Teilen) → **WhisperLoom**.
- **Telegram, Signal:** Nachricht lange drücken → Teilen → WhisperLoom. Wo genau der Teilen-Eintrag sitzt, unterscheidet sich je App und Version — er heißt aber überall „Teilen".
- Genauso mit Aufnahmen aus Rekorder-Apps oder Dateien aus dem Dateimanager. Mehrere Dateien auf einmal gehen auch.

WhisperLoom öffnet den Bildschirm **Transkription**. Oben steht die Quelle mit Dauer („Sprachnachricht · 0:42 · 1 Datei"), darunter der Fortschritt („Audio wird entpackt …", „Wird übertragen …", mit KI-Stufe „Text wird verbessert …" bzw. mit dem lokalen Textmodell „Text wird lokal verbessert …"; bei mehreren oder langen Dateien „Datei 1 von 3 · Stück 1 von 2 · …"). Fertige Dateien erscheinen sofort, auch wenn weitere noch laufen.

### 6.2 Das Ergebnis

- Der Text erscheint in **Absätzen** — WhisperLoom setzt sie an Satzgrenzen, bevorzugt vor Wörtern wie „Also", „Außerdem", „Dann". Ausnahme: „Zusammenfassen“ mit der Form „Fließtext“ ([8.2](#82-sprachnachrichten)) gibt je Stück einen Absatz. Bei mehreren Dateien gibt es je Datei einen Abschnitt mit Quelle und Dauer. Der Text ist markierbar.
- Schalter **Füllwörter ausblenden** (standardmäßig **an**): Blendet „ähm, äh …" aus. Ausgeschaltet zeigt WhisperLoom den Text **wortgetreu, 100 %** — bei fremden Nachrichten will man manchmal genau wissen, was gesagt wurde. Der Schalter wirkt sofort auf die Anzeige und auf Kopieren/Teilen; es wird nichts neu hochgeladen.
- Ist unter Einstellungen → Textverbesserung → **Bei geteilten Sprachnachrichten** eine KI-Stufe gewählt ([Kapitel 8.2](#82-sprachnachrichten)), zeigt der Bildschirm die verbesserte Fassung; der Füllwort-Schalter entfällt dann, darunter steht z. B. „Textverbesserung: Glätten · änderbar unter Einstellungen › Textverbesserung“ — ein Tipp auf den Link öffnet diese Seite. Scheitert die KI (Netz, Anbieter, kein Zugang), erscheint wie gewohnt die Fassung ohne KI mit Schalter und dem Hinweis „Textverbesserung übersprungen: …"; bei mehreren Dateien nennt die Zeile, bei wie vielen die KI übersprungen wurde. Bei Offline-Erkennung mit der Regel „Online, ohne Netz lokal" springt nach einem Online-Fehler das lokale Textmodell ein („Textverbesserung: Glätten · online fehlgeschlagen, lokal verbessert"); die weiteren Stücke gehen dann gleich lokal ([9.7](#97-lokales-textmodell-gemma-4)).
- **Kopieren** legt den angezeigten Text in die Zwischenablage (bis Android 12 mit dem Hinweis „In die Zwischenablage kopiert", ab Android 13 bestätigt das System selbst); **Teilen** gibt ihn als Text an eine andere App weiter. Die Zwischenablage wird nie automatisch überschrieben.
- Ist eine Datei fehlgeschlagen, steht der Grund bei ihr („Fehlgeschlagen: …") mit **Erneut** nur für diese Datei; bei mehreren Fehlern gibt es zusätzlich **Alles erneut**.
- Ist noch kein Zugang eingerichtet, zeigt der Bildschirm „Kein Zugang eingerichtet" mit **Einrichtung öffnen**.

### 6.3 Lange Nachrichten

WhisperLoom wandelt geteiltes Audio auf dem Gerät in das Format um, das die Erkennung braucht (16 kHz, Mono) — deshalb funktionieren auch WhatsApp-Nachrichten (Opus in OGG), die viele Online-Dienste nicht direkt annehmen. Lange Aufnahmen werden in **Stücke von höchstens 5 Minuten** geteilt; geschnitten wird an einer leisen Stelle (einer Sprechpause), damit kein Wort zerteilt wird. Jedes Stück wird einzeln erkannt, der Text hinterher zusammengesetzt; die Fortschrittsanzeige zählt die Stücke mit. Das gilt für Online-Dienste wie für den Offline-Modus.

Beim Offline-Modus dauern lange Nachrichten entsprechend länger; Schließen des Bildschirms bricht die laufende Erkennung ab.

### 6.4 Was nicht passiert

Geteilte Nachrichten werden ab Werk **nicht** durch die KI-Textverbesserung geschickt — sie erscheinen so, wie sie erkannt wurden (nur die lokale Füllwort-Ausblendung und die Absatzbildung kommen dazu). Die Stufe fürs Diktat gilt hier nicht; wer auch Sprachnachrichten glätten, verschönern oder zusammenfassen lassen will, stellt das getrennt unter Einstellungen → Textverbesserung → **Bei geteilten Sprachnachrichten** ein ([8.2](#82-sprachnachrichten)). Und sie werden nirgends gespeichert, auch nicht im Verlauf: Wenn du den Bildschirm schließt, ist der Text weg — außer du hast ihn kopiert oder geteilt.

---

## 7. Anbieter und API-Keys

WhisperLoom spricht die **OpenAI-kompatible API**, die inzwischen viele Anbieter anbieten; ElevenLabs und Ollama spricht es über deren eigene Schnittstelle. Du brauchst ein Konto beim Anbieter deiner Wahl und einen **API-Key** — einen persönlichen Zugangsschlüssel, mit dem der Anbieter die Nutzung abrechnet. Du bezahlst nur, was du nutzt; ein Diktat kostet meist unter einem Cent.

Alle Angaben in diesem Kapitel: **Stand 09/2026, Textmodelle 10/2026, ohne Gewähr.** Preise in US-Dollar, wie von den Anbietern ausgewiesen.

### 7.1 Übersicht

Die Spalte „Modelle" nennt die Einträge, wie sie in WhisperLoom im Dropdown stehen (erster Eintrag = Voreinstellung). Bei der Textverbesserung steht davor **Empfehlung je Stufe** (ab Werk, [8.4](#84-modell-je-stufe)): Glätten rechnet dann mit dem ersten Modell, Verschönern, Zusammenfassen und Prompt mit dem zweiten (beide fett).

| Anbieter (Dropdown) | Für | Modelle (Erkennung) | Modelle (Textverbesserung) | Preis Erkennung | Kostenlos? | Key holen |
|---|---|---|---|---|---|---|
| **OpenAI** | Erkennung + Text | GPT Transcribe (empfohlen) · GPT-4o Transcribe (Auslauf 02/2027) · GPT-4o mini Transcribe (Auslauf 02/2027) · Whisper v2 (Legacy, Auslauf 02/2027) | **GPT-6 Luna** · **GPT-6 Sol** · GPT-4o mini · GPT-4.1 mini · GPT-5.6 Luna · GPT-5.4 nano (Auslauf 04/2027) · GPT-5 mini (Auslauf 12/2026) · GPT-5 nano (Auslauf 12/2026) | GPT Transcribe $0,0045/min · GPT-4o Transcribe $0,006/min · GPT-4o mini Transcribe $0,003/min · Whisper $0,006/min | nein — Guthaben ab $5 | https://platform.openai.com/api-keys |
| **Groq (kostenlos)** | Erkennung + Text | Whisper Large v3 Turbo · Whisper Large v3 | **GPT-OSS 20B** · **GPT-OSS 120B** · Qwen 3.8 27B (Preview) | Turbo $0,04/h (≈ $0,00067/min) · Large v3 $0,111/h | **ja** — Free-Plan ohne Zahlungsmittel: 2 h Audio/Stunde, 8 h/Tag; LLM 30 Anfragen/min, 1.000/Tag | https://console.groq.com/keys |
| **Mistral** | Erkennung + Text | Voxtral Mini Transcribe 2 | **Mistral Small 4** · **Mistral Large 3** · Ministral 3 8B | $0,003/min | Experiment-Plan gratis (Telefon-Verifizierung; ob Audio enthalten ist, ist nicht belegt) | https://console.mistral.ai/api-keys |
| **ElevenLabs (Scribe)** | nur Erkennung | Scribe v2 · Scribe v2 Medical | — (keine Textverbesserung) | $0,22/h (≈ $0,0037/min); Vokabular kostet etwa 20 % Aufpreis | Free-Plan mit 4 h 30 min Scribe | https://elevenlabs.io/app/settings/api-keys — Key mit Berechtigung **„Speech to Text“** |
| **Together AI** | Erkennung | Whisper Large v3 | — (Modell frei eintippen) | $0,0015/min | Startguthaben für neue Konten | https://api.together.ai/settings/api-keys |
| **DeepInfra** | Erkennung | Whisper Large v3 Turbo · Whisper Large v3 | — (Modell frei eintippen) | Turbo $0,0002/min · Large v3 $0,00045/min | nein | https://deepinfra.com/dash/api_keys |
| **OpenRouter** | Erkennung + Text | Voxtral Mini Transcribe (via OpenRouter) · GPT-4o mini Transcribe (via OpenRouter) · Whisper Large v3 Turbo (via OpenRouter) | **Claude Haiku 5.5** · **Claude Sonnet 5.5** · GPT-6 Luna · GPT-4o mini · Gemini 3.8 Flash · Mistral Small 4 · Claude Haiku 4.5 (Legacy) | ab $0,003/min | „:free"-Textmodelle mit Limits (20/min; 50 bzw. 1.000/Tag) | https://openrouter.ai/settings/keys |
| **Anthropic (Claude)** | nur Text | — | **Claude Haiku 5.5** · **Claude Sonnet 5.5** · Claude Opus 5.5 · Claude Sonnet 5 · Claude Haiku 4.5 (Legacy) | — | kleines Startguthaben | https://platform.claude.com/settings/keys |
| **Google Gemini** | nur Text | — | **Gemini 3.5 Flash-Lite** · **Gemini 3.8 Flash** · Gemini 2.5 Flash-Lite und Gemini 2.5 Flash (nur Bestandskonten) | — | **ja**, aber Free-Tier-Inhalte dürfen zum Training genutzt werden | https://aistudio.google.com/apikey |
| **DeepSeek** | nur Text | — | **DeepSeek Flash (V4.1)** · **DeepSeek V4 Pro** · DeepSeek V4 Flash (alter Name) | — | nein | https://platform.deepseek.com/api_keys |
| **Ollama (lokal / Homeserver)** | nur Text | — | frei — die Liste kommt automatisch von deinem Server (Empfehlung je Hardware in [8.3](#83-ki-zugang)) | — | deine Hardware | kein Key nötig (Feld optional) |
| **Ollama Cloud** | nur Text | — | **Gemma 4 31B (empfohlen)** · **Mistral Large 3** · GLM 5.3 Flash · GPT-OSS 20B · GPT-OSS 120B; weitere per Knopf unter dem Feld „Modell" ([8.3](#83-ki-zugang)) | — | — | https://ollama.com/settings/keys |
| **Eigener Server** | Erkennung + Text | frei (z. B. `Systran/faster-whisper-medium`, `whisper-1`) | frei (z. B. `qwen3:8b`) | deine Hardware | — | kein Key nötig, außer dein Server verlangt einen |

Mit der Pro-Funktion **Modelle vom Server** wählst du zusätzlich aus der aktuellen Liste des Anbieters ([7.6](#76-modelle-vom-server-pro-funktion)).

Textverbesserung kostet zusätzlich. Mit den Modellen zum Glätten bleibt das deutlich unter den Kosten der Erkennung — bei GPT-6 Luna oder Claude Haiku 5.5 etwa $0,0002 pro Diktat-Minute, bei Groqs GPT-OSS 20B etwa $0,0001. Die stärkeren Modelle zum Umformulieren kosten ein Vielfaches: GPT-6 Sol oder Claude Sonnet 5.5 etwa $0,003–0,004 pro Diktat-Minute, Claude Opus 5.5 knapp $0,01 — so viel wie die Erkennung selbst oder mehr. Bei OpenAI ist Umformulieren mit GPT-6 Sol damit 13- bis 17-mal so teuer wie mit dem früheren Standard GPT-4o mini, bei OpenRouter mit Claude Sonnet 5.5 ebenso. Wer viel verschönert oder zusammenfasst und sparen will, stellt unter **Modell je Stufe** ([8.4](#84-modell-je-stufe)) ein günstigeres Modell ein.

### 7.2 Empfehlungen

- **Kostenlos anfangen: Groq.** Free-Plan ohne Kreditkarte, 8 Stunden Audio pro Tag, „Whisper Large v3 Turbo" ist für Deutsch gut und sehr schnell. Mit demselben Key läuft auch die Textverbesserung (GPT-OSS 20B) — ein Konto, alles gratis.
- **Beste Qualität: OpenAI „GPT Transcribe".** Das aktuelle Modell mit den besten dokumentierten Erkennungsraten; $0,0045 pro Minute, dafür ist eine Mindestaufladung von $5 nötig. Das Standardmodell in WhisperLoom.
- **Europäischer Anbieter: Mistral „Voxtral Mini Transcribe 2".** Server in der EU, Deutsch als Kernsprache, $0,003/min.
- **Viele Sprachen, Fachbegriffe: ElevenLabs „Scribe v2".** Über 90 Sprachen, $0,22 pro Stunde; das Vokabular geht als Schlüsselbegriffe mit (etwa 20 % Aufpreis). Für medizinische Diktate gibt es „Scribe v2 Medical". Keine Textverbesserung — dafür einen eigenen Zugang eintragen ([8.3](#83-ki-zugang)).
- **Sehr günstig:** DeepInfra ab $0,0002/min oder Together AI $0,0015/min — beide mit Whisper Large v3.
- **Ein Key für alles:** OpenRouter bündelt viele Modelle unter einem Konto; für lange Aufnahmen ungünstig (60-Sekunden-Limit je Anfrage beim Anbieter).
- **Textverbesserung:** Ohne eigene Wahl rechnet jeder Anbieter mit seiner **Empfehlung je Stufe** ([7.1](#71-übersicht)). Gemessen ist nur Anthropic (108 Anfragen, blind bewertet, Noten bis 10): Claude Haiku 5.5 glättet fast so gut wie Sonnet 5.5 (8,46 zu 8,65, mit der Bereinigung „Lesbar“ sogar 8,56 zu 8,43), in 0,8 statt 1,6 s und für etwa ein Zwanzigstel des Preises. Beim Zusammenfassen liegt Sonnet deutlich vorn (7,5 zu 6,9). Opus 5.5 schreibt insgesamt am besten (8,35 gegenüber 8,19 bei Sonnet), braucht aber 2,8 s und kostet rund das Zweieinhalbfache von Sonnet. Die Empfehlungen der anderen Anbieter folgen deren Angaben und sind nicht gemessen.

### 7.3 Modelle, die auslaufen

OpenAI hat die älteren Erkennungsmodelle **GPT-4o Transcribe, GPT-4o mini Transcribe und Whisper v2 zum 2027-02-26 abgekündigt**; die Textmodelle GPT-5 mini und GPT-5 nano enden am 2026-12-11, GPT-5.4 nano am 2027-04-01 (Nachfolger GPT-6 Luna). WhisperLoom kennzeichnet solche Einträge im Dropdown mit „(Auslauf 02/2027)", „(Auslauf 12/2026)" bzw. „(Auslauf 04/2027)". Claude Haiku 4.5 heißt „(Legacy)": Anthropic schaltet es frühestens am 2026-10-15 ab, und es kostet das Zehnfache von Haiku 5.5. Wer aus Version 2.x umsteigt, behält sein bisheriges Modell (meist GPT-4o Transcribe) — es funktioniert bis zur Abschaltung, danach unter Spracherkennung → Modell auf „GPT Transcribe (empfohlen)" wechseln.

### 7.4 Key besorgen — Schritt für Schritt

Dieselben Schritte zeigt WhisperLoom unter **Wo bekomme ich einen Key?** (im Assistenten und unter Spracherkennung) und unter Anleitung & Hilfe → **API-Key bekommen**; von dort führt jeweils ein Link direkt zur Key-Seite. Der Key wird beim Erzeugen meist **nur einmal angezeigt** — direkt kopieren und in WhisperLoom einfügen (Einfügen-Symbol im Key-Feld).

**OpenAI** — 1) https://platform.openai.com registrieren. 2) *Settings → Billing* → Zahlungsmittel + Prepaid-Guthaben (mind. $5). 3) https://platform.openai.com/api-keys → *Create new secret key* → kopieren. 4) In WhisperLoom: Anbieter „OpenAI", Key einfügen.

**Groq** — 1) https://console.groq.com registrieren (Google/GitHub/E-Mail). 2) https://console.groq.com/keys → *Create API Key*. 3) Kostenlos nutzbar ohne Zahlungsmittel (Free-Plan). Optional *Billing → Developer* für höhere Limits und 100-MB-Dateien.

**Mistral** — 1) https://console.mistral.ai registrieren, Studio aktivieren. 2) Plan wählen: *Experiment* (gratis, Telefonnummer verifizieren) oder *Pay-as-you-go* (Karte). 3) *API Keys* → *Create new key*.

**ElevenLabs** (nur Erkennung) — 1) https://elevenlabs.io registrieren (Free-Plan mit 4 h 30 min Scribe). 2) *Settings → API Keys* → *Create API Key* (direkt: https://elevenlabs.io/app/settings/api-keys) und bei den Berechtigungen **„Speech to Text"** erlauben — ohne sie lehnt ElevenLabs jede Erkennung ab, WhisperLoom meldet dann „Key oder Berechtigung „Speech to Text" prüfen". Für **Modelle vom Server** ([7.6](#76-modelle-vom-server-pro-funktion)) zusätzlich **„Models: Lesen"**. 3) In WhisperLoom: Anbieter „ElevenLabs (Scribe)", Key einfügen. Für die Textverbesserung brauchst du einen zweiten Zugang, etwa Groq ([8.3](#83-ki-zugang)).

**Together AI** — 1) https://api.together.ai registrieren (Startguthaben). 2) *Settings → API Keys* → *Create key*.

**DeepInfra** — 1) https://deepinfra.com anmelden (GitHub/Google). 2) *Dashboard → API Keys* → *New API Key*. 3) Guthaben aufladen.

**OpenRouter** — 1) https://openrouter.ai anmelden. 2) *Credits* aufladen (ab $10 Guthaben steigt das Limit der „:free"-Modelle auf 1.000 Anfragen/Tag). 3) https://openrouter.ai/settings/keys → *Create Key*.

**Anthropic** (nur Textverbesserung) — 1) https://platform.claude.com registrieren. 2) *Billing* → Guthaben (kleines Startguthaben vorhanden). 3) https://platform.claude.com/settings/keys → *Create Key*. WhisperLoom zeigt dazu den Hinweis „OpenAI-Kompatibilitätsschicht — von Anthropic als Test-Werkzeug eingestuft."

**Google Gemini** (nur Textverbesserung) — 1) https://aistudio.google.com/apikey öffnen, mit Google-Konto anmelden, Bedingungen akzeptieren. 2) *Create API key*. 3) Gratis nutzbar — aber: **Im Free-Tier darf Google die Inhalte zum Training nutzen.** Diktate sind oft privat; entweder Billing aktivieren oder den Anbieter meiden. WhisperLoom zeigt die Warnung „Free-Tier: Google darf Inhalte zum Training nutzen."

**DeepSeek** (nur Textverbesserung) — 1) https://platform.deepseek.com registrieren. 2) *Top up* (kein Free-Tier). 3) https://platform.deepseek.com/api_keys → *Create new API key*. Hinweis in WhisperLoom: „Server in China — Datenschutz beachten."

**Ollama Cloud** (nur Textverbesserung) — 1) https://ollama.com registrieren. 2) *Settings → Keys* → *Add API Key* (direkt: https://ollama.com/settings/keys). 3) In WhisperLoom: Einstellungen → **KI-Zugang** → **Eigenen Zugang verwenden** → Anbieter „Ollama Cloud", Key einfügen. Für ein **lokales Ollama** (eigener Rechner oder Homeserver) brauchst du keinen Key ([8.3](#83-ki-zugang)).

### 7.5 Eigenes Modell und Zugang prüfen

- **Eigenes Modell …** (letzter Eintrag im Modell-Dropdown, mit „Modelle vom Server" unten in der Auswahl): Für Modell-IDs, die nicht in der Liste stehen — etwa ein neues Modell des Anbieters oder ein Modell auf dem eigenen Server. Die ID genau so eintragen, wie der Anbieter sie nennt. Für unbekannte IDs setzt WhisperLoom die Parameter selbst: OpenAI-Reasoning-Modelle (z. B. `gpt-5.6-terra`, `o4-mini`) bekommen kein `temperature` und die Denk-Stufe „low", Gemini-3-Modelle kein `temperature` (so rät es Google), Groqs Qwen 3 und GPT-OSS die passende Denk-Stufe, ein Snapshot wie `gpt-5-mini-2025-08-07` erbt vom Listen-Modell. Lehnt ein Server `temperature` trotzdem ab, versucht WhisperLoom es genau einmal ohne und merkt sich das für diesen Anbieter, diese Adresse und dieses Modell: Ab dann geht jede Anfrage gleich ohne `temperature` raus.
- **Zugang prüfen** (unter Spracherkennung bzw. KI-Zugang; für das Modell einer Stufe **Modell prüfen**, [8.4](#84-modell-je-stufe)): Schickt eine kurze Testanfrage an den eingetragenen Zugang und meldet „Verbunden · x s" oder den Fehlergrund. Praktisch nach jedem Key- oder Anbieterwechsel.

### 7.6 Modelle vom Server (Pro-Funktion)

Die Modelle in WhisperLoom sind **Empfehlungen** mit Stand 09/2026 (Textmodelle 10/2026) — die Anbieter bringen laufend neue und schalten alte ab. Wer das aktuelle Angebot sehen will, schaltet unter Einstellungen → Gruppe PRO → **Erweitert** → Karte **Pro-Funktionen** den Schalter **Modelle vom Server** ein („Lädt bei jedem Anbieter die aktuelle Modell-Liste – für Erkennung und Textverbesserung."). Ab Werk ist er aus; dann bleibt alles wie in 7.1 beschrieben (Ollama lädt seine Liste trotzdem, siehe [8.3](#83-ki-zugang)).

Eingeschaltet wird das Feld **Modell** unter Spracherkennung und unter KI-Zugang (auch bei „wie Erkennung", ebenso die Modell-Auswahl auf den Seiten der Stufen) zu einer Auswahl, bei jedem Anbieter einschließlich Eigener Server — nur bei Together AI und DeepInfra bleibt das Textmodell ein Freitext:

- Antippen öffnet **Modell wählen**: oben das Suchfeld **Modell suchen**, darunter **Empfohlen** (die eingebauten Modelle mit ihren Notizen) und **Vom Server · Stand …** mit der geladenen Liste — alphabetisch, bei Anthropic neueste zuerst. Unten **Eigenes Modell …** und **Schließen**.
- Eine Empfehlung, die der Anbieter nicht mehr listet, trägt den Zusatz **nicht mehr gelistet**; ein datierter Snapshot (z. B. `claude-haiku-4-5-20251001`) zählt als gelistet. Dein gewähltes Modell bleibt trotzdem gültig; steht es in keiner Liste, erscheint es markiert ganz oben.
- Der Knopf **Modelle aktualisieren** unter dem Feld lädt die Liste neu und meldet „n Modelle gefunden", „Keine passenden Modelle gefunden." oder „Modelle nicht geladen: …" mit dem Grund. Bei einem Fehler bleibt die bisherige Liste.
- Beim Öffnen von Spracherkennung bzw. KI-Zugang lädt WhisperLoom die Liste still nach, wenn sie fehlt oder älter als einen Tag ist und der Key (falls nötig) eingetragen ist. Ein Fehler dabei bleibt stumm; nach einem Fehlschlag versucht es WhisperLoom still erst am nächsten Tag wieder — sofort, wenn du den Key änderst oder den Knopf drückst.

WhisperLoom zeigt nur, was passt: unter Spracherkennung Spracherkennungs-Modelle, unter KI-Zugang Chat-Modelle — ohne Einbettungs-, Bild- oder Sprachausgabe-Modelle. Abgeschaltete Modelle fallen weg, soweit der Anbieter ein Datum nennt; ein künftiges Abschaltdatum steht als „Auslauf JJJJ-MM-TT" dabei. Für Modelle ohne eingebauten Eintrag setzt WhisperLoom die Parameter selbst ([7.5](#75-eigenes-modell-und-zugang-prüfen)).

**ElevenLabs** listet Scribe womöglich gar nicht — dann bleibt nur „Empfohlen", und „nicht mehr gelistet" erscheint dort nie. Scheitert „Modelle aktualisieren" bei ElevenLabs mit „Key oder Berechtigung „Models: Lesen" prüfen", fehlt dem Key diese Berechtigung; fürs Diktieren braucht er sie nicht. **Ollama** lädt seine Liste wie bisher bei jedem Öffnen. Die Listen speichert WhisperLoom auf dem Gerät, ohne Key ([Kapitel 12](#12-datenschutz)).

---

## 8. Textverbesserung

Hier stellst du ein, was mit dem erkannten Text passiert, bevor er ins Feld kommt. Es gibt zwei Ebenen: die **KI-Textverbesserung** (eine zweite Anfrage an ein Sprachmodell — online oder, bei Offline-Erkennung, an das lokale Textmodell auf dem Gerät) und die **festen Regeln** (lokal, immer verfügbar, kostenlos).

Seit 3.9.0 verteilt sich das auf vier Seiten der Einstellungen. Jede Zeile im Hub zeigt in der Unterzeile, was gerade eingestellt ist:

| Gruppe | Seite | Unterzeile (Beispiel) | Was du dort einstellst |
|---|---|---|---|
| **TEXT** | **Textverbesserung** | „Diktat: Glätten · Sprachnachrichten: Aus“, offline mit KI-Stufe dazu die Regel („… · lokal bei Offline“) | die Stufe fürs Diktat ([8.1](#81-diktat)) und für geteilte Sprachnachrichten ([8.2](#82-sprachnachrichten)), je Stufe eine eigene Seite |
| | **Wörterbuch & Regeln** | „3 Begriffe · Füllwörter · Groß-Schreibung“ | Vokabular ([8.7](#87-wörterbuch-und-sprache)) und feste Regeln ([8.6](#86-feste-regeln)) |
| **MODELLE & ZUGÄNGE** | **KI-Zugang** | „Wie Erkennung · OpenAI · Empfehlung je Stufe“, „Anthropic · 1 Stufe mit eigenem Modell“, „Kein Online-Zugang“ | Anbieter, Key und Modell ([8.3](#83-ki-zugang)), darunter die Übersicht Modell je Stufe ([8.4](#84-modell-je-stufe)) |
| | **Offline-Modelle** | „1 geladen · 190 MB belegt“ | Textmodelle und die Regel bei Offline-Erkennung ([8.5](#85-bei-offline-erkennung), [9.7](#97-lokales-textmodell-gemma-4)) |

**Wo ist was hin?** (für alle, die von 3.8.x kommen) Text → Diktat und Text → Sprachnachrichten sind jetzt die Seite **Textverbesserung**, Text → Online-Zugang & Modelle heißt **KI-Zugang**, Text → Offline-Erkennung steht in den **Offline-Modellen**, Text → Regeln ohne KI und das Vokabular (bisher Erkennung → Sprache & Kontext) stehen unter **Wörterbuch & Regeln**, und „Leerzeichen nach Diktat anhängen“ steht unter **Knopf & Tastatur** → Einfügen. Die Schalter „Lesbarer glätten“, „Füllwörter intelligent entfernen“ und „Automatische Absätze“ sind in den Seiten der Stufen aufgegangen (siehe „Update von 3.8.x“ in [8.1](#81-diktat)).

### 8.1 Diktat

Einstellungen → **Textverbesserung**, Karte **Beim Diktieren**: die Stufen als Liste. Die Stufe gilt für den Knopf und die Tastatur; in der Tastatur wechselst du sie auch direkt ([5.4](#54-textverbesserung-direkt-in-der-tastatur)).

**Jede Zeile hat zwei Ziele:** Tipp auf den Punkt wählt die Stufe, Tipp auf die Zeile öffnet ihre Einstellungen (erkennbar am **›** hinter dem Namen). Ein Fehltipp auf die Zeile ändert also nichts, und oben auf der Seite kannst du die Stufe ebenfalls verwenden. Mit TalkBack sind es zwei Elemente: die Zeile als Schaltfläche („Glätten, Einstellungen öffnen“) und der Punkt als Optionsfeld („Glätten für Diktat verwenden“). Die Unterzeile nennt die Kurzbeschreibung und dahinter, was vom Standard abweicht, etwa „… · Lesbar“, „… · Ohne Absätze“ oder ein eigenes Modell wie „… · Claude Opus 5.5“.

| Stufe | Was passiert | Auf ihrer Seite |
|---|---|---|
| **Aus** | Nur die festen Regeln, keine zweite Anfrage. | was ohne KI mit dem Text passiert, Weg zu Wörterbuch & Regeln |
| **Glätten** | Zeichensetzung und Groß-/Kleinschreibung, nah an deinen Worten. | Bereinigung, Absätze, Modell |
| **Verschönern** | Formuliert flüssiger und klarer, behält Inhalt, Ton und deine Wörter. | Absätze, Modell |
| **Zusammenfassen** | Kürzt auf das Wesentliche. Namen, Zahlen und Termine bleiben genau. | Form, Modell |
| **Prompt** (Pro) | Formt das Diktat zu einem gegliederten Prompt für ChatGPT, Claude & Co. | Modell |

Unter der Liste steht „Zweite Anfrage · ca. 1–2 s länger · geringe Zusatzkosten“ (gilt für jede Stufe außer „Aus“) und die Zeile **Wörterbuch & Regeln** mit den eingeschalteten festen Regeln ([8.6](#86-feste-regeln)). Für Alltagsdiktate ist **Glätten** die sinnvolle Wahl — der Wortlaut bleibt, nur Kommas, Punkte und Groß-/Kleinschreibung werden richtig gesetzt. „Verschönern" eignet sich für E-Mails aus einem Gedankenstrom: Es formuliert flüssiger, behält aber deinen Ton (locker bleibt locker, förmlich bleibt förmlich), deine Wörter und alle Fakten und wird nicht länger als dein Diktat. „Zusammenfassen" eignet sich für lange Notizen: höchstens halb so lang, Namen, Zahlen, Termine und offene Fragen bleiben genau so, wie du sie gesagt hast, und es bleibt bei „ich" bzw. „wir". Die KI wird ausdrücklich angewiesen, nichts zu übersetzen und nichts zu erfinden.

**Die Seite einer Stufe** zeigt oben die Karte **Beim Diktieren** mit der Kurzbeschreibung und, wenn die Stufe gerade gilt, „Beim Diktat aktiv“ — sonst den Knopf **Für Diktat verwenden**. Darunter steht nur, was für diese Stufe gilt:

- **Glätten → Bereinigung** (eine Auswahl, ab Werk „Nur Zeichensetzung“):
  - **Nur Zeichensetzung** — „Setzt Satzzeichen und Groß-/Kleinschreibung. Die KI streicht kein Wort, das macht nur deine Füllwort-Liste.“ Auch kein „ähm“: Das räumt danach die Füllwort-Liste der festen Regeln auf, samt deiner eigenen Wörter.
  - **Ohne Füllwörter** — „Die KI lässt Füllwörter, Versprecher und Wiederholungen weg, im Zweifel bleibt das Wort.“
  - **Lesbar** — „Repariert auch holprige Sätze und Satzabbrüche. Deine Wörter und dein Ton bleiben.“ Gesprochenes liest sich wörtlich abgetippt oft holprig. Mit „Lesbar“ bleibt Glätten nah am Wortlaut, darf aber Satzanfänge streichen, die du abbrichst und neu beginnst, bei einer Selbstkorrektur („um drei, nee, um halb vier") nur die neue Angabe behalten, lange „und dann … und dann"-Ketten in Sätze teilen und verrutschten Satzbau gerade rücken („weil ich hab keine Zeit" → „weil ich keine Zeit hab"). Was dich ausmacht, bleibt ausdrücklich: deine Wortwahl ohne Synonyme, Kurzformen wie „hab", „nen", „gibt's", kleine Wörter wie „halt", „ja", „mal", „ne?", das Perfekt, Du oder Sie, „ich glaub" und betonte Wiederholungen („echt, echt knapp"). Der Text bleibt ungefähr so lang wie dein Diktat; anders als „Verschönern" formuliert „Lesbar" nichts um. Füllwörter lässt es dabei ebenfalls weg.
- **Glätten und Verschönern → Absätze** (Schalter, ab Werk an): „Die KI gliedert längere Diktate in Absätze. Aus: alles als ein durchgehender Text.“ Ausgeschaltet bekommt die KI die Anweisung, keine Absätze und Zeilenumbrüche zu setzen; liefert das Modell trotzdem Zeilenumbrüche, zieht WhisperLoom sie zu einem Fließtext zusammen. Jede der beiden Stufen hat ihren eigenen Schalter.
- **Zusammenfassen → Form** (eine Auswahl, ab Werk „Automatisch“): **Automatisch** — „Als Liste ab drei Aufgaben, Terminen oder Fragen, sonst in kurzen Absätzen.“ **Fließtext** — „Wenige Sätze am Stück, ohne Liste.“
- **Modell** (alle Stufen außer „Aus“): womit die Stufe rechnet, etwa „Standard · Claude Haiku 5.5“. Ein Tipp öffnet die Auswahl ([8.4](#84-modell-je-stufe)). Darunter: „Gilt beim Diktat und für Sprachnachrichten. Ein neuer Anbieter setzt es auf „Standard“ zurück.“ Ohne Online-Zugang oder ohne Modell im Zugang steht dort stattdessen ein Hinweis mit dem Weg zum **KI-Zugang**.
- **Aus:** „Ohne KI gelten nur die festen Regeln: die Füllwort-Liste mit deinen eigenen Wörtern und die Großschreibung am Satzanfang, je nachdem, was davon eingeschaltet ist.“ Darunter die Zeile **Wörterbuch & Regeln**. Kein Modell, keine Bereinigung.

**Füllwörter — KI und Liste arbeiten zusammen.** Die Bereinigungen „Ohne Füllwörter“ und „Lesbar“ lassen die KI selbst entscheiden, welche Füllwörter, Versprecher und Wiederholungen wegkönnen — sie bekommt dafür den Rohtext samt „äh“, an dem sie Selbstkorrekturen wie „Montag, äh, Dienstag“ erkennt. Danach läuft immer noch die feste Liste „Füllwörter entfernen“ ([8.6](#86-feste-regeln)) als Netz, nach jeder KI-Stufe außer „Prompt“: Bei „Nur Zeichensetzung“ lässt die KI jedes „ähm“ stehen (im eigenen Vergleich mit Claude blieben in 36 von 36 Antworten alle Füllsilben stehen), und kleine Modelle übersehen auch dort viele, wo sie streichen dürften. Die eingebaute Liste enthält nur eindeutige Füllsilben, nach der KI richtet sie also keinen Schaden an. Deine **eigenen Wörter** (etwa „halt“) laufen ebenfalls immer mit, weil du sie bewusst eingetragen hast. „Verschönern“ hat keine eigene Füllwort-Anweisung; die Liste räumt danach auf.

**Fragen und Bitten im Diktat:** Alle Stufen schicken dein Diktat markiert als Text an die KI, mit der Anweisung, ihn nur zu bearbeiten. Diktierst du „Schreib mir eine Einladung für Samstag" oder „Wie spät ist es in New York?", kommt genau dieser Satz bearbeitet zurück, nicht die Einladung oder die Uhrzeit. Liefert ein Modell trotzdem eine Antwort, die weit länger ist als dein Diktat, fügt WhisperLoom den Rohtext mit Hinweis ein. Eine Einleitung wie „Hier ist der geglättete Text:" oder Anführungszeichen um den ganzen Text entfernt WhisperLoom automatisch. Liefert das Modell gar nichts (keine oder eine leere Antwort), kommt der Text ohne KI mit den festen Regeln und dem Hinweis „Textverbesserung übersprungen: Leere Antwort des Modells“.

**Stufe „Prompt“ (Pro-Funktion):** Einstellungen → Erweitert → Karte **Pro-Funktionen** → **Stufe „Prompt“ anbieten** schaltet eine fünfte Stufe frei — in der Tastatur und hier in der Liste. Sie formt ein Diktat zu einem Prompt für einen KI-Assistenten (ChatGPT, Claude, Gemini …). Die Gliederung richtet sich nach dem Diktat:

- eine Frage oder einfache Bitte (unter 15 Wörtern immer): ein bis drei Sätze, ohne Liste,
- mehrere Vorgaben: ein Satz mit dem Auftrag, darunter die Vorgaben als „- “-Liste,
- ein großer Auftrag mit Hintergrund: Abschnitte „Ziel:“, „Hintergrund:“, „Aufgabe:“, „Vorgaben:“, „Format:“ — nur die, zu denen du etwas gesagt hast.

Diktierst du Material mit (eine E-Mail, einen Text), steht es am Ende zwischen Tags wie `<text>` … `</text>` — so trennen die Assistenten Auftrag und Inhalt am zuverlässigsten. Bewusst ohne Markdown (`#`, `**`): liest sich im Eingabefeld roh genauso gut und verleitet den Assistenten nicht zu Markdown-Antworten. Die KI erfindet keine Rolle, Länge oder Zielgruppe dazu, löst Selbstkorrekturen („nee, warte …") auf und bleibt in deiner Sprache. Füllwörter und Groß-Schreibung erledigt hier das Modell; die festen Regeln laufen danach nicht. Beantwortet das Modell deine Bitte, statt sie umzuformulieren (aus „schreib mir ein Gedicht" wird ein Gedicht), und ist die Antwort deutlich länger als dein Diktat, erkennt WhisperLoom das und fügt den Rohtext mit Hinweis ein. Eine kurze Antwort (ein Vierzeiler, „17 mal 23 ist 391") fällt dabei nicht auf — dann hilft nur ein stärkeres Modell, etwa über das Modell der Stufe „Prompt“ ([8.4](#84-modell-je-stufe)). Die Gliederung gehört bei „Prompt“ zum Zweck und bleibt immer erhalten. Wie die anderen Stufen gilt „Prompt", bis du umschaltest; schaltest du die Option aus, gilt wieder „Glätten".

**Update von 3.8.x:** WhisperLoom übernimmt deine bisherigen Schalter, getrennt für Diktat und Sprachnachrichten. War „Lesbarer glätten“ an, steht die Bereinigung auf **Lesbar**; sonst war „Füllwörter intelligent entfernen“ an, dann auf **Ohne Füllwörter** (beim Diktat und bei Sprachnachrichten, der Schalter galt für beides); sonst auf **Nur Zeichensetzung**. „Automatische Absätze“ wird zu den Schaltern **Absätze** bei Glätten und Verschönern und zur **Form** beim Zusammenfassen des Diktats (an = Automatisch, aus = Fließtext). Sprachnachrichten bekommen die Form „Automatisch“. Was du nach dem Update änderst, bleibt.

### 8.2 Sprachnachrichten

Einstellungen → **Textverbesserung**, Karte **Bei geteilten Sprachnachrichten**: „Für Audios, die du per Teilen an WhisperLoom schickst (z. B. aus WhatsApp). Eigene Stufe, unabhängig vom Diktat; bei „Aus" bleibt der Text wortgetreu." Zur Wahl stehen **Aus** (ab Werk, „Wortgetreu, Füllwörter per Schalter ausblendbar. Keine zweite Anfrage.“), **Glätten**, **Verschönern** und **Zusammenfassen** — „Prompt" nicht, das ergibt für eine fremde Nachricht keinen Sinn. Die Zeilen funktionieren wie beim Diktat: Punkt wählt, Zeile öffnet die Seite der Stufe, hier mit **Für Sprachnachrichten verwenden** bzw. „Bei Sprachnachrichten aktiv“.

Die Seiten der Sprachnachrichten haben eigene Werte, unabhängig vom Diktat:

- **Glätten → Bereinigung** wie beim Diktat ([8.1](#81-diktat)), aber nur für geteilte Sprachnachrichten. Du kannst etwa Diktate mit „Nur Zeichensetzung“ glätten und Sprachnachrichten „Lesbar“.
- **Zusammenfassen → Form** (Automatisch oder Fließtext), ebenfalls eigener Wert.
- **Glätten und Verschönern → Absätze:** „Sprachnachrichten werden immer in Absätze gegliedert.“ Einen Schalter gibt es hier nicht. Beim Zusammenfassen entscheidet die Form: „Automatisch“ gliedert, „Fließtext“ gibt je Stück einen Absatz.
- **Modell:** „Wie beim Diktat · …“ — Sprachnachrichten rechnen mit demselben Modell je Stufe wie das Diktat ([8.4](#84-modell-je-stufe)); ein Tipp auf die Zeile öffnet die Seite der Stufe beim Diktat, wo du es änderst.
- **Aus:** „Die Nachricht bleibt wortgetreu, ohne KI und ohne die festen Regeln. Im Fenster blendet der Schalter „Füllwörter ausblenden“ die Wörter der Füllwort-Liste aus.“ Darunter der Weg zu **Wörterbuch & Regeln**, wo du die Liste bearbeitest.

Jedes 5-Minuten-Stück ([6.3](#63-lange-nachrichten)) geht einzeln an die KI — am Stück würde eine lange Nachricht an der Längengrenze des Modells abgeschnitten. Die meisten Sprachnachrichten sind ein Stück; bei längeren gibt „Zusammenfassen" eine Zusammenfassung je Stück. Scheitert ein Stück, zeigt WhisperLoom die ganze Datei ohne KI, mit Hinweis. Nach der KI wirken die festen Regeln wie beim Diktat ([8.6](#86-feste-regeln)). Es gilt derselbe Zugang wie fürs Diktat ([8.3](#83-ki-zugang)); jedes Stück kostet eine zusätzliche Anfrage.

**Update von 3.8.6:** Der eigene Schalter „Lesbarer glätten“ der Sprachnachrichten wird zur Bereinigung **Lesbar**; der Schalter ließ sich in 3.8.6 bei einer anderen Stufe als „Glätten“ nicht abschalten, die Bereinigung ist jetzt jederzeit änderbar.

### 8.3 KI-Zugang

Einstellungen → **KI-Zugang**: oben die Karte **Online-Zugang für die Textverbesserung**, darunter die Übersicht **Modell je Stufe** ([8.4](#84-modell-je-stufe)).

Standardmäßig nutzt die Textverbesserung **Anbieter und Key der Erkennung** (Schalter **Eigenen Zugang verwenden** aus). Das ist bei OpenAI, Groq, Mistral und OpenRouter der einfache Weg: ein Konto, ein Key. Im Feld **Modell** steht dann **Empfehlung je Stufe**: Glätten rechnet mit einem schnellen Modell, Verschönern, Zusammenfassen und Prompt mit einem stärkeren — bei OpenAI „GPT-6 Luna" und „GPT-6 Sol", bei Groq „GPT-OSS 20B" und „GPT-OSS 120B" (alle Anbieter: [7.1](#71-übersicht)). Die Unterzeile nennt beide. Wählst du stattdessen ein Modell aus der Liste oder tippst eins per **Eigenes Modell …** ein, rechnen alle Stufen damit, die kein eigenes Modell haben.

Diese Karte regelt nur die **Online**-Textverbesserung. Bei Offline-Erkennung rechnet ab Werk das lokale Textmodell, ganz ohne Online-Zugang ([9.7](#97-lokales-textmodell-gemma-4)).

**Eigenen Zugang verwenden** einschalten, wenn

- die Erkennung offline läuft und du offline erkannten Text online verbessern lassen willst — mit der Regel „Online, ohne Netz lokal" oder „Überspringen" ([9.7](#97-lokales-textmodell-gemma-4)). „Wie Erkennung" zählt bei Offline-Erkennung nie: Der gespeicherte Online-Zugang der Erkennung bekommt dann keinen Text; der ausgeschaltete Schalter sagt deshalb „Bei Offline-Erkennung geht ohne eigenen Zugang kein Text online." statt „Nutzt Anbieter und Key der Erkennung". Statt der Zugangs-Felder zeigt die Karte je nach Regel, was passiert:
  - **Lokales Textmodell:** „Bei Offline-Erkennung verbessert das lokale Textmodell — dafür brauchst du keinen Online-Zugang." (ohne Knopf)
  - **Online, ohne Netz lokal:** „Ohne eigenen Zugang verbessert bei Offline-Erkennung das lokale Textmodell. Mit eigenem Zugang geht es online, solange Netz da ist." mit **Eigenen Zugang eintragen**
  - **Überspringen** (Warnfarbe): „Ohne eigenen Zugang kommt der Text bei Offline-Erkennung ohne KI. Mit eigenem Zugang wird online verbessert, solange Netz da ist." mit **Eigenen Zugang eintragen**
- dein Erkennungs-Anbieter nur Sprache erkennt (ElevenLabs) — „wie Erkennung" gibt es dort nicht: Die Karte zeigt „ElevenLabs erkennt nur Sprache und bietet keine Textverbesserung." mit **Eigenen Zugang eintragen**, **Zugang prüfen** ist gesperrt. Ist trotzdem eine Stufe gewählt, kommt der Text ohne KI an, mit dem Hinweis „Der Erkennungs-Anbieter kann keinen Text verbessern — unter „KI-Zugang“ einen eigenen Zugang eintragen", oder
- dein Erkennungs-Anbieter keine eingebauten Textmodelle hat (Together AI, DeepInfra) und du kein Modell eintippen willst — „wie Erkennung" geht dort nur mit selbst eingetipptem Modell (**Zugang prüfen** erst dann); ohne kommt der Text ohne KI an, Hinweis „Kein Textmodell eingetragen — unter „KI-Zugang“ ein Modell eintragen", oder
- du ein anderes Sprachmodell willst als beim Erkennungs-Anbieter.

Dann erscheinen eigene Felder: **Anbieter** (OpenAI · Groq · Mistral · OpenRouter · Anthropic (Claude) · Google Gemini · DeepSeek · Ollama (lokal / Homeserver) · Ollama Cloud · Eigener Server), bei Eigener Server die **Base-URL**, der **API-Key** und das **Modell**. Der Key der Erkennung wird dabei nie an den anderen Anbieter geschickt. Beim Eigenen Server ist das Modellfeld ein Freitext („z. B. qwen3:8b"), mit **Modelle vom Server** eine Auswahl aus der Liste deines Servers ([7.6](#76-modelle-vom-server-pro-funktion)).

**Anbieterwechsel setzt die Modelle zurück:** Wählst du einen anderen Anbieter, schaltest „Eigenen Zugang verwenden“ ein oder aus oder wechselst bei „wie Erkennung" den Anbieter der Erkennung, gehen das Modell und die Modelle aller Stufen auf „Standard" zurück. Ein Modell des alten Anbieters kennt der neue meist nicht — bis 3.8.5 blieb es in zwei dieser Fälle stehen, und der Text kam ohne KI. Eine neue Server-Adresse (Ollama) setzt nichts zurück.

**Ollama** gibt es in zwei Varianten, beide nur für die Textverbesserung (Ollama kann keine Spracherkennung — die Erkennung läuft weiter über einen anderen Anbieter oder offline):

- **Ollama (lokal / Homeserver)** — dein eigenes Ollama, z. B. auf einem Rechner zu Hause. Feld **Server-Adresse**: die Adresse mit Port, meist `:11434`, z. B. `http://homeserver:11434` oder `http://192.168.1.10:11434`. Ein angehängtes `/v1` oder `/api` entfernt WhisperLoom selbst. **Kein Key nötig** (das Key-Feld ist optional), Zeitüberschreitung 600 s. `http://` nur im eigenen Netz (LAN/VPN) — sonst warnt das Feld. Hinweis in der App: auf dem Server `OLLAMA_HOST=0.0.0.0` setzen, sonst hört Ollama nur auf sich selbst; das Handy muss den Server erreichen (gleiches WLAN oder VPN wie Tailscale). Der Text verlässt dein Netz nicht. Einrichtung des Servers: [Kapitel 10, Schritt 2](#schritt-2--textverbesserung-starten-optional-ollama).
  **Welches Modell?** Eine Empfehlung je Stufe gibt es hier nicht, weil die Liste von deinem Server kommt. Gemma 4 denkt nicht nach, wenn man es nicht ausdrücklich verlangt, und eignet sich deshalb für Diktate: `gemma4:12b` zum Glätten (schwache Hardware, nur Prozessor mit bis zu 8 GB RAM: `gemma4:e4b`), `gemma4:26b` zum Umformulieren (ein MoE-Modell, das auch auf einem Heimserver ohne Grafikkarte mit 32 GB RAM zügig rechnen soll, nicht gemessen; mit 24-GB-Grafikkarte `gemma4:31b`). Das Modell im Feld **Modell** ist Pflicht und gilt für alle Stufen; schnellere oder stärkere legst du je Stufe fest ([8.4](#84-modell-je-stufe)).
- **Ollama Cloud** — Modelle laufen auf ollama.com (fest `https://ollama.com`), **API-Key nötig** ([7.4](#74-key-besorgen--schritt-für-schritt)). Empfehlung je Stufe: **Gemma 4 31B (empfohlen)** zum Glätten — schnell und ohne „Nachdenken" — und **Mistral Large 3** zum Umformulieren (ebenfalls ohne Nachdenken, gutes Deutsch, Tempo nicht gemessen); zur Auswahl stehen außerdem GLM 5.3 Flash, GPT-OSS 20B und GPT-OSS 120B.

**Modell-Liste vom Server:** Sobald Ollama verbunden ist (Adresse eingetragen, bei der Cloud auch der Key), lädt WhisperLoom die Modelle, die auf dem Server liegen, automatisch ins Auswahlfeld **Modell**. Der Knopf **Modelle vom Server laden** holt die Liste erneut und meldet „n Modelle gefunden" oder den Grund, warum es nicht geklappt hat (z. B. „Der Server hat keine Modelle …"). Beim lokalen Ollama übernimmt WhisperLoom das erste gefundene Modell, solange noch keins gewählt ist. Ein Modell, das nicht in der Liste steht, trägst du über den letzten Eintrag **Eigenes Modell …** per Name ein; beim lokalen Ollama ohne geladene Liste ist das Feld „Modell" ohnehin ein freies Textfeld. Mit der Pro-Funktion **Modelle vom Server** ([7.6](#76-modelle-vom-server-pro-funktion)) öffnet das Feld die Auswahl mit Suche und der Knopf heißt **Modelle aktualisieren**; geladen wird bei Ollama trotzdem bei jedem Öffnen.

**Anbieter nur für Text** — mit Hinweisen, die WhisperLoom direkt an der Auswahl zeigt:

- **Anthropic (Claude)**: gute Textqualität, im eigenen Vergleich die beste ([7.2](#72-empfehlungen)); „OpenAI-Kompatibilitätsschicht — von Anthropic als Test-Werkzeug eingestuft." (funktional, aber offiziell nicht als Dauerlösung gedacht). Claude 5 lehnt `temperature` ab und denkt ab Werk nach; WhisperLoom schickt deshalb kein `temperature` und schaltet das Nachdenken ab, wo das Modell es zulässt (Haiku 5.5, Sonnet). Opus 5.5 denkt immer nach und braucht deshalb länger.
- **Google Gemini**: gratis, aber „Free-Tier: Google darf Inhalte zum Training nutzen."
- **DeepSeek**: „Server in China — Datenschutz beachten." Das Nachdenken ist bei DeepSeek abgeschaltet, sonst dauert jedes Diktat deutlich länger.

Auch hier gibt es **Zugang prüfen** — WhisperLoom schickt dem Modell eine Mini-Anfrage und wertet die Antwort aus. Bei „Empfehlung je Stufe" prüft es das Modell zum Glätten; jedes andere Modell prüfst du in der Auswahl der Stufe mit **Modell prüfen** ([8.4](#84-modell-je-stufe)).

**Ohne Netz wartet nichts:** Vor jeder Online-Textverbesserung prüft WhisperLoom, ob das Telefon gerade Internet hat. Fehlt es, geht keine Anfrage raus, und der Text kommt sofort — ohne KI mit dem Hinweis „Textverbesserung übersprungen: Kein Netz für den Online-Zugang" oder, bei Offline-Erkennung, je nach Regel lokal verbessert ([9.7](#97-lokales-textmodell-gemma-4)). Für Server im eigenen Netz (private Adresse, `*.local`, `*.lan`, `*.home.arpa`, `*.internal`, Tailscale mit `100.x` oder `*.ts.net`) reicht eine WLAN- oder VPN-Verbindung, auch ohne Internet. Ein aktives VPN zählt nur, wenn darunter ein Netz da ist — Android meldet ein VPN sonst auch im Funkloch als verbunden. Nach einer Online-Erkennung reicht jedes verbundene Netz: Die Erkennung ist gerade darüber gelaufen.

### 8.4 Modell je Stufe

Das Modell wählst du auf der Seite der Stufe beim Diktat (Zeile **Modell**, [8.1](#81-diktat)). Es gilt fürs Diktat (Knopf und Tastatur) und für geteilte Sprachnachrichten gemeinsam; deren Seiten zeigen es nur an („Wie beim Diktat · …“). Unter Einstellungen → **KI-Zugang** steht außerdem die Übersicht **Modell je Stufe**: „„Standard“ ist das Modell des Zugangs, bei „Empfehlung je Stufe“ ein schnelles zum Glätten und ein stärkeres zum Umformulieren. Gilt für Diktat und Sprachnachrichten.“ Die Zeilen **Glätten**, **Verschönern**, **Zusammenfassen** und — nur mit der Pro-Funktion „Prompt" ([8.1](#81-diktat)) — **Prompt** zeigen, womit die Stufe rechnet, etwa „Standard · Claude Haiku 5.5" oder ein eigenes Modell wie „Claude Opus 5.5"; ein Tipp führt auf die Seite der Stufe. Darunter: „Ein neuer Anbieter setzt alle Stufen auf „Standard“ zurück."

**Was „Standard" heißt,** bestimmt das Feld **Modell** des Zugangs ([8.3](#83-ki-zugang)):

- **Empfehlung je Stufe** (ab Werk): Glätten — in jeder Bereinigung — rechnet mit der Empfehlung zum Glätten, Verschönern, Zusammenfassen und Prompt mit der zum Umformulieren. Welche Modelle das je Anbieter sind, steht in [7.1](#71-übersicht).
- **ein bestimmtes Modell**: Alle Stufen ohne eigenes Modell rechnen damit.

**Eigenes Modell für eine Stufe:** Ein Tipp auf die Zeile **Modell** öffnet **Modell für …** (z. B. „Modell für Zusammenfassen"): ganz oben **Standard (…)** mit dem Modell, das die Stufe sonst nimmt, darunter die Modelle des Anbieters mit Preis-Notiz, bei Ollama und mit der Pro-Funktion **Modelle vom Server** die Liste vom Server. Unter der Liste steht **Modell prüfen**, ganz unten **Eigenes Modell …** für eine freie ID und **Schließen**. **Modell prüfen** prüft genau das Modell, mit dem die Stufe gerade rechnet — praktisch bei einer frei eingetippten ID, die sonst erst beim Diktat scheitern würde. **Standard** schaltet die Stufe zurück.

Wechselst du die Stufe mit dem Zauberstab der Tastatur, kommt ihr Modell automatisch mit. Alle Bereinigungen von Glätten rechnen mit dem Modell von Glätten. Eigene Anbieter oder Keys je Stufe gibt es nicht: Alle Stufen laufen über den KI-Zugang.

**Wozu?** Ein schnelles, günstiges Modell reicht fürs Glätten; fürs Kürzen und Umformulieren lohnt ein stärkeres. Im eigenen Vergleich mit Claude glättete Haiku 5.5 fast so gut wie Sonnet 5.5, doppelt so schnell und für einen Bruchteil des Preises; beim Zusammenfassen lag Sonnet deutlich vorn ([7.2](#72-empfehlungen)). Wer nur fürs Zusammenfassen das Beste will, stellt dort Claude Opus 5.5 ein und lässt den Rest auf „Standard". Ein stärkeres Modell kostet je Anfrage ein Vielfaches ([7.1](#71-übersicht)).

**Sonderfälle:**

- **Kein Online-Zugang** (Offline-Erkennung ohne eigenen Zugang, ElevenLabs „wie Erkennung"): Statt der Zeilen steht „Ohne Online-Zugang für die Textverbesserung gibt es hier nichts zu wählen.", auf der Seite der Stufe dazu der Weg zum **KI-Zugang**.
- **Kein Modell im Zugang** (Ollama oder Eigener Server mit leerem Feld **Modell**, Together AI oder DeepInfra „wie Erkennung" ohne eingetipptes Modell): Unter KI-Zugang steht „Trag oben zuerst ein Modell ein — es ist der Standard aller Stufen. Danach kannst du hier je Stufe ein anderes wählen.", auf der Seite der Stufe „Trag unter „KI-Zugang“ zuerst ein Modell ein. …“ Ein Modell je Stufe wirkt erst, wenn der Zugang selbst eins hat.
- **Ollama (lokal / Homeserver) und Eigener Server:** Es gibt keine eingebauten Modelle. „Standard" ist das Modell im Feld **Modell**; je Stufe wählst du aus der Liste des Servers (beim Eigenen Server mit der Pro-Funktion **Modelle vom Server**) oder per **Eigenes Modell …**.
- **Lokales Textmodell:** Modell je Stufe gilt nur online. Offline rechnet Gemma 4 alle Stufen selbst ([9.7](#97-lokales-textmodell-gemma-4)).

### 8.5 Bei Offline-Erkennung

Nur auf Geräten, die offline erkennen können, steht auf der Seite **Textverbesserung** ganz unten die Zeile **Bei Offline-Erkennung** mit Regel und Modell („Lokales Textmodell · Gemma 4 E2B“, „Lokales Textmodell · Textmodell fehlt“ oder „Überspringen“). Sie führt zu Einstellungen → **Offline-Modelle**: Dort stehen im Abschnitt **TEXTVERBESSERUNG** die beiden Textmodelle zum Laden und Wählen und darunter die Regel „Textverbesserung bei Offline-Erkennung" mit **Lokales Textmodell** (ab Werk), **Online, ohne Netz lokal** und **Überspringen**. Fehlt das gewählte Textmodell, steht über den Modellen die Warnkarte „Offline ohne Textmodell"; das Banner auf dem Startbildschirm führt direkt auf diese Seite. Alles Weitere zum lokalen Textmodell und zur Regel: [9.7](#97-lokales-textmodell-gemma-4).

### 8.6 Feste Regeln

Einstellungen → **Wörterbuch & Regeln** → Karte **Feste Regeln**. Lokal, kostenlos, ohne KI und nach jeder KI-Stufe außer „Prompt“ ([8.1](#81-diktat)):

- **Füllwörter entfernen (ähm, äh …)** — „Feste Wortliste je Sprache · läuft zuletzt, mit und ohne KI“ (Deutsch: ähm, äh, öhm, ähem, hmm, öh; Englisch: um, uh, uhm, erm, hmm; dazu Listen für Spanisch, Französisch, Italienisch). Ein Komma, das durch das Entfernen direkt vor dem Satzende landen würde, verschwindet mit.
- **Liste bearbeiten** öffnet das Blatt **Füllwörter** (immer bedienbar, die Liste gilt auch fürs Ausblenden im Sprachnachrichten-Fenster): Sprache wählen, unter **Eingebaut** einzelne Standardwörter abwählen (z. B. wenn „hmm" bei dir ein echtes Wort ist), unter **Eigene Wörter** weitere hinzufügen (Feld „Wort hinzufügen", auch Zwei-Wort-Floskeln). **Standard wiederherstellen** setzt beides zurück. Hinweis aus der App: „Nur eindeutige Füllsilben — echte Wörter wie „halt" bleiben, sonst kaputte Sätze."
- **Automatisch groß schreiben** — Satzanfänge groß (kein Title-Case). Nach Abkürzungen wie „z. B.“, „ca.“, „d.h.“, nach Ordnungszahlen („vom 1. bis“) und überhaupt nach Zahl oder Einzelbuchstabe mit Punkt („2021.“, „Plan B.“) schreibt die Regel das nächste Wort nicht groß, es bleibt, wie Erkennung oder KI es liefern. Fällt dort ein groß geschriebenes Füllwort weg („2021. Ähm, dann“), wird das Wort danach groß („2021. Dann“).

**Leerzeichen nach Diktat anhängen** — damit das nächste Diktat oder Tippen nicht am letzten Wort klebt — steht seit 3.9.0 unter Einstellungen → **Knopf & Tastatur** → Karte **Einfügen**.

### 8.7 Wörterbuch und Sprache

Die **Sprache** stellst du unter Einstellungen → **Spracherkennung** ein: Automatisch erkennen · Deutsch (Voreinstellung) · Englisch · Spanisch · Französisch · Italienisch. Eine feste Sprache ist schneller und genauer als „Automatisch erkennen" — vor allem bei kurzen Diktaten; sie bestimmt auch, welche Füllwort-Liste gilt. Bei „Automatisch erkennen" nimmt WhisperLoom die vom Modell erkannte Sprache für die Nachbearbeitung.

Das **Vokabular** steht unter Einstellungen → **Wörterbuch & Regeln** → Karte **Wörterbuch** (bis 3.8.6 unter Erkennung → Sprache & Kontext): Namen, Fachbegriffe und Schreibweisen, die die Erkennung kennen soll. Die Zeile zeigt, was hinterlegt ist (z. B. „3 Begriffe · Datei: namen.md"); **Bearbeiten** öffnet das Blatt **Vokabular**. Die Begriffe gehen als Prompt an den Anbieter und helfen bei Eigennamen und Fachwörtern. Kostet nichts extra — außer bei ElevenLabs (etwa 20 % Aufpreis, siehe unten). Das Vokabular wirkt online **und** offline (dort als Start-Prompt des Modells).

**Eigene Begriffe:** Im Feld **Begriff hinzufügen** einen Begriff eintippen und mit Enter oder Plus übernehmen; mehrere auf einmal mit Komma trennen („Christof Treitges, WhisperLoom, SvelteKit"). Die Liste **Eigene Begriffe (n)** zeigt alle Einträge; ✕ löscht einen einzelnen, **Eigene löschen** alle eigenen. **Fertig** schließt das Blatt. Hattest du in einer älteren Version Text im früheren Feld „Kontext: Namen, Fachbegriffe, Schreibweisen", bleibt er erhalten: Jede seiner Zeilen erscheint als ein Eintrag (ein einzeiliger Text also als genau einer) und wird mitgeschickt.

**Datei (.md oder .txt):** Für große Listen verknüpfst du eine Textdatei — etwa eine Notiz, die du ohnehin pflegst. **Datei verknüpfen** öffnet die Dateiauswahl von Android. Die Datei bleibt **dauerhaft verknüpft** und wird bei **jedem Diktat neu gelesen** — änderst du sie, wirkt das beim nächsten Diktat, ohne neu zu verknüpfen. Danach stehen **Neu lesen**, **Andere Datei** und **Lösen** zur Verfügung; darunter „n Begriffe · werden bei jedem Diktat neu gelesen". Ist die Datei verschoben oder gelöscht, zeigt das Blatt „Datei nicht lesbar — verschoben oder gelöscht? Neu verknüpfen." Ein Diktat bricht deswegen nie ab — es gelten dann nur die eigenen Begriffe.

So liest WhisperLoom die Datei: eine Zeile = ein Begriff; innerhalb einer Zeile trennen zusätzlich Komma und Semikolon. Aufzählungszeichen (`-`, `*`, `+`, `1.`), Checkboxen (`- [ ]`), Zitat-Zeichen (`>`) und Hervorhebungen (`**fett**`, `` `code` ``) werden entfernt; Überschriften (`# …`), Codeblöcke, Trennlinien (`---`) und Leerzeilen übersprungen. Doppelte Begriffe fallen weg (Groß-/Kleinschreibung egal). Gelesen werden höchstens 256 KB. Beispiel:

```markdown
# Namen
- Christof Treitges
- **WhisperLoom**

# Technik
SvelteKit, FastAPI; Tailscale
```

Ergibt fünf Begriffe: Christof Treitges, WhisperLoom, SvelteKit, FastAPI, Tailscale.

**Wie viel mitgeht:** Whisper beachtet nur rund 200 Wörter Kontext. Und zwar die am **Ende** des Kontexts. WhisperLoom stellt deshalb die Begriffe aus der Datei nach vorn und die eigenen ans Ende, mit Komma verbunden und auf **800 Zeichen** gekappt — nie mitten in einem Begriff. Wird es zu lang, fallen zuerst die vorderen Datei-Begriffe weg. Das Blatt sagt, was ankommt: „Alle n Begriffe werden mitgeschickt." bzw. „x von y Begriffen werden mitgeschickt: Whisper beachtet nur rund 200 Wörter Kontext. Eigene Begriffe haben Vorrang vor der Datei." Wichtige Namen gehören also in die eigene Liste oder ans Ende der Datei.

Mistral und OpenRouter nehmen kein Vokabular entgegen — dort wird es nicht mitgeschickt, und unter der Zeile steht der Hinweis „Dieser Anbieter nimmt kein Vokabular entgegen — es wirkt nur bei anderen Anbietern und offline."

**ElevenLabs** bekommt das Vokabular als Schlüsselbegriffe: jeden mitgeschickten Begriff einzeln (nach der Kappung auf 800 Zeichen), höchstens 100 — ab 101 rechnet ElevenLabs jede Anfrage mit mindestens 20 Sekunden ab; es bleiben die letzten, also die eigenen. Begriffe mit mehr als fünf Wörtern, ab 50 Zeichen, mit `< > { } [ ] \` oder mit einem Satzzeichen am Ende (Satzstücke aus altem Freitext, auch das bei der Kappung angeschnittene Stück) lässt WhisperLoom dort weg. Unter der Zeile steht „Geht als Schlüsselbegriffe an ElevenLabs und hilft bei Eigennamen und Fachwörtern. Vokabular kostet bei ElevenLabs etwa 20 % Aufpreis."

---

## 9. Offline-Modus

Im Offline-Modus erkennt WhisperLoom Sprache direkt auf dem Gerät — mit whisper.cpp und einem Whisper-Modell, das du einmalig herunterlädst. Danach braucht die Erkennung kein Internet, keinen Key und schickt nichts weg. Für die Textverbesserung gibt es dazu ein **lokales Textmodell** (Gemma 4), ebenfalls einmalig zu laden ([9.7](#97-lokales-textmodell-gemma-4)).

### 9.1 Voraussetzungen

- **Prozessor:** 64-Bit-ARM (arm64-v8a) mit FP16-Vektorrechnung und DotProd (Armv8.2 — Cortex-A55/A75 und neuer, also praktisch alle Geräte ab etwa 2018). Ältere Chips (Cortex-A53/A72, z. B. Snapdragon 835/660) fehlen diese Befehle; dort meldet WhisperLoom „Offline-Erkennung wird von diesem Gerät nicht unterstützt (CPU ohne FP16/DotProd)" bzw. bietet Offline gar nicht erst an. Diese Geräte wären für die Erkennung ohnehin zu langsam.
- **Arbeitsspeicher:** Für Small etwa 430 MB frei; Geräte mit weniger als etwa 3 GB RAM sind ungeeignet. **Large v3 Turbo** braucht rund 1 GB und wird nur auf Geräten mit mindestens 6 GB angeboten — sonst ist es ausgegraut („Für dieses Gerät zu groß").
- **Speicherplatz:** 32–574 MB je Modell, dauerhaft im App-Speicher.

### 9.2 Die Modelle

Einstellungen → **Offline-Modelle** (oder Schritt 2b im Assistenten). „Modelle werden einmalig von huggingface.co geladen und bleiben auf dem Gerät. Kein Modell ist in der App enthalten." Der Bildschirm hat zwei Abschnitte: **SPRACHERKENNUNG** mit der Umschaltung Online/Offline und den Whisper-Modellen (diese Tabelle) und **TEXTVERBESSERUNG** mit den Gemma-Modellen ([9.7](#97-lokales-textmodell-gemma-4)).

| Modell | Download | Arbeitsspeicher | Einschätzung | Empfehlung |
|---|---|---|---|---|
| **Tiny** | 32 MB | ~250 MB | nur zum Ausprobieren — für Deutsch zu ungenau | — |
| **Base** | 60 MB | ~355 MB | schnell, kurze Sätze | wenn Small zu langsam ist |
| **Small** | 190 MB | ~430 MB | gute Qualität für Deutsch | **Empfohlen** (Voreinstellung) |
| **Large v3 Turbo** | 574 MB | ~1 GB | beste Qualität, langsam, Gerät ab 6 GB RAM | nur für geduldige Nutzer mit starkem Gerät |

Die Modelle sind quantisierte Versionen (q5) der OpenAI-Whisper-Modelle aus dem Repository `huggingface.co/ggerganov/whisper.cpp`; alle sind mehrsprachig. Quelle und Prüfsummen sind fest in der App hinterlegt — eine beschädigte oder veränderte Datei wird abgelehnt.

### 9.3 Laden, auswählen, löschen

- **Laden (190 MB):** Am besten im WLAN. Über mobile Daten fragt WhisperLoom: „Über mobile Daten laden? — 190 MB werden heruntergeladen. Im WLAN ist das kostenlos." → **Laden** oder **Abbrechen**.
- Der Download läuft in einem Hintergrund-Dienst weiter, auch wenn du den Bildschirm verlässt oder WhisperLoom schließt. Fortschritt in der Liste („42 % · 80 MB von 190 MB · 3,1 MB/s") und in der Benachrichtigung **Modell wird geladen** (mit **Abbrechen**). Am Ende: „Small ist bereit".
- **Abgebrochen oder Verbindung weg?** Ein Netzabbruch wird bis zu dreimal automatisch wiederholt und setzt an der Stelle fort, an der es aufhörte. Nach einem Fehler zeigt die Zeile „Fehlgeschlagen: …" mit **Erneut** — auch das setzt den Download fort, nicht neu. Nur ein Abbruch durch dich verwirft die Teildatei.
- Nach dem Download prüft WhisperLoom Größe und SHA-256-Prüfsumme. Stimmt etwas nicht: „Datei beschädigt — erneut laden".
- **Auswählen:** Der Radio-Button links markiert das aktive Modell (nur bei geladenen Modellen wählbar). Ein Wechsel greift beim nächsten Diktat.
- **Löschen:** Papierkorb-Symbol → „Small löschen? — 190 MB werden frei. Für die Offline-Erkennung muss dann ein anderes Modell geladen werden." Ist es das aktive und einzige Modell, warnt der Dialog zusätzlich („Dies ist das aktive Modell — Offline ist danach nicht einsatzbereit."); der Startbildschirm zeigt dann das Banner „Offline gewählt, aber kein Modell geladen."
- Die Zeile „Belegt: … · Frei: …" zeigt, was die Modelle auf dem Gerät belegen — Whisper- und Textmodelle zusammen, samt Zwischenspeicher der Textmodelle.

Unter Spracherkennung → Karte **Offline-Modell** steht das aktive Modell mit **Ändern**. Hinweis dort: „Erste Nutzung lädt das Modell in den Speicher (2–5 s)." Danach bleibt es geladen; erst wenn du die WhisperLoom-Oberfläche öffnest und wieder verlässt oder der Arbeitsspeicher knapp wird, gibt WhisperLoom das Modell frei, und das nächste Diktat lädt es erneut.

### 9.4 Genauigkeit

Die Offline-Erkennung arbeitet fest mit Beam-Search (fünf Kandidaten — derselbe Modus wie die whisper.cpp-Kommandozeile; weniger Abbrüche und Halluzinationen als Greedy). Einen Umschalter auf den schnelleren, etwas ungenaueren Greedy-Modus gibt es in der App nicht; für Small ist der Laufzeit-Unterschied ohnehin klein, weil der Encoder dominiert. Wer Tempo braucht, wählt ein kleineres Modell (Base).

### 9.5 Wie lange dauert es?

Nur eine **grobe Größenordnung** — WhisperLoom wurde auf keinem Gerät vermessen; die Werte sind aus Erfahrungsberichten zu whisper.cpp auf Mittelklasse-ARM-Geräten (4 Threads) für etwa 10 Sekunden Sprache extrapoliert:

| Modell | Größenordnung für 10 s Sprache |
|---|---|
| Tiny | 1–2 s |
| Base | 2–4 s |
| Small | 5–12 s |
| Large v3 Turbo | 20–60 s (sehr unsicher) |

Whisper rechnet immer über ein 30-Sekunden-Fenster — ein 3-Sekunden-Diktat ist also kaum schneller als ein 10-Sekunden-Diktat. Ist dir Small zu langsam, hilft Base; ist die Qualität nicht gut genug, hilft der Online-Dienst.

Was die Qualität offline am meisten verbessert, in dieser Reihenfolge: das größere Modell · eine fest eingestellte Sprache statt „Automatisch erkennen" · das Vokabular mit deinen Eigennamen.

### 9.6 Online und offline kombinieren

Die Umschaltung zwischen **Online-Dienst** und **Offline-Modell** steht unter Spracherkennung ganz oben (und in Offline-Modelle). Beide Zugänge bleiben gespeichert — du kannst also mit Offline im Flugzeug diktieren und zu Hause auf den Online-Dienst zurückwechseln. Wie der Text verbessert wird, hängt vom Erkennungsweg ab: Online erkannter Text geht wie bisher an den KI-Zugang ([8.3](#83-ki-zugang)); der Anbieter der Erkennung und der Anbieter der Textverbesserung dürfen verschieden sein. Offline erkannten Text verbessert ab Werk das lokale Textmodell auf dem Gerät — einen Online-Zugang brauchst du dafür nicht. Ob offline stattdessen online verbessert oder die KI übersprungen wird, stellst du unter Einstellungen → **Offline-Modelle** bei „Textverbesserung bei Offline-Erkennung“ ein ([9.7](#97-lokales-textmodell-gemma-4)).

### 9.7 Lokales Textmodell (Gemma 4)

Erkennst du offline und ist eine KI-Stufe an (fürs Diktat oder für geteilte Sprachnachrichten), verbessert ab Werk ein **lokales Textmodell** den erkannten Text direkt auf dem Gerät. Es gelten dieselben Stufen, Einstellungen der Stufen und Anweisungen wie online ([8.1](#81-diktat)) — nur ohne Netz, ohne Key und ohne Kosten. Der Text verlässt das Handy nicht. Online erkannter Text wird davon unberührt wie bisher online verbessert.

**Die Modelle** stehen unter Einstellungen → **Offline-Modelle** im Abschnitt **TEXTVERBESSERUNG**: „Verbessert offline erkannten Text auf dem Gerät — ohne Netz, der Text verlässt das Handy nicht. Wann es rechnet, stellst du darunter bei „Textverbesserung bei Offline-Erkennung“ ein."

| Modell | Download | dazu beim ersten Start | Arbeitsspeicher beim Rechnen (Google, nur Modell) | Gerät braucht | Einschätzung |
|---|---|---|---|---|---|
| **Gemma 4 E2B** | 2,6 GB | +800 MB | ~1,8 GB | ab 6 GB RAM | **Empfohlen** (Voreinstellung), schneller |
| **Gemma 4 E4B** | 3,7 GB | +1,1 GB (geschätzt) | ~3,3 GB | ab 8 GB RAM | genauer, langsamer |

- **Dazu beim ersten Start:** Beim ersten Laden legt das Modell einen Zwischenspeicher an, der danach das Laden beschleunigt. Bei E2B sind es gemessen 788 MB, bei E4B ist der Wert geschätzt. Die App zeigt beides an („2,6 GB · +800 MB beim ersten Start"), prüft vor dem Download, ob Platz für Modell und Zwischenspeicher ist („Nicht genug Speicherplatz"), und löscht den Zwischenspeicher mit dem Modell.
- **Arbeitsspeicher:** Die Werte in der Tabelle sind Googles Angaben fürs Rechnen auf dem Prozessor, nur fürs Modell (in der Spitze rund 1,8 bzw. 3,3 GB). Der ganze Prozess braucht mehr: In einem eigenen Test auf einem Server-Prozessor belegte er mit E2B in der Spitze 2,8 GB — samt Java-Laufzeit und eingeblendetem Zwischenspeicher. In WhisperLoom bleibt außerdem das Whisper-Modell im selben Prozess geladen, während Gemma rechnet (Small etwa 430 MB, Large v3 Turbo etwa 1 GB); mit E2B kommen so grob 3–4 GB zusammen. Auf einem 6-GB-Gerät ist das gut die Hälfte des Arbeitsspeichers — wird es knapp, helfen Small statt Large v3 Turbo und weniger offene Apps. Unter der Grenze ist das Modell ausgegraut („Für dieses Gerät zu groß"); wie bei Large v3 Turbo zählt ein Gerät, das etwas weniger meldet (10 % Toleranz), noch mit. Die Grenzen gelten für reinen Text mit kurzem Kontext — Googles eigene Beispiel-App verlangt für Bilder, Ton und langen Kontext mehr. Auf einem Handy gemessen ist der Bedarf in WhisperLoom noch nicht. Reicht der freie Speicher nicht, kommt der Text ohne KI mit dem Hinweis „Lokales Textmodell fehlgeschlagen".
- **Voraussetzung** ist ein Gerät, das offline erkennen kann ([9.1](#91-voraussetzungen)). Gemma rechnet auf dem Prozessor (CPU). Die Grafikeinheit nutzt WhisperLoom bewusst nicht, weil sie auf manchen Geräten (Pixel, Exynos, Mali) falsch rechnet.
- **Quelle:** „huggingface.co/litert-community (Gemma 4, Apache 2.0)". Dateiversion, Größe und SHA-256-Prüfsumme sind fest in der App hinterlegt, wie bei den Whisper-Modellen.

**Laden, wählen, löschen** wie bei den Whisper-Modellen ([9.3](#93-laden-auswählen-löschen)): **Laden (2,6 GB)**, Nachfrage über mobile Daten, Download im Hintergrund mit Benachrichtigung, Fortsetzen nach Abbruch, Prüfung von Größe und Prüfsumme. Es lädt immer nur ein Modell zur Zeit. Gemma 4 E2B ist ab Werk gewählt. Anders als bei den Whisper-Modellen lädt ein Tipp auf die Zeile eines noch nicht geladenen Textmodells es gleich (wie **Laden**); ist ein Modell zu groß fürs Gerät, bleibt die Zeile ausgegraut („Für dieses Gerät zu groß"). Ist der Download fertig, ist das geladene Modell gewählt — bis dahin bleibt ein schon geladenes aktiv; ist keins geladen, gilt das ladende sofort als gewählt. Ein geladenes Modell wählt ein Tipp direkt. Läuft gerade ein anderer Download, steht an der Zeile „Laden geht, sobald der laufende Download fertig ist." Brichst du einen Download ab, gilt wieder ein geladenes Textmodell, sonst Gemma 4 E2B. Löschst du das gewählte Textmodell, während ein anderes geladen ist, ist danach das andere gewählt. **Löschen** fragt „Gemma 4 E2B löschen? — 2,6 GB Modell und 788 MB Zwischenspeicher werden frei. Für die lokale Textverbesserung muss dann wieder ein Textmodell geladen werden." und nimmt den Zwischenspeicher mit; war das Modell noch nie geladen, gibt es keinen Zwischenspeicher, und der Dialog nennt nur die 2,6 GB. Ist es das gewählte und einzige geladene Textmodell, steht dabei „Dies ist das aktive Textmodell — offline kommt der Text danach ohne KI."

**Im Arbeitsspeicher:** Das Modell wird geladen, sobald es gebraucht wird. Damit das nicht erst nach der Erkennung beginnt, startet WhisperLoom das Laden schon mit der Aufnahme (Knopf, Tastatur, Pro Widget). Vorgewärmt bleibt es bis zur Verbesserung geladen, auch wenn Aufnahme und Erkennung länger dauern (höchstens 10 Minuten). Nach 2 Minuten ohne Diktat, bei knappem Speicher, beim Löschen und beim Wechsel des Modells gibt WhisperLoom es wieder frei.

**Die Regel** steht unter Einstellungen → **Offline-Modelle** unter den Textmodellen (nur auf Geräten, die offline erkennen können), Karte „Textverbesserung bei Offline-Erkennung"; die Seite Textverbesserung führt mit der Zeile **Bei Offline-Erkennung** hin ([8.5](#85-bei-offline-erkennung)):

- **Lokales Textmodell** — „Standard — offline erkannter Text wird auf dem Gerät verbessert, nie online."
- **Online, ohne Netz lokal** — „Mit eigenem Online-Zugang und Netz online. Ohne Netz, ohne eigenen Zugang oder bei einem Fehler lokal."
- **Überspringen** — „Kein lokales Modell. Mit eigenem Online-Zugang und Netz online, sonst kommt der Text ohne KI."

Ein Textmodell zu laden ändert die Regel nicht; die wählst du hier. Passt kein Textmodell ins Gerät (unter 6 GB RAM), sind beide Textmodelle ausgegraut („Für dieses Gerät zu groß“), über der Regel steht „Für ein lokales Textmodell braucht das Gerät mindestens 6 GB RAM.“, „Lokales Textmodell" und „Online, ohne Netz lokal" sind gesperrt, und die Regel wirkt wie „Überspringen". Stehen die Stufen fürs Diktat und für geteilte Sprachnachrichten beide auf „Aus", wirkt die Regel nicht („Braucht eine Stufe über „Aus“.“). „Eigener Online-Zugang" heißt: Unter KI-Zugang → „Online-Zugang für die Textverbesserung" ist **Eigenen Zugang verwenden** an und vollständig eingetragen ([8.3](#83-ki-zugang)). „Wie Erkennung" zählt bei Offline-Erkennung nie.

**Was passiert wann** — bei Offline-Erkennung und einer KI-Stufe über „Aus":

| Lage | Lokales Textmodell | Online, ohne Netz lokal | Überspringen |
|---|---|---|---|
| Eigener Online-Zugang, Netz da | lokal | online; scheitert die Anfrage, lokal mit Hinweis „Online-Textverbesserung fehlgeschlagen — lokal verbessert" | online; scheitert die Anfrage, ohne KI mit Hinweis |
| Eigener Online-Zugang, kein Netz | lokal | lokal, es geht keine Anfrage raus | sofort ohne KI, Hinweis „Kein Netz für den Online-Zugang" |
| Kein eigener Online-Zugang | lokal | lokal | ohne KI, ohne Hinweis (so gewählt) |
| Textmodell nicht geladen | sofort ohne KI, Hinweis „Kein Textmodell geladen …" | mit Zugang und Netz online; sonst sofort ohne KI, Hinweis „Kein Textmodell geladen …" | wie oben, braucht kein Textmodell |

„Ohne KI" heißt: der erkannte Text mit den festen Regeln ([8.6](#86-feste-regeln)). Der Hinweis erscheint am Knopf als kurze Meldung, in der Tastatur in der Statuszeile und bei geteilten Sprachnachrichten unter dem Text, z. B. „Textverbesserung übersprungen: Kein Textmodell geladen — unter „Offline-Modelle“ laden oder „Überspringen“ wählen“.

**Offline ohne Textmodell.** Erkennst du offline, ist eine KI-Stufe an, steht die Regel auf „Lokales Textmodell" oder „Online, ohne Netz lokal" und ist das gewählte Textmodell nicht geladen (oder zu groß fürs Gerät), zeigt WhisperLoom an drei Stellen dieselbe Warnkarte: unter **Spracherkennung**, in **Offline-Modelle** (Abschnitt Textverbesserung) und im Assistenten (Schritt 2b). Sie lautet „Offline ohne Textmodell — Die Textverbesserung braucht offline ein lokales Modell: Gemma 4 E2B (2,6 GB · +800 MB beim ersten Start). Lade es oder überspringe sie — dann kommt der Text ohne KI." Mit eigenem Online-Zugang sagt sie, was dann wirklich passiert: bei „Lokales Textmodell" „… Lade es oder überspringe sie — dann verbessert mit Netz dein Online-Zugang, sonst kommt der Text ohne KI.", bei „Online, ohne Netz lokal" „Mit Netz verbessert dein Online-Zugang, ohne Netz ein lokales Modell: … Lade es oder überspringe es — dann kommt der Text ohne Netz ohne KI." Zwei Knöpfe:

- **Textmodell laden (2,6 GB)** lädt das gewählte Modell (passt es nicht ins Gerät, Gemma 4 E2B) und wählt es aus. Die Karte zeigt dann den Fortschritt.
- **Überspringen** stellt die Regel auf „Überspringen".

Wo die Textmodelle direkt darunter stehen (Offline-Modelle, Assistent), hat die Karte nur **Überspringen**: Geladen wird an der Zeile des Modells, die auch Fortschritt und Fehler zeigt, und solange das gewählte Modell lädt, fehlt die Karte dort.

Bis dahin kommt der Text offline **sofort** ohne KI — WhisperLoom wartet nie auf ein fehlendes Modell. Ausnahme „Online, ohne Netz lokal" mit eigenem Online-Zugang: Mit Netz geht der Text weiter an diesen Zugang, nur ohne Netz kommt er ohne KI. Der Startbildschirm zeigt die Zeile Textverbesserung in Warnfarbe („Glätten · Textmodell fehlt — Text ohne KI", bei der Ausnahme „Glätten · GPT-6 Luna · ohne Netz ohne KI") und das Banner „Offline ohne Textmodell — der Text kommt ohne KI." (bei der Ausnahme „… — ohne Netz kommt der Text ohne KI.") mit **Beheben** (führt zu den Offline-Modellen). In der Tastatur sind die KI-Stufen ausgegraut („Offline ohne Textmodell — tippe zum Laden").

**Update von 3.8.x:** Umgestellt wird nichts, die Regel steht für alle auf „Lokales Textmodell". Wer schon offline mit einer KI-Stufe diktiert, sieht deshalb die Warnkarte, bis er das Modell lädt oder „Überspringen" wählt. Auf Geräten unter 6 GB RAM passt kein Textmodell: Dort wirkt die Regel von selbst wie „Überspringen" — ohne Warnkarte, ohne Banner, und die Tastatur bietet kein Laden an. Mit „Überspringen" und eigenem Online-Zugang bleibt alles wie bisher: Online verbessert wird, solange Netz da ist — ohne Netz kommt der Text jetzt sofort statt nach einer Zeitüberschreitung.

**Anzeige:** Auf dem Startbildschirm steht bei Offline-Erkennung in der Zeile Textverbesserung, was passiert: „Glätten · lokal · Gemma 4 E2B", „Glätten · GPT-6 Luna · ohne Netz lokal", „Glätten · offline übersprungen" oder, bei „Überspringen" mit eigenem Zugang, das Online-Modell wie gewohnt. Ist nur die Stufe für geteilte Sprachnachrichten an und fehlt das Textmodell, steht dort in Warnfarbe „Aus · geteilte Sprachnachrichten: Textmodell fehlt" — passend zum Banner. Im Einstellungs-Hub hängt die Zeile **Textverbesserung** bei Offline-Erkennung mit KI-Stufe die Kurzform der Regel an: „lokal bei Offline", „online, ohne Netz lokal" oder „ohne lokales Modell"; auf der Seite Textverbesserung nennt die Zeile **Bei Offline-Erkennung** Regel und Modell („Lokales Textmodell · Gemma 4 E2B"). Die Zeile **Offline-Modelle** zählt Whisper- und Textmodelle zusammen.

**Ausweg: ohne KI einfügen.** Läuft die Textverbesserung — online oder lokal —, zeigt die Tastatur „Text wird verbessert … antippen = ohne KI einfügen" und der Knopf „verbessert … tippen = ohne KI". Ein Tipp auf die Statuszeile, das Mikrofon oder den Knopf fügt den erkannten Text sofort ohne KI ein; die laufende Verbesserung wird verworfen. Das hilft, wenn ein langes Diktat auf einem langsamen Gerät dauert.

**Wie lange dauert es?** Nur grobe Anhaltspunkte — auf einem Handy ist die lokale Textverbesserung noch nicht vermessen:

| | Gemma 4 E2B | Gemma 4 E4B |
|---|---|---|
| Diktat von etwa einer Minute, Spitzengerät | etwa 5–8 s | etwa 15–20 s |
| Diktat von etwa einer Minute, Mittelklasse | etwa 20–45 s (sehr unsicher) | zwei- bis dreimal so lang |
| Erstes Laden überhaupt (baut den Zwischenspeicher) | einige Sekunden bis etwa 30 s | bis etwa 40 s berichtet |

Hochgerechnet aus Googles Messung auf einem Galaxy S26 Ultra (Modellkarten unter huggingface.co/litert-community: E2B rund 47, E4B rund 18 Textbausteine pro Sekunde, erste Ausgabe nach 1,8 bzw. 5,3 s) und aus einem eigenen Test auf einem Server-Prozessor mit 4 Kernen (E2B: 4–8,5 s je Verbesserung kurzer bis mittlerer Diktate, erstes Laden 6 s, danach 1 s). Für Mittelklasse-Handys gibt es mit dieser Technik keine Messung; die Spanne ist eine Schätzung. Kurze Diktate gehen entsprechend schneller, langsamer als online ist es fast immer.

**Qualität:** Gemma 4 E2B ist ein kleines Modell. Im Test hat es diktierte Bitten („Schreib mir eine Einladung …") wie gewünscht bearbeitet statt ausgeführt, und Kürzen und Verschönern waren brauchbar. Bei „Glätten" schreibt es Kurzformen manchmal aus („hab" → „habe"). E4B ist größer und soll genauer sein, ist aber nicht eigens getestet. Wer online bessere Texte will, nimmt „Online, ohne Netz lokal" mit eigenem Online-Zugang.

**Geteilte Sprachnachrichten und Pro Widgets** folgen derselben Regel. Bei geteilten Nachrichten wird jedes 5-Minuten-Stück einzeln lokal verbessert („Text wird lokal verbessert …") — lange Nachrichten dauern dann entsprechend.

---

## 10. Eigener Server

WhisperLoom spricht die OpenAI-API. Jeder Server, der `POST /v1/audio/transcriptions` (und optional `POST /v1/chat/completions`) anbietet, funktioniert — also auch ein Rechner bei dir zu Hause oder dein VPS. Das Audio verlässt dann nie deine eigene Infrastruktur.

**Du brauchst:** einen Linux-Rechner/VPS mit Docker (x86-64 oder ARM64, ≥ 4 Kerne, ≥ 8 GB RAM; für die Textverbesserung zusätzlich ≈ 6 GB) und eine Verbindung vom Handy dorthin (gleiches WLAN, Tailscale/WireGuard oder HTTPS über Caddy).

Auf CPU-Servern ist die Erkennung deutlich langsamer als bei den Cloud-Anbietern (ein 20-Sekunden-Diktat kann mit dem Modell „medium" 20–40 Sekunden dauern; Schätzwerte, nicht gemessen). WhisperLoom rechnet damit: Beim Anbieter „Eigener Server" gilt eine Zeitüberschreitung von **600 Sekunden** statt 90.

### Schritt 1 — Spracherkennung starten (speaches)

```bash
docker run -d --name speaches --restart unless-stopped \
  -p 8000:8000 \
  -v hf-hub-cache:/home/ubuntu/.cache/huggingface/hub \
  -e WHISPER__COMPUTE_TYPE=int8 \
  -e WHISPER__CPU_THREADS=4 \
  -e ENABLE_UI=false \
  -e API_KEY=mein-geheimer-schluessel \
  ghcr.io/speaches-ai/speaches:latest-cpu

# Modell einmalig herunterladen (medium ≈ 1,5 GB; Alternative: Systran/faster-whisper-large-v3)
curl -X POST -H "Authorization: Bearer mein-geheimer-schluessel" \
  http://localhost:8000/v1/models/Systran/faster-whisper-medium
```

`API_KEY` weglassen, wenn der Server nur im eigenen Netz erreichbar ist — dann bleibt das Feld „API-Key (optional)" in WhisperLoom leer. WhisperLoom schickt bei leerem Key gar keinen Authorization-Header (manche Server werten ein leeres Token als ungültig).

*Alternative (noch kleiner): whisper.cpp*

```bash
git clone https://github.com/ggml-org/whisper.cpp && cd whisper.cpp
cmake -B build && cmake --build build -j --config Release
./models/download-ggml-model.sh large-v3-turbo-q5_0      # 547 MiB; oder: medium
./build/bin/whisper-server -m models/ggml-large-v3-turbo-q5_0.bin \
  --host 0.0.0.0 --port 8080 -t 4 -l auto \
  --inference-path /v1/audio/transcriptions
```

Wichtig: `-l auto` (oder `-l de`), sonst nimmt der Server Englisch an, wenn die App bei „Automatisch erkennen" keine Sprache mitschickt. whisper-server hat **keine** Passwortabfrage — nur im LAN/VPN betreiben oder hinter Caddy (Schritt 3). Das Feld „Modell" ignoriert whisper-server; WhisperLoom schickt 16-kHz-Mono-WAV, ein `--convert`/ffmpeg ist nicht nötig.

Weitere passende Server: **LocalAI** (`/v1/audio/transcriptions`, Modellname `whisper-1`, Key per `LOCALAI_API_KEY` optional) und **hwdsl2/whisper-server** (faster-whisper, erzeugt bei Neuinstallation mit Volume automatisch einen Key — im Container-Log nachsehen). Nicht kompatibel ist `openai-whisper-asr-webservice` (anderes Schema).

### Schritt 2 — Textverbesserung starten (optional, Ollama)

```bash
curl -fsSL https://ollama.com/install.sh | sh
sudo systemctl edit ollama            # Drop-in anlegen:
#   [Service]
#   Environment="OLLAMA_HOST=0.0.0.0:11434"
sudo systemctl daemon-reload && sudo systemctl restart ollama
ollama pull qwen3:8b                  # 5,2 GB; schneller: ollama pull gemma3:4b
```

Ollama braucht keinen Key. Auf 4 CPU-Kernen liefert ein 8B-Modell ≈ 5–8 Wörter pro Sekunde — die Textverbesserung eines längeren Diktats dauert also spürbar; bei Bedarf `gemma3:4b` nehmen oder die Stufe auf „Aus" lassen. WhisperLoom schaltet beim Eigenen Server das „Nachdenken" von Qwen3 & Co. automatisch ab (`reasoning_effort: none`) und entfernt trotzdem versehentlich mitgelieferte Denk-Blöcke aus der Antwort. Beim Anbieter **Ollama (lokal / Homeserver)** spricht WhisperLoom die native Ollama-API (`/api/chat`); die liefert das „Nachdenken" getrennt vom eigentlichen Text, es landet also nie im Diktat.

`OLLAMA_HOST=0.0.0.0` ist nötig, damit das Handy den Server erreicht — ohne die Einstellung hört Ollama nur auf sich selbst (localhost).

### Schritt 3 — Von außen erreichbar machen

**Variante A: Tailscale (empfohlen, kein offener Port).** Tailscale auf Server und Handy installieren, beide im selben Tailnet. Dann in WhisperLoom `http://100.x.y.z:8000/v1` eintragen (die Tailscale-IP des Servers). Mit gültigem Zertifikat: `sudo tailscale serve --bg --https=443 localhost:8000` → `https://<server>.<tailnet>.ts.net/v1` (MagicDNS und HTTPS-Zertifikate im Tailscale-Admin aktivieren).

**Variante B: Caddy mit TLS + Bearer-Token** (wenn der Server ohnehin öffentlich ist, z. B. VPS mit Domain):

```caddyfile
whisper.example.de {
	@unauth not header Authorization "Bearer {env.WHISPERLOOM_TOKEN}"
	respond @unauth "Unauthorized" 401

	handle /v1/audio/* {
		reverse_proxy 127.0.0.1:8000        # speaches (oder 8080 für whisper-server mit --inference-path)
	}
	handle /v1/chat/* {
		reverse_proxy 127.0.0.1:11434       # Ollama
	}
	respond 404
}
```

`WHISPERLOOM_TOKEN` als Umgebungsvariable des Caddy-Dienstes setzen (nicht in die Datei schreiben). In WhisperLoom: Base-URL `https://whisper.example.de/v1`, API-Key = Token. Vorteil: Erkennung und Textverbesserung laufen hinter **einer** URL — in WhisperLoom bleibt „Eigenen Zugang verwenden" dann einfach aus, und die Textverbesserung nutzt automatisch denselben Zugang. Diese Variante nutzt für die Textverbesserung den OpenAI-kompatiblen Weg über `/v1/chat/*`. Willst du hinter Caddy stattdessen den Anbieter **Ollama (lokal / Homeserver)** verwenden, braucht es zusätzlich einen `handle /api/*`-Block zum Ollama-Port (die App ruft dort `/api/chat` und `/api/tags` auf); dann „Eigenen Zugang verwenden" einschalten und als Server-Adresse `https://whisper.example.de` mit dem Token als Key eintragen.

### Schritt 4 — Testen (vom Rechner aus)

```bash
# 3-Sekunden-Test-WAV (16 kHz mono) erzeugen
ffmpeg -f lavfi -i "sine=frequency=440:duration=3" -ar 16000 -ac 1 test.wav

curl -s http://SERVER:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer mein-geheimer-schluessel" \
  -F file=@test.wav -F model=Systran/faster-whisper-medium \
  -F language=de -F response_format=json
# → {"text":"…"}

curl -s http://SERVER:11434/v1/chat/completions -H "Content-Type: application/json" -d '{
  "model":"qwen3:8b","temperature":0,"reasoning_effort":"none",
  "messages":[{"role":"system","content":"Korrigiere nur Zeichensetzung."},
              {"role":"user","content":"hallo das ist ein test ohne punkt und komma"}]}'

curl -s http://SERVER:11434/api/tags
# → Liste der Ollama-Modelle, die WhisperLoom im Auswahlfeld „Modell" anbietet
```

Bei Fehler: `docker logs speaches` bzw. `journalctl -u ollama`.

### Schritt 5 — In WhisperLoom eintragen

**Spracherkennung** (Einstellungen → Spracherkennung, oder Schritt 2a im Assistenten) → Anbieter **Eigener Server**:

| Feld | Wert |
|---|---|
| Base-URL | `http://SERVER:8000/v1` (LAN/Tailscale) oder `https://whisper.example.de/v1` (Caddy). „Muss auf /v1 enden. http:// nur im eigenen Netz (LAN/VPN)." |
| API-Key (optional) | leer, oder der in `API_KEY`/Caddy gesetzte Token |
| Modell | `Systran/faster-whisper-medium` (speaches) · `whisper-1` (LocalAI/hwdsl2) · beliebig (whisper.cpp) — per **Eigenes Modell …** eintragen |
| Zeitüberschreitung | fest 600 s beim Eigenen Server (Cloud-Anbieter 90 s) — CPU-Server brauchen bei langen Aufnahmen Minuten; nicht einstellbar |

Dann **Zugang prüfen**.

**Textverbesserung** (Einstellungen → KI-Zugang): Läuft Ollama auf einem eigenen Port (Variante ohne Caddy), Schalter **Eigenen Zugang verwenden** ein → Anbieter **Ollama (lokal / Homeserver)** (empfohlen) → **Server-Adresse** `http://SERVER:11434` (ohne `/v1`), Key leer. Die Modelle deines Servers lädt WhisperLoom dann automatisch ins Auswahlfeld **Modell** (sonst per Knopf **Modelle vom Server laden**, mit Pro **Modelle aktualisieren**); `qwen3:8b` auswählen. Der bisherige Weg funktioniert weiter: Anbieter **Eigener Server** → Base-URL `http://SERVER:11434/v1`, Key leer, Modell `qwen3:8b`. Hinter Caddy mit einer gemeinsamen URL bleibt der Schalter aus.

**http oder https?** Unverschlüsseltes `http://` akzeptiert WhisperLoom ohne Warnung nur zu privaten Adressen (192.168.x.x, 10.x.x.x, 172.16–31.x.x, 100.64–127.x.x/Tailscale, `localhost`, `*.local`, `*.lan`, `*.home.arpa`, `*.internal`). Bei einer öffentlichen Adresse warnt das Feld „Unverschlüsselt über das Internet — https:// oder VPN (Tailscale) verwenden". Über das Internet immer `https://` oder VPN. Die Cloud-Anbieter aus der Liste sind fest auf https eingestellt.

---

## 11. Verlauf

Seit 3.9.0 merkt sich WhisperLoom deine Diktate: Was du mit der **Diktat-Tastatur** oder dem **schwebenden Knopf** diktierst, landet im **Verlauf** — mit dem Rohtext der Erkennung und der Fassung, die damals eingefügt wurde. So findest du ein Diktat wieder, das im falschen Feld gelandet ist, kopierst es noch einmal oder lässt es nachträglich anders verbessern, ohne neu zu sprechen.

Der Verlauf ist **ab Werk an**, mit höchstens 50 Einträgen — auch nach dem Update. Er liegt nur auf dem Gerät ([12](#12-datenschutz)). Abschalten: [11.4](#114-verlauf-einstellungen).

**Was nicht in den Verlauf kommt:** keine Aufnahmen (Audio wird nie gespeichert), keine leeren Erkennungen, nicht die Ziel-App. Geteilte Sprachnachrichten und Pro Widgets zeichnen nicht auf. Private Felder erkennen Tastatur und Knopf unterschiedlich gut:

- **Diktat-Tastatur:** nichts aus Passwortfeldern und aus Feldern, die keine Vorschläge lernen dürfen (Inkognito-Modus des Browsers u. Ä.). Beides meldet das Feld der Tastatur selbst.
- **Schwebender Knopf:** nichts aus Feldern, die die Bedienungshilfe als Passwortfeld meldet. Ein Inkognito-Feld kann die Bedienungshilfe nicht erkennen, dessen Text kommt also in den Verlauf. Ohne Bedienungshilfe kennt der Knopf das Zielfeld gar nicht: Der Text kommt dann in die Zwischenablage und in den Verlauf, auch aus einem Passwortfeld.

Wer das nicht will, diktiert in solche Felder mit der Diktat-Tastatur oder schaltet den Verlauf aus ([11.4](#114-verlauf-einstellungen)).

### 11.1 Die Liste

Zwei Einstiege führen zur Liste: das **Verlauf-Symbol** oben rechts auf dem Startbildschirm (neben **?** und **⚙**) und Einstellungen → Gruppe **VERLAUF** → **Verlauf** (Unterzeile „An · 12 von 50“ bzw. „Aus“).

- Die Einträge sind nach Tagen gruppiert (**Heute**, **Gestern**, Datum), die neuesten oben.
- Jede Zeile zeigt den **Ursprung** — den Rohtext der Erkennung — in bis zu drei Zeilen, darunter Uhrzeit · Quelle (Tastatur oder Knopf) · Dauer. Rechts steht die Stufe von damals; ist die KI gescheitert, „ohne KI“.
- **Tippen** öffnet den Eintrag. **Wischen** löscht ihn, „Eintrag gelöscht“ mit **Rückgängig**; mit TalkBack über die Aktion „Löschen“.
- **⋮** oben rechts: **Verlauf-Einstellungen** ([11.4](#114-verlauf-einstellungen)) und **Alle löschen** (mit Rückfrage „Alle Einträge löschen?“).
- Leer steht dort „Noch keine Diktate“; ist der Verlauf aus, „Verlauf ist aus · Diktate werden nicht gespeichert.“ mit **Einschalten**.

### 11.2 Ein Eintrag

Der Eintrag ist aufgebaut wie das Fenster für Sprachnachrichten: oben eine Kopfkarte („Heute, 14:32“ · „Tastatur · 0:41 · 86 Wörter“), darunter Chips für den **Ursprung** und jede vorhandene Fassung (etwa „Glätten · Lesbar“, „Zusammenfassen“, „Bearbeitet“), dann der Text — markierbar — und eine Hinweiszeile, womit er entstanden ist, z. B. „Zusammenfassen · Claude Sonnet 5.5“, „Glätten · ohne KI: Kein Netz für den Online-Zugang“ oder „Ohne KI · nur die festen Regeln“. Der Eintrag öffnet auf der Fassung, die damals eingefügt wurde. Beim Ursprung gibt es wie im Sprachnachrichten-Fenster den Schalter **Füllwörter ausblenden**.

Unten: **Kopieren** nimmt die gerade sichtbare Fassung (bis Android 12 mit „In die Zwischenablage kopiert“, ab Android 13 bestätigt das System selbst), **Andere Stufe …** rechnet eine weitere Fassung ([11.3](#113-andere-stufe)). Oben **✎** öffnet **Bearbeiten**, **⋮** bietet **Teilen** und **Löschen**.

**Bearbeiten** ist ein Vollbild mit **✕**, dem Titel „Bearbeiten“ und **Speichern**. Darüber steht, welche Fassung du bearbeitest („Fassung: Glätten · Lesbar“), daneben Rückgängig und Wiederholen; unten **Kopieren** und **Teilen**. Speichern ersetzt die Fassung und markiert sie als bearbeitet. Bearbeitest du den Ursprung, entsteht eine eigene Fassung „Bearbeitet“ — der Rohtext der Erkennung bleibt immer erhalten. Schließt du mit ungesicherten Änderungen, fragt WhisperLoom „Änderungen verwerfen?“.

### 11.3 Andere Stufe

**Andere Stufe …** öffnet eine Auswahl mit den Stufen der Einstellungen: oben **Glätten** mit den drei Bereinigungen, darunter **Weitere Stufen** (Verschönern, Zusammenfassen und mit Pro „Prompt“). Vorhandene Fassungen tragen „vorhanden“. „Aus dem Ursprung mit deinen aktuellen Einstellungen: eine Text-Anfrage, keine neue Erkennung. Deine eingestellte Stufe bleibt.“

- Gerechnet wird immer aus dem Ursprung, mit den **aktuellen Einstellungen fürs Diktat**: KI-Zugang und Modell je Stufe, Absätze und Form, bei Offline-Erkennung die Regel aus [9.7](#97-lokales-textmodell-gemma-4). Online geht dafür der Rohtext an deinen KI-Zugang — eine Anfrage, wie beim Diktat.
- Während der Rechnung zeigt der neue Chip eine Ladeanzeige; die Rechnung läuft weiter, auch wenn du das Handy drehst. Danach erscheint die Fassung als Chip.
- Klappt es nicht (kein Netz, kein Zugang, Fehler beim Anbieter), bleibt der Eintrag unverändert, und eine kurze Meldung unten am Bildschirm (Snackbar) sagt warum („… · Eintrag unverändert“). Hast du den Eintrag inzwischen verlassen, kommt sie beim nächsten Öffnen. Vor dem Neu-Verarbeiten prüft WhisperLoom das Netz streng, damit nichts in einem WLAN mit Anmeldeseite hängen bleibt.
- Dieselbe Stufe noch einmal ersetzt ihre Fassung; war sie bearbeitet, fragt WhisperLoom vorher „Bearbeitete Fassung ersetzen?“. Solange eine Fassung neu gerechnet wird, ist ✎ für sie gesperrt.

### 11.4 Verlauf-Einstellungen

Im Verlauf → **⋮** → **Verlauf-Einstellungen**:

- **Verlauf speichern** — „Diktate aus Tastatur und Knopf. Ausgenommen: bei der Tastatur Passwort- und Inkognito-Felder, beim Knopf Passwortfelder, wenn die Bedienungshilfe an ist.“ Ausschalten fragt „Verlauf ausschalten? — Alle Einträge werden gelöscht, neue Diktate nicht mehr gespeichert.“ und löscht dann alles.
- **Größe** — 10, 25, **50** (ab Werk), 100, 250 oder 500 Einträge. Ist der Verlauf voll, fällt beim nächsten Diktat der älteste Eintrag weg. Verkleinern fragt „Verlauf verkleinern? — Die 12 ältesten Einträge werden gelöscht.“ und kürzt sofort. Ein Eintrag braucht grob geschätzt 2–5 KB, 500 Einträge also etwa 1–2,5 MB.
- **Alle löschen** (mit Rückfrage).
- Hinweis: „Der Verlauf liegt nur auf diesem Gerät und kommt nie in ein Backup. Audio wird nicht gespeichert.“

**Text bei verschwundenem Feld:** War das Eingabefeld beim Einfügen weg (App gewechselt, Tastatur zu), ging der fertige Text der Diktat-Tastatur bis 3.8.6 still verloren. Jetzt liegt er im Verlauf, und die Statuszeile der Tastatur sagt bis zum nächsten Diktat „Feld nicht mehr da — Text liegt im Verlauf“. Ist der Verlauf aus, kommt er in die Zwischenablage („Feld nicht mehr da — Text in der Zwischenablage“). Aus einem Passwort- oder Inkognito-Feld hebt WhisperLoom ihn nirgends auf, die Zeile sagt dann „Feld nicht mehr da — Text nicht eingefügt“. Den Erfolgs-Ring gibt es nur, wenn der Text im Feld oder im Verlauf liegt.

---

## 12. Datenschutz

WhisperLoom hat keinen eigenen Server, kein Konto, keine Telemetrie. Was mit deinen Daten passiert, hängt allein vom gewählten Erkennungsweg und von der Textverbesserung ab:

**Online:** Audio und Vokabular gehen an den gewählten Anbieter. Bei Textverbesserung geht der erkannte Text an das Sprachmodell (denselben oder einen anderen Anbieter). Dein Key bleibt auf dem Gerät. WhisperLoom speichert keine Aufnahmen (Ausnahme: Pro Widgets, siehe unten). Unter Erkennung steht immer, an wen gesendet wird („Audio wird zur Erkennung an OpenAI gesendet."). Was der Anbieter mit den Daten macht, regeln dessen Bedingungen — bei Google Gemini im Free-Tier ausdrücklich Trainingsnutzung, bei DeepSeek Server in China; WhisperLoom weist an der Auswahl darauf hin.

**ElevenLabs:** Das Audio geht an ElevenLabs, das Vokabular als Schlüsselbegriffe. ElevenLabs bewahrt Anfragen laut eigener Datenschutzerklärung auf; ohne Speicherung („Zero Retention") arbeitet es nur für Enterprise-Kunden.

**Modelle vom Server (Pro):** Ist die Funktion an, fragt WhisperLoom beim Anbieter zusätzlich die Modell-Liste ab (mit deinem Key, ohne Audio oder Text). Auf dem Gerät gespeichert wird nur die Liste.

**Offline:** Die Aufnahme verlässt das Gerät nicht. Mit dem **lokalen Textmodell** (Regel „Lokales Textmodell", ab Werk) bleibt auch der erkannte Text auf dem Gerät — Gemma rechnet auf dem Handy und schickt nichts weg. Ins Netz gehen nur die Modell-Downloads von huggingface.co: die Whisper-Modelle von `huggingface.co/ggerganov/whisper.cpp`, die Textmodelle von `huggingface.co/litert-community` (ohne Konto, ohne Key; Hugging Face sieht dabei wie jeder Webserver deine IP-Adresse). Nur mit den Regeln „Online, ohne Netz lokal" oder „Überspringen" und einem eigenen Online-Zugang geht der erkannte Text bei Netz an dieses Sprachmodell ([9.7](#97-lokales-textmodell-gemma-4)).

**Eigener Server:** Audio und Text gehen nur an deinen Server.

**Pro Widgets:** Der Auftrag eines Sprach-Command-Widgets geht nur an den Server, der in genau diesem Widget eingetragen ist. Erkennung und, falls eingeschaltet, Textverbesserung laufen wie beim Diktat über die eingestellten Anbieter (siehe oben, [14.9](#149-was-dabei-gesendet-wird)).

**Ollama:** Beim Anbieter **Ollama (lokal / Homeserver)** geht der Text für die Textverbesserung nur an dein eigenes Ollama — er verlässt dein Netz nicht. Bei **Ollama Cloud** geht er an ollama.com.

**Verlauf:** Den Text deiner Diktate aus Knopf und Tastatur speichert WhisperLoom im Verlauf ([11](#11-verlauf)) — Rohtext der Erkennung, die Fassungen der Stufen mit dem Namen des Modells, Sprache, Quelle (Tastatur oder Knopf), Zeitpunkt und Dauer. Er liegt nur auf dem Gerät, in einem Ordner, den Android nie sichert: nie im Google-Backup, nie im Geräteumzug. Kein Audio, keine Ziel-App, keine geteilten Sprachnachrichten. Die Diktat-Tastatur nimmt nichts aus Passwortfeldern und aus Feldern auf, die keine Vorschläge lernen dürfen (Inkognito). Der Knopf erkennt nur Passwortfelder, und nur mit eingeschalteter Bedienungshilfe; Inkognito-Felder erkennt er nie ([11](#11-verlauf)). Den Verlauf schickt WhisperLoom nirgends hin — nur wenn du bei einem Eintrag **Andere Stufe …** wählst, geht dessen Rohtext an das Sprachmodell deiner Textverbesserung, genau wie bei einem Diktat (bei Offline-Erkennung je nach Regel ans lokale Textmodell). Größe 10–500 Einträge (ab Werk 50), ältere fallen von selbst weg; Löschen einzeln per Wischen, **Alle löschen** im ⋮-Menü, und **Verlauf speichern** auszuschalten löscht alles. Eine eigene Verschlüsselung gibt es nicht; Android verschlüsselt den App-Speicher. Die Texte erscheinen nie in Logs oder Benachrichtigungen.

**Bedienungshilfe:** Die Bedienungshilfe „WhisperLoom Text-Einfügen" fügt den diktierten Text in das fokussierte Feld ein. Dafür liest sie nur dieses Feld — Text und Cursor, damit an der richtigen Stelle eingefügt wird — und prüft, ob es ein Passwortfeld ist, damit dessen Text nicht in den Verlauf kommt. Andere Bildschirminhalte liest sie nicht, den Feldinhalt speichert sie nicht. Die Diktate vom Knopf hebt der Verlauf auf, solange er an ist (siehe oben). Android zeigt beim Aktivieren die übliche Warnung für Bedienungshilfen („kann Bildschirminhalte lesen") — WhisperLoom nutzt davon nur das Einfügen und diese Prüfung.

**Auf dem Gerät gespeichert:** Deine Einstellungen inklusive API-Key (im privaten App-Speicher, für andere Apps unzugänglich), die heruntergeladenen Modelle (Whisper- und Textmodelle, dazu der Zwischenspeicher, den ein Textmodell beim ersten Start anlegt), dein Vokabular (bei einer verknüpften Datei nur der Verweis darauf), die zuletzt geladenen Modell-Listen der Anbieter (ohne Key) und die Position des Knopfs, der Verlauf (siehe oben); mit Pro Widgets außerdem die Widgets samt Server-Adressen, Tokens und Galerie-Bildern sowie ein noch nicht gesendeter Auftrag, bis er draußen oder verworfen ist ([14.9](#149-was-dabei-gesendet-wird)). Sonst keine Aufnahmen und keine Texte; geteilte Sprachnachrichten werden nirgends gespeichert. Ein fehlgeschlagenes oder pausiertes Diktat bleibt nur so lange im Arbeitsspeicher, bis du es sendest oder verwirfst. Wechselst du bei offenem Diktat die Tastatur über die Navigationsleiste oder die Systemeinstellungen, wird es wie beim Senden erkannt und landet im Verlauf, wenn er an ist ([5.3](#53-ohne-halten-diktieren-wisch-geste-und-pause)). WhisperLoom ist vom Android-System-Backup ausgenommen (`allowBackup=false`), und weil manche Hersteller den Geräteumzug trotzdem zulassen, sind Einstellungen, Widgets, Modelle und Modell-Listen dafür zusätzlich ausdrücklich ausgeschlossen: die Einstellungen inklusive Key und Widget-Tokens landen weder im Google-Backup noch im Geräte-zu-Gerät-Transfer — nach einem Gerätewechsel richtest du den Zugang neu ein. Der Verlauf liegt ohnehin in einem Ordner, den Android nie sichert.

**Berechtigungen:** Mikrofon (Aufnahme), Internet (Online-Dienst und Modell-Download), Über anderen Apps anzeigen (Knopf), Benachrichtigungen (Beenden-Aktion und Download-Fortschritt), Netzwerkstatus (Nachfrage vor Downloads über mobile Daten und Prüfung, ob vor einer Online-Textverbesserung Netz da ist), Vordergrund-Dienste (Knopf, Modell-Download und Aufnahme eines Pro Widgets). Das lokale Textmodell braucht keine neue Berechtigung.

---

## 13. Wenn etwas nicht klappt

Die häufigsten Punkte stehen auch in der App unter Anleitung & Hilfe → **Wenn etwas nicht klappt**, jeweils mit einem Button zum passenden Ziel.

**Der Knopf erscheint nicht.**
„Über anderen Apps anzeigen" muss erlaubt sein (Einstellungen → Knopf & Tastatur → Berechtigungen). Zusätzlich in den Android-Einstellungen die **Akku-Optimierung** für WhisperLoom ausschalten (Apps → WhisperLoom → Akku → „Nicht eingeschränkt"/„Uneingeschränkt"), sonst beendet Android den Dienst im Hintergrund. Einige Hersteller (Xiaomi, Huawei, Oppo …) haben eigene Autostart-/Hintergrund-Sperren — WhisperLoom dort freigeben. Zeigt WhisperLoom „„Über anderen Apps anzeigen" wurde entzogen" → **Erlauben**.

**Der Text landet nur in der Zwischenablage.**
Die Bedienungshilfe „WhisperLoom" ist aus. Aktivieren (Einstellungen → Knopf & Tastatur → Berechtigungen → Bedienungshilfe → **Öffnen**, oder dort ein Tipp auf die Zeile **Textausgabe**), dann fügt WhisperLoom den Text direkt ein. In manchen Apps ist das Feld kein normales Textfeld (etwa in einigen Spielen oder Terminal-Apps) — dann bleibt die Zwischenablage der Weg.

**„Eingeschränkte Einstellung" beim Aktivieren der Bedienungshilfe.**
Bei manuell installierten Apps: App-Info → ⋮ → **Eingeschränkte Einstellungen zulassen**, dann die Bedienungshilfe erneut aktivieren (siehe [Schritt 5](#schritt-5--text-automatisch-einfügen-empfohlen)).

**„Key ungültig (401)" oder „Server verlangt einen (anderen) API-Key".**
Key beim Anbieter neu erzeugen und einfügen (Zwischenablage-Symbol im Key-Feld, keine Leerzeichen). Prüfen, ob der Key zum gewählten Anbieter gehört — ein OpenAI-Key funktioniert nicht bei Groq. Bei ElevenLabs braucht der Key die Berechtigung „Speech to Text" (Meldung „Key oder Berechtigung „Speech to Text" prüfen"). Dann **Zugang prüfen**.

**„Guthaben aufgebraucht (402) — beim Anbieter aufladen".**
Das Guthaben bzw. die Inklusiv-Stunden beim Anbieter sind verbraucht (z. B. ElevenLabs, OpenRouter). Im Konto des Anbieters aufladen oder den Tarif wechseln. Das Diktat bleibt gepuffert.

**„Limit erreicht (429) — später erneut".**
Bei Groq das Tages-/Minutenlimit des Free-Plans, bei OpenAI meist leeres Guthaben oder das Tier-Limit. Guthaben bzw. Limits im Konto des Anbieters prüfen. Das Diktat bleibt gepuffert — einfach später nochmal antippen.

**„Endpunkt nicht gefunden — Base-URL muss auf /v1 enden" (404).**
Nur beim Eigenen Server: Base-URL ohne `/v1` eingetragen, oder whisper-server läuft ohne `--inference-path /v1/audio/transcriptions`.

**„Unverschlüsseltes http:// ist zu dieser Adresse nicht erlaubt — https:// oder lokale Adresse nutzen".**
`http://` geht nur zu privaten Adressen (siehe [Kapitel 10](#10-eigener-server)). Für einen Server im Internet `https://` (Caddy, Tailscale Serve) verwenden oder per VPN eine private Adresse nutzen.

**„Zeitüberschreitung — Server zu langsam oder Verbindung schlecht".**
Beim Eigenen Server (Limit 600 s): ein kleineres Modell auf dem Server verwenden, mehr Threads geben oder kürzer diktieren. Bei Cloud-Anbietern deutet der Fehler auf eine schlechte Verbindung — WhisperLoom puffert das Diktat, Tippen sendet erneut.

**„Server nicht erreichbar — läuft er, stimmt der Port, gleiches WLAN/VPN?" / „Server nicht gefunden — Hostname/IP prüfen".**
Nur beim Eigenen Server: Läuft der Container? Stimmt der Port (speaches 8000, whisper-server 8080, Ollama 11434)? Sind Handy und Server im selben Netz bzw. Tailnet? Test mit `curl` von einem Rechner aus (Kapitel 10, Schritt 4).

**Ollama: keine Modelle im Auswahlfeld / Server nicht erreichbar.**
Beim Anbieter „Ollama (lokal / Homeserver)": Auf dem Server muss `OLLAMA_HOST=0.0.0.0` gesetzt sein, sonst hört Ollama nur auf sich selbst und das Handy kommt nicht durch ([Kapitel 10, Schritt 2](#schritt-2--textverbesserung-starten-optional-ollama)). Handy und Server müssen im selben WLAN bzw. VPN (Tailscale) sein; die Server-Adresse braucht den Port, meist `:11434`. Meldet der Knopf **Modelle vom Server laden** (mit Pro: **Modelle aktualisieren**) „Der Server hat keine Modelle …", ist Ollama erreichbar, hat aber noch kein Modell — auf dem Server z. B. `ollama pull qwen3:8b` ausführen und die Liste erneut laden. Bei „Ollama Cloud" prüfen, ob der Key eingetragen ist.

**Offline ist zu langsam.**
Ein kleineres Modell wählen (Base oder Small) oder auf den Online-Dienst wechseln. Auch andere gleichzeitig laufende Apps bremsen — die Erkennung nutzt alle Performance-Kerne.

**„Offline ohne Textmodell" / „Textverbesserung übersprungen: Kein Textmodell geladen — …".**
Offline ist eine KI-Stufe an, aber das lokale Textmodell fehlt. In der Warnkarte (oder unter Offline-Modelle) **Textmodell laden** antippen — oder **Überspringen**, wenn offline keine KI rechnen soll ([9.7](#97-lokales-textmodell-gemma-4)). Auf Geräten unter 6 GB RAM kommt beides nicht vor: Dort passt kein Textmodell, und die Regel wirkt wie „Überspringen".

**„Textverbesserung übersprungen: Lokales Textmodell fehlgeschlagen".**
Das Textmodell ließ sich nicht laden oder rechnen, meist weil gerade zu wenig Arbeitsspeicher frei ist. Andere Apps schließen und erneut diktieren; hilft das nicht, Gemma 4 E2B statt E4B wählen. Kommt der Fehler gleich beim ersten Diktat nach dem Download, das Textmodell unter Offline-Modelle löschen und neu laden.

**Die lokale Textverbesserung dauert zu lange.**
Ein Tipp auf die Statuszeile, das Mikrofon oder den Knopf fügt den Text sofort ohne KI ein. Spätestens nach 45 Sekunden plus 0,6 Sekunden je Wort (höchstens 10 Minuten) bricht WhisperLoom die Rechnung selbst ab; der Text kommt dann ohne KI mit „Lokales Textmodell fehlgeschlagen". Dauerhaft helfen Gemma 4 E2B statt E4B oder die Regel „Online, ohne Netz lokal" mit eigenem Online-Zugang.

**„Textverbesserung übersprungen: Diktat zu lang für das lokale Textmodell".**
Das lokale Modell fasst Diktat und Antwort zusammen nur rund 4.000 Tokens. Ab etwa 800 Wörtern rechnet es deshalb gar nicht erst — sonst käme nur der Anfang des Texts zurück. Kürzer diktieren oder mit eigenem Online-Zugang „Online, ohne Netz lokal" wählen.

**„Datei beschädigt — erneut laden" / „Offline-Modell konnte nicht geladen werden".**
Modell unter Offline-Modelle löschen und neu laden. Tritt der Fehler direkt nach dem Download auf, war die Übertragung fehlerhaft; WhisperLoom lädt das Modell beim nächsten Versuch neu.

**Der Modell-Download bricht ab.**
„Netzwerkfehler beim Laden": WhisperLoom wiederholt bis zu dreimal automatisch und setzt dann mit **Erneut** an derselben Stelle fort (Teildatei bleibt erhalten). „Nicht genug Speicherplatz": Platz schaffen. „Zeitlimit für Hintergrund-Downloads erreicht — bitte erneut starten": Android begrenzt Hintergrund-Downloads dieser Art auf sechs Stunden pro Tag — erneut starten, der Download läuft weiter.

**„Kein Zugang eingerichtet — in WhisperLoom einrichten" / „Kein Offline-Modell geladen — unter Offline-Modelle laden".**
Die Erkennung ist nicht vollständig eingerichtet: online fehlt Key oder URL, offline das Modell. Der Startbildschirm zeigt den Grund in der Karte Status.

**„Offline-Erkennung wird von diesem Gerät nicht unterstützt (CPU ohne FP16/DotProd)".**
Der Prozessor ist zu alt für die eingebaute Offline-Engine (siehe [9.1](#91-voraussetzungen)). Online-Dienst oder Eigener Server verwenden.

**Der erkannte Text ist auf Englisch, obwohl ich Deutsch spreche.**
Sprache unter Einstellungen → Spracherkennung auf „Deutsch" stellen statt „Automatisch erkennen". Beim eigenen whisper-server zusätzlich `-l auto` oder `-l de` beim Start.

**Nach dem Update fehlt mein Modell / meine Einstellung.**
Einstellungen aus 2.x werden übernommen (Key, URL, Modell, Sprache, Regeln). Offline-Modelle aus Version 1.x (Tag `offline-v1`) werden nicht übernommen — in 3.0.0 unter Offline-Modelle neu laden.

---

## 14. Pro Widgets (Sprach-Command-Widgets)

Pro Widgets sind für Entwickler und Bastler gedacht: Ein **Sprach-Command-Widget** auf dem Startbildschirm nimmt auf und schickt den erkannten Text an einen Server, den du selbst betreibst. Wenn du keinen hast, überspring dieses Kapitel — ab Werk sind Pro Widgets aus, und du merkst von ihnen nichts. Normale Widgets ohne eigenen Server folgen mit einem späteren Update; bis dahin zeigt Einstellungen → **Widgets** nur einen Platzhalter („Normale Widgets für den Startbildschirm kommen mit einem späteren Update.“).

**Die Idee:** Du tippst auf das Widget, sprichst deinen Auftrag, tippst noch einmal (oder hörst einfach auf zu sprechen, wenn du Auto-Stopp eingeschaltet hast) — WhisperLoom schreibt mit und schickt den Text an deinen eigenen Agenten. Der arbeitet den Auftrag ab und antwortet dort, wo du ihm sonst schreibst. Die App wartet nicht auf die Antwort; sie ist nach ein paar Sekunden fertig. Jedes Widget hat seinen eigenen Namen und seinen eigenen Server — ein Widget kann also an den Agenten zu Hause gehen, ein zweites an einen anderen.

### 14.1 Was du brauchst

- Einen erreichbaren Server mit der **Bridge** (dem kleinen Gegenstück, das den Auftrag entgegennimmt und an deinen Agenten übergibt; Quellcode und Einrichtung: [hermes-bridge](https://github.com/CTreitges/hermes-bridge)). Adresse und ein **Token** bekommst du von dort. Mehrere Widgets dürfen denselben Server nutzen oder verschiedene.
- Eine eingerichtete Erkennung — online oder offline, beides geht. Das Mitschreiben läuft genau so wie beim normalen Diktat.
- Die Mikrofon-Berechtigung. Die hast du aus der Einrichtung meist schon.

### 14.2 Pro Widgets freischalten

Einstellungen → Gruppe PRO → **Erweitert** → Karte **Pro-Funktionen** → Schalter **Pro Widgets** einschalten („Blendet im Widget-Menü den Tab „Pro Widgets“ ein: Sprach-Command-Widgets mit eigenem Server.“). Darunter erscheinen zwei Zeilen: **Pro Widgets verwalten** öffnet den Tab „Pro Widgets“, **Anleitung Pro Widgets** ein bebildertes Tutorial mit sechs Seiten.

Das Widget-Menü unter Einstellungen → **Widgets** hat danach zwei Tabs: **Widgets** (normale Widgets, noch ein Platzhalter) und **Pro Widgets** — geöffnet wird „Pro Widgets“. Ist der Schalter aus, gibt es keine Tabs, nur den Platzhalter mit dem Hinweis „Für Entwickler: Pro Widgets lassen sich unter „Erweitert“ freischalten.“ und dem Button **Erweitert**.

**Update von 3.7.0:** Hattest du den Sprachauftrag unter den früheren „Erweiterten Optionen“ eingeschaltet, sind Pro Widgets weiter an. Server-Adresse und Token von dort übernimmt WhisperLoom beim ersten Start einmalig in jedes Widget, das noch keinen eigenen Server hat — du musst nichts neu eintragen.

### 14.3 Ein Widget anlegen und bearbeiten

Einstellungen → **Widgets** → Tab **Pro Widgets** → Karte **Neues Pro Widget** → **Sprach-Command-Widget**. WhisperLoom legt ein Widget an („Sprach-Command 2“, „Sprach-Command 3“ …) und öffnet gleich **Widget bearbeiten**. Hat eines deiner Widgets schon einen funktionierenden Server, sind dessen Adresse und Token bereits eingetragen. Das **Standard-Widget** (ab Werk „Sprach-Command“) gibt es immer; du kannst es bearbeiten, aber nicht löschen.

Unter **Deine Pro Widgets** stehen alle Widgets mit Namen, darunter Server und Modus („bridge.example.de · Tippen startet und stoppt“) oder die Warnung **Server fehlt**. Ein Tipp öffnet **Widget bearbeiten**. Jede Änderung gilt sofort, einen Speichern-Knopf gibt es nicht:

- **Name** — Pflicht, höchstens 24 Zeichen; er steht unter dem Widget auf dem Startbildschirm. Ist das Feld leer, meldet es „Jedes Widget braucht einen Namen.“ — gespeichert bleibt dann der letzte Name.
- **Name unter dem Widget anzeigen** — ab Werk an. Ausgeschaltet zeigt das Widget nur seine Kachel.
- **Server** — Server-Adresse, Token und Verbindung prüfen, siehe [14.4](#144-server-je-widget).
- **Symbol** — eins von 22 eingebauten (Einkauf, Zuhause, Arbeit, Idee, Termin, Notiz …) oder **Aus Galerie**: ein eigenes Foto, rund zugeschnitten. **Bild entfernen** nimmt wieder das Mikrofon. Das Symbol siehst du, solange das Widget bereit ist oder einen Fehler zeigt; beim Aufnehmen und Senden zeigt es wie gewohnt den Zustand.
- **Automatisch senden nach Sprechpause** — siehe [14.6](#146-auto-stopp).
- **Widget löschen** — vorher fragt die App nach. Liegt es auf dem Startbildschirm, zeigt es danach das Standard-Widget. Wartet noch ein Auftrag dieses Widgets, wird er mit verworfen — er geht nie an den Server eines anderen Widgets.
- **Fertig** schließt den Editor.

### 14.4 Server je Widget

Jedes Widget hat seinen eigenen Server. Im Editor unter **Server**:

1. **Server-Adresse** — die Basis-Adresse deiner Bridge ohne `/v1/task`, zum Beispiel `https://bridge.example.de` (ein Unterpfad hinter einem Reverse-Proxy ist erlaubt). Unverschlüsseltes `http://` nimmt WhisperLoom nur für Adressen im Heimnetz oder VPN an; ins offene Internet gibt es eine Warnung.
2. **Token** — das Auge zeigt es kurz im Klartext, das Klemmbrett-Symbol fügt es aus der Zwischenablage ein.
3. **Verbindung prüfen** — antippbar, sobald Adresse und Token vollständig sind. Der Chip darunter sagt dir sofort, ob beides stimmt. Die Prüfung löst **keinen** Auftrag aus — es geht nichts an deinen Agenten.

Ein Auftrag geht immer an den Server des Widgets, mit dem du ihn aufgenommen hast. Adresse und Token liest WhisperLoom bei jedem Sendeversuch neu: Korrigierst du sie, während ein Auftrag wartet, gilt die Korrektur beim nächsten Versuch. Fehlt einem Widget der Server oder ist die Adresse ungültig, zeigt es „Server fehlt — tippen“ und nimmt gar nicht erst auf — die Erkennung wäre sonst umsonst bezahlt.

### 14.5 Das Widget auf den Startbildschirm legen

Drücke lange auf eine freie Stelle deines Startbildschirms → **Widgets** → **WhisperLoom** → **Sprach-Command** dorthin ziehen, wo du es haben willst. Hast du mehr als das Standard-Widget angelegt, fragt WhisperLoom jetzt **„Welches Widget-Profil?“** — tipp eins an, und das Widget ist da. Dort kannst du auch gleich ein **Neues Widget-Profil** anlegen; nach dem Bearbeiten ist es vorgewählt, ein Tipp darauf übernimmt es. Wischst du die Frage weg, wird das Widget nicht hinzugefügt. Gibt es nur das Standard-Widget, fragt WhisperLoom nichts. Sind Pro Widgets noch aus, steht in der Frage „Pro Widgets sind aus. In der App unter Einstellungen → Erweitert einschalten.“ — hinlegen geht trotzdem, das Widget zeigt dann „Pro Widgets aus — tippen“.

**Der Name steht unter dem Widget** — wie bei den App-Symbolen, in heller Schrift mit leichtem Schatten, damit er auf jedem Hintergrundbild lesbar bleibt. Zu lange Namen enden mit „…“. Ausblenden: im Editor **Name unter dem Widget anzeigen** ausschalten.

**Größe:** Zwei mal zwei Felder sind die Voreinstellung. Drück lange auf das Widget und zieh an den Rändern — alles von einem Feld bis vier mal zwei geht. Ganz klein zeigt es nur das Symbol, breit Symbol und Text nebeneinander, ab zwei mal zwei steht beides untereinander; der Name steht in jeder Größe darunter. Du kannst auch mehrere Widgets hinlegen, etwa eins pro Server oder Zweck.

**Auf dem Startbildschirm** (Karte im Tab „Pro Widgets“, sobald ein Widget liegt) listet deine platzierten Widgets („Widget 1 · Einkauf“). Ein Tipp darauf wechselt das Widget-Profil. Ab Android 12 geht das auch direkt am Widget: lange drücken → **Neu konfigurieren**.

Die bebilderte Anleitung dazu liegt unter Erweitert → **Anleitung Pro Widgets**, im Tab „Pro Widgets“ unter **Hilfe** → **Anleitung ansehen** und unter Anleitung & Hilfe → **Widgets & Pro Widgets**.

### 14.6 Auto-Stopp

Ab Werk startet ein Tipp die Aufnahme und ein zweiter Tipp sendet. Schaltest du im Editor **Automatisch senden nach Sprechpause** ein, endet die Aufnahme von selbst, sobald du nach dem Sprechen eine Pause machst, und wird gesendet. Wie lange die Pause sein muss, stellst du darunter ein: **Kurz** (1,2 Sekunden), **Normal** (2 Sekunden) oder **Lang** (3,5 Sekunden, für Denkpausen). Ein Tipp beendet trotzdem jederzeit. Hört das Widget nach dem Start 8 Sekunden lang keine Sprache, verwirft es die Aufnahme und sendet nichts. In sehr lauter Umgebung kann Auto-Stopp zu früh oder zu spät auslösen — dann einfach per Tipp beenden oder das Widget auf „Lang“ stellen.

Alle Widgets zeigen dieselbe Aufnahme: startest du mit einem, zeigen alle „nimmt auf“. Ob Auto-Stopp greift und an welchen Server der Auftrag geht, richtet sich nach dem Widget, mit dem du gestartet hast.

### 14.7 Benutzen — was das Widget zeigt

| Widget zeigt | Bedeutung | Ein Tipp … |
|---|---|---|
| Symbol des Widgets (ab Werk das Mikrofon), „Tippen und sprechen“ | bereit | startet die Aufnahme |
| rot, laufende Zeit | nimmt auf | beendet die Aufnahme und schickt den Auftrag los (mit Auto-Stopp passiert das auch von selbst nach der Sprechpause) |
| blau, „Wird gesendet …“ | schreibt mit und überträgt | sendet sofort, wenn der Auftrag nur wartet. Läuft gerade ein Versuch, passiert erst nach drei Minuten etwas (bei der Offline-Erkennung erst, wenn sie fertig gerechnet hat) — ein ungeduldiger zweiter Tipp soll nichts doppelt bezahlen |
| grün, „Gesendet“ | der Auftrag ist draußen | startet eine neue Aufnahme |
| rot mit Fehlergrund | etwas hat nicht geklappt | schickt **denselben** Auftrag noch einmal, ohne neu aufzunehmen. Kann er so nicht raus (Pro Widgets aus, seinem Widget fehlt der Server), öffnet der Tipp stattdessen die Stelle, an der du es behebst |
| grau mit Server-Symbol, „Server fehlt — tippen“ | diesem Widget fehlen Adresse oder Token | öffnet **Widget bearbeiten** für genau dieses Widget |
| grau mit durchgestrichenem Mikrofon, „Pro Widgets aus — tippen“ | der Schalter „Pro Widgets“ ist aus | öffnet **Erweitert** |
| rot, „Mikrofon nicht erlaubt — tippen“ | WhisperLoom darf das Mikrofon nicht nutzen | öffnet den Tab „Pro Widgets“ mit der Karte **Mikrofon** |

„Bereit“ und „Server fehlt“ gelten je Widget: Ein Widget mit Server ist bereit, eines ohne zeigt „Server fehlt“. Aufnahme, Senden, „Gesendet“ und Fehler zeigen alle Widgets zugleich — es gibt immer nur einen Auftrag.

Ist ein Versuch gescheitert und folgt noch ein weiterer, bleibt das Widget blau und nennt den Grund: „Zeitüberschreitung … — neuer Versuch folgt, tippen = jetzt“. Ein Tipp sendet dann sofort, statt auf den nächsten Versuch zu warten.

Nach dem Absenden darf der Bildschirm ausgehen: Mitschreiben und Übertragen laufen als Auftrag im System weiter und überstehen auch ein kurzes Funkloch. Ist der Auftrag draußen, wird das Widget grün („Gesendet“) und bleibt so, bis du das nächste Mal darauf tippst — dann beginnt wie gewohnt eine neue Aufnahme.

Vergisst du das Beenden, macht das Widget nach fünf Minuten von allein Schluss und schickt das Gesprochene ab. Eine Aufnahme läuft also nie unbemerkt weiter.

### 14.8 Wenn etwas nicht klappt

**„Mikrofon nicht erlaubt — tippen“**
Der Tipp öffnet Einstellungen → Widgets → Tab „Pro Widgets“. Dort steht die Karte **Mikrofon** („Für Pro Widgets fehlt die Mikrofon-Berechtigung.“); ein Tipp darauf erklärt kurz, wofür, und fragt dann Android nach der Erlaubnis.

**„Pro Widgets aus — tippen“**
Der Schalter unter Erweitert ist aus. Der Tipp führt direkt dorthin.

**„Server fehlt — tippen“**
Diesem Widget fehlen Server-Adresse oder Token, oder die Adresse ist ungültig. Der Tipp öffnet **Widget bearbeiten** für genau dieses Widget; sobald Adresse und Token stimmen, ist es bereit.

**„Server fehlt im Widget „…“ — tippen für erneuten Versuch“**
Ein Auftrag wartet, aber sein Widget hat inzwischen keinen gültigen Server mehr. Der Auftrag bleibt gespeichert. Der Tipp — auf dieses oder ein anderes Widget — öffnet **Widget bearbeiten** für das Widget des Auftrags: Server eintragen, dann aufs Widget tippen — er geht los, ohne neu aufzunehmen.

**„Pro Widgets sind aus — tippen für erneuten Versuch“**
Du hast Pro Widgets ausgeschaltet, während ein Auftrag wartete. Er bleibt gespeichert. Der Tipp führt nach **Erweitert**: wieder einschalten, dann aufs Widget tippen — oder den Auftrag dort unter **Offener Auftrag** verwerfen.

**„Widget gelöscht — Auftrag verworfen“**
Das Widget, mit dem du aufgenommen hast, wurde gelöscht, bevor sein Auftrag draußen war. Der Auftrag ist weg — WhisperLoom schickt ihn nie an den Server eines anderen Widgets. Ein Tipp startet eine neue Aufnahme.

**„Kein Ton aufgenommen“**
Es kam nichts am Mikrofon an — etwa weil eine andere App es belegt (Telefonat, Sprachassistent) oder Android es der App im Hintergrund entzogen hat. Ein Tipp startet einen neuen Anlauf.

**„Zu kurz — länger sprechen“**
Der zweite Tipp kam zu schnell. Unter einer knappen Sekunde ist es ein Fehlgriff, kein Auftrag.

**„Nichts gehört — tippen für erneuten Versuch“**
Nur mit Auto-Stopp: Das Widget hat 8 Sekunden lang keine Sprache erkannt und die Aufnahme verworfen — gesendet wurde nichts. Gleichmäßiger Lärm (Auto, Lüfter, Brummen) zählt dabei nicht als Sprache. Ein Tipp startet eine neue Aufnahme. Passiert das oft, obwohl du sprichst — etwa in lauter Umgebung —, sprich näher ans Telefon oder schalte Auto-Stopp für dieses Widget aus.

**Das Widget lässt sich nicht hinzufügen**
Beim Hinzufügen erscheint „Welches Widget-Profil?“, und du hast die Frage weggewischt — dann legt der Startbildschirm das Widget nicht ab. Einfach noch einmal hinziehen und ein Widget-Profil antippen.

**Das Widget bleibt auf „Wird gesendet …“ stehen oder zeigt „… — neuer Versuch folgt“**
Ein Versuch ist gescheitert, meist am Netz oder an einem Server, der gerade erst startet. WhisperLoom versucht es von allein erneut, mit wachsendem Abstand. Ein Tipp auf die Fläche sendet sofort, ohne auf den nächsten Termin oder auf Netz zu warten. Läuft gerade ein Versuch, lässt der Tipp ihn in Ruhe; hängt er länger als drei Minuten, ersetzt ihn der Tipp. Das gilt nicht, solange die Offline-Erkennung noch rechnet: sie lässt sich nicht abbrechen, ein zweiter Versuch müsste hinter ihr warten und würde alles nur verlängern. Der Tipp tut dann nichts — bei einer langen Aufnahme und einem großen Modell kann das einige Minuten dauern. Doppelt ankommen kann dabei praktisch nichts: die Bridge führt denselben Auftrag innerhalb einer Stunde nur einmal aus. Geht er erst später erneut hinaus (etwa ein roter Auftrag, den du am nächsten Tag noch einmal sendest) oder wurde die Bridge dazwischen neu gestartet, kann er in seltenen Fällen ein zweites Mal ausgeführt werden — dann nämlich, wenn der erste Versuch schon angekommen war und nur die Antwort verloren ging. Ohne Netz scheitert der sofortige Versuch ehrlich, und nach einigen Versuchen wird das Widget rot.

Hängt es trotzdem immer wieder: Einstellungen des Telefons → Apps → WhisperLoom → Akku → **Nicht eingeschränkt**. Manche Hersteller halten Hintergrundaufträge sonst lange zurück. Für die Fehlersuche am Rechner zeigt `adb shell dumpsys jobscheduler com.chris.whisperloom`, worauf der Auftrag wartet, und `adb shell am get-standby-bucket com.chris.whisperloom` die Standby-Stufe der App.

Willst du den Auftrag loswerden: Einstellungen → Widgets → Tab „Pro Widgets“ → Karte **Offener Auftrag** → **Offenen Auftrag verwerfen**. Sind Pro Widgets aus, steht die Karte unter Einstellungen → **Erweitert**. Sie erscheint nur, solange ein Auftrag wartet.

**Ein Fehler mit Zahl (z. B. 401 oder 503)**
401 heißt: Token stimmt nicht — korrigier es im Editor des Widgets (Einstellungen → Widgets → Pro Widgets → Widget antippen) und tippe dann auf das Widget, der Auftrag ist noch da. 503 heißt: die Bridge erreicht deinen Agenten gerade nicht; WhisperLoom versucht es von allein mehrmals erneut (das Widget nennt dabei den Grund), erst danach wird das Widget rot.

### 14.9 Was dabei gesendet wird

An den Server des Widgets gehen: der erkannte Text, eine Auftragskennung, Zeitpunkt und Dauer der Aufnahme. **Den Auftrag bekommt nur die Adresse, die in diesem Widget eingetragen ist** — nicht der Hersteller der App und nicht der Server eines anderen Widgets. Erkannt und verbessert wird wie beim Diktat: Fürs Mitschreiben geht die Aufnahme an den Erkennungsweg, den du eingestellt hast, und ist die Textverbesserung an, geht der erkannte Text vorher an deren Anbieter (Kapitel 12 beschreibt das im Detail). Ein Auftrag, der noch nicht durchging, liegt so lange auf dem Telefon, bis er abgeschickt, verworfen oder ersetzt wird; er wird nicht in ein Cloud-Backup übernommen. Die Widgets mit Server-Adressen und Tokens sowie ihre Galerie-Bilder werden nur auf dem Gerät gespeichert und nicht gesichert.

---

## 15. Häufige Fragen

**Was kostet WhisperLoom?**
Die App ist kostenlos und quelloffen (MIT-Lizenz). Kosten entstehen nur beim Online-Anbieter — mit Groq gar keine, mit OpenAI GPT Transcribe etwa $0,0045 pro Diktat-Minute (Stand 09/2026, ohne Gewähr). Die Textverbesserung kostet je nach Modell zwischen etwa $0,0001 und knapp $0,01 pro Diktat-Minute ([7.1](#71-übersicht)). Der Offline-Modus ist komplett kostenlos.

**Brauche ich Google Play oder ein Google-Konto?**
Nein. WhisperLoom wird als APK installiert und braucht keine Google-Dienste. Nur wenn du Google Gemini für die Textverbesserung wählst, brauchst du ein Google-Konto für den Key.

**Muss ich meine Tastatur wechseln?**
Nein. Der schwebende Knopf arbeitet mit jeder Tastatur zusammen (Gboard, SwiftKey, …) — die Tastatur bleibt, wie sie ist. Die Diktat-Tastatur ist nur eine Alternative.

**Kann ich online und offline gleichzeitig eingerichtet haben?**
Ja. Beide Zugänge bleiben gespeichert; unter Spracherkennung schaltest du mit einem Tipp um.

**Welche Sprachen?**
Deutsch (Voreinstellung), Englisch, Spanisch, Französisch, Italienisch oder „Automatisch erkennen". Die Erkennungsmodelle selbst beherrschen deutlich mehr Sprachen; die Auswahl in WhisperLoom umfasst die fünf Sprachen, für die eingebaute Füllwort-Listen mitkommen. Für andere Sprachen „Automatisch erkennen" wählen.

**Wie lang darf ein Diktat sein?**
Ein Diktat per Knopf oder Tastatur wird am Stück an die Erkennung geschickt; die Online-Anbieter nehmen höchstens 25 MB pro Anfrage (etwa 13 Minuten bei WhisperLooms Audioformat). Für die Praxis: einzelne Sätze bis wenige Minuten. Die Diktat-Tastatur warnt bei Online-Erkennung ab 10 Minuten und pausiert bei 12 Minuten von selbst ([5.3](#53-ohne-halten-diktieren-wisch-geste-und-pause)). Geteilte Sprachnachrichten dürfen beliebig lang sein — sie werden in 5-Minuten-Stücke geteilt.

**Warum ist die Erkennung offline so viel langsamer als online?**
Die Online-Anbieter rechnen auf Grafikkarten-Servern; auf dem Telefon läuft das Modell auf der CPU. Dafür geht offline nichts nach außen. Small ist der Kompromiss; wer Geduld hat, bekommt mit Large v3 Turbo fast Online-Qualität.

**Speichert WhisperLoom meine Diktate?**
Aufnahmen nicht (nur ein Pro-Widget-Auftrag wartet bis zum Senden). Den Text deiner Diktate aus Knopf und Tastatur hebt der **Verlauf** auf — nur auf dem Gerät, nie im Backup, ab Werk mit höchstens 50 Einträgen. Die Tastatur lässt Passwort- und Inkognito-Felder aus, der Knopf nur Passwortfelder, die die Bedienungshilfe meldet. Abschalten unter Verlauf → ⋮ → Verlauf-Einstellungen; das löscht alle Einträge ([Kapitel 11](#11-verlauf)). Geteilte Sprachnachrichten werden nicht gespeichert. Siehe auch [Kapitel 12](#12-datenschutz).

**Kann ich Namen und Fachbegriffe hinterlegen?**
Ja: Einstellungen → Wörterbuch & Regeln → **Vokabular** — als Liste in der App oder als verknüpfte .md-/.txt-Datei, die bei jedem Diktat neu gelesen wird ([8.7](#87-wörterbuch-und-sprache)). Wirkt online und offline, kostet nichts extra — außer bei ElevenLabs (etwa 20 % Aufpreis).

**Was passiert beim Update von 2.x auf 3.0.0?**
Deine Einstellungen werden automatisch übernommen: Base-URL, Key, Modell und Kontext-Prompt landen unter Spracherkennung bzw. im Vokabular (Anbieter „OpenAI" bzw. „Eigener Server", je nach URL), die Option „Text von der KI glätten lassen" wird zur Stufe **Glätten**, die Füllwort-/Groß-Schreib-/Leerzeichen-Regeln bleiben. Dein bisheriges Modell bleibt eingetragen (GPT-4o Transcribe wird als Auslauf-Modell gekennzeichnet); neue Installationen starten mit **GPT Transcribe**. Da 3.0.0 mehr Berechtigungen kennt (Benachrichtigungen ab Android 13), kann der Assistent einmalig die noch offenen Schritte zeigen.

**Warum fragt WhisperLoom nach „Über anderen Apps anzeigen" und einer Bedienungshilfe?**
Der Knopf muss über anderen Apps liegen (Overlay), und um Text in ein fremdes Textfeld zu schreiben, ohne die Tastatur zu wechseln, gibt es unter Android nur den Weg über eine Bedienungshilfe. Beides ist optional: Mit „Nur Tastatur nutzen" kommt WhisperLoom ohne beides aus.

**Was passiert beim Drehen des Bildschirms?**
Der Knopf bleibt im sichtbaren Bereich; eine im Querformat gemerkte Position wird im Hochformat korrigiert. Auf Tablets ist WhisperLoom nicht gesondert getestet.

**Wo finde ich diese Anleitung in der App?**
Einstellungen → **Anleitung & Hilfe** (oder das **?** oben rechts auf dem Startbildschirm), acht Abschnitte, jeder mit Bild und Kurztext: So funktioniert's · Einrichtung Schritt für Schritt · API-Key bekommen · Eigener Server · Offline-Modus · Widgets & Pro Widgets · Datenschutz · Wenn etwas nicht klappt. Dazu zwei bebilderte Tutorials: das Einsteiger-Tutorial (Anleitung & Hilfe → **Tutorial erneut ansehen**) und die Anleitung Pro Widgets ([Kapitel 14](#14-pro-widgets-sprach-command-widgets)).

**Wo melde ich Fehler?**
Auf der GitHub-Seite des Projekts (Link unter Einstellungen → Über WhisperLoom → „Quellcode auf GitHub"). Hilfreich sind Android-Version, Gerät, Erkennungsweg (online/offline, Anbieter, Modell) und die genaue Fehlermeldung aus der App.
