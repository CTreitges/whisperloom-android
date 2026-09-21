package com.chris.whisperloom.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prominent Disclosure (Google-Play-Pflicht): der Consent-Dialog erscheint VOR dem Zugriff und
 * ruft onAccept ausschliesslich nach aktiver Zustimmung ("Fortfahren"), nicht bei Abbruch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class ProminentDisclosureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun label(res: Int) = ctx.getString(res)

    @Test fun accept_only_after_confirm() {
        var accepted = 0
        compose.setContent {
            WhisperLoomTheme {
                val gate = rememberDisclosureGate(DisclosureKind.MICROPHONE, onAccept = { accepted++ })
                Button(onClick = gate.request) { Text("open") }
            }
        }
        // Vor dem Antippen: kein Dialog, kein onAccept
        compose.onNodeWithText(label(R.string.disclosure_mic_title)).assertDoesNotExist()
        assertEquals(0, accepted)

        // Antippen -> Disclosure erscheint, onAccept noch NICHT gerufen
        compose.onNodeWithText("open").performClick()
        compose.onNodeWithText(label(R.string.disclosure_mic_title)).assertIsDisplayed()
        assertEquals(0, accepted)

        // Fortfahren -> onAccept genau einmal, Dialog verschwindet
        compose.onNodeWithText(label(R.string.disclosure_continue)).performClick()
        assertEquals(1, accepted)
        compose.onNodeWithText(label(R.string.disclosure_mic_title)).assertDoesNotExist()
    }

    @Test fun cancel_does_not_accept() {
        var accepted = 0
        compose.setContent {
            WhisperLoomTheme {
                val gate = rememberDisclosureGate(DisclosureKind.ACCESSIBILITY, onAccept = { accepted++ })
                Button(onClick = gate.request) { Text("open") }
            }
        }
        compose.onNodeWithText("open").performClick()
        compose.onNodeWithText(label(R.string.disclosure_a11y_title)).assertIsDisplayed()

        // Abbrechen -> kein onAccept, Dialog verschwindet
        compose.onNodeWithText(label(R.string.common_cancel)).performClick()
        assertEquals(0, accepted)
        compose.onNodeWithText(label(R.string.disclosure_a11y_title)).assertDoesNotExist()
    }
}
