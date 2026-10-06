# Drittkomponenten und Lizenzen

WhisperLoom steht unter der MIT-Lizenz (siehe LICENSE). Enthaltene bzw. genutzte Drittkomponenten:

Im APK enthalten:
- whisper.cpp inkl. ggml (MIT) — https://github.com/ggml-org/whisper.cpp — als Git-Submodul (v1.9.3), statisch in libwhisperloom.so gelinkt
- Jetpack Compose / AndroidX (Apache License 2.0) — https://developer.android.com/jetpack — u. a. Compose UI und Material 3, activity-compose, lifecycle-runtime-compose, WorkManager
- Kotlin-Standardbibliothek, kotlin-reflect und kotlinx.coroutines (Apache License 2.0) — https://kotlinlang.org — Coroutines kommen transitiv über Compose/AndroidX, kotlin-reflect über LiteRT-LM
- LiteRT-LM (Apache License 2.0) — https://github.com/google-ai-edge/LiteRT-LM — `com.google.ai.edge.litertlm:litertlm-android` 0.16.1, rechnet das lokale Textmodell; native Bibliothek liblitertlm_jni.so (arm64).
  Die Bibliothek enthält statisch gelinkte Drittkomponenten mit eigenen Lizenzen, u. a. Abseil, TensorFlow Lite,
  XNNPACK, KleidiAI, SentencePiece, FlatBuffers (Apache License 2.0), Protocol Buffers, RE2, Darts-clone
  (BSD), ICU4C (Unicode-Lizenz), Eigen 3 (MPL 2.0), Boost (Boost Software License 1.0) und BoringSSL.
  Ihre vollständigen Lizenz- und Copyright-Hinweise liefert Google als `THIRD_PARTY_NOTICE.txt` im AAR mit;
  WhisperLoom legt die Datei unverändert ins APK und ins Repo:
  [`app/src/main/assets/licenses/litertlm-0.16.1-THIRD_PARTY_NOTICE.txt`](app/src/main/assets/licenses/litertlm-0.16.1-THIRD_PARTY_NOTICE.txt)
  (2.053.178 Byte, im APK komprimiert rund 270 KB). In der App verweisen „Über WhisperLoom“ und die Hilfe darauf.
- Gson (Apache License 2.0) — https://github.com/google/gson — 2.13.2, transitiv über LiteRT-LM
- Material Symbols (Apache License 2.0) — https://fonts.google.com/icons — als eigene Vektor-Drawables

Nicht im APK:
- Whisper-Modelle im ggml-Format (MIT, OpenAI/ggml) — werden NICHT mitgeliefert, sondern auf Wunsch des Nutzers
  zur Laufzeit von https://huggingface.co/ggerganov/whisper.cpp geladen
- Gemma-4-Modelle E2B und E4B im LiteRT-LM-Format (Apache License 2.0, Google) — werden NICHT mitgeliefert, sondern
  auf Wunsch des Nutzers zur Laufzeit von https://huggingface.co/litert-community geladen (feste Revision, SHA-256)
- Nur für Tests: JUnit 4 (Eclipse Public License 1.0), Robolectric (MIT), AndroidX Test und work-testing (Apache License 2.0)
