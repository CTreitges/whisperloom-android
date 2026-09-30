package com.chris.whisperloom.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Findet eine Anleitungs-Illustration an Drawable UND Bildtext. Der Bildtext allein reicht nicht:
 * [GuideHeader] und die Tutorial-Seiten bekommen Bild und Text getrennt, ein vertauschtes
 * Drawable bliebe sonst unbemerkt.
 */
fun hasIllustration(@DrawableRes image: Int, text: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(IllustrationRes, image) and hasContentDescription(text)

/** GuideHeader: Bild und Bildtext am selben Knoten, Kurztext optional darunter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class GuideHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun bildUndBildtextSitzenAmSelbenKnoten() {
        compose.setContent {
            WhisperLoomTheme { GuideHeader(R.drawable.ill_pro_features, R.string.img_pro_features, text = "Kurztext") }
        }
        compose.onNode(hasIllustration(R.drawable.ill_pro_features, ctx.getString(R.string.img_pro_features))).assertIsDisplayed()
        compose.onNodeWithText("Kurztext").assertIsDisplayed()
    }

    @Test fun dieIllustrationIstHoechstens168DpHoch() {
        // Spec §8.1: 320x240 auf 411 dp Breite waere gut 300 dp hoch — die Grenze haelt es bei 168 dp.
        compose.setContent {
            WhisperLoomTheme { GuideHeader(R.drawable.ill_pro_features, R.string.img_pro_features) }
        }
        val hoehe = compose.onNode(hasIllustration(R.drawable.ill_pro_features, ctx.getString(R.string.img_pro_features)))
            .assertIsDisplayed().getUnclippedBoundsInRoot().height
        assertTrue("Bild $hoehe hoch, erlaubt 168 dp", hoehe <= 168.dp)
    }

    @Test fun einAnderesDrawableFaelltAuf() {
        // Gleicher Bildtext, falsches Bild: der Matcher darf nicht greifen.
        compose.setContent {
            WhisperLoomTheme { GuideHeader(R.drawable.ill_agent_server, R.string.img_pro_features) }
        }
        compose.onNode(hasIllustration(R.drawable.ill_pro_features, ctx.getString(R.string.img_pro_features))).assertDoesNotExist()
        compose.onNode(hasIllustration(R.drawable.ill_agent_server, ctx.getString(R.string.img_pro_features))).assertExists()
    }
}
