package com.chris.whisperloom.ui.share

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.SharedTranscript
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** Semantik-Tests der Share-Ansicht (UX-Spec §2.9, Akzeptanzkriterien §7 "S"). */
// qualifiers: Telefon-Groesse (Robolectric-Default ist winzig), sonst liegen Listen-Eintraege ausserhalb des Viewports.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShareScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun transcript(
        source: String,
        cleaned: List<String>,
        verbatim: List<String> = cleaned,
        durationMs: Long = 7000,
    ) = SharedTranscript(
        source = source,
        verbatimText = verbatim.joinToString("\n\n"),
        cleanedText = cleaned.joinToString("\n\n"),
        paragraphsVerbatim = verbatim,
        paragraphsCleaned = cleaned,
        durationMs = durationMs,
        backendLabel = "Test",
    )

    private val done = transcript("a.ogg", cleaned = listOf("Erster Absatz.", "Zweiter Absatz."))

    private fun show(
        state: ShareUiState,
        onClose: () -> Unit = {},
        onCopy: () -> Unit = {},
        onRetryFile: (Int) -> Unit = {},
        onRetryAll: () -> Unit = {},
        onOpenSetup: () -> Unit = {},
    ) {
        compose.setContent {
            WhisperLoomTheme {
                ShareScreen(
                    state = state,
                    onClose = onClose,
                    onCopy = onCopy,
                    onShare = {},
                    onRetryFile = onRetryFile,
                    onRetryAll = onRetryAll,
                    onHideFillersChange = {},
                    onOpenSetup = onOpenSetup,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun ladenZeigtFortschrittUndSkeleton() {
        show(
            ShareUiState(
                phase = SharePhase.LOADING,
                files = listOf(ShareFile("a.ogg"), ShareFile("b.ogg")),
                progress = ShareProgress(0, 2, 1, 3, "Wird übertragen …"),
            ),
        )
        compose.onNodeWithText("Datei 1 von 2 · Stück 2 von 3 · Wird übertragen …").assertIsDisplayed()
        compose.onNodeWithTag(SKELETON_TAG).assertExists()
        compose.onNodeWithText("2 Dateien · –:–– gesamt").assertIsDisplayed()
        compose.onNodeWithText("Kopieren").assertDoesNotExist()
        compose.onNodeWithText("Füllwörter ausblenden").assertDoesNotExist()
    }

    @Test fun eineDateiEinStueckZeigtNurDenStatus() {
        show(
            ShareUiState(
                phase = SharePhase.LOADING,
                files = listOf(ShareFile("")),
                progress = ShareProgress(0, 1, 0, 1, "Audio wird entpackt …"),
            ),
        )
        compose.onNodeWithText("Audio wird entpackt …").assertIsDisplayed()
        compose.onNodeWithText("Datei 1 von 1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Sprachnachricht").assertIsDisplayed()
        compose.onNodeWithText("–:–– · 1 Datei").assertIsDisplayed()
    }

    @Test fun fertigZeigtAbsaetzeEinzelnUndAktionsleiste() {
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = done))))
        compose.onNodeWithText("Erster Absatz.").assertIsDisplayed()
        compose.onNodeWithText("Zweiter Absatz.").assertIsDisplayed()
        compose.onNodeWithText("a.ogg").assertIsDisplayed()
        compose.onNodeWithText("0:07 · 1 Datei").assertIsDisplayed()
        compose.onNodeWithText("Kopieren").assertIsDisplayed()
        compose.onNodeWithText("Teilen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Text kopieren").assertIsDisplayed()
        compose.onNodeWithContentDescription("Alles erneut").assertDoesNotExist()
        compose.onNodeWithText("Füllwörter ausblenden").assertIsDisplayed()
        compose.onNodeWithTag(SKELETON_TAG).assertDoesNotExist()
    }

    @Test fun schalterWechseltSichtbarenTextUndSchreibtPrefs() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = ShareController(
            context = ctx,
            uris = listOf(Uri.parse("content://test/a.ogg")),
            transcriber = ShareTranscriber { _, _, _, _ ->
                transcript("a.ogg", cleaned = listOf("Hallo Welt."), verbatim = listOf("Ähm hallo Welt."))
            },
            nameOf = { _, _ -> "a.ogg" },
            executor = Executor { it.run() },
            post = { it() },
        )
        controller.start()
        compose.setContent {
            WhisperLoomTheme {
                ShareScreen(
                    state = controller.state,
                    onClose = {},
                    onCopy = {},
                    onShare = {},
                    onRetryFile = controller::retryFile,
                    onRetryAll = controller::retryAll,
                    onHideFillersChange = controller::setHideFillers,
                    onOpenSetup = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Hallo Welt.").assertIsDisplayed()
        compose.onNodeWithText("ähm, äh … ausgeblendet").assertIsDisplayed()

        compose.onNodeWithText("Füllwörter ausblenden").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ähm hallo Welt.").assertIsDisplayed()
        compose.onNodeWithText("Wortgetreu, 100 %").assertIsDisplayed()
        assertFalse(Prefs(ctx).shareHideFillers)
        assertEquals("Ähm hallo Welt.", controller.plainText())
    }

    @Test fun kiFassungErsetztDenSchalterUndNenntDieStufe() {
        val k = done.copy(paragraphsRefined = listOf("Geglätteter Absatz."), refineMode = RefineMode.POLISH)
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = k))))
        compose.onNodeWithText("Geglätteter Absatz.").assertIsDisplayed()
        compose.onNodeWithText("Erster Absatz.").assertDoesNotExist()
        compose.onNodeWithText("Füllwörter ausblenden").assertDoesNotExist()
        compose.onNodeWithText("Textverbesserung: Glätten · änderbar unter Einstellungen › Text › Sprachnachrichten").assertIsDisplayed()
    }

    @Test fun onlineGescheitertLokalVerbessertIstEinHinweisKeinFehler() {
        val k = done.copy(paragraphsRefined = listOf("Lokal geglättet."), refineMode = RefineMode.POLISH, refineLocalFallback = true)
        val state = ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = k)))
        assertTrue(state.refineLocalFallback)
        show(state)
        compose.onNodeWithText("Lokal geglättet.").assertIsDisplayed()
        compose.onNodeWithText("Textverbesserung: Glätten · online fehlgeschlagen, lokal verbessert").assertIsDisplayed()
        compose.onNodeWithText("Füllwörter ausblenden").assertDoesNotExist()
    }

    @Test fun waehrendDerLokalenVerbesserungNenntDieFortschrittszeileLokal() {
        val text = ctx.getString(com.chris.whisperloom.R.string.share_refining_local)
        show(ShareUiState(phase = SharePhase.LOADING, files = listOf(ShareFile("a.ogg")), progress = ShareProgress(0, 1, 1, 1, text)))
        compose.onNodeWithText("Text wird lokal verbessert …").assertIsDisplayed()
    }

    @Test fun gescheiterteKiZeigtGrundUndDenSchalter() {
        val k = done.copy(refineMode = RefineMode.BEAUTIFY, refineSkipped = "API-Fehler 401")
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = k))))
        compose.onNodeWithText("Erster Absatz.").assertIsDisplayed()
        compose.onNodeWithText("Textverbesserung übersprungen: API-Fehler 401").assertIsDisplayed()
        compose.onNodeWithText("Füllwörter ausblenden").assertIsDisplayed()
    }

    @Test fun ohneStufeKeinHinweis() {
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = done))))
        compose.onNodeWithText("Textverbesserung", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Füllwörter ausblenden").assertIsDisplayed()
    }

    /** Review: bei gemischtem Ergebnis sah die KI-Fassung der anderen Dateien wie gescheitert aus. */
    @Test fun teilweiseGescheiterteKiNenntDieAnzahl() {
        val ok = done.copy(source = "a.ogg", paragraphsRefined = listOf("Geglättet A."), refineMode = RefineMode.BEAUTIFY)
        val kaputt = transcript("b.ogg", cleaned = listOf("Roh B.")).copy(refineMode = RefineMode.BEAUTIFY, refineSkipped = "API-Fehler 429")
        val state = ShareUiState(
            phase = SharePhase.DONE,
            files = listOf(ShareFile("a.ogg", result = ok), ShareFile("b.ogg", result = kaputt)),
        )
        assertEquals(1, state.refineFailures)
        assertTrue(state.hasUnrefined)
        assertEquals(RefineMode.BEAUTIFY, state.refineMode)
        show(state)
        compose.onNodeWithText("Geglättet A.").assertIsDisplayed()
        compose.onNodeWithText("Roh B.").assertIsDisplayed()
        compose.onNodeWithText("Textverbesserung: Verschönern · bei 1 von 2 Dateien übersprungen: API-Fehler 429").assertIsDisplayed()
        // Der Schalter bleibt — er wirkt auf die Datei ohne KI-Fassung.
        compose.onNodeWithText("Füllwörter ausblenden").assertIsDisplayed()
    }

    @Test fun fehlerkarteZeigtErneutNurFuerDieseDatei() {
        var retried = -1
        show(
            ShareUiState(
                phase = SharePhase.DONE,
                files = listOf(ShareFile("a.ogg", result = done), ShareFile("b.ogg", error = "Netz weg")),
            ),
            onRetryFile = { retried = it },
        )
        compose.onNodeWithText("a.ogg · 0:07").assertIsDisplayed() // Abschnitts-Ueberschrift wegen Fehler
        compose.onNodeWithText("Fehlgeschlagen: Netz weg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Erneut").performScrollTo().performClick()
        assertEquals(1, retried)
        compose.onNodeWithContentDescription("Alles erneut").assertIsDisplayed()
    }

    @Test fun erneutIstGesperrtSolangeEineAndereDateiLaeuft() {
        // Review KOR-1: waehrend LADEN gibt es keinen Einzel-Retry (er wuerde den Lauf ersetzen).
        show(
            ShareUiState(
                phase = SharePhase.LOADING,
                files = listOf(ShareFile("a.ogg", error = "Netz weg"), ShareFile("b.ogg")),
                progress = ShareProgress(1, 2, 0, 1, "Wird übertragen …"),
            ),
        )
        compose.onNodeWithText("Fehlgeschlagen: Netz weg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Erneut").assertIsNotEnabled()
    }

    @Test fun keinZugangZeigtEinrichtungOeffnen() {
        var opened = false
        show(ShareUiState(phase = SharePhase.NOT_CONFIGURED, files = listOf(ShareFile("a.ogg"))), onOpenSetup = { opened = true })
        compose.onNodeWithText("Kein Zugang eingerichtet").assertIsDisplayed()
        compose.onNodeWithText("Schließen").assertIsDisplayed()
        compose.onNodeWithText("Einrichtung öffnen").performClick()
        assertTrue(opened)
    }

    @Test fun keineDateiZeigtSchliessen() {
        var closed = false
        show(ShareUiState(phase = SharePhase.NO_FILE), onClose = { closed = true })
        compose.onNodeWithText("Keine Audiodatei erhalten").assertIsDisplayed()
        compose.onNodeWithText("Schließen").performClick()
        assertTrue(closed)
    }

    @Test fun gesamtfehlerZeigtGrundUndErneut() {
        var retried = false
        show(
            ShareUiState(phase = SharePhase.ALL_FAILED, files = listOf(ShareFile("a.ogg", error = "Kaputt")), failure = "Kaputt"),
            onRetryAll = { retried = true },
        )
        compose.onNodeWithText("Transkription fehlgeschlagen").assertIsDisplayed()
        compose.onNodeWithText("Fehlgeschlagen: Kaputt").assertIsDisplayed()
        compose.onNodeWithText("Kopieren").assertDoesNotExist()
        compose.onNodeWithText("Erneut").performClick()
        assertTrue(retried)
    }

    @Test fun nichtsErkanntWirdAngezeigt() {
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = transcript("a.ogg", emptyList())))))
        compose.onNodeWithText("(nichts erkannt)").assertIsDisplayed()
    }

    @Test fun kopierenZeigtSnackbar() {
        var copied = false
        compose.mainClock.autoAdvance = false
        show(ShareUiState(phase = SharePhase.DONE, files = listOf(ShareFile("a.ogg", result = done))), onCopy = { copied = true })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Kopieren").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(copied)
        compose.onNodeWithText("In die Zwischenablage kopiert").assertIsDisplayed()
    }
}
