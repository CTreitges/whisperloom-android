package com.chris.whisperloom.ui.share

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R

// Wiederverwendbare Bausteine der Share-Ansicht (auch fuer die Haupt-Screens brauchbar).

/** Test-Tag der Skeleton-Zeilen. */
const val SKELETON_TAG = "skeleton"

/** Reduce-Motion (UX-Spec §5.4): Animator-Skalierung 0 = keine Animationen. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Platzhalter fuer den laufenden Text: 3 Zeilen surfaceContainerHigh (14 dp, Radius 7,
 * Breite 100/92/60 %), Alpha-Puls 0,6 → 1,0 in 1200 ms; bei Reduce-Motion statisch.
 */
@Composable
fun SkeletonLines(modifier: Modifier = Modifier) {
    val alpha: State<Float> = if (rememberReduceMotion()) {
        remember { mutableFloatStateOf(0.8f) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1200), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .testTag(SKELETON_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (width in listOf(1f, 0.92f, 0.6f)) {
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp)),
            )
        }
    }
}

/** Primaerer Button nach Spec §1.3: 56 dp hoch, volle Breite. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(56.dp)) {
        Text(text)
    }
}

/** Tertiaerer Button in Buttonhoehe, damit das Touch-Ziel gross bleibt. */
@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(56.dp)) {
        Text(text)
    }
}

/**
 * Zentrierte Zustandskarte (KEINE DATEI, KEIN ZUGANG, FEHLER GESAMT):
 * Icon 48 dp, titleMedium, bodyMedium, darunter die Buttons aus [actions].
 */
@Composable
fun StatusCard(
    icon: Painter,
    iconTint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painter = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(48.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                actions()
            }
        }
    }
}

/**
 * Fehler je Datei: OutlinedCard mit error-Rahmen, Grund (Live-Region Assertive) und "Erneut" nur fuer
 * diese Datei. [retryEnabled] = false, solange noch eine andere Datei laeuft (ein neuer Lauf wuerde
 * den laufenden ersetzen und dessen Ergebnis verwerfen).
 */
@Composable
fun ErrorCard(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry, enabled = retryEnabled) { Text(retryLabel) }
        }
    }
}
