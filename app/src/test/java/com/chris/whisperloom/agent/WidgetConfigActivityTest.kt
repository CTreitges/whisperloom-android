package com.chris.whisperloom.agent

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Profilwahl beim Platzieren und Neu-Konfigurieren: Standard ist Abbruch, fremde Ids bleiben
 * unberuehrt, eine Wahl bindet, zeichnet und meldet OK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class WidgetConfigActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(ctx)
    private lateinit var store: WidgetProfileStore

    @Before fun leeren() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetProfileStore(ctx)
    }

    /** Wie beim Platzieren: der Launcher hat die Id schon an unseren Provider gebunden, gezeichnet ist noch nichts. */
    private fun gebunden(id: Int, provider: ComponentName = ComponentName(ctx, VoiceTaskWidget::class.java)): Int {
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { this.provider = provider })
        return id
    }

    private fun unseres(): Int = gebunden(41)

    private fun intent(id: Int?): Intent =
        Intent(ctx, WidgetConfigActivity::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }

    /** Nur onCreate — reicht fuer alle Wege, die sich sofort beenden. */
    private fun erzeugen(id: Int?): Activity =
        Robolectric.buildActivity(WidgetConfigActivity::class.java, intent(id)).create().get()

    /** Mit sichtbarem Sheet. */
    private fun oeffnen(id: Int): Activity {
        val activity = Robolectric.buildActivity(WidgetConfigActivity::class.java, intent(id)).setup().get()
        compose.waitForIdle()
        return activity
    }

    private fun ergebnis(activity: Activity): Pair<Int, Int> = shadowOf(activity).let {
        it.resultCode to it.resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun zeile(id: Int): String =
        shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString()

    // --- Abbruch und Schutz ------------------------------------------------------------

    @Test fun ohneIdBrichtSieSofortAb() {
        val activity = erzeugen(null)
        assertEquals(Activity.RESULT_CANCELED to AppWidgetManager.INVALID_APPWIDGET_ID, ergebnis(activity))
        assertTrue(activity.isFinishing)
    }

    @Test fun eineFremdeIdBleibtUnberuehrt() {
        // Die Activity ist exported — jede App koennte sie mit irgendeiner Id starten.
        val fremd = gebunden(77, ComponentName("com.example.fremd", "com.example.fremd.Widget"))
        val activity = erzeugen(fremd)
        assertEquals(Activity.RESULT_CANCELED to fremd, ergebnis(activity))
        assertTrue(activity.isFinishing)
        assertFalse(store.isBound(fremd))
    }

    @Test fun eineUnbekannteIdBleibtUnberuehrt() {
        val activity = erzeugen(4711)
        assertEquals(Activity.RESULT_CANCELED to 4711, ergebnis(activity))
        assertFalse(store.isBound(4711))
    }

    // --- Erstplatzierung ------------------------------------------------------------------

    @Test fun mitNurDemStandardprofilLandetDasWidgetSofort() {
        val id = unseres()
        val activity = erzeugen(id)

        assertEquals(Activity.RESULT_OK to id, ergebnis(activity))
        assertTrue("Unsichtbar: keine Frage ohne Auswahl", activity.isFinishing)
        assertTrue(store.isBound(id))
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(id).id)
        // Selbst gezeichnet — das System schickt nach der Konfiguration kein Update.
        assertEquals(ctx.getString(R.string.widget_off), zeile(id))
    }

    @Test fun mitZweiProfilenBindetDieWahl() {
        val einkauf = store.create("Einkauf")
        val id = unseres()
        val activity = oeffnen(id)

        compose.onNodeWithText("Welches Profil?").assertExists()
        assertEquals("Solange nichts gewaehlt ist: Abbruch", Activity.RESULT_CANCELED to id, ergebnis(activity))
        compose.onNode(hasText("Sprachauftrag") and isSelectable()).assertIsSelected()

        compose.onNode(hasText("Einkauf") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals(Activity.RESULT_OK to id, ergebnis(activity))
        assertTrue(activity.isFinishing)
        assertEquals(einkauf.id, store.forWidget(id).id)
        assertTrue(shadowOf(manager).getViewFor(id).contentDescription.startsWith("Einkauf"))
    }

    @Test fun dieWahlZeichnetDasWidgetMitDemServerSeinesProfils() {
        serverEinrichten(ctx)
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        store.create("Einkauf")
        val id = unseres()
        oeffnen(id)

        compose.onNode(hasText("Einkauf") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals("Nur das Standardprofil hat einen Server", ctx.getString(R.string.widget_no_server), zeile(id))
    }

    @Test fun stillGebundenIstEinWidgetMitServerBereit() {
        serverEinrichten(ctx)
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val id = unseres()

        erzeugen(id)

        assertEquals(ctx.getString(R.string.widget_ready), zeile(id))
    }

    // --- Neu konfigurieren ------------------------------------------------------------------

    @Test fun neuKonfigurierenFragtAuchBeiNurEinemProfil() {
        val id = unseres()
        store.bind(id, WidgetProfile.DEFAULT_ID)
        val activity = oeffnen(id)

        compose.onNodeWithText("Welches Profil?").assertExists()
        compose.onNode(hasText("Sprachauftrag") and isSelectable()).assertIsSelected()
        assertFalse(activity.isFinishing)
    }

    @Test fun einBestandsWidgetZeigtNachDemUpdateBeimNeuKonfigurierenDieAuswahl() {
        // Lag schon vor den Profilen auf dem Startbildschirm: keine Bindung. Ohne Uebernahme
        // hielte die Activity das fuer eine Erstplatzierung, baende still und schloesse sich.
        val id = unseres()
        assertFalse("Vorbedingung: Bestand ist ungebunden", store.isBound(id))

        VoiceTaskWidget().onReceive(ctx, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        val activity = oeffnen(id)

        compose.onNodeWithText("Welches Profil?").assertExists()
        compose.onNodeWithText("Neues Profil").assertExists()
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED to id, ergebnis(activity))
    }

    @Test fun neuKonfigurierenWaehltDasGebundeneProfilVor() {
        val einkauf = store.create("Einkauf")
        val id = unseres()
        store.bind(id, einkauf.id)
        val activity = oeffnen(id)

        compose.onNode(hasText("Einkauf") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Sprachauftrag") and isSelectable()).assertIsNotSelected()

        compose.onNode(hasText("Sprachauftrag") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals(Activity.RESULT_OK to id, ergebnis(activity))
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(id).id)
    }

    @Test fun einNeuesProfilIstNachDemEditorVorgewaehlt() {
        val id = unseres()
        store.bind(id, WidgetProfile.DEFAULT_ID)
        val activity = oeffnen(id)

        click("Neues Profil")
        compose.onNodeWithText("Profil bearbeiten").assertExists()
        val neu = store.all().single { !it.isDefault }
        assertEquals("Sprach-Command 2", neu.name)

        click("Fertig")

        compose.onNodeWithText("Welches Profil?").assertExists()
        compose.onNode(hasText("Sprach-Command 2") and isSelectable()).assertIsSelected()
        assertEquals("Erst der Tipp bestaetigt", WidgetProfile.DEFAULT_ID, store.forWidget(id).id)
        assertEquals(Activity.RESULT_CANCELED to id, ergebnis(activity))

        compose.onNode(hasText("Sprach-Command 2") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals(Activity.RESULT_OK to id, ergebnis(activity))
        assertEquals(neu.id, store.forWidget(id).id)
        assertNotNull(shadowOf(manager).getViewFor(id))
    }
}
