> **Stand 2026-09-06, Grundlage der v3-Implementierung; Abweichungen siehe CHANGELOG/Code.** Unveränderte Kopie der verbindlichen UX-Spezifikation (Arbeitsstand `research/ux-spec.md`); Fundstellen-Verweise auf `research/*.md` meinen die Reports unter `docs/research/`.

# WhisperLoom v3 — Verbindliche UX-Spezifikation

Stand: 2026-09-06 · Autor: Lead-Design/Compose-Architektur · Gültig für Branch `v3-redesign`.
Diese Datei ist die **einzige Vorlage** für die Implementierungs-Agenten. Wo die drei Entwürfe (A „Fokus", B „Dashboard", C „Assistent") Alternativen offen ließen, ist hier **entschieden**. Abweichungen nur mit ausdrücklicher Freigabe des Nutzers.

---

## 0. Entscheidung und Rahmen

### 0.1 Bewertung der Entwürfe (0–10)

| Entwurf | Abdeckung 9 Pkt. | Erstnutzer-Klarheit | Modern/Dark | Compose-M3-Umsetzbarkeit | Konsistenz | Aufwand | Gesamt |
|---|---|---|---|---|---|---|---|
| A „Fokus" | 9 | 8 | 8 | 9 | 9 | 8 | **8,3** |
| B „Dashboard" | 9 | 7 | 8 | 7 | 8 | 6 | **7,4** |
| C „Assistent" | 10 | 9 | 9 | 8 | 9 | 7 | **8,7** |

**Gewinner: C „Geführter Assistent".** Übernommen aus A: Home-Hero als erstes Element mit maximalem Gewicht, Stufen-Auswahl als Radio-Liste statt 4-fach-Segmented, Snackbar statt Toast, Ausweg „Einrichten" im Share-Screen, präzise Icon-Pfadgeometrie. Übernommen aus B: Barrierefreiheits-Regeln (Live-Regions, `stateDescription`, verschmolzene Zeilen), ReadinessBanner auf Home, „Position zurücksetzen", 21-Balken-Pegelband in der IME, Notification-Tap → Home. Verworfen: B's `NavHost` (keine Nav-Lib), B's „Letztes Diktat"-Zeile (neuer Zustand ohne Nutzen), C's Ausprobier-Feld auf Home (gehört zu Tastatur), C's 4-fach-Segmented für Stufen (Labels zu lang), A's `gpt-5-nano`-Default (Auslauf 12/2026), A/B-Blau als Akzent (Türkis aus C ist markanter und kollidiert nicht mit Aufnahme-Rot).

### 0.2 Technischer Rahmen (verbindlich, aus `compose-stack.md`)

- Stack: AGP 9.4.0 · Gradle 9.6.1 · JDK 17 · compileSdk 37 · targetSdk 35 · minSdk 26 · Compose BOM **2026.08.00** (ui 1.12.0, **material3 1.4.0**) · activity-compose 1.13.0 · lifecycle-runtime-compose 2.11.0. Quelle: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- **Keine** Navigation-Lib, **keine** `material-icons-*`-Artefakte, **keine** Expressive-APIs (`@ExperimentalMaterial3ExpressiveApi`). Nur stabile material3-1.4.0-Komponenten: `Scaffold`, `TopAppBar`/`LargeTopAppBar`, `ElevatedCard`/`OutlinedCard`, `ListItem`, `Switch`, `RadioButton`, `SingleChoiceSegmentedButtonRow`+`SegmentedButton`, `ExposedDropdownMenuBox`, `Button`/`FilledTonalButton`/`OutlinedButton`/`TextButton`/`IconButton`, `LinearProgressIndicator(progress = { })`, `CircularProgressIndicator`, `ModalBottomSheet`, `AlertDialog`, `AssistChip`/`FilterChip`/`InputChip`, `OutlinedTextField`, `Snackbar`, `HorizontalDivider`, `FlowRow` (foundation).
- Navigation: `MainActivity` (einzige Compose-Activity) mit `sealed class Screen` + `rememberSaveable` + `BackHandler`. `ShareTranscribeActivity` bleibt als Intent-Ziel (Compose-Inhalt, `excludeFromRecents`). `SetupActivity` und `SettingsActivity` entfallen; `xml/method.xml` `settingsActivity` → `MainActivity` mit Extra `route=settings`.
- Theme: fest dunkel (`darkColorScheme` aus §3), **kein** Dynamic Color, `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))`, Manifest-Theme `Theme.Material.NoActionBar` mit `windowBackground = @color/loom_background`.
- Eine Farb-Wahrheit: alle Hex-Werte in `res/values/colors.xml` (Präfix `loom_`), Compose hebt sie per `colorResource()` in `darkColorScheme` + `LocalLoomColors` (CompositionLocal für `recording`, `success`, `warning` + Container). IME/Overlay/Notification lesen dieselben `@color/loom_*`.
- Icons: Material Symbols Rounded (Weight 400, Fill 0, 24 dp) von https://fonts.google.com/icons als `res/drawable/ic_<name>.xml` eingecheckt; Compose per `painterResource`. Liste in §3.3.

---

## 1. Screen-Inventar, Navigation, Startlogik

### 1.1 Inventar

| ID | Screen | Technik | Zweck |
|---|---|---|---|
| R | Router (unsichtbar) | `MainActivity`, Funktion | Assistent oder Home |
| H | **Home** | Compose | Hero-Button Knopf starten/beenden + Status |
| W | **Einrichtungs-Assistent** | Compose, `AnimatedContent` je Seite | W1 Willkommen · Schritte 1–7 · W9 Fertig |
| W-Ü | Schritt-Übersicht | `ModalBottomSheet` | alle Schritte mit Status, anspringbar |
| E | **Einstellungen (Hub)** | Compose | 5 Gruppen + Über |
| E1 | Erkennung | Compose | Engine · Anbieter/Key/Modell (Transkription) · Sprache · Kontext |
| E2 | Text | Compose | Stufe · KI-Füllwörter · eigener LLM-Zugang · Regeln ohne KI |
| E3 | Knopf & Tastatur | Compose | Knopf-Status/Start/Position · Berechtigungen · Tastatur · Probierfeld |
| E4 | Offline-Modelle | Compose | Liste · Download · Löschen · Auswahl · Engine-Schalter |
| E5 | Hilfe & Anleitung | Compose | Anleitung · API-Keys · Eigener Server · Offline · Datenschutz · Probleme |
| E6 | Über WhisperLoom | `ModalBottomSheet` | Version, Lizenzen, Quellcode |
| S | **Transkription** (Share-Ziel) | Compose in `ShareTranscribeActivity` | geteilte Sprachnachrichten |
| B1 | Sheet „Wo bekomme ich einen Key?" | `ModalBottomSheet` | Schritte + Link je Anbieter |
| B2 | Sheet „Eigenes Modell" | `ModalBottomSheet` | Freitext Modell-ID |
| B3 | Sheet „Füllwörter bearbeiten" | `ModalBottomSheet` | Chips je Sprache + eigene Wörter |
| D1 | Dialog „Modell löschen?" | `AlertDialog` | Bestätigung |
| D2 | Dialog „Über mobile Daten laden?" | `AlertDialog` | vor Download über gebührenpflichtiges Netz |
| V1 | Schwebender Knopf + Abbrechen-Ziel | View (Overlay) | 4 Zustände |
| V2 | IME-Tastatur | View | Halten-zum-Sprechen |
| N1 | Foreground-Notification | Framework | „Diktat aktiv" + Beenden |

### 1.2 Startlogik (Router) — präzise Bedingung „eingerichtet"

Wird bei jedem `onResume` der `MainActivity` ausgewertet (Systemdialoge ändern Status außerhalb der App):

```
providerNeedsKey  = Prefs.sttProvider != "custom"
recognitionReady  = (Prefs.engine == "online"
                        && Prefs.apiBaseUrl.isValidHttpUrl()
                        && (!providerNeedsKey || Prefs.apiKey.isNotBlank()))
                 || (Prefs.engine == "offline"
                        && ModelStore.isInstalled(Prefs.offlineModel))      // Datei ohne .part, Größe == Katalog
micGranted        = checkSelfPermission(RECORD_AUDIO) == GRANTED
overlayOk         = Settings.canDrawOverlays(ctx)
                 || (Prefs.overlaySkipped && imeEnabled())                 // bewusster „nur Tastatur"-Pfad
isSetUp           = recognitionReady && micGranted && overlayOk
```

- `Prefs.engine` Default `""` (nicht gewählt) → `recognitionReady = false`.
- Bedienungshilfe, Benachrichtigungen (API ≥ 33) und Tastatur sind **empfohlen/optional**, blockieren nicht (Clipboard-Fallback existiert; Notification hat nur „Beenden", das Home kann das auch).
- `isSetUp == false` → **W** öffnet auf dem **ersten nicht erledigten Schritt** (Reihenfolge §2.2); W1 Willkommen nur, wenn `Prefs.welcomeSeen == false`.
- `isSetUp == true` → **H**. H prüft zusätzlich weiche Punkte (Bedienungshilfe aus, Benachrichtigung verweigert, Offline gewählt aber Modell gelöscht) und zeigt sie als Banner/Ampel — nie als Rauswurf in den Assistenten.
- Sonderfall: Home offen, Nutzer entzieht später Mikrofon oder Overlay → Home bleibt, Hero-Button `enabled=false` mit Hinweis-Chip, der in den passenden Schritt springt.

```
Launcher / Notification-Tap (route=home) / IME-Zahnrad (route=settings) / Share-Fehler (route=setup)
        │
        ▼
   [R] isSetUp?
        ├── nein ──► [W] erster offener Schritt ──…──► [W9 Fertig] ──► [H]   (W vom Back-Stack entfernt)
        │              Zurück: Schritt zurück; auf erstem sichtbaren Schritt = App verlassen
        │              (W aus H/E geöffnet: Zurück = dorthin; Kopfzeile zeigt `close`)
        └── ja ────► [H] ──⚙──► [E] ──► E1 | E2 | E3 | E4 | E5 | E6
                     [H] ──?──► [E5]
                     [H] Status-Zeile ──► E1 / E2 / E3 / E4
                     [H] Hinweis-Chip / Banner ──► [W] auf genau dem Schritt
Teilen-Menü (ACTION_SEND / SEND_MULTIPLE audio/*) ──► [S]
                     [S] recognitionReady == false ──► Button „Einrichtung öffnen" ──► MainActivity(route=setup)
```

Deep-Link-Extras der `MainActivity`: `route` ∈ {`home`,`settings`,`setup`} und optional `step` ∈ {1..7}. Kein Screen ohne Ausweg: jeder Fehlerzustand besitzt eine Aktion (Erneut / Einrichten / Systemeinstellung öffnen / Schließen).

### 1.3 Globale Layout-Regeln

- `Scaffold(containerColor = background)`, Inhalt in `LazyColumn`/`Column(verticalScroll)`; horizontales Padding **20 dp**, Abstand zwischen Karten **16 dp**, Karten-Innenabstand **20 dp**, Abschnittsabstand **24 dp**, unten 24 dp + Navigations-Inset. Raster 4 dp.
- Karten: `ElevatedCard(shape = RoundedCornerShape(20.dp), colors = surfaceContainer, elevation 0)`; `OutlinedCard` mit `outlineVariant` 1 dp für Info/Leerzustände.
- Buttons: Primär `Button` 56 dp hoch (Hero 64 dp), volle Breite, voll rund; `FilledTonalButton` für Sekundär; `TextButton` für Tertiär. Alle interaktiven Elemente ≥ 48 × 48 dp; `ListItem` min 56 dp (mit Supporting 72 dp).
- Speichern: Einstellungen schreiben sofort (`onValueChange` bzw. Fokusverlust bei Textfeldern) in `Prefs` — kein „Speichern"-Button. Textfelder zusätzlich bei `onPause`.
- Fehler in Einstellungen immer feldbezogen (`isError` + `supportingText`) oder als Inline-Chip; Dialoge nur für destruktive Aktionen (D1) und Netz-Entscheidungen (D2).
- Snackbar (nie Toast) in allen Compose-Screens; Overlay/IME behalten Toast (kein Host).

---

## 2. Screens im Detail

Notation: Reihenfolge von oben nach unten; Texte sind die finalen Strings (Keys in §6); Abstände in dp.

### 2.1 H — Home

```
Scaffold
├─ TopAppBar (klein, 64 dp, containerColor background)
│    title  "WhisperLoom"                              titleLarge · onSurface
│    actions: IconButton ic_help      cd home_cd_help      → E5
│             IconButton ic_settings  cd home_cd_settings  → E
├─ [Padding 20 / Abstand 16]
├─ HERO-KARTE  ElevatedCard(surfaceContainer, radius 24, padding 20)          ← erstes Element, Hauptaktion
│    Row(gap 16, vertical center)
│    ├─ Box 56 dp Kreis:  AUS → primaryContainer, Icon ic_mic 28 dp onPrimaryContainer
│    │                    AN  → recordingContainer, 2 dp Ring recording, Icon ic_mic 28 dp recordingText,
│    │                          Puls-Ring (§5 Motion) hinter dem Kreis
│    └─ Column
│         Text titleLarge   home_hero_title "Schwebender Mikro-Knopf" (beide Zustände gleich)
│         Text bodyMedium onSurfaceVariant
│              AUS → home_hero_sub_off  "Aus — startet den Knopf über allen Apps"
│              AN  → home_hero_sub_on   "Läuft — antippen zum Diktieren, ziehen zum Verschieben"
│    Spacer 16
│    HAUPTBUTTON (volle Breite, 64 dp, shape voll rund, labelLarge 16 sp, Leading-Icon 24 dp)
│      AUS      → Button(primary/onPrimary)                    ic_play_arrow  home_hero_btn_start "Mikro-Knopf starten"
│      AN       → FilledTonalButton(container recordingContainer, content recordingText)  ic_stop  home_hero_btn_stop "Mikro-Knopf beenden"
│      PENDING  → wie zuvor, enabled=false, Icon ersetzt durch CircularProgressIndicator 20 dp (max. 500 ms, danach Status neu lesen)
│      BLOCKED  → Button enabled=false; darunter (8 dp) AssistChip(leading ic_warning, warningContainer/onWarningContainer, klickbar)
│                 Text: home_blocked_mic     "Mikrofon fehlt — beheben"            → W Schritt 3
│                       home_blocked_overlay "Über anderen Apps anzeigen fehlt — beheben" → W Schritt 4
│                 Chip ist Live-Region (Polite).
├─ READINESS-BANNER (nur wenn ≥ 1 weicher Punkt offen)  Card(warningContainer, radius 20, padding 16)
│    Row: Icon ic_warning 24 onWarningContainer · Column(weight 1)
│         Text titleSmall onWarningContainer   home_banner_title  "Noch nicht optimal"
│         Text bodyMedium onWarningContainer   eine der Zeilen (Priorität in dieser Reihenfolge, nur die erste):
│             home_banner_a11y   "Ohne Bedienungshilfe landet der Text nur in der Zwischenablage."
│             home_banner_model  "Offline gewählt, aber kein Modell geladen."
│             home_banner_notif  "Ohne Benachrichtigung kannst du den Knopf nur hier beenden."
│         FilledTonalButton (rechtsbündig, 40 dp)  home_banner_fix "Beheben"  → W Schritt 5 / E4 / W Schritt 6
├─ STATUS-KARTE  OutlinedCard(radius 20)
│    Text titleSmall onSurfaceVariant (padding 20/12)  home_status_title "Status"
│    ListItem ×4–5 (leading Status-Icon 24 dp: ic_check_circle success | ic_warning warning | ic_error error; trailing ic_chevron_right; ganze Zeile klickbar)
│    ├─ home_row_recognition "Erkennung"
│    │     Online:  "Online · {Anbieter} · {Modell-Label}"      Offline: "Offline · {Modell-Label} ({Größe})"
│    │     Icon: success | error wenn Offline ohne Modell                                             → E1
│    ├─ home_row_refine "Textverbesserung"
│    │     "Aus" | "{Stufe} · {LLM-Modell-Label}"   Icon: neutral ic_auto_fix_high onSurfaceVariant   → E2
│    ├─ home_row_permissions "Berechtigungen"
│    │     "Mikrofon ✓ · Über Apps ✓ · Bedienungshilfe ✓"  bzw. "… Bedienungshilfe ✗ (Zwischenablage)"
│    │     Icon: success wenn alle drei, warning wenn Bedienungshilfe fehlt, error wenn Pflicht fehlt   → E3
│    ├─ home_row_keyboard "Diktat-Tastatur"
│    │     "Aktiv" | "Aktiviert, nicht ausgewählt" | "Nicht aktiviert (optional)"   Icon: success | neutral ic_keyboard → E3
│    └─ home_row_models "Offline-Modelle"   (nur wenn ≥ 1 Modell installiert ODER engine == offline)
│          "{n} geladen · {MB} MB belegt" | "Keins geladen"                                           → E4
├─ INFO-KARTE  OutlinedCard  Row: Icon ic_voicemail 24 tertiary · Text bodyMedium home_share_hint
│    "Sprachnachrichten abtippen: in WhatsApp lange drücken → Teilen → WhisperLoom."  · TextButton common_more "Mehr" → E5 Abschnitt 1
└─ FOOTER  Text bodySmall outline, zentriert: home_version "WhisperLoom %1$s"  ·  TextButton(bodySmall) home_rerun_setup "Einrichtung erneut öffnen" → W Schritt 1 (Zurück = Home)
```

Zustände H: kein Lade-/Leerzustand (alles synchron aus Prefs/System; Service-Status via `FloatingMicService.isRunning`). PENDING max. 500 ms nach Tipp (bestehendes `postDelayed(500)`-Verhalten). Fehler: Service-Start scheitert (Overlay entzogen) → Snackbar `home_snack_overlay_lost` mit Aktion `home_snack_fix` → W Schritt 4.

Semantik: Hero-Button `contentDescription` = Label, `stateDescription` = home_state_running / home_state_stopped. Status-Zeilen `semantics(mergeDescendants = true)`, Status-Text als `stateDescription`; Chevron `contentDescription = null`.

### 2.2 W — Einrichtungs-Assistent

**Rahmen auf jeder Seite:**

```
Scaffold
├─ TopAppBar (klein): navigationIcon ic_arrow_back (Schritt zurück; auf W1 bzw. erstem sichtbaren Schritt: ic_close = App/Home verlassen) cd cd_back / cd_close
│    title titleMedium  setup_title "Einrichtung"
│    action IconButton ic_checklist cd setup_cd_overview → W-Ü   (nicht auf W1/W9)
├─ Fortschrittszeile (nicht auf W1/W9): LinearProgressIndicator(progress = { done/total }, 6 dp, strokeCap Round, primary auf surfaceContainerHighest), Padding 20/8
│    Text labelMedium onSurfaceVariant  setup_progress "Schritt %1$d von %2$d"   (total = 7 auf API ≥ 33, sonst 6)
├─ Inhalt Column(padding 24 horizontal, 16 top, gap 16), scrollbar
│    ├─ Box 72 dp Kreis primaryContainer  └─ Icon 36 dp onPrimaryContainer (Schritt-Icon)
│    ├─ Text headlineSmall onSurface        Schritt-Titel
│    ├─ Text bodyLarge onSurfaceVariant     Erklärung (2–3 Sätze)
│    ├─ Status-Chip AssistChip(enabled=false-Optik, Leading-Icon 18 dp):
│    │      setup_chip_done     "Erledigt"    ic_check_circle · successContainer/onSuccessContainer
│    │      setup_chip_open     "Fehlt noch"  ic_warning      · warningContainer/onWarningContainer
│    │      setup_chip_optional "Optional"    ic_info         · secondaryContainer/onSecondaryContainer
│    │      setup_chip_skipped  "Übersprungen" ic_remove_circle_outline · surfaceContainerHigh/onSurfaceVariant
│    └─ Schritt-spezifischer Inhalt (unten)
└─ Bottom-Bar  Surface(surfaceContainer, oben 1 dp outlineVariant, padding 20/16 + Nav-Inset)
     Row: TextButton setup_back "Zurück" (links; unsichtbar auf W1)  ·  Spacer  ·  [TextButton setup_skip "Überspringen" nur optionale Schritte]  ·  Button 56 dp (Hauptaktion)
     Hauptaktion: solange Schritt offen = schrittspezifischer Button; erledigt = Button setup_next "Weiter" (trailing ic_arrow_forward).
     Laufende Aktion (Download/Prüfung): Button enabled=false, Leading CircularProgressIndicator 18 dp, Text setup_working "Bitte warten …"
```

Rückkehr aus Systemdialogen/-einstellungen: `onResume` → Status aller Schritte neu prüfen; erledigter Schritt: Chip morpht zu „Erledigt" (§5 Motion), Hauptaktion wird „Weiter". Fehler → Snackbar mit Aktion `common_retry` „Erneut"; Schritt bleibt stehen. Kein Schritt ohne Weg vor (Überspringen/Alternative) oder zurück.

**W-Ü Schritt-Übersicht (`ModalBottomSheet`):** Titel titleLarge `setup_overview_title` „Alle Schritte"; je Schritt `ListItem`: Leading Status-Icon (ic_check_circle success / ic_radio_button_unchecked outline / ic_remove_circle_outline onSurfaceVariant), Headline Schrittname, Supporting Kurzstatus (z. B. „OpenAI · GPT Transcribe", „fehlt", „übersprungen"), Trailing ic_chevron_right; Tippen springt hin. Unten TextButton `common_close` „Schließen".

**W1 Willkommen** (nur `welcomeSeen == false`; kein Fortschritt, kein Chip):
- Hero: App-Icon-Motiv (Vordergrund-Vektor aus §4.1) 96 dp in primaryContainer-Kreis 120 dp.
- headlineMedium `welcome_title` „Diktiere in jede App."
- bodyLarge `welcome_body` „WhisperLoom nimmt auf, erkennt den Text über deinen eigenen Zugang oder ein Offline-Modell und tippt ihn ins aktuelle Feld. In ein paar Schritten ist alles bereit."
- Drei rahmenlose `ListItem` (Icon 24 primary + bodyMedium): `welcome_point_1` „Online oder offline — du entscheidest" (ic_cloud) · `welcome_point_2` „Dein Key bleibt auf dem Gerät" (ic_lock) · `welcome_point_3` „Dauert etwa zwei Minuten" (ic_schedule).
- Bottom: Button `welcome_start` „Los geht's" → setzt `welcomeSeen = true`, → Schritt 1.

**Schritt 1 — Erkennungsweg** (Icon ic_graphic_eq; Pflicht):
- Titel `setup_s1_title` „Wie soll WhisperLoom Sprache erkennen?"; Text `setup_s1_body` „Du kannst später jederzeit wechseln."
- Zwei auswählbare `OutlinedCard` untereinander (Radio-Semantik `selectableGroup`, Rahmen 2 dp primary bei Auswahl sonst 1 dp outlineVariant, Padding 20, Höhe wrap ≥ 96):
  - **Online** — Icon ic_cloud 28 primary · titleMedium `setup_s1_online` „Online-Dienst" · bodyMedium onSurfaceVariant `setup_s1_online_body` „Beste Qualität, schnell. Audio wird an den gewählten Anbieter gesendet. Braucht einen API-Key (bei Groq kostenlos)." · labelMedium primary `setup_s1_recommended` „Empfohlen".
  - **Offline** — Icon ic_offline_bolt 28 tertiary · `setup_s1_offline` „Offline auf dem Gerät" · `setup_s1_offline_body` „Alles bleibt auf dem Gerät. Modell einmalig laden (32–574 MB), Erkennung dauert einige Sekunden." · Wenn Gerät ungeeignet (`/proc/cpuinfo` ohne `fphp`+`asimddp` oder RAM < 3 GB, Quelle `whisper-cpp.md §3`): Karte `enabled=false`, Chip warning `setup_s1_offline_unavailable` „Auf diesem Gerät nicht verfügbar".
- Auswahl schreibt `Prefs.engine`. Hauptaktion: `setup_next` „Weiter" (enabled sobald gewählt).

**Schritt 2a — Zugang zum Dienst** (bei Online; Icon ic_key; Pflicht):
- Titel `setup_s2a_title` „Zugang zum Dienst"; Text `setup_s2a_body` „Wähle den Anbieter und füge deinen API-Key ein. Der Key wird nur auf diesem Gerät gespeichert."
- `ExposedDropdownMenuBox` Label `rec_provider` „Anbieter": OpenAI · Groq (kostenlos) · Mistral · Together AI · DeepInfra · OpenRouter · Eigener Server (Reihenfolge fest; Voreinstellung OpenAI). Auswahl setzt `sttProvider`, `apiBaseUrl` (Preset) und `apiModel` (erstes Modell der Liste, §8.2). Unter dem Feld bodySmall onSurfaceVariant: Base-URL des Presets (readonly).
- Nur bei „Eigener Server": `OutlinedTextField` `rec_base_url` „Base-URL" (`KeyboardType.Uri`, Placeholder `https://server:8000/v1`), supportingText `rec_base_url_hint` „Muss auf /v1 enden. http:// nur im eigenen Netz (LAN/VPN)."; Validierung `ServerUrlCheck.problem()` → `isError` + Meldung (`err_url_invalid`, `err_url_scheme`, `err_url_https_required`, `err_url_cleartext_public`, `err_url_v1`).
- `OutlinedTextField` `rec_api_key` „API-Key": `PasswordVisualTransformation`, `singleLine`, `imeAction Done`; Trailing-Icons: ic_visibility/ic_visibility_off (cd `cd_key_show`/`cd_key_hide`) und ic_content_paste (cd `cd_paste`, fügt Zwischenablage ein, supportingText kurz `rec_key_pasted` „Eingefügt"). supportingText Standard `rec_key_local` „Wird nur auf diesem Gerät gespeichert." Bei „Eigener Server": Label `rec_api_key_optional` „API-Key (optional)".
- `ExposedDropdownMenuBox` `rec_model` „Modell": Preset-Modelle des Anbieters (Labels aus §8.2) + letzter Eintrag `rec_model_custom` „Eigenes Modell…" → B2.
- TextButton mit Leading ic_help `rec_key_where` „Wo bekomme ich einen Key?" → B1.
- FilledTonalButton Leading ic_verified `rec_test` „Zugang prüfen": sendet 1 s Stille-WAV an `/audio/transcriptions` (Provider-Flags §8.2 beachten). Ergebnis-Chip inline (AnimatedVisibility): success `rec_test_ok` „Verbunden · %1$s s" / error `rec_test_fail` mit Grund aus `err_401` · `err_429` · `err_net` · `err_timeout` · `err_server` · `err_unknown`. Prüfung ist optional.
- bodySmall mit Leading ic_privacy_tip 16 onSurfaceVariant: `rec_privacy_online` „Audio wird zur Erkennung an %1$s gesendet."
- Chip: „Fehlt noch" → „Erledigt", sobald `recognitionReady`. Hauptaktion: `setup_next`.

**Schritt 2b — Offline-Modell laden** (bei Offline; Icon ic_download; Pflicht):
- Titel `setup_s2b_title` „Offline-Modell laden"; Text `setup_s2b_body` „Wähle ein Modell. Empfehlung: Small — gute Qualität für Deutsch bei 190 MB."
- Modell-Liste exakt wie E4 (§2.7 Zeilenaufbau), gleiche Komponente `ModelRow`.
- Chip „Erledigt", sobald das gewählte Modell installiert ist. Hauptaktion: `setup_next`.

**Schritt 3 — Mikrofon** (ic_mic; Pflicht, kein Überspringen):
- Titel `setup_s3_title` „Mikrofon erlauben"; Text `setup_s3_body` „Ohne Mikrofon kein Diktat. Android fragt dich gleich — bitte „Bei Nutzung der App" wählen."
- Hauptaktion: `setup_s3_btn` „Mikrofon erlauben" → Runtime-Dialog `RECORD_AUDIO`.
- Dauerhaft verweigert (`shouldShowRequestPermissionRationale == false` nach mind. einer Ablehnung): Text wird `setup_s3_denied` „Du hast das Mikrofon abgelehnt. Bitte in den App-Einstellungen erlauben.", Hauptaktion `setup_open_app_settings` „App-Einstellungen öffnen" (`ACTION_APPLICATION_DETAILS_SETTINGS`).

**Schritt 4 — Über anderen Apps anzeigen** (ic_layers; Pflicht mit Alternative):
- Titel `setup_s4_title` „Über anderen Apps anzeigen"; Text `setup_s4_body` „Der schwebende Mikro-Knopf liegt über anderen Apps. Dafür braucht Android deine Erlaubnis — du landest gleich in den Systemeinstellungen: dort „WhisperLoom" einschalten und zurück."
- Aufklappbare `OutlinedCard` (TextButton `setup_more_info` „Was passiert dabei?" → AnimatedVisibility) mit 3 nummerierten Zeilen `setup_s4_step_1..3` („Systemeinstellung öffnet sich", „Schalter „Über anderen Apps anzeigen" einschalten", „Zurück-Taste — WhisperLoom prüft automatisch").
- Hauptaktion: `setup_s4_btn` „Einstellung öffnen" → `ACTION_MANAGE_OVERLAY_PERMISSION` mit `package:`-URI. Rückkehr: `onResume` prüft `canDrawOverlays()`.
- Alternative: TextButton `setup_s4_keyboard_only` „Nur Tastatur nutzen" → `overlaySkipped = true`, Chip „Übersprungen", Schritt 7 (Tastatur) wird Pflicht (Chip „Fehlt noch", kein Überspringen dort). Hinweis bodySmall `setup_s4_keyboard_only_hint` „Ohne diesen Schritt funktioniert nur die Tastatur-Variante."

**Schritt 5 — Bedienungshilfe** (ic_accessibility_new; empfohlen):
- Titel `setup_s5_title` „Text automatisch einfügen"; Text `a11y_description` (bestehend).
- Info-Karte secondaryContainer/onSecondaryContainer, Icon ic_info: `setup_s5_fallback` „Ohne diesen Schritt landet der Text in der Zwischenablage — du fügst ihn dann selbst ein."
- Aufklappbar „Was passiert dabei?": `setup_s5_step_1..3` („Bedienungshilfe-Einstellungen öffnen sich", „Installierte Apps → WhisperLoom → Ein", „Bei „Eingeschränkte Einstellung": App-Info → ⋮ → Eingeschränkte Einstellungen zulassen, dann erneut"). Der dritte Punkt betrifft Sideload-Installationen ab Android 13 — im Gerätetest prüfen.
- Hauptaktion: `setup_s5_btn` „Bedienungshilfe aktivieren" → `ACTION_ACCESSIBILITY_SETTINGS`; Rückkehr prüft `TextInserterAccessibilityService.isRunning()`. TextButton `setup_skip`.

**Schritt 6 — Benachrichtigungen** (nur API ≥ 33; ic_notifications; empfohlen):
- Titel `setup_s6_title` „Beenden per Benachrichtigung"; Text `setup_s6_body` „Solange der Knopf läuft, zeigt Android eine stille Benachrichtigung mit „Beenden". Ohne Erlaubnis ist sie unsichtbar — beenden kannst du den Knopf dann in der App."
- Hauptaktion: `setup_s6_btn` „Benachrichtigungen erlauben" → `POST_NOTIFICATIONS`; dauerhaft verweigert → `setup_open_app_settings`. TextButton `setup_skip`.

**Schritt 7 — Diktat-Tastatur** (ic_keyboard; optional, Pflicht wenn `overlaySkipped`):
- Titel `setup_s7_title` „Diktat-Tastatur"; Text `setup_s7_body` „Alternative zum Knopf: die WhisperLoom-Tastatur mit Halten-zum-Sprechen. Zwei Schritte: aktivieren, dann auswählen."
- Zwei `ListItem` mit Leading Status-Icon + Trailing FilledTonalButton: `setup_s7_enable` „1 · Tastatur aktivieren" → `setup_s7_enable_btn` „Aktivieren" (`ACTION_INPUT_METHOD_SETTINGS`); `setup_s7_select` „2 · Tastatur auswählen" → `setup_s7_select_btn` „Auswählen" (`InputMethodManager.showInputMethodPicker()`), `enabled` erst wenn 1 erledigt.
- `OutlinedTextField` `setup_try_hint` (bestehend „Zum Diktieren hierher tippen …"), `minLines 2`.
- TextButton `setup_skip` (entfällt bei `overlaySkipped`). Erledigt = IME aktiviert (Auswahl ist nicht prüfbar-pflichtig).

**W9 Fertig** (kein Fortschritt/Chip):
- Hero: 96 dp Kreis successContainer, Icon ic_check 48 onSuccessContainer (Spring-Einblendung §5).
- headlineMedium `setup_done_title` „Bereit zum Diktieren".
- `ElevatedCard` `setup_done_summary` „Deine Einrichtung": `ListItem`-Zeilen mit Status-Icon: Erkennung („Online · Groq · Whisper Large v3 Turbo" / „Offline · Small"), Mikrofon, Über anderen Apps, Bedienungshilfe (ggf. `setup_done_a11y_skipped` „übersprungen — Zwischenablage"), Benachrichtigungen, Tastatur. Übersprungene Zeilen sind antippbar → Schritt.
- Kurzanleitung 3 `ListItem` (nummerierte 24-dp-Kreise primaryContainer): `help_dictate_1` „Knopf antippen = Aufnahme", `help_dictate_2` „Nochmal antippen = fertig & einfügen", `help_dictate_3` „Auf ✕ ziehen = verwerfen".
- Bottom: Button `setup_done_start` „Knopf starten & los" (startet `FloatingMicService`, → H; bei `overlaySkipped`: Text `setup_done_home` „Zum Start"). W wird vom Back-Stack entfernt.

### 2.3 E — Einstellungen (Hub)

```
Scaffold(topBar = LargeTopAppBar(title settings_title "Einstellungen", navigationIcon ic_arrow_back, scrollBehavior exitUntilCollapsed))
LazyColumn — ListItem je Gruppe (Leading: Icon 24 in 40-dp-Kreis surfaceContainerHigh · Headline titleMedium · Supporting bodyMedium = aktueller Wert · Trailing ic_chevron_right), getrennt durch HorizontalDivider(outlineVariant, inset 76 dp)
├─ ic_graphic_eq         settings_group_recognition "Erkennung"        "Online · {Anbieter} · {Modell}" | "Offline · {Modell}"   → E1
├─ ic_auto_fix_high      settings_group_text        "Text"             "{Stufe} · Füllwörter · Groß-Schreibung" (nur aktive Regeln)  → E2
├─ ic_touch_app          settings_group_button      "Knopf & Tastatur" "Knopf läuft · Tastatur aktiv" | "Knopf aus · Tastatur nicht aktiviert" → E3
├─ ic_download_for_offline settings_group_models    "Offline-Modelle"  "{n} geladen · {MB} MB belegt" | settings_models_none "Keins geladen" → E4
├─ ic_help               settings_group_help        "Anleitung & Hilfe" settings_help_sub "Anleitung, API-Keys, Datenschutz" → E5
└─ ic_info               settings_group_about       "Über WhisperLoom"  "Version {v}" → E6 (Sheet: Version, Lizenzen whisper.cpp MIT/Compose Apache 2.0, Link GitHub-Repo, Button common_close)
```

Keine Switches auf Hub-Ebene. Kein Lade-/Leerzustand.

### 2.4 E1 — Erkennung

```
LargeTopAppBar rec_title "Erkennung"
Column(padding 20, gap 16)
├─ SingleChoiceSegmentedButtonRow (volle Breite, 48 dp): [ic_cloud rec_engine_online "Online-Dienst"] [ic_offline_bolt rec_engine_offline "Offline-Modell"] → Prefs.engine
│    Wechsel auf Offline ohne installiertes Modell: darunter OutlinedCard(warningContainer-Rahmen): Icon ic_warning · rec_no_model "Kein Modell geladen" · FilledTonalButton rec_load_model "Modell laden" → E4
├─ [ONLINE] ElevatedCard rec_card_transcription "Transkription"  (Inhalt = identisch Schritt 2a: Anbieter-Dropdown · Base-URL-Zeile/-Feld · API-Key · Modell-Dropdown · „Wo bekomme ich einen Key?" · „Zugang prüfen" + Chip)
│    Abgekündigte Modelle tragen im Label das Suffix aus §8.2 („Auslauf 02/2027").
├─ [OFFLINE] ElevatedCard rec_card_offline "Offline-Modell": ListItem Headline "{Modell-Label}" Supporting "{MB} MB · {Qualitäts-Hinweis}" Trailing TextButton common_change "Ändern" → E4; bodySmall rec_offline_first_use "Erste Nutzung lädt das Modell in den Speicher (2–5 s)."
├─ ElevatedCard rec_card_language "Sprache & Kontext"
│    ExposedDropdownMenuBox rec_language "Sprache": Prefs.LANGUAGES (Automatisch erkennen · Deutsch · Englisch · Spanisch · Französisch · Italienisch)
│    OutlinedTextField pref_api_prompt_hint (bestehend), minLines 2, capitalization Sentences; supportingText pref_api_prompt_info (bestehend)
│    Bei Anbieter Mistral/OpenRouter (sttSendsPrompt=false): Label rec_context_words "Kontext-Wörter (kommagetrennt)", supportingText rec_context_words_info "Wird als Wortliste (context_bias) mitgeschickt."
│    [Stand 3.0.0: context_bias ist NICHT umgesetzt (Mistral-Doku: nur Englisch belastbar, OpenRouter kennt es nicht) — supportingText rec_context_unsupported "Dieser Anbieter nimmt keinen Kontext entgegen — …", Label bleibt pref_api_prompt_hint.]
└─ Datenschutz-Zeile bodySmall onSurfaceVariant, Leading ic_privacy_tip 16: rec_privacy_online "Audio wird zur Erkennung an %1$s gesendet." | rec_privacy_offline "Alles bleibt auf dem Gerät."
```

Fehler nur feldbezogen (`isError`) oder als Test-Chip. Leerzustand nur „Offline ohne Modell" (Karte oben).

### 2.5 E2 — Text

```
LargeTopAppBar text_title "Text"
Column(padding 20, gap 16)
├─ ElevatedCard text_card_refine "Textverbesserung (KI)"  — Leading-Icon der Karte ic_auto_fix_high tertiary
│    Column(selectableGroup) — 4 ListItem mit RadioButton (Trailing) und Supporting:
│      level_off        "Aus"            text_level_off_sub       "Nur die Regeln unten, keine zweite Anfrage."
│      level_smooth     "Glätten"        text_level_smooth_sub    "Zeichensetzung, Groß-/Kleinschreibung, Absätze. Inhalt unverändert."
│      level_beautify   "Verschönern"    text_level_beautify_sub  "Formuliert verständlicher, zieht Sätze zusammen, bewahrt den Inhalt."
│      level_summarize  "Zusammenfassen" text_level_summarize_sub "Kürzt auf die Kernaussagen."
│    → Prefs.refineLevel (off|smooth|beautify|summarize). Migration: llmPolish==true → smooth.
│    bodySmall onSurfaceVariant  text_level_cost "Zweite Anfrage · ca. 1–2 s länger · geringe Zusatzkosten"
│    ListItem + Switch  pref_smart_fillers (bestehend "Füllwörter intelligent entfernen") Supporting pref_smart_fillers_info (bestehend); enabled = level != off, sonst Supporting text_smart_needs_level "Braucht eine Stufe über „Aus"."
├─ ElevatedCard text_card_access "Zugang für die Textverbesserung"
│    ListItem + Switch text_own_access "Eigenen Zugang verwenden" → Prefs.llmUseOwn (Default AUS)
│      AUS: Supporting text_own_access_off "Nutzt Anbieter und Key der Erkennung (%1$s)."
│      AN : Supporting text_own_access_on  "Eigener Anbieter, Key und Modell."
│    AnimatedVisibility(AN): ExposedDropdownMenuBox text_llm_provider "Anbieter" (OpenAI · Groq · Mistral · OpenRouter · Anthropic · Google Gemini · DeepSeek · Eigener Server) · Base-URL (nur Eigener Server) · API-Key (Passwort, Auge, Einfügen) · Hinweis-Chip warning bei Gemini text_gemini_warning "Free-Tier: Google darf Inhalte zum Training nutzen." · bei DeepSeek text_deepseek_warning "Server in China — Datenschutz beachten."
│    ExposedDropdownMenuBox text_llm_model "Modell": LLM-Modelle des wirksamen Anbieters (§8.2) + text_model_custom "Eigenes Modell…" → B2.
│      Wirksamer Anbieter ohne LLM-Modelle (Together, DeepInfra, Eigener Server): nur Freitextfeld text_llm_model mit Placeholder "z. B. qwen3:8b".
│      engine == offline und Switch AUS: Karte zeigt statt Modell-Dropdown OutlinedCard warning: text_needs_online "Textverbesserung braucht einen Online-Zugang." + FilledTonalButton text_add_access "Eigenen Zugang eintragen" (schaltet Switch AN).
│    FilledTonalButton ic_verified text_test "Zugang prüfen" (Chat-Completion „Antworte mit OK") + Ergebnis-Chip wie E1.
└─ ElevatedCard text_card_rules "Regeln ohne KI"  (immer aktiv, lokal)
     ListItem + Switch pref_remove_fillers (bestehend "Füllwörter entfernen (ähm, äh …)") Supporting text_fillers_sub "Feste Wortliste je Sprache" · zweite Zeile TextButton text_fillers_edit "Liste bearbeiten" → B3 (enabled = Switch AN)
     ListItem + Switch pref_auto_cap (bestehend)
     ListItem + Switch pref_trailing_space (bestehend)
     bodySmall (nur wenn smartFillers aktiv): text_fillers_paused "Die Wortliste pausiert, solange die KI über Füllwörter entscheidet."
```

**B3 Füllwörter bearbeiten (`ModalBottomSheet`):** Titel titleLarge `fillers_title` „Füllwörter"; `SingleChoiceSegmentedButtonRow` Sprachen (Deutsch · Englisch · Spanisch · Französisch · Italienisch; vorausgewählt = Prefs.language, bei „auto" Deutsch); Abschnitt labelLarge `fillers_builtin` „Eingebaut": `FlowRow` aus `FilterChip` (Standardwörter aus `TextPolisher.FILLERS[lang]`, abwählbar → `Prefs.disabledFillers`); Abschnitt `fillers_custom` „Eigene Wörter": `FlowRow` aus `InputChip` (Trailing ic_close, cd `cd_remove_word`) + `OutlinedTextField` `fillers_add_hint` „Wort hinzufügen" mit Trailing IconButton ic_add (cd `cd_add_word`), `imeAction Done` fügt hinzu (→ `Prefs.customFillers`, klein geschrieben, ohne Duplikate); Leerzustand bodyMedium onSurfaceVariant `fillers_empty` „Noch keine eigenen Wörter"; bodySmall `fillers_hint` „Nur eindeutige Füllsilben — echte Wörter wie „halt" bleiben, sonst kaputte Sätze."; Row: TextButton `fillers_reset` „Standard wiederherstellen" · Button `common_done` „Fertig".

### 2.6 E3 — Knopf & Tastatur

```
LargeTopAppBar button_title "Knopf & Tastatur"
Column(padding 20, gap 16)
├─ ElevatedCard button_card_bubble "Schwebender Knopf"
│    ListItem Headline = home_state_running "Läuft" | home_state_stopped "Aus" Leading Status-Icon; Trailing Button 48 dp (home_hero_btn_start / home_hero_btn_stop, gleiche Logik wie H)
│    ListItem ic_restart_alt button_reset_pos "Position zurücksetzen" Supporting button_reset_pos_sub "Setzt den Knopf an die Startposition" → Prefs.floatX/Y = Default, Snackbar button_pos_reset_done "Position zurückgesetzt"
│    ListItem button_text_output "Textausgabe" Supporting button_text_output_a11y "Bedienungshilfe: fügt direkt ins Feld ein" | button_text_output_clip "Zwischenablage (Bedienungshilfe aus)"
├─ ElevatedCard button_card_permissions "Berechtigungen"  — je Zeile Leading Status-Icon, Headline, Trailing: erledigt = ic_check_circle success; offen = FilledTonalButton
│    perm_mic "Mikrofon"                       → Button perm_allow "Erlauben" (Runtime / App-Einstellungen wie Schritt 3)
│    perm_overlay "Über anderen Apps anzeigen" → Button perm_open "Öffnen" (Overlay-Intent)
│    perm_a11y "Bedienungshilfe"  Supporting perm_a11y_sub "Empfohlen — fügt Text direkt ein" → Button perm_open
│    perm_notif "Benachrichtigungen" (nur API ≥ 33) → Button perm_allow
├─ ElevatedCard button_card_keyboard "Diktier-Tastatur"
│    bodyMedium button_keyboard_intro "Alternative zum Knopf: WhisperLoom als Tastatur mit Halten-zum-Sprechen. Nützlich, wenn eine App kein Overlay erlaubt."
│    ListItem setup_s7_enable / setup_s7_select mit Status + Buttons (wie Schritt 7)
│    OutlinedTextField setup_try_hint minLines 3
└─ OutlinedCard button_card_howto "Kurzanleitung": 3 Zeilen help_dictate_1..3 + help_dictate_4 "Rot = Fehler, antippen = erneut"
```

### 2.7 E4 — Offline-Modelle

```
LargeTopAppBar models_title "Offline-Modelle"
Column(padding 20, gap 16)
├─ bodyMedium onSurfaceVariant  models_intro "Modelle werden einmalig von huggingface.co geladen und bleiben auf dem Gerät. Kein Modell ist in der App enthalten."
│  bodySmall models_storage "Belegt: %1$s · Frei: %2$s"
├─ SingleChoiceSegmentedButtonRow rec_engine_online | rec_engine_offline (gleicher Pref wie E1; Offline nur wählbar, wenn ≥ 1 Modell installiert, sonst Snackbar models_need_download "Zuerst ein Modell laden")
├─ MODELL-LISTE — je Modell ElevatedCard(padding 16) = ModelRow:
│    Row: RadioButton (Auswahl = Prefs.offlineModel; enabled nur wenn installiert) · Column(weight 1): titleMedium Label · bodyMedium onSurfaceVariant Details · Trailing je Zustand
│    Zustände Trailing:
│      NICHT GELADEN → FilledTonalButton ic_download models_load "Laden (%1$s)"           cd "{Label} herunterladen"
│      LÄDT          → IconButton ic_close cd models_cancel_cd "Download abbrechen"; unter der Row: LinearProgressIndicator(progress, 4 dp, primary) + labelSmall models_progress "%1$d %% · %2$s von %3$s · %4$s"  (Fortschritt unbekannt → indeterminate + models_progress_unknown "%1$s geladen")
│      GELADEN       → Icon ic_check_circle success 24 + IconButton ic_delete cd models_delete_cd "{Label} löschen" → D1
│      FEHLER        → Rahmen 1 dp error; labelSmall error models_failed "Fehlgeschlagen: %1$s" + FilledTonalButton common_retry "Erneut" (Range-Resume)
│      ZU GROSS      → Karte gedimmt (38 %), Chip warning models_too_big "Für dieses Gerät zu groß" (nur Large auf < 6 GB RAM)
│    Chip primaryContainer models_recommended "Empfohlen" bei Small.
│    Modelle (Reihenfolge fest; Details-Text = "{Größe} · {RAM} RAM · {Qualität}"):
│      tiny   models_tiny  "Tiny"            "32 MB · ~250 MB RAM · nur zum Ausprobieren"
│      base   models_base  "Base"            "60 MB · ~355 MB RAM · schnell, kurze Sätze"
│      small  models_small "Small"           "190 MB · ~430 MB RAM · gute Qualität für Deutsch"   ← Empfohlen, Default
│      large  models_large "Large v3 Turbo"  "574 MB · ~1 GB RAM · beste Qualität, langsam, ab 6 GB Gerätespeicher"
└─ LEERZUSTAND (nichts installiert, kein Download aktiv) über der Liste: Icon ic_download_for_offline 64 outline zentriert · bodyLarge models_empty_title "Noch kein Modell geladen" · bodyMedium onSurfaceVariant models_empty_body "Empfehlung: Small — gute Qualität für Deutsch bei 190 MB."
   Fußzeile bodySmall outline models_source "Quelle: huggingface.co/ggerganov/whisper.cpp"
```

Verhalten: Download läuft in einem Foreground-Service (`dataSync`) weiter, wenn der Screen verlassen wird; Notification zeigt Fortschritt; Abbruch löscht `.part`. Vor Start über gebührenpflichtiges Netz → **D2** (`AlertDialog`): Titel `models_metered_title` „Über mobile Daten laden?", Text `models_metered_body` „%1$s werden heruntergeladen. Im WLAN ist das kostenlos.", TextButton `common_cancel` „Abbrechen", Button `models_metered_ok` „Laden". Integrität: Größe + SHA-256 aus Katalog (`whisper-cpp.md §5`); Mismatch → FEHLER-Zeile mit `err_model_checksum` „Datei beschädigt — erneut laden".
**D1** (`AlertDialog`): Titel `models_delete_title` „%1$s löschen?", Text `models_delete_body` „%1$s werden frei. Für die Offline-Erkennung muss dann ein anderes Modell geladen werden."; ist es das aktive und einzige Modell: Zusatz `models_delete_active` „Dies ist das aktive Modell — Offline ist danach nicht einsatzbereit."; Buttons `common_cancel` · `common_delete` „Löschen" (Textfarbe error). Nach Löschen des einzigen Modells bleibt `engine=offline`; Home zeigt Banner `home_banner_model`.

Modell-Daten (Dateinamen, Bytes, SHA-256, URL-Schema `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<datei>`): `ggml-tiny-q5_1.bin` 32 152 673 · `ggml-base-q5_1.bin` 59 707 625 · `ggml-small-q5_1.bin` 190 085 487 · `ggml-large-v3-turbo-q5_0.bin` 574 041 195 — Hashes in `whisper-cpp.md §5`.

### 2.8 E5 — Hilfe & Anleitung

`LargeTopAppBar` `help_title` „Anleitung & Hilfe". `LazyColumn` mit aufklappbaren `ElevatedCard`-Abschnitten (Kopfzeile: Icon 24 primary · titleMedium · Trailing ic_expand_more, rotiert 180° bei offen; `AnimatedVisibility`); Abschnitt 1 initial offen; Deep-Link `section` öffnet einen bestimmten. Links als `ListItem` mit Trailing ic_open_in_new (cd `cd_open_link`), `ACTION_VIEW`; kein Browser (`ActivityNotFoundException`) → Snackbar `err_no_browser` „Kein Browser gefunden" mit Aktion `common_copy_link` „Link kopieren".

1. `help_s1_title` „So funktioniert's" (ic_touch_app) — drei `ListItem`: Knopf (`help_s1_bubble` „Schwebender Knopf: antippen = Aufnahme, nochmal = fertig & einfügen, auf ✕ ziehen = verwerfen, rot = Fehler, antippen = erneut."), Tastatur (`help_s1_keyboard` „Diktier-Tastatur: Mikrofon gedrückt halten, sprechen, loslassen."), Teilen (`help_s1_share` „Sprachnachrichten: in WhatsApp lange drücken → Teilen → WhisperLoom. Der Text erscheint in Absätzen, du kannst ihn kopieren oder weitergeben.").
2. `help_s2_title` „Einrichtung Schritt für Schritt" (ic_checklist) — 7 Zeilen mit Ziffern-Badge, je TextButton `common_open` „Öffnen" → W Schritt n.
3. `help_s3_title` „API-Key bekommen" (ic_key) — bodyMedium `help_s3_intro` „Ein API-Key ist ein persönlicher Zugangsschlüssel. Du bezahlst nur, was du nutzt — ein Diktat kostet meist unter einem Cent." Je Anbieter `ListItem`: Headline Name, Supporting Kurzschritte + Preisniveau (Texte `help_key_openai` … `help_key_deepseek`, §6), Trailing open_in_new → Key-URL (§8.2). Fußnote `help_prices_note` „Preise Stand 09/2026, ohne Gewähr."
4. `help_s4_title` „Eigener Server" (ic_dns) — `help_s4_body` „Jeder OpenAI-kompatible Server funktioniert: Endpunkte /v1/audio/transcriptions (Erkennung) und /v1/chat/completions (Textverbesserung). Unter Erkennung → Anbieter „Eigener Server" Base-URL eintragen, Key nur wenn der Server einen verlangt. Empfohlene Server: speaches, whisper.cpp-server, LocalAI; für Textverbesserung Ollama. http:// nur im eigenen Netz oder per VPN — sonst https."
5. `help_s5_title` „Offline-Modus" (ic_offline_bolt) — `help_s5_body` „Ein Offline-Modell erkennt Sprache direkt auf dem Gerät. Es wird einmalig geladen (32–574 MB) und liegt im App-Speicher; löschen kannst du es unter Offline-Modelle. Small ist für Deutsch die beste Balance. Die Erkennung dauert je Modell 2–10 Sekunden."
6. `help_s6_title` „Datenschutz" (ic_lock) — zwei Karten nebeneinander (auf schmalen Geräten untereinander): `help_s6_online` „Online: Audio und Kontext-Prompt gehen an den gewählten Anbieter. Bei Textverbesserung geht der erkannte Text an das Sprachmodell. Dein Key bleibt auf dem Gerät. WhisperLoom speichert keine Aufnahmen." · `help_s6_offline` „Offline: Nichts verlässt das Gerät — nur der Modell-Download geht ins Netz." · Zeile `help_s6_a11y` „Die Bedienungshilfe liest nichts mit und speichert nichts; sie fügt nur den diktierten Text ein."
7. `help_s7_title` „Wenn etwas nicht klappt" (ic_build) — Akkordeon-Einträge, jeder mit Button zum passenden Ziel: `help_p1` „Knopf erscheint nicht" (→ Schritt 4; Text: Overlay-Recht + Akku-Optimierung ausschalten), `help_p2` „Text landet nur in der Zwischenablage" (→ Schritt 5), `help_p3` „Key ungültig (401) / Limit (429)" (→ E1; Text: Key neu erzeugen, Guthaben prüfen), `help_p4` „Eingeschränkte Einstellung" (→ App-Info), `help_p5` „Offline zu langsam" (→ E4; kleineres Modell).
8. Fußzeile bodySmall outline: `home_version` „WhisperLoom %1$s" · `about_license` „whisper.cpp (MIT) · Jetpack Compose (Apache 2.0)" · Link `about_source` „Quellcode auf GitHub".

Zustände E5: statisch; kein Lade-/Leerzustand.

### 2.9 S — Transkription (Share-Ziel)

```
Scaffold (ShareTranscribeActivity, Compose, excludeFromRecents)
├─ TopAppBar title share_title "Transkription" · navigationIcon ic_close cd cd_close (beendet, setzt cancelled) · action IconButton ic_content_copy cd share_copy (nur FERTIG)
├─ Column(padding 20, gap 16)
│  ├─ KOPF-KARTE ElevatedCard(surfaceContainer): Row: 40-dp-Kreis primaryContainer mit ic_voicemail 24 onPrimaryContainer · Column: titleMedium Quelle (displayName bzw. share_unknown_source "Sprachnachricht") · bodyMedium onSurfaceVariant share_head_single "%1$s · 1 Datei" | share_head_multi "%1$d Dateien · %2$s gesamt"
│  ├─ FORTSCHRITT (nur LADEN, AnimatedVisibility): LinearProgressIndicator(6 dp, Round, primary) determinate = (fileIndex + chunk/chunks) / files; labelMedium onSurfaceVariant (Live-Region Polite):
│  │     share_progress "Datei %1$d von %2$d · Stück %3$d von %4$d · %5$s"  (bei 1 Datei/1 Stück nur der Statusanteil: share_decoding / share_sending / share_starting, bestehend)
│  ├─ TEXT-BEREICH LazyColumn(weight 1):
│  │     je Datei ein Abschnitt; Überschrift (nur bei > 1 Datei oder Fehlern): 16 dp Abstand + HorizontalDivider + titleSmall primary share_section "%1$s · %2$s" (Quelle, Dauer)
│  │     Absätze: Text bodyLarge 16 sp / Zeilenhöhe 24 sp, onSurface, Absatzabstand 12 dp, in SelectionContainer
│  │       Absatzregel (verbindlich): (1) vorhandene "\n\n" übernehmen; sonst (2) neuer Absatz an jeder Stück-Grenze (AudioChunks); (3) innerhalb eines Stücks nach jeweils 3 Sätzen (Satzende = . ! ? gefolgt von Leerzeichen) oder sobald ≥ 350 Zeichen erreicht sind.
│  │     Zwischenergebnis: fertige Dateien sofort (bestehend partial=true); laufende Datei darunter Skeleton = 3 Box-Zeilen surfaceContainerHigh, 14 dp hoch, Radius 7, Breite 100/92/60 %, Alpha-Puls 0,6→1,0 1200 ms
│  │     Leer erkannt: bodyMedium outline kursiv share_nothing_recognised (bestehend "(nichts erkannt)")
│  │     Fehler je Datei: OutlinedCard(Rahmen error) Row: ic_error 24 error · bodyMedium share_one_failed (bestehend "Fehlgeschlagen: %1$s") · TextButton share_retry_file "Erneut" (nur diese Datei)
│  ├─ UMSCHALTER-LEISTE Surface(surfaceContainerLow, radius 16, 56 dp, nur FERTIG): Row: Column(weight 1): bodyMedium share_hide_fillers "Füllwörter ausblenden" · labelSmall onSurfaceVariant (AN share_hide_fillers_on "ähm, äh … ausgeblendet" | AUS share_hide_fillers_off "Wortgetreu, 100 %") · Switch (Default AN; cd = Label)
│  │     Wirkt sofort auf Anzeige und auf Kopieren/Teilen; lokal via TextPolisher (Sprache = Prefs.language), kein neuer Upload; Rohtext bleibt im State.
└─ AKTIONSLEISTE Surface(surfaceContainer, padding 20/16 + Nav-Inset, nur FERTIG oder FEHLER):
     Row(gap 12): FilledTonalButton ic_content_copy share_copy "Kopieren" (weight 1) · Button ic_share share_forward "Teilen" (weight 1) · [IconButton ic_refresh cd share_retry_all "Alles erneut" nur wenn ≥ 1 Fehler]
     Kopieren → Snackbar share_copied (bestehend). Teilen → ACTION_SEND text/plain (plainText() ohne Überschriften bei 1 Datei; mit Überschriften + Leerzeile bei mehreren).
```

Globale Zustände (zentrierte Karte, Icon 48 dp, titleMedium, bodyMedium, Buttons 56 dp):
- **KEINE DATEI** (kein Audio-URI): ic_music_off outline · `share_no_audio` (bestehend) · `share_no_audio_body` „Teile eine Audiodatei oder Sprachnachricht mit WhisperLoom." · Button `common_close`.
- **KEIN ZUGANG** (`ApiNotConfiguredException` / `recognitionReady == false`): ic_key warning · `share_not_configured` „Kein Zugang eingerichtet" · `share_not_configured_body` „Richte einen Online-Dienst oder ein Offline-Modell ein, dann klappt es." · Button `share_open_setup` „Einrichtung öffnen" (→ MainActivity route=setup) · TextButton `common_close`.
- **FEHLER GESAMT** (alle Dateien fehlgeschlagen): ic_error error · `share_all_failed` „Transkription fehlgeschlagen" · Grund (`share_one_failed`) · Button `common_retry` · TextButton `common_close`.
- **LADEN**: Kopf + Fortschritt + Skeleton; keine Aktionsleiste. Abbruch bei Schließen (bestehendes `cancelled`).
- Zwischenablage wird nie automatisch überschrieben (bestehend).

### 2.10 Sheets B1, B2

- **B1 „Wo bekomme ich einen Key?"** (`ModalBottomSheet`): Titel titleLarge `key_sheet_title`; `ListItem` je Anbieter mit LLM- oder STT-Bezug (Kontext: aus E1 nur STT-Anbieter, aus E2 nur LLM-Anbieter): Headline Name, Supporting Kurzschritte (`help_key_*`), Trailing ic_open_in_new → Key-URL. Unten `common_close`.
- **B2 „Eigenes Modell"**: Titel `model_custom_title` „Eigenes Modell"; `OutlinedTextField` `model_custom_hint` „Modell-ID" (Placeholder je Kontext: `whisper-1` bzw. `qwen3:8b`), supportingText `model_custom_info` „Genau so, wie der Anbieter die ID nennt."; Row: TextButton `common_cancel` · Button `common_apply` „Übernehmen" (enabled = nicht leer). Übernahme schreibt `apiModel` bzw. `llmModel`; Dropdown zeigt danach die freie ID als aktuellen Wert.

---

## 3. Farb-Tokens, Typografie, Ikonografie

### 3.1 Farbschema (fest dunkel; `res/values/colors.xml` mit Präfix `loom_`, Compose `darkColorScheme` + `LocalLoomColors`)

Akzent **Aurora-Türkis**, Aufnahme-Rot als eigener Token, Tertiär Lavendel für KI-/Textverbesserungs-Elemente, Sekundär kühles Blau für Info/Optional.

| M3-Rolle | Hex | Verwendung |
|---|---|---|
| `background` / `surface` | `#0E1116` | Fenster, Scaffold, IME-Grund |
| `surfaceContainerLowest` | `#090B0F` | Textfeld-Grund in Sheets |
| `surfaceContainerLow` | `#161A20` | Umschalter-Leiste (S), Bubble-Label |
| `surfaceContainer` | `#1A1F26` | ElevatedCard, Sheets, Bottom-Bars, Bubble IDLE |
| `surfaceContainerHigh` | `#242A32` | Chips, IME-Tasten, Skeleton, Icon-Kreise im Hub |
| `surfaceContainerHighest` | `#2E353E` | Progress-Track, Taste gedrückt, Timer-Pille |
| `surfaceBright` | `#343B45` | Drag-Hervorhebung, Menü-Grund |
| `onSurface` | `#E4E8EE` | Primärtext |
| `onSurfaceVariant` | `#B8C0CC` | Sekundärtext, inaktive Icons |
| `outline` | `#8A93A0` | Feldrahmen aktiv, Platzhalter, Fußzeilen |
| `outlineVariant` | `#3A424D` | Divider, Karten-Rahmen (dekorativ) |
| `primary` | `#6FD9C7` | Hauptbutton, Fortschritt, Auswahl-Rahmen, Mikro bereit, Switch an |
| `onPrimary` | `#00382F` | Text/Icon auf primary |
| `primaryContainer` | `#0F5B50` | Hero-Icon-Kreise, Bubble SENDING, App-Icon-Grund |
| `onPrimaryContainer` | `#B4F2E6` | darauf |
| `secondary` | `#A9C4E8` | neutrale Status-Icons |
| `onSecondary` | `#12304F` | — |
| `secondaryContainer` | `#2B4665` | Info-Karten, Chip „Optional", Segmented aktiv |
| `onSecondaryContainer` | `#D8E7FA` | darauf |
| `tertiary` | `#D7B8FF` | KI/Textverbesserung, Share-Kopf-Icon |
| `onTertiary` | `#3C1D6A` | — |
| `tertiaryContainer` | `#54368A` | Chip „KI aktiv" |
| `onTertiaryContainer` | `#EEDCFF` | darauf |
| `error` | `#FFB4AB` | Fehlertext, Fehler-Rahmen, Bubble ERROR-Ring |
| `onError` | `#690005` | — |
| `errorContainer` | `#93000A` | Bubble ERROR, Fehlerkarten, Cancel-Ziel „hover" |
| `onErrorContainer` | `#FFDAD6` | darauf |

**Zusatz-Tokens** (`LocalLoomColors` + `colors.xml`):

| Token | Hex | Verwendung |
|---|---|---|
| `recording` | `#FF4D4D` | Aufnahme-Rot: Bubble/IME RECORDING-Füllung, Puls-Ring, Pegel, Cancel-Ring |
| `onRecording` | `#1A0508` | Stop-Icon auf recording |
| `recordingContainer` | `#4A0F12` | Hero-Icon-Kreis im Laufzustand, Beenden-Button |
| `recordingText` | `#FF8A80` | Timer-Text, Beenden-Button-Text, IME-Status bei Aufnahme |
| `success` | `#6FDD8B` | Erledigt-Icons, Erfolg-Chip-Icon |
| `onSuccess` | `#00391A` | — |
| `successContainer` / `onSuccessContainer` | `#123D24` / `#B4F5C4` | Chip „Erledigt", Fertig-Hero |
| `warning` | `#FFC85C` | Warn-Icons, „Fehlt noch" |
| `onWarning` | `#3D2C00` | — |
| `warningContainer` / `onWarningContainer` | `#4A3600` / `#FFE3A8` | ReadinessBanner, Warn-Chips |

Disabled: `onSurface` 38 % Alpha (M3-Standard) — nie als einziger Informationsträger; disabled Buttons bekommen einen Erklär-Chip.

**Kontrast (WCAG 2.x, berechnet mit `research/contrast_final.py` am 2026-09-06):**

| Vordergrund | Hintergrund | Ratio | Bewertung |
|---|---|---|---|
| `onSurface` #E4E8EE | `surface` #0E1116 | 15,38:1 | AA |
| `onSurface` | `surfaceContainer` #1A1F26 | 13,47:1 | AA |
| `onSurface` | `surfaceContainerHigh` #242A32 | 11,76:1 | AA (IME-Tasten) |
| `onSurface` | `surfaceContainerHighest` #2E353E | 10,07:1 | AA |
| `onSurfaceVariant` #B8C0CC | `surface` | 10,31:1 | AA |
| `onSurfaceVariant` | `surfaceContainer` | 9,03:1 | AA |
| `onSurfaceVariant` | `surfaceContainerHighest` | 6,76:1 | AA |
| `outline` #8A93A0 | `surface` | 6,09:1 | AA (Fußzeilen, Platzhalter) |
| `outlineVariant` #3A424D | `surface` | 1,86:1 | nur dekorativ (Divider/Rahmen), nie Text |
| `primary` #6FD9C7 | `surface` | 11,19:1 | AA |
| `primary` | `surfaceContainerHighest` | 7,33:1 | AA |
| `onPrimary` #00382F | `primary` | 7,73:1 | AA |
| `onPrimaryContainer` #B4F2E6 | `primaryContainer` #0F5B50 | 6,38:1 | AA |
| `primary` | `primaryContainer` | 4,72:1 | AA (Sendet-Ring) |
| `secondary` #A9C4E8 | `surface` | 10,58:1 | AA |
| `onSecondaryContainer` #D8E7FA | `secondaryContainer` #2B4665 | 7,72:1 | AA |
| `tertiary` #D7B8FF | `surface` | 10,98:1 | AA |
| `onTertiaryContainer` #EEDCFF | `tertiaryContainer` #54368A | 7,18:1 | AA |
| `error` #FFB4AB | `surface` / `surfaceContainer` | 11,14 / 9,76:1 | AA |
| `onErrorContainer` #FFDAD6 | `errorContainer` #93000A | 7,24:1 | AA |
| `recording` #FF4D4D | `surface` / `surfaceContainer` | 5,78 / 5,06:1 | AA (Ring/Pegel) |
| `onRecording` #1A0508 | `recording` | 6,01:1 | AA (Stop-Icon) |
| Weiß #FFFFFF | `recording` | 3,27:1 | nur Grafik — **nicht** verwenden für Icon/Text auf Rot |
| `recordingText` #FF8A80 | `surface` / `surfaceContainerHighest` / `recordingContainer` | 8,28 / 5,43 / 6,72:1 | AA (Timer, Beenden-Button) |
| `success` #6FDD8B | `surface` / `surfaceContainer` | 11,16 / 9,77:1 | AA |
| `onSuccessContainer` #B4F5C4 | `successContainer` #123D24 | 9,77:1 | AA |
| `warning` #FFC85C | `surface` / `surfaceContainer` | 12,31 / 10,78:1 | AA |
| `onWarningContainer` #FFE3A8 | `warningContainer` #4A3600 | 9,24:1 | AA |
| `onPrimaryContainer` | App-Icon-Grund #0F5B50 | 6,38:1 | AA (Icon-Motiv) |

Regel: **jede Text-Paarung ≥ 4,5:1, jede Grafik-/Rahmen-Paarung ≥ 3:1**; Ausnahmen nur `outlineVariant` (dekorativ).

### 3.2 Typografie (M3 Type Scale, Systemschrift Roboto, kein eingebetteter Font; `Typography()`-Default)

| Token | Größe/Zeile | Einsatz |
|---|---|---|
| `headlineMedium` | 28/36 | W1-Titel, W9-Titel, LargeTopAppBar (ausgeklappt) |
| `headlineSmall` | 24/32 | Schritt-Titel im Assistenten |
| `titleLarge` | 22/28 | TopAppBar-Titel (Home, S), Sheet-Titel, Hero-Titel |
| `titleMedium` | 16/24 Medium | ListItem-Headlines, Karten-Titel, Modell-Namen |
| `titleSmall` | 14/20 Medium | Sektions-Überschriften („Status"), Share-Abschnitte |
| `bodyLarge` | 16/24 | Erklärtexte im Assistenten, Transkript-Absätze |
| `bodyMedium` | 14/20 | Supporting-Texte, Karten-Body |
| `bodySmall` | 12/16 | Fußnoten, Datenschutz-Zeilen, Kosten-Hinweise |
| `labelLarge` | 14/20 Medium | Buttons, Chips, Segmented (Hero-Button 16 sp) |
| `labelMedium` | 12/16 Medium | „Schritt 3 von 7", Fortschrittstexte, Bubble-Label |
| `labelSmall` | 11/16 Medium | Download-Prozent, Cancel-Ziel-Label, IME-Status |

Mindestgröße 11 sp (nur labelSmall), sonst ≥ 12 sp. `fontScale` wird respektiert; keine fixen Höhen bei Textcontainern (`wrapContentHeight`, Mindesthöhen). Timer mit `FontFeatureSettings("tnum")`.

### 3.3 Ikonografie — eigene VectorDrawables (Material Symbols Rounded, Weight 400, Fill 0, 24 dp; Quelle https://fonts.google.com/icons, Apache 2.0)

Dateiname `res/drawable/ic_<symbolname>.xml`, `android:tint`-frei (Tint per Compose `tint`/View `imageTintList`). Vollständige Liste (48 Icons):

`mic` · `mic_off` · `stop` · `play_arrow` · `refresh` · `replay` · `close` · `check` · `check_circle` · `radio_button_unchecked` · `remove_circle_outline` · `warning` · `error` · `info` · `help` · `settings` · `arrow_back` · `arrow_forward` · `chevron_right` · `expand_more` · `checklist` · `graphic_eq` · `cloud` · `offline_bolt` · `download` · `download_for_offline` · `delete` · `key` · `visibility` · `visibility_off` · `content_paste` · `content_copy` · `share` · `verified` · `open_in_new` · `privacy_tip` · `lock` · `schedule` · `layers` · `accessibility_new` · `notifications` · `keyboard` · `keyboard_return` · `backspace` · `language` · `touch_app` · `auto_fix_high` · `restart_alt` · `voicemail` · `music_off` · `add` · `dns` · `build`

Größen: Standard 24 dp; Wizard-Hero 36 dp; Leerzustand 48/64 dp; Bubble 28 dp; IME-Mikro 36 dp; IME-Tasten 22 dp. Jedes Icon-Only-Element hat `contentDescription` (Keys `cd_*`), dekorative Icons `null`. Emoji auf Tasten (🌐 ⌫ ⏎ ↻ ⚙ ✕) entfallen.

---

## 4. App-Icon, Share-Icon, Notification-Icon

Vorgaben: adaptive Ebenen 108 × 108 dp, sichtbarer Kern 66 × 66 dp (Kreis Ø 66 um (54,54), Radius 33), 18 dp Maskenreserve; Monochrom-Ebene für Themed Icons (API 33+). Quelle: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive. Alle Koordinaten im 108er-Viewport (dp). Keine Schatten (flach), Parallax kommt vom Launcher.

### 4.1 Launcher-Icon `mipmap-anydpi-v26/ic_launcher.xml` (+ `ic_launcher_round.xml` = gleiche Referenz)

**Hintergrund `drawable/ic_launcher_background.xml`:** Vollfläche `M0,0 H108 V108 H0 Z` Fill `#0F5B50`; darüber radialer Verlauf (Kreis Mitte (54,48), Radius 64) `#177066` (Mitte) → `#0F5B50` (Rand). Keine weiteren Formen.

**Vordergrund `drawable/ic_launcher_foreground.xml`** (Motiv ≈ 60 × 42 dp, x 26,8–81,2 · y 34–76, alle Punkte ≤ 32 dp vom Zentrum):
1. **Mikrofon-Kapsel** — abgerundetes Rechteck x 45–63 (Breite 18), y 34–64 (Höhe 30), Radius 9: `M54,34 a9,9 0 0 1 9,9 v12 a9,9 0 0 1 -18,0 v-12 a9,9 0 0 1 9,-9 z`, Fill `#B4F2E6`.
2. **Grill** — drei Schlitze 8 × 2, Radius 1, x 50–58, Mittellinien y = 44 / 49 / 54, Fill `#0F5B50` bei Alpha 0,85.
3. **Bügel** — Bogen um (54,54), Radius 15, von (39,54) unten herum nach (69,54): `M39,54 a15,15 0 0 0 30,0`; Stroke 4, `strokeLineCap round`, kein Fill, Farbe `#B4F2E6`.
4. **Stiel + Fuß** — `M54,69 v7` und `M46,76 h16`; Stroke 4 round, `#B4F2E6`.
5. **Schallwellen** — je Seite zwei konzentrische Bögen um (54,50), Öffnungswinkel ±25° um die Horizontale, Stroke 3,5 round, Farbe `#6FD9C7` (innen Alpha 1,0; außen Alpha 0,6):
   - rechts innen R 23: `M74.8,40.3 a23,23 0 0 1 0,19.4`
   - rechts außen R 30: `M81.2,37.3 a30,30 0 0 1 0,25.4`
   - links innen R 23: `M33.2,40.3 a23,23 0 0 0 0,19.4`
   - links außen R 30: `M26.8,37.3 a30,30 0 0 0 0,25.4`
   Prüfung Safe-Zone: entferntester Punkt (81.2, 37.3) → Abstand zu (54,54) = 31,9 dp < 33 ✓; Fuß (46,76) → 23,4 ✓.

**Monochrom `drawable/ic_launcher_monochrome.xml`:** Pfade 1, 3, 4, 5 in **einer** Farbe `#FFFFFF` (System tönt), ohne Alpha (äußere Wellen voll), ohne Grill; Strichstärken +0,5 (Bügel/Stiel/Fuß 4,5; Wellen 4). XML: `<adaptive-icon>` mit `<background>`, `<foreground>`, `<monochrome>`; Legacy-PNG nicht nötig (minSdk 26). Play-Store-512-px-PNG bei Bedarf aus demselben Vektor rendern.

### 4.2 Share-Ziel-Icon `mipmap-anydpi-v26/ic_share.xml` (Manifest `android:icon` + `android:label="@string/share_label"` an `ShareTranscribeActivity`)

Gleicher Hintergrund wie 4.1. Vordergrund `drawable/ic_share_foreground.xml`: Mikrofon (Pfade 1–4) um **8 dp nach links** verschoben (Kapsel x 37–55, Bügel um (46,54), Stiel x 46, Fuß x 38–54), **keine** Wellen; rechts drei **Textzeilen** als abgerundete Balken Höhe 4, Radius 2, Fill `#6FD9C7`: x 62–82 (y 42–46), x 62–78 (y 50–54), x 62–72 (y 58–62). Lesart „Sprache → Text". Safe-Zone: (82,42) → 30,5 ✓. Monochrom analog (alles `#FFFFFF`).

### 4.3 Notification-Small-Icon `drawable/ic_stat_whisperloom.xml` (24 × 24, reine Alpha-Silhouette, Weiß auf transparent; Quelle https://documentation.onesignal.com/docs/en/notification-icons)

- Kapsel: `M12,3 a2,2 0 0 1 2,2 v5 a2,2 0 0 1 -4,0 V5 a2,2 0 0 1 2,-2 z` Fill `#FFFFFF` (x 10–14, y 3–12).
- Bügel: `M7.5,10 a4.5,4.5 0 0 0 9,0` Stroke 1,75 round.
- Stiel/Fuß: `M12,14.5 v3.5` und `M9.5,18 h5`, Stroke 1,75 round.
- Wellen R 7,5 um (12,10), ±22°: rechts `M18.95,7.2 a7.5,7.5 0 0 1 0,5.6`, links `M5.05,7.2 a7.5,7.5 0 0 0 0,5.6`, Stroke 1,5 round.
- Sicherheitsrand 2 dp eingehalten (x 5,05–18,95; y 3–18). `Notification.Builder.setColor(loom_primary)`; während RECORDING `setColor(loom_recording)`.

### 4.4 Weitere Vektoren

`ic_mic` (24 dp, für Bubble/IME/Hero; Material-Symbol) · `ic_stop` · `ic_replay` · `ic_close` (Cancel-Ziel). Splash: keine Lib; Manifest-Theme mit `windowBackground = @color/loom_background` (Android 12+ zeigt System-Splash mit dem adaptiven Icon).

---

## 5. Schwebender Knopf (V1), IME-Tastatur (V2), Notification (N1), Motion, Barrierefreiheit

### 5.1 Schwebender Knopf — Geometrie (`layout/floating_mic.xml`, `FloatingMicService`, `BubbleUi`)

- Container `FrameLayout` 96 × 96 dp (Platz für Puls-Ring 1,35 × 68 = 92 dp), Knopf-Kreis **68 dp** zentriert (bisher 56), Elevation 8 dp, `outlineSpotShadowColor` Schwarz 40 %.
- Ebenen (hinten → vorn): `pulse_ring` (View 84 dp oval, Stroke 4, nur RECORDING) · `state_ring` (View 72 dp oval, Stroke 2) · `bubble` (68 dp oval, `bubble_bg.xml` mit `@color/loom_*`) · `bubble_progress` (`ProgressBar` indeterminate 76 dp, Stroke 3, `indeterminateTint loom_primary`, nur SENDING) · `bubble_icon` (`ImageView` 28 dp) · darunter (4 dp Abstand) `bubble_label` (`TextView` in Pille: Höhe 22, Radius 11, Padding 10/0, labelMedium 12 sp, Grund `loom_surfaceContainerLow` 92 % Alpha).
- Touch-Ziel = ganzer 68-dp-Kreis. Drag/Tap-Unterscheidung, Positionsspeicherung (`floatX/floatY`), Clamp an Bildschirmrand: bestehend; **kein** Kanten-Snap (Position bleibt, wo losgelassen).

**Vier Zustände (`BubbleState`):**

| Zustand | Füllung | Ring | Icon (28 dp) | Label | Tipp / Ziehen |
|---|---|---|---|---|---|
| **IDLE** — bereit | `surfaceContainer` #1A1F26 @ 96 % | 2 dp `primary` | `ic_mic` in `primary` | keins | Tipp = Aufnahme starten · Ziehen = verschieben (Cancel-Ziel bleibt aus) |
| **RECORDING** — nimmt auf | `recording` #FF4D4D | Puls-Ring `recording` (§5.4) + statischer 2 dp Ring `recordingText` bei Reduce-Motion | `ic_stop` in `onRecording` #1A0508 | „● 0:07": Punkt 6 dp `recording` (blinkt 1 Hz), Text `recordingText`, 1×/s (`BubbleUi.formatDuration`, tnum) | Tipp = beenden + senden · Ziehen zeigt Cancel-Ziel |
| **SENDING** — sendet | `primaryContainer` #0F5B50 | rotierender 3-dp-Bogen `primary` (270°, 1 U/s) statt statischem Ring | `ic_mic` in `onPrimaryContainer` @ 50 % | `float_sending` „sendet …" `onSurfaceVariant` | Tipp ignoriert · Ziehen erlaubt; **keine** Gesamt-Alpha-Reduktion mehr |
| **ERROR** — fehlgeschlagen | `errorContainer` #93000A | 2 dp `error` | `ic_replay` in `onErrorContainer` #FFDAD6 | `float_retry_hint` „tippen = erneut", Pille `errorContainer`/`onErrorContainer` | Tipp = erneut senden (Audio bleibt gepuffert) · Ziehen aufs ✕ = verwerfen |
| *IDLE-Hinweis „kopiert"* (nur ohne Bedienungshilfe, 2 s nach SENDING) | wie IDLE | wie IDLE | `ic_content_copy` in `primary` | `float_copied_short` „Kopiert — einfügen" | wie IDLE |

`contentDescription` je Zustand (`View.setContentDescription` in `applyState`, plus `announceForAccessibility` beim Wechsel): `cd_bubble_idle` „Diktat starten" · `cd_bubble_recording` „Aufnahme läuft, %1$s — antippen zum Beenden" · `cd_bubble_sending` „Wird übertragen" · `cd_bubble_error` „Fehler — antippen für erneuten Versuch". Der Knopf ist ein einzelnes fokussierbares Element; Label `importantForAccessibility = no`.

### 5.2 Abbrechen-Ziel (`layout/floating_cancel.xml`)

- Kreis **72 dp** (bisher 64), unten mittig, Unterkante 96 dp über dem Bildschirmrand (bestehend), Füllung `surfaceContainerHigh` @ 92 %, Ring 2 dp `outline`, Icon `ic_close` 28 dp `onSurface`; darunter (6 dp) labelSmall `onSurfaceVariant` `float_cancel_label` „Verwerfen". Scrim: vertikaler Verlauf `#00000000` → `#66000000` über dem unteren Bildschirmviertel während des Drags.
- Erscheint nur beim Ziehen in RECORDING/ERROR (`fadeIn + scaleIn(0.8)` 200 ms). Treffer-Radius (Magnet) 56 dp (`BubblePosition.isOverCancel`): Ziel skaliert 1,12, Füllung `errorContainer`, Icon `onErrorContainer`, Label `float_cancel_release` „Loslassen zum Verwerfen"; Haptik `CONFIRM` beim Eintritt, `REJECT` beim Loslassen darüber. Loslassen → Toast `float_discarded` (bestehend), Knopf springt an gemerkte Position zurück, Zustand IDLE. Ziel `contentDescription` `cd_cancel` (bestehend „Diktat verwerfen").

### 5.3 IME-Tastatur (`layout/keyboard_view.xml`, `WhisperLoomInputMethodService`)

```
LinearLayout vertical, background @color/loom_background, oben 1 dp @color/loom_outlineVariant, paddingH 12, paddingTop 8, paddingBottom 12 (+ Nav-Inset); Gesamthöhe ≈ 240 dp
├─ Statuszeile   TextView labelMedium 13 sp, zentriert, Höhe 24, Farbe je Zustand:
│                kb_hint_hold (onSurfaceVariant) · kb_listening (recordingText) · kb_transcribing (onSurfaceVariant) · kb_error (error) ·
│                kb_need_permission (warning; Tipp → MainActivity route=setup step=3) · kb_not_configured "Kein Zugang eingerichtet — tippe zum Einrichten" (warning; Tipp → route=setup)
│                accessibilityLiveRegion = polite
├─ Pegelband     LevelBandView (custom View) Höhe 28, marginH 40: 21 Balken, 3 dp breit, 4 dp Gap, Radius 1,5, Höhe 4–24 dp aus RMS
│                (Attack 50 ms / Release 250 ms); Farbe loom_recording bei Aufnahme, sonst loom_surfaceContainerHighest flach; importantForAccessibility = no
├─ Mikro-Zone    FrameLayout 112 dp: Puls-Ring (View 104 dp oval, nur Aufnahme) + ImageButton 88 dp oval (mic_button_bg.xml, Level-Drawable):
│                IDLE      loom_surfaceContainerHigh + 2 dp Ring loom_primary, ic_mic 36 dp loom_primary
│                RECORDING loom_recording, ic_stop 36 dp loom_onRecording, Puls
│                SENDING   loom_primaryContainer + ProgressBar-Bogen loom_primary, ic_mic loom_onPrimaryContainer @ 50 %
│                ERROR     loom_errorContainer + 2 dp Ring loom_error, ic_replay loom_onErrorContainer
│                contentDescription cd_mic (bestehend); Halten-zum-Sprechen bleibt (ACTION_DOWN/UP)
└─ Tastenreihe   LinearLayout 52 dp, marginTop 8, Abstand 4 dp; Tasten: Höhe 48, minWidth 44, Radius 12, Füllung loom_surfaceContainerHigh, gedrückt loom_surfaceContainerHighest,
                 Ripple loom_onSurface @ 12 %; Icons 22 dp loom_onSurface, Text 18 sp loom_onSurface
                 [ic_language cd_kb_switch] [ "," cd_kb_comma ] [ Leertaste weight 1, Label kb_space "Leer" 15 sp onSurfaceVariant, cd_kb_space ] [ "." cd_kb_period ] [ic_backspace cd_kb_backspace] [ic_keyboard_return cd_kb_enter]
                 [ic_replay cd_kb_retry, Farbe loom_error — nur ERROR sichtbar] [ic_settings cd_kb_settings → MainActivity route=settings]
```

Subtypes de/en (`method.xml`) bleiben. Tastenhöhe wächst ab `fontScale ≥ 1,3` auf 56 dp. Alle Drawables referenzieren ausschließlich `@color/loom_*`.

### 5.4 Motion & Feedback (dezent, alle Werte verbindlich)

| Ereignis | Verhalten |
|---|---|
| Aufnahme-Puls (Bubble, IME, Home-Hero-Kreis) | äußerer Ring `recording`, Alpha 0,45 → 0, Skalierung 1,0 → 1,35, 1200 ms, `LinearOutSlowIn`, `Restart`; Pegel moduliert Startradius bis +15 %. Reduce-Motion (`ANIMATOR_DURATION_SCALE == 0` bzw. `LocalAccessibilityManager`): statischer 2-dp-Ring |
| IDLE → RECORDING | Farb-Crossfade 200 ms, Icon-Crossfade `mic`→`stop`; Haptik `CONFIRM` (API 30+, sonst `KEYBOARD_TAP`) |
| RECORDING → SENDING | Ring → rotierender Bogen; Icon fadet auf 50 % (150 ms); Haptik `CONTEXT_CLICK` |
| SENDING → IDLE (Erfolg) | 300 ms `success`-Ring 2 dp, dann IDLE |
| → ERROR | Shake ±6 dp, 3 Halbschwingungen, 300 ms; Haptik `REJECT` (API 30+, sonst `LONG_PRESS`); Label fade-in |
| Hero-Button (H) | `animateColorAsState` 250 ms primary ⇄ recordingContainer, Icon-Crossfade play ⇄ stop |
| Status-Chip offen → erledigt | Crossfade 250 ms, `check` skaliert 0,6 → 1,0 |
| W9 Erfolgs-Kreis | Spring `dampingRatio 0.6`, 0,8 → 1,0, einmalig |
| Schrittwechsel W | `AnimatedContent`: `slideInHorizontally(+30 %) + fadeIn` / `slideOutHorizontally(−30 %) + fadeOut`, 300 ms, rückwärts gespiegelt |
| Detail-Screens (H → E → E1…) | Fade-through: `fadeIn(220) + scaleIn(0.92)`; Sheets/Dialoge M3-Standard |
| Fortschritt | `animateFloatAsState` 300–400 ms, max. 1 Update/100 ms |
| Skeleton (S) | Alpha-Puls 0,6 → 1,0, 1200 ms |
| Ton | keiner |

Keine Bounces außer dem Shake; keine Animation > 500 ms außer Endlosschleifen.

### 5.5 Notification (N1)

`setSmallIcon(ic_stat_whisperloom)`, `setColor(loom_primary)` (RECORDING: `loom_recording`), Titel `float_running`, Text `float_running_text`, Aktion `float_stop` „Beenden" (`ic_stop`), `setOngoing(true)`, Kanal `float_channel` `IMPORTANCE_LOW`, **`contentIntent` → `MainActivity(route=home)`** (neu — bisher totes Ende).

### 5.6 Barrierefreiheit (verbindlich, Compose und View)

- Status nie nur über Farbe: Pill/Badge/Chip = Farbe + Icon + Text; TalkBack liest Status als `stateDescription` der Zeile, nicht als separates Element.
- Zeilen mit Aktion: `Modifier.semantics(mergeDescendants = true)`, Button mit `onClickLabel` („Mikrofon erlauben"). Dekorative Icons `contentDescription = null`.
- Live-Regions: Fortschrittstexte (S, E4, Schritt 2b), Test-Ergebnis-Chips, Hero-Hinweis-Chip → `Polite`; Fehlerkarten → `Assertive`.
- Passwortfeld: Auge-Icon `cd_key_show`/`cd_key_hide`, Einfügen `cd_paste`; Key wird nie im Klartext vorgelesen.
- Touch-Ziele ≥ 48 dp überall; IME-Tasten 48 dp; Bubble 68 dp; Cancel-Ziel 72 dp. `Modifier.minimumInteractiveComponentSize()` an IconButtons.
- Schriftskalierung: alle Größen `sp`, keine fixen Texthöhen; Karten wachsen mit.
- Fokus-Indikator (Tastatur/D-Pad): 2 dp `primary` Outline auf allen klickbaren Karten/Zeilen.
- Reduce-Motion respektiert (§5.4).

---

## 6. Strings (`res/values/strings.xml`, Deutsch) — vollständig

Regeln: bestehende Keys bleiben gültig (mit „(bestehend)" markiert; Text ggf. aktualisiert); Platzhalter `%1$s`/`%1$d` wie angegeben; Anführungszeichen als `„…"`; Ellipsen als `…`. Keys ohne Text-Änderung, die entfallen: `setup_step_api/mic/enable/select`, `setup_api_btn`, `setup_open_settings`, `setup_pick_ime`, `setup_try`, `setup_open_prefs`, `status_done`, `status_open`, `setup_float_*`, `setup_overlay_btn`, `setup_a11y_btn`, `setup_start_bubble`, `setup_stop_bubble`, `setup_notif_btn`, `pref_api_title`, `pref_api_url_hint`, `pref_api_key_hint`, `pref_api_model_hint`, `pref_api_examples`, `pref_llm_polish`, `pref_llm_polish_info`, `pref_llm_model_hint`, `api_not_configured`, `settings_title` (Text geändert).

### 6.1 Allgemein / gemeinsam

| Key | Text |
|---|---|
| `app_name` (bestehend) | WhisperLoom |
| `ime_label` (bestehend) | WhisperLoom Diktat |
| `subtype_de` / `subtype_en` (bestehend) | Deutsch / Englisch |
| `common_next` | Weiter |
| `common_back` | Zurück |
| `common_close` | Schließen |
| `common_cancel` | Abbrechen |
| `common_done` | Fertig |
| `common_apply` | Übernehmen |
| `common_open` | Öffnen |
| `common_change` | Ändern |
| `common_delete` | Löschen |
| `common_retry` | Erneut |
| `common_copy_link` | Link kopieren |
| `common_more` | Mehr |
| `cd_back` | Zurück |
| `cd_close` | Schließen |
| `cd_key_show` | API-Key anzeigen |
| `cd_key_hide` | API-Key verbergen |
| `cd_paste` | Aus Zwischenablage einfügen |
| `cd_open_link` | Link im Browser öffnen |
| `cd_add_word` | Wort hinzufügen |
| `cd_remove_word` | %1$s entfernen |
| `err_net` | Keine Verbindung |
| `err_timeout` | Server antwortet nicht (Zeitüberschreitung) |
| `err_401` | Key ungültig (401) |
| `err_429` | Limit erreicht (429) — später erneut |
| `err_server` | Server-Fehler %1$d |
| `err_unknown` | Unbekannter Fehler: %1$s |
| `err_no_browser` | Kein Browser gefunden |
| `err_url_invalid` | Ungültige URL |
| `err_url_scheme` | URL muss mit http:// oder https:// beginnen |
| `err_url_https_required` | Dieser Anbieter braucht https:// |
| `err_url_cleartext_public` | Unverschlüsselt über das Internet — https:// oder VPN verwenden |
| `err_url_v1` | Base-URL endet normalerweise auf /v1 |
| `err_storage_full` | Nicht genug Speicherplatz |
| `err_download_net` | Netzwerkfehler beim Laden |
| `err_model_checksum` | Datei beschädigt — erneut laden |
| `provider_openai` | OpenAI |
| `provider_groq` | Groq (kostenlos) |
| `provider_mistral` | Mistral |
| `provider_together` | Together AI |
| `provider_deepinfra` | DeepInfra |
| `provider_openrouter` | OpenRouter |
| `provider_anthropic` | Anthropic (Claude) |
| `provider_gemini` | Google Gemini |
| `provider_deepseek` | DeepSeek |
| `provider_custom` | Eigener Server |
| `level_off` | Aus |
| `level_smooth` | Glätten |
| `level_beautify` | Verschönern |
| `level_summarize` | Zusammenfassen |

### 6.2 Home (H)

| Key | Text |
|---|---|
| `home_cd_help` | Anleitung und Hilfe |
| `home_cd_settings` | Einstellungen |
| `home_hero_title` | Schwebender Mikro-Knopf |
| `home_hero_sub_off` | Aus — startet den Knopf über allen Apps |
| `home_hero_sub_on` | Läuft — antippen zum Diktieren, ziehen zum Verschieben |
| `home_hero_btn_start` | Mikro-Knopf starten |
| `home_hero_btn_stop` | Mikro-Knopf beenden |
| `home_state_running` | Läuft |
| `home_state_stopped` | Aus |
| `home_blocked_mic` | Mikrofon fehlt — beheben |
| `home_blocked_overlay` | „Über anderen Apps anzeigen" fehlt — beheben |
| `home_banner_title` | Noch nicht optimal |
| `home_banner_a11y` | Ohne Bedienungshilfe landet der Text nur in der Zwischenablage. |
| `home_banner_model` | Offline gewählt, aber kein Modell geladen. |
| `home_banner_notif` | Ohne Benachrichtigung kannst du den Knopf nur hier beenden. |
| `home_banner_fix` | Beheben |
| `home_status_title` | Status |
| `home_row_recognition` | Erkennung |
| `home_row_refine` | Textverbesserung |
| `home_row_permissions` | Berechtigungen |
| `home_row_keyboard` | Diktat-Tastatur |
| `home_row_models` | Offline-Modelle |
| `home_val_online` | Online · %1$s · %2$s |
| `home_val_offline` | Offline · %1$s (%2$s) |
| `home_val_offline_missing` | Offline · kein Modell geladen |
| `home_val_refine_off` | Aus |
| `home_val_refine` | %1$s · %2$s |
| `home_val_perms_ok` | Mikrofon ✓ · Über Apps ✓ · Bedienungshilfe ✓ |
| `home_val_perms_a11y_missing` | Mikrofon ✓ · Über Apps ✓ · Bedienungshilfe ✗ (Zwischenablage) |
| `home_val_perms_missing` | Pflicht-Berechtigung fehlt |
| `home_val_kb_active` | Aktiv |
| `home_val_kb_enabled` | Aktiviert, nicht ausgewählt |
| `home_val_kb_off` | Nicht aktiviert (optional) |
| `home_val_models` | %1$d geladen · %2$s belegt |
| `home_val_models_none` | Keins geladen |
| `home_share_hint` | Sprachnachrichten abtippen: in WhatsApp lange drücken → Teilen → WhisperLoom. |
| `home_rerun_setup` | Einrichtung erneut öffnen |
| `home_version` | WhisperLoom %1$s |
| `home_snack_overlay_lost` | „Über anderen Apps anzeigen" wurde entzogen |
| `home_snack_fix` | Erlauben |

### 6.3 Einrichtungs-Assistent (W)

| Key | Text |
|---|---|
| `setup_title` (bestehend, neu) | Einrichtung |
| `setup_cd_overview` | Alle Schritte anzeigen |
| `setup_progress` | Schritt %1$d von %2$d |
| `setup_chip_done` | Erledigt |
| `setup_chip_open` | Fehlt noch |
| `setup_chip_optional` | Optional |
| `setup_chip_skipped` | Übersprungen |
| `setup_back` | Zurück |
| `setup_skip` | Überspringen |
| `setup_next` | Weiter |
| `setup_working` | Bitte warten … |
| `setup_more_info` | Was passiert dabei? |
| `setup_open_app_settings` | App-Einstellungen öffnen |
| `setup_overview_title` | Alle Schritte |
| `setup_step_1` | Erkennungsweg |
| `setup_step_2` | Zugang oder Modell |
| `setup_step_3` | Mikrofon |
| `setup_step_4` | Über anderen Apps anzeigen |
| `setup_step_5` | Bedienungshilfe |
| `setup_step_6` | Benachrichtigungen |
| `setup_step_7` | Diktat-Tastatur |
| `setup_status_missing` | fehlt |
| `setup_status_skipped` | übersprungen |
| `setup_status_done` | erledigt |
| `welcome_title` | Diktiere in jede App. |
| `welcome_body` | WhisperLoom nimmt auf, erkennt den Text über deinen eigenen Zugang oder ein Offline-Modell und tippt ihn ins aktuelle Feld. In ein paar Schritten ist alles bereit. |
| `welcome_point_1` | Online oder offline — du entscheidest |
| `welcome_point_2` | Dein Key bleibt auf dem Gerät |
| `welcome_point_3` | Dauert etwa zwei Minuten |
| `welcome_start` | Los geht's |
| `setup_s1_title` | Wie soll WhisperLoom Sprache erkennen? |
| `setup_s1_body` | Du kannst später jederzeit wechseln. |
| `setup_s1_online` | Online-Dienst |
| `setup_s1_online_body` | Beste Qualität, schnell. Audio wird an den gewählten Anbieter gesendet. Braucht einen API-Key (bei Groq kostenlos). |
| `setup_s1_recommended` | Empfohlen |
| `setup_s1_offline` | Offline auf dem Gerät |
| `setup_s1_offline_body` | Alles bleibt auf dem Gerät. Modell einmalig laden (32–574 MB), Erkennung dauert einige Sekunden. |
| `setup_s1_offline_unavailable` | Auf diesem Gerät nicht verfügbar |
| `setup_s2a_title` | Zugang zum Dienst |
| `setup_s2a_body` | Wähle den Anbieter und füge deinen API-Key ein. Der Key wird nur auf diesem Gerät gespeichert. |
| `setup_s2b_title` | Offline-Modell laden |
| `setup_s2b_body` | Wähle ein Modell. Empfehlung: Small — gute Qualität für Deutsch bei 190 MB. |
| `setup_s3_title` | Mikrofon erlauben |
| `setup_s3_body` | Ohne Mikrofon kein Diktat. Android fragt dich gleich — bitte „Bei Nutzung der App" wählen. |
| `setup_s3_btn` | Mikrofon erlauben |
| `setup_s3_denied` | Du hast das Mikrofon abgelehnt. Bitte in den App-Einstellungen erlauben. |
| `setup_s4_title` | Über anderen Apps anzeigen |
| `setup_s4_body` | Der schwebende Mikro-Knopf liegt über anderen Apps. Dafür braucht Android deine Erlaubnis — du landest gleich in den Systemeinstellungen: dort „WhisperLoom" einschalten und zurück. |
| `setup_s4_step_1` | Die Systemeinstellung öffnet sich |
| `setup_s4_step_2` | Schalter „Über anderen Apps anzeigen" einschalten |
| `setup_s4_step_3` | Zurück-Taste — WhisperLoom prüft automatisch |
| `setup_s4_btn` | Einstellung öffnen |
| `setup_s4_keyboard_only` | Nur Tastatur nutzen |
| `setup_s4_keyboard_only_hint` | Ohne diesen Schritt funktioniert nur die Tastatur-Variante. |
| `setup_s5_title` | Text automatisch einfügen |
| `setup_s5_fallback` | Ohne diesen Schritt landet der Text in der Zwischenablage — du fügst ihn dann selbst ein. |
| `setup_s5_step_1` | Die Bedienungshilfe-Einstellungen öffnen sich |
| `setup_s5_step_2` | Installierte Apps → WhisperLoom → Ein |
| `setup_s5_step_3` | Bei „Eingeschränkte Einstellung": App-Info → ⋮ → Eingeschränkte Einstellungen zulassen, dann erneut |
| `setup_s5_btn` | Bedienungshilfe aktivieren |
| `setup_s6_title` | Beenden per Benachrichtigung |
| `setup_s6_body` | Solange der Knopf läuft, zeigt Android eine stille Benachrichtigung mit „Beenden". Ohne Erlaubnis ist sie unsichtbar — beenden kannst du den Knopf dann in der App. |
| `setup_s6_btn` | Benachrichtigungen erlauben |
| `setup_s7_title` | Diktat-Tastatur |
| `setup_s7_body` | Alternative zum Knopf: die WhisperLoom-Tastatur mit Halten-zum-Sprechen. Zwei Schritte: aktivieren, dann auswählen. |
| `setup_s7_enable` | 1 · Tastatur aktivieren |
| `setup_s7_enable_btn` | Aktivieren |
| `setup_s7_select` | 2 · Tastatur auswählen |
| `setup_s7_select_btn` | Auswählen |
| `setup_try_hint` (bestehend) | Zum Diktieren hierher tippen … |
| `setup_done_title` | Bereit zum Diktieren |
| `setup_done_summary` | Deine Einrichtung |
| `setup_done_a11y_skipped` | übersprungen — Text landet in der Zwischenablage |
| `setup_done_start` | Knopf starten & los |
| `setup_done_home` | Zum Start |
| `a11y_label` (bestehend) | WhisperLoom Text-Einfügen |
| `a11y_description` (bestehend) | Fügt diktierten Text ins gerade fokussierte Feld ein, damit du beim Diktieren nicht die Tastatur wechseln musst. Es wird nichts mitgelesen oder gespeichert. |

### 6.4 Einstellungen-Hub (E), Über (E6)

| Key | Text |
|---|---|
| `settings_title` (bestehend, neu) | Einstellungen |
| `settings_group_recognition` | Erkennung |
| `settings_group_text` | Text |
| `settings_group_button` | Knopf & Tastatur |
| `settings_group_models` | Offline-Modelle |
| `settings_group_help` | Anleitung & Hilfe |
| `settings_group_about` | Über WhisperLoom |
| `settings_help_sub` | Anleitung, API-Keys, Datenschutz |
| `settings_models_none` | Keins geladen |
| `settings_val_bubble_on` | Knopf läuft |
| `settings_val_bubble_off` | Knopf aus |
| `settings_val_kb_on` | Tastatur aktiv |
| `settings_val_kb_off` | Tastatur nicht aktiviert |
| `settings_val_text` | %1$s · %2$s |
| `settings_rule_fillers` | Füllwörter |
| `settings_rule_cap` | Groß-Schreibung |
| `settings_rule_space` | Leerzeichen |
| `about_version` | Version %1$s |
| `about_license` | whisper.cpp (MIT) · Jetpack Compose (Apache 2.0) · Material Symbols (Apache 2.0) |
| `about_source` | Quellcode auf GitHub |

### 6.5 Erkennung (E1) und Zugangs-Felder (auch Schritt 2a)

| Key | Text |
|---|---|
| `rec_title` | Erkennung |
| `rec_engine_online` | Online-Dienst |
| `rec_engine_offline` | Offline-Modell |
| `rec_no_model` | Kein Modell geladen |
| `rec_load_model` | Modell laden |
| `rec_card_transcription` | Transkription |
| `rec_card_offline` | Offline-Modell |
| `rec_offline_first_use` | Erste Nutzung lädt das Modell in den Speicher (2–5 s). |
| `rec_card_language` | Sprache & Kontext |
| `rec_language` | Sprache |
| `rec_provider` | Anbieter |
| `rec_base_url` | Base-URL |
| `rec_base_url_hint` | Muss auf /v1 enden. http:// nur im eigenen Netz (LAN/VPN). |
| `rec_api_key` | API-Key |
| `rec_api_key_optional` | API-Key (optional) |
| `rec_key_local` | Wird nur auf diesem Gerät gespeichert. |
| `rec_key_pasted` | Eingefügt |
| `rec_model` | Modell |
| `rec_model_custom` | Eigenes Modell … |
| `rec_key_where` | Wo bekomme ich einen Key? |
| `rec_test` | Zugang prüfen |
| `rec_test_ok` | Verbunden · %1$s s |
| `rec_test_fail` | Fehler: %1$s |
| `rec_privacy_online` | Audio wird zur Erkennung an %1$s gesendet. |
| `rec_privacy_offline` | Alles bleibt auf dem Gerät. |
| `rec_context_unsupported` | Dieser Anbieter nimmt keinen Kontext entgegen — das Feld wirkt nur bei anderen Anbietern und offline. (3.0.0, statt rec_context_words/_info) |
| `pref_api_prompt_hint` (bestehend) | Kontext: Namen, Fachbegriffe, Schreibweisen |
| `pref_api_prompt_info` (bestehend) | Wird als Prompt mitgeschickt und hilft der Erkennung bei Eigennamen und Fachwörtern. Kostet nichts extra. |
| `pref_language` (bestehend) | Sprache |
| `model_custom_title` | Eigenes Modell |
| `model_custom_hint` | Modell-ID |
| `model_custom_info` | Genau so, wie der Anbieter die ID nennt. |
| `model_deprecated_suffix` | (Auslauf %1$s) |
| `key_sheet_title` | Wo bekomme ich einen Key? |

### 6.6 Text (E2) und Füllwörter-Sheet (B3)

| Key | Text |
|---|---|
| `text_title` | Text |
| `text_card_refine` | Textverbesserung (KI) |
| `text_level_off_sub` | Nur die Regeln unten, keine zweite Anfrage. |
| `text_level_smooth_sub` | Zeichensetzung, Groß-/Kleinschreibung, Absätze. Inhalt unverändert. |
| `text_level_beautify_sub` | Formuliert verständlicher, zieht Sätze zusammen, bewahrt den Inhalt. |
| `text_level_summarize_sub` | Kürzt auf die Kernaussagen. |
| `text_level_cost` | Zweite Anfrage · ca. 1–2 s länger · geringe Zusatzkosten |
| `pref_smart_fillers` (bestehend) | Füllwörter intelligent entfernen |
| `pref_smart_fillers_info` (bestehend, neu) | Statt fester Wortliste entscheidet die KI selbst, welche Füllwörter, Versprecher und Wiederholungen weg können. Im Zweifel bleibt das Wort. |
| `text_smart_needs_level` | Braucht eine Stufe über „Aus". |
| `text_card_access` | Zugang für die Textverbesserung |
| `text_own_access` | Eigenen Zugang verwenden |
| `text_own_access_off` | Nutzt Anbieter und Key der Erkennung (%1$s). |
| `text_own_access_on` | Eigener Anbieter, Key und Modell. |
| `text_llm_provider` | Anbieter |
| `text_llm_model` | Modell |
| `text_llm_model_placeholder` | z. B. qwen3:8b |
| `text_model_custom` | Eigenes Modell … |
| `text_gemini_warning` | Free-Tier: Google darf Inhalte zum Training nutzen. |
| `text_deepseek_warning` | Server in China — Datenschutz beachten. |
| `text_anthropic_note` | OpenAI-Kompatibilitätsschicht — von Anthropic als Test-Werkzeug eingestuft. |
| `text_needs_online` | Textverbesserung braucht einen Online-Zugang. |
| `text_add_access` | Eigenen Zugang eintragen |
| `text_test` | Zugang prüfen |
| `text_card_rules` | Regeln ohne KI |
| `pref_remove_fillers` (bestehend) | Füllwörter entfernen (ähm, äh …) |
| `text_fillers_sub` | Feste Wortliste je Sprache |
| `text_fillers_edit` | Liste bearbeiten |
| `pref_auto_cap` (bestehend) | Automatisch groß schreiben |
| `pref_trailing_space` (bestehend) | Leerzeichen nach Diktat anhängen |
| `text_fillers_paused` | Die Wortliste pausiert, solange die KI über Füllwörter entscheidet. |
| `fillers_title` | Füllwörter |
| `fillers_builtin` | Eingebaut |
| `fillers_custom` | Eigene Wörter |
| `fillers_add_hint` | Wort hinzufügen |
| `fillers_empty` | Noch keine eigenen Wörter |
| `fillers_hint` | Nur eindeutige Füllsilben — echte Wörter wie „halt" bleiben, sonst kaputte Sätze. |
| `fillers_reset` | Standard wiederherstellen |

### 6.7 Knopf & Tastatur (E3)

| Key | Text |
|---|---|
| `button_title` | Knopf & Tastatur |
| `button_card_bubble` | Schwebender Knopf |
| `button_reset_pos` | Position zurücksetzen |
| `button_reset_pos_sub` | Setzt den Knopf an die Startposition |
| `button_pos_reset_done` | Position zurückgesetzt |
| `button_text_output` | Textausgabe |
| `button_text_output_a11y` | Bedienungshilfe: fügt direkt ins Feld ein |
| `button_text_output_clip` | Zwischenablage (Bedienungshilfe aus) |
| `button_card_permissions` | Berechtigungen |
| `perm_mic` | Mikrofon |
| `perm_overlay` | Über anderen Apps anzeigen |
| `perm_a11y` | Bedienungshilfe |
| `perm_a11y_sub` | Empfohlen — fügt Text direkt ein |
| `perm_notif` | Benachrichtigungen |
| `perm_allow` | Erlauben |
| `perm_open` | Öffnen |
| `button_card_keyboard` | Diktier-Tastatur |
| `button_keyboard_intro` | Alternative zum Knopf: WhisperLoom als Tastatur mit Halten-zum-Sprechen. Nützlich, wenn eine App kein Overlay erlaubt. |
| `button_card_howto` | Kurzanleitung |
| `help_dictate_1` | Knopf antippen = Aufnahme |
| `help_dictate_2` | Nochmal antippen = fertig & einfügen |
| `help_dictate_3` | Auf ✕ ziehen = verwerfen |
| `help_dictate_4` | Rot = Fehler, antippen = erneut |

### 6.8 Offline-Modelle (E4, Schritt 2b, D1, D2, Download-Notification)

| Key | Text |
|---|---|
| `models_title` | Offline-Modelle |
| `models_intro` | Modelle werden einmalig von huggingface.co geladen und bleiben auf dem Gerät. Kein Modell ist in der App enthalten. |
| `models_storage` | Belegt: %1$s · Frei: %2$s |
| `models_need_download` | Zuerst ein Modell laden |
| `models_load` | Laden (%1$s) |
| `models_download_cd` | %1$s herunterladen |
| `models_cancel_cd` | Download abbrechen |
| `models_delete_cd` | %1$s löschen |
| `models_progress` | %1$d %% · %2$s von %3$s · %4$s |
| `models_progress_unknown` | %1$s geladen |
| `models_failed` | Fehlgeschlagen: %1$s |
| `models_too_big` | Für dieses Gerät zu groß |
| `models_recommended` | Empfohlen |
| `models_installed` | Geladen |
| `models_tiny` | Tiny |
| `models_tiny_sub` | 32 MB · ~250 MB RAM · nur zum Ausprobieren |
| `models_base` | Base |
| `models_base_sub` | 60 MB · ~355 MB RAM · schnell, kurze Sätze |
| `models_small` | Small |
| `models_small_sub` | 190 MB · ~430 MB RAM · gute Qualität für Deutsch |
| `models_large` | Large v3 Turbo |
| `models_large_sub` | 574 MB · ~1 GB RAM · beste Qualität, langsam, ab 6 GB Gerätespeicher |
| `models_empty_title` | Noch kein Modell geladen |
| `models_empty_body` | Empfehlung: Small — gute Qualität für Deutsch bei 190 MB. |
| `models_source` | Quelle: huggingface.co/ggerganov/whisper.cpp |
| `models_metered_title` | Über mobile Daten laden? |
| `models_metered_body` | %1$s werden heruntergeladen. Im WLAN ist das kostenlos. |
| `models_metered_ok` | Laden |
| `models_delete_title` | %1$s löschen? |
| `models_delete_body` | %1$s werden frei. Für die Offline-Erkennung muss dann ein anderes Modell geladen werden. |
| `models_delete_active` | Dies ist das aktive Modell — Offline ist danach nicht einsatzbereit. |
| `models_notif_title` | Modell wird geladen |
| `models_notif_text` | %1$s · %2$d %% |
| `models_notif_done` | %1$s ist bereit |

### 6.9 Hilfe (E5, B1)

| Key | Text |
|---|---|
| `help_title` | Anleitung & Hilfe |
| `help_s1_title` | So funktioniert's |
| `help_s1_bubble` | Schwebender Knopf: antippen = Aufnahme, nochmal = fertig & einfügen, auf ✕ ziehen = verwerfen, rot = Fehler, antippen = erneut. |
| `help_s1_keyboard` | Diktier-Tastatur: Mikrofon gedrückt halten, sprechen, loslassen. |
| `help_s1_share` | Sprachnachrichten: in WhatsApp lange drücken → Teilen → WhisperLoom. Der Text erscheint in Absätzen, du kannst ihn kopieren oder weitergeben. |
| `help_s2_title` | Einrichtung Schritt für Schritt |
| `help_s3_title` | API-Key bekommen |
| `help_s3_intro` | Ein API-Key ist ein persönlicher Zugangsschlüssel. Du bezahlst nur, was du nutzt — ein Diktat kostet meist unter einem Cent. |
| `help_key_openai` | platform.openai.com registrieren → Billing: Zahlungsmittel + Guthaben (mind. 5 $) → API keys → Create new secret key. GPT Transcribe ≈ 0,0045 $/min. |
| `help_key_groq` | console.groq.com registrieren → API Keys → Create API Key. Kostenlos ohne Zahlungsmittel (8 h Audio/Tag). |
| `help_key_mistral` | console.mistral.ai registrieren → Plan „Experiment" (gratis, Telefon-Verifizierung) oder Pay-as-you-go → API Keys → Create new key. Voxtral ≈ 0,003 $/min. |
| `help_key_together` | api.together.ai registrieren (Startguthaben) → Settings → API Keys → Create key. Whisper Large v3 ≈ 0,0015 $/min. |
| `help_key_deepinfra` | deepinfra.com anmelden → Dashboard → API Keys → New API Key → Guthaben aufladen. Whisper Turbo ≈ 0,0002 $/min. |
| `help_key_openrouter` | openrouter.ai anmelden → Credits aufladen → Settings → Keys → Create Key. Ein Key für viele Modelle. |
| `help_key_anthropic` | platform.claude.com registrieren → Billing → Settings → Keys → Create Key. Nur Textverbesserung. |
| `help_key_gemini` | aistudio.google.com/apikey öffnen → Create API key. Gratis, aber Free-Tier-Inhalte dürfen zum Training genutzt werden. Nur Textverbesserung. |
| `help_key_deepseek` | platform.deepseek.com registrieren → Top up → API keys → Create new API key. Nur Textverbesserung. |
| `help_prices_note` | Preise Stand 09/2026, ohne Gewähr. |
| `help_s4_title` | Eigener Server |
| `help_s4_body` | Jeder OpenAI-kompatible Server funktioniert: Endpunkte /v1/audio/transcriptions (Erkennung) und /v1/chat/completions (Textverbesserung). Unter Erkennung → Anbieter „Eigener Server" die Base-URL eintragen, Key nur wenn der Server einen verlangt. Empfohlene Server: speaches, whisper.cpp-server, LocalAI; für Textverbesserung Ollama. http:// nur im eigenen Netz oder per VPN — sonst https. |
| `help_s5_title` | Offline-Modus |
| `help_s5_body` | Ein Offline-Modell erkennt Sprache direkt auf dem Gerät. Es wird einmalig geladen (32–574 MB) und liegt im App-Speicher; löschen kannst du es unter Offline-Modelle. Small ist für Deutsch die beste Balance. Die Erkennung dauert je Modell 2–10 Sekunden. |
| `help_s6_title` | Datenschutz |
| `help_s6_online` | Online: Audio und Kontext-Prompt gehen an den gewählten Anbieter. Bei Textverbesserung geht der erkannte Text an das Sprachmodell. Dein Key bleibt auf dem Gerät. WhisperLoom speichert keine Aufnahmen. |
| `help_s6_offline` | Offline: Nichts verlässt das Gerät — nur der Modell-Download geht ins Netz. |
| `help_s6_a11y` | Die Bedienungshilfe liest nichts mit und speichert nichts; sie fügt nur den diktierten Text ein. |
| `help_s7_title` | Wenn etwas nicht klappt |
| `help_p1` | Knopf erscheint nicht |
| `help_p1_body` | „Über anderen Apps anzeigen" muss erlaubt sein. Zusätzlich in den Android-Einstellungen die Akku-Optimierung für WhisperLoom ausschalten. |
| `help_p2` | Text landet nur in der Zwischenablage |
| `help_p2_body` | Die Bedienungshilfe „WhisperLoom" ist aus. Aktivieren, dann fügt WhisperLoom den Text direkt ein. |
| `help_p3` | Key ungültig (401) oder Limit erreicht (429) |
| `help_p3_body` | Key beim Anbieter neu erzeugen und einfügen; Guthaben oder Tageslimit prüfen. |
| `help_p4` | „Eingeschränkte Einstellung" |
| `help_p4_body` | Bei manuell installierten Apps: App-Info → ⋮ → Eingeschränkte Einstellungen zulassen, dann die Bedienungshilfe erneut aktivieren. |
| `help_p5` | Offline zu langsam |
| `help_p5_body` | Ein kleineres Modell wählen (Base oder Small) oder auf den Online-Dienst wechseln. |

### 6.10 Transkription (S)

| Key | Text |
|---|---|
| `share_label` (bestehend) | Mit WhisperLoom transkribieren |
| `share_title` (bestehend) | Transkription |
| `share_starting` (bestehend) | Wird vorbereitet … |
| `share_decoding` (bestehend) | Audio wird entpackt … |
| `share_sending` (bestehend) | Wird übertragen … |
| `share_file_of` (bestehend) | Datei %1$d/%2$d |
| `share_progress` | Datei %1$d von %2$d · Stück %3$d von %4$d · %5$s |
| `share_head_single` | %1$s · 1 Datei |
| `share_head_multi` | %1$d Dateien · %2$s gesamt |
| `share_section` | %1$s · %2$s |
| `share_copy` (bestehend) | Kopieren |
| `share_forward` (bestehend) | Teilen |
| `share_copied` (bestehend) | In die Zwischenablage kopiert |
| `share_no_audio` (bestehend) | Keine Audiodatei erhalten |
| `share_no_audio_body` | Teile eine Audiodatei oder Sprachnachricht mit WhisperLoom. |
| `share_empty` (bestehend) | Die Datei enthält keine Tonspur |
| `share_nothing_recognised` (bestehend) | (nichts erkannt) |
| `share_one_failed` (bestehend) | Fehlgeschlagen: %1$s |
| `share_all_failed` | Transkription fehlgeschlagen |
| `share_unknown_source` (bestehend) | Sprachnachricht |
| `share_not_configured` | Kein Zugang eingerichtet |
| `share_not_configured_body` | Richte einen Online-Dienst oder ein Offline-Modell ein, dann klappt es. |
| `share_open_setup` | Einrichtung öffnen |
| `share_retry_file` | Erneut |
| `share_retry_all` | Alles erneut |
| `share_hide_fillers` | Füllwörter ausblenden |
| `share_hide_fillers_on` | ähm, äh … ausgeblendet |
| `share_hide_fillers_off` | Wortgetreu, 100 % |
| `share_cd_copy` | Text kopieren |

### 6.11 Schwebender Knopf (V1) und Notification (N1)

| Key | Text |
|---|---|
| `float_channel` (bestehend) | WhisperLoom Diktat |
| `float_running` (bestehend) | WhisperLoom-Diktat aktiv |
| `float_running_text` (bestehend) | Knopf antippen zum Diktieren, ziehen zum Verschieben |
| `float_stop` (bestehend) | Beenden |
| `float_no_overlay` (bestehend) | Bitte „Über anderen Apps anzeigen" erlauben |
| `float_clipboard_fallback` (bestehend) | Text kopiert — Bedienungshilfe aktivieren zum automatischen Einfügen |
| `float_copied_short` | Kopiert — einfügen |
| `float_sending` (bestehend) | sendet … |
| `float_retry_hint` (bestehend) | tippen = erneut |
| `float_discarded` (bestehend) | Diktat verworfen |
| `float_cancel_label` | Verwerfen |
| `float_cancel_release` | Loslassen zum Verwerfen |
| `float_not_configured` | Kein Zugang eingerichtet — in WhisperLoom einrichten |
| `cd_bubble_idle` | Diktat starten |
| `cd_bubble_recording` | Aufnahme läuft, %1$s — antippen zum Beenden |
| `cd_bubble_sending` | Wird übertragen |
| `cd_bubble_error` | Fehler — antippen für erneuten Versuch |
| `cd_cancel` (bestehend) | Diktat verwerfen |

### 6.12 IME-Tastatur (V2)

| Key | Text |
|---|---|
| `kb_hint_hold` (bestehend) | Halte das Mikrofon gedrückt und sprich |
| `kb_listening` (bestehend) | Höre zu … rechts wischen stellt fest |
| `kb_transcribing` (bestehend) | Wird übertragen … |
| `kb_need_permission` (bestehend) | Mikrofon-Berechtigung fehlt — tippe zum Einrichten |
| `kb_not_configured` | Kein Zugang eingerichtet — tippe zum Einrichten |
| `kb_error` (bestehend) | Fehler bei der Erkennung — erneut versuchen |
| `kb_space` (bestehend) | Leer |
| `cd_mic` (bestehend) | Diktat-Mikrofon — antippen zum Starten, gedrückt halten zum Sprechen |
| `cd_kb_switch` | Eingabemethode wechseln |
| `cd_kb_comma` | Komma |
| `cd_kb_space` | Leerzeichen |
| `cd_kb_period` | Punkt |
| `cd_kb_backspace` | Löschen |
| `cd_kb_enter` | Eingabe |
| `cd_kb_retry` | Erneut senden |
| `cd_kb_settings` | WhisperLoom-Einstellungen |

Wisch-Geste (V3):

| Key | Text |
|---|---|
| `kb_lock_armed` | Loslassen stellt die Aufnahme fest |
| `kb_cancel_armed` | Loslassen verwirft die Aufnahme |
| `kb_locked` | Aufnahme %1$s — senden oder verwerfen |
| `kb_discarded` | Aufnahme verworfen |
| `cd_kb_discard` | Aufnahme verwerfen |
| `cd_kb_send` | Aufnahme senden |
| `cd_kb_lock` | Aufnahme feststellen |
| `cd_mic_recording` | Aufnahme läuft, %1$s — antippen zum Senden |
| `cd_mic_locked` | Aufnahme festgestellt, %1$s — antippen zum Senden |
| `cd_mic_sending` | Wird übertragen |
| `cd_mic_error` | Fehler — antippen für erneuten Versuch |

**Geste (§5.3):** Aus der laufenden Aufnahme nach rechts ziehen stellt fest, nach links
verwirft. Schwellen relativ zum Druckpunkt: einrasten ab 56 dp (wie `CANCEL_HIT_RADIUS_DP`),
lösen erst unter 56 − 12 dp (Hysterese), senkrechte Toleranz 64 dp — für ein eingerastetes Ziel
64 + 12 dp, weil ein Daumen-Wisch einen Bogen beschreibt und sonst auf der Grenzlinie die
Einrast-Haptik prasselt. Die Ziele erscheinen erst, wenn der Finger den `scaledTouchSlop`
überschritten hat (er ist auch die Untergrenze aller Schwellen); ein liegender Finger zittert. Haptik `CONFIRM` beim Einrasten und beim Feststellen, `REJECT`
beim Verwerfen. Während des Ziehens sind die beiden 56-dp-Ziele in der Mikro-Zone reine
Anzeigen (`state_activated` hebt das getroffene hervor, `importantForAccessibility=no`);
festgestellt werden daraus Tasten, rechts mit `ic_send` statt `ic_lock`.

**A11y (§5.6):** Die Mikro-Taste hat zusätzlich zum `OnTouchListener` einen `OnClickListener` —
mit TalkBack kommt Gedrückthalten nicht an. Ein per Klick gestartetes Diktat geht sofort in den
festgestellten Zustand, sonst gäbe es nichts, was die Aufnahme beendet. Die `contentDescription`
der Taste folgt dem Zustand (`cd_mic*`).

Die Statuszeile bleibt `accessibilityLiveRegion="polite"` — **außer im festgestellten Zustand**:
dort schreibt der Sekunden-Ticker sie laufend neu, und jede Änderung einer Live-Region wird
vorgelesen. Das wäre eine Ansage pro Sekunde in das eigene Diktat hinein. Das Feststellen selbst
wird noch angesagt (der erste Tick läuft davor), danach schaltet der Dienst die Region auf `none`
und beim Verlassen zurück auf `polite`.

---

## 7. Akzeptanzkriterien (prüfbar, je Screen)

**H Home**
1. Bei `isSetUp == true` ist H der erste gezeichnete Screen; erstes sichtbares Element unter der App-Bar ist die Hero-Karte mit dem 64-dp-Hauptbutton (Text „Mikro-Knopf starten" bzw. „Mikro-Knopf beenden").
2. Tipp auf den Hauptbutton startet/stoppt `FloatingMicService`; spätestens 500 ms später zeigt der Button den neuen Zustand (Text, Farbe, Icon).
3. Ist Mikrofon oder Overlay entzogen, ist der Button `enabled=false` und ein klickbarer Warn-Chip führt in den passenden Assistenten-Schritt; kein Screen-Wechsel in den Assistenten ohne Nutzeraktion.
4. Jede Status-Zeile navigiert (E1/E2/E3/E4); Status ist als `stateDescription` für TalkBack lesbar; Chevrons ohne contentDescription.
5. ReadinessBanner erscheint genau dann, wenn Bedienungshilfe aus, Offline ohne Modell oder Benachrichtigung (API ≥ 33) verweigert ist, und verschwindet nach Behebung bei `onResume`.
6. Kein Text unter 12 sp; alle Textpaarungen ≥ 4,5:1 (Tokens §3.1).

**W Assistent (alle Schritte)**
1. Bei `isSetUp == false` öffnet W auf dem ersten nicht erledigten Schritt; W1 nur beim allerersten Start.
2. Jede Seite hat genau einen primären Button in der Bottom-Bar; Pflichtschritte sind ohne Erledigung nicht überspringbar, optionale zeigen „Überspringen".
3. Rückkehr aus jedem System-Intent aktualisiert den Status-Chip innerhalb eines `onResume` ohne Nutzeraktion.
4. Mikrofon dauerhaft verweigert → Button wird „App-Einstellungen öffnen"; Overlay verweigert → „Nur Tastatur nutzen" führt zu einem lauffähigen Zustand (Schritt 7 wird Pflicht).
5. Schritt 2a: „Weiter" ist aktiv, sobald Key (bzw. bei Eigener Server: gültige URL) vorliegt; „Zugang prüfen" liefert innerhalb 15 s Erfolg- oder Fehler-Chip mit Klartext.
6. Schritt 2b/E4: Download zeigt Prozent + MB, ist abbrechbar, überlebt Screen-Wechsel, verweigert bei falscher Größe/SHA-256 mit Fehlerzeile und Erneut-Button.
7. W9 „Knopf starten & los" startet den Service und landet auf H; Zurück von H verlässt die App (W nicht im Back-Stack).

**E Hub**
1. Fünf Gruppen + Über in fester Reihenfolge; Supporting-Texte zeigen aktuelle Werte (Anbieter/Modell, Stufe, Knopf-/Tastaturstatus, Modelle).
2. Keine Switches auf dieser Ebene; jede Zeile navigiert; Zurück = H.

**E1 Erkennung**
1. Segmented „Online/Offline" schreibt `Prefs.engine` sofort; Offline ohne Modell zeigt die Warnkarte mit „Modell laden".
2. Anbieter-Wechsel setzt Base-URL und erstes Modell des Presets; Base-URL-Feld ist nur bei „Eigener Server" editierbar; ungültige URL → `isError` mit Klartext aus §6.1.
3. API-Key-Feld maskiert, Auge und Einfügen funktionieren, Key wird nie im Klartext vorgelesen.
4. Modell-Dropdown enthält exakt die Preset-Modelle (§8.2) + „Eigenes Modell …"; freie ID wird nach Übernahme als Wert angezeigt.
5. Datenschutz-Zeile nennt den tatsächlichen Anbieter bzw. „Alles bleibt auf dem Gerät".

**E2 Text**
1. Vier Stufen als Radio-Liste; `smartFillers`-Switch ist bei „Aus" deaktiviert mit Hinweistext.
2. „Eigenen Zugang verwenden" AUS → Supporting nennt den Erkennungs-Anbieter; AN → eigene Felder erscheinen animiert; bei Offline-Engine ohne eigenen Zugang erscheint die Warnkarte mit „Eigenen Zugang eintragen".
3. Gemini/DeepSeek/Anthropic zeigen ihren Hinweis-Chip.
4. B3: Standardwörter je Sprache abwählbar, eigene Wörter hinzufügbar/entfernbar, Leerzustand-Text, „Standard wiederherstellen" leert `disabledFillers`/`customFillers`.

**E3 Knopf & Tastatur**
1. Start/Beenden-Button verhält sich wie auf H; „Position zurücksetzen" setzt `floatX/floatY` auf Default und bestätigt per Snackbar.
2. Berechtigungszeilen zeigen Check oder Aktions-Button; nach Rückkehr aus dem System-Intent aktualisiert sich die Zeile bei `onResume`.
3. Tastatur-Zeilen: „Auswählen" erst nach „Aktivieren" enabled; Probierfeld nimmt Fokus an.

**E4 Offline-Modelle**
1. Liste zeigt genau Tiny/Base/Small/Large v3 Turbo mit Größe, RAM, Hinweis; Small trägt „Empfohlen"; Large ist auf Geräten < 6 GB RAM gedimmt.
2. Download über gebührenpflichtiges Netz fragt D2; Abbruch entfernt `.part`; Fortschritt determinate mit Prozent/MB/Rate.
3. Löschen fragt D1 mit Größe; aktives einziges Modell löst Zusatzhinweis aus; nach Löschen zeigt H das Banner „Offline gewählt, aber kein Modell geladen".
4. Leerzustand mit Illustration und Empfehlung, wenn nichts installiert und kein Download läuft.
5. Kein Modell liegt im APK (APK ohne `assets/*.bin`).

**E5 Hilfe**
1. Sieben Abschnitte in fester Reihenfolge, aufklappbar, erster offen; jeder Link öffnet den Browser oder zeigt „Kein Browser gefunden" mit „Link kopieren".
2. Abschnitt 2 hat je Schritt einen „Öffnen"-Button, der den Assistenten auf dem Schritt öffnet; Abschnitt 7 hat je Problem einen Button zum Ziel.
3. Datenschutz nennt Online- und Offline-Fall sowie die Bedienungshilfe.

**S Transkription**
1. Kopf zeigt Quelle + Dauer (bzw. „n Dateien · Gesamtdauer"); während der Arbeit Fortschritt „Datei x von y · Stück n von m · Status" mit determinate Balken.
2. Ergebnis erscheint in Absätzen (Regel §2.9) in `SelectionContainer`; Teilergebnisse erscheinen vor Abschluss aller Dateien.
3. „Füllwörter ausblenden" ist standardmäßig AN; AUS zeigt den unveränderten Rohtext (wortgetreu); Kopieren/Teilen liefern den angezeigten Stand.
4. Fehler je Datei zeigt Grund + „Erneut" nur für diese Datei; „Kein Zugang" zeigt „Einrichtung öffnen" (führt in W), „Keine Audiodatei" zeigt „Schließen".
5. Kopieren zeigt Snackbar; Zwischenablage wird nie automatisch überschrieben; Schließen bricht laufende Arbeit ab.
6. Die Activity trägt im Teilen-Menü das Icon `ic_share` (Mikrofon + Textzeilen) und das Label „Mit WhisperLoom transkribieren".

**V1 Schwebender Knopf**
1. Knopf 68 dp; die vier Zustände unterscheiden sich gleichzeitig in Füllung, Ring, Icon und Label (Tabelle §5.1).
2. RECORDING zeigt Timer „● m:ss" im Sekundentakt und Puls-Ring (statisch bei Reduce-Motion); SENDING zeigt rotierenden Bogen ohne Gesamt-Alpha; ERROR zeigt Shake einmalig, Tipp sendet erneut.
3. Ziehen in RECORDING/ERROR blendet das 72-dp-Abbrechen-Ziel ein; im Treffer-Radius wächst es auf 1,12 und färbt `errorContainer`; Loslassen verwirft mit Toast und stellt Position wieder her.
4. `contentDescription` wechselt je Zustand; ohne Bedienungshilfe erscheint nach dem Senden 2 s „Kopiert — einfügen".
5. Alle Farben stammen aus `@color/loom_*`; kein zweiter Hex-Wert in Drawables/Code.

**V2 IME-Tastatur**
1. Grund `loom_background`, Tasten `loom_surfaceContainerHigh`, Text/Icon `loom_onSurface`; keine Emoji-Glyphen.
2. Mikro-Taste 88 dp zeigt dieselben vier Zustände wie der Knopf; Pegelband (21 Balken) reagiert nur während der Aufnahme.
3. Statuszeile zeigt bei fehlendem Mikrofon/Zugang einen Warntext, dessen Tipp den Assistenten auf dem passenden Schritt öffnet.
4. Jede Taste ≥ 48 dp und mit contentDescription; Zahnrad öffnet E.

**N1 Notification**
1. Small-Icon ist die weiße Silhouette `ic_stat_whisperloom` (kein Quadrat); Farbe `loom_primary`, während RECORDING `loom_recording`.
2. Aktion „Beenden" stoppt den Service; Tipp auf die Notification öffnet H.

**Icons**
1. `ic_launcher` ist adaptiv mit Hintergrund, Vordergrund und Monochrom-Ebene; Motiv liegt vollständig im 66-dp-Kern (alle Pfadpunkte ≤ 33 dp vom Zentrum); Themed-Icon-Darstellung auf API 33+ zeigt das Mikrofon lesbar.
2. Der Launcher zeigt bei Kreis-, Squircle- und Rounded-Square-Maske keine abgeschnittenen Wellen.

---

## 8. Anhang

### 8.1 Neue/geänderte Prefs (`Prefs.kt`; bestehende Keys bleiben)

| Property | Key | Typ / Default | Zweck |
|---|---|---|---|
| `engine` | `engine` | String, `""` (online/offline) | Erkennungsweg |
| `sttProvider` | `stt_provider` | String, `openai` | Preset-ID (§8.2) |
| `apiBaseUrl` / `apiKey` / `apiModel` / `apiPrompt` (bestehend) | — | Default-Modell neu `gpt-transcribe` | Transkription |
| `llmUseOwn` | `llm_use_own` | Boolean, false | eigener LLM-Zugang |
| `llmProvider` | `llm_provider` | String, `openai` | Preset-ID |
| `llmBaseUrl` / `llmApiKey` | `llm_url` / `llm_key` | String, `""` | eigener Zugang |
| `llmModel` (bestehend) | `llm_model` | Default `gpt-4o-mini` | |
| `refineLevel` | `refine_level` | String, `off` (off/smooth/beautify/summarize); Migration `llm_polish==true` → `smooth` | Stufe |
| `smartFillers` / `removeFillers` / `autoCapitalize` / `trailingSpace` / `language` (bestehend) | — | — | |
| `customFillers` | `custom_fillers` | StringSet, leer | eigene Wörter |
| `disabledFillers` | `disabled_fillers` | StringSet, leer | abgewählte Standardwörter |
| `offlineModel` | `offline_model` | String, `small` (tiny/base/small/large) | aktives Modell |
| `overlaySkipped` | `overlay_skipped` | Boolean, false | „Nur Tastatur"-Pfad |
| `welcomeSeen` | `welcome_seen` | Boolean, false | W1 nur einmal |
| `a11ySkipped` / `notifSkipped` / `keyboardSkipped` | `setup_skip_*` | Boolean, false | Chip „Übersprungen" |
| `floatX` / `floatY` (bestehend) | — | — | |

### 8.2 Provider-Katalog für die Dropdowns (Auszug aus `api-providers.md §8`, Stand 2026-09-06 — Labels sind die Dropdown-Texte)

| Provider (ID) | Base-URL | STT-Modelle (Label → ID; erstes = Default) | LLM-Modelle (Label → ID; erstes = Default) | Flags |
|---|---|---|---|---|
| OpenAI (`openai`) | `https://api.openai.com/v1` | GPT Transcribe (empfohlen) → `gpt-transcribe` · GPT-4o Transcribe (Auslauf 02/2027) → `gpt-4o-transcribe` · GPT-4o mini Transcribe (Auslauf 02/2027) → `gpt-4o-mini-transcribe` · Whisper v2 (Auslauf 02/2027) → `whisper-1` | GPT-4o mini → `gpt-4o-mini` · GPT-4.1 mini → `gpt-4.1-mini` · GPT-5.6 Luna → `gpt-5.6-luna` · GPT-5.4 nano → `gpt-5.4-nano` | `gpt-transcribe`: `languages[]` statt `language`, `keywords[]`; gpt-5.x: kein `temperature`, `reasoning_effort=none`, `max_completion_tokens` |
| Groq (`groq`) | `https://api.groq.com/openai/v1` | Whisper Large v3 Turbo → `whisper-large-v3-turbo` · Whisper Large v3 → `whisper-large-v3` | GPT-OSS 20B → `openai/gpt-oss-20b` · GPT-OSS 120B → `openai/gpt-oss-120b` | LLM `reasoning_effort=low`; 25 MB |
| Mistral (`mistral`) | `https://api.mistral.ai/v1` | Voxtral Mini Transcribe 2 → `voxtral-mini-latest` | Mistral Small 4 → `mistral-small-latest` · Ministral 3 8B → `ministral-8b-latest` | `sttSendsPrompt=false`, `sttSendsResponseFormat=false`; Kontext als `context_bias` |
| Together AI (`together`) | `https://api.together.ai/v1` | Whisper Large v3 → `openai/whisper-large-v3` | — (Freitext) | 80 MB |
| DeepInfra (`deepinfra`) | `https://api.deepinfra.com/v1/openai` | Whisper Large v3 Turbo → `openai/whisper-large-v3-turbo` · Whisper Large v3 → `openai/whisper-large-v3` | — (Freitext) | `sttPath=https://api.deepinfra.com/v1/audio/transcriptions` |
| OpenRouter (`openrouter`) | `https://openrouter.ai/api/v1` | Voxtral Mini Transcribe → `mistralai/voxtral-mini-transcribe` · GPT-4o mini Transcribe → `openai/gpt-4o-mini-transcribe` · Whisper Large v3 Turbo → `openai/whisper-large-v3-turbo` | GPT-4o mini → `openai/gpt-4o-mini` · Gemini 2.5 Flash-Lite → `google/gemini-2.5-flash-lite` · Claude Haiku 4.5 → `anthropic/claude-haiku-4.5` · Mistral Small 4 → `mistralai/mistral-small-2603` | `sttSendsPrompt=false`; 60-s-Timeout |
| Anthropic (`anthropic`, nur LLM) | `https://api.anthropic.com/v1` | — | Claude Haiku 4.5 → `claude-haiku-4-5` · Claude Sonnet 5 → `claude-sonnet-5` | Hinweis `text_anthropic_note` |
| Google Gemini (`gemini`, nur LLM) | `https://generativelanguage.googleapis.com/v1beta/openai` | — | Gemini 2.5 Flash-Lite → `gemini-2.5-flash-lite` · Gemini 2.5 Flash → `gemini-2.5-flash` | Warn-Chip `text_gemini_warning`; `reasoning_effort=none` |
| DeepSeek (`deepseek`, nur LLM) | `https://api.deepseek.com` | — | DeepSeek V4 Flash → `deepseek-v4-flash` | Warn-Chip `text_deepseek_warning` |
| Eigener Server (`custom`) | frei | Freitext (Placeholder `whisper-1`) | Freitext (Placeholder `qwen3:8b`) | Key optional; `http://` nur private Hosts; Read-Timeout 600 s |

Key-URLs (B1/E5): OpenAI `https://platform.openai.com/api-keys` · Groq `https://console.groq.com/keys` · Mistral `https://console.mistral.ai/api-keys` · Together `https://api.together.ai/settings/api-keys` · DeepInfra `https://deepinfra.com/dash/api_keys` · OpenRouter `https://openrouter.ai/settings/keys` · Anthropic `https://platform.claude.com/settings/keys` · Gemini `https://aistudio.google.com/apikey` · DeepSeek `https://platform.deepseek.com/api_keys`.
Der Katalog wird als Kotlin-Objekt (`ProviderCatalog`) aus dem JSON in `api-providers.md §8` übernommen; Preise erscheinen **nur** im Hilfe-Screen (§6.9), nicht in Dropdowns.

### 8.3 Funktions-Erhalt (alt → neu)

| Bestehend | Neuer Ort |
|---|---|
| `SetupActivity` (Status API/Mikro/IME/Overlay/A11y, Buttons, Bubble-Toggle, `try_field`) | W Schritte 2a/3/4/5/6/7 · H Hero · E3 |
| `SettingsActivity` (`api_url/key/model/prompt`, Sprache, Füllwörter/Groß/Leer, `switch_llm` + `switch_smart_fillers`, `llm_model`) | E1 · E2 (Stufe ersetzt `switch_llm`) |
| `ShareTranscribeActivity` (SEND/SEND_MULTIPLE, Fortschritt Datei/Stück, Teilergebnisse, Abschnitte, Kopieren/Teilen/Retry, `plainText()`, Abbruch) | S (+ Absätze, Füllwort-Schalter, Einzel-Retry, „Einrichten"-Ausweg) |
| `FloatingMicService`/`BubbleUi` (4 Zustände, Timer, Drag/Tap, Cancel-Ziel, Position, Clipboard-Fallback, Notification) | V1/N1 — Logik identisch, Optik/Größe/Feedback neu, Notification-Tap neu |
| `WhisperLoomInputMethodService` (Status, Pegel, Halten, Tasten, Retry, Zahnrad, Subtypes) | V2 |
| `TextPolisher.FILLERS` | B3 Standardwörter (+ `disabledFillers`/`customFillers` in `PolishOptions`) |
| `RefinePrompt.build(german, smartFillers)` | erweitert um `level` (smooth/beautify/summarize) |
| `offline-v1` (`WhisperEngine`, `WhisperModel`, JNI) | E4/Schritt 2b + `ModelStore` (Download, SHA-256, `.part`) — Details `whisper-cpp.md` |
| Manifest | Launcher → `MainActivity`; `ShareTranscribeActivity` `android:icon=@mipmap/ic_share`; `SetupActivity`/`SettingsActivity` entfallen; Download-FGS `dataSync` |

### 8.4 Quellen

- Compose BOM 2026.08.00 / material3 1.4.0: https://developer.android.com/develop/ui/compose/bom/bom-mapping · https://developer.android.com/jetpack/androidx/releases/compose-material3
- Material-Icons-Library nicht mehr empfohlen; XML von fonts.google.com: https://developer.android.com/develop/ui/compose/graphics/images/material · https://fonts.google.com/icons
- M3 in Compose (Farbrollen, Typografie, kein Dynamic Color): https://developer.android.com/develop/ui/compose/designsystems/material3
- M3 Type Scale: https://m3.material.io/styles/typography/type-scale-tokens · Motion: https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
- Adaptive Icons (108/66 dp, Monochrom): https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
- Notification-Small-Icon (Alpha-only): https://documentation.onesignal.com/docs/en/notification-icons
- Touch-Ziele 48 dp: https://github.com/cvs-health/android-compose-accessibility-techniques/blob/main/doc/interactions/MinimumTouchTargetSize.md
- FGS-Typen/Android-15-Regeln: https://developer.android.com/develop/background-work/services/fgs/service-types · https://developer.android.com/about/versions/15/behavior-changes-15
- Provider/Modelle/Preise/Key-URLs: `api-providers.md` (u. a. https://developers.openai.com/api/docs/guides/speech-to-text, https://developers.openai.com/api/docs/pricing, https://console.groq.com/docs/speech-to-text, https://docs.mistral.ai/studio/audio/speech_to_text/offline_transcription, https://openrouter.ai/settings/keys)
- Offline-Modelle (Bytes, SHA-256, RAM): `whisper-cpp.md §5` — https://huggingface.co/ggerganov/whisper.cpp
- Self-Hosting/URL-Validierung: `self-hosted.md §5/§6`
- Kontrastrechnung: `research/contrast_final.py` (WCAG-2-Relative-Luminance), Ergebnis in §3.1
