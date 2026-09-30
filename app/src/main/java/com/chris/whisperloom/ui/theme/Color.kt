package com.chris.whisperloom.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Reine Kotlin-Fassung der v3-Farb-Tokens (UX-Spec §3.1) fuer Kontexte OHNE Composition
 * (Tests, Previews ohne Theme, spaetere Nicht-Compose-Aufrufer).
 *
 * Werte = res/values/colors.xml (dort ist die einzige Wahrheit; das Theme liest per
 * colorResource()). WhisperLoomThemeTest prueft, dass beide Listen uebereinstimmen.
 */
object LoomPalette {
    // M3-Rollen
    val background = Color(0xFF0E1116)
    val surface = Color(0xFF0E1116)
    val surfaceContainerLowest = Color(0xFF090B0F)
    val surfaceContainerLow = Color(0xFF161A20)
    val surfaceContainer = Color(0xFF1A1F26)
    val surfaceContainerHigh = Color(0xFF242A32)
    val surfaceContainerHighest = Color(0xFF2E353E)
    val surfaceBright = Color(0xFF343B45)
    val onSurface = Color(0xFFE4E8EE)
    val onSurfaceVariant = Color(0xFFB8C0CC)
    val outline = Color(0xFF8A93A0)
    val outlineVariant = Color(0xFF3A424D)
    val primary = Color(0xFF6FD9C7)
    val onPrimary = Color(0xFF00382F)
    val primaryContainer = Color(0xFF0F5B50)
    val onPrimaryContainer = Color(0xFFB4F2E6)
    val secondary = Color(0xFFA9C4E8)
    val onSecondary = Color(0xFF12304F)
    val secondaryContainer = Color(0xFF2B4665)
    val onSecondaryContainer = Color(0xFFD8E7FA)
    val tertiary = Color(0xFFD7B8FF)
    val onTertiary = Color(0xFF3C1D6A)
    val tertiaryContainer = Color(0xFF54368A)
    val onTertiaryContainer = Color(0xFFEEDCFF)
    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)

    // Zusatz-Tokens (LocalLoomColors)
    val recording = Color(0xFFFF4D4D)
    val onRecording = Color(0xFF1A0508)
    val recordingContainer = Color(0xFF4A0F12)
    val recordingText = Color(0xFFFF8A80)
    val success = Color(0xFF6FDD8B)
    val onSuccess = Color(0xFF00391A)
    val successContainer = Color(0xFF123D24)
    val onSuccessContainer = Color(0xFFB4F5C4)
    val warning = Color(0xFFFFC85C)
    val onWarning = Color(0xFF3D2C00)
    val warningContainer = Color(0xFF4A3600)
    val onWarningContainer = Color(0xFFFFE3A8)

    // Abgeleitet: Schatten des Widget-Namens (nur im Widget-Layout genutzt)
    val labelShadow = Color(0xCC000000)

    /** Zusatz-Tokens als LoomColors — Default von LocalLoomColors ausserhalb von WhisperLoomTheme. */
    val loomColors = LoomColors(
        recording = recording,
        onRecording = onRecording,
        recordingContainer = recordingContainer,
        recordingText = recordingText,
        success = success,
        onSuccess = onSuccess,
        successContainer = successContainer,
        onSuccessContainer = onSuccessContainer,
        warning = warning,
        onWarning = onWarning,
        warningContainer = warningContainer,
        onWarningContainer = onWarningContainer,
    )
}
