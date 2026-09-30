package com.chris.whisperloom.ui.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.TEST_SERVER_TOKEN
import com.chris.whisperloom.agent.TEST_SERVER_URL
import com.chris.whisperloom.agent.VoiceTaskState
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.agent.WidgetKind
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import com.chris.whisperloom.agent.serverEinrichten
import com.chris.whisperloom.ui.WhisperLoomApp
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.ui.tutorial.TutorialKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Widget-Menue (Tabs "Widgets" / "Pro Widgets") und Editor: alles wird sofort gespeichert, der
 * Hub fuehrt immer hin. Ohne ausdrueckliche Angabe sind Pro Widgets an.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class WidgetsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: NavState
    private lateinit var env: AppEnv
    private lateinit var store: WidgetProfileStore
    private var gelesenerStatus = SystemStatus(micGranted = true)

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        VoiceTaskStore(ctx).clear()
        store = WidgetProfileStore(ctx)
        Prefs(ctx).proWidgetsEnabled = true
    }

    private fun widget(): Int =
        shadowOf(AppWidgetManager.getInstance(ctx)).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    private fun zeile(id: Int): String =
        shadowOf(AppWidgetManager.getInstance(ctx)).getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString()

    private fun namensSichtbarkeit(id: Int): Int =
        shadowOf(AppWidgetManager.getInstance(ctx)).getViewFor(id).findViewById<View>(R.id.widget_name).visibility

    /** Was der Photo Picker zurueckgibt — er antwortet sofort, statt eine echte Auswahl zu oeffnen. */
    private var bild: Uri? = null

    /** Angefragte Berechtigungen: der System-Dialog erscheint nicht, die Anfrage bleibt offen. */
    private val angefragt = mutableListOf<Any?>()
    private val bildWahl = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                if (contract is ActivityResultContracts.RequestPermission) angefragt += input else dispatchResult(requestCode, bild)
            }
        }
    }

    private fun show(screen: Screen = Screen.Widgets(), micGranted: Boolean = true) {
        nav = NavState(listOf(Screen.SettingsHub, screen).distinct())
        gelesenerStatus = SystemStatus(micGranted = micGranted)
        env = AppEnv(PrefsState(Prefs(ctx)), gelesenerStatus) { gelesenerStatus }
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

    /** Ein Tab der Leiste — "Widgets" steht auch als Titel da. */
    private fun tab(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tabWaehlen(name: String) {
        compose.onNode(tab(name)).performClick()
        compose.waitForIdle()
    }

    /** Editor des Widgets mit diesem Namen oeffnen. */
    private fun bearbeiten(name: String) = click(name)

    private val namensFeld = hasSetTextAction() and hasText("Name")
    private val adressFeld = hasSetTextAction() and hasText("Server-Adresse")
    private val tokenFeld = hasSetTextAction() and hasText("Token")

    // --- Hub ---------------------------------------------------------------------

    @Test fun hubZeigtWidgetsAuchOhneProWidgets() {
        Prefs(ctx).proWidgetsEnabled = false
        show(Screen.SettingsHub)
        compose.onNodeWithText("Widgets").assertExists()
        compose.onNodeWithText("Normale Widgets folgen bald").assertExists()
        click("Widgets")
        assertEquals(Screen.Widgets(), nav.current)
    }

    @Test fun hubZaehltProWidgetsUndPlatzierteWidgets() {
        store.create("Einkauf")
        widget()
        show(Screen.SettingsHub)
        compose.onNodeWithText("2 Pro Widgets · 1 auf dem Startbildschirm").assertExists()
    }

    // --- Ohne Pro Widgets: keine Tabs ------------------------------------------------

    @Test fun ohneProWidgetsGibtEsKeineTabsNurDenPlatzhalter() {
        Prefs(ctx).proWidgetsEnabled = false
        show(Screen.Widgets(WidgetTab.PRO))
        compose.onAllNodes(tab("Pro Widgets")).assertCountEquals(0)
        compose.onAllNodes(tab("Widgets")).assertCountEquals(0)
        compose.onNodeWithText("Normale Widgets für den Startbildschirm kommen mit einem späteren Update.").assertIsDisplayed()
        compose.onNodeWithContentDescription(ctx.getString(R.string.img_widgets_normal)).assertExists()
        compose.onNodeWithText("Neues Pro Widget").assertDoesNotExist()
    }

    @Test fun ohneProWidgetsFuehrtEinHinweisNachErweitert() {
        Prefs(ctx).proWidgetsEnabled = false
        show()
        compose.onNodeWithText("Für Entwickler: Pro Widgets lassen sich unter „Erweitert“ freischalten.").assertIsDisplayed()
        click("Erweitert")
        assertEquals(Screen.Advanced, nav.current)
    }

    // --- Mit Pro Widgets: Tabs -----------------------------------------------------------

    @Test fun mitProWidgetsStartetDerTabProWidgets() {
        show()
        compose.onNode(tab("Pro Widgets")).assertIsSelected()
        compose.onNode(tab("Widgets")).assertIsNotSelected()
        compose.onNodeWithText(
            "Sprach-Command-Widgets nehmen auf und schicken den Text an deinen eigenen Server. Jedes Widget hat seinen eigenen Namen und Server.",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(ctx.getString(R.string.img_pro_widgets)).assertExists()
        compose.onNodeWithText("Neues Pro Widget").assertExists()
        compose.onNodeWithText("Deine Pro Widgets").assertExists()
        compose.onNodeWithText("Für Entwickler: Pro Widgets lassen sich unter „Erweitert“ freischalten.").assertDoesNotExist()
    }

    @Test fun derTabWidgetsZeigtDenPlatzhalterOhneFreischaltHinweis() {
        show()
        tabWaehlen("Widgets")
        compose.onNode(tab("Widgets")).assertIsSelected()
        compose.onNodeWithText("Normale Widgets für den Startbildschirm kommen mit einem späteren Update.").assertIsDisplayed()
        compose.onNodeWithText("Für Entwickler: Pro Widgets lassen sich unter „Erweitert“ freischalten.").assertDoesNotExist()
        compose.onNodeWithText("Neues Pro Widget").assertDoesNotExist()
        assertEquals(Screen.Widgets(WidgetTab.NORMAL), nav.current)

        tabWaehlen("Pro Widgets")
        compose.onNodeWithText("Neues Pro Widget").assertExists()
        assertEquals(Screen.Widgets(WidgetTab.PRO), nav.current)
    }

    @Test fun derTabAusDemArgumentIstVorgewaehlt() {
        show(Screen.Widgets(WidgetTab.NORMAL))
        compose.onNode(tab("Widgets")).assertIsSelected()
        compose.onNodeWithText("Neues Pro Widget").assertDoesNotExist()
    }

    @Test fun derGewaehlteTabBleibtNachWegUndZurueck() {
        // Er steht im Back-Stack: der Screen entsteht nach dem Zurueck neu, ohne gemerkten Zustand.
        show()
        tabWaehlen("Widgets")
        compose.runOnIdle { nav.push(Screen.Advanced) }
        compose.waitForIdle()
        compose.runOnIdle { nav.pop() }
        compose.waitForIdle()
        compose.onNode(tab("Widgets")).assertIsSelected()
    }

    // --- Neues Pro Widget und die eigenen ------------------------------------------------

    @Test fun neuesProWidgetLegtEinSprachCommandWidgetAnUndOeffnetDenEditor() {
        show()
        compose.onNodeWithText("Sprechen und an deinen Server schicken").assertExists()
        click("Sprach-Command-Widget")
        assertEquals(listOf("", "Sprach-Command 2"), store.all().map { it.name })
        assertTrue(store.all().all { it.kind == WidgetKind.VOICE_COMMAND })
        compose.onNodeWithText("Widget bearbeiten").assertExists()
        compose.onNode(namensFeld and hasText("Sprach-Command 2")).assertExists()
    }

    @Test fun einWidgetMitServerZeigtDenHostEinsOhneWarnt() {
        serverEinrichten(ctx)
        store.create("Einkauf")
        show()
        compose.onNodeWithText("Sprach-Command").assertExists()
        compose.onNodeWithText("bridge.example.de · Tippen startet und stoppt").assertExists()
        // Die Zeile ist fuer TalkBack ein Element: Name und Warnung stehen im selben Knoten.
        compose.onAllNodes(hasText("Server fehlt")).assertCountEquals(1)
        compose.onNode(hasText("Einkauf") and hasText("Server fehlt")).assertExists()
    }

    @Test fun derModusStehtInDerZeile() {
        store.save(
            WidgetProfile(
                id = "a", name = "Schnell", autoStop = true, pause = SpeechPause.SHORT,
                serverUrl = TEST_SERVER_URL, serverToken = TEST_SERVER_TOKEN,
            ),
        )
        show()
        compose.onNodeWithText("bridge.example.de · Stoppt nach Sprechpause · Kurz").assertExists()
    }

    @Test fun dasStandardprofilStehtOhneServerMitWarnungDa() {
        show()
        compose.onNodeWithText("Sprach-Command").assertExists()
        compose.onNodeWithText("Server fehlt").assertExists()
        compose.onNodeWithText("Auf dem Startbildschirm").assertDoesNotExist()
    }

    // --- Mikrofon und offener Auftrag (frueher in "Erweitert") -------------------------------

    @Test fun ohneMikrofonErscheintDerHinweisNurMitProWidgets() {
        show(micGranted = false)
        compose.onNodeWithText("Für Pro Widgets fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()
    }

    @Test fun ohneProWidgetsKeinMikrofonHinweis() {
        Prefs(ctx).proWidgetsEnabled = false
        show(micGranted = false)
        compose.onNodeWithText("Für Pro Widgets fehlt die Mikrofon-Berechtigung.").assertDoesNotExist()
    }

    @Test fun derHinweisVerschwindetSobaldDasMikrofonErlaubtIst() {
        // Der Screen muss den Status lesen, nicht selbst checkSelfPermission rufen: sonst
        // bliebe die Warnung stehen, nachdem der Nutzer die Berechtigung gerade erteilt hat.
        show(micGranted = false)
        compose.onNodeWithText("Für Pro Widgets fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()

        gelesenerStatus = SystemStatus(micGranted = true)
        env.refreshStatus()
        compose.waitForIdle()

        compose.onNodeWithText("Für Pro Widgets fehlt die Mikrofon-Berechtigung.").assertDoesNotExist()
    }

    @Test fun derMikrofonHinweisFragtErstNachDemEigenenHinweis() {
        // Play-Pflicht (Prominent Disclosure): vor dem System-Dialog steht der eigene Hinweis, und
        // nur "Fortfahren" fragt die Berechtigung an.
        val titel = ctx.getString(R.string.disclosure_mic_title)
        show(micGranted = false)
        click("Für Pro Widgets fehlt die Mikrofon-Berechtigung.")
        compose.onNodeWithText(titel).assertIsDisplayed()
        assertTrue("Noch keine Anfrage", angefragt.isEmpty())

        click("Abbrechen")
        compose.onNodeWithText(titel).assertDoesNotExist()
        assertTrue("Abbrechen fragt nichts an", angefragt.isEmpty())

        click("Für Pro Widgets fehlt die Mikrofon-Berechtigung.")
        click(ctx.getString(R.string.disclosure_continue))
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), angefragt)
    }

    @Test fun ohneOffenenAuftragGibtEsNichtsZuVerwerfen() {
        show()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
    }

    @Test fun einHaengenderAuftragLaesstSichVerwerfen() {
        val task = VoiceTaskStore(ctx)
        task.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", WidgetProfile.DEFAULT_ID)
        task.state = VoiceTaskState.WORKING
        var abgebrochen = 0
        val echtesCancel = VoiceTaskWork.cancelImpl
        VoiceTaskWork.cancelImpl = { abgebrochen++ }
        try {
            show()
            click("Offenen Auftrag verwerfen")
        } finally {
            VoiceTaskWork.cancelImpl = echtesCancel
        }
        assertEquals(1, abgebrochen)
        assertFalse("Der Auftrag muss wirklich weg sein", VoiceTaskStore(ctx).hasWork)
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
    }

    @Test fun loeschenImEditorNimmtDieKarteOffenerAuftragMit() {
        // Editor und Karte liegen im selben Tab: nach dem Loeschen darf keine Karte stehen bleiben,
        // deren Tipp "Auftrag verworfen" meldet, obwohl nichts mehr da ist.
        val p = store.create("Einkauf")
        VoiceTaskStore(ctx).begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", p.id)
        val echtesCancel = VoiceTaskWork.cancelImpl
        VoiceTaskWork.cancelImpl = { }
        try {
            show()
            compose.onNodeWithText("Offenen Auftrag verwerfen").assertExists()
            bearbeiten("Einkauf")
            click("Widget löschen")
            click("Löschen")
        } finally {
            VoiceTaskWork.cancelImpl = echtesCancel
        }
        assertFalse(VoiceTaskStore(ctx).hasWork)
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
        compose.onNodeWithText("Offener Auftrag").assertDoesNotExist()
    }

    // --- Hilfe -------------------------------------------------------------------------

    @Test fun dieHilfeFuehrtInsTutorialProWidgets() {
        show()
        compose.onNodeWithText("Widget platzieren: Startbildschirm lange drücken → Widgets → WhisperLoom → „Sprach-Command“.")
            .assertExists()
        click("Anleitung ansehen")
        assertEquals(Screen.Tutorial(kind = TutorialKind.AGENT), nav.current)
    }

    // --- Editor per Deep-Link (Widget-Tipp ohne Server) ------------------------------

    @Test fun einProfilAusDemDeepLinkOeffnetSeinenEditor() {
        val p = store.create("Einkauf")
        show(Screen.Widgets(WidgetTab.PRO, p.id))
        compose.onNodeWithText("Widget bearbeiten").assertExists()
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
        compose.onNodeWithText("Widget bearbeiten").assertDoesNotExist()
    }

    @Test fun einErneuterWidgetTippOeffnetDenEditorAuchBeiOffenemMenue() {
        // MainActivity ist singleTask: der zweite Tipp kommt per onNewIntent, waehrend das Menue
        // noch oben liegt — gleicher Screen-Key, also dieselbe Composition. Pro Widgets aus, damit
        // der Hinweis einen Weg nach "Erweitert" und zurueck bietet.
        Prefs(ctx).proWidgetsEnabled = false
        val p = store.create("Einkauf")
        val env = AppEnv(PrefsState(Prefs(ctx)), SystemStatus(micGranted = true)) { SystemStatus(micGranted = true) }
        var route by mutableStateOf<RouteRequest?>(RouteRequest(AppNav.ROUTE_WIDGETS, profileId = p.id))
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides bildWahl) {
                    WhisperLoomApp(env, route) { route = null }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Widget bearbeiten").assertExists()
        click("Fertig")
        compose.onNodeWithText("Widget bearbeiten").assertDoesNotExist()

        compose.runOnIdle { route = RouteRequest(AppNav.ROUTE_WIDGETS, profileId = p.id) }
        compose.waitForIdle()
        compose.onNodeWithText("Widget bearbeiten").assertExists()
        click("Fertig")

        // Auch dieser Wunsch ist verbraucht: Weg und Zurueck oeffnet den Editor nicht noch einmal.
        click("Erweitert")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Für Entwickler: Pro Widgets lassen sich unter „Erweitert“ freischalten.").assertExists()
        compose.onNodeWithText("Widget bearbeiten").assertDoesNotExist()
    }

    @Test fun einDeepLinkWechseltVomOffenenEditorZumAnderenProfil() {
        val a = store.create("Einkauf")
        val b = store.create("Notiz")
        show(Screen.Widgets(WidgetTab.PRO, a.id))
        click("Widget löschen")
        compose.onNodeWithText("„Einkauf“ löschen?").assertExists()

        compose.runOnIdle { nav.replaceAll(Screen.SettingsHub, Screen.Widgets(WidgetTab.PRO, b.id)) }
        compose.waitForIdle()

        compose.onNode(hasSetTextAction() and hasText("Notiz")).assertExists()
        // Frischer Editor: die Rueckfrage von vorhin wuerde jetzt "Notiz" loeschen.
        compose.onNodeWithText("„Notiz“ löschen?").assertDoesNotExist()
        assertEquals("Der Wunsch ist verbraucht", Screen.Widgets(WidgetTab.PRO), nav.current)
        assertTrue(store.get(a.id) != null && store.get(b.id) != null)
    }

    @Test fun einDeepLinkAufDenTabWidgetsWechseltZumEditorUndTabPro() {
        // Der Editor gehoert zu einem Pro Widget: der Tab wechselt mit, auch wenn gerade "Widgets" offen ist.
        val p = store.create("Einkauf")
        show(Screen.Widgets(WidgetTab.NORMAL))
        compose.runOnIdle { nav.replaceTop(Screen.Widgets(WidgetTab.PRO, p.id)) }
        compose.waitForIdle()
        compose.onNodeWithText("Widget bearbeiten").assertExists()
        compose.onNode(tab("Pro Widgets")).assertIsSelected()
        assertEquals(Screen.Widgets(WidgetTab.PRO), nav.current)
    }

    @Test fun einGeloeschtesProfilAusDemDeepLinkOeffnetNichts() {
        show(Screen.Widgets(WidgetTab.PRO, "weg"))
        compose.onNodeWithText("Widget bearbeiten").assertDoesNotExist()
        compose.onNodeWithText("Neues Pro Widget").assertExists()
    }

    // --- Editor: Name ------------------------------------------------------------------

    @Test fun umbenennenWirdSofortGespeichert() {
        val p = store.create("Alt")
        show()
        bearbeiten("Alt")
        compose.onNode(namensFeld).performTextReplacement("  Einkauf  ")
        compose.waitForIdle()
        assertEquals("Getrimmt gespeichert, ohne Fertig-Knopf", "Einkauf", store.get(p.id)!!.name)
    }

    @Test fun dasNamensfeldZerschneidetKeinEmoji() {
        // 23 Zeichen + Emoji = 25 UTF-16-Einheiten: an Stelle 24 laege sonst eine Emoji-Haelfte,
        // im Feld, im Speicher und auf dem Widget.
        val p = store.create("Alt")
        show()
        bearbeiten("Alt")
        compose.onNode(namensFeld).performTextReplacement("a".repeat(23) + String(Character.toChars(0x1F6D2)))
        compose.waitForIdle()

        assertEquals("a".repeat(23), store.get(p.id)!!.name)
        assertEquals("a".repeat(23), compose.onNode(namensFeld).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun jedesWidgetBrauchtEinenNamen() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNodeWithText("Steht unter dem Widget auf dem Startbildschirm.").assertExists()

        compose.onNode(namensFeld).performTextReplacement("")
        compose.waitForIdle()
        compose.onNodeWithText("Jedes Widget braucht einen Namen.").assertIsDisplayed()
        compose.onNode(namensFeld).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        assertEquals("Ein leerer Name wird nie gespeichert", "Einkauf", store.get(p.id)!!.name)

        compose.onNode(namensFeld).performTextReplacement("   ")
        compose.waitForIdle()
        compose.onNodeWithText("Jedes Widget braucht einen Namen.").assertIsDisplayed()
        assertEquals("Nur Leerzeichen zaehlen nicht", "Einkauf", store.get(p.id)!!.name)

        // Schliessen mit leerem Feld: der zuletzt gespeicherte Name bleibt und steht wieder im Feld.
        click("Fertig")
        bearbeiten("Einkauf")
        compose.onNode(namensFeld and hasText("Einkauf")).assertExists()
        compose.onNodeWithText("Jedes Widget braucht einen Namen.").assertDoesNotExist()
    }

    @Test fun dasStandardprofilZeigtSeinenNamenImFeld() {
        // Frueher leer mit "Leer = Sprachauftrag" — jetzt steht der Name da, den das Widget zeigt.
        show()
        bearbeiten("Sprach-Command")
        compose.onNode(namensFeld and hasText("Sprach-Command")).assertExists()
    }

    @Test fun derNameUnterDemWidgetLaesstSichAbschalten() {
        val p = store.create("Einkauf")
        val id = widget()
        store.bind(id, p.id)
        show()
        VoiceTaskWidget.refresh(ctx)
        assertEquals(View.VISIBLE, namensSichtbarkeit(id))

        bearbeiten("Einkauf")
        compose.onNodeWithText("Name unter dem Widget anzeigen").assertIsOn()
        click("Name unter dem Widget anzeigen")
        assertFalse(store.get(p.id)!!.showName)
        compose.onNodeWithText("Name unter dem Widget anzeigen").assertIsOff()
        assertEquals("Sofort auf dem Startbildschirm, ohne Fertig", View.GONE, namensSichtbarkeit(id))

        click("Name unter dem Widget anzeigen")
        assertEquals(View.VISIBLE, namensSichtbarkeit(id))
    }

    // --- Editor: Server ------------------------------------------------------------------

    @Test fun dieAdresseWirdSofortGespeichert() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNodeWithText("Server").assertExists()
        compose.onNode(adressFeld).performTextInput("https://bridge.example.de")
        compose.waitForIdle()
        assertEquals("https://bridge.example.de", store.get(p.id)!!.serverUrl)
    }

    @Test fun dasTokenWirdSofortGespeichert() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNode(tokenFeld).performTextInput("geheim")
        compose.waitForIdle()
        assertEquals("geheim", store.get(p.id)!!.serverToken)
        assertEquals("Nur dieses Widget", "", store.get(WidgetProfile.DEFAULT_ID)!!.serverToken)
    }

    @Test fun einTippfehlerInDerAdresseWirdBenannt() {
        store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNode(adressFeld).performTextInput("bridge.example.de")
        compose.waitForIdle()
        compose.onNodeWithText("URL muss mit http:// oder https:// beginnen").assertIsDisplayed()
    }

    @Test fun pruefenBleibtGesperrtSolangeEtwasFehlt() {
        store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNodeWithText("Verbindung prüfen").assertIsNotEnabled()
        compose.onNode(adressFeld).performTextInput("https://bridge.example.de")
        compose.waitForIdle()
        compose.onNodeWithText("Verbindung prüfen").assertIsNotEnabled()
    }

    @Test fun pruefenWirdMitAdresseUndTokenMoeglich() {
        store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        compose.onNode(adressFeld).performTextInput("https://bridge.example.de")
        compose.onNode(tokenFeld).performTextInput("geheim")
        compose.waitForIdle()
        compose.onNodeWithText("Verbindung prüfen").assertIsEnabled()
    }

    @Test fun derDatenschutzHinweisStehtDa() {
        show()
        bearbeiten("Sprach-Command")
        compose.onNodeWithText(
            "Transkript und Auftrag gehen nur an den Server dieses Widgets. Aufgenommen wird wie beim Diktat über die eingestellte Erkennung.",
        ).assertExists()
    }

    @Test fun einNeuerServerMachtDasWidgetBereitSobaldDasTippenRuht() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val id = widget()
        show()
        VoiceTaskWidget.refresh(ctx)
        assertEquals(ctx.getString(R.string.widget_no_server), zeile(id))

        bearbeiten("Sprach-Command")
        compose.onNode(adressFeld).performTextInput("https://bridge.example.de")
        compose.onNode(tokenFeld).performTextInput("geheim")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("Ohne Fertig — nach der Tipp-Pause", ctx.getString(R.string.widget_ready), zeile(id))
    }

    // --- Editor: Symbol, Auto-Stopp, Loeschen --------------------------------------------

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
            hasAnyAncestor(hasAnyDescendant(hasText("Widget bearbeiten")))
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

    @Test fun dasStandardprofilLaesstSichNichtLoeschen() {
        show()
        bearbeiten("Sprach-Command")
        compose.onNodeWithText("Widget bearbeiten").assertExists()
        compose.onNodeWithText("Widget löschen").assertDoesNotExist()
    }

    @Test fun loeschenFragtNachUndStelltDieWidgetsAufStandard() {
        val p = store.create("Einkauf")
        val id = widget()
        store.bind(id, p.id)
        show()
        bearbeiten("Einkauf")
        click("Widget löschen")
        compose.onNodeWithText("„Einkauf“ löschen?").assertExists()
        compose.onNodeWithText("1 Widget auf dem Startbildschirm zeigt danach „Sprach-Command“.").assertExists()
        assertTrue("Erst die Rueckfrage, dann das Loeschen", store.get(p.id) != null)

        click("Löschen")

        assertNull(store.get(p.id))
        assertEquals(WidgetProfile.DEFAULT_ID, store.forWidget(id).id)
        compose.onNodeWithText("Widget bearbeiten").assertDoesNotExist()
    }

    @Test fun abbrechenLaesstDasProfilStehen() {
        val p = store.create("Einkauf")
        show()
        bearbeiten("Einkauf")
        click("Widget löschen")
        compose.onNodeWithText("Es liegt gerade nicht auf dem Startbildschirm.").assertExists()
        click("Abbrechen")
        assertTrue(store.get(p.id) != null)
    }

    // --- Widgets auf dem Startbildschirm --------------------------------------------------

    @Test fun einPlatziertesWidgetBekommtPerAuswahlEinAnderesProfil() {
        val p = store.create("Einkauf")
        val id = widget()
        show()
        compose.onNodeWithText("Auf dem Startbildschirm").assertExists()
        click("Widget 1 · Sprach-Command")
        compose.onNodeWithText("Welches Widget-Profil?").assertExists()

        // "Einkauf" steht auch in der Liste dahinter; auswaehlbar ist nur die Zeile im Sheet.
        compose.onNode(hasText("Sprach-Command") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Einkauf") and isSelectable()).performClick()
        compose.waitForIdle()

        assertEquals(p.id, store.forWidget(id).id)
        compose.onNodeWithText("Widget 1 · Einkauf").assertExists()
        compose.onNodeWithText("Welches Widget-Profil?").assertDoesNotExist()
    }
}
