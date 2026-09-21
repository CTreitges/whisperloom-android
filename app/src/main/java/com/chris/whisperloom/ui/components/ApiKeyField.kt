package com.chris.whisperloom.ui.components

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.chris.whisperloom.R
import kotlinx.coroutines.delay

/**
 * API-Key-Feld (Spec §2.2 Schritt 2a): maskiert, Auge zum Anzeigen, Einfuegen aus der
 * Zwischenablage; der Key wird nie im Klartext vorgelesen (Passwort-Semantik).
 */
@Composable
fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    label: String? = null,
) {
    val ctx = LocalContext.current
    var visible by rememberSaveable { mutableStateOf(false) }
    var pasted by remember { mutableStateOf(false) }
    LaunchedEffect(pasted) {
        if (pasted) {
            delay(PASTED_HINT_MS)
            pasted = false
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label ?: stringResource(if (optional) R.string.rec_api_key_optional else R.string.rec_api_key)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        supportingText = { Text(stringResource(if (pasted) R.string.rec_key_pasted else R.string.rec_key_local)) },
        trailingIcon = {
            Row {
                IconButton(onClick = { visible = !visible }) {
                    LoomIcon(
                        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                        stringResource(if (visible) R.string.cd_key_hide else R.string.cd_key_show),
                    )
                }
                IconButton(onClick = {
                    val text = ctx.getSystemService(ClipboardManager::class.java)
                        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        onValueChange(text)
                        pasted = true
                    }
                }) {
                    LoomIcon(R.drawable.ic_content_paste, stringResource(R.string.cd_paste))
                }
            }
        },
    )
}

private const val PASTED_HINT_MS = 2000L
