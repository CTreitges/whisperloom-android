# WhisperLoom (Android) — Claude-Kontext

> Globale Regeln: `~/.claude/CLAUDE.md` + `~/.claude/rules/`. Hier nur Projekt-Spezifisches. Stand 2026-11-10 (project-init).

## Zweck
Android-App: Diktieren in jede App (schwebender Knopf, Diktat-Tastatur, Sprachnachrichten per Teilen) — online über eigene Anbieter-Zugänge oder offline mit whisper.cpp, optional KI-Textverbesserung.

## Stack
- Kotlin + Jetpack Compose (Material 3) in `app/src/main/java/com/chris/whisperloom/`; IME und Overlay als klassische Views.
- Offline-Erkennung: Submodul `whisper.cpp` @ v1.9.3, JNI in `app/src/main/cpp/`; lokales Textmodell: LiteRT-LM 0.16.1 (fest gepinnt, 0.17.x braucht Kotlin 2.4).
- JDK 17, compileSdk 37, targetSdk 36, minSdk 26, NDK 28.2.13676358, CMake 3.22.1; Versionen: Tabelle „Bauen“ in `README.md`.
- Architektur und Pakete: `README.md` → „Architektur“; UX verbindlich: `docs/design/ux-spec-v3.md`.

## Befehle
| Zweck | Befehl |
|---|---|
| Tests + Lint | `./gradlew testDebugUnitTest lintDebug` (ohne Gerät/Emulator; JVM + Robolectric) |
| Build | `./gradlew assembleDebug` · ohne NDK: `./gradlew -Pwhisperloom.skipNative=true assembleDebug` |
| JNI-Abgleich | `py -3 -X utf8 tools/check_jni_symbols.py` (CI: `python3`) |
| Submodul fehlt | `git submodule update --init --recursive` |
| CI | `.github/workflows/build.yml` bei jedem Push und PR: JNI-Check, Tests + Lint, Debug-, signiertes Release-APK und Play-AAB mit APK-Prüfungen |

## Repo-Etikette
- Default-Branch `main`; Tests in `app/src/test/`, HTTP-Pfade gegen einen lokalen JDK-`HttpServer`, nie gegen echte Anbieter.

## Projekt-Gotchas
- Reine Logik in Android-freien Kotlin-Objekten (JVM-testbar), Compose nur in `ui/`, klassische Views nur in IME und Overlay.
- Bewusst ohne ViewModel-, Navigation-, DI- und HTTP-Client-Bibliothek (`HttpURLConnection`) und ohne `material-icons-*` (eigene Vektor-XML).
- Eine Farbwahrheit: `res/values/colors.xml` (Präfix `loom_`), gespiegelt in `ui/theme/Color.kt`.
- `CHANGELOG.md` und `fastlane/metadata/android/de-DE/changelogs/` sind App-Inhalt (Build kopiert sie nach `assets/patchnotes/`); Format = Vertrag laut `README.md` → „Patchnotes“. `PatchnotesAssetsTest` verlangt `## [x.y.z]` zur `versionName` und die Highlight-Datei zum `versionCode`.
- Paket-ID `com.chris.whisperloom` (GitHub/F-Droid) nie ändern; nur das Play-Bundle heißt `com.whisperloom` (`-Pwhisperloom.applicationId`).
- Release-Keystore nur als GitHub-Secrets bzw. lokal `keystore/` (gitignored); kein Modell (`assets/*.bin`) ins APK, Modelle kommen per Download.
- API-Keys liegen in den Prefs; `allowBackup=false` und die Backup-Regeln schließen sie aus (`ManifestBackupTest`), Verlauf nur in `noBackupFilesDir/history/`.
- `gradle/actions/setup-gradle@v5` bleibt bewusst (v6 = Gradle Terms of Use); Wechsel nur nach Christofs Entscheidung.

## Doku
- `README.md`, `docs/ANLEITUNG.md` (Nutzer), `docs/research/`, `docs/PLAYSTORE-RELEASE.md`, `CHANGELOG.md`
