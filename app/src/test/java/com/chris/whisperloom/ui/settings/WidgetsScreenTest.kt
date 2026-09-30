package com.chris.whisperloom.ui.settings

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import com.chris.whisperloom.agent.serverEinrichten
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Untermenue "Widgets" und Profil-Editor: alles wird sofort gespeichert, der Hub fuehrt immer hin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class WidgetsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: NavState
    private lateinit var store: WidgetProfileStore

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetProfileStore(ctx)
    }

    private fun widget(): Int =
        shadowOf(AppWidgetManager.getInstance(ctx)).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    /** Was der Photo Picker zurueckgibt — er antwortet sofort, statt eine echte Auswahl zu oeffnen. */
    private var bild: Uri? = null
    private val bildWahl = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, bild)
            }
        }
    }

    private fun show(screen: Screen = Screen.Widgets()) {
        nav = NavState(listOf(Screen.SettingsHub, screen).distinct())
        val env = AppEnv(PrefsState(Prefs(ctx)), SystemStatus(micGranted = true)) { SystemStatus(micGranted = true) }
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalAppEnv provides env, LocalActivityResultRegistryOwner provides bildWahl) {
                    when (val s = nav.current) {
                        Screen.SettingsHub -> SettingsHubScreen(nav)
                        is Screen.Widgets -> WidgetsScreen(nav, s.tab, s.edit)
                        else -> Unit
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** Profil-Sheet des Profils mit diesem Namen oeffnen. */
    private fun bearbeiten(name: String) = click(name)

    // --- Hub ---------------------------------------------------------------------

    @Test fun hubZeigtWidgetsAuchOhneSprachauftrag() {
        show(Screen.SettingsHub)
        compose.onNodeWithText("Widgets").assertExists()
        compose.onNodeWithText("1 Profil · noch keins auf dem Startbildschirm").assertExists()
        click("Widgets")
        assertEquals(Screen.Widgets(), nav.current)
    }

    @Test fun hubZaehltProfileUndPlatzierteWidgets() {
        store.create("Einkauf")
        widget()
        show(Screen.SettingsHub)
        compose.onNodeWithText("2 Profile · 1 auf dem Startbildschirm").assertExists()
    }

    // --- Hinweis ohne Pro Widgets -------------------------------------------------------

    @Test fun ohneProWidgetsFuehrtEinHinweisZuDenErweitertenOptionen() {
        show()
        compose.onNodeWithText("Der Sprachauftrag ist aus. Die Widgets nehmen erst auf, wenn er unter „Erweiterte Optionen“ eingerichtet ist.")
            .assertIsDisplayed()
        click("Erweiterte Optionen")
        assertEquals(Screen.Advanced, nav.current)
    }

    @Test fun mitProWidgetsKeinHinweis() {
        serverEinrichten(ctx)
        show()
        compose.onNodeWithText("Erweiterte Optionen").assertDoesNotExist()
    }

    // --- Editor per Deep-Link (Widget-Tipp ohne Server) ------------------------------

    @Test fun einProfilAusDemDeepLinkOeffnetSeinenEditor() {
        val p = store.create("Einkauf")
        show(Screen.Widgets(WidgetTab.PRO, p.id))
        compose.onNodeWithText("Profil bearbeiten").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Einkauf")).assertExists()
    }

    @Test fun derEditorAusDemDeepLinkOeffnetSichNurEinmal() {
        val p = store.create("Einkauf")
        show(Screen.Widgets(WidgetTab.PRO, p.id))
        assertEquals("Der Wunsch ist verbraucht, der Tab bleibt", Screen.Widgets(WidgetTab.PRO), nav.current)
        click("Fertig")

        // Weg und zurueck: der Screen entsteht neu — der Editor darf nicht wieder aufgehen.
        compose.runOnIdle { nav.push(Screen.Advanced) }
        compose.waitForIdle()
        compose.runOnIdle { nav.pop() }
        compose.waitForIdle()

        compose.onNodeWithText("Einkauf").assertExists()
        compose.onNodeWithText("Profil bearbeiten").assertDoesNotExist()
    }

    @Test fun einGeloeschtesProfilAusDemDeepLinkOeffnetNichts() {
        show(Screen.Widgets(WidgetTab.PRO, "weg"))
        compose.onNodeWithText("Profil bearbeiten").assertDoesNotExist()
        compose.onNodeWithText("Neues Profil").assertExists()
    }

    // --- Profile -------------------------------------------------------------------

    @Test fun dasStandardprofilStehtMitSeinemModusDa() {
        show()
        compose.onNodeWithText("Sprachauftrag").assertExists()
        compose.onNodeWithText("Tippen startet und stoppt").assertExists()
        compose.onNodeWithText("Auf dem Startbildschirm").assertDoesNotExist()
    }

    @Test fun neuesProfilWirdSofortAngelegtUndGeoeffnet() {
        show()
        click("Neues Profil")
        assertEquals(listOf("", "Sprach-Command 2"), store.all().map { it.name })
        compose.onNodeWithText("Profil bearbeiten").assertExists()
    }

    @Test fun umbenennenWirdSofortGespeichert() {
        val p = store.create("Alt")
        show()
        bearbeiten("Alt")
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("  Einkauf  ")
        compose.waitForIdle()
        assertEquals("Getrimmt gespeichert, ohne Fertig-Knopf", "Einkauf", store.get(p.id)!!.name)
    }

    @Test fun dasNamensfeldZerschneidetKeinEmoji() {
        // 23 Zeichen + Emoji = 25 UTF-16-Einheiten: an Stelle 24 laege sonst eine Emoji-Haelfte,
        // im Feld, im Speicher und auf dem Widget.
        val p = store.create("Alt")
        show()
        bearbeiten("Alt")
        val feld = hasSetTextAction() and hasText("Name")
        compose.onNode(feld).performTextReplacement("a".repeat(23) + String(Character.toChars(0x1F6D2)))
        compose.waitForIdle()

        assertEquals("a".repeat(23), store.get(p.id)!!.name)
        assertEquals("a".repeat(23), compose.onNode(feld).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun symbolWahlWirdSofortGespeichert() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNodeWithContentDescription("Mikrofon").assertIsSelected()
        compose.onNodeWithContentDescription("Einkauf").performClick()
        compose.waitForIdle()
        assertEquals(ProfileIcon.BuiltIn("shopping_cart"), store.get(p.id)!!.icon)
        compose.onNodeWithContentDescription("Einkauf").assertIsSelected()
        compose.onNodeWithContentDescription("Mikrofon").assertIsNotSelected()
        compose.onNodeWithContentDescription("Aus Galerie").assertExists()
        compose.onNodeWithText("Bild entfernen").assertDoesNotExist()
    }

    @Test fun einUnlesbaresBildMeldetSichImSheet() {
        // Die Meldung muss IM Sheet stehen: eine Snackbar laege im Activity-Fenster darunter,
        // verdeckt vom Sheet, das nach dem Fehlschlag offen bleibt.
        val p = store.create("Einkauf")
        bild = Uri.parse("content://com.example.gibtsnicht/bild/1")
        show()
        bearbeiten("Einkauf")
        compose.onNodeWithContentDescription("Aus Galerie").performClick()

        val imSheet = hasText(ctx.getString(R.string.widget_photo_failed)) and
            hasAnyAncestor(hasAnyDescendant(hasText("Profil bearbeiten")))
        compose.waitUntil(5_000) { compose.onAllNodes(imSheet).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(imSheet).assertIsDisplayed()
        assertEquals(ProfileIcon.DEFAULT, store.get(p.id)!!.icon)
    }

    @Test fun autoStoppUndSprechpauseWerdenSofortGespeichert() {
        val p = store.create("Notiz")
        show()
        bearbeiten("Notiz")
        compose.onNodeWithText("Sprechpause").assertDoesNotExist()

        click("Automatisch senden nach Sprechpause")
        assertTrue(store.get(p.id)!!.autoStop)
        assertEquals(SpeechPause.NORMAL, store.get(p.id)!!.pause)
        compose.onNodeWithText("Nach dem letzten Wort 2 Sekunden warten, dann senden.").assertExists()

        click("Lang")
        assertEquals(SpeechPause.LONG, store.get(p.id)!!.pause)
        compose.onNodeWithText("Nach dem letzten Wort 3,5 Sekunden warten, dann senden — für Denkpausen.").assertExists()
    }

    @Test fun derModusStehtInDerProfilzeile() {
        store.save(WidgetProfile(id = "a", name = "Schnell", autoStop = true, pause = SpeechPause.SHORT))
        show()
        compose.onNodeWithText("Stoppt nach Sprechpause · Kurz").assertExists()
    }

    @Test fun dasStandardprofilLaesstSichNichtLoeschen() {
        show()
        bearbeiten("Sprachauftrag")
        compose.onNodeWithText("Profil bearbeiten").assertExists()
        compose.onNodeWithText("Profil löschen").assertDoesNotExist()
    }

    @Test fun loeschenFragtNachUndStelltDieWidgetsAufStandard() {
        val p = store.create("Einkauf")
        val id = widget()
        store.bind(id, p.id)
        show()
        bearbeiten("Einkauf")
        click("Profil löschen")
        compose.onNodeWithText("„Einkauf“ löschen?").assertExists()
        compose.onNodeWithText("1 Widget zeigt danach „Sprachauftrag“.").assertExists()
        assertTrue("Erst die Rueckfrage, dann das Loeschen", store.get(p.id) != null)

        click("Löschen")

        assertNull(store.get(p.id))
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(id).id)
        compose.onNodeWithText("Profil bearbeiten").assertDoesNotExist()
    }

    @Test fun abbrechenLaesstDasProfilStehen() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        click("Profil löschen")
        compose.onNodeWithText("Kein Widget nutzt dieses Profil gerade.").assertExists()
        click("Abbrechen")
        assertTrue(store.get(p.id) != null)
    }

    // --- Widgets auf dem Startbildschirm --------------------------------------------------

    @Test fun einPlatziertesWidgetBekommtPerAuswahlEinAnderesProfil() {
        val p = store.create("Einkauf")
        val id = widget()
        show()
        compose.onNodeWithText("Auf dem Startbildschirm").assertExists()
        click("Widget 1 · Sprachauftrag")
        compose.onNodeWithText("Welches Profil?").assertExists()

        // "Einkauf" steht auch in der Profilkarte dahinter; auswaehlbar ist nur die Zeile im Sheet.
        compose.onNode(hasText("Sprachauftrag") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Einkauf") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals(p.id, store.forWidget(id).id)
        compose.onNodeWithText("Widget 1 · Einkauf").assertExists()
        compose.onNodeWithText("Welches Profil?").assertDoesNotExist()
    }
}
