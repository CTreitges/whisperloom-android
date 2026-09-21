package com.chris.whisperloom.ui.nav

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
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
            Screen.Home, Screen.Setup(3), Screen.SettingsHub, Screen.Recognition, Screen.TextSettings,
            Screen.ButtonKeyboard, Screen.Models, Screen.Agent, Screen.Help(4), Screen.Tutorial(2),
            Screen.Tutorial(1, startBubbleAfter = true), Screen.Tutorial(0, kind = TutorialKind.AGENT),
        )
        screens.forEach { assertEquals(it, Screen.decode(it.encode())) }
        assertEquals(Screen.Home, Screen.decode("unbekannt"))
    }
}
