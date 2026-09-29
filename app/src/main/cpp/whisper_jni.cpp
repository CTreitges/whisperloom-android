// JNI-Bruecke zwischen Kotlin (com.chris.whisperloom.whisper.WhisperLib) und whisper.cpp (v1.9.3).
//
// Die Symbolnamen MUESSEN exakt zu Paket + Objektname der Kotlin-Seite passen:
//   Java_com_chris_whisperloom_whisper_WhisperLib_<methode>
// Lokal gibt es kein NDK — tools/check_jni_symbols.py und JniSymbolsTest gleichen beide Seiten ab.
//
// Kein Asset-Loader mehr: Modelle liegen ausschliesslich als Datei in filesDir/models (Download).
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdint>
#include "whisper.h"

#define TAG "WhisperLoomJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Abbruch-Flag: von beliebigem Thread setzbar, whisper_full pollt es vor jedem ggml-Graph.
static std::atomic<bool> g_abort{false};

static bool abort_cb(void * /*user_data*/) {
    return g_abort.load(std::memory_order_relaxed);
}

// whisper.cpp/ggml loggen sonst nach stderr (auf Android unsichtbar) -> logcat.
static void log_cb(ggml_log_level level, const char *text, void * /*user_data*/) {
    int prio = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR) {
        prio = ANDROID_LOG_ERROR;
    } else if (level == GGML_LOG_LEVEL_WARN) {
        prio = ANDROID_LOG_WARN;
    } else if (level == GGML_LOG_LEVEL_DEBUG) {
        prio = ANDROID_LOG_DEBUG;
    }
    __android_log_write(prio, "whisper.cpp", text);
}

static whisper_context_params make_cparams(jboolean flash_attn) {
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu    = false;             // reiner CPU-Build (kein Vulkan-Backend gelinkt)
    cparams.flash_attn = flash_attn != 0;   // Default true in 1.9.x; als Debug-Schalter durchgereicht
    return cparams;
}

static struct whisper_context *to_ctx(jlong ptr) {
    return reinterpret_cast<struct whisper_context *>(static_cast<intptr_t>(ptr));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str, jboolean flash_attn) {
    (void) thiz;
    whisper_log_set(log_cb, nullptr);
    const char *path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Lade Modell aus Datei '%s'", path);
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, make_cparams(flash_attn));
    if (!ctx) {
        LOGW("Modell '%s' konnte nicht geladen werden", path);
    }
    env->ReleaseStringUTFChars(model_path_str, path);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(ctx));
}

JNIEXPORT void JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env; (void) thiz;
    whisper_free(to_ctx(context_ptr));
}

// Rueckgabe: 0 = ok, sonst whisper_full-Fehlercode (auch nach requestAbort).
JNIEXPORT jint JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr,
        jint num_threads, jstring language_str, jstring initial_prompt_str,
        jint beam_size, jboolean suppress_nst, jboolean timestamps, jfloatArray audio_data) {
    (void) thiz;
    struct whisper_context *ctx = to_ctx(context_ptr);
    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_len = env->GetArrayLength(audio_data);
    const char *language = env->GetStringUTFChars(language_str, nullptr);
    const char *prompt = initial_prompt_str ? env->GetStringUTFChars(initial_prompt_str, nullptr) : nullptr;

    // Beam-Search (beam_size > 1) wie whisper-cli-Default, sonst Greedy.
    const enum whisper_sampling_strategy strategy =
            beam_size > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY;
    struct whisper_full_params params = whisper_full_default_params(strategy);
    params.beam_search.beam_size = beam_size > 1 ? beam_size : -1;
    params.greedy.best_of        = 5;                  // Decoder-Anzahl im Temperatur-Fallback
    params.print_realtime        = false;
    params.print_progress        = false;
    params.print_timestamps      = false;
    params.print_special         = false;
    params.translate             = false;
    // Kotlin (WhisperContext.useTimestamps) schaltet Zeitstempel nur bei Audio > 30 s ein: mit
    // no_timestamps setzt whisper.cpp seek_delta fest auf 30 s (v1.9.3 src/whisper.cpp Z.7418-7420)
    // und schneidet mitten im Wort; mit Zeitstempeln startet das naechste Fenster am letzten
    // Zeitstempel. Der Segmenttext bleibt ohne Zeitstempel (print_special=false, Z.7652).
    params.no_timestamps         = timestamps == 0;
    params.single_segment        = false;
    // no_context leert nur den Kontext eines VORHERIGEN whisper_full-Aufrufs (Z.6937-6940). Innerhalb
    // eines Aufrufs reicht whisper.cpp den Text jedes 30-s-Fensters trotzdem als Prompt an das
    // naechste weiter (prompt_past1, Z.7627-7638); nur bei hoechstens 5 s Rest verwirft es ihn (Z.7064-7067).
    params.no_context            = true;
    params.suppress_blank        = true;
    params.suppress_nst          = suppress_nst != 0;  // Nicht-Sprach-Tokens (♪, [Musik]) unterdruecken
    params.n_threads             = num_threads;
    params.language              = language;           // "auto" => whisper_lang_auto_detect vor dem Decoding
    params.detect_language       = false;
    params.initial_prompt        = (prompt && prompt[0]) ? prompt : nullptr; // max n_text_ctx/2 = 224 Tokens
    params.carry_initial_prompt  = false;
    // Fallback-Defaults bewusst belassen: temperature_inc 0.2, entropy_thold 2.4,
    // logprob_thold -1.0, no_speech_thold 0.6 (whisper.cpp v1.9.3)
    params.abort_callback           = abort_cb;
    params.abort_callback_user_data = nullptr;

    g_abort.store(false);
    whisper_reset_timings(ctx);
    const int rc = whisper_full(ctx, params, audio, audio_len);
    if (rc != 0) {
        LOGW("whisper_full rc=%d (abgebrochen=%d)", rc, (int) g_abort.load());
    }

    if (prompt) env->ReleaseStringUTFChars(initial_prompt_str, prompt);
    env->ReleaseStringUTFChars(language_str, language);
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
    return rc;
}

JNIEXPORT void JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_requestAbort(JNIEnv *env, jobject thiz) {
    (void) env; (void) thiz;
    g_abort.store(true);
}

JNIEXPORT jint JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env; (void) thiz;
    return whisper_full_n_segments(to_ctx(context_ptr));
}

JNIEXPORT jstring JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    (void) thiz;
    const char *text = whisper_full_get_segment_text(to_ctx(context_ptr), index);
    return env->NewStringUTF(text ? text : "");
}

// Erkannte Sprache des letzten Laufs (bei language="auto"), z. B. "de" — fuer TextPolisher.
JNIEXPORT jstring JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_getDetectedLanguage(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) thiz;
    const int id = whisper_full_lang_id(to_ctx(context_ptr));
    const char *lang = id >= 0 ? whisper_lang_str(id) : nullptr;
    return env->NewStringUTF(lang ? lang : "");
}

JNIEXPORT jstring JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_getSystemInfo(JNIEnv *env, jobject thiz) {
    (void) thiz;
    return env->NewStringUTF(whisper_print_system_info());
}

// Fuer Messungen auf dem Geraet: Encoder-/Decoder-Zeiten nach logcat (ueber log_cb).
JNIEXPORT void JNICALL
Java_com_chris_whisperloom_whisper_WhisperLib_printTimings(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env; (void) thiz;
    whisper_print_timings(to_ctx(context_ptr));
}

} // extern "C"
