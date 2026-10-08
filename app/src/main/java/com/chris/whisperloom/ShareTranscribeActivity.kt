package com.chris.whisperloom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chris.whisperloom.ui.share.SharePhase
import com.chris.whisperloom.ui.share.ShareController
import com.chris.whisperloom.ui.share.ShareScreen
import com.chris.whisperloom.ui.theme.WhisperLoomTheme

/**
 * Nimmt aus anderen Apps geteilte Audiodateien entgegen (Teilen-Menue) und transkribiert
 * sie — der Hauptfall sind WhatsApp-Sprachnachrichten. Ablauf in [ShareController],
 * Darstellung in [ShareScreen] (UX-Spec §2.9).
 *
 * Der Text wird ab Werk WORTGETREU erkannt; "Fuellwoerter ausblenden" ist ein Schalter in der
 * Ansicht. Eine KI-Stufe gibt es nur ueber die Einstellungen (Text › Sprachnachrichten).
 * Die Zwischenablage wird bewusst NICHT automatisch ueberschrieben — nur auf Knopfdruck.
 */
class ShareTranscribeActivity : ComponentActivity() {

    private lateinit var controller: ShareController
    private var wasPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fest dunkel, Systemleisten transparent (Spec §0.2); Scaffold und Aktionsleiste tragen die Insets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        controller = ShareController(this, incomingUris(intent))
        controller.start()
        setContent {
            WhisperLoomTheme {
                ShareScreen(
                    state = controller.state,
                    onClose = ::close,
                    onCopy = ::copyToClipboard,
                    onShare = ::forward,
                    onRetryFile = controller::retryFile,
                    onRetryAll = controller::retryAll,
                    onHideFillersChange = controller::setHideFillers,
                    onOpenSetup = ::openSetup,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
    }

    /** Zurueck aus der Einrichtung: bei "Kein Zugang" gleich noch einmal versuchen. */
    override fun onResume() {
        super.onResume()
        if (wasPaused && controller.state.phase == SharePhase.NOT_CONFIGURED) controller.start()
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }

    /** ACTION_SEND liefert eine Datei, ACTION_SEND_MULTIPLE mehrere. */
    private fun incomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().filterNotNull()
            else -> emptyList()
        }
    }

    // --- Aktionen -------------------------------------------------------------

    /** Schliessen bricht laufende Arbeit ab. */
    private fun close() {
        controller.cancel()
        finish()
    }

    /** Ausweg "Einrichtung oeffnen" -> Assistent in der MainActivity. */
    private fun openSetup() {
        startActivity(AppNav.setup(this))
    }

    private fun plainText(): String = controller.plainText(getString(R.string.share_nothing_recognised))

    private fun copyToClipboard() {
        val text = plainText()
        if (text.isBlank()) return
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whisperloom", text))
        }
    }

    private fun forward() {
        val text = plainText()
        if (text.isBlank()) return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share_forward),
            ),
        )
    }
}
