package com.chris.whisperloom.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R

/**
 * Versionstext mit (?) daneben: oeffnet die Patchnotes (Home-Fusszeile, Hilfe, Ueber-Sheet).
 * IconButton = 48-dp-Touch-Ziel; primary, weil das in der App "tippbar" bedeutet.
 */
@Composable
fun VersionWithPatchnotes(
    text: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = color)
        IconButton(onClick = onClick) {
            LoomIcon(R.drawable.ic_help, stringResource(R.string.cd_patchnotes), Modifier.size(iconSize), MaterialTheme.colorScheme.primary)
        }
    }
}
