package com.chris.whisperloom.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Abgrenzung der Auswahl-Zone einer [StageRow] (E11): getoente Flaeche (Standard) oder nur ein Trenner. */
enum class StageZone { TONAL, DIVIDER }

/** Breite der Auswahl-Zone: AOSP `two_target_min_width`, 1,5 × 48 dp. */
val StageZoneWidth = 72.dp

private val ZoneShape = RoundedCornerShape(16.dp)

/**
 * Stufe mit zwei Zielen (3.9.0, Muster der Android-Systemeinstellungen wie WLAN): Der Text-Bereich
 * oeffnet die Seite der Stufe und aendert nichts, die Zone rechts waehlt die Stufe. Die Zone ist so
 * hoch wie die Zeile, dauerhaft leicht getoent, hat ihre eigene Ripple und traegt den unveraenderten
 * RadioButton. TalkBack: zwei Elemente — der Text als Schaltflaeche "Einstellungen oeffnen", die Zone
 * als Optionsfeld [selectLabel] mit Zustand. [index] = Position in der Liste ([stageList]).
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
    zone: StageZone = StageZone.TONAL,
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
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            // Neben der Zone bleiben bei 360 dp und doppelter Schrift nur ca. 190 dp: lange Woerter
            // ("Kleinschreibung.") trennen statt einen Punkt allein in die naechste Zeile zu schieben.
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (zone == StageZone.DIVIDER) {
            VerticalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        // Getippt wird ueber die volle Zeilenhoehe; die Flaeche selbst ist oben und unten 4 dp
        // eingerueckt, damit untereinander stehende Zonen getrennt aussehen.
        Box(
            Modifier
                .width(StageZoneWidth)
                .fillMaxHeight()
                .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onSelect)
                .semantics { contentDescription = selectLabel }
                .stageListItem(index)
                .padding(vertical = 4.dp)
                .clip(ZoneShape)
                .background(if (zone == StageZone.TONAL) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/**
 * Radio-Punkt einer Zeile ohne eigene Seite ("Aus") als `trailing` einer [LoomRow]: in derselben
 * Spalte wie die Punkte in den Zonen der [StageRow]s darunter.
 */
@Composable
fun StageRadio(selected: Boolean) {
    Box(Modifier.width(StageZoneWidth), contentAlignment = Alignment.Center) {
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Liste der Stufen fuer TalkBack ("3 von 5"). Selbst gesetzt, weil `selectableGroup()` nur direkte
 * Kinder zaehlt und die Zonen tiefer liegen; der Elternknoten darf deshalb kein `selectableGroup()`
 * tragen, sonst ersetzt Compose die Positionen ([stageListItem]).
 */
fun Modifier.stageList(count: Int): Modifier = semantics { collectionInfo = CollectionInfo(rowCount = count, columnCount = 1) }

/** Position [index] in einer [stageList]: an der Zone einer [StageRow] und an der Zeile "Aus". */
fun Modifier.stageListItem(index: Int): Modifier =
    semantics { collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1) }
