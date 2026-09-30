package com.chris.whisperloom.ui.state

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.WidgetPhoto
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
