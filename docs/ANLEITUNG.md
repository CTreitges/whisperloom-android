# WhisperLoom — Anleitung

Version 3.2.0 · Stand 2026-09-07 · Für Android 8.0 (API 26) und neuer

Diese Anleitung richtet sich an Anwender, die WhisperLoom installieren, einrichten und im Alltag nutzen wollen. Entwickler finden Bau- und Architektur-Hinweise in der [README](../README.md); was sich von Version zu Version geändert hat, steht im [CHANGELOG](../CHANGELOG.md).

Alle Bezeichnungen in dieser Anleitung („Mikro-Knopf starten", „Zugang prüfen", „Füllwörter ausblenden" …) sind die Bezeichnungen, die in der App selbst stehen. Preise und Modellnamen der Online-Anbieter: **Stand 09/2026, ohne Gewähr** — die Anbieter ändern beides laufend.

---

## Inhalt

1. [Was WhisperLoom kann](#1-was-whisperloom-kann)
2. [Installation](#2-installation)
3. [Erste Einrichtung — der Assistent Schritt für Schritt](#3-erste-einrichtung--der-assistent-schritt-für-schritt)
4. [Diktieren mit dem schwebenden Knopf](#4-diktieren-mit-dem-schwebenden-knopf)
5. [Diktieren mit der Tastatur](#5-diktieren-mit-der-tastatur)
6. [Sprachnachrichten abtippen](#6-sprachnachrichten-abtippen)
7. [Anbieter und API-Keys](#7-anbieter-und-api-keys)
8. [Textverbesserung](#8-textverbesserung)
9. [Offline-Modus](#9-offline-modus)
10. [Eigener Server](#10-eigener-server)
11. [Datenschutz](#11-datenschutz)
12. [Wenn etwas nicht klappt](#12-wenn-etwas-nicht-klappt)
13. [Sprachauftrag an einen eigenen Agenten](#13-sprachauftrag-an-einen-eigenen-agenten)
14. [Häufige Fragen](#14-häufige-fragen)

---

## 1. Was WhisperLoom kann

WhisperLoom ist eine Diktier-App für Android. Du sprichst, WhisperLoom erkennt den Text und tippt ihn in das Feld, in dem gerade der Cursor steht — in WhatsApp, Gmail, im Browser, in Notizen, überall.

Dafür gibt es drei Wege:

| Weg | Wann sinnvoll | Kurz gesagt |
|---|---|---|
| **Schwebender Mikro-Knopf** | Der Normalfall. Deine gewohnte Tastatur (z. B. Gboard) bleibt aktiv. | Ein runder Knopf liegt über allen Apps. Antippen = Aufnahme, nochmal antippen = fertig, der Text landet im aktiven Feld. |
| **Diktat-Tastatur** | Wenn eine App kein Overlay erlaubt, oder wenn du lieber ohne Knopf arbeitest. | WhisperLoom als eigene Android-Tastatur: Mikrofon gedrückt halten, sprechen, loslassen. |
| **Sprachnachrichten abtippen** | Für Nachrichten, die andere dir schicken. | In WhatsApp, Telegram, Signal & Co. eine Sprachnachricht teilen → WhisperLoom zeigt den Text in Absätzen, zum Lesen, Kopieren oder Weitergeben. |

Die Spracherkennung selbst läuft wahlweise

- **online** über einen Dienst deiner Wahl (OpenAI, Groq, Mistral, Together AI, DeepInfra, OpenRouter oder ein eigener Server). Du brauchst dafür einmalig einen API-Key des Anbieters; bei Groq ist das kostenlos. Beste Qualität, schnell.
- **offline** direkt auf dem Gerät mit einem einmalig heruntergeladenen Whisper-Modell (32–574 MB). Nichts verlässt das Telefon, die Erkennung dauert dafür einige Sekunden.

Dazu kommt eine optionale **Textverbesserung**: Ein Sprachmodell (KI) glättet Zeichensetzung und Groß-/Kleinschreibung, formuliert verständlicher oder fasst zusammen — in vier Stufen von „Aus" bis „Zusammenfassen". Unabhängig davon räumt WhisperLoom lokal auf: Füllwörter („ähm", „äh") entfernen, Satzanfänge groß schreiben, ein Leerzeichen anhängen.

Was WhisperLoom **nicht** tut: Es speichert keine Aufnahmen, es liest nicht mit, was du sonst tippst, und dein API-Key bleibt auf dem Gerät.

---

## 2. Installation

WhisperLoom wird nicht über den Play Store verteilt, sondern als APK-Datei über die GitHub-Releases-Seite des Projekts.

### 2.1 Voraussetzungen

- Android 8.0 oder neuer.
- Für den **Offline-Modus** zusätzlich: ein 64-Bit-ARM-Gerät (arm64-v8a), dessen Prozessor FP16-Vektorrechnung und DotProd beherrscht (praktisch alle Geräte ab etwa 2018, siehe [Kapitel 9](#9-offline-modus)). Auf anderen Geräten steht nur der Online-Modus zur Verfügung — WhisperLoom zeigt das im Assistenten als „Auf diesem Gerät nicht verfügbar" an.

### 2.2 Installation über F-Droid (eigenes Repository, empfohlen für Updates)

WhisperLoom liegt in einem eigenen F-Droid-Repository. Damit bekommst du Updates wie aus einem App-Store — signiert mit demselben Entwickler-Schlüssel wie die GitHub-Releases.

1. F-Droid-Client installieren (https://f-droid.org).
2. Auf dem Handy diese Seite öffnen und auf „Repo mit einem Tipp hinzufügen" tippen: https://ctreitges.de/fdroid/ — oder im F-Droid-Client unter *Einstellungen → Paketquellen → +* eintragen:
   - Adresse: `https://ctreitges.de/fdroid/repo`
   - Fingerprint: `f07ab6293f13c3d637aaa24eb048f2df9bc55013fa3d92e065d72e4b00fe89d0`
3. Nach dem Aktualisieren der Paketquellen erscheint „WhisperLoom"; installieren wie jede andere App. Updates meldet der F-Droid-Client automatisch (das Repo gleicht sich stündlich mit den GitHub-Releases ab).

### 2.3 APK installieren

1. Auf der Releases-Seite des Projekts das Release **3.2.0** öffnen und die APK-Datei auf das Telefon laden (direkt im Browser des Telefons ist am einfachsten).
2. Die heruntergeladene Datei antippen. Android fragt beim ersten Mal, ob der Browser (bzw. der Dateimanager) **unbekannte Apps installieren** darf — das Wording heißt je nach Hersteller „Unbekannte Apps installieren", „Aus dieser Quelle zulassen" oder „Unbekannte Quellen". Erlauben, zurück, erneut „Installieren" antippen.
3. Google Play Protect prüft die App ggf. beim Installieren. Das ist normal für Apps außerhalb des Play Stores.
4. Nach der Installation **WhisperLoom** öffnen — der Einrichtungs-Assistent startet ([Kapitel 3](#3-erste-einrichtung--der-assistent-schritt-für-schritt)).

### 2.4 Updates

- **Ab 3.2.0 — neuer Name WhisperLoom, neue Paket-ID `com.chris.whisperloom`:** WhisperLoom wird als neue App installiert; die vorherige Version deinstallieren und API-Key/Einstellungen einmal neu eingeben. Signaturschlüssel unverändert.
- Eine neue Version wird einfach **über die alte installiert** (APK herunterladen, antippen, „Aktualisieren"). Alle Einstellungen, der API-Key und heruntergeladene Offline-Modelle bleiben erhalten.
- Beim Sprung von Version 2.x auf 3.0.0 werden die alten Einstellungen automatisch übernommen (Details in den [Häufigen Fragen](#13-häufige-fragen)).
- Voraussetzung dafür ist derselbe **Signaturschlüssel**: Alle offiziellen Releases werden mit demselben Schlüssel signiert. Meldet Android „App nicht installiert" oder „Paket steht in Konflikt", stammt die bereits installierte Version aus einer anders signierten Quelle (z. B. ein selbst gebautes Debug-APK). Dann bleibt nur: alte Version deinstallieren (Einstellungen gehen dabei verloren) und das Release neu installieren.

---

## 3. Erste Einrichtung — der Assistent Schritt für Schritt

Beim ersten Start öffnet WhisperLoom die **Einrichtung** — einen Assistenten mit bis zu sieben Schritten. Die Kopfzeile zeigt „Schritt x von y" (7 Schritte ab Android 13, sonst 6, weil es dort keinen Benachrichtigungs-Schritt gibt). Über das Listen-Symbol oben rechts („Alle Schritte anzeigen") kannst du jederzeit zu einem anderen Schritt springen. Jeder Schritt trägt einen Status-Chip: **Erledigt**, **Fehlt noch**, **Optional** oder **Übersprungen**.

Der Assistent merkt sich, was erledigt ist. Wenn du ihn verlässt und WhisperLoom später wieder öffnest, landest du auf dem ersten noch offenen Pflichtschritt. Kehrst du aus einem Systemdialog zurück, prüft WhisperLoom den Status automatisch neu — du musst nichts bestätigen.

Vor dem ersten Schritt begrüßt dich eine Willkommensseite („Diktiere in jede App.") mit dem Button **Los geht's**.

### Schritt 1 — Erkennungsweg: „Wie soll WhisperLoom Sprache erkennen?"

Zwei Karten, du wählst eine:

- **Online-Dienst** (Empfohlen) — „Beste Qualität, schnell. Audio wird an den gewählten Anbieter gesendet. Braucht einen API-Key (bei Groq kostenlos)."
- **Offline auf dem Gerät** — „Alles bleibt auf dem Gerät. Modell einmalig laden (32–574 MB), Erkennung dauert einige Sekunden." Ist dein Gerät nicht geeignet, ist die Karte ausgegraut und trägt den Hinweis „Auf diesem Gerät nicht verfügbar".

Du kannst später jederzeit wechseln (Einstellungen → Erkennung). Dann **Weiter**.

### Schritt 2 — Zugang oder Modell

Der Inhalt hängt von Schritt 1 ab.

**2a · Zugang zum Dienst** (bei Online-Dienst):

1. **Anbieter** wählen: OpenAI · Groq (kostenlos) · Mistral · Together AI · DeepInfra · OpenRouter · Eigener Server. Unter dem Feld steht die Base-URL des Anbieters. Nur bei „Eigener Server" gibst du die **Base-URL** selbst ein ([Kapitel 10](#10-eigener-server)).
2. **API-Key** einfügen. Das Feld ist maskiert; das Auge-Symbol zeigt den Key an, das Einfügen-Symbol holt ihn aus der Zwischenablage. „Wird nur auf diesem Gerät gespeichert." Beim Eigenen Server heißt das Feld „API-Key (optional)".
3. **Modell** wählen. Die Liste zeigt die Modelle des Anbieters mit dem empfohlenen Modell an erster Stelle; der letzte Eintrag **Eigenes Modell …** öffnet ein Feld für eine beliebige Modell-ID („Genau so, wie der Anbieter die ID nennt.").
4. Du hast noch keinen Key? **Wo bekomme ich einen Key?** öffnet die Kurzanleitung je Anbieter mit Link zur Key-Seite (ausführlich in [Kapitel 7](#7-anbieter-und-api-keys)).
5. Optional **Zugang prüfen**: WhisperLoom schickt eine Sekunde Stille an den Anbieter. Erfolg zeigt „Verbunden · x s", ein Fehler nennt den Grund („Key ungültig (401)", „Limit erreicht (429) — später erneut", „Keine Verbindung", „Server antwortet nicht (Zeitüberschreitung)" …).

Darunter steht der Datenschutz-Hinweis „Audio wird zur Erkennung an {Anbieter} gesendet." Sobald Key (bzw. beim Eigenen Server eine gültige URL) vorliegt, wird der Chip **Erledigt** und **Weiter** aktiv.

**2b · Offline-Modell laden** (bei Offline):

„Wähle ein Modell. Empfehlung: Small — gute Qualität für Deutsch bei 190 MB." Die Liste zeigt Tiny, Base, Small und Large v3 Turbo mit Größe, Speicherbedarf und Hinweis. **Laden (190 MB)** startet den Download; er läuft im Hintergrund weiter und zeigt den Fortschritt in einer Benachrichtigung. Über mobile Daten fragt WhisperLoom vorher nach („Über mobile Daten laden?"). Alles Weitere zu Modellen in [Kapitel 9](#9-offline-modus). Sobald das gewählte Modell vollständig geladen ist: **Weiter**.

### Schritt 3 — Mikrofon erlauben (Pflicht)

„Ohne Mikrofon kein Diktat. Android fragt dich gleich — bitte „Bei Nutzung der App" wählen." Button **Mikrofon erlauben** öffnet den Android-Dialog.

Hast du das Mikrofon schon zweimal abgelehnt, zeigt Android den Dialog nicht mehr. WhisperLoom erkennt das („Du hast das Mikrofon abgelehnt. Bitte in den App-Einstellungen erlauben.") und bietet **App-Einstellungen öffnen** an: dort unter Berechtigungen → Mikrofon → „Nur bei Nutzung der App zulassen".

### Schritt 4 — Über anderen Apps anzeigen (Pflicht, mit Alternative)

Der schwebende Mikro-Knopf liegt über anderen Apps — dafür braucht Android deine Erlaubnis. **Einstellung öffnen** führt in die Systemeinstellung; dort den Schalter **„Über anderen Apps anzeigen"** für WhisperLoom einschalten und mit der Zurück-Taste zurückkehren. WhisperLoom prüft automatisch. Der aufklappbare Text **Was passiert dabei?** beschreibt die drei Schritte.

**Nur Tastatur nutzen:** Wenn du keinen schwebenden Knopf willst (oder ihn nicht erlauben kannst), überspringst du den Schritt mit diesem Button. Dann funktioniert nur die Tastatur-Variante — Schritt 7 wird deshalb zum Pflichtschritt. Der Chip zeigt **Übersprungen**; du kannst die Erlaubnis später jederzeit unter Einstellungen → Knopf & Tastatur → Berechtigungen nachholen.

### Schritt 5 — Text automatisch einfügen (empfohlen)

Die **Bedienungshilfe** „WhisperLoom Text-Einfügen" fügt den diktierten Text ins gerade fokussierte Feld ein, damit du beim Diktieren nicht die Tastatur wechseln musst. Es wird nichts mitgelesen oder gespeichert.

Ohne diesen Schritt landet der Text in der **Zwischenablage** — du fügst ihn dann selbst ein (langes Drücken im Textfeld → Einfügen). Das funktioniert, ist aber ein Handgriff mehr.

**Bedienungshilfe aktivieren** öffnet die Bedienungshilfe-Einstellungen von Android: Installierte Apps (bzw. „Heruntergeladene Apps") → WhisperLoom → Ein → Bestätigen.

**„Eingeschränkte Einstellung":** Bei Apps, die nicht aus dem Play Store stammen, blockiert Android ab Version 13 das Einschalten einer Bedienungshilfe zunächst mit diesem Hinweis. Lösung: App-Info von WhisperLoom öffnen (in den Android-Einstellungen → Apps → WhisperLoom, oder App-Symbol lange drücken → ⓘ) → Menü **⋮** oben rechts → **Eingeschränkte Einstellungen zulassen** → danach die Bedienungshilfe erneut aktivieren. Der aufklappbare Text „Was passiert dabei?" im Assistenten nennt genau diese Reihenfolge.

Dieser Schritt lässt sich mit **Überspringen** auslassen; auf dem Startbildschirm erinnert dann ein Banner daran.

### Schritt 6 — Beenden per Benachrichtigung (ab Android 13, empfohlen)

„Solange der Knopf läuft, zeigt Android eine stille Benachrichtigung mit „Beenden". Ohne Erlaubnis ist sie unsichtbar — beenden kannst du den Knopf dann in der App." Button **Benachrichtigungen erlauben**; bei dauerhafter Ablehnung wieder **App-Einstellungen öffnen**. Auch dieser Schritt ist überspringbar.

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

**Knopf starten & los** zeigt beim ersten Mal ein kurzes, bebildertes **Tutorial** (vier Seiten: Diktieren mit dem Knopf · Diktier-Tastatur · Sprachnachrichten abtippen · Der Text ist da); **Überspringen** ist jederzeit möglich. Erst danach startet der schwebende Mikro-Knopf (damit er nicht über dem Tutorial schwebt), und du landest auf dem Startbildschirm; bei „Nur Tastatur" heißt der Button **Zum Start**.

Das Tutorial ist erneut aufrufbar unter **Anleitung & Hilfe → Tutorial erneut ansehen**; **Mehr** beim Hinweis „Sprachnachrichten abtippen" auf dem Startbildschirm springt direkt zur Seite über Sprachnachrichten.

### Der Startbildschirm

Danach zeigt WhisperLoom beim Öffnen den Startbildschirm:

- Ganz oben die Karte **Schwebender Mikro-Knopf** mit dem großen Button **Mikro-Knopf starten** bzw. **Mikro-Knopf beenden**. Fehlt eine Pflicht-Berechtigung, ist der Button gesperrt und ein Chip („Mikrofon fehlt — beheben" / „„Über anderen Apps anzeigen" fehlt — beheben") führt in den passenden Schritt.
- Ein Banner **Noch nicht optimal** mit Button **Beheben**, wenn die Bedienungshilfe aus ist, Offline gewählt aber kein Modell geladen ist, oder Benachrichtigungen verweigert sind.
- Die Karte **Status** mit den Zeilen **Erkennung**, **Textverbesserung**, **Berechtigungen**, **Diktat-Tastatur** und (sobald relevant) **Offline-Modelle**. Jede Zeile führt in die passende Einstellung.
- Ein Hinweis auf das Abtippen von Sprachnachrichten und ganz unten **Einrichtung erneut öffnen**.
- Oben rechts: **?** (Anleitung und Hilfe) und **⚙** (Einstellungen).

Die **Einstellungen** sind in sechs Gruppen geteilt: **Erkennung** · **Text** · **Knopf & Tastatur** · **Offline-Modelle** · **Anleitung & Hilfe** · **Über WhisperLoom**. Änderungen werden sofort gespeichert; es gibt keinen „Speichern"-Button.

---

## 4. Diktieren mit dem schwebenden Knopf

Der Knopf (68 dp groß) schwebt über allen Apps, solange er läuft. Er startet über **Mikro-Knopf starten** auf dem Startbildschirm oder am Ende des Assistenten und läuft weiter, wenn du WhisperLoom verlässt. Solange er läuft, zeigt Android die stille Benachrichtigung „WhisperLoom-Diktat aktiv" mit der Aktion **Beenden**; ein Tipp auf die Benachrichtigung öffnet WhisperLoom.

### 4.1 Die vier Zustände

| Zustand | So sieht er aus | Tippen | Ziehen |
|---|---|---|---|
| **Bereit** | dunkler Kreis mit türkisem Ring, Mikrofon-Symbol | Aufnahme starten | Knopf verschieben |
| **Nimmt auf** | roter Kreis, pulsierender Ring, Stopp-Symbol, darunter der Timer „● 0:07" | Aufnahme beenden und senden | Knopf verschieben — dabei erscheint unten das Abbrechen-Ziel |
| **Sendet** | dunkeltürkiser Kreis mit rotierendem Bogen, Label „sendet …" | (wird ignoriert) | Knopf verschieben |
| **Fehler** | dunkelroter Kreis, Wiederholen-Symbol, Label „tippen = erneut" | erneut senden — das Audio ist noch da | aufs Abbrechen-Ziel ziehen = verwerfen |

Der Ablauf im Normalfall: **antippen → sprechen → nochmal antippen**. Kurz darauf steht der Text im Feld, in dem der Cursor stand. Wichtig: Der Cursor muss in einem Textfeld stehen, *bevor* du die Aufnahme beendest — WhisperLoom fügt dort ein, wo gerade der Fokus ist.

Beim Diktieren gilt: normal sprechen, ohne Kunstpausen. Satzzeichen musst du nicht diktieren — die Online-Modelle setzen sie selbst; mit der Stufe „Glätten" der Textverbesserung ([Kapitel 8](#8-textverbesserung)) werden Zeichensetzung und Groß-/Kleinschreibung zusätzlich korrigiert. Vor dem Senden schneidet WhisperLoom Stille am Anfang und Ende weg.

### 4.2 Verschieben und Abbrechen

- **Verschieben:** Knopf gedrückt halten und ziehen. Er bleibt, wo du ihn loslässt (kein Einrasten am Rand), und merkt sich die Position. Er rutscht nie aus dem Bildschirm.
- **Abbrechen:** Während der Aufnahme (oder im Fehlerzustand) den Knopf nach unten ziehen. Am unteren Rand erscheint das Abbrechen-Ziel **✕ Verwerfen**. Sobald der Knopf darüber ist, wächst das Ziel, färbt sich rot und zeigt **Loslassen zum Verwerfen** — loslassen, und das Diktat ist weg („Diktat verworfen"). Der Knopf springt an seine alte Position zurück.
- **Position zurücksetzen:** Ist der Knopf mal an einer unpraktischen Stelle gelandet (z. B. nach dem Drehen des Bildschirms), setzt Einstellungen → Knopf & Tastatur → **Position zurücksetzen** ihn an die Startposition.

### 4.3 Fehler und erneut senden

Schlägt die Erkennung fehl — kein Netz, Server überlastet, Zeitüberschreitung —, wechselt der Knopf in den Fehlerzustand, wackelt kurz und zeigt „tippen = erneut". **Das Diktat geht nicht verloren:** Das Audio bleibt gepuffert, ein Tipp sendet es erneut. Erst das Ziehen aufs ✕ verwirft es.

Bei Fehlern, die sich durch Wiederholen nicht beheben lassen (z. B. ungültiger Key, unbekanntes Modell), zeigt WhisperLoom den Grund als kurze Meldung; in dem Fall hilft ein Blick in Einstellungen → Erkennung ([Kapitel 12](#12-wenn-etwas-nicht-klappt)).

### 4.4 Ohne Bedienungshilfe: Zwischenablage

Ist die Bedienungshilfe nicht aktiv, kann WhisperLoom den Text nicht direkt einfügen. Er wird stattdessen in die **Zwischenablage** kopiert; der Knopf zeigt zwei Sekunden lang „Kopiert — einfügen". Dann im Textfeld lange drücken → Einfügen. Auf dem Startbildschirm erinnert das Banner „Ohne Bedienungshilfe landet der Text nur in der Zwischenablage." mit **Beheben** an den fehlenden Schritt.

### 4.5 Beenden

**Mikro-Knopf beenden** auf dem Startbildschirm, oder **Beenden** in der Benachrichtigung. Beim nächsten Start steht der Knopf wieder an seiner gemerkten Position.

---

## 5. Diktieren mit der Tastatur

Die **Diktat-Tastatur** ist eine eigene Android-Eingabemethode („WhisperLoom Diktat"). Sie ersetzt deine normale Tastatur nicht — du wechselst bei Bedarf hin und wieder zurück.

### 5.1 Aktivieren und wechseln

Aktivieren und auswählen wie in [Schritt 7](#schritt-7--diktat-tastatur-optional-pflicht-wenn-du-in-schritt-4-nur-tastatur-nutzen-gewählt-hast) des Assistenten, oder später unter Einstellungen → Knopf & Tastatur → **Diktier-Tastatur**. Zwischen den Tastaturen wechselst du wie bei jeder Android-Tastatur: über das Tastatur-Symbol in der Navigationsleiste, oder direkt in der WhisperLoom-Tastatur über die Globus-Taste („Eingabemethode wechseln").

### 5.2 Bedienung

Die Tastatur besteht aus drei Zonen:

- **Statuszeile** oben: „Halte das Mikrofon gedrückt und sprich" — während der Aufnahme „Höre zu … rechts wischen stellt fest", dann „Wird übertragen …". Fehlt das Mikrofon oder der Zugang, steht dort ein Warntext („Mikrofon-Berechtigung fehlt — tippe zum Einrichten" / „Kein Zugang eingerichtet — tippe zum Einrichten"); ein Tipp darauf öffnet den passenden Schritt der Einrichtung.
- **Pegelband und Mikrofon-Taste**: Die große Taste in der Mitte funktioniert mit **Halten-zum-Sprechen** — gedrückt halten, sprechen, loslassen. Das Pegelband darüber zeigt während der Aufnahme deine Lautstärke. Die Taste zeigt dieselben vier Zustände wie der schwebende Knopf (bereit, nimmt auf, sendet, Fehler).
- **Tastenreihe** unten: Globus (Eingabemethode wechseln) · Komma · Leertaste · Punkt · Löschen · Eingabe · (nur nach einem Fehler:) **Erneut senden** · Zahnrad (WhisperLoom-Einstellungen).

Rechts über dem Mikrofon sitzt außerdem ein **Zauberstab** — er klappt die Textverbesserung auf, siehe [5.4](#54-textverbesserung-direkt-in-der-tastatur).

Der erkannte Text wird direkt an der Cursor-Position eingefügt — die Bedienungshilfe ist bei der Tastatur nicht nötig. Nach einem Fehler („Fehler bei der Erkennung — erneut versuchen") bleibt das Audio erhalten; die Taste **Erneut senden** wiederholt den Versuch.

Wenn du „Leerzeichen nach Diktat anhängen" (Einstellungen → Text → Regeln ohne KI) aktiv hast, kannst du mehrere Diktate direkt hintereinander sprechen, ohne zwischendurch ein Leerzeichen zu tippen.

### 5.3 Ohne Halten diktieren: die Wisch-Geste

Für längere Diktate musst du das Mikrofon nicht die ganze Zeit gedrückt halten. Halte es an, fang an zu sprechen — und **zieh den Finger nach rechts**, ohne loszulassen. Links und rechts neben dem Mikrofon erscheinen zwei Kreise: links ✕ zum Verwerfen, rechts ein Schloss zum Feststellen. Sobald der Kreis sich einfärbt, sagt die Statuszeile, was das Loslassen tut („Loslassen stellt die Aufnahme fest"). Jetzt loslassen.

Die Aufnahme läuft dann **freihändig weiter**. Die Statuszeile zählt die Dauer mit („Aufnahme 0:42 — senden oder verwerfen"), und aus den beiden Kreisen werden Tasten:

- **Senden** (rechts, Papierflieger) beendet die Aufnahme und fügt den Text ein. Ein Tipp auf das Mikrofon in der Mitte tut dasselbe.
- **Verwerfen** (links, ✕) wirft die Aufnahme weg — nichts wird übertragen, nichts eingefügt.

**Nach links ziehen** statt nach rechts verwirft sofort, ohne Umweg über das Feststellen. Und wenn du einfach loslässt, ohne zu ziehen, wird wie bisher direkt gesendet — die Geste ändert daran nichts.

Zwei Feinheiten: Ziehst du stark nach oben oder unten weg, gilt das nicht mehr als Wischen und es rastet nichts ein. Und schließt du die Tastatur, während eine festgestellte Aufnahme läuft, wird sie noch fertig übertragen und eingefügt, solange das Eingabefeld erhalten bleibt; ist auch das Feld weg, wird die Aufnahme verworfen.

**Mit TalkBack:** Gedrückthalten funktioniert dort nicht. Deshalb **startet ein Antippen** des Mikrofons die Aufnahme, und sie ist sofort festgestellt — ein zweiter Tipp sendet. Die Tasten Verwerfen und Senden sind dann ganz normal ansteuerbar.

### 5.4 Textverbesserung direkt in der Tastatur

Die Stufe der Textverbesserung musst du nicht in den Einstellungen suchen. Ein Tipp auf den **Zauberstab** rechts über dem Mikrofon klappt eine Zeile mit den vier Stufen auf:

**Aus · Glätten · Schöner · Kürzen**

(Das sind dieselben Stufen wie unter Einstellungen → Text, dort heißen sie ausgeschrieben „Verschönern" und „Zusammenfassen" — in der schmalen Tastenzeile passen nur die Kurzfassungen.)

Die aktuelle Stufe ist hervorgehoben. Ein Tipp wählt eine andere — sie gilt ab dem nächsten Diktat, ohne Umweg über die Einstellungen. Änderst du sie hier, steht sie in den Einstellungen genauso. Ein zweiter Tipp auf den Zauberstab klappt die Zeile wieder ein; solange sie zu ist, ist die Tastatur genauso hoch wie vorher.

Sobald du zu diktieren anfängst, verschwindet der Zauberstab — an seiner Stelle erscheinen dann die Ziele der Wisch-Geste. Eine offene Stufen-Zeile klappt dabei zu.

Die drei oberen Stufen brauchen einen KI-Zugang. Hast du keinen eingerichtet, sind sie ausgegraut und die Statuszeile sagt es dir — ein Tipp darauf führt in die Einstellungen. **Aus** bleibt immer wählbar.

---

## 6. Sprachnachrichten abtippen

WhisperLoom erscheint im **Teilen-Menü** von Android für Audiodateien („Mit WhisperLoom transkribieren"). Damit lässt sich jede Sprachnachricht in Text verwandeln — ideal für lange Nachrichten oder wenn du gerade nicht hören kannst.

### 6.1 So geht's

- **WhatsApp:** Sprachnachricht lange drücken → **Teilen** (bzw. ⋮ → Teilen) → **WhisperLoom**.
- **Telegram, Signal:** Nachricht lange drücken → Teilen → WhisperLoom. Wo genau der Teilen-Eintrag sitzt, unterscheidet sich je App und Version — er heißt aber überall „Teilen".
- Genauso mit Aufnahmen aus Rekorder-Apps oder Dateien aus dem Dateimanager. Mehrere Dateien auf einmal gehen auch.

WhisperLoom öffnet den Bildschirm **Transkription**. Oben steht die Quelle mit Dauer („Sprachnachricht · 0:42 · 1 Datei"), darunter der Fortschritt („Audio wird entpackt …", „Wird übertragen …"; bei mehreren oder langen Dateien „Datei 1 von 3 · Stück 1 von 2 · …"). Fertige Dateien erscheinen sofort, auch wenn weitere noch laufen.

### 6.2 Das Ergebnis

- Der Text erscheint in **Absätzen** — WhisperLoom setzt sie an Satzgrenzen, bevorzugt vor Wörtern wie „Also", „Außerdem", „Dann". Bei mehreren Dateien gibt es je Datei einen Abschnitt mit Quelle und Dauer. Der Text ist markierbar.
- Schalter **Füllwörter ausblenden** (standardmäßig **an**): Blendet „ähm, äh …" aus. Ausgeschaltet zeigt WhisperLoom den Text **wortgetreu, 100 %** — bei fremden Nachrichten will man manchmal genau wissen, was gesagt wurde. Der Schalter wirkt sofort auf die Anzeige und auf Kopieren/Teilen; es wird nichts neu hochgeladen.
- **Kopieren** legt den angezeigten Text in die Zwischenablage („In die Zwischenablage kopiert"); **Teilen** gibt ihn als Text an eine andere App weiter. Die Zwischenablage wird nie automatisch überschrieben.
- Ist eine Datei fehlgeschlagen, steht der Grund bei ihr („Fehlgeschlagen: …") mit **Erneut** nur für diese Datei; bei mehreren Fehlern gibt es zusätzlich **Alles erneut**.
- Ist noch kein Zugang eingerichtet, zeigt der Bildschirm „Kein Zugang eingerichtet" mit **Einrichtung öffnen**.

### 6.3 Lange Nachrichten

WhisperLoom wandelt geteiltes Audio auf dem Gerät in das Format um, das die Erkennung braucht (16 kHz, Mono) — deshalb funktionieren auch WhatsApp-Nachrichten (Opus in OGG), die viele Online-Dienste nicht direkt annehmen. Lange Aufnahmen werden in **Stücke von höchstens 5 Minuten** geteilt; geschnitten wird an einer leisen Stelle (einer Sprechpause), damit kein Wort zerteilt wird. Jedes Stück wird einzeln erkannt, der Text hinterher zusammengesetzt; die Fortschrittsanzeige zählt die Stücke mit. Das gilt für Online-Dienste wie für den Offline-Modus.

Beim Offline-Modus dauern lange Nachrichten entsprechend länger; Schließen des Bildschirms bricht die laufende Erkennung ab.

### 6.4 Was nicht passiert

Geteilte Nachrichten werden **nicht** durch die KI-Textverbesserung geschickt — sie erscheinen so, wie sie erkannt wurden (nur die lokale Füllwort-Ausblendung und die Absatzbildung kommen dazu). Und sie werden nirgends gespeichert: Wenn du den Bildschirm schließt, ist der Text weg — außer du hast ihn kopiert oder geteilt.

---

## 7. Anbieter und API-Keys

WhisperLoom spricht die **OpenAI-kompatible API**, die inzwischen viele Anbieter anbieten. Du brauchst ein Konto beim Anbieter deiner Wahl und einen **API-Key** — einen persönlichen Zugangsschlüssel, mit dem der Anbieter die Nutzung abrechnet. Du bezahlst nur, was du nutzt; ein Diktat kostet meist unter einem Cent.

Alle Angaben in diesem Kapitel: **Stand 09/2026, ohne Gewähr.** Preise in US-Dollar, wie von den Anbietern ausgewiesen.

### 7.1 Übersicht

Die Spalte „Modelle" nennt die Einträge, wie sie in WhisperLoom im Dropdown stehen (erster Eintrag = Voreinstellung).

| Anbieter (Dropdown) | Für | Modelle (Erkennung) | Modelle (Textverbesserung) | Preis Erkennung | Kostenlos? | Key holen |
|---|---|---|---|---|---|---|
| **OpenAI** | Erkennung + Text | GPT Transcribe (empfohlen) · GPT-4o Transcribe (Auslauf 02/2027) · GPT-4o mini Transcribe (Auslauf 02/2027) · Whisper v2 (Legacy, Auslauf 02/2027) | GPT-4o mini · GPT-4.1 mini · GPT-5.6 Luna · GPT-5.4 nano · GPT-5 mini (Auslauf 12/2026) · GPT-5 nano (Auslauf 12/2026) | GPT Transcribe $0,0045/min · GPT-4o Transcribe $0,006/min · GPT-4o mini Transcribe $0,003/min · Whisper $0,006/min | nein — Guthaben ab $5 | https://platform.openai.com/api-keys |
| **Groq (kostenlos)** | Erkennung + Text | Whisper Large v3 Turbo · Whisper Large v3 | GPT-OSS 20B · GPT-OSS 120B · Qwen 3.6 27B (Preview) | Turbo $0,04/h (≈ $0,00067/min) · Large v3 $0,111/h | **ja** — Free-Plan ohne Zahlungsmittel: 2 h Audio/Stunde, 8 h/Tag; LLM 30 Anfragen/min, 1.000/Tag | https://console.groq.com/keys |
| **Mistral** | Erkennung + Text | Voxtral Mini Transcribe 2 | Mistral Small 4 · Ministral 3 8B | $0,003/min | Experiment-Plan gratis (Telefon-Verifizierung; ob Audio enthalten ist, ist nicht belegt) | https://console.mistral.ai/api-keys |
| **Together AI** | Erkennung | Whisper Large v3 | — (Modell frei eintippen) | $0,0015/min | Startguthaben für neue Konten | https://api.together.ai/settings/api-keys |
| **DeepInfra** | Erkennung | Whisper Large v3 Turbo · Whisper Large v3 | — (Modell frei eintippen) | Turbo $0,0002/min · Large v3 $0,00045/min | nein | https://deepinfra.com/dash/api_keys |
| **OpenRouter** | Erkennung + Text | Voxtral Mini Transcribe (via OpenRouter) · GPT-4o mini Transcribe (via OpenRouter) · Whisper Large v3 Turbo (via OpenRouter) | GPT-4o mini · Gemini 2.5 Flash-Lite · Claude Haiku 4.5 · Mistral Small 4 | ab $0,003/min | „:free"-Textmodelle mit Limits (20/min; 50 bzw. 1.000/Tag) | https://openrouter.ai/settings/keys |
| **Anthropic (Claude)** | nur Text | — | Claude Haiku 4.5 · Claude Sonnet 5 | — | kleines Startguthaben | https://platform.claude.com/settings/keys |
| **Google Gemini** | nur Text | — | Gemini 2.5 Flash-Lite · Gemini 2.5 Flash · Gemini 3.8 Flash | — | **ja**, aber Free-Tier-Inhalte dürfen zum Training genutzt werden | https://aistudio.google.com/apikey |
| **DeepSeek** | nur Text | — | DeepSeek V4 Flash | — | nein | https://platform.deepseek.com/api_keys |
| **Eigener Server** | Erkennung + Text | frei (z. B. `Systran/faster-whisper-medium`, `whisper-1`) | frei (z. B. `qwen3:8b`) | deine Hardware | — | kein Key nötig, außer dein Server verlangt einen |

Textverbesserung kostet zusätzlich, aber deutlich weniger als die Erkennung — bei GPT-4o mini etwa $0,0002 pro Diktat-Minute, bei Groqs GPT-OSS 20B etwa $0,0001. Der Preisvergleich entscheidet sich bei der Erkennung.

### 7.2 Empfehlungen

- **Kostenlos anfangen: Groq.** Free-Plan ohne Kreditkarte, 8 Stunden Audio pro Tag, „Whisper Large v3 Turbo" ist für Deutsch gut und sehr schnell. Mit demselben Key läuft auch die Textverbesserung (GPT-OSS 20B) — ein Konto, alles gratis.
- **Beste Qualität: OpenAI „GPT Transcribe".** Das aktuelle Modell mit den besten dokumentierten Erkennungsraten; $0,0045 pro Minute, dafür ist eine Mindestaufladung von $5 nötig. Das Standardmodell in WhisperLoom.
- **Europäischer Anbieter: Mistral „Voxtral Mini Transcribe 2".** Server in der EU, Deutsch als Kernsprache, $0,003/min.
- **Sehr günstig:** DeepInfra ab $0,0002/min oder Together AI $0,0015/min — beide mit Whisper Large v3.
- **Ein Key für alles:** OpenRouter bündelt viele Modelle unter einem Konto; für lange Aufnahmen ungünstig (60-Sekunden-Limit je Anfrage beim Anbieter).

### 7.3 Modelle, die auslaufen

OpenAI hat die älteren Erkennungsmodelle **GPT-4o Transcribe, GPT-4o mini Transcribe und Whisper v2 zum 2027-02-26 abgekündigt**; die Textmodelle GPT-5 mini und GPT-5 nano enden am 2026-12-11. WhisperLoom kennzeichnet solche Einträge im Dropdown mit „(Auslauf 02/2027)" bzw. „(Auslauf 12/2026)". Wer aus Version 2.x umsteigt, behält sein bisheriges Modell (meist GPT-4o Transcribe) — es funktioniert bis zur Abschaltung, danach unter Erkennung → Modell auf „GPT Transcribe (empfohlen)" wechseln.

### 7.4 Key besorgen — Schritt für Schritt

Dieselben Schritte zeigt WhisperLoom unter **Wo bekomme ich einen Key?** (im Assistenten und unter Erkennung) und unter Anleitung & Hilfe → **API-Key bekommen**; von dort führt jeweils ein Link direkt zur Key-Seite. Der Key wird beim Erzeugen meist **nur einmal angezeigt** — direkt kopieren und in WhisperLoom einfügen (Einfügen-Symbol im Key-Feld).

**OpenAI** — 1) https://platform.openai.com registrieren. 2) *Settings → Billing* → Zahlungsmittel + Prepaid-Guthaben (mind. $5). 3) https://platform.openai.com/api-keys → *Create new secret key* → kopieren. 4) In WhisperLoom: Anbieter „OpenAI", Key einfügen.

**Groq** — 1) https://console.groq.com registrieren (Google/GitHub/E-Mail). 2) https://console.groq.com/keys → *Create API Key*. 3) Kostenlos nutzbar ohne Zahlungsmittel (Free-Plan). Optional *Billing → Developer* für höhere Limits und 100-MB-Dateien.

**Mistral** — 1) https://console.mistral.ai registrieren, Studio aktivieren. 2) Plan wählen: *Experiment* (gratis, Telefonnummer verifizieren) oder *Pay-as-you-go* (Karte). 3) *API Keys* → *Create new key*.

**Together AI** — 1) https://api.together.ai registrieren (Startguthaben). 2) *Settings → API Keys* → *Create key*.

**DeepInfra** — 1) https://deepinfra.com anmelden (GitHub/Google). 2) *Dashboard → API Keys* → *New API Key*. 3) Guthaben aufladen.

**OpenRouter** — 1) https://openrouter.ai anmelden. 2) *Credits* aufladen (ab $10 Guthaben steigt das Limit der „:free"-Modelle auf 1.000 Anfragen/Tag). 3) https://openrouter.ai/settings/keys → *Create Key*.

**Anthropic** (nur Textverbesserung) — 1) https://platform.claude.com registrieren. 2) *Billing* → Guthaben (kleines Startguthaben vorhanden). 3) https://platform.claude.com/settings/keys → *Create Key*. WhisperLoom zeigt dazu den Hinweis „OpenAI-Kompatibilitätsschicht — von Anthropic als Test-Werkzeug eingestuft."

**Google Gemini** (nur Textverbesserung) — 1) https://aistudio.google.com/apikey öffnen, mit Google-Konto anmelden, Bedingungen akzeptieren. 2) *Create API key*. 3) Gratis nutzbar — aber: **Im Free-Tier darf Google die Inhalte zum Training nutzen.** Diktate sind oft privat; entweder Billing aktivieren oder den Anbieter meiden. WhisperLoom zeigt die Warnung „Free-Tier: Google darf Inhalte zum Training nutzen."

**DeepSeek** (nur Textverbesserung) — 1) https://platform.deepseek.com registrieren. 2) *Top up* (kein Free-Tier). 3) https://platform.deepseek.com/api_keys → *Create new API key*. Hinweis in WhisperLoom: „Server in China — Datenschutz beachten."

### 7.5 Eigenes Modell und Zugang prüfen

- **Eigenes Modell …** (letzter Eintrag im Modell-Dropdown): Für Modell-IDs, die nicht in der Liste stehen — etwa ein neues Modell des Anbieters oder ein Modell auf dem eigenen Server. Die ID genau so eintragen, wie der Anbieter sie nennt. Achtung bei OpenAI: Frei eingetippte Reasoning-Modelle (z. B. `gpt-5.6-terra`) lehnen den Standard-Parameter der Textverbesserung ab; für die Textverbesserung deshalb möglichst ein Modell **aus der Liste** wählen.
- **Zugang prüfen** (unter Erkennung bzw. Text): Schickt eine kurze Testanfrage an den eingetragenen Zugang und meldet „Verbunden · x s" oder den Fehlergrund. Praktisch nach jedem Key- oder Anbieterwechsel.

---

## 8. Textverbesserung

Einstellungen → **Text**. Hier stellst du ein, was mit dem erkannten Text passiert, bevor er ins Feld kommt. Es gibt zwei Ebenen: die **KI-Textverbesserung** (eine zweite Anfrage an ein Sprachmodell) und die **Regeln ohne KI** (lokal, immer verfügbar, kostenlos).

### 8.1 Textverbesserung (KI) — vier Stufen

| Stufe | Was passiert |
|---|---|
| **Aus** | Nur die Regeln unten, keine zweite Anfrage. |
| **Glätten** | Zeichensetzung, Groß-/Kleinschreibung, Absätze. Inhalt unverändert. |
| **Verschönern** | Formuliert verständlicher, zieht Sätze zusammen, bewahrt den Inhalt. |
| **Zusammenfassen** | Kürzt auf die Kernaussagen. |

Jede Stufe außer „Aus" bedeutet: „Zweite Anfrage · ca. 1–2 s länger · geringe Zusatzkosten". Für Alltagsdiktate ist **Glätten** die sinnvolle Wahl — der Wortlaut bleibt, nur Kommas, Punkte und Groß-/Kleinschreibung werden richtig gesetzt. „Verschönern" eignet sich für E-Mails aus einem Gedankenstrom, „Zusammenfassen" für lange Notizen. Die KI wird ausdrücklich angewiesen, nichts zu übersetzen und nichts zu erfinden.

**Füllwörter intelligent entfernen:** „Statt fester Wortliste entscheidet die KI selbst, welche Füllwörter, Versprecher und Wiederholungen weg können. Im Zweifel bleibt das Wort." Braucht eine Stufe über „Aus" (bei „Zusammenfassen" ohne Wirkung). Solange dieser Schalter aktiv ist, pausiert die feste Wortliste der Regeln — sonst würde zweimal gefiltert.

### 8.2 Zugang für die Textverbesserung

Standardmäßig nutzt die Textverbesserung **Anbieter und Key der Erkennung** (Schalter **Eigenen Zugang verwenden** aus). Das ist bei OpenAI, Groq, Mistral und OpenRouter der einfache Weg: ein Konto, ein Key. Als Modell wird die Voreinstellung des Anbieters genommen (bei OpenAI „GPT-4o mini", bei Groq „GPT-OSS 20B"); unter **Modell** kannst du ein anderes wählen oder per **Eigenes Modell …** eintippen.

**Eigenen Zugang verwenden** einschalten, wenn

- die Erkennung offline läuft (Offline-Modelle können keine Textverbesserung — die Karte zeigt dann „Textverbesserung braucht einen Online-Zugang." mit **Eigenen Zugang eintragen**),
- dein Erkennungs-Anbieter keine Textmodelle hat (Together AI, DeepInfra), oder
- du ein anderes Sprachmodell willst als beim Erkennungs-Anbieter.

Dann erscheinen eigene Felder: **Anbieter** (OpenAI · Groq · Mistral · OpenRouter · Anthropic (Claude) · Google Gemini · DeepSeek · Eigener Server), bei Eigener Server die **Base-URL**, der **API-Key** und das **Modell**. Der Key der Erkennung wird dabei nie an den anderen Anbieter geschickt. Beim Eigenen Server (z. B. Ollama) und bei Together/DeepInfra ist das Modellfeld ein Freitext („z. B. qwen3:8b").

**Anbieter nur für Text** — mit Hinweisen, die WhisperLoom direkt an der Auswahl zeigt:

- **Anthropic (Claude)**: gute Textqualität; „OpenAI-Kompatibilitätsschicht — von Anthropic als Test-Werkzeug eingestuft." (funktional, aber offiziell nicht als Dauerlösung gedacht).
- **Google Gemini**: gratis, aber „Free-Tier: Google darf Inhalte zum Training nutzen."
- **DeepSeek**: „Server in China — Datenschutz beachten."

Auch hier gibt es **Zugang prüfen** — WhisperLoom schickt dem Modell eine Mini-Anfrage und wertet die Antwort aus.

### 8.3 Regeln ohne KI

Immer aktiv, lokal, kostenlos:

- **Füllwörter entfernen (ähm, äh …)** — feste Wortliste je Sprache (Deutsch: ähm, äh, öhm, ähem, hmm, öh; Englisch: um, uh, uhm, erm, hmm; dazu Listen für Spanisch, Französisch, Italienisch). Ein Komma, das durch das Entfernen direkt vor dem Satzende landen würde, verschwindet mit.
- **Liste bearbeiten** öffnet das Blatt **Füllwörter**: Sprache wählen, unter **Eingebaut** einzelne Standardwörter abwählen (z. B. wenn „hmm" bei dir ein echtes Wort ist), unter **Eigene Wörter** weitere hinzufügen (Feld „Wort hinzufügen", auch Zwei-Wort-Floskeln). **Standard wiederherstellen** setzt beides zurück. Hinweis aus der App: „Nur eindeutige Füllsilben — echte Wörter wie „halt" bleiben, sonst kaputte Sätze."
- **Automatisch groß schreiben** — Satzanfänge groß (kein Title-Case).
- **Leerzeichen nach Diktat anhängen** — damit das nächste Diktat oder Tippen nicht am letzten Wort klebt.

### 8.4 Sprache & Kontext

Unter Einstellungen → **Erkennung** → Karte **Sprache & Kontext**:

- **Sprache**: Automatisch erkennen · Deutsch (Voreinstellung) · Englisch · Spanisch · Französisch · Italienisch. Eine feste Sprache ist schneller und genauer als „Automatisch erkennen" — vor allem bei kurzen Diktaten; sie bestimmt auch, welche Füllwort-Liste gilt. Bei „Automatisch erkennen" nimmt WhisperLoom die vom Modell erkannte Sprache für die Nachbearbeitung.
- **Kontext: Namen, Fachbegriffe, Schreibweisen** — ein Freitextfeld, das als Prompt mitgeschickt wird und der Erkennung bei Eigennamen und Fachwörtern hilft. Kostet nichts extra. Beispiel: „Christof Treitges, WhisperLoom, Lieferschein-Processor, SvelteKit". Der Kontext wirkt online **und** offline (dort als Start-Prompt des Modells). Mistral und OpenRouter unterstützen das Prompt-Feld nicht — dort wird der Kontext nicht mitgeschickt; das Feld zeigt dann den Hinweis „Dieser Anbieter nimmt keinen Kontext entgegen …" und wirkt weiter bei anderen Anbietern und offline.

---

## 9. Offline-Modus

Im Offline-Modus erkennt WhisperLoom Sprache direkt auf dem Gerät — mit whisper.cpp und einem Whisper-Modell, das du einmalig herunterlädst. Danach braucht die Erkennung kein Internet, keinen Key und schickt nichts weg.

### 9.1 Voraussetzungen

- **Prozessor:** 64-Bit-ARM (arm64-v8a) mit FP16-Vektorrechnung und DotProd (Armv8.2 — Cortex-A55/A75 und neuer, also praktisch alle Geräte ab etwa 2018). Ältere Chips (Cortex-A53/A72, z. B. Snapdragon 835/660) fehlen diese Befehle; dort meldet WhisperLoom „Offline-Erkennung wird von diesem Gerät nicht unterstützt (CPU ohne FP16/DotProd)" bzw. bietet Offline gar nicht erst an. Diese Geräte wären für die Erkennung ohnehin zu langsam.
- **Arbeitsspeicher:** Für Small etwa 430 MB frei; Geräte mit weniger als etwa 3 GB RAM sind ungeeignet. **Large v3 Turbo** braucht rund 1 GB und wird nur auf Geräten mit mindestens 6 GB angeboten — sonst ist es ausgegraut („Für dieses Gerät zu groß").
- **Speicherplatz:** 32–574 MB je Modell, dauerhaft im App-Speicher.

### 9.2 Die Modelle

Einstellungen → **Offline-Modelle** (oder Schritt 2b im Assistenten). „Modelle werden einmalig von huggingface.co geladen und bleiben auf dem Gerät. Kein Modell ist in der App enthalten."

| Modell | Download | Arbeitsspeicher | Einschätzung | Empfehlung |
|---|---|---|---|---|
| **Tiny** | 32 MB | ~250 MB | nur zum Ausprobieren — für Deutsch zu ungenau | — |
| **Base** | 60 MB | ~355 MB | schnell, kurze Sätze | wenn Small zu langsam ist |
| **Small** | 190 MB | ~430 MB | gute Qualität für Deutsch | **Empfohlen** (Voreinstellung) |
| **Large v3 Turbo** | 574 MB | ~1 GB | beste Qualität, langsam, ab 6 GB Gerätespeicher | nur für geduldige Nutzer mit starkem Gerät |

Die Modelle sind quantisierte Versionen (q5) der OpenAI-Whisper-Modelle aus dem Repository `huggingface.co/ggerganov/whisper.cpp`; alle sind mehrsprachig. Quelle und Prüfsummen sind fest in der App hinterlegt — eine beschädigte oder veränderte Datei wird abgelehnt.

### 9.3 Laden, auswählen, löschen

- **Laden (190 MB):** Am besten im WLAN. Über mobile Daten fragt WhisperLoom: „Über mobile Daten laden? — 190 MB werden heruntergeladen. Im WLAN ist das kostenlos." → **Laden** oder **Abbrechen**.
- Der Download läuft in einem Hintergrund-Dienst weiter, auch wenn du den Bildschirm verlässt oder WhisperLoom schließt. Fortschritt in der Liste („42 % · 80 MB von 190 MB · 3,1 MB/s") und in der Benachrichtigung **Modell wird geladen** (mit **Abbrechen**). Am Ende: „Small ist bereit".
- **Abgebrochen oder Verbindung weg?** Ein Netzabbruch wird bis zu dreimal automatisch wiederholt und setzt an der Stelle fort, an der es aufhörte. Nach einem Fehler zeigt die Zeile „Fehlgeschlagen: …" mit **Erneut** — auch das setzt den Download fort, nicht neu. Nur ein Abbruch durch dich verwirft die Teildatei.
- Nach dem Download prüft WhisperLoom Größe und SHA-256-Prüfsumme. Stimmt etwas nicht: „Datei beschädigt — erneut laden".
- **Auswählen:** Der Radio-Button links markiert das aktive Modell (nur bei geladenen Modellen wählbar). Ein Wechsel greift beim nächsten Diktat.
- **Löschen:** Papierkorb-Symbol → „Small löschen? — 190 MB werden frei. Für die Offline-Erkennung muss dann ein anderes Modell geladen werden." Ist es das aktive und einzige Modell, warnt der Dialog zusätzlich („Dies ist das aktive Modell — Offline ist danach nicht einsatzbereit."); der Startbildschirm zeigt dann das Banner „Offline gewählt, aber kein Modell geladen."
- Die Zeile „Belegt: … · Frei: …" zeigt, was die Modelle auf dem Gerät belegen.

Unter Erkennung → Offline-Modell steht das aktive Modell mit **Ändern**. Hinweis dort: „Erste Nutzung lädt das Modell in den Speicher (2–5 s)." Danach bleibt es geladen; erst wenn du die WhisperLoom-Oberfläche öffnest und wieder verlässt oder der Arbeitsspeicher knapp wird, gibt WhisperLoom das Modell frei, und das nächste Diktat lädt es erneut.

### 9.4 Genauigkeit

Die Offline-Erkennung arbeitet fest mit Beam-Search (fünf Kandidaten — derselbe Modus wie die whisper.cpp-Kommandozeile; weniger Abbrüche und Halluzinationen als Greedy). Einen Umschalter auf den schnelleren, etwas ungenaueren Greedy-Modus gibt es in 3.0.0 nicht; für Small ist der Laufzeit-Unterschied ohnehin klein, weil der Encoder dominiert. Wer Tempo braucht, wählt ein kleineres Modell (Base).

### 9.5 Wie lange dauert es?

Nur eine **grobe Größenordnung** — WhisperLoom wurde auf keinem Gerät vermessen; die Werte sind aus Erfahrungsberichten zu whisper.cpp auf Mittelklasse-ARM-Geräten (4 Threads) für etwa 10 Sekunden Sprache extrapoliert:

| Modell | Größenordnung für 10 s Sprache |
|---|---|
| Tiny | 1–2 s |
| Base | 2–4 s |
| Small | 5–12 s |
| Large v3 Turbo | 20–60 s (sehr unsicher) |

Whisper rechnet immer über ein 30-Sekunden-Fenster — ein 3-Sekunden-Diktat ist also kaum schneller als ein 10-Sekunden-Diktat. Ist dir Small zu langsam, hilft Base; ist die Qualität nicht gut genug, hilft der Online-Dienst.

Was die Qualität offline am meisten verbessert, in dieser Reihenfolge: das größere Modell · eine fest eingestellte Sprache statt „Automatisch erkennen" · der Kontext-Prompt mit deinen Eigennamen · der Modus „Genau".

### 9.6 Online und offline kombinieren

Die Umschaltung zwischen **Online-Dienst** und **Offline-Modell** steht unter Erkennung ganz oben (und in Offline-Modelle). Beide Zugänge bleiben gespeichert — du kannst also mit Offline im Flugzeug diktieren und zu Hause auf den Online-Dienst zurückwechseln. Die KI-Textverbesserung braucht in jedem Fall einen Online-Zugang (siehe [8.2](#82-zugang-für-die-textverbesserung)); der Anbieter der Erkennung und der Anbieter der Textverbesserung dürfen verschieden sein.

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

Ollama braucht keinen Key. Auf 4 CPU-Kernen liefert ein 8B-Modell ≈ 5–8 Wörter pro Sekunde — die Textverbesserung eines längeren Diktats dauert also spürbar; bei Bedarf `gemma3:4b` nehmen oder die Stufe auf „Aus" lassen. WhisperLoom schaltet beim Eigenen Server das „Nachdenken" von Qwen3 & Co. automatisch ab (`reasoning_effort: none`) und entfernt trotzdem versehentlich mitgelieferte Denk-Blöcke aus der Antwort.

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

`WHISPERLOOM_TOKEN` als Umgebungsvariable des Caddy-Dienstes setzen (nicht in die Datei schreiben). In WhisperLoom: Base-URL `https://whisper.example.de/v1`, API-Key = Token. Vorteil: Erkennung und Textverbesserung laufen hinter **einer** URL — in WhisperLoom bleibt „Eigenen Zugang verwenden" dann einfach aus, und die Textverbesserung nutzt automatisch denselben Zugang.

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
```

Bei Fehler: `docker logs speaches` bzw. `journalctl -u ollama`.

### Schritt 5 — In WhisperLoom eintragen

**Erkennung** (Einstellungen → Erkennung, oder Schritt 2a im Assistenten) → Anbieter **Eigener Server**:

| Feld | Wert |
|---|---|
| Base-URL | `http://SERVER:8000/v1` (LAN/Tailscale) oder `https://whisper.example.de/v1` (Caddy). „Muss auf /v1 enden. http:// nur im eigenen Netz (LAN/VPN)." |
| API-Key (optional) | leer, oder der in `API_KEY`/Caddy gesetzte Token |
| Modell | `Systran/faster-whisper-medium` (speaches) · `whisper-1` (LocalAI/hwdsl2) · beliebig (whisper.cpp) — per **Eigenes Modell …** eintragen |
| Zeitüberschreitung | fest 600 s beim Eigenen Server (Cloud-Anbieter 90 s) — CPU-Server brauchen bei langen Aufnahmen Minuten; nicht einstellbar |

Dann **Zugang prüfen**.

**Textverbesserung** (Einstellungen → Text): Läuft Ollama auf einem eigenen Port (Variante ohne Caddy), Schalter **Eigenen Zugang verwenden** ein → Anbieter **Eigener Server** → Base-URL `http://SERVER:11434/v1`, Key leer, Modell `qwen3:8b`. Hinter Caddy mit einer gemeinsamen URL bleibt der Schalter aus.

**http oder https?** Unverschlüsseltes `http://` akzeptiert WhisperLoom ohne Warnung nur zu privaten Adressen (192.168.x.x, 10.x.x.x, 172.16–31.x.x, 100.64–127.x.x/Tailscale, `localhost`, `*.local`, `*.lan`, `*.home.arpa`, `*.internal`). Bei einer öffentlichen Adresse warnt das Feld „Unverschlüsselt über das Internet — https:// oder VPN (Tailscale) verwenden". Über das Internet immer `https://` oder VPN. Die Cloud-Anbieter aus der Liste sind fest auf https eingestellt.

---

## 11. Datenschutz

WhisperLoom hat keinen eigenen Server, kein Konto, keine Telemetrie. Was mit deinen Daten passiert, hängt allein vom gewählten Erkennungsweg ab:

**Online:** Audio und Kontext-Prompt gehen an den gewählten Anbieter. Bei Textverbesserung geht der erkannte Text an das Sprachmodell (denselben oder einen anderen Anbieter). Dein Key bleibt auf dem Gerät. WhisperLoom speichert keine Aufnahmen. Unter Erkennung steht immer, an wen gesendet wird („Audio wird zur Erkennung an OpenAI gesendet."). Was der Anbieter mit den Daten macht, regeln dessen Bedingungen — bei Google Gemini im Free-Tier ausdrücklich Trainingsnutzung, bei DeepSeek Server in China; WhisperLoom weist an der Auswahl darauf hin.

**Offline:** Nichts verlässt das Gerät — nur der Modell-Download geht ins Netz (zu huggingface.co).

**Eigener Server:** Audio und Text gehen nur an deinen Server.

**Bedienungshilfe:** Die Bedienungshilfe „WhisperLoom Text-Einfügen" liest nichts mit und speichert nichts; sie fügt nur den diktierten Text in das fokussierte Feld ein. Android zeigt beim Aktivieren die übliche Warnung für Bedienungshilfen („kann Bildschirminhalte lesen") — WhisperLoom nutzt davon ausschließlich das Einfügen.

**Auf dem Gerät gespeichert:** Deine Einstellungen inklusive API-Key (im privaten App-Speicher, für andere Apps unzugänglich), die heruntergeladenen Modelle und die Position des Knopfs. Keine Aufnahmen, keine Texte, keine Verläufe. Ein fehlgeschlagenes Diktat bleibt nur so lange im Arbeitsspeicher gepuffert, bis du es erneut sendest oder verwirfst. WhisperLoom ist vom Android-System-Backup ausgenommen (`allowBackup=false`): die Einstellungen inklusive Key landen weder im Google-Backup noch im Geräte-zu-Gerät-Transfer — nach einem Gerätewechsel richtest du den Zugang neu ein.

**Berechtigungen:** Mikrofon (Aufnahme), Internet (Online-Dienst und Modell-Download), Über anderen Apps anzeigen (Knopf), Benachrichtigungen (Beenden-Aktion und Download-Fortschritt), Netzwerkstatus (Nachfrage vor Downloads über mobile Daten), Vordergrund-Dienste (Knopf und Modell-Download).

---

## 12. Wenn etwas nicht klappt

Die häufigsten Punkte stehen auch in der App unter Anleitung & Hilfe → **Wenn etwas nicht klappt**, jeweils mit einem Button zum passenden Ziel.

**Der Knopf erscheint nicht.**
„Über anderen Apps anzeigen" muss erlaubt sein (Einstellungen → Knopf & Tastatur → Berechtigungen). Zusätzlich in den Android-Einstellungen die **Akku-Optimierung** für WhisperLoom ausschalten (Apps → WhisperLoom → Akku → „Nicht eingeschränkt"/„Uneingeschränkt"), sonst beendet Android den Dienst im Hintergrund. Einige Hersteller (Xiaomi, Huawei, Oppo …) haben eigene Autostart-/Hintergrund-Sperren — WhisperLoom dort freigeben. Zeigt WhisperLoom „„Über anderen Apps anzeigen" wurde entzogen" → **Erlauben**.

**Der Text landet nur in der Zwischenablage.**
Die Bedienungshilfe „WhisperLoom" ist aus. Aktivieren (Einstellungen → Knopf & Tastatur → Berechtigungen → Bedienungshilfe → **Öffnen**), dann fügt WhisperLoom den Text direkt ein. In manchen Apps ist das Feld kein normales Textfeld (etwa in einigen Spielen oder Terminal-Apps) — dann bleibt die Zwischenablage der Weg.

**„Eingeschränkte Einstellung" beim Aktivieren der Bedienungshilfe.**
Bei manuell installierten Apps: App-Info → ⋮ → **Eingeschränkte Einstellungen zulassen**, dann die Bedienungshilfe erneut aktivieren (siehe [Schritt 5](#schritt-5--text-automatisch-einfügen-empfohlen)).

**„Key ungültig (401)" oder „Server verlangt einen (anderen) API-Key".**
Key beim Anbieter neu erzeugen und einfügen (Zwischenablage-Symbol im Key-Feld, keine Leerzeichen). Prüfen, ob der Key zum gewählten Anbieter gehört — ein OpenAI-Key funktioniert nicht bei Groq. Dann **Zugang prüfen**.

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

**Offline ist zu langsam.**
Ein kleineres Modell wählen (Base oder Small) oder auf den Online-Dienst wechseln. Auch andere gleichzeitig laufende Apps bremsen — die Erkennung nutzt alle Performance-Kerne.

**„Datei beschädigt — erneut laden" / „Offline-Modell konnte nicht geladen werden".**
Modell unter Offline-Modelle löschen und neu laden. Tritt der Fehler direkt nach dem Download auf, war die Übertragung fehlerhaft; WhisperLoom lädt das Modell beim nächsten Versuch neu.

**Der Modell-Download bricht ab.**
„Netzwerkfehler beim Laden": WhisperLoom wiederholt bis zu dreimal automatisch und setzt dann mit **Erneut** an derselben Stelle fort (Teildatei bleibt erhalten). „Nicht genug Speicherplatz": Platz schaffen. „Zeitlimit für Hintergrund-Downloads erreicht — bitte erneut starten": Android begrenzt Hintergrund-Downloads dieser Art auf sechs Stunden pro Tag — erneut starten, der Download läuft weiter.

**„Kein Zugang eingerichtet — in WhisperLoom einrichten" / „Kein Offline-Modell geladen — unter Offline-Modelle laden".**
Die Erkennung ist nicht vollständig eingerichtet: online fehlt Key oder URL, offline das Modell. Der Startbildschirm zeigt den Grund in der Karte Status.

**„Offline-Erkennung wird von diesem Gerät nicht unterstützt (CPU ohne FP16/DotProd)".**
Der Prozessor ist zu alt für die eingebaute Offline-Engine (siehe [9.1](#91-voraussetzungen)). Online-Dienst oder Eigener Server verwenden.

**Der erkannte Text ist auf Englisch, obwohl ich Deutsch spreche.**
Sprache unter Erkennung → Sprache & Kontext auf „Deutsch" stellen statt „Automatisch erkennen". Beim eigenen whisper-server zusätzlich `-l auto` oder `-l de` beim Start.

**Nach dem Update fehlt mein Modell / meine Einstellung.**
Einstellungen aus 2.x werden übernommen (Key, URL, Modell, Sprache, Regeln). Offline-Modelle aus Version 1.x (Tag `offline-v1`) werden nicht übernommen — in 3.0.0 unter Offline-Modelle neu laden.

---

## 13. Sprachauftrag an einen eigenen Agenten

Das ist die einzige Funktion in WhisperLoom, die einen Server voraussetzt, den du selbst betreibst. Wenn du keinen hast, überspring dieses Kapitel — du wirst von der Funktion sonst nirgendwo etwas merken, sie ist ab Werk aus.

**Die Idee:** Auf deinem Startbildschirm liegt ein Widget. Du tippst darauf, sprichst deinen Auftrag, tippst noch einmal — WhisperLoom schreibt mit und schickt den Text an deinen eigenen Agenten. Der arbeitet den Auftrag ab und antwortet dort, wo du ihm sonst schreibst. Die App wartet nicht auf die Antwort; sie ist nach ein paar Sekunden fertig.

### 13.1 Was du brauchst

- Einen erreichbaren Server mit der **Bridge** (dem kleinen Gegenstück, das den Auftrag entgegennimmt und an deinen Agenten übergibt). Adresse und ein **Token** bekommst du von dort.
- Eine eingerichtete Erkennung — online oder offline, beides geht. Das Mitschreiben läuft genau so wie beim normalen Diktat.
- Die Mikrofon-Berechtigung. Die hast du aus der Einrichtung meist schon.

### 13.2 Einrichten

Einstellungen → **Erweiterte Optionen**.

1. **Sprachauftrag aktivieren** einschalten.
2. **Server-Adresse** eintragen — die Basis-Adresse ohne Pfad, zum Beispiel `https://bridge.example.de`. Unverschlüsseltes `http://` nimmt WhisperLoom nur für Adressen im Heimnetz oder VPN an; ins offene Internet gibt es eine Warnung.
3. **Token** eintragen. Das Auge zeigt es kurz im Klartext, das Klemmbrett-Symbol fügt es aus der Zwischenablage ein.
4. **Verbindung prüfen** antippen. Der Chip darunter sagt dir sofort, ob Adresse und Token stimmen. Die Prüfung löst **keinen** Auftrag aus — es geht nichts an deinen Agenten.

### 13.3 Das Widget auf den Startbildschirm legen

Drücke lange auf eine freie Stelle deines Startbildschirms → **Widgets** → **WhisperLoom** → **Sprachauftrag** dorthin ziehen, wo du es haben willst. Es lässt sich in der Größe ändern; zwei mal zwei Felder sind die Voreinstellung.

Unter Erweiterte Optionen → **Anleitung ansehen** liegt dieselbe Erklärung noch einmal als bebildertes Tutorial.

### 13.4 Benutzen — die vier Zustände

| Widget zeigt | Bedeutung | Ein Tipp … |
|---|---|---|
| Mikrofon, „Tippen und sprechen" | bereit | startet die Aufnahme |
| rot, laufende Zeit | nimmt auf | beendet die Aufnahme und schickt den Auftrag los |
| blau, „Wird gesendet …" | schreibt mit und überträgt | tut nichts (mit Absicht — ein ungeduldiger zweiter Tipp soll nichts kaputt machen) |
| rot mit Fehlergrund | etwas hat nicht geklappt | schickt **denselben** Auftrag noch einmal, ohne neu aufzunehmen |

Nach dem Absenden darf der Bildschirm ausgehen: Mitschreiben und Übertragen laufen als Auftrag im System weiter und überstehen auch ein kurzes Funkloch. Ist der Auftrag draußen, wird das Widget grün („Gesendet") und bleibt so, bis du das nächste Mal darauf tippst — dann beginnt wie gewohnt eine neue Aufnahme.

Vergisst du das Beenden, macht das Widget nach fünf Minuten von allein Schluss und schickt das Gesprochene ab. Eine Aufnahme läuft also nie unbemerkt weiter.

### 13.5 Wenn etwas nicht klappt

**„Mikrofon nicht erlaubt — tippen"**
Der Tipp führt dich in die Erweiterten Optionen; dort erlaubst du das Mikrofon.

**„Sprachauftrag ist aus — tippen"**
Der Schalter ist aus, oder Adresse bzw. Token fehlen. Der Tipp führt direkt zur richtigen Stelle.

**„Kein Ton aufgenommen"**
Es kam nichts am Mikrofon an — etwa weil eine andere App es belegt (Telefonat, Sprachassistent) oder Android es der App im Hintergrund entzogen hat. Ein Tipp startet einen neuen Anlauf.

**„Zu kurz — länger sprechen"**
Der zweite Tipp kam zu schnell. Unter einer knappen Sekunde ist es ein Fehlgriff, kein Auftrag.

**Das Widget bleibt auf „Wird gesendet …" stehen**
Meistens fehlt nur das Netz — der Auftrag wartet und geht von allein raus, sobald wieder Empfang da ist. Ein Tipp auf die Fläche sieht nach, ob der Auftrag überhaupt noch eingeplant ist, und reiht ihn nötigenfalls neu ein. Willst du ihn loswerden: Einstellungen → Erweiterte Optionen → **Offenen Auftrag verwerfen**.

**Ein Fehler mit Zahl (z. B. 401 oder 503)**
401 heißt: Token stimmt nicht — korrigier es in den Erweiterten Optionen und tippe dann auf das Widget, der Auftrag ist noch da. 503 heißt: die Bridge erreicht deinen Agenten gerade nicht; WhisperLoom versucht es von allein mehrmals erneut, erst danach wird das Widget rot.

### 13.6 Was dabei gesendet wird

An deinen Server gehen: der erkannte Text, eine Auftragskennung, Zeitpunkt und Dauer der Aufnahme. **Nur an die Adresse, die du einträgst** — nicht an den Hersteller der App und an niemanden sonst. Die Aufnahme selbst bleibt auf dem Telefon; fürs Mitschreiben geht sie an den Erkennungsweg, den du ohnehin eingestellt hast (Kapitel 11 beschreibt das im Detail). Ein Auftrag, der noch nicht durchging, liegt so lange auf dem Telefon, bis er abgeschickt oder ersetzt wird; er wird nicht in ein Cloud-Backup übernommen.

---

## 14. Häufige Fragen

**Was kostet WhisperLoom?**
Die App ist kostenlos und quelloffen (MIT-Lizenz). Kosten entstehen nur beim Online-Anbieter — mit Groq gar keine, mit OpenAI GPT Transcribe etwa $0,0045 pro Diktat-Minute (Stand 09/2026, ohne Gewähr). Der Offline-Modus ist komplett kostenlos.

**Brauche ich Google Play oder ein Google-Konto?**
Nein. WhisperLoom wird als APK installiert und braucht keine Google-Dienste. Nur wenn du Google Gemini für die Textverbesserung wählst, brauchst du ein Google-Konto für den Key.

**Muss ich meine Tastatur wechseln?**
Nein. Der schwebende Knopf arbeitet mit jeder Tastatur zusammen (Gboard, SwiftKey, …) — die Tastatur bleibt, wie sie ist. Die Diktat-Tastatur ist nur eine Alternative.

**Kann ich online und offline gleichzeitig eingerichtet haben?**
Ja. Beide Zugänge bleiben gespeichert; unter Erkennung schaltest du mit einem Tipp um.

**Welche Sprachen?**
Deutsch (Voreinstellung), Englisch, Spanisch, Französisch, Italienisch oder „Automatisch erkennen". Die Erkennungsmodelle selbst beherrschen deutlich mehr Sprachen; die Auswahl in WhisperLoom umfasst die fünf Sprachen, für die eingebaute Füllwort-Listen mitkommen. Für andere Sprachen „Automatisch erkennen" wählen.

**Wie lang darf ein Diktat sein?**
Ein Diktat per Knopf oder Tastatur wird am Stück an die Erkennung geschickt; die Online-Anbieter nehmen höchstens 25 MB pro Anfrage (etwa 13 Minuten bei WhisperLooms Audioformat). Für die Praxis: einzelne Sätze bis wenige Minuten. Geteilte Sprachnachrichten dürfen beliebig lang sein — sie werden in 5-Minuten-Stücke geteilt.

**Warum ist die Erkennung offline so viel langsamer als online?**
Die Online-Anbieter rechnen auf Grafikkarten-Servern; auf dem Telefon läuft das Modell auf der CPU. Dafür geht offline nichts nach außen. Small ist der Kompromiss; wer Geduld hat, bekommt mit Large v3 Turbo fast Online-Qualität.

**Speichert WhisperLoom meine Diktate?**
Nein — weder Audio noch Text. Siehe [Kapitel 11](#11-datenschutz).

**Kann ich Namen und Fachbegriffe hinterlegen?**
Ja: Erkennung → Sprache & Kontext → „Kontext: Namen, Fachbegriffe, Schreibweisen". Wirkt online und offline, kostet nichts extra.

**Was passiert beim Update von 2.x auf 3.0.0?**
Deine Einstellungen werden automatisch übernommen: Base-URL, Key, Modell und Kontext-Prompt landen unter Erkennung (Anbieter „OpenAI" bzw. „Eigener Server", je nach URL), die Option „Text von der KI glätten lassen" wird zur Stufe **Glätten**, die Füllwort-/Groß-Schreib-/Leerzeichen-Regeln bleiben. Dein bisheriges Modell bleibt eingetragen (GPT-4o Transcribe wird als Auslauf-Modell gekennzeichnet); neue Installationen starten mit **GPT Transcribe**. Da 3.0.0 mehr Berechtigungen kennt (Benachrichtigungen ab Android 13), kann der Assistent einmalig die noch offenen Schritte zeigen.

**Warum fragt WhisperLoom nach „Über anderen Apps anzeigen" und einer Bedienungshilfe?**
Der Knopf muss über anderen Apps liegen (Overlay), und um Text in ein fremdes Textfeld zu schreiben, ohne die Tastatur zu wechseln, gibt es unter Android nur den Weg über eine Bedienungshilfe. Beides ist optional: Mit „Nur Tastatur nutzen" kommt WhisperLoom ohne beides aus.

**Was passiert beim Drehen des Bildschirms?**
Der Knopf bleibt im sichtbaren Bereich; eine im Querformat gemerkte Position wird im Hochformat korrigiert. Auf Tablets ist WhisperLoom nicht gesondert getestet.

**Wo finde ich diese Anleitung in der App?**
Einstellungen → **Anleitung & Hilfe** (oder das **?** oben rechts auf dem Startbildschirm): So funktioniert's · Einrichtung Schritt für Schritt · API-Key bekommen · Eigener Server · Offline-Modus · Datenschutz · Wenn etwas nicht klappt.

**Wo melde ich Fehler?**
Auf der GitHub-Seite des Projekts (Link unter Einstellungen → Über WhisperLoom → „Quellcode auf GitHub"). Hilfreich sind Android-Version, Gerät, Erkennungsweg (online/offline, Anbieter, Modell) und die genaue Fehlermeldung aus der App.
