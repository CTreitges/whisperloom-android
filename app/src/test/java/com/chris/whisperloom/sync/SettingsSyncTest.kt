package com.chris.whisperloom.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Einstellungs-Export/-Import im Format v1 (Desktop `docs/settings-format.md`). Die Fixtures sind
 * byte-gleiche Kopien aus `CTreitges/whisperloom-desktop` (`tests/fixtures/`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSyncTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun fixture(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()

    private fun importOk(prefs: Prefs, json: String): ImportPreview {
        val preview = SettingsSync.preview(prefs, json)
        assertEquals(emptyList<String>(), preview.errors)
        assertTrue(preview.canApply)
        SettingsSync.apply(ctx, prefs, preview)
        return preview
    }

    // --- Roundtrip ------------------------------------------------------------------------------

    @Test fun fixtureRoundtripErgibtDieselbeDatei() {
        val prefs = Prefs(ctx)
        importOk(prefs, fixture("settings-v1.json"))

        val exported = JSONObject(SettingsSync.export(Prefs(ctx)))
        val expected = JSONObject(fixture("settings-v1.json"))
        // Fuellwoerter speichert Android als Menge: Reihenfolge ist keine Bedeutung.
        assertEquals(canonical(expected), canonical(exported))
    }

    @Test fun fixtureLandetInDenVorhandenenPrefs() {
        val prefs = Prefs(ctx)
        importOk(prefs, fixture("settings-v1.json"))

        assertEquals("de", prefs.language)
        assertEquals(Engine.ONLINE, prefs.engine)
        assertEquals("groq", prefs.sttProviderId)
        assertEquals("whisper-large-v3-turbo", prefs.apiModel)
        assertEquals("WhisperLoom\nKubernetes\nTreitges", prefs.apiPrompt)
        assertTrue(prefs.sttFallbackEnabled)
        assertEquals("same", prefs.llmProviderId)
        assertEquals("llama-3.1-8b-instant", prefs.llmModelFor(RefineMode.POLISH))
        assertEquals("llama-3.3-70b-versatile", prefs.llmModelFor(RefineMode.SUMMARIZE))
        assertEquals("", prefs.llmModelFor(RefineMode.BEAUTIFY))
        assertEquals(RefineMode.POLISH, prefs.refineMode)
        assertTrue(prefs.promptLevelEnabled)
        assertEquals(PolishCleanup.CLEAN, prefs.polishCleanupFor(RefineWay.DICTATION))
        assertFalse(prefs.paragraphsFor(RefineMode.BEAUTIFY))
        assertEquals(RefineMode.BEAUTIFY, prefs.shareRefineMode)
        assertFalse(prefs.trailingSpace)
        assertEquals(setOf("sozusagen", "quasi"), prefs.customFillers)
        assertEquals(setOf("also"), prefs.disabledFillers)
        assertEquals(100, prefs.historySize)
        assertTrue(prefs.welcomeSeen)
        assertFalse(prefs.proWidgetsEnabled)
    }

    // --- Abschnitt desktop und Unbekanntes ------------------------------------------------------

    @Test fun desktopUndUnbekannteFelderBleibenBeimReExport() {
        val file = JSONObject(fixture("settings-v1.json"))
        file.getJSONObject("desktop").put("hotkeys", JSONObject().put("dictate", "Ctrl+Alt+D"))
        file.getJSONObject("android").put("futureAndroid", 7)
        file.put("futureTopLevel", JSONObject().put("x", JSONArray().put(1).put(2)))
        val prefs = Prefs(ctx)
        importOk(prefs, file.toString())

        // Aenderung auf dem Telefon nach dem Import: desktop bleibt trotzdem unveraendert.
        prefs.language = "en"
        val exported = JSONObject(SettingsSync.export(Prefs(ctx)))

        assertEquals(canonical(file.getJSONObject("desktop")), canonical(exported.getJSONObject("desktop")))
        assertEquals(canonical(file.getJSONObject("futureTopLevel")), canonical(exported.getJSONObject("futureTopLevel")))
        assertEquals(7, exported.getJSONObject("android").getInt("futureAndroid"))
        assertEquals("en", exported.getString("language"))
    }

    @Test fun teilDateiOhneDesktopLaesstDenGemerktenAbschnittStehen() {
        val prefs = Prefs(ctx)
        importOk(prefs, fixture("settings-v1.json"))
        importOk(prefs, """{"schemaVersion": 1, "language": "fr"}""")

        val exported = JSONObject(SettingsSync.export(Prefs(ctx)))
        assertEquals("fr", exported.getString("language"))
        assertEquals("groq", exported.getJSONObject("stt").getString("providerId"))
        assertEquals(32, exported.getJSONObject("desktop").getInt("stageFontSize"))
    }

    @Test fun ohneImportGibtEsKeinenDesktopAbschnitt() {
        val exported = JSONObject(SettingsSync.export(Prefs(ctx)))
        assertEquals(1, exported.getInt("schemaVersion"))
        assertFalse(exported.has("desktop"))
        assertTrue(exported.isNull("engine"))
    }

    // --- Keys -----------------------------------------------------------------------------------

    @Test fun exportEnthaeltNieKeys() {
        val prefs = Prefs(ctx)
        prefs.apiKey = "test-key-not-real-1"
        prefs.llmKey = "test-key-not-real-2"
        prefs.sttFallbackKey = "test-key-not-real-3"
        prefs.llmFallbackKey = "test-key-not-real-4"
        importOk(prefs, fixture("settings-v1.json"))

        val text = SettingsSync.export(Prefs(ctx))
        assertFalse(text, text.contains("test-key-not-real"))
        val tree = JSONObject(text)
        assertFalse(tree.has("secrets"))
        assertFalse(tree.has("policy"))
        // Der Import laesst die Keys des Telefons stehen.
        assertEquals("test-key-not-real-1", prefs.apiKey)
    }

    @Test fun secretsUndPolicyWerdenMitHinweisIgnoriert() {
        val prefs = Prefs(ctx)
        val preview = importOk(prefs, fixture("settings-v1-provisioning.json"))

        assertEquals(1, preview.warnings.size)
        assertTrue(preview.warnings.single(), preview.warnings.single().contains("secrets"))
        assertFalse(preview.warnings.single().contains("test-key-not-real"))
        assertEquals("", prefs.apiKey)
        assertEquals("", prefs.llmKey)
        // policy.historyEnabled=false gilt nur auf dem Desktop.
        assertTrue(prefs.historyEnabled)
        // Die Einstellungen der Datei gelten trotzdem.
        assertEquals("groq", prefs.sttProviderId)
        assertEquals("openai", prefs.llmProviderId)
        assertEquals("gpt-5-mini", prefs.llmModelFor(RefineMode.POLISH))

        val text = SettingsSync.export(Prefs(ctx))
        assertFalse(text.contains("test-key-not-real"))
        assertFalse(JSONObject(text).has("secrets"))
        assertFalse(JSONObject(text).has("policy"))
    }

    // --- Ungueltige Dateien ---------------------------------------------------------------------

    @Test fun ungueltigeDateienMeldenFehlerUndAendernNichts() {
        val prefs = Prefs(ctx)
        importOk(prefs, fixture("settings-v1.json"))
        val before = SettingsSync.export(Prefs(ctx))

        val cases = mapOf(
            "kein json {" to "Kein gültiges JSON",
            "[1, 2]" to "JSON-Objekt",
            """{"schemaVersion": 2, "language": "en"}""" to "Bitte WhisperLoom aktualisieren",
            """{"schemaVersion": "1"}""" to "schemaVersion",
            """{"language": "en", "history": {"size": 33}}""" to "history.size: eine von 10, 25, 50, 100, 250, 500",
            """{"stt": {"providerId": "gibtsnicht"}}""" to "stt.providerId: Anbieter unbekannt",
            """{"llm": {"stageModels": {"foo": "x"}}}""" to "llm.stageModels.foo: Stufe unbekannt",
            """{"stt": {"baseUrl": "ftp://x"}}""" to "stt.baseUrl: leer oder http(s)-URL",
            """{"stt": {"readTimeoutSec": 5}}""" to "stt.readTimeoutSec: 0 oder 30–1800",
            """{"refine": {"stage": "laut"}}""" to "refine.stage: eines von",
            """{"share": {"stage": "prompt"}}""" to "share.stage: eines von",
            """{"language": 5}""" to "Falscher Typ bei language",
            """{"history": "viel"}""" to "Falscher Typ bei history",
            """{"polish": {"customFillers": ["a", 1]}}""" to "Falscher Typ bei polish.customFillers",
            """{"desktop": 1}""" to "Falscher Typ bei desktop",
        )
        for ((json, message) in cases) {
            val preview = SettingsSync.preview(prefs, json)
            assertFalse(json, preview.canApply)
            assertTrue("$json -> ${preview.errors}", preview.errors.any { it.contains(message) })
        }
        // Auch ein erzwungenes apply einer ungueltigen Vorschau aendert nichts.
        runCatching { SettingsSync.apply(ctx, prefs, SettingsSync.preview(prefs, """{"language": "en", "history": {"size": 33}}""")) }

        assertEquals(before, SettingsSync.export(Prefs(ctx)))
        assertEquals("de", prefs.language)
    }

    @Test fun fehlermeldungNenntNieDenInhalt() {
        val preview = SettingsSync.preview(Prefs(ctx), """{"secrets": {"groq": "test-key-not-real-9"}, "x": tru""")
        assertFalse(preview.canApply)
        assertFalse(preview.errors.toString(), preview.errors.joinToString().contains("test-key-not-real"))
    }

    // --- Lesen: Kommentare, Komma am Ende, Schema 0 ----------------------------------------------

    @Test fun kommentareUndKommaAmEndeSindErlaubt() {
        val prefs = Prefs(ctx)
        importOk(
            prefs,
            """
            {
              // Kommentar
              "schemaVersion": 1, /* noch einer */
              "language": "en",
              "polish": {"customFillers": ["Halt", "halt", " eben ",],},
            }
            """.trimIndent(),
        )
        assertEquals("en", prefs.language)
        assertEquals(setOf("halt", "eben"), prefs.customFillers)
    }

    @Test fun schemaNullWirdMigriert() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"refine": {"stage": "paragraphs"}, "share": {"stage": "paragraphs"}}""")
        assertEquals(RefineMode.POLISH, prefs.refineMode)
        assertEquals(RefineMode.POLISH, prefs.shareRefineMode)
    }

    @Test fun vorschauNenntDieGeaendertenPfade() {
        val prefs = Prefs(ctx)
        val preview = SettingsSync.preview(prefs, """{"language": "en", "history": {"size": 100}, "desktop": {"uiLanguage": "en"}}""")
        assertEquals(listOf("history.size", "language"), preview.changes)
        // Nichts uebernommen, solange apply nicht laeuft.
        assertEquals("de", prefs.language)
    }

    @Test fun verlaufAusschaltenWarnt() {
        val preview = SettingsSync.preview(Prefs(ctx), """{"history": {"enabled": false}}""")
        assertTrue(preview.canApply)
        assertTrue(preview.warnings.toString(), preview.warnings.any { it.contains("Verlauf") })
    }

    @Test fun exportSchreibtUrlsUndSonderzeichenLesbarUndVerlustfrei() {
        val prefs = Prefs(ctx)
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "https://whisper.example.org/v1"
        prefs.apiPrompt = "Zitat \"so\"\nTab\there\\Pfad\u0001"
        val text = SettingsSync.export(prefs)
        assertTrue(text, text.contains("\"baseUrl\": \"https://whisper.example.org/v1\""))
        assertFalse(text.contains("\\/"))

        clear()
        val other = Prefs(ctx)
        importOk(other, text)
        assertEquals("https://whisper.example.org/v1", other.apiBaseUrl)
        assertEquals("Zitat \"so\"\nTab\there\\Pfad\u0001", other.apiPrompt)
    }

    @Test fun stufeLesbarWirdGlaettenMitBereinigungLesbar() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"schemaVersion": 1, "refine": {"stage": "readable", "polishCleanup": "plain"}}""")
        assertEquals(RefineMode.POLISH, prefs.refineMode)
        assertEquals(PolishCleanup.READABLE, prefs.polishCleanupFor(RefineWay.DICTATION))
    }

    // --- Hilfen ---------------------------------------------------------------------------------

    /** Vergleichbarer Text: Schluessel sortiert, die Fuellwort-Listen als Menge. */
    private fun canonical(value: Any?, path: String = ""): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted()
            .joinToString(",", "{", "}") { "\"$it\":" + canonical(value.get(it), if (path.isEmpty()) it else "$path.$it") }
        is JSONArray -> {
            val items = (0 until value.length()).map { canonical(value.get(it), path) }
            (if (path.endsWith("Fillers")) items.sorted() else items).joinToString(",", "[", "]")
        }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
