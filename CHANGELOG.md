# Changelog

Alle nennenswerten Änderungen an WhisperLoom. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [3.5.0] — 2026-09-24

### Hinzugefügt

- **Vokabular als Liste statt Freitextfeld.** Einstellungen → Erkennung → **Vokabular** → **Bearbeiten** öffnet eine Liste: Namen, Fachbegriffe und Schreibweisen einzeln hinzufügen (mehrere auf einmal mit Komma) und einzeln löschen. Ein vorhandener Freitext bleibt erhalten: jede seiner Zeilen wird ein Eintrag.
- **Vokabular aus einer Datei.** Eine .md- oder .txt-Datei lässt sich dauerhaft verknüpfen; sie wird bei **jedem Diktat neu gelesen** — Änderungen an der Datei wirken sofort. Eine Zeile ist ein Begriff; Aufzählungszeichen, Checkboxen und Hervorhebungen fallen weg, Überschriften und Codeblöcke werden übersprungen. Mitgeschickt werden höchstens 800 Zeichen, weil Whisper nur rund 200 Wörter Kontext beachtet — und zwar die am Ende. Deshalb stehen die eigenen Begriffe hinten und haben Vorrang; wird es zu lang, fallen zuerst die vorderen Datei-Begriffe weg. Das Sheet sagt, wie viele Begriffe ankommen. Eine verschwundene Datei bricht kein Diktat ab.
- **Textverbesserung mit Ollama.** Zwei neue Anbieter: **Ollama (lokal / Homeserver)** mit Server-Adresse und ohne Key — der Text verlässt das eigene Netz nicht — und **Ollama Cloud** mit Key von ollama.com. Sobald Ollama verbunden ist, lädt die App die Modelle vom Server ins Auswahlfeld; über **Eigenes Modell …** lässt sich jeder Name eintragen. Empfohlen für die Cloud: Gemma 4 31B (schnell, denkt nicht nach). Alle bisherigen Anbieter bleiben.
- **Schalter „Automatische Absätze“** unter Einstellungen → Text. An (wie bisher): Die KI gliedert längere Diktate in Absätze. Aus: ein durchgehender Text — auch wenn das Modell trotzdem Umbrüche liefert.

### Behoben

- **Die App sprang beim Tippen.** Android schob das ganze Fenster hoch, sobald die Tastatur aufging — die Titelleiste verschwand, der Inhalt rutschte unter die Statusleiste, und bei jeder neuen Zeile ruckte es erneut. Jetzt macht der Inhalt der Tastatur Platz, und das Feld scrollt ruhig in den sichtbaren Bereich.
- **Einstellungen → Text:** Wer „Eigenen Zugang verwenden“ aus- und wieder einschaltete, nahm Adresse und Key des vorherigen Anbieters mit — der Key konnte so beim falschen Anbieter landen. Die Felder werden jetzt geleert, wie beim Anbieterwechsel.
- Die Diktat-Tastatur zählt einen Textverbesserungs-Zugang ohne Modell (Ollama lokal, eigener Server) nicht mehr als bereit.
- **Ollama Cloud war vom Handy aus nicht erreichbar:** ollama.com sperrt die Standard-Kennung von Android-Apps. WhisperLoom meldet sich jetzt mit eigener Kennung, und „Zugang prüfen" unterscheidet „Zugriff verweigert (403)" von „Key ungültig (401)".

### Sicherheit

- ollama.com steht in der https-Pflicht-Liste der App; unverschlüsseltes http:// bleibt dem eigenen Netz vorbehalten.

## [3.4.1] — 2026-09-22

### Behoben

- **Der Sprachauftrag sagt jetzt, wenn die Textverbesserung ausgefallen ist.** Scheitert die KI-Stufe (Anbieter zickt, Zeitüberschreitung, Rate-Limit), ging wie überall der Rohtext raus — beim Widget aber stillschweigend. Wer „Glätten" eingeschaltet hatte, bekam ungeglätteten Text und hielt die Erkennung für schlecht. Das Widget meldet den Ausfall jetzt („Gesendet — ohne Textverbesserung"), so wie es die Diktat-Tastatur schon immer getan hat.

### Geändert

- Store-Beschreibungen in beiden Sprachen nachgezogen: Wisch-Geste, Schnellzugriff auf die Textstufen und der Sprachauftrag fehlten dort.

## [3.4.0] — 2026-09-21

### Hinzugefügt

- **Sprachauftrag an einen eigenen Agenten (Widget).** Ein Widget auf dem Startbildschirm nimmt auf, WhisperLoom schreibt mit und schickt den Text an einen Server, den du selbst betreibst; die Antwort kommt später dort, wo du deinem Agenten sonst schreibst. Vier Zustände (bereit · nimmt auf mit laufender Zeit · arbeitet · Fehler), ein Tipp auf den Fehler schickt **denselben** Auftrag erneut, ohne neu aufzunehmen oder noch einmal zu transkribieren. Mitschreiben und Übertragen laufen als Auftrag im System weiter — ausgeschalteter Bildschirm und kurze Funklöcher überstehen sie.
- **Einstellungen → Erweiterte Optionen** (neue, siebte Zeile im Hub): Schalter, Server-Adresse, Token, „Verbindung prüfen" (löst keinen Auftrag aus) und ein eigenes bebildertes Tutorial mit vier Seiten. Ab Werk **aus**; wer die Funktion nicht nutzt, merkt sonst nichts davon.

- Bleibt ein Auftrag hängen, kommt man wieder heraus: ein Tipp auf „Wird gesendet …" reiht ihn nötigenfalls neu ein, und unter Erweiterte Optionen lässt er sich verwerfen. Eine vergessene Aufnahme beendet sich nach fünf Minuten von selbst und wird abgeschickt.

### Sicherheit

- Der offene Sprachauftrag (Transkript und Aufnahme) liegt in einer eigenen Datei auf dem Gerät und ist von Cloud-Backup und Geräte-Transfer ausgenommen.

## [3.3.0] — 2026-09-21

### Hinzugefügt

- **Wisch-Geste in der Diktat-Tastatur:** Beim Aufnehmen nach rechts ziehen stellt die Aufnahme fest — sie läuft freihändig weiter, die Statuszeile zählt die Dauer mit, und aus den beiden Wisch-Zielen werden die Tasten **Verwerfen** und **Senden**. Nach links ziehen verwirft sofort. Loslassen ohne Ziehen sendet wie bisher.

- **Textverbesserung direkt in der Tastatur:** Der Zauberstab rechts über dem Mikrofon klappt eine Zeile mit den vier Stufen auf (Aus · Glätten · Schöner · Kürzen). Die Wahl gilt ab dem nächsten Diktat und ist dieselbe Einstellung wie unter Einstellungen → Text. Ohne KI-Zugang sind die drei oberen Stufen ausgegraut.

### Behoben

- **Einstellungen zeigten veraltete Werte**, nachdem die Diktat-Tastatur etwas geändert hatte — sie ist ein eigener Dienst, und der Einstellungs-Screen bekam davon nichts mit.
- **Diktat-Tastatur mit TalkBack bedienbar:** Die Mikrofon-Taste reagierte nur auf Gedrückthalten und war damit über Bedienungshilfen faktisch nicht zu bedienen. Über Bedienungshilfen startet ein Antippen die Aufnahme jetzt festgestellt, ein zweiter Tipp sendet; die Beschreibung der Taste folgt dem Zustand. (Ohne Bedienungshilfen bleibt es beim Halten — ein kurzer Tipp ist weiterhin zu kurz für ein Diktat und wird verworfen.)

## [3.2.0] — 2026-09-07

### Hinzugefügt

- **Illustriertes Tutorial nach der Einrichtung** (Knopf, Tastatur, Sprachnachrichten aus WhatsApp abtippen, Ergebnis): vier Seiten, einmalig nach „Knopf starten & los" bzw. beim ersten Start nach dem Update; erneut unter Anleitung & Hilfe → „Tutorial erneut ansehen", „Mehr" beim Sprachnachrichten-Hinweis auf dem Startbildschirm springt direkt zur passenden Seite.

### Geändert

- **Neuer Name: WhisperLoom.** Neue Paket-ID `com.chris.whisperloom` — als neue App installieren, vorherige Version deinstallieren, Einstellungen einmal neu eingeben. Signaturschlüssel unverändert.
- Der schwebende Knopf startet nach dem Tutorial, nicht mehr darüber.

## [3.1.0] — 2026-09-07

### Geändert

- **Neuer Name, neue Paket-ID** — die App wird als neue App installiert; die vorherige Version deinstallieren und API-Key/Einstellungen einmal neu eingeben. Signaturschlüssel unverändert.

## [3.0.0] — 2026-09-07

Komplett neue Oberfläche, Offline-Erkennung zurück, Anbieter-Katalog, Textverbesserung in Stufen.

### Hinzugefügt

- **Einrichtungs-Assistent** mit sieben Schritten (Erkennungsweg · Zugang oder Modell · Mikrofon · Über anderen Apps anzeigen · Bedienungshilfe · Benachrichtigungen · Diktat-Tastatur), Status-Chips, Schritt-Übersicht, Willkommens- und Abschlussseite; „Nur Tastatur nutzen" als Weg ohne Overlay.
- **Startbildschirm** mit Hauptaktion „Mikro-Knopf starten/beenden", Status-Karte (Erkennung, Textverbesserung, Berechtigungen, Diktat-Tastatur, Offline-Modelle) und Hinweis-Banner „Noch nicht optimal".
- **Anbieter-Katalog** für die Erkennung: OpenAI, Groq, Mistral, Together AI, DeepInfra, OpenRouter, Eigener Server — mit Modellen, Base-URLs, Key-Links und Anbieter-Flags (`languages[]` bei GPT Transcribe, `prompt`/`response_format` je Anbieter, Pfad-Override DeepInfra). Für die Textverbesserung zusätzlich Anthropic (Claude), Google Gemini, DeepSeek (mit Hinweisen zu Test-Schicht, Trainingsnutzung, Serverstandort).
- **Getrennte Zugänge** für Erkennung und Textverbesserung („Eigenen Zugang verwenden"); ein anderer Anbieter erbt nie den Erkennungs-Key.
- **„Zugang prüfen"** für Erkennung und Textverbesserung; „Wo bekomme ich einen Key?" mit Schritten je Anbieter; „Eigenes Modell …" als freie Modell-ID.
- **Textverbesserung in Stufen:** Aus · Glätten · Verschönern · Zusammenfassen; `<think>`-Blöcke von Reasoning-Modellen werden entfernt; `temperature` nur bei Modellen, die es unterstützen, sonst `reasoning_effort`/`max_completion_tokens`.
- **Füllwörter bearbeiten:** eingebaute Wörter je Sprache abwählbar, eigene Wörter (auch mehrwortig) hinzufügbar, „Standard wiederherstellen".
- **Offline-Erkennung** mit whisper.cpp v1.9.3: Modelle Tiny (32 MB), Base (60 MB), Small (190 MB, empfohlen), Large v3 Turbo (574 MB, ab 6 GB RAM) werden bei Bedarf von huggingface.co geladen — Download-Dienst mit Benachrichtigung und Abbrechen, Fortsetzen nach Abbruch (Range-Resume), automatische Wiederholung, SHA-256- und Größenprüfung, Nachfrage über mobile Daten, Löschen mit Bestätigung. Kein Modell im APK. Beam-Search (fünf Kandidaten), Kontext-Prompt als `initial_prompt`, Unterdrückung von Nicht-Sprach-Tokens, Abbruch, erkannte Sprache bei „Automatisch erkennen", Freigabe des Modells bei Speicherdruck. Laufzeit-Guard: nur arm64 mit FP16 + DotProd.
- **Eigener Server:** Key optional (ohne Key kein Authorization-Header), `http://` zu privaten Adressen (LAN, Tailscale, `.local` …) mit Warnung bei öffentlichen, Read-Timeout 600 s, `reasoning_effort: none` für Ollama/Qwen3, lesbare Netz- und Statusfehler („Server nicht erreichbar — …", „Base-URL muss auf /v1 enden", Cleartext-Hinweis).
- **Transkription geteilter Sprachnachrichten:** Absätze (Heuristik `Paragrapher`), Schalter „Füllwörter ausblenden" (Standard an, ausgeschaltet wortgetreu), Erneut je Datei und „Alles erneut", „Einrichtung öffnen" bei fehlendem Zugang, Anzeige des aktiven Backends.
- **Schwebender Knopf** neu: 68 dp, vier Zustände mit Füllung/Ring/Icon/Label, Timer „● m:ss", Puls-Ring, rotierender Sende-Bogen, Erfolgsring, Shake bei Fehler, Haptik, Reduce-Motion; Abbrechen-Ziel 72 dp mit Magnet-Radius, Scrim und „Loslassen zum Verwerfen"; Hinweis „Kopiert — einfügen" beim Zwischenablage-Fallback.
- **Diktat-Tastatur** neu: Statuszeile mit tippbaren Warnhinweisen (führen in die Einrichtung), 21-Balken-Pegelband, Mikro-Taste mit denselben vier Zuständen, Tastenreihe mit Material-Symbolen und contentDescriptions, Zahnrad → Einstellungen.
- **Benachrichtigung:** monochromes Icon, Farbe je Zustand, Tipp öffnet WhisperLoom (bisher totes Ende).
- **Adaptives App-Icon** (Hintergrund, Vordergrund, Monochrom für Themed Icons), eigenes Teilen-Ziel-Icon, Notification-Icon.
- **Deep-Links** `route`/`step` aus Overlay, IME und Benachrichtigung in Home, Einrichtung (auf einem bestimmten Schritt) oder Einstellungen.
- Dokumentation: `docs/ANLEITUNG.md` (Nutzer-Anleitung), `docs/design/ux-spec-v3.md`, `docs/research/`, dieses CHANGELOG.

### Geändert

- **Oberfläche komplett auf Jetpack Compose (Material 3, festes dunkles Theme)** — die bisherigen XML-Activities für Einrichtung und Einstellungen sind durch `MainActivity` mit State-Navigation ersetzt; alle UI-Texte überarbeitet.
- **Breaking: Standardmodell ist jetzt `gpt-transcribe`** („GPT Transcribe") statt `gpt-4o-transcribe`. OpenAI hat `gpt-4o-transcribe`, `gpt-4o-mini-transcribe` und `whisper-1` zum 2027-02-26 abgekündigt; sie bleiben wählbar und sind im Dropdown mit „(Auslauf 02/2027)" markiert. **Bestehende Einstellungen werden automatisch migriert** (einmalig, `prefs_version = 3`): eingetragene URL/Key/Modell bleiben unverändert erhalten, „Text von der KI glätten lassen" wird zur Stufe „Glätten", ein vorhandener Key setzt den Erkennungsweg auf „Online-Dienst".
- Für `gpt-transcribe` wird die Sprache als `languages[]` statt `language` gesendet; Mistral und OpenRouter bekommen kein `prompt`-Feld, Mistral zusätzlich kein `response_format` (Anbieter-Flags im Katalog).
- Geteilte Sprachnachrichten erscheinen standardmäßig ohne Füllwörter; der wortgetreue Text ist per Schalter weiterhin verfügbar. Text mit Absätzen aus der KI behält seine Zeilenumbrüche.
- Netz- und HTTP-Fehler zeigen verständliche Meldungen statt roher Exception-Texte.
- Scheitert die Textverbesserung (falsches Modell, 401/429, eigener Server aus, Base-URL leer), kommt der erkannte Text unverändert an — mit Hinweis „Textverbesserung übersprungen: …" statt verlorenem Diktat.
- `android:allowBackup="false"`: Einstellungen inklusive API-Keys bleiben auf dem Gerät (kein Google-Auto-Backup, kein Geräte-zu-Gerät-Transfer).
- v2-Einstellungen mit eigener Base-URL werden beim Update dem passenden Anbieter bzw. „Eigener Server" zugeordnet (vorher: Label OpenAI mit https-Pflicht, Assistent blieb offen).
- Offline wird nur auf Geräten mit mindestens 3 GB RAM angeboten (UX-Spec §2, Schritt 1).
- Share-Ansicht: „Erneut" je Datei ist gesperrt, solange noch eine andere Datei läuft (sonst ging deren Ergebnis verloren).
- Deep-Links (`route`/`step`) werden nur beim echten Start angewendet — nach Rotation/Prozess-Tod bleibt der wiederhergestellte Back-Stack erhalten.
- Modell-Zeilen sind für TalkBack ein Element („Small, Optionsfeld, ausgewählt"); „Erneut" nach Fehlschlag ist während eines anderen Downloads gesperrt.
- Download-Abbruch bei hängender Verbindung endet als Abbruch (Teildatei verworfen) statt als „Netzwerkfehler".
- Lint-Fehler brechen den Build (`abortOnError = true`); Kontext-Feld bei Mistral/OpenRouter sagt ehrlich, dass der Anbieter keinen Kontext entgegennimmt.
- Alle Farben in einer Wahrheit (`res/values/colors.xml`, Präfix `loom_`), Overlay/IME/Benachrichtigung/Themes lesen dieselben Tokens; Emoji-Glyphen auf Tasten durch Vektor-Icons ersetzt.
- Ein Komma direkt vor dem Satzende, das durch das Entfernen eines Füllworts übrig bliebe, wird mit entfernt.

### Entfernt

- `SetupActivity`/`SettingsActivity` (XML) samt Layouts und den zugehörigen Strings; Alt-Farbtokens (`kb_*`, `accent*`, `recording`, `launcher_bg`).
- Altes `Transcriber`-Interface (ersetzt durch `TranscriptionBackend` mit `OnlineBackend`/`OfflineBackend`).
- Gebündeltes Modell / Asset-Loader — Modelle kommen ausschließlich per Download (ein Test stellt sicher, dass kein `assets/*.bin` im APK liegt).

### Technik

- Toolchain: AGP 9.4.0, Gradle 9.6.1, Kotlin 2.2.10 (built-in, kein `kotlin.android`-Plugin mehr), Compose-Compiler-Plugin 2.2.10, JDK 17, compileSdk 37, targetSdk 35, minSdk 26, versionCode 3.
- Compose BOM 2026.08.00 (ui 1.12.0, material3 1.4.0), activity-compose 1.13.0, lifecycle-runtime-compose 2.11.0; bewusst ohne navigation-compose, material-icons, ViewModel-Lib, DI.
- Release mit R8 (Full Mode) + Resource-Shrinking; `mapping.txt` als CI-Artefakt; Keep-Regel nur für `WhisperLib` (JNI).
- Native: Submodul `whisper.cpp` @ v1.9.3 (`371b5a75`), NDK 28.2.13676358, CMake 3.22.1, nur `arm64-v8a`, `GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod`, `c++_static`, 16-KB-Page-Alignment, Debug-Buildtyp mit optimiertem Native-Build; Gradle-Property `whisperloom.skipNative` baut ohne NDK (APK ohne Offline-Engine).
- Network-Security-Config: Klartext nur für eigene Server, alle Cloud-Domains des Katalogs strikt https (Test hält beides synchron).
- Neue Foreground-Service-Typen/Permissions: `dataSync` (Modell-Download), `FOREGROUND_SERVICE_DATA_SYNC`, `ACCESS_NETWORK_STATE`; `Application`-Klasse `WhisperLoomApplication`.
- CI: Submodule, NDK/CMake/Build-Tools 36/Platform 37, `.cxx`-Cache, JNI-Symbol-Abgleich (`tools/check_jni_symbols.py`), Prüfung des Release-APKs (`.so` vorhanden, kein Modell, nur arm64, 16-KB-Alignment).
- Tests: Robolectric 4.16.1 (SDK 35, `sqliteMode=LEGACY`, `conscryptMode=OFF` für aarch64-Hosts), androidx.test core 1.7.0 / ext-junit 1.3.0, Compose `ui-test-junit4`; Stand 3.0.0: 58 Testklassen in 55 Dateien, 403 `@Test`-Methoden (JVM + Robolectric, HTTP gegen lokalen `HttpServer`).

### Bekannte Punkte

- Gerätetests der 3.0.0 stehen aus (Overlay/IME-Darstellung, Offline-Laufzeit und -Qualität, Live-Verhalten der einzelnen Anbieter — insbesondere `languages[]` bei GPT Transcribe, Mistral ohne `prompt`/`response_format`, DeepInfra-Pfad, OpenRouter ohne `prompt`). Laufzeitangaben zum Offline-Modus sind Größenordnungen aus Sekundärquellen.
- Frei eingetippte OpenAI-Reasoning-Modelle (nicht im Katalog) bekommen `temperature: 0` und werden vom Anbieter abgelehnt — Modell aus der Liste wählen.

## [2.1.0] — 2026-08-20

### Hinzugefügt

- **Sprachnachrichten aus anderen Apps transkribieren:** WhisperLoom erscheint im Teilen-Menü (`ACTION_SEND`/`ACTION_SEND_MULTIPLE` für `audio/*` und `application/ogg`). Eine WhatsApp-, Telegram- oder Signal-Sprachnachricht teilen und den Text lesen, kopieren oder weiterleiten; mehrere Dateien werden nacheinander abgearbeitet und mit Quelle und Dauer überschrieben.
- Geteiltes Audio wird auf dem Gerät dekodiert (MediaExtractor/MediaCodec → 16 kHz Mono, in eine Datei statt in den Heap), weil die Anbieter Opus-in-OGG nicht direkt annehmen; lange Aufnahmen werden bei 5 Minuten an der leisesten Stelle der letzten 20 Sekunden geschnitten (nie vor der halben Höchstlänge, nur bei deutlich leiserer Stelle).
- Geteilte Nachrichten werden wortgetreu ausgegeben (keine Füllwort-Entfernung, keine KI-Glättung); die Zwischenablage wird nur auf Knopfdruck überschrieben.
- `WavEncoder` gibt den Kopf getrennt heraus (Streaming aus der PCM-Datei, `WavUpload`); Dauer-Formatierung in `Formats`.

## [2.0.0] — 2026-08-19

### Geändert

- **Breaking: nur noch API-Betrieb.** Der lokale whisper.cpp-Betrieb lieferte auf dem Telefon zu schlechte Ergebnisse und wurde komplett entfernt (Submodul, JNI, CMake/NDK-Build, Modell-Download, gebündeltes Modell, Emulator-Tests). APK ~154 MB → ~2 MB. Letzter Stand mit lokalem Modell: Tag `offline-v1`.
- Standardmodell `gpt-4o-transcribe` statt `whisper-1`.
- Schwebender Knopf für die Netz-Latenz überarbeitet: vier sichtbare Zustände (bereit / nimmt auf mit Timer / sendet / Fehler); fehlgeschlagene Diktate bleiben gepuffert, Tippen sendet erneut — nur bei wiederholbaren Fehlern (Netz, 408/429/5xx); Ziehen auf ein Abbrechen-Ziel verwirft. Die Tastatur bekommt eine Wiederholen-Taste.

### Hinzugefügt

- `api`-Package: `ApiTranscriber` mit `prompt`-Feld (Kontext für Eigennamen und Fachbegriffe), `TextRefiner` (optionale KI-Glättung von Zeichensetzung, Grammatik, Absätzen), `Http` mit typisierten Fehlern.
- Option „Füllwörter intelligent entfernen": die KI entscheidet selbst statt fester Wortliste; der Regex-Filter wird dann abgeschaltet, damit nicht zweimal gefiltert wird.

### Behoben

- Bedienungshilfe setzte bei leerem Feld den Platzhalter-Text („Nachricht", „Google") vor das Diktat — Hint wird jetzt erkannt.
- Schwebender Knopf: Tipp/Zieh-Schwelle nutzt `scaledTouchSlop`, Position wird gemerkt statt bei jedem Start zurückgesetzt, Clamping hält den Knopf im Bildschirm (auch nach Drehung).

## [1.0] — 2026-07-09

### Hinzugefügt

- Erste Version: lokale Offline-Diktier-Tastatur mit Whisper über whisper.cpp (NDK/JNI), Modelle small/base/tiny (q5) mit Download-on-demand und Integritätsprüfung, gebündeltes Modell im APK.
- Default-Sprache Deutsch, Stille-Trimmen vor der Erkennung, Textveredelung (Füllwörter, Groß-Schreibung, Whitespace).
- Optionale OpenAI-kompatible Cloud-API (opt-in) über `ApiTranscriber`/`WavEncoder`.
- Diktat ohne Tastaturwechsel: schwebender Mikro-Button (Overlay) + Bedienungshilfe, die den Text an der Cursor-Position ins fokussierte Feld einfügt — Gboard bleibt aktiv.
- Release-Signierung aus GitHub-Secrets, CI mit Unit-Tests, Lint, Debug- und Release-APK.

[3.2.0]: #320--2026-09-07
[3.1.0]: #310--2026-09-07
[3.0.0]: #300--2026-09-07
[2.1.0]: #210--2026-08-20
[2.0.0]: #200--2026-08-19
[1.0]: #10--2026-07-09
