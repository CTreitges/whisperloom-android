> **Historisch — Recherche-Archiv, Stand 2026-09-06.** Keine gepflegte Referenz: maßgeblich sind Code und [CHANGELOG](../../CHANGELOG.md); Einordnung in der [Übersicht](README.md).

# WhisperLoom — Provider „Eigener Server" (Recherche + Anleitung + App-Änderungsliste)

Stand: 2026-09-06 · Ziel-Maschine für spätere Installation: Oracle-VPS aarch64 (4 Kerne, 24 GB RAM, Ubuntu 24.04, Caddy, systemd-User-Services).

Alle Versions-/Verhaltensaussagen stammen aus den verlinkten Quellen (abgerufen 2026-09-06). Unsicheres ist als **(unsicher)** markiert; Leistungszahlen für den VPS sind **Schätzungen** aus fremden ARM-Benchmarks, nicht gemessen.

---

## 0. Ist-Zustand der App (gelesen)

| Datei | Relevantes Verhalten |
|---|---|
| `app/src/main/java/com/chris/whisperloom/api/Http.kt` | `HttpURLConnection`, `CONNECT_TIMEOUT_MS = 15_000`, `READ_TIMEOUT_MS = 90_000` (Konstanten, nicht konfigurierbar). Setzt **immer** `Authorization: Bearer $apiKey` — auch bei leerem Key. `endpoint(baseUrl, path)` = `baseUrl.trimEnd('/') + path`. Fehlertext aus `{"error":{"message":…}}`, sonst erste 200 Zeichen. |
| `api/ApiTranscriber.kt` | `if (apiKey.isBlank()) throw ApiNotConfiguredException()`. Multipart-Felder: `model`, `response_format=json`, `language` (nur wenn ≠ `auto`), `prompt` (optional), `file` als `audio.wav` (`Content-Type: audio/wav`, 16 kHz Mono PCM — `AudioUtils.SAMPLE_RATE = 16_000`). Liest `text` aus der Antwort. Pfad: `/audio/transcriptions` → Base-URL muss auf `/v1` enden. |
| `api/TextRefiner.kt` | Gleicher `baseUrl`/`apiKey` wie Transkription (aus `TranscriptionEngine`), `POST /chat/completions`, `temperature: 0`, liest `choices[0].message.content`. `if (apiKey.isBlank()) throw ApiNotConfiguredException()`. |
| `api/ApiErrors.kt` | `ApiNotConfiguredException("Kein API-Key hinterlegt")`, `ApiHttpException(code, detail)`, `ApiNetworkException(cause.message)` — die rohe `IOException`-Meldung landet im UI (`toast(e.message)` in `FloatingMicService`, `statusView.text = e.message` in der IME). `isRetryable()`: Netzfehler, 408/429/≥500. |
| `TranscriptionEngine.kt` | `isConfigured()` = `apiKey.isNotBlank()`; `transcribe()` wirft bei leerem Key; nutzt **eine** Base-URL für STT und LLM. |
| `Prefs.kt` | `apiBaseUrl` (Default `https://api.openai.com/v1`), `apiKey`, `apiModel` (`gpt-4o-transcribe`), `apiPrompt`, `llmModel` (`gpt-4o-mini`), `llmPolish`, … Keine Timeout-, Preset- oder getrennten LLM-Server-Felder. |
| `AndroidManifest.xml` | Kein `android:networkSecurityConfig`, kein `usesCleartextTraffic`. `targetSdk 35` → Klartext-HTTP ist **standardmäßig blockiert** (siehe §3). `res/xml/` enthält nur `accessibility_service_config.xml` und `method.xml`. |
| `SettingsActivity.kt` | Freitext-`EditText` für URL/Key/Modell/Prompt/LLM-Modell; leere URL/Modell fallen auf Defaults zurück. Keine Validierung. |

Konsequenz: Ohne Änderungen scheitert „Eigener Server" an (1) leerem Key, (2) `http://` im LAN, (3) 90 s Read-Timeout bei CPU-Servern, (4) STT- und LLM-Server auf derselben Base-URL.

---

## 1. OpenAI-kompatible STT-Server (Vergleich)

### 1.1 Übersichtstabelle

| Server | Endpunkt `/v1/audio/transcriptions` | Felder `model`/`language`/`prompt`/`response_format` | API-Key | WAV ok | CPU-only | aarch64-Image | Einschätzung für WhisperLoom |
|---|---|---|---|---|---|---|---|
| **speaches** (ex faster-whisper-server) | ja, nativ | alle vier; `prompt` → `initial_prompt` | optional (`API_KEY`), ohne Env offen | ja | ja (`latest-cpu`, `WHISPER__COMPUTE_TYPE=int8`) | **ja** (Workflow baut `linux/amd64,linux/arm64`) | **Empfehlung #1** (voll kompatibel, Key optional) |
| **hwdsl2/whisper-server** (faster-whisper) | ja, nativ | alle vier | `WHISPER_API_KEY`; Neuinstallation mit Volume **erzeugt automatisch einen Key** | ja | ja (int8 Default) | **ja** (amd64+arm64) | Empfehlung #2 (junges Projekt, 04/2026; Lizenz „Other") |
| **whisper.cpp `whisper-server`** | nur `/inference`; per `--inference-path /v1/audio/transcriptions` umbiegbar | `language`, `prompt`, `response_format` ja; `model` wird **ignoriert** | **keiner** eingebaut → Reverse-Proxy nötig | ja (16 kHz WAV; anderes nur mit `--convert`+ffmpeg) | ja (Kernzweck) | ja (Quelle/Docker `whisper.cpp:main`) | Empfehlung #3 (leichtgewichtig, aber Default-Sprache `en` beachten) |
| **LocalAI** | ja | `model`, `language`, `prompt`, `response_format` | `LOCALAI_API_KEY` optional, ohne Key offen | ja | ja (`latest-cpu`) | ja (Dockerfile arm64) | ok, aber schwergewichtiger All-in-one |
| **onerahmet/openai-whisper-asr-webservice** | **nein** — nur `POST /asr` mit Feld `audio_file`, Query-Params `language`, `output` | nicht OpenAI-Schema | keiner | ja | ja | ja (amd64+arm64) | **nicht kompatibel** ohne Adapter → nicht empfehlen |
| **vLLM** (`vllm serve openai/whisper-large-v3-turbo`) | ja | `language`, `prompt`, `response_format`, `stream` | `--api-key` optional | ja | GPU-orientiert, CPU-STT nicht dokumentiert | — | für VPS ungeeignet |
| **achetronic/parakeet** (NVIDIA Parakeet TDT 0.6B ONNX) | ja | `language` ja, `prompt` **wird ignoriert** | `PARAKEET_API_KEY` optional | ja | ja | Image nur amd64 (arm64 via ONNX-Runtime selbst bauen) | interessant, aber kein Prompt/Vokabular |
| WhisperX-Server | `/v1/audio/transcriptions` (Diarisierung/pyannote) | — | — | schwer, GPU | — | für Diktat überdimensioniert |

Quellen: speaches [GitHub](https://github.com/speaches-ai/speaches), [Installation](https://speaches.ai/installation/), [Workflow platforms](https://raw.githubusercontent.com/speaches-ai/speaches/master/.github/workflows/docker-build-and-push.yaml), [config.py via Context7](https://github.com/speaches-ai/speaches/blob/master/src/speaches/config.py); hwdsl2 [README](https://github.com/hwdsl2/docker-whisper); whisper.cpp [server README](https://github.com/ggml-org/whisper.cpp/blob/master/examples/server/README.md), [server.cpp](https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/examples/server/server.cpp); LocalAI [Audio-to-Text](https://localai.io/docs/features/audio-to-text/), [Authentication](https://localai.io/docs/features/authentication/); whisper-asr-webservice [Endpoints](https://ahmetoner.com/whisper-asr-webservice/endpoints/), [Run](https://ahmetoner.com/whisper-asr-webservice/run/); vLLM [Speech-to-Text](https://docs.vllm.ai/en/latest/serving/online_serving/speech_to_text/); parakeet [README](https://github.com/achetronic/parakeet/blob/master/README.md).

### 1.2 speaches (Detail)

- Aktuelles Release-Tag: `v0.9.0-rc.3` (2025-12-27); Docker-Tags `latest-cpu`, `latest-cuda`, `0.9.0-rc.3-cpu` (~1,2 GB). Quelle: [GitHub Releases API](https://api.github.com/repos/speaches-ai/speaches/releases/latest), [Railway-Template](https://railway.com/deploy/speaches).
- Start (CPU, Doku wörtlich):
  ```bash
  docker run --rm --detach --publish 8000:8000 --name speaches \
    --volume hf-hub-cache:/home/ubuntu/.cache/huggingface/hub \
    ghcr.io/speaches-ai/speaches:latest-cpu
  ```
- Konfiguration per Env (Pydantic, verschachtelt mit `__`): `API_KEY` (wenn gesetzt, Pflicht für alle API-Requests; `/health`, `/docs`, UI bleiben offen), `WHISPER__COMPUTE_TYPE=int8`, `WHISPER__CPU_THREADS=4`, `WHISPER__INFERENCE_DEVICE=cpu`, `PRELOAD_MODELS=[…]` (Download beim Start), `ENABLE_UI=false`, `UVICORN_HOST/PORT` (Default `0.0.0.0:8000`). Quelle: [config.py](https://github.com/speaches-ai/speaches/blob/master/src/speaches/config.py), [Configuration](https://speaches.ai/configuration/).
- Modellnamen = Hugging-Face-Repo-IDs im CTranslate2-Format, z. B. `Systran/faster-whisper-large-v3`, `Systran/faster-whisper-medium`, `Systran/faster-distil-whisper-small.en`; Alias-Datei `model_aliases.json` mappt `whisper-1` → `Systran/faster-whisper-large-v3`. Download: `curl -X POST "$BASE/v1/models/Systran/faster-whisper-medium"`, Liste: `GET /v1/models`. Quelle: [model-discovery](https://github.com/speaches-ai/speaches/blob/master/docs/usage/model-discovery.md). Für large-v3-turbo als CT2 existiert das Community-Repo `deepdml/faster-whisper-large-v3-turbo-ct2` ([HF](https://huggingface.co/deepdml/faster-whisper-large-v3-turbo-ct2/discussions/3)) **(unsicher, ob speaches es ohne Weiteres listet — vor Einsatz testen)**.
- Request-Mapping: `prompt` → `initial_prompt`, `language`, `temperature`, `response_format` (Default `json`) → direkt an `faster_whisper.BatchedInferencePipeline.transcribe()`. Quelle: [executors/whisper.py](https://github.com/speaches-ai/speaches/blob/master/src/speaches/executors/whisper.py).
- aarch64: Der Build-Workflow setzt `platforms: linux/amd64,linux/arm64`; das `-cpu`-Image wird also für arm64 veröffentlicht. CTranslate2 liefert offizielle `manylinux_2_28_aarch64`-Wheels (v4.8.2, 2026-08-31). Quelle: [Workflow](https://raw.githubusercontent.com/speaches-ai/speaches/master/.github/workflows/docker-build-and-push.yaml), [PyPI ctranslate2](https://pypi.org/project/ctranslate2/#files). Bekannter arm64-Bug betrifft nur das **CUDA**-Image (CT2 ohne CUDA, [Issue #620](https://github.com/speaches-ai/speaches/issues/620)) — für CPU irrelevant.

### 1.3 hwdsl2/whisper-server (Detail)

- Image `hwdsl2/whisper-server` (`:latest` CPU, `:cuda` nur amd64), `linux/amd64` + `linux/arm64`. Start: `docker run --name whisper --restart=always -v whisper-data:/var/lib/whisper -p 9000:9000 -d hwdsl2/whisper-server`.
- Env: `WHISPER_MODEL` (tiny…large-v3, `large-v3-turbo`, `turbo`; Default `base`), `WHISPER_COMPUTE_TYPE` (CPU-Default `int8`), `WHISPER_API_KEY`, `WHISPER_LANGUAGE` (Default `auto`), `WHISPER_THREADS`.
- **Achtung Key:** „Fresh installs with a mounted `/var/lib/whisper` volume auto-generate an API key" — Key aus dem Container-Log lesen oder `WHISPER_API_KEY` explizit setzen.
- Felder: `file`, `model` (Pflicht, Wert egal — `whisper-1`), `language`, `prompt`, `response_format` (`json`/`text`/`verbose_json`/`srt`/`vtt`), `stream`. Formate „mp3, m4a, wav, webm, ogg, flac …" (ffmpeg).
- RAM laut README: small ≈ 1,5 GB, medium ≈ 5 GB, large-v3-turbo ≈ 6 GB **(unsicher, wirkt für int8 hoch)**.
- Projekt erst seit 2026-04 (101 Stars, letzter Push 2026-08-18, Lizenz „Other"). Quelle: [README](https://raw.githubusercontent.com/hwdsl2/docker-whisper/master/README.md), [Repo-API](https://api.github.com/repos/hwdsl2/docker-whisper).

### 1.4 whisper.cpp `whisper-server` (Detail)

- Endpunkt Default `/inference`; mit `--inference-path /v1/audio/transcriptions` OpenAI-Pfad. Optionen: `--host` (Default `127.0.0.1`!), `--port 8080`, `-m ggml-….bin`, `-t N` Threads, `-l LANG` (Default **`en`**, `auto` möglich), `--convert` (ffmpeg für Nicht-WAV), `--vad`, `--no-gpu`. Quelle: [server README](https://github.com/ggml-org/whisper.cpp/blob/master/examples/server/README.md).
- Multipart-Felder, die `server.cpp` liest: `file` (Pflicht), `language`, `detect_language`, `prompt`, `carry_initial_prompt`, `response_format`, `temperature`, `temperature_inc`, Decoder-Parameter (`beam_size`, `best_of`, …), VAD-Parameter. **Unbekannte Felder (z. B. `model`) werden ignoriert.** Antwort bei `response_format=json`: `{"text": "…"}` — exakt was `ApiTranscriber` liest. Fehler: HTTP 400 (`file` fehlt / „failed to read audio data"), 500, 503 (Modell lädt). **Keine Authentifizierung**, CORS `*`. Quelle: [server.cpp](https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/examples/server/server.cpp).
- WAV-Anforderung: „WAV Files are passed to the inference model via http requests"; die App sendet 16 kHz Mono PCM-WAV → passt ohne `--convert`.
- **Stolperfalle Sprache:** Wenn WhisperLoom bei „Automatisch erkennen" das Feld `language` weglässt, nimmt whisper-server seinen Startwert (Default `en`). Server daher mit `-l auto` (oder `-l de`) starten.
- Modelle (`models/download-ggml-model.sh <name>`): `small` 466 MiB, `medium` 1,5 GiB, `large-v3-turbo` 1,5 GiB, `large-v3-turbo-q5_0` 547 MiB, `large-v3-q5_0` 1,1 GiB. Multilingual, sofern kein `.en`. Quelle: [models/README.md](https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/models/README.md).
- Docker: `docker run -it --rm -p 8080:8080 -v path/to/models:/models whisper.cpp:main "whisper-server --host 0.0.0.0 -m /models/ggml-base.bin"` (README-Muster; Image `ghcr.io/ggml-org/whisper.cpp:main`). Quelle: [README Docker](https://github.com/ggml-org/whisper.cpp/blob/master/README.md).
- Letztes Release-Tag laut GitHub-API: `b4938` (2026-08-20) **(unsicher — Tag-Schema wirkt wie llama.cpp-Build-Nummern; ggf. Versionsschema geändert)**.

### 1.5 LocalAI (Detail)

- `curl http://localhost:8080/v1/audio/transcriptions -F file=@x.wav -F model=whisper-1`; optional `language`, `prompt`, `response_format` (`json` Default). Backends: whisper.cpp (Default), faster-whisper, parakeet-cpp, moonshine. Modell per YAML/Galerie (z. B. `github:mudler/LocalAI/gallery/whisper-base.yaml@master` als `whisper-1`). Auth: `LOCALAI_API_KEY=k1,k2`; ohne Key „does not restrict requests". Images `localai/localai:latest-cpu`, arm64 im Dockerfile. Quelle: [Audio-to-Text](https://localai.io/docs/features/audio-to-text/), [Authentication](https://localai.io/docs/features/authentication/), [Container-Doku](https://localai.io/docs/installation/containers/).

### 1.6 OpenAI-Referenz (Kompatibilitätsbasis)

`POST /v1/audio/transcriptions`, multipart: `file`, `model`, `language`, `prompt` (Whisper: ≤ 224 Tokens), `response_format` (`json`/`text`/`srt`/`verbose_json`/`vtt`; für `gpt-4o-transcribe` nur `json`), `temperature`. Quelle: [OpenAI API Reference](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create).

---

## 2. OpenAI-kompatible LLM-Server für die Textveredelung

| Server | Base-URL | Key | Start | Modellname | Hinweise |
|---|---|---|---|---|---|
| **Ollama** (v0.33.3, 2026-09-02, `ollama-linux-arm64.tar.zst`) | `http://host:11434/v1` | „required but ignored" — Header darf fehlen | `curl -fsSL https://ollama.com/install.sh \| sh`; LAN-Bind via `Environment="OLLAMA_HOST=0.0.0.0:11434"` im systemd-Drop-in | `qwen3:8b`, `gemma3:12b`, `gemma3:4b`, `mistral-nemo:12b`, `llama3.1:8b` | `/v1/chat/completions` unterstützt `temperature`, `max_tokens`, `response_format`, `reasoning_effort` (`"none"` schaltet Thinking bei Qwen3 & Co. ab) |
| **llama.cpp `llama-server`** | `http://host:8080/v1` | `--api-key KEY` optional (mehrere komma-getrennt) | `llama-server -hf <user>/<repo>:<quant> --host 0.0.0.0 --port 8080 -c 4096 -t 4` | Pfad aus `-m` bzw. `--alias NAME`; Modellfeld ansonsten beliebig | leichtgewichtig, ein Modell pro Prozess |
| **LM Studio** | `http://host:1234/v1` | keiner (Key wird ignoriert) | GUI: Server starten, Bind `0.0.0.0` für LAN | geladener Modellname (`GET /v1/models`) | Desktop-Tool (Windows/Mac), keine Auth → nur LAN/VPN |
| **vLLM** | `http://host:8000/v1` | `--api-key` optional | `vllm serve <hf-model>` | HF-ID | GPU-orientiert, für VPS-CPU nicht sinnvoll |

Quellen: Ollama [OpenAI-Kompatibilität](https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx), [openai.go (reasoning_effort none)](https://github.com/ollama/ollama/blob/main/openai/openai.go), [FAQ OLLAMA_HOST](https://github.com/ollama/ollama/blob/main/docs/faq.mdx), [Releases](https://api.github.com/repos/ollama/ollama/releases/latest); llama.cpp [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md); LM Studio [OpenAI-Compat](https://lmstudio.ai/docs/developer/openai-compat), [Markaicode-Setup](https://markaicode.com/lm-studio-api-server-openai-compatible/).

**Modellwahl für deutsche Zeichensetzungs-/Grammatikkorrektur (Ollama-Library, abgerufen 2026-09-06):**

| Modell | Größe | Kontext | Bemerkung |
|---|---|---|---|
| `qwen3:8b` (Default-Tag) | 5,2 GB | 40K | starke Mehrsprachigkeit; **Thinking standardmäßig an** → in der App `reasoning_effort: "none"` senden (sonst Latenz + evtl. Denk-Text). Quelle: [ollama.com/library/qwen3](https://ollama.com/library/qwen3) |
| `gemma3:4b` / `gemma3:12b` | 3,3 / 8,1 GB | 128K | „over 140 languages"; 4b ist der Schnellste mit brauchbarem Deutsch, 12b auf 4 Kernen langsam. Quelle: [gemma3](https://ollama.com/library/gemma3) |
| `gemma4` (12B/26B/31B) | — | — | existiert (24,3 M Pulls), für CPU-4-Kerne zu groß. Quelle: [Suche gemma4](https://ollama.com/search?q=gemma4) |
| `mistral-nemo:12b` | 7,1 GB | 128K | Mistral-Modelle gelten als stark in europäischen Sprachen; 12B auf CPU ≈ 3–4 tok/s. Quelle: [mistral-nemo](https://ollama.com/library/mistral-nemo), [Vergleich](https://www.promptquorum.com/local-llms/multilingual-local-llms) |
| `llama3.1:8b` | ~4,9 GB | 128K | solide, Deutsch etwas schwächer als Qwen3/Mistral (Community-Einschätzung, **unsicher**) |

Empfehlung: **`qwen3:8b` mit `reasoning_effort: "none"`** als Default, `gemma3:4b` als „schnell"-Alternative. Vergleiche fanden sich nur für Übersetzung, nicht für Zeichensetzungskorrektur ([glukhov.org](https://www.glukhov.org/llm-hosting/ollama/translation-quality-comparison-llms-on-ollama/)) → Qualität für Diktat-Cleanup selbst testen.

---

## 3. Android-Anforderungen

### 3.1 Klartext-HTTP

- Ab `targetSdk 28` ist Klartext standardmäßig verboten (`base-config cleartextTrafficPermitted="false"`). Quelle: [Network security configuration](https://developer.android.com/privacy-and-security/security-config).
- `android:usesCleartextTraffic="true"` global: funktioniert, gilt aber für alle Hosts; die Manifest-Doku sagt: „This flag is ignored on Android 7.0 (API level 24) and above if an Android Network Security Config is present" und „This attribute is getting deprecated and will be ignored for apps targeting API levels 38 and above." → **Network Security Config statt Manifest-Flag.** Quelle: [application-element](https://developer.android.com/guide/topics/manifest/application-element).
- **IP-Adressen in `<domain>`:** Die offizielle Doku nennt nur Hostnamen. Im AOSP-Code wird der `<domain>`-Text nur `trim().toLowerCase()` (keine Validierung) und in `ApplicationConfig.getConfigForHostname()` per String-Vergleich (exakt bzw. `includeSubdomains` = endet mit `"." + domain`) gegen den Hostnamen aus der URL gematcht — für `http://192.168.1.50:8000` ist das der String `192.168.1.50`. **Einzelne IP-Literale funktionieren also als exakte Einträge; CIDR-Bereiche (192.168.0.0/16, 100.64.0.0/10) sind nicht ausdrückbar.** Quelle: [XmlConfigSource.java](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/security/net/config/XmlConfigSource.java), [ApplicationConfig.java](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/security/net/config/ApplicationConfig.java). Ein alter Flutter-Report ([#65841](https://github.com/flutter/flutter/issues/65841)) meldet einen Crash mit IP-Einträgen — offen, ohne Reproduktion, vermutlich anderes Problem **(unsicher)**.
- `includeSubdomains="true"` auf einer **TLD-artigen Endung** wie `local` matcht jeden `xyz.local`-Host (Suffix-Logik), ebenso `home.arpa`, `lan`, `internal`, `ts.net`.

**Konsequenz / Empfehlung:** Da der Nutzer eine beliebige LAN-IP eintippt, ist eine Whitelist nur mit `base-config cleartextTrafficPermitted="true"` praktikabel. Kompensation in der App: `http://` nur akzeptieren, wenn der Host lokal/privat ist (`localhost`, `127.*`, `10/8`, `172.16/12`, `192.168/16`, `169.254/16`, Tailscale-CGNAT `100.64/10`, Endungen `.local/.lan/.home.arpa/.internal`), sonst Warnung „Unverschlüsselt über das Internet — bitte https oder VPN". Alternative (strenger, weniger komfortabel): Klartext gar nicht erlauben und nur `https://` (Caddy/Tailscale Serve) unterstützen.

### 3.2 Erreichbarkeit von außen

- **Tailscale:** Handy + Server im selben Tailnet, stabile `100.x.y.z`-IP, kein offener Port. `tailscale serve --bg --https=443 localhost:8000` gibt dem Dienst ein gültiges TLS-Zertifikat unter `https://<maschine>.<tailnet>.ts.net` (MagicDNS + HTTPS-Zertifikate im Admin aktivieren). Serve = nur Tailnet, Funnel = öffentlich. Quelle: [Tailscale Serve](https://tailscale.com/kb/1242/tailscale-serve), [DNS](https://tailscale.com/docs/reference/dns-in-tailscale).
- **WireGuard:** gleiches Prinzip mit eigenem Tunnel (`10.x`-Adressen) — kein Zusatzaufwand in der App.
- **Caddy als TLS-Reverse-Proxy mit Bearer-Auth** (whisper-server hat keine Auth): Matcher `header Authorization "Bearer …"` + `not {…}` → `respond 401`. Alternative `basic_auth` (bcrypt via `caddy hash-password`; seit v2.8.0 heißt die Direktive `basic_auth`) — WhisperLoom sendet aber Bearer, daher Bearer-Matcher wählen. Quelle: [Caddy matchers](https://caddyserver.com/docs/caddyfile/matchers), [basic_auth](https://caddyserver.com/docs/caddyfile/directives/basic_auth), [Beispiel Bearer-Auth vor Ollama](https://medium.com/@dmitrywat/securing-your-ollama-instance-with-caddy-bearer-authentication-a2f131ae0101).

### 3.3 Timeouts

`READ_TIMEOUT_MS = 90_000` reicht für Cloud-APIs, nicht für CPU-Server: Ein 5-Minuten-Chunk (`AudioChunks` stückelt bei 5 min) bei RTF 1,5 braucht ≈ 7,5 min. → Read-Timeout konfigurierbar (Preset „Eigener Server" Default 600 s), Connect-Timeout kann bei 15 s bleiben.

### 3.4 Leerer API-Key

Ollama ignoriert den Key, whisper-server kennt keinen, speaches/LocalAI/hwdsl2 sind ohne Env offen. → Bei leerem Key den `Authorization`-Header **weglassen** (nicht `Bearer ` senden — manche Server werten ein leeres Token als ungültig).

---

## 4. Oracle-VPS aarch64 — was läuft realistisch CPU-only?

Hardware: 4 Ampere-Altra-Kerne (Neoverse-N1, NEON, kein SVE), 24 GB RAM. Keine GPU.

**Belegte Referenzpunkte (fremde ARM-Systeme):**

| System | Modell | Ergebnis | Quelle |
|---|---|---|---|
| RK3588 (4×A76 2,4 GHz + 4×A55, `-t 8`), whisper.cpp | small | RTF 0,47 (5-min-Audio in 142 s), 889 MB | [turingpi.com](https://turingpi.com/whisper-cpp-piper-tts-arm64-turing-pi-rk3588/) (06/2026) |
| ebd. | medium | RTF 1,51, 2,14 GB | ebd. |
| ebd. | large-v3-turbo | JFK-Sample (11 s) in 30,9 s ≈ RTF 2,8, 1,8 GB | ebd. |
| Raspberry Pi 5, whisper.cpp | small | ≈ 0,4–0,6× Echtzeit (RTF ≈ 1,7–2,5) | [smartscope](https://smartscope.blog/en/generative-ai/foundations/whisper-local-cpu-implementation/) |
| faster-whisper int8 (x86, 13-min-Audio) | large-v3-turbo | 19,6 s, 1 545 MB Peak (GPU-nahe Hardware, nicht ARM) | [HF-Diskussion](https://huggingface.co/deepdml/faster-whisper-large-v3-turbo-ct2/discussions/3) |
| Oracle A1 4 OCPU/24 GB, Ollama/llama.cpp | 7B Q4_K_M | Ollama ≈ 5–8 tok/s, llama.cpp ≈ 8–12 tok/s; 13B ≈ 3–4 tok/s | [easecloud (02/2026)](https://blog.easecloud.io/ai-cloud/launch-oracle-cloud-llms-in/) |

**Schätzung für den VPS (Ampere Altra ≈ A76-Klasse, 4 Kerne, whisper.cpp / faster-whisper int8):**

| Modell | RTF geschätzt | 20-s-Diktat | Deutsch-Qualität |
|---|---|---|---|
| `small` (whisper.cpp) / `Systran/faster-whisper-small` | 0,3–0,6 | 6–12 s | mäßig (Deutsch merklich schwächer als medium) |
| `medium` bzw. `medium-q5_0` | 1,0–1,8 | 20–36 s | gut |
| `large-v3-turbo` (ggml q5_0 / CT2 int8) | 1,5–3 | 30–60 s | sehr gut (nahe large-v3) |
| `large-v3` | > 4 | > 80 s | nicht sinnvoll |

→ **Empfehlung VPS:** speaches `latest-cpu` (arm64-Build, `WHISPER__COMPUTE_TYPE=int8`, `WHISPER__CPU_THREADS=4`) mit `Systran/faster-whisper-medium` als Default und `large-v3-turbo`-CT2 als Qualitätsoption; Fallback whisper.cpp `whisper-server -m ggml-large-v3-turbo-q5_0.bin -t 4 -l auto`. Vor Festlegung **`whisper-bench -t 4`** auf dem VPS laufen lassen (Zahlen oben sind Schätzungen). RAM ist kein Engpass (≤ 3 GB für STT + ≈ 5–6 GB für `qwen3:8b`).
LLM: `qwen3:8b` ≈ 5–8 tok/s → ein 60-Wort-Diktat (≈ 100 Tokens Ausgabe) dauert ≈ 15–20 s zusätzlich; `gemma3:4b` etwa doppelt so schnell. Für spürbar flüssige Veredelung eher `gemma3:4b` oder LLM-Politur aus lassen.

**Nichts installieren** — nur Vorbereitung. Deploy später über den `vps-deploy`-Skill (systemd-User-Service + Caddy-Route).

---

## 5. Provider-Preset „Eigener Server"

Vorschlag: Preset-Enum in `Prefs`/Settings (OpenAI · Groq · Eigener Server), das Defaults setzt und die Validierung steuert. Nur „Eigener Server" schaltet Key-Optionalität, HTTP-Erlaubnis und langes Timeout frei.

| Feld (Prefs-Key) | Default im Preset | Validierung | Hinweis im UI |
|---|---|---|---|
| `apiBaseUrl` (`api_url`) | `http://192.168.1.50:8000/v1` (Platzhalter) | Pflicht; Schema `http`/`https`; endet auf `/v1` (sonst Hinweis „Base-URL endet normalerweise auf /v1"); bei `http` Host muss privat/lokal sein (§3.1) sonst Warnung | „z. B. speaches: http://SERVER:8000/v1 · whisper.cpp: http://SERVER:8080/v1" |
| `apiKey` (`api_key`) | leer | **optional** (`requiresKey = false`) | „Leer lassen, wenn der Server keinen Key verlangt" |
| `apiModel` (`api_model`) | `Systran/faster-whisper-medium` | frei, nicht leer | „speaches: Systran/faster-whisper-… · hwdsl2/LocalAI: whisper-1 · whisper.cpp: beliebig (wird ignoriert)" |
| `apiPrompt` | leer | — | wie bisher |
| `apiReadTimeoutSec` (**neu**) | 600 | 30–1800 | „CPU-Server sind langsam; Zeitüberschreitung in Sekunden" |
| `llmBaseUrl` (**neu**, `llm_url`) | `http://192.168.1.50:11434/v1` | leer = gleiche URL wie STT; sonst wie `apiBaseUrl` | „Ollama: http://SERVER:11434/v1" |
| `llmApiKey` (**neu**, `llm_key`) | leer | optional | — |
| `llmModel` (`llm_model`) | `qwen3:8b` | frei | „Ollama: qwen3:8b, gemma3:4b" |
| `llmNoThinking` (**neu**, optional) | true | — | sendet `reasoning_effort: "none"` (Ollama akzeptiert; OpenAI-Cloud-Modelle ohne Reasoning ignorieren/lehnen ab → nur im Preset „Eigener Server" senden) |
| `language` | wie bisher | — | Bei whisper.cpp-Server: Server mit `-l auto` starten, sonst `en`-Default |

Preset-Defaults (Kotlin-Skizze, in `Prefs.companion`):

```kotlin
enum class Provider(val label: String, val requiresKey: Boolean, val allowsHttp: Boolean,
                    val baseUrl: String, val sttModel: String, val llmModel: String,
                    val readTimeoutSec: Int) {
    OPENAI("OpenAI", true, false, "https://api.openai.com/v1", "gpt-4o-transcribe", "gpt-4o-mini", 90),
    GROQ("Groq", true, false, "https://api.groq.com/openai/v1", "whisper-large-v3-turbo", "llama-3.1-8b-instant", 90),
    SELF_HOSTED("Eigener Server", false, true, "http://192.168.1.50:8000/v1",
                "Systran/faster-whisper-medium", "qwen3:8b", 600),
}
```

Validierungsregeln (JVM-testbar, z. B. `ServerUrlCheck.kt`):

```kotlin
object ServerUrlCheck {
    private val privateHostSuffixes = listOf(".local", ".lan", ".home.arpa", ".internal")

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || privateHostSuffixes.any { h.endsWith(it) }) return true
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || p[0] == 127 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) ||
            (p[0] == 100 && p[1] in 64..127)          // Tailscale CGNAT 100.64.0.0/10
    }

    /** null = ok, sonst Hinweistext fuer das UI. */
    fun problem(baseUrl: String, allowsHttp: Boolean): String? {
        val u = runCatching { java.net.URI(baseUrl.trim()) }.getOrNull()
            ?: return "Ungültige URL"
        if (u.scheme != "http" && u.scheme != "https") return "URL muss mit http:// oder https:// beginnen"
        if (u.host.isNullOrBlank()) return "Kein Host in der URL"
        if (u.scheme == "http" && !allowsHttp) return "Dieser Anbieter braucht https://"
        if (u.scheme == "http" && !isPrivateHost(u.host)) return "Unverschlüsselt über das Internet — https:// oder VPN (Tailscale) verwenden"
        if (!u.path.trimEnd('/').endsWith("/v1")) return "Base-URL endet normalerweise auf /v1"
        return null
    }
}
```

---

## 6. Anleitungskapitel „Eigener Server" (für README / Setup-Hilfe)

### Eigener Server

WhisperLoom spricht die OpenAI-API. Jeder Server, der `POST /v1/audio/transcriptions` (und optional `POST /v1/chat/completions`) anbietet, funktioniert — also auch ein Rechner bei dir zu Hause oder dein VPS. Das Audio verlässt dann nie deine eigene Infrastruktur.

**Du brauchst:** einen Linux-Rechner/VPS mit Docker (x86-64 oder ARM64, ≥ 4 Kerne, ≥ 8 GB RAM; für die Textveredelung zusätzlich ≈ 6 GB) und eine Verbindung vom Handy dorthin (gleiches WLAN, Tailscale/WireGuard oder HTTPS über Caddy).

#### Schritt 1 — Spracherkennung starten (speaches)

```bash
docker run -d --name speaches --restart unless-stopped \
  -p 8000:8000 \
  -v hf-hub-cache:/home/ubuntu/.cache/huggingface/hub \
  -e WHISPER__COMPUTE_TYPE=int8 \
  -e WHISPER__CPU_THREADS=4 \
  -e ENABLE_UI=false \
  -e API_KEY=mein-geheimer-schluessel \
  ghcr.io/speaches-ai/speaches:latest-cpu

# Modell einmalig herunterladen (medium ≈ 1,5 GB; Alternative: Systran/faster-whisper-large-v3)
curl -X POST -H "Authorization: Bearer mein-geheimer-schluessel" \
  http://localhost:8000/v1/models/Systran/faster-whisper-medium
```

`API_KEY` weglassen, wenn der Server nur im eigenen Netz erreichbar ist — dann bleibt das Key-Feld in WhisperLoom leer.

*Alternative (noch kleiner): whisper.cpp*

```bash
git clone https://github.com/ggml-org/whisper.cpp && cd whisper.cpp
cmake -B build && cmake --build build -j --config Release
./models/download-ggml-model.sh large-v3-turbo-q5_0      # 547 MiB; oder: medium
./build/bin/whisper-server -m models/ggml-large-v3-turbo-q5_0.bin \
  --host 0.0.0.0 --port 8080 -t 4 -l auto \
  --inference-path /v1/audio/transcriptions
```

Wichtig: `-l auto` (oder `-l de`), sonst nimmt der Server Englisch an, wenn die App keine Sprache mitschickt. whisper-server hat **keine** Passwortabfrage — nur im LAN/VPN betreiben oder hinter Caddy (Schritt 3).

#### Schritt 2 — Textveredelung starten (optional, Ollama)

```bash
curl -fsSL https://ollama.com/install.sh | sh
sudo systemctl edit ollama            # Drop-in anlegen:
#   [Service]
#   Environment="OLLAMA_HOST=0.0.0.0:11434"
sudo systemctl daemon-reload && sudo systemctl restart ollama
ollama pull qwen3:8b                  # 5,2 GB; schneller: ollama pull gemma3:4b
```

Ollama braucht keinen Key. Auf 4 CPU-Kernen liefert ein 8B-Modell ≈ 5–8 Wörter pro Sekunde — die Veredelung eines längeren Diktats dauert also spürbar; bei Bedarf `gemma3:4b` nehmen oder die LLM-Politur ausschalten.

#### Schritt 3 — Von außen erreichbar machen

**Variante A: Tailscale (empfohlen, kein offener Port).** Tailscale auf Server und Handy installieren, beide im selben Tailnet. Dann in WhisperLoom `http://100.x.y.z:8000/v1` eintragen (die Tailscale-IP des Servers). Mit gültigem Zertifikat: `sudo tailscale serve --bg --https=443 localhost:8000` → `https://<server>.<tailnet>.ts.net/v1` (MagicDNS und HTTPS-Zertifikate im Tailscale-Admin aktivieren).

**Variante B: Caddy mit TLS + Bearer-Token** (wenn der Server ohnehin öffentlich ist, z. B. VPS mit Domain):

```caddyfile
whisper.example.de {
	@unauth not header Authorization "Bearer {env.WHISPERLOOM_TOKEN}"
	respond @unauth "Unauthorized" 401

	handle /v1/audio/* {
		reverse_proxy 127.0.0.1:8000        # speaches (oder 8080 für whisper-server mit --inference-path)
	}
	handle /v1/chat/* {
		reverse_proxy 127.0.0.1:11434       # Ollama
	}
	respond 404
}
```

`WHISPERLOOM_TOKEN` als Umgebungsvariable des Caddy-Dienstes setzen (nicht in die Datei schreiben). In WhisperLoom: Base-URL `https://whisper.example.de/v1`, Key = Token. Vorteil: STT und LLM laufen hinter **einer** URL, so dass in der App keine zweite Base-URL nötig ist. (Für whisper-server statt speaches den `handle_path`-Block auf Port 8080 zeigen lassen; das Ollama-Backend braucht keinen eigenen Key, der Caddy-Token schützt beide.)

#### Schritt 4 — Testen (vom Rechner aus)

```bash
# 3-Sekunden-Test-WAV (16 kHz mono) erzeugen
ffmpeg -f lavfi -i "sine=frequency=440:duration=3" -ar 16000 -ac 1 test.wav

curl -s http://SERVER:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer mein-geheimer-schluessel" \
  -F file=@test.wav -F model=Systran/faster-whisper-medium \
  -F language=de -F response_format=json
# → {"text":"…"}

curl -s http://SERVER:11434/v1/chat/completions -H "Content-Type: application/json" -d '{
  "model":"qwen3:8b","temperature":0,"reasoning_effort":"none",
  "messages":[{"role":"system","content":"Korrigiere nur Zeichensetzung."},
              {"role":"user","content":"hallo das ist ein test ohne punkt und komma"}]}'
```

Bei Fehler: `docker logs speaches` bzw. `journalctl -u ollama`.

#### Schritt 5 — In WhisperLoom eintragen

Einstellungen → Anbieter **Eigener Server**:

| Feld | Wert |
|---|---|
| Base-URL | `http://SERVER:8000/v1` (LAN/Tailscale) oder `https://whisper.example.de/v1` (Caddy) |
| API-Key | leer, oder der in `API_KEY`/Caddy gesetzte Token |
| Modell | `Systran/faster-whisper-medium` (speaches) · `whisper-1` (LocalAI/hwdsl2) · beliebig (whisper.cpp) |
| Zeitüberschreitung | 600 s (CPU-Server brauchen bei langen Aufnahmen Minuten) |
| LLM-Server-URL | `http://SERVER:11434/v1` (Ollama; leer = gleiche URL wie oben) |
| LLM-Modell | `qwen3:8b` |

Hinweis: Unverschlüsseltes `http://` erlaubt WhisperLoom nur zu privaten Adressen (192.168.x.x, 10.x.x.x, 172.16–31.x.x, 100.64–127.x.x/Tailscale, `*.local`). Über das Internet immer `https://` oder VPN.

---

## 7. App-Änderungsliste (mit Snippets)

Scope: nur, was „Eigener Server" braucht. Tests (JVM-Unit, JUnit wie im Repo) sind pro Punkt angegeben.

### 7.1 `Http.kt` — optionaler Key, konfigurierbares Read-Timeout, verständliche Netzfehler

```kotlin
internal object Http {
    const val CONNECT_TIMEOUT_MS = 15_000
    const val DEFAULT_READ_TIMEOUT_MS = 90_000

    fun post(
        url: String,
        apiKey: String,
        contentType: String,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        write: (java.io.OutputStream) -> Unit,
    ): String {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                // Eigene Server (Ollama, whisper.cpp) brauchen keinen Key:
                // Header dann ganz weglassen statt "Bearer " zu senden.
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", contentType)
            }
        } catch (e: IOException) {
            throw ApiNetworkException(e)
        }
        // … Rest unverändert
    }
}
```

Fehlertexte in `ApiErrors.kt` (statt roher `cause.message`):

```kotlin
class ApiNetworkException(cause: IOException) : RuntimeException(describe(cause), cause) {
    companion object {
        fun describe(e: IOException): String = when {
            e is java.net.UnknownHostException -> "Server nicht gefunden – Hostname/IP prüfen"
            e is java.net.ConnectException -> "Server nicht erreichbar – läuft er, stimmt der Port, gleiches WLAN/VPN?"
            e is java.net.SocketTimeoutException -> "Zeitüberschreitung – Server zu langsam? Timeout in den Einstellungen erhöhen"
            e is javax.net.ssl.SSLException -> "TLS-Fehler – Zertifikat des Servers ungültig"
            e.message?.contains("Cleartext HTTP traffic", ignoreCase = true) == true ->
                "Unverschlüsseltes http:// ist zu dieser Adresse nicht erlaubt – https:// oder lokale Adresse nutzen"
            else -> e.message ?: "Netzwerkfehler"
        }
    }
}
```

HTTP-Statushinweise (in `Http.errorDetail` oder beim Werfen der `ApiHttpException`): `401/403` → „Server verlangt einen (anderen) API-Key", `404` → „Endpunkt nicht gefunden – Base-URL muss auf /v1 enden (whisper.cpp: `--inference-path /v1/audio/transcriptions`)", `400` mit „failed to read audio data" → whisper.cpp ohne `--convert` (sollte mit WAV nicht auftreten), `503` → bleibt retryable (Modell lädt).
**Tests:** `ApiErrorsTest` erweitern (Mapping je Exception-Typ; `isRetryable` unverändert grün). `HttpTest` mit lokalem `com.sun.net.httpserver.HttpServer` (JDK, keine Abhängigkeit): prüft, dass ohne Key **kein** `Authorization`-Header ankommt und mit Key `Bearer x`.

### 7.2 `ApiTranscriber.kt` / `TextRefiner.kt` / `TranscriptionEngine.kt` — Key optional

- `if (apiKey.isBlank()) throw ApiNotConfiguredException()` in `ApiTranscriber.transcribe`, `TextRefiner.refine` und `TranscriptionEngine.transcribe` **entfernen**; `TranscriptionEngine.isConfigured()` = `apiBaseUrl.isNotBlank() && (!provider.requiresKey || apiKey.isNotBlank())`.
- `ApiNotConfiguredException`-Text anpassen: „Anbieter nicht eingerichtet (Base-URL/Key prüfen)".
- `readTimeoutMs = prefs.apiReadTimeoutSec * 1000` an `Http.post` durchreichen.
- `TextRefiner` bekommt eigene `baseUrl`/`apiKey` (aus `prefs.llmBaseUrl.ifBlank { prefs.apiBaseUrl }`, `prefs.llmApiKey.ifBlank { prefs.apiKey }`) und optional `"reasoning_effort": "none"` (nur wenn `provider == SELF_HOSTED`, da OpenAI-Modelle ohne Reasoning den Parameter nicht kennen — **unsicher**, ob OpenAI ihn bei `gpt-4o-mini` ablehnt; deshalb auf das Preset begrenzen).
- Modellname bleibt Freitext (bereits so); für whisper.cpp ist der Wert egal, wird ignoriert.
- Endpunkt-Pfad: **nicht** konfigurierbar machen (YAGNI) — whisper.cpp löst das serverseitig per `--inference-path`, whisper-asr-webservice wird nicht unterstützt.
**Tests:** `RefinePromptTest` bleibt; neuer Test, dass das Payload `reasoning_effort` nur im Self-Hosted-Fall enthält; `TranscriptionEngine.isConfigured`-Logik als reine Funktion (`ProviderConfig.isComplete(...)`) testen.

### 7.3 `Prefs.kt` — neue Felder

```kotlin
var provider: Provider
    get() = Provider.entries.firstOrNull { it.name == sp.getString(KEY_PROVIDER, null) } ?: Provider.OPENAI
    set(v) = sp.edit().putString(KEY_PROVIDER, v.name).apply()

var apiReadTimeoutSec: Int
    get() = sp.getInt(KEY_READ_TIMEOUT, provider.readTimeoutSec)
    set(v) = sp.edit().putInt(KEY_READ_TIMEOUT, v.coerceIn(30, 1800)).apply()

var llmBaseUrl: String   // leer = wie apiBaseUrl
    get() = sp.getString(KEY_LLM_URL, "") ?: ""
    set(v) = sp.edit().putString(KEY_LLM_URL, v).apply()

var llmApiKey: String
    get() = sp.getString(KEY_LLM_KEY, "") ?: ""
    set(v) = sp.edit().putString(KEY_LLM_KEY, v).apply()
```

Migration: bestehende Nutzer haben keinen `provider` → Default `OPENAI`, Verhalten unverändert.

### 7.4 `AndroidManifest.xml` + `res/xml/network_security_config.xml`

Manifest:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    … >
```

**Variante B (empfohlen, da IP-Whitelists ohne CIDR nicht praktikabel sind — App prüft privaten Host selbst, §5):**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Klartext-HTTP nur, damit ein eigener Whisper-/Ollama-Server im LAN oder
     per Tailscale (100.64.0.0/10) ohne TLS erreichbar ist. Android kennt keine
     IP-Bereiche in <domain>, deshalb global erlaubt; die App akzeptiert http://
     nur zu privaten Adressen (ServerUrlCheck). -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Cloud-Anbieter bleiben strikt https -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">openai.com</domain>
        <domain includeSubdomains="true">groq.com</domain>
    </domain-config>
</network-security-config>
```

**Variante A (streng, nur wenn man ausschließlich Hostnamen/`.local`/einzelne IPs zulassen will):**

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain>localhost</domain>
        <domain includeSubdomains="true">local</domain>      <!-- mDNS: server.local -->
        <domain includeSubdomains="true">home.arpa</domain>
        <domain includeSubdomains="true">lan</domain>
        <domain includeSubdomains="true">internal</domain>
        <!-- einzelne IPs sind möglich (String-Match), Bereiche nicht: -->
        <domain>192.168.1.50</domain>
    </domain-config>
</network-security-config>
```

Nachteil A: jede neue IP erfordert einen App-Rebuild → für Endnutzer unbrauchbar; nur Hostnamen praktikabel.
`android:usesCleartextTraffic` **nicht** setzen (wird bei vorhandener NSC ignoriert, Deprecation ab API 38). Kein Unit-Test möglich (XML-Ressource) → Ausnahme explizit; manuell prüfen: Debug-Build, `http://<LAN-IP>:8000/v1`, Erwartung Erfolg statt „Cleartext HTTP traffic … not permitted".

### 7.5 `SettingsActivity` / Layout

- Spinner/Radio „Anbieter" (OpenAI · Groq · Eigener Server) → setzt Defaults in URL/Modell/LLM-Modell/Timeout, wenn die Felder leer sind oder dem alten Preset-Default entsprechen.
- Neue Felder nur bei „Eigener Server" sichtbar: LLM-Server-URL, LLM-Key, Zeitüberschreitung (Sekunden).
- Inline-Validierung über `ServerUrlCheck.problem(...)` (Hinweistext unter dem URL-Feld, kein Blocken bei Warnungen außer „ungültige URL").
- Key-Feld-Hint im Preset: „optional".
- Optional: Button „Verbindung testen" → `GET {baseUrl}/models` (speaches, hwdsl2, Ollama, LocalAI liefern es; whisper.cpp nicht → 404 als „Server antwortet, /models nicht vorhanden" werten).
**Tests:** `ServerUrlCheckTest` (private/öffentliche Hosts, `.local`, Tailscale 100.64–127, `/v1`-Hinweis, http bei Cloud-Preset abgelehnt), `ProviderTest` (Defaults je Preset).

### 7.6 Sonstiges

- `README.md` → Kapitel aus §6 unter „Einrichten" ergänzen.
- `AudioChunks` (5-min-Stücke) bleibt; mit 600-s-Timeout sind CPU-Server bis RTF ≈ 2 abgedeckt. Retry-Logik (`isRetryable`) passt: 503 von whisper.cpp beim Modell-Laden wird wiederholt.
- Kein neuer Dependency-Bedarf (weiter `HttpURLConnection`).

---

## 8. Quellenverzeichnis

- speaches: https://github.com/speaches-ai/speaches · https://speaches.ai/installation/ · https://speaches.ai/configuration/ · https://github.com/speaches-ai/speaches/blob/master/docs/usage/model-discovery.md · https://raw.githubusercontent.com/speaches-ai/speaches/master/.github/workflows/docker-build-and-push.yaml · https://api.github.com/repos/speaches-ai/speaches/releases/latest · https://github.com/speaches-ai/speaches/issues/620
- hwdsl2/docker-whisper: https://github.com/hwdsl2/docker-whisper · https://api.github.com/repos/hwdsl2/docker-whisper
- whisper.cpp: https://github.com/ggml-org/whisper.cpp/blob/master/examples/server/README.md · https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/examples/server/server.cpp · https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/models/README.md · https://github.com/ggml-org/whisper.cpp/blob/master/README.md · https://api.github.com/repos/ggml-org/whisper.cpp/releases/latest
- whisper-asr-webservice: https://ahmetoner.com/whisper-asr-webservice/endpoints/ · https://ahmetoner.com/whisper-asr-webservice/run/
- LocalAI: https://localai.io/docs/features/audio-to-text/ · https://localai.io/docs/features/authentication/ · https://localai.io/docs/installation/containers/
- vLLM: https://docs.vllm.ai/en/latest/serving/online_serving/speech_to_text/
- Parakeet-Server: https://github.com/achetronic/parakeet/blob/master/README.md
- OpenAI-Referenz: https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create
- CTranslate2 aarch64-Wheels: https://pypi.org/project/ctranslate2/#files · https://github.com/opennmt/ctranslate2
- Ollama: https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx · https://github.com/ollama/ollama/blob/main/openai/openai.go · https://github.com/ollama/ollama/blob/main/docs/faq.mdx · https://github.com/ollama/ollama/blob/main/docs/linux.mdx · https://api.github.com/repos/ollama/ollama/releases/latest · https://ollama.com/library/qwen3 · https://ollama.com/library/gemma3 · https://ollama.com/library/mistral-nemo · https://ollama.com/search?q=gemma4
- llama.cpp server: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
- LM Studio: https://lmstudio.ai/docs/developer/openai-compat · https://markaicode.com/lm-studio-api-server-openai-compatible/
- Android: https://developer.android.com/privacy-and-security/security-config · https://developer.android.com/guide/topics/manifest/application-element · https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/security/net/config/ApplicationConfig.java · https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/security/net/config/XmlConfigSource.java · https://github.com/flutter/flutter/issues/65841
- Caddy: https://caddyserver.com/docs/caddyfile/matchers · https://caddyserver.com/docs/caddyfile/directives/basic_auth · https://medium.com/@dmitrywat/securing-your-ollama-instance-with-caddy-bearer-authentication-a2f131ae0101
- Tailscale: https://tailscale.com/kb/1242/tailscale-serve · https://tailscale.com/docs/reference/dns-in-tailscale
- Benchmarks ARM/Ampere: https://turingpi.com/whisper-cpp-piper-tts-arm64-turing-pi-rk3588/ · https://smartscope.blog/en/generative-ai/foundations/whisper-local-cpu-implementation/ · https://blog.easecloud.io/ai-cloud/launch-oracle-cloud-llms-in/ · https://huggingface.co/deepdml/faster-whisper-large-v3-turbo-ct2/discussions/3
- Modellvergleiche (Deutsch): https://www.promptquorum.com/local-llms/multilingual-local-llms · https://www.glukhov.org/llm-hosting/ollama/translation-quality-comparison-llms-on-ollama/
