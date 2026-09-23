# WhisperLoom — Diktieren in jede Android-App

Sprechen statt tippen: WhisperLoom nimmt auf, erkennt den Text und schreibt ihn in das Feld, in dem gerade der Cursor steht — über einen **schwebenden Mikro-Knopf** (die gewohnte Tastatur bleibt), als eigene **Diktat-Tastatur** oder für **Sprachnachrichten** aus WhatsApp & Co. per Teilen-Menü.

Die Erkennung läuft wahlweise **online** über deinen eigenen Zugang bei einem OpenAI-kompatiblen Anbieter (OpenAI, Groq, Mistral, Together AI, DeepInfra, OpenRouter oder ein eigener Server) oder **offline** auf dem Gerät mit whisper.cpp und einem einmalig heruntergeladenen Modell. Eine optionale KI-Textverbesserung glättet, verschönert oder fasst zusammen.

Version **3.5.0** — Nutzer-Anleitung: [docs/ANLEITUNG.md](docs/ANLEITUNG.md) · Änderungen: [CHANGELOG.md](CHANGELOG.md).

Inspiriert von [Wispr Flow](https://wisprflow.ai/) — eigenständige, unabhängige Implementierung.

## Features (3.5.0)

- **Drei Diktat-Wege:** schwebender Mikro-Knopf über allen Apps (Overlay + Bedienungshilfe fügt direkt ins Feld ein, Zwischenablage als Fallback) · Diktat-Tastatur mit Halten-zum-Sprechen und Pegelband · Sprachnachrichten aus WhatsApp/Telegram/Signal per Teilen-Menü abtippen (Absätze, Schalter „Füllwörter ausblenden", mehrere Dateien, lange Aufnahmen gestückelt).
- **Online oder offline:** Anbieter-Katalog mit Modellen, Preisen und Key-Links; Offline-Erkennung mit whisper.cpp v1.9.3 und Modellen Tiny/Base/Small/Large v3 Turbo (Download bei Bedarf mit Fortsetzen und SHA-256-Prüfung, kein Modell im APK).
- **Geführte Einrichtung:** Assistent mit sieben Schritten (Erkennungsweg, Zugang oder Modell, Mikrofon, Über anderen Apps anzeigen, Bedienungshilfe, Benachrichtigungen, Diktat-Tastatur), „Zugang prüfen", Startbildschirm mit Status und Hinweisen.
- **Textverbesserung in Stufen:** Aus · Glätten · Verschönern · Zusammenfassen; „Füllwörter intelligent entfernen"; Schalter „Automatische Absätze" (aus = ein durchgehender Text); getrennter Zugang für die Textverbesserung (auch Anthropic, Google Gemini, DeepSeek, **Ollama lokal/Homeserver** ohne Key und **Ollama Cloud** — Modell-Liste automatisch vom Server, „Eigenes Modell …" für freie Eingabe); lokale Regeln ohne KI (Füllwörter mit bearbeitbarer Liste, Groß-Schreibung, Leerzeichen).
- **Eigener Server:** speaches, whisper.cpp-server, LocalAI; Ollama wahlweise als eigener Anbieter (native API) oder über die `/v1`-Schnittstelle — Key optional, `http://` im privaten Netz, 600-s-Timeout, lesbare Netzfehler.
- **Kein Diktat geht verloren:** Fehlgeschlagene Anfragen bleiben gepuffert, ein Tipp sendet erneut; Ziehen aufs ✕ verwirft.
- **Vokabular** für Eigennamen und Fachbegriffe (online und offline): Liste in der App plus dauerhaft verknüpfte `.md`-/`.txt`-Datei, die bei jedem Diktat neu gelesen wird; Sprachen de/en/es/fr/it/auto.
- Dunkles Material-3-Design (Jetpack Compose), adaptives App-Icon, MIT-Lizenz.

## Schnellstart

1. **APK installieren:** Release von der GitHub-Releases-Seite laden, „Unbekannte Apps installieren" erlauben, öffnen.
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
                 trimSilence → Backend → Refine(LLM, Modus) → TextPolisher                    │
                 Backend = OnlineBackend(ApiTranscriber) | OfflineBackend(WhisperEngine/JNI)   │
                                                                                              │
                 SharedAudioTranscriber: decode → chunks → Backend → Paragrapher → (Filler-Toggle)
Daten: Prefs (SharedPreferences) · ProviderCatalog (Kotlin-Objekte) · ModelCatalog/ModelStore (filesDir/models)
Dienste: FloatingMicService (FGS microphone) · ModelDownloadService (FGS dataSync) · TextInserterAccessibilityService
```

Grundsätze: reine Logik in Android-freien Kotlin-Objekten (JVM-testbar), Compose nur in `ui/`, klassische Views nur in IME und Overlay. Bewusst **ohne** ViewModel-, Navigation- oder DI-Bibliothek, ohne HTTP-Client-Bibliothek (`HttpURLConnection`) und ohne `material-icons-*` (Icons als eigene Vektor-XML).

| Schicht | Paket / Dateien (`app/src/main/java/com/chris/whisperloom/`) |
|---|---|
| Oberfläche | `ui/` — Compose (Material 3, festes dunkles Theme): `MainActivity` mit State-Navigation; Screens Home, Einrichtungs-Assistent, Einstellungen (Erkennung, Text, Knopf & Tastatur, Offline-Modelle, Anleitung & Hilfe, Über); Farb-Tokens in `ui/theme/Color.kt`, `ui/theme/Theme.kt` (eine Farbwahrheit: `res/values/colors.xml`, Präfix `loom_`) |
| Online-Erkennung / LLM | `api/` — `ProviderCatalog` (Anbieter, Modelle, Flags), `ApiAccess` + `AccessResolver` (getrennte STT-/LLM-Zugänge), `Http` (Bearer nur bei Key, Read-Timeout, GET, eigener User-Agent `WhisperLoom/<Version> (Android)`), `OllamaApi` (native Ollama-API: `POST /api/chat`, Modell-Liste `/api/tags`), `ApiErrors` (lesbare Netz-/Statusfehler, `isRetryable`), `TranscriptionRequest` (Multipart-Felder je Anbieter, `languages[]` bei GPT Transcribe), `ApiTranscriber`, `WavUpload`, `ChatPayload` (`temperature` vs. `reasoning_effort`), `RefinePrompt` (Modi Glätten/Verschönern/Zusammenfassen/Absätze), `TextRefiner`, `ServerUrlCheck` (private Hosts, http-Regeln) |
| Offline-Erkennung | `whisper/` — `WhisperLib` (JNI-Bindings), `WhisperContext` (ein nativer Kontext, Single-Thread), `WhisperEngine` (prozessweit, Modellwechsel, Freigabe bei Speicherdruck), `OfflineBackend`, `OfflineSupport` (CPU-Guard fphp+asimddp, Performance-Kerne, RAM), `ModelCatalog` (Datei, Bytes, SHA-256), `ModelStore` (`filesDir/models`, `.part`), `ModelDownloader` (Range-Resume, SHA-256 streamend, Retry), `ModelDownloads` (StateFlow), `ModelDownloadService` (Foreground-Service `dataSync`); nativ: `app/src/main/cpp/CMakeLists.txt`, `whisper_jni.cpp`; Submodul `whisper.cpp` @ v1.9.3 |
| Schwebender Knopf | `overlay/` — `FloatingMicService` (Overlay, Drag/Tap, Retry-Puffer, Clipboard-Fallback), `BubbleUi`/`BubbleVisuals`/`BubbleMotion` (Zustände, Timer, Motion — reine Logik), `BubbleRenderer`, `BubbleAnimators`, `MicViews`, `CancelTarget` (Abbrechen-Ziel mit Scrim), `BubblePosition` (Clamping, Magnet-Radius), `BubbleNotification` |
| Diktat-Tastatur | `ime/` — `WhisperLoomInputMethodService`, `LevelBand` + `LevelBandView` (21-Balken-Pegel), `ImeMetrics` |
| Text einfügen | `a11y/` — `TextInserterAccessibilityService`, `TextInsertion` (Cursor/Auswahl, leeres Feld) |
| Pipeline & Audio | `TranscriptionEngine` (trimSilence → Backend → Refine → Polish; `SharedAudioTranscriber` für geteilte Audios), `TranscriptionBackend` (`OnlineBackend`), `AudioRecorder`, `AudioUtils`, `WavEncoder`, `AudioDecoder` (MediaCodec → 16 kHz Mono), `AudioConvert`, `AudioChunks` (5-Minuten-Stücke an Sprechpausen), `Formats` |
| Textveredelung | `TextPolisher` + `PolishPlan` (Füllwörter eingebaut/eigene/abgewählte, Groß-Schreibung, Whitespace), `Paragrapher` (Absatz-Heuristik), `Vocabulary` (eigene Begriffe, Datei-Parser, Kappung auf 800 Zeichen) + `VocabularySource` (verknüpfte Datei, bei jedem Diktat neu gelesen) |
| Daten & Start | `Prefs` (SharedPreferences, Migration v2 → v3), `SetupState` (Zugang vollständig?; „eingerichtet?" entscheidet `ui/nav/SetupRouter`), `AppNav` (Deep-Link-Intents route/step), `WhisperLoomApplication` (Application: Engine-Init, `onTrimMemory`), `ShareTranscribeActivity` (Teilen-Ziel) |

Weitere Unterlagen: [docs/design/ux-spec-v3.md](docs/design/ux-spec-v3.md) (verbindliche UX-Spezifikation der v3-Oberfläche) und [docs/research/](docs/research/README.md) (Recherche-Reports zu Compose-Stack, Anbietern, whisper.cpp und Self-Hosting).

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

Zielplattform: compileSdk 37, targetSdk 35, minSdk 26. Compose BOM 2026.08.00 (ui 1.12.0, material3 1.4.0), activity-compose 1.13.0, lifecycle-runtime-compose 2.11.0.

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

### CI (GitHub Actions)

`.github/workflows/build.yml` läuft bei jedem Push und Pull Request:

1. Checkout mit Submodulen, JDK 17, Gradle-Cache, Android-SDK; `sdkmanager` installiert NDK 28.2.13676358, CMake 3.22.1, Build-Tools 36.0.0 und Platform 37.
2. CMake-Zwischenstand (`app/.cxx`) wird gecacht (Key: Submodul-Commit + `cpp/**` + `build.gradle.kts`).
3. `tools/check_jni_symbols.py`, dann `testDebugUnitTest lintDebug`, `assembleDebug`.
4. Signiertes `assembleRelease` mit dem Keystore aus den Secrets.
5. Prüfung des Release-APKs: `lib/arm64-v8a/libwhisperloom.so` vorhanden, **kein** `assets/*.bin` (Modelle kommen nur per Download), keine anderen ABIs, `zipalign -P 16` und `llvm-readelf` bestätigen 16-KB-Alignment aller `LOAD`-Segmente.
6. Artefakte: `whisperloom-debug-apk`, `whisperloom-release-apk`, `whisperloom-release-mapping` (R8-`mapping.txt` zum Entschlüsseln von Stacktraces), `unit-and-lint-reports`.

### Release-Signierung

Ein persistenter PKCS12-Keystore liegt als GitHub-Secrets `WHISPERLOOM_KEYSTORE_B64` (Base64) und `WHISPERLOOM_KEYSTORE_PASSWORD` (nicht im Repo). Die CI dekodiert ihn und signiert `assembleRelease`; derselbe Schlüssel signiert jedes Release, damit Updates über die installierte Version gehen. Für lokale Release-Builds: Keystore unter `keystore/whisperloom-release.p12` ablegen (per `.gitignore` ausgeschlossen) und `WHISPERLOOM_KEYSTORE`, `WHISPERLOOM_KEYSTORE_PASSWORD`, `WHISPERLOOM_KEY_ALIAS` (Standard `whisperloom`) als Umgebungsvariablen setzen — fehlt der Keystore, bleibt das Release-APK unsigniert. Release-Builds laufen mit R8 (Full Mode) und Resource-Shrinking; einzige Keep-Regel: `com.chris.whisperloom.whisper.WhisperLib` (JNI-Symbole).

## Tests

Alle Tests laufen ohne Gerät und ohne Emulator (`./gradlew testDebugUnitTest`): reine JVM-Tests für die Logik, Robolectric (SDK 35) für alles, was Android-Ressourcen, `org.json`, SharedPreferences oder Layout-Inflation braucht. HTTP-Pfade werden gegen einen lokalen JDK-`HttpServer` getestet. Stand 3.5.0: 81 Testklassen in 77 Dateien, 695 `@Test`-Methoden.

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
| `PrefsTest` | Migration v2 → v3 (einmalig), neue Schlüssel (Vokabular-Datei leer, Absatz-Schalter ab Werk an), Timeout-Grenzen, StringSets, Flags |
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
| `api/RefinePromptTest` | Anweisungen je Modus (DE/EN), smartFillers, nie übersetzen oder erfinden; Absatz-Schalter (Standard = bisheriger Wortlaut, aus = Fließtext, Absatz-Modus ignoriert ihn) |
| `api/ServerUrlCheckTest` | Private/öffentliche Hosts, http-Regeln je Anbieter, `/v1`-Hinweis (nicht bei Ollama im Heimnetz), Ollama Cloud nur https, ungültige Eingaben |
| `api/TranscriptionRequestTest` | Multipart-Felder je Anbieter: `languages[]`, `prompt`, `response_format`, `auto`, Pfad-Override |
| `api/WavUploadTest` | Upload → Samples (Diktat und gestreamtes Stück) |
| `ime/ImeMetricsTest` | Tastenhöhe ab fontScale 1,3, Level der Mikro-Taste |
| `ime/KeyboardLayoutTest` | `keyboard_view.xml` inflatet, IDs, keine Emoji, contentDescriptions, vier Zustände, Pegelband |
| `ime/LevelBandTest` | 21 Balken, Attack 50 ms / Release 250 ms, Wertebereich |
| `overlay/BubbleMotionTest` | Motion-Dauern und -Kurven, Shake, Reduce-Motion, Haptik je API |
| `overlay/BubbleNotificationTest` | Silhouetten-Icon, Farbe je Zustand, Beenden-Aktion, Tipp → Home |
| `overlay/BubblePositionTest`, `overlay/CancelTargetTest` | Clamping am Bildschirmrand, Maße nach Spec, Magnet-Radius des Abbrechen-Ziels |
| `overlay/BubbleUiTest` | Timer „● m:ss", Blinken mit 1 Hz |
| `overlay/BubbleVisualsTest` | Die vier Zustände unterscheiden sich in Füllung, Ring, Icon und Label |
| `overlay/OverlayLayoutsTest` | `floating_mic`/`floating_cancel` inflaten, Renderer zeichnet jeden Zustand |
| `ui/MainFlowTest` | Oberflächen-Abläufe, u. a. Vokabular-Sheet (Liste, Zeile mit Anzahl und Datei), Absatz-Schalter (an, wirkt nur mit Stufe), Ollama-Masken (Server-Adresse, Modell-Auswahl mit freier Eingabe) |
| `ui/theme/WhisperLoomThemeTest` | Compose-Smoke, Spec-Tokens im Farbschema, Palette == `colors.xml` |
| `whisper/DownloadStateTest` | Prozent-Rechnung, Zustands-Map je Modell |
| `whisper/JniSymbolsTest` | `external fun` ↔ `JNIEXPORT`-Symbole (Name, Präfix, Parameterzahl), kein Asset-Loader |
| `whisper/ModelCatalogTest` | Bytes/SHA-256/URLs der vier Modelle, Small = Default und Empfehlung |
| `whisper/ModelDownloadServiceTest` | Intents, Sofort-Stopp bei unbekannter ID, Fehlertexte |
| `whisper/ModelDownloaderTest` | Kompletter Download, Resume (206), Server ohne Range, Checksum-Mismatch, Cancel, Netzabbruch + Retry, 404/503, Speicherplatz |
| `whisper/ModelStoreTest` | `.part`-Konvention, installierte Modelle, Löschen, belegter Platz |
| `whisper/OfflineSupportTest` | CPU-Features aus `/proc/cpuinfo`, Performance-Kerne, RAM-Toleranz für Large |

Nicht durch Tests abgedeckt und nur auf dem Gerät prüfbar: Overlay-/IME-Darstellung und Animationen, Offline-Laufzeit und -Qualität, das Live-Verhalten der einzelnen Anbieter.

## Versionen

Aktuell **3.5.0** (2026-09-24). Alle Änderungen seit 1.0 im [CHANGELOG.md](CHANGELOG.md). Der letzte Stand der ersten Offline-Generation (Modell im APK) liegt als Tag `offline-v1` im Repo.

## Lizenz / Credits

- WhisperLoom: **MIT** (siehe [LICENSE](LICENSE)).
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) — Offline-Erkennung; Modelle aus [huggingface.co/ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp).
- Jetpack Compose / AndroidX (Apache 2.0).
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).
- Inspiration: Wispr Flow — eigenständige, unabhängige Implementierung.
