package com.chris.whisperloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Compose-Smoke auf der JVM. Robolectric 4.16.1: SDK 35 laeuft mit JDK 17, SDK 36 braeuchte JDK 21.
// SDK explizit pinnen, damit ein spaeterer targetSdk-Sprung die Tests nicht still bricht.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhisperLoomThemeTest {

    // v2-API (Compose 1.12): StandardTestDispatcher statt Unconfined; die v1-createComposeRule ist deprecated.
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersTextInsideTheme() {
        compose.setContent {
            WhisperLoomTheme { Text("Hallo WhisperLoom") }
        }
        compose.onNodeWithText("Hallo WhisperLoom").assertIsDisplayed()
    }

    @Test
    fun colorSchemeUsesSpecTokens() {
        var background: Color? = null
        var primary: Color? = null
        var surfaceContainerHigh: Color? = null
        var outlineVariant: Color? = null
        compose.setContent {
            WhisperLoomTheme {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
                surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                outlineVariant = MaterialTheme.colorScheme.outlineVariant
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF0E1116), background)
        assertEquals(Color(0xFF6FD9C7), primary)
        assertEquals(Color(0xFF242A32), surfaceContainerHigh)
        assertEquals(Color(0xFF3A424D), outlineVariant)
    }

    @Test
    fun loomColorsProvideRecordingToken() {
        var recording: Color? = null
        var warningContainer: Color? = null
        compose.setContent {
            WhisperLoomTheme {
                recording = MaterialTheme.loom.recording
                warningContainer = MaterialTheme.loom.warningContainer
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFFF4D4D), recording)
        assertEquals(Color(0xFF4A3600), warningContainer)
    }

    @Test
    fun loomColorsFallBackToPaletteOutsideTheme() {
        var recording: Color? = null
        compose.setContent { recording = MaterialTheme.loom.recording }
        compose.waitForIdle()
        assertEquals(LoomPalette.recording, recording)
    }

    // "Eine Farb-Wahrheit": LoomPalette (Kotlin) darf nicht von colors.xml wegdriften.
    @Test
    fun paletteMatchesColorResources() {
        val expected = mapOf(
            "loom_background" to LoomPalette.background,
            "loom_surface" to LoomPalette.surface,
            "loom_surfaceContainerLowest" to LoomPalette.surfaceContainerLowest,
            "loom_surfaceContainerLow" to LoomPalette.surfaceContainerLow,
            "loom_surfaceContainer" to LoomPalette.surfaceContainer,
            "loom_surfaceContainerHigh" to LoomPalette.surfaceContainerHigh,
            "loom_surfaceContainerHighest" to LoomPalette.surfaceContainerHighest,
            "loom_surfaceBright" to LoomPalette.surfaceBright,
            "loom_onSurface" to LoomPalette.onSurface,
            "loom_onSurfaceVariant" to LoomPalette.onSurfaceVariant,
            "loom_outline" to LoomPalette.outline,
            "loom_outlineVariant" to LoomPalette.outlineVariant,
            "loom_primary" to LoomPalette.primary,
            "loom_onPrimary" to LoomPalette.onPrimary,
            "loom_primaryContainer" to LoomPalette.primaryContainer,
            "loom_onPrimaryContainer" to LoomPalette.onPrimaryContainer,
            "loom_secondary" to LoomPalette.secondary,
            "loom_onSecondary" to LoomPalette.onSecondary,
            "loom_secondaryContainer" to LoomPalette.secondaryContainer,
            "loom_onSecondaryContainer" to LoomPalette.onSecondaryContainer,
            "loom_tertiary" to LoomPalette.tertiary,
            "loom_onTertiary" to LoomPalette.onTertiary,
            "loom_tertiaryContainer" to LoomPalette.tertiaryContainer,
            "loom_onTertiaryContainer" to LoomPalette.onTertiaryContainer,
            "loom_error" to LoomPalette.error,
            "loom_onError" to LoomPalette.onError,
            "loom_errorContainer" to LoomPalette.errorContainer,
            "loom_onErrorContainer" to LoomPalette.onErrorContainer,
            "loom_recording" to LoomPalette.recording,
            "loom_onRecording" to LoomPalette.onRecording,
            "loom_recordingContainer" to LoomPalette.recordingContainer,
            "loom_recordingText" to LoomPalette.recordingText,
            "loom_success" to LoomPalette.success,
            "loom_onSuccess" to LoomPalette.onSuccess,
            "loom_successContainer" to LoomPalette.successContainer,
            "loom_onSuccessContainer" to LoomPalette.onSuccessContainer,
            "loom_warning" to LoomPalette.warning,
            "loom_onWarning" to LoomPalette.onWarning,
            "loom_warningContainer" to LoomPalette.warningContainer,
            "loom_onWarningContainer" to LoomPalette.onWarningContainer,
            "loom_labelShadow" to LoomPalette.labelShadow,
        )
        val actual = mutableMapOf<String, Color>()
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            for (name in expected.keys) {
                // getIdentifier nur im Test: die Namen kommen aus der Spec-Liste oben, nicht aus R.
                val id = context.resources.getIdentifier(name, "color", context.packageName)
                actual[name] = if (id == 0) Color.Unspecified else colorResource(id)
            }
        }
        compose.waitForIdle()
        assertEquals(expected, actual)
    }
}
