> **Historisch — Recherche-Archiv, Stand 2026-09-06.** Keine gepflegte Referenz: maßgeblich sind Code und [CHANGELOG](../../CHANGELOG.md); Einordnung in der [Übersicht](README.md).

# WhisperLoom — Provider- und Modell-Katalog (Stand 2026-09-06)

Recherche gegen aktuelle Anbieter-Doku für die Dropdowns „Transkription" (`POST {baseUrl}/audio/transcriptions`, multipart WAV, Felder `model/language/prompt/response_format`) und „Textverbesserung" (`POST {baseUrl}/chat/completions`, aktuell mit `temperature: 0`).
Bezug im Code: `app/src/main/java/com/chris/whisperloom/api/ApiTranscriber.kt`, `api/TextRefiner.kt`, `Prefs.kt` (Defaults `DEFAULT_API_URL = https://api.openai.com/v1`, `DEFAULT_API_MODEL = gpt-4o-transcribe`, `DEFAULT_LLM_MODEL = gpt-4o-mini`).

Legende: **[belegt]** = aus offizieller Doku zitiert · **[sekundär]** = nur Drittquelle · **[unsicher]** = widersprüchlich/nicht auffindbar, vor Release testen.

---

## 0. Kernaussagen (TL;DR)

1. **Der bisherige Default `gpt-4o-transcribe` ist abgekündigt** (Shutdown 2027-02-26, zusammen mit `whisper-1`, `gpt-4o-mini-transcribe`, `gpt-4o-transcribe-diarize`). Nachfolger ist **`gpt-transcribe`** ($0.0045/min, seit 2026-07-28) — nutzt aber `languages[]` (Array) statt `language` und kennt zusätzlich `keywords[]`. [belegt: OpenAI Deprecations + Transcription-Guide]
2. **`gpt-4o-mini` (LLM-Default) lebt noch** und ist nicht abgekündigt ($0.15/$0.60 pro 1M). `gpt-4.1-mini` ebenfalls. `gpt-5-mini`/`gpt-5-nano` sterben 2026-12-11, `gpt-4.1-nano` 2026-10-23. [belegt]
3. **OpenAI-Reasoning-Modelle (gpt-5.x, gpt-5.6-luna/terra/sol, gpt-6-astra) unterstützen `temperature` NICHT** — die App darf `temperature` bei diesen nicht senden, stattdessen `reasoning_effort: "none"` (gpt-5.1+, gpt-5.6) und ggf. `max_completion_tokens` statt `max_tokens`. [belegt: Microsoft-Learn-Tabelle + OpenAI Model-Guidance]
4. **Groq** ist der Gratis-Kandidat: `whisper-large-v3-turbo` im Free-Plan 7.200 Audio-Sekunden/Stunde, 28.800/Tag; LLM `openai/gpt-oss-20b` 30 RPM / 1.000 RPD gratis. Llama-Modelle sind bei Groq abgekündigt (Shutdown 2026-08-16). [belegt]
5. **Mistral Voxtral** (`voxtral-mini-latest`, $0.003/min, 13 Sprachen inkl. Deutsch) läuft am OpenAI-ähnlichen Pfad `/v1/audio/transcriptions` mit Bearer-Key — aber Parameter-Set weicht ab (`context_bias`, `diarize`; `prompt`/`response_format` nicht dokumentiert). [belegt, Kompatibilität der Extra-Felder unsicher]
6. **Wirklich OpenAI-kompatible Fremd-STT** außer Groq/Mistral: **Together AI** (`openai/whisper-large-v3`, $0.0015/min, `prompt` unterstützt), **DeepInfra** (`/v1/audio/transcriptions`, whisper-large-v3-turbo $0.0002/min), **OpenRouter** (`/api/v1/audio/transcriptions`, multipart-kompatibel, 25 MB, 60 s Timeout). **Fireworks: Audio seit 2026-06-10 deprecated → raus.** ElevenLabs/Deepgram/AssemblyAI: keine native OpenAI-Kompatibilität → raus.
7. **LLM-only**: OpenRouter, Anthropic-Kompatibilitätsschicht (`claude-haiku-4-5`, temperature 0–1 OK, offiziell „nicht für Produktion"), Gemini (`gemini-2.5-flash-lite` $0.10/$0.40, Free-Tier mit Trainingsdaten-Nutzung), DeepSeek (`deepseek-v4-flash`, Peak/Off-Peak-Preise seit 2026-08-16).

---

## 1. OpenAI — `https://api.openai.com/v1`

### 1.1 Transkription (`/audio/transcriptions`)

| Modell | Status | Preis | `language` | `prompt` | `response_format` | `temperature` | Quelle |
|---|---|---|---|---|---|---|---|
| **`gpt-transcribe`** | aktuell, empfohlen | **$0.0045/min** | **`languages[]`** (Array, ISO-639-1 wie `de`; regionale Codes wie `zh-cn`; nicht zusammen mit `language`) | ja („unstructured context about the recording") + **`keywords[]`** | json (Standard); Streaming | dokumentiert 0–1, Default 0 (Referenz allgemein) | [1][2][3] |
| `gpt-4o-transcribe` | **abgekündigt → Shutdown 2027-02-26** | $0.006/min ($2.50/1M Audio-In, $10/1M Out) | `language` ISO-639-1 | ja | **nur `json`** (kein text/verbose_json) | 0–1 | [1][4][5] |
| `gpt-4o-mini-transcribe` (+ Snapshot `gpt-4o-mini-transcribe-2025-12-15`) | **abgekündigt → 2027-02-26** | $0.003/min ($1.25/$5 pro 1M) | ISO-639-1 | ja | **nur `json`** | 0–1 | [1][4][5] |
| `gpt-4o-transcribe-diarize` | **abgekündigt → 2027-02-26** | $0.006/min | ISO-639-1 | — | json, text, diarized_json; `chunking_strategy` Pflicht > 30 s | — | [1][4] |
| `whisper-1` | Legacy, **abgekündigt → 2027-02-26** | $0.006/min | ISO-639-1 | ja (max 224 Token) | json, text, srt, verbose_json, vtt | 0–1 | [1][2][4] |
| `gpt-live-transcribe` | nur Realtime (`v1/realtime`) | $0.017/min | — | — | — | — | [4] — **nicht für die App** |

Weitere Fakten [belegt]:
- Dateilimit **25 MB**; Formate „mp3, mp4, mpeg, mpga, m4a, wav, webm" [2]. Die App lädt WAV 16 kHz mono (≈1,9 MB/min) → **AudioChunks bei 5 min bleibt unter 25 MB** (≈9,6 MB), Puffer vorhanden.
- gpt-transcribe: Rate-Limit Tier 1 „500 RPM, 200.000 TPM" [3]. gpt-4o-transcribe Tier 1: 500 RPM / 10.000 TPM [5].
- API-Referenz (Parameterliste): `file`, `model`, `language`, `prompt`, `response_format`, `temperature` (0–1, Default 0), `timestamp_granularities[]`, `include[]`, `stream`, `chunking_strategy`, `known_speaker_names[]` [1].
- Deprecation-Ankündigung 2026-08-26: „whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe, gpt-4o-transcribe-diarize → Shutdown 2027-02-26, Ersatz gpt-live-transcribe oder gpt-transcribe" [6].

**Konsequenz für die App:** Für `gpt-transcribe` muss `ApiTranscriber` statt `language=de` das Feld **`languages[]=de`** senden (und optional `keywords[]` aus dem bisherigen `apiPrompt` ableiten oder ein eigenes Feld). Ob `gpt-transcribe` das alte `language` toleriert oder mit 400 ablehnt, ist **[unsicher]** → beim ersten Live-Test prüfen. Alle anderen Provider bleiben bei `language`.

### 1.2 Textverbesserung (`/chat/completions`)

| Modell | Status | Input / Output je 1M | `temperature` | Hinweise | Quelle |
|---|---|---|---|---|---|
| **`gpt-4o-mini`** | aktiv, nicht abgekündigt | $0.15 / $0.60 | **ja** (0–2) | 128k Kontext; klassisches Non-Reasoning-Modell → bester Drop-in | [4][7] |
| `gpt-4.1-mini` | aktiv | $0.40 / $1.60 | ja | 1M Kontext | [4][8] |
| `gpt-4.1-nano` | **Shutdown 2026-10-23** | $0.10 / $0.40 | ja | Ersatz laut OpenAI: gpt-5.6-luna | [4][6] |
| `gpt-5-mini` | **Shutdown 2026-12-11** | $0.25 / $2.00 | **nein** | Reasoning; `reasoning_effort: minimal` möglich | [4][6][9] |
| `gpt-5-nano` | **Shutdown 2026-12-11** | $0.05 / $0.40 | **nein** | wie oben | [4][6][9] |
| **`gpt-5.6-luna`** | aktuell (Cost-Tier) | $0.20 / $1.20 (Cached $0.02) | **nein** | Reasoning; `reasoning_effort` none/low/medium(Default)/high/xhigh/max; 1.05M Kontext; Chat Completions unterstützt | [4][10][9] |
| `gpt-5.6-terra` | aktuell | $2.00 / $12.00 | nein | wie luna | [4][9] |
| `gpt-5.4-nano` / `gpt-5.4-mini` | aktuell | $0.20/$1.25 · $0.75/$4.50 | nein | `none` unterstützt | [4][9] |
| `gpt-6-astra` | Flagship | $10 / $50 | nein (`none` → 400) | überdimensioniert für Diktat | [4][11] |

**Temperature-Regel [belegt]:** Microsoft-Learn-Feature-Tabelle für die GPT-5-Reasoning-Modelle (gpt-5.6-sol/terra/luna, 5.5, 5.4-nano/mini, 5.2, 5.1, 5-pro …) führt `temperature` und `top_p` als „–" (nicht unterstützt); „Other reasoning models don't support the following parameters: temperature, top_p, presence_penalty, frequency_penalty, logprobs, top_logprobs, logit_bias, max_tokens" [9]. OpenAI-Model-Guidance zu GPT-6: „Remove temperature, top_p, and top_logprobs" [11]. Die Chat-Completions-Referenz selbst nennt `max_tokens` „deprecated in favor of max_completion_tokens" [12].
**`reasoning_effort` [belegt]:** „gpt-6-astra, gpt-5.6, gpt-5.5, gpt-5.4, gpt-5.2, gpt-5.1 … support 'None' … `minimal` works only with the original GPT-5 reasoning models. `minimal` doesn't work with gpt-5.1 or greater." [9]. GPT-6 Astra: `none` → HTTP 400 [11]. gpt-5.6 in Chat Completions: Function-Tools nur mit `reasoning_effort: none` (irrelevant für die App, keine Tools) [9][13].

**Empfohlene Request-Logik für `TextRefiner`:**
```json
// Non-Reasoning (gpt-4o-mini, gpt-4.1-mini, Groq/Mistral/Gemini/Claude/DeepSeek):
{ "model": "...", "temperature": 0, "messages": [...] }

// OpenAI-Reasoning (gpt-5.1+, gpt-5.6-*, gpt-5.4-*):
{ "model": "gpt-5.6-luna", "reasoning_effort": "none", "max_completion_tokens": 4096, "messages": [...] }

// gpt-5-mini / gpt-5-nano (bis 2026-12-11): reasoning_effort "minimal" statt "none"; kein temperature.
```
→ Katalogfeld `temperatureSupported` steuert das; zusätzlich Feld `reasoningEffort` (`null | "none" | "minimal"`).

### 1.3 Key & Guthaben [belegt/standard]
1. Konto unter https://platform.openai.com anlegen (Doku: „create an API key in the dashboard") [14].
2. Guthaben aufladen: Billing → Prepaid; **Tier 1 ab $5 Zahlung** (Usage-Tier-Tabelle: Free „allowed geography", Tier 1 „$5 paid", $100/Monat Limit) [15]. Ein Gratis-Guthaben für neue Konten ist **nicht** dokumentiert → [unsicher], eher nein.
3. Key erzeugen: **https://platform.openai.com/api-keys** → „Create new secret key" → nur einmal sichtbar.

---

## 2. Groq — `https://api.groq.com/openai/v1`

### 2.1 STT [belegt]
Endpunkt „https://api.groq.com/openai/v1/audio/transcriptions" [16]. Parameter: `model` (Pflicht), `file` **oder** `url`, `language` („ISO-639-1 format improves accuracy"), `prompt`, `response_format` json/text/verbose_json (Default json), `temperature` 0–1 (Default 0), `timestamp_granularities` [17].
Dateigröße **25 MB Free-Tier / 100 MB Dev-Tier**; Formate flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm; Mindestabrechnung 10 s; Empfehlung 16 kHz mono (WAV/FLAC) — genau das liefert die App [16].

| Modell | Preis | Status | Quelle |
|---|---|---|---|
| **`whisper-large-v3-turbo`** | **$0.04/h ≈ $0.00067/min** | Production, 216× Echtzeit, 99+ Sprachen | [16][18] |
| `whisper-large-v3` | $0.111/h ≈ $0.00185/min | Production, WER 8.4 % (short-form), 100 MB | [16][19] |
| `distil-whisper-large-v3-en` | — | **abgeschaltet 2025-08-23** → nicht mehr anbieten | [20] |

Free-Plan-Limits STT: „20 RPM, 2K RPD, 7.2K ASH, 28.8K ASD" (= 2 h Audio/Stunde, 8 h/Tag) [21].

### 2.2 LLM [belegt]
| Modell | Preis (In/Out je 1M) | Free-Plan | Status | Quelle |
|---|---|---|---|---|
| **`openai/gpt-oss-20b`** | $0.075 / $0.30 | 30 RPM, 1K RPD, 8K TPM, 200K TPD | Production, ~1000 tps | [21][22] |
| `openai/gpt-oss-120b` | $0.15 / $0.60 | 30 RPM, 1K RPD, 8K TPM, 200K TPD | Production, ~500 tps | [21][23] |
| `qwen/qwen3.6-27b` | ~$0.35 / ~$9 (Seite nennt „$0.60 per 1.7M in / $3.00 per 333K out") | 30 RPM, 1K RPD | **Preview** | [24] |
| `llama-3.3-70b-versatile` | — | nicht im Free-Plan | **Shutdown 2026-08-16** (nur noch Enterprise) | [20][25] |
| `llama-3.1-8b-instant` | — | nicht im Free-Plan | **Shutdown 2026-08-16** (nur noch Enterprise) | [20][26] |

Besonderheiten Chat: `temperature` 0–2 (Default 1) unterstützt; `reasoning_effort` (gpt-oss: low/medium/high; qwen3.6: none/default), `reasoning_format` hidden/raw/parsed, `include_reasoning`. **gpt-oss legt Reasoning standardmäßig in ein separates `reasoning`-Feld** — `message.content` bleibt sauber; für Qwen erscheint Reasoning als `<think>`-Tags im `content` → dort `reasoning_format: "hidden"` oder `reasoning_effort: "none"` senden [17][27]. Empfohlen für gpt-oss: `reasoning_effort: "low"`, `temperature: 0` erlaubt.

### 2.3 Key [belegt]
Quickstart: „Please visit /keys to create an API Key" → **https://console.groq.com/keys** [28]. Kein Zahlungsmittel für den Free-Plan nötig; „Upgrade to Developer plan to access higher limits" [21].

---

## 3. Mistral — `https://api.mistral.ai/v1`

### 3.1 STT (Voxtral) [belegt, Kompatibilität teils unsicher]
- Modell-ID **`voxtral-mini-latest`** = Voxtral Mini Transcribe 2 (v26.02), **$0.003/min**; Realtime-Variante `voxtral-mini-transcribe-realtime-2602` ($0.006/min, WebSocket → nicht für die App) [29][30]. Vorgänger `voxtral-mini-2507` retired 2026-05-31 [31].
- **13 Sprachen inkl. Deutsch**; „approximately 4% word error rate on FLEURS", laut Mistral besser als GPT-4o mini Transcribe, Gemini 2.5 Flash, Deepgram Nova; bis 3 h Audio pro Request [29][30].
- Doku-Parameter: `file` (oder `file_url`/Base64), `model`, `language` (optional, „manually set the language … for better accuracy"), `timestamp_granularities` (nicht zusammen mit `language`), `diarize`, `context_bias` (bis 100 Begriffe, „optimized for English; other languages experimental") [32]. Auth: Bearer-Key [33].
- **[unsicher]** Felder `prompt` und `response_format` sind nicht dokumentiert. Die App sendet beide immer. Multipart-Extra-Felder werden von FastAPI-Servern üblicherweise ignoriert, aber Mistral validiert streng (422 möglich) → **Live-Test Pflicht**; im Zweifel per Provider-Flag `sendsPrompt=false`, `sendsResponseFormat=false`. Antwort enthält `text` [32] → `optString("text")` passt.

### 3.2 LLM
| Modell | Preis (In/Out je 1M) | temperature | Quelle |
|---|---|---|---|
| **`mistral-small-latest`** (= `mistral-small-2603`, Mistral Small 4 119B) | $0.15 / $0.60 **[sekundär]** | ja | [34][35] |
| `ministral-8b-latest` (Ministral 3 8B) | ~$0.15 / $0.15 [sekundär] | ja | [35] |
| `mistral-medium-latest` (Medium 3.5) | $1.50 / $7.50 [sekundär] | ja | [34] |

Offizielle Preisseite (mistral.ai/pricing) ist JS-gerendert und ließ sich nicht maschinell auslesen → Preise vor Release auf https://mistral.ai/pricing gegenprüfen.

### 3.3 Key & Free-Tier
- Konsole **https://console.mistral.ai** → Studio aktivieren → API-Keys [33]. Direkter Pfad (Standard): https://console.mistral.ai/api-keys.
- **Experiment-Plan (gratis)** [sekundär, help.mistral.ai-Artikel via Suche]: 1 Request/s, 500k Token/min, 1 Mrd. Token/Monat, **Telefonverifizierung** nötig, keine Kreditkarte; gilt für alle Modelle [36]. Ob Voxtral-Minuten im Experiment-Plan enthalten sind → [unsicher].

---

## 4. Weitere STT-Anbieter mit echtem `/audio/transcriptions` + Bearer

| Anbieter | Base-URL (App) | Kompatibel? | STT-Modelle | Preis | Notizen | Quelle |
|---|---|---|---|---|---|---|
| **Together AI** | `https://api.together.ai/v1` (auch `api.together.xyz`) | **ja** — curl: `-F file=@… -F model=openai/whisper-large-v3`, Bearer | `openai/whisper-large-v3`; außerdem `nvidia/parakeet-tdt-0.6b-v3`, `deepgram/nova-3-multi` (dedicated) | **$0.0015/min** (Whisper) | `language` ISO-639-1 (auch `auto`), **`prompt` unterstützt** („only on Whisper-family models"), `response_format=json`; 80 MB Upload, bis 4 h. Key: https://api.together.ai/settings/api-keys (Doku: „project's API keys page"). Free-Credits für neue Nutzer, Höhe nicht dokumentiert. | [37][38][39] |
| **DeepInfra** | Chat: `https://api.deepinfra.com/v1/openai` · **Audio: `https://api.deepinfra.com/v1/audio/transcriptions`** | ja, aber **abweichender Pfad** → App braucht `sttPathOverride` oder eigene Base-URL je Endpunkt [unsicher ob `/v1/openai/audio/transcriptions` auch existiert] | `openai/whisper-large-v3-turbo` ($0.0002/min), `openai/whisper-large-v3` ($0.00045/min), `mistralai/Voxtral-Mini-3B-2507` ($0.001/min), `Qwen/Qwen3-ASR-1.7B` | ab **$0.0002/min** | Felder `file, model, language, prompt, response_format (json/verbose_json/text/srt/vtt), temperature (Default 0)`; Bearer. Key: https://deepinfra.com/dash/api_keys (Standard-Pfad). | [40][41][42] |
| **OpenRouter** | `https://openrouter.ai/api/v1` | **ja** — „OpenAI-compatible multipart … `file` and `model`" | `openai/gpt-4o-mini-transcribe` (Token-Preis $1.25/$5 je 1M, ≈ $0.003/min), `mistralai/voxtral-mini-transcribe` ($0.003/min), `openai/whisper-1`, `openai/whisper-large-v3-turbo`, `elevenlabs/scribe`, Deepgram | ab $0.003/min | `language` ISO-639-1, `temperature` 0–1, `response_format` json/verbose_json; **`prompt` nicht dokumentiert [unsicher]**; **25 MB**, **60 s Upstream-Timeout** (lange Chunks riskant), keine SRT/VTT. Key: https://openrouter.ai/settings/keys. | [43][44][45][46] |
| Fireworks | — | **raus**: „Audio inference and image generation are deprecated" (Changelog 2026-06-10), Endpunkt liefert 401 | — | — | | [47] |
| Azure OpenAI | — | raus (abweichende URL/`api-key`-Header, Deployment-Namen) | | | | — |
| ElevenLabs Scribe | — | **nein**: `POST /v1/speech-to-text`, Header `xi-api-key`, Feld `model_id` | | | nur via LiteLLM-Proxy kompatibel | [48] |
| Deepgram | — | nein: `/v1/listen`, `Authorization: Token …`; OpenAI-Form nur via LiteLLM | | | | [49] |
| AssemblyAI | — | nein: `/v2/transcripts` (async Job); OpenAI-kompatibel ist nur ihr LLM-Gateway | | | | [49] |

---

## 5. LLM-only-Provider für Textverbesserung

| Provider | Base-URL | Modelle (ID · In/Out je 1M) | temperature | Free-Tier | Key | Quelle |
|---|---|---|---|---|---|---|
| **OpenRouter** | `https://openrouter.ai/api/v1` | `openai/gpt-4o-mini` $0.15/$0.60 · `google/gemini-2.5-flash-lite` $0.10/$0.40 · `anthropic/claude-haiku-4.5` $1/$5 · `mistralai/mistral-small-2603` | ja (0–2) | `:free`-Varianten: 20 RPM, 50 RPD (<$10 Guthaben) bzw. 1.000 RPD (≥$10) | https://openrouter.ai/settings/keys | [50][51][52][53] |
| **Anthropic (OpenAI-Kompatibilität)** | `https://api.anthropic.com/v1` | `claude-haiku-4-5` $1/$5 · `claude-sonnet-5` $2/$10 · (`claude-sonnet-4-6` $3/$15) | **ja, 0–1** („Values greater than 1 are capped at 1") | „small amount of free credits" für Neue | https://platform.claude.com/settings/keys | [54][55] |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-flash-lite` $0.10/$0.40 · `gemini-2.5-flash` $0.30/$2.50 · `gemini-3.1-flash-lite` $0.25/$1.50 · `gemini-3.8-flash` $0.75/$3.75 | ja | **ja, alle genannten Modelle „Free of charge"** — aber „Content used to improve our products" (Trainingsnutzung!) | https://aistudio.google.com/apikey | [56][57][58] |
| **DeepSeek** | `https://api.deepseek.com` (App hängt `/chat/completions` an; `/v1`-Suffix historisch ebenfalls akzeptiert [unsicher]) | `deepseek-v4-flash` (Alias `deepseek-chat` seit 2026-07-24 abgeschaltet) | ja | nein („The API is not free") | https://platform.deepseek.com/api_keys | [59][60][61] |

Anmerkungen:
- **Anthropic:** Bearer-Header „Fully supported"; Schicht ist „primarily intended to test and compare model capabilities, and is not considered a long-term or production-ready solution" — funktional aber stabil („intended to remain fully functional"). System-Message wird gehoisted (passt zur App). `reasoning_effort` wird ignoriert. Keine Audio-Endpunkte [54].
- **Gemini:** `reasoning_effort` → thinking-Level; **`none` nur bei 2.5-Modellen** („Reasoning cannot be turned off for Gemini 2.5 Pro or 3 models") → für Diktat-Korrektur `gemini-2.5-flash-lite` + `reasoning_effort: "none"` (schnell, günstig); 3.x-Flash-Lite denkt immer → Latenz [56].
- **DeepSeek Preise [unsicher/widersprüchlich]:** Seit 2026-08-16 16:00 UTC Peak/Off-Peak („Off-peak rates are 50% lower than peak") [60]. Drittquellen nennen für V4-Flash $0.14/$0.28 (alt) bzw. $0.22/$0.66 off-peak / $0.44/$1.32 peak (Peak 01–04 und 06–10 UTC) [61][62]. Offizielle Tabelle (api-docs.deepseek.com/quick_start/pricing) ist JS-gerendert → vor Release manuell prüfen. Größenordnung: für Diktat vernachlässigbar.

---

## 6. Preis- und Free-Tier-Tabelle (pro Diktat-Minute)

Annahme Diktat: 1 min Sprache ≈ 150 Wörter ≈ 250 Token Rohtext; LLM-Aufruf ≈ 350 Token Input (System-Prompt + Text) + 250 Token Output.

| Zweck | Provider / Modell | Preis je Diktat-Minute | Free-Tier | Deutsch-Qualität (Einschätzung) |
|---|---|---|---|---|
| STT | OpenAI `gpt-transcribe` | **$0.0045** | nein ($5 Mindestaufladung) | sehr gut (WER Common Voice 22 Sprachen 19,3 % vs. whisper-1 40,4 % [3b]); `keywords`/`prompt` für Eigennamen |
| STT | OpenAI `gpt-4o-transcribe` (Alt-Default) | $0.006 | nein | gut, aber EOL 2027-02 |
| STT | OpenAI `gpt-4o-mini-transcribe` | $0.003 | nein | ok, EOL 2027-02 |
| STT | Groq `whisper-large-v3-turbo` | **$0.00067** | **8 h Audio/Tag gratis** | gut (Whisper v3), Halluzinationen bei Stille → trimSilence hilft |
| STT | Groq `whisper-large-v3` | $0.00185 | 8 h/Tag gratis | sehr gut (WER 8,4 %) |
| STT | Mistral `voxtral-mini-latest` | **$0.003** | Experiment-Plan [unsicher für Audio] | sehr gut (Deutsch in den 13 Kernsprachen, ~4 % WER FLEURS) |
| STT | Together `openai/whisper-large-v3` | $0.0015 | Startguthaben | sehr gut |
| STT | DeepInfra `openai/whisper-large-v3-turbo` | $0.0002 | nein | gut |
| STT | OpenRouter `mistralai/voxtral-mini-transcribe` | $0.003 | nein | sehr gut; 60-s-Timeout beachten |
| LLM | OpenAI `gpt-4o-mini` | ≈ $0.0002 | nein | gut |
| LLM | OpenAI `gpt-5.6-luna` (`reasoning_effort: none`) | ≈ $0.0004 | nein | gut; zukunftssicher |
| LLM | Groq `openai/gpt-oss-20b` | ≈ $0.0001 | 1.000 Req/Tag gratis | gut, sehr schnell |
| LLM | Gemini `gemini-2.5-flash-lite` | ≈ $0.00014 | gratis (Datennutzung!) | gut |
| LLM | Anthropic `claude-haiku-4-5` | ≈ $0.0016 | Startguthaben | sehr gut |
| LLM | Mistral `mistral-small-latest` | ≈ $0.0002 [sekundär] | Experiment-Plan | gut (europ. Anbieter) |
| LLM | DeepSeek `deepseek-v4-flash` | ≈ $0.0003 [unsicher] | nein | gut |

LLM-Kosten sind gegenüber STT um Faktor 10–30 kleiner — der Preisvergleich entscheidet sich bei STT.

---

## 7. „Wo bekomme ich einen Key?" — Schritte je Provider (für die Anleitung)

**OpenAI** — 1) https://platform.openai.com registrieren. 2) *Settings → Billing* → Zahlungsmittel + Prepaid-Guthaben (**mind. $5**, damit Tier 1). 3) https://platform.openai.com/api-keys → *Create new secret key* → kopieren (wird nur einmal angezeigt). 4) In WhisperLoom: Provider „OpenAI", Key einfügen.

**Groq** — 1) https://console.groq.com registrieren (Google/GitHub/E-Mail). 2) https://console.groq.com/keys → *Create API Key*. 3) Kostenlos nutzbar ohne Zahlungsmittel (Free-Plan). Optional *Billing → Developer* für höhere Limits/100-MB-Dateien.

**Mistral** — 1) https://console.mistral.ai registrieren, Studio aktivieren. 2) Plan wählen: *Experiment* (gratis, **Telefonnummer verifizieren**) oder *Pay-as-you-go* (Karte). 3) *API Keys* → *Create new key*.

**Together AI** — 1) https://api.together.ai registrieren (Startguthaben). 2) *Settings → API Keys* → *Create key*.

**DeepInfra** — 1) https://deepinfra.com anmelden (GitHub/Google). 2) *Dashboard → API Keys* → *New API Key*. 3) Guthaben aufladen.

**OpenRouter** — 1) https://openrouter.ai anmelden. 2) *Credits* aufladen (≥ $10 hebt `:free`-Limit auf 1.000 Req/Tag). 3) https://openrouter.ai/settings/keys → *Create Key*.

**Anthropic** — 1) https://platform.claude.com registrieren. 2) *Billing* → Guthaben (kleines Startguthaben vorhanden). 3) https://platform.claude.com/settings/keys → *Create Key*. Hinweis in der App: „OpenAI-Kompatibilitätsschicht, von Anthropic als Test-Werkzeug eingestuft".

**Google Gemini** — 1) https://aistudio.google.com/apikey öffnen, Google-Konto, Bedingungen akzeptieren. 2) *Create API key*. 3) Gratis nutzbar; **Hinweis: Free-Tier-Inhalte dürfen von Google zum Training genutzt werden** — Diktate sind oft privat → Billing aktivieren (Tier 1) oder Provider meiden.

**DeepSeek** — 1) https://platform.deepseek.com registrieren. 2) *Top up* (kein Free-Tier). 3) https://platform.deepseek.com/api_keys → *Create new API key*.

---

## 8. JSON-Katalog (für die Dropdowns)

Felder: `id, name, baseUrl, sttModels[{id,label,note}], llmModels[{id,label,note,temperatureSupported,reasoningEffort?}], keyUrl, needsKey, notes`. Zusätzliche Provider-Flags, die der Code auswerten sollte: `sttLanguageField` (`"language"` | `"languages[]"`), `sttSendsPrompt`, `sttSendsResponseFormat`, `sttPath` (Override), `sttMaxBytes`.

```json
{
  "catalogDate": "2026-09-06",
  "providers": [
    {
      "id": "openai",
      "name": "OpenAI",
      "baseUrl": "https://api.openai.com/v1",
      "needsKey": true,
      "keyUrl": "https://platform.openai.com/api-keys",
      "sttMaxBytes": 26214400,
      "sttModels": [
        { "id": "gpt-transcribe", "label": "GPT Transcribe (empfohlen)", "note": "$0.0045/min. Nutzt languages[] statt language, kennt keywords[] + prompt.", "languageField": "languages[]" },
        { "id": "gpt-4o-transcribe", "label": "GPT-4o Transcribe (Auslauf 02/2027)", "note": "$0.006/min, nur response_format=json, Shutdown 2027-02-26." },
        { "id": "gpt-4o-mini-transcribe", "label": "GPT-4o mini Transcribe (Auslauf 02/2027)", "note": "$0.003/min, nur json, Shutdown 2027-02-26." },
        { "id": "whisper-1", "label": "Whisper v2 (Legacy, Auslauf 02/2027)", "note": "$0.006/min, prompt max 224 Token." }
      ],
      "llmModels": [
        { "id": "gpt-4o-mini", "label": "GPT-4o mini", "note": "$0.15/$0.60 je 1M. Schnell, günstig, klassisch.", "temperatureSupported": true },
        { "id": "gpt-4.1-mini", "label": "GPT-4.1 mini", "note": "$0.40/$1.60 je 1M.", "temperatureSupported": true },
        { "id": "gpt-5.6-luna", "label": "GPT-5.6 Luna", "note": "$0.20/$1.20 je 1M. Reasoning-Modell: kein temperature, reasoning_effort=none senden.", "temperatureSupported": false, "reasoningEffort": "none" },
        { "id": "gpt-5.4-nano", "label": "GPT-5.4 nano", "note": "$0.20/$1.25 je 1M. Kein temperature; reasoning_effort=none.", "temperatureSupported": false, "reasoningEffort": "none" },
        { "id": "gpt-5-mini", "label": "GPT-5 mini (Auslauf 12/2026)", "note": "$0.25/$2.00. Kein temperature; reasoning_effort=minimal. Shutdown 2026-12-11.", "temperatureSupported": false, "reasoningEffort": "minimal" },
        { "id": "gpt-5-nano", "label": "GPT-5 nano (Auslauf 12/2026)", "note": "$0.05/$0.40. Kein temperature; reasoning_effort=minimal. Shutdown 2026-12-11.", "temperatureSupported": false, "reasoningEffort": "minimal" }
      ],
      "notes": "Bezahlpflichtig (Tier 1 ab $5). 25-MB-Limit. Alle 4o-/whisper-STT-Modelle enden 2027-02-26."
    },
    {
      "id": "groq",
      "name": "Groq",
      "baseUrl": "https://api.groq.com/openai/v1",
      "needsKey": true,
      "keyUrl": "https://console.groq.com/keys",
      "sttMaxBytes": 26214400,
      "sttModels": [
        { "id": "whisper-large-v3-turbo", "label": "Whisper Large v3 Turbo", "note": "$0.04/h. Free-Plan: 2 h Audio/Std, 8 h/Tag. Sehr schnell." },
        { "id": "whisper-large-v3", "label": "Whisper Large v3", "note": "$0.111/h. Höhere Genauigkeit (WER 8,4 %)." }
      ],
      "llmModels": [
        { "id": "openai/gpt-oss-20b", "label": "GPT-OSS 20B", "note": "$0.075/$0.30 je 1M. Free: 30 RPM, 1000/Tag. Reasoning separat im Feld reasoning; reasoning_effort=low empfohlen.", "temperatureSupported": true, "reasoningEffort": "low" },
        { "id": "openai/gpt-oss-120b", "label": "GPT-OSS 120B", "note": "$0.15/$0.60 je 1M. Free: 30 RPM, 1000/Tag.", "temperatureSupported": true, "reasoningEffort": "low" },
        { "id": "qwen/qwen3.6-27b", "label": "Qwen 3.6 27B (Preview)", "note": "Preview. reasoning_effort=none senden, sonst <think>-Tags im Text.", "temperatureSupported": true, "reasoningEffort": "none" }
      ],
      "notes": "Free-Plan ohne Zahlungsmittel. 25 MB (Free) / 100 MB (Dev). Llama-Modelle seit 2026-08-16 abgeschaltet."
    },
    {
      "id": "mistral",
      "name": "Mistral (Voxtral)",
      "baseUrl": "https://api.mistral.ai/v1",
      "needsKey": true,
      "keyUrl": "https://console.mistral.ai/api-keys",
      "sttSendsPrompt": false,
      "sttSendsResponseFormat": false,
      "sttModels": [
        { "id": "voxtral-mini-latest", "label": "Voxtral Mini Transcribe 2", "note": "$0.003/min. 13 Sprachen inkl. Deutsch, bis 3 h. prompt/response_format nicht dokumentiert -> nicht senden." }
      ],
      "llmModels": [
        { "id": "mistral-small-latest", "label": "Mistral Small 4", "note": "ca. $0.15/$0.60 je 1M (Drittquelle).", "temperatureSupported": true },
        { "id": "ministral-8b-latest", "label": "Ministral 3 8B", "note": "Sehr günstig, klein.", "temperatureSupported": true }
      ],
      "notes": "EU-Anbieter. Experiment-Plan gratis (Telefonverifizierung, 1 req/s). Kompatibilität der Extra-Felder live testen."
    },
    {
      "id": "together",
      "name": "Together AI",
      "baseUrl": "https://api.together.ai/v1",
      "needsKey": true,
      "keyUrl": "https://api.together.ai/settings/api-keys",
      "sttMaxBytes": 83886080,
      "sttModels": [
        { "id": "openai/whisper-large-v3", "label": "Whisper Large v3", "note": "$0.0015/min. prompt unterstützt, language ISO-639-1 oder auto. 80 MB." }
      ],
      "llmModels": [],
      "notes": "OpenAI-kompatibel. Startguthaben für neue Konten. LLM-IDs nicht verifiziert -> vorerst nur STT."
    },
    {
      "id": "deepinfra",
      "name": "DeepInfra",
      "baseUrl": "https://api.deepinfra.com/v1/openai",
      "sttPath": "https://api.deepinfra.com/v1/audio/transcriptions",
      "needsKey": true,
      "keyUrl": "https://deepinfra.com/dash/api_keys",
      "sttModels": [
        { "id": "openai/whisper-large-v3-turbo", "label": "Whisper Large v3 Turbo", "note": "$0.0002/min." },
        { "id": "openai/whisper-large-v3", "label": "Whisper Large v3", "note": "$0.00045/min." }
      ],
      "llmModels": [],
      "notes": "Audio-Endpunkt liegt unter /v1/audio/transcriptions (nicht /v1/openai) -> Pfad-Override nötig; live testen."
    },
    {
      "id": "openrouter",
      "name": "OpenRouter",
      "baseUrl": "https://openrouter.ai/api/v1",
      "needsKey": true,
      "keyUrl": "https://openrouter.ai/settings/keys",
      "sttMaxBytes": 26214400,
      "sttSendsPrompt": false,
      "sttModels": [
        { "id": "mistralai/voxtral-mini-transcribe", "label": "Voxtral Mini Transcribe (via OpenRouter)", "note": "$0.003/min. 60-s-Timeout, 25 MB." },
        { "id": "openai/gpt-4o-mini-transcribe", "label": "GPT-4o mini Transcribe (via OpenRouter)", "note": "Token-Preis $1.25/$5 je 1M (~$0.003/min)." },
        { "id": "openai/whisper-large-v3-turbo", "label": "Whisper Large v3 Turbo (via OpenRouter)", "note": "Sekundenpreis, günstig." }
      ],
      "llmModels": [
        { "id": "openai/gpt-4o-mini", "label": "GPT-4o mini", "note": "$0.15/$0.60 je 1M.", "temperatureSupported": true },
        { "id": "google/gemini-2.5-flash-lite", "label": "Gemini 2.5 Flash-Lite", "note": "$0.10/$0.40 je 1M.", "temperatureSupported": true },
        { "id": "anthropic/claude-haiku-4.5", "label": "Claude Haiku 4.5", "note": "$1/$5 je 1M.", "temperatureSupported": true },
        { "id": "mistralai/mistral-small-2603", "label": "Mistral Small 4", "note": "günstig, EU-Provider wählbar.", "temperatureSupported": true }
      ],
      "notes": "Ein Key für viele Modelle. :free-Modelle 20 RPM / 50-1000 RPD. prompt bei STT nicht dokumentiert -> nicht senden."
    },
    {
      "id": "anthropic",
      "name": "Anthropic (Claude)",
      "baseUrl": "https://api.anthropic.com/v1",
      "needsKey": true,
      "keyUrl": "https://platform.claude.com/settings/keys",
      "sttModels": [],
      "llmModels": [
        { "id": "claude-haiku-4-5", "label": "Claude Haiku 4.5", "note": "$1/$5 je 1M. temperature 0-1.", "temperatureSupported": true },
        { "id": "claude-sonnet-5", "label": "Claude Sonnet 5", "note": "$2/$10 je 1M. Höchste Textqualität.", "temperatureSupported": true }
      ],
      "notes": "Nur Textverbesserung. OpenAI-Kompatibilitätsschicht offiziell 'zum Testen', funktional stabil."
    },
    {
      "id": "gemini",
      "name": "Google Gemini",
      "baseUrl": "https://generativelanguage.googleapis.com/v1beta/openai",
      "needsKey": true,
      "keyUrl": "https://aistudio.google.com/apikey",
      "sttModels": [],
      "llmModels": [
        { "id": "gemini-2.5-flash-lite", "label": "Gemini 2.5 Flash-Lite", "note": "$0.10/$0.40 je 1M, Free-Tier. reasoning_effort=none möglich.", "temperatureSupported": true, "reasoningEffort": "none" },
        { "id": "gemini-2.5-flash", "label": "Gemini 2.5 Flash", "note": "$0.30/$2.50 je 1M, Free-Tier. reasoning_effort=none möglich.", "temperatureSupported": true, "reasoningEffort": "none" },
        { "id": "gemini-3.8-flash", "label": "Gemini 3.8 Flash", "note": "$0.75/$3.75 je 1M. Reasoning nicht abschaltbar -> langsamer.", "temperatureSupported": true }
      ],
      "notes": "Nur Textverbesserung. Free-Tier: Inhalte werden zum Training genutzt -> Warnhinweis in der App."
    },
    {
      "id": "deepseek",
      "name": "DeepSeek",
      "baseUrl": "https://api.deepseek.com",
      "needsKey": true,
      "keyUrl": "https://platform.deepseek.com/api_keys",
      "sttModels": [],
      "llmModels": [
        { "id": "deepseek-v4-flash", "label": "DeepSeek V4 Flash", "note": "Peak/Off-Peak-Preise (~$0.14-0.44 / $0.28-1.32 je 1M, unsicher). Alias deepseek-chat abgeschaltet.", "temperatureSupported": true }
      ],
      "notes": "Nur Textverbesserung. Kein Free-Tier. Server in China -> Datenschutz-Hinweis."
    },
    {
      "id": "custom",
      "name": "Eigener Server (OpenAI-kompatibel)",
      "baseUrl": "",
      "needsKey": false,
      "keyUrl": "",
      "sttModels": [],
      "llmModels": [],
      "notes": "Freie Base-URL + Modell-IDs (z. B. faster-whisper-server, speaches, LocalAI, Ollama). Key optional."
    }
  ]
}
```

---

## 9. Empfehlung Default-Provider/-Modelle (Deutsch, Qualität/Preis)

| Rolle | Empfehlung | Begründung |
|---|---|---|
| **STT-Default (bezahlt)** | **OpenAI `gpt-transcribe`** | Bestes dokumentiertes WER-Niveau, $0.0045/min (25 % günstiger als der Alt-Default), `keywords[]`/`prompt` für Namen; Nachfolger der EOL-Modelle. Erfordert Code-Anpassung `languages[]`. |
| **STT-Default (gratis)** | **Groq `whisper-large-v3-turbo`** | 8 h/Tag gratis ohne Karte, 25 MB reicht für 5-min-Chunks, `language=de` + `prompt` 1:1 kompatibel mit dem bestehenden `ApiTranscriber`. Für Onboarding ohne Bezahlhürde ideal. |
| **STT-Alternative EU** | Mistral `voxtral-mini-latest` | $0.003/min, Deutsch als Kernsprache, EU-Anbieter; vorher Extra-Felder testen. |
| **LLM-Default** | **OpenAI `gpt-4o-mini`** (bleibt) — mit Migrationspfad `gpt-5.6-luna` (`reasoning_effort: none`, kein `temperature`) | gpt-4o-mini ist nicht abgekündigt, unterstützt `temperature: 0`, Kosten ~$0.0002/Diktat-Minute. Sobald OpenAI es abkündigt, Default auf gpt-5.6-luna schwenken — dafür jetzt schon `temperatureSupported`/`reasoningEffort` im Katalog. |
| **LLM-Default (gratis)** | Groq `openai/gpt-oss-20b` (`reasoning_effort: low`) | Gleicher Key wie Groq-STT → Ein-Provider-Setup komplett gratis. |
| **Nicht als Default** | Gemini Free-Tier (Trainingsdaten-Nutzung), DeepSeek (Datenschutz, Preis unklar), Anthropic (Test-Schicht, teurer) | als Optionen anbieten, mit Hinweistext. |

**Konkrete Code-Folgen (nicht umgesetzt, nur Befund):**
1. `Prefs.DEFAULT_API_MODEL` → `gpt-transcribe`; `ApiTranscriber`: Feldname `languages[]` für dieses Modell (Provider-/Modell-Flag), `keywords[]` optional.
2. `TextRefiner`: `temperature` nur bei `temperatureSupported`; sonst `reasoning_effort` (`none`/`minimal`) und `max_completion_tokens`.
3. Provider-Flags `sttSendsPrompt`/`sttSendsResponseFormat`/`sttPath` für Mistral, OpenRouter, DeepInfra.
4. Anleitungstexte aus §7; Warnhinweise für Gemini-Free (Training) und Anthropic (Test-Schicht).
5. Tests: Request-Body-Builder-Tests je Modellklasse (Reasoning vs. klassisch; `language` vs. `languages[]`).

---

## Quellen

[1] OpenAI API-Referenz Audio/Create transcription — https://developers.openai.com/api/docs/api-reference/audio
[2] OpenAI Guide Speech-to-text (25 MB, Formate, Modellvergleich) — https://developers.openai.com/api/docs/guides/speech-to-text
[3] OpenAI Model-Seite gpt-transcribe ($0.0045/min, Rate-Limits) — https://developers.openai.com/api/docs/models/gpt-transcribe · Guide Transcription (`languages` statt `language`, `keywords`) — https://developers.openai.com/api/docs/guides/transcription
[3b] Ankündigung 2026-07-28 (WER-Zahlen) — https://community.openai.com/t/gpt-live-transcribe-and-gpt-transcribe-two-new-transcription-models-in-the-api/1388318 · https://x.com/ArtificialAnlys/status/2082285338509418727
[4] OpenAI Pricing — https://developers.openai.com/api/docs/pricing
[5] Model-Seite gpt-4o-transcribe — https://developers.openai.com/api/docs/models/gpt-4o-transcribe
[6] OpenAI Deprecations — https://developers.openai.com/api/docs/deprecations
[7] Model-Seite gpt-4o-mini — https://developers.openai.com/api/docs/models/gpt-4o-mini
[8] Model-Seite gpt-4.1-mini — https://developers.openai.com/api/docs/models/gpt-4.1-mini
[9] Microsoft Learn „Azure OpenAI reasoning models" (Feature-Tabelle temperature „–", Unsupported parameters, reasoning_effort-Werte; Stand 2026-08-20) — https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/reasoning
[10] Model-Seite gpt-5.6-luna — https://developers.openai.com/api/docs/models/gpt-5.6-luna
[11] OpenAI Model guidance / GPT-6 (Remove temperature, top_p) — https://developers.openai.com/api/docs/guides/latest-model · Reasoning-Guide (none → 400 bei GPT-6) — https://developers.openai.com/api/docs/guides/reasoning
[12] Chat Completions Referenz — https://developers.openai.com/api/docs/api-reference/chat/create
[13] gpt-5.6 + tools in Chat Completions — https://github.com/BerriAI/litellm/issues/33221 · https://community.openai.com/t/gpt-5-6-chat-completion-reasoning-effort-bug-behavior-change/1386454
[14] OpenAI Quickstart — https://developers.openai.com/api/docs/quickstart
[15] OpenAI Rate limits / Usage tiers — https://developers.openai.com/api/docs/guides/rate-limits
[16] Groq Speech-to-Text — https://console.groq.com/docs/speech-to-text
[17] Groq API-Referenz — https://console.groq.com/docs/api-reference
[18] Groq whisper-large-v3-turbo — https://console.groq.com/docs/model/whisper-large-v3-turbo
[19] Groq whisper-large-v3 — https://console.groq.com/docs/model/whisper-large-v3
[20] Groq Deprecations — https://console.groq.com/docs/deprecations
[21] Groq Rate limits (Free-Plan-Tabelle) — https://console.groq.com/docs/rate-limits
[22] Groq gpt-oss-20b — https://console.groq.com/docs/model/openai/gpt-oss-20b
[23] Groq gpt-oss-120b — https://console.groq.com/docs/model/openai/gpt-oss-120b
[24] Groq qwen3.6-27b — https://console.groq.com/docs/model/qwen/qwen3.6-27b
[25] Groq llama-3.3-70b-versatile — https://console.groq.com/docs/model/llama-3.3-70b-versatile
[26] Groq llama-3.1-8b-instant — https://console.groq.com/docs/model/llama-3.1-8b-instant
[27] Groq Reasoning — https://console.groq.com/docs/reasoning
[28] Groq Quickstart (Key) — https://console.groq.com/docs/quickstart
[29] Mistral News „Voxtral Transcribe 2" (IDs, $0.003/min, 13 Sprachen) — https://mistral.ai/news/voxtral-transcribe-2/
[30] Mistral Audio-Capabilities — https://docs.mistral.ai/capabilities/audio/ · https://docs.mistral.ai/capabilities/audio/speech_to_text/
[31] Mistral Models overview (Deprecated-Tabelle) — https://docs.mistral.ai/getting-started/models/models_overview
[32] Mistral Offline transcription — https://docs.mistral.ai/studio/audio/speech_to_text/offline_transcription
[33] Mistral API-Referenz / Quickstart — https://docs.mistral.ai/api/ · https://docs.mistral.ai/getting-started/quickstart
[34] Mistral-Preise (Drittquelle) — https://www.cloudzero.com/blog/mistral-api-pricing/
[35] mistral-small-latest = mistral-small-2603 — https://openrouter.ai/mistralai/mistral-small-2603 · https://huggingface.co/mistralai/Mistral-Small-4-119B-2603 · Ministral-Preis: [34]
[36] Mistral Experiment-Plan — https://help.mistral.ai/en/articles/455206-how-can-i-try-the-api-for-free-with-the-experiment-plan · https://help.mistral.ai/en/articles/225174-what-are-the-limits-of-the-free-tier (Inhalt via Suchergebnis, Seite direkt nicht abrufbar)
[37] Together Speech-to-Text — https://docs.together.ai/docs/speech-to-text
[38] Together Pricing (Whisper $0.0015/min) — https://www.together.ai/pricing
[39] Together Quickstart (Key, Base-URL) — https://docs.together.ai/docs/quickstart
[40] DeepInfra OpenAI Audio Transcriptions — https://docs.deepinfra.com/api-reference/audio/openai-audio-transcriptions.md
[41] DeepInfra ASR-Modelle/Preise — https://deepinfra.com/models/automatic-speech-recognition
[42] DeepInfra Chat overview (Base-URL /v1/openai) — https://docs.deepinfra.com/chat/overview
[43] OpenRouter STT-Guide — https://openrouter.ai/docs/guides/overview/multimodal/stt
[44] OpenRouter Audio-APIs Ankündigung — https://openrouter.ai/blog/announcements/announcing-audio-apis/
[45] OpenRouter voxtral-mini-transcribe — https://openrouter.ai/mistralai/voxtral-mini-transcribe
[46] OpenRouter gpt-4o-mini-transcribe — https://openrouter.ai/openai/gpt-4o-mini-transcribe
[47] Fireworks Audio deprecated (Changelog-Zitat) — https://github.com/BerriAI/litellm/issues/30916 · https://fireworks.ai/blog/audio-transcription-launch
[48] ElevenLabs STT API — https://elevenlabs.io/docs/api-reference/speech-to-text/convert
[49] LiteLLM-Provider-Seiten (Deepgram/AssemblyAI nur via Proxy) — https://docs.litellm.ai/docs/providers/deepgram · https://docs.litellm.ai/docs/pass_through/assembly_ai
[50] OpenRouter API overview — https://openrouter.ai/docs/api-reference/overview
[51] OpenRouter Limits (:free) — https://openrouter.ai/docs/api-reference/limits
[52] OpenRouter gpt-4o-mini — https://openrouter.ai/openai/gpt-4o-mini · gemini-2.5-flash-lite — https://openrouter.ai/google/gemini-2.5-flash-lite
[53] OpenRouter claude-haiku-4.5 — https://openrouter.ai/anthropic/claude-haiku-4.5
[54] Anthropic OpenAI SDK compatibility — https://platform.claude.com/docs/en/api/openai-sdk
[55] Anthropic Pricing — https://platform.claude.com/docs/en/about-claude/pricing
[56] Gemini OpenAI compatibility — https://ai.google.dev/gemini-api/docs/openai
[57] Gemini Pricing — https://ai.google.dev/gemini-api/docs/pricing · Models — https://ai.google.dev/gemini-api/docs/models
[58] Gemini API key — https://ai.google.dev/gemini-api/docs/api-key · Rate limits — https://ai.google.dev/gemini-api/docs/rate-limits
[59] DeepSeek API docs (Base-URL, Modelle, Key) — https://api-docs.deepseek.com/
[60] DeepSeek Updates (Peak/Off-Peak ab 2026-08-16, Alias-Ende 2026-07-24) — https://api-docs.deepseek.com/updates · https://api-docs.deepseek.com/news/news260813
[61] DeepSeek-Preise Drittquellen — https://www.cloudzero.com/blog/deepseek-pricing/ · https://benchlm.ai/deepseek/api-pricing
[62] https://www.aipricing.guru/deepseek-pricing/
