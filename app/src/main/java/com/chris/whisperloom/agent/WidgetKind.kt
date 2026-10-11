package com.chris.whisperloom.agent

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chris.whisperloom.R

/** Wer ein Feature sieht: alle Nutzer oder nur mit freigeschalteten Pro-Funktionen. */
enum class Tier { NORMAL, PRO }

/**
 * Widget-Typen. Gespeichert wird [key] (stabil), nie der Enum-Name — wie bei [SpeechPause].
 * Neuer Typ = neuer Eintrag; das Widget-Menue zeigt ihn dann von selbst. Einen eigenen Provider
 * (eigener Eintrag in der Widget-Auswahl des Launchers) braucht nur ein Typ mit eigener Optik oder
 * eigenem Verhalten. [CLAUDE_CODE] unterscheidet sich nur im Auftrag an die Bridge und haengt
 * deshalb wie [VOICE_COMMAND] am einen [VoiceTaskWidget] — der Typ steht im Profil.
 */
enum class WidgetKind(
    val key: String,
    val tier: Tier,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
) {
    VOICE_COMMAND("voice_command", Tier.PRO, R.string.kind_voice_command, R.string.kind_voice_command_sub, R.drawable.ic_mic),

    /** Startet ueber die claude-bridge eine neue Claude-Code-Session; Modell und Effort stehen im Profil. */
    CLAUDE_CODE("claude_code", Tier.PRO, R.string.kind_claude_code, R.string.kind_claude_code_sub, R.drawable.ic_auto_fix_high);

    companion object {
        /** Unbekannt oder fehlend = [VOICE_COMMAND]: alle Profile bis 3.7.0 sind Sprach-Command-Widgets. */
        fun fromKey(key: String?): WidgetKind = entries.firstOrNull { it.key == key } ?: VOICE_COMMAND

        fun of(tier: Tier): List<WidgetKind> = entries.filter { it.tier == tier }
    }
}
