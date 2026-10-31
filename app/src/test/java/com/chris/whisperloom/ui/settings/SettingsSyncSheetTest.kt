package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.sync.SettingsSync
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Einstellungen uebertragen: Hub-Zeile, Sheet und die Vorschau des Imports mit dem Hinweis zu secrets/policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class SettingsSyncSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    private fun fixture(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun hubZeileOeffnetDasSheet() {
        val status = SystemStatus()
        val env = AppEnv(PrefsState(prefs), status) { status }
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { SettingsHubScreen(NavState(listOf(Screen.Home, Screen.SettingsHub))) } }
        }
        compose.onNodeWithText("Export und Import, auch mit WhisperLoom Desktop").assertExists()
        compose.onNodeWithText("Einstellungen übertragen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Exportieren").assertExists()
        compose.onNodeWithText("Importieren").assertExists()
        compose.onNodeWithText("API-Keys kommen nie in die Datei", substring = true).assertExists()
    }

    @Test fun vorschauNenntSecretsUndPolicyUndUebernimmtErstNachOk() {
        val preview = SettingsSync.preview(prefs, fixture("settings-v1-provisioning.json"))
        var applied = 0
        compose.setContent { WhisperLoomTheme { ImportDialog(preview, onApply = { applied++ }, onDismiss = {}) } }

        compose.onNodeWithText("Einstellungen übernehmen?").assertExists()
        compose.onNodeWithText("(secrets, policy) gelten nur für WhisperLoom Desktop", substring = true).assertExists()
        compose.onNodeWithText("Die API-Keys dieses Geräts bleiben, wie sie sind.").assertExists()
        compose.onNodeWithText("• stt.providerId").assertExists()
        compose.onNodeWithText("test-key-not-real", substring = true).assertDoesNotExist()
        assertEquals(0, applied)
        compose.onNodeWithText("Übernehmen").performClick()
        assertEquals(1, applied)
    }

    @Test fun langeAenderungslistenWerdenGekuerzt() {
        val preview = SettingsSync.preview(prefs, fixture("settings-v1.json"))
        val more = preview.changes.size - 12
        compose.setContent { WhisperLoomTheme { ImportDialog(preview, onApply = {}, onDismiss = {}) } }

        compose.onNodeWithText("${preview.changes.size} Einstellungen ändern sich:").assertExists()
        compose.onNodeWithText("… und $more weitere").assertExists()
        compose.onNodeWithText("• ${preview.changes[11]}").assertExists()
        compose.onNodeWithText("• ${preview.changes[12]}").assertDoesNotExist()
    }

    @Test fun ungueltigeDateiZeigtDenFehlerOhneUebernehmen() {
        val preview = SettingsSync.preview(prefs, """{"history": {"size": 33}}""")
        compose.setContent { WhisperLoomTheme { ImportDialog(preview, onApply = {}, onDismiss = {}) } }

        compose.onNodeWithText("Datei ungültig – nichts übernommen").assertExists()
        compose.onNodeWithText("history.size: eine von 10, 25, 50, 100, 250, 500").assertExists()
        compose.onNodeWithText("Übernehmen").assertDoesNotExist()
        compose.onNodeWithText("Schließen").assertExists()
    }
}
