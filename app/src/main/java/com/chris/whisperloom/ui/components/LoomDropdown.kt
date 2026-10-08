package com.chris.whisperloom.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Auswahlfeld (ExposedDropdownMenuBox, Spec §0.2) mit optionalem letzten Eintrag
 * [extraOption] (z. B. "Eigenes Modell …"), der statt einer Auswahl [onExtra] ausloest.
 * [optionSupporting] = zweite, kleine Zeile eines Eintrags (null = keine).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoomDropdown(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    extraOption: String? = null,
    onExtra: () -> Unit = {},
    supportingText: (@Composable () -> Unit)? = null,
    optionSupporting: (T) -> String? = { null },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dropdown:$label")
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ) {
            options.forEach { option ->
                val sub = optionSupporting(option)
                DropdownMenuItem(
                    text = {
                        if (sub == null) {
                            Text(optionLabel(option))
                        } else {
                            Column {
                                Text(optionLabel(option))
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (extraOption != null) {
                DropdownMenuItem(
                    text = { Text(extraOption) },
                    onClick = {
                        expanded = false
                        onExtra()
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Sieht aus wie [LoomDropdown], oeffnet aber statt des Menues [onClick] — fuer Auswahlen mit
 * eigenem Sheet (Modell-Liste vom Server mit Suche und Hunderten Eintraegen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoomPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    // Das Menue bleibt zu: die Box liefert nur Anker, Klick und Pfeil wie beim Dropdown.
    ExposedDropdownMenuBox(expanded = false, onExpandedChange = { onClick() }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
            isError = isError,
            supportingText = supportingText,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("picker:$label")
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
    }
}
