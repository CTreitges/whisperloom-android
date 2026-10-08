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
 * Text › Online-Zugang & Modelle (3.8.6): der Zugang fuer die Textverbesserung und darunter
 * "Modell je Stufe" — die Modellwahl an einem Ort.
 */
@Composable
fun TextAccessScreen(nav: NavState) {
    TextPageScaffold(R.string.text_hub_access, nav) { snack ->
        SectionCard(title = stringResource(R.string.text_card_access)) {
            LlmAccessSection(snack)
        }
        SectionCard(title = stringResource(R.string.text_models_title), gap = 4.dp) {
            StageModelsSection()
        }
    }
}
