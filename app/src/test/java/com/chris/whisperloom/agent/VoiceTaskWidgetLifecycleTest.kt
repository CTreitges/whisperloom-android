package com.chris.whisperloom.agent

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Was mit der Profil-Zuordnung passiert, wenn Widgets verschwinden, wiederkommen oder wachsen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWidgetLifecycleTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(ctx)
    private lateinit var store: WidgetProfileStore

    @Before fun leeren() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetProfileStore(ctx)
    }

    private fun widget(): Int = shadowOf(manager).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    /** Wie das System: als Broadcast an den Provider, der ihn selbst auf die Callbacks verteilt. */
    private fun senden(intent: Intent) = VoiceTaskWidget().onReceive(ctx, intent)

    private fun einsZuEins() = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 57)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 127)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 51)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 102)
    }

    @Test fun einEntferntesWidgetVerliertSeineZuordnung() {
        val profil = store.create("Einkauf")
        val id = widget()
        store.bind(id, profil.id)

        senden(Intent(AppWidgetManager.ACTION_APPWIDGET_DELETED).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))

        assertFalse(store.isBound(id))
        assertEquals(0, store.boundCount(profil.id))
        assertNotNull("Das Profil selbst bleibt", store.get(profil.id))
    }

    @Test fun nachDerWiederherstellungZiehtDasProfilMit() {
        val profil = store.create("Einkauf")
        store.bind(7, profil.id)
        val neu = widget()

        senden(
            Intent(AppWidgetManager.ACTION_APPWIDGET_RESTORED)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS, intArrayOf(7))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(neu)),
        )

        assertEquals(profil.id, store.forWidget(neu).id)
        assertFalse(store.isBound(7))
        assertTrue(manager.getAppWidgetOptions(neu).getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED))
        // Neu gezeichnet (onUpdate folgt im Provider direkt auf onRestored) — schon mit dem Profil.
        assertTrue(shadowOf(manager).getViewFor(neu).contentDescription.startsWith("Einkauf"))
    }

    @Test fun abAndroid12ZeichnetEineGroessenaenderungNichtNeu() {
        // Das System waehlt selbst aus der Groessen-Map; ein Neuzeichnen kostete nur Arbeit —
        // und uebermalte mit resolve() einen Zustand, den gerade Dienst oder Worker zeigen.
        val id = widget()
        VoiceTaskWidgetView.push(ctx, VoiceTaskState.ERROR, message = "Merkzeichen")

        manager.updateAppWidgetOptions(id, einsZuEins())

        val zeile = shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString()
        assertTrue(zeile.contains("Merkzeichen"))
    }

    @Test @Config(sdk = [30])
    fun unterAndroid12ZeichnetEineGroessenaenderungNeu() {
        val id = widget()
        // Ohne gemeldete Groesse: der gewohnte Stapel mit Namen.
        assertNotNull(shadowOf(manager).getViewFor(id).findViewById<View>(R.id.widget_name))

        manager.updateAppWidgetOptions(id, einsZuEins())

        assertNull(
            "1x1 hochkant zeigt nur das Symbol",
            shadowOf(manager).getViewFor(id).findViewById<View>(R.id.widget_name),
        )
    }
}
