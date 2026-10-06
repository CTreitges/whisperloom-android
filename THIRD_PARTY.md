# Drittkomponenten und Lizenzen

WhisperLoom steht unter der MIT-Lizenz (siehe LICENSE). Enthaltene bzw. genutzte Drittkomponenten:

Im APK enthalten:
- whisper.cpp inkl. ggml (MIT) — https://github.com/ggml-org/whisper.cpp — als Git-Submodul (v1.9.3), statisch in libwhisperloom.so gelinkt
- Jetpack Compose / AndroidX (Apache License 2.0) — https://developer.android.com/jetpack — u. a. Compose UI und Material 3, activity-compose, lifecycle-runtime-compose, WorkManager
- Kotlin-Standardbibliothek und kotlinx.coroutines (Apache License 2.0) — https://kotlinlang.org — Coroutines kommen transitiv über Compose/AndroidX
- Material Symbols (Apache License 2.0) — https://fonts.google.com/icons — als eigene Vektor-Drawables

Nicht im APK:
- Whisper-Modelle im ggml-Format (MIT, OpenAI/ggml) — werden NICHT mitgeliefert, sondern auf Wunsch des Nutzers
  zur Laufzeit von https://huggingface.co/ggerganov/whisper.cpp geladen
- Nur für Tests: JUnit 4 (Eclipse Public License 1.0), Robolectric (MIT), AndroidX Test und work-testing (Apache License 2.0)
