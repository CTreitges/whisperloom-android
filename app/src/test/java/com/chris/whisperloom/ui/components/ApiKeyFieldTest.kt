package com.chris.whisperloom.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ApiKeyField: das Auge nennt das Feld beim Namen — "API-Key" oder das uebergebene Label. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class ApiKeyFieldTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(label: String? = null, optional: Boolean = false) {
        compose.setContent {
            WhisperLoomTheme {
                var value by remember { mutableStateOf("geheim") }
                ApiKeyField(value = value, onValueChange = { value = it }, optional = optional, label = label)
            }
        }
        compose.waitForIdle()
    }

    private fun auge(text: String) = compose.onNodeWithContentDescription(text)

    @Test fun ohneLabelHeisstDasAugeApiKey() {
        show()
        auge("API-Key anzeigen").performClick()
        compose.waitForIdle()
        auge("API-Key verbergen").assertExists()
        auge("API-Key anzeigen").assertDoesNotExist()
    }

    @Test fun mitLabelHeisstDasAugeWieDasFeld() {
        show(label = "Token")
        auge("Token anzeigen").assertExists()
        auge("API-Key anzeigen").assertDoesNotExist()
        auge("Token anzeigen").performClick()
        compose.waitForIdle()
        auge("Token verbergen").assertExists()
        auge("API-Key verbergen").assertDoesNotExist()
    }

    @Test fun optionalOhneLabelNenntNurDenApiKey() {
        // Das Feld heisst "API-Key (optional)" — das Auge bleibt beim Namen ohne Zusatz.
        show(optional = true)
        auge("API-Key anzeigen").assertExists()
    }
}
