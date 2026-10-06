# WhisperLoom — Diktieren in jede Android-App

Sprechen statt tippen: WhisperLoom nimmt auf, erkennt den Text und schreibt ihn in das Feld, in dem gerade der Cursor steht — über einen **schwebenden Mikro-Knopf** (die gewohnte Tastatur bleibt), als eigene **Diktat-Tastatur** oder für **Sprachnachrichten** aus WhatsApp & Co. per Teilen-Menü.

Die Erkennung läuft wahlweise **online** über deinen eigenen Zugang bei einem Anbieter (OpenAI, Groq, Mistral, ElevenLabs, Together AI, DeepInfra, OpenRouter oder ein eigener OpenAI-kompatibler Server) oder **offline** auf dem Gerät mit whisper.cpp und einem einmalig heruntergeladenen Modell. Eine optionale KI-Textverbesserung glättet, verschönert oder fasst zusammen — online oder, bei Offline-Erkennung, mit einem **lokalen Textmodell** (Gemma 4) direkt auf dem Gerät.

Version **3.9.0** — Nutzer-Anleitung: [docs/ANLEITUNG.md](docs/ANLEITUNG.md) · Änderungen: [CHANGELOG.md](CHANGELOG.md) (in der App: **?** neben der Version).

Inspiriert von [Wispr Flow](https://wisprflow.ai/) — eigenständige, unabhängige Implementierung.

## Features (3.9.0)

- **Drei Diktat-Wege:** schwebender Mikro-Knopf über allen Apps (Overlay + Bedienungshilfe fügt direkt ins Feld ein, Zwischenablage als Fallback) · Diktat-Tastatur mit Halten-zum-Sprechen, Wisch-Geste (rechts feststellen, links verwerfen), Zauberstab für die Textstufen und Pegelband · Sprachnachrichten aus WhatsApp/Telegram/Signal per Teilen-Menü abtippen (Absätze, Schalter „Füllwörter ausblenden", mehrere Dateien, lange Aufnahmen gestückelt, eigene KI-Stufe Glätten/Verschönern/Zusammenfassen wählbar).
- **Pro Widgets** (für Entwickler, ab Werk aus, freischaltbar unter Einstellungen → **Erweitert**, braucht eigenen Server): **Sprach-Command-Widgets** schicken ein Diktat an deinen eigenen Agenten. Jedes Widget hat seinen eigenen Namen (steht unter der Kachel, abschaltbar), eigenen Server mit Token und „Verbindung prüfen", Symbol oder Galerie-Bild und Auto-Stopp nach Sprechpause; frei skalierbar 1 × 1 bis 4 × 2, verwaltet unter Einstellungen → Widgets → Tab „Pro Widgets". Gegenstelle auf dem eigenen Server: [hermes-bridge](https://github.com/CTreitges/hermes-bridge). Normale Widgets folgen.
- **Online oder offline:** Anbieter-Katalog (Stand 2026-09-30) mit Modellen, Preisen und Key-Links — neu **ElevenLabs Scribe v2** (und v2 Medical, 90+ Sprachen; Vokabular geht als Schlüsselbegriffe, ca. 20 % Aufpreis; reine Erkennung, Textverbesserung dann über einen eigenen Zugang); Offline-Erkennung mit whisper.cpp v1.9.3 und Modellen Tiny/Base/Small/Large v3 Turbo (Download bei Bedarf mit Fortsetzen und SHA-256-Prüfung, kein Modell im APK).
- **Lokale Textverbesserung offline** (neu in 3.9.0): Gemma 4 E2B (2,6 GB, ab 6 GB RAM, empfohlen) oder E4B (3,7 GB, ab 8 GB RAM) über LiteRT-LM auf der CPU — dieselben Stufen und Anweisungen wie online, ohne Netz, der Text verlässt das Gerät nicht. Download von huggingface.co/litert-community (gepinnte Revision, SHA-256, Platzprüfung inkl. Zwischenspeicher von 0,8 bzw. 1,1 GB). Regel „Textverbesserung bei Offline-Erkennung“: **Lokales Textmodell** (Standard) · **Online, ohne Netz lokal** · **Überspringen**; fehlt das Modell, kommt der Text sofort ohne KI und eine Pflichtkarte bietet „Textmodell laden“ oder „Überspringen“ (Erkennung, Offline-Modelle, Text, Assistent; Banner auf dem Startbildschirm). Netz-Vorprüfung vor jeder Online-Textverbesserung (kein Warten ohne Netz) und ein Ausweg während der Verbesserung: ein Tipp fügt den Text sofort ohne KI ein.
- **Modelle vom Server** (Pro-Funktion unter „Erweitert", ab Werk aus): lädt bei jedem Anbieter die aktuelle Modell-Liste für Erkennung und Textverbesserung — Auswahl mit Suche, „Empfohlen" (Katalog) und „Vom Server · Stand …", Knopf „Modelle aktualisieren", stilles Nachladen, wenn die Liste älter als einen Tag ist; Listen ohne Key auf dem Gerät. Für Modelle ohne Katalog-Eintrag leitet WhisperLoom die Parameter ab (kein `temperature` bei Reasoning-Modellen, notfalls ein zweiter Versuch ohne).
- **Geführte Einrichtung:** bebilderter Assistent mit sieben Schritten (Erkennungsweg, Zugang oder Modell, Mikrofon, Über anderen Apps anzeigen, Bedienungshilfe, Benachrichtigungen, Diktat-Tastatur), „Zugang prüfen", Startbildschirm mit Status und Hinweisen; Einstellungen in vier Gruppen (Grundlagen · Bedienung · Pro · Info), Anleitung & Hilfe mit acht bebilderten Abschnitten.
- **Textverbesserung in Stufen:** Aus · Glätten · Verschönern · Zusammenfassen, Schalter **„Lesbarer glätten"** (Glätten repariert auch Satzabbrüche, Versprecher und Bandwurmsätze, Wortwahl und Ton bleiben), das Diktat geht in jeder Stufe markiert an die KI (diktierte Fragen und Bitten werden bearbeitet, nicht beantwortet), dazu **Prompt** (Pro-Funktion unter „Erweitert": formt ein Diktat zu einem gegliederten Prompt für KI-Assistenten); „Füllwörter intelligent entfernen"; Schalter „Automatische Absätze" (aus = ein durchgehender Text); getrennter Zugang für die Textverbesserung (auch Anthropic, Google Gemini, DeepSeek, **Ollama lokal/Homeserver** ohne Key und **Ollama Cloud** — Modell-Liste automatisch vom Server, „Eigenes Modell …" für freie Eingabe); lokale Regeln ohne KI (Füllwörter mit bearbeitbarer Liste, Groß-Schreibung, Leerzeichen).
- **Eigener Server:** speaches, whisper.cpp-server, LocalAI; Ollama wahlweise als eigener Anbieter (native API) oder über die `/v1`-Schnittstelle — Key optional, `http://` im privaten Netz, 600-s-Timeout, lesbare Netzfehler.
- **Kein Diktat geht verloren:** Fehlgeschlagene Anfragen bleiben gepuffert, ein Tipp sendet erneut; Ziehen aufs ✕ verwirft.
- **Vokabular** für Eigennamen und Fachbegriffe (online und offline): Liste in der App plus dauerhaft verknüpfte `.md`-/`.txt`-Datei, die bei jedem Diktat neu gelesen wird; Sprachen de/en/es/fr/it/auto.
- **Patchnotes in der App:** das **?** neben der Versionsnummer (Startbildschirm, Anleitung & Hilfe, Über WhisperLoom) zeigt das Wichtigste der aktuellen Version, Details zum Aufklappen und frühere Versionen — gelesen aus `CHANGELOG.md` und den Fastlane-Highlights (siehe unten).
- Dunkles Material-3-Design (Jetpack Compose), adaptives App-Icon, MIT-Lizenz.

## Schnellstart

1. **APK installieren:** Release von der [GitHub-Releases-Seite](https://github.com/CTreitges/whisperloom-android/releases) laden, „Unbekannte Apps installieren" erlauben, öffnen — oder über F-Droid (siehe unten), dann kommen Updates automatisch.
2. **Einrichtung durchlaufen:** Erkennungsweg wählen — *Online-Dienst* (API-Key eintragen, z. B. kostenlos bei Groq) oder *Offline auf dem Gerät* (Modell „Small", 190 MB, laden) — dann Mikrofon, „Über anderen Apps anzeigen" und Bedienungshilfe erlauben.
3. **Diktieren:** „Knopf starten & los" → in einer beliebigen App den Knopf antippen, sprechen, nochmal antippen. Der Text steht im Feld.

Alles Weitere — Anbieter und Keys, Textverbesserung, Offline-Modelle, eigener Server, Datenschutz, Fehlerbehebung — in [docs/ANLEITUNG.md](docs/ANLEITUNG.md).


### Installation über F-Droid

Eigenes Repository (signiert mit dem Release-Key): `https://ctreitges.de/fdroid/repo` — Fingerprint `f07ab6293f13c3d637aaa24eb048f2df9bc55013fa3d92e065d72e4b00fe89d0`. Auf dem Handy: https://ctreitges.de/fdroid/ öffnen und „Repo mit einem Tipp hinzufügen". Details in der [Anleitung](docs/ANLEITUNG.md).

## Architektur

```
UI (Compose, MainActivity + State-Navigation)      IME (Views)      Overlay (Views)      Share (Compose)
        │                                              │                 │                    │
        └──────────────── TranscriptionEngine (Pipeline) ────────────────┘                    │
                 trimSilence → Backend → Refine(RefinePlan, Modus) → TextPolisher             │
                 Backend = OnlineBackend(ApiTranscriber) | OfflineBackend(WhisperEngine/JNI)   │
                 RefinePlan = online (TextRefiner) | lokal (LocalTextEngine) | ohne KI        │
                                                                                              │
                 SharedAudioTranscriber: decode → chunks → Backend → Paragrapher → (Filler-Toggle)
Daten: Prefs (SharedPreferences) · ProviderCatalog (Kotlin-Objekte) · ModelCatalog/TextModelCatalog/ModelStore (filesDir/models)
Dienste: FloatingMicService (FGS microphone) · ModelDownloadService (FGS dataSync) · TextInserterAccessibilityService
```

Grundsätze: reine Logik in Android-freien Kotlin-Objekten (JVM-testbar), Compose nur in `ui/`, klassische Views nur in IME und Overlay. Bewusst **ohne** ViewModel-, Navigation- oder DI-Bibliothek, ohne HTTP-Client-Bibliothek (`HttpURLConnection`) und ohne `material-icons-*` (Icons als eigene Vektor-XML).

| Schicht | Paket / Dateien (`app/src/main/java/com/chris/whisperloom/`) |
|---|---|
| Oberfläche | `ui/` — Compose (Material 3, festes dunkles Theme): `MainActivity` mit State-Navigation; Screens Home, Einrichtungs-Assistent, Einstellungen in vier Gruppen (Erkennung, Offline-Modelle, Text · Knopf & Tastatur, Widgets · Erweitert · Anleitung & Hilfe, Über), Tutorials, Patchnotes (`ui/patchnotes/`: Parser, Loader, Screen); Modell-Auswahl mit Suche in `ui/access/ModelPickerSheet.kt`; Farb-Tokens in `ui/theme/Color.kt`, `ui/theme/Theme.kt` (eine Farbwahrheit: `res/values/colors.xml`, Präfix `loom_`) |
| Online-Erkennung / LLM | `api/` — `ProviderCatalog` (Anbieter, Modelle, Flags), `ApiAccess` + `AccessResolver` (getrennte STT-/LLM-Zugänge), `Http` (Bearer nur bei Key, Read-Timeout, GET, eigener User-Agent `WhisperLoom/<Version> (Android)`), `OllamaApi` (native Ollama-API: `POST /api/chat`, Modell-Liste `/api/tags`), `ElevenLabsStt` (ElevenLabs Speech-to-Text: `xi-api-key`, `model_id`, keyterms, rohes PCM), `ModelLists` (Modell-Listen je Anbieter laden und filtern, Flags für Nicht-Katalog-Modelle), `ApiErrors` (lesbare Netz-/Statusfehler, `isRetryable`), `TranscriptionRequest` (Multipart-Felder je Anbieter, `languages[]` bei GPT Transcribe), `ApiTranscriber`, `WavUpload`, `ChatPayload` (`temperature` vs. `reasoning_effort`), `RefinePrompt` (Modi Glätten/Lesbar/Verschönern/Zusammenfassen/Absätze/Prompt, Diktat in `<diktat>`-Markierung), `TextRefiner`, `ServerUrlCheck` (private Hosts, http-Regeln), `NetworkCheck` (Netz-Vorprüfung vor der Online-Textverbesserung: `NET_CAPABILITY_VALIDATED`, im eigenen Netz reicht ein aktives Netz) |
| Offline-Erkennung | `whisper/` — `WhisperLib` (JNI-Bindings), `WhisperContext` (ein nativer Kontext, Single-Thread), `WhisperEngine` (prozessweit, Modellwechsel, Freigabe bei Speicherdruck), `OfflineBackend`, `OfflineSupport` (CPU-Guard fphp+asimddp, Performance-Kerne, RAM), `ModelCatalog` (Whisper-Modelle) und `TextModelCatalog` (Gemma 4 E2B/E4B) als `OfflineModel` (URL, Bytes, SHA-256, RAM-Grenze, `extraDiskBytes` für den Cache), `ModelStore` (`filesDir/models`, `.part`, Cache `models/llm-cache/<id>`), `ModelDownloader` (Range-Resume, SHA-256 streamend, Retry), `ModelDownloads` (StateFlow), `ModelDownloadService` (Foreground-Service `dataSync`); nativ: `app/src/main/cpp/CMakeLists.txt`, `whisper_jni.cpp`; Submodul `whisper.cpp` @ v1.9.3 |
| Lokales Textmodell | `llm/` — `LocalTextModel` (Interface, Fake in Tests) + `LiteRtTextModel` (LiteRT-LM 0.16.1, `Backend.CPU`, `maxNumTokens` 4096, eine Conversation je Auftrag, Abbruch per `cancelProcess`), `LocalTextEngine` (prozessweit, ein Lock für Laden/Rechnen/Freigeben, Vorwärmen beim Aufnahmestart, Freigabe nach 2 min Leerlauf, bei Speicherdruck, Löschen und Modellwechsel), `LocalRefiner` (derselbe Prompt und dieselbe Nacharbeit wie `TextRefiner`) |
| Schwebender Knopf | `overlay/` — `FloatingMicService` (Overlay, Drag/Tap, Retry-Puffer, Clipboard-Fallback), `BubbleUi`/`BubbleVisuals`/`BubbleMotion` (Zustände, Timer, Motion — reine Logik), `BubbleRenderer`, `BubbleAnimators`, `MicViews`, `CancelTarget` (Abbrechen-Ziel mit Scrim), `BubblePosition` (Clamping, Magnet-Radius), `BubbleNotification` |
| Diktat-Tastatur | `ime/` — `WhisperLoomInputMethodService`, `LevelBand` + `LevelBandView` (21-Balken-Pegel), `DictationGesture` + `GestureTargets` (Wisch-Geste: feststellen/verwerfen), `RefineBar` (Zauberstab mit den Textstufen), `ImeMetrics` |
| Text einfügen | `a11y/` — `TextInserterAccessibilityService`, `TextInsertion` (Cursor/Auswahl, leeres Feld) |
| Pro Widgets | `agent/` — `WidgetKind`/`Tier` (Widget-Typen je Stufe), `WidgetProfile` + `WidgetProfileStore` (Name, Symbol, Auto-Stopp, Server je Widget; einmalige Übernahme des früheren globalen Servers), `WidgetIcons` + `WidgetPhoto` (eingebaute Symbole, Galerie-Bild), `VoiceTaskWidget` + `VoiceTaskWidgetView` + `WidgetLayouts` (RemoteViews, Ruhezustand je Widget, Name unter der Kachel), `VoiceTaskTrampolineActivity` (Tipp-Ziele), `VoiceTaskService` (Aufnahme, FGS microphone), `VoiceTaskWorker` + `VoiceTaskPipeline` + `VoiceTaskStore` (WorkManager-Auftrag mit Profil-Id, Retry), `AgentBridge` (Bridge-HTTP), `AutoStopDetector`, `WidgetConfigActivity` (Profilwahl beim Platzieren); Freischalten über `ProFeature` in „Erweitert" |
| Pipeline & Audio | `TranscriptionEngine` (trimSilence → Backend → Refine → Polish; `SharedAudioTranscriber` für geteilte Audios), `RefineDecision` (Regel „Textverbesserung bei Offline-Erkennung“ und Entscheidungstabelle als reine Funktion: online, lokal, online mit lokaler Ausweichlösung oder ohne KI; Pflichtkarte `localModelMissing`), `RefinePlan` (sammelt Regel, Zugang, Netz und Modell, führt die Route aus, Connect-Timeout 8 s mit Ausweichlösung), `RefineSkip` (Ausweg: Tipp fügt ohne KI ein), `SharedRefine` (eigene KI-Stufe je 5-Minuten-Stück geteilter Audios), `TranscriptionBackend` (`OnlineBackend`), `AudioRecorder`, `AudioUtils`, `WavEncoder`, `AudioDecoder` (MediaCodec → 16 kHz Mono), `AudioConvert`, `AudioChunks` (5-Minuten-Stücke an Sprechpausen), `Formats` |
| Textveredelung | `TextPolisher` + `PolishPlan` (Füllwörter eingebaut/eigene/abgewählte, Groß-Schreibung, Whitespace), `Paragrapher` (Absatz-Heuristik), `Vocabulary` (eigene Begriffe, Datei-Parser, Kappung auf 800 Zeichen) + `VocabularySource` (verknüpfte Datei, bei jedem Diktat neu gelesen) |
| Daten & Start | `Prefs` (SharedPreferences, Migration v2 → v3), `ModelCache` (geladene Modell-Listen in eigener Datei `whisperloom_models`, ohne Keys), `SetupState` (Zugang vollständig?; „eingerichtet?" entscheidet `ui/nav/SetupRouter`), `AppNav` (Deep-Link-Intents route/step), `WhisperLoomApplication` (Application: Engine-Init, `onTrimMemory`), `ShareTranscribeActivity` (Teilen-Ziel) |

Weitere Unterlagen: [docs/design/ux-spec-v3.md](docs/design/ux-spec-v3.md) (verbindliche UX-Spezifikation der v3-Oberfläche) und [docs/research/](docs/research/README.md) (Recherche-Reports zu Compose-Stack, Anbietern, whisper.cpp und Self-Hosting, Stand 3.0.0), dazu [docs/PLAYSTORE-RELEASE.md](docs/PLAYSTORE-RELEASE.md) (Vorbereitung der Play-Store-Veröffentlichung) und [docs/privacy.html](docs/privacy.html) (Datenschutzerklärung DE/EN, veröffentlicht unter https://ctreitges.de/fdroid/privacy.html).

## Bauen

### Voraussetzungen

| Komponente | Version |
|---|---|
| JDK | 17 |
| Android SDK Platform | 37 (`platforms;android-37.0`) |
| Build-Tools | 36.0.0 |
| NDK | 28.2.13676358 (r28c) — nur für die Offline-Engine |
| CMake | 3.22.1 — nur für die Offline-Engine |
| Gradle / AGP | Wrapper 9.6.1 / 9.4.0 (Kotlin 2.2.10 built-in, Compose-Compiler-Plugin 2.2.10) |

Zielplattform: compileSdk 37, targetSdk 36 (Play-Vorgabe seit 31.08.2026), minSdk 26. Compose BOM 2026.08.00 (ui 1.12.0, material3 1.4.0), activity-compose 1.13.0, lifecycle-runtime-compose 2.11.0, WorkManager 2.11.2 (Pro Widgets), LiteRT-LM 0.16.1 (`com.google.ai.edge.litertlm:litertlm-android`, lokales Textmodell; fest gepinnt, weil 0.17.x Kotlin 2.4 voraussetzt; transitiv Gson 2.13.2 und kotlin-reflect). Durch die native LiteRT-LM-Bibliothek (`liblitertlm_jni.so`, arm64 21,5 MB, unkomprimiert für das 16-KB-Alignment) wächst das Release-APK von etwa 4 MB (3.8.3) auf etwa 26 MB; die Gemma-Modelle sind nicht enthalten. Tests: JUnit 4.13.2, Robolectric 4.16.1, androidx.test core 1.7.0 / ext-junit 1.3.0, work-testing 2.11.2.

### Lokal

```bash
git clone --recurse-submodules https://github.com/CTreitges/whisperloom-android.git     # whisper.cpp kommt als Submodul (v1.9.3)
cd whisperloom-android
./gradlew assembleDebug                        # Debug-APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug          # Tests + Lint
```

Fehlt das Submodul (Clone ohne `--recurse-submodules`): `git submodule update --init --recursive`.

**Ohne NDK bauen** (z. B. auf einem aarch64-Linux-Host, für den es kein NDK gibt):

```bash
./gradlew -Pwhisperloom.skipNative=true assembleDebug
```

Damit entfällt der komplette Native-Build — Kotlin, Tests und ein APK entstehen trotzdem, aber **ohne `libwhisperloom.so`**: Die Offline-Engine meldet auf dem Gerät „nicht unterstützt", der Online-Modus funktioniert. Die Property kann auch dauerhaft in `~/.gradle/gradle.properties` stehen (`whisperloom.skipNative=true`).

Native-Konfiguration (nur ohne `whisperloom.skipNative`): nur `arm64-v8a`, `GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod` mit Laufzeit-Guard in `OfflineSupport`, `c++_static` (eine `.so`), Debug-Buildtyp baut den Native-Teil trotzdem als Release (whisper.cpp PR #3913 — sonst unbrauchbar langsam), 16-KB-Page-Alignment, `debugSymbolLevel = SYMBOL_TABLE` im Release. `tools/check_jni_symbols.py` gleicht die `external fun`-Deklarationen in `WhisperLib.kt` mit den `JNIEXPORT`-Symbolen in `whisper_jni.cpp` ab (derselbe Abgleich läuft als `JniSymbolsTest`).

### Patchnotes: CHANGELOG und Fastlane-Texte sind App-Inhalt

`CHANGELOG.md` und `fastlane/metadata/android/de-DE/changelogs/` sind **App-Inhalt**: Die Task
`copy<Variant>PatchnotesAssets` (`app/build.gradle.kts`) kopiert sie beim Build nach `assets/patchnotes/`, die App
zeigt sie unter dem **?** neben der Versionsnummer (`ui/patchnotes/`). Fehlt eine der Quellen, scheitert der Build.
Ihr Format ist damit ein Vertrag:

- Versionskopf `## [x.y.z] — JJJJ-MM-TT` (`## [Unreleased]` wird nicht angezeigt)
- die bekannten `###`-Kategorien: Hinzugefügt · Geändert · Behoben · Sicherheit · Entfernt · Bekannte Punkte ·
  Technik (Technik wird ausgeblendet; andere Titel erscheinen unverändert)
- Punkte als `- **Lead.** Text`, Unterpunkte zwei Leerzeichen eingerückt; inline nur `**fett**`, `` `Code` `` und
  `[Text](https://…)`
- Highlights: Zeile 1 `WhisperLoom x.y.z` (optional ` – Tagline`), danach `• Punkt` bzw. `• Neu: Punkt`

`PatchnotesAssetsTest` hält den Release ehrlich: Zur aktuellen `versionName` muss `## [x.y.z]` im CHANGELOG stehen und
zum `versionCode` die Highlight-Datei existieren.

### CI (GitHub Actions)

`.github/workflows/build.yml` läuft bei jedem Push und Pull Request sowie manuell (`workflow_dispatch`); ein neuerer Lauf auf demselben Branch bricht den älteren ab:

1. Checkout mit Submodulen, JDK 17, Gradle-Cache, Android-SDK; `sdkmanager` installiert NDK 28.2.13676358, CMake 3.22.1, Build-Tools 36.0.0 und Platform 37.
2. CMake-Zwischenstand (`app/.cxx`) wird gecacht (Key: Submodul-Commit + `cpp/**` + `build.gradle.kts`).
3. `tools/check_jni_symbols.py`, dann `testDebugUnitTest lintDebug`, `assembleDebug`.
4. Signiertes `assembleRelease` mit dem Keystore aus den Secrets.
5. Prüfung des Release-APKs: `lib/arm64-v8a/libwhisperloom.so` vorhanden, **kein** `assets/*.bin` (Modelle kommen nur per Download), keine anderen ABIs, `zipalign -P 16` und `llvm-readelf` bestätigen 16-KB-Alignment aller `LOAD`-Segmente.
6. Signiertes `bundleRelease` (Android App Bundle für den Play Store, gleicher Schlüssel als Upload-Key) mit derselben Prüfung auf Native-Lib, Modelle und ABIs.
7. Artefakte: `whisperloom-debug-apk`, `whisperloom-release-apk`, `whisperloom-release-mapping` (R8-`mapping.txt` zum Entschlüsseln von Stacktraces), `whisperloom-release-aab`, `unit-and-lint-reports`.

### Release-Signierung

Ein persistenter PKCS12-Keystore liegt als GitHub-Secrets `WHISPERLOOM_KEYSTORE_B64` (Base64) und `WHISPERLOOM_KEYSTORE_PASSWORD` (nicht im Repo). Die CI dekodiert ihn und signiert `assembleRelease` und `bundleRelease`; derselbe Schlüssel signiert jedes Release, damit Updates über die installierte Version gehen. Für lokale Release-Builds: Keystore unter `keystore/whisperloom-release.p12` ablegen (per `.gitignore` ausgeschlossen) und `WHISPERLOOM_KEYSTORE`, `WHISPERLOOM_KEYSTORE_PASSWORD`, `WHISPERLOOM_KEY_ALIAS` (Standard `whisperloom`) als Umgebungsvariablen setzen — fehlt der Keystore, bleibt das Release-APK unsigniert. Release-Builds laufen mit R8 (Full Mode) und Resource-Shrinking; Keep-Regeln: `com.chris.whisperloom.whisper.WhisperLib` (JNI-Symbole) und `com.google.ai.edge.litertlm.**` (das AAR bringt keine Consumer-Regeln mit, ohne die Regel stürzt die lokale Textverbesserung im Release ab, LiteRT-LM #3739).

## Tests

Alle Tests laufen ohne Gerät und ohne Emulator (`./gradlew testDebugUnitTest`): reine JVM-Tests für die Logik, Robolectric (meist SDK 35) für alles, was Android-Ressourcen, `org.json`, SharedPreferences oder Layout-Inflation braucht. HTTP-Pfade werden gegen einen lokalen JDK-`HttpServer` getestet. Stand 3.9.0: 127 Testklassen in 126 Dateien, 1508 `@Test`-Methoden. Die Tabelle nennt eine Auswahl:

| Testklasse | Deckt ab |
|---|---|
| `AppNavTest` | Deep-Link-Intents aus Overlay/IME/Notification (`route`, `step`, `NEW_TASK`) |
| `AudioChunksTest` | Stückelung langer Aufnahmen: Schnitt an der leisesten Stelle, nie vor der halben Höchstlänge, keine Winz-Stücke |
| `AudioConvertTest` | Downmix, Resampling, RMS-Profil geteilter Audios |
| `AudioUtilsTest` | Stille-Trimmen |
| `FormatsTest` | Dauer-Formatierung (Timer, Überschriften) |
| `ManifestBackupTest` | `allowBackup=false` + Backup-/Extraktionsregeln schließen die Prefs-Datei (API-Keys) aus; Hauptfenster mit `adjustResize` (Tastatur schiebt das Fenster nicht mehr hoch) |
| `NetworkSecurityConfigTest` | Jeder Cloud-Anbieter des Katalogs (inkl. ollama.com) ist in der https-Pflicht-Liste; Klartext nur global |
| `ParagrapherTest` | Absatz-Heuristik: Satzgrenzen, Diskursmarker, Abkürzungen, Wortlaut bleibt |
| `PolishPlanTest` | Welche Nachbearbeitung greift: Wortliste vs. KI-Entscheidung, verbatim/cleaned; ohne „Automatische Absätze" wird KI-Text zu einem Absatz |
| `PrefsTest` | Migration v2 → v3 (einmalig), neue Schlüssel (Vokabular-Datei leer, Absatz-Schalter ab Werk an; Regel `offline_refine` ab Werk `local` ohne Migration, Textmodell `gemma4_e2b`), Timeout-Grenzen, StringSets, Flags; „wie Erkennung“ nimmt offline nie den alten Online-Zugang |
| `RefineDecisionTest`, `RefinePipelineTest` | Entscheidungstabelle der Textverbesserung bei Offline-Erkennung als komplette Matrix (Regel, eigener Zugang, Netz, Textmodell); end-to-end über `transcribe` und `SharedRefine` mit Fake-Erkennung, Fake-Netz, Fake-Textmodell und einem `HttpServer`, der jede Anfrage zählt: lokal statt online, ohne Netz keine Anfrage, ohne Modell sofort ohne KI mit Hinweis, Ausweichlösung nach Online-Fehler, 8-s-Connect-Timeout, Ausweg-Tipp, Vorwärmen |
| `SetupStateTest` | Transkriptions-Zugang vollständig (URL, Key je nach Anbieter) |
| `TextPolisherTest` | Füllwörter (eingebaut, eigene, abgewählte, mehrwortig), Groß-Schreibung, Whitespace, Zeilenumbrüche |
| `TranscriptionEngineTest` | Backend-Wahl und kompletter Diktat-Pfad gegen einen lokalen „eigenen Server": Multipart-Felder, kein Header ohne Key, Ollama-Body, Politur, Offline-Konfiguration; Diktat mit Ollama lokal/Cloud (Key nur bei der Cloud, Fehler → Rohtext mit Hinweis), Absatz-Schalter an/aus, Vokabular aus Liste + Datei (bei jedem Diktat neu gelesen), verschwundene Datei bricht kein Diktat ab |
| `VocabularyTest` | Eigene Begriffe (Komma/Semikolon, Duplikate, Entfernen), alter Freitext (auch mehrzeilig) bleibt erhalten, Datei-Parser (Markdown-Zeichen, BOM, Windows-Zeilenenden, Duplikate ohne Groß-/Kleinschreibung), eigene Begriffe am Ende (Whisper beachtet das Ende), Kappung von vorn nur zwischen ganzen Begriffen, Tabellen/leere Aufgaben, Windows-1252 |
| `WavEncoderTest`, `WavHeaderTest`, `WavSamplesTest` | WAV-Header und PCM-Kodierung, getrennter Kopf fürs Streaming, Rückweg PCM → Float |
| `a11y/TextInsertionTest` | Einfügen an Cursor/Auswahl, leeres Feld (Hint-Regression), Leerzeichen-Logik |
| `api/AccessResolverTest` | STT-/LLM-Zugänge: Defaults, `same`, nie der STT-Key an einen anderen Anbieter; Ollama Cloud mit Katalog-URL und eigenem Key, Ollama lokal ohne Adresse bleibt leer |
| `api/ApiErrorsTest` | Wiederholbarkeit (Netz, 408/429/5xx) und lesbare Netz-/Status-Meldungen |
| `api/ChatPayloadTest`, `api/ChatPayloadJsonTest` | `temperature` vs. `reasoning_effort` je Modell/Anbieter, JSON-Body, Escaping |
| `api/HttpTest` | Authorization-Header nur mit Key, Fehlerstatus lesbar, Read-Timeout, Verbindung verweigert, URL-Trimmen, eigener User-Agent statt „Dalvik/…", Ollama-Fehlerform lesbar |
| `api/OllamaApiTest` | Native Ollama-API: Server-Wurzel ohne `/api`/`/v1`, `/api/chat`-Payload (ohne `think`, ohne Streaming; gpt-oss `think: low`), Antwort-Parser, Modell-Liste aus `/api/tags` (lokal ohne Key, Cloud mit Key, sortiert, ohne Duplikate), keine Anfrage ohne Adresse, Fehlerform |
| `api/ProviderCatalogTest` | IDs eindeutig, Cloud = https + Key, Defaults existieren, Auslauf-Kennzeichnung, Flags, Dropdown-Reihenfolge; Ollama lokal braucht Adresse, aber keinen Key; Ollama Cloud mit Key und empfohlenem Modell; nur Ollama spricht die native API |
| `api/RefinePromptTest` | Anweisungen je Modus (DE/EN), smartFillers, nie übersetzen oder erfinden; Absatz-Schalter (Standard = bisheriger Wortlaut, aus = Fließtext, Absatz-Modus ignoriert ihn); Stufe „Prompt“ (Diktat markiert, nicht beantworten, Gliederung nach Umfang, Kurz-Grenze) |
| `api/PromptCleanupTest` | Nacharbeit der Stufe „Prompt“: Vorrede/Label/Anführungszeichen weg, Nutzer-Anweisungen bleiben, zu lange Ausgabe → Rohtext |
| `api/ServerUrlCheckTest` | Private/öffentliche Hosts, http-Regeln je Anbieter, `/v1`-Hinweis (nicht bei Ollama im Heimnetz), Ollama Cloud nur https, ungültige Eingaben |
| `api/NetworkCheckTest`, `api/AndroidNetworkCheckTest` | Netz-Vorprüfung: Internet erst mit `NET_CAPABILITY_VALIDATED`, im eigenen Netz (private Hosts, `*.ts.net`) reicht ein aktives Netz |
| `api/TranscriptionRequestTest` | Multipart-Felder je Anbieter: `languages[]`, `prompt`, `response_format`, `auto`, Pfad-Override |
| `api/WavUploadTest` | Upload → Samples (Diktat und gestreamtes Stück) |
| `api/ElevenLabsSttTest` | ElevenLabs-Felder (`xi-api-key`, `model_id`, Sprache, keyterms nach den Anbieter-Regeln: höchstens 100, die letzten gewinnen, Satzstücke fallen weg), die der Anbieter sonst erst live mit 422 ablehnt |
| `api/ModelListsTest`, `api/ModelListsLoadTest`, `api/ModelFlagsTest` | „Modelle vom Server": Adressen und Header je Anbieter, Filterregeln, kein Key-Header nach Weiterleitung zu fremdem Host, Parameter-Flags für Modelle ohne Katalog-Eintrag |
| `api/TextCleanupTest`, `api/TextRefinerRetryTest` | Nacharbeit der KI-Stufen (Markierungen, Vorrede, erfüllte Bitte → Rohtext), genau ein zweiter Versuch ohne `temperature` |
| `SharedRefineTest` | KI-Stufe für geteilte Sprachnachrichten: welche Stufe gilt, was je Stück ans Modell geht, kein KI-Fehler kostet die Nachricht |
| `agent/*` (23 Testklassen) | Pro Widgets: Profile und Server je Widget, Auto-Stopp, Aufnahme-Dienst, WorkManager-Auftrag mit Retry, Bridge-HTTP, Widget-Layouts und -Zustände, Profilwahl beim Platzieren |
| `ime/DictationGestureTest`, `ime/ImeGestureTest`, `ime/ImeWiringTest`, `ime/RefineBarTest` | Wisch-Geste (feststellen, verwerfen), TalkBack-Bedienung der Mikro-Taste, Textstufen-Zeile in der Tastatur |
| `ime/ImeRefineTest`, `overlay/FloatingMicRefineTest` | KI-Stufen offline nach Regel und Textmodell, Hinweis führt zu den Offline-Modellen, Vorwärmen beim Aufnahmestart, Tipp in der Textverbesserung fügt ohne KI ein (Tastatur und Knopf) |
| `ui/access/ModelPickerTest` | Modell-Auswahl mit „Empfohlen" und „Vom Server", „Modelle aktualisieren", stilles Nachladen nur bei veralteter Liste |
| `ime/ImeMetricsTest` | Tastenhöhe ab fontScale 1,3, Level der Mikro-Taste |
| `ime/KeyboardLayoutTest` | `keyboard_view.xml` inflatet, IDs, keine Emoji, contentDescriptions, vier Zustände, Pegelband |
| `ime/LevelBandTest` | 21 Balken, Attack 50 ms / Release 250 ms, Wertebereich |
| `overlay/BubbleMotionTest` | Motion-Dauern und -Kurven, Shake, Reduce-Motion, Haptik je API |
| `overlay/BubbleNotificationTest` | Silhouetten-Icon, Farbe je Zustand, Beenden-Aktion, Tipp → Home |
| `overlay/BubblePositionTest`, `overlay/CancelTargetTest` | Clamping am Bildschirmrand, Maße nach Spec, Magnet-Radius des Abbrechen-Ziels |
| `overlay/BubbleUiTest` | Timer „● m:ss", Blinken mit 1 Hz |
| `overlay/BubbleVisualsTest` | Die vier Zustände unterscheiden sich in Füllung, Ring, Icon und Label |
| `overlay/OverlayLayoutsTest` | `floating_mic`/`floating_cancel` inflaten, Renderer zeichnet jeden Zustand |
| `PatchnotesAssetsTest` | Release-Wächter: CHANGELOG-Eintrag und Highlight-Datei zur aktuellen Version liegen in den Assets |
| `ui/patchnotes/PatchnotesParserTest` | CHANGELOG-/Highlight-Parser: Versionsköpfe, `[Unreleased]`, Kategorien, Lead-Split, Unterpunkte, Inline-Markdown, Teaser; dazu die echten Dateien (historische Fakten) |
| `ui/patchnotes/PatchnotesScreenTest` | Patchnotes-Screen mit den echten Assets: Hero, Aufklappen, Technik ausgeblendet, „Vor Version 3", Überschriften, Fehlerzustand |
| `ui/patchnotes/PatchnotesScreenshotTest` | PNGs der Patchnotes, Home-Fußzeile (360 dp) und des Über-Sheets nach `app/build/reports/screenshots/` (Robolectric-Native-Graphics; auf linux-aarch64 übersprungen) |
| `ui/MainFlowTest` | Oberflächen-Abläufe, u. a. Vokabular-Sheet (Liste, Zeile mit Anzahl und Datei), Absatz-Schalter (an, wirkt nur mit Stufe), Ollama-Masken (Server-Adresse, Modell-Auswahl mit freier Eingabe) |
| `ui/models/TextModelUiTest`, `ui/nav/SystemStatusTest` | Offline-Modelle mit zwei Abschnitten, E4B unter 8 GB gedimmt, Pflichtkarte erscheint/verschwindet (Erkennung, Offline-Modelle, Text, Assistent 2b), Knöpfe und Radio setzen die Regel, Karte „Offline-Erkennung“, Online-Zugang offline, Home-Status und Banner, Hub-Kurzform; installierte Whisper- und Textmodelle getrennt |
| `ui/models/TextModelScreenshotTest` | PNGs der neuen Oberflächen (Offline-Modelle, Pflichtkarte auch bei 360 dp, Karte Offline-Erkennung, Home-Banner, Assistent 2b) nach `app/build/reports/screenshots/` (auf linux-aarch64 übersprungen) |
| `ui/theme/WhisperLoomThemeTest` | Compose-Smoke, Spec-Tokens im Farbschema, Palette == `colors.xml` |
| `whisper/DownloadStateTest` | Prozent-Rechnung, Zustands-Map je Modell |
| `whisper/JniSymbolsTest` | `external fun` ↔ `JNIEXPORT`-Symbole (Name, Präfix, Parameterzahl), kein Asset-Loader |
| `whisper/ModelCatalogTest` | Bytes/SHA-256/URLs der vier Modelle, Small = Default und Empfehlung |
| `whisper/TextModelCatalogTest`, `llm/LocalTextEngineTest`, `llm/LocalRefinerTest` | Gemma 4 E2B/E4B: gepinnte URLs, Bytes, SHA-256, Cache-Zuschlag, RAM-Grenzen 6/8 GB mit Toleranz; Halter mit Fake-Modell (Laden, Wechsel, nie `close` mitten in der Rechnung, Leerlauf, Speicherdruck, Vorwärmen, Abbruch); derselbe Auftrag und dieselbe Nacharbeit wie online |
| `whisper/ModelDownloadServiceTest` | Intents, Sofort-Stopp bei unbekannter ID, Fehlertexte |
| `whisper/ModelDownloaderTest` | Kompletter Download, Resume (206), Server ohne Range, Checksum-Mismatch, Cancel, Netzabbruch + Retry, 404/503, Speicherplatz |
| `whisper/ModelStoreTest` | `.part`-Konvention, installierte Modelle, Löschen, belegter Platz |
| `whisper/OfflineSupportTest` | CPU-Features aus `/proc/cpuinfo`, Performance-Kerne, RAM-Toleranz für Large |

Nicht durch Tests abgedeckt und nur auf dem Gerät prüfbar: Overlay-/IME-Darstellung und Animationen, Offline-Laufzeit und -Qualität, die echte Gemma-Rechnung (LiteRT-LM lädt seine native Bibliothek nicht in der JVM; die Tests nutzen ein Fake-Modell hinter `LocalTextModel`), das Live-Verhalten der einzelnen Anbieter.

## Versionen

Aktuell **3.9.0** (2026-10-06). Alle Änderungen seit 1.0 im [CHANGELOG.md](CHANGELOG.md). Der letzte Stand der ersten Offline-Generation (Modell im APK) liegt als Tag `offline-v1` im Repo.

## Lizenz / Credits

- WhisperLoom: **MIT** (siehe [LICENSE](LICENSE)).
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) — Offline-Erkennung; Modelle aus [huggingface.co/ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp).
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) (Apache 2.0, mit Gson, Apache 2.0) — lokales Textmodell; Gemma-4-Modelle (Apache 2.0) aus [huggingface.co/litert-community](https://huggingface.co/litert-community), nicht im APK.
- Jetpack Compose / AndroidX inkl. WorkManager, Kotlin-Standardbibliothek und kotlinx.coroutines (Apache 2.0).
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).
- Vollständige Liste mit Test-Abhängigkeiten: [THIRD_PARTY.md](THIRD_PARTY.md).
- Inspiration: Wispr Flow — eigenständige, unabhängige Implementierung.
