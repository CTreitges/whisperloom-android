# Recherche-Reports zu WhisperLoom 3.0.0

Stand aller Reports: **2026-09-06** (Recherchetag vor der v3-Implementierung). Die Dateien sind unveränderte Kopien der Arbeitsreports; sie dokumentieren, worauf die Entscheidungen der Version 3.0.0 beruhen, und sind **keine** gepflegte Referenz.

| Datei | Inhalt | Wofür in 3.0.0 genutzt |
|---|---|---|
| [compose-stack.md](compose-stack.md) | Toolchain-Entscheidung für Jetpack Compose + Material 3: AGP/Gradle/Kotlin/compileSdk-Kompatibilität, Compose-BOM, Robolectric auf der JVM, R8/APK-Größe, fertige Gradle-Snippets | Build-Fundament (`build.gradle.kts`, Wrapper, Tests) |
| [api-providers.md](api-providers.md) | Provider- und Modell-Katalog: OpenAI, Groq, Mistral, Together AI, DeepInfra, OpenRouter, Anthropic, Gemini, DeepSeek — Endpunkte, Parameter-Abweichungen, Preise, Free-Tiers, „Wo bekomme ich einen Key?", JSON-Katalog | `api/ProviderCatalog.kt`, Hilfe-Texte, Anleitung Kapitel 7 |
| [whisper-cpp.md](whisper-cpp.md) | Wiederherstellung der On-Device-Erkennung: whisper.cpp-Version, NDK/CMake, Compiler-Flags und Laufzeit-Guard, Decoding-Parameter, Modell-Katalog mit Bytes/SHA-256/RAM, Download-Design, Kontext-Lebenszyklus | `whisper/`, `app/src/main/cpp/`, Anleitung Kapitel 9 |
| [self-hosted.md](self-hosted.md) | Anbieter „Eigener Server": Vergleich OpenAI-kompatibler STT-Server (speaches, whisper.cpp-server, LocalAI …) und LLM-Server (Ollama …), Android-Klartext-Regeln, Timeouts, Tailscale/Caddy, Anleitungskapitel, App-Änderungsliste | `ServerUrlCheck`, `Http`, Network-Security-Config, Anleitung Kapitel 10 |

## Hinweise

- **Preise, Free-Tiers, Modellnamen und Abkündigungen sind zeitabhängig.** Die Reports zitieren den Stand vom 2026-09-06; in den Reports markierte Stellen **[unsicher]**/**(unsicher)**/**[sekundär]** waren schon damals nicht belegbar oder nur aus Drittquellen. Vor jeder Übernahme in App-Texte gegen die verlinkte Anbieter-Doku prüfen.
- Leistungsangaben (Offline-Laufzeiten, CPU-Server) sind Schätzungen aus fremden Benchmarks, nicht auf Zielgeräten gemessen.
- Die Reports enthalten Code-Skizzen und Änderungslisten aus der Planungsphase; maßgeblich ist der tatsächliche Code im Repo. Abweichungen sind im [CHANGELOG](../../CHANGELOG.md) und im Code dokumentiert (z. B. heißt die Prefs-Stufe im Code `refine_mode` mit den Werten `off/polish/beautify/summarize`).
- Die verbindliche UX-Spezifikation der v3-Oberfläche liegt unter [../design/ux-spec-v3.md](../design/ux-spec-v3.md).
- Seit 3.0.0 hat sich manches überholt, das hier noch als offen oder anders entschieden steht — u. a. targetSdk 36 (Play-Vorbereitung), neue Anbieter (Ollama lokal/Cloud ab 3.5.0, ElevenLabs ab 3.8.0) und der Modell-Katalog mit Stand 2026-09-30 (`api/ProviderCatalog.kt`). Die Recherchen dazu liegen nicht im Repo; den aktuellen Stand nennen [README](../../README.md), [Anleitung](../ANLEITUNG.md) und [CHANGELOG](../../CHANGELOG.md).
- Externe Quellen können umgezogen sein. Linkprüfung am 2026-10-05: zwei Mistral-Hilfeartikel in `api-providers.md` (help.mistral.ai) liefern 404; die übrigen Quellen waren erreichbar oder sperren nur automatisierte Abrufe (Anmeldeseiten, Bot-Schutz).
