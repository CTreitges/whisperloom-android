package com.chris.whisperloom.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import com.chris.whisperloom.R
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.ui.components.LinkRow
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.providerLabel

/** B1 "Wo bekomme ich einen Key?": je Anbieter Kurzschritte + Link zur Key-Seite (Spec §2.10). */
@Composable
fun KeySheet(providers: List<Provider>, snack: SnackController, onDismiss: () -> Unit) {
    LoomSheet(title = stringResource(R.string.key_sheet_title), onDismiss = onDismiss) { dismiss ->
        Column {
            providers.filter { it.keyUrl.isNotEmpty() }.forEach { p ->
                LinkRow(headline = providerLabel(p), supporting = helpKeyText(p.id), url = p.keyUrl, snack = snack)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
        }
    }
}

/** Kurzanleitung "Key bekommen" je Anbieter (Spec §6.9). */
@Composable
fun helpKeyText(providerId: String): String? = when (providerId) {
    "openai" -> stringResource(R.string.help_key_openai)
    "groq" -> stringResource(R.string.help_key_groq)
    "mistral" -> stringResource(R.string.help_key_mistral)
    "together" -> stringResource(R.string.help_key_together)
    "deepinfra" -> stringResource(R.string.help_key_deepinfra)
    "openrouter" -> stringResource(R.string.help_key_openrouter)
    "anthropic" -> stringResource(R.string.help_key_anthropic)
    "gemini" -> stringResource(R.string.help_key_gemini)
    "deepseek" -> stringResource(R.string.help_key_deepseek)
    "ollama-cloud" -> stringResource(R.string.help_key_ollama_cloud)
    else -> null
}

/** B2 "Eigenes Modell": freie Modell-ID; Uebernehmen nur bei Text (Spec §2.10). */
@Composable
fun CustomModelSheet(
    placeholder: String,
    initial: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    LoomSheet(title = stringResource(R.string.model_custom_title), onDismiss = onDismiss) { dismiss ->
        val apply = {
            if (text.isNotBlank()) {
                onApply(text.trim())
                dismiss()
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.model_custom_hint)) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            supportingText = { Text(stringResource(R.string.model_custom_info)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_cancel)) }
            Button(onClick = apply, enabled = text.isNotBlank()) { Text(stringResource(R.string.common_apply)) }
        }
    }
}

private typealias Alignment = androidx.compose.ui.Alignment
