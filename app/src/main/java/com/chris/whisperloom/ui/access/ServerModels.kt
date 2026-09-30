package com.chris.whisperloom.ui.access

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.R
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zuletzt geladene Modell-Liste eines Zugangs ([ModelCache]) als Compose-Zustand.
 *
 * Je Anbieter, Zweck und Adresse eine eigene Instanz ([rememberServerModels]): wechselt eines
 * davon, landet ein noch laufendes Laden in der alten Instanz und nicht mehr in der Anzeige.
 */
@Stable
class ServerModels(private val cache: ModelCache, private val kind: ModelKind, initial: ModelCache.Entry?) {
    var entry by mutableStateOf(initial)
        private set

    /** Nur der Knopf zeigt "Lade Modelle …"; das stille Nachladen nicht. */
    var loading by mutableStateOf(false)
        private set

    val ids: List<String> get() = entry?.models.orEmpty().map { it.id }

    /** Keine Liste oder aelter als ein Tag. */
    val isStale: Boolean get() = cache.isStale(entry)

    /** Laedt neu und legt die Liste ab. Bei einem Fehler bleibt die alte stehen. */
    suspend fun refresh(access: ApiAccess, showLoading: Boolean): Result<ModelCache.Entry> {
        if (showLoading) loading = true
        try {
            val result = withContext(Dispatchers.IO) { runCatching { cache.refresh(access, kind) } }
            result.onSuccess { entry = it }
            return result
        } finally {
            if (showLoading) loading = false
        }
    }
}

/** Die Liste zu [access] (immer `prefs.sttAccess()` bzw. `prefs.llmAccess()`, dann passt der Schluessel). */
@Composable
fun rememberServerModels(access: ApiAccess, kind: ModelKind): ServerModels {
    val cache = LocalAppEnv.current.prefs.prefs.modelCache
    return remember(ModelCache.key(access.provider.id, kind, access.baseUrl)) {
        ServerModels(cache, kind, cache.get(access, kind))
    }
}

/**
 * Laedt die Liste still im Hintergrund, sobald der Zugang steht ([ready]): nur wenn sie fehlt oder
 * veraltet ist, mit [always] bei jedem Oeffnen (Ollama wie bisher). Die Pause entprellt das Tippen
 * in Adress- und Key-Feld; ein Fehler bleibt hier stumm (der Knopf meldet ihn).
 */
@Composable
fun AutoLoadModels(
    models: ServerModels,
    ready: Boolean,
    apiKey: String,
    always: Boolean = false,
    onLoaded: (ModelCache.Entry) -> Unit = {},
    access: () -> ApiAccess,
) {
    LaunchedEffect(models, ready, apiKey) {
        if (!ready || !(always || models.isStale)) return@LaunchedEffect
        delay(AUTOLOAD_DELAY_MS)
        models.refresh(access(), showLoading = false).onSuccess(onLoaded)
    }
}

/** Knopf "Modelle aktualisieren" (bzw. Ollama ohne Pro: "vom Server laden"); meldet Anzahl, "keine" oder den Fehler. */
@Composable
fun LoadModelsButton(
    models: ServerModels,
    enabled: Boolean,
    snack: SnackController,
    @StringRes label: Int = R.string.models_refresh,
    @DrawableRes icon: Int = R.drawable.ic_refresh,
    @StringRes noneText: Int = R.string.models_none,
    onLoaded: (ModelCache.Entry) -> Unit = {},
    access: () -> ApiAccess,
) {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val current by rememberUpdatedState(models)
    TextButton(
        enabled = enabled && !models.loading,
        onClick = {
            val started = models
            val target = access()
            scope.launch {
                val result = started.refresh(target, showLoading = true)
                // Inzwischen anderer Anbieter oder andere Adresse: Ergebnis gehoert nicht mehr hierher.
                if (current !== started) return@launch
                result.onSuccess { e ->
                    onLoaded(e)
                    val n = e.models.size
                    snack.show(if (n == 0) res.getString(noneText) else res.getQuantityString(R.plurals.models_count, n, n))
                }.onFailure { e ->
                    snack.show(res.getString(R.string.models_load_failed, e.message ?: e.javaClass.simpleName))
                }
            }
        },
    ) {
        LoomIcon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (models.loading) R.string.models_loading else label))
    }
}

/** Wartezeit nach der letzten Eingabe, bevor die Liste automatisch geladen wird. */
private const val AUTOLOAD_DELAY_MS = 700L
