package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.PolishPlan
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.TextPolisher
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.languageLabel
import com.chris.whisperloom.ui.state.LocalAppEnv

/** B3 Fuellwoerter bearbeiten (Spec §2.5): Sprache, eingebaute Woerter abwaehlbar, eigene Woerter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FillersSheet(onDismiss: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    // Fuellwoerter gibt es je Einzelsprache; "Deutsch + Englisch" nutzt die erkannte.
    val languages = Prefs.LANGUAGES.map { it.first }.filter { it != "auto" && it != Prefs.LANG_DE_EN }
    var lang by rememberSaveable { mutableStateOf(if (prefs.language in languages) prefs.language else "de") }
    var input by rememberSaveable { mutableStateOf("") }

    fun addWords() {
        val words = PolishPlan.parseFillers(input)
        if (words.isNotEmpty()) {
            prefs.customFillers = prefs.customFillers + words
            input = ""
        }
    }

    LoomSheet(title = stringResource(R.string.fillers_title), onDismiss = onDismiss) { dismiss ->
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            languages.forEachIndexed { i, code ->
                SegmentedButton(
                    selected = lang == code,
                    onClick = { lang = code },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = languages.size),
                    icon = {},
                    label = { Text(languageLabel(code), maxLines = 1) },
                )
            }
        }

        Text(stringResource(R.string.fillers_builtin), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPolisher.builtinFillers(lang).forEach { word ->
                val enabled = word !in prefs.disabledFillers
                FilterChip(
                    selected = enabled,
                    onClick = { prefs.disabledFillers = if (enabled) prefs.disabledFillers + word else prefs.disabledFillers - word },
                    label = { Text(word) },
                )
            }
        }

        Text(stringResource(R.string.fillers_custom), style = MaterialTheme.typography.labelLarge)
        if (prefs.customFillers.isEmpty()) {
            Text(
                stringResource(R.string.fillers_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prefs.customFillers.sorted().forEach { word ->
                    InputChip(
                        selected = false,
                        onClick = { prefs.customFillers = prefs.customFillers - word },
                        label = { Text(word) },
                        trailingIcon = {
                            LoomIcon(R.drawable.ic_close, stringResource(R.string.cd_remove_word, word), Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.fillers_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addWords() }),
            trailingIcon = {
                IconButton(onClick = { addWords() }) {
                    LoomIcon(R.drawable.ic_add, stringResource(R.string.cd_add_word))
                }
            },
        )
        Text(
            stringResource(R.string.fillers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                prefs.disabledFillers = emptySet()
                prefs.customFillers = emptySet()
            }) { Text(stringResource(R.string.fillers_reset)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = dismiss) { Text(stringResource(R.string.common_done)) }
        }
    }
}
