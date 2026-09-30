package com.chris.whisperloom.agent

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Profile und die Zuordnung Widget-Instanz → Profil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetProfileStoreTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sp get() = ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE)

    /** Die Einstellungen, in denen bis 3.7.0 der eine Server fuer alle Widgets stand. */
    private val alt get() = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private lateinit var store: WidgetProfileStore

    @Before fun leeren() {
        sp.edit().clear().commit()
        alt.edit().clear().commit()
        File(ctx.filesDir, WidgetPhoto.DIR).deleteRecursively()
        store = WidgetProfileStore(ctx)
    }

    private fun foto(name: String): File =
        File(File(ctx.filesDir, WidgetPhoto.DIR).apply { mkdirs() }, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun leererSpeicherKenntNurDasVirtuelleStandardprofil() {
        assertEquals(listOf(WidgetProfile.DEFAULT), store.all())
        assertEquals(WidgetProfile.DEFAULT, store.get(WidgetProfile.DEFAULT_ID))
        assertEquals(WidgetProfile.DEFAULT, store.forWidget(7))
        assertFalse(store.isBound(7))
        assertEquals(0, store.boundCount(WidgetProfile.DEFAULT_ID))
        assertTrue("Lesen darf nichts schreiben: ${sp.all}", sp.all.isEmpty())
    }

    @Test fun eineUnbekannteIdErgibtDasStandardprofil() {
        assertNull(store.get("gibt-es-nicht"))
        store.bind(3, "gibt-es-nicht")
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(3).id)
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(99).id)
    }

    @Test fun anlegenHaengtHintenAnMitDefaults() {
        val p = store.create("  Einkauf  ")
        assertEquals("Einkauf", p.name)
        assertFalse(p.autoStop)
        assertEquals(SpeechPause.NORMAL, p.pause)
        assertEquals(ProfileIcon.BuiltIn("mic"), p.icon)
        assertEquals(listOf(WidgetProfile.DEFAULT_ID, p.id), store.all().map { it.id })
        assertNotEquals(p.id, store.create("Arbeit").id)
        assertEquals("Neue Instanz liest dasselbe", p, WidgetProfileStore(ctx).get(p.id))
    }

    @Test fun speichernErsetztPerIdUndKuerztDenNamen() {
        val p = store.create("Einkauf")
        store.save(p.copy(name = "  " + "x".repeat(30), autoStop = true, pause = SpeechPause.SHORT))
        val neu = store.get(p.id)!!
        assertEquals("x".repeat(24), neu.name)
        assertTrue(neu.autoStop)
        assertEquals(SpeechPause.SHORT, neu.pause)
        assertEquals(2, store.all().size)
    }

    @Test fun speichernEinerNeuenIdLegtSieAn() {
        store.save(WidgetProfile("von-aussen", name = "Import"))
        assertEquals(listOf(WidgetProfile.DEFAULT_ID, "von-aussen"), store.all().map { it.id })
    }

    @Test fun dasStandardprofilIstNichtLoeschbarAberBearbeitbar() {
        store.bind(1, WidgetProfile.DEFAULT_ID)
        assertEquals(0, store.delete(WidgetProfile.DEFAULT_ID))
        assertEquals(WidgetProfile.DEFAULT_ID, store.all().first().id)
        assertTrue(store.isBound(1))

        store.save(WidgetProfile.DEFAULT.copy(name = "Haupt", icon = ProfileIcon.BuiltIn("star")))
        assertEquals("Haupt", store.all().first().name)
        assertEquals("Haupt", store.forWidget(1).name)
        assertEquals(1, store.all().size)
    }

    @Test fun loeschenBindetAufStandardUmUndZaehltDieWidgets() {
        val a = store.create("A")
        val b = store.create("B")
        store.bind(1, a.id)
        store.bind(2, a.id)
        store.bind(3, b.id)
        assertEquals(2, store.delete(a.id))
        assertEquals(listOf(WidgetProfile.DEFAULT_ID, b.id), store.all().map { it.id })
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(1).id)
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(2).id)
        assertEquals(b.id, store.forWidget(3).id)
        assertTrue("Die Bindung bleibt bestehen, nur auf Standard", store.isBound(1))
        assertEquals(2, store.boundCount(WidgetProfile.DEFAULT_ID))
        assertEquals(0, store.delete(a.id))
    }

    @Test fun loeschenEntferntDasFoto() {
        val datei = foto("a-1.png")
        val a = store.create("A")
        store.save(a.copy(icon = ProfileIcon.Photo("a-1.png")))
        assertTrue(datei.isFile)
        store.delete(a.id)
        assertFalse(datei.exists())
    }

    @Test fun einFotoWechselLoeschtDieAlteDatei() {
        val alt = foto("a-1.png")
        val neu = foto("a-2.png")
        val a = store.create("A")
        store.save(a.copy(icon = ProfileIcon.Photo("a-1.png")))

        store.save(store.get(a.id)!!.copy(name = "Umbenannt"))
        assertTrue("Gleiches Foto bleibt", alt.isFile)

        store.save(store.get(a.id)!!.copy(icon = ProfileIcon.Photo("a-2.png")))
        assertFalse(alt.exists())
        assertTrue(neu.isFile)

        store.save(store.get(a.id)!!.copy(icon = ProfileIcon.BuiltIn("home")))
        assertFalse("Zurueck auf ein Symbol raeumt das Foto weg", neu.exists())
    }

    @Test fun bindenUndLoesen() {
        val a = store.create("A")
        store.bind(5, a.id)
        assertTrue(store.isBound(5))
        assertEquals(a.id, store.forWidget(5).id)
        store.bind(5, WidgetProfile.DEFAULT_ID)
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(5).id)
        store.bind(6, a.id)
        store.unbind(intArrayOf(5, 6, 404))
        assertFalse(store.isBound(5))
        assertFalse(store.isBound(6))
        assertEquals(a, store.get(a.id))
    }

    @Test fun wiederherstellenZiehtDieBindungenMit() {
        val a = store.create("A")
        val b = store.create("B")
        store.bind(10, a.id)
        store.bind(11, b.id)
        store.remap(intArrayOf(10, 11), intArrayOf(20, 21))
        assertFalse(store.isBound(10))
        assertFalse(store.isBound(11))
        assertEquals(a.id, store.forWidget(20).id)
        assertEquals(b.id, store.forWidget(21).id)
    }

    @Test fun wiederherstellenMitUeberschneidendenIds() {
        // Alte 1,2 werden neue 2,3: Id 2 wechselt von "b" auf "a", darf aber nicht verloren gehen.
        val a = store.create("A")
        val b = store.create("B")
        store.bind(1, a.id)
        store.bind(2, b.id)
        store.remap(intArrayOf(1, 2), intArrayOf(2, 3))
        assertFalse(store.isBound(1))
        assertEquals(a.id, store.forWidget(2).id)
        assertEquals(b.id, store.forWidget(3).id)
    }

    @Test fun wiederherstellenOhneBindungBleibtUngebunden() {
        store.remap(intArrayOf(10), intArrayOf(20))
        assertFalse(store.isBound(20))
    }

    @Test fun uebernehmenBindetNurUngebundeneAnDasStandardprofil() {
        val a = store.create("A")
        store.bind(10, a.id)
        store.adopt(intArrayOf(10, 11, 12))
        assertEquals("Eine gewaehlte Bindung bleibt", a.id, store.forWidget(10).id)
        assertTrue(store.isBound(11))
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(11).id)
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(12).id)
        assertEquals(1, store.boundCount(a.id))

        store.adopt(IntArray(0))
        assertFalse(store.isBound(13))
    }

    @Test fun behaltenRaeumtVerschwundeneWidgetsWeg() {
        val a = store.create("A")
        store.bind(1, a.id)
        store.bind(2, a.id)
        store.bind(3, WidgetProfile.DEFAULT_ID)
        store.retain(intArrayOf(2, 99))
        assertFalse(store.isBound(1))
        assertTrue(store.isBound(2))
        assertFalse(store.isBound(3))
        assertEquals(1, store.boundCount(a.id))
        assertEquals("Profile bleiben", 2, store.all().size)
    }

    @Test fun zaehlenKenntNurBindungen() {
        val a = store.create("A")
        store.bind(1, a.id)
        store.bind(2, a.id)
        store.bind(3, WidgetProfile.DEFAULT_ID)
        assertEquals(2, store.boundCount(a.id))
        assertEquals(1, store.boundCount(WidgetProfile.DEFAULT_ID))
        assertEquals(0, store.boundCount("gibt-es-nicht"))
    }

    @Test fun fremdeSchluesselGeltenNichtAlsBindung() {
        val a = store.create("A")
        sp.edit().putString("w_abc", a.id).putInt("w_5", 1).putString("x_7", a.id).commit()
        assertEquals(0, store.boundCount(a.id))
        store.retain(intArrayOf())
        assertTrue("Unbekanntes bleibt unangetastet", sp.contains("w_abc"))
        assertEquals(2, store.all().size)
    }

    @Test fun kaputtesJsonImSpeicherErgibtDasStandardprofil() {
        sp.edit().putString("profiles", "{kaputt").commit()
        assertEquals(listOf(WidgetProfile.DEFAULT), store.all())
        assertEquals(WidgetProfile.DEFAULT, store.forWidget(1))
        val p = store.create("Neu")
        assertEquals(listOf(WidgetProfile.DEFAULT_ID, p.id), store.all().map { it.id })
    }

    // --- Server je Widget: Token und Backup ---------------------------------------

    private fun xml(name: String): String =
        listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name").map(::File).first { it.exists() }.readText()

    @Test fun dasServerTokenLiegtNurInDerBackupFreienProfilDatei() {
        store.save(WidgetProfile("a", serverUrl = "https://b.example.de", serverToken = "streng-geheim-42"))
        assertTrue(sp.getString("profiles", "")!!.contains("streng-geheim-42"))
        assertFalse(alt.all.values.any { it.toString().contains("streng-geheim-42") })

        val ausschluss = """<exclude domain="sharedpref" path="${WidgetProfileStore.FILE}.xml" />"""
        val extraction = xml("data_extraction_rules.xml")
        listOf(
            xml("backup_rules.xml"),
            extraction.substringAfter("<cloud-backup").substringBefore("</cloud-backup>"),
            extraction.substringAfter("<device-transfer").substringBefore("</device-transfer>"),
        ).forEach { assertTrue("Profil-Datei nicht ausgeschlossen: $it", it.contains(ausschluss)) }
    }

    // --- Migration: bis 3.7.0 ein Server fuer alle Widgets ------------------------

    private fun alterServer(url: String = "https://alt.example.de", token: String = "alt-token") {
        alt.edit().putString("agent_url", url).putString("agent_token", token).commit()
    }

    private fun migrieren() = store.migrateLegacyServer(alt, "Sprachauftrag")

    @Test fun dieMigrationGibtJedemProfilDenAltenServer() {
        alterServer()
        val a = store.create("A")

        migrieren()

        val alle = store.all()
        assertEquals(listOf(WidgetProfile.DEFAULT_ID, a.id), alle.map { it.id })
        alle.forEach {
            assertEquals(it.id, "https://alt.example.de", it.serverUrl)
            assertEquals(it.id, "alt-token", it.serverToken)
            assertTrue(it.serverReady)
        }
        assertTrue("Das virtuelle Standardprofil ist jetzt gespeichert", sp.getString("profiles", "")!!.contains("\"default\""))
        assertEquals("Und hat einen Namen", "Sprachauftrag", store.get(WidgetProfile.DEFAULT_ID)!!.name)
        assertEquals("A", store.get(a.id)!!.name)
    }

    @Test fun einProfilMitEigenemServerBleibtUnberuehrt() {
        alterServer()
        val eigen = WidgetProfile("e", name = "Eigen", serverUrl = "https://neu.example.de", serverToken = "neu")
        store.save(eigen)
        val ohneName = store.create("")

        migrieren()

        assertEquals(eigen, store.get("e"))
        assertEquals("Leere Namen anderer Profile schreibt die Migration nicht um", "", store.get(ohneName.id)!!.name)
        assertEquals("https://alt.example.de", store.get(ohneName.id)!!.serverUrl)
    }

    @Test fun dieAltenSchluesselVerschwindenErstNachDemSchreiben() {
        alterServer()
        var imProfilBeimEntfernen: String? = null
        val horcher = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "agent_url") imProfilBeimEntfernen = WidgetProfileStore(ctx).get(WidgetProfile.DEFAULT_ID)!!.serverUrl
        }
        alt.registerOnSharedPreferenceChangeListener(horcher)
        try {
            migrieren()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            alt.unregisterOnSharedPreferenceChangeListener(horcher)
        }

        assertEquals("Beim Entfernen stand der Server schon im Profil", "https://alt.example.de", imProfilBeimEntfernen)
        assertFalse(alt.contains("agent_url"))
        assertFalse(alt.contains("agent_token"))
    }

    @Test fun einZweiterLaufAendertNichts() {
        alterServer()
        migrieren()
        val nachDemErsten = sp.all.toMap()
        migrieren()
        assertEquals(nachDemErsten, sp.all.toMap())

        // Leert der Nutzer danach den Server eines Widgets, fuellt ihn kein spaeterer Start wieder auf.
        store.save(store.get(WidgetProfile.DEFAULT_ID)!!.copy(serverUrl = "", serverToken = ""))
        migrieren()
        assertEquals("", store.get(WidgetProfile.DEFAULT_ID)!!.serverUrl)
    }

    @Test fun nurEineAlteAdresseWirdUebernommenDasTokenBleibtLeer() {
        // In 3.7.0 speicherte jedes Feld einzeln — ein halb eingerichteter Server ist moeglich.
        alterServer(token = "")
        val a = store.create("A")

        migrieren()

        listOf(WidgetProfile.DEFAULT_ID, a.id).map { store.get(it)!! }.forEach {
            assertEquals(it.id, "https://alt.example.de", it.serverUrl)
            assertEquals(it.id, "", it.serverToken)
            assertFalse("Ohne Token nicht bereit", it.serverReady)
        }
        assertFalse(alt.contains("agent_url"))
        assertFalse(alt.contains("agent_token"))
    }

    @Test fun nurEinAltesTokenWirdUebernommenDieAdresseBleibtLeer() {
        alterServer(url = "")
        val a = store.create("A")

        migrieren()

        listOf(WidgetProfile.DEFAULT_ID, a.id).map { store.get(it)!! }.forEach {
            assertEquals(it.id, "", it.serverUrl)
            assertEquals(it.id, "alt-token", it.serverToken)
            assertFalse("Ohne Adresse nicht bereit", it.serverReady)
        }
        assertFalse(alt.contains("agent_url"))
        assertFalse(alt.contains("agent_token"))
    }

    @Test fun ohneAltenServerBleibtDerSpeicherLeer() {
        migrieren()
        assertTrue("Lesen darf nichts schreiben: ${sp.all}", sp.all.isEmpty())

        alterServer(url = "   ", token = "")
        migrieren()
        assertTrue("Nur Leerzeichen zaehlt als leer: ${sp.all}", sp.all.isEmpty())
    }
}
