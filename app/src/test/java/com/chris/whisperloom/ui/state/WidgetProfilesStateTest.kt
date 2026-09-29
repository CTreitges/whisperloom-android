package com.chris.whisperloom.ui.state

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.VoiceTaskWidget
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
