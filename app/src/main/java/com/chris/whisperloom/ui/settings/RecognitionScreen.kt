package com.chris.whisperloom.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.access.PrivacyLine
import com.chris.whisperloom.ui.access.SttAccessSection
import com.chris.whisperloom.ui.access.SttFallbackSection
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.languageLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.models.LocalModelRequiredCard
import com.chris.whisperloom.ui.models.ModelListSection
import com.chris.whisperloom.ui.models.localModelMissing
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore

/**
 * E1 — Spracherkennung (UX-Spec §2.4, Neuaufbau §6.20): der Umschalter Online/Offline, dann je nach Weg
 * der Transkriptions-Zugang mit Fallback (3.9.1) oder die Offline-Modelle — Liste mit Laden, Auswahl und
 * Loeschen, bis 3.9.1 die eigene Seite "Offline-Modelle" mit zweitem Umschalter. Darunter die Sprache.
 * Das Vokabular steht seit 3.9.0 unter Woerterbuch & Regeln.
 */
@Composable
fun RecognitionScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    val offline = prefs.engine == Engine.OFFLINE
    val stt = prefs.sttAccess()

    DetailScaffold(title = stringResource(R.string.rec_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            // Offline geht auch ohne geladenes Modell: die Liste darunter zeigt dann "Noch kein Modell geladen".
            EngineSwitch(snack, requireModelForOffline = false)

            // Offline mit KI-Stufe: ohne Textmodell kaeme der Text ohne KI (Spec §4, Pflichtkarte).
            if (localModelMissing(prefs, status)) LocalModelRequiredCard()

            if (!offline) {
                SectionCard(title = stringResource(R.string.rec_card_transcription)) {
                    SttAccessSection(snack, showPrivacy = false)
                    // 3.9.1: direkt unter dem ersten Anbieter, Felder erst mit dem Schalter.
                    SttFallbackSection()
                }
            } else {
                OfflineModels(snack)
            }

            // Bis 3.8.6 "Sprache & Kontext"; der Kontext (Vokabular) steht jetzt im Woerterbuch.
            SectionCard {
                LoomDropdown(
                    label = stringResource(R.string.rec_language),
                    value = languageLabel(prefs.language),
                    options = Prefs.LANGUAGES,
                    optionLabel = { it.second },
                    onSelect = { prefs.language = it.first },
                )
            }

            PrivacyLine(
                if (offline) stringResource(R.string.rec_privacy_offline)
                else stringResource(R.string.rec_privacy_online, providerShortName(stt.provider)),
            )
        }
    }
}

/**
 * Die Whisper-Modelle (bis 3.9.1 der Abschnitt Spracherkennung der Offline-Modelle): Intro, Speicher,
 * die Liste mit Laden, Auswahl und Loeschen, Quelle und der Hinweis zur ersten Nutzung.
 */
@Composable
private fun OfflineModels(snack: SnackController) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val store = remember { ModelStore(ctx) }
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    // Nach Download/Loeschen aendert sich der Systemstatus (installierte Modelle) -> Speicherzeile neu lesen.
    val used = remember(states, env.status) { store.usedBytes() }
    val free = remember(states, env.status) { store.freeBytes() }

    SectionHeader(stringResource(R.string.rec_card_offline), inset = 0.dp)
    Text(
        stringResource(R.string.models_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.models_storage, fileSize(used), fileSize(free)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelListSection(snack)
    Text(
        stringResource(R.string.models_source),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        stringResource(R.string.rec_offline_first_use),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
