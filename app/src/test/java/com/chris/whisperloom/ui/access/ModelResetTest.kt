package com.chris.whisperloom.ui.access

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.LlmAccessScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
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

/**
 * Modell je Stufe (3.8.6): Wechselt der Anbieter der Textverbesserung, gehen das Modell des Zugangs
 * und alle Stufen-Modelle zurueck auf Standard — an allen vier Stellen. Sonst ginge z. B.
 * claude-sonnet-5 an OpenAI (404). Bis 3.8.5 blieb das Modell beim Ausschalten des eigenen Zugangs
 * und beim Erkennungs-Wechsel unter "wie Erkennung" stehen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class ModelResetTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.apiKey = "gsk"
        prefs.refineMode = RefineMode.POLISH
    }

    private fun modelleGewaehlt(model: String) {
        prefs.llmModel = model
        RefineMode.MODEL_STAGES.forEach { prefs.setLlmModelFor(it, model) }
    }

    private fun assertAlleModelleStandard() {
        val p = Prefs(ctx)
        assertEquals("", p.llmModel)
        RefineMode.MODEL_STAGES.forEach { assertEquals(it.name, "", p.llmModelFor(it)) }
    }

    private fun show(content: @Composable (NavState) -> Unit) {
        val status = SystemStatus()
        val env = AppEnv(PrefsState(prefs), status) { status }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
    }

    @Test fun anbieterwechselSetztAlleModelleZurueck() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        modelleGewaehlt("claude-sonnet-5")
        show { LlmAccessScreen(it) }
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OpenRouter").performClick()
        compose.waitForIdle()
        assertEquals("openrouter", Prefs(ctx).llmProviderId)
        assertAlleModelleStandard()
    }

    @Test fun eigenerZugangAusSetztAlleModelleZurueck() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        modelleGewaehlt("claude-sonnet-5")
        show { LlmAccessScreen(it) }
        compose.onNodeWithText("Eigenen Zugang verwenden").performClick()
        compose.waitForIdle()
        assertEquals("same", Prefs(ctx).llmProviderId)
        assertAlleModelleStandard()
    }

    @Test fun eigenerZugangAnStartetOhneAlteModelle() {
        // "Wie Erkennung" mit Groq, Modelle fuer Groq gewaehlt — der eigene Zugang beginnt bei Standard.
        modelleGewaehlt("openai/gpt-oss-120b")
        show { LlmAccessScreen(it) }
        compose.onNodeWithText("Eigenen Zugang verwenden").performClick()
        compose.waitForIdle()
        assertEquals("groq", Prefs(ctx).llmProviderId)
        assertAlleModelleStandard()
    }

    @Test fun erkennungsWechselBeiWieErkennungSetztDieTextmodelleZurueck() {
        modelleGewaehlt("openai/gpt-oss-120b")
        show { RecognitionScreen(it) }
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OpenAI").performClick()
        compose.waitForIdle()
        assertEquals("openai", Prefs(ctx).sttProviderId)
        assertAlleModelleStandard()
    }

    @Test fun erkennungsWechselLaesstDenEigenenZugangInRuhe() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        modelleGewaehlt("claude-sonnet-5")
        show { RecognitionScreen(it) }
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OpenAI").performClick()
        compose.waitForIdle()
        assertEquals("claude-sonnet-5", Prefs(ctx).llmModel)
        assertEquals("claude-sonnet-5", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
    }
}
