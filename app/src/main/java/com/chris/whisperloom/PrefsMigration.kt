package com.chris.whisperloom

import android.content.SharedPreferences
import androidx.core.content.edit
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.api.ProviderCatalog

/**
 * Hebt die Einstellungen einer aelteren Version auf den Stand von [Prefs]. Laeuft je Stufe genau
 * einmal (prefs_version) und ist idempotent; [Prefs] ruft es beim Anlegen.
 *
 * v2 -> v3:
 *  - der Schalter "KI glaetten" (llm_polish) wird zum Modus refine_mode
 *  - v2 hatte eine freie api_url ohne Anbieter: passt sie zu einem Katalog-Preset, wird
 *    dieser Anbieter gesetzt, sonst "Eigener Server" (sonst bliebe ein LAN-Server unter
 *    dem Label OpenAI mit https-Pflicht haengen — der Assistent kaeme nie zu "fertig")
 *  - Bestandsnutzer (API-Key da bzw. eigener Server, keine Engine gewaehlt) bleiben online
 *
 * v3 -> v4 (3.8.0): Gemini-Nutzer ohne gewaehltes Modell liefen auf der Voreinstellung
 * gemini-2.5-flash-lite. Die Voreinstellung ist jetzt 3.5 Flash-Lite (fuer neue Konten) — wer
 * schon 2.5 nutzt, behaelt es, statt unbemerkt auf ein anderes, nachdenkendes Modell zu wechseln.
 *
 * v4 -> v5 (3.8.6): "Lesbarer glaetten" gab es getrennt fuer Sprachnachrichten, Startwert der des
 * Diktats. Seit v6 geht das direkt in die Bereinigung der Sprachnachrichten ([toV6]).
 *
 * v5 -> v6 (3.9.0): Einstellungen je Stufe und Weg ([toV6]).
 */
internal object PrefsMigration {

    fun run(sp: SharedPreferences) {
        val version = sp.getInt(Prefs.KEY_PREFS_VERSION, 0)
        if (version >= Prefs.PREFS_VERSION) return
        sp.edit {
            if (version < 3) toV3(sp, this)
            if (version < 4 && sp.getString(Prefs.KEY_LLM_PROVIDER, "") == GEMINI_ID && sp.getString(Prefs.KEY_LLM_MODEL, "").isNullOrBlank()) {
                putString(Prefs.KEY_LLM_MODEL, GEMINI_LEGACY_DEFAULT)
            }
            if (version < 6) toV6(sp, this)
            putInt(Prefs.KEY_PREFS_VERSION, Prefs.PREFS_VERSION)
        }
    }

    private fun toV3(sp: SharedPreferences, e: SharedPreferences.Editor) {
        if (!sp.contains(Prefs.KEY_REFINE_MODE) && sp.getBoolean(KEY_LLM_POLISH_LEGACY, false)) {
            e.putString(Prefs.KEY_REFINE_MODE, RefineMode.POLISH.key)
        }
        val legacyUrl = sp.getString(Prefs.KEY_API_URL, "").orEmpty().trim()
        var provider = ProviderCatalog.openai
        if (!sp.contains(Prefs.KEY_STT_PROVIDER) && legacyUrl.isNotEmpty()) {
            provider = providerForLegacyUrl(legacyUrl)
            if (provider.id != ProviderCatalog.OPENAI_ID) e.putString(Prefs.KEY_STT_PROVIDER, provider.id)
        }
        val hasKey = !sp.getString(Prefs.KEY_API_KEY, "").isNullOrBlank()
        if (sp.getString(Prefs.KEY_ENGINE, "").isNullOrBlank() && (hasKey || !provider.needsKey)) {
            e.putString(Prefs.KEY_ENGINE, Engine.ONLINE.key)
        }
    }

    /**
     * Die Bereinigung beim Glaetten ersetzt "Lesbarer glaetten" (je Weg) und "Fuellwoerter
     * intelligent" (galt fuer beide Wege): Lesbar geht vor, dort raeumt die KI ohnehin auf.
     * "Automatische Absaetze" wird zu den Absaetzen von Glaetten und Verschoenern und zur Form
     * beim Zusammenfassen des Diktats. Sprachnachrichten waren immer gegliedert: Form "automatisch".
     * Die Modelle je Stufe bleiben, wie sie sind — sie gelten fuer beide Wege. Schon gesetzte Werte
     * bleiben; die alten Schluessel werden danach nur noch hier gelesen.
     */
    private fun toV6(sp: SharedPreferences, e: SharedPreferences.Editor) {
        val smart = sp.getBoolean(KEY_SMART_FILLERS, false)
        val readable = sp.getBoolean(KEY_POLISH_READABLE, false)
        // Vor v5 gab es keinen eigenen Schalter: dann galt der des Diktats (wie die v5-Migration).
        val shareReadable = if (sp.contains(KEY_SHARE_POLISH_READABLE)) sp.getBoolean(KEY_SHARE_POLISH_READABLE, false) else readable
        val paragraphs = sp.getBoolean(KEY_REFINE_PARAGRAPHS, true)
        fun putIfAbsent(key: String, value: String) {
            if (!sp.contains(key)) e.putString(key, value)
        }
        putIfAbsent(Prefs.KEY_POLISH_CLEANUP, cleanup(readable, smart).key)
        putIfAbsent(Prefs.KEY_SHARE_POLISH_CLEANUP, cleanup(shareReadable, smart).key)
        for (key in listOf(Prefs.KEY_PARAGRAPHS_POLISH, Prefs.KEY_PARAGRAPHS_BEAUTIFY)) {
            if (!sp.contains(key)) e.putBoolean(key, paragraphs)
        }
        putIfAbsent(Prefs.KEY_SUMMARIZE_FORM, (if (paragraphs) SummarizeForm.AUTO else SummarizeForm.PROSE).key)
        putIfAbsent(Prefs.KEY_SHARE_SUMMARIZE_FORM, SummarizeForm.AUTO.key)
    }

    private fun cleanup(readable: Boolean, smartFillers: Boolean): PolishCleanup = when {
        readable -> PolishCleanup.READABLE
        smartFillers -> PolishCleanup.CLEAN
        else -> PolishCleanup.PLAIN
    }

    /** Katalog-Anbieter mit genau dieser Base-URL, sonst der eigene Server. */
    private fun providerForLegacyUrl(url: String): Provider {
        val wanted = url.trimEnd('/')
        return ProviderCatalog.sttProviders.firstOrNull { !it.isCustom && it.baseUrl.trimEnd('/') == wanted }
            ?: ProviderCatalog.custom
    }

    /** v4: Voreinstellung bis 3.7 — bleibt fuer Gemini-Bestandsnutzer ohne gewaehltes Modell. */
    private const val GEMINI_ID = "gemini"
    private const val GEMINI_LEGACY_DEFAULT = "gemini-2.5-flash-lite"

    // Altlasten: nur noch hier gelesen, nie geschrieben (ein Downgrade findet sie unveraendert vor).
    private const val KEY_LLM_POLISH_LEGACY = "llm_polish"
    private const val KEY_SMART_FILLERS = "smart_fillers"
    private const val KEY_REFINE_PARAGRAPHS = "refine_paragraphs"
    private const val KEY_POLISH_READABLE = "polish_readable"
    private const val KEY_SHARE_POLISH_READABLE = "share_polish_readable"
}
