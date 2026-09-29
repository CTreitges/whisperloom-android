package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Profil-Datenmodell und sein JSON — Robolectric wegen org.json (im reinen JVM-Test nur Stubs). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetProfileTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun lies(json: String) = WidgetProfile.fromJson(JSONObject(json))

    @Test fun einProfilUeberstehtDenJsonRoundtrip() {
        val p = WidgetProfile(
            id = "3f2a",
            name = "Einkauf",
            icon = ProfileIcon.Photo("3f2a-1759140000.png"),
            autoStop = true,
            pause = SpeechPause.LONG,
        )
        assertEquals(p, WidgetProfile.fromJson(p.toJson()))
        val liste = listOf(WidgetProfile.DEFAULT, p, WidgetProfile("b", icon = ProfileIcon.BuiltIn("star")))
        assertEquals(liste, WidgetProfile.decodeAll(WidgetProfile.encodeAll(liste)))
    }

    @Test fun fehlendeFelderBekommenDieDefaults() {
        val p = lies("""{"id":"x"}""")
        assertEquals(WidgetProfile("x"), p)
        assertEquals(ProfileIcon.BuiltIn("mic"), p!!.icon)
        assertFalse(p.autoStop)
        assertEquals(SpeechPause.NORMAL, p.pause)
    }

    @Test fun unbekannteFelderStoerenNicht() {
        val p = lies("""{"id":"x","name":"Arbeit","zielBridge":"spaeter","textstufe":3,"autoStop":true}""")
        assertEquals(WidgetProfile("x", name = "Arbeit", autoStop = true), p)
    }

    @Test fun jsonNullIstLeerUndNichtDerText_null() {
        // optString macht aus JSON-null sonst "null" — das Widget hiesse dann "null".
        val p = lies("""{"id":"x","name":null,"icon":null,"pause":null}""")
        assertEquals(WidgetProfile("x"), p)
    }

    @Test fun ohneIdIstEinEintragUnbrauchbar() {
        assertNull(lies("""{"name":"Waise"}"""))
        assertNull(lies("""{"id":"","name":"Leer"}"""))
        assertNull(lies("""{"id":null}"""))
    }

    @Test fun kaputtesJsonErgibtNurDasStandardprofil() {
        val nurStandard = listOf(WidgetProfile.DEFAULT)
        assertEquals(nurStandard, WidgetProfile.decodeAll(null))
        assertEquals(nurStandard, WidgetProfile.decodeAll(""))
        assertEquals(nurStandard, WidgetProfile.decodeAll("[{\"id\":"))
        assertEquals(nurStandard, WidgetProfile.decodeAll("""{"id":"x"}"""))
        assertEquals(nurStandard, WidgetProfile.decodeAll("""[1, "text", null, {}, {"id":""}]"""))
    }

    @Test fun dasStandardprofilStehtImmerVorn() {
        val liste = WidgetProfile.decodeAll("""[{"id":"a"},{"id":"default","name":"Haupt"},{"id":"b"}]""")
        assertEquals(listOf("default", "a", "b"), liste.map { it.id })
        assertEquals("Haupt", liste.first().name)
        assertEquals(listOf("default", "a"), WidgetProfile.decodeAll("""[{"id":"a"}]""").map { it.id })
    }

    @Test fun doppelteIdsZaehlenEinmalDerErsteGewinnt() {
        val liste = WidgetProfile.decodeAll("""[{"id":"a","name":"Erst"},{"id":"a","name":"Zweit"}]""")
        assertEquals(listOf("default", "a"), liste.map { it.id })
        assertEquals("Erst", liste[1].name)
    }

    @Test fun symboleWerdenAlsBOderPKodiert() {
        assertEquals("b:star", ProfileIcon.encode(ProfileIcon.BuiltIn("star")))
        assertEquals("p:a-1.png", ProfileIcon.encode(ProfileIcon.Photo("a-1.png")))
        assertEquals(ProfileIcon.BuiltIn("star"), ProfileIcon.decode("b:star"))
        assertEquals(ProfileIcon.Photo("a-1.png"), ProfileIcon.decode("p:a-1.png"))
        assertEquals("b:star", lies("""{"id":"x","icon":"b:star"}""")!!.toJson().getString("icon"))
    }

    @Test fun einUnbekanntesSymbolErgibtDasMikrofon() {
        val mic = ProfileIcon.BuiltIn("mic")
        assertEquals(mic, ProfileIcon.decode("b:rakete"))
        assertEquals(mic, ProfileIcon.decode("b:"))
        assertEquals(mic, ProfileIcon.decode("x:star"))
        assertEquals(mic, ProfileIcon.decode("star"))
        assertEquals(mic, ProfileIcon.decode(""))
        assertEquals(mic, ProfileIcon.decode("b"))
    }

    @Test fun einFotoNameIstNieEinPfad() {
        // Geloescht wird ueber diesen Namen — "../whisperloom.xml" loeschte sonst die Einstellungen.
        val mic = ProfileIcon.BuiltIn("mic")
        assertEquals(mic, ProfileIcon.decode("p:"))
        assertEquals(mic, ProfileIcon.decode("p:../whisperloom.xml"))
        assertEquals(mic, ProfileIcon.decode("p:sub/a.png"))
        assertEquals(mic, ProfileIcon.decode("p:..\\a.png"))
        assertEquals(mic, ProfileIcon.decode("p:.."))
    }

    @Test fun derNameWirdGetrimmtUndBei24ZeichenAbgeschnitten() {
        assertEquals("Einkauf", WidgetProfile.cleanName("  Einkauf \n"))
        assertEquals("a".repeat(24), WidgetProfile.cleanName("a".repeat(30)))
        assertEquals("a".repeat(24), WidgetProfile.cleanName("a".repeat(24)))
        assertEquals("Einkauf", lies("""{"id":"x","name":"  Einkauf  "}""")!!.name)
        assertEquals(24, lies("""{"id":"x","name":"${"b".repeat(40)}"}""")!!.name.length)
    }

    @Test fun beimKuerzenBleibtEinEmojiGanz() {
        // 23 Zeichen + Emoji (2 UTF-16-Einheiten): an Stelle 24 laege sonst ein halbes Surrogat-Paar.
        val emoji = String(Character.toChars(0x1F6D2)) // Einkaufswagen
        assertEquals(2, emoji.length)
        val name = "a".repeat(23) + emoji
        assertEquals("a".repeat(23), WidgetProfile.cleanName(name))
        val passt = "a".repeat(22) + emoji
        assertEquals(passt, WidgetProfile.cleanName(passt))
        // Kein Leerzeichen am Ende nach dem Kuerzen.
        assertEquals("a".repeat(22), WidgetProfile.cleanName("a".repeat(22) + "  zu lang"))
    }

    @Test fun einLeererNameZeigtSprachauftrag() {
        assertEquals("Sprachauftrag", WidgetProfile("x").displayName(ctx))
        assertEquals("Einkauf", WidgetProfile("x", name = "Einkauf").displayName(ctx))
    }

    @Test fun dasStandardprofilIstAlsSolchesErkennbar() {
        assertTrue(WidgetProfile.DEFAULT.isDefault)
        assertEquals("default", WidgetProfile.DEFAULT_ID)
        assertFalse(WidgetProfile("x").isDefault)
    }

    @Test fun dieSprechpausenHabenDieVereinbartenLaengen() {
        assertEquals(1200L, SpeechPause.SHORT.ms)
        assertEquals(2000L, SpeechPause.NORMAL.ms)
        assertEquals(3500L, SpeechPause.LONG.ms)
        // Gespeicherte Schluessel sind festgeschrieben — ein Umbenennen setzte jedes Profil zurueck.
        assertEquals(listOf("short", "normal", "long"), SpeechPause.entries.map { it.key })
    }

    @Test fun dieSprechpauseWirdTolerantGelesen() {
        SpeechPause.entries.forEach { pause ->
            assertEquals(pause, lies(WidgetProfile("x", pause = pause).toJson().toString())!!.pause)
        }
        assertEquals(SpeechPause.NORMAL, lies("""{"id":"x","pause":"extralang"}""")!!.pause)
        assertEquals(SpeechPause.NORMAL, lies("""{"id":"x","pause":"LONG"}""")!!.pause)
        assertEquals(SpeechPause.NORMAL, lies("""{"id":"x","pause":3500}""")!!.pause)
        assertEquals(SpeechPause.NORMAL, SpeechPause.fromKey(null))
    }

    @Test fun dasGespeicherteFormatBleibtStabil() {
        val json = WidgetProfile("x", name = "N", icon = ProfileIcon.BuiltIn("home"), autoStop = true, pause = SpeechPause.SHORT).toJson()
        assertEquals("x", json.getString("id"))
        assertEquals("N", json.getString("name"))
        assertEquals("b:home", json.getString("icon"))
        assertTrue(json.getBoolean("autoStop"))
        assertEquals("short", json.getString("pause"))
    }
}
