package com.chris.whisperloom

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

/**
 * Vorbelegte Einstellungen fuer einen Einzel-Build (`-Pwhisperloom.presetFile=…`, siehe app/build.gradle.kts).
 * Normale Builds haben kein Asset `preset.json` — dann passiert nichts.
 *
 * Format: `{"groups": [{"schluessel": wert, …}, …]}` mit den rohen Schluesseln aus [Prefs]
 * (String, Boolean oder Int). Eine Gruppe wird nur geschrieben, wenn KEINER ihrer Schluessel schon
 * gesetzt ist — so entsteht nie ein halber Zugang (z. B. alter Anbieter mit neuem Key), und nichts
 * Vorhandenes wird ueberschrieben. Das Ganze laeuft einmal ([KEY_APPLIED]); spaeter geloeschte Werte
 * kommen nicht zurueck. Ein Update auf ein normales APK behaelt die Werte (SharedPreferences bleiben).
 */
object PresetSeed {

    const val ASSET = "preset.json"
    internal const val KEY_APPLIED = "preset_applied"

    fun applyFromAssets(context: Context) {
        val sp = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_APPLIED, false)) return
        val json = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return
        }
        apply(sp, json)
    }

    /** @return Zahl der geschriebenen Gruppen. */
    fun apply(sp: SharedPreferences, json: String): Int {
        if (sp.getBoolean(KEY_APPLIED, false)) return 0
        val groups: JSONArray = JSONObject(json).getJSONArray("groups")
        var written = 0
        sp.edit(commit = true) {
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                val keys = group.keys().asSequence().toList()
                if (keys.any { sp.contains(it) }) continue
                for (key in keys) {
                    when (val value = group.get(key)) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        else -> throw IllegalArgumentException("Typ von $key nicht unterstuetzt: ${value::class.simpleName}")
                    }
                }
                written++
            }
            putBoolean(KEY_APPLIED, true)
        }
        return written
    }
}
