package com.chris.whisperloom.ui.nav

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.ui.tutorial.TutorialKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deep-Link-Erkennung der MainActivity: route/step-Extras und der Einstellungen-Alias (method.xml). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteRequestTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun launcherIntentOhneExtrasIstKeinDeepLink() {
        assertNull(RouteRequest.from(Intent(Intent.ACTION_MAIN)))
        assertNull(RouteRequest.from(null))
    }

    @Test fun appNavIntentsWerdenErkannt() {
        assertEquals(RouteRequest("home"), RouteRequest.from(AppNav.home(ctx)))
        assertEquals(RouteRequest("settings"), RouteRequest.from(AppNav.settings(ctx)))
        assertEquals(RouteRequest("setup", 3), RouteRequest.from(AppNav.setup(ctx, 3)))
        assertEquals(RouteRequest("setup"), RouteRequest.from(AppNav.setup(ctx)))
        assertEquals(RouteRequest("advanced"), RouteRequest.from(AppNav.advanced(ctx)))
        assertEquals(RouteRequest("widgets"), RouteRequest.from(AppNav.proWidgets(ctx)))
        assertEquals(RouteRequest("widgets", profileId = "p1"), RouteRequest.from(AppNav.widgetProfile(ctx, "p1")))
        assertEquals(RouteRequest("models"), RouteRequest.from(AppNav.models(ctx)))
        assertEquals(RouteRequest("refine"), RouteRequest.from(AppNav.refine(ctx)))
        assertEquals(RouteRequest("llm-access"), RouteRequest.from(AppNav.llmAccess(ctx)))
    }

    @Test fun dieNeuenSeitenRoutenTragenKeinProfil() {
        // Die Activity ist exportiert: ein fremdes Profil an KI-Zugang oder Textverbesserung faellt weg.
        assertEquals(RouteRequest("llm-access"), RouteRequest.from(AppNav.llmAccess(ctx).putExtra(AppNav.EXTRA_PROFILE, "p1")))
        assertEquals(RouteRequest("refine"), RouteRequest.from(AppNav.refine(ctx).putExtra(AppNav.EXTRA_PROFILE, "p1")))
    }

    @Test fun einProfilGiltNurFuerDieWidgetRoute() {
        val fremd = AppNav.settings(ctx).putExtra(AppNav.EXTRA_PROFILE, "p1")
        assertEquals(RouteRequest("settings"), RouteRequest.from(fremd))
    }

    @Test fun einProfilDasDenBackStackBraecheWirdIgnoriert() {
        // Die MainActivity ist exportiert; ":" trennt im gespeicherten Back-Stack die Argumente.
        assertEquals(RouteRequest("widgets"), RouteRequest.from(AppNav.widgetProfile(ctx, "a:b")))
        assertEquals(RouteRequest("widgets"), RouteRequest.from(AppNav.widgetProfile(ctx, " ")))
    }

    @Test fun deepLinkNurBeimErststartNichtNachRecreate() {
        // Review SPEC-1: nach Rotation/Prozess-Tod liefert getIntent() denselben Deep-Link — der
        // wiederhergestellte Back-Stack darf nicht durch replaceAll() ueberschrieben werden.
        assertEquals(RouteRequest("settings"), RouteRequest.initial(AppNav.settings(ctx), null))
        assertNull(RouteRequest.initial(AppNav.settings(ctx), Bundle()))
        assertNull(RouteRequest.initial(Intent(Intent.ACTION_MAIN), null))
    }

    @Test fun ungueltigerSchrittWirdIgnoriert() {
        val i = AppNav.setup(ctx).putExtra(AppNav.EXTRA_STEP, 42)
        assertEquals(RouteRequest("setup"), RouteRequest.from(i))
    }

    @Test fun settingsAliasOhneExtrasOeffnetEinstellungen() {
        val i = Intent().setComponent(ComponentName(ctx.packageName, RouteRequest.SETTINGS_ALIAS))
        assertEquals(RouteRequest("settings"), RouteRequest.from(i))
    }

    @Test fun screenEncodingIstStabil() {
        val screens = listOf(
            Screen.Home, Screen.Setup(3), Screen.SettingsHub, Screen.Refine, Screen.Dictionary, Screen.Recognition,
            Screen.LlmAccess, Screen.ButtonKeyboard, Screen.Models, Screen.Advanced, Screen.Widgets(), Screen.Widgets(WidgetTab.NORMAL),
            Screen.Widgets(WidgetTab.PRO), Screen.Widgets(WidgetTab.PRO, "p1"), Screen.Widgets(edit = "default"),
            Screen.Help(4), Screen.Patchnotes, Screen.Tutorial(2),
            Screen.Tutorial(1, startBubbleAfter = true), Screen.Tutorial(0, kind = TutorialKind.PRO_WIDGETS),
        )
        screens.forEach { assertEquals(it, Screen.decode(it.encode())) }
        assertEquals(Screen.Home, Screen.decode("unbekannt"))
        assertEquals("widgets:pro:p1", Screen.Widgets(WidgetTab.PRO, "p1").encode())
        assertEquals("advanced", Screen.Advanced.encode())
        assertEquals("patchnotes", Screen.Patchnotes.encode())
        assertEquals("refine", Screen.Refine.encode())
        assertEquals("dictionary", Screen.Dictionary.encode())
        assertEquals("llm-access", Screen.LlmAccess.encode())
        // Das Pro-Widgets-Heft behaelt den Schluessel aus 3.7.0 (Sprachauftrag).
        assertEquals("tutorial:0:0:agent", Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS).encode())
    }

    @Test fun stufenSeitenHabenJeEinenEigenenSchluessel() {
        val seiten = RefineWay.entries.flatMap { way -> RefineMode.MODEL_STAGES.mapNotNull { Screen.Stage.of(it, way) } }
        assertEquals("4 fuers Diktat, 3 fuer Sprachnachrichten", 7, seiten.size)
        seiten.forEach { assertEquals(it, Screen.decode(it.encode())) }
        assertEquals("stage:polish:dictation", Screen.Stage(RefineMode.POLISH, RefineWay.DICTATION).encode())
        assertEquals("stage:summarize:share", Screen.Stage(RefineMode.SUMMARIZE, RefineWay.SHARE).key)
        // Eigener Schluessel je Seite: AnimatedContent blendet auch von Stufe zu Stufe ueber.
        assertEquals(seiten.size, seiten.map { it.key }.toSet().size)
    }

    @Test fun unbekannteStufenSeiteLandetAufDerTextverbesserung() {
        listOf(
            "stage", "stage:polish", "stage:polish:fax", "stage:quer:dictation", "stage:off:dictation",
            "stage:readable:dictation", "stage:paragraphs:dictation", "stage:prompt:share",
        ).forEach { assertEquals(it, Screen.Refine, Screen.decode(it)) }
        assertEquals(null, Screen.Stage.of(RefineMode.PROMPT, RefineWay.SHARE))
    }

    @Test fun alteBackStacksBleibenLesbar() {
        // Gespeichert von 3.7.0: "agent" hiess der Screen "Erweitert", "widgets" hatte keine Argumente.
        assertEquals(Screen.Advanced, Screen.decode("agent"))
        assertEquals(Screen.Widgets(), Screen.decode("widgets"))
        assertEquals("Unbekannter Tab: der Screen waehlt", Screen.Widgets(), Screen.decode("widgets:quer:"))
    }

    @Test fun alteTextSeitenLandenAufIhrerNeuenSeite() {
        // Gespeichert von 3.8.6: der Text-Hub ("text") und seine Unterseiten ("text-page:<seite>").
        assertEquals(Screen.Refine, Screen.decode("text"))
        assertEquals(Screen.Refine, Screen.decode("text-page:dictation"))
        assertEquals(Screen.Refine, Screen.decode("text-page:share"))
        assertEquals(Screen.LlmAccess, Screen.decode("text-page:access"))
        assertEquals(Screen.Models, Screen.decode("text-page:offline"))
        assertEquals(Screen.Dictionary, Screen.decode("text-page:rules"))
        // Unbekannte oder fehlende Unterseite: die Nachfolgerin des Text-Hubs.
        assertEquals(Screen.Refine, Screen.decode("text-page:modelle"))
        assertEquals(Screen.Refine, Screen.decode("text-page"))
    }

    @Test fun einAlterBackStackBleibtBeimWiederherstellenBedienbar() {
        // Prozess-Tod unter 3.8.6 auf Text › Offline-Erkennung, Neustart mit 3.9.0.
        val alt = listOf("home", "settings", "text", "text-page:offline")
        assertEquals(
            listOf(Screen.Home, Screen.SettingsHub, Screen.Refine, Screen.Models),
            NavState.Saver.restore(alt)!!.snapshot(),
        )
    }
}
