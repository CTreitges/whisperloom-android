# Schwebender Knopf am Textfeld — Gestenkonzept (Ziel 4.0)

Stand 27.11.2026, Branch `feat/bubble-textfeld`. Ergänzt UX-Spec §6.22; wo beide abweichen, gilt nach dem Merge
§6.22 (wird mit diesem Stand nachgezogen). Vorbild für das Bedienmuster: Wispr Flow (Android). Übernommen sind nur
Bedienmuster, keine Form, Farben oder Icons.

## 1. Gesten am Knopf

| Geste | Bedingung | Wirkung |
|---|---|---|
| Tippen | jeder Zustand | wie bisher: IDLE startet, RECORDING sendet, SENDING überspringt die KI, ERROR sendet erneut |
| Ziehen vor der Haltezeit | Finger verlässt den Touch-Slop, bevor die Haltezeit abläuft | Knopf verschieben; Ziel unten: in IDLE „Ausblenden bis zum nächsten Feld“, in RECORDING/ERROR „Verwerfen“ (unverändert) |
| Halten ohne Wischen | IDLE, Haltezeit abgelaufen, Finger im Slop | Push-to-Talk: Aufnahme startet (Haptik `CONFIRM`), Loslassen sendet mit dem aktuellen Modus |
| Halten und Wischen | IDLE, nach der Haltezeit weiter als die Einrast-Schwelle | Modus-Kreuz: Loslassen auf einem Modus setzt ihn dauerhaft und sendet mit ihm; auf ✕ verwerfen |

- **Haltezeit** = `ViewConfiguration.getLongPressTimeout()` (System, Standard 400 ms, folgt „Berührungs- und
  Haltedauer“). **Touch-Slop** = `scaledTouchSlop` des Systems.
- Halten wirkt nur aus IDLE. In RECORDING, SENDING und ERROR bleibt ein langer Druck ein Tipp (wie #38).
- **Langsamer Tipp (N9):** Endet ein Halten ohne eingerastetes Ziel in der ersten Sekunde nach Ablauf der Haltezeit
  (`BubbleGesture.HOLD_MIN_MS`), läuft die Aufnahme freihändig weiter statt zu senden. Mit eingerastetem Ziel gilt das
  nicht: das war eine bewusste Wahl.
- **Nach der Haltezeit verschiebt nichts mehr.** Bisher (#38) verschob Halten + Ziehen den Knopf mit gehaltener
  Aufnahme und zeigte das Verwerfen-Ziel unten; das ersetzt das ✕ im Kreuz.
- **Abgefangener Touch** (`ACTION_CANCEL`) im Halten: gehaltene Aufnahme senden (kein Diktat verlieren), Modus nicht
  ändern.
- **Schalter „Halten zum Sprechen“ aus** (E3, Ausweg bei Tremor): Halten öffnet das Kreuz ohne Aufnahme. Loslassen
  ohne Ziel wirkt wie ein Tipp (startet die Aufnahme), auf einem Modus nur Moduswechsel.
- **Kein Mikrofonrecht oder nicht eingerichtet:** Kreuz ohne Aufnahme (kein Sprung in den Assistenten mitten in der
  Geste); Loslassen ohne Ziel wirkt wie ein Tipp und zeigt den bisherigen Hinweis.

## 2. Modus-Kreuz als Bogen

Ziele: Aus, Glätten, Schöner, Kürzen (die Kurznamen der Tastatur-Leiste `kb_level_*`, damit beide Wege gleich
heißen), dazu **Prompt** als fünftes Ziel nur bei `Prefs.promptLevelEnabled`. Gegenüber dem Bogen das **✕**
(verwerfen).

### Maße

| Größe | Wert | Grund |
|---|---|---|
| Bogenradius (Knopfmitte → Zielmitte) | 112 dp | Ziele liegen frei neben dem 68-dp-Knopf (≥ 40 dp Luft), Daumenweg kurz |
| Ziel | Pille 76 × 44 dp, Text 14 sp (bei großer Schrift bis 10 sp verkleinert, einzeilig) | „Glätten“ passt; Treffer zählt nicht über die Fläche, sondern über das nächste Ziel (unten) |
| Spannweite | 180° (4 Ziele: 60° Abstand, 5 Ziele: 45°) | Nachbarn überlappen nicht: kleinster Mittenabstand 79 dp > 76 dp |
| ✕ | Kreis 52 dp, 96 dp Gegenrichtung | näher als die Modi, aber jenseits der Einrast-Schwelle |
| Einrast-Schwelle | 56 dp Fingerweg ab Druckpunkt | wie die Tastatur (`DictationGesture.ARM_DISTANCE_DP`) |
| Hysterese | 12 dp | wie die Tastatur: ein Ziel löst erst unter 44 dp, ein anderes Ziel gewinnt erst, wenn es ≥ 12 dp näher liegt |
| Rand | 8 dp zum Bildschirmrand | alle Ziele vollständig sichtbar |

### Auswahl
- Gewählt wird das **Ziel, das dem Finger am nächsten liegt**, sobald der Finger mindestens 56 dp vom Druckpunkt
  entfernt ist — nicht ein Winkelsektor. So funktioniert dieselbe Logik auch, wenn der Bogen am Rand gedreht oder
  gestaucht ist (N10).
- Einrasten auf ein neues Ziel: Haptik `CONTEXT_CLICK`; das Ziel wird hervorgehoben (Füllung `primaryContainer`,
  Text `onPrimaryContainer`, ✕ in `errorContainer`).

### Lage am Rand (N10)
Der Bogen öffnet nach oben. Passt er nicht vollständig in den Bildschirm (8 dp Rand), probiert die Logik weitere
Richtungen und Spannweiten durch und nimmt die erste, bei der **alle** Ziele und das ✕ im Bildschirm liegen:
1. Richtungen: oben, dann die übrigen 45°-Richtungen nach Nähe zur Richtung „Knopf → Bildschirmmitte“.
2. Je Richtung die Spannweiten 180°, 135°, 90°; bei kleinerer Spannweite wächst der Radius, bis benachbarte
   Ziele wieder ≥ 80 dp auseinander liegen.
3. Das ✕ liegt immer gegenüber der Bogenmitte.
4. Passt nichts (sehr kleiner Bildschirm): erste Lage, alle Ziele in den Bildschirm geklemmt.
Die Reihenfolge der Ziele bleibt im Uhrzeigersinn gleich (Aus zuerst), damit der Daumen sie blind wiederfindet.

### Sichtbarkeit (Christof: „ganz durchsichtig … bis man seinen Finger vom Button bewegt, dann besser sichtbar“)
- Das Kreuz erscheint, sobald die Haltezeit abläuft, mit **Deckkraft 0,15** — mit dem Finger auf dem Knopf kaum zu
  sehen.
- Deckkraft steigt linear mit dem Fingerweg vom Slop bis zur Einrast-Schwelle auf **1,0**: wer wegwischt, sieht das
  Kreuz deutlich. Keine Animation, sondern direkt dem Finger folgend (auch bei „Bewegung reduzieren“ gleich).
- Knopf: in Ruhe 0,5 deckend, bei Berührung, Halten und Kreuz 1,0 (§6.22).
- Kontrast (Leitlinien 02/03, WCAG 1.4.3/1.4.11): Pille `surfaceContainerHigh` 96 % mit Rand `outline` 1 dp, Text
  `onSurface` (≈ 12:1 zur Füllung). Die dunkle Füllung trägt den Text auf hellem wie dunklem App-Hintergrund; der Rand
  hebt die Pille auf dunklem Grund ab (`outline` zu `#0E1116` ≈ 6:1, zu Weiß ≈ 3:1). Der aktuelle Modus trägt einen
  2-dp-Rand `primary`.
- Eigenes Overlay-Fenster nach dem Muster von `CancelTarget`: nicht fokussierbar, nicht berührbar; nur während der
  Geste angehängt. Die Geste selbst gehört weiter dem Knopf-Fenster (der Touch begann dort).

## 3. Loslassen im Kreuz

| Ziel | Aufnahme läuft, Sprache erkannt | Aufnahme läuft, keine Sprache | keine Aufnahme |
|---|---|---|---|
| Modus | Modus speichern (`Prefs.refineMode`), senden mit ihm | Modus speichern, Aufnahme verwerfen, **keine** Anfrage (N7) | Modus speichern |
| ✕ | verwerfen (Toast `float_discarded`, Haptik `REJECT`) | verwerfen | nichts |
| kein Ziel | senden (Langsamer-Tipp-Regel) | senden (die bestehende 0,3-s-Grenze verwirft Fehlgriffe) | wie Tipp |

- Moduswechsel: Toast „Modus: Glätten“ (`float_mode_set`), Haptik `CONFIRM`.
- **Sprache erkannt** = Spitzenpegel der Aufnahme mindestens **0,1** (−20 dBFS) des Vollausschlags
  (`ModeCross.SPEECH_PEAK`, gemessen über `AudioRecorder.onAmplitude`). Raumrauschen liegt bei etwa 0,01–0,05, Sprache
  am Telefon über 0,2. Grenzfälle prüft HT4.
- Die KI-Stufen sind auch ohne eingerichteten KI-Zugang wählbar; die Erkennung meldet dann wie bisher „ohne
  Textverbesserung“ (ein Fehlgriff zerstört nichts).

## 4. TalkBack (N11)
Wischen ist für Screenreader nicht bedienbar. Der Knopf bekommt Bedienungshilfe-Aktionen:
- Doppeltipp = Tipp (unverändert, `OnClickListener`).
- Je Modus „Modus: Glätten“ usw. (Prompt nur wenn eingeschaltet); der aktive Modus trägt „(aktiv)“. Setzt nur den
  Modus und sagt ihn an, startet nichts.
- „Ausblenden“ in IDLE (wie Ablegen aufs Ziel unten), „Verwerfen“ in RECORDING und ERROR.
- „Erneut senden“ ist der Doppeltipp in ERROR (die Ansage des Zustands nennt es schon).
Aktionen werden bei jedem Zustandswechsel und nach jedem Moduswechsel neu gesetzt.

## 5. Zustände der Geste (`BubbleGesture`)
`NONE → PRESSED` (Finger unten) → `HELD` (Haltezeit, Kreuz offen; mit oder ohne Aufnahme) oder `DRAGGING` (Slop vor der
Haltezeit). In `HELD` verschiebt Bewegung nicht, sie wählt nur ein Ziel. Loslassen liefert `TAP`, `SEND`, `DROP`,
`PICK` (Modus), `DISCARD` (✕) oder `NONE`. Die Entscheidungen stehen Android-frei in `BubbleGesture` und `ModeCross`
(Lage, Auswahl, Deckkraft, Sprachpegel); `FloatingMicService` und `ModeCrossWindow` führen nur aus.

## 6. Bewusst nicht
- Kein Andocken an den Rand, kein Regler für Deckkraft oder Größe (Out-of-Scope).
- Kein Kreuz während einer laufenden Tipp-Aufnahme (dort bleibt Ziehen = Verschieben/Verwerfen); kann nach dem Handtest
  nachgezogen werden.
- `FloatingMicService.kt` (> 700 Zeilen) wird nicht zerlegt: die neue Logik liegt in eigenen Dateien; den
  Touch-Listener herauszuziehen bräuchte eine Host-Schnittstelle mit rund zwölf Methoden und wäre nicht klarer.
