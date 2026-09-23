package com.chris.whisperloom.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.Vocabulary
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.components.ApiKeyField
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.providerLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.state.LocalAppEnv

/**
 * Zugang zum Transkriptions-Dienst — identisch in Schritt 2a und E1 (Spec §2.2/§2.4):
 * Anbieter, Base-URL (Preset-Zeile bzw. Feld beim eigenen Server), API-Key, Modell,
 * "Wo bekomme ich einen Key?", "Zugang pruefen", Datenschutz-Zeile.
 */
@Composable
fun SttAccessSection(snack: SnackController, showPrivacy: Boolean = true) {
    val prefs = LocalAppEnv.current.prefs
    val access = prefs.sttAccess()
    val provider = access.provider
    val providers = ProviderCatalog.sttProviders
    val labels = providers.associate { it.id to providerLabel(it) }
    var showKeySheet by rememberSaveable { mutableStateOf(false) }
    var showCustomModel by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoomDropdown(
            label = stringResource(R.string.rec_provider),
            value = labels[provider.id] ?: provider.name,
            options = providers,
            optionLabel = { labels[it.id] ?: it.name },
            onSelect = { p ->
                if (p.id != prefs.sttProviderId) {
                    // Anbieter-Wechsel: Preset-URL und erstes Modell (leer = Anbieter-Default, wp2 §4).
                    prefs.sttProviderId = p.id
                    prefs.apiBaseUrl = ""
                    prefs.apiModel = ""
                }
            },
            supportingText = if (!provider.isCustom) ({ Text(access.baseUrl) }) else null,
        )

        if (provider.isCustom) {
            val problem = if (prefs.apiBaseUrl.isBlank()) null else ServerUrlCheck.check(prefs.apiBaseUrl, provider)
            OutlinedTextField(
                value = prefs.apiBaseUrl,
                onValueChange = { prefs.apiBaseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rec_base_url)) },
                placeholder = { Text("https://server:8000/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = problem?.severity == ServerUrlCheck.Severity.ERROR,
                supportingText = {
                    Text(if (problem != null) urlProblemText(problem) else stringResource(R.string.rec_base_url_hint))
                },
            )
        }

        ApiKeyField(value = prefs.apiKey, onValueChange = { prefs.apiKey = it }, optional = provider.isCustom)

        if (provider.isCustom) {
            OutlinedTextField(
                value = prefs.apiModel,
                onValueChange = { prefs.apiModel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rec_model)) },
                placeholder = { Text("whisper-1") },
                singleLine = true,
                // Kein Default beim eigenen Server: speaches/LocalAI lehnen ein leeres model mit 422 ab.
                isError = prefs.apiModel.isBlank(),
                supportingText = { Text(stringResource(R.string.model_custom_info)) },
            )
        } else {
            LoomDropdown(
                label = stringResource(R.string.rec_model),
                value = modelLabel(access),
                options = provider.sttModels,
                optionLabel = { it.label },
                onSelect = { prefs.apiModel = it.id },
                extraOption = stringResource(R.string.rec_model_custom),
                onExtra = { showCustomModel = true },
            )
        }

        TextButton(onClick = { showKeySheet = true }) {
            LoomIcon(R.drawable.ic_help, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.rec_key_where))
        }

        TestAccessRow(label = stringResource(R.string.rec_test)) {
            AccessTest.stt(prefs.sttAccess(), Vocabulary.prompt(Vocabulary.entries(prefs.apiPrompt)).text, prefs.language)
        }

        if (showPrivacy) PrivacyLine(stringResource(R.string.rec_privacy_online, providerShortName(provider)))
    }

    if (showKeySheet) KeySheet(providers, snack) { showKeySheet = false }
    if (showCustomModel) {
        CustomModelSheet(
            placeholder = "whisper-1",
            initial = if (access.modelOption == null) access.model else "",
            onApply = { prefs.apiModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/** bodySmall-Zeile mit ic_privacy_tip 16 dp (Spec §2.4). */
@Composable
fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoomIcon(R.drawable.ic_privacy_tip, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Klartext zu ServerUrlCheck-Problemen (Spec §6.1 err_url_*). */
@Composable
fun urlProblemText(problem: ServerUrlCheck.Problem): String = when (problem.message) {
    ServerUrlCheck.MSG_INVALID, ServerUrlCheck.MSG_NO_HOST -> stringResource(R.string.err_url_invalid)
    ServerUrlCheck.MSG_SCHEME -> stringResource(R.string.err_url_scheme)
    ServerUrlCheck.MSG_HTTPS_REQUIRED -> stringResource(R.string.err_url_https_required)
    ServerUrlCheck.MSG_PUBLIC_HTTP -> stringResource(R.string.err_url_cleartext_public)
    ServerUrlCheck.MSG_V1 -> stringResource(R.string.err_url_v1)
    else -> problem.message
}
