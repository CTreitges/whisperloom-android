package com.chris.whisperloom

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deep-Link-Intents aus Overlay/IME/Notification (UX-Spec §1.2): Ziel MainActivity, route + step + NEW_TASK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun newTask(i: Intent) = i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0

    @Test fun homeZieltAufMainActivityMitRouteHome() {
        val i = AppNav.home(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertEquals("home", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse(i.hasExtra(AppNav.EXTRA_STEP))
        assertTrue(newTask(i))
    }

    @Test fun setupOhneSchrittHatKeinStepExtra() {
        val i = AppNav.setup(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertEquals("setup", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse(i.hasExtra(AppNav.EXTRA_STEP))
        assertTrue(newTask(i))
    }

    @Test fun setupMitSchrittTraegtStep() {
        val i = AppNav.setup(ctx, 3)
        assertEquals("setup", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertEquals(3, i.getIntExtra(AppNav.EXTRA_STEP, -1))
    }

    @Test fun settingsZieltAufMainActivityMitRouteSettings() {
        val i = AppNav.settings(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertEquals("settings", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertTrue(newTask(i))
    }

    @Test fun erweitertZieltAufMainActivityMitRouteAdvanced() {
        val i = AppNav.advanced(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertEquals("advanced", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertTrue(newTask(i))
    }

    @Test fun proWidgetsZieltAufDieWidgetsOhneProfil() {
        val i = AppNav.proWidgets(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertEquals("widgets", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse(i.hasExtra(AppNav.EXTRA_PROFILE))
        assertTrue(newTask(i))
    }

    @Test fun widgetProfileTraegtDasProfil() {
        val i = AppNav.widgetProfile(ctx, "p1")
        assertEquals("widgets", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertEquals("p1", i.getStringExtra(AppNav.EXTRA_PROFILE))
        assertTrue(newTask(i))
    }

    @Test fun extraNamenSindStabil() {
        assertEquals("route", AppNav.EXTRA_ROUTE)
        assertEquals("step", AppNav.EXTRA_STEP)
        assertEquals("profile", AppNav.EXTRA_PROFILE)
        // "agent" steht in Intents von 3.7.0 und bleibt als Alias fuer "Erweitert".
        assertEquals("agent", AppNav.ROUTE_AGENT)
    }
}
