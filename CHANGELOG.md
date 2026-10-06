# Changelog

Alle nennenswerten Änderungen an WhisperLoom. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [Unreleased]

### Behoben

- **Gemma 4 E4B (und E2B) direkt wählbar.** Bisher war ein Textmodell erst nach dem Download wählbar, und ein fertiges E4B blieb ungenutzt, solange E2B gewählt war. Jetzt lädt ein Tipp auf ein passendes, noch nicht geladenes Textmodell es (wie **Laden**, über mobile Daten mit Nachfrage), und ist der Download fertig, ist es gewählt. Zu große Modelle bleiben ausgegraut („Für dieses Gerät zu groß").
- **Auswahl auch unter Text und im Assistenten.** Die Karte **Offline-Erkennung** zeigt beide Textmodelle statt nur des gewählten, der Assistent (Schritt 2b) nach „Lokales Textmodell" ebenfalls; Standard bleibt Gemma 4 E2B.
- **Kein stummes „Laden" mehr.** Läuft ein anderer Download, sagt die Textmodell-Zeile „Laden geht, sobald der laufende Download fertig ist."

## [3.8.5] — 2026-10-06

### Hinzugefügt

- **Lokales Textmodell für die Offline-Erkennung.** Erkennst du offline, verbessert jetzt Gemma 4 den Text direkt auf dem Gerät: dieselben Stufen und Anweisungen wie online, ohne Netz, ohne Key, ohne Kosten — der Text verlässt das Handy nicht. Einstellungen → Offline-Modelle hat dafür den neuen Abschnitt **Textverbesserung** mit **Gemma 4 E2B** (empfohlen, 2,6 GB, ab 6 GB Arbeitsspeicher) und **Gemma 4 E4B** (3,7 GB, genauer, langsamer, ab 8 GB). Beim ersten Start legt das Modell einen Zwischenspeicher an (etwa 0,8 bzw. 1,1 GB); die App zeigt ihn bei der Größe mit an und löscht ihn mit dem Modell. Der Download kommt von huggingface.co/litert-community, mit Fortsetzen und Prüfsumme wie bei den Whisper-Modellen.
- **Regel „Textverbesserung bei Offline-Erkennung“.** Einstellungen → Text → neue Karte **Offline-Erkennung** mit drei Optionen: **Lokales Textmodell** (Standard, offline erkannter Text geht nie online), **Online, ohne Netz lokal** (mit eigenem Online-Zugang und Netz online, sonst oder bei einem Fehler lokal) und **Überspringen** (kein lokales Modell; ohne eigenen Zugang oder ohne Netz kommt der Text ohne KI). Online erkannter Text wird wie bisher online verbessert.
- **„Offline ohne Textmodell“.** Ist offline eine KI-Stufe an und fehlt das Textmodell, zeigen Erkennung, Offline-Modelle, Text und der Assistent dieselbe Karte mit **Textmodell laden** und **Überspringen**, und der Startbildschirm warnt mit einem Banner. Bis dahin kommt der Text sofort ohne KI — mit „Online, ohne Netz lokal“ und eigenem Online-Zugang nur ohne Netz, mit Netz geht er an diesen Zugang. Die App wartet nie auf ein fehlendes Modell. Auf Geräten unter 6 GB RAM passt kein Textmodell: Dort wirkt die Regel wie „Überspringen“, ohne Warnkarte und Banner.
- **Ausweg während der Textverbesserung.** Tastatur und schwebender Knopf zeigen jetzt, dass der Text verbessert wird („Text wird verbessert … antippen = ohne KI einfügen“). Ein Tipp fügt den erkannten Text sofort ohne KI ein, online wie lokal.
- **Einrichtung offline:** Nach dem Whisper-Modell wählst du im Assistenten **Lokales Textmodell** oder **Überspringen**. „Lokales Textmodell“ schaltet die Stufe „Glätten“ ein, wenn noch keine KI-Stufe an ist. „Weiter“ geht schon, während das Textmodell im Hintergrund lädt; scheitert der Download, nennt der Assistent den Grund und bietet ihn erneut an.

### Geändert

- **Die Zugangs-Karte unter Text heißt „Online-Zugang für die Textverbesserung“.** Bei Offline-Erkennung sagt sie, was nach der gewählten Regel passiert, statt pauschal „braucht einen Online-Zugang“.
- **Startbildschirm, Einstellungen und Tastatur kennen das Textmodell.** Die Zeile Textverbesserung zeigt bei Offline-Erkennung, wie verbessert wird („Glätten · lokal · Gemma 4 E2B“, „Glätten · offline übersprungen“), der Einstellungs-Hub die Kurzform der Regel. Die Tastatur gibt die KI-Stufen offline frei, sobald das Textmodell bereit ist; sonst führt ihr Hinweis zu den Offline-Modellen.
- **Offline-Modelle** zählen Whisper- und Textmodelle zusammen, in „Belegt“ samt Zwischenspeicher.

### Behoben

- **Offline-Erkennung schickte den Text still an den alten Online-Zugang.** Stand die Textverbesserung auf „wie Erkennung“, ging offline erkannter Text an den noch gespeicherten Online-Anbieter der Erkennung, obwohl die Karte „braucht einen Online-Zugang“ zeigte. Bei Offline-Erkennung zählt jetzt nur ein eigener Text-Zugang.
- **Kein Hänger mehr ohne Netz.** Vor jeder Online-Textverbesserung prüft die App, ob das Telefon Internet hat. Ohne Netz geht keine Anfrage raus, und der Text kommt sofort — lokal verbessert oder ohne KI mit Hinweis — statt erst nach einer Zeitüberschreitung. Für Server im eigenen Netz (Ollama zu Hause, Tailscale) reicht eine WLAN- oder VPN-Verbindung.

### Technik

- Neue Abhängigkeit **LiteRT-LM 0.16.1** (`com.google.ai.edge.litertlm:litertlm-android`, Apache 2.0), fest gepinnt, weil 0.17.x Kotlin 2.4 voraussetzt; transitiv Gson 2.13.2 und kotlin-reflect 2.2.21. R8-Keep-Regel für das Paket `com.google.ai.edge.litertlm` (das AAR bringt keine Consumer-Regeln mit, ohne sie stürzt die Rechnung im Release ab, LiteRT-LM #3739). Die native Bibliothek (arm64, 21,5 MB, unkomprimiert) lässt das Release-APK von etwa 4 auf etwa 26 MB wachsen; die Modelle sind nicht im APK. Die Lizenzhinweise der darin gelinkten Bibliotheken (Abseil, XNNPACK, Protobuf, Eigen u. a.) liegen als `assets/licenses/litertlm-0.16.1-THIRD_PARTY_NOTICE.txt` im APK.
- Gemma rechnet auf der CPU (`Backend.CPU`, `maxNumTokens` 4096), eine Conversation je Auftrag, prozessweit geteilt (`LocalTextEngine`), Vorwärmen beim Aufnahmestart, Freigabe nach 2 min Leerlauf, bei Speicherdruck, Löschen und Modellwechsel; Abbruch nur per `cancelProcess`, nie `close` mitten in der Rechnung. Dateien auf feste Hugging-Face-Revisionen gepinnt (Bytes, SHA-256), Platzprüfung inklusive Cache (`extraDiskBytes`).
- Entscheidungstabelle als reine Funktion (`RefineDecision`), Ausführung in `RefinePlan` für Diktat und geteilte Audios; Netz-Vorprüfung `NetworkCheck` (`NET_CAPABILITY_VALIDATED`, im eigenen Netz reicht ein aktives Netz); Connect-Timeout 8 s statt 15 s, wenn das lokale Modell einspringen kann. Neue Prefs `offline_refine` (Standard `local`) und `local_llm_model` (Standard `gemma4_e2b`), ohne Migration.
- Doku: Anleitung (8.2, neues Kapitel 9.7, 9.6, 11), README, Datenschutzerklärung, `THIRD_PARTY.md`, Store-Texte.

## [3.8.3] — 2026-10-06

### Behoben

- **Vokabular-Hinweis:** Wird von einer gekürzten Liste genau ein Begriff mitgeschickt, heißt es jetzt „1 von 3 Begriffen wird mitgeschickt“ statt „werden“.
- **Datenschutz-Texte sind genauer:** Der Hinweis vor der Mikrofon-Freigabe und „Anleitung & Hilfe“ sagten pauschal „speichert keine Aufnahmen“. Ein Auftrag eines Pro Widgets bleibt aber auf dem Gerät, bis er gesendet ist; das steht jetzt dabei. Und „offline verlässt nichts das Gerät“ heißt jetzt „die Aufnahme bleibt auf dem Gerät“, denn mit eingeschalteter Textverbesserung geht der erkannte Text auch nach einer Offline-Erkennung an das Sprachmodell.
- **Hilfe zum Offline-Modus:** Statt „2–10 Sekunden“ steht dort jetzt „Small: etwa 5–12 s“ (wie in der Anleitung), und Small heißt „empfohlen“ statt „am besten“.
- **Über WhisperLoom** nennt bei den Lizenzen jetzt auch Kotlin und kotlinx.coroutines.

### Technik

- Beschreibungen auf Stand gebracht: README, Anleitung, Datenschutzerklärung (ElevenLabs, Pro Widgets, gespeicherte Daten), Play-Store-Notizen, `THIRD_PARTY.md` und Store-Beschreibung (was online wohin geht); Recherche-Reports als historisch markiert.
- CI: actions/checkout v7, setup-java v6, cache v6, upload-artifact v7, android-actions/setup-android v4, gradle/actions/setup-gradle v5 (bewusst nicht v6, Begründung in `build.yml`).
- Lint von 113 Warnungen auf 0: KTX-Erweiterungen (`edit {}`, `toUri()`, `isVisible`, `createBitmap`), sechs ungenutzte Texte entfernt, `mipmap-anydpi-v26` → `mipmap-anydpi` (minSdk 26), bewusste Ausnahmen mit Begründung in `app/lint.xml`.

## [3.8.2] — 2026-10-05

### Hinzugefügt

- **Patchnotes in der App.** Das Fragezeichen neben der Versionsnummer (Startbildschirm unten, Anleitung & Hilfe, Über WhisperLoom) zeigt, was sich geändert hat: das Wichtigste zuerst, Details zum Aufklappen, frühere Versionen kompakt darunter.

## [3.8.1] — 2026-10-05

### Hinzugefügt

- **„Lesbarer glätten“.** Neuer Schalter unter Einstellungen → Text, direkt unter den Stufen (ab Werk aus). Gesprochenes liest sich wörtlich abgetippt oft holprig. Mit dem Schalter repariert „Glätten“ auch Satzabbrüche, Versprecher, Wiederholungen und Bandwurmsätze, behält bei einer Selbstkorrektur („um drei, nee, um halb vier“) nur die neue Angabe und rückt verrutschten Satzbau gerade („weil ich hab keine Zeit“ → „weil ich keine Zeit hab“). Deine Wortwahl, Kurzformen wie „hab“ und „nen“, kleine Wörter wie „halt“ und „ne?“, Du oder Sie und „ich glaub“ bleiben. Der Schalter gilt fürs Diktat und für geteilte Sprachnachrichten. Ohne ihn bleibt „Glätten“ wie bisher bei Zeichensetzung, Groß-/Kleinschreibung und Absätzen.

### Geändert

- **Neue Anweisungen für Glätten, Verschönern und Zusammenfassen.** Recherchiert und an 27 Test-Diktaten mit einem kleinen lokalen Modell (gemma3:4b) erprobt. „Verschönern“ formuliert flüssiger und behält dabei deinen Ton (locker bleibt locker, förmlich bleibt förmlich), deine Wörter und alle Fakten. „Zusammenfassen“ wird höchstens halb so lang, behält Namen, Zahlen, Termine und offene Fragen genau und bleibt bei „ich“ bzw. „wir“; ein sehr kurzes Diktat kommt nur bereinigt zurück.
- **Diktierte Fragen und Bitten werden bearbeitet, nicht beantwortet.** Dein Diktat geht in jeder Stufe markiert an die KI. Vorher konnte ein kleines Modell auf „Schreib mir eine Einladung für Samstag“ die Einladung schreiben. Liefert ein Modell trotzdem eine weit längere Antwort, fügt WhisperLoom den Rohtext mit Hinweis ein. Eine Einleitung wie „Hier ist der geglättete Text:“ und Anführungszeichen um den ganzen Text fallen automatisch weg.

### Behoben

- **„Glätten“ setzt kein Fragezeichen mehr hinter Aussagen** (kleine Modelle machten aus „Bin in zehn Minuten da.“ eine Frage).
- **„Füllwörter intelligent entfernen“ mit „Glätten“:** Die Anweisung widersprach sich nicht mehr selbst, doppelt gesagte Wörter verschwinden jetzt.

## [3.8.0] — 2026-09-30

### Hinzugefügt

- **ElevenLabs (Scribe) als Spracherkennung.** Einstellungen → Erkennung → Anbieter **ElevenLabs (Scribe)** mit den Modellen Scribe v2 und Scribe v2 Medical. Den Key legst du unter elevenlabs.io → API Keys an, mit der Berechtigung „Speech to Text“ (für die Modell-Liste zusätzlich „Models: Lesen“). Dein Vokabular geht als Fachbegriffe mit; das kostet bei ElevenLabs etwa 20 % Aufpreis. Eine Textverbesserung bietet ElevenLabs nicht, dafür trägst du unter „Text“ einen eigenen Zugang ein.
- **Modelle vom Server (Pro).** Unter Einstellungen → **Erweitert** schaltest du **Modelle vom Server** ein. Dann lädt jeder Anbieter seine aktuelle Modell-Liste, für die Erkennung und für die Textverbesserung. Die Auswahl zeigt oben die Empfehlungen und darunter alle Modelle vom Server mit Stand-Datum. Sie hat ein Suchfeld und den Knopf **Modelle aktualisieren**. Ist die Liste älter als 24 Stunden, lädt die App sie beim Öffnen neu. Passende Einstellungen wie temperature oder Reasoning-Stufe setzt die App für neue Modelle selbst.

### Geändert

- **Empfehlungen auf dem Stand vom 30.09.2026:** DeepSeek `deepseek-flash`, Gemini 3.5 Flash-Lite bzw. 3.8 Flash, bei OpenRouter keine auslaufenden Gemini-2.5-Modelle mehr, Groq Qwen 3.8. Wer Gemini ohne eigene Modellwahl nutzt, behält sein bisheriges Modell.
- **Textverbesserung „wie Erkennung“** wird bei einem Anbieter, der nur erkennt (ElevenLabs), klar als nicht möglich angezeigt: in den Einstellungen, auf dem Startbildschirm und in der Tastatur.

### Behoben

- **Textverbesserung mit neuen Modellen:** Lehnt ein Modell `temperature` ab, versucht die App es einmal ohne statt aufzugeben.

## [3.7.1] — 2026-09-30

### Hinzugefügt

- **Pro Widgets.** Einstellungen → **Erweitert** schaltet jetzt die Pro-Funktionen frei: **Pro Widgets** (vorher „Sprachauftrag“) und die Stufe „Prompt“. Mit Pro Widgets bekommt das Widget-Menü einen eigenen Tab **Pro Widgets**. Dort legst du **Sprach-Command-Widgets** an. Der Tab „Widgets“ ist für normale Widgets vorgesehen, die mit einem späteren Update kommen.
- **Server je Widget.** Server-Adresse, Token und „Verbindung prüfen“ stehen direkt im Editor des jeweiligen Widgets, ein eigenes Server-Menü gibt es nicht mehr. Jedes Widget kann an einen anderen Server senden. Jeder Auftrag merkt sich sein Widget, und ein gelöschtes Widget sendet nichts mehr. Bestehende Widgets übernehmen beim Update den bisherigen Server.
- **Name unter dem Widget.** Jedes Widget hat einen Pflichtnamen, der wie bei App-Symbolen unter der Kachel steht, in jeder Größe. Pro Widget lässt er sich ausblenden.
- **Zustand „Server fehlt“.** Ein Widget ohne gültigen Server sagt das selbst, und ein Tipp öffnet direkt seinen Editor. Bei ausgeschalteten Pro Widgets führt der Tipp nach „Erweitert“.
- **Bebilderte Anleitungen.** Hilfe, Einrichtungsassistent, Tutorials, „Erweitert“ und das Widget-Menü zeigen oben eine eigene Illustration und darunter kurze Texte. Neu sind der Hilfe-Abschnitt **Widgets & Pro Widgets** und das Tutorial **Pro Widgets** mit 6 Seiten.

### Geändert

- **Einstellungen neu sortiert** in die Gruppen **Grundlagen** (Erkennung, Offline-Modelle, Text), **Bedienung** (Knopf & Tastatur, Widgets), **Pro** (Erweitert) und **Info** (Anleitung & Hilfe, Über).
- **Widget heißt „Sprach-Command“** in der Widget-Auswahl, im Benachrichtigungskanal und bei TalkBack.
- **Anleitungstexte gekürzt**, der Inhalt bleibt erhalten.
- **Offener Auftrag** lässt sich auch bei ausgeschalteten Pro Widgets unter „Erweitert“ verwerfen.

## [3.7.0] — 2026-09-30

### Hinzugefügt

- **KI-Textverbesserung für geteilte Sprachnachrichten** (ab Werk aus). Einstellungen → Text → **Geteilte Sprachnachrichten**: eigene Stufe **Glätten**, **Verschönern** oder **Zusammenfassen**, unabhängig von der Stufe fürs Diktat. Jedes 5-Minuten-Stück geht einzeln an die KI, der Transkriptions-Bildschirm zeigt dann die verbesserte Fassung mit dem Hinweis „Textverbesserung: …". Scheitert die KI, bleibt es bei der Fassung ohne KI mit Hinweis. „Füllwörter intelligent entfernen" lässt sich dafür auch ohne Diktat-Stufe einschalten.
- **Widget-Profile für den Sprachauftrag.** Jedes Widget auf dem Startbildschirm kann anders aussehen und sich anders verhalten: eigener Name, eigenes Symbol, eigener Auto-Stopp. Neues Untermenü Einstellungen → **Widgets** (auch aus den Erweiterten Optionen erreichbar): Profile anlegen, bearbeiten und löschen, jedem platzierten Widget ein Profil zuordnen. Jede Änderung wird sofort gespeichert. Das Standardprofil „Sprachauftrag“ gibt es immer; bestehende Widgets sehen aus wie bisher. Ist der Sprachauftrag noch nicht eingerichtet, sagt das Untermenü, wo das geht.
  - **Symbol:** 22 eingebaute Symbole (Mikrofon, Einkauf, Zuhause, Arbeit, Idee, Termin, Notiz, Auto, Herz, Stern, Chat und die bisherigen technischen) oder ein **Bild aus der Galerie**, rund zugeschnitten. Die Galerie öffnet der System-Auswahldialog; die App braucht dafür keine Speicher-Berechtigung.
  - **Auto-Stopp** (pro Profil, ab Werk aus): Nach einer Sprechpause endet die Aufnahme von selbst und wird gesendet — die Pause ist wählbar: Kurz 1,2 s, Normal 2 s, Lang 3,5 s. Hört das Widget 8 Sekunden lang keine Sprache, verwirft es die Aufnahme und sendet nichts („Nichts gehört — tippen für erneuten Versuch“); gleichmäßiger Lärm wie im Auto zählt dabei nicht als Sprache. Ein Tipp beendet wie bisher jederzeit, die 5-Minuten-Grenze bleibt.
  - **Profilwahl beim Platzieren:** Gibt es mehr als ein Profil, fragt WhisperLoom beim Hinzufügen „Welches Profil?“ (dort lässt sich auch gleich ein neues anlegen). Mit nur dem Standardprofil landet das Widget ohne Rückfrage. Ab Android 12 lässt sich das Profil später per langem Druck → **Neu konfigurieren** wechseln, sonst im Untermenü.
- **Das Widget ist frei skalierbar** von 1 × 1 bis 4 × 2 Feldern: ganz klein nur das Symbol, breit Symbol und Text nebeneinander, ab 2 × 2 untereinander. Platziert wird weiterhin 2 × 2.

### Behoben

- **Abgeschnittene Textverbesserung wurde als fertig eingefügt.** Hörte das Sprachmodell an seiner Längengrenze auf (z. B. GPT-5-Modelle bei sehr langen Diktaten, kleines Kontextfenster bei Ollama), kam nur der Anfang an. Jetzt erkennt WhisperLoom den Abbruch und fügt den Rohtext mit Hinweis ein.
- **Offline-Erkennung schnitt lange Aufnahmen alle 30 Sekunden mitten im Wort.** Whisper hört in Stücken von 30 Sekunden. Bisher begann das nächste Stück stur 30 Sekunden später, auch mitten in einem Wort. Das Wort an der Grenze fehlte oder kam verstümmelt an. Betroffen waren Offline-Diktate über 30 Sekunden und geteilte Sprachnachrichten. Jetzt beginnt das nächste Stück dort, wo Whisper den letzten vollständigen Satzteil erkannt hat. Diktate bis 30 Sekunden laufen unverändert und genauso schnell wie bisher.
- **Widget-Meldung „Kein Ton aufgenommen“** stand mit doppeltem „— tippen für erneuten Versuch“ da; auch der Bildschirmleser las den Hinweis zweimal vor.
- **Sprachauftrag-Widget blieb auf „Wird gesendet …“ hängen, nur ein Force-Stop half** ([#10](https://github.com/CTreitges/whisperloom-android/issues/10)). Scheiterte der erste Versuch (etwa weil der Server gerade erst startete), wartete der Auftrag still auf den nächsten Termin, und ein Tipp auf das Widget tat nichts.
  - Ein gescheiterter Versuch ist jetzt sichtbar: „‹Grund› — neuer Versuch folgt, tippen = jetzt“.
  - Ein Tipp auf „Wird gesendet …“ sendet sofort, statt auf den nächsten geplanten Versuch zu warten, und wartet dabei auch nicht auf eine hängende Netz-Bedingung (VPN, WLAN ohne Internet). Läuft gerade ein Versuch, ersetzt der Tipp ihn erst, wenn er länger als drei Minuten hängt. Ein ungeduldiger Tipp in dieser Zeit kostet so keine zweite Transkription. Eine laufende Offline-Erkennung ersetzt der Tipp gar nicht: sie lässt sich nicht abbrechen, und der Ersatz müsste nur hinter ihr warten.
  - Die App wartet bis zu 75 s auf die Antwort der Bridge statt 30 s. Die Bridge übergibt den Auftrag an den Agenten, bevor sie antwortet, und das dauert bis zu 60 s. Vorher hielt die App einen längst angenommenen Auftrag für gescheitert.
  - Ein abgelöster, verworfener oder vom System gestoppter Versuch schickt nichts mehr hinterher und überschreibt keinen neuen Auftrag. Ein Doppeltipp auf „erneut senden“ startet keinen zweiten Versuch.
  - Hängt es trotzdem: Akku für WhisperLoom auf „Nicht eingeschränkt“ stellen. Diagnose am Rechner: `adb shell dumpsys jobscheduler com.chris.whisperloom` und `adb shell am get-standby-bucket com.chris.whisperloom`.

## [3.6.0] — 2026-09-25

### Hinzugefügt

- **Textverbesserungs-Stufe „Prompt“** (erweiterte Option, ab Werk aus). Einstellungen → Erweiterte Optionen → **Stufe „Prompt“ anbieten** schaltet eine fünfte Stufe in Tastatur und Einstellungen frei. Sie formt ein Diktat zu einem Prompt für KI-Assistenten wie ChatGPT, Claude oder Gemini: Eine kurze Frage bleibt ein bis drei Sätze, mehrere Vorgaben werden eine Liste, ein großer Auftrag bekommt Abschnitte („Ziel:“, „Hintergrund:“, „Aufgabe:“, „Vorgaben:“, „Format:“). Nichts wird dazuerfunden, Selbstkorrekturen („nee, warte …“) werden aufgelöst, die Sprache bleibt. Beantwortet das Modell die diktierte Bitte, statt sie umzuformulieren, und ist die Antwort deutlich länger als dein Diktat (das lange Gedicht, um das du gebeten hast), kommt der Rohtext mit Hinweis. Eine kurze Antwort lässt sich so nicht erkennen. Die Gliederung bleibt auch mit „Automatische Absätze“ aus erhalten.

### Behoben

- **Großschreibung mitten im Satz.** Nach einem Punkt ohne folgendes Leerzeichen wurde großgeschrieben: „config.Yaml“, „www.Example.Com“, „Python 3.13 Gegenüber“, „GPT 4.1 Mini“. Ein Satz endet jetzt erst mit Punkt, Ausrufe- oder Fragezeichen und Leerraum danach.
- **„alle .log-Dateien“ wurde zu „alle.log-Dateien“.** Ein Punkt direkt vor einem Wort (Dateiendung, versteckte Datei) behält das Leerzeichen davor.
- **Füllwörter blieben stehen, wenn die Textverbesserung ausfiel.** Mit „Füllwörter intelligent entfernen“ sollte die KI sie entfernen — scheiterte sie (Netz, Anbieter, Zeitüberschreitung), tat es niemand, und im Rohtext standen die „ähm“s. Jetzt räumt WhisperLoom den Rohtext in dem Fall mit den festen Regeln auf.

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
