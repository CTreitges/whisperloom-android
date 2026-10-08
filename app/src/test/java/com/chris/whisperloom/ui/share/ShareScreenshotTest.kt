package com.chris.whisperloom.ui.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.SharedTranscript
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import java.io.File
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots des Sprachnachrichten-Fensters zum Ansehen (PNGs nach app/build/reports/screenshots/):
 * Seit 3.9.0 teilt es Kopfkarte, Text, Hinweiszeile, Fuellwoerter-Leiste und Aktionsleiste mit dem
 * Verlauf — die Bilder muessen vor und nach dem Herausloesen gleich aussehen. Wie
 * SettingsScreenshotTest: auf linux-aarch64 uebersprungen, unter qemu (x86_64-JVM) ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareScreenshotTest {

    companion object {
        @BeforeClass @JvmStatic fun nurMitNativeRuntime() {
            val linuxArm = System.getProperty("os.name").orEmpty().startsWith("Linux") && System.getProperty("os.arch") == "aarch64"
            Assume.assumeFalse("Robolectric-Native-Graphics fehlt auf Linux aarch64", linuxArm)
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun transcript(source: String, verbatim: List<String>, cleaned: List<String>, refined: List<String>? = null, skipped: String? = null) =
        SharedTranscript(
            source = source,
            verbatimText = verbatim.joinToString("\n\n"),
            cleanedText = cleaned.joinToString("\n\n"),
            paragraphsVerbatim = verbatim,
            paragraphsCleaned = cleaned,
            durationMs = 83_000,
            backendLabel = "OpenAI",
            paragraphsRefined = refined,
            refineMode = RefineMode.POLISH,
            refineSkipped = skipped,
        )

    private fun shot(name: String, state: ShareUiState) {
        compose.setContent {
            WhisperLoomTheme {
                ShareScreen(state, onClose = {}, onCopy = {}, onShare = {}, onRetryFile = {}, onRetryAll = {},
                    onHideFillersChange = {}, onOpenSetup = {}, onOpenRefine = {})
            }
        }
        compose.waitForIdle()
        val file = File("build/reports/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ohneKiMitFuellwoerterLeiste() {
        val t = transcript(
            "PTT-20261008-WA0003.opus",
            verbatim = listOf("Ähm also ich wollte kurz sagen, dass ich morgen, äh, später komme.", "Und Samstag passt mir gut."),
            cleaned = listOf("Also ich wollte kurz sagen, dass ich morgen später komme.", "Und Samstag passt mir gut."),
            skipped = "Kein Netz",
        )
        shot("sprachnachricht-ohne-ki", ShareUiState(SharePhase.DONE, listOf(ShareFile("PTT-20261008-WA0003.opus", t)), hideFillers = true))
    }

    @Test fun mitKiUndFehlerBeiZweiDateien() {
        val t = transcript(
            "a.opus",
            verbatim = listOf("ähm hallo"),
            cleaned = listOf("hallo"),
            refined = listOf("Hallo, wegen Samstag: Ich bringe den Kuchen mit.", "Bis dann."),
        )
        shot("sprachnachricht-zwei-dateien", ShareUiState(SharePhase.DONE, listOf(ShareFile("a.opus", t), ShareFile("b.opus", error = "API-Fehler 500"))))
    }

    @Test fun laden() {
        shot(
            "sprachnachricht-laden",
            ShareUiState(SharePhase.LOADING, listOf(ShareFile("a.opus"), ShareFile("b.opus")), progress = ShareProgress(0, 2, 1, 3, "Wird übertragen …")),
        )
    }
}
