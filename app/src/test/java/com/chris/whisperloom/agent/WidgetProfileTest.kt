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
            showName = false,
            serverUrl = "https://bridge.example.de",
            serverToken = "geheim",
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
        // Profile von vor 3.7.1: Sprach-Command-Widget, Name sichtbar, noch ohne eigenen Server.
        assertEquals(WidgetKind.VOICE_COMMAND, p.kind)
        assertTrue(p.showName)
        assertEquals("", p.serverUrl)
        assertEquals("", p.serverToken)
        assertFalse(p.serverReady)
    }

    @Test fun unbekannteFelderStoerenNicht() {
        val p = lies("""{"id":"x","name":"Arbeit","zielBridge":"spaeter","textstufe":3,"autoStop":true}""")
        assertEquals(WidgetProfile("x", name = "Arbeit", autoStop = true), p)
    }

    @Test fun jsonNullIstLeerUndNichtDerText_null() {
        // optString macht aus JSON-null sonst "null" — das Widget hiesse dann "null".
        val p = lies("""{"id":"x","name":null,"icon":null,"pause":null,"kind":null,"showName":null,"serverUrl":null,"serverToken":null}""")
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

    @Test fun eineAlleinStehendeEmojiHaelfteFaelltWeg() {
        // So kam sie aus dem Namensfeld (take(24)): genau 24 Einheiten, also "nicht zu lang".
        val emoji = String(Character.toChars(0x1F6D2))
        val halb = "a".repeat(23) + emoji[0]
        assertEquals(24, halb.length)
        assertEquals("a".repeat(23), WidgetProfile.cleanName(halb))

        assertEquals("a".repeat(23), WidgetProfile.clip("a".repeat(23) + emoji))
        assertEquals("a".repeat(22) + emoji, WidgetProfile.clip("a".repeat(22) + emoji))
        assertEquals("Waehrend des Tippens wird nicht getrimmt", "Einkauf ", WidgetProfile.clip("Einkauf "))
        assertEquals("", WidgetProfile.clip(""))
    }

    @Test fun einLeererNameZeigtSprachCommand() {
        assertEquals("Sprach-Command", WidgetProfile("x").displayName(ctx))
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
        val json = WidgetProfile(
            "x", name = "N", icon = ProfileIcon.BuiltIn("home"), autoStop = true, pause = SpeechPause.SHORT,
            showName = false, serverUrl = "https://b.example.de", serverToken = "t",
        ).toJson()
        assertEquals("x", json.getString("id"))
        assertEquals("N", json.getString("name"))
        assertEquals("b:home", json.getString("icon"))
        assertTrue(json.getBoolean("autoStop"))
        assertEquals("short", json.getString("pause"))
        assertEquals("voice_command", json.getString("kind"))
        assertFalse(json.getBoolean("showName"))
        assertEquals("https://b.example.de", json.getString("serverUrl"))
        assertEquals("t", json.getString("serverToken"))
    }

    // --- Server je Widget (3.7.1) -------------------------------------------------

    @Test fun derServerIstErstMitAdresseUndTokenBereit() {
        assertFalse("Ohne Adresse kann nichts gesendet werden", WidgetProfile("x", serverToken = "geheim").serverReady)
        assertFalse("Ohne Token kann nichts gesendet werden", WidgetProfile("x", serverUrl = "https://b.example.de").serverReady)
        assertFalse("Nur Leerzeichen", WidgetProfile("x", serverUrl = "https://b.example.de", serverToken = "  ").serverReady)
        assertTrue(WidgetProfile("x", serverUrl = "https://b.example.de", serverToken = "geheim").serverReady)
    }

    @Test fun eineUnbrauchbareAdresseZaehltNichtAlsEingerichtet() {
        // Sonst stuende das Widget auf "bereit", der Nutzer spraeche, die Transkription waere
        // bezahlt — und erst danach kaeme der Fehler.
        assertFalse("Adresse ohne Schema", WidgetProfile("x", serverUrl = "bridge.example.de", serverToken = "geheim").serverReady)
        assertFalse("Nur Leerzeichen", WidgetProfile("x", serverUrl = "   ", serverToken = "geheim").serverReady)
        assertTrue("http im LAN ist erlaubt", WidgetProfile("x", serverUrl = "http://192.168.1.5:8080", serverToken = "geheim").serverReady)
    }

    @Test fun dieAdresseWirdGetrimmtGelesen() {
        assertEquals("https://b.example.de", lies("""{"id":"x","serverUrl":"  https://b.example.de "}""")!!.serverUrl)
    }

    @Test fun einUnbekannterTypWirdZumSprachCommand() {
        assertEquals(WidgetKind.VOICE_COMMAND, lies("""{"id":"x","kind":"uhr"}""")!!.kind)
        assertEquals(WidgetKind.VOICE_COMMAND, lies("""{"id":"x","kind":3}""")!!.kind)
    }

    @Test fun dasTokenStehtNieImText() {
        // Ein Log.d("$profile") oder ein Absturzbericht darf das Token nicht zeigen.
        val mit = WidgetProfile("x", name = "Einkauf", serverUrl = "https://b.example.de", serverToken = "streng-geheim-42")
        assertFalse(mit.toString().contains("streng-geheim-42"))
        assertTrue(mit.toString().contains("serverToken=***"))
        assertTrue("Der Rest bleibt lesbar", mit.toString().contains("name=Einkauf"))
        assertTrue(WidgetProfile("x").toString().contains("serverToken=)"))
    }

    @Test fun dieWidgetTypenSindNachStufeGetrennt() {
        assertEquals(listOf(WidgetKind.VOICE_COMMAND), WidgetKind.of(Tier.PRO))
        assertTrue("Normale Widgets folgen spaeter", WidgetKind.of(Tier.NORMAL).isEmpty())
        // Gespeicherte Schluessel sind festgeschrieben — ein Umbenennen setzte jedes Profil zurueck.
        assertEquals(listOf("voice_command"), WidgetKind.entries.map { it.key })
        assertEquals(WidgetKind.VOICE_COMMAND, WidgetKind.fromKey(null))
    }
}
