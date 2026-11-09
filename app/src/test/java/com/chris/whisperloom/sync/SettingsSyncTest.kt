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
        // Derselbe Anbieter wie in der Datei: kein neues Ziel, der Key bleibt.
        prefs.sttProviderId = "groq"
        importOk(prefs, fixture("settings-v1.json"))

        val text = SettingsSync.export(Prefs(ctx))
        assertFalse(text, text.contains("test-key-not-real"))
        val tree = JSONObject(text)
        assertFalse(tree.has("secrets"))
        assertFalse(tree.has("policy"))
        // Der Import laesst die Keys stehen, solange ihr Ziel gleich bleibt.
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
        // In der Datei steht das abgeschaltete gpt-5-mini.
        assertEquals("gpt-5.6-luna", prefs.llmModelFor(RefineMode.POLISH))

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

    // --- Review-Befunde --------------------------------------------------------------------------

    @Test fun luekenInListenUndTiefeVerschachtelungSindFehlerKeinAbsturz() {
        val prefs = Prefs(ctx)
        val before = SettingsSync.export(prefs)
        listOf(
            """{"polish": {"customFillers": ["a",,"b"]}}""",
            """{"futureField": [,"a"]}""",
            """{"desktop": {"x": [1,,]}}""",
            "{\"x\": " + "[".repeat(5000) + "]".repeat(5000) + "}",
            "{\"x\": " + "[".repeat(100) + "]".repeat(100) + "}",
        ).forEach { json ->
            val preview = SettingsSync.preview(prefs, json)
            assertFalse(json.take(40), preview.canApply)
            assertTrue(preview.errors.toString(), preview.errors.single().startsWith("Kein gültiges JSON"))
        }
        assertEquals(before, SettingsSync.export(prefs))
    }

    @Test fun engineNullAusDemDesktopLaesstDieWahlDesGeraetsStehen() {
        val prefs = Prefs(ctx)
        prefs.engine = Engine.OFFLINE
        importOk(prefs, """{"schemaVersion": 1, "engine": null, "language": "en"}""")
        assertEquals(Engine.OFFLINE, prefs.engine)
        assertEquals("en", prefs.language)
    }

    @Test fun urlMitLeerraumOderGrossemSchemaIstGueltigWieZurLaufzeit() {
        val prefs = Prefs(ctx)
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "https://whisper.example.org/v1 "
        prefs.llmUrl = "HTTPS://llm.example.org/v1"
        val preview = SettingsSync.preview(Prefs(ctx), SettingsSync.export(prefs))
        assertEquals(emptyList<String>(), preview.errors)
    }

    @Test fun schemaVersionNullGiltAlsSchemaNullWieAufDemDesktop() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"schemaVersion": null, "refine": {"stage": "paragraphs"}}""")
        assertEquals(RefineMode.POLISH, prefs.refineMode)
    }

    @Test fun kaputteSecretsOderPolicyLehntAndroidAbWieDerDesktop() {
        mapOf(
            """{"secrets": 5}""" to "secrets muss ein Objekt sein",
            """{"secrets": {"groq": 1}}""" to "secrets.groq muss ein nicht leerer Text sein",
            """{"secrets": {"groq": "  "}}""" to "secrets.groq muss ein nicht leerer Text sein",
            """{"policy": []}""" to "policy muss ein Objekt sein",
        ).forEach { (json, message) ->
            val preview = SettingsSync.preview(Prefs(ctx), json)
            assertFalse(json, preview.canApply)
            assertTrue("$json -> ${preview.errors}", preview.errors.single().startsWith(message))
        }
    }

    // --- Anbieterwechsel beim Import: Key, Adresse, Modelle ---------------------------------------

    /** Vier Zugaenge mit gespeichertem Key; die Fallbacks sind aus, zaehlen aber mit. */
    private fun deviceWithKeys(): Prefs = Prefs(ctx).apply {
        sttProviderId = "groq"
        apiKey = "test-key-not-real-1"
        llmProviderId = "anthropic"
        llmKey = "test-key-not-real-2"
        sttFallbackProviderId = "groq"
        sttFallbackKey = "test-key-not-real-3"
        llmFallbackProviderId = "groq"
        llmFallbackKey = "test-key-not-real-4"
    }

    @Test fun anbieterwechselLoeschtDenKeyAllerVierZugaenge() {
        val prefs = deviceWithKeys()
        val preview = importOk(
            prefs,
            """{"stt": {"providerId": "openai"}, "llm": {"providerId": "groq"},
               "sttFallback": {"providerId": "openai"}, "llmFallback": {"providerId": "openai"}}""",
        )
        assertEquals("", prefs.apiKey)
        assertEquals("", prefs.llmKey)
        assertEquals("", prefs.sttFallbackKey)
        assertEquals("", prefs.llmFallbackKey)
        assertTrue(preview.warnings.toString(), preview.warnings.any { it.contains("Key") })
        assertFalse(preview.warnings.toString(), preview.warnings.joinToString().contains("test-key-not-real"))
    }

    @Test fun gleicherAnbieterBehaeltDenKey() {
        val prefs = deviceWithKeys()
        val preview = importOk(
            prefs,
            """{"stt": {"providerId": "groq", "model": "whisper-large-v3"}, "llm": {"providerId": "anthropic"},
               "sttFallback": {"providerId": "groq"}, "llmFallback": {"providerId": "groq"}}""",
        )
        assertEquals("test-key-not-real-1", prefs.apiKey)
        assertEquals("test-key-not-real-2", prefs.llmKey)
        assertEquals("test-key-not-real-3", prefs.sttFallbackKey)
        assertEquals("test-key-not-real-4", prefs.llmFallbackKey)
        assertFalse(preview.warnings.toString(), preview.warnings.any { it.contains("Key") })
    }

    @Test fun neueAdresseDesEigenenServersLoeschtDenKey() {
        val prefs = Prefs(ctx).apply {
            sttProviderId = "custom"
            apiBaseUrl = "https://a.example.org/v1"
            apiKey = "test-key-not-real-1"
        }
        importOk(prefs, """{"stt": {"baseUrl": "https://b.example.org/v1"}}""")
        assertEquals("", prefs.apiKey)
    }

    @Test fun andererPfadAufDemselbenHostBehaeltDenKey() {
        val prefs = Prefs(ctx).apply {
            sttProviderId = "custom"
            apiBaseUrl = "https://a.example.org/v1"
            apiKey = "test-key-not-real-1"
        }
        importOk(prefs, """{"stt": {"baseUrl": "https://a.example.org/openai/v1"}}""")
        assertEquals("test-key-not-real-1", prefs.apiKey)
    }

    @Test fun geerbterKeyGehtNichtAnEineNeueAdresse() {
        // Eigener Server fuer beides, die Textverbesserung ohne eigenen Key: sie erbt den der Erkennung.
        val prefs = Prefs(ctx).apply {
            sttProviderId = "custom"
            apiBaseUrl = "https://a.example.org/v1"
            apiKey = "test-key-not-real-1"
            llmProviderId = "custom"
        }
        importOk(prefs, """{"llm": {"baseUrl": "https://fremd.example.org/v1"}}""")
        assertEquals("", prefs.apiKey)
    }

    @Test fun teilDateiNurMitAnbieterLeertAdresseUndModell() {
        val prefs = Prefs(ctx).apply {
            sttProviderId = "custom"
            apiBaseUrl = "http://192.168.1.5:8000/v1"
            apiModel = "mein-whisper"
            sttFallbackProviderId = "custom"
            sttFallbackUrl = "http://192.168.1.6:8000/v1"
            sttFallbackModel = "mein-fallback"
            llmFallbackProviderId = "custom"
            llmFallbackUrl = "http://192.168.1.7:8000/v1"
            llmFallbackModel = "mein-text"
        }
        importOk(
            prefs,
            """{"stt": {"providerId": "groq"}, "sttFallback": {"providerId": "groq"}, "llmFallback": {"providerId": "groq"}}""",
        )
        assertEquals("", prefs.apiBaseUrl)
        assertEquals("", prefs.apiModel)
        assertEquals("", prefs.sttFallbackUrl)
        assertEquals("", prefs.sttFallbackModel)
        assertEquals("", prefs.llmFallbackUrl)
        assertEquals("", prefs.llmFallbackModel)
    }

    @Test fun adresseUndModellDerDateiGeltenAuchBeimAnbieterwechsel() {
        val prefs = Prefs(ctx).apply { sttProviderId = "groq" }
        importOk(prefs, """{"stt": {"providerId": "custom", "baseUrl": "https://neu.example.org/v1", "model": "m"}}""")
        assertEquals("https://neu.example.org/v1", prefs.apiBaseUrl)
        assertEquals("m", prefs.apiModel)
    }

    @Test fun anbieterwechselErsetztDieStufenModelleGanz() {
        val prefs = Prefs(ctx).apply {
            llmProviderId = "anthropic"
            llmModel = "claude-sonnet-5"
            setLlmModelFor(RefineMode.POLISH, "claude-haiku")
            setLlmModelFor(RefineMode.BEAUTIFY, "claude-opus")
        }
        importOk(prefs, """{"llm": {"providerId": "groq", "stageModels": {"polish": "llama-3.1-8b-instant"}}}""")
        assertEquals("llama-3.1-8b-instant", prefs.llmModelFor(RefineMode.POLISH))
        assertEquals("", prefs.llmModelFor(RefineMode.BEAUTIFY))
        assertEquals("", prefs.llmModel)
    }

    @Test fun wechselDerErkennungSetztModelleVonWieErkennungZurueck() {
        val prefs = Prefs(ctx).apply {
            sttProviderId = "groq"
            llmProviderId = "same"
            setLlmModelFor(RefineMode.POLISH, "llama-3.1-8b-instant")
        }
        importOk(prefs, """{"stt": {"providerId": "openai"}}""")
        assertEquals("", prefs.llmModelFor(RefineMode.POLISH))
    }

    @Test fun gleicherAnbieterMischtStufenModelleFeldweise() {
        val prefs = Prefs(ctx).apply {
            llmProviderId = "anthropic"
            setLlmModelFor(RefineMode.BEAUTIFY, "claude-opus")
        }
        importOk(prefs, """{"llm": {"stageModels": {"polish": "claude-haiku"}}}""")
        assertEquals("claude-haiku", prefs.llmModelFor(RefineMode.POLISH))
        assertEquals("claude-opus", prefs.llmModelFor(RefineMode.BEAUTIFY))
    }

    // --- Adressen wie ServerUrlCheck, Klartext ins Internet ist ein Fehler ------------------------

    @Test fun klartextInsInternetIstEinFehler() {
        listOf("stt", "sttFallback", "llm", "llmFallback").forEach { section ->
            val json = """{"$section": {"providerId": "custom", "baseUrl": "http://fremd.example/v1"}}"""
            val preview = SettingsSync.preview(Prefs(ctx), json)
            assertFalse(json, preview.canApply)
            assertTrue("$json -> ${preview.errors}", preview.errors.any { it.startsWith("$section.baseUrl: Unverschlüsselt") })
        }
    }

    @Test fun anbieterOhneHttpVerlangtHttps() {
        val preview = SettingsSync.preview(Prefs(ctx), """{"stt": {"providerId": "openai", "baseUrl": "http://api.example.com/v1"}}""")
        assertFalse(preview.canApply)
        assertTrue(preview.errors.toString(), preview.errors.any { it == "stt.baseUrl: Dieser Anbieter braucht https://" })
    }

    @Test fun wieErkennungPrueftDieAdresseGegenDenAnbieterDerErkennung() {
        val json = """{"stt": {"providerId": "openai"}, "llm": {"providerId": "same", "baseUrl": "http://text.example/v1"}}"""
        val preview = SettingsSync.preview(Prefs(ctx), json)
        assertFalse(preview.canApply)
        assertTrue(preview.errors.toString(), preview.errors.any { it == "llm.baseUrl: Dieser Anbieter braucht https://" })
    }

    @Test fun klartextZuPrivatenAdressenBleibtErlaubt() {
        listOf("http://192.168.1.5:8000/v1", "http://localhost:8000/v1", "http://server.local/v1", "https://fremd.example/v1").forEach { url ->
            val preview = SettingsSync.preview(Prefs(ctx), """{"stt": {"providerId": "custom", "baseUrl": "$url"}}""")
            assertEquals(url, emptyList<String>(), preview.errors)
        }
    }

    // --- Sprache ----------------------------------------------------------------------------------

    @Test fun deutschUndEnglischIstEineGueltigeSprache() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"language": "de+en"}""")
        assertEquals(Prefs.LANG_DE_EN, prefs.language)
    }

    @Test fun eigenerExportMitDeutschUndEnglischLaesstSichWiederImportieren() {
        val prefs = Prefs(ctx).apply { language = Prefs.LANG_DE_EN }
        assertEquals(emptyList<String>(), SettingsSync.preview(prefs, SettingsSync.export(prefs)).errors)
    }

    @Test fun ungueltigeSpracheNenntDieErlaubtenWerte() {
        val preview = SettingsSync.preview(Prefs(ctx), """{"language": "de+fr"}""")
        assertTrue(preview.errors.toString(), preview.errors.single().contains("\"de+en\""))
    }

    // --- Abgeschaltete OpenAI-Modelle ---------------------------------------------------------------

    @Test fun abgeschalteteOpenAiModelleWerdenZumNachfolger() {
        val prefs = Prefs(ctx)
        importOk(
            prefs,
            """{"stt": {"providerId": "openai", "model": "whisper-1"},
               "sttFallback": {"providerId": "openai", "model": "gpt-4o-transcribe"},
               "llm": {"providerId": "openai", "model": "gpt-5-nano", "stageModels": {"polish": "gpt-5-mini-2025-08-07"}},
               "llmFallback": {"providerId": "openai", "model": "gpt-5-mini"}}""",
        )
        assertEquals("gpt-transcribe", prefs.apiModel)
        assertEquals("gpt-transcribe", prefs.sttFallbackModel)
        assertEquals("gpt-5.6-luna", prefs.llmModel)
        assertEquals("gpt-5.6-luna", prefs.llmModelFor(RefineMode.POLISH))
        assertEquals("gpt-5.6-luna", prefs.llmFallbackModel)
    }

    @Test fun wieErkennungNutztDenAnbieterDerErkennungFuerDieStufen() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"stt": {"providerId": "openai"}, "llm": {"providerId": "same", "stageModels": {"polish": "gpt-5-mini"}}}""")
        assertEquals("gpt-5.6-luna", prefs.llmModelFor(RefineMode.POLISH))
    }

    @Test fun eigenerServerBehaeltSeineModellNamen() {
        val prefs = Prefs(ctx)
        importOk(prefs, """{"stt": {"providerId": "custom", "baseUrl": "https://w.example.org/v1", "model": "whisper-1"}}""")
        assertEquals("whisper-1", prefs.apiModel)
    }

    @Test fun teilDateiOhneAnbieterZaehltDenAnbieterDesGeraets() {
        val openai = Prefs(ctx).apply { sttProviderId = "openai" }
        importOk(openai, """{"stt": {"model": "whisper-1"}}""")
        assertEquals("gpt-transcribe", openai.apiModel)

        clear()
        val custom = Prefs(ctx).apply { sttProviderId = "custom" }
        importOk(custom, """{"stt": {"model": "whisper-1"}}""")
        assertEquals("whisper-1", custom.apiModel)
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
