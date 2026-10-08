package com.chris.whisperloom.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review SPEC-1: ein Deep-Link (IME-Zahnrad -> Einstellungen) darf nach Rotation/Prozess-Tod
 * nicht erneut angewendet werden — der per rememberSaveable wiederhergestellte Back-Stack
 * (Spec §0.2) bleibt, statt auf [Start, Hub] zurueckzufallen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class MainActivityRecreateTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs(ctx).apply {
            engine = Engine.ONLINE
            apiKey = "sk-test"
            welcomeSeen = true
        }
    }

    @Test fun deepLinkWirdNachRecreateNichtErneutAngewendet() {
        ActivityScenario.launch<MainActivity>(AppNav.settings(ctx)).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("Einstellungen").assertIsDisplayed() // Hub per Deep-Link
            compose.onNodeWithText("Spracherkennung").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("dropdown:Anbieter").assertIsDisplayed() // Erkennungs-Screen

            scenario.recreate() // Rotation / Prozess-Tod: getIntent() liefert den Deep-Link erneut
            compose.waitForIdle()
            compose.onNodeWithTag("dropdown:Anbieter").assertIsDisplayed() // Stack blieb, kein Rueckfall auf den Hub
        }
    }
}
