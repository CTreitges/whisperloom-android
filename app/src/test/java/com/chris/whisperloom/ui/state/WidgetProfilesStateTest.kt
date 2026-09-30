package com.chris.whisperloom.ui.state

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.Tier
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.agent.WidgetKind
import com.chris.whisperloom.agent.WidgetPhoto
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Der Compose-Spiegel der Widget-Profile: schreibt sofort durch und liest danach neu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetProfilesStateTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(ctx)

    @Before fun leeren() {
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        VoiceTaskStore(ctx).clear()
    }

    private fun widget(): Int = shadowOf(manager).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    @Test fun ohneProfileGibtEsNurDasStandardprofilUndKeineWidgets() {
        val s = WidgetProfilesState(ctx)
        assertEquals(listOf(WidgetProfile.DEFAULT), s.profiles)
        assertTrue(s.placed.isEmpty())
    }

    @Test fun anlegenUndSpeichernStehenSofortImSpeicherUndImSpiegel() {
        val s = WidgetProfilesState(ctx)
        val p = s.create("Einkauf")
        assertEquals(listOf("", "Einkauf"), s.profiles.map { it.name })

        s.save(p.copy(autoStop = true, pause = SpeechPause.LONG), redraw = false)
        val gespeichert = WidgetProfileStore(ctx).get(p.id)!!
        assertTrue(gespeichert.autoStop)
        assertEquals(SpeechPause.LONG, gespeichert.pause)
        assertEquals(gespeichert, s.profile(p.id))
    }

    @Test fun platzierteWidgetsInReihenfolgeMitIhremProfil() {
        val s0 = WidgetProfilesState(ctx)
        val einkauf = s0.create("Einkauf")
        val a = widget()
        val b = widget()
        val s = WidgetProfilesState(ctx)
        assertEquals(listOf(a, b).sorted(), s.placed.map { it.widgetId })
        assertTrue(s.placed.all { it.profile.isDefault })

        s.bind(b, einkauf.id)
        assertEquals(einkauf.id, s.placed.first { it.widgetId == b }.profile.id)
        assertEquals(einkauf.id, WidgetProfileStore(ctx).forWidget(b).id)
    }

    @Test fun loeschenStelltGebundeneWidgetsAufStandardUm() {
        val s = WidgetProfilesState(ctx)
        val p = s.create("Einkauf")
        val id = widget()
        s.bind(id, p.id)
        assertEquals(1, s.boundCount(p.id))

        assertEquals(1, s.delete(p.id))
        assertNull(s.profile(p.id))
        assertTrue(s.placed.single().profile.isDefault)
        assertEquals(0, s.delete(WidgetProfile.DEFAULT_ID))
        assertEquals(1, s.profiles.size)
    }

    @Test fun neuLesenVergisstBindungenVerschwundenerWidgets() {
        val store = WidgetProfileStore(ctx)
        val p = store.create("Einkauf")
        val lebt = widget()
        store.bind(lebt, p.id)
        store.bind(9999, p.id) // Widget ohne onDeleted vom Startbildschirm verschwunden

        val s = WidgetProfilesState(ctx)
        s.reload()

        assertTrue(store.isBound(lebt))
        assertFalse(store.isBound(9999))
        assertEquals(1, s.boundCount(p.id))
    }

    @Test fun einNeuesProfilBekommtDieKleinsteFreieNummer() {
        val s = WidgetProfilesState(ctx)
        val zwei = s.createNew()
        val drei = s.createNew()
        assertEquals(listOf("Sprach-Command 2", "Sprach-Command 3"), listOf(zwei.name, drei.name))

        s.delete(zwei.id)
        assertEquals("Die Luecke wird gefuellt", "Sprach-Command 2", s.createNew().name)
        assertEquals("Kein Doppelname", s.profiles.size, s.profiles.map { it.name }.toSet().size)
        assertEquals("Sprach-Command 4", s.createNew().name)
    }

    @Test fun einNeuesWidgetUebernimmtDenErstenBrauchbarenServer() {
        val store = WidgetProfileStore(ctx)
        store.save(WidgetProfile("kaputt", name = "Kaputt", serverUrl = "ohne-schema.de", serverToken = "x"))
        store.save(WidgetProfile("gut", name = "Gut", serverUrl = "https://bridge.example.de", serverToken = "geheim"))
        val s = WidgetProfilesState(ctx)

        val neu = s.createNew()

        assertEquals(WidgetKind.VOICE_COMMAND, neu.kind)
        assertEquals("https://bridge.example.de", neu.serverUrl)
        assertEquals("geheim", neu.serverToken)
        assertEquals("Sofort gespeichert", neu, store.get(neu.id))
        assertEquals(neu, s.profile(neu.id))
    }

    @Test fun ohneEingerichtetenServerStartetEinNeuesWidgetLeer() {
        val neu = WidgetProfilesState(ctx).createNew()
        assertEquals("", neu.serverUrl)
        assertEquals("", neu.serverToken)
        assertFalse(neu.serverReady)
    }

    @Test fun dieProfileLassenSichNachStufeFiltern() {
        val s = WidgetProfilesState(ctx)
        s.createNew()
        assertEquals("Alle bisherigen Profile sind Pro Widgets", s.profiles, s.profiles(Tier.PRO))
        assertTrue(s.profiles(Tier.NORMAL).isEmpty())
    }

    // --- Loeschen verwirft den offenen Auftrag des Profils ------------------------

    private val echtesCancel = VoiceTaskWork.cancelImpl

    @After fun zuruecksetzen() {
        VoiceTaskWork.cancelImpl = echtesCancel
    }

    /** Offener Auftrag des Profils [profileId]; zaehlt, wie oft der Job abgebrochen wird. */
    private fun offenerAuftrag(profileId: String): () -> Int {
        VoiceTaskStore(ctx).begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", profileId)
        var abgebrochen = 0
        VoiceTaskWork.cancelImpl = { abgebrochen++ }
        return { abgebrochen }
    }

    @Test fun einGeloeschtesProfilNimmtSeinenOffenenAuftragMit() {
        val s = WidgetProfilesState(ctx)
        val p = s.create("Einkauf")
        val abgebrochen = offenerAuftrag(p.id)

        s.delete(p.id)

        assertFalse("Sein Server ist weg — an einen anderen darf der Auftrag nicht", VoiceTaskStore(ctx).hasWork)
        assertFalse("Die Karte \"Offener Auftrag\" geht mit", s.hasWork)
        assertEquals(1, abgebrochen())
    }

    @Test fun derAuftragEinesAnderenProfilsBleibt() {
        val s = WidgetProfilesState(ctx)
        val p = s.create("Einkauf")
        val abgebrochen = offenerAuftrag(WidgetProfile.DEFAULT_ID)

        s.delete(p.id)
        s.delete(WidgetProfile.DEFAULT_ID) // nicht loeschbar — also bleibt auch sein Auftrag

        assertTrue(VoiceTaskStore(ctx).hasWork)
        assertTrue(s.hasWork)
        assertEquals(0, abgebrochen())
    }

    @Test fun verwerfenBrichtDenAuftragAbUndLeertDenSpiegel() {
        val abgebrochen = offenerAuftrag(WidgetProfile.DEFAULT_ID)
        val s = WidgetProfilesState(ctx)
        assertTrue(s.hasWork)

        s.discardWork()

        assertFalse(VoiceTaskStore(ctx).hasWork)
        assertFalse(s.hasWork)
        assertEquals(1, abgebrochen())
    }

    @Test fun neuLesenSiehtEinenInzwischenGesendetenAuftrag() {
        offenerAuftrag(WidgetProfile.DEFAULT_ID)
        val s = WidgetProfilesState(ctx)
        VoiceTaskStore(ctx).clear() // der Worker hat gesendet, waehrend die App im Hintergrund lag
        assertTrue("Vorbedingung: noch der alte Stand", s.hasWork)

        s.reload()

        assertFalse(s.hasWork)
    }

    @Test fun neuLesenRaeumtFotosAbgebrochenerImporteWeg() {
        val store = WidgetProfileStore(ctx)
        val ordner = File(ctx.filesDir, WidgetPhoto.DIR).apply { mkdirs() }
        fun foto(name: String, alterMs: Long) = File(ordner, name).apply {
            writeText("png")
            setLastModified(System.currentTimeMillis() - alterMs)
        }
        val genutzt = foto("p1-1.png", 10 * 60_000L)
        store.save(store.create("Einkauf").copy(icon = ProfileIcon.Photo(genutzt.name)))
        val verwaist = foto("p1-2.png", 10 * 60_000L) // Sheet waehrend des Imports geschlossen
        val frisch = foto("p1-3.png", 0) // Import laeuft noch, das Profil ist noch nicht gespeichert

        WidgetProfilesState(ctx).reload()

        assertTrue(genutzt.isFile)
        assertFalse(verwaist.isFile)
        assertTrue("Ein laufender Import bleibt unberuehrt", frisch.isFile)
    }

    @Test fun ohneWidgetDienstStuerztNichtsAb() {
        // Geraete ohne android.software.app_widgets (TV, Auto, abgespeckte Images): getInstance
        // liefert null. Der Einstellungs-Hub legt diesen Zustand bei jedem Oeffnen an.
        val ohneWidgetDienst = object : ContextWrapper(ctx) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? =
                if (name == Context.APPWIDGET_SERVICE) null else super.getSystemService(name)
        }
        assertNull("Vorbedingung", AppWidgetManager.getInstance(ohneWidgetDienst))

        val s = WidgetProfilesState(ohneWidgetDienst)
        assertTrue(s.placed.isEmpty())
        val p = s.create("Einkauf")
        s.save(p.copy(autoStop = true)) // mit Neuzeichnen
        s.reload()

        assertEquals(2, s.profiles.size)
        assertTrue(s.placed.isEmpty())
    }

    @Test fun neuLesenSiehtAenderungenVonAussen() {
        val s = WidgetProfilesState(ctx)
        WidgetProfileStore(ctx).create("Von aussen")
        val id = widget()
        assertEquals(1, s.profiles.size)

        s.reload()

        assertEquals(2, s.profiles.size)
        assertEquals(listOf(id), s.placed.map { it.widgetId })
    }
}
