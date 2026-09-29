package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Profile und die Zuordnung Widget-Instanz → Profil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetProfileStoreTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sp get() = ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE)
    private lateinit var store: WidgetProfileStore

    @Before fun leeren() {
        sp.edit().clear().commit()
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
}
