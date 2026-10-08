package com.chris.whisperloom.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.access.LlmAccessSection
import com.chris.whisperloom.ui.access.StageModelsSection
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.nav.NavState

/**
 * KI-Zugang (3.9.0, vorher Text › Online-Zugang & Modelle): der Zugang fuer die Textverbesserung und
 * darunter "Modell je Stufe" — gilt fuer Diktat und Sprachnachrichten.
 */
@Composable
fun LlmAccessScreen(nav: NavState) {
    SettingsPageScaffold(R.string.settings_group_llm, nav) { snack ->
        SectionCard(title = stringResource(R.string.text_card_access)) {
            LlmAccessSection(snack)
        }
        SectionCard(title = stringResource(R.string.text_models_title), gap = 4.dp) {
            StageModelsSection()
        }
    }
}
