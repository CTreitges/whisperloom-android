package com.chris.whisperloom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R

/** Trenner zwischen zwei Hub-Zeilen — fuer Tests, die pruefen, dass eine Gruppe ohne Trenner endet. */
const val HUB_DIVIDER_TAG = "hub-divider"

/**
 * Zeile eines Hubs (Einstellungen, Text): Icon im Kreis, Titel, aktueller Wert als Unterzeile, Pfeil.
 * TalkBack liest den Wert als Zustand. Die letzte Zeile einer Gruppe ohne Trenner ([divider]).
 */
@Composable
internal fun HubRow(icon: Int, headline: String, value: String, divider: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface) }
        },
        trailingContent = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = value },
    )
    if (divider) {
        HorizontalDivider(
            Modifier.padding(start = 76.dp).testTag(HUB_DIVIDER_TAG),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
