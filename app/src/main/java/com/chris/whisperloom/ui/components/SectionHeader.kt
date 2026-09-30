package com.chris.whisperloom.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Gruppen-Ueberschrift in Listen (Einstellungs-Hub): labelLarge in primary, in Grossbuchstaben.
 * TalkBack springt per Ueberschrift von Gruppe zu Gruppe und liest [text] in normaler Schreibung
 * vor — "PRO" oder "INFO" wuerde es sonst womoeglich buchstabieren.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
            .semantics {
                heading()
                contentDescription = text
            },
    )
}
