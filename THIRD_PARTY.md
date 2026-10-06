# Drittkomponenten und Lizenzen

WhisperLoom steht unter der MIT-Lizenz (siehe LICENSE). Enthaltene bzw. genutzte Drittkomponenten:

Im APK enthalten:
- whisper.cpp inkl. ggml (MIT) — https://github.com/ggml-org/whisper.cpp — als Git-Submodul (v1.9.3), statisch in libwhisperloom.so gelinkt
- Jetpack Compose / AndroidX (Apache License 2.0) — https://developer.android.com/jetpack — u. a. Compose UI und Material 3, activity-compose, lifecycle-runtime-compose, WorkManager
- Kotlin-Standardbibliothek, kotlin-reflect und kotlinx.coroutines (Apache License 2.0) — https://kotlinlang.org — Coroutines kommen transitiv über Compose/AndroidX, kotlin-reflect über LiteRT-LM
- LiteRT-LM (Apache License 2.0) — https://github.com/google-ai-edge/LiteRT-LM — `com.google.ai.edge.litertlm:litertlm-android` 0.16.1, rechnet das lokale Textmodell; native Bibliothek liblitertlm_jni.so (arm64)
- Gson (Apache License 2.0) — https://github.com/google/gson — 2.13.2, transitiv über LiteRT-LM
- Material Symbols (Apache License 2.0) — https://fonts.google.com/icons — als eigene Vektor-Drawables

Nicht im APK:
- Whisper-Modelle im ggml-Format (MIT, OpenAI/ggml) — werden NICHT mitgeliefert, sondern auf Wunsch des Nutzers
  zur Laufzeit von https://huggingface.co/ggerganov/whisper.cpp geladen
- Gemma-4-Modelle E2B und E4B im LiteRT-LM-Format (Apache License 2.0, Google) — werden NICHT mitgeliefert, sondern
  auf Wunsch des Nutzers zur Laufzeit von https://huggingface.co/litert-community geladen (feste Revision, SHA-256)
- Nur für Tests: JUnit 4 (Eclipse Public License 1.0), Robolectric (MIT), AndroidX Test und work-testing (Apache License 2.0)
