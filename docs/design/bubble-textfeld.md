# Schwebender Knopf am Textfeld — Gestenkonzept (Ziel 4.0)

Stand 27.11.2026, Branch `feat/bubble-textfeld`. Ergänzt UX-Spec §6.22; wo beide abweichen, gilt dieses Dokument
(§6.22 verweist hierher). Vorbild für das Bedienmuster: Wispr Flow (Android). Übernommen sind nur Bedienmuster,
keine Form, Farben oder Icons. Die Zweitmeinung von Gemini 4 Neon ist eingearbeitet (letzter Abschnitt).

## 1. Gesten am Knopf

| Geste | Bedingung | Wirkung |
|---|---|---|
| Tippen | jeder Zustand | wie bisher: IDLE startet, RECORDING sendet, SENDING überspringt die KI, ERROR sendet erneut |
| Ziehen vor der Haltezeit | Finger verlässt den Touch-Slop, bevor die Haltezeit abläuft | Knopf verschieben; Ziel unten: in IDLE „Ausblenden bis zum nächsten Feld“, in RECORDING/ERROR „Verwerfen“, in SENDING nur verschieben (unverändert) |
| Halten ohne Wischen | IDLE, Haltezeit abgelaufen, Finger im Slop | Push-to-Talk: Aufnahme startet (Haptik `CONFIRM` vor dem Mikrofon), Loslassen sendet mit dem aktuellen Modus |
| Halten und Wischen | IDLE, nach der Haltezeit weiter als die Einrast-Schwelle | Modus-Kreuz: Loslassen auf einem Modus setzt ihn dauerhaft und sendet mit ihm; auf ✕ verwerfen |

- **Haltezeit** = `ViewConfiguration.getLongPressTimeout()` (System, Standard 400 ms, folgt „Berührungs- und
  Haltedauer“). **Touch-Slop** = `scaledTouchSlop` des Systems.
- Halten wirkt nur aus IDLE. In RECORDING, SENDING und ERROR bleibt ein langer Druck ein Tipp (wie #38).
- **Langsamer Tipp (N9):** Endet ein Halten ohne Ziel in der ersten Sekunde nach Ablauf der Haltezeit
  (`BubbleGesture.HOLD_MIN_MS`) **und hat der Finger den Slop nie verlassen**, läuft die Aufnahme freihändig weiter.
  Wer das Kreuz aufgezogen hat und in die Mitte zurückkehrt, hat sich umentschieden: dann gilt die Regel nicht.
- **Halten ohne Sprache sendet nichts:** Loslassen ohne Ziel sendet nur, wenn die Aufnahme Sprache enthält (§3);
  sonst zurück in Ruhe, keine Anfrage.
- **Nach der Haltezeit verschiebt nichts mehr.** Bisher (#38) verschob Halten + Ziehen den Knopf mit gehaltener
  Aufnahme und zeigte das Verwerfen-Ziel unten; das ersetzt das ✕ im Kreuz.
- **Abgefangener Touch** (`ACTION_CANCEL`, z. B. Systemgeste) im Halten: liegt der Finger auf dem ✕, verwerfen;
  sonst die gehaltene Aufnahme senden, wenn sie Sprache enthält. Den Modus ändert ein Abbruch nie.
- **Schalter „Halten zum Sprechen“ aus** (E3, Ausweg bei Tremor): Halten öffnet das Kreuz ohne Aufnahme. Loslassen
  ohne Bewegung wirkt wie ein Tipp (startet die Aufnahme, der langsame Tipp bleibt so bedienbar); wer das Kreuz
  aufgezogen hat und ohne Ziel loslässt, schließt es nur; auf einem Modus nur Moduswechsel.
- **Kein Mikrofonrecht oder nicht eingerichtet:** Kreuz ohne Aufnahme (kein Sprung in den Assistenten mitten in der
  Geste); Loslassen ohne Bewegung wirkt wie ein Tipp und zeigt den bisherigen Hinweis.
- **Eine Haptik je Loslassen:** Modus `CONFIRM`, Verwerfen `REJECT`, Senden ohne Ziel `CONTEXT_CLICK`.

## 2. Modus-Kreuz als Bogen

Ziele: Aus, Glätten, Schöner, Kürzen (die Kurznamen der Tastatur-Leiste `kb_level_*`, damit beide Wege gleich
heißen), dazu **Prompt** als fünftes Ziel nur bei `Prefs.promptLevelEnabled`. Dazu das **✕** (verwerfen).
Logik Android-frei in `ModeCross`, Zeichnen in `ModeCrossWindow`.

### Maße

| Größe | Wert | Grund |
|---|---|---|
| Bogenradius (Druckpunkt → Zielmitte) | 112 dp, gestaucht bis 140 dp, nur in Ecken bis 220 dp | Ziele liegen frei neben dem 68-dp-Knopf, Daumenweg kurz |
| Ziel | Pille 72 × 44 dp, Text 14 sp, bei großer Schrift bis 12 sp, darüber wächst die Pille | „Glätten“ passt; kein Text unter 12 sp (UX-Spec §7) |
| Spannweite | 180° (4 Ziele: 60° Abstand, 5 Ziele: 45°); am Rand 150°/120°/90° | Nachbarn bei 180° mindestens 7 dp auseinander |
| ✕ | Kreis 52 dp, 96 dp vom Druckpunkt, gegenüber der Bogenmitte (am Rand seitlich) | jenseits der Einrast-Schwelle |
| Einrast-Schwelle | 56 dp Fingerweg ab Druckpunkt; eingerastet löst ein Ziel erst unter 44 dp | wie die Tastatur (`DictationGesture`) |
| Winkel-Hysterese | 6° | ein anderes Ziel gewinnt erst, wenn die Fingerrichtung 6° näher an ihm liegt |
| Richtungsabstand | ≥ 20° zwischen zwei Zielen, in Ecken ≥ 12° | jedes Ziel bleibt wählbar |
| Rand | 8 dp zum sicheren Bereich (ohne Status-, Navigationsleiste, Kamera-Aussparung) | alle Ziele sichtbar |

### Auswahl
- Gewählt wird nach **Richtung**: das Ziel, dessen Richtung vom Druckpunkt der Fingerrichtung am nächsten liegt,
  sobald der Finger mindestens 56 dp vom Druckpunkt entfernt ist. Abstand entscheidet nicht — sonst gewönne das
  nähere ✕ gegen die weiter entfernten Modi.
- Der Bogen liegt um den **Druckpunkt**, nicht um die Knopfmitte: Geometrie relativ zum Finger, blind gleich.
- Einrasten auf ein neues Ziel: Haptik `CONTEXT_CLICK`; das Ziel wird hervorgehoben (Füllung `primaryContainer`,
  Text `onPrimaryContainer`, ✕ in `errorContainer`), leicht vergrößert (1,08).

### Lage am Rand (N10)
Der Bogen öffnet nach oben. Passt er nicht in den sicheren Bereich, probiert die Logik in dieser Reihenfolge:
1. Spannweiten 180°, 150°, 120°, 90° (bei kleinerer Spannweite wächst der Radius bis 140 dp, damit Nachbarn
   ≥ 80 dp auseinander liegen; braucht es mehr, entfällt die Spannweite).
2. Je Spannweite die Richtungen: oben, dann die übrigen 45°-Richtungen nach Nähe zur Richtung „Druckpunkt →
   Bildschirmmitte“ (am rechten Rand öffnet der Bogen also nach links: ein gespiegelter Halbkreis).
3. Je Richtung das ✕ gegenüber, dann schräg (±45°), dann seitlich (±90°).
4. Die ganze Lage darf gemeinsam verschoben werden, bis sie im sicheren Bereich liegt.
Genommen wird die erste Lage, bei der alle Ziele im Bereich liegen, sich nicht überlappen, ≥ 80 dp vom Druckpunkt
liegen und ≥ 20° Richtungsabstand haben. In Ecken (nur ein Viertelkreis frei) gibt es das nicht: dann die Lage mit
dem größten Richtungsabstand (≥ 12°, Radius bis 220 dp). Die Reihenfolge der Modi bleibt im Uhrzeigersinn gleich
(Aus zuerst). Geprüft auf 360 × 780 und 411 × 891 dp an Rändern, Ecken und Mitte (`ModeCrossTest`).

### Sichtbarkeit (Christof: „ganz durchsichtig … bis man seinen Finger vom Button bewegt, dann besser sichtbar“)
- Das Kreuz erscheint, sobald die Haltezeit abläuft, mit **Deckkraft 0,15** — mit dem Finger auf dem Knopf kaum zu
  sehen.
- Ab dem Touch-Slop steigt die Deckkraft linear und ist bei **32 dp Fingerweg voll** — vor der Einrast-Schwelle
  (56 dp), damit man die Namen liest, bevor etwas einrastet. Keine Animation, direkt dem Finger folgend.
- Knopf: in Ruhe 0,5 deckend, bei Berührung, Halten und Kreuz 1,0 (§6.22).
- Kontrast (Leitlinien 02/03, WCAG 1.4.3/1.4.11): Pille `surfaceContainerHigh` mit Rand `outline` 1 dp, Text
  `onSurface`; der aktuelle Modus innen gefüllt (`secondaryContainer`, Text `onSecondaryContainer`) plus 2-dp-Rand
  `primary` — nicht nur über den Rand erkennbar. Fenster-Deckkraft höchstens 0,8 (siehe unten): Text zu Füllung
  bleibt über 4,5:1, auf weißem Grund liegt die Pille dunkel (≈ 5:1 zum Hintergrund).
- Eigenes Overlay-Fenster nach dem Muster von `CancelTarget`: nicht fokussierbar, nicht berührbar, vollflächig mit
  **Fenster-Deckkraft 0,8** — Android 12+ blockt Berührungen anderer Apps unter einem nicht berührbaren Fenster mit
  mehr Deckkraft (untrusted touch occlusion). Nur während der Geste angehängt; die Geste gehört dem Knopf-Fenster.

## 3. Loslassen im Kreuz

| Ziel | Aufnahme läuft, Sprache | Aufnahme läuft, keine Sprache | keine Aufnahme |
|---|---|---|---|
| Modus | Modus speichern (`Prefs.refineMode`), senden mit ihm | Modus speichern, Aufnahme verwerfen, **keine** Anfrage (N7) | Modus speichern |
| ✕ | verwerfen (Toast `float_discarded`, Haptik `REJECT`) | verwerfen | nichts |
| kein Ziel | senden (Langsamer-Tipp-Regel §1) | nichts senden, zurück in Ruhe | wie Tipp, nach Bewegung nur schließen |

- Moduswechsel: Toast „Modus: Glätten“ (`float_mode_set`), Haptik `CONFIRM`.
- **Sprache** (`ModeCross.hasSpeech`): mindestens **200 ms am Stück** in 20-ms-Rahmen mit Effektivpegel **≥ 0,015**
  (≈ −36 dBFS); Lücken bis 40 ms (Verschlusslaute) zählen mit, längere Pausen setzen die Zählung zurück.
  Raumrauschen liegt darunter, ein Knacks (Haptik, Finger am Gehäuse, 50–80 ms) unter der Dauer — auch mehrere
  Einrast-Knackse beim Wischen über mehrere Ziele;
  leises Sprechen (Spitzen 0,04–0,09) zählt. Die bestehende 0,3-s-Grenze verwirft weiter Fehlgriffe, den Modus
  speichert die Wahl trotzdem.
- Die KI-Stufen sind auch ohne eingerichteten KI-Zugang wählbar; die Erkennung meldet dann wie bisher „ohne
  Textverbesserung“ (ein Fehlgriff zerstört nichts).

## 4. TalkBack und Schalterzugriff (N11)
Wischen ist mit Screenreader oder Schalter nicht bedienbar. Der Knopf bekommt Bedienungshilfe-Aktionen
(`BubbleActions`, Schalterzugriff zeigt sie ebenfalls):
- Doppeltipp = Tipp (unverändert, `OnClickListener`; die Zustandsbeschreibung nennt Start, Senden, Erneut senden).
- Je Modus „Modus: Glätten“ usw. (Prompt nur wenn eingeschaltet); der aktive trägt „(aktiv)“. Setzt nur den Modus
  und sagt ihn an, startet nichts. In IDLE, RECORDING (gilt dann für dieses Diktat) und ERROR; nicht in SENDING.
- Zustandsbeschreibung (`stateDescription`) des Knopfs: „Modus: Glätten“ — der aktive Modus gehört zur Ansage.
- „Knopf bis zum nächsten Textfeld ausblenden“ in IDLE, „Diktat verwerfen“ in RECORDING und ERROR.
Aktionen werden bei jedem Zustandswechsel, nach jedem Moduswechsel und beim Einblenden neu gesetzt.

## 5. Zustände der Geste (`BubbleGesture`)
`NONE → PRESSED` (Finger unten) → `HELD` (Haltezeit, Kreuz offen; mit oder ohne Aufnahme) oder `DRAGGING` (Slop vor der
Haltezeit). In `HELD` verschiebt Bewegung nicht, sie wählt nur ein Ziel (`aim`). Loslassen liefert `TAP`, `SEND`,
`DROP`, `PICK` (Modus), `DISCARD` (✕) oder `NONE`. Die Entscheidungen stehen Android-frei in `BubbleGesture`,
`ModeCross` (Lage, Auswahl, Deckkraft, Sprache) und `BubbleActions`; `FloatingMicService` und `ModeCrossWindow` führen
nur aus. Endet die Aufnahme während des Haltens von außen (12-min-Grenze, Mikrofonfehler), wirkt Loslassen auf
einem Modus nur als Moduswechsel und ✕ verwirft nichts mehr (die Übertragung läuft).

## 6. Bewusst nicht
- Kein Andocken an den Rand, kein Regler für Deckkraft oder Größe (Out-of-Scope).
- Kein Kreuz während einer laufenden Tipp-Aufnahme (dort bleibt Ziehen = Verschieben/Verwerfen).
- `FloatingMicService.kt` (> 800 Zeilen) wird nicht zerlegt: die neue Logik liegt in eigenen Dateien; den
  Touch-Listener herauszuziehen bräuchte eine Host-Schnittstelle mit rund zwölf Methoden und wäre nicht klarer.

## Neon-Befunde und Entscheidung

Zweitmeinung Gemini 4 Neon (Antigravity, 27.11.2026, nur Bericht, Lauf `20261127-223528-neon-bubble-concept`) zum
ersten Stand dieses Dokuments: 3 kritisch, 4 wichtig, 1 klein. Gewichtung durch Claude, jeweils gegen Code und
Rechnung geprüft:

| # | Befund (Neon) | Schwere | Entscheidung |
|---|---|---|---|
| 1 | Starres ✕ gegenüber + 180°-Bogen passt am Rand nie, Fallback klumpt Ziele auf den Knopf | kritisch | **übernommen**: ✕ auch schräg/seitlich, ganze Lage verschiebbar, Prüfungen auf Abstand, Überlappung, Richtung; Radius gedeckelt (140, Ecke 220 statt 205+); Tests auf 360/411 dp an allen Rändern und Ecken |
| 2 | Auswahl nach nächstem Abstand bevorzugt das nähere ✕; 12-dp-Hysterese macht Nachbarwechsel unmöglich | kritisch | **übernommen**: Auswahl nach Richtung mit 6° Winkel-Hysterese, radiale Hysterese nur an der Schwelle; Pille 72 dp (Spalt 7 statt 3 dp) |
| 3 | Spitzenpegel ≥ 0,1 wird von der eigenen Haptik ausgelöst und verwirft leises Sprechen | kritisch | **übernommen**: Effektivpegel ≥ 0,015 über ≥ 200 ms; `CONFIRM` vor dem Mikrofonstart; gilt auch für Halten ohne Ziel |
| 4 | Bedienkonflikte: langsamer Tipp nach aufgezogenem Kreuz, Schalter aus startet ungewollt, `ACTION_CANCEL` sendet auf ✕, doppelte Haptik | wichtig | **übernommen**: langsamer Tipp nur ohne Bewegung; Schalter aus: nach Bewegung nur schließen (ohne Bewegung bleibt der Tipp als Tremor-Ausweg); Abbruch auf ✕ verwirft; eine Haptik je Loslassen. Timing-Falle Haltezeit bleibt (Systemwert, HT3/HT9 prüfen das Gefühl) |
| 5 | 0,15 bis zur Schwelle unlesbar; aktiver Modus nur über Rand; Modus in Ruhe unsichtbar | wichtig | **teilweise**: voll schon bei 32 dp, aktiver Modus innen gefüllt; 0,15 auf dem Knopf bleibt (Christofs Vorgabe „ganz durchsichtig“); Modus-Anzeige in Ruhe nur per `stateDescription`, sichtbare Anzeige als Idee nach dem Handtest |
| 6 | WCAG 2.5.7: Kreuz bei Schalter aus antippbar; `stateDescription`; Aktionen je Zustand; Seite wechseln; < 12 sp | wichtig | **teilweise**: `stateDescription` mit Modus, Modus-Aktionen nicht in SENDING, Mindestschrift 12 sp. Nicht übernommen: antippbares Kreuz — die Modus-Aktionen bedient auch der Schalterzugriff, die Stufe ist außerdem in Tastatur-Leiste und Einstellungen per Tipp wählbar (Alternative ohne Ziehen vorhanden); „Seite wechseln“ ist Andocken (Out-of-Scope) |
| 7 | Android 12–16: untrusted touch, Drehen, Insets, Randgesten, Mehrfinger, externe Abbrüche | wichtig | **teilweise**: Fenster-Deckkraft 0,8, sicherer Bereich aus `WindowMetrics`-Insets, Vorprüfung vor `startRec`, externe Abbrüche definiert (§5). Nicht in diesem Branch: `setSystemGestureExclusionRects` und Mehrfinger-Behandlung (betreffen den Knopf schon heute, eigenes Todo), Drehen während der Geste (System sendet `ACTION_CANCEL`) |
| 8 | Kleinere Textstellen (SENDING beim Ziehen, RTL, 0,3-s-Grenze) | klein | **übernommen** (§1, §3); RTL: Koordinaten sind absolut (`Gravity.START` = links, App ohne RTL-Sprache) |

Kritisch offen nach Gewichtung: keiner.
