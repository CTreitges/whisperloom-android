> **Historisch — Recherche-Archiv, Stand 2026-09-06.** Keine gepflegte Referenz: maßgeblich sind Code und [CHANGELOG](../../CHANGELOG.md); Einordnung in der [Übersicht](README.md).

# whisper.cpp On-Device-Erkennung für WhisperLoom — Wiederherstellung technisch abgesichert

Stand der Recherche: 2026-09-06 · Alt-Stand: Tag `offline-v1`
Alle Versions-/Größen-/API-Angaben stammen aus live abgefragten Quellen (GitHub-API, Raw-Dateien, Hugging-Face-API, Google-SDK-Repository-XML, developer.android.com). Unsicheres ist als **[grob]** oder **[unsicher]** markiert.

---

## 0. Kurzfassung

| Thema | Empfehlung |
|---|---|
| whisper.cpp-Tag | **`v1.9.3`** (Commit `371b5a75`, 2026-08-20). API zu `offline-v1` (= exakt `v1.9.1`, Commit `f049fff`) **vollständig kompatibel** — nur Additionen. Achtung: GitHub flaggt v1.9.3 als *Pre-release*; Fallback `v1.9.2` (`306c88f4`, 2026-08-04). |
| NDK | **`28.2.13676358`** (r28c, stable, 16-KB-Alignment per Default). LTS-Alternative `27.3.13750724` (r27d) nur mit Linker-Flags. |
| CMake | **`3.22.1`** (bewährt mit AGP 8.7.3, erfüllt whisper ≥3.5 / ggml ≥3.14 / NDK-r28-Toolchain ≥3.10). Alternativ `3.31.6`. |
| ABI | **nur `arm64-v8a`**, eine `.so`, `GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod`, plus Kotlin-Laufzeit-Guard (`/proc/cpuinfo` muss `fphp` und `asimddp` enthalten, sonst API-Modus). |
| .so-Größe | ~1,5 MB stripped gemessen (fremder Prebuilt, c++_shared) → mit `c++_static` **[grob] 2–2,5 MB**. |
| Decoding | Beam-Search `beam_size=5`, `initial_prompt` = `Prefs.apiPrompt`, `suppress_nst=true`, `suppress_blank=true`, `no_context=true`, `no_timestamps=true`, Sprache fest (`de`) statt `auto`, Temperatur-Fallback an (Defaults), `abort_callback` für Abbruch. |
| Standard-Modell | **`ggml-small-q5_1.bin`** (190 085 487 B, ~430 MB RAM). `base-q5_1` als „schnell“, `large-v3-turbo-q5_0` als „beste Qualität“ nur für ≥6 GB-RAM-Geräte. `tiny` und `medium` nicht anbieten. |
| Download | **Eigener HttpURLConnection-Downloader** (Range-Resume, `.part` + rename, Content-Length + SHA-256 aus HF-Metadaten) in `filesDir/models/`; Ausführung im Foreground-Service `dataSync` (oder WorkManager-`CoroutineWorker` + `setForeground`, sobald AndroidX ohnehin da ist). `DownloadManager` nicht. |
| Lebenszyklus | Ein prozessweiter `whisper_context` (`WhisperEngine`-Singleton), Single-Thread-Executor, Freigabe bei Modellwechsel/`onTrimMemory`; whisper.h: nur „thread-safe as long as the same whisper_context is not used by multiple threads concurrently“. |
| VAD | Eingebautes Silero-VAD (`ggml-silero-v5.1.2.bin`, 885 098 B) für Diktat **nicht** nötig (trimSilence reicht); optional später für lange geteilte Sprachnachrichten. |

---

## 1. Ist-Analyse Alt-Stand (`offline-v1`)

Gelesen via `git show offline-v1:<pfad>`:

- **Submodul** `whisper.cpp` @ `f049fff95a089aa9969deb009cdd4892b3e74916` — das ist **exakt der Release-Commit `v1.9.1`** („release : v1.9.1 (#3892)“, 2026-06-19). Quelle: GitHub-Tags-API `ggml-org/whisper.cpp`.
- **`app/src/main/cpp/CMakeLists.txt`**: `add_subdirectory(whisper.cpp)` mit `WHISPER_BUILD_TESTS/EXAMPLES/SERVER=OFF`, `WHISPER_SDL2/CURL=OFF`, `GGML_OPENMP=OFF`, `GGML_NATIVE=OFF`, `BUILD_SHARED_LIBS=OFF`; Target `whisperloom` (SHARED) linkt `log android whisper`. Solide Grundlage — bleibt.
- **`whisper_jni.cpp`**: Asset-Streaming-Loader (`whisper_model_loader`), `whisper_init_with_params`, `whisper_init_from_file_with_params`, `whisper_context_default_params` (`use_gpu=false`), `whisper_full_default_params(WHISPER_SAMPLING_GREEDY)`, `whisper_full`, `whisper_full_n_segments`, `whisper_full_get_segment_text`, `whisper_print_system_info`. Kein `initial_prompt`, kein Beam-Search, kein Abbruch.
- **`WhisperContext.kt`**: Single-Thread-Executor „whisper-worker“, `threads = availableProcessors().coerceIn(2, 6)`. **`WhisperEngine.kt`**: prozessweites Singleton, lädt bei Modellwechsel neu. **`WhisperModel.kt`**: Enum SMALL/BASE/TINY (q5_1) mit Byte-Größen (stimmen weiterhin exakt mit HF, s. §5), Download ohne Resume, SMALL als APK-Asset gebündelt (190 MB APK).
- **`build.gradle.kts`**: `ndkVersion 27.2.12479018`, CMake `3.22.1`, `abiFilters arm64-v8a + x86_64`, `noCompress += "bin"`, `useLegacyPackaging=false`.
- **CI**: NDK/CMake per `sdkmanager`, Modell-Download + `actions/cache`, Emulator-Instrumented-Test (x86_64, API 30).

Aktueller Stand `v3-redesign`: kein Native-Code, `Transcriber`-Interface (`transcribe(samples: FloatArray, language: String): String`, `release()`), `TranscriptionEngine` (trimSilence → `ApiTranscriber` → optional `TextRefiner` → `TextPolisher`), `Prefs.language` Default `"de"`, `Prefs.apiPrompt` (Kontext-Prompt), `AudioUtils.SAMPLE_RATE = 16_000`. Manifest hat bereits `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`.

---

## 2. Release-Wahl und API-Kompatibilität

### 2.1 Releases (GitHub-API `repos/ggml-org/whisper.cpp/releases|tags`, 2026-09-06)

| Tag | Datum | Commit | Flag |
|---|---|---|---|
| **v1.9.3** | 2026-08-20 | `371b5a7561823ab2bb32142d2751e35e7534727b` | *Pre-release* (GitHub-Flag; Release-Notes: „Semantic versioning is still work in progress“, neuer Release-Prozess #3996) |
| b4938 | 2026-08-20 | — | Nightly-Build |
| v1.9.2 | 2026-08-04 | `306c88f4d1286aec1bf96e544632897886af5501` | stable |
| v1.9.1 | 2026-06-19 | `f049fff95a089aa9969deb009cdd4892b3e74916` | stable (= alter Submodul-Stand) |
| v1.9.0 | 2026-06-17 | `86c40c3b…` | stable |

Quelle: <https://github.com/ggml-org/whisper.cpp/releases>, <https://api.github.com/repos/ggml-org/whisper.cpp/tags>

### 2.2 API-Diff `include/whisper.h` v1.9.1 → v1.9.3

`diff` der Raw-Dateien: **23 Zeilen, ausschließlich Additionen**:
- `whisper_full_get_token_t0/t1[_from_state]` (Token-Zeiten, VAD-korrigiert)
- `whisper_full_n_vad_segments[_from_state]`, `whisper_full_get_vad_segment_t0/t1[_from_state]` (interne VAD-Segmente auslesen)

**Alle im Alt-Stand benutzten Funktionen existieren unverändert** (`whisper_init_with_params` Z.208, `whisper_init_from_file_with_params` Z.206, `whisper_context_default_params` Z.594, `whisper_full_default_params` Z.598, `whisper_full_n_segments` Z.630, `whisper_full_get_segment_text` Z.651, `whisper_print_system_info` Z.450, `whisper_free` Z.268, `whisper_model_loader` Z.153). Kein Feld in `whisper_full_params`/`whisper_context_params` entfernt oder umbenannt.
Quelle: <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/include/whisper.h>

### 2.3 Relevante Änderungen seit v1.9.1

**v1.9.2** (<https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.2>):
- #3913 „Improved inference performance of Android example project“ — reiner Build-Fix: `-DCMAKE_BUILD_TYPE=Release` auch für AGP-Debug-Builds, weil AGP-Debug den Native-Build unoptimiert lässt („causing a critical performance issue“). **Für WhisperLoom übernehmen (§8.3).**
- #3910/#3916 VAD: Token-Zeiten auf Original-Zeitachse, VAD-Segmente exponiert. #3907/#3963 VAD-CLI-Argument-Fixes.
- #3921 „Remove leading space from txt output“ (nur CLI-Textausgabe; `whisper_full_get_segment_text` liefert weiterhin führendes Leerzeichen → `trim()` in Kotlin bleibt nötig).

**v1.9.3** (<https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.3>):
- **#3956 „heap out-of-bounds read in log_mel_spectrogram on very short audio“** — direkt relevant für Diktat (sehr kurze Aufnahmen nach trimSilence).
- #3957 Modell-Loader gegen fehlerhafte Tensor-Header gehärtet (Schutz bei Teildownloads).
- ggml-Sync: „ggml-cpu : fix CPU affinity mask being ignored on Android (llama/26838)“ — Ursache: `#elif defined(__gnu_linux__)` schloss Android aus; Fix auf `__linux__`. Messung im PR: 2×A75+6×A55, Threads auf die großen Kerne gepinnt 22,8 t/s vs. kleine Kerne 11,8 t/s (<https://github.com/ggml-org/llama.cpp/pull/26838>).
- „ggml : add aarch64 HWCAP fallbacks and fix fp16 variant detection (llama/25554)“, „kleidiai: runtime feature detection for aarch64“, ggml-Version 0.20.2, „cmake : add config version support“.
- ggml-Backend-Registry: `GGML_BACKEND_DL` (dynamische Backends) existiert, ist **default OFF** und erfordert `BUILD_SHARED_LIBS=ON` (ggml/src/CMakeLists.txt Z.188). Bei unserem statischen Build irrelevant. Bemerkenswert: ggml v1.9.3 kennt jetzt **Android-spezifische CPU-Varianten** (`android_armv8.0_1` … `android_armv9.2_2`, ggml/src/CMakeLists.txt Z.416–424) für `GGML_CPU_ALL_VARIANTS` — späteres Upgrade-Pfad für Laufzeit-Dispatch, s. §3.4.
- Option-Namen unverändert: `WHISPER_BUILD_TESTS/EXAMPLES/SERVER` (Default = `WHISPER_STANDALONE`, bei `add_subdirectory` also schon OFF, CMakeLists Z.41–50, 103–105), `WHISPER_SDL2`, `WHISPER_CURL` (Z.108–109), `GGML_OPENMP` (ggml/CMakeLists Z.245, default ON), `GGML_NATIVE` (default OFF bei `CMAKE_CROSSCOMPILING`, ggml/CMakeLists Z.105–110), `GGML_CPU_ARM_ARCH` (Z.184), `GGML_CPU_KLEIDIAI` (Z.153, default OFF), `GGML_LLAMAFILE` (Z.197), `BUILD_SHARED_LIBS` (Z.77).

### 2.4 Empfehlung

**An `v1.9.3` pinnen** (Submodul auf `371b5a7561823ab2bb32142d2751e35e7534727b`). Begründung: identische API, drei für Diktat/Android relevante Fixes (#3956, #3957, Affinity). Das *Pre-release*-Flag ist nach den Release-Notes ein Artefakt des umgestellten Release-Prozesses (#3996), kein Qualitätsurteil; wer es konservativ will, nimmt `v1.9.2`. **Nie** auf `master`/Nightly pinnen — der ggml-Sync ändert wöchentlich Build-Details.

---

## 3. Android-Build

### 3.1 NDK

| Version | Status (Google-Repo `repository2-3.xml`, GitHub `android/ndk` Releases) | 16-KB-Alignment |
|---|---|---|
| `27.2.12479018` (alt) | r27c, stable | **nicht** Default → Linker-Flags nötig |
| `27.3.13750724` | r27d = **2024-LTS**, „Supported until r30 is released“ | nicht Default; Option `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES` seit r27 vorhanden |
| **`28.2.13676358`** | r28c (2025-07-08), stable; Default-NDK von AGP 9.4 | **Default ON**: „The default alignment of shared libraries for arm64-v8a and x86_64 is now 16k“; NDK-CMake-Toolchain braucht CMake ≥3.10 |
| `29.0.14206865` | r29 (2025-10-06), stable; LLVM clang-r563880c | Default ON |
| `30.0.16138531` | r30 RC1 (2026-08-24) | — |

AGP 8.7.x nutzt per Default `27.0.12077973`, akzeptiert aber jede installierte Version via `android.ndkVersion` („Before release, each AGP version is thoroughly tested with the latest stable NDK release at that time“). Es gibt kein dokumentiertes Maximum; r28 mit AGP ≥8.5.1 ist der von Google für 16 KB empfohlene Pfad.
Quellen: <https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes>, <https://developer.android.com/build/releases/gradle-plugin>, <https://developer.android.com/studio/projects/install-ndk>, <https://github.com/android/ndk/releases> (r28/r28c/r27d-Bodies), <https://github.com/android/ndk/wiki>, <https://dl.google.com/android/repository/repository2-3.xml>

**Empfehlung: `28.2.13676358`.** r29 bringt nur LLVM-Update ohne Nutzen für uns; r27d wäre LTS, braucht aber die Zusatz-Flags.

### 3.2 16-KB-Page-Size

- „NDK version r28 and higher compile 16 KB-aligned by default.“ Für r27 und älter: `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` (CMake: `target_link_options(... PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")`).
- APK-Seite: „you need to upgrade to Android Gradle Plugin (AGP) version 8.5.1 or higher“ → AGP 8.7.3 ok; `useLegacyPackaging = false` beibehalten (unkomprimierte, zip-alignte .so).
- Play-Anforderung laut Seite (Stand Abruf): „all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates.“ WhisperLoom wird sideloaded, aber Pixel-Geräte mit 16-KB-Kernel existieren → trotzdem einhalten.
- Prüfung: `zipalign -v -c -P 16 4 app.apk` (build-tools ≥35) bzw. `check_elf_alignment.sh`. Eigene Messung am fremden Prebuilt (§3.5): alle `PT_LOAD` mit `align=16384`.
- **Linker-Flags trotz r28 mitgeben?** Ja, unschädlich redundant; sichert den r27d-Fallback ab (Snippet §8.2).
Quelle: <https://developer.android.com/guide/practices/page-sizes>

### 3.3 CMake

Im SDK-Manager verfügbar (repository2-3.xml): `3.6.4111459`, `3.10.2.4988404`, `3.18.1`, **`3.22.1`**, `3.30.3–3.30.5`, `3.31.0–3.31.6`, `4.0.2`, `4.0.3`, `4.1.0–4.1.2`.
Anforderungen: whisper.cpp `cmake_minimum_required(3.5)`, ggml `3.14...3.28`, NDK-r28-Toolchain ≥3.10, KleidiAI (falls je aktiviert) profitiert ab 3.24/3.28.
**Empfehlung `3.22.1`** — identisch zum Alt-Stand, mit AGP 8.7.3 erprobt; CMake 4.x würde Policy-Kompatibilität <3.5 entfernen (whisper sitzt genau auf 3.5 → Warnungen; kein Gewinn). `3.31.6` als sichere Alternative, falls 3.22.1 in Zukunft aus dem Repo fällt.

### 3.4 Compiler-Flags für arm64-v8a

**Mechanik in ggml (ggml/src/ggml-cpu/CMakeLists.txt v1.9.3):**
- Z.113ff: nur bei `GGML_NATIVE` wird per `-mcpu=native` sondiert (bei Cross-Compile automatisch OFF).
- Z.169–170: `if (GGML_CPU_ARM_ARCH) list(APPEND ARCH_FLAGS -march=${GGML_CPU_ARM_ARCH})` — genau der Hebel für uns; danach `check_cxx_source_compiles` auf `__ARM_FEATURE_DOTPROD/SVE/MATMUL_INT8/FMA/FP16_VECTOR_ARITHMETIC/SME` (Z.226–236) setzt `HAVE_*` und aktiviert die Kernel.
- Ohne `GGML_CPU_ARM_ARCH` und ohne `GGML_NATIVE` → Baseline `armv8-a` (NDK-Default): kein FP16-Vektor-Rechnen, kein DotProd → deutlich langsamer.
- Q5_1 profitiert direkt von DotProd: `ggml_vec_dot_q5_1_q8_1` (ggml/src/ggml-cpu/arch/arm/quants.c Z.1032) nutzt `ggml_vdotq_s32` (68 Verwendungen in der Datei), ohne `__ARM_FEATURE_DOTPROD` wird per `vmull` emuliert. FP16-Arithmetik beschleunigt die F16-Tensoren (Encoder-Conv/Attention).

**Was bauen die Referenzen?**
- Offizielles `examples/whisper.android` (v1.9.3, `lib/src/main/jni/whisper/CMakeLists.txt`): zwei Targets für arm64 — `whisper_v8fp16_va` mit `-march=armv8.2-a+fp16` und Fallback `whisper` (Baseline), Auswahl zur Laufzeit in `LibWhisper.kt` über `/proc/cpuinfo` enthält `"fphp"` (Z.108–129). Release-Flags: `-O3 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections`, Link `-Wl,--gc-sections -Wl,--exclude-libs,ALL -flto`.
- `whisper.rn` (android/src/main/CMakeLists.txt): `rnwhisper_v8fp16_va_2` (`-march=armv8.2-a+fp16`), `rnwhisper_v8` (`-march=armv8-a`), generic.
- llama.cpp `docs/build.md` (Android arm64-v8a): `-DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF`, „`GGML_LLAMAFILE=OFF` avoids the llamafile backend, which is not supported on Android“, „`GGML_OPENMP=OFF` avoids adding an OpenMP runtime dependency“, globales `-march` nur bewusst („raise the baseline instruction set“). KleidiAI wird dort empfohlen — für uns **nicht**: KleidiAI-Kernel decken Q4_0/Q8_0/F16/BF16 ab, nicht Q5_1, und der Build lädt zur Configure-Zeit ein Tarball (Netz in CI, MD5-gepinnt v1.24.0, ggml-cpu/CMakeLists Z.586–588).

**Entscheidung für WhisperLoom (einfachster sicherer Weg):**
- **Eine** `.so`, `-DGGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod`. **Kein `+i8mm`**: I8MM ist Armv8.6 (erst Cortex-A710/X2, Snapdragon 8 Gen 1, Dimensity 9000, 2022+); ggml nutzt es v. a. in Q4_0-Repack/GEMM-Pfaden, Gewinn für Q5_1 gering, SIGILL-Risiko auf 2018–2021-Geräten hoch (vgl. Termux-SIGILL-Bericht <https://github.com/ggml-org/whisper.cpp/issues/967>).
- **Laufzeit-Guard in Kotlin** statt zweiter Bibliothek: `System.loadLibrary` nur, wenn `/proc/cpuinfo` `Features` sowohl `fphp` (HWCAP_FPHP) als auch `asimddp` (HWCAP_ASIMDDP) enthält (Kernel-Namen: `arch/arm64/kernel/cpuinfo.c` Z.60/71; Doku `Documentation/arch/arm64/elf_hwcaps.rst`). Sonst: On-Device nicht verfügbar → API-Modus. Betroffen sind nur Armv8.0-Geräte (Cortex-A53/A57/A72/A73, z. B. Snapdragon 835/660, Kirin 970) — für eine Diktier-App mit `small`-Modell wären die ohnehin zu langsam.
- **Späterer Upgrade-Pfad** (nicht jetzt, YAGNI): `GGML_BACKEND_DL=ON` + `BUILD_SHARED_LIBS=ON` + `GGML_CPU_ALL_VARIANTS=ON` erzeugt die 7 Android-Varianten (ggml/src/CMakeLists Z.416–424) mit Laufzeit-Scoring; erfordert `useLegacyPackaging=true` (Extraktion nach `nativeLibraryDir`) und `ggml_backend_load_all_from_path()`. Deutlich mehr Build-Komplexität und ~7× ggml-cpu-Größe.

Weitere Optionen: `GGML_OPENMP=OFF` (NDK liefert libomp, aber CMake-FindOpenMP im NDK-Kontext ist laut llama.cpp-Doku nicht unterstützt; ggml nutzt dann eigenen Threadpool), `GGML_NATIVE=OFF`, `GGML_LLAMAFILE=OFF`, `BUILD_SHARED_LIBS=OFF`, `ANDROID_STL=c++_static` (eine `.so`, kein `libc++_shared.so`).
Quellen: <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/ggml/src/ggml-cpu/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/examples/whisper.android/lib/src/main/java/com/whispercpp/whisper/LibWhisper.kt>, <https://raw.githubusercontent.com/mybigday/whisper.rn/main/android/src/main/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/build.md>, <https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md>

### 3.5 Warum nur `arm64-v8a` und erwartete `.so`-Größe

- Reale Zielgeräte sind ausnahmslos arm64; `armeabi-v7a` wird von whisper.cpp nur mit vfpv4-Fallback und praktisch unbrauchbarer Geschwindigkeit bedient; `x86_64` nur für Emulator. Der alte x86_64-Emulator-Test kann bleiben, wenn CI-Instrumented-Tests gewünscht sind — dann `abiFilters` um `x86_64` erweitern und `GGML_CPU_ARM_ARCH` per `if(ANDROID_ABI STREQUAL "arm64-v8a")` scoped setzen (Snippet §8.1 tut das). Für das Release-APK reicht arm64 (Prebuilt-Anbieter: „`arm64-v8a` (covers >90% of modern Android devices)“).
- **Größe gemessen**: `dev.ffmpegkit-maintained:whisper-android:1.0.0` (whisper.cpp @ `51c6961`, Release-Assets 2026-07-05): `jni/arm64-v8a/libwhisper.so` = **1 524 520 Bytes**, gestrippt (kein `.symtab`/`.debug_info`), `PT_LOAD align=16384`, gebaut mit „Android clang version 18.0.3 (based on r522817c)“ = NDK r27; STL als `libc++_shared.so` (1 292 904 B) separat. Für WhisperLoom mit `c++_static` und ggf. `-flto`: **[grob] 2–2,5 MB** im APK (unkomprimiert). Quelle: <https://github.com/ffmpegkit-maintained/whisper/releases/tag/v1.0.0>

---

## 4. Qualitätsparameter für Diktat

Defaults aus `src/whisper.cpp` v1.9.3 `whisper_full_default_params` (Z.5945–6048): `n_threads = min(4, hardware_concurrency)`, `no_context=true`, `single_segment=false`, `suppress_blank=true`, `suppress_nst=false`, `temperature=0`, `temperature_inc=0.2`, `entropy_thold=2.4`, `logprob_thold=-1.0`, `no_speech_thold=0.6`, `greedy.best_of=5`, `beam_search.beam_size=5`, `vad=false`, `carry_initial_prompt=false`. `whisper_context_default_params`: `use_gpu=true`, **`flash_attn=true`** (Z.3616–3620). `whisper-cli` wählt `strategy = beam_size > 1 ? BEAM_SEARCH : GREEDY` mit Default `beam_size=5` (cli.cpp Z.45, Z.1213) — d. h. **die Referenz-CLI läuft standardmäßig mit Beam-Search 5**.

| Parameter | Empfehlung | Wirkung / Begründung (Quelle) |
|---|---|---|
| `strategy` / `beam_size` | `WHISPER_SAMPLING_BEAM_SEARCH`, `beam_size=5`, `greedy.best_of=5` | Beam-Search ist der OpenAI-Default-Pfad; Literatur: WER-Unterschied zu Greedy „< 1.5 pp“, aber weniger Abbrüche/Halluzinationen. Kosten: `n_decoders = max(best_of, beam_size)` = 5 Decoder pro Schritt (whisper.cpp Z.6900–6913) — Decoder-Zeit ~×5, Encoder unverändert; bei `small` dominiert der Encoder, bei `large-v3-turbo` (4 Decoder-Layer) ist der Decoder ohnehin klein. Als Einstellung „Schnell (Greedy)“ / „Genau (Beam 5)“ exponieren. |
| `initial_prompt` | `Prefs.apiPrompt` durchreichen (max. `n_text_ctx/2` = 224 Tokens) | Gleiches Mechanismus wie API-`prompt`: „prepended to any existing text context … conditioning the model toward vocabulary consistent with the prompt“ (whisper.h Z.521–528; Tokenisierung whisper.cpp Z.6950–6960). Für Deutsch zusätzlich Stil-Priming, z. B. „Diktat auf Deutsch, mit Punkt, Komma und korrekter Groß- und Kleinschreibung.“ `carry_initial_prompt=false` lassen (bei `no_context=true` irrelevant, nur ein Fenster hat Prompt). |
| `language` | **fest `"de"`** (Prefs-Default), `"auto"` nur wenn User es wählt | `"auto"`/`""`/`nullptr` → `whisper_lang_auto_detect_with_state` vor dem Decoding (whisper.cpp Z.6849–6852) = zusätzlicher Decoder-Durchlauf und bei 3-Sekunden-Diktaten Fehlerkennungs-Risiko. Bei `auto`: erkannte Sprache per `whisper_full_lang_id`+`whisper_lang_str` zurückgeben, damit `TextPolisher` die richtige Füllwortliste nimmt. |
| `suppress_nst` | **`true`** | Unterdrückt Nicht-Sprach-Tokens (♪, [Musik] …) wie OpenAI `suppress_tokens=-1`; verhindert typische Diktat-Artefakte. Default in whisper.cpp ist false (Z.5986). |
| `suppress_blank` | `true` (Default) | Kein leeres erstes Token. |
| `no_context` | `true` | Kein Text-Carry zwischen 30-s-Fenstern → verhindert Wiederholungsschleifen (vgl. Issue #1853). Für kurze Diktate ohne Nachteil. |
| `no_timestamps` / `single_segment` | `true` / `false` | Wir wollen nur Text; mehrere Segmente pro Fenster erlauben. |
| `temperature_inc` / `entropy_thold` / `logprob_thold` / `no_speech_thold` | Defaults `0.2 / 2.4 / -1.0 / 0.6` | Fallback: Decoder gilt als gescheitert bei `entropy < 2.4` (Wiederholung, Z.7564) oder `avg_logprobs < -1.0 && no_speech_prob < 0.6` (Z.7592) → erneutes Decoding mit `t += 0.2` und `best_of` Decodern. Segment wird als Stille verworfen bei `no_speech_prob > 0.6 && avg_logprobs < logprob_thold` (Z.7622). Latenz-Bremse falls nötig: `temperature_inc = 0` (= CLI `--no-fallback`), nur wenn Messung es verlangt. |
| `n_threads` | Anzahl Performance-Kerne, min 2, max 6 | Offizielles Beispiel zählt Kerne mit `cpuinfo_max_freq` oberhalb des Minimums (`WhisperCpuConfig.kt`); ggml pinnt seit v1.9.3 auch auf Android korrekt. Mehr Threads als Big-Cores bremsen (A55-Kerne halb so schnell, PR llama/26838). |
| `flash_attn` (`whisper_context_params`) | `true` (Default) | Seit 1.9.x Default in Lib und CLI (cli.cpp Z.79); CPU-Kernel vorhanden („vectorize flash-attention V-cache F16→F32“ in v1.9.3). Als Debug-Schalter exponieren, um bei Auffälligkeiten A/B zu testen. |
| `use_gpu` | `false` | Kein Vulkan-Backend im Build; verhindert Backend-Suche/Logspam. |
| `abort_callback` | atomarer Abbruch-Flag | Ermöglicht „Abbrechen“ in Overlay/IME ohne Prozess-Kill. |
| `audio_ctx` | 0 (Default) | Verkleinern beschleunigt, aber „can significantly reduce the quality“ (whisper.h). Nicht anfassen. |
| VAD (`vad`, `vad_model_path`, `vad_params`) | **aus** für Diktat | Silero-VAD schneidet Stille vor Whisper weg („can significantly speed up the transcription process“, README §VAD). Beim Diktat ist Audio schon `trimSilence`-gekürzt und meist < 30 s → Gewinn ≈ 0, Kosten: 885-KB-Zweitmodell, LSTM-Lauf, Risiko leise Sprache zu kappen. **Sinnvoll später** für `SharedAudioTranscriber` (Minuten-lange WhatsApp-Nachrichten: weniger 30-s-Fenster, weniger Stille-Halluzinationen). Defaults: `threshold 0.5, min_speech 250 ms, min_silence 100 ms, speech_pad 30 ms, samples_overlap 0.1 s` (whisper.cpp Z.4462–4472). Modelle: `ggml-silero-v5.1.2.bin` 885 098 B, SHA-256 `29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf`; `ggml-silero-v6.2.0.bin` 885 098 B, `2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987` (HF-API `ggml-org/whisper-vad`, dieses Repo antwortet 200). |

**Was bringt messbar bessere deutsche Ergebnisse?** In dieser Reihenfolge: (1) Modellgröße (`small` ≫ `base` ≫ `tiny`; `large-v3-turbo` ≈ `large-v2`-Niveau: „performs comparably to large-v2 across languages“, „larger degradation on some languages like Thai and Cantonese“ — Deutsch nicht betroffen; Turbo ist **nicht** für Übersetzung gedacht, <https://github.com/openai/whisper/discussions/2363>), (2) Sprache fest statt auto, (3) `initial_prompt` mit Eigennamen/Fachbegriffen und Satzzeichen-Priming, (4) Beam-Search 5, (5) `suppress_nst`. Quantisierung: q5_1 „loses less than 1 % WER vs F16“ (Sekundärquelle, **[unsicher]**: <https://mvpfactory.io/blog/wiring-whisper-cpp-to-android-s-audiorecord-api-real-time-on-device-speech>).

**Laufzeit für 10 s Audio auf Mittelklasse-ARM, CPU, 4 Threads — [grob], aus Sekundärquellen extrapoliert, unbedingt auf Zielgerät messen** (Whisper rechnet den Encoder immer über ein 30-s-Fenster, die Audiolänge unter 30 s ändert die Encoderzeit kaum):

| Modell | Anhaltspunkte | Schätzung 10 s Audio |
|---|---|---|
| tiny-q5_1 | „~5 s Audio in ~1–2 s“ (tiny q8_0, 4 Threads, Discussion #3567); Snapdragon 778G: tiny q8_0 118 ms je 1-s-Chunk (mvpfactory) | 1–2 s |
| base-q5_1 | Pixel 6a base Q5 „300–500 ms“ Latenz mit Streaming-Tricks; 778G base q8_0 245 ms/1 s | 2–4 s |
| small-q5_1 | Issue #1070: „about 30 seconds“ für wenige Wörter (2023, vermutlich Debug-Build); ~4× base | 5–12 s |
| large-v3-turbo-q5_0 | Encoder = voller `large`-Encoder (32 Layer, 1280 dim), Decoder nur 4 Layer | 20–60 s **[sehr unsicher]** |
| medium-q5_0 | 24+24 Layer | 30–90 s **[sehr unsicher]** — nicht anbieten |

Quellen: <https://github.com/ggml-org/whisper.cpp/discussions/3567>, <https://github.com/ggml-org/whisper.cpp/issues/1070>, <https://mvpfactory.io/blog/wiring-whisper-cpp-to-android-s-audiorecord-api-building-a-sub-100ms-on-device>. Die Benchmark-Sammlung <https://github.com/ggml-org/whisper.cpp/issues/89> enthält **keine** Android-Einträge. Konsequenz: JNI-Bench (`whisper_bench_ggml_mul_mat_str`) und ein Timing-Log (`whisper_print_timings`) im Debug-Menü behalten, um echte Zahlen zu bekommen.

---

## 5. Modell-Katalog `huggingface.co/ggerganov/whisper.cpp`

Abgefragt per `GET https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true` (Repo-Commit `5359861c739e955e79d9a303bcbc70fb988958b1`, lastModified 2024-10-29) und per `HEAD …/resolve/main/<datei>` (Redirect → `x-linked-size`, finaler `content-length`, `accept-ranges: bytes` — **Range-Resume wird unterstützt**). Alle Modelle ohne `.en`-Suffix sind multilingual (Deutsch ok). RAM = Modellgröße + KV-Caches + Compute-Buffer aus `whisper_init_state`-Logs (CPU, Desktop-Logs; auf Android ähnlich, **[grob]**).

| id | Datei | Bytes | SHA-256 | RAM gesamt [grob] | Deutsch | Empfehlung |
|---|---|---|---|---|---|---|
| tiny | `ggml-tiny-q5_1.bin` | 32 152 673 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` | ~250 MB (31,6 + KV 21 + Compute 198; Issue #2392) | schwach | nur Emulator-/CI-Test |
| base | `ggml-base-q5_1.bin` | 59 707 625 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` | ~355 MB (59 + 28 + 267; Issue #3421) | brauchbar für kurze Sätze | Option „Schnell“ |
| **small** | **`ggml-small-q5_1.bin`** | **190 085 487** | **`ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`** | **~430 MB** (189,5 + KV 80 + Compute 160; Issue #3654) | gut | **Standard-Download** |
| small (q8) | `ggml-small-q8_0.bin` | 264 464 607 | `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f` | ~505 MB | gut (minimal besser als q5_1) | optional, wenn Messung Q5-Verlust zeigt |
| medium | `ggml-medium-q5_0.bin` | 539 212 467 | `19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f` | ~1,6 GB (538,6 + KV 308 + Compute 773; Issue #2248) | sehr gut | **nicht anbieten** — langsamer und größer als turbo bei ähnlicher Qualität |
| large-v3-turbo | `ggml-large-v3-turbo-q5_0.bin` | 574 041 195 | `394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2` | ~1,0 GB (574 + ~410 wie q8_0-Log) | sehr gut (≈ large-v2) | Option „Beste Qualität“, nur ≥6 GB RAM, Warnhinweis Dauer |
| large-v3-turbo (q8) | `ggml-large-v3-turbo-q8_0.bin` | 874 188 075 | `317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1` | ~1,3 GB (873,6 + KV 50 + Compute 359; Issue #3047) | sehr gut | nicht anbieten (Mehrwert zu q5_0 gering) |

URL-Schema: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<datei>` (302 → CDN; `HttpURLConnection.instanceFollowRedirects=true` reicht, Cross-Host-Redirect wird von HttpURLConnection gefolgt, solange das Schema https bleibt).
Hinweis: `huggingface.co/ggml-org/whisper.cpp` liefert 401 (gated) — **ggerganov**-Repo verwenden; `ggml-org/whisper-vad` ist offen.
Die Byte-Größen im alten `WhisperModel.kt` (SMALL 190 085 487, BASE 59 707 625, TINY 32 152 673) stimmen weiterhin.
README-Memory-Tabelle (F16, nicht quantisiert, als Obergrenze): tiny ~273 MB, base ~388 MB, small ~852 MB, medium ~2,1 GB, large ~3,9 GB (<https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/README.md>).

**Empfehlung Standard-Download:** `ggml-small-q5_1.bin` — beste Balance für Deutsch, 190 MB Download, ~430 MB RAM (läuft in 4-GB-Geräten neben IME). Kein Modell mehr im APK bündeln (APK bleibt ~5 MB; Modell beim Onboarding laden).

---

## 6. Download-Design

### 6.1 `DownloadManager` (Android-Framework)

Fakten (Referenz `DownloadManager.Request`):
- Ziel nur externer Speicher: `setDestinationInExternalFilesDir(context, dirType, subPath)` („path within the application's external files directory (as returned by Context.getExternalFilesDir(String))“), `setDestinationInExternalPublicDir`, `setDestinationUri` („For applications targeting Build.VERSION_CODES.Q or above, WRITE EXTERNAL_STORAGE permission is not needed and the uri must refer to a path within the directories owned by the application (e.g. Context.getExternalFilesDir(String)) or a path within the top-level Downloads directory“). **`getFilesDir()` ist nicht erreichbar.** Ohne Ziel: „downloads are saved to a generated filename in the shared download cache and may be deleted by the system at any time“.
- Plus: Fortschritt/Notification (`setNotificationVisibility`), überlebt App-Kill, Resume durch das System, `setAllowedOverMetered(false)`.
- Minus: kein SHA-256, Download-Provider kann vom User deaktiviert sein (`enqueue` wirft dann), Fortschritt nur per Polling der `query()`-Cursor, Datei landet auf „external“ (Emulated Storage, aber App-privat und ohne Permission — funktional ok, da JNI nur einen Pfad braucht).
Quelle: <https://developer.android.com/reference/android/app/DownloadManager.Request>

### 6.2 Eigener `HttpURLConnection`-Downloader (Empfehlung)

Der alte `ModelManager.download()` existiert bereits (blockierend, `.tmp` + rename, Content-Length-Check). Erweiterungen:
1. Ziel `filesDir/models/<datei>`, Teil-Datei `<datei>.part`.
2. **Resume**: existiert `.part` mit n Bytes → Header `Range: bytes=n-`; Antwort muss `206` sein und `Content-Range: bytes n-…/total` tragen, sonst `.part` verwerfen und bei 0 starten. HF/CDN liefert `accept-ranges: bytes` (gemessen).
3. **Integrität**: erwartete Bytes aus Katalog (§5) == finale Dateigröße **und** SHA-256 (MessageDigest streamend beim Schreiben; beim Resume die vorhandenen Bytes vorher einlesen). Bei Mismatch löschen, Fehler melden (fängt HTML-Fehlerseiten, Teildownloads, CDN-Korruption; whisper.cpp v1.9.3 härtet zusätzlich den Loader, #3957).
4. `renameTo` erst nach erfolgreichem Hash → atomar; `WhisperEngine` lädt nur Dateien ohne `.part`.
5. Timeouts 15 s connect / 30 s read, Retry mit Backoff (max 3), `setAllowedOverMetered`-Äquivalent: vor Start `ConnectivityManager.isActiveNetworkMetered` prüfen und User fragen (190–574 MB).
6. Reiner Kotlin-Code → Unit-testbar mit lokalem `HttpServer` (Range, 206/200, Abbruch mitten drin).

**Ausführungsrahmen** — 190 MB dauern im Mobilnetz Minuten, Activity kann sterben:
- **Variante A (ohne AndroidX, passt zur heutigen Codebasis):** `ModelDownloadService` als Foreground-Service mit `android:foregroundServiceType="dataSync"` + Permission `FOREGROUND_SERVICE_DATA_SYNC` (Pflicht ab Android 14: „you must declare an appropriate service type for each foreground service“). Android 15: dataSync max **6 h pro 24 h**, danach `Service.onTimeout()` → `stopSelf()` in wenigen Sekunden, „if the user brings the app to the foreground, the timer resets“ — für einen user-gestarteten Download irrelevant, `onTimeout` trotzdem implementieren. Nicht aus `BOOT_COMPLETED` starten (Android 15 verboten). Notification mit Fortschritt (Kanal existiert schon für den Mikro-FGS).
- **Variante B (sobald Compose/AndroidX im Projekt):** WorkManager `2.11.2` (stable, 2026-03-25; `2.12.0-rc01` 2026-08-12) `CoroutineWorker` + `setForeground(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))`, Constraints (`NetworkType.UNMETERED` optional), automatischer Retry, überlebt Reboot. Caveat aus der Doku: „Starting with Android 16, long running workers (which use foreground services) can exhaust your app's job quota“ — bei einem einmaligen Download vernachlässigbar.
- Google nennt für Downloads selbst `DownloadManager`/WorkManager/„user-initiated data transfer jobs“ als Alternativen zum FGS; für unseren Fall (ein User-Klick, Integritätsprüfung, filesDir) ist der eigene Downloader im FGS bzw. Worker die einfachste korrekte Lösung.
Quellen: <https://developer.android.com/develop/background-work/services/fgs/service-types>, <https://developer.android.com/about/versions/15/behavior-changes-15>, <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>, <https://developer.android.com/jetpack/androidx/releases/work>

**Empfehlung:** Eigener Downloader (Kern) + Variante A jetzt; auf Variante B umstellen, wenn AndroidX eh kommt (Kern bleibt identisch).

---

## 7. Kontext-Lebenszyklus & Speicher

- **Thread-Sicherheit**: whisper.h Z.45–46: „The following interface is thread-safe as long as the sample whisper_context is not used by multiple threads concurrently.“ → Alt-Design beibehalten: ein `Executors.newSingleThreadExecutor("whisper-worker")` pro Kontext; Laden, `whisper_full`, Segment-Auslesen und `whisper_free` laufen ausschließlich dort. Der `abort_callback` darf dagegen von jedem Thread gesetzt werden (`std::atomic<bool>`).
- **Ein Kontext prozessweit**: IME (`WhisperLoomInputMethodService`), `FloatingMicService` und `ShareTranscribeActivity` laufen im selben App-Prozess → `WhisperEngine`-Singleton wie im Alt-Stand (`synchronized(lock)`, `ensureLoaded`), sonst läge das Modell doppelt im RAM.
- **Modellwechsel**: `release()` des alten Kontexts **vor** `create` des neuen (Peak sonst Summe beider; bei small→turbo ~1,4 GB).
- **Speicherdruck**: small ~430 MB, turbo ~1,0 GB (§5). IME-Prozesse sind LMK-Kandidaten, wenn sie im Hintergrund groß sind → `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL / UI_HIDDEN)` im Application/Service: Kontext freigeben, beim nächsten Diktat neu laden (Ladezeit small ~1–3 s von Flash, **[grob]**). Optional „Modell im Speicher halten“-Schalter. Mindest-RAM-Check vor Turbo-Download: `ActivityManager.MemoryInfo.totalMem ≥ 6 GB`.
- **Kurze Eingaben**: whisper warnt bei < 100 ms („input is too short“, whisper.cpp Z.6884); `WhisperContext.transcribe` gibt bei `samples.isEmpty()` bereits `""` zurück — zusätzlich bei < 0,5 s nach trimSilence auf 1 s mit Stille auffüllen (Empfehlung der Warnung) oder abbrechen.
- **Lange Eingaben** (ShareTranscribe, Minuten): `whisper_full` iteriert selbst über 30-s-Fenster; die 5-Minuten-`AudioChunks`-Stückelung ist für lokal unnötig, schadet aber nicht (Fortschritt pro Stück). Progress via `progress_callback`.
- **Prozess-Ende**: `whisper_free` im `onDestroy` der Services ist nicht nötig, wenn der Prozess stirbt; wichtig ist nur, dass `ptr` nach `free` auf 0 gesetzt wird (Doppel-Free vermeiden — Alt-Code macht das).

---

## 8. Lieferungen

### 8.1 `app/src/main/cpp/CMakeLists.txt` (angepasst)

```cmake
cmake_minimum_required(VERSION 3.22)
project(whisperloom CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp liegt als Git-Submodul in der Repo-Wurzel (Tag v1.9.3, Commit 371b5a75).
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp)
if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "whisper.cpp-Submodul fehlt unter ${WHISPER_DIR}. Ausfuehren: git submodule update --init --recursive")
endif()

# --- whisper.cpp / ggml schlank & reproduzierbar (Option-Namen v1.9.3 geprueft) ---
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2           OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)  # ggml-eigener Threadpool; NDK-OpenMP via CMake nicht unterstuetzt (llama.cpp docs/android.md)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)  # Cross-Compile
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)  # "not supported on Android" (llama.cpp docs/build.md)
set(GGML_CPU_KLEIDIAI      OFF CACHE BOOL "" FORCE)  # deckt Q5_1 nicht ab, laedt Tarball zur Configure-Zeit
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)  # whisper+ggml statisch in libwhisperloom.so

# Ziel-ISA: Armv8.2 + FP16-Vektorarithmetik + DotProd (Cortex-A55/A75 und neuer, 2018+).
# Kein +i8mm (Armv8.6, SIGILL auf 2018-2021-SoCs). Kotlin prueft /proc/cpuinfo auf "fphp" und "asimddp".
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
endif()

add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp EXCLUDE_FROM_ALL)

find_library(LOG_LIB log)
add_library(whisperloom SHARED whisper_jni.cpp)
target_link_libraries(whisperloom PRIVATE ${LOG_LIB} android whisper)

# Groesse/Geschwindigkeit wie examples/whisper.android (v1.9.3)
foreach(t whisperloom whisper ggml ggml-base ggml-cpu)
    if(TARGET ${t})
        target_compile_options(${t} PRIVATE
            $<$<NOT:$<CONFIG:Debug>>:-O3>
            -fvisibility=hidden -fvisibility-inlines-hidden
            -ffunction-sections -fdata-sections)
    endif()
endforeach()
target_link_options(whisperloom PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    # 16-KB-Page-Size: ab NDK r28 Default, fuer NDK r27 (LTS-Fallback) Pflicht; redundant unschaedlich.
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384)
```

Hinweise: Die ggml-Target-Namen (`ggml`, `ggml-base`, `ggml-cpu`) sind in ggml/src/CMakeLists.txt v1.9.3 so definiert (Z.192ff); die `if(TARGET)`-Schleife bleibt robust, falls sich das ändert. `-flto` optional (Beispiel nutzt es; verlängert Link-Zeit, spart ~10–20 % Größe **[grob]**).

### 8.2 `whisper_jni.cpp` — Änderungen gegenüber `offline-v1` (Diff-artig)

```diff
 #include <jni.h>
 #include <android/asset_manager.h>
 #include <android/asset_manager_jni.h>
 #include <android/log.h>
 #include <string.h>
+#include <atomic>
 #include "whisper.h"
 
+// Abbruch-Flag: von beliebigem Thread setzbar, whisper_full pollt es vor jedem ggml-Graph.
+static std::atomic<bool> g_abort{false};
+static bool abort_cb(void * /*user_data*/) { return g_abort.load(std::memory_order_relaxed); }
+
+// Log-Umleitung nach logcat statt stderr (whisper_log_set gibt es seit 1.5)
+static void log_cb(ggml_log_level level, const char * text, void *) {
+    int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
+             : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
+    __android_log_write(prio, "whisper.cpp", text);
+}
+
+static whisper_context_params make_cparams(jboolean flash_attn) {
+    whisper_context_params cparams = whisper_context_default_params();
+    cparams.use_gpu    = false;      // reiner CPU-Build (kein Vulkan-Backend gelinkt)
+    cparams.flash_attn = flash_attn; // Default true in 1.9.x; als Debug-Schalter durchgereicht
+    return cparams;
+}

 JNIEXPORT jlong JNICALL
 Java_com_chris_whisperloom_WhisperLib_initContext(
-        JNIEnv *env, jobject thiz, jstring model_path_str) {
+        JNIEnv *env, jobject thiz, jstring model_path_str, jboolean flash_attn) {
     (void) thiz;
+    whisper_log_set(log_cb, nullptr);
     const char *path = env->GetStringUTFChars(model_path_str, nullptr);
-    whisper_context_params cparams = whisper_context_default_params();
-    cparams.use_gpu = false;
-    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
+    struct whisper_context *ctx = whisper_init_from_file_with_params(path, make_cparams(flash_attn));
     env->ReleaseStringUTFChars(model_path_str, path);
     return (jlong) ctx;
 }
 (initContextFromAsset analog; kann entfallen, wenn kein Modell mehr gebuendelt wird)

-JNIEXPORT void JNICALL
+JNIEXPORT jint JNICALL
 Java_com_chris_whisperloom_WhisperLib_fullTranscribe(
         JNIEnv *env, jobject thiz, jlong context_ptr,
-        jint num_threads, jstring language_str, jfloatArray audio_data) {
+        jint num_threads, jstring language_str, jstring initial_prompt_str,
+        jint beam_size, jboolean suppress_nst, jfloatArray audio_data) {
     (void) thiz;
     struct whisper_context *ctx = (struct whisper_context *) context_ptr;
     jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
     const jsize audio_len = env->GetArrayLength(audio_data);
     const char *language = env->GetStringUTFChars(language_str, nullptr);
+    const char *prompt = initial_prompt_str ? env->GetStringUTFChars(initial_prompt_str, nullptr) : nullptr;
 
-    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
+    // Beam-Search (beam_size > 1) wie whisper-cli-Default, sonst Greedy.
+    const enum whisper_sampling_strategy strategy =
+            beam_size > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY;
+    struct whisper_full_params params = whisper_full_default_params(strategy);
+    params.beam_search.beam_size = beam_size > 1 ? beam_size : -1;
+    params.greedy.best_of        = 5;            // Decoder-Anzahl im Temperatur-Fallback
     params.print_realtime   = false;
     params.print_progress   = false;
     params.print_timestamps = false;
     params.print_special    = false;
     params.translate        = false;
     params.no_timestamps    = true;
     params.single_segment   = false;
     params.no_context       = true;
     params.suppress_blank   = true;
+    params.suppress_nst     = suppress_nst;      // Nicht-Sprach-Tokens (♪, [Musik]) unterdruecken
     params.n_threads        = num_threads;
-    params.language         = language; // "auto" => Auto-Erkennung
+    params.language         = language;          // "auto" => whisper_lang_auto_detect vor dem Decoding
+    params.detect_language  = false;
+    params.initial_prompt   = (prompt && prompt[0]) ? prompt : nullptr; // max n_text_ctx/2 = 224 Tokens
+    params.carry_initial_prompt = false;
+    // Fallback-Defaults bewusst belassen: temperature_inc 0.2, entropy_thold 2.4,
+    // logprob_thold -1.0, no_speech_thold 0.6 (whisper.cpp v1.9.3 Z.5992-5995)
+    params.abort_callback           = abort_cb;
+    params.abort_callback_user_data = nullptr;
 
+    g_abort.store(false);
     whisper_reset_timings(ctx);
-    if (whisper_full(ctx, params, audio, audio_len) != 0) {
-        LOGW("whisper_full ist fehlgeschlagen");
-    }
+    const int rc = whisper_full(ctx, params, audio, audio_len);
+    if (rc != 0) LOGW("whisper_full rc=%d (abgebrochen=%d)", rc, (int) g_abort.load());
 
+    if (prompt) env->ReleaseStringUTFChars(initial_prompt_str, prompt);
     env->ReleaseStringUTFChars(language_str, language);
     env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
+    return rc;
 }
+
+JNIEXPORT void JNICALL
+Java_com_chris_whisperloom_WhisperLib_requestAbort(JNIEnv *, jobject) { g_abort.store(true); }
+
+// Erkannte Sprache (bei language="auto"), z. B. "de" — fuer TextPolisher.
+JNIEXPORT jstring JNICALL
+Java_com_chris_whisperloom_WhisperLib_getDetectedLanguage(JNIEnv *env, jobject, jlong context_ptr) {
+    const int id = whisper_full_lang_id((struct whisper_context *) context_ptr);
+    return env->NewStringUTF(id >= 0 ? whisper_lang_str(id) : "");
+}
+
+// Optional fuer Debug-Menue: Timings (Encoder/Decoder-ms) nach logcat.
+JNIEXPORT void JNICALL
+Java_com_chris_whisperloom_WhisperLib_printTimings(JNIEnv *, jobject, jlong context_ptr) {
+    whisper_print_timings((struct whisper_context *) context_ptr);
+}
```

Unverändert: `freeContext`, `getTextSegmentCount`, `getTextSegment` (Text mit `trim()` in Kotlin, führendes Leerzeichen bleibt), `getSystemInfo`. Alle verwendeten Symbole in whisper.h v1.9.3 verifiziert (`whisper_log_set`, `whisper_full_lang_id` Z.634, `whisper_lang_str`, `whisper_print_timings`, `whisper_reset_timings`).

Kotlin-Seite (`WhisperLib.kt`):
```kotlin
internal object WhisperLib {
    /** CPU muss FP16-Vektorarithmetik (fphp) und DotProd (asimddp) koennen — sonst SIGILL. */
    val supported: Boolean by lazy {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
        val feat = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        abi && feat.contains("fphp") && feat.contains("asimddp")
    }
    init { if (supported) System.loadLibrary("whisperloom") }

    external fun initContext(modelPath: String, flashAttn: Boolean): Long
    external fun freeContext(ctx: Long)
    external fun fullTranscribe(ctx: Long, threads: Int, language: String, initialPrompt: String?,
                                beamSize: Int, suppressNst: Boolean, audio: FloatArray): Int
    external fun requestAbort()
    external fun getTextSegmentCount(ctx: Long): Int
    external fun getTextSegment(ctx: Long, i: Int): String
    external fun getDetectedLanguage(ctx: Long): String
    external fun getSystemInfo(): String
    external fun printTimings(ctx: Long)
}
```
Thread-Anzahl (aus `examples/whisper.android/.../WhisperCpuConfig.kt` übernommen): Kerne zählen, deren `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq` über dem Minimum liegt, `coerceIn(2, 6)`.

### 8.3 `app/build.gradle.kts` — Snippets

```kotlin
android {
    namespace = "com.chris.whisperloom"
    compileSdk = 35
    ndkVersion = "28.2.13676358"          // r28c: 16-KB-Alignment Default, stable

    defaultConfig {
        // ...
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_static",   // eine .so, kein libc++_shared.so
                    "-DANDROID_PLATFORM=android-26",
                )
            }
        }
        ndk { abiFilters += listOf("arm64-v8a") }   // fuer Emulator-Tests zusaetzlich "x86_64"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            // whisper.cpp PR #3913: AGP-Debug laesst den Native-Build unoptimiert -> unbrauchbar langsam.
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
        release {
            // ... Signing wie bisher
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }   // Crash-Symbolisierung, .so bleibt gestrippt
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = false }   // unkomprimiert + 16-KB-zipaligned (AGP >= 8.5.1)
    }
}
```
Kein `noCompress += "bin"` mehr nötig, wenn kein Modell im APK liegt.

### 8.4 CI (`.github/workflows/build.yml`) — Ergänzungen

```yaml
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive            # whisper.cpp @ v1.9.3

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }

      - uses: gradle/actions/setup-gradle@v4   # Gradle-/Dependency-Cache

      - uses: android-actions/setup-android@v3

      - name: NDK, CMake, Build-Tools installieren
        run: sdkmanager --install "ndk;28.2.13676358" "cmake;3.22.1" "build-tools;35.0.0"

      # Native-Zwischenstand cachen (spart ~5-10 min whisper/ggml-Compile); Key an Submodul-Commit + CMakeLists binden
      - name: CMake-Build cachen
        uses: actions/cache@v4
        with:
          path: app/.cxx
          key: cxx-${{ runner.os }}-${{ hashFiles('.gitmodules', 'app/src/main/cpp/**', 'app/build.gradle.kts') }}-${{ hashFiles('.git/modules/whisper.cpp/HEAD') }}

      - name: Unit-Tests + Lint
        run: ./gradlew --no-daemon testDebugUnitTest lintDebug

      - name: Release-APK bauen (signiert)
        run: ./gradlew --no-daemon assembleRelease

      - name: 16-KB-Alignment pruefen
        run: |
          ZA="$ANDROID_HOME/build-tools/35.0.0/zipalign"
          "$ZA" -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk | tail -n 3
          READELF="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
          unzip -o -q app/build/outputs/apk/release/app-release.apk 'lib/arm64-v8a/*.so' -d /tmp/apk
          for so in /tmp/apk/lib/arm64-v8a/*.so; do
            echo "$so"; "$READELF" -lW "$so" | awk '$1=="LOAD"{print "  LOAD align", $NF}'
            "$READELF" -lW "$so" | awk '$1=="LOAD" && $NF!="0x4000"{bad=1} END{exit bad}'
          done
```
Für Emulator-Instrumented-Tests (falls behalten): `tiny-q5_1` (32 MB) per `actions/cache` mit Key = SHA-256 aus §5 nach `app/src/androidTest/assets/` legen und `abiFilters += "x86_64"` — dann greift der `GGML_CPU_ARM_ARCH`-Block nicht (nur arm64).

### 8.5 Empfohlene `whisper_full_params` (Zusammenfassung)

```
strategy            = BEAM_SEARCH (beam_size 5)   // Einstellung "Genau"; "Schnell" = GREEDY
greedy.best_of      = 5
n_threads           = Performance-Kerne (2..6)
language            = Prefs.language ("de"; "auto" nur explizit)
detect_language     = false
initial_prompt      = Prefs.apiPrompt (+ Satzzeichen-Priming), carry_initial_prompt = false
no_context          = true
no_timestamps       = true
single_segment      = false
suppress_blank      = true
suppress_nst        = true
temperature/inc     = 0.0 / 0.2      entropy_thold 2.4   logprob_thold -1.0   no_speech_thold 0.6
translate           = false
audio_ctx           = 0
vad                 = false            // spaeter optional fuer ShareTranscribe (silero-v5.1.2)
abort_callback      = g_abort
context: use_gpu=false, flash_attn=true
```

---

## 9. Risiken & offene Punkte

1. **Laufzeit auf Zielgerät unbekannt** — alle Zeitangaben in §4 sind Sekundärquellen/Extrapolation. Erster Schritt nach Build: `whisper_print_timings` auf Christofs Gerät mit small/turbo, 10-s-Sample.
2. **v1.9.3 „Pre-release“-Flag** — falls es sich als echte RC-Markierung herausstellt, auf `v1.9.2` zurück (API identisch).
3. **Armv8.0-Geräte** ausgeschlossen (Guard). Bewusst; Alternative wäre die 2-Bibliotheken-Variante des offiziellen Beispiels.
4. **RAM**: small ~430 MB im IME-Prozess; auf 3–4-GB-Geräten LMK-Risiko → `onTrimMemory`-Freigabe einbauen.
5. **HF-Verfügbarkeit/Größenänderung**: Katalog-Repo seit 2024-10-29 unverändert; SHA-256 fest im Code → bei Upstream-Änderung schlägt Integritätsprüfung sauber fehl statt korrupt zu laden.
6. **CMake 4.x im SDK-Manager**: nicht verwenden (Policy-Kompatibilität <3.5 entfernt; whisper sitzt auf 3.5).
7. **Debug-Build-Falle** (PR #3913) — ohne `-DCMAKE_BUILD_TYPE=Release` im Debug-Typ wirkt On-Device „kaputt langsam“.
8. **Offen**: Soll die Auswahl Lokal/API pro Kontext (IME vs. ShareTranscribe) getrennt sein? Soll `large-v3-turbo` überhaupt angeboten werden, bevor Messwerte vorliegen?

---

## Quellen (Auswahl, alle am 2026-09-06 abgerufen)

- whisper.cpp Releases/Tags: <https://github.com/ggml-org/whisper.cpp/releases>, <https://api.github.com/repos/ggml-org/whisper.cpp/tags>
- whisper.h v1.9.3: <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/include/whisper.h> · v1.9.1: <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.1/include/whisper.h>
- src/whisper.cpp v1.9.3 (Defaults, Fallback-Logik): <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/src/whisper.cpp>
- CMake: <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/ggml/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/ggml/src/CMakeLists.txt>, <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/ggml/src/ggml-cpu/CMakeLists.txt>
- Android-Beispiel v1.9.3: `examples/whisper.android/{app/build.gradle, lib/build.gradle, lib/src/main/jni/whisper/CMakeLists.txt, jni.c, LibWhisper.kt, WhisperCpuConfig.kt}`; PR #3913 <https://github.com/ggml-org/whisper.cpp/pull/3913>
- README v1.9.3 (Memory, Quantization, VAD): <https://raw.githubusercontent.com/ggml-org/whisper.cpp/v1.9.3/README.md>
- llama.cpp Android-Doku: <https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md>, <https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/build.md>; Affinity-Fix <https://github.com/ggml-org/llama.cpp/pull/26838>
- Modelle: <https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true>, HEAD auf `…/resolve/main/<datei>`; VAD <https://huggingface.co/api/models/ggml-org/whisper-vad?blobs=true>
- Memory-Logs: Issues <https://github.com/ggml-org/whisper.cpp/issues/3654> (small-q5_1), #3421 (base-q5_1), #2392 (tiny-q5_1), #2248 (medium-q5_0), #3047 (large-v3-turbo-q8_0)
- large-v3-turbo: <https://github.com/openai/whisper/discussions/2363>, <https://huggingface.co/openai/whisper-large-v3-turbo>
- Android: AGP 8.7 <https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes>; AGP aktuell <https://developer.android.com/build/releases/gradle-plugin>; NDK/CMake installieren <https://developer.android.com/studio/projects/install-ndk>; 16 KB <https://developer.android.com/guide/practices/page-sizes>; NDK-Releases <https://github.com/android/ndk/releases>, <https://github.com/android/ndk/wiki>; SDK-Repo <https://dl.google.com/android/repository/repository2-3.xml>
- DownloadManager: <https://developer.android.com/reference/android/app/DownloadManager.Request>; FGS-Typen <https://developer.android.com/develop/background-work/services/fgs/service-types>; Android-15-dataSync-Limit <https://developer.android.com/about/versions/15/behavior-changes-15>; WorkManager long-running <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>, Releases <https://developer.android.com/jetpack/androidx/releases/work>
- CPU-Feature-Namen: <https://raw.githubusercontent.com/torvalds/linux/master/arch/arm64/kernel/cpuinfo.c>, <https://raw.githubusercontent.com/torvalds/linux/master/Documentation/arch/arm64/elf_hwcaps.rst>
- SIGILL-Bericht Android: <https://github.com/ggml-org/whisper.cpp/issues/967>; Android-Laufzeit-Anhaltspunkte: <https://github.com/ggml-org/whisper.cpp/discussions/3567>, <https://github.com/ggml-org/whisper.cpp/issues/1070>, <https://mvpfactory.io/blog/wiring-whisper-cpp-to-android-s-audiorecord-api-building-a-sub-100ms-on-device>
- Prebuilt-Größenmessung: <https://github.com/ffmpegkit-maintained/whisper/releases/tag/v1.0.0>
