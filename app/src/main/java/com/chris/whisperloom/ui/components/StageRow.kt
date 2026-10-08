package com.chris.whisperloom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R

/** Breite der Auswahl-Zone: AOSP `two_target_min_width`, 1,5 × 48 dp. */
val StageZoneWidth = 72.dp

/** Das "›" hinter dem Titel (Tests: Lage neben Titel und Zone). */
const val STAGE_CHEVRON_TAG = "stage-chevron"

private val ZoneShape = RoundedCornerShape(16.dp)

/**
 * Stufe mit zwei Zielen (3.9.0, Muster der Android-Systemeinstellungen wie WLAN): Der Text-Bereich
 * oeffnet die Seite der Stufe und aendert nichts, erkennbar am "›" hinter dem Titel. Die Zone rechts
 * waehlt die Stufe: unsichtbar, nur eine groessere Trefferflaeche um den unveraenderten RadioButton,
 * so hoch wie die Zeile; beim Druecken leuchtet ihre eigene Ripple auf. TalkBack: zwei Elemente —
 * der Text als Schaltflaeche "Einstellungen oeffnen", die Zone als Optionsfeld [selectLabel] mit
 * Zustand. [index] = Position in der Liste ([stageList]).
 */
@Composable
fun StageRow(
    headline: String,
    supporting: String,
    selected: Boolean,
    selectLabel: String,
    index: Int,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.stage_open_settings), role = Role.Button, onClick = onOpen)
                .heightIn(min = 72.dp)
                .padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            // Das "›" bleibt neben dem Titel: wird es eng, bricht der Titel um, nie das "›" allein.
            // "Zusammenfassen" passt bei 360 dp und doppelter Schrift nicht in eine Zeile: trennen
            // statt mitten im Wort abzubrechen.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LoomIcon(
                    R.drawable.ic_chevron_right,
                    null,
                    Modifier.size(20.dp).testTag(STAGE_CHEVRON_TAG),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Neben der Zone bleiben bei 360 dp und doppelter Schrift nur ca. 190 dp: lange Woerter
            // ("Kleinschreibung.") trennen statt einen Punkt allein in die naechste Zeile zu schieben.
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Getippt wird ueber die volle Zeilenhoehe; die Ripple ist oben und unten 4 dp eingerueckt und
        // abgerundet, damit sie bei untereinander stehenden Zonen nicht ineinanderlaeuft.
        Box(
            Modifier
                .width(StageZoneWidth)
                .fillMaxHeight()
                .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onSelect)
                .semantics { contentDescription = selectLabel }
                .stageListItem(index)
                .padding(vertical = 4.dp)
                .clip(ZoneShape)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/**
 * Liste der Stufen fuer TalkBack ("3 von 5"). Selbst gesetzt, weil `selectableGroup()` nur direkte
 * Kinder zaehlt und die Zonen tiefer liegen; der Elternknoten darf deshalb kein `selectableGroup()`
 * tragen, sonst ersetzt Compose die Positionen ([stageListItem]).
 */
fun Modifier.stageList(count: Int): Modifier = semantics { collectionInfo = CollectionInfo(rowCount = count, columnCount = 1) }

/** Position [index] in einer [stageList], an der Zone einer [StageRow]. */
private fun Modifier.stageListItem(index: Int): Modifier =
    semantics { collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1) }
